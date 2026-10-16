package com.example.data

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.data.models.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class GalleryRepository(private val context: Context) {

    private val contentResolver: ContentResolver = context.contentResolver
    private val prefs: SharedPreferences = context.getSharedPreferences("xeno_gallery_prefs", Context.MODE_PRIVATE)
    private val customMediaItems = mutableListOf<MediaItem>()

    private val deletedMediaIds: MutableSet<Long> = loadDeletedIds()
    private val deletedMediaUris: MutableSet<String> = loadDeletedUris()
    private val deletedMediaNames: MutableSet<String> = loadDeletedNames()

    private fun loadDeletedIds(): MutableSet<Long> {
        val savedStr = prefs.getString("deleted_ids", null)
        val defaultIds = setOf(
            9001L, 9002L, 9003L, 9004L, 9005L,
            8001L, 8002L, 8003L, 8004L, 8005L, 8006L
        )
        if (savedStr.isNullOrBlank()) {
            return defaultIds.toMutableSet()
        }
        val set = savedStr.split(",").mapNotNull { it.trim().toLongOrNull() }.toMutableSet()
        set.addAll(defaultIds)
        return set
    }

    private fun loadDeletedUris(): MutableSet<String> {
        val saved = prefs.getStringSet("deleted_uris_set", null)
        return saved?.toMutableSet() ?: mutableSetOf()
    }

    private fun loadDeletedNames(): MutableSet<String> {
        val saved = prefs.getStringSet("deleted_names_set", null)
        return saved?.toMutableSet() ?: mutableSetOf()
    }

    private fun saveDeletedIds() {
        prefs.edit().putString("deleted_ids", deletedMediaIds.joinToString(",")).apply()
    }

    private fun saveDeletedUris() {
        prefs.edit().putStringSet("deleted_uris_set", deletedMediaUris).apply()
    }

    private fun saveDeletedNames() {
        prefs.edit().putStringSet("deleted_names_set", deletedMediaNames).apply()
    }

    private fun isDemoItem(item: MediaItem): Boolean {
        val uriStr = item.uri.toString()
        return item.id in 8000L..9999L ||
                uriStr.contains("commondatastorage.googleapis.com") ||
                uriStr.contains("images.unsplash.com")
    }

    fun addCustomMediaItem(item: MediaItem) {
        if (!isDemoItem(item) &&
            item.id !in deletedMediaIds &&
            item.uri.toString() !in deletedMediaUris &&
            item.displayName !in deletedMediaNames &&
            customMediaItems.none { it.id == item.id || it.uri == item.uri }
        ) {
            customMediaItems.add(0, item)
        }
    }

    suspend fun deleteMediaItem(item: MediaItem): Boolean = withContext(Dispatchers.IO) {
        // Track ID, URI string, and DisplayName permanently
        deletedMediaIds.add(item.id)
        deletedMediaUris.add(item.uri.toString())
        deletedMediaNames.add(item.displayName)
        saveDeletedIds()
        saveDeletedUris()
        saveDeletedNames()

        customMediaItems.removeAll {
            it.id == item.id || it.uri == item.uri || it.displayName == item.displayName
        }

        var success = false

        // 1. Try deleting via ContentResolver
        try {
            val rows = contentResolver.delete(item.uri, null, null)
            if (rows > 0) success = true
        } catch (e: Exception) {
            Log.w("GalleryRepository", "contentResolver.delete failed for ${item.uri}: ${e.message}")
        }

        // 2. Try deleting via DocumentsContract if applicable
        if (!success) {
            try {
                if (DocumentsContract.isDocumentUri(context, item.uri)) {
                    success = DocumentsContract.deleteDocument(contentResolver, item.uri)
                }
            } catch (e: Exception) {
                Log.w("GalleryRepository", "deleteDocument failed for ${item.uri}: ${e.message}")
            }
        }

        // 3. Try resolving filesystem path via MediaStore DATA column and deleting local file
        try {
            contentResolver.query(item.uri, arrayOf(MediaStore.MediaColumns.DATA), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val dataIdx = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
                    if (dataIdx != -1) {
                        val path = cursor.getString(dataIdx)
                        if (!path.isNullOrBlank()) {
                            val f = File(path)
                            if (f.exists()) {
                                if (f.delete()) success = true
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.d("GalleryRepository", "DATA column resolution failed: ${e.message}")
        }

        // 4. Try direct file path if available
        try {
            val path = item.uri.path
            if (path != null) {
                val f = File(path)
                if (f.exists()) {
                    if (f.delete()) success = true
                }
            }
        } catch (_: Exception) {}

        true
    }

    fun deleteDemoMedia() {
        customMediaItems.removeAll { isDemoItem(it) }
        deletedMediaIds.addAll(listOf(9001L, 9002L, 9003L, 9004L, 9005L, 8001L, 8002L, 8003L, 8004L, 8005L, 8006L))
        saveDeletedIds()
    }

    fun addSampleVideos() {
        // Demo videos removed per user request
    }

    fun addSamplePhotos() {
        // Demo photos removed per user request
    }

    suspend fun importMediaUri(uri: Uri, isVideo: Boolean): MediaItem = withContext(Dispatchers.IO) {
        var displayName = if (isVideo) "Video_${System.currentTimeMillis() % 10000}.mp4" else "Photo_${System.currentTimeMillis() % 10000}.jpg"
        var size = 0L
        var durationMs: Long? = if (isVideo) 0L else null
        var width = 0
        var height = 0

        try {
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (cursor.moveToFirst()) {
                    if (nameIdx != -1) {
                        val name = cursor.getString(nameIdx)
                        if (!name.isNullOrBlank()) displayName = name
                    }
                    if (sizeIdx != -1) {
                        size = cursor.getLong(sizeIdx)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("GalleryRepository", "Error resolving metadata from URI", e)
        }

        if (isVideo) {
            var retriever: MediaMetadataRetriever? = null
            try {
                retriever = MediaMetadataRetriever()
                retriever.setDataSource(context, uri)
                val durStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                if (durStr != null) {
                    durationMs = durStr.toLongOrNull() ?: 0L
                }
                val wStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                val hStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                width = wStr?.toIntOrNull() ?: 0
                height = hStr?.toIntOrNull() ?: 0
            } catch (e: Exception) {
                Log.e("GalleryRepository", "Error retrieving video metadata", e)
            } finally {
                try {
                    retriever?.release()
                } catch (_: Exception) {}
            }
        }

        var finalUri = uri
        try {
            val mediaDir = File(context.filesDir, if (isVideo) "gallery_videos" else "gallery_photos").apply { mkdirs() }
            val localFile = File(mediaDir, displayName)
            contentResolver.openInputStream(uri)?.use { input ->
                localFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            if (localFile.exists() && localFile.length() > 0) {
                finalUri = Uri.fromFile(localFile)
                size = localFile.length()
            }
        } catch (e: Exception) {
            Log.w("GalleryRepository", "Error caching picked media file locally: ${e.message}")
        }

        var folderName = if (isVideo) "Videos" else "Camera"
        val pathStr = uri.path ?: uri.toString()
        when {
            pathStr.contains("WhatsApp", ignoreCase = true) -> folderName = if (isVideo) "WhatsApp Video" else "WhatsApp Images"
            pathStr.contains("Download", ignoreCase = true) -> folderName = "Downloads"
            pathStr.contains("Screenshot", ignoreCase = true) -> folderName = "Screenshots"
            pathStr.contains("Camera", ignoreCase = true) || pathStr.contains("DCIM", ignoreCase = true) -> folderName = "Camera"
            pathStr.contains("Movie", ignoreCase = true) -> folderName = "Movies"
            displayName.startsWith("Video_", ignoreCase = true) -> folderName = "Imported Videos"
            displayName.startsWith("Photo_", ignoreCase = true) -> folderName = "Camera"
        }

        val item = MediaItem(
            id = System.currentTimeMillis(),
            uri = finalUri,
            displayName = displayName,
            dateAdded = System.currentTimeMillis() / 1000,
            size = size,
            durationMs = durationMs,
            isVideo = isVideo,
            width = width,
            height = height,
            folderName = folderName
        )
        addCustomMediaItem(item)
        item
    }

    suspend fun getMediaItems(): List<MediaItem> = withContext(Dispatchers.IO) {
        val hasPermission = checkMediaPermission()
        val deviceItems = mutableListOf<MediaItem>()

        if (hasPermission) {
            // 1. Query Images from MediaStore
            try {
                val imageProjection = arrayOf(
                    MediaStore.Images.Media._ID,
                    MediaStore.Images.Media.DISPLAY_NAME,
                    MediaStore.Images.Media.DATE_ADDED,
                    MediaStore.Images.Media.SIZE,
                    MediaStore.Images.Media.WIDTH,
                    MediaStore.Images.Media.HEIGHT,
                    MediaStore.Images.Media.BUCKET_DISPLAY_NAME
                )
                contentResolver.query(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    imageProjection,
                    null,
                    null,
                    "${MediaStore.Images.Media.DATE_ADDED} DESC"
                )?.use { cursor ->
                    val idIdx = cursor.getColumnIndex(MediaStore.Images.Media._ID)
                    val nameIdx = cursor.getColumnIndex(MediaStore.Images.Media.DISPLAY_NAME)
                    val dateIdx = cursor.getColumnIndex(MediaStore.Images.Media.DATE_ADDED)
                    val sizeIdx = cursor.getColumnIndex(MediaStore.Images.Media.SIZE)
                    val widthIdx = cursor.getColumnIndex(MediaStore.Images.Media.WIDTH)
                    val heightIdx = cursor.getColumnIndex(MediaStore.Images.Media.HEIGHT)
                    val bucketIdx = cursor.getColumnIndex(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)

                    while (cursor.moveToNext() && deviceItems.size < 200) {
                        val id = if (idIdx != -1) cursor.getLong(idIdx) else 0L
                        val name = if (nameIdx != -1) cursor.getString(nameIdx) ?: "Photo" else "Photo"
                        val date = if (dateIdx != -1) cursor.getLong(dateIdx) else System.currentTimeMillis() / 1000
                        val size = if (sizeIdx != -1) cursor.getLong(sizeIdx) else 0L
                        val width = if (widthIdx != -1) cursor.getInt(widthIdx) else 0
                        val height = if (heightIdx != -1) cursor.getInt(heightIdx) else 0
                        val bucket = if (bucketIdx != -1) cursor.getString(bucketIdx) else null
                        val folder = if (!bucket.isNullOrBlank()) bucket else "Camera"

                        val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                        deviceItems.add(
                            MediaItem(
                                id = id,
                                uri = uri,
                                displayName = name,
                                dateAdded = date,
                                size = size,
                                isVideo = false,
                                width = width,
                                height = height,
                                folderName = folder
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e("GalleryRepository", "Error querying images", e)
            }

            // 2. Query Videos from MediaStore
            try {
                val videoProjection = arrayOf(
                    MediaStore.Video.Media._ID,
                    MediaStore.Video.Media.DISPLAY_NAME,
                    MediaStore.Video.Media.DATE_ADDED,
                    MediaStore.Video.Media.SIZE,
                    MediaStore.Video.Media.DURATION,
                    MediaStore.Video.Media.WIDTH,
                    MediaStore.Video.Media.HEIGHT,
                    MediaStore.Video.Media.BUCKET_DISPLAY_NAME,
                    MediaStore.Video.Media.DATA
                )
                contentResolver.query(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    videoProjection,
                    null,
                    null,
                    "${MediaStore.Video.Media.DATE_ADDED} DESC"
                )?.use { cursor ->
                    val idIdx = cursor.getColumnIndex(MediaStore.Video.Media._ID)
                    val nameIdx = cursor.getColumnIndex(MediaStore.Video.Media.DISPLAY_NAME)
                    val dateIdx = cursor.getColumnIndex(MediaStore.Video.Media.DATE_ADDED)
                    val sizeIdx = cursor.getColumnIndex(MediaStore.Video.Media.SIZE)
                    val durIdx = cursor.getColumnIndex(MediaStore.Video.Media.DURATION)
                    val widthIdx = cursor.getColumnIndex(MediaStore.Video.Media.WIDTH)
                    val heightIdx = cursor.getColumnIndex(MediaStore.Video.Media.HEIGHT)
                    val bucketIdx = cursor.getColumnIndex(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)
                    val dataIdx = cursor.getColumnIndex(MediaStore.Video.Media.DATA)

                    while (cursor.moveToNext()) {
                        val id = if (idIdx != -1) cursor.getLong(idIdx) else 0L
                        val name = if (nameIdx != -1) cursor.getString(nameIdx) ?: "Video" else "Video"
                        val date = if (dateIdx != -1) cursor.getLong(dateIdx) else System.currentTimeMillis() / 1000
                        val size = if (sizeIdx != -1) cursor.getLong(sizeIdx) else 0L
                        val dur = if (durIdx != -1) cursor.getLong(durIdx) else 0L
                        val width = if (widthIdx != -1) cursor.getInt(widthIdx) else 0
                        val height = if (heightIdx != -1) cursor.getInt(heightIdx) else 0
                        val bucket = if (bucketIdx != -1) cursor.getString(bucketIdx) else null
                        val rawPath = if (dataIdx != -1) cursor.getString(dataIdx) else null
                        val relPathIdx = try { cursor.getColumnIndex("relative_path") } catch (_: Exception) { -1 }
                        val relPath = if (relPathIdx != -1) {
                            try { cursor.getString(relPathIdx) } catch (_: Exception) { null }
                        } else null
                        val folderFromRel = relPath?.trim('/')?.split('/')?.lastOrNull()?.takeIf { it.isNotBlank() }
                        val folder = when {
                            !bucket.isNullOrBlank() -> bucket
                            folderFromRel != null -> folderFromRel
                            rawPath != null -> File(rawPath).parentFile?.name?.ifBlank { "Videos" } ?: "Videos"
                            else -> "Videos"
                        }

                        val uri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
                        deviceItems.add(
                            MediaItem(
                                id = id + 1_000_000,
                                uri = uri,
                                displayName = name,
                                dateAdded = date,
                                size = size,
                                durationMs = dur,
                                isVideo = true,
                                width = width,
                                height = height,
                                folderName = folder
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e("GalleryRepository", "Error querying videos", e)
            }
        }

        // 3. Scan Public Downloads directory for downloaded videos & photos
        try {
            val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
            if (downloadsDir.exists() && downloadsDir.isDirectory) {
                downloadsDir.listFiles()?.forEach { file ->
                    if (file.isFile) {
                        val name = file.name
                        val ext = file.extension.lowercase()
                        val isVideo = listOf("mp4", "mkv", "webm", "avi", "3gp", "mov", "ts").contains(ext)
                        val isImage = listOf("jpg", "jpeg", "png", "webp", "gif").contains(ext)
                        if (isVideo || isImage) {
                            val uri = Uri.fromFile(file)
                            deviceItems.add(
                                MediaItem(
                                    id = file.absolutePath.hashCode().toLong(),
                                    uri = uri,
                                    displayName = name,
                                    dateAdded = file.lastModified() / 1000,
                                    size = file.length(),
                                    durationMs = if (isVideo) 0L else null,
                                    isVideo = isVideo,
                                    width = 0,
                                    height = 0,
                                    folderName = "Downloads"
                                )
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w("GalleryRepository", "Error scanning downloads folder: ${e.message}")
        }

        // 4. Scan App-Specific Downloads directory
        try {
            val appDlDir = context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS)
            if (appDlDir != null && appDlDir.exists() && appDlDir.isDirectory) {
                appDlDir.listFiles()?.forEach { file ->
                    if (file.isFile) {
                        val name = file.name
                        val ext = file.extension.lowercase()
                        val isVideo = listOf("mp4", "mkv", "webm", "avi", "3gp", "mov", "ts").contains(ext)
                        val isImage = listOf("jpg", "jpeg", "png", "webp", "gif").contains(ext)
                        if (isVideo || isImage) {
                            val uri = Uri.fromFile(file)
                            deviceItems.add(
                                MediaItem(
                                    id = file.absolutePath.hashCode().toLong(),
                                    uri = uri,
                                    displayName = name,
                                    dateAdded = file.lastModified() / 1000,
                                    size = file.length(),
                                    durationMs = if (isVideo) 0L else null,
                                    isVideo = isVideo,
                                    width = 0,
                                    height = 0,
                                    folderName = "Downloads"
                                )
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w("GalleryRepository", "Error scanning app downloads: ${e.message}")
        }

        // 5. Scan internal gallery folders (cached / imported videos and photos)
        try {
            val vidDir = File(context.filesDir, "gallery_videos")
            if (vidDir.exists() && vidDir.isDirectory) {
                vidDir.listFiles()?.forEach { file ->
                    if (file.isFile && file.length() > 0) {
                        deviceItems.add(
                            MediaItem(
                                id = file.absolutePath.hashCode().toLong(),
                                uri = Uri.fromFile(file),
                                displayName = file.name,
                                dateAdded = file.lastModified() / 1000,
                                size = file.length(),
                                durationMs = 0L,
                                isVideo = true,
                                width = 0,
                                height = 0,
                                folderName = "Videos"
                            )
                        )
                    }
                }
            }
            val photoDir = File(context.filesDir, "gallery_photos")
            if (photoDir.exists() && photoDir.isDirectory) {
                photoDir.listFiles()?.forEach { file ->
                    if (file.isFile && file.length() > 0) {
                        deviceItems.add(
                            MediaItem(
                                id = file.absolutePath.hashCode().toLong(),
                                uri = Uri.fromFile(file),
                                displayName = file.name,
                                dateAdded = file.lastModified() / 1000,
                                size = file.length(),
                                isVideo = false,
                                width = 0,
                                height = 0,
                                folderName = "Camera"
                            )
                        )
                    }
                }
            }
        } catch (_: Exception) {}

        // 6. Also scan tracked downloads from DownloadRepository
        try {
            val downloadRepo = DownloadRepository(context)
            val tracked = downloadRepo.getTrackedDownloads()
            for (dl in tracked) {
                val isVideo = dl.mimeType.contains("video", ignoreCase = true) ||
                        listOf("mp4", "mkv", "webm", "avi", "3gp", "mov").any { dl.fileName.lowercase().endsWith(".$it") }
                val isImage = dl.mimeType.contains("image", ignoreCase = true) ||
                        listOf("jpg", "jpeg", "png", "webp", "gif").any { dl.fileName.lowercase().endsWith(".$it") }
                if (isVideo || isImage) {
                    val file = dl.filePath?.let { File(it) }
                    val uri = if (file != null && file.exists()) Uri.fromFile(file) else Uri.parse(dl.url)
                    deviceItems.add(
                        MediaItem(
                            id = dl.id,
                            uri = uri,
                            displayName = dl.fileName,
                            dateAdded = dl.timestamp / 1000,
                            size = dl.fileSize,
                            durationMs = if (isVideo) 0L else null,
                            isVideo = isVideo,
                            width = 0,
                            height = 0,
                            folderName = "Downloads"
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.w("GalleryRepository", "Error scanning tracked downloads: ${e.message}")
        }

        // Combine custom picked media with device items (excluding demo items and deleted items)
        val combined = (customMediaItems + deviceItems)
            .filter {
                !isDemoItem(it) &&
                it.id !in deletedMediaIds &&
                it.uri.toString() !in deletedMediaUris &&
                it.displayName !in deletedMediaNames
            }
            .distinctBy { it.id }
            .toMutableList()

        combined.sortedByDescending { it.dateAdded }
    }

    private fun checkMediaPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val img = ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED
            val vid = ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED
            val partial = if (Build.VERSION.SDK_INT >= 34) {
                ContextCompat.checkSelfPermission(context, "android.permission.READ_MEDIA_VISUAL_USER_SELECTED") == PackageManager.PERMISSION_GRANTED
            } else false
            img || vid || partial
        } else {
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
    }
}
