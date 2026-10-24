package com.example.ui.screens

import android.content.Context
import android.graphics.BitmapFactory
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.widget.MediaController
import android.widget.Toast
import android.widget.VideoView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.SmartDisplay
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

data class YouTubeOfflineVideo(
    val videoId: String,
    val title: String,
    val channelName: String,
    val durationText: String,
    val localVideoPath: String,
    val localThumbnailPath: String?,
    val fileSizeBytes: Long,
    val downloadedAt: Long
)

object YouTubeOfflineRepository {
    private const val PREFS_NAME = "xeno_youtube_offline_prefs"
    private const val KEY_VIDEOS_JSON = "offline_videos_json"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _downloadsFlow = MutableStateFlow<List<YouTubeOfflineVideo>>(emptyList())
    val downloadsFlow: StateFlow<List<YouTubeOfflineVideo>> = _downloadsFlow.asStateFlow()

    // Maps videoId -> download progress (0..100)
    private val _activeDownloadsFlow = MutableStateFlow<Map<String, Int>>(emptyMap())
    val activeDownloadsFlow: StateFlow<Map<String, Int>> = _activeDownloadsFlow.asStateFlow()

    // Live stream URLs captured from WebView playback (videoId -> CapturedMediaStreams)
    private data class CapturedMediaStreams(
        var progressiveMp4Url: String? = null,
        var adaptiveVideoMp4Url: String? = null,
        var adaptiveAudioMp4Url: String? = null,
        var userAgent: String = ""
    )

    private val capturedStreamsByVideoId = ConcurrentHashMap<String, CapturedMediaStreams>()
    @Volatile
    private var latestCapturedStreams = CapturedMediaStreams()

    /**
     * Called from WebViewClient.onLoadResource whenever YouTube's HTML5 player requests a
     * `googlevideo.com/videoplayback` segment. Stripping `&range=`, `&rn=`, `&rbuf=`, and `&ump=`
     * yields a signed stream URL bound to the user's exact IP and session.
     */
    fun onCapturedGoogleVideoUrl(pageUrl: String?, resourceUrl: String, userAgent: String) {
        if (!resourceUrl.contains("googlevideo.com/videoplayback")) return
        try {
            val cleanedUrl = sanitizeGoogleVideoUrl(resourceUrl)
            if (!cleanedUrl.startsWith("http")) return

            val videoId = extractVideoIdFromPageUrl(pageUrl ?: "")
            val entry = if (videoId.isNotBlank()) {
                capturedStreamsByVideoId.getOrPut(videoId) { CapturedMediaStreams(userAgent = userAgent) }
            } else {
                latestCapturedStreams
            }
            entry.userAgent = userAgent

            val isItag18Or22 = cleanedUrl.contains("itag=18&") || cleanedUrl.contains("itag=22&") ||
                cleanedUrl.endsWith("itag=18") || cleanedUrl.endsWith("itag=22")
            val isVideoMp4 = cleanedUrl.contains("mime=video%2Fmp4", ignoreCase = true) ||
                cleanedUrl.contains("mime=video/mp4", ignoreCase = true)
            val isAudioMp4 = cleanedUrl.contains("mime=audio%2Fmp4", ignoreCase = true) ||
                cleanedUrl.contains("mime=audio/mp4", ignoreCase = true) ||
                cleanedUrl.contains("itag=140&")

            when {
                isItag18Or22 -> {
                    entry.progressiveMp4Url = cleanedUrl
                    latestCapturedStreams.progressiveMp4Url = cleanedUrl
                    latestCapturedStreams.userAgent = userAgent
                }
                isVideoMp4 -> {
                    entry.adaptiveVideoMp4Url = cleanedUrl
                    latestCapturedStreams.adaptiveVideoMp4Url = cleanedUrl
                    latestCapturedStreams.userAgent = userAgent
                }
                isAudioMp4 -> {
                    entry.adaptiveAudioMp4Url = cleanedUrl
                    latestCapturedStreams.adaptiveAudioMp4Url = cleanedUrl
                    latestCapturedStreams.userAgent = userAgent
                }
            }
        } catch (_: Exception) {}
    }

    fun sanitizeGoogleVideoUrl(rawUrl: String): String {
        if (!rawUrl.contains("googlevideo.com/videoplayback")) return rawUrl.trim()
        return rawUrl.trim()
            .replace(Regex("([?&])range=[^&]+"), "$1")
            .replace(Regex("([?&])rn=[^&]+"), "$1")
            .replace(Regex("([?&])rbuf=[^&]+"), "$1")
            .replace(Regex("([?&])ump=[^&]+"), "$1")
            .replace(Regex("([?&])srfvp=[^&]+"), "$1")
            .replace("?&", "?")
            .replace(Regex("&+"), "&")
            .trimEnd('&', '?')
    }

    private fun extractVideoIdFromPageUrl(url: String): String {
        return when {
            url.contains("v=") -> url.substringAfter("v=").substringBefore("&").substringBefore("#").trim()
            url.contains("/shorts/") -> url.substringAfter("/shorts/").substringBefore("?").substringBefore("/").trim()
            else -> ""
        }
    }

    fun isDeviceOnline(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return true
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val net = cm.activeNetwork ?: return false
                val caps = cm.getNetworkCapabilities(net) ?: return false
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            } else {
                @Suppress("DEPRECATION")
                cm.activeNetworkInfo?.isConnected == true
            }
        } catch (_: Exception) {
            true
        }
    }

    private fun getOfflineDir(context: Context): File {
        val dir = context.getExternalFilesDir("youtube_offline")
            ?: File(context.filesDir, "youtube_offline")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun loadSavedVideos(context: Context): List<YouTubeOfflineVideo> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_VIDEOS_JSON, null) ?: run {
            _downloadsFlow.value = emptyList()
            return emptyList()
        }
        val list = mutableListOf<YouTubeOfflineVideo>()
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val videoPath = obj.optString("localVideoPath", "")
                val file = File(videoPath)
                if (videoPath.isNotBlank() && file.exists() && file.length() > 0L) {
                    list.add(
                        YouTubeOfflineVideo(
                            videoId = obj.optString("videoId", ""),
                            title = obj.optString("title", "YouTube Video"),
                            channelName = obj.optString("channelName", "YouTube"),
                            durationText = obj.optString("durationText", ""),
                            localVideoPath = videoPath,
                            localThumbnailPath = obj.optString("localThumbnailPath", "").takeIf { it.isNotBlank() && File(it).exists() },
                            fileSizeBytes = file.length(),
                            downloadedAt = obj.optLong("downloadedAt", System.currentTimeMillis())
                        )
                    )
                }
            }
        } catch (_: Exception) {}
        _downloadsFlow.value = list
        return list
    }

    private fun saveVideosList(context: Context, list: List<YouTubeOfflineVideo>) {
        val arr = JSONArray()
        for (item in list) {
            val obj = JSONObject().apply {
                put("videoId", item.videoId)
                put("title", item.title)
                put("channelName", item.channelName)
                put("durationText", item.durationText)
                put("localVideoPath", item.localVideoPath)
                put("localThumbnailPath", item.localThumbnailPath ?: "")
                put("fileSizeBytes", item.fileSizeBytes)
                put("downloadedAt", item.downloadedAt)
            }
            arr.put(obj)
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_VIDEOS_JSON, arr.toString())
            .apply()
        _downloadsFlow.value = list
    }

    fun deleteOfflineVideo(context: Context, videoId: String) {
        val current = loadSavedVideos(context)
        val target = current.find { it.videoId == videoId }
        target?.let {
            try { File(it.localVideoPath).delete() } catch (_: Exception) {}
            try { it.localThumbnailPath?.let { p -> File(p).delete() } } catch (_: Exception) {}
        }
        val updated = current.filter { it.videoId != videoId }
        saveVideosList(context, updated)
    }

    fun isVideoDownloaded(context: Context, videoId: String): Boolean {
        if (videoId.isBlank()) return false
        return _downloadsFlow.value.any { it.videoId == videoId } ||
                loadSavedVideos(context).any { it.videoId == videoId }
    }

    fun getDownloadProgress(videoId: String): Int {
        return _activeDownloadsFlow.value[videoId] ?: -1
    }

    fun startVideoDownload(
        context: Context,
        mainHandler: Handler,
        videoId: String,
        rawTitle: String,
        rawChannel: String,
        durationSecondsStr: String,
        directStreamUrl: String,
        userAgent: String,
        onCompleted: (Boolean, String) -> Unit = { _, _ -> }
    ) {
        val cleanId = videoId.trim().substringBefore("&").substringBefore("?").substringBefore("/").takeIf { it.isNotBlank() } ?: return
        if (_activeDownloadsFlow.value.containsKey(cleanId)) {
            mainHandler.post {
                Toast.makeText(context, "Already downloading this video...", Toast.LENGTH_SHORT).show()
            }
            return
        }

        val appCtx = context.applicationContext
        _activeDownloadsFlow.value = _activeDownloadsFlow.value + (cleanId to 2)

        mainHandler.post {
            Toast.makeText(
                appCtx,
                "Downloading \"${rawTitle.ifBlank { "YouTube Video" }}\" for offline playback...",
                Toast.LENGTH_SHORT
            ).show()
        }

        scope.launch {
            var finalTitle = rawTitle.trim().removeSuffix("- YouTube").trim().ifBlank { "YouTube Video ($cleanId)" }
            var finalAuthor = rawChannel.trim().ifBlank { "YouTube Channel" }
            var finalDurationSec = durationSecondsStr.trim().toLongOrNull() ?: 0L
            val offlineDir = getOfflineDir(appCtx)
            val videoFile = File(offlineDir, "yt_${cleanId}.mp4")
            val tempFile = File(offlineDir, "yt_${cleanId}.part")
            val tempVideoOnlyFile = File(offlineDir, "yt_${cleanId}_v.part")
            val tempAudioOnlyFile = File(offlineDir, "yt_${cleanId}_a.part")
            val thumbFile = File(offlineDir, "yt_${cleanId}.jpg")

            try {
                val cookies = try {
                    CookieManager.getInstance().getCookie("https://www.youtube.com")
                        ?: CookieManager.getInstance().getCookie("https://m.youtube.com")
                        ?: ""
                } catch (_: Exception) {
                    ""
                }

                _activeDownloadsFlow.value = _activeDownloadsFlow.value + (cleanId to 6)

                // Parse directStreamUrl if WebView provided a single URL or "videoUrl|||audioUrl" pair
                var pageProgressiveUrl: String? = null
                var pageAdaptiveVideoUrl: String? = null
                var pageAdaptiveAudioUrl: String? = null

                if (directStreamUrl.isNotBlank()) {
                    if (directStreamUrl.contains("|||")) {
                        val parts = directStreamUrl.split("|||")
                        val vPart = sanitizeGoogleVideoUrl(parts.getOrNull(0) ?: "")
                        val aPart = sanitizeGoogleVideoUrl(parts.getOrNull(1) ?: "")
                        if (vPart.startsWith("http") && aPart.startsWith("http")) {
                            pageAdaptiveVideoUrl = vPart
                            pageAdaptiveAudioUrl = aPart
                        } else if (vPart.startsWith("http")) {
                            pageProgressiveUrl = vPart
                        }
                    } else {
                        val cleaned = sanitizeGoogleVideoUrl(directStreamUrl)
                        if (cleaned.startsWith("http://") || cleaned.startsWith("https://")) {
                            pageProgressiveUrl = cleaned
                        }
                    }
                }

                // Also check WebView network sniffer cache for this videoId
                val captured = capturedStreamsByVideoId[cleanId] ?: latestCapturedStreams
                if (pageProgressiveUrl.isNullOrBlank() && !captured.progressiveMp4Url.isNullOrBlank()) {
                    pageProgressiveUrl = captured.progressiveMp4Url
                }
                if (pageAdaptiveVideoUrl.isNullOrBlank() && !captured.adaptiveVideoMp4Url.isNullOrBlank()) {
                    pageAdaptiveVideoUrl = captured.adaptiveVideoMp4Url
                    pageAdaptiveAudioUrl = captured.adaptiveAudioMp4Url
                }

                // Resolve via InnerTube multi-client engine + public API fallback
                _activeDownloadsFlow.value = _activeDownloadsFlow.value + (cleanId to 10)
                val innertubeResult = resolveInnerTubeStream(cleanId, cookies) ?: resolvePipedOrInvidiousStream(cleanId)

                if (innertubeResult != null) {
                    if (innertubeResult.title.isNotBlank() && (finalTitle.startsWith("YouTube Video") || finalTitle.length < 4)) {
                        finalTitle = innertubeResult.title
                    }
                    if (innertubeResult.author.isNotBlank() && (finalAuthor == "YouTube Channel" || finalAuthor == "YouTube")) {
                        finalAuthor = innertubeResult.author
                    }
                    if (innertubeResult.durationSec > 0L && finalDurationSec <= 0L) {
                        finalDurationSec = innertubeResult.durationSec
                    }
                }

                // Download thumbnail for offline library card
                try {
                    val thumbUrls = listOf(
                        "https://i.ytimg.com/vi/$cleanId/hqdefault.jpg",
                        "https://i.ytimg.com/vi/$cleanId/mqdefault.jpg"
                    )
                    for (tUrl in thumbUrls) {
                        if (downloadBinaryFile(tUrl, thumbFile, userAgent, "", null)) {
                            break
                        }
                    }
                } catch (_: Exception) {}

                _activeDownloadsFlow.value = _activeDownloadsFlow.value + (cleanId to 15)

                var downloadedOk = false

                // Candidate list of progressive (single-file video+audio MP4) streams
                val progressiveCandidates = mutableListOf<Pair<String, String>>()
                if (!pageProgressiveUrl.isNullOrBlank()) {
                    progressiveCandidates.add(pageProgressiveUrl to userAgent)
                }
                if (innertubeResult != null) {
                    if (innertubeResult.progressiveUrls.isNotEmpty()) {
                        for (pUrl in innertubeResult.progressiveUrls) {
                            progressiveCandidates.add(pUrl to innertubeResult.clientUserAgent)
                        }
                    }
                }

                // Step A: Try all progressive MP4 streams first (single file with both video & audio)
                for ((candUrl, candUa) in progressiveCandidates) {
                    if (downloadStreamWithRangeFallback(
                            urlStr = candUrl,
                            destFile = tempFile,
                            userAgent = candUa,
                            cookies = cookies
                        ) { progressPct ->
                            val mapped = (15 + (progressPct * 0.82f).toInt()).coerceIn(15, 98)
                            _activeDownloadsFlow.value = _activeDownloadsFlow.value + (cleanId to mapped)
                        }
                    ) {
                        downloadedOk = true
                        break
                    }
                }

                // Step B: If no progressive stream succeeded, try Adaptive Video MP4 + Adaptive Audio M4A muxing
                if (!downloadedOk) {
                    val adaptiveVideoCandidates = mutableListOf<Pair<String, String>>()
                    if (!pageAdaptiveVideoUrl.isNullOrBlank()) {
                        adaptiveVideoCandidates.add(pageAdaptiveVideoUrl to userAgent)
                    }
                    if (innertubeResult != null) {
                        for (vUrl in innertubeResult.adaptiveVideoUrls) {
                            adaptiveVideoCandidates.add(vUrl to innertubeResult.clientUserAgent)
                        }
                    }

                    val adaptiveAudioUrl = pageAdaptiveAudioUrl?.takeIf { it.isNotBlank() }
                        ?: innertubeResult?.adaptiveAudioUrl
                    val adaptiveAudioUa = if (!pageAdaptiveAudioUrl.isNullOrBlank()) userAgent else (innertubeResult?.clientUserAgent ?: userAgent)

                    for ((vUrl, vUa) in adaptiveVideoCandidates) {
                        val videoOk = downloadStreamWithRangeFallback(
                            urlStr = vUrl,
                            destFile = tempVideoOnlyFile,
                            userAgent = vUa,
                            cookies = cookies
                        ) { progressPct ->
                            val mapped = (15 + (progressPct * 0.60f).toInt()).coerceIn(15, 75)
                            _activeDownloadsFlow.value = _activeDownloadsFlow.value + (cleanId to mapped)
                        }

                        if (videoOk && tempVideoOnlyFile.exists() && tempVideoOnlyFile.length() > 1024L) {
                            var muxedOk = false
                            if (!adaptiveAudioUrl.isNullOrBlank()) {
                                val audioOk = downloadStreamWithRangeFallback(
                                    urlStr = adaptiveAudioUrl,
                                    destFile = tempAudioOnlyFile,
                                    userAgent = adaptiveAudioUa,
                                    cookies = cookies
                                ) { progressPct ->
                                    val mapped = (75 + (progressPct * 0.18f).toInt()).coerceIn(75, 93)
                                    _activeDownloadsFlow.value = _activeDownloadsFlow.value + (cleanId to mapped)
                                }
                                if (audioOk && tempAudioOnlyFile.exists() && tempAudioOnlyFile.length() > 512L) {
                                    _activeDownloadsFlow.value = _activeDownloadsFlow.value + (cleanId to 95)
                                    muxedOk = muxVideoAndAudioMp4(tempVideoOnlyFile, tempAudioOnlyFile, tempFile)
                                }
                            }
                            if (!muxedOk) {
                                // Use the video MP4 directly if audio track muxing was not needed or skipped
                                if (tempFile.exists()) tempFile.delete()
                                tempVideoOnlyFile.renameTo(tempFile)
                            }
                            downloadedOk = tempFile.exists() && tempFile.length() > 1024L
                            break
                        }
                    }
                }

                try { if (tempVideoOnlyFile.exists()) tempVideoOnlyFile.delete() } catch (_: Exception) {}
                try { if (tempAudioOnlyFile.exists()) tempAudioOnlyFile.delete() } catch (_: Exception) {}

                if (downloadedOk && tempFile.exists() && tempFile.length() > 1024L) {
                    if (videoFile.exists()) videoFile.delete()
                    tempFile.renameTo(videoFile)

                    val durationFormatted = formatDurationSeconds(finalDurationSec)
                    val newEntry = YouTubeOfflineVideo(
                        videoId = cleanId,
                        title = finalTitle,
                        channelName = finalAuthor,
                        durationText = durationFormatted,
                        localVideoPath = videoFile.absolutePath,
                        localThumbnailPath = thumbFile.absolutePath.takeIf { thumbFile.exists() && thumbFile.length() > 0L },
                        fileSizeBytes = videoFile.length(),
                        downloadedAt = System.currentTimeMillis()
                    )

                    val existing = loadSavedVideos(appCtx).filter { it.videoId != cleanId }
                    saveVideosList(appCtx, listOf(newEntry) + existing)

                    _activeDownloadsFlow.value = _activeDownloadsFlow.value - cleanId
                    mainHandler.post {
                        Toast.makeText(
                            appCtx,
                            "Downloaded \"$finalTitle\" for offline viewing!",
                            Toast.LENGTH_SHORT
                        ).show()
                        onCompleted(true, finalTitle)
                    }
                } else {
                    try { tempFile.delete() } catch (_: Exception) {}
                    _activeDownloadsFlow.value = _activeDownloadsFlow.value - cleanId
                    mainHandler.post {
                        Toast.makeText(
                            appCtx,
                            "Could not download this video right now. Please let the video play for 2 seconds and tap Download again.",
                            Toast.LENGTH_LONG
                        ).show()
                        onCompleted(false, finalTitle)
                    }
                }
            } catch (e: Exception) {
                try { tempFile.delete() } catch (_: Exception) {}
                try { tempVideoOnlyFile.delete() } catch (_: Exception) {}
                try { tempAudioOnlyFile.delete() } catch (_: Exception) {}
                _activeDownloadsFlow.value = _activeDownloadsFlow.value - cleanId
                mainHandler.post {
                    Toast.makeText(
                        appCtx,
                        "Download error: ${e.localizedMessage ?: "Failed"}",
                        Toast.LENGTH_SHORT
                    ).show()
                    onCompleted(false, finalTitle)
                }
            }
        }
    }

    private data class InnerTubeStreamInfo(
        val progressiveUrls: List<String>,
        val adaptiveVideoUrls: List<String>,
        val adaptiveAudioUrl: String?,
        val title: String,
        val author: String,
        val durationSec: Long,
        val clientUserAgent: String
    )

    private fun resolveInnerTubeStream(videoId: String, cookies: String): InnerTubeStreamInfo? {
        data class ClientProfile(
            val clientName: String,
            val clientVersion: String,
            val clientIdHeader: String,
            val userAgent: String,
            val osName: String,
            val osVersion: String,
            val androidSdkVersion: Int? = null,
            val deviceMake: String = "",
            val deviceModel: String = "",
            val useCookies: Boolean = false,
            val thirdPartyEmbedUrl: String? = null
        )

        val profiles = listOf(
            // 1. ANDROID_VR (Oculus Quest 3) WITHOUT cookies — returns direct progressive & adaptive streams reliably
            ClientProfile(
                clientName = "ANDROID_VR",
                clientVersion = "1.62.27",
                clientIdHeader = "28",
                userAgent = "com.google.android.apps.youtube.vr.oculus/1.62.27 (Linux; U; Android 12L; eureka-user Build/SQ3A.220605.009.A1) gzip",
                osName = "Android",
                osVersion = "12L",
                androidSdkVersion = 32,
                deviceMake = "Oculus",
                deviceModel = "Quest 3",
                useCookies = false
            ),
            // 2. IOS WITHOUT cookies — returns progressive MP4 / adaptive MP4 without poToken requirement
            ClientProfile(
                clientName = "IOS",
                clientVersion = "20.03.02",
                clientIdHeader = "5",
                userAgent = "com.google.ios.youtube/20.03.02 (iPhone16,2; U; CPU iOS 18_2_1 like Mac OS X;)",
                osName = "iOS",
                osVersion = "18.2.1.22C161",
                deviceMake = "Apple",
                deviceModel = "iPhone16,2",
                useCookies = false
            ),
            // 3. ANDROID_TESTSUITE WITHOUT cookies — returns progressive itag 18 + adaptive streams
            ClientProfile(
                clientName = "ANDROID_TESTSUITE",
                clientVersion = "1.9",
                clientIdHeader = "30",
                userAgent = "com.google.android.youtube/1.9 (Linux; U; Android 14; en_US) gzip",
                osName = "Android",
                osVersion = "14",
                androidSdkVersion = 34,
                useCookies = false
            ),
            // 4. ANDROID WITHOUT cookies
            ClientProfile(
                clientName = "ANDROID",
                clientVersion = "20.03.38",
                clientIdHeader = "3",
                userAgent = "com.google.android.youtube/20.03.38 (Linux; U; Android 14; en_US) gzip",
                osName = "Android",
                osVersion = "14",
                androidSdkVersion = 34,
                useCookies = false
            ),
            // 5. TVHTML5_SIMPLY_EMBEDDED_PLAYER
            ClientProfile(
                clientName = "TVHTML5_SIMPLY_EMBEDDED_PLAYER",
                clientVersion = "2.0",
                clientIdHeader = "85",
                userAgent = "Mozilla/5.0 (PlayStation; PlayStation 5/2.26) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/13.0 Safari/605.1.15",
                osName = "PlayStation",
                osVersion = "2.26",
                useCookies = false,
                thirdPartyEmbedUrl = "https://www.youtube.com/embed/$videoId"
            ),
            // 6. ANDROID_VR WITH cookies (fallback for signed-in / age-gated videos)
            ClientProfile(
                clientName = "ANDROID_VR",
                clientVersion = "1.62.27",
                clientIdHeader = "28",
                userAgent = "com.google.android.apps.youtube.vr.oculus/1.62.27 (Linux; U; Android 12L; eureka-user Build/SQ3A.220605.009.A1) gzip",
                osName = "Android",
                osVersion = "12L",
                androidSdkVersion = 32,
                deviceMake = "Oculus",
                deviceModel = "Quest 3",
                useCookies = true
            )
        )

        for (profile in profiles) {
            if (profile.useCookies && cookies.isBlank()) continue
            try {
                val url = URL("https://www.youtube.com/youtubei/v1/player?prettyPrint=false")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 10000
                    readTimeout = 10000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("Accept", "application/json")
                    setRequestProperty("User-Agent", profile.userAgent)
                    setRequestProperty("X-YouTube-Client-Name", profile.clientIdHeader)
                    setRequestProperty("X-YouTube-Client-Version", profile.clientVersion)
                    setRequestProperty("Origin", "https://www.youtube.com")
                    if (profile.useCookies && cookies.isNotBlank()) {
                        setRequestProperty("Cookie", cookies)
                    }
                }

                val clientObj = JSONObject().apply {
                    put("clientName", profile.clientName)
                    put("clientVersion", profile.clientVersion)
                    put("osName", profile.osName)
                    put("osVersion", profile.osVersion)
                    put("hl", "en")
                    put("gl", "US")
                    put("utcOffsetMinutes", 0)
                    if (profile.androidSdkVersion != null) {
                        put("androidSdkVersion", profile.androidSdkVersion)
                    }
                    if (profile.deviceMake.isNotBlank()) {
                        put("deviceMake", profile.deviceMake)
                    }
                    if (profile.deviceModel.isNotBlank()) {
                        put("deviceModel", profile.deviceModel)
                    }
                }

                val contextObj = JSONObject().apply {
                    put("client", clientObj)
                    if (profile.thirdPartyEmbedUrl != null) {
                        put("thirdParty", JSONObject().apply {
                            put("embedUrl", profile.thirdPartyEmbedUrl)
                        })
                    }
                }

                val payload = JSONObject().apply {
                    put("videoId", videoId)
                    put("context", contextObj)
                    put("playbackContext", JSONObject().apply {
                        put("contentPlaybackContext", JSONObject().apply {
                            put("html5Preference", "HTML5_PREF_WANTS")
                            put("signatureTimestamp", 20073)
                        })
                    })
                    put("contentCheckOk", true)
                    put("racyCheckOk", true)
                }

                OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { writer ->
                    writer.write(payload.toString())
                    writer.flush()
                }

                if (conn.responseCode in 200..299) {
                    val responseStr = conn.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(responseStr)

                    val videoDetails = json.optJSONObject("videoDetails")
                    val title = videoDetails?.optString("title", "") ?: ""
                    val author = videoDetails?.optString("author", "") ?: ""
                    val lengthSec = videoDetails?.optString("lengthSeconds", "0")?.toLongOrNull() ?: 0L

                    val streamingData = json.optJSONObject("streamingData")
                    val progressiveUrls = mutableListOf<String>()
                    val adaptiveVideoUrls = mutableListOf<String>()
                    var bestAudioUrl: String? = null

                    // 1. Progressive formats (single MP4 containing both video + audio, e.g. itag 18 or 22)
                    val formats = streamingData?.optJSONArray("formats")
                    if (formats != null) {
                        val sortedFormats = mutableListOf<JSONObject>()
                        for (i in 0 until formats.length()) {
                            formats.optJSONObject(i)?.let { sortedFormats.add(it) }
                        }
                        sortedFormats.sortByDescending { fmt ->
                            val mime = fmt.optString("mimeType", "")
                            val itag = fmt.optInt("itag", 0)
                            when {
                                itag == 18 -> 100 // 360p MP4 with audio (fastest & most reliable)
                                itag == 22 -> 95  // 720p MP4 with audio
                                mime.contains("video/mp4") -> 80
                                else -> 40
                            }
                        }
                        for (fmt in sortedFormats) {
                            val directUrl = fmt.optString("url", "")
                            if (directUrl.startsWith("http")) {
                                progressiveUrls.add(directUrl)
                            }
                        }
                    }

                    // 2. Adaptive formats (separate video/mp4 and audio/mp4 streams)
                    val adaptive = streamingData?.optJSONArray("adaptiveFormats")
                    if (adaptive != null) {
                        val videoFmts = mutableListOf<JSONObject>()
                        val audioFmts = mutableListOf<JSONObject>()
                        for (i in 0 until adaptive.length()) {
                            val fmt = adaptive.optJSONObject(i) ?: continue
                            val directUrl = fmt.optString("url", "")
                            if (!directUrl.startsWith("http")) continue
                            val mime = fmt.optString("mimeType", "")
                            if (mime.startsWith("video/mp4", ignoreCase = true)) {
                                videoFmts.add(fmt)
                            } else if (mime.startsWith("audio/mp4", ignoreCase = true)) {
                                audioFmts.add(fmt)
                            }
                        }

                        // Prefer H.264 (avc1) 360p/480p/720p for fast download and universal hardware playback
                        videoFmts.sortByDescending { fmt ->
                            val itag = fmt.optInt("itag", 0)
                            val mime = fmt.optString("mimeType", "")
                            val isAvc = mime.contains("avc1", ignoreCase = true)
                            when {
                                itag == 134 -> 100 // 360p H.264 MP4
                                itag == 135 -> 95  // 480p H.264 MP4
                                itag == 136 -> 90  // 720p H.264 MP4
                                itag == 160 -> 80  // 144p H.264 MP4
                                isAvc -> 75
                                else -> 50
                            }
                        }
                        for (vFmt in videoFmts) {
                            adaptiveVideoUrls.add(vFmt.optString("url", ""))
                        }

                        audioFmts.sortByDescending { fmt ->
                            val itag = fmt.optInt("itag", 0)
                            if (itag == 140) 100 else 80
                        }
                        bestAudioUrl = audioFmts.firstOrNull()?.optString("url", "")?.takeIf { it.startsWith("http") }
                    }

                    if (progressiveUrls.isNotEmpty() || adaptiveVideoUrls.isNotEmpty()) {
                        return InnerTubeStreamInfo(
                            progressiveUrls = progressiveUrls,
                            adaptiveVideoUrls = adaptiveVideoUrls,
                            adaptiveAudioUrl = bestAudioUrl,
                            title = title,
                            author = author,
                            durationSec = lengthSec,
                            clientUserAgent = profile.userAgent
                        )
                    }
                }
            } catch (_: Exception) {}
        }
        return null
    }

    /**
     * Secondary fallback using public Piped/Invidious JSON stream endpoints in case direct InnerTube
     * is rate-limited on the current network.
     */
    private fun resolvePipedOrInvidiousStream(videoId: String): InnerTubeStreamInfo? {
        val pipedApis = listOf(
            "https://pipedapi.kavin.rocks/streams/$videoId",
            "https://pipedapi.tokhmi.xyz/streams/$videoId",
            "https://api.piped.private.coffee/streams/$videoId"
        )
        for (endpoint in pipedApis) {
            try {
                val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 8000
                    readTimeout = 8000
                    setRequestProperty("Accept", "application/json")
                    setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/131.0.0.0 Mobile Safari/537.36")
                }
                if (conn.responseCode in 200..299) {
                    val body = conn.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(body)
                    val title = json.optString("title", "")
                    val uploader = json.optString("uploader", "")
                    val duration = json.optLong("duration", 0L)
                    val progressive = mutableListOf<String>()
                    val adaptiveVid = mutableListOf<String>()
                    var adaptiveAud: String? = null

                    val videoStreams = json.optJSONArray("videoStreams")
                    if (videoStreams != null) {
                        for (i in 0 until videoStreams.length()) {
                            val vs = videoStreams.optJSONObject(i) ?: continue
                            val u = vs.optString("url", "")
                            val format = vs.optString("format", "")
                            val videoOnly = vs.optBoolean("videoOnly", true)
                            if (u.startsWith("http") && (format.contains("MPEG_4", true) || u.contains("video/mp4") || u.contains("mime=video%2Fmp4"))) {
                                if (!videoOnly) {
                                    progressive.add(u)
                                } else {
                                    adaptiveVid.add(u)
                                }
                            }
                        }
                    }
                    val audioStreams = json.optJSONArray("audioStreams")
                    if (audioStreams != null) {
                        for (i in 0 until audioStreams.length()) {
                            val asObj = audioStreams.optJSONObject(i) ?: continue
                            val u = asObj.optString("url", "")
                            val mime = asObj.optString("mimeType", "")
                            if (u.startsWith("http") && (mime.contains("mp4", true) || mime.contains("m4a", true))) {
                                adaptiveAud = u
                                break
                            }
                        }
                    }
                    if (progressive.isNotEmpty() || adaptiveVid.isNotEmpty()) {
                        return InnerTubeStreamInfo(
                            progressiveUrls = progressive,
                            adaptiveVideoUrls = adaptiveVid,
                            adaptiveAudioUrl = adaptiveAud,
                            title = title,
                            author = uploader,
                            durationSec = duration,
                            clientUserAgent = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/131.0.0.0 Mobile Safari/537.36"
                        )
                    }
                }
            } catch (_: Exception) {}
        }
        return null
    }

    /**
     * Downloads a YouTube stream URL to disk.
     * - Automatically matches headers to the stream's `c=` client type (preventing 403 Forbidden
     *   caused by sending web Origin/Referer/Cookie headers on ANDROID_VR / IOS / ANDROID stream URLs).
     * - Automatically falls back to chunked byte-range requests (`&range=start-end`) if a single GET
     *   is rejected or throttled by `googlevideo.com`.
     */
    private fun downloadStreamWithRangeFallback(
        urlStr: String,
        destFile: File,
        userAgent: String,
        cookies: String,
        onProgress: ((Int) -> Unit)? = null
    ): Boolean {
        val cleanedUrl = sanitizeGoogleVideoUrl(urlStr)
        val isNativeMobileClientUrl = cleanedUrl.contains("c=ANDROID", ignoreCase = true) ||
            cleanedUrl.contains("c=IOS", ignoreCase = true)

        val effectiveUserAgent = when {
            cleanedUrl.contains("c=ANDROID_VR", ignoreCase = true) ->
                "com.google.android.apps.youtube.vr.oculus/1.62.27 (Linux; U; Android 12L; eureka-user Build/SQ3A.220605.009.A1) gzip"
            cleanedUrl.contains("c=IOS", ignoreCase = true) ->
                "com.google.ios.youtube/20.03.02 (iPhone16,2; U; CPU iOS 18_2_1 like Mac OS X;)"
            cleanedUrl.contains("c=ANDROID_TESTSUITE", ignoreCase = true) ->
                "com.google.android.youtube/1.9 (Linux; U; Android 14; en_US) gzip"
            cleanedUrl.contains("c=ANDROID", ignoreCase = true) ->
                "com.google.android.youtube/20.03.38 (Linux; U; Android 14; en_US) gzip"
            else -> userAgent
        }

        // 1. Try direct streaming download first
        if (downloadBinaryFile(
                urlStr = cleanedUrl,
                destFile = destFile,
                userAgent = effectiveUserAgent,
                cookies = if (isNativeMobileClientUrl) "" else cookies,
                sendWebOriginHeaders = !isNativeMobileClientUrl,
                onProgress = onProgress
            )
        ) {
            return true
        }

        // 2. Fallback: Chunked Range downloading (&range=start-end in 4MB chunks)
        return downloadInRangeChunks(
            baseUrl = cleanedUrl,
            destFile = destFile,
            userAgent = effectiveUserAgent,
            cookies = if (isNativeMobileClientUrl) "" else cookies,
            sendWebOriginHeaders = !isNativeMobileClientUrl,
            onProgress = onProgress
        )
    }

    private fun downloadBinaryFile(
        urlStr: String,
        destFile: File,
        userAgent: String,
        cookies: String,
        onProgress: ((Int) -> Unit)? = null,
        sendWebOriginHeaders: Boolean = true
    ): Boolean {
        var conn: HttpURLConnection? = null
        return try {
            if (destFile.exists()) destFile.delete()
            val url = URL(urlStr)
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 15000
                readTimeout = 30000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", userAgent)
                setRequestProperty("Accept", "*/*")
                if (sendWebOriginHeaders) {
                    setRequestProperty("Referer", "https://www.youtube.com/")
                    setRequestProperty("Origin", "https://www.youtube.com")
                }
                if (cookies.isNotBlank()) {
                    setRequestProperty("Cookie", cookies)
                }
            }
            conn.connect()
            val code = conn.responseCode
            if (code !in 200..299) return false

            val clenParam = Regex("[?&]clen=(\\d+)").find(urlStr)?.groupValues?.getOrNull(1)?.toLongOrNull() ?: -1L
            val totalBytes = conn.contentLengthLong.takeIf { it > 0L } ?: clenParam

            BufferedInputStream(conn.inputStream).use { input ->
                FileOutputStream(destFile).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var bytesRead: Int
                    var downloaded = 0L
                    var lastReportedPct = -1
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        downloaded += bytesRead
                        if (onProgress != null && totalBytes > 0L) {
                            val pct = ((downloaded * 100L) / totalBytes).toInt().coerceIn(0, 100)
                            if (pct != lastReportedPct) {
                                lastReportedPct = pct
                                onProgress(pct)
                            }
                        }
                    }
                    output.flush()
                }
            }
            destFile.exists() && destFile.length() > 1024L
        } catch (_: Exception) {
            false
        } finally {
            try { conn?.disconnect() } catch (_: Exception) {}
        }
    }

    private fun downloadInRangeChunks(
        baseUrl: String,
        destFile: File,
        userAgent: String,
        cookies: String,
        sendWebOriginHeaders: Boolean,
        onProgress: ((Int) -> Unit)?
    ): Boolean {
        return try {
            if (destFile.exists()) destFile.delete()
            val clenTotal = Regex("[?&]clen=(\\d+)").find(baseUrl)?.groupValues?.getOrNull(1)?.toLongOrNull() ?: -1L
            val chunkSize = 4L * 1024L * 1024L // 4 MB chunks
            var currentOffset = 0L
            var knownTotal = clenTotal

            FileOutputStream(destFile).use { fileOut ->
                val buffer = ByteArray(64 * 1024)
                var chunkIndex = 0
                while (chunkIndex < 120) { // Up to 480 MB max safety bound
                    chunkIndex++
                    val endByte = if (knownTotal > 0L) {
                        (currentOffset + chunkSize - 1L).coerceAtMost(knownTotal - 1L)
                    } else {
                        currentOffset + chunkSize - 1L
                    }
                    if (knownTotal > 0L && currentOffset >= knownTotal) break

                    val sep = if (baseUrl.contains("?")) "&" else "?"
                    val chunkUrl = "${baseUrl}${sep}range=${currentOffset}-${endByte}"
                    var conn: HttpURLConnection? = null
                    var chunkRead = 0L
                    try {
                        conn = (URL(chunkUrl).openConnection() as HttpURLConnection).apply {
                            requestMethod = "GET"
                            connectTimeout = 12000
                            readTimeout = 25000
                            instanceFollowRedirects = true
                            setRequestProperty("User-Agent", userAgent)
                            setRequestProperty("Accept", "*/*")
                            if (sendWebOriginHeaders) {
                                setRequestProperty("Referer", "https://www.youtube.com/")
                                setRequestProperty("Origin", "https://www.youtube.com")
                            }
                            if (cookies.isNotBlank()) {
                                setRequestProperty("Cookie", cookies)
                            }
                        }
                        conn.connect()
                        val code = conn.responseCode
                        if (code !in 200..299) {
                            break
                        }
                        if (knownTotal <= 0L) {
                            val cr = conn.getHeaderField("Content-Range")
                            if (cr != null && cr.contains("/")) {
                                val totalStr = cr.substringAfter("/").trim()
                                knownTotal = totalStr.toLongOrNull() ?: -1L
                            }
                        }
                        BufferedInputStream(conn.inputStream).use { input ->
                            var read: Int
                            while (input.read(buffer).also { read = it } != -1) {
                                fileOut.write(buffer, 0, read)
                                chunkRead += read
                                val totalNow = currentOffset + chunkRead
                                if (onProgress != null && knownTotal > 0L) {
                                    val pct = ((totalNow * 100L) / knownTotal).toInt().coerceIn(0, 100)
                                    onProgress(pct)
                                }
                            }
                        }
                    } finally {
                        try { conn?.disconnect() } catch (_: Exception) {}
                    }

                    if (chunkRead <= 0L) break
                    currentOffset += chunkRead
                    if (knownTotal > 0L && currentOffset >= knownTotal) break
                    if (knownTotal <= 0L && chunkRead < chunkSize) break
                }
                fileOut.flush()
            }
            destFile.exists() && destFile.length() > 4096L
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Combines an adaptive H.264 MP4 video file and an AAC M4A audio file into a single
     * playable MP4 file using Android's native MediaExtractor and MediaMuxer.
     */
    private fun muxVideoAndAudioMp4(videoFile: File, audioFile: File, outputFile: File): Boolean {
        var videoExtractor: MediaExtractor? = null
        var audioExtractor: MediaExtractor? = null
        var muxer: MediaMuxer? = null
        return try {
            if (outputFile.exists()) outputFile.delete()
            videoExtractor = MediaExtractor().apply { setDataSource(videoFile.absolutePath) }
            audioExtractor = MediaExtractor().apply { setDataSource(audioFile.absolutePath) }

            var videoTrackIndexExtractor = -1
            var videoFormat: MediaFormat? = null
            for (i in 0 until videoExtractor.trackCount) {
                val fmt = videoExtractor.getTrackFormat(i)
                val mime = fmt.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("video/")) {
                    videoTrackIndexExtractor = i
                    videoFormat = fmt
                    break
                }
            }

            var audioTrackIndexExtractor = -1
            var audioFormat: MediaFormat? = null
            for (i in 0 until audioExtractor.trackCount) {
                val fmt = audioExtractor.getTrackFormat(i)
                val mime = fmt.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    audioTrackIndexExtractor = i
                    audioFormat = fmt
                    break
                }
            }

            if (videoTrackIndexExtractor < 0 || videoFormat == null) return false

            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            videoExtractor.selectTrack(videoTrackIndexExtractor)
            val muxVideoTrack = muxer.addTrack(videoFormat)

            var muxAudioTrack = -1
            if (audioTrackIndexExtractor >= 0 && audioFormat != null) {
                audioExtractor.selectTrack(audioTrackIndexExtractor)
                muxAudioTrack = muxer.addTrack(audioFormat)
            }

            muxer.start()

            val buffer = ByteBuffer.allocate(512 * 1024)
            val bufferInfo = MediaCodec.BufferInfo()

            // Write video samples
            while (true) {
                buffer.clear()
                val sampleSize = videoExtractor.readSampleData(buffer, 0)
                if (sampleSize < 0) break
                bufferInfo.offset = 0
                bufferInfo.size = sampleSize
                bufferInfo.presentationTimeUs = videoExtractor.sampleTime
                val sampleFlags = videoExtractor.sampleFlags
                bufferInfo.flags = if ((sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC) != 0) {
                    MediaCodec.BUFFER_FLAG_KEY_FRAME
                } else 0
                muxer.writeSampleData(muxVideoTrack, buffer, bufferInfo)
                videoExtractor.advance()
            }

            // Write audio samples
            if (muxAudioTrack >= 0) {
                while (true) {
                    buffer.clear()
                    val sampleSize = audioExtractor.readSampleData(buffer, 0)
                    if (sampleSize < 0) break
                    bufferInfo.offset = 0
                    bufferInfo.size = sampleSize
                    bufferInfo.presentationTimeUs = audioExtractor.sampleTime
                    val sampleFlags = audioExtractor.sampleFlags
                    bufferInfo.flags = if ((sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC) != 0) {
                        MediaCodec.BUFFER_FLAG_KEY_FRAME
                    } else 0
                    muxer.writeSampleData(muxAudioTrack, buffer, bufferInfo)
                    audioExtractor.advance()
                }
            }

            muxer.stop()
            outputFile.exists() && outputFile.length() > 4096L
        } catch (_: Exception) {
            false
        } finally {
            try { videoExtractor?.release() } catch (_: Exception) {}
            try { audioExtractor?.release() } catch (_: Exception) {}
            try { muxer?.release() } catch (_: Exception) {}
        }
    }

    fun formatDurationSeconds(seconds: Long): String {
        if (seconds <= 0L) return ""
        val hrs = seconds / 3600L
        val mins = (seconds % 3600L) / 60L
        val secs = seconds % 60L
        return if (hrs > 0L) {
            String.format(Locale.US, "%d:%02d:%02d", hrs, mins, secs)
        } else {
            String.format(Locale.US, "%d:%02d", mins, secs)
        }
    }

    fun formatFileSize(bytes: Long): String {
        if (bytes <= 0L) return "0 MB"
        val mb = bytes.toDouble() / (1024.0 * 1024.0)
        return if (mb >= 1.0) {
            String.format(Locale.US, "%.1f MB", mb)
        } else {
            val kb = bytes.toDouble() / 1024.0
            String.format(Locale.US, "%.0f KB", kb)
        }
    }
}

class YouTubeJsDownloadBridge(
    private val context: Context,
    private val mainHandler: Handler,
    private val userAgentProvider: () -> String,
    private val onOpenOfflineLibrary: () -> Unit
) {
    @JavascriptInterface
    fun downloadVideo(
        videoId: String,
        title: String,
        author: String,
        durationSeconds: String,
        directStreamUrl: String
    ) {
        mainHandler.post {
            YouTubeOfflineRepository.startVideoDownload(
                context = context,
                mainHandler = mainHandler,
                videoId = videoId,
                rawTitle = title,
                rawChannel = author,
                durationSecondsStr = durationSeconds,
                directStreamUrl = directStreamUrl,
                userAgent = userAgentProvider()
            )
        }
    }

    @JavascriptInterface
    fun openOfflineDownloads() {
        mainHandler.post {
            onOpenOfflineLibrary()
        }
    }

    @JavascriptInterface
    fun isDownloaded(videoId: String): Boolean {
        return YouTubeOfflineRepository.isVideoDownloaded(context, videoId)
    }

    @JavascriptInterface
    fun getProgress(videoId: String): Int {
        return YouTubeOfflineRepository.getDownloadProgress(videoId)
    }
}

/**
 * YouTube Desktop-Style Offline Downloads View embedded directly inside the YouTube Clone Space workspace.
 * Displays automatically when visiting YouTube while offline, or when toggled via the Downloads button.
 */
@Composable
fun YouTubeOfflineDesktopView(
    videos: List<YouTubeOfflineVideo>,
    activeDownloads: Map<String, Int>,
    isOfflineNetwork: Boolean,
    selectedVideo: YouTubeOfflineVideo?,
    onSelectVideo: (YouTubeOfflineVideo?) -> Unit,
    onDeleteVideo: (String) -> Unit,
    onReturnToLiveYouTube: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var videoViewRef by remember { mutableStateOf<VideoView?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var currentPosMs by remember { mutableIntStateOf(0) }
    var durationMs by remember { mutableIntStateOf(0) }
    var isFullscreenPlayer by remember { mutableStateOf(false) }

    val activePlayingVideo = selectedVideo ?: videos.firstOrNull()

    LaunchedEffect(activePlayingVideo?.localVideoPath, isPlaying) {
        while (activePlayingVideo != null) {
            val vv = videoViewRef
            if (vv != null) {
                try {
                    isPlaying = vv.isPlaying
                    if (vv.isPlaying) {
                        currentPosMs = vv.currentPosition.coerceAtLeast(0)
                        val dur = vv.duration
                        if (dur > 0) durationMs = dur
                    }
                } catch (_: Exception) {}
            }
            delay(400L)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            try {
                videoViewRef?.stopPlayback()
            } catch (_: Exception) {}
            videoViewRef = null
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0F0F0F))
            .testTag("youtube_offline_desktop_view")
    ) {
        // Offline / Desktop Downloads Header Banner
        if (!isFullscreenPlayer) {
            Surface(
                color = Color(0xFF181818),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFFFF0000))
                        ) {
                            Icon(
                                imageVector = if (isOfflineNetwork) Icons.Filled.CloudOff else Icons.Filled.DownloadDone,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(19.dp)
                            )
                        }

                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "YouTube Downloads",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = if (isOfflineNetwork) Color(0xFFF59E0B).copy(alpha = 0.2f) else Color(0xFF10B981).copy(alpha = 0.2f)
                                ) {
                                    Text(
                                        text = if (isOfflineNetwork) "OFFLINE MODE" else "AVAILABLE OFFLINE",
                                        color = if (isOfflineNetwork) Color(0xFFFBBF24) else Color(0xFF34D399),
                                        fontSize = 9.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Text(
                                text = if (isOfflineNetwork) {
                                    "No internet connection • Watch your saved videos offline"
                                } else {
                                    "${videos.size} downloaded video${if (videos.size == 1) "" else "s"} ready for offline viewing"
                                },
                                color = Color(0xFFAAAAAA),
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    FilledTonalButton(
                        onClick = onReturnToLiveYouTube,
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = Color(0xFF272727),
                            contentColor = Color.White
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier
                            .height(34.dp)
                            .testTag("yt_offline_back_to_live_btn")
                    ) {
                        Icon(
                            imageVector = if (isOfflineNetwork) Icons.Filled.Refresh else Icons.Filled.Public,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isOfflineNetwork) "Retry Online" else "Live YouTube",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        // Active Download Progress Bar (if a video is currently downloading)
        if (activeDownloads.isNotEmpty() && !isFullscreenPlayer) {
            val entry = activeDownloads.entries.first()
            Surface(
                color = Color(0xFF212121),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Saving video for offline playback (${entry.key})...",
                            color = Color.White,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "${entry.value}%",
                            color = Color(0xFFFF0000),
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    LinearProgressIndicator(
                        progress = { entry.value / 100f },
                        color = Color(0xFFFF0000),
                        trackColor = Color(0xFF383838),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                    )
                }
            }
        }

        if (videos.isEmpty()) {
            // Empty Offline Library State
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A)),
                    modifier = Modifier.fillMaxWidth()
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
                                .background(Color(0xFFFF0000).copy(alpha = 0.16f))
                        ) {
                            Icon(
                                imageVector = Icons.Filled.VideoLibrary,
                                contentDescription = null,
                                tint = Color(0xFFFF0000),
                                modifier = Modifier.size(32.dp)
                            )
                        }

                        Text(
                            text = "No Downloaded YouTube Videos Yet",
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )

                        Text(
                            text = "While watching any YouTube video or Short in Clone Space, tap the Download button in the top bar or below the video. Your downloaded videos will appear here so you can watch them anytime offline!",
                            color = Color(0xFFAAAAAA),
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center
                        )

                        Button(
                            onClick = onReturnToLiveYouTube,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFFF0000),
                                contentColor = Color.White
                            )
                        ) {
                            Icon(Icons.Filled.SmartDisplay, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Browse YouTube Videos to Download")
                        }
                    }
                }
            }
        } else {
            // Embedded Offline Video Player + Desktop-Style Downloaded Videos Playlist
            if (activePlayingVideo != null) {
                Column(
                    modifier = if (isFullscreenPlayer) {
                        Modifier.fillMaxSize()
                    } else {
                        Modifier.fillMaxWidth()
                    }
                ) {
                    Box(
                        modifier = if (isFullscreenPlayer) {
                            Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .background(Color.Black)
                        } else {
                            Modifier
                                .fillMaxWidth()
                                .aspectRatio(16f / 9f)
                                .background(Color.Black)
                        }
                    ) {
                        androidx.compose.runtime.key(activePlayingVideo.localVideoPath) {
                            AndroidView(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .testTag("yt_offline_video_player"),
                                factory = { ctx ->
                                    VideoView(ctx).apply {
                                        val mediaController = MediaController(ctx)
                                        mediaController.setAnchorView(this)
                                        setMediaController(mediaController)
                                        setVideoURI(Uri.fromFile(File(activePlayingVideo.localVideoPath)))
                                        setOnPreparedListener { mp ->
                                            durationMs = mp.duration.coerceAtLeast(1)
                                            mp.start()
                                            isPlaying = true
                                        }
                                        setOnCompletionListener {
                                            isPlaying = false
                                            currentPosMs = durationMs
                                        }
                                        videoViewRef = this
                                    }
                                },
                                update = { vv ->
                                    videoViewRef = vv
                                }
                            )
                        }
                    }

                    // Custom YouTube Desktop-Style Transport & Metadata Bar below Player
                    Surface(
                        color = Color(0xFF141414),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            // Seek Slider + Controls Row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                IconButton(
                                    onClick = {
                                        val vv = videoViewRef ?: return@IconButton
                                        if (vv.isPlaying) {
                                            vv.pause()
                                            isPlaying = false
                                        } else {
                                            vv.start()
                                            isPlaying = true
                                        }
                                    },
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                        contentDescription = if (isPlaying) "Pause" else "Play",
                                        tint = Color.White
                                    )
                                }

                                IconButton(
                                    onClick = {
                                        val vv = videoViewRef ?: return@IconButton
                                        val target = (vv.currentPosition - 10000).coerceAtLeast(0)
                                        vv.seekTo(target)
                                        currentPosMs = target
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Replay10,
                                        contentDescription = "Rewind 10s",
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                IconButton(
                                    onClick = {
                                        val vv = videoViewRef ?: return@IconButton
                                        val target = (vv.currentPosition + 10000).coerceAtMost(durationMs.coerceAtLeast(1))
                                        vv.seekTo(target)
                                        currentPosMs = target
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Forward10,
                                        contentDescription = "Forward 10s",
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                val progressFraction = if (durationMs > 0) {
                                    (currentPosMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
                                } else 0f

                                Slider(
                                    value = progressFraction,
                                    onValueChange = { frac ->
                                        val target = (frac * durationMs.coerceAtLeast(1)).toInt()
                                        videoViewRef?.seekTo(target)
                                        currentPosMs = target
                                    },
                                    colors = SliderDefaults.colors(
                                        thumbColor = Color(0xFFFF0000),
                                        activeTrackColor = Color(0xFFFF0000),
                                        inactiveTrackColor = Color(0xFF444444)
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(22.dp)
                                )

                                Text(
                                    text = "${YouTubeOfflineRepository.formatDurationSeconds(currentPosMs / 1000L).ifBlank { "0:00" }} / ${
                                        YouTubeOfflineRepository.formatDurationSeconds(durationMs / 1000L).ifBlank { activePlayingVideo.durationText.ifBlank { "0:00" } }
                                    }",
                                    color = Color(0xFFCCCCCC),
                                    fontSize = 11.sp
                                )

                                IconButton(
                                    onClick = { isFullscreenPlayer = !isFullscreenPlayer },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isFullscreenPlayer) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
                                        contentDescription = "Toggle Fullscreen",
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }

                            if (!isFullscreenPlayer) {
                                Text(
                                    text = activePlayingVideo.title,
                                    color = Color.White,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = activePlayingVideo.channelName,
                                        color = Color(0xFFAAAAAA),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = "• ${YouTubeOfflineRepository.formatFileSize(activePlayingVideo.fileSizeBytes)}",
                                        color = Color(0xFF888888),
                                        fontSize = 11.5.sp
                                    )
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.CheckCircle,
                                            contentDescription = null,
                                            tint = Color(0xFF2BA640),
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Text(
                                            text = "Saved on device",
                                            color = Color(0xFF2BA640),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Desktop-Style Downloaded Videos List below Player
            if (!isFullscreenPlayer) {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .testTag("yt_offline_videos_list"),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        Text(
                            text = "Downloaded Videos (${videos.size})",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(bottom = 2.dp)
                        )
                    }

                    items(
                        items = videos,
                        key = { it.videoId }
                    ) { item ->
                        val isSelected = activePlayingVideo?.videoId == item.videoId
                        YouTubeOfflineVideoCard(
                            video = item,
                            isSelected = isSelected,
                            onClick = { onSelectVideo(item) },
                            onDelete = { onDeleteVideo(item.videoId) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun YouTubeOfflineVideoCard(
    video: YouTubeOfflineVideo,
    isSelected: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val thumbBitmap = remember(video.localThumbnailPath) {
        video.localThumbnailPath?.let { path ->
            try {
                BitmapFactory.decodeFile(path)?.asImageBitmap()
            } catch (_: Exception) {
                null
            }
        }
    }

    val dateStr = remember(video.downloadedAt) {
        try {
            SimpleDateFormat("MMM d, yyyy", Locale.US).format(Date(video.downloadedAt))
        } catch (_: Exception) {
            ""
        }
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) Color(0xFF272727) else Color(0xFF181818),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .then(
                if (isSelected) Modifier.border(1.dp, Color(0xFFFF0000).copy(alpha = 0.7f), RoundedCornerShape(12.dp))
                else Modifier
            )
            .clickable(onClick = onClick)
            .testTag("yt_offline_item_${video.videoId}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 16:9 Video Thumbnail with Duration Badge
            Box(
                modifier = Modifier
                    .width(136.dp)
                    .height(76.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                if (thumbBitmap != null) {
                    Image(
                        bitmap = thumbBitmap,
                        contentDescription = video.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.SmartDisplay,
                        contentDescription = null,
                        tint = Color(0xFFFF0000),
                        modifier = Modifier.size(32.dp)
                    )
                }

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.65f))
                ) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = "Play Offline",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }

                if (video.durationText.isNotBlank()) {
                    Surface(
                        color = Color.Black.copy(alpha = 0.82f),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(4.dp)
                    ) {
                        Text(
                            text = video.durationText,
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                }
            }

            // Video Details
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = video.title,
                    color = Color.White,
                    fontSize = 13.5.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = video.channelName,
                    color = Color(0xFFAAAAAA),
                    fontSize = 11.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        color = Color(0xFF2BA640).copy(alpha = 0.18f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.DownloadDone,
                                contentDescription = null,
                                tint = Color(0xFF2BA640),
                                modifier = Modifier.size(11.dp)
                            )
                            Text(
                                text = YouTubeOfflineRepository.formatFileSize(video.fileSizeBytes),
                                color = Color(0xFF2BA640),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    if (dateStr.isNotBlank()) {
                        Text(
                            text = "• $dateStr",
                            color = Color(0xFF777777),
                            fontSize = 10.5.sp
                        )
                    }
                }
            }

            IconButton(
                onClick = onDelete,
                modifier = Modifier
                    .size(34.dp)
                    .testTag("yt_offline_delete_${video.videoId}")
            ) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = "Delete offline video",
                    tint = Color(0xFF888888),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
