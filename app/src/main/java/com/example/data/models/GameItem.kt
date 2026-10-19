package com.example.data.models

data class InstalledGameItem(
    val packageName: String,
    val appName: String,
    val versionName: String?,
    val isUserApp: Boolean = true,
    val apkUri: String? = null,
    val localApkPath: String? = null,
    val iconPath: String? = null,
    val entryUrl: String? = null,
    val mainActivityName: String? = null,
    val fileSizeFormatted: String? = null,
    val addedTimestamp: Long = System.currentTimeMillis()
)
