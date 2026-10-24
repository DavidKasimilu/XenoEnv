package com.example.telecom

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.data.MusicTrackItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

data class MusicPlaybackState(
    val playlist: List<MusicTrackItem> = emptyList(),
    val currentTrack: MusicTrackItem? = null,
    val isPlaying: Boolean = false,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val isRepeatOne: Boolean = false,
    val isShuffle: Boolean = false
)

object MusicPlaybackController {
    private val _state = MutableStateFlow(MusicPlaybackState())
    val state: StateFlow<MusicPlaybackState> = _state.asStateFlow()

    private var mediaPlayer: MediaPlayer? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var appContext: Context? = null

    private val progressRunnable = object : Runnable {
        override fun run() {
            val mp = mediaPlayer
            if (mp != null) {
                try {
                    if (mp.isPlaying) {
                        val pos = mp.currentPosition.toLong().coerceAtLeast(0L)
                        val dur = mp.duration.toLong().coerceAtLeast(_state.value.currentTrack?.durationMs ?: 0L)
                        _state.value = _state.value.copy(
                            isPlaying = true,
                            currentPositionMs = pos,
                            durationMs = dur
                        )
                    }
                } catch (_: Exception) {}
            }
            if (_state.value.isPlaying) {
                mainHandler.postDelayed(this, 500L)
            }
        }
    }

    fun setPlaylist(tracks: List<MusicTrackItem>, favoriteIds: Set<String>) {
        val synced = tracks.map { it.copy(isFavorite = favoriteIds.contains(it.id)) }
        val cur = _state.value.currentTrack
        val updatedCur = cur?.let { c ->
            synced.find { it.id == c.id } ?: c.copy(isFavorite = favoriteIds.contains(c.id))
        }
        _state.value = _state.value.copy(
            playlist = synced,
            currentTrack = updatedCur
        )
    }

    fun syncFavorites(favoriteIds: Set<String>) {
        val updatedList = _state.value.playlist.map {
            it.copy(isFavorite = favoriteIds.contains(it.id))
        }
        val cur = _state.value.currentTrack?.let {
            it.copy(isFavorite = favoriteIds.contains(it.id))
        }
        _state.value = _state.value.copy(
            playlist = updatedList,
            currentTrack = cur
        )
    }

    fun playTrack(context: Context, track: MusicTrackItem, queue: List<MusicTrackItem>? = null) {
        val ctx = context.applicationContext
        appContext = ctx
        val activeQueue = queue?.takeIf { it.isNotEmpty() }
            ?: _state.value.playlist.takeIf { it.isNotEmpty() }
            ?: listOf(track)

        _state.value = _state.value.copy(
            playlist = activeQueue,
            currentTrack = track,
            currentPositionMs = 0L,
            durationMs = track.durationMs
        )

        ensureForegroundServiceStarted(ctx)
        startMediaPlayerForTrack(ctx, track)
    }

    fun togglePlayPause(context: Context) {
        val ctx = context.applicationContext
        appContext = ctx
        val mp = mediaPlayer
        val curTrack = _state.value.currentTrack
        if (mp != null && curTrack != null) {
            try {
                if (mp.isPlaying) {
                    mp.pause()
                    mainHandler.removeCallbacks(progressRunnable)
                    _state.value = _state.value.copy(
                        isPlaying = false,
                        currentPositionMs = mp.currentPosition.toLong().coerceAtLeast(0L)
                    )
                    releaseBackupWakeLock()
                    XenoMusicPlaybackService.refreshNotification(ctx)
                } else {
                    requestMusicAudioFocus(ctx)
                    acquireBackupWakeLock(ctx)
                    ensureForegroundServiceStarted(ctx)
                    mp.start()
                    _state.value = _state.value.copy(isPlaying = true)
                    mainHandler.removeCallbacks(progressRunnable)
                    mainHandler.post(progressRunnable)
                    XenoMusicPlaybackService.refreshNotification(ctx)
                }
                return
            } catch (_: Exception) {}
        }

        val fallbackTrack = curTrack ?: _state.value.playlist.firstOrNull()
        if (fallbackTrack != null) {
            playTrack(ctx, fallbackTrack)
        }
    }

    fun playNext(context: Context) {
        val ctx = context.applicationContext
        val list = _state.value.playlist
        if (list.isEmpty()) return
        val cur = _state.value.currentTrack
        val nextTrack = if (_state.value.isShuffle && list.size > 1) {
            val candidates = list.filter { it.id != cur?.id }
            candidates.randomOrNull() ?: list.first()
        } else {
            val idx = list.indexOfFirst { it.id == cur?.id }
            if (idx < 0 || idx + 1 >= list.size) list.first() else list[idx + 1]
        }
        playTrack(ctx, nextTrack, list)
    }

    fun playPrevious(context: Context) {
        val ctx = context.applicationContext
        val mp = mediaPlayer
        try {
            if (mp != null && mp.currentPosition > 4000) {
                mp.seekTo(0)
                _state.value = _state.value.copy(currentPositionMs = 0L)
                return
            }
        } catch (_: Exception) {}

        val list = _state.value.playlist
        if (list.isEmpty()) return
        val cur = _state.value.currentTrack
        val idx = list.indexOfFirst { it.id == cur?.id }
        val prevTrack = if (idx <= 0) list.last() else list[idx - 1]
        playTrack(ctx, prevTrack, list)
    }

    fun seekTo(positionMs: Long) {
        val mp = mediaPlayer ?: return
        try {
            val dur = mp.duration.toLong().coerceAtLeast(1L)
            val target = positionMs.coerceIn(0L, dur)
            mp.seekTo(target.toInt())
            _state.value = _state.value.copy(currentPositionMs = target)
        } catch (_: Exception) {}
    }

    fun toggleRepeatOne() {
        _state.value = _state.value.copy(isRepeatOne = !_state.value.isRepeatOne)
    }

    fun toggleShuffle() {
        _state.value = _state.value.copy(isShuffle = !_state.value.isShuffle)
    }

    fun stopPlayback(context: Context) {
        mainHandler.removeCallbacks(progressRunnable)
        try {
            mediaPlayer?.stop()
        } catch (_: Exception) {}
        try {
            mediaPlayer?.release()
        } catch (_: Exception) {}
        mediaPlayer = null
        releaseBackupWakeLock()
        abandonMusicAudioFocus(context)
        _state.value = _state.value.copy(
            isPlaying = false,
            currentPositionMs = 0L
        )
    }

    private fun startMediaPlayerForTrack(context: Context, track: MusicTrackItem) {
        mainHandler.removeCallbacks(progressRunnable)
        try {
            mediaPlayer?.reset()
            mediaPlayer?.release()
        } catch (_: Exception) {}
        mediaPlayer = null

        requestMusicAudioFocus(context)
        acquireBackupWakeLock(context)

        try {
            val mp = MediaPlayer()
            mp.setWakeMode(context.applicationContext, PowerManager.PARTIAL_WAKE_LOCK)
            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )

            var dataSourceSet = false
            if (!track.filePath.isNullOrBlank()) {
                val f = File(track.filePath)
                if (f.exists() && f.canRead()) {
                    try {
                        mp.setDataSource(f.absolutePath)
                        dataSourceSet = true
                    } catch (_: Exception) {}
                }
            }
            if (!dataSourceSet) {
                mp.setDataSource(context.applicationContext, Uri.parse(track.uriString))
            }

            mp.setOnPreparedListener { player ->
                val resolvedDuration = player.duration.toLong().coerceAtLeast(track.durationMs)
                player.start()
                _state.value = _state.value.copy(
                    currentTrack = track,
                    isPlaying = true,
                    currentPositionMs = 0L,
                    durationMs = resolvedDuration
                )
                mainHandler.removeCallbacks(progressRunnable)
                mainHandler.post(progressRunnable)
                XenoMusicPlaybackService.refreshNotification(context.applicationContext)
            }

            mp.setOnCompletionListener {
                if (_state.value.isRepeatOne) {
                    val cur = _state.value.currentTrack ?: track
                    playTrack(context.applicationContext, cur, _state.value.playlist)
                } else {
                    playNext(context.applicationContext)
                }
            }

            mp.setOnErrorListener { _, _, _ ->
                _state.value = _state.value.copy(isPlaying = false)
                true
            }

            mediaPlayer = mp
            mp.prepareAsync()
        } catch (_: Exception) {
            _state.value = _state.value.copy(isPlaying = false)
        }
    }

    private fun ensureForegroundServiceStarted(context: Context) {
        try {
            val intent = Intent(context, XenoMusicPlaybackService::class.java).apply {
                action = XenoMusicPlaybackService.ACTION_START_OR_UPDATE
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        } catch (_: Exception) {}
    }

    private fun acquireBackupWakeLock(context: Context) {
        try {
            if (wakeLock?.isHeld == true) return
            val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
            wakeLock = pm.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "Xeno:CloneSpaceMusicWakeLock"
            ).apply {
                setReferenceCounted(false)
                acquire(6 * 60 * 60 * 1000L) // Up to 6 hours continuous screen-off playback
            }
        } catch (_: Exception) {}
    }

    private fun releaseBackupWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (_: Exception) {}
        wakeLock = null
    }

    private fun requestMusicAudioFocus(context: Context) {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val req = audioFocusRequest ?: AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    .setAcceptsDelayedFocusGain(true)
                    .setOnAudioFocusChangeListener { focusChange ->
                        when (focusChange) {
                            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                                try { mediaPlayer?.setVolume(0.25f, 0.25f) } catch (_: Exception) {}
                            }
                            AudioManager.AUDIOFOCUS_GAIN -> {
                                try { mediaPlayer?.setVolume(1.0f, 1.0f) } catch (_: Exception) {}
                            }
                        }
                    }
                    .build().also { audioFocusRequest = it }
                am.requestAudioFocus(req)
            } else {
                @Suppress("DEPRECATION")
                am.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
            }
        } catch (_: Exception) {}
    }

    private fun abandonMusicAudioFocus(context: Context) {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioFocusRequest?.let { am.abandonAudioFocusRequest(it) }
            }
        } catch (_: Exception) {}
    }
}

class XenoMusicPlaybackService : Service() {

    companion object {
        const val CHANNEL_ID = "xeno_clone_music_playback_channel"
        const val NOTIFICATION_ID = 4409
        const val ACTION_START_OR_UPDATE = "com.example.telecom.MUSIC_START_OR_UPDATE"
        const val ACTION_PLAY_PAUSE = "com.example.telecom.MUSIC_PLAY_PAUSE"
        const val ACTION_NEXT = "com.example.telecom.MUSIC_NEXT"
        const val ACTION_PREVIOUS = "com.example.telecom.MUSIC_PREVIOUS"
        const val ACTION_STOP = "com.example.telecom.MUSIC_STOP"

        fun refreshNotification(context: Context) {
            try {
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
                ensureChannel(context, nm)
                nm.notify(NOTIFICATION_ID, buildNotification(context))
            } catch (_: Exception) {}
        }

        private fun ensureChannel(context: Context, nm: NotificationManager) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "Clone Space Music Player",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Background MP3 playback when screen is off or sleeping"
                    setShowBadge(false)
                }
                nm.createNotificationChannel(channel)
            }
        }

        fun buildNotification(context: Context): Notification {
            val state = MusicPlaybackController.state.value
            val trackTitle = state.currentTrack?.title ?: "Clone Space Music Player"
            val trackSub = state.currentTrack?.let { "${it.artist} • ${it.folderName}" } ?: "Ready to play MP3s"

            val openAppIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("open_music_player", true)
            }
            val contentPendingIntent = PendingIntent.getActivity(
                context,
                401,
                openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val prevIntent = PendingIntent.getService(
                context,
                402,
                Intent(context, XenoMusicPlaybackService::class.java).apply { action = ACTION_PREVIOUS },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val playPauseIntent = PendingIntent.getService(
                context,
                403,
                Intent(context, XenoMusicPlaybackService::class.java).apply { action = ACTION_PLAY_PAUSE },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val nextIntent = PendingIntent.getService(
                context,
                404,
                Intent(context, XenoMusicPlaybackService::class.java).apply { action = ACTION_NEXT },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            return NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle(trackTitle)
                .setContentText(trackSub)
                .setSubText(if (state.isPlaying) "Playing (Screen-Off Active)" else "Paused")
                .setContentIntent(contentPendingIntent)
                .setOngoing(state.isPlaying)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .addAction(android.R.drawable.ic_media_previous, "Previous", prevIntent)
                .addAction(
                    if (state.isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                    if (state.isPlaying) "Pause" else "Play",
                    playPauseIntent
                )
                .addAction(android.R.drawable.ic_media_next, "Next", nextIntent)
                .build()
        }
    }

    override fun onCreate() {
        super.onCreate()
        startForegroundWithNotification()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundWithNotification()
        when (intent?.action) {
            ACTION_PLAY_PAUSE -> MusicPlaybackController.togglePlayPause(applicationContext)
            ACTION_NEXT -> MusicPlaybackController.playNext(applicationContext)
            ACTION_PREVIOUS -> MusicPlaybackController.playPrevious(applicationContext)
            ACTION_STOP -> {
                MusicPlaybackController.stopPlayback(applicationContext)
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
        }
        return START_STICKY
    }

    private fun startForegroundWithNotification() {
        try {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            if (nm != null) {
                ensureChannel(applicationContext, nm)
            }
            val notification = buildNotification(applicationContext)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (_: Exception) {}
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
