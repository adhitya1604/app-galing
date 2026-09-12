package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PackageDao {
    @Query("SELECT * FROM packages ORDER BY scannedAt DESC")
    fun getAllPackages(): Flow<List<PackageEntity>>

    @Query("SELECT * FROM packages WHERE courierId = :courierId ORDER BY scannedAt DESC")
    fun getPackagesByCourier(courierId: String): Flow<List<PackageEntity>>

    @Query("SELECT * FROM packages WHERE id = :id LIMIT 1")
    suspend fun getPackageById(id: Long): PackageEntity?

    @Query("SELECT * FROM packages WHERE trackingNumber = :trackingNumber LIMIT 1")
    suspend fun getPackageByTrackingNumber(trackingNumber: String): PackageEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPackage(pkg: PackageEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPackages(packages: List<PackageEntity>)

    @Update
    suspend fun updatePackage(pkg: PackageEntity)

    @Query("DELETE FROM packages WHERE id = :id")
    suspend fun deletePackageById(id: Long)

    @Query("SELECT COUNT(*) FROM packages")
    fun getTotalPackageCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM packages WHERE scannedAt >= :startOfDayTimestamp")
    fun getTodayPackageCount(startOfDayTimestamp: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM packages WHERE courierId = :courierId AND scannedAt >= :startOfDayTimestamp")
    fun getCourierTodayPackageCount(courierId: String, startOfDayTimestamp: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM packages WHERE courierId = :courierId AND status = 'SELESAI' AND scannedAt >= :startOfDayTimestamp")
    fun getCourierTodaySuccessCount(courierId: String, startOfDayTimestamp: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM packages WHERE courierId = :courierId AND status != 'SELESAI' AND scannedAt >= :startOfDayTimestamp")
    fun getCourierTodayPendingCount(courierId: String, startOfDayTimestamp: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM packages")
    suspend fun getPackageTotalCount(): Int

    @Query("DELETE FROM packages")
    suspend fun deleteAllPackages()
}
