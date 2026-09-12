package com.example.data.remote

import android.util.Log
import com.example.data.model.PackageItem
import com.example.data.model.PackageStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class VercelCloudDataSource private constructor(
    private val neonFallback: NeonCloudDataSource = NeonCloudDataSource.getInstance()
) {
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun fetchAllPackages(): Result<List<PackageItem>> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("${CloudConfig.VERCEL_BASE_URL}packages")
                .get()
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string().orEmpty()
                val jsonArray = JSONArray(body)
                val list = mutableListOf<PackageItem>()
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    list.add(
                        PackageItem(
                            id = obj.optLong("id", 0L),
                            trackingNumber = obj.optString("tracking_number", obj.optString("trackingNumber", "")),
                            recipientName = obj.optString("recipient_name", obj.optString("recipientName", "")),
                            address = obj.optString("address", ""),
                            packagePhoto = if (obj.isNull("package_photo")) null else obj.optString("package_photo", null),
                            courierId = obj.optString("courier_id", obj.optString("courierId", "KUR001")),
                            courierName = obj.optString("courier_name", obj.optString("courierName", "Kurir Duo Galing")),
                            status = try {
                                PackageStatus.valueOf(obj.optString("status", PackageStatus.DIBAWA_KURIR.name))
                            } catch (e: Exception) {
                                PackageStatus.DIBAWA_KURIR
                            },
                            scannedAt = obj.optLong("scanned_at", System.currentTimeMillis()),
                            createdAt = obj.optLong("created_at", System.currentTimeMillis()),
                            updatedAt = obj.optLong("updated_at", System.currentTimeMillis()),
                            notes = obj.optString("notes", "")
                        )
                    )
                }
                Result.success(list)
            } else {
                // Fallback to direct Neon PostgreSQL
                neonFallback.fetchAllPackages()
            }
        } catch (e: Exception) {
            Log.d("VercelCloud", "Vercel endpoint unavailable, using Neon direct SQL fallback: ${e.message}")
            neonFallback.fetchAllPackages()
        }
    }

    suspend fun savePackage(item: PackageItem): Result<Long> = withContext(Dispatchers.IO) {
        try {
            val payload = JSONObject().apply {
                put("trackingNumber", item.trackingNumber)
                put("recipientName", item.recipientName)
                put("address", item.address)
                put("courierId", item.courierId)
                put("courierName", item.courierName)
                put("status", item.status.name)
                put("notes", item.notes)
                if (item.packagePhoto != null) put("packagePhoto", item.packagePhoto)
            }.toString()

            val request = Request.Builder()
                .url("${CloudConfig.VERCEL_BASE_URL}packages")
                .post(payload.toRequestBody(jsonMediaType))
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string().orEmpty()
                val json = JSONObject(body)
                val id = json.optLong("id", System.currentTimeMillis())
                Result.success(id)
            } else {
                neonFallback.savePackage(item)
            }
        } catch (e: Exception) {
            neonFallback.savePackage(item)
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: VercelCloudDataSource? = null

        fun getInstance(): VercelCloudDataSource {
            return INSTANCE ?: synchronized(this) {
                val instance = VercelCloudDataSource()
                INSTANCE = instance
                instance
            }
        }
    }
}
