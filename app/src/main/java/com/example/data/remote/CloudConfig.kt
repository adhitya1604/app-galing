package com.example.data.remote

object CloudConfig {
    const val VERCEL_BASE_URL = "https://duogaling-backend.vercel.app/api/"
    const val NEON_SQL_URL = "https://ep-cold-voice-b3sk5v3q-pooler.c-4.ap-southeast-1.aws.neon.tech/sql"
    const val NEON_CONNECTION_STRING = "postgresql://neondb_owner:npg_MSwvl9d3eLnY@ep-cold-voice-b3sk5v3q-pooler.c-4.ap-southeast-1.aws.neon.tech/neondb?sslmode=require"
}

enum class CloudSyncStatus {
    ONLINE,
    SYNCING,
    OFFLINE,
    ERROR
}
