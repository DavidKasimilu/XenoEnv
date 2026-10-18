package com.example.ui.screens

import com.example.data.models.MediaItem

import android.content.Intent
import android.net.Uri
import android.text.format.Formatter
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.models.DownloadItem
import com.example.data.models.DownloadStatus
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class DownloadFilter { ALL, VIDEOS, IMAGES, DOCUMENTS, AUDIO, ARCHIVES }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserDownloadsScreen(
    downloads: List<DownloadItem>,
    onDeleteDownload: (DownloadItem) -> Unit,
    onClearAllDownloads: () -> Unit,
    onStartBrowsing: () -> Unit,
    onPlayVideo: (MediaItem) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf(DownloadFilter.ALL) }
    var itemToDelete by remember { mutableStateOf<DownloadItem?>(null) }
    var showClearAllDialog by remember { mutableStateOf(false) }

    val context = LocalContext.current

    val filteredDownloads = remember(downloads, searchQuery, selectedFilter) {
        downloads.filter { item ->
            val matchesSearch = searchQuery.isBlank() ||
                    item.fileName.contains(searchQuery, ignoreCase = true) ||
                    item.url.contains(searchQuery, ignoreCase = true)

            val matchesFilter = when (selectedFilter) {
                DownloadFilter.ALL -> true
                DownloadFilter.VIDEOS -> item.mimeType.contains("video", ignoreCase = true) ||
                        listOf("mp4", "mkv", "webm", "avi").any { item.fileName.lowercase().endsWith(".$it") }
                DownloadFilter.IMAGES -> item.mimeType.contains("image", ignoreCase = true) ||
                        listOf("jpg", "jpeg", "png", "gif", "webp").any { item.fileName.lowercase().endsWith(".$it") }
                DownloadFilter.DOCUMENTS -> item.mimeType.contains("pdf", ignoreCase = true) ||
                        item.mimeType.contains("document", ignoreCase = true) ||
                        listOf("pdf", "doc", "docx", "txt", "xlsx").any { item.fileName.lowercase().endsWith(".$it") }
                DownloadFilter.AUDIO -> item.mimeType.contains("audio", ignoreCase = true) ||
                        listOf("mp3", "m4a", "wav", "aac").any { item.fileName.lowercase().endsWith(".$it") }
                DownloadFilter.ARCHIVES -> item.mimeType.contains("zip", ignoreCase = true) ||
                        listOf("zip", "rar", "7z", "apk").any { item.fileName.lowercase().endsWith(".$it") }
            }

            matchesSearch && matchesFilter
        }
    }

    val totalBytes = remember(downloads) {
        downloads.sumOf { it.fileSize }
    }

    val formattedTotalSize = remember(totalBytes) {
        if (totalBytes > 0) Formatter.formatShortFileSize(context, totalBytes) else "0 B"
    }

    BackHandler {
        onStartBrowsing()
    }

    Box(modifier = modifier.fillMaxSize().testTag("browser_downloads_screen")) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(
                        onClick = onStartBrowsing,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to Browser"
                        )
                    }
                    Column {
                        Text(
                            text = "Browser Downloads",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${downloads.size} Files • $formattedTotalSize Total",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (downloads.isNotEmpty()) {
                    IconButton(
                        onClick = { showClearAllDialog = true },
                        modifier = Modifier.testTag("clear_all_downloads_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = "Clear all downloads",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            // Search Bar & Filters
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search downloaded files...") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Filled.Clear, contentDescription = "Clear search")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        focusedContainerColor = MaterialTheme.colorScheme.surface
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("search_downloads_input")
                )

                // Filter Chips
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 6.dp)
                ) {
                    items(DownloadFilter.entries) { filter ->
                        val count = remember(downloads, filter) {
                            when (filter) {
                                DownloadFilter.ALL -> downloads.size
                                DownloadFilter.VIDEOS -> downloads.count { it.mimeType.contains("video", ignoreCase = true) || listOf("mp4", "mkv", "webm").any { ext -> it.fileName.lowercase().endsWith(".$ext") } }
                                DownloadFilter.IMAGES -> downloads.count { it.mimeType.contains("image", ignoreCase = true) || listOf("jpg", "png", "webp").any { ext -> it.fileName.lowercase().endsWith(".$ext") } }
                                DownloadFilter.DOCUMENTS -> downloads.count { it.mimeType.contains("pdf", ignoreCase = true) || listOf("pdf", "doc", "txt").any { ext -> it.fileName.lowercase().endsWith(".$ext") } }
                                DownloadFilter.AUDIO -> downloads.count { it.mimeType.contains("audio", ignoreCase = true) || listOf("mp3", "m4a").any { ext -> it.fileName.lowercase().endsWith(".$ext") } }
                                DownloadFilter.ARCHIVES -> downloads.count { it.mimeType.contains("zip", ignoreCase = true) || listOf("zip", "apk").any { ext -> it.fileName.lowercase().endsWith(".$ext") } }
                            }
                        }

                        val label = when (filter) {
                            DownloadFilter.ALL -> "All ($count)"
                            DownloadFilter.VIDEOS -> "Videos ($count)"
                            DownloadFilter.IMAGES -> "Images ($count)"
                            DownloadFilter.DOCUMENTS -> "Docs ($count)"
                            DownloadFilter.AUDIO -> "Audio ($count)"
                            DownloadFilter.ARCHIVES -> "Archives ($count)"
                        }

                        FilterChip(
                            selected = selectedFilter == filter,
                            onClick = { selectedFilter = filter },
                            label = { Text(label, fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        )
                    }
                }
            }

            // Downloads List
            if (filteredDownloads.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(72.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Filled.Download,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                        }

                        Text(
                            text = if (searchQuery.isNotEmpty()) "No files match your search" else "No browser downloads yet",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (searchQuery.isNotEmpty()) "Try a different search term or category filter." else "Files, videos, and documents downloaded while browsing will appear here.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        if (searchQuery.isEmpty()) {
                            Button(
                                onClick = onStartBrowsing,
                                modifier = Modifier
                                    .padding(top = 8.dp)
                                    .testTag("start_browsing_downloads_btn")
                            ) {
                                Text("Start Browsing")
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("downloads_list"),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredDownloads, key = { "dl_${it.id}_${it.fileName}" }) { item ->
                        val isVideo = item.mimeType.contains("video", ignoreCase = true) ||
                                listOf("mp4", "mkv", "webm", "avi", "3gp", "mov").any { item.fileName.lowercase().endsWith(".$it") }
                        DownloadItemCard(
                            item = item,
                            onOpen = {
                                if (isVideo && item.filePath != null && File(item.filePath).exists()) {
                                    val mediaItem = MediaItem(
                                        id = item.id,
                                        uri = Uri.fromFile(File(item.filePath)),
                                        displayName = item.fileName,
                                        dateAdded = item.timestamp / 1000L,
                                        size = item.fileSize,
                                        isVideo = true,
                                        folderName = "Downloads"
                                    )
                                    onPlayVideo(mediaItem)
                                } else {
                                    openFile(context, item)
                                }
                            },
                            onShare = {
                                shareFile(context, item)
                            },
                            onDelete = {
                                itemToDelete = item
                            }
                        )
                    }
                }
            }
        }

        // Delete confirmation dialog
        itemToDelete?.let { target ->
            AlertDialog(
                onDismissRequest = { itemToDelete = null },
                icon = { Icon(Icons.Filled.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                title = { Text("Delete Download?") },
                text = { Text("Are you sure you want to delete \"${target.fileName}\"?") },
                confirmButton = {
                    Button(
                        onClick = {
                            itemToDelete = null
                            onDeleteDownload(target)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.testTag("confirm_delete_download_btn")
                    ) {
                        Text("Delete")
                    }
                },
                dismissButton = {
                    OutlinedButton(
                        onClick = { itemToDelete = null }
                    ) {
                        Text("Cancel")
                    }
                }
            )
        }

        // Clear all dialog
        if (showClearAllDialog) {
            AlertDialog(
                onDismissRequest = { showClearAllDialog = false },
                icon = { Icon(Icons.Filled.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                title = { Text("Clear All Downloads?") },
                text = { Text("This will remove all recorded download history from the browser.") },
                confirmButton = {
                    Button(
                        onClick = {
                            showClearAllDialog = false
                            onClearAllDownloads()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Clear All")
                    }
                },
                dismissButton = {
                    OutlinedButton(onClick = { showClearAllDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@Composable
private fun DownloadItemCard(
    item: DownloadItem,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val formattedSize = remember(item.fileSize) {
        if (item.fileSize > 0) Formatter.formatShortFileSize(context, item.fileSize) else "Unknown size"
    }

    val formattedDate = remember(item.timestamp) {
        val sdf = SimpleDateFormat("MMM dd, yyyy • HH:mm", Locale.getDefault())
        sdf.format(Date(item.timestamp))
    }

    val fileIcon: ImageVector = remember(item.mimeType, item.fileName) {
        val name = item.fileName.lowercase()
        when {
            item.mimeType.contains("video") || listOf("mp4", "mkv", "webm").any { name.endsWith(".$it") } -> Icons.Filled.Videocam
            item.mimeType.contains("image") || listOf("jpg", "png", "webp", "gif").any { name.endsWith(".$it") } -> Icons.Filled.Image
            item.mimeType.contains("pdf") || listOf("pdf", "doc", "txt").any { name.endsWith(".$it") } -> Icons.Filled.Description
            item.mimeType.contains("audio") || listOf("mp3", "m4a", "wav").any { name.endsWith(".$it") } -> Icons.Filled.AudioFile
            item.mimeType.contains("zip") || listOf("zip", "rar", "apk").any { name.endsWith(".$it") } -> Icons.Filled.FolderZip
            else -> Icons.Filled.Folder
        }
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        modifier = modifier
            .fillMaxWidth()
            .clickable { onOpen() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(46.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = fileIcon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.fileName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "$formattedSize • $formattedDate",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onShare) {
                    Icon(Icons.Filled.Share, contentDescription = "Share", modifier = Modifier.size(20.dp))
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

private fun openFile(context: android.content.Context, item: DownloadItem) {
    try {
        val file = item.filePath?.let { File(it) }
        if (file != null && file.exists()) {
            val safeUri = try {
                androidx.core.content.FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
            } catch (_: Exception) {
                Uri.fromFile(file)
            }
            val intent = Intent(Intent.ACTION_VIEW).apply {
                val mime = if (item.mimeType.isNotBlank()) item.mimeType else {
                    when (file.extension.lowercase()) {
                        "mp4", "mkv", "webm", "avi", "3gp", "mov" -> "video/*"
                        "jpg", "jpeg", "png", "webp", "gif" -> "image/*"
                        "pdf" -> "application/pdf"
                        "mp3", "m4a", "wav" -> "audio/*"
                        else -> "*/*"
                    }
                }
                setDataAndType(safeUri, mime)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(intent)
        } else {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(item.url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    } catch (e: Exception) {
        android.widget.Toast.makeText(context, "Cannot open file: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
    }
}

private fun shareFile(context: android.content.Context, item: DownloadItem) {
    try {
        val file = item.filePath?.let { File(it) }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = if (item.mimeType.isNotBlank()) item.mimeType else "*/*"
            if (file != null && file.exists()) {
                val safeUri = try {
                    androidx.core.content.FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        file
                    )
                } catch (_: Exception) {
                    Uri.fromFile(file)
                }
                putExtra(Intent.EXTRA_STREAM, safeUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } else {
                putExtra(Intent.EXTRA_TEXT, item.url)
            }
        }
        context.startActivity(Intent.createChooser(intent, "Share Downloaded File"))
    } catch (e: Exception) {
        android.widget.Toast.makeText(context, "Cannot share file: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
    }
}
