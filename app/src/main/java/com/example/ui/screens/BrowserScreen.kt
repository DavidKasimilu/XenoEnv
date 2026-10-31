package com.example.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.JsResult
import android.webkit.PermissionRequest
import android.webkit.RenderProcessGoneDetail
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.key
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.DesktopMac
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.PhoneAndroid
import com.example.ui.components.PasswordManagerDialog
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SmartDisplay
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.data.models.BrowserHistoryItem
import com.example.data.models.WebTab
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val MOBILE_BROWSER_USER_AGENT =
    "Mozilla/5.0 (Linux; Android 14; Pixel 8 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"

private const val DESKTOP_BROWSER_USER_AGENT =
    "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

private val MOBILE_BROWSER_HEADERS = mapOf(
    "Sec-CH-UA" to "\"Google Chrome\";v=\"131\", \"Chromium\";v=\"131\", \"Not_A Brand\";v=\"24\"",
    "Sec-CH-UA-Mobile" to "?1",
    "Sec-CH-UA-Platform" to "\"Android\""
)

private val DESKTOP_BROWSER_HEADERS = mapOf(
    "Sec-CH-UA" to "\"Google Chrome\";v=\"131\", \"Chromium\";v=\"131\", \"Not_A Brand\";v=\"24\"",
    "Sec-CH-UA-Mobile" to "?0",
    "Sec-CH-UA-Platform" to "\"Linux\""
)

/**
 * Helper function that configures Android WebView to behave as a standard Mobile Chrome Browser
 * by default, switches to full Desktop Browser mode when toggled from the 3-dot menu, and automatically
 * enables Desktop Chrome WebRTC mode for online call platforms like Google Meet, Teams, and Zoom.
 */
@SuppressLint("SetJavaScriptEnabled")
private fun applyBrowserModeSettings(webView: WebView, isDesktopMode: Boolean, currentUrl: String = "") {
    val isOnlineCallPlatform = currentUrl.contains("meet.google.com", ignoreCase = true) ||
        currentUrl.contains("teams.microsoft.com", ignoreCase = true) ||
        currentUrl.contains("teams.live.com", ignoreCase = true) ||
        currentUrl.contains("zoom.us", ignoreCase = true) ||
        currentUrl.contains("web.whatsapp.com", ignoreCase = true)

    val forceDesktop = isDesktopMode || isOnlineCallPlatform

    webView.settings.apply {
        javaScriptEnabled = true
        domStorageEnabled = true
        databaseEnabled = true
        useWideViewPort = true
        loadWithOverviewMode = true
        setSupportZoom(true)
        builtInZoomControls = true
        displayZoomControls = false
        safeBrowsingEnabled = false
        mediaPlaybackRequiresUserGesture = false
        allowFileAccess = true
        allowContentAccess = true
        javaScriptCanOpenWindowsAutomatically = true
        setSupportMultipleWindows(true)
        setGeolocationEnabled(true)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            offscreenPreRaster = true
        }
        @Suppress("DEPRECATION")
        setRenderPriority(android.webkit.WebSettings.RenderPriority.HIGH)
        mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        cacheMode = android.webkit.WebSettings.LOAD_DEFAULT
        userAgentString = if (forceDesktop) DESKTOP_BROWSER_USER_AGENT else MOBILE_BROWSER_USER_AGENT
        textZoom = 100
        defaultTextEncodingName = "UTF-8"
    }

    webView.isFocusable = true
    webView.isFocusableInTouchMode = true
    webView.isClickable = true
    com.example.ui.components.GlobalSwipeDownTracker.attachToWebView(webView, disallowParentIntercept = false)

    try {
        val cm = CookieManager.getInstance()
        cm.setAcceptCookie(true)
        cm.setAcceptThirdPartyCookies(webView, true)
    } catch (_: Exception) {}
}

/**
 * Mobile & Desktop Browser Helper Engine:
 * Injects lightweight polyfills without breaking native DOM APIs, window.open, or navigator.userAgentData,
 * ensuring flawless GitHub menus, modal popovers, and WebRTC audio/video call compatibility.
 */
private fun injectBrowserHelperEngine(view: WebView?, isDesktopMode: Boolean) {
    com.example.ui.components.GlobalSwipeDownTracker.injectSwipeScrollObserver(view)
    val js = """
        (function() {
            try {
                if (!window.chrome) {
                    window.chrome = { runtime: {}, app: { isInstalled: false } };
                }
                if (!document.fullscreenEnabled) {
                    try { Object.defineProperty(document, 'fullscreenEnabled', { value: true, configurable: true }); } catch(e) {}
                }
                if (!document.webkitFullscreenEnabled) {
                    try { Object.defineProperty(document, 'webkitFullscreenEnabled', { value: true, configurable: true }); } catch(e) {}
                }
                if (window.HTMLVideoElement && !HTMLVideoElement.prototype.webkitSupportsFullscreen) {
                    try { HTMLVideoElement.prototype.webkitSupportsFullscreen = true; } catch(e) {}
                }
                if (window.screen && window.screen.orientation) {
                    try {
                        window.screen.orientation.lock = function() { return Promise.resolve(); };
                        window.screen.orientation.unlock = function() {};
                    } catch(e) {}
                }

                // Ensure clicking the fullscreen icon on YouTube videos reliably enters/exits HTML5 fullscreen
                if (!window.__xenoBrowserYtFsHooked && (location.hostname || '').indexOf('youtube.com') !== -1) {
                    window.__xenoBrowserYtFsHooked = true;
                    document.addEventListener('click', function(ev) {
                        try {
                            var t = ev && ev.target;
                            if (!t || typeof t.closest !== 'function') return;
                            var fsBtn = t.closest(
                                '.fullscreen-icon, ' +
                                '.ytp-fullscreen-button, ' +
                                'button[aria-label*="Full screen" i], ' +
                                'button[aria-label*="fullscreen" i], ' +
                                'button[aria-label*="Exit full screen" i]'
                            );
                            if (!fsBtn) return;
                            var wasFs = !!(document.fullscreenElement || document.webkitFullscreenElement);
                            setTimeout(function() {
                                var nowFs = !!(document.fullscreenElement || document.webkitFullscreenElement);
                                if (!wasFs && !nowFs) {
                                    var target = document.getElementById('movie_player') ||
                                                 document.getElementById('player-container-id') ||
                                                 document.querySelector('.html5-video-player') ||
                                                 document.querySelector('video');
                                    if (target) {
                                        if (typeof target.requestFullscreen === 'function') {
                                            var p = target.requestFullscreen();
                                            if (p && typeof p.catch === 'function') p.catch(function(){});
                                        } else if (typeof target.webkitRequestFullscreen === 'function') {
                                            target.webkitRequestFullscreen();
                                        }
                                    }
                                } else if (wasFs && nowFs) {
                                    if (typeof document.exitFullscreen === 'function') {
                                        document.exitFullscreen().catch(function(){});
                                    } else if (typeof document.webkitExitFullscreen === 'function') {
                                        document.webkitExitFullscreen();
                                    }
                                }
                            }, 50);
                        } catch(e) {}
                    }, true);
                }

                // WebRTC Online Call helper: ensure mediaDevices & getUserMedia are exposed and functional
                if (navigator.mediaDevices && !navigator.mediaDevices.getUserMedia && navigator.getUserMedia) {
                    navigator.mediaDevices.getUserMedia = function(constraints) {
                        return new Promise(function(resolve, reject) {
                            navigator.getUserMedia(constraints, resolve, reject);
                        });
                    };
                }
            } catch (e) {}
        })();
    """.trimIndent()
    view?.evaluateJavascript(js, null)
}

enum class BrowserVideoScaleMode(val label: String, val scaleX: Float, val scaleY: Float) {
    FIT("Fit (16:9)", 1.0f, 1.0f),
    FILL_ZOOM("Fill (No Black Bars)", 1.34f, 1.34f),
    STRETCH("Stretch to Fill", 1.32f, 1.0f)
}

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BrowserScreen(
    tabs: List<WebTab>,
    activeTabId: String,
    history: List<BrowserHistoryItem>,
    showTabSwitcher: Boolean,
    showHistoryDialog: Boolean,
    onOpenNewTab: (String) -> Unit,
    onSelectTab: (String) -> Unit,
    onCloseTab: (String) -> Unit,
    onUpdateTabUrl: (String, String) -> Unit,
    onUpdateTabLoading: (Boolean, Int) -> Unit,
    onUpdateTabNavState: (Boolean, Boolean) -> Unit,
    onToggleTabDesktopMode: () -> Unit = {},
    onSetShowTabSwitcher: (Boolean) -> Unit,
    onSetShowHistoryDialog: (Boolean) -> Unit,
    onClearHistory: () -> Unit,
    isBrowserHeaderHidden: Boolean,
    onToggleBrowserHeader: () -> Unit,
    isBrowserFullscreenVideo: Boolean = false,
    onSetBrowserFullscreenVideo: (Boolean) -> Unit = {},
    onAddDownloadItem: (dmId: Long, url: String, fileName: String, mimeType: String, fileSize: Long) -> Unit = { _, _, _, _, _ -> },
    savedPasswords: List<com.example.data.models.SavedPassword> = emptyList(),
    showPasswordManager: Boolean = false,
    onSetShowPasswordManager: (Boolean) -> Unit = {},
    onSavePassword: (domain: String, title: String, username: String, pass: String) -> Unit = { _, _, _, _ -> },
    onDeletePassword: (Long) -> Unit = {},
    onOpenSpace: () -> Unit = {},
    onOpenDownloads: () -> Unit = {},
    onSetShowMediaManager: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current
    val currentTab = tabs.find { it.id == activeTabId } ?: tabs.firstOrNull()

    var urlInputText by remember { mutableStateOf(currentTab?.url ?: "https://www.google.com") }
    var activeWebView by remember { mutableStateOf<WebView?>(null) }
    var showMenu by remember { mutableStateOf(false) }
    var customView by remember { mutableStateOf<android.view.View?>(null) }
    var customViewCallback by remember { mutableStateOf<WebChromeClient.CustomViewCallback?>(null) }
    var videoScaleMode by remember { mutableStateOf(BrowserVideoScaleMode.FIT) }
    var showFullscreenControls by remember { mutableStateOf(true) }
    var isLandscapeLocked by remember { mutableStateOf(true) }
    val context = LocalContext.current

    var pendingWebPermissionRequest by remember { mutableStateOf<PermissionRequest?>(null) }
    val callPermissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        val micOk = perms[Manifest.permission.RECORD_AUDIO] == true ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        val camOk = perms[Manifest.permission.CAMERA] == true ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

        pendingWebPermissionRequest?.let { req ->
            val granted = mutableListOf<String>()
            val requested = req.resources ?: emptyArray()
            for (r in requested) {
                if (r == PermissionRequest.RESOURCE_AUDIO_CAPTURE && micOk) granted.add(r)
                else if (r == PermissionRequest.RESOURCE_VIDEO_CAPTURE && camOk) granted.add(r)
                else if (r == PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID) granted.add(r)
            }
            if (granted.isNotEmpty()) {
                req.grant(granted.toTypedArray())
            } else {
                req.deny()
            }
            pendingWebPermissionRequest = null
        }
    }

    var fileChooserCallback by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }
    val fileChooserLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uris = if (result.resultCode == Activity.RESULT_OK) {
            val data = result.data
            if (data?.clipData != null) {
                val count = data.clipData!!.itemCount
                val list = mutableListOf<Uri>()
                for (i in 0 until count) {
                    list.add(data.clipData!!.getItemAt(i).uri)
                }
                list.toTypedArray()
            } else if (data?.data != null) {
                arrayOf(data.data!!)
            } else {
                null
            }
        } else {
            null
        }
        fileChooserCallback?.onReceiveValue(uris)
        fileChooserCallback = null
    }

    LaunchedEffect(currentTab?.url) {
        if (currentTab != null && !urlInputText.equals(currentTab.url, ignoreCase = true)) {
            urlInputText = currentTab.url
        }
    }

    // Handle physical back button in web navigation
    BackHandler(enabled = activeWebView?.canGoBack() == true && customView == null && !isBrowserFullscreenVideo) {
        activeWebView?.goBack()
    }

    // Handle back button to exit full screen when web page cannot go back
    BackHandler(enabled = (isBrowserHeaderHidden || isBrowserFullscreenVideo) && activeWebView?.canGoBack() != true && customView == null) {
        if (isBrowserFullscreenVideo) {
            val activity = context as? android.app.Activity
            activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            onSetBrowserFullscreenVideo(false)
        } else {
            onToggleBrowserHeader()
        }
    }

    fun submitUrl(input: String) {
        focusManager.clearFocus()
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return

        val targetUrl = when {
            trimmed.startsWith("http://") || trimmed.startsWith("https://") -> trimmed
            trimmed.contains(".") && !trimmed.contains(" ") -> "https://$trimmed"
            else -> "https://www.google.com/search?q=${android.net.Uri.encode(trimmed)}"
        }
        urlInputText = targetUrl
        val isDesktop = currentTab?.isDesktopMode == true
        activeWebView?.let { wv ->
            applyBrowserModeSettings(wv, isDesktop, targetUrl)
            wv.loadUrl(targetUrl, if (isDesktop) DESKTOP_BROWSER_HEADERS else MOBILE_BROWSER_HEADERS)
        }
    }

    fun triggerLandscapeVideo() {
        val activity = context as? android.app.Activity
        activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        onSetBrowserFullscreenVideo(true)
        showFullscreenControls = true

        activeWebView?.evaluateJavascript(
            """
            (function() {
                // 1. Try clicking YouTube's fullscreen button directly if present
                var ytFullscreenBtn = document.querySelector('.ytp-fullscreen-button') ||
                                      document.querySelector('button[aria-label*="Full screen"]') ||
                                      document.querySelector('button[title*="Full screen"]');
                if (ytFullscreenBtn) {
                    ytFullscreenBtn.click();
                    return;
                }

                // 2. Find video and player elements
                var v = document.querySelector('video');
                var p = document.querySelector('.html5-video-player') ||
                        document.querySelector('#movie_player') ||
                        document.querySelector('#player') ||
                        (v ? v.parentElement : null);

                var target = p || v;
                if (target) {
                    if (target.requestFullscreen) {
                        target.requestFullscreen().catch(function() {
                            if (v && v.webkitEnterFullscreen) v.webkitEnterFullscreen();
                        });
                    } else if (target.webkitRequestFullscreen) {
                        target.webkitRequestFullscreen();
                    } else if (v && v.webkitEnterFullscreen) {
                        v.webkitEnterFullscreen();
                    } else if (v && v.requestFullscreen) {
                        v.requestFullscreen();
                    }
                }

                // Expand video in-page as immediate fallback
                if (v) {
                    v.style.setProperty('object-fit', 'cover', 'important');
                }
            })();
            """.trimIndent(), null
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag("browser_screen")
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Top Browser Header & Navigation Strip
            if (!isBrowserHeaderHidden) {
                Surface(
                    tonalElevation = 2.dp,
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Column {

                // Horizontal Tabs Strip (if > 1 tab)
                if (tabs.size > 1) {
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        items(tabs, key = { it.id }) { tab ->
                            val isSelected = tab.id == activeTabId
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier
                                    .clickable { onSelectTab(tab.id) }
                                    .testTag("tab_pill_${tab.id}")
                            ) {
                                Row(
                                    modifier = Modifier.padding(start = 10.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = tab.title.ifBlank { "Tab" },
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.widthIn(max = 100.dp)
                                    )
                                    IconButton(
                                        onClick = { onCloseTab(tab.id) },
                                        modifier = Modifier.size(18.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Close,
                                            contentDescription = "Close tab",
                                            tint = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(12.dp)
                                        )
                                    }
                                }
                            }
                        }

                        item {
                            IconButton(
                                onClick = { onOpenNewTab("https://www.google.com") },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Add,
                                    contentDescription = "New Tab",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }

                // Address & Search Bar Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    // Navigation Arrows
                    IconButton(
                        onClick = { activeWebView?.goBack() },
                        enabled = currentTab?.canGoBack == true,
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    IconButton(
                        onClick = { activeWebView?.goForward() },
                        enabled = currentTab?.canGoForward == true,
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "Forward",
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Sleek Thinner Address Bar Section
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val isOnlineCallSite = currentTab != null && (
                                currentTab.url.contains("meet.google.com", ignoreCase = true) ||
                                currentTab.url.contains("teams.microsoft.com", ignoreCase = true) ||
                                currentTab.url.contains("zoom.us", ignoreCase = true)
                            )
                            if (isOnlineCallSite) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0xFF064E3B),
                                    border = BorderStroke(1.dp, Color(0xFF10B981))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(6.dp)
                                                .background(Color(0xFF34D399), CircleShape)
                                        )
                                        Text(
                                            text = "Call Ready",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF6EE7B7)
                                        )
                                    }
                                }
                            } else {
                                Icon(
                                    imageVector = Icons.Outlined.Language,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            Box(
                                modifier = Modifier.weight(1f),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                if (urlInputText.isEmpty()) {
                                    Text(
                                        text = "Search or type URL...",
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                BasicTextField(
                                    value = urlInputText,
                                    onValueChange = { urlInputText = it },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("browser_address_input"),
                                    singleLine = true,
                                    textStyle = TextStyle(
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontWeight = FontWeight.Medium
                                    ),
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                                    keyboardActions = KeyboardActions(onGo = { submitUrl(urlInputText) }),
                                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary)
                                )
                            }
                            if (urlInputText.isNotEmpty()) {
                                IconButton(
                                    onClick = { urlInputText = "" },
                                    modifier = Modifier.size(20.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Clear,
                                        contentDescription = "Clear",
                                        modifier = Modifier.size(14.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    // Open Browser Tabs Icon (showing count badge)
                    IconButton(
                        onClick = { onSetShowTabSwitcher(true) },
                        modifier = Modifier
                            .size(34.dp)
                            .testTag("browser_tab_switcher_button")
                    ) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(24.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = tabs.size.toString(),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }

                    // Key Icon (Saved Passwords & Auto-fill)
                    IconButton(
                        onClick = { onSetShowPasswordManager(true) },
                        modifier = Modifier
                            .size(34.dp)
                            .testTag("toolbar_password_manager_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Key,
                            contentDescription = "Saved Passwords & Auto-fill",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    // 3-Dot Menu
                    Box {
                        IconButton(
                            onClick = { showMenu = true },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.MoreVert,
                                contentDescription = "Menu",
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("New Tab") },
                                leadingIcon = { Icon(Icons.Filled.Add, null) },
                                onClick = {
                                    showMenu = false
                                    onOpenNewTab("https://www.google.com")
                                }
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = "Desktop Mode",
                                        fontWeight = if (currentTab?.isDesktopMode == true) FontWeight.SemiBold else FontWeight.Normal
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = if (currentTab?.isDesktopMode == true) Icons.Filled.DesktopMac else Icons.Filled.PhoneAndroid,
                                        contentDescription = "Desktop Mode"
                                    )
                                },
                                trailingIcon = {
                                    Checkbox(
                                        checked = currentTab?.isDesktopMode == true,
                                        onCheckedChange = null
                                    )
                                },
                                onClick = {
                                    showMenu = false
                                    val nextDesktopMode = !(currentTab?.isDesktopMode ?: false)
                                    onToggleTabDesktopMode()
                                    activeWebView?.let { wv ->
                                        wv.tag = nextDesktopMode
                                        val activeUrl = wv.url ?: currentTab?.url ?: "https://www.google.com"
                                        val reloadUrl = if (nextDesktopMode) {
                                            activeUrl
                                                .replace("://m.youtube.com", "://www.youtube.com")
                                                .replace("://m.facebook.com", "://www.facebook.com")
                                                .replace(".m.wikipedia.org", ".wikipedia.org")
                                        } else {
                                            activeUrl
                                        }
                                        applyBrowserModeSettings(wv, nextDesktopMode, reloadUrl)
                                        wv.loadUrl(
                                            reloadUrl,
                                            if (nextDesktopMode) DESKTOP_BROWSER_HEADERS else MOBILE_BROWSER_HEADERS
                                        )
                                    }
                                },
                                modifier = Modifier.testTag("browser_menu_desktop_mode_toggle")
                            )
                            DropdownMenuItem(
                                text = { Text("Space Sandbox") },
                                leadingIcon = { Icon(Icons.Filled.Layers, null) },
                                onClick = {
                                    showMenu = false
                                    onOpenSpace()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Downloads") },
                                leadingIcon = { Icon(Icons.Filled.Download, null) },
                                onClick = {
                                    showMenu = false
                                    onOpenDownloads()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Saved Passwords") },
                                leadingIcon = { Icon(Icons.Filled.Key, null) },
                                onClick = {
                                    showMenu = false
                                    onSetShowPasswordManager(true)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Media") },
                                leadingIcon = { Icon(Icons.Filled.SmartDisplay, null) },
                                onClick = {
                                    showMenu = false
                                    onSetShowMediaManager(true)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Watch in Landscape") },
                                leadingIcon = { Icon(Icons.Filled.ScreenRotation, null) },
                                onClick = {
                                    showMenu = false
                                    triggerLandscapeVideo()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Toggle No Black Bars") },
                                leadingIcon = { Icon(Icons.Filled.FitScreen, null) },
                                onClick = {
                                    showMenu = false
                                    videoScaleMode = when (videoScaleMode) {
                                        BrowserVideoScaleMode.FIT -> BrowserVideoScaleMode.FILL_ZOOM
                                        BrowserVideoScaleMode.FILL_ZOOM -> BrowserVideoScaleMode.STRETCH
                                        BrowserVideoScaleMode.STRETCH -> BrowserVideoScaleMode.FIT
                                    }
                                    val modeStr = videoScaleMode.name
                                    activeWebView?.evaluateJavascript(
                                        """
                                        (function() {
                                            var v = document.querySelector('video');
                                            if (v) {
                                                v.style.objectFit = '$modeStr' === 'FILL_ZOOM' ? 'cover' : ('$modeStr' === 'STRETCH' ? 'fill' : 'contain');
                                            }
                                        })();
                                        """.trimIndent(), null
                                    )
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Refresh") },
                                leadingIcon = { Icon(Icons.Filled.Refresh, null) },
                                onClick = {
                                    showMenu = false
                                    activeWebView?.reload()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Home") },
                                leadingIcon = { Icon(Icons.Filled.Home, null) },
                                onClick = {
                                    showMenu = false
                                    urlInputText = "https://www.google.com"
                                    val isDesktop = currentTab?.isDesktopMode == true
                                    activeWebView?.let { wv ->
                                        applyBrowserModeSettings(wv, isDesktop, "https://www.google.com")
                                        wv.loadUrl("https://www.google.com", if (isDesktop) DESKTOP_BROWSER_HEADERS else MOBILE_BROWSER_HEADERS)
                                    }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Hide Address Bar") },
                                leadingIcon = { Icon(Icons.Filled.Fullscreen, null) },
                                onClick = {
                                    showMenu = false
                                    onToggleBrowserHeader()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Browsing History") },
                                leadingIcon = { Icon(Icons.Filled.History, null) },
                                onClick = {
                                    showMenu = false
                                    onSetShowHistoryDialog(true)
                                }
                            )
                        }
                    }
                }

                if (currentTab?.isLoading == true && (currentTab.progress in 1..99)) {
                    LinearProgressIndicator(
                        progress = { currentTab.progress / 100f },
                        modifier = Modifier.fillMaxWidth().height(2.dp),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }

        // Main WebView Container
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            currentTab?.let { tab ->
                var hasRenderCrash by remember(tab.id) { mutableStateOf(false) }
                var reloadKey by remember(tab.id) { mutableIntStateOf(0) }

                if (hasRenderCrash) {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp)
                            .align(Alignment.Center)
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(36.dp)
                            )
                            Text(
                                text = "Web Renderer Reset",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "The browser engine encountered an emulator graphics reset. Tap reload to restore this page.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                            Button(
                                onClick = {
                                    hasRenderCrash = false
                                    reloadKey++
                                }
                            ) {
                                Icon(Icons.Filled.Refresh, null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Reload Webpage")
                            }
                        }
                    }
                } else {
                    key(tab.id, reloadKey) {
                        AndroidView(
                            modifier = Modifier.fillMaxSize(),
                            factory = { ctx ->
                                WebView(ctx).apply {
                                    try {
                                        setLayerType(android.view.View.LAYER_TYPE_SOFTWARE, null)
                                    } catch (_: Exception) {}

                                    applyBrowserModeSettings(this, tab.isDesktopMode, tab.url)
                                    isNestedScrollingEnabled = true

                                    try {
                                        val cm = android.webkit.CookieManager.getInstance()
                                        cm.setAcceptCookie(true)
                                        cm.setAcceptThirdPartyCookies(this, true)
                                    } catch (_: Exception) {}

                                    setDownloadListener { url, userAgent, contentDisposition, mimetype, contentLength ->
                                        try {
                                            val fileName = android.webkit.URLUtil.guessFileName(url, contentDisposition, mimetype)
                                            val request = android.app.DownloadManager.Request(Uri.parse(url)).apply {
                                                setMimeType(mimetype)
                                                val cookies = android.webkit.CookieManager.getInstance().getCookie(url)
                                                addRequestHeader("cookie", cookies)
                                                addRequestHeader("User-Agent", userAgent)
                                                setDescription("Downloading file...")
                                                setTitle(fileName)
                                                setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                                                setDestinationInExternalPublicDir(android.os.Environment.DIRECTORY_DOWNLOADS, fileName)
                                            }
                                            val dm = ctx.getSystemService(android.content.Context.DOWNLOAD_SERVICE) as android.app.DownloadManager
                                            val downloadId = dm.enqueue(request)
                                            onAddDownloadItem(downloadId, url, fileName, mimetype, contentLength)
                                            android.widget.Toast.makeText(ctx, "Downloading $fileName...", android.widget.Toast.LENGTH_SHORT).show()
                                        } catch (e: Exception) {
                                            android.widget.Toast.makeText(ctx, "Download failed: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    }

                                    webViewClient = object : WebViewClient() {
                                        override fun onReceivedError(view: WebView?, errorCode: Int, description: String?, failingUrl: String?) {
                                            super.onReceivedError(view, errorCode, description, failingUrl)
                                            onUpdateTabLoading(false, 100)
                                        }

                                        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                            super.onPageStarted(view, url, favicon)
                                            onUpdateTabLoading(true, 15)
                                            val isDesktop = (view?.tag as? Boolean) ?: tab.isDesktopMode
                                            if (url != null) {
                                                if (view != null) {
                                                    applyBrowserModeSettings(view, isDesktop, url)
                                                    if (url.contains("meet.google.com") || url.contains("teams.microsoft.com") || url.contains("zoom.us")) {
                                                        view.keepScreenOn = true
                                                    }
                                                }
                                                onUpdateTabNavState(view?.canGoBack() ?: false, view?.canGoForward() ?: false)
                                            }
                                            injectBrowserHelperEngine(view, isDesktop)
                                        }

                                        override fun onPageCommitVisible(view: WebView?, url: String?) {
                                            super.onPageCommitVisible(view, url)
                                            val isDesktop = (view?.tag as? Boolean) ?: tab.isDesktopMode
                                            injectBrowserHelperEngine(view, isDesktop)
                                        }

                                        override fun onPageFinished(view: WebView?, url: String?) {
                                            super.onPageFinished(view, url)
                                            onUpdateTabLoading(false, 100)
                                            val isDesktop = (view?.tag as? Boolean) ?: tab.isDesktopMode
                                            if (url != null) {
                                                val title = view?.title ?: ""
                                                onUpdateTabUrl(url, title)
                                                onUpdateTabNavState(view?.canGoBack() ?: false, view?.canGoForward() ?: false)
                                            }
                                            injectBrowserHelperEngine(view, isDesktop)
                                            try {
                                                CookieManager.getInstance().flush()
                                            } catch (_: Exception) {}
                                        }

                                        override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                                            super.doUpdateVisitedHistory(view, url, isReload)
                                            val isDesktop = (view?.tag as? Boolean) ?: tab.isDesktopMode
                                            injectBrowserHelperEngine(view, isDesktop)
                                            if (url != null) {
                                                onUpdateTabNavState(view?.canGoBack() ?: false, view?.canGoForward() ?: false)
                                            }
                                        }

                                        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                            val targetUrl = request?.url?.toString() ?: return false
                                            if (targetUrl.startsWith("intent://")) {
                                                try {
                                                    val parsed = Intent.parseUri(targetUrl, Intent.URI_INTENT_SCHEME)
                                                    val fallbackUrl = parsed.getStringExtra("browser_fallback_url")
                                                    if (!fallbackUrl.isNullOrBlank() && (fallbackUrl.startsWith("http://") || fallbackUrl.startsWith("https://"))) {
                                                        view?.loadUrl(fallbackUrl)
                                                        return true
                                                    }
                                                    ctx.startActivity(parsed)
                                                } catch (_: Exception) {}
                                                return true
                                            }
                                            if (targetUrl.startsWith("whatsapp://") || targetUrl.startsWith("tel:") || targetUrl.startsWith("mailto:")) {
                                                try {
                                                    val intent = Intent.parseUri(targetUrl, Intent.URI_INTENT_SCHEME)
                                                    ctx.startActivity(intent)
                                                } catch (_: Exception) {}
                                                return true
                                            }
                                            if (targetUrl.startsWith("http://") || targetUrl.startsWith("https://") ||
                                                targetUrl.startsWith("about:") || targetUrl.startsWith("blob:") ||
                                                targetUrl.startsWith("data:") || targetUrl.startsWith("javascript:")
                                            ) {
                                                return false
                                            }
                                            return try {
                                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl))
                                                ctx.startActivity(intent)
                                                true
                                            } catch (_: Exception) {
                                                false
                                            }
                                        }

                                        override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
                                            try {
                                                view?.let {
                                                    val parent = it.parent as? android.view.ViewGroup
                                                    parent?.removeView(it)
                                                    it.stopLoading()
                                                    it.destroy()
                                                }
                                            } catch (_: Exception) {}
                                            activeWebView = null
                                            hasRenderCrash = true
                                            return true
                                        }
                                    }

                                    webChromeClient = object : WebChromeClient() {
                                        override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                            super.onProgressChanged(view, newProgress)
                                            onUpdateTabLoading(newProgress < 100, newProgress)
                                        }

                                        override fun onReceivedTitle(view: WebView?, title: String?) {
                                            super.onReceivedTitle(view, title)
                                            if (!title.isNullOrBlank() && view?.url != null) {
                                                onUpdateTabUrl(view.url ?: "", title)
                                            }
                                        }

                                        override fun onPermissionRequest(request: PermissionRequest?) {
                                            if (request == null) return
                                            val mainHandler = Handler(Looper.getMainLooper())
                                            mainHandler.post {
                                                try {
                                                    val resources = request.resources ?: emptyArray()
                                                    val needsAudio = resources.contains(PermissionRequest.RESOURCE_AUDIO_CAPTURE)
                                                    val needsVideo = resources.contains(PermissionRequest.RESOURCE_VIDEO_CAPTURE)

                                                    val micGranted = ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                                                    val camGranted = ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

                                                    if ((needsAudio && !micGranted) || (needsVideo && !camGranted)) {
                                                        pendingWebPermissionRequest = request
                                                        val toRequest = mutableListOf<String>()
                                                        if (needsAudio && !micGranted) toRequest.add(Manifest.permission.RECORD_AUDIO)
                                                        if (needsVideo && !camGranted) toRequest.add(Manifest.permission.CAMERA)
                                                        callPermissionsLauncher.launch(toRequest.toTypedArray())
                                                    } else {
                                                        this@apply.keepScreenOn = true
                                                        request.grant(resources)
                                                    }
                                                } catch (_: Exception) {
                                                    try {
                                                        request.grant(request.resources)
                                                    } catch (_: Exception) {}
                                                }
                                            }
                                        }

                                        override fun onPermissionRequestCanceled(request: PermissionRequest?) {
                                            if (pendingWebPermissionRequest == request) {
                                                pendingWebPermissionRequest = null
                                            }
                                        }

                                        override fun onGeolocationPermissionsShowPrompt(origin: String?, callback: GeolocationPermissions.Callback?) {
                                            callback?.invoke(origin, true, false)
                                        }

                                        override fun onJsAlert(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
                                            try {
                                                android.widget.Toast.makeText(ctx, message ?: "", android.widget.Toast.LENGTH_SHORT).show()
                                            } catch (_: Exception) {}
                                            result?.confirm()
                                            return true
                                        }

                                        override fun onJsConfirm(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
                                            result?.confirm()
                                            return true
                                        }

                                        override fun onShowFileChooser(
                                            webView: WebView?,
                                            filePathCallback: ValueCallback<Array<Uri>>?,
                                            fileChooserParams: FileChooserParams?
                                        ): Boolean {
                                            fileChooserCallback?.onReceiveValue(null)
                                            fileChooserCallback = filePathCallback
                                            val intent = fileChooserParams?.createIntent() ?: Intent(Intent.ACTION_GET_CONTENT).apply {
                                                type = "*/*"
                                                addCategory(Intent.CATEGORY_OPENABLE)
                                            }
                                            return try {
                                                fileChooserLauncher.launch(intent)
                                                true
                                            } catch (_: Exception) {
                                                fileChooserCallback?.onReceiveValue(null)
                                                fileChooserCallback = null
                                                false
                                            }
                                        }

                                        override fun onCreateWindow(
                                            view: WebView?,
                                            isDialog: Boolean,
                                            isUserGesture: Boolean,
                                            resultMsg: Message?
                                        ): Boolean {
                                            val transport = resultMsg?.obj as? WebView.WebViewTransport ?: return false
                                            val tempWebView = WebView(view?.context ?: return false).apply {
                                                settings.javaScriptEnabled = true
                                                settings.domStorageEnabled = true
                                                webViewClient = object : WebViewClient() {
                                                    override fun shouldOverrideUrlLoading(v: WebView?, req: WebResourceRequest?): Boolean {
                                                        val url = req?.url?.toString() ?: return false
                                                        view.loadUrl(url)
                                                        return true
                                                    }
                                                }
                                            }
                                            transport.webView = tempWebView
                                            resultMsg.sendToTarget()
                                            return true
                                        }

                                        override fun onShowCustomView(view: android.view.View?, callback: WebChromeClient.CustomViewCallback?) {
                                            super.onShowCustomView(view, callback)
                                            customView = view
                                            customViewCallback = callback
                                            onSetBrowserFullscreenVideo(true)
                                            (ctx as? android.app.Activity)?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                                        }

                                        override fun onHideCustomView() {
                                            super.onHideCustomView()
                                            try {
                                                customViewCallback?.onCustomViewHidden()
                                            } catch (_: Exception) {}
                                            customView = null
                                            customViewCallback = null
                                            onSetBrowserFullscreenVideo(false)
                                            (ctx as? android.app.Activity)?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                                        }
                                    }

                                    tag = tab.isDesktopMode
                                    loadUrl(
                                        tab.url,
                                        if (tab.isDesktopMode) DESKTOP_BROWSER_HEADERS else MOBILE_BROWSER_HEADERS
                                    )
                                    activeWebView = this
                                }
                            },
                            update = { webView ->
                                webView.tag = tab.isDesktopMode
                                activeWebView = webView
                            }
                        )
                    }
                }
            }
        }
    }

        // Floating pill to trigger Fullscreen Landscape when on YouTube or video sites
        if (!isBrowserFullscreenVideo && customView == null && currentTab != null && (currentTab.url.contains("youtube.com") || currentTab.url.contains("youtu.be") || currentTab.url.contains("vimeo.com"))) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(end = 16.dp, bottom = 20.dp),
                contentAlignment = Alignment.BottomEnd
            ) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.primary,
                    shadowElevation = 6.dp,
                    tonalElevation = 6.dp,
                    modifier = Modifier
                        .clickable { triggerLandscapeVideo() }
                        .testTag("floating_landscape_video_pill")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.SmartDisplay,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Landscape Fullscreen",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }
        }

        // Floating icon button to exit landscape in-page mode if customView is null
        if (isBrowserFullscreenVideo && customView == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                contentAlignment = Alignment.TopEnd
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.94f),
                    shadowElevation = 6.dp,
                    tonalElevation = 6.dp,
                    modifier = Modifier
                        .size(48.dp)
                        .clickable {
                            val activity = context as? android.app.Activity
                            activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                            onSetBrowserFullscreenVideo(false)
                        }
                        .testTag("exit_landscape_btn")
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Icon(
                            imageVector = Icons.Filled.FullscreenExit,
                            contentDescription = "Exit Landscape",
                            modifier = Modifier.size(26.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        }

        // Floating icon button to exit full screen when header is hidden
        if (isBrowserHeaderHidden && !isBrowserFullscreenVideo) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                contentAlignment = Alignment.TopEnd
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.94f),
                    shadowElevation = 6.dp,
                    tonalElevation = 6.dp,
                    modifier = Modifier
                        .size(48.dp)
                        .clickable { onToggleBrowserHeader() }
                        .testTag("exit_fullscreen_btn")
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Icon(
                            imageVector = Icons.Filled.FullscreenExit,
                            contentDescription = "Exit Full Screen",
                            modifier = Modifier.size(26.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        }
    // Tab Switcher Modal
    if (showTabSwitcher) {
        ModalBottomSheet(
            onDismissRequest = { onSetShowTabSwitcher(false) },
            sheetState = rememberModalBottomSheetState(),
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Open Tabs (${tabs.size})",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = {
                        onOpenNewTab("https://www.google.com")
                    }) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = "New Tab",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(tabs, key = { it.id }) { tab ->
                        val isSelected = tab.id == activeTabId
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectTab(tab.id) }
                                .testTag("tab_item_${tab.id}"),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = tab.title.ifBlank { "New Tab" },
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = tab.url,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                IconButton(
                                    onClick = { onCloseTab(tab.id) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Close,
                                        contentDescription = "Close tab",
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Browsing History Dialog
    if (showHistoryDialog) {
        AlertDialog(
            onDismissRequest = { onSetShowHistoryDialog(false) },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Browsing History", fontWeight = FontWeight.Bold)
                    if (history.isNotEmpty()) {
                        IconButton(onClick = onClearHistory) {
                            Icon(
                                imageVector = Icons.Outlined.Delete,
                                contentDescription = "Clear History",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            },
            text = {
                if (history.isEmpty()) {
                    Text("No browsing history saved yet.")
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 300.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(history, key = { "${it.id}_${it.timestamp}" }) { item ->
                            val dateStr = remember(item.timestamp) {
                                SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(Date(item.timestamp))
                            }
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        urlInputText = item.url
                                        activeWebView?.loadUrl(item.url)
                                        onSetShowHistoryDialog(false)
                                    }
                                    .padding(vertical = 6.dp)
                            ) {
                                Text(
                                    text = item.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = item.url,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = dateStr,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            HorizontalDivider()
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { onSetShowHistoryDialog(false) }) {
                    Text("Close")
                }
            }
        )
    }

    // Modal: Password Manager & Auto-fill
    if (showPasswordManager) {
        val domain = currentTab?.url?.let {
            Uri.parse(it).host?.replace("www.", "")
        } ?: ""

        PasswordManagerDialog(
            currentDomain = domain,
            savedPasswords = savedPasswords,
            onSavePassword = onSavePassword,
            onDeletePassword = onDeletePassword,
            onAutofillIntoTab = { username, pass ->
                val js = """
                    (function(u, p) {
                        let passInputs = document.querySelectorAll('input[type="password"]');
                        if (passInputs.length === 0) {
                            passInputs = document.querySelectorAll('input[name*="pass"], input[id*="pass"]');
                        }
                        passInputs.forEach(function(passInput) {
                            passInput.value = p;
                            passInput.dispatchEvent(new Event('input', { bubbles: true }));
                            passInput.dispatchEvent(new Event('change', { bubbles: true }));
                            let form = passInput.form || document;
                            let userInput = form.querySelector('input[type="email"], input[type="text"], input[name*="user"], input[name*="login"], input[id*="user"], input[id*="login"]');
                            if (userInput) {
                                userInput.value = u;
                                userInput.dispatchEvent(new Event('input', { bubbles: true }));
                                userInput.dispatchEvent(new Event('change', { bubbles: true }));
                            }
                        });
                    })('${username.replace("'", "\\'")}', '${pass.replace("'", "\\'")}');
                """.trimIndent()
                activeWebView?.evaluateJavascript(js, null)
            },
            onDismiss = { onSetShowPasswordManager(false) }
        )
    }

    // Fullscreen Video CustomView Overlay (rendered inside Box so WebView stays attached underneath)
    customView?.let { activeCustomView ->
        val scaleX = videoScaleMode.scaleX
        val scaleY = videoScaleMode.scaleY

        BackHandler {
            val activity = context as? android.app.Activity
            activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            onSetBrowserFullscreenVideo(false)
            try {
                customViewCallback?.onCustomViewHidden()
            } catch (_: Exception) {}
            customView = null
            customViewCallback = null
        }

        LaunchedEffect(showFullscreenControls) {
            if (showFullscreenControls) {
                delay(3500L)
                showFullscreenControls = false
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    showFullscreenControls = !showFullscreenControls
                }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = scaleX,
                        scaleY = scaleY,
                        clip = true
                    ),
                contentAlignment = Alignment.Center
            ) {
                AndroidView(
                    factory = {
                        (activeCustomView.parent as? android.view.ViewGroup)?.removeView(activeCustomView)
                        activeCustomView
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Top Control Overlay Bar
            AnimatedVisibility(
                visible = showFullscreenControls,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.TopCenter)
            ) {
                Surface(
                    color = Color.Black.copy(alpha = 0.75f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            IconButton(
                                onClick = {
                                    val activity = context as? android.app.Activity
                                    activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                                    onSetBrowserFullscreenVideo(false)
                                    try {
                                        customViewCallback?.onCustomViewHidden()
                                    } catch (_: Exception) {}
                                    customView = null
                                    customViewCallback = null
                                },
                                modifier = Modifier
                                    .size(38.dp)
                                    .background(Color.White.copy(alpha = 0.2f), CircleShape)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = "Exit Fullscreen",
                                    tint = Color.White
                                )
                            }

                            Text(
                                text = "Landscape Video",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )

                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                    text = videoScaleMode.label,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    videoScaleMode = when (videoScaleMode) {
                                        BrowserVideoScaleMode.FIT -> BrowserVideoScaleMode.FILL_ZOOM
                                        BrowserVideoScaleMode.FILL_ZOOM -> BrowserVideoScaleMode.STRETCH
                                        BrowserVideoScaleMode.STRETCH -> BrowserVideoScaleMode.FIT
                                    }
                                    val modeStr = videoScaleMode.name
                                    activeWebView?.evaluateJavascript(
                                        """
                                        (function() {
                                            var v = document.querySelector('video');
                                            if (v) {
                                                v.style.objectFit = '$modeStr' === 'FILL_ZOOM' ? 'cover' : ('$modeStr' === 'STRETCH' ? 'fill' : 'contain');
                                            }
                                        })();
                                        """.trimIndent(), null
                                    )
                                },
                                shape = RoundedCornerShape(20.dp),
                                modifier = Modifier.height(36.dp)
                            ) {
                                Icon(
                                    imageVector = if (videoScaleMode == BrowserVideoScaleMode.FILL_ZOOM) Icons.Filled.CropFree else Icons.Filled.FitScreen,
                                    contentDescription = "Aspect Ratio",
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = when (videoScaleMode) {
                                        BrowserVideoScaleMode.FIT -> "No Black Bars"
                                        BrowserVideoScaleMode.FILL_ZOOM -> "Stretch"
                                        BrowserVideoScaleMode.STRETCH -> "Fit 16:9"
                                    },
                                    fontSize = 12.sp
                                )
                            }

                            IconButton(
                                onClick = {
                                    val activity = context as? android.app.Activity
                                    isLandscapeLocked = !isLandscapeLocked
                                    activity?.requestedOrientation = if (isLandscapeLocked) {
                                        android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                                    } else {
                                        android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                                    }
                                },
                                modifier = Modifier
                                    .size(38.dp)
                                    .background(Color.White.copy(alpha = 0.2f), CircleShape)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.ScreenRotation,
                                    contentDescription = "Rotate Screen",
                                    tint = if (isLandscapeLocked) MaterialTheme.colorScheme.primary else Color.White
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
}
