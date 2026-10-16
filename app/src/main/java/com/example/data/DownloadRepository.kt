package com.example.data

import android.app.DownloadManager
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Environment
import android.util.Log
import com.example.data.models.DownloadItem
import com.example.data.models.DownloadStatus
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class DownloadRepository(private val context: Context) {

    private val prefs = context.getSharedPreferences("xeno_downloads_prefs", Context.MODE_PRIVATE)

    fun getTrackedDownloads(): List<DownloadItem> {
        val list = mutableListOf<DownloadItem>()

        // 1. Query system DownloadManager for actual downloaded files
        try {
            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
            if (dm != null) {
                val query = DownloadManager.Query()
                dm.query(query)?.use { cursor ->
                    val idIdx = cursor.getColumnIndex(DownloadManager.COLUMN_ID)
                    val titleIdx = cursor.getColumnIndex(DownloadManager.COLUMN_TITLE)
                    val uriIdx = cursor.getColumnIndex(DownloadManager.COLUMN_URI)
                    val localUriIdx = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)
                    val statusIdx = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                    val totalSizeIdx = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                    val downloadedIdx = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                    val mimeIdx = cursor.getColumnIndex(DownloadManager.COLUMN_MEDIA_TYPE)

                    while (cursor.moveToNext()) {
                        val dmId = if (idIdx != -1) cursor.getLong(idIdx) else -1L
                        val title = if (titleIdx != -1) cursor.getString(titleIdx) ?: "Download" else "Download"
                        val url = if (uriIdx != -1) cursor.getString(uriIdx) ?: "" else ""
                        val localUriStr = if (localUriIdx != -1) cursor.getString(localUriIdx) else null
                        val statusInt = if (statusIdx != -1) cursor.getInt(statusIdx) else DownloadManager.STATUS_SUCCESSFUL
                        val totalSize = if (totalSizeIdx != -1) cursor.getLong(totalSizeIdx) else 0L
                        val downloaded = if (downloadedIdx != -1) cursor.getLong(downloadedIdx) else totalSize
                        val mimeType = if (mimeIdx != -1) cursor.getString(mimeIdx) ?: "" else ""

                        val status = when (statusInt) {
                            DownloadManager.STATUS_SUCCESSFUL -> DownloadStatus.COMPLETED
                            DownloadManager.STATUS_RUNNING, DownloadManager.STATUS_PENDING, DownloadManager.STATUS_PAUSED -> DownloadStatus.DOWNLOADING
                            DownloadManager.STATUS_FAILED -> DownloadStatus.FAILED
                            else -> DownloadStatus.COMPLETED
                        }

                        val localPath = localUriStr?.let { Uri.parse(it).path }

                        list.add(
                            DownloadItem(
                                id = dmId,
                                downloadManagerId = dmId,
                                url = url,
                                fileName = title,
                                filePath = localPath,
                                mimeType = mimeType,
                                fileSize = totalSize,
                                bytesDownloaded = downloaded,
                                status = status,
                                timestamp = System.currentTimeMillis()
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.w("DownloadRepository", "Error querying DownloadManager: ${e.message}")
        }

        // 2. Combine with saved local downloads history
        val savedStr = prefs.getString("saved_downloads_history", null)
        if (!savedStr.isNullOrBlank()) {
            try {
                val jsonArray = JSONArray(savedStr)
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    val dmId = obj.optLong("downloadManagerId", -1L)
                    if (list.none { it.downloadManagerId == dmId && dmId != -1L }) {
                        list.add(
                            DownloadItem(
                                id = obj.optLong("id", System.currentTimeMillis()),
                                downloadManagerId = dmId,
                                url = obj.optString("url", ""),
                                fileName = obj.optString("fileName", "File"),
                                filePath = obj.optString("filePath", null),
                                mimeType = obj.optString("mimeType", ""),
                                fileSize = obj.optLong("fileSize", 0L),
                                bytesDownloaded = obj.optLong("bytesDownloaded", 0L),
                                status = try {
                                    DownloadStatus.valueOf(obj.optString("status", "COMPLETED"))
                                } catch (_: Exception) {
                                    DownloadStatus.COMPLETED
                                },
                                timestamp = obj.optLong("timestamp", System.currentTimeMillis())
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e("DownloadRepository", "Error parsing saved downloads history", e)
            }
        }

        // 3. Scan Public Downloads folder for existing files if list is empty
        if (list.isEmpty()) {
            try {
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (downloadsDir.exists() && downloadsDir.isDirectory) {
                    downloadsDir.listFiles()?.take(20)?.forEach { f ->
                        if (f.isFile) {
                            list.add(
                                DownloadItem(
                                    id = f.name.hashCode().toLong(),
                                    url = f.absolutePath,
                                    fileName = f.name,
                                    filePath = f.absolutePath,
                                    mimeType = getMimeTypeFromExtension(f.extension),
                                    fileSize = f.length(),
                                    bytesDownloaded = f.length(),
                                    status = DownloadStatus.COMPLETED,
                                    timestamp = f.lastModified()
                                )
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w("DownloadRepository", "Error scanning downloads folder", e)
            }
        }

        return list.sortedByDescending { it.timestamp }
    }

    fun addDownloadItem(item: DownloadItem) {
        val current = getTrackedDownloads().toMutableList()
        current.removeAll { it.id == item.id || (it.downloadManagerId == item.downloadManagerId && item.downloadManagerId != -1L) }
        current.add(0, item)
        saveHistory(current)
    }

    fun deleteDownloadItem(item: DownloadItem) {
        val current = getTrackedDownloads().toMutableList()
        current.removeAll { it.id == item.id }
        saveHistory(current)

        // Try deleting file from filesystem
        try {
            item.filePath?.let { path ->
                val f = File(path)
                if (f.exists()) f.delete()
            }
        } catch (_: Exception) {}

        // Try removing from DownloadManager
        if (item.downloadManagerId != -1L) {
            try {
                val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
                dm?.remove(item.downloadManagerId)
            } catch (_: Exception) {}
        }
    }

    fun clearAllDownloads() {
        prefs.edit().remove("saved_downloads_history").apply()
    }

    private fun saveHistory(list: List<DownloadItem>) {
        try {
            val jsonArray = JSONArray()
            list.take(100).forEach { item ->
                val obj = JSONObject().apply {
                    put("id", item.id)
                    put("downloadManagerId", item.downloadManagerId)
                    put("url", item.url)
                    put("fileName", item.fileName)
                    put("filePath", item.filePath ?: "")
                    put("mimeType", item.mimeType)
                    put("fileSize", item.fileSize)
                    put("bytesDownloaded", item.bytesDownloaded)
                    put("status", item.status.name)
                    put("timestamp", item.timestamp)
                }
                jsonArray.put(obj)
            }
            prefs.edit().putString("saved_downloads_history", jsonArray.toString()).apply()
        } catch (e: Exception) {
            Log.e("DownloadRepository", "Error saving downloads history", e)
        }
    }

    private fun getMimeTypeFromExtension(ext: String): String {
        return when (ext.lowercase()) {
            "mp4", "mkv", "webm", "avi" -> "video/*"
            "jpg", "jpeg", "png", "gif", "webp" -> "image/*"
            "pdf" -> "application/pdf"
            "zip", "rar", "7z", "tar", "gz" -> "application/zip"
            "mp3", "m4a", "wav", "aac" -> "audio/*"
            "apk" -> "application/vnd.android.package-archive"
            else -> "*/*"
        }
    }
}
