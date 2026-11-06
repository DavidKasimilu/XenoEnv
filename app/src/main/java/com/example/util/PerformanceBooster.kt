package com.example.util

import android.content.Context
import android.util.Log
import com.example.data.models.SpeedBoostResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object PerformanceBooster {

    private const val TAG = "PerformanceBooster"

    suspend fun executeSuperBoost(context: Context): SpeedBoostResult = withContext(Dispatchers.IO) {
        var cleanedFilesCount = 0

        val runtimeBefore = Runtime.getRuntime()
        val totalMemBefore = runtimeBefore.totalMemory()
        val freeMemBefore = runtimeBefore.freeMemory()
        val usedMemBeforeMb = ((totalMemBefore - freeMemBefore) / (1024 * 1024)).toInt()

        // 1. Clear app cache directory
        try {
            val cacheDir = context.cacheDir
            if (cacheDir != null && cacheDir.exists()) {
                cleanedFilesCount += deleteDirContents(cacheDir)
            }
            val extCacheDir = context.externalCacheDir
            if (extCacheDir != null && extCacheDir.exists()) {
                cleanedFilesCount += deleteDirContents(extCacheDir)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error cleaning cache directories: ${e.message}")
        }

        val runtimeAfter = Runtime.getRuntime()
        val totalMemAfter = runtimeAfter.totalMemory()
        val freeMemAfter = runtimeAfter.freeMemory()
        val usedMemAfterMb = ((totalMemAfter - freeMemAfter) / (1024 * 1024)).toInt()

        val actualFreedMb = (usedMemBeforeMb - usedMemAfterMb).coerceAtLeast(0)
        
        // Calculate percentages based on heap limits for "real" feedback
        val maxMem = runtimeAfter.maxMemory() / (1024 * 1024)
        val initialRamPct = ((usedMemBeforeMb.toFloat() / maxMem) * 100).toInt().coerceIn(0, 100)
        val boostedRamPct = ((usedMemAfterMb.toFloat() / maxMem) * 100).toInt().coerceIn(0, 100)
        val speedBoostPct = if (initialRamPct > boostedRamPct) (initialRamPct - boostedRamPct) else 0

        SpeedBoostResult(
            freedMemoryMb = actualFreedMb,
            speedBoostPercent = speedBoostPct,
            initialRamPercent = initialRamPct,
            boostedRamPercent = boostedRamPct,
            cleanedCacheFilesCount = cleanedFilesCount
        )
    }

    private fun deleteDirContents(dir: File): Int {
        var count = 0
        try {
            val files = dir.listFiles() ?: return 0
            for (file in files) {
                if (file.isDirectory) {
                    count += deleteDirContents(file)
                }
                if (file.name.contains("cache", ignoreCase = true) ||
                    file.name.endsWith(".tmp") ||
                    file.name.endsWith(".pdf") ||
                    file.name.endsWith(".log") ||
                    file.name.endsWith(".apk")
                ) {
                    if (file.delete()) count++
                }
            }
        } catch (_: Exception) {}
        return count
    }
}
