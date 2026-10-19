package com.example.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VideoCall
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.models.MediaItem
import com.example.ui.components.VideoGridItemCard
import com.example.ui.components.VideoPlayerModal
import com.example.ui.components.VideoThumbnailImage
import com.example.ui.viewmodel.MediaFilter

enum class GalleryTab { IMAGES, VIDEOS }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryScreen(
    mediaItems: List<MediaItem>,
    filter: MediaFilter,
    selectedImageItem: MediaItem?,
    selectedVideoItem: MediaItem?,
    isControlsHidden: Boolean,
    onToggleHide: (Boolean) -> Unit,
    onFilterChange: (MediaFilter) -> Unit,
    onSelectImage: (MediaItem?) -> Unit,
    onSelectVideo: (MediaItem?) -> Unit,
    onRefresh: () -> Unit,
    onOpenBuiltInCamera: () -> Unit = {},
    onAddCustomMedia: ((android.net.Uri, Boolean, String) -> Unit)? = null,
    onDeleteMedia: ((MediaItem) -> Unit)? = null,
    onDeleteDemoMedia: (() -> Unit)? = null,
    onAddSampleVideos: (() -> Unit)? = null,
    onAddSamplePhotos: (() -> Unit)? = null,
    onClose: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var currentTab by remember { mutableStateOf(GalleryTab.VIDEOS) }
    var mediaToDelete by remember { mutableStateOf<MediaItem?>(null) }
    var selectedVideoFolder by remember { mutableStateOf<String?>(null) }
    var selectedPhotoFolder by remember { mutableStateOf<String?>(null) }
    var isVideoFolderMode by remember { mutableStateOf(false) }
    var isPhotoFolderMode by remember { mutableStateOf(true) }

    // Intercept back navigation when browsing inside a folder or closing Media screen
    BackHandler(enabled = selectedImageItem == null && selectedVideoItem == null && selectedVideoFolder != null) {
        selectedVideoFolder = null
    }
    BackHandler(enabled = selectedImageItem == null && selectedVideoItem == null && selectedPhotoFolder != null) {
        selectedPhotoFolder = null
    }
    BackHandler(enabled = selectedImageItem == null && selectedVideoItem == null && selectedVideoFolder == null && selectedPhotoFolder == null && onClose != null) {
        onClose?.invoke()
    }

    val context = LocalContext.current

    var hasVideoPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED
            } else {
                ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
            }
        )
    }

    val videoPermissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        val granted = perms.values.any { it }
        hasVideoPermission = granted
        onRefresh()
    }

    fun requestVideoPermissions() {
        val perms = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.add(Manifest.permission.READ_MEDIA_VIDEO)
            perms.add(Manifest.permission.READ_MEDIA_IMAGES)
            if (Build.VERSION.SDK_INT >= 34) {
                perms.add("android.permission.READ_MEDIA_VISUAL_USER_SELECTED")
            }
        } else {
            perms.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            perms.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
        videoPermissionsLauncher.launch(perms.toTypedArray())
    }

    // Video Picker Launcher for adding videos with thumbnails
    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let {
            onAddCustomMedia?.invoke(it, true, "Video_${System.currentTimeMillis() % 10000}.mp4")
        }
    }

    // Photo Picker Launcher
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let {
            onAddCustomMedia?.invoke(it, false, "Photo_${System.currentTimeMillis() % 10000}.jpg")
        }
    }

    val photosCount = remember(mediaItems) { mediaItems.count { !it.isVideo } }
    val videosCount = remember(mediaItems) { mediaItems.count { it.isVideo } }

    val filteredItems = remember(mediaItems, filter, currentTab) {
        when (currentTab) {
            GalleryTab.IMAGES -> mediaItems.filter { !it.isVideo }
            GalleryTab.VIDEOS -> mediaItems.filter { it.isVideo }
        }
    }

    // Check if any demo items exist to show explicit cleanup button
    val hasDemoItems = remember(mediaItems) {
        mediaItems.any {
            it.id in 8000L..9999L ||
            it.uri.toString().contains("sample") ||
            it.uri.toString().contains("commondatastorage") ||
            it.uri.toString().contains("unsplash")
        }
    }

    Box(modifier = modifier.fillMaxSize().testTag("gallery_screen")) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Gallery",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "$photosCount Images • $videosCount Videos",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (onClose != null) {
                        FilledTonalIconButton(
                            onClick = onClose,
                            modifier = Modifier.testTag("close_media_manager_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Close Media"
                            )
                        }
                    }
                    // Delete demo media button if any demo pictures/videos are found
                    if (hasDemoItems && onDeleteDemoMedia != null) {
                        FilledTonalIconButton(
                            onClick = onDeleteDemoMedia,
                            modifier = Modifier.testTag("delete_demo_media_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Delete,
                                contentDescription = "Delete demo pictures and videos",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }

                    // Import Video button
                    FilledTonalIconButton(
                        onClick = {
                            videoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                            )
                        },
                        modifier = Modifier.testTag("import_video_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.VideoCall,
                            contentDescription = "Import video"
                        )
                    }

                    // Import Photo button
                    FilledTonalIconButton(
                        onClick = {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        modifier = Modifier.testTag("import_photo_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AddPhotoAlternate,
                            contentDescription = "Import photo"
                        )
                    }

                    // Refresh
                    IconButton(
                        onClick = {
                            hasVideoPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED
                            } else {
                                ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
                            }
                            onRefresh()
                        },
                        modifier = Modifier.testTag("refresh_gallery_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = "Refresh"
                        )
                    }

                    if (!hasVideoPermission) {
                        FilledTonalIconButton(
                            onClick = { requestVideoPermissions() },
                            modifier = Modifier.testTag("grant_media_perm_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Lock,
                                contentDescription = "Grant storage and video permission",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            // TabRow with badges
            TabRow(selectedTabIndex = currentTab.ordinal) {
                Tab(
                    selected = currentTab == GalleryTab.IMAGES,
                    onClick = { currentTab = GalleryTab.IMAGES },
                    text = {
                        Text(
                            text = if (photosCount > 0) "Images ($photosCount)" else "Images",
                            fontWeight = if (currentTab == GalleryTab.IMAGES) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    modifier = Modifier.testTag("gallery_tab_images")
                )
                Tab(
                    selected = currentTab == GalleryTab.VIDEOS,
                    onClick = { currentTab = GalleryTab.VIDEOS },
                    text = {
                        Text(
                            text = if (videosCount > 0) "Videos ($videosCount)" else "Videos",
                            fontWeight = if (currentTab == GalleryTab.VIDEOS) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    modifier = Modifier.testTag("gallery_tab_videos")
                )
            }

            // Main Content Area
            if (filteredItems.isEmpty()) {
                    // Empty state
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
                                        imageVector = if (currentTab == GalleryTab.VIDEOS) {
                                            if (!hasVideoPermission) Icons.Filled.Lock else Icons.Filled.Videocam
                                        } else Icons.Filled.Image,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(36.dp)
                                    )
                                }
                            }

                            Text(
                                text = if (currentTab == GalleryTab.VIDEOS) {
                                    if (!hasVideoPermission) "Storage & Video Permission Required" else "No videos in gallery"
                                } else "No photos in gallery",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )

                            Text(
                                text = if (currentTab == GalleryTab.VIDEOS) {
                                    if (!hasVideoPermission)
                                        "Grant video permission so Xeno can scan your device folders and play all your videos."
                                    else
                                        "Import videos with thumbnails from your device using the button below"
                                } else
                                    "Take photos using the camera or import photos from your device",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 24.dp)
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            if (currentTab == GalleryTab.VIDEOS && !hasVideoPermission) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = { requestVideoPermissions() },
                                        modifier = Modifier.testTag("empty_grant_video_perm_btn")
                                    ) {
                                        Icon(Icons.Filled.VideoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Grant Video Permission")
                                    }

                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedButton(
                                            onClick = {
                                                videoPickerLauncher.launch(
                                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                                                )
                                            },
                                            modifier = Modifier.testTag("empty_add_video_btn")
                                        ) {
                                            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Import Video")
                                        }

                                        OutlinedButton(
                                            onClick = {
                                                try {
                                                    val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                                        data = Uri.fromParts("package", context.packageName, null)
                                                    }
                                                    context.startActivity(intent)
                                                } catch (_: Exception) {}
                                            }
                                        ) {
                                            Text("App Settings")
                                        }
                                    }
                                }
                            } else {
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    if (currentTab == GalleryTab.VIDEOS) {
                                        Button(
                                            onClick = {
                                                videoPickerLauncher.launch(
                                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                                                )
                                            },
                                            modifier = Modifier.testTag("empty_add_video_btn")
                                        ) {
                                            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Import Video")
                                        }
                                    } else {
                                        Button(
                                            onClick = onOpenBuiltInCamera,
                                            modifier = Modifier.testTag("empty_open_camera_btn")
                                        ) {
                                            Icon(Icons.Filled.PhotoCamera, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Open Camera")
                                        }

                                        OutlinedButton(
                                            onClick = {
                                                photoPickerLauncher.launch(
                                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                                )
                                            },
                                            modifier = Modifier.testTag("empty_import_photo_btn")
                                        ) {
                                            Icon(Icons.Filled.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Import Photo")
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    if (currentTab == GalleryTab.VIDEOS) {
                        val videoFolderGroups = remember(filteredItems) {
                            filteredItems.groupBy { it.folderName }
                        }

                        if (selectedVideoFolder != null) {
                            val folderVideos = videoFolderGroups[selectedVideoFolder] ?: emptyList()
                            Column(modifier = Modifier.fillMaxSize()) {
                                // Folder sub-header with back button
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    FilledTonalIconButton(
                                        onClick = { selectedVideoFolder = null },
                                        modifier = Modifier.testTag("video_folder_back_btn")
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                            contentDescription = "Back to video folders"
                                        )
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = selectedVideoFolder ?: "Videos",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "${folderVideos.size} ${if (folderVideos.size == 1) "Video" else "Videos"} • Tap video to watch",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                LazyVerticalGrid(
                                    columns = GridCells.Adaptive(minSize = 152.dp),
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .testTag("videos_grid"),
                                    contentPadding = PaddingValues(start = 12.dp, top = 8.dp, end = 12.dp, bottom = 96.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    itemsIndexed(folderVideos, key = { index, item -> "vid_f_${item.id}_${item.uri}_$index" }) { _, item ->
                                        VideoGridItemCard(
                                            item = item,
                                            onClick = { onSelectVideo(item) },
                                            onDelete = { mediaToDelete = it }
                                        )
                                    }
                                }
                            }
                        } else {
                            Column(modifier = Modifier.fillMaxSize()) {
                                if (!hasVideoPermission) {
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 14.dp, vertical = 6.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(
                                                modifier = Modifier.weight(1f),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Icon(
                                                    Icons.Filled.Lock,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Text(
                                                    text = "Grant permission to see all device video folders",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                                )
                                            }
                                            TextButton(
                                                onClick = { requestVideoPermissions() },
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                                            ) {
                                                Text("Grant", fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }

                                // Folder Quick-Filter Chips Row
                                LazyRow(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    item {
                                        FilterChip(
                                            selected = !isVideoFolderMode,
                                            onClick = { isVideoFolderMode = false },
                                            label = { Text("Group by Folder (${videoFolderGroups.size})") },
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = Icons.Filled.Folder,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            },
                                            modifier = Modifier.testTag("video_grouped_view_chip")
                                        )
                                    }
                                    item {
                                        FilterChip(
                                            selected = isVideoFolderMode,
                                            onClick = { isVideoFolderMode = true },
                                            label = { Text("Album Cards") },
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = Icons.Filled.GridView,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            },
                                            modifier = Modifier.testTag("video_albums_chip")
                                        )
                                    }
                                    items(videoFolderGroups.keys.toList(), key = { "chip_vf_$it" }) { fName ->
                                        val count = videoFolderGroups[fName]?.size ?: 0
                                        FilterChip(
                                            selected = false,
                                            onClick = { selectedVideoFolder = fName },
                                            label = { Text("$fName ($count)") }
                                        )
                                    }
                                }

                                if (isVideoFolderMode) {
                                    LazyVerticalGrid(
                                        columns = GridCells.Adaptive(minSize = 150.dp),
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .testTag("video_folders_grid"),
                                        contentPadding = PaddingValues(start = 14.dp, top = 8.dp, end = 14.dp, bottom = 96.dp),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        items(videoFolderGroups.keys.toList(), key = { "vfolder_$it" }) { folderName ->
                                            val folderVideos = videoFolderGroups[folderName] ?: emptyList()
                                            MediaFolderCard(
                                                name = folderName,
                                                count = folderVideos.size,
                                                coverItem = folderVideos.firstOrNull(),
                                                isVideo = true,
                                                onClick = { selectedVideoFolder = folderName }
                                            )
                                        }
                                    }
                                } else {
                                    // Grouped by Folder: Videos arranged in a multi-column grid under each folder header
                                    LazyVerticalGrid(
                                        columns = GridCells.Adaptive(minSize = 152.dp),
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .testTag("videos_grouped_list"),
                                        contentPadding = PaddingValues(start = 12.dp, top = 6.dp, end = 12.dp, bottom = 96.dp),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        verticalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        videoFolderGroups.forEach { (folderName, folderVideos) ->
                                            item(
                                                key = "header_$folderName",
                                                span = { GridItemSpan(maxLineSpan) }
                                            ) {
                                                Surface(
                                                    shape = RoundedCornerShape(12.dp),
                                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(top = 4.dp)
                                                        .clickable { selectedVideoFolder = folderName }
                                                ) {
                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .padding(horizontal = 12.dp, vertical = 8.dp),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.SpaceBetween
                                                    ) {
                                                        Row(
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                        ) {
                                                            Icon(
                                                                imageVector = Icons.Filled.Folder,
                                                                contentDescription = null,
                                                                tint = MaterialTheme.colorScheme.primary,
                                                                modifier = Modifier.size(20.dp)
                                                            )
                                                            Text(
                                                                text = folderName,
                                                                style = MaterialTheme.typography.titleMedium,
                                                                fontWeight = FontWeight.Bold
                                                            )
                                                            Surface(
                                                                shape = RoundedCornerShape(8.dp),
                                                                color = MaterialTheme.colorScheme.primaryContainer
                                                            ) {
                                                                Text(
                                                                    text = "${folderVideos.size}",
                                                                    style = MaterialTheme.typography.labelSmall,
                                                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                                    fontWeight = FontWeight.Bold,
                                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                                )
                                                            }
                                                        }

                                                        TextButton(
                                                            onClick = { selectedVideoFolder = folderName },
                                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                                                        ) {
                                                            Text("Open Folder", style = MaterialTheme.typography.labelMedium)
                                                            Spacer(modifier = Modifier.width(4.dp))
                                                            Icon(
                                                                Icons.AutoMirrored.Filled.ArrowForward,
                                                                contentDescription = null,
                                                                modifier = Modifier.size(14.dp)
                                                            )
                                                        }
                                                    }
                                                }
                                            }

                                            itemsIndexed(
                                                folderVideos,
                                                key = { idx, item -> "grp_${folderName}_${item.id}_${item.uri}_$idx" }
                                            ) { _, item ->
                                                VideoGridItemCard(
                                                    item = item,
                                                    onClick = { onSelectVideo(item) },
                                                    onDelete = { mediaToDelete = it }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        val photoFolderGroups = remember(filteredItems) {
                            filteredItems.groupBy { it.folderName }
                        }

                        if (selectedPhotoFolder != null) {
                            val folderPhotos = photoFolderGroups[selectedPhotoFolder] ?: emptyList()
                            Column(modifier = Modifier.fillMaxSize()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    FilledTonalIconButton(
                                        onClick = { selectedPhotoFolder = null },
                                        modifier = Modifier.testTag("photo_folder_back_btn")
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                            contentDescription = "Back to photo folders"
                                        )
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = selectedPhotoFolder ?: "Photos",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "${folderPhotos.size} ${if (folderPhotos.size == 1) "Photo" else "Photos"} in folder",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                LazyVerticalGrid(
                                    columns = GridCells.Adaptive(minSize = 108.dp),
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .testTag("photos_grid"),
                                    contentPadding = PaddingValues(start = 12.dp, top = 8.dp, end = 12.dp, bottom = 96.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    itemsIndexed(folderPhotos, key = { index, item -> "img_f_${item.id}_${item.uri}_$index" }) { _, item ->
                                        GalleryPhotoGridItem(
                                            item = item,
                                            onClick = {
                                                if (item.isVideo) {
                                                    onSelectVideo(item)
                                                } else {
                                                    onSelectImage(item)
                                                }
                                            },
                                            onDelete = { mediaToDelete = item }
                                        )
                                    }
                                }
                            }
                        } else {
                            Column(modifier = Modifier.fillMaxSize()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    FilterChip(
                                        selected = isPhotoFolderMode,
                                        onClick = { isPhotoFolderMode = true },
                                        label = { Text("Group by Folder (${photoFolderGroups.size})") },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Filled.Folder,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        },
                                        modifier = Modifier.testTag("photo_folders_toggle_chip")
                                    )
                                    FilterChip(
                                        selected = !isPhotoFolderMode,
                                        onClick = { isPhotoFolderMode = false },
                                        label = { Text("All Photos (${filteredItems.size})") },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Filled.GridView,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        },
                                        modifier = Modifier.testTag("all_photos_toggle_chip")
                                    )
                                }

                                if (isPhotoFolderMode) {
                                    LazyVerticalGrid(
                                        columns = GridCells.Adaptive(minSize = 150.dp),
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .testTag("photo_folders_grid"),
                                        contentPadding = PaddingValues(start = 12.dp, top = 8.dp, end = 12.dp, bottom = 96.dp),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        items(photoFolderGroups.keys.toList(), key = { "pfolder_$it" }) { folderName ->
                                            val folderPhotos = photoFolderGroups[folderName] ?: emptyList()
                                            MediaFolderCard(
                                                name = folderName,
                                                count = folderPhotos.size,
                                                coverItem = folderPhotos.firstOrNull(),
                                                isVideo = false,
                                                onClick = { selectedPhotoFolder = folderName }
                                            )
                                        }
                                    }
                                } else {
                                    LazyVerticalGrid(
                                        columns = GridCells.Adaptive(minSize = 108.dp),
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .testTag("photos_grid"),
                                        contentPadding = PaddingValues(start = 12.dp, top = 8.dp, end = 12.dp, bottom = 96.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        itemsIndexed(filteredItems, key = { index, item -> "img_${item.id}_${item.uri}_$index" }) { _, item ->
                                            GalleryPhotoGridItem(
                                                item = item,
                                                onClick = {
                                                    if (item.isVideo) {
                                                        onSelectVideo(item)
                                                    } else {
                                                        onSelectImage(item)
                                                    }
                                                },
                                                onDelete = { mediaToDelete = item }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

        // Fullscreen Photo Viewer with HorizontalPager
        selectedImageItem?.let { imageItem ->
            val photos = filteredItems.filter { !it.isVideo }
            val initialIndex = photos.indexOfFirst { it.id == imageItem.id }.coerceAtLeast(0)
            val pagerState = rememberPagerState(initialPage = initialIndex) { photos.size.coerceAtLeast(1) }
            val currentPhoto = if (photos.isNotEmpty() && pagerState.currentPage in photos.indices) {
                photos[pagerState.currentPage]
            } else {
                imageItem
            }

            BackHandler {
                onSelectImage(null)
            }

            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("fullscreen_photo_viewer"),
                color = Color.Black
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize()
                    ) { page ->
                        val photo = photos.getOrNull(page) ?: currentPhoto
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(photo.uri)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = photo.displayName,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }

                    // Top Bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .align(Alignment.TopCenter),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { onSelectImage(null) },
                            modifier = Modifier
                                .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                                .testTag("close_photo_viewer_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Close",
                                tint = Color.White
                            )
                        }

                        if (photos.size > 1) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color.Black.copy(alpha = 0.6f)
                            ) {
                                Text(
                                    text = "${pagerState.currentPage + 1} / ${photos.size}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }

                    // Bottom Bar with Title & Actions (Share and Delete)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                                )
                            )
                            .padding(horizontal = 20.dp, vertical = 20.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = currentPhoto.displayName,
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${currentPhoto.formattedDate} • ${currentPhoto.formattedSize}",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.7f)
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            IconButton(
                                onClick = {
                                    try {
                                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                            type = "image/*"
                                            putExtra(Intent.EXTRA_STREAM, currentPhoto.uri)
                                        }
                                        context.startActivity(Intent.createChooser(shareIntent, "Share Photo"))
                                    } catch (_: Exception) {}
                                },
                                modifier = Modifier
                                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                                    .testTag("share_photo_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Share,
                                    contentDescription = "Share photo",
                                    tint = Color.White
                                )
                            }

                            if (onDeleteMedia != null) {
                                IconButton(
                                    onClick = { mediaToDelete = currentPhoto },
                                    modifier = Modifier
                                        .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                                        .testTag("delete_photo_btn")
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Delete,
                                        contentDescription = "Delete photo",
                                        tint = Color(0xFFFF6B6B)
                                    )
                                }
                            }
                        }
                    }
                }
            }

        // Fullscreen Video Player Modal
        selectedVideoItem?.let { videoItem ->
            VideoPlayerModal(
                videoItem = videoItem,
                isControlsHidden = isControlsHidden,
                onToggleHide = onToggleHide,
                onClose = { onSelectVideo(null) },
                onDeleteVideo = { target ->
                    onSelectVideo(null)
                    onDeleteMedia?.invoke(target)
                }
            )
        }

        // Delete Confirmation Dialog
        mediaToDelete?.let { target ->
            AlertDialog(
                onDismissRequest = { mediaToDelete = null },
                icon = { Icon(Icons.Filled.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                title = { Text(if (target.isVideo) "Delete Video?" else "Delete Photo?") },
                text = { Text("Are you sure you want to delete \"${target.displayName}\" from your gallery?") },
                confirmButton = {
                    Button(
                        onClick = {
                            mediaToDelete = null
                            if (selectedImageItem?.id == target.id) {
                                onSelectImage(null)
                            }
                            if (selectedVideoItem?.id == target.id) {
                                onSelectVideo(null)
                            }
                            onDeleteMedia?.invoke(target)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.testTag("confirm_delete_media_btn")
                    ) {
                        Text("Delete")
                    }
                },
                dismissButton = {
                    OutlinedButton(
                        onClick = { mediaToDelete = null },
                        modifier = Modifier.testTag("cancel_delete_media_btn")
                    ) {
                        Text("Cancel")
                    }
                }
            )
        }

        // Floating Action Buttons
        if (selectedVideoItem == null && selectedImageItem == null) {
            when (currentTab) {
                GalleryTab.VIDEOS -> {
                    ExtendedFloatingActionButton(
                        onClick = {
                            videoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                            )
                        },
                        icon = { Icon(Icons.Filled.VideoCall, contentDescription = null) },
                        text = { Text("Add Video") },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 16.dp, bottom = 16.dp)
                            .testTag("add_video_fab")
                    )
                }
                GalleryTab.IMAGES -> {
                    // No camera FAB
                }
            }
        }
    }
}
}

/**
 * Clean, properly spaced card item for the Photos Grid with delete affordance
 */
@Composable
private fun GalleryPhotoGridItem(
    item: MediaItem,
    onClick: () -> Unit,
    onDelete: ((MediaItem) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        modifier = modifier
            .aspectRatio(1f)
            .clickable(onClick = onClick)
            .testTag("gallery_photo_${item.id}")
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (item.isVideo) {
                VideoThumbnailImage(
                    item = item,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                // Duration & play icon badge for video items in photos grid
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color.Black.copy(alpha = 0.7f),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = item.formattedDuration,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            } else {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(item.uri)
                        .size(360, 360)
                        .memoryCacheKey(item.uri.toString())
                        .diskCacheKey(item.uri.toString())
                        .crossfade(true)
                        .build(),
                    contentDescription = item.displayName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Quick delete icon on top right
            if (onDelete != null) {
                IconButton(
                    onClick = { onDelete(item) },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(28.dp)
                        .background(Color.Black.copy(alpha = 0.45f), CircleShape)
                        .testTag("delete_photo_item_${item.id}")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = "Delete photo",
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

/**
 * Beautiful Material 3 Folder Card with cover image/video thumbnail,
 * album badge, item count, and subtle gradient scrim.
 */
@Composable
fun MediaFolderCard(
    name: String,
    count: Int,
    coverItem: MediaItem?,
    isVideo: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = modifier
            .aspectRatio(1f)
            .clickable(onClick = onClick)
            .testTag(if (isVideo) "video_folder_$name" else "photo_folder_$name")
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (coverItem != null) {
                if (coverItem.isVideo) {
                    VideoThumbnailImage(
                        item = coverItem,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(coverItem.uri)
                            .size(360, 360)
                            .crossfade(true)
                            .build(),
                        contentDescription = name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isVideo) Icons.Filled.Videocam else Icons.Filled.Image,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Top Badge with Folder Icon
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color.Black.copy(alpha = 0.65f),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Folder,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = if (isVideo) "Video Album" else "Album",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Bottom Scrim with Folder Name and Count
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.88f))
                        )
                    )
                    .padding(horizontal = 10.dp, vertical = 10.dp)
            ) {
                Column {
                    Text(
                        text = name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (isVideo) "$count ${if (count == 1) "Video" else "Videos"}" else "$count ${if (count == 1) "Photo" else "Photos"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.75f)
                    )
                }
            }
        }
    }
}

