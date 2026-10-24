package com.example.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled. Equalizer
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.LockClock
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3. FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.MusicTrackItem
import com.example.telecom.MusicPlaybackController
import java.util.Locale

enum class MusicFilterTab {
    ALL_SONGS,
    FAVORITES
}

@Composable
fun MusicPlayerScreen(
    tracks: List<MusicTrackItem>,
    isLoading: Boolean,
    onRefreshScan: () -> Unit,
    onToggleFavorite: (MusicTrackItem) -> Unit,
    onImportAudioUris: (List<Uri>) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val playbackState by MusicPlaybackController.state.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableStateOf(MusicFilterTab.ALL_SONGS) }
    var searchQuery by remember { mutableStateOf("") }
    var isDraggingSlider by remember { mutableStateOf(false) }
    var sliderDragFraction by remember { mutableFloatStateOf(0f) }

    val hasAudioPermission = remember(tracks.size, isLoading) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_AUDIO) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
    }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        onRefreshScan()
    }

    val importMp3Launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            onImportAudioUris(uris)
        }
    }

    LaunchedEffect(Unit) {
        if (!hasAudioPermission) {
            val perms = mutableListOf<String>()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                perms.add(Manifest.permission.READ_MEDIA_AUDIO)
                perms.add(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                perms.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
            audioPermissionLauncher.launch(perms.toTypedArray())
        } else if (tracks.isEmpty()) {
            onRefreshScan()
        }
    }

    BackHandler {
        onClose()
    }

    val favoriteCount = remember(tracks) { tracks.count { it.isFavorite } }

    val filteredTracks = remember(tracks, selectedTab, searchQuery) {
        val base = when (selectedTab) {
            MusicFilterTab.ALL_SONGS -> tracks
            MusicFilterTab.FAVORITES -> tracks.filter { it.isFavorite }
        }
        val q = searchQuery.trim().lowercase()
        if (q.isEmpty()) {
            base
        } else {
            base.filter {
                it.title.lowercase().contains(q) ||
                        it.artist.lowercase().contains(q) ||
                        it.folderName.lowercase().contains(q) ||
                        it.album.lowercase().contains(q)
            }
        }
    }

    val currentTrack = remember(playbackState.currentTrack, tracks) {
        val active = playbackState.currentTrack ?: return@remember tracks.firstOrNull()
        tracks.find { it.id == active.id } ?: active
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("clone_space_music_player_screen")
    ) {
        // Header Bar
        Surface(
            tonalElevation = 2.dp,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier
                            .size(40.dp)
                            .testTag("music_player_back_btn")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to Clone Space"
                        )
                    }

                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color(0xFFEC4899), Color(0xFF8B5CF6))
                                )
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Headphones,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Column {
                        Text(
                            text = "Music Player",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.LockClock,
                                contentDescription = null,
                                tint = Color(0xFF10B981),
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = "Plays when screen sleeps • ${tracks.size} MP3s",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilledTonalIconButton(
                        onClick = { importMp3Launcher.launch(arrayOf("audio/*")) },
                        modifier = Modifier
                            .size(38.dp)
                            .testTag("music_import_mp3_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = "Import MP3 Files",
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    FilledTonalIconButton(
                        onClick = onRefreshScan,
                        modifier = Modifier
                            .size(38.dp)
                            .testTag("music_rescan_device_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = "Scan Device for MP3s",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        // Search & Filter Tabs Row
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search MP3 songs, artists, or folders...") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(
                                imageVector = Icons.Filled.Clear,
                                contentDescription = "Clear search",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("music_search_input")
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = selectedTab == MusicFilterTab.ALL_SONGS,
                    onClick = { selectedTab = MusicFilterTab.ALL_SONGS },
                    label = {
                        Text(
                            text = "All MP3s (${tracks.size})",
                            fontWeight = FontWeight.SemiBold
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.MusicNote,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    modifier = Modifier.testTag("music_tab_all")
                )

                FilterChip(
                    selected = selectedTab == MusicFilterTab.FAVORITES,
                    onClick = { selectedTab = MusicFilterTab.FAVORITES },
                    label = {
                        Text(
                            text = "Favourites ($favoriteCount)",
                            fontWeight = FontWeight.SemiBold
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = if (selectedTab == MusicFilterTab.FAVORITES) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            contentDescription = null,
                            tint = if (selectedTab == MusicFilterTab.FAVORITES) Color(0xFFEF4444) else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFFEF4444).copy(alpha = 0.16f),
                        selectedLabelColor = Color(0xFFEF4444)
                    ),
                    modifier = Modifier.testTag("music_tab_favorites")
                )
            }
        }

        // Main Track List Area
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            if (isLoading) {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator()
                    Text(
                        text = "Scanning device for MP3 files...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else if (filteredTracks.isEmpty()) {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp)
                        .align(Alignment.Center)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFEC4899).copy(alpha = 0.15f))
                        ) {
                            Icon(
                                imageVector = if (selectedTab == MusicFilterTab.FAVORITES) Icons.Filled.FavoriteBorder else Icons.Filled.MusicNote,
                                contentDescription = null,
                                tint = Color(0xFFEC4899),
                                modifier = Modifier.size(32.dp)
                            )
                        }

                        Text(
                            text = when {
                                selectedTab == MusicFilterTab.FAVORITES -> "No Favourite Songs Yet"
                                searchQuery.isNotBlank() -> "No matching MP3s found"
                                else -> "No MP3 Files Found on Device"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )

                        Text(
                            text = when {
                                selectedTab == MusicFilterTab.FAVORITES ->
                                    "Tap the heart icon on any MP3 song to add it to your Favourites."
                                else ->
                                    "Tap Scan Device to search all device folders for .mp3 files, or tap Import MP3 to pick audio files directly."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = {
                                    if (!hasAudioPermission) {
                                        val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                            arrayOf(Manifest.permission.READ_MEDIA_AUDIO)
                                        } else {
                                            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
                                        }
                                        audioPermissionLauncher.launch(perms)
                                    } else {
                                        onRefreshScan()
                                    }
                                },
                                modifier = Modifier.testTag("empty_scan_mp3_btn")
                            ) {
                                Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Scan Device")
                            }

                            FilledTonalButton(
                                onClick = { importMp3Launcher.launch(arrayOf("audio/*")) },
                                modifier = Modifier.testTag("empty_import_mp3_btn")
                            ) {
                                Icon(Icons.Filled.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Import MP3")
                            }
                        }

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
                            FilledTonalButton(
                                onClick = {
                                    try {
                                        val intent = Intent(
                                            Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                                            Uri.parse("package:${context.packageName}")
                                        )
                                        context.startActivity(intent)
                                    } catch (_: Exception) {
                                        try {
                                            context.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                                        } catch (_: Exception) {}
                                    }
                                }
                            ) {
                                Text("Grant Full Folder Scan Access", fontSize = 12.sp)
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("music_tracks_list"),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(
                        items = filteredTracks,
                        key = { _, item -> item.id }
                    ) { index, item ->
                        val isCurrentTrack = playbackState.currentTrack?.id == item.id
                        val isTrackPlaying = isCurrentTrack && playbackState.isPlaying

                        MusicTrackRowCard(
                            track = item,
                            index = index + 1,
                            isCurrentTrack = isCurrentTrack,
                            isPlaying = isTrackPlaying,
                            onPlayClick = {
                                if (isCurrentTrack) {
                                    MusicPlaybackController.togglePlayPause(context)
                                } else {
                                    MusicPlaybackController.playTrack(context, item, filteredTracks)
                                }
                            },
                            onToggleFavorite = { onToggleFavorite(item) }
                        )
                    }
                }
            }
        }

        // Persistent Bottom Now Playing Deck (Play/Pause, Next, Previous, Seek, Favourite)
        NowPlayingControlDeck(
            currentTrack = currentTrack,
            isPlaying = playbackState.isPlaying,
            currentPositionMs = playbackState.currentPositionMs,
            durationMs = playbackState.durationMs.coerceAtLeast(currentTrack?.durationMs ?: 0L),
            isRepeatOne = playbackState.isRepeatOne,
            isShuffle = playbackState.isShuffle,
            isDraggingSlider = isDraggingSlider,
            sliderDragFraction = sliderDragFraction,
            onSliderDragChange = { dragging, fraction ->
                isDraggingSlider = dragging
                sliderDragFraction = fraction
            },
            onSeekTo = { targetMs ->
                MusicPlaybackController.seekTo(targetMs)
            },
            onPlayPause = {
                if (playbackState.currentTrack == null && currentTrack != null) {
                    MusicPlaybackController.playTrack(context, currentTrack, filteredTracks.ifEmpty { tracks })
                } else {
                    MusicPlaybackController.togglePlayPause(context)
                }
            },
            onNext = { MusicPlaybackController.playNext(context) },
            onPrevious = { MusicPlaybackController.playPrevious(context) },
            onToggleFavorite = {
                currentTrack?.let { onToggleFavorite(it) }
            },
            onToggleRepeat = { MusicPlaybackController.toggleRepeatOne() },
            onToggleShuffle = { MusicPlaybackController.toggleShuffle() }
        )
    }
}

@Composable
private fun MusicTrackRowCard(
    track: MusicTrackItem,
    index: Int,
    isCurrentTrack: Boolean,
    isPlaying: Boolean,
    onPlayClick: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (isCurrentTrack) {
            Color(0xFFEC4899).copy(alpha = 0.14f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        },
        border = if (isCurrentTrack) {
            androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEC4899).copy(alpha = 0.6f))
        } else null,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onPlayClick)
            .testTag("music_track_row_$index")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (isCurrentTrack) {
                            Brush.verticalGradient(listOf(Color(0xFFEC4899), Color(0xFF8B5CF6)))
                        } else {
                            Brush.verticalGradient(
                                listOf(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                                    MaterialTheme.colorScheme.secondary.copy(alpha = 0.18f)
                                )
                            )
                        }
                    )
            ) {
                Icon(
                    imageVector = when {
                        isPlaying -> Icons.Filled.GraphicEq
                        isCurrentTrack -> Icons.Filled.Equalizer
                        else -> Icons.Filled.MusicNote
                    },
                    contentDescription = null,
                    tint = if (isCurrentTrack) Color.White else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isCurrentTrack) FontWeight.Bold else FontWeight.SemiBold,
                    color = if (isCurrentTrack) Color(0xFFEC4899) else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "${track.artist} • ${track.folderName}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (track.durationMs > 0L) {
                        Text(
                            text = "• ${formatMusicDuration(track.durationMs)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            IconButton(
                onClick = onToggleFavorite,
                modifier = Modifier
                    .size(40.dp)
                    .testTag("music_track_fav_btn_$index")
            ) {
                Icon(
                    imageVector = if (track.isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    contentDescription = if (track.isFavorite) "Remove from Favourites" else "Add to Favourites",
                    tint = if (track.isFavorite) Color(0xFFEF4444) else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun NowPlayingControlDeck(
    currentTrack: MusicTrackItem?,
    isPlaying: Boolean,
    currentPositionMs: Long,
    durationMs: Long,
    isRepeatOne: Boolean,
    isShuffle: Boolean,
    isDraggingSlider: Boolean,
    sliderDragFraction: Float,
    onSliderDragChange: (Boolean, Float) -> Unit,
    onSeekTo: (Long) -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onToggleFavorite: () -> Unit,
    onToggleRepeat: () -> Unit,
    onToggleShuffle: () -> Unit
) {
    val safeDuration = durationMs.coerceAtLeast(1L)
    val computedFraction = (currentPositionMs.toFloat() / safeDuration.toFloat()).coerceIn(0f, 1f)
    val activeSliderValue = if (isDraggingSlider) sliderDragFraction else computedFraction

    Surface(
        tonalElevation = 8.dp,
        shadowElevation = 12.dp,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("music_now_playing_deck")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Top Row: Track info + Favourite Heart Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color(0xFFEC4899), Color(0xFF8B5CF6))
                                )
                            )
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Filled.GraphicEq else Icons.Filled.MusicNote,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = currentTrack?.title ?: "Select an MP3 to Play",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = currentTrack?.let { "${it.artist} • ${it.folderName}" }
                                ?: "Plays continuously even when screen turns off",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                IconButton(
                    onClick = onToggleFavorite,
                    enabled = currentTrack != null,
                    modifier = Modifier
                        .size(44.dp)
                        .testTag("music_deck_favorite_btn")
                ) {
                    val isFav = currentTrack?.isFavorite == true
                    Icon(
                        imageVector = if (isFav) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = if (isFav) "Unfavourite" else "Favourite",
                        tint = if (isFav) Color(0xFFEF4444) else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }

            // Seek Slider & Timestamps
            Column(modifier = Modifier.fillMaxWidth()) {
                Slider(
                    value = activeSliderValue,
                    onValueChange = { frac ->
                        onSliderDragChange(true, frac)
                    },
                    onValueChangeFinished = {
                        val targetMs = (sliderDragFraction * safeDuration).toLong()
                        onSliderDragChange(false, sliderDragFraction)
                        onSeekTo(targetMs)
                    },
                    enabled = currentTrack != null,
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFFEC4899),
                        activeTrackColor = Color(0xFFEC4899)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(26.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val displayedPos = if (isDraggingSlider) {
                        (sliderDragFraction * safeDuration).toLong()
                    } else {
                        currentPositionMs
                    }
                    Text(
                        text = formatMusicDuration(displayedPos),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = formatMusicDuration(if (durationMs > 0L) durationMs else 0L),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Playback Transport Controls: Shuffle, Previous, Play/Pause, Next, Repeat
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onToggleShuffle,
                    modifier = Modifier
                        .size(44.dp)
                        .testTag("music_deck_shuffle_btn")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Shuffle,
                        contentDescription = "Toggle Shuffle",
                        tint = if (isShuffle) Color(0xFFEC4899) else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                FilledTonalIconButton(
                    onClick = onPrevious,
                    enabled = currentTrack != null,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("music_deck_previous_btn")
                ) {
                    Icon(
                        imageVector = Icons.Filled.SkipPrevious,
                        contentDescription = "Previous Track",
                        modifier = Modifier.size(26.dp)
                    )
                }

                FilledIconButton(
                    onClick = onPlayPause,
                    enabled = currentTrack != null,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = Color(0xFFEC4899),
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .size(60.dp)
                        .testTag("music_deck_play_pause_btn")
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        modifier = Modifier.size(32.dp)
                    )
                }

                FilledTonalIconButton(
                    onClick = onNext,
                    enabled = currentTrack != null,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("music_deck_next_btn")
                ) {
                    Icon(
                        imageVector = Icons.Filled.SkipNext,
                        contentDescription = "Next Track",
                        modifier = Modifier.size(26.dp)
                    )
                }

                IconButton(
                    onClick = onToggleRepeat,
                    modifier = Modifier
                        .size(44.dp)
                        .testTag("music_deck_repeat_btn")
                ) {
                    Icon(
                        imageVector = if (isRepeatOne) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                        contentDescription = "Toggle Repeat",
                        tint = if (isRepeatOne) Color(0xFFEC4899) else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

private fun formatMusicDuration(durationMs: Long): String {
    val totalSeconds = (durationMs / 1000L).coerceAtLeast(0L)
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}
