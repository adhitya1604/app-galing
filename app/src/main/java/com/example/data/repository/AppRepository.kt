package com.example.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.data.local.AppDatabase
import com.example.data.local.PackageEntity
import com.example.data.local.UserEntity
import com.example.data.model.CourierSummary
import com.example.data.model.DailyStat
import com.example.data.model.PackageItem
import com.example.data.model.PackageStatus
import com.example.data.model.User
import com.example.data.model.UserRole
import com.example.data.remote.CloudConfig
import com.example.data.remote.CloudSyncStatus
import com.example.data.remote.NeonCloudDataSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

class AppRepository private constructor(
    private val database: AppDatabase,
    private val sharedPrefs: SharedPreferences
) {
    private val userDao = database.userDao()
    private val packageDao = database.packageDao()
    private val cloudDataSource = NeonCloudDataSource.getInstance()

    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: StateFlow<User?> = _currentUser.asStateFlow()

    private val _cloudStatus = MutableStateFlow(CloudSyncStatus.ONLINE)
    val cloudStatus: StateFlow<CloudSyncStatus> = _cloudStatus.asStateFlow()

    private val repositoryScope = CoroutineScope(Dispatchers.IO)

    init {
        // One-time migration to clear legacy demo data if not yet purged
        repositoryScope.launch {
            val hasPurgedLegacy = sharedPrefs.getBoolean("DATA_CLEARED_V2", false)
            if (!hasPurgedLegacy) {
                try {
                    packageDao.deleteAllPackages()
                    userDao.deleteAllUsers()
                } catch (e: Exception) {
                    Log.w("AppRepository", "Init purge: ${e.message}")
                }
                sharedPrefs.edit().remove(KEY_SAVED_USER_ID).putBoolean("DATA_CLEARED_V2", true).apply()
                _currentUser.value = null
            } else {
                // Check saved session
                val savedUserId = sharedPrefs.getLong(KEY_SAVED_USER_ID, -1L)
                if (savedUserId != -1L) {
                    val userEntity = userDao.getUserById(savedUserId)
                    userEntity?.let {
                        _currentUser.value = it.toDomain()
                    }
                }
            }
        }

        // Start initial cloud sync & real-time background loop
        repositoryScope.launch {
            syncFromCloud()
            while (isActive) {
                delay(10_000) // sync every 10 seconds in real-time
                syncFromCloud()
            }
        }
    }

    suspend fun syncFromCloud() = withContext(Dispatchers.IO) {
        try {
            _cloudStatus.value = CloudSyncStatus.SYNCING
            val packagesResult = cloudDataSource.fetchAllPackages()
            if (packagesResult.isSuccess) {
                val cloudPackages = packagesResult.getOrNull().orEmpty()
                packageDao.deleteAllPackages()
                if (cloudPackages.isNotEmpty()) {
                    val entities = cloudPackages.map { PackageEntity.fromDomain(it) }
                    packageDao.insertPackages(entities)
                }
                _cloudStatus.value = CloudSyncStatus.ONLINE
            } else {
                Log.w("AppRepository", "Cloud sync packages error: ${packagesResult.exceptionOrNull()?.message}")
                _cloudStatus.value = CloudSyncStatus.OFFLINE
            }

            // Also sync all users with credentials from cloud
            val usersResult = cloudDataSource.fetchAllUsersWithCredentials()
            if (usersResult.isSuccess) {
                val cloudUsers = usersResult.getOrNull().orEmpty()
                userDao.deleteAllUsers()
                if (cloudUsers.isNotEmpty()) {
                    val userEntities = cloudUsers.map { (user, pass) ->
                        UserEntity.fromDomain(user, pass)
                    }
                    userDao.insertUsers(userEntities)
                }
            }
        } catch (e: Exception) {
            Log.e("AppRepository", "Sync exception", e)
            _cloudStatus.value = CloudSyncStatus.OFFLINE
        }
    }

    suspend fun clearAllData(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            packageDao.deleteAllPackages()
            userDao.deleteAllUsers()
            sharedPrefs.edit().clear().putBoolean("DATA_CLEARED_V2", true).apply()
            _currentUser.value = null
            cloudDataSource.clearAllCloudData()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("AppRepository", "clearAllData error", e)
            Result.failure(e)
        }
    }

    // --- Authentication ---

    suspend fun login(emailOrPhone: String, password: String, rememberMe: Boolean = true): Result<User> = withContext(Dispatchers.IO) {
        val cleanInput = emailOrPhone.trim()

        // 1. Try cloud login first for real-time validity
        try {
            val cloudUserResult = cloudDataSource.verifyCloudLogin(cleanInput, password)
            if (cloudUserResult.isSuccess) {
                val cloudUser = cloudUserResult.getOrNull()
                if (cloudUser != null) {
                    if (!cloudUser.isActive) {
                        return@withContext Result.failure(Exception("Akun Anda sedang dinonaktifkan di sistem cloud."))
                    }

                    // Cache user locally
                    val entity = UserEntity.fromDomain(cloudUser, password)
                    userDao.insertUser(entity)
                    _currentUser.value = cloudUser

                    if (rememberMe) {
                        sharedPrefs.edit().putLong(KEY_SAVED_USER_ID, cloudUser.id).apply()
                    } else {
                        sharedPrefs.edit().remove(KEY_SAVED_USER_ID).apply()
                    }

                    // Trigger immediate package sync for this user
                    repositoryScope.launch { syncFromCloud() }
                    return@withContext Result.success(cloudUser)
                }
            } else {
                // If cloud returned explicit password error, throw it
                val err = cloudUserResult.exceptionOrNull()
                if (err != null && err.message?.contains("Password salah") == true) {
                    return@withContext Result.failure(err)
                }
            }
        } catch (e: Exception) {
            Log.w("AppRepository", "Cloud login network fallback: ${e.message}")
        }

        // 2. Fallback to local database if offline or user cached
        val localUser = userDao.getUserByEmailOrPhone(cleanInput.lowercase())
            ?: userDao.getUserByEmailOrPhone(cleanInput)

        if (localUser == null) {
            return@withContext Result.failure(Exception("Akun tidak ditemukan. Periksa email atau nomor HP Anda."))
        }

        if (localUser.passwordHash != password) {
            return@withContext Result.failure(Exception("Password salah. Silakan coba lagi."))
        }

        if (!localUser.isActive) {
            return@withContext Result.failure(Exception("Akun Anda sedang dinonaktifkan."))
        }

        val domainUser = localUser.toDomain()
        _currentUser.value = domainUser

        if (rememberMe) {
            sharedPrefs.edit().putLong(KEY_SAVED_USER_ID, domainUser.id).apply()
        } else {
            sharedPrefs.edit().remove(KEY_SAVED_USER_ID).apply()
        }

        Result.success(domainUser)
    }

    suspend fun register(
        name: String,
        email: String,
        phone: String,
        password: String,
        role: UserRole
    ): Result<User> = withContext(Dispatchers.IO) {
        // 1. Try register to cloud PostgreSQL first
        val cloudResult = cloudDataSource.registerUserInCloud(name, email, phone, password, role)
        val finalUser = if (cloudResult.isSuccess) {
            val user = cloudResult.getOrNull()!!
            userDao.insertUser(UserEntity.fromDomain(user, password))
            user
        } else {
            // Local fallback
            val existing = userDao.getUserByEmailOrPhone(email.trim().lowercase())
                ?: userDao.getUserByEmailOrPhone(phone.trim())
            if (existing != null) {
                return@withContext Result.failure(Exception("Email atau Nomor HP sudah terdaftar."))
            }

            val count = userDao.getUserTotalCount()
            val courierCode = if (role == UserRole.ADMIN) "ADM${String.format("%03d", count + 1)}" else "KUR${String.format("%03d", count + 1)}"

            val newUser = UserEntity(
                name = name.trim(),
                email = email.trim().lowercase(),
                phone = phone.trim(),
                passwordHash = password,
                role = role.name,
                courierId = courierCode,
                isActive = true
            )
            val id = userDao.insertUser(newUser)
            newUser.copy(id = id).toDomain()
        }

        Result.success(finalUser)
    }

    fun logout() {
        _currentUser.value = null
        sharedPrefs.edit().remove(KEY_SAVED_USER_ID).apply()
    }

    suspend fun updateProfile(name: String, phone: String, photo: String?): Result<User> = withContext(Dispatchers.IO) {
        val user = _currentUser.value ?: return@withContext Result.failure(Exception("Tidak ada sesi aktif"))
        val entity = userDao.getUserById(user.id) ?: return@withContext Result.failure(Exception("Pengguna tidak ditemukan"))

        val updated = entity.copy(name = name, phone = phone, photo = photo ?: entity.photo)
        userDao.updateUser(updated)
        val domain = updated.toDomain()
        _currentUser.value = domain
        Result.success(domain)
    }

    suspend fun changePassword(oldPass: String, newPass: String): Result<Unit> = withContext(Dispatchers.IO) {
        val user = _currentUser.value ?: return@withContext Result.failure(Exception("Tidak ada sesi aktif"))
        val entity = userDao.getUserById(user.id) ?: return@withContext Result.failure(Exception("Pengguna tidak ditemukan"))
        if (entity.passwordHash != oldPass) {
            return@withContext Result.failure(Exception("Password lama tidak sesuai."))
        }
        userDao.updateUser(entity.copy(passwordHash = newPass))
        Result.success(Unit)
    }

    fun getAllCouriers(): Flow<List<User>> {
        return userDao.getAllCouriers().map { list -> list.map { it.toDomain() } }
    }

    // --- Package Operations (Cloud + Local Sync) ---

    fun getAllPackages(): Flow<List<PackageItem>> {
        return packageDao.getAllPackages().map { list -> list.map { it.toDomain() } }
    }

    fun getPackagesForCourier(courierId: String): Flow<List<PackageItem>> {
        return packageDao.getPackagesByCourier(courierId).map { list -> list.map { it.toDomain() } }
    }

    suspend fun getPackageById(id: Long): PackageItem? = withContext(Dispatchers.IO) {
        packageDao.getPackageById(id)?.toDomain()
    }

    suspend fun insertPackage(item: PackageItem): Result<Long> = withContext(Dispatchers.IO) {
        try {
            // Save to Neon Cloud database
            val cloudRes = cloudDataSource.savePackage(item)
            val assignedId = if (cloudRes.isSuccess) cloudRes.getOrNull() ?: item.id else item.id

            // Save to local cache
            val entity = PackageEntity.fromDomain(item.copy(id = if (assignedId > 0) assignedId else item.id))
            val localId = packageDao.insertPackage(entity)

            // Trigger sync
            repositoryScope.launch { syncFromCloud() }

            Result.success(if (assignedId > 0) assignedId else localId)
        } catch (e: Exception) {
            Log.e("AppRepository", "Insert package error", e)
            val entity = PackageEntity.fromDomain(item)
            val id = packageDao.insertPackage(entity)
            Result.success(id)
        }
    }

    suspend fun updatePackageStatus(packageId: Long, newStatus: PackageStatus): Result<Unit> = withContext(Dispatchers.IO) {
        val pkg = packageDao.getPackageById(packageId) ?: return@withContext Result.failure(Exception("Paket tidak ditemukan"))
        val updated = pkg.copy(status = newStatus.name, updatedAt = System.currentTimeMillis())
        packageDao.updatePackage(updated)

        // Sync update to Cloud
        repositoryScope.launch {
            cloudDataSource.updatePackageStatus(packageId, pkg.trackingNumber, newStatus)
            syncFromCloud()
        }

        Result.success(Unit)
    }

    suspend fun updatePackage(item: PackageItem): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val entity = PackageEntity.fromDomain(item).copy(updatedAt = System.currentTimeMillis())
            packageDao.updatePackage(entity)

            // Sync to cloud
            repositoryScope.launch {
                cloudDataSource.updatePackage(item)
                syncFromCloud()
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deletePackage(id: Long): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val pkg = packageDao.getPackageById(id)
            packageDao.deletePackageById(id)

            if (pkg != null) {
                repositoryScope.launch {
                    cloudDataSource.deletePackage(id, pkg.trackingNumber)
                    syncFromCloud()
                }
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // --- Statistics Helpers ---

    fun getStartOfDayTimestamp(): Long {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }

    fun getCourierTodayStats(courierId: String): Flow<Triple<Int, Int, Int>> {
        val startOfDay = getStartOfDayTimestamp()
        return packageDao.getPackagesByCourier(courierId).map { list ->
            val todayList = list.filter { it.scannedAt >= startOfDay }
            val totalToday = todayList.size
            val successToday = todayList.count { it.status == PackageStatus.SELESAI.name }
            val pendingToday = totalToday - successToday
            Triple(totalToday, successToday, pendingToday)
        }
    }

    fun getAdminDashboardStats(): Flow<AdminStats> {
        val startOfDay = getStartOfDayTimestamp()
        return combine(packageDao.getAllPackages(), userDao.getCourierCount()) { list, courierCount ->
            val totalPackages = list.size
            val todayPackages = list.count { it.scannedAt >= startOfDay }
            val completedPackages = list.count { it.status == PackageStatus.SELESAI.name }
            val pendingPackages = totalPackages - completedPackages

            // 7 Days Stats calculation
            val dailyStats = mutableListOf<DailyStat>()
            for (i in 6 downTo 0) {
                val dayCal = Calendar.getInstance()
                dayCal.timeInMillis = System.currentTimeMillis() - (i * 86400_000L)
                val dayStart = dayCal.apply {
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
                val dayEnd = dayStart + 86400_000L
                val dayCount = list.count { it.scannedAt in dayStart until dayEnd }
                val dayName = when (dayCal.get(Calendar.DAY_OF_WEEK)) {
                    Calendar.MONDAY -> "Sen"
                    Calendar.TUESDAY -> "Sel"
                    Calendar.WEDNESDAY -> "Rab"
                    Calendar.THURSDAY -> "Kam"
                    Calendar.FRIDAY -> "Jum"
                    Calendar.SATURDAY -> "Sab"
                    Calendar.SUNDAY -> "Min"
                    else -> "Hari"
                }
                dailyStats.add(DailyStat(dayName, dayCount))
            }

            AdminStats(
                totalPackages = totalPackages,
                todayPackages = todayPackages,
                totalCouriers = courierCount,
                completedPackages = completedPackages,
                pendingPackages = pendingPackages,
                sevenDaysStats = dailyStats
            )
        }
    }

    data class AdminStats(
        val totalPackages: Int,
        val todayPackages: Int,
        val totalCouriers: Int,
        val completedPackages: Int,
        val pendingPackages: Int,
        val sevenDaysStats: List<DailyStat>
    )

    companion object {
        private const val PREFS_NAME = "duo_galing_prefs"
        private const val KEY_SAVED_USER_ID = "saved_user_id"

        @Volatile
        private var INSTANCE: AppRepository? = null

        fun getInstance(context: Context): AppRepository {
            return INSTANCE ?: synchronized(this) {
                val db = AppDatabase.getDatabase(context, CoroutineScope(Dispatchers.IO))
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val instance = AppRepository(db, prefs)
                INSTANCE = instance
                instance
            }
        }
    }
}
