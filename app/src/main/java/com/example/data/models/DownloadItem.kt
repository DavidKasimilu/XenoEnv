package com.example.data.models

enum class DownloadStatus { DOWNLOADING, COMPLETED, FAILED, CANCELLED }

data class DownloadItem(
    val id: Long = System.currentTimeMillis(),
    val downloadManagerId: Long = -1L,
    val url: String,
    val fileName: String,
    val filePath: String? = null,
    val mimeType: String = "",
    val fileSize: Long = 0L,
    val bytesDownloaded: Long = 0L,
    val status: DownloadStatus = DownloadStatus.COMPLETED,
    val timestamp: Long = System.currentTimeMillis()
)
