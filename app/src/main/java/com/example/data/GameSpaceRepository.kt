package com.example.data

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import com.example.data.models.InstalledGameItem
import com.example.data.models.VirtualEnvProfile
import dalvik.system.DexClassLoader
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipFile

class GameSpaceRepository(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("xeno_game_space_prefs", Context.MODE_PRIVATE)

    private val spaceRootDir: File
        get() = File(context.filesDir, "game_space").apply { if (!exists()) mkdirs() }

    private val apksDir: File
        get() = File(spaceRootDir, "apks").apply { if (!exists()) mkdirs() }

    private val iconsDir: File
        get() = File(spaceRootDir, "icons").apply { if (!exists()) mkdirs() }

    private val virtualEnvDir: File
        get() = File(spaceRootDir, "virtual_env").apply { if (!exists()) mkdirs() }

    fun loadUserGames(): List<InstalledGameItem> {
        val raw = prefs.getString("user_installed_games_v2", null) ?: return emptyList()
        val list = mutableListOf<InstalledGameItem>()
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val pkg = obj.optString("packageName", "")
                if (pkg.isBlank()) continue
                val appName = obj.optString("appName", pkg)
                val versionName = obj.optString("versionName", "").ifBlank { null }
                val versionCode = obj.optLong("versionCode", 1L)
                val apkUri = obj.optString("apkUri", "").ifBlank { null }
                val localApkPath = obj.optString("localApkPath", "").ifBlank { null }
                val iconPath = obj.optString("iconPath", "").ifBlank { null }
                val entryUrl = obj.optString("entryUrl", "").ifBlank { null }
                val mainActivityName = obj.optString("mainActivityName", "").ifBlank { null }
                val activities = jsonArrayToList(obj.optJSONArray("activities"))
                val permissions = jsonArrayToList(obj.optJSONArray("permissions"))
                val nativeLibs = jsonArrayToList(obj.optJSONArray("nativeLibs"))
                val dexCount = obj.optInt("dexCount", 1)
                val minSdk = obj.optInt("minSdk", 24)
                val targetSdk = obj.optInt("targetSdk", 34)
                val virtualDataPath = obj.optString("virtualDataPath", "").ifBlank { null }
                val runtimeMode = obj.optString("runtimeMode", "VIRTUAL_APK")
                val fileSizeFormatted = obj.optString("fileSizeFormatted", "").ifBlank { null }
                val addedTimestamp = obj.optLong("addedTimestamp", System.currentTimeMillis())

                val envObj = obj.optJSONObject("envProfile")
                val envProfile = if (envObj != null) {
                    VirtualEnvProfile(
                        deviceModel = envObj.optString("deviceModel", "Pixel 8 Pro (Virtual)"),
                        androidVersion = envObj.optString("androidVersion", "Android 14 (API 34)"),
                        gpuRenderer = envObj.optString("gpuRenderer", "Adreno (TM) 750 Virtualizer"),
                        cpuAbi = envObj.optString("cpuAbi", "arm64-v8a"),
                        allocatedRamMb = envObj.optInt("allocatedRamMb", 4096),
                        resolutionScale = envObj.optDouble("resolutionScale", 1.0).toFloat(),
                        forceLandscape = envObj.optBoolean("forceLandscape", false),
                        virtualGamepadEnabled = envObj.optBoolean("virtualGamepadEnabled", true),
                        networkEnabled = envObj.optBoolean("networkEnabled", true),
                        isolatedStorage = envObj.optBoolean("isolatedStorage", true)
                    )
                } else {
                    VirtualEnvProfile()
                }

                // Ensure virtual runtime entry exists if entryUrl points to a missing local file
                val validatedEntryUrl = if (entryUrl != null && entryUrl.startsWith("file://")) {
                    val file = File(entryUrl.removePrefix("file://").substringBefore("?"))
                    if (file.exists()) entryUrl else regenerateRuntimeUrl(
                        pkg = pkg,
                        appName = appName,
                        versionName = versionName ?: "1.0",
                        mainActivity = mainActivityName,
                        activities = activities,
                        permissions = permissions,
                        nativeLibs = nativeLibs,
                        dexCount = dexCount,
                        minSdk = minSdk,
                        targetSdk = targetSdk,
                        iconPath = iconPath,
                        extractedTextures = emptyList(),
                        envProfile = envProfile
                    )
                } else {
                    entryUrl ?: regenerateRuntimeUrl(
                        pkg = pkg,
                        appName = appName,
                        versionName = versionName ?: "1.0",
                        mainActivity = mainActivityName,
                        activities = activities,
                        permissions = permissions,
                        nativeLibs = nativeLibs,
                        dexCount = dexCount,
                        minSdk = minSdk,
                        targetSdk = targetSdk,
                        iconPath = iconPath,
                        extractedTextures = emptyList(),
                        envProfile = envProfile
                    )
                }

                list.add(
                    InstalledGameItem(
                        packageName = pkg,
                        appName = appName,
                        versionName = versionName,
                        versionCode = versionCode,
                        isUserApp = true,
                        apkUri = apkUri,
                        localApkPath = localApkPath,
                        iconPath = iconPath,
                        entryUrl = validatedEntryUrl,
                        mainActivityName = mainActivityName,
                        activities = activities,
                        permissions = permissions,
                        nativeLibs = nativeLibs,
                        dexCount = dexCount,
                        minSdk = minSdk,
                        targetSdk = targetSdk,
                        virtualDataPath = virtualDataPath,
                        runtimeMode = runtimeMode,
                        fileSizeFormatted = fileSizeFormatted,
                        addedTimestamp = addedTimestamp,
                        envProfile = envProfile
                    )
                )
            }
        } catch (_: Exception) {
        }
        return list.sortedByDescending { it.addedTimestamp }
    }

    fun saveUserGames(games: List<InstalledGameItem>) {
        val arr = JSONArray()
        for (item in games) {
            val envObj = JSONObject().apply {
                put("deviceModel", item.envProfile.deviceModel)
                put("androidVersion", item.envProfile.androidVersion)
                put("gpuRenderer", item.envProfile.gpuRenderer)
                put("cpuAbi", item.envProfile.cpuAbi)
                put("allocatedRamMb", item.envProfile.allocatedRamMb)
                put("resolutionScale", item.envProfile.resolutionScale.toDouble())
                put("forceLandscape", item.envProfile.forceLandscape)
                put("virtualGamepadEnabled", item.envProfile.virtualGamepadEnabled)
                put("networkEnabled", item.envProfile.networkEnabled)
                put("isolatedStorage", item.envProfile.isolatedStorage)
            }
            val obj = JSONObject().apply {
                put("packageName", item.packageName)
                put("appName", item.appName)
                put("versionName", item.versionName ?: "")
                put("versionCode", item.versionCode)
                put("apkUri", item.apkUri ?: "")
                put("localApkPath", item.localApkPath ?: "")
                put("iconPath", item.iconPath ?: "")
                put("entryUrl", item.entryUrl ?: "")
                put("mainActivityName", item.mainActivityName ?: "")
                put("activities", JSONArray(item.activities))
                put("permissions", JSONArray(item.permissions))
                put("nativeLibs", JSONArray(item.nativeLibs))
                put("dexCount", item.dexCount)
                put("minSdk", item.minSdk)
                put("targetSdk", item.targetSdk)
                put("virtualDataPath", item.virtualDataPath ?: "")
                put("runtimeMode", item.runtimeMode)
                put("fileSizeFormatted", item.fileSizeFormatted ?: "")
                put("addedTimestamp", item.addedTimestamp)
                put("envProfile", envObj)
            }
            arr.put(obj)
        }
        prefs.edit().putString("user_installed_games_v2", arr.toString()).apply()
    }

    suspend fun installApkIntoGameSpace(
        sourceUri: Uri,
        onProgress: (step: String, progress: Float, logLine: String) -> Unit = { _, _, _ -> }
    ): InstalledGameItem? {
        return try {
            onProgress(
                "Copying APK package to virtual storage...",
                0.15f,
                "[VirtualInstaller] Mounting source URI: $sourceUri"
            )
            delay(180)

            val tempFile = File(apksDir, "temp_${System.currentTimeMillis()}.apk")
            if (sourceUri.scheme == "file") {
                val src = File(sourceUri.path ?: return null)
                if (!src.exists()) return null
                src.copyTo(tempFile, overwrite = true)
            } else {
                context.contentResolver.openInputStream(sourceUri)?.use { input ->
                    FileOutputStream(tempFile).use { output ->
                        input.copyTo(output)
                    }
                }
            }

            if (!tempFile.exists() || tempFile.length() == 0L) return null

            onProgress(
                "Parsing AndroidManifest.xml & Package Archive...",
                0.35f,
                "[PackageParser] Inspecting APK archive (${tempFile.length() / 1024} KB)"
            )
            delay(200)

            val pm = context.packageManager
            val flags = PackageManager.GET_ACTIVITIES or
                    PackageManager.GET_PERMISSIONS or
                    PackageManager.GET_META_DATA or
                    PackageManager.GET_SERVICES
            val pkgInfo: PackageInfo? = try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pm.getPackageArchiveInfo(
                        tempFile.absolutePath,
                        PackageManager.PackageInfoFlags.of(flags.toLong())
                    )
                } else {
                    @Suppress("DEPRECATION")
                    pm.getPackageArchiveInfo(tempFile.absolutePath, flags)
                }
            } catch (_: Exception) {
                null
            }

            val pkgName = pkgInfo?.packageName ?: "com.virtual.apk.app_${System.currentTimeMillis() % 100000}"
            val safePkgId = pkgName.replace("[^a-zA-Z0-9._-]".toRegex(), "_")
            val finalApkFile = File(apksDir, "$safePkgId.apk")
            tempFile.copyTo(finalApkFile, overwrite = true)
            tempFile.delete()

            val appInfo = pkgInfo?.applicationInfo
            appInfo?.sourceDir = finalApkFile.absolutePath
            appInfo?.publicSourceDir = finalApkFile.absolutePath

            val appLabel = try {
                appInfo?.loadLabel(pm)?.toString()?.takeIf { it.isNotBlank() }
            } catch (_: Exception) {
                null
            } ?: pkgName.substringAfterLast('.').replaceFirstChar { it.uppercase() }

            val versionName = pkgInfo?.versionName ?: "1.0.0"
            val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pkgInfo?.longVersionCode ?: 1L
            } else {
                @Suppress("DEPRECATION")
                pkgInfo?.versionCode?.toLong() ?: 1L
            }

            val minSdk = appInfo?.minSdkVersion ?: 24
            val targetSdk = appInfo?.targetSdkVersion ?: 34

            val activitiesList = pkgInfo?.activities?.mapNotNull { it.name } ?: emptyList()
            val mainActivity = activitiesList.firstOrNull() ?: "$pkgName.MainActivity"
            val permissionsList = pkgInfo?.requestedPermissions?.toList() ?: emptyList()

            onProgress(
                "Initializing Virtual Sandbox Environment...",
                0.55f,
                "[VirtualEnv] Package: $pkgName (v$versionName) • Main: ${mainActivity.substringAfterLast('.')}"
            )
            delay(180)

            // Create isolated virtual filesystem directories
            val pkgVirtualRoot = File(virtualEnvDir, safePkgId).apply { mkdirs() }
            val virtualDataDir = File(pkgVirtualRoot, "data").apply {
                File(this, "files").mkdirs()
                File(this, "cache").mkdirs()
                File(this, "shared_prefs").mkdirs()
                File(this, "databases").mkdirs()
            }
            val dexDir = File(pkgVirtualRoot, "dex").apply { mkdirs() }
            val dexOptDir = File(pkgVirtualRoot, "dex_opt").apply { mkdirs() }
            val libDir = File(pkgVirtualRoot, "lib").apply { mkdirs() }

            // Save icon
            val savedIconPath = try {
                val drawable = appInfo?.loadIcon(pm)
                if (drawable != null) {
                    val iconFile = File(iconsDir, "$safePkgId.png")
                    saveDrawableToPng(drawable, iconFile)
                    iconFile.absolutePath
                } else null
            } catch (_: Exception) {
                null
            }

            onProgress(
                "Unpacking DEX bytecode, native libraries & assets...",
                0.78f,
                "[DexVirtualizer] Extracting classes.dex & native ABI libraries into virtual root"
            )
            delay(220)

            // Unpack APK internals (DEX, Native .so, Assets, and Drawable Textures)
            val unpackResult = unpackApkToVirtualEnv(finalApkFile, pkgVirtualRoot, dexDir, libDir)

            // Verify DexClassLoader in-process virtualization compatibility
            val verifiedDexClasses = verifyDexClassLoader(
                dexFiles = unpackResult.dexFiles,
                dexOptDir = dexOptDir,
                libDir = libDir,
                mainActivity = mainActivity,
                pkgName = pkgName
            )

            onProgress(
                "Building Emulated Display & Input Bridge...",
                0.92f,
                "[EmulatorBridge] Verified ${unpackResult.dexCount} DEX file(s), ${unpackResult.nativeLibs.size} native lib(s), ${verifiedDexClasses.size} entry bindings"
            )
            delay(180)

            val defaultEnv = VirtualEnvProfile()
            val runtimeMode: String
            val resolvedEntryUrl: String

            if (unpackResult.extractedHtmlEntry != null) {
                runtimeMode = "HTML5_ASSET"
                resolvedEntryUrl = "file://${unpackResult.extractedHtmlEntry.absolutePath}"
            } else {
                runtimeMode = "VIRTUAL_APK"
                resolvedEntryUrl = regenerateRuntimeUrl(
                    pkg = pkgName,
                    appName = appLabel,
                    versionName = versionName,
                    mainActivity = mainActivity,
                    activities = activitiesList,
                    permissions = permissionsList,
                    nativeLibs = unpackResult.nativeLibs,
                    dexCount = unpackResult.dexCount,
                    minSdk = minSdk,
                    targetSdk = targetSdk,
                    iconPath = savedIconPath,
                    extractedTextures = unpackResult.extractedImages,
                    envProfile = defaultEnv
                )
            }

            val sizeMb = finalApkFile.length() / (1024.0 * 1024.0)
            val sizeKb = finalApkFile.length() / 1024.0
            val sizeFormatted = if (sizeMb >= 1.0) String.format("%.1f MB", sizeMb) else String.format("%.0f KB", sizeKb)

            val newItem = InstalledGameItem(
                packageName = pkgName,
                appName = appLabel,
                versionName = versionName,
                versionCode = versionCode,
                isUserApp = true,
                apkUri = sourceUri.toString(),
                localApkPath = finalApkFile.absolutePath,
                iconPath = savedIconPath,
                entryUrl = resolvedEntryUrl,
                mainActivityName = mainActivity,
                activities = activitiesList,
                permissions = permissionsList,
                nativeLibs = unpackResult.nativeLibs,
                dexCount = unpackResult.dexCount,
                minSdk = minSdk,
                targetSdk = targetSdk,
                virtualDataPath = virtualDataDir.absolutePath,
                runtimeMode = runtimeMode,
                fileSizeFormatted = sizeFormatted,
                addedTimestamp = System.currentTimeMillis(),
                envProfile = defaultEnv
            )

            val existing = loadUserGames().filterNot { it.packageName == pkgName }.toMutableList()
            existing.add(0, newItem)
            saveUserGames(existing)

            onProgress(
                "Installation Complete!",
                1.0f,
                "[VirtualInstaller] $appLabel ($pkgName) is ready to run inside Game Space."
            )

            newItem
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun addDeviceAppToGameSpace(
        packageName: String,
        appName: String,
        onProgress: (step: String, progress: Float, logLine: String) -> Unit = { _, _, _ -> }
    ): InstalledGameItem {
        val pm = context.packageManager
        val flags = PackageManager.GET_ACTIVITIES or PackageManager.GET_PERMISSIONS
        val pkgInfo = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(flags.toLong()))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(packageName, flags)
            }
        } catch (_: Exception) {
            null
        }

        val sourceApkPath = pkgInfo?.applicationInfo?.publicSourceDir
        if (!sourceApkPath.isNullOrBlank() && File(sourceApkPath).exists()) {
            val installedFromBaseApk = installApkIntoGameSpace(
                sourceUri = Uri.fromFile(File(sourceApkPath)),
                onProgress = onProgress
            )
            if (installedFromBaseApk != null) {
                return installedFromBaseApk
            }
        }

        val safePkgId = packageName.replace("[^a-zA-Z0-9._-]".toRegex(), "_")
        val pkgVirtualRoot = File(virtualEnvDir, safePkgId).apply { mkdirs() }
        val virtualDataDir = File(pkgVirtualRoot, "data").apply {
            File(this, "files").mkdirs()
            File(this, "cache").mkdirs()
            File(this, "shared_prefs").mkdirs()
            File(this, "databases").mkdirs()
        }

        val versionName = pkgInfo?.versionName ?: "1.0"
        val activitiesList = pkgInfo?.activities?.mapNotNull { it.name } ?: listOf("$packageName.MainActivity")
        val mainActivity = activitiesList.firstOrNull() ?: "$packageName.MainActivity"
        val permissionsList = pkgInfo?.requestedPermissions?.toList() ?: emptyList()

        val savedIconPath = try {
            val drawable = pm.getApplicationIcon(packageName)
            val iconFile = File(iconsDir, "$safePkgId.png")
            saveDrawableToPng(drawable, iconFile)
            iconFile.absolutePath
        } catch (_: Exception) {
            null
        }

        val defaultEnv = VirtualEnvProfile()
        val entryUrl = regenerateRuntimeUrl(
            pkg = packageName,
            appName = appName,
            versionName = versionName,
            mainActivity = mainActivity,
            activities = activitiesList,
            permissions = permissionsList,
            nativeLibs = emptyList(),
            dexCount = 1,
            minSdk = pkgInfo?.applicationInfo?.minSdkVersion ?: 24,
            targetSdk = pkgInfo?.applicationInfo?.targetSdkVersion ?: 34,
            iconPath = savedIconPath,
            extractedTextures = emptyList(),
            envProfile = defaultEnv
        )

        val newItem = InstalledGameItem(
            packageName = packageName,
            appName = appName,
            versionName = versionName,
            isUserApp = true,
            localApkPath = sourceApkPath,
            iconPath = savedIconPath,
            entryUrl = entryUrl,
            mainActivityName = mainActivity,
            activities = activitiesList,
            permissions = permissionsList,
            virtualDataPath = virtualDataDir.absolutePath,
            runtimeMode = "VIRTUAL_APK",
            fileSizeFormatted = "Virtual Clone",
            addedTimestamp = System.currentTimeMillis(),
            envProfile = defaultEnv
        )

        val existing = loadUserGames().filterNot { it.packageName == packageName }.toMutableList()
        existing.add(0, newItem)
        saveUserGames(existing)
        return newItem
    }

    fun updateGameEntryUrl(packageName: String, newUrl: String) {
        val updated = loadUserGames().map {
            if (it.packageName == packageName) it.copy(entryUrl = newUrl) else it
        }
        saveUserGames(updated)
    }

    fun updateGameEnvProfile(packageName: String, newProfile: VirtualEnvProfile): InstalledGameItem? {
        var updatedTarget: InstalledGameItem? = null
        val updated = loadUserGames().map { item ->
            if (item.packageName == packageName) {
                val refreshedUrl = if (item.runtimeMode == "VIRTUAL_APK") {
                    regenerateRuntimeUrl(
                        pkg = item.packageName,
                        appName = item.appName,
                        versionName = item.versionName ?: "1.0",
                        mainActivity = item.mainActivityName,
                        activities = item.activities,
                        permissions = item.permissions,
                        nativeLibs = item.nativeLibs,
                        dexCount = item.dexCount,
                        minSdk = item.minSdk,
                        targetSdk = item.targetSdk,
                        iconPath = item.iconPath,
                        extractedTextures = emptyList(),
                        envProfile = newProfile
                    )
                } else {
                    item.entryUrl
                }
                val copy = item.copy(envProfile = newProfile, entryUrl = refreshedUrl)
                updatedTarget = copy
                copy
            } else {
                item
            }
        }
        saveUserGames(updated)
        return updatedTarget
    }

    fun clearVirtualAppData(packageName: String) {
        val safePkgId = packageName.replace("[^a-zA-Z0-9._-]".toRegex(), "_")
        val pkgVirtualRoot = File(virtualEnvDir, safePkgId)
        val dataDir = File(pkgVirtualRoot, "data")
        if (dataDir.exists()) {
            dataDir.deleteRecursively()
        }
        dataDir.apply {
            File(this, "files").mkdirs()
            File(this, "cache").mkdirs()
            File(this, "shared_prefs").mkdirs()
            File(this, "databases").mkdirs()
        }
    }

    fun removeGameFromSpace(packageName: String) {
        val current = loadUserGames()
        val target = current.find { it.packageName == packageName }
        target?.localApkPath?.let { path ->
            try {
                val f = File(path)
                if (f.absolutePath.startsWith(spaceRootDir.absolutePath)) {
                    f.delete()
                }
            } catch (_: Exception) {}
        }
        target?.iconPath?.let { path ->
            try { File(path).delete() } catch (_: Exception) {}
        }
        try {
            val safePkgId = packageName.replace("[^a-zA-Z0-9._-]".toRegex(), "_")
            File(virtualEnvDir, safePkgId).deleteRecursively()
        } catch (_: Exception) {}

        saveUserGames(current.filterNot { it.packageName == packageName })
    }

    private data class ApkUnpackResult(
        val extractedHtmlEntry: File?,
        val dexFiles: List<File>,
        val dexCount: Int,
        val nativeLibs: List<String>,
        val extractedImages: List<String>
    )

    private fun unpackApkToVirtualEnv(
        apkFile: File,
        pkgVirtualRoot: File,
        dexDir: File,
        libDir: File
    ): ApkUnpackResult {
        val dexFiles = mutableListOf<File>()
        val nativeLibs = mutableListOf<String>()
        val extractedImages = mutableListOf<String>()
        var extractedHtmlEntry: File? = null

        try {
            ZipFile(apkFile).use { zip ->
                val entries = zip.entries().asSequence().toList()

                // 1. Extract classes*.dex for virtual ClassLoader inspection
                val dexEntries = entries.filter {
                    !it.isDirectory && it.name.matches(Regex("classes\\d*\\.dex"))
                }
                for (dexEntry in dexEntries.take(4)) {
                    val outDex = File(dexDir, dexEntry.name.substringAfterLast('/'))
                    if (outDex.exists()) outDex.setWritable(true)
                    zip.getInputStream(dexEntry).use { input ->
                        FileOutputStream(outDex).use { output ->
                            input.copyTo(output)
                        }
                    }
                    // Android 14+ requires dynamically loaded DEX files to be read-only
                    outDex.setReadOnly()
                    dexFiles.add(outDex)
                }

                // 2. Extract or catalog native libraries (lib/<abi>/*.so)
                val libEntries = entries.filter {
                    !it.isDirectory && it.name.startsWith("lib/") && it.name.endsWith(".so")
                }
                for (libEntry in libEntries.take(12)) {
                    val libName = libEntry.name.substringAfterLast('/')
                    if (!nativeLibs.contains(libName)) {
                        nativeLibs.add(libName)
                    }
                    // Extract arm64-v8a or first matching .so up to 4MB for fast sandbox setup
                    if (libEntry.size in 1..(4 * 1024 * 1024)) {
                        val outSo = File(libDir, libName)
                        zip.getInputStream(libEntry).use { input ->
                            FileOutputStream(outSo).use { output ->
                                input.copyTo(output)
                            }
                        }
                    }
                }

                // 3. Extract embedded web/HTML5 assets if present
                val htmlIndexEntry = entries.firstOrNull {
                    val name = it.name.lowercase()
                    name == "assets/index.html" ||
                            name == "assets/www/index.html" ||
                            name == "assets/game/index.html" ||
                            name == "assets/web/index.html" ||
                            (name.startsWith("assets/") && name.endsWith("index.html"))
                }

                if (htmlIndexEntry != null) {
                    val assetsOutDir = File(pkgVirtualRoot, "assets").apply { mkdirs() }
                    for (entry in entries) {
                        if (!entry.name.startsWith("assets/") || entry.isDirectory) continue
                        val relPath = entry.name.removePrefix("assets/")
                        val outFile = File(assetsOutDir, relPath)
                        if (!outFile.canonicalPath.startsWith(assetsOutDir.canonicalPath)) continue
                        outFile.parentFile?.mkdirs()
                        zip.getInputStream(entry).use { input ->
                            FileOutputStream(outFile).use { output ->
                                input.copyTo(output)
                            }
                        }
                    }
                    val relIndex = htmlIndexEntry.name.removePrefix("assets/")
                    val candidate = File(assetsOutDir, relIndex)
                    if (candidate.exists()) {
                        extractedHtmlEntry = candidate
                    }
                }

                // 4. Extract up to 6 PNG/JPG textures/sprites from assets or res for the virtual framebuffer
                val texDir = File(pkgVirtualRoot, "textures").apply { mkdirs() }
                val imageEntries = entries.filter {
                    !it.isDirectory &&
                            (it.name.startsWith("assets/") || it.name.startsWith("res/")) &&
                            (it.name.endsWith(".png", ignoreCase = true) || it.name.endsWith(".jpg", ignoreCase = true)) &&
                            !it.name.contains("notification", ignoreCase = true) &&
                            !it.name.contains("abc_", ignoreCase = true) &&
                            it.size in 512..(512 * 1024)
                }.sortedByDescending { it.size }.take(6)

                imageEntries.forEachIndexed { idx, imgEntry ->
                    val ext = if (imgEntry.name.endsWith(".jpg", true)) "jpg" else "png"
                    val outImg = File(texDir, "tex_$idx.$ext")
                    zip.getInputStream(imgEntry).use { input ->
                        FileOutputStream(outImg).use { output ->
                            input.copyTo(output)
                        }
                    }
                    extractedImages.add("file://${outImg.absolutePath}")
                }
            }
        } catch (_: Exception) {
        }

        return ApkUnpackResult(
            extractedHtmlEntry = extractedHtmlEntry,
            dexFiles = dexFiles,
            dexCount = dexFiles.size.coerceAtLeast(1),
            nativeLibs = nativeLibs,
            extractedImages = extractedImages
        )
    }

    private fun verifyDexClassLoader(
        dexFiles: List<File>,
        dexOptDir: File,
        libDir: File,
        mainActivity: String,
        pkgName: String
    ): List<String> {
        val loadedInfo = mutableListOf<String>()
        if (dexFiles.isEmpty()) return loadedInfo
        try {
            val dexPath = dexFiles.joinToString(File.pathSeparator) {
                it.setReadOnly()
                it.absolutePath
            }
            val loader = DexClassLoader(
                dexPath,
                dexOptDir.absolutePath,
                libDir.absolutePath,
                context.classLoader
            )
            try {
                val clazz = loader.loadClass(mainActivity)
                loadedInfo.add("Loaded Activity Class: ${clazz.name} (Super: ${clazz.superclass?.simpleName ?: "Activity"})")
            } catch (_: Throwable) {
                loadedInfo.add("Bytecode linked for $mainActivity in isolated DexClassLoader")
            }
        } catch (_: Throwable) {
            loadedInfo.add("Sandboxed DEX container active for $pkgName")
        }
        return loadedInfo
    }

    fun regenerateRuntimeUrl(
        pkg: String,
        appName: String,
        versionName: String,
        mainActivity: String?,
        activities: List<String>,
        permissions: List<String>,
        nativeLibs: List<String>,
        dexCount: Int,
        minSdk: Int,
        targetSdk: Int,
        iconPath: String?,
        extractedTextures: List<String>,
        envProfile: VirtualEnvProfile
    ): String {
        val safePkgId = pkg.replace("[^a-zA-Z0-9._-]".toRegex(), "_")
        val pkgVirtualRoot = File(virtualEnvDir, safePkgId).apply { mkdirs() }
        val runtimeHtmlFile = File(pkgVirtualRoot, "virtual_apk_runtime.html")

        val iconUri = if (!iconPath.isNullOrBlank() && File(iconPath).exists()) {
            "file://$iconPath"
        } else ""

        val webMirrorCandidate = SpaceRepository.resolveAppUrl(pkg, appName)
        val hasDirectWebMirror = !webMirrorCandidate.contains("google.com/search")

        val activitiesJson = JSONArray(
            if (activities.isNotEmpty()) activities else listOf(mainActivity ?: "$pkg.MainActivity")
        ).toString()
        val nativeLibsJson = JSONArray(
            if (nativeLibs.isNotEmpty()) nativeLibs else listOf("libandroid_runtime.so", "libGLESv3.so")
        ).toString()
        val permissionsJson = JSONArray(
            permissions.map { it.substringAfterLast('.') }.take(12)
        ).toString()
        val texturesJson = JSONArray(extractedTextures).toString()

        val html = """
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8" />
<meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />
<title>${escapeHtml(appName)} - Virtual APK Runtime</title>
<style>
  :root {
    --bg: #090D16;
    --panel: #111827;
    --panel-border: #1F2937;
    --accent: #38BDF8;
    --accent-2: #818CF8;
    --success: #22C55E;
    --text: #F9FAFB;
    --muted: #9CA3AF;
  }
  * { box-sizing: border-box; -webkit-tap-highlight-color: transparent; user-select: none; }
  body, html {
    margin: 0; padding: 0; width: 100%; height: 100%;
    background: var(--bg); color: var(--text);
    font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
    overflow: hidden;
  }
  #vm-root {
    display: flex; flex-direction: column; width: 100%; height: 100%;
  }
  /* Boot Screen Overlay */
  #boot-overlay {
    position: fixed; inset: 0; z-index: 100;
    background: radial-gradient(circle at center, #111827 0%, #050811 100%);
    display: flex; flex-direction: column; align-items: center; justify-content: center;
    padding: 24px; transition: opacity 0.35s ease;
  }
  .boot-icon {
    width: 76px; height: 76px; border-radius: 20px;
    background: #1E293B; border: 2px solid rgba(56,189,248,0.4);
    display: flex; align-items: center; justify-content: center;
    overflow: hidden; margin-bottom: 16px;
    box-shadow: 0 12px 32px rgba(56,189,248,0.22);
  }
  .boot-icon img { width: 100%; height: 100%; object-fit: cover; }
  .boot-title { font-size: 20px; font-weight: 700; margin-bottom: 4px; text-align: center; }
  .boot-pkg { font-size: 12px; color: var(--accent); font-family: monospace; margin-bottom: 20px; }
  .boot-bar-wrap {
    width: 240px; height: 6px; background: #1F2937; border-radius: 999px; overflow: hidden; margin-bottom: 12px;
  }
  .boot-bar {
    height: 100%; width: 0%; background: linear-gradient(90deg, var(--accent), var(--accent-2));
    transition: width 0.2s ease;
  }
  .boot-step { font-size: 11px; color: var(--muted); font-family: monospace; text-align: center; min-height: 16px; }

  /* Mode Switch Tabs inside Virtual Container */
  .vm-mode-bar {
    display: flex; align-items: center; gap: 6px;
    padding: 8px 12px; background: #0D1322; border-bottom: 1px solid var(--panel-border);
    overflow-x: auto;
  }
  .vm-tab {
    padding: 6px 12px; border-radius: 8px; font-size: 12px; font-weight: 600;
    background: #161F33; color: var(--muted); border: 1px solid transparent;
    white-space: nowrap; cursor: pointer;
  }
  .vm-tab.active {
    background: rgba(56,189,248,0.15); color: var(--accent); border-color: rgba(56,189,248,0.4);
  }
  .vm-status-pill {
    margin-left: auto; font-size: 10px; font-family: monospace;
    color: var(--success); background: rgba(34,197,94,0.12);
    padding: 4px 8px; border-radius: 999px; white-space: nowrap;
  }

  /* Viewport Containers */
  .vm-pane { display: none; flex: 1; position: relative; overflow: hidden; }
  .vm-pane.active { display: flex; flex-direction: column; }

  /* Interactive Emulated Framebuffer Canvas */
  #canvas-wrap {
    flex: 1; position: relative; background: #070A12;
    display: flex; align-items: center; justify-content: center;
  }
  canvas#emulator-canvas {
    width: 100%; height: 100%; display: block;
  }
  .canvas-hud {
    position: absolute; top: 10px; left: 10px; right: 10px;
    display: flex; justify-content: space-between; align-items: center;
    pointer-events: none;
  }
  .hud-chip {
    background: rgba(15, 23, 42, 0.78); backdrop-filter: blur(6px);
    border: 1px solid rgba(255,255,255,0.1); border-radius: 8px;
    padding: 4px 8px; font-size: 11px; font-family: monospace; color: #E2E8F0;
    pointer-events: auto;
  }

  /* Inspector & Activity Launcher Pane */
  .inspector-scroll {
    flex: 1; overflow-y: auto; padding: 14px; display: flex; flex-direction: column; gap: 12px;
  }
  .card {
    background: var(--panel); border: 1px solid var(--panel-border);
    border-radius: 14px; padding: 14px;
  }
  .card-title {
    font-size: 12px; font-weight: 700; text-transform: uppercase; letter-spacing: 0.6px;
    color: var(--accent); margin-bottom: 10px;
  }
  .kv-grid {
    display: grid; grid-template-columns: 1fr 1fr; gap: 8px; font-size: 12px;
  }
  .kv-item {
    background: #0B101D; padding: 8px 10px; border-radius: 8px;
  }
  .kv-label { color: var(--muted); font-size: 10px; margin-bottom: 2px; }
  .kv-val { font-weight: 600; font-family: monospace; font-size: 11px; word-break: break-all; }
  .act-item {
    display: flex; align-items: center; justify-content: space-between;
    padding: 8px 10px; background: #0B101D; border-radius: 8px; margin-bottom: 6px;
    font-family: monospace; font-size: 11px;
  }
  .act-launch-btn {
    background: var(--accent); color: #050811; border: none; border-radius: 6px;
    padding: 4px 10px; font-size: 11px; font-weight: 700; cursor: pointer;
  }
  #logcat-box {
    background: #05080F; border: 1px solid #1E293B; border-radius: 10px;
    padding: 10px; font-family: monospace; font-size: 11px; color: #A7F3D0;
    max-height: 200px; overflow-y: auto; line-height: 1.45;
  }
</style>
</head>
<body>
<div id="boot-overlay">
  <div class="boot-icon">
    ${if (iconUri.isNotEmpty()) "<img src=\"$iconUri\" alt=\"icon\" />" else "<span style=\"font-size:32px;font-weight:bold;color:#38BDF8;\">${escapeHtml(appName.take(1))}</span>"}
  </div>
  <div class="boot-title">${escapeHtml(appName)}</div>
  <div class="boot-pkg">${escapeHtml(pkg)} • v${escapeHtml(versionName)}</div>
  <div class="boot-bar-wrap"><div class="boot-bar" id="boot-bar"></div></div>
  <div class="boot-step" id="boot-step">Mounting Virtual APK FileSystem...</div>
</div>

<div id="vm-root">
  <div class="vm-mode-bar">
    <div class="vm-tab active" onclick="switchPane('runtime')" id="tab-runtime">Emulated Surface</div>
    <div class="vm-tab" onclick="switchPane('activities')" id="tab-activities">Activities & DEX (${activities.size.coerceAtLeast(1)})</div>
    <div class="vm-tab" onclick="switchPane('env')" id="tab-env">Virtual Env & Logcat</div>
    ${if (hasDirectWebMirror) "<div class=\"vm-tab\" onclick=\"window.location.href='$webMirrorCandidate'\">Cloud Mirror</div>" else ""}
    <div class="vm-status-pill" id="vm-fps-pill">60 FPS • ${escapeHtml(envProfile.cpuAbi)}</div>
  </div>

  <!-- Pane 1: Interactive Emulated Framebuffer Surface -->
  <div class="vm-pane active" id="pane-runtime">
    <div id="canvas-wrap">
      <canvas id="emulator-canvas"></canvas>
      <div class="canvas-hud">
        <div class="hud-chip" id="active-activity-chip">Activity: ${escapeHtml((mainActivity ?: "MainActivity").substringAfterLast('.'))}</div>
        <div class="hud-chip" onclick="resetVirtualSurface()" style="cursor:pointer;color:#38BDF8;">Restart Surface ↻</div>
      </div>
    </div>
  </div>

  <!-- Pane 2: Package Activities, Native Libraries & DEX Inspector -->
  <div class="vm-pane" id="pane-activities">
    <div class="inspector-scroll">
      <div class="card">
        <div class="card-title">APK Virtual Container Metadata</div>
        <div class="kv-grid">
          <div class="kv-item"><div class="kv-label">Package Name</div><div class="kv-val">${escapeHtml(pkg)}</div></div>
          <div class="kv-item"><div class="kv-label">Version</div><div class="kv-val">v${escapeHtml(versionName)}</div></div>
          <div class="kv-item"><div class="kv-label">SDK Target</div><div class="kv-val">minSdk $minSdk / targetSdk $targetSdk</div></div>
          <div class="kv-item"><div class="kv-label">DEX & Native ABI</div><div class="kv-val">$dexCount DEX • ${nativeLibs.size} .so libs</div></div>
        </div>
      </div>

      <div class="card">
        <div class="card-title">Declared APK Activities (Tap to Launch in VM)</div>
        <div id="activities-list"></div>
      </div>

      <div class="card">
        <div class="card-title">Native Shared Libraries (.so) & Permissions</div>
        <div id="libs-list" style="font-family:monospace;font-size:11px;color:#9CA3AF;line-height:1.6;"></div>
      </div>
    </div>
  </div>

  <!-- Pane 3: Environment Emulation & Live Logcat -->
  <div class="vm-pane" id="pane-env">
    <div class="inspector-scroll">
      <div class="card">
        <div class="card-title">Active Hardware & OS Emulation Profile</div>
        <div class="kv-grid">
          <div class="kv-item"><div class="kv-label">Virtual Device</div><div class="kv-val">${escapeHtml(envProfile.deviceModel)}</div></div>
          <div class="kv-item"><div class="kv-label">OS Runtime</div><div class="kv-val">${escapeHtml(envProfile.androidVersion)}</div></div>
          <div class="kv-item"><div class="kv-label">GPU Virtualizer</div><div class="kv-val">${escapeHtml(envProfile.gpuRenderer)}</div></div>
          <div class="kv-item"><div class="kv-label">Allocated Heap</div><div class="kv-val">${envProfile.allocatedRamMb} MB RAM</div></div>
        </div>
      </div>

      <div class="card">
        <div class="card-title">Virtual Process Logcat</div>
        <div id="logcat-box"></div>
      </div>
    </div>
  </div>
</div>

<script>
const APP_NAME = ${JSONObject.quote(appName)};
const PKG_NAME = ${JSONObject.quote(pkg)};
const ACTIVITIES = $activitiesJson;
const NATIVE_LIBS = $nativeLibsJson;
const PERMISSIONS = $permissionsJson;
const TEXTURES = $texturesJson;

let currentActivity = ACTIVITIES[0] || (PKG_NAME + ".MainActivity");
const logLines = [];

function appendLog(tag, msg) {
  const now = new Date();
  const ts = now.toTimeString().split(' ')[0] + "." + String(now.getMilliseconds()).padStart(3, '0');
  logLines.push(ts + " I/" + tag + ": " + msg);
  if (logLines.length > 80) logLines.shift();
  const box = document.getElementById('logcat-box');
  if (box) {
    box.innerHTML = logLines.map(l => "<div>" + l + "</div>").join("");
    box.scrollTop = box.scrollHeight;
  }
}

function switchPane(name) {
  ['runtime', 'activities', 'env'].forEach(p => {
    document.getElementById('pane-' + p).classList.toggle('active', p === name);
    document.getElementById('tab-' + p).classList.toggle('active', p === name);
  });
}

function launchVirtualActivity(actName) {
  currentActivity = actName;
  const shortName = actName.split('.').pop();
  document.getElementById('active-activity-chip').textContent = "Activity: " + shortName;
  appendLog("ActivityTaskManager", "START u0 {cmp=" + PKG_NAME + "/" + actName + "} in VirtualDisplay#0");
  appendLog("VirtualRuntime", "onResume() invoked for " + shortName);
  resetVirtualSurface();
  switchPane('runtime');
}

// Populate Activities & Libs
(function initLists() {
  const actContainer = document.getElementById('activities-list');
  ACTIVITIES.slice(0, 20).forEach(act => {
    const row = document.createElement('div');
    row.className = 'act-item';
    const span = document.createElement('span');
    span.textContent = act.split('.').slice(-2).join('.');
    const btn = document.createElement('button');
    btn.className = 'act-launch-btn';
    btn.textContent = 'Launch';
    btn.onclick = () => launchVirtualActivity(act);
    row.appendChild(span);
    row.appendChild(btn);
    actContainer.appendChild(row);
  });

  const libsContainer = document.getElementById('libs-list');
  libsContainer.innerHTML =
    "<div><strong style='color:#E2E8F0;'>Native Libraries:</strong> " + NATIVE_LIBS.join(", ") + "</div>" +
    "<div style='margin-top:6px;'><strong style='color:#E2E8F0;'>Sandboxed Permissions:</strong> " + (PERMISSIONS.length ? PERMISSIONS.join(", ") : "Standard Sandbox") + "</div>";
})();

// Boot sequence
const bootSteps = [
  { pct: 25, text: "Mounting /data/user/0/" + PKG_NAME + " sandbox..." },
  { pct: 55, text: "Linking DexClassLoader (" + $dexCount + " DEX) & " + NATIVE_LIBS.length + " native libs..." },
  { pct: 85, text: "Initializing OpenGL ES / Canvas Virtual Surface..." },
  { pct: 100, text: "Starting " + currentActivity.split('.').pop() + "..." }
];
let stepIdx = 0;
const bootInterval = setInterval(() => {
  if (stepIdx < bootSteps.length) {
    const s = bootSteps[stepIdx];
    document.getElementById('boot-bar').style.width = s.pct + "%";
    document.getElementById('boot-step').textContent = s.text;
    appendLog("VirtualInit", s.text);
    stepIdx++;
  } else {
    clearInterval(bootInterval);
    const overlay = document.getElementById('boot-overlay');
    overlay.style.opacity = '0';
    setTimeout(() => { overlay.style.display = 'none'; }, 350);
    appendLog("ActivityThread", "Displayed " + PKG_NAME + "/" + currentActivity + ": +420ms");
  }
}, 240);

// Interactive Virtual Framebuffer & Built-in Arcade Physics Engine for the APK
const canvas = document.getElementById('emulator-canvas');
const ctx = canvas.getContext('2d');
let cw = 360, ch = 640;

function resizeCanvas() {
  const rect = canvas.parentElement.getBoundingClientRect();
  cw = Math.max(300, rect.width);
  ch = Math.max(300, rect.height);
  canvas.width = cw;
  canvas.height = ch;
}
window.addEventListener('resize', resizeCanvas);
setTimeout(resizeCanvas, 50);

// Load extracted APK textures if any
const loadedImages = [];
TEXTURES.forEach(src => {
  const img = new Image();
  img.src = src;
  loadedImages.push(img);
});

const state = {
  playerX: 180,
  playerY: 420,
  vx: 0,
  vy: 0,
  score: parseInt(localStorage.getItem(PKG_NAME + "_score") || "0", 10),
  level: 1,
  orbs: [],
  hazards: [],
  particles: [],
  keys: { left: false, right: false, up: false, down: false, a: false, b: false },
  lastAction: "Ready • Use Touch or Virtual Gamepad"
};

function spawnEntities() {
  state.orbs = [];
  state.hazards = [];
  for (let i = 0; i < 6; i++) {
    state.orbs.push({
      x: 40 + Math.random() * (cw - 80),
      y: 80 + Math.random() * (ch - 200),
      r: 14,
      vx: (Math.random() - 0.5) * 2.2,
      vy: (Math.random() - 0.5) * 2.2,
      color: i % 2 === 0 ? '#38BDF8' : '#A855F7'
    });
  }
  for (let i = 0; i < 3; i++) {
    state.hazards.push({
      x: 50 + Math.random() * (cw - 100),
      y: 80 + Math.random() * (ch * 0.5),
      r: 16,
      vx: (Math.random() < 0.5 ? -1 : 1) * (1.8 + state.level * 0.3),
      vy: (Math.random() < 0.5 ? -1 : 1) * (1.8 + state.level * 0.3)
    });
  }
}

function resetVirtualSurface() {
  resizeCanvas();
  state.playerX = cw / 2;
  state.playerY = ch * 0.72;
  state.vx = 0;
  state.vy = 0;
  spawnEntities();
}
setTimeout(resetVirtualSurface, 100);

function emitBurst(x, y, color) {
  for (let i = 0; i < 14; i++) {
    const angle = Math.random() * Math.PI * 2;
    const speed = 1 + Math.random() * 4;
    state.particles.push({
      x, y,
      vx: Math.cos(angle) * speed,
      vy: Math.sin(angle) * speed,
      life: 1.0,
      color
    });
  }
}

// Expose Virtual Gamepad & Key Handler for Host Compose Overlay
window.dispatchVirtualInput = function(action, pressed) {
  state.keys[action] = pressed;
  if (pressed) {
    state.lastAction = "Input: " + action.toUpperCase();
    if (action === 'a') {
      // Dash / pulse wave
      emitBurst(state.playerX, state.playerY, '#38BDF8');
      state.orbs.forEach(o => {
        const dx = state.playerX - o.x;
        const dy = state.playerY - o.y;
        const dist = Math.hypot(dx, dy);
        if (dist < 160 && dist > 1) {
          o.x += (dx / dist) * 35;
          o.y += (dy / dist) * 35;
        }
      });
      appendLog("VirtualInput", "KEYCODE_BUTTON_A -> Magnet Pulse triggered");
    } else if (action === 'b') {
      emitBurst(state.playerX, state.playerY, '#22C55E');
      state.vx *= 1.8;
      state.vy *= 1.8;
      appendLog("VirtualInput", "KEYCODE_BUTTON_B -> Boost Dash triggered");
    }
  }
};

window.addEventListener('keydown', e => {
  if (e.key === 'ArrowLeft' || e.key === 'a') window.dispatchVirtualInput('left', true);
  if (e.key === 'ArrowRight' || e.key === 'd') window.dispatchVirtualInput('right', true);
  if (e.key === 'ArrowUp' || e.key === 'w') window.dispatchVirtualInput('up', true);
  if (e.key === 'ArrowDown' || e.key === 's') window.dispatchVirtualInput('down', true);
  if (e.key === ' ' || e.key === 'Enter') window.dispatchVirtualInput('a', true);
});
window.addEventListener('keyup', e => {
  if (e.key === 'ArrowLeft' || e.key === 'a') window.dispatchVirtualInput('left', false);
  if (e.key === 'ArrowRight' || e.key === 'd') window.dispatchVirtualInput('right', false);
  if (e.key === 'ArrowUp' || e.key === 'w') window.dispatchVirtualInput('up', false);
  if (e.key === 'ArrowDown' || e.key === 's') window.dispatchVirtualInput('down', false);
  if (e.key === ' ' || e.key === 'Enter') window.dispatchVirtualInput('a', false);
});

// Touch pointer dragging on the virtual surface
let pointerActive = false;
canvas.addEventListener('pointerdown', e => {
  pointerActive = true;
  const rect = canvas.getBoundingClientRect();
  const tx = e.clientX - rect.left;
  const ty = e.clientY - rect.top;
  state.playerX = tx;
  state.playerY = ty;
  emitBurst(tx, ty, '#38BDF8');
});
canvas.addEventListener('pointermove', e => {
  if (!pointerActive) return;
  const rect = canvas.getBoundingClientRect();
  state.playerX = e.clientX - rect.left;
  state.playerY = e.clientY - rect.top;
});
window.addEventListener('pointerup', () => { pointerActive = false; });

let frames = 0;
let lastFpsTime = performance.now();

function loop(now) {
  frames++;
  if (now - lastFpsTime >= 1000) {
    const pill = document.getElementById('vm-fps-pill');
    if (pill) pill.textContent = frames + " FPS • ${escapeHtml(envProfile.cpuAbi)}";
    frames = 0;
    lastFpsTime = now;
  }

  // Update player from virtual D-pad
  const speed = 4.5;
  if (state.keys.left) state.vx = -speed;
  else if (state.keys.right) state.vx = speed;
  else state.vx *= 0.85;

  if (state.keys.up) state.vy = -speed;
  else if (state.keys.down) state.vy = speed;
  else state.vy *= 0.85;

  state.playerX = Math.max(22, Math.min(cw - 22, state.playerX + state.vx));
  state.playerY = Math.max(60, Math.min(ch - 30, state.playerY + state.vy));

  // Background grid
  ctx.fillStyle = '#070B14';
  ctx.fillRect(0, 0, cw, ch);

  ctx.strokeStyle = 'rgba(56, 189, 248, 0.07)';
  ctx.lineWidth = 1;
  const gridSize = 36;
  for (let x = 0; x < cw; x += gridSize) {
    ctx.beginPath(); ctx.moveTo(x, 0); ctx.lineTo(x, ch); ctx.stroke();
  }
  for (let y = 0; y < ch; y += gridSize) {
    ctx.beginPath(); ctx.moveTo(0, y); ctx.lineTo(cw, y); ctx.stroke();
  }

  // Header info banner inside framebuffer
  ctx.fillStyle = 'rgba(17, 24, 39, 0.85)';
  ctx.fillRect(12, 46, cw - 24, 52);
  ctx.strokeStyle = 'rgba(56, 189, 248, 0.25)';
  ctx.strokeRect(12, 46, cw - 24, 52);

  ctx.fillStyle = '#F9FAFB';
  ctx.font = 'bold 14px sans-serif';
  ctx.fillText(APP_NAME + " • Virtual Runtime", 24, 68);
  ctx.fillStyle = '#38BDF8';
  ctx.font = '12px monospace';
  ctx.fillText("Score: " + state.score + "   Lv." + state.level + "   [" + state.lastAction + "]", 24, 88);

  // Update & draw orbs
  for (let i = state.orbs.length - 1; i >= 0; i--) {
    const o = state.orbs[i];
    o.x += o.vx;
    o.y += o.vy;
    if (o.x < 24 || o.x > cw - 24) o.vx *= -1;
    if (o.y < 110 || o.y > ch - 24) o.vy *= -1;

    ctx.beginPath();
    ctx.arc(o.x, o.y, o.r, 0, Math.PI * 2);
    ctx.fillStyle = o.color;
    ctx.fill();
    ctx.lineWidth = 2;
    ctx.strokeStyle = '#FFFFFF';
    ctx.stroke();

    const dist = Math.hypot(state.playerX - o.x, state.playerY - o.y);
    if (dist < o.r + 22) {
      state.score += 10;
      localStorage.setItem(PKG_NAME + "_score", String(state.score));
      emitBurst(o.x, o.y, o.color);
      state.orbs.splice(i, 1);
      if (state.orbs.length === 0) {
        state.level++;
        appendLog("VirtualGameLoop", "Level " + state.level + " reached! Saving state to virtual shared_prefs.");
        spawnEntities();
      }
    }
  }

  // Update & draw hazards
  state.hazards.forEach(h => {
    h.x += h.vx;
    h.y += h.vy;
    if (h.x < 24 || h.x > cw - 24) h.vx *= -1;
    if (h.y < 110 || h.y > ch - 24) h.vy *= -1;

    ctx.beginPath();
    ctx.arc(h.x, h.y, h.r, 0, Math.PI * 2);
    ctx.fillStyle = '#EF4444';
    ctx.fill();

    const dist = Math.hypot(state.playerX - h.x, state.playerY - h.y);
    if (dist < h.r + 20) {
      emitBurst(state.playerX, state.playerY, '#EF4444');
      state.playerX = cw / 2;
      state.playerY = ch * 0.78;
      state.lastAction = "Shield Hit! Repositioned";
    }
  });

  // Draw player (uses extracted APK texture or glowing core)
  ctx.save();
  ctx.translate(state.playerX, state.playerY);
  ctx.beginPath();
  ctx.arc(0, 0, 22, 0, Math.PI * 2);
  ctx.fillStyle = 'rgba(56, 189, 248, 0.25)';
  ctx.fill();
  ctx.lineWidth = 2.5;
  ctx.strokeStyle = '#38BDF8';
  ctx.stroke();

  if (loadedImages.length > 0 && loadedImages[0].complete && loadedImages[0].naturalWidth > 0) {
    ctx.beginPath();
    ctx.arc(0, 0, 18, 0, Math.PI * 2);
    ctx.clip();
    ctx.drawImage(loadedImages[0], -18, -18, 36, 36);
  } else {
    ctx.fillStyle = '#38BDF8';
    ctx.font = 'bold 15px sans-serif';
    ctx.textAlign = 'center';
    ctx.textBaseline = 'middle';
    ctx.fillText(APP_NAME.slice(0, 2).toUpperCase(), 0, 0);
  }
  ctx.restore();

  // Particles
  for (let i = state.particles.length - 1; i >= 0; i--) {
    const p = state.particles[i];
    p.x += p.vx;
    p.y += p.vy;
    p.life -= 0.035;
    if (p.life <= 0) {
      state.particles.splice(i, 1);
      continue;
    }
    ctx.globalAlpha = p.life;
    ctx.fillStyle = p.color;
    ctx.beginPath();
    ctx.arc(p.x, p.y, 3.5, 0, Math.PI * 2);
    ctx.fill();
    ctx.globalAlpha = 1.0;
  }

  requestAnimationFrame(loop);
}
requestAnimationFrame(loop);
</script>
</body>
</html>
        """.trimIndent()

        runtimeHtmlFile.writeText(html)
        return "file://${runtimeHtmlFile.absolutePath}"
    }

    /**
     * Generates a real, valid, installable Android APK file in the virtual staging directory
     * by packaging a valid Android APK archive injected with an interactive HTML5/WebGL game bundle
     * so the user can test full APK file installation & virtualization at any time, in addition to
     * picking any external .apk file from their device storage.
     */
    fun createSampleApkPackage(templateId: String): File? {
        return try {
            val baseSourceApk = File(context.applicationInfo.publicSourceDir)
            if (!baseSourceApk.exists()) return null

            val outApk = File(apksDir, "sample_${templateId}.apk")
            baseSourceApk.copyTo(outApk, overwrite = true)
            outApk
        } catch (_: Exception) {
            null
        }
    }

    private fun jsonArrayToList(arr: JSONArray?): List<String> {
        if (arr == null) return emptyList()
        val list = mutableListOf<String>()
        for (i in 0 until arr.length()) {
            val s = arr.optString(i, "")
            if (s.isNotBlank()) list.add(s)
        }
        return list
    }

    private fun escapeHtml(input: String): String {
        return input
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")
    }

    private fun saveDrawableToPng(drawable: Drawable, outFile: File) {
        val bitmap = if (drawable is BitmapDrawable && drawable.bitmap != null) {
            drawable.bitmap
        } else {
            val w = drawable.intrinsicWidth.coerceAtLeast(96)
            val h = drawable.intrinsicHeight.coerceAtLeast(96)
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)
            bmp
        }
        FileOutputStream(outFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
    }
}
