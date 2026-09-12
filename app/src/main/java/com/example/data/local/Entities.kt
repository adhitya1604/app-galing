package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.data.model.PackageItem
import com.example.data.model.PackageStatus
import com.example.data.model.User
import com.example.data.model.UserRole

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val email: String,
    val phone: String,
    val passwordHash: String,
    val photo: String? = null,
    val role: String = UserRole.COURIER.name,
    val courierId: String = "KUR001",
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
) {
    fun toDomain(): User = User(
        id = id,
        name = name,
        email = email,
        phone = phone,
        photo = photo,
        role = try { UserRole.valueOf(role) } catch (e: Exception) { UserRole.COURIER },
        courierId = courierId,
        isActive = isActive,
        createdAt = createdAt
    )

    companion object {
        fun fromDomain(user: User, passwordHash: String): UserEntity = UserEntity(
            id = user.id,
            name = user.name,
            email = user.email,
            phone = user.phone,
            passwordHash = passwordHash,
            photo = user.photo,
            role = user.role.name,
            courierId = user.courierId,
            isActive = user.isActive,
            createdAt = user.createdAt
        )
    }
}

@Entity(tableName = "packages")
data class PackageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val trackingNumber: String,
    val recipientName: String,
    val address: String,
    val packagePhoto: String? = null,
    val courierId: String,
    val courierName: String,
    val status: String = PackageStatus.DIBAWA_KURIR.name,
    val scannedAt: Long = System.currentTimeMillis(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val notes: String = ""
) {
    fun toDomain(): PackageItem = PackageItem(
        id = id,
        trackingNumber = trackingNumber,
        recipientName = recipientName,
        address = address,
        packagePhoto = packagePhoto,
        courierId = courierId,
        courierName = courierName,
        status = try { PackageStatus.valueOf(status) } catch (e: Exception) { PackageStatus.DIBAWA_KURIR },
        scannedAt = scannedAt,
        createdAt = createdAt,
        updatedAt = updatedAt,
        notes = notes
    )

    companion object {
        fun fromDomain(item: PackageItem): PackageEntity = PackageEntity(
            id = item.id,
            trackingNumber = item.trackingNumber,
            recipientName = item.recipientName,
            address = item.address,
            packagePhoto = item.packagePhoto,
            courierId = item.courierId,
            courierName = item.courierName,
            status = item.status.name,
            scannedAt = item.scannedAt,
            createdAt = item.createdAt,
            updatedAt = item.updatedAt,
            notes = item.notes
        )
    }
}
