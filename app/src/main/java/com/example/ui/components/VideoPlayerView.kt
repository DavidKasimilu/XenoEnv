package com.example.ui.components

import android.app.Activity
import android.content.Intent
import android.content.pm.ActivityInfo
import android.media.MediaPlayer
import android.os.Build
import android.util.Log
import android.view.WindowManager
import android.widget.VideoView
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.VolumeMute
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
import androidx.compose.ui.graphics.graphicsLayer
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

    // Default to Zoom-to-Fit (true) so video fills the screen preserving natural aspect ratio with no top or bottom bars
    var isZoomToFit by remember { mutableStateOf(true) }
    var videoWidth by remember { mutableIntStateOf(if (videoItem.width > 0) videoItem.width else 16) }
    var videoHeight by remember { mutableIntStateOf(if (videoItem.height > 0) videoItem.height else 9) }
    var showZoomNotice by remember { mutableStateOf(false) }

    var playbackError by remember { mutableStateOf<String?>(null) }
    var retryTrigger by remember { mutableIntStateOf(0) }

    // Hide phone status bar & navigation bar immediately for maximum immersive video view
    DisposableEffect(Unit) {
        val activity = context as? Activity
        val window = activity?.window
        if (window != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                window.attributes = window.attributes.apply {
                    layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
            }
            val insetsController = WindowCompat.getInsetsController(window, window.decorView)
            insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            insetsController.hide(WindowInsetsCompat.Type.systemBars())
        }
        onDispose {
            try {
                videoViewRef?.stopPlayback()
            } catch (_: Exception) {}
            videoViewRef = null
            mediaPlayerRef = null
            if (window != null) {
                val insetsController = WindowCompat.getInsetsController(window, window.decorView)
                insetsController.show(WindowInsetsCompat.Type.systemBars())
            }
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    val handleClose = {
        try {
            videoViewRef?.stopPlayback()
        } catch (_: Exception) {}
        videoViewRef = null
        mediaPlayerRef = null
        val activity = context as? Activity
        activity?.window?.let { window ->
            val insetsController = WindowCompat.getInsetsController(window, window.decorView)
            insetsController.show(WindowInsetsCompat.Type.systemBars())
        }
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        onClose()
    }

    BackHandler {
        handleClose()
    }

    // Auto-hide controls after 3.2 seconds during active playback for unobstructed immersive viewing
    LaunchedEffect(isControlsHidden, isPlaying, isUserScrubbing) {
        if (!isControlsHidden && isPlaying && !isUserScrubbing) {
            delay(3200L)
            onToggleHide(true)
        }
    }

    // Auto-dismiss transient zoom mode badge
    LaunchedEffect(showZoomNotice) {
        if (showZoomNotice) {
            delay(1800L)
            showZoomNotice = false
        }
    }

    // Polling loop to sync seekbar position smoothly
    LaunchedEffect(isPlaying, isUserScrubbing) {
        while (isPlaying && !isUserScrubbing) {
            videoViewRef?.let { vv ->
                try {
                    if (vv.isPlaying) {
                        currentPosMs = vv.currentPosition
                        if (vv.duration > 0) {
                            totalDurationMs = vv.duration
                        }
                    }
                } catch (_: Exception) {}
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

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                onToggleHide(!isControlsHidden)
            }
            .testTag("video_player_modal")
    ) {
        val containerWidth = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val containerHeight = constraints.maxHeight.toFloat().coerceAtLeast(1f)
        val screenAspect = containerWidth / containerHeight
        val naturalVideoAspect = if (videoWidth > 0 && videoHeight > 0) {
            videoWidth.toFloat() / videoHeight.toFloat()
        } else {
            16f / 9f
        }

        // Uniform scale factor that zooms the aspect-preserved video to completely cover the screen with zero top/bottom bars
        val zoomScale = if (isZoomToFit) {
            if (naturalVideoAspect > screenAspect) {
                (naturalVideoAspect / screenAspect).coerceAtLeast(1f)
            } else {
                (screenAspect / naturalVideoAspect).coerceAtLeast(1f)
            }
        } else {
            1f
        }

        // Edge-to-Edge Video Surface Container
        Box(
            modifier = Modifier
                .fillMaxSize()
                .align(Alignment.Center)
                .clip(RoundedCornerShape(0.dp))
                .testTag("video_player_container"),
            contentAlignment = Alignment.Center
        ) {
            key(retryTrigger) {
                AndroidView(
                    modifier = Modifier
                        .aspectRatio(naturalVideoAspect)
                        .fillMaxSize()
                        .graphicsLayer(
                            scaleX = zoomScale,
                            scaleY = zoomScale
                        ),
                    factory = { ctx ->
                        VideoView(ctx).apply {
                            setVideoURI(videoItem.uri)
                            setOnPreparedListener { mp ->
                                mediaPlayerRef = mp
                                isBuffering = false
                                playbackError = null
                                if (duration > 0) {
                                    totalDurationMs = duration
                                }
                                if (mp.videoWidth > 0 && mp.videoHeight > 0) {
                                    videoWidth = mp.videoWidth
                                    videoHeight = mp.videoHeight
                                }
                                try {
                                    mp.setVideoScalingMode(
                                        if (isZoomToFit) {
                                            MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING
                                        } else {
                                            MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT
                                        }
                                    )
                                } catch (_: Exception) {}
                                try {
                                    val vol = if (isMuted) 0f else 1f
                                    mp.setVolume(vol, vol)
                                } catch (_: Exception) {}
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
                    },
                    update = { view ->
                        videoViewRef = view
                    }
                )
            }
        }

        // Playback Error Overlay
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
                    Button(
                        onClick = {
                            playbackError = null
                            isBuffering = true
                            retryTrigger++
                        }
                    ) {
                        Text("Retry")
                    }
                    OutlinedButton(onClick = handleClose) {
                        Text("Close", color = Color.White)
                    }
                }
            }
        }

        // Buffering Indicator
        if (isBuffering && playbackError == null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Color.White)
            }
        }

        // Zoom Mode Feedback Pill
        AnimatedVisibility(
            visible = showZoomNotice,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 72.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color.Black.copy(alpha = 0.75f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = if (isZoomToFit) Icons.Filled.CropFree else Icons.Filled.FitScreen,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = if (isZoomToFit) "Zoom to Fit (Immersive)" else "Original Aspect Fit",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }

        // Floating Translucent Controls (Tap anywhere on video to show/hide)
        AnimatedVisibility(
            visible = !isControlsHidden,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Minimal Top Floating Actions
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Black.copy(alpha = 0.65f), Color.Transparent)
                            )
                        )
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        IconButton(
                            onClick = handleClose,
                            modifier = Modifier
                                .size(40.dp)
                                .background(Color.Black.copy(alpha = 0.45f), CircleShape)
                                .testTag("video_player_close_button")
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
                            fontSize = 15.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Zoom to Fit / Original Fit Toggle
                        IconButton(
                            onClick = {
                                isZoomToFit = !isZoomToFit
                                showZoomNotice = true
                                try {
                                    mediaPlayerRef?.setVideoScalingMode(
                                        if (isZoomToFit) {
                                            MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING
                                        } else {
                                            MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT
                                        }
                                    )
                                } catch (_: Exception) {}
                            },
                            modifier = Modifier
                                .size(40.dp)
                                .background(
                                    if (isZoomToFit) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.45f),
                                    CircleShape
                                )
                                .testTag("video_player_best_fit_button")
                        ) {
                            Icon(
                                imageVector = if (isZoomToFit) Icons.Filled.CropFree else Icons.Filled.FitScreen,
                                contentDescription = "Zoom to Fit",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // Rotate Screen Orientation Toggle
                        IconButton(
                            onClick = {
                                val activity = context as? Activity
                                if (activity != null) {
                                    if (activity.requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE ||
                                        activity.requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                                    ) {
                                        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                                    } else {
                                        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                                    }
                                }
                            },
                            modifier = Modifier
                                .size(40.dp)
                                .background(Color.Black.copy(alpha = 0.45f), CircleShape)
                                .testTag("video_player_landscape_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.ScreenRotation,
                                contentDescription = "Rotate Screen",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // Mute Toggle
                        IconButton(
                            onClick = {
                                val nextMuted = !isMuted
                                isMuted = nextMuted
                                try {
                                    val vol = if (nextMuted) 0f else 1f
                                    mediaPlayerRef?.setVolume(vol, vol)
                                } catch (_: Exception) {}
                            },
                            modifier = Modifier
                                .size(40.dp)
                                .background(Color.Black.copy(alpha = 0.45f), CircleShape)
                                .testTag("video_player_mute_button")
                        ) {
                            Icon(
                                imageVector = if (isMuted) Icons.Filled.VolumeMute else Icons.Filled.VolumeUp,
                                contentDescription = "Mute toggle",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // Delete Video Button
                        if (onDeleteVideo != null) {
                            IconButton(
                                onClick = { showDeleteDialog = true },
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(Color.Black.copy(alpha = 0.45f), CircleShape)
                                    .testTag("video_player_delete_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Delete,
                                    contentDescription = "Delete video",
                                    tint = Color(0xFFFF6B6B),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }

                // Center Play / Pause & Quick Seek (-10s / +10s) Overlay
                Row(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalArrangement = Arrangement.spacedBy(28.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            videoViewRef?.let { vv ->
                                val target = maxOf(0, vv.currentPosition - 10000)
                                vv.seekTo(target)
                                currentPosMs = target
                            }
                        },
                        modifier = Modifier
                            .size(52.dp)
                            .background(Color.Black.copy(alpha = 0.45f), CircleShape)
                            .testTag("video_player_rewind_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.FastRewind,
                            contentDescription = "Rewind 10s",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
                        modifier = Modifier
                            .size(68.dp)
                            .clip(CircleShape)
                            .clickable { togglePlayPause() }
                            .testTag("video_player_play_pause")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = when {
                                    isCompleted -> Icons.Filled.Replay
                                    isPlaying -> Icons.Filled.Pause
                                    else -> Icons.Filled.PlayArrow
                                },
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                tint = Color.White,
                                modifier = Modifier.size(38.dp)
                            )
                        }
                    }

                    IconButton(
                        onClick = {
                            videoViewRef?.let { vv ->
                                val dur = if (vv.duration > 0) vv.duration else totalDurationMs
                                val target = minOf(dur, vv.currentPosition + 10000)
                                vv.seekTo(target)
                                currentPosMs = target
                            }
                        },
                        modifier = Modifier
                            .size(52.dp)
                            .background(Color.Black.copy(alpha = 0.45f), CircleShape)
                            .testTag("video_player_forward_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.FastForward,
                            contentDescription = "Forward 10s",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                // Bottom Floating Seek Bar & Pause Button
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f))
                            )
                        )
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        IconButton(
                            onClick = { togglePlayPause() },
                            modifier = Modifier
                                .size(38.dp)
                                .background(Color.White.copy(alpha = 0.15f), CircleShape)
                                .testTag("video_player_bottom_pause_btn")
                        ) {
                            Icon(
                                imageVector = when {
                                    isCompleted -> Icons.Filled.Replay
                                    isPlaying -> Icons.Filled.Pause
                                    else -> Icons.Filled.PlayArrow
                                },
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        Text(
                            text = formatTime(currentPosMs),
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )

                        Slider(
                            value = if (totalDurationMs > 0) (currentPosMs.toFloat() / totalDurationMs.toFloat()).coerceIn(0f, 1f) else 0f,
                            onValueChange = { frac ->
                                isUserScrubbing = true
                                val targetMs = (frac * totalDurationMs).toInt()
                                currentPosMs = targetMs
                                videoViewRef?.seekTo(targetMs)
                            },
                            onValueChangeFinished = {
                                isUserScrubbing = false
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("video_player_seek_slider"),
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary,
                                inactiveTrackColor = Color.White.copy(alpha = 0.35f)
                            )
                        )

                        Text(
                            text = formatTime(totalDurationMs),
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
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
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showDeleteDialog = false },
                    modifier = Modifier.testTag("cancel_delete_video_btn")
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}
