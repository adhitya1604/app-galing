package com.example.data.model

enum class UserRole {
    COURIER,
    ADMIN;

    val displayName: String
        get() = when (this) {
            COURIER -> "Kurir"
            ADMIN -> "Admin"
        }
}

enum class PackageStatus {
    BARU,
    DIBAWA_KURIR,
    DIANTAR,
    SELESAI;

    val displayName: String
        get() = when (this) {
            BARU -> "Baru"
            DIBAWA_KURIR -> "Dibawa Kurir"
            DIANTAR -> "Diantar"
            SELESAI -> "Selesai"
        }
}

data class User(
    val id: Long = 0,
    val name: String,
    val email: String,
    val phone: String,
    val photo: String? = null,
    val role: UserRole = UserRole.COURIER,
    val courierId: String = "KUR001",
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)

data class PackageItem(
    val id: Long = 0,
    val trackingNumber: String,
    val recipientName: String,
    val address: String,
    val packagePhoto: String? = null,
    val courierId: String,
    val courierName: String,
    val status: PackageStatus = PackageStatus.DIBAWA_KURIR,
    val scannedAt: Long = System.currentTimeMillis(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val notes: String = ""
)

data class CourierSummary(
    val courierId: String,
    val name: String,
    val phone: String,
    val totalPackages: Int,
    val todayPackages: Int,
    val completedToday: Int,
    val activeStatus: String = "Aktif"
)

data class DailyStat(
    val dayLabel: String,
    val count: Int
)

data class OcrResult(
    val rawText: String,
    val trackingNumber: String,
    val recipientName: String,
    val address: String,
    val courierCompany: String = "",
    val confidence: Float = 0.95f,
    val isRecognized: Boolean = true
)
