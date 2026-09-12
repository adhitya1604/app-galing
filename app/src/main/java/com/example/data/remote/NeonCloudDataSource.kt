package com.example.data.remote

import android.util.Log
import com.example.data.model.PackageItem
import com.example.data.model.PackageStatus
import com.example.data.model.User
import com.example.data.model.UserRole
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class NeonCloudDataSource private constructor() {

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private fun escapeSql(value: String): String {
        return value.replace("'", "''")
    }

    private suspend fun executeQuery(sql: String): Result<JSONObject> = withContext(Dispatchers.IO) {
        try {
            val payload = JSONObject().apply {
                put("query", sql)
            }.toString()

            val request = Request.Builder()
                .url(CloudConfig.NEON_SQL_URL)
                .addHeader("Content-Type", "application/json")
                .addHeader("Neon-Connection-String", CloudConfig.NEON_CONNECTION_STRING)
                .post(payload.toRequestBody(jsonMediaType))
                .build()

            val response = client.newCall(request).execute()
            val bodyString = response.body?.string().orEmpty()

            if (!response.isSuccessful) {
                Log.e("NeonCloud", "SQL Error ${response.code}: $bodyString")
                return@withContext Result.failure(Exception("Cloud SQL error: ${response.code} $bodyString"))
            }

            val json = JSONObject(bodyString)
            Result.success(json)
        } catch (e: Exception) {
            Log.e("NeonCloud", "Network exception", e)
            Result.failure(e)
        }
    }

    suspend fun testConnection(): Boolean = withContext(Dispatchers.IO) {
        val result = executeQuery("SELECT 1 as ping;")
        result.isSuccess
    }

    suspend fun fetchAllPackages(): Result<List<PackageItem>> = withContext(Dispatchers.IO) {
        val sql = "SELECT * FROM packages ORDER BY id DESC;"
        val res = executeQuery(sql)
        res.fold(
            onSuccess = { json ->
                val rows = json.optJSONArray("rows") ?: JSONArray()
                val list = mutableListOf<PackageItem>()
                for (i in 0 until rows.length()) {
                    val row = rows.getJSONObject(i)
                    val id = row.optLong("id", 0L)
                    val tracking = row.optString("tracking_number", "")
                    val recipient = row.optString("recipient_name", "")
                    val address = row.optString("address", "")
                    val packagePhoto = if (row.isNull("package_photo")) null else row.optString("package_photo", null)
                    val courierId = row.optString("courier_id", "KUR001")
                    val courierName = row.optString("courier_name", "Kurir Duo Galing")
                    val statusStr = row.optString("status", PackageStatus.DIBAWA_KURIR.name)
                    val scannedAt = row.optLong("scanned_at", System.currentTimeMillis())
                    val createdAt = row.optLong("created_at", System.currentTimeMillis())
                    val updatedAt = row.optLong("updated_at", System.currentTimeMillis())
                    val notes = row.optString("notes", "")

                    val status = try {
                        PackageStatus.valueOf(statusStr)
                    } catch (e: Exception) {
                        PackageStatus.DIBAWA_KURIR
                    }

                    list.add(
                        PackageItem(
                            id = id,
                            trackingNumber = tracking,
                            recipientName = recipient,
                            address = address,
                            packagePhoto = packagePhoto,
                            courierId = courierId,
                            courierName = courierName,
                            status = status,
                            scannedAt = scannedAt,
                            createdAt = createdAt,
                            updatedAt = updatedAt,
                            notes = notes
                        )
                    )
                }
                Result.success(list)
            },
            onFailure = { Result.failure(it) }
        )
    }

    suspend fun savePackage(item: PackageItem): Result<Long> = withContext(Dispatchers.IO) {
        val safeTracking = escapeSql(item.trackingNumber)
        val safeRecipient = escapeSql(item.recipientName)
        val safeAddress = escapeSql(item.address)
        val safeCourierId = escapeSql(item.courierId)
        val safeCourierName = escapeSql(item.courierName)
        val safeStatus = escapeSql(item.status.name)
        val safeNotes = escapeSql(item.notes)
        val photoVal = if (item.packagePhoto != null) "'${escapeSql(item.packagePhoto)}'" else "NULL"
        val now = System.currentTimeMillis()

        val sql = """
            INSERT INTO packages (
                tracking_number, recipient_name, address, package_photo,
                courier_id, courier_name, status, scanned_at, created_at, updated_at, notes
            ) VALUES (
                '$safeTracking', '$safeRecipient', '$safeAddress', $photoVal,
                '$safeCourierId', '$safeCourierName', '$safeStatus', ${item.scannedAt}, ${item.createdAt}, $now, '$safeNotes'
            ) RETURNING id;
        """.trimIndent()

        val res = executeQuery(sql)
        res.fold(
            onSuccess = { json ->
                val rows = json.optJSONArray("rows")
                val insertedId = if (rows != null && rows.length() > 0) {
                    rows.getJSONObject(0).optLong("id", now)
                } else {
                    now
                }
                Result.success(insertedId)
            },
            onFailure = { Result.failure(it) }
        )
    }

    suspend fun updatePackageStatus(packageId: Long, trackingNumber: String, status: PackageStatus): Result<Unit> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val safeStatus = escapeSql(status.name)
        val safeTracking = escapeSql(trackingNumber)

        val sql = if (packageId > 0) {
            "UPDATE packages SET status = '$safeStatus', updated_at = $now WHERE id = $packageId;"
        } else {
            "UPDATE packages SET status = '$safeStatus', updated_at = $now WHERE tracking_number = '$safeTracking';"
        }

        val res = executeQuery(sql)
        res.fold(
            onSuccess = { Result.success(Unit) },
            onFailure = { Result.failure(it) }
        )
    }

    suspend fun updatePackage(item: PackageItem): Result<Unit> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val safeRecipient = escapeSql(item.recipientName)
        val safeAddress = escapeSql(item.address)
        val safeStatus = escapeSql(item.status.name)
        val safeNotes = escapeSql(item.notes)
        val safeTracking = escapeSql(item.trackingNumber)
        val photoVal = if (item.packagePhoto != null) "'${escapeSql(item.packagePhoto)}'" else "NULL"

        val sql = if (item.id > 0) {
            """
            UPDATE packages SET 
                recipient_name = '$safeRecipient',
                address = '$safeAddress',
                status = '$safeStatus',
                notes = '$safeNotes',
                package_photo = $photoVal,
                updated_at = $now
            WHERE id = ${item.id};
            """.trimIndent()
        } else {
            """
            UPDATE packages SET 
                recipient_name = '$safeRecipient',
                address = '$safeAddress',
                status = '$safeStatus',
                notes = '$safeNotes',
                package_photo = $photoVal,
                updated_at = $now
            WHERE tracking_number = '$safeTracking';
            """.trimIndent()
        }

        val res = executeQuery(sql)
        res.fold(
            onSuccess = { Result.success(Unit) },
            onFailure = { Result.failure(it) }
        )
    }

    suspend fun deletePackage(packageId: Long, trackingNumber: String): Result<Unit> = withContext(Dispatchers.IO) {
        val sql = if (packageId > 0) {
            "DELETE FROM packages WHERE id = $packageId;"
        } else {
            "DELETE FROM packages WHERE tracking_number = '${escapeSql(trackingNumber)}';"
        }
        val res = executeQuery(sql)
        res.fold(
            onSuccess = { Result.success(Unit) },
            onFailure = { Result.failure(it) }
        )
    }

    suspend fun clearAllCloudData(): Result<Unit> = withContext(Dispatchers.IO) {
        val q1 = executeQuery("TRUNCATE TABLE packages;")
        val q2 = executeQuery("TRUNCATE TABLE users RESTART IDENTITY CASCADE;")
        if (q1.isSuccess && q2.isSuccess) {
            Result.success(Unit)
        } else {
            Result.failure(q1.exceptionOrNull() ?: q2.exceptionOrNull() ?: Exception("Gagal menghapus data cloud"))
        }
    }

    suspend fun verifyCloudLogin(emailOrPhone: String, passwordHash: String): Result<User?> = withContext(Dispatchers.IO) {
        val safeInput = escapeSql(emailOrPhone.trim().lowercase())
        val sql = "SELECT * FROM users WHERE (LOWER(email) = '$safeInput' OR phone = '$safeInput') LIMIT 1;"
        val res = executeQuery(sql)
        res.fold(
            onSuccess = { json ->
                val rows = json.optJSONArray("rows")
                if (rows == null || rows.length() == 0) {
                    Result.success(null)
                } else {
                    val row = rows.getJSONObject(0)
                    val passInDb = row.optString("password_hash", "")
                    if (passInDb != passwordHash) {
                        Result.failure(Exception("Password salah. Silakan coba lagi."))
                    } else {
                        val id = row.optLong("id", 1L)
                        val name = row.optString("name", "")
                        val email = row.optString("email", "")
                        val phone = row.optString("phone", "")
                        val photo = if (row.isNull("photo")) null else row.optString("photo", null)
                        val roleStr = row.optString("role", "COURIER")
                        val courierId = row.optString("courier_id", "KUR001")
                        val isActive = row.optBoolean("is_active", true)
                        val createdAt = row.optLong("created_at", System.currentTimeMillis())

                        val role = try { UserRole.valueOf(roleStr) } catch (e: Exception) { UserRole.COURIER }
                        val user = User(
                            id = id,
                            name = name,
                            email = email,
                            phone = phone,
                            photo = photo,
                            role = role,
                            courierId = courierId,
                            isActive = isActive,
                            createdAt = createdAt
                        )
                        Result.success(user)
                    }
                }
            },
            onFailure = { Result.failure(it) }
        )
    }

    suspend fun registerUserInCloud(
        name: String,
        email: String,
        phone: String,
        passwordHash: String,
        role: UserRole
    ): Result<User> = withContext(Dispatchers.IO) {
        val safeName = escapeSql(name.trim())
        val safeEmail = escapeSql(email.trim().lowercase())
        val safePhone = escapeSql(phone.trim())
        val safePassword = escapeSql(passwordHash)
        val now = System.currentTimeMillis()

        // Generate code
        val countRes = executeQuery("SELECT COUNT(*) as count FROM users;")
        val currentCount = countRes.getOrNull()?.optJSONArray("rows")?.optJSONObject(0)?.optInt("count", 0) ?: 0
        val courierCode = if (role == UserRole.ADMIN) "ADM${String.format("%03d", currentCount + 1)}" else "KUR${String.format("%03d", currentCount + 1)}"

        val sql = """
            INSERT INTO users (name, email, phone, password_hash, role, courier_id, is_active, created_at)
            VALUES ('$safeName', '$safeEmail', '$safePhone', '$safePassword', '${role.name}', '$courierCode', true, $now)
            RETURNING id;
        """.trimIndent()

        val res = executeQuery(sql)
        res.fold(
            onSuccess = { json ->
                val id = json.optJSONArray("rows")?.optJSONObject(0)?.optLong("id", now) ?: now
                val domain = User(
                    id = id,
                    name = safeName,
                    email = safeEmail,
                    phone = safePhone,
                    role = role,
                    courierId = courierCode,
                    isActive = true,
                    createdAt = now
                )
                Result.success(domain)
            },
            onFailure = { Result.failure(it) }
        )
    }

    suspend fun fetchAllCouriers(): Result<List<User>> = withContext(Dispatchers.IO) {
        val sql = "SELECT * FROM users WHERE role = 'COURIER' ORDER BY id ASC;"
        val res = executeQuery(sql)
        res.fold(
            onSuccess = { json ->
                val rows = json.optJSONArray("rows") ?: JSONArray()
                val list = mutableListOf<User>()
                for (i in 0 until rows.length()) {
                    val row = rows.getJSONObject(i)
                    val id = row.optLong("id", (i + 1).toLong())
                    val name = row.optString("name", "")
                    val email = row.optString("email", "")
                    val phone = row.optString("phone", "")
                    val photo = if (row.isNull("photo")) null else row.optString("photo", null)
                    val courierId = row.optString("courier_id", "KUR${String.format("%03d", i + 1)}")
                    val isActive = row.optBoolean("is_active", true)
                    val createdAt = row.optLong("created_at", System.currentTimeMillis())

                    list.add(
                        User(
                            id = id,
                            name = name,
                            email = email,
                            phone = phone,
                            photo = photo,
                            role = UserRole.COURIER,
                            courierId = courierId,
                            isActive = isActive,
                            createdAt = createdAt
                        )
                    )
                }
                Result.success(list)
            },
            onFailure = { Result.failure(it) }
        )
    }

    suspend fun fetchAllUsersWithCredentials(): Result<List<Pair<User, String>>> = withContext(Dispatchers.IO) {
        val sql = "SELECT * FROM users ORDER BY id ASC;"
        val res = executeQuery(sql)
        res.fold(
            onSuccess = { json ->
                val rows = json.optJSONArray("rows") ?: JSONArray()
                val list = mutableListOf<Pair<User, String>>()
                for (i in 0 until rows.length()) {
                    val row = rows.getJSONObject(i)
                    val id = row.optLong("id", (i + 1).toLong())
                    val name = row.optString("name", "")
                    val email = row.optString("email", "")
                    val phone = row.optString("phone", "")
                    val pass = row.optString("password_hash", "")
                    val photo = if (row.isNull("photo")) null else row.optString("photo", null)
                    val roleStr = row.optString("role", "COURIER")
                    val courierId = row.optString("courier_id", if (roleStr == "ADMIN") "ADM001" else "KUR001")
                    val isActive = row.optBoolean("is_active", true)
                    val createdAt = row.optLong("created_at", System.currentTimeMillis())
                    val role = try { UserRole.valueOf(roleStr) } catch (e: Exception) { UserRole.COURIER }

                    val u = User(
                        id = id,
                        name = name,
                        email = email,
                        phone = phone,
                        photo = photo,
                        role = role,
                        courierId = courierId,
                        isActive = isActive,
                        createdAt = createdAt
                    )
                    list.add(Pair(u, pass))
                }
                Result.success(list)
            },
            onFailure = { Result.failure(it) }
        )
    }

    companion object {
        @Volatile
        private var INSTANCE: NeonCloudDataSource? = null

        fun getInstance(): NeonCloudDataSource {
            return INSTANCE ?: synchronized(this) {
                val instance = NeonCloudDataSource()
                INSTANCE = instance
                instance
            }
        }
    }
}
