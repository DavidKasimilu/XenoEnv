package com.example.ui.components

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.media.MediaPlayer
import android.net.Uri
import android.util.Log
import android.widget.VideoView
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.data.models.MediaItem
import kotlinx.coroutines.delay
import java.io.File
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
    var isPlaying by remember { mutableStateOf(false) }
    val context = LocalContext.current
    var currentPosMs by remember { mutableIntStateOf(0) }
    var totalDurationMs by remember { mutableIntStateOf((videoItem.durationMs ?: 0L).toInt()) }
    var isMuted by remember { mutableStateOf(false) }
    var isBuffering by remember { mutableStateOf(true) }
    var videoViewRef by remember { mutableStateOf<VideoView?>(null) }
    var isCompleted by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    // Best fit mode states (fits video to screen preserving natural aspect ratio)
    var isBestFit by remember { mutableStateOf(false) }
    var videoWidth by remember { mutableIntStateOf(if (videoItem.width > 0) videoItem.width else 16) }
    var videoHeight by remember { mutableIntStateOf(if (videoItem.height > 0) videoItem.height else 9) }
    var showBestFitNotice by remember { mutableStateOf(false) }

    var playbackError by remember { mutableStateOf<String?>(null) }
    var retryTrigger by remember { mutableIntStateOf(0) }

    fun cleanupMediaResources() {
        try {
            videoViewRef?.stopPlayback()
        } catch (_: Exception) {}
        videoViewRef = null
    }

    val handleClose = {
        cleanupMediaResources()
        val activity = context as? Activity
        activity?.window?.let { window ->
            val insetsController = WindowCompat.getInsetsController(window, window.decorView)
            insetsController.show(WindowInsetsCompat.Type.systemBars())
        }
        onClose()
    }

    BackHandler {
        handleClose()
    }

    // Auto-dismiss transient notice
    LaunchedEffect(showBestFitNotice) {
        if (showBestFitNotice) {
            delay(2200L)
            showBestFitNotice = false
        }
    }

    fun applyBestFitMode() {
        isBestFit = !isBestFit
        showBestFitNotice = true
    }

    // Polling loop to sync seekbar position
    LaunchedEffect(isPlaying) {
        while (isPlaying) {
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
            delay(300)
        }
    }

    fun formatTime(ms: Int): String {
        val totalSec = ms / 1000
        val min = totalSec / 60
        val sec = totalSec % 60
        return String.format(Locale.getDefault(), "%02d:%02d", min, sec)
    }

    DisposableEffect(Unit) {
        onDispose {
            cleanupMediaResources()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable {
                val nextHidden = !isControlsHidden
                onToggleHide(nextHidden)
            }
            .testTag("video_player_modal")
    ) {
        // VideoView Container
        Box(
            modifier = Modifier
                .fillMaxSize()
                .align(Alignment.Center)
                .testTag("video_player_container"),
            contentAlignment = Alignment.Center
        ) {
            val videoModifier = if (isBestFit && videoWidth > 0 && videoHeight > 0) {
                Modifier
                    .aspectRatio(videoWidth.toFloat() / videoHeight.toFloat())
                    .fillMaxWidth()
            } else {
                Modifier.fillMaxSize()
            }

            key(retryTrigger) {
                AndroidView(
                    modifier = videoModifier,
                    factory = { ctx ->
                        VideoView(ctx).apply {
                            setVideoURI(videoItem.uri)
                            setOnPreparedListener { mp ->
                                isBuffering = false
                                playbackError = null
                                totalDurationMs = duration
                                if (mp.videoWidth > 0 && mp.videoHeight > 0) {
                                    videoWidth = mp.videoWidth
                                    videoHeight = mp.videoHeight
                                }
                                if (isMuted) {
                                    try { mp.setVolume(0f, 0f) } catch (_: Exception) {}
                                } else {
                                    try { mp.setVolume(1f, 1f) } catch (_: Exception) {}
                                }
                                start()
                                isPlaying = true
                            }
                            setOnCompletionListener {
                                isPlaying = false
                                isCompleted = true
                            }
                            setOnErrorListener { _, what, extra ->
                                Log.e("VideoView", "Playback error: what=$what, extra=$extra")
                                isBuffering = false
                                playbackError = "Unable to play video (error $what). Format may be unsupported."
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
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.size(56.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = playbackError ?: "Playback failed",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
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
                        Text("Retry Playback")
                    }
                    OutlinedButton(
                        onClick = {
                            try {
                                val playIntent = Intent(Intent.ACTION_VIEW).apply {
                                    setDataAndType(videoItem.uri, "video/*")
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(playIntent)
                            } catch (e: Exception) {
                                android.widget.Toast.makeText(context, "No external video player found", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }
                    ) {
                        Text("Open External")
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

        // Best Fit Feedback Notice Overlay
        AnimatedVisibility(
            visible = showBestFitNotice,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 90.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = if (isBestFit) MaterialTheme.colorScheme.primaryContainer else Color.Black.copy(alpha = 0.75f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.35f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.FitScreen,
                        contentDescription = null,
                        tint = if (isBestFit) MaterialTheme.colorScheme.onPrimaryContainer else Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = if (isBestFit) "Best Fit Mode (Aspect Ratio Preserved)" else "Standard View Restored",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isBestFit) MaterialTheme.colorScheme.onPrimaryContainer else Color.White
                    )
                }
            }
        }

        if (!isControlsHidden) {
            // Top Bar Controls (Title, Close Button, Landscape, Hide, Best Fit, Mute, Delete)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 40.dp, start = 16.dp, end = 16.dp)
                    .align(Alignment.TopCenter),
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
                            .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                            .testTag("video_player_close_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Close",
                            tint = Color.White
                        )
                    }

                    Column {
                        Text(
                            text = videoItem.displayName,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            maxLines = 1
                        )
                        if (isBestFit) {
                            Text(
                                text = "Best Fit Active",
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Landscape Toggle
                    IconButton(
                        onClick = {
                            val activity = context as? Activity
                            if (activity != null) {
                                if (activity.requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE) {
                                    activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                                } else {
                                    activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                                }
                            }
                        },
                        modifier = Modifier
                            .size(40.dp)
                            .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                            .testTag("video_player_landscape_button")
                    ) {
                        Text("Land", color = Color.White, fontSize = 11.sp)
                    }

                    // Hide Controls Button
                    IconButton(
                        onClick = { onToggleHide(true) },
                        modifier = Modifier
                            .size(40.dp)
                            .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                            .testTag("video_player_hide_button")
                    ) {
                        Text("Hide", color = Color.White, fontSize = 11.sp)
                    }

                    // Best Fit Button
                    IconButton(
                        onClick = { applyBestFitMode() },
                        modifier = Modifier
                            .size(40.dp)
                            .background(
                                if (isBestFit) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.5f),
                                CircleShape
                            )
                            .testTag("video_player_best_fit_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.FitScreen,
                            contentDescription = "Best fit video to screen",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Mute Toggle Button
                    IconButton(
                        onClick = {
                            val nextMuted = !isMuted
                            isMuted = nextMuted
                            videoViewRef?.let { vv ->
                                // VideoView doesn't have a direct setVolume without media player, but we can access or use audio attributes if needed
                            }
                        },
                        modifier = Modifier
                            .size(40.dp)
                            .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                            .testTag("video_player_mute_button")
                    ) {
                        Icon(
                            imageVector = if (isMuted) Icons.Filled.VolumeMute else Icons.Filled.VolumeUp,
                            contentDescription = "Mute toggle",
                            tint = Color.White
                        )
                    }

                    // Delete Video Button
                    if (onDeleteVideo != null) {
                        IconButton(
                            onClick = { showDeleteDialog = true },
                            modifier = Modifier
                                .size(40.dp)
                                .background(Color.Black.copy(alpha = 0.5f), CircleShape)
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

            // Bottom Controls Overlay
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(Color.Black.copy(alpha = 0.65f))
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
                // Seek bar & Timestamps
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = formatTime(currentPosMs),
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )

                    Slider(
                        value = if (totalDurationMs > 0) (currentPosMs.toFloat() / totalDurationMs.toFloat()).coerceIn(0f, 1f) else 0f,
                        onValueChange = { frac ->
                            val targetMs = (frac * totalDurationMs).toInt()
                            currentPosMs = targetMs
                            videoViewRef?.seekTo(targetMs)
                        },
                        modifier = Modifier.weight(1f),
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                        )
                    )

                    Text(
                        text = formatTime(totalDurationMs),
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                // Play / Pause / Replay Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
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
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(imageVector = Icons.Filled.FastRewind, contentDescription = "Seek Backward", tint = Color.White)
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .clickable {
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
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    IconButton(
                        onClick = {
                            videoViewRef?.let { vv ->
                                val target = minOf(vv.duration, vv.currentPosition + 10000)
                                vv.seekTo(target)
                                currentPosMs = target
                            }
                        },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(imageVector = Icons.Filled.FastForward, contentDescription = "Seek Forward", tint = Color.White)
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
