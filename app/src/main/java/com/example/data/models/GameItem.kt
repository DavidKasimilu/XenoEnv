package com.example.data.models

data class VirtualEnvProfile(
    val deviceModel: String = "Pixel 8 Pro (Virtual)",
    val androidVersion: String = "Android 14 (API 34)",
    val gpuRenderer: String = "Adreno (TM) 750 Virtualizer",
    val cpuAbi: String = "arm64-v8a",
    val allocatedRamMb: Int = 4096,
    val resolutionScale: Float = 1.0f,
    val forceLandscape: Boolean = false,
    val virtualGamepadEnabled: Boolean = true,
    val networkEnabled: Boolean = true,
    val isolatedStorage: Boolean = true
)

data class ApkInstallState(
    val isInstalling: Boolean = false,
    val fileName: String = "",
    val currentStep: String = "",
    val progress: Float = 0f,
    val logs: List<String> = emptyList(),
    val error: String? = null,
    val completedItem: InstalledGameItem? = null
)

data class InstalledGameItem(
    val packageName: String,
    val appName: String,
    val versionName: String?,
    val versionCode: Long = 1L,
    val isUserApp: Boolean = true,
    val apkUri: String? = null,
    val localApkPath: String? = null,
    val iconPath: String? = null,
    val entryUrl: String? = null,
    val mainActivityName: String? = null,
    val activities: List<String> = emptyList(),
    val permissions: List<String> = emptyList(),
    val nativeLibs: List<String> = emptyList(),
    val dexCount: Int = 1,
    val minSdk: Int = 24,
    val targetSdk: Int = 34,
    val virtualDataPath: String? = null,
    val runtimeMode: String = "VIRTUAL_APK", // "HTML5_ASSET", "VIRTUAL_APK", "WEB_BRIDGE"
    val fileSizeFormatted: String? = null,
    val addedTimestamp: Long = System.currentTimeMillis(),
    val envProfile: VirtualEnvProfile = VirtualEnvProfile()
)
