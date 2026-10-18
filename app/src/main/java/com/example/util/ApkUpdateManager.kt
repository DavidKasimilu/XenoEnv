package com.example.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

data class ApkPackageDetails(
    val packageName: String,
    val appName: String,
    val versionName: String,
    val versionCode: Long,
    val isThisAppUpdate: Boolean,
    val currentVersionName: String,
    val currentVersionCode: Long,
    val safeFileUri: Uri,
    val fileSizeFormatted: String
)

object ApkUpdateManager {

    fun canInstallUnknownPackages(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    fun openInstallPermissionSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (_: Exception) {
                val intent = Intent(Settings.ACTION_SECURITY_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            }
        }
    }

    fun getCurrentAppVersionInfo(context: Context): Pair<String, Long> {
        return try {
            val pInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
            val vName = pInfo.versionName ?: "1.0"
            val vCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                pInfo.versionCode.toLong()
            }
            Pair(vName, vCode)
        } catch (_: Exception) {
            Pair("1.0", 1L)
        }
    }

    fun inspectApk(context: Context, sourceUri: Uri): ApkPackageDetails? {
        return try {
            val localApkFile: File = if (sourceUri.scheme == "file") {
                File(sourceUri.path ?: return null)
            } else {
                // Copy stream to cache for inspection and safe FileProvider URI
                val cacheFile = File(context.cacheDir, "update_installer.apk")
                context.contentResolver.openInputStream(sourceUri)?.use { input ->
                    FileOutputStream(cacheFile).use { output ->
                        input.copyTo(output)
                    }
                }
                cacheFile
            }

            if (!localApkFile.exists() || localApkFile.length() == 0L) return null

            val pm = context.packageManager
            val archiveInfo: PackageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageArchiveInfo(localApkFile.absolutePath, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageArchiveInfo(localApkFile.absolutePath, 0)
            } ?: return null

            val apkPkgName = archiveInfo.packageName ?: return null
            val apkVerName = archiveInfo.versionName ?: "1.0"
            val apkVerCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                archiveInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                archiveInfo.versionCode.toLong()
            }

            // Extract App Label if possible
            archiveInfo.applicationInfo?.sourceDir = localApkFile.absolutePath
            archiveInfo.applicationInfo?.publicSourceDir = localApkFile.absolutePath
            val appLabel = archiveInfo.applicationInfo?.loadLabel(pm)?.toString()
                ?: if (apkPkgName == context.packageName) "Xeno Update" else apkPkgName

            val (currVerName, currVerCode) = getCurrentAppVersionInfo(context)
            val isSameApp = apkPkgName == context.packageName

            val safeUri = try {
                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", localApkFile)
            } catch (_: Exception) {
                Uri.fromFile(localApkFile)
            }

            val sizeKb = localApkFile.length() / 1024.0
            val sizeMb = sizeKb / 1024.0
            val sizeFormatted = if (sizeMb >= 1.0) String.format("%.1f MB", sizeMb) else String.format("%.0f KB", sizeKb)

            ApkPackageDetails(
                packageName = apkPkgName,
                appName = appLabel,
                versionName = apkVerName,
                versionCode = apkVerCode,
                isThisAppUpdate = isSameApp,
                currentVersionName = currVerName,
                currentVersionCode = currVerCode,
                safeFileUri = safeUri,
                fileSizeFormatted = sizeFormatted
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun launchPackageInstaller(context: Context, safeUri: Uri): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(safeUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            try {
                @Suppress("DEPRECATION")
                val intent = Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
                    data = safeUri
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
                }
                context.startActivity(intent)
                true
            } catch (_: Exception) {
                false
            }
        }
    }
}
