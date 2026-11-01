package com.example.data.models

data class SpeedBoostResult(
    val freedMemoryMb: Int,
    val speedBoostPercent: Int,
    val initialRamPercent: Int,
    val boostedRamPercent: Int,
    val cleanedCacheFilesCount: Int,
    val timestamp: Long = System.currentTimeMillis()
)
