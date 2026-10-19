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

    private val runtimeDir: File
        get() = File(spaceRootDir, "runtime").apply { if (!exists()) mkdirs() }

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
                val apkUri = obj.optString("apkUri", "").ifBlank { null }
                val localApkPath = obj.optString("localApkPath", "").ifBlank { null }
                val iconPath = obj.optString("iconPath", "").ifBlank { null }
                val entryUrl = obj.optString("entryUrl", "").ifBlank {
                    SpaceRepository.resolveAppUrl(pkg, appName)
                }
                val mainActivityName = obj.optString("mainActivityName", "").ifBlank { null }
                val fileSizeFormatted = obj.optString("fileSizeFormatted", "").ifBlank { null }
                val addedTimestamp = obj.optLong("addedTimestamp", System.currentTimeMillis())

                list.add(
                    InstalledGameItem(
                        packageName = pkg,
                        appName = appName,
                        versionName = versionName,
                        isUserApp = true,
                        apkUri = apkUri,
                        localApkPath = localApkPath,
                        iconPath = iconPath,
                        entryUrl = entryUrl,
                        mainActivityName = mainActivityName,
                        fileSizeFormatted = fileSizeFormatted,
                        addedTimestamp = addedTimestamp
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
            val obj = JSONObject().apply {
                put("packageName", item.packageName)
                put("appName", item.appName)
                put("versionName", item.versionName ?: "")
                put("apkUri", item.apkUri ?: "")
                put("localApkPath", item.localApkPath ?: "")
                put("iconPath", item.iconPath ?: "")
                put("entryUrl", item.entryUrl ?: "")
                put("mainActivityName", item.mainActivityName ?: "")
                put("fileSizeFormatted", item.fileSizeFormatted ?: "")
                put("addedTimestamp", item.addedTimestamp)
            }
            arr.put(obj)
        }
        prefs.edit().putString("user_installed_games_v2", arr.toString()).apply()
    }

    fun installApkIntoGameSpace(sourceUri: Uri): InstalledGameItem? {
        return try {
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

            val pm = context.packageManager
            val flags = PackageManager.GET_ACTIVITIES or PackageManager.GET_META_DATA
            val pkgInfo: PackageInfo? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageArchiveInfo(tempFile.absolutePath, PackageManager.PackageInfoFlags.of(flags.toLong()))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageArchiveInfo(tempFile.absolutePath, flags)
            }

            val pkgName = pkgInfo?.packageName ?: "com.sandbox.apk.${System.currentTimeMillis()}"
            val finalApkFile = File(apksDir, "${pkgName.replace("[^a-zA-Z0-9._-]".toRegex(), "_")}.apk")
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

            val versionName = pkgInfo?.versionName ?: "1.0"
            val mainActivity = pkgInfo?.activities?.firstOrNull()?.name

            // Save icon
            val savedIconPath = try {
                val drawable = appInfo?.loadIcon(pm)
                if (drawable != null) {
                    val iconFile = File(iconsDir, "${pkgName.replace("[^a-zA-Z0-9._-]".toRegex(), "_")}.png")
                    saveDrawableToPng(drawable, iconFile)
                    iconFile.absolutePath
                } else null
            } catch (_: Exception) {
                null
            }

            // Check if APK has embedded web/HTML5 runtime assets (e.g., assets/index.html or assets/www/index.html)
            val extractedHtmlEntry = extractWebAssetsFromApk(finalApkFile, pkgName)

            val resolvedEntryUrl = if (extractedHtmlEntry != null) {
                "file://${extractedHtmlEntry.absolutePath}"
            } else {
                SpaceRepository.resolveAppUrl(pkgName, appLabel)
            }

            val sizeMb = finalApkFile.length() / (1024.0 * 1024.0)
            val sizeKb = finalApkFile.length() / 1024.0
            val sizeFormatted = if (sizeMb >= 1.0) String.format("%.1f MB", sizeMb) else String.format("%.0f KB", sizeKb)

            val newItem = InstalledGameItem(
                packageName = pkgName,
                appName = appLabel,
                versionName = versionName,
                isUserApp = true,
                apkUri = sourceUri.toString(),
                localApkPath = finalApkFile.absolutePath,
                iconPath = savedIconPath,
                entryUrl = resolvedEntryUrl,
                mainActivityName = mainActivity,
                fileSizeFormatted = sizeFormatted,
                addedTimestamp = System.currentTimeMillis()
            )

            val existing = loadUserGames().filterNot { it.packageName == pkgName }.toMutableList()
            existing.add(0, newItem)
            saveUserGames(existing)

            newItem
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun addDeviceAppToGameSpace(packageName: String, appName: String): InstalledGameItem {
        val pm = context.packageManager
        val pkgInfo = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(PackageManager.GET_ACTIVITIES.toLong()))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(packageName, PackageManager.GET_ACTIVITIES)
            }
        } catch (_: Exception) {
            null
        }

        val versionName = pkgInfo?.versionName ?: "1.0"
        val mainActivity = pkgInfo?.activities?.firstOrNull()?.name
        val sourceApkPath = pkgInfo?.applicationInfo?.publicSourceDir

        val savedIconPath = try {
            val drawable = pm.getApplicationIcon(packageName)
            val iconFile = File(iconsDir, "${packageName.replace("[^a-zA-Z0-9._-]".toRegex(), "_")}.png")
            saveDrawableToPng(drawable, iconFile)
            iconFile.absolutePath
        } catch (_: Exception) {
            null
        }

        val extractedHtml = if (!sourceApkPath.isNullOrBlank() && File(sourceApkPath).exists()) {
            extractWebAssetsFromApk(File(sourceApkPath), packageName)
        } else null

        val entryUrl = if (extractedHtml != null) {
            "file://${extractedHtml.absolutePath}"
        } else {
            SpaceRepository.resolveAppUrl(packageName, appName)
        }

        val newItem = InstalledGameItem(
            packageName = packageName,
            appName = appName,
            versionName = versionName,
            isUserApp = true,
            localApkPath = sourceApkPath,
            iconPath = savedIconPath,
            entryUrl = entryUrl,
            mainActivityName = mainActivity,
            fileSizeFormatted = "Sandboxed",
            addedTimestamp = System.currentTimeMillis()
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
            File(runtimeDir, packageName.replace("[^a-zA-Z0-9._-]".toRegex(), "_")).deleteRecursively()
        } catch (_: Exception) {}

        saveUserGames(current.filterNot { it.packageName == packageName })
    }

    private fun extractWebAssetsFromApk(apkFile: File, packageName: String): File? {
        return try {
            ZipFile(apkFile).use { zip ->
                val entries = zip.entries().asSequence().toList()
                val htmlIndexEntry = entries.firstOrNull {
                    val name = it.name.lowercase()
                    name == "assets/index.html" ||
                            name == "assets/www/index.html" ||
                            name == "assets/game/index.html" ||
                            name == "assets/web/index.html" ||
                            (name.startsWith("assets/") && name.endsWith("index.html"))
                } ?: return null

                val targetOutDir = File(runtimeDir, packageName.replace("[^a-zA-Z0-9._-]".toRegex(), "_"))
                if (targetOutDir.exists()) targetOutDir.deleteRecursively()
                targetOutDir.mkdirs()

                for (entry in entries) {
                    if (!entry.name.startsWith("assets/") || entry.isDirectory) continue
                    val relPath = entry.name.removePrefix("assets/")
                    val outFile = File(targetOutDir, relPath)
                    // Prevent zip slip
                    if (!outFile.canonicalPath.startsWith(targetOutDir.canonicalPath)) continue
                    outFile.parentFile?.mkdirs()
                    zip.getInputStream(entry).use { input ->
                        FileOutputStream(outFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                }

                val relIndex = htmlIndexEntry.name.removePrefix("assets/")
                val finalIndexFile = File(targetOutDir, relIndex)
                if (finalIndexFile.exists()) finalIndexFile else null
            }
        } catch (_: Exception) {
            null
        }
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
