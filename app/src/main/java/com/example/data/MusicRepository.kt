package com.example.data

import android.content.ContentUris
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

data class MusicTrackItem(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val folderName: String,
    val durationMs: Long,
    val sizeBytes: Long,
    val uriString: String,
    val filePath: String? = null,
    val dateModified: Long = 0L,
    val isFavorite: Boolean = false
)

class MusicRepository(private val context: Context) {

    private val prefs = context.getSharedPreferences("xeno_music_favorites_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_FAVORITE_IDS = "favorite_track_ids"
        private val SUPPORTED_AUDIO_EXTENSIONS = setOf("mp3", "m4a", "aac", "wav", "flac", "ogg", "opus")
    }

    fun getFavoriteTrackIds(): Set<String> {
        return prefs.getStringSet(KEY_FAVORITE_IDS, emptySet()) ?: emptySet()
    }

    fun toggleFavorite(trackId: String): Set<String> {
        val current = getFavoriteTrackIds().toMutableSet()
        if (current.contains(trackId)) {
            current.remove(trackId)
        } else {
            current.add(trackId)
        }
        prefs.edit().putStringSet(KEY_FAVORITE_IDS, current).apply()
        return current
    }

    suspend fun scanDeviceMp3Files(): List<MusicTrackItem> = withContext(Dispatchers.IO) {
        val favorites = getFavoriteTrackIds()
        val resultsByKey = LinkedHashMap<String, MusicTrackItem>()

        // 1. Query MediaStore.Audio.Media for all MP3 and music files on the device
        try {
            val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            } else {
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            }

            val projection = mutableListOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.DISPLAY_NAME,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.SIZE,
                MediaStore.Audio.Media.DATA,
                MediaStore.Audio.Media.DATE_MODIFIED,
                MediaStore.Audio.Media.MIME_TYPE
            ).apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    add(MediaStore.Audio.Media.RELATIVE_PATH)
                }
            }.toTypedArray()

            val sortOrder = "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"

            context.contentResolver.query(
                collection,
                projection,
                null,
                null,
                sortOrder
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = cursor.getColumnIndex(MediaStore.Audio.Media.TITLE)
                val nameCol = cursor.getColumnIndex(MediaStore.Audio.Media.DISPLAY_NAME)
                val artistCol = cursor.getColumnIndex(MediaStore.Audio.Media.ARTIST)
                val albumCol = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM)
                val durationCol = cursor.getColumnIndex(MediaStore.Audio.Media.DURATION)
                val sizeCol = cursor.getColumnIndex(MediaStore.Audio.Media.SIZE)
                val dataCol = cursor.getColumnIndex(MediaStore.Audio.Media.DATA)
                val dateCol = cursor.getColumnIndex(MediaStore.Audio.Media.DATE_MODIFIED)
                val mimeCol = cursor.getColumnIndex(MediaStore.Audio.Media.MIME_TYPE)
                val relPathCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    cursor.getColumnIndex(MediaStore.Audio.Media.RELATIVE_PATH)
                } else -1

                while (cursor.moveToNext()) {
                    val mediaId = cursor.getLong(idCol)
                    val displayName = if (nameCol >= 0) cursor.getString(nameCol) ?: "" else ""
                    val rawTitle = if (titleCol >= 0) cursor.getString(titleCol) ?: "" else ""
                    val rawArtist = if (artistCol >= 0) cursor.getString(artistCol) ?: "" else ""
                    val rawAlbum = if (albumCol >= 0) cursor.getString(albumCol) ?: "" else ""
                    val durationMs = if (durationCol >= 0) cursor.getLong(durationCol) else 0L
                    val sizeBytes = if (sizeCol >= 0) cursor.getLong(sizeCol) else 0L
                    val filePath = if (dataCol >= 0) cursor.getString(dataCol) else null
                    val dateMod = if (dateCol >= 0) cursor.getLong(dateCol) * 1000L else 0L
                    val mimeType = if (mimeCol >= 0) cursor.getString(mimeCol) ?: "" else ""
                    val relPath = if (relPathCol >= 0) cursor.getString(relPathCol) ?: "" else ""

                    val ext = displayName.substringAfterLast('.', "").lowercase()
                        .ifBlank { filePath?.substringAfterLast('.', "")?.lowercase() ?: "" }

                    val isAudioFile = ext in SUPPORTED_AUDIO_EXTENSIONS ||
                            mimeType.startsWith("audio/", ignoreCase = true)
                    if (!isAudioFile) continue
                    if (sizeBytes <= 0L && filePath.isNullOrBlank()) continue

                    val contentUri = ContentUris.withAppendedId(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        mediaId
                    )

                    val cleanTitle = when {
                        rawTitle.isNotBlank() && !rawTitle.startsWith("<unknown", ignoreCase = true) -> rawTitle
                        displayName.isNotBlank() -> displayName.substringBeforeLast('.')
                        !filePath.isNullOrBlank() -> File(filePath).nameWithoutExtension
                        else -> "Audio Track $mediaId"
                    }

                    val cleanArtist = when {
                        rawArtist.isNotBlank() && !rawArtist.contains("<unknown>", ignoreCase = true) -> rawArtist
                        else -> "Local MP3"
                    }

                    val folderName = when {
                        relPath.isNotBlank() -> relPath.trim('/').substringAfterLast('/', "Music")
                        !filePath.isNullOrBlank() -> File(filePath).parentFile?.name ?: "Music"
                        else -> "Music"
                    }

                    val canonicalKey = if (!filePath.isNullOrBlank()) {
                        try {
                            File(filePath).canonicalPath.lowercase()
                        } catch (_: Exception) {
                            filePath.lowercase()
                        }
                    } else {
                        contentUri.toString()
                    }

                    val trackId = canonicalKey
                    resultsByKey[canonicalKey] = MusicTrackItem(
                        id = trackId,
                        title = cleanTitle,
                        artist = cleanArtist,
                        album = rawAlbum.takeIf { !it.contains("<unknown>", ignoreCase = true) } ?: folderName,
                        folderName = folderName,
                        durationMs = durationMs,
                        sizeBytes = sizeBytes,
                        uriString = contentUri.toString(),
                        filePath = filePath,
                        dateModified = dateMod,
                        isFavorite = favorites.contains(trackId)
                    )
                }
            }
        } catch (_: Exception) {
        }

        // 2. Also scan common device directories directly for .mp3 / audio files not yet indexed in MediaStore
        val candidateDirs = linkedSetOf<File>()
        try {
            Environment.getExternalStorageDirectory()?.let { root ->
                candidateDirs.add(File(root, "Music"))
                candidateDirs.add(File(root, "Download"))
                candidateDirs.add(File(root, "Downloads"))
                candidateDirs.add(File(root, "Podcasts"))
                candidateDirs.add(File(root, "Audio"))
                candidateDirs.add(File(root, "Ringtones"))
            }
        } catch (_: Exception) {}

        listOf(
            Environment.DIRECTORY_MUSIC,
            Environment.DIRECTORY_DOWNLOADS,
            Environment.DIRECTORY_PODCASTS,
            Environment.DIRECTORY_RINGTONES,
            Environment.DIRECTORY_NOTIFICATIONS
        ).forEach { dirType ->
            try {
                Environment.getExternalStoragePublicDirectory(dirType)?.let { candidateDirs.add(it) }
            } catch (_: Exception) {}
        }

        try {
            context.getExternalFilesDir("clone_space_music")?.let { candidateDirs.add(it) }
            context.getExternalFilesDir(Environment.DIRECTORY_MUSIC)?.let { candidateDirs.add(it) }
            context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)?.let { candidateDirs.add(it) }
        } catch (_: Exception) {}

        fun scanDirRecursively(dir: File, depth: Int) {
            if (depth > 4 || !dir.exists() || !dir.isDirectory || !dir.canRead()) return
            val children = try {
                dir.listFiles()
            } catch (_: Exception) {
                null
            } ?: return

            for (child in children) {
                if (child.name.startsWith(".")) continue
                if (child.isDirectory) {
                    val lowerName = child.name.lowercase()
                    if (lowerName != "android" && lowerName != "cache" && lowerName != "thumbnails") {
                        scanDirRecursively(child, depth + 1)
                    }
                } else if (child.isFile && child.length() > 512L) {
                    val ext = child.extension.lowercase()
                    if (ext in SUPPORTED_AUDIO_EXTENSIONS) {
                        val canonicalKey = try {
                            child.canonicalPath.lowercase()
                        } catch (_: Exception) {
                            child.absolutePath.lowercase()
                        }
                        if (!resultsByKey.containsKey(canonicalKey)) {
                            var title = child.nameWithoutExtension
                            var artist = "Local MP3"
                            var album = child.parentFile?.name ?: "Music"
                            var durationMs = 0L

                            val retriever = MediaMetadataRetriever()
                            try {
                                retriever.setDataSource(child.absolutePath)
                                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                                    ?.takeIf { it.isNotBlank() }
                                    ?.let { title = it }
                                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                                    ?.takeIf { it.isNotBlank() && !it.contains("<unknown>", ignoreCase = true) }
                                    ?.let { artist = it }
                                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
                                    ?.takeIf { it.isNotBlank() && !it.contains("<unknown>", ignoreCase = true) }
                                    ?.let { album = it }
                                durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                                    ?.toLongOrNull() ?: 0L
                            } catch (_: Exception) {
                            } finally {
                                try {
                                    retriever.release()
                                } catch (_: Exception) {}
                            }

                            val fileUri = Uri.fromFile(child).toString()
                            resultsByKey[canonicalKey] = MusicTrackItem(
                                id = canonicalKey,
                                title = title,
                                artist = artist,
                                album = album,
                                folderName = child.parentFile?.name ?: "Music",
                                durationMs = durationMs,
                                sizeBytes = child.length(),
                                uriString = fileUri,
                                filePath = child.absolutePath,
                                dateModified = child.lastModified(),
                                isFavorite = favorites.contains(canonicalKey)
                            )
                        }
                    }
                }
            }
        }

        for (dir in candidateDirs) {
            scanDirRecursively(dir, 0)
        }

        // Prioritize .mp3 files first, then alphabetical by title
        resultsByKey.values.sortedWith(
            compareByDescending<MusicTrackItem> {
                (it.filePath?.endsWith(".mp3", ignoreCase = true) == true) ||
                        it.uriString.endsWith(".mp3", ignoreCase = true)
            }.thenBy { it.title.lowercase() }
        )
    }

    suspend fun importAudioUris(uris: List<Uri>): Int = withContext(Dispatchers.IO) {
        val destDir = context.getExternalFilesDir("clone_space_music")
            ?: File(context.filesDir, "clone_space_music")
        if (!destDir.exists()) destDir.mkdirs()

        var importedCount = 0
        for (uri in uris) {
            try {
                var displayName = "Track_${System.currentTimeMillis()}.mp3"
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIdx >= 0 && cursor.moveToFirst()) {
                        val name = cursor.getString(nameIdx)
                        if (!name.isNullOrBlank()) displayName = name
                    }
                }
                if (!displayName.contains('.')) {
                    displayName = "$displayName.mp3"
                }
                val safeName = displayName.replace(Regex("[^a-zA-Z0-9._\\- ()]"), "_")
                val targetFile = File(destDir, safeName)
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(targetFile).use { output ->
                        input.copyTo(output)
                    }
                }
                if (targetFile.exists() && targetFile.length() > 0L) {
                    importedCount++
                }
            } catch (_: Exception) {
            }
        }
        importedCount
    }
}
