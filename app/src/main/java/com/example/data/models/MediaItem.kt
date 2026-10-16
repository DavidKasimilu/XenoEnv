package com.example.data.models

import android.net.Uri
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class MediaItem(
    val id: Long,
    val uri: Uri,
    val displayName: String,
    val dateAdded: Long,
    val size: Long,
    val durationMs: Long? = null,
    val isVideo: Boolean = false,
    val width: Int = 0,
    val height: Int = 0,
    val customThumbnailUri: Uri? = null,
    val folderName: String = "Camera"
) {
    val formattedDuration: String
        get() {
            if (durationMs == null || durationMs <= 0) return "0:00"
            val totalSeconds = durationMs / 1000
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            return String.format(Locale.getDefault(), "%d:%02d", minutes, seconds)
        }

    val formattedDate: String
        get() {
            val date = Date(dateAdded * 1000L)
            val formatter = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
            return formatter.format(date)
        }

    val formattedSize: String
        get() {
            if (size <= 0) return "0 KB"
            val kb = size / 1024.0
            val mb = kb / 1024.0
            return if (mb >= 1.0) {
                String.format(Locale.getDefault(), "%.1f MB", mb)
            } else {
                String.format(Locale.getDefault(), "%.0f KB", kb)
            }
        }
}
