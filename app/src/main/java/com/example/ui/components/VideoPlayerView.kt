package com.example.ui.components

import android.app.Activity
import android.content.pm.ActivityInfo
import android.media.MediaPlayer
import android.os.Build
import android.util.Log
import android.view.WindowManager
import android.widget.VideoView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.data.models.MediaItem
import kotlinx.coroutines.delay
import java.util.Locale

/**
 * Gallery video player.
 *
 * - Normal mode: the video plays in its own aspect ratio (never cropped or stretched) under a
 *   small top bar, with the controls laid out neatly underneath. System bars stay visible.
 * - Full screen (Fullscreen icon): hides the system bars, rotates to landscape for wide videos
 *   (or stays portrait for tall ones) and fits the video to the screen by its aspect ratio.
 *   Tap the video to show/hide the controls. Tap the icon again (or Back) to leave full screen.
 */
@Composable
fun VideoPlayerModal(
    videoItem: MediaItem,
    isControlsHidden: Boolean,
    onToggleHide: (Boolean) -> Unit,
    onClose: () -> Unit,
    onDeleteVideo: ((MediaItem) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context as? Activity

    var isPlaying by remember { mutableStateOf(false) }
    var currentPosMs by remember { mutableIntStateOf(0) }
    var totalDurationMs by remember { mutableIntStateOf((videoItem.durationMs ?: 0L).toInt()) }
    var isMuted by remember { mutableStateOf(false) }
    var isBuffering by remember { mutableStateOf(true) }
    var videoViewRef by remember { mutableStateOf<VideoView?>(null) }
    var mediaPlayerRef by remember { mutableStateOf<MediaPlayer?>(null) }
    var isCompleted by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var isUserScrubbing by remember { mutableStateOf(false) }
    var isFullscreen by remember { mutableStateOf(false) }

    var videoWidth by remember { mutableIntStateOf(if (videoItem.width > 0) videoItem.width else 16) }
    var videoHeight by remember { mutableIntStateOf(if (videoItem.height > 0) videoItem.height else 9) }

    var playbackError by remember { mutableStateOf<String?>(null) }
    var retryTrigger by remember { mutableIntStateOf(0) }

    // Full screen = immersive bars + orientation that matches the video. Undone when leaving full screen.
    DisposableEffect(isFullscreen) {
        val window = activity?.window
        if (isFullscreen && activity != null && window != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                window.attributes = window.attributes.apply {
                    layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
            }
            val controller = WindowCompat.getInsetsController(window, window.decorView)
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
            activity.requestedOrientation = if (videoWidth > videoHeight) {
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            } else {
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            }
        }
        onDispose {
            if (isFullscreen && activity != null && window != null) {
                val controller = WindowCompat.getInsetsController(window, window.decorView)
                controller.show(WindowInsetsCompat.Type.systemBars())
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            try {
                videoViewRef?.stopPlayback()
            } catch (_: Exception) {
            }
            videoViewRef = null
            mediaPlayerRef = null
        }
    }

    val handleClose = {
        try {
            videoViewRef?.stopPlayback()
        } catch (_: Exception) {
        }
        videoViewRef = null
        mediaPlayerRef = null
        onClose()
    }

    BackHandler {
        if (isFullscreen) {
            isFullscreen = false
        } else {
            handleClose()
        }
    }

    // In full screen the controls fade away while playing; in normal mode they are always visible
    LaunchedEffect(isFullscreen, isControlsHidden, isPlaying, isUserScrubbing) {
        if (isFullscreen && !isControlsHidden && isPlaying && !isUserScrubbing) {
            delay(3200L)
            onToggleHide(true)
        }
    }

    // Keep the seek bar in sync with playback
    LaunchedEffect(isPlaying, isUserScrubbing) {
        while (isPlaying && !isUserScrubbing) {
            videoViewRef?.let { vv ->
                try {
                    if (vv.isPlaying) {
                        currentPosMs = vv.currentPosition
                        if (vv.duration > 0) totalDurationMs = vv.duration
                    }
                } catch (_: Exception) {
                }
            }
            delay(200)
        }
    }

    fun formatTime(ms: Int): String {
        val totalSec = (ms / 1000).coerceAtLeast(0)
        val hours = totalSec / 3600
        val min = (totalSec % 3600) / 60
        val sec = totalSec % 60
        return if (hours > 0) {
            String.format(Locale.getDefault(), "%d:%02d:%02d", hours, min, sec)
        } else {
            String.format(Locale.getDefault(), "%02d:%02d", min, sec)
        }
    }

    fun togglePlayPause() {
        videoViewRef?.let { vv ->
            if (isCompleted) {
                vv.seekTo(0)
                vv.start()
                isPlaying = true
                isCompleted = false
            } else if (isPlaying) {
                vv.pause()
                isPlaying = false
            } else {
                vv.start()
                isPlaying = true
            }
        }
    }

    fun seekBy(deltaMs: Int) {
        videoViewRef?.let { vv ->
            val dur = if (vv.duration > 0) vv.duration else totalDurationMs
            val target = (vv.currentPosition + deltaMs).coerceIn(0, dur.coerceAtLeast(0))
            vv.seekTo(target)
            currentPosMs = target
            if (isCompleted && target < dur) isCompleted = false
        }
    }

    fun toggleMute() {
        val next = !isMuted
        isMuted = next
        try {
            val vol = if (next) 0f else 1f
            mediaPlayerRef?.setVolume(vol, vol)
        } catch (_: Exception) {
        }
    }

    fun enterFullscreen() {
        onToggleHide(false)
        isFullscreen = true
    }

    val playPauseIcon = when {
        isCompleted -> Icons.Filled.Replay
        isPlaying -> Icons.Filled.Pause
        else -> Icons.Filled.PlayArrow
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .testTag("video_player_modal")
    ) {
        // ── Top bar (normal mode only) ────────────────────────────────────────────
        if (!isFullscreen) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = handleClose,
                    modifier = Modifier.testTag("video_player_close_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Close",
                        tint = Color.White
                    )
                }
                Text(
                    text = videoItem.displayName,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 4.dp)
                )
                if (onDeleteVideo != null) {
                    IconButton(
                        onClick = { showDeleteDialog = true },
                        modifier = Modifier.testTag("video_player_delete_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = "Delete video",
                            tint = Color(0xFFFF6B6B)
                        )
                    }
                }
            }
        }

        // ── Video area: always the video's own aspect ratio ─────────────────────────
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .testTag("video_player_container")
        ) {
            val videoAspect = if (videoWidth > 0 && videoHeight > 0) {
                (videoWidth.toFloat() / videoHeight.toFloat()).coerceIn(0.2f, 5f)
            } else {
                16f / 9f
            }
            val areaAspect = maxWidth.value / maxHeight.value.coerceAtLeast(1f)
            val fitWidth = if (areaAspect > videoAspect) maxHeight * videoAspect else maxWidth
            val fitHeight = if (areaAspect > videoAspect) maxHeight else maxWidth / videoAspect

            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(fitWidth, fitHeight)
            ) {
                key(retryTrigger) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            VideoView(ctx).apply {
                                videoViewRef = this
                                setVideoURI(videoItem.uri)
                                setOnPreparedListener { mp ->
                                    mediaPlayerRef = mp
                                    isBuffering = false
                                    playbackError = null
                                    if (duration > 0) totalDurationMs = duration
                                    if (mp.videoWidth > 0 && mp.videoHeight > 0) {
                                        videoWidth = mp.videoWidth
                                        videoHeight = mp.videoHeight
                                    }
                                    mp.setOnVideoSizeChangedListener { _, w, h ->
                                        if (w > 0 && h > 0) {
                                            videoWidth = w
                                            videoHeight = h
                                        }
                                    }
                                    try {
                                        val vol = if (isMuted) 0f else 1f
                                        mp.setVolume(vol, vol)
                                    } catch (_: Exception) {
                                    }
                                    start()
                                    isPlaying = true
                                }
                                setOnCompletionListener {
                                    isPlaying = false
                                    isCompleted = true
                                    currentPosMs = totalDurationMs
                                    onToggleHide(false)
                                }
                                setOnErrorListener { _, what, extra ->
                                    Log.e("VideoView", "Playback error: what=$what, extra=$extra")
                                    isBuffering = false
                                    playbackError = "Unable to play video (error $what)."
                                    true
                                }
                            }
                        }
                    )
                }
            }

            // Tap the video: play/pause in normal mode, show/hide controls in full screen
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        if (isFullscreen) onToggleHide(!isControlsHidden) else togglePlayPause()
                    }
            )

            if (isBuffering && playbackError == null) {
                CircularProgressIndicator(color = Color.White, modifier = Modifier.align(Alignment.Center))
            }

            // Big play button on the paused video (normal mode)
            if (!isFullscreen && !isPlaying && !isBuffering && playbackError == null) {
                Surface(
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.55f),
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(64.dp)
                        .clip(CircleShape)
                        .clickable { togglePlayPause() }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = playPauseIcon,
                            contentDescription = "Play",
                            tint = Color.White,
                            modifier = Modifier.size(38.dp)
                        )
                    }
                }
            }

            // Full-screen overlay controls
            if (isFullscreen) {
                if (!isControlsHidden) {
                    Box(modifier = Modifier.fillMaxSize().displayCutoutPadding()) {
                        // Top: exit full screen + title, mute on the right
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.TopCenter)
                                .background(
                                    Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.65f), Color.Transparent))
                                )
                                .padding(horizontal = 8.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { isFullscreen = false }) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Exit full screen",
                                    tint = Color.White
                                )
                            }
                            Text(
                                text = videoItem.displayName,
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 4.dp)
                            )
                            IconButton(onClick = { toggleMute() }) {
                                Icon(
                                    imageVector = if (isMuted) Icons.Filled.VolumeOff else Icons.Filled.VolumeUp,
                                    contentDescription = "Mute",
                                    tint = Color.White
                                )
                            }
                        }

                        // Center: rewind / play-pause / forward
                        Row(
                            modifier = Modifier.align(Alignment.Center),
                            horizontalArrangement = Arrangement.spacedBy(32.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = { seekBy(-10000) },
                                modifier = Modifier
                                    .size(52.dp)
                                    .background(Color.Black.copy(alpha = 0.45f), CircleShape)
                            ) {
                                Icon(Icons.Filled.Replay10, "Back 10 seconds", tint = Color.White, modifier = Modifier.size(30.dp))
                            }
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
                                modifier = Modifier
                                    .size(66.dp)
                                    .clip(CircleShape)
                                    .clickable { togglePlayPause() }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(playPauseIcon, "Play or pause", tint = Color.White, modifier = Modifier.size(38.dp))
                                }
                            }
                            IconButton(
                                onClick = { seekBy(10000) },
                                modifier = Modifier
                                    .size(52.dp)
                                    .background(Color.Black.copy(alpha = 0.45f), CircleShape)
                            ) {
                                Icon(Icons.Filled.Forward10, "Forward 10 seconds", tint = Color.White, modifier = Modifier.size(30.dp))
                            }
                        }

                        // Bottom: time - seek bar - time - exit full screen
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.BottomCenter)
                                .background(
                                    Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f)))
                                )
                                .padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(formatTime(currentPosMs), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            SeekBar(
                                currentPosMs = currentPosMs,
                                totalDurationMs = totalDurationMs,
                                onScrub = { isUserScrubbing = true; currentPosMs = it },
                                onScrubFinished = {
                                    videoViewRef?.seekTo(currentPosMs)
                                    isUserScrubbing = false
                                },
                                modifier = Modifier.weight(1f)
                            )
                            Text(formatTime(totalDurationMs), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            IconButton(
                                onClick = { isFullscreen = false },
                                modifier = Modifier.testTag("video_player_fullscreen_button")
                            ) {
                                Icon(Icons.Filled.FullscreenExit, "Exit full screen", tint = Color.White)
                            }
                        }
                    }
                }
            }

            // Playback error
            if (playbackError != null) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.85f))
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = playbackError ?: "Playback failed",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(onClick = {
                            playbackError = null
                            isBuffering = true
                            retryTrigger++
                        }) { Text("Retry") }
                        OutlinedButton(onClick = handleClose) { Text("Close", color = Color.White) }
                    }
                }
            }
        }

        // ── Controls under the video (normal mode only) ───────────────────────────
        if (!isFullscreen) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(formatTime(currentPosMs), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    SeekBar(
                        currentPosMs = currentPosMs,
                        totalDurationMs = totalDurationMs,
                        onScrub = { isUserScrubbing = true; currentPosMs = it },
                        onScrubFinished = {
                            videoViewRef?.seekTo(currentPosMs)
                            isUserScrubbing = false
                        },
                        modifier = Modifier.weight(1f)
                    )
                    Text(formatTime(totalDurationMs), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    IconButton(
                        onClick = { toggleMute() },
                        modifier = Modifier.testTag("video_player_mute_button")
                    ) {
                        Icon(
                            imageVector = if (isMuted) Icons.Filled.VolumeOff else Icons.Filled.VolumeUp,
                            contentDescription = "Mute",
                            tint = Color.White
                        )
                    }
                    IconButton(
                        onClick = { seekBy(-10000) },
                        modifier = Modifier.testTag("video_player_rewind_button")
                    ) {
                        Icon(Icons.Filled.Replay10, "Back 10 seconds", tint = Color.White, modifier = Modifier.size(30.dp))
                    }
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .size(60.dp)
                            .clip(CircleShape)
                            .clickable { togglePlayPause() }
                            .testTag("video_player_play_pause")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(playPauseIcon, "Play or pause", tint = Color.White, modifier = Modifier.size(34.dp))
                        }
                    }
                    IconButton(
                        onClick = { seekBy(10000) },
                        modifier = Modifier.testTag("video_player_forward_button")
                    ) {
                        Icon(Icons.Filled.Forward10, "Forward 10 seconds", tint = Color.White, modifier = Modifier.size(30.dp))
                    }
                    IconButton(
                        onClick = { enterFullscreen() },
                        modifier = Modifier.testTag("video_player_fullscreen_button")
                    ) {
                        Icon(Icons.Filled.Fullscreen, "Full screen", tint = Color.White)
                    }
                }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            icon = { Icon(Icons.Filled.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Delete Video?") },
            text = { Text("Are you sure you want to delete \"${videoItem.displayName}\" from your gallery?") },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteDialog = false
                        handleClose()
                        onDeleteVideo?.invoke(videoItem)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("confirm_delete_video_btn")
                ) { Text("Delete") }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showDeleteDialog = false },
                    modifier = Modifier.testTag("cancel_delete_video_btn")
                ) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun SeekBar(
    currentPosMs: Int,
    totalDurationMs: Int,
    onScrub: (Int) -> Unit,
    onScrubFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    Slider(
        value = if (totalDurationMs > 0) (currentPosMs.toFloat() / totalDurationMs.toFloat()).coerceIn(0f, 1f) else 0f,
        onValueChange = { frac -> onScrub((frac * totalDurationMs).toInt()) },
        onValueChangeFinished = onScrubFinished,
        modifier = modifier.testTag("video_player_seek_slider"),
        colors = SliderDefaults.colors(
            thumbColor = MaterialTheme.colorScheme.primary,
            activeTrackColor = MaterialTheme.colorScheme.primary,
            inactiveTrackColor = Color.White.copy(alpha = 0.35f)
        )
    )
}
