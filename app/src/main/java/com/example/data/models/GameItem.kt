package com.example.data.models

data class InstalledGameItem(
    val packageName: String,
    val appName: String,
    val versionName: String?,
    val isUserApp: Boolean,
    val apkUri: String? = null
)
