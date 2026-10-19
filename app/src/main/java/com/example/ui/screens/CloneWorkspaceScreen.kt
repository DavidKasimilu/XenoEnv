package com.example.ui.screens

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.view.View
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.PermissionRequest
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DesktopMac
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.data.models.ClonedAppInstance
import com.example.ui.components.AppIconImage
import com.example.ui.components.parseHexColor

private const val MOBILE_USER_AGENT =
    "Mozilla/5.0 (Linux; Android 14; Pixel 8 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"

private const val DESKTOP_USER_AGENT =
    "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

private val WHATSAPP_WEB_HEADERS = mapOf(
    "Sec-CH-UA" to "\"Google Chrome\";v=\"131\", \"Chromium\";v=\"131\", \"Not_A Brand\";v=\"24\"",
    "Sec-CH-UA-Mobile" to "?0",
    "Sec-CH-UA-Platform" to "\"Linux\"",
    "Upgrade-Insecure-Requests" to "1"
)

/**
 * Lightweight, idempotent WhatsApp Web Support Engine for Android WebView:
 * Polyfills missing Desktop Chrome APIs (`window.Notification`, `window.chrome`, `navigator.userAgentData`)
 * and ensures `#app` stays visible without heavy CSS zoom or repeated ServiceWorker mutations that could
 * overload the emulator's Chromium renderer process.
 */
private fun injectWhatsAppWebSupportEngine(view: WebView?) {
    val js = """
        (function() {
            try {
                if (!window.__xenoWaPolyfilled) {
                    window.__xenoWaPolyfilled = true;

                    if (!window.chrome) {
                        window.chrome = {
                            runtime: {},
                            app: { isInstalled: false },
                            webstore: {}
                        };
                    }

                    if (typeof window.Notification === 'undefined') {
                        var MockNotification = function(title, options) {
                            this.title = title;
                            this.options = options || {};
                            this.close = function() {};
                        };
                        MockNotification.permission = 'granted';
                        MockNotification.requestPermission = function(cb) {
                            if (typeof cb === 'function') cb('granted');
                            return Promise.resolve('granted');
                        };
                        window.Notification = MockNotification;
                    }

                    Object.defineProperty(navigator, 'userAgentData', {
                        get: function() {
                            return {
                                brands: [
                                    { brand: 'Google Chrome', version: '131' },
                                    { brand: 'Chromium', version: '131' },
                                    { brand: 'Not_A Brand', version: '24' }
                                ],
                                mobile: false,
                                platform: 'Linux',
                                getHighEntropyValues: function() {
                                    return Promise.resolve({
                                        architecture: 'x86',
                                        bitness: '64',
                                        mobile: false,
                                        model: '',
                                        platform: 'Linux',
                                        platformVersion: '6.5.0',
                                        uaFullVersion: '131.0.0.0'
                                    });
                                }
                            };
                        },
                        configurable: true
                    });
                    Object.defineProperty(navigator, 'platform', { get: function() { return 'Linux x86_64'; }, configurable: true });
                    Object.defineProperty(navigator, 'vendor', { get: function() { return 'Google Inc.'; }, configurable: true });
                    Object.defineProperty(navigator, 'maxTouchPoints', { get: function() { return 0; }, configurable: true });
                    Object.defineProperty(navigator, 'webdriver', { get: function() { return false; }, configurable: true });
                }

                var styleId = 'xeno-wa-support-style';
                if (!document.getElementById(styleId) && document.head) {
                    var st = document.createElement('style');
                    st.id = styleId;
                    st.textContent =
                        'html, body { width: 100% !important; height: 100% !important; min-height: 100vh !important; overflow: auto !important; background-color: #111b21 !important; } ' +
                        '#app { min-width: 320px !important; min-height: 100vh !important; width: 100% !important; height: 100% !important; display: block !important; visibility: visible !important; opacity: 1 !important; } ' +
                        '#app > div { min-width: 320px !important; width: 100% !important; height: 100% !important; overflow: auto !important; }';
                    document.head.appendChild(st);
                }
            } catch(e) {}
        })();
    """.trimIndent()
    view?.evaluateJavascript(js, null)
}

/**
 * Non-intrusive Instagram Reels & Smooth Scrolling Helper Engine:
 * Enhances video playback, pre-buffering, scroll-snap physics, and passive touch handling
 * in-place without reloading or interrupting the active Instagram session.
 */
private fun injectInstagramReelsAndScrollHelper(view: WebView?) {
    val js = """
        (function() {
            try {
                // 1. Passive touch & wheel listener optimization for 60fps+ scroll responsiveness
                if (!window.__xenoIgPassivePatched) {
                    window.__xenoIgPassivePatched = true;
                    var origAddEventListener = EventTarget.prototype.addEventListener;
                    EventTarget.prototype.addEventListener = function(type, listener, options) {
                        if (type === 'touchstart' || type === 'touchmove' || type === 'wheel' || type === 'mousewheel') {
                            var newOpts = options;
                            if (typeof options === 'boolean') {
                                newOpts = { capture: options, passive: true };
                            } else if (typeof options === 'object' && options !== null) {
                                if (typeof options.passive === 'undefined') {
                                    newOpts = Object.assign({}, options, { passive: true });
                                }
                            } else if (typeof options === 'undefined') {
                                newOpts = { passive: true };
                            }
                            return origAddEventListener.call(this, type, listener, newOpts);
                        }
                        return origAddEventListener.call(this, type, listener, options);
                    };
                }

                // 2. Smooth Scroll, GPU Layer Promotion & Reels Snap CSS
                var styleId = 'xeno-ig-reels-smooth-style';
                if (!document.getElementById(styleId) && document.head) {
                    var st = document.createElement('style');
                    st.id = styleId;
                    st.textContent =
                        'html, body { -webkit-overflow-scrolling: touch !important; overscroll-behavior-y: contain !important; overflow-x: hidden !important; touch-action: pan-y manipulation !important; -webkit-tap-highlight-color: transparent !important; } ' +
                        'main, section, article, div[role="main"], div[style*="scroll-snap"] { -webkit-overflow-scrolling: touch !important; } ' +
                        'div[style*="scroll-snap-type"] > div, div[class*=" reel"], article { scroll-snap-stop: always !important; } ' +
                        'video { transform: translateZ(0) !important; -webkit-transform: translateZ(0) !important; backface-visibility: hidden !important; -webkit-backface-visibility: hidden !important; will-change: transform !important; object-fit: cover; } ' +
                        '::-webkit-scrollbar { width: 0px !important; height: 0px !important; display: none !important; }';
                    document.head.appendChild(st);
                }

                // 3. Reels Video Inline Playback, Preload & Viewport Intersection Optimizer
                if (!window.__xenoIgVideoHelper) {
                    window.__xenoIgVideoHelper = {
                        userPausedMap: new WeakMap(),
                        observedVideos: new WeakSet(),
                        observer: null
                    };

                    if ('IntersectionObserver' in window) {
                        window.__xenoIgVideoHelper.observer = new IntersectionObserver(function(entries) {
                            entries.forEach(function(entry) {
                                var vid = entry.target;
                                if (!vid || vid.tagName !== 'VIDEO') return;
                                if (entry.isIntersecting && entry.intersectionRatio >= 0.6) {
                                    vid.preload = 'auto';
                                    // Pre-warm next video in DOM for zero-lag Reel swipe
                                    try {
                                        var allVids = Array.prototype.slice.call(document.querySelectorAll('video'));
                                        var idx = allVids.indexOf(vid);
                                        if (idx >= 0 && idx + 1 < allVids.length) {
                                            allVids[idx + 1].preload = 'auto';
                                        }
                                    } catch(e) {}

                                    if (vid.paused && !window.__xenoIgVideoHelper.userPausedMap.get(vid)) {
                                        var playPromise = vid.play();
                                        if (playPromise && typeof playPromise.catch === 'function') {
                                            playPromise.catch(function() {
                                                // If autoplay with sound is blocked before first interaction, play muted smoothly
                                                if (!vid.muted) {
                                                    vid.muted = true;
                                                    vid.play().catch(function(){});
                                                }
                                            });
                                        }
                                    }
                                } else if (!entry.isIntersecting || entry.intersectionRatio < 0.15) {
                                    // Pause off-screen Reels so background decoders don't cause scroll frame drops
                                    if (!vid.paused) {
                                        try { vid.pause(); } catch(e) {}
                                    }
                                }
                            });
                        }, { threshold: [0.0, 0.15, 0.6, 0.85] });
                    }
                }

                function optimizeInstagramVideos() {
                    var videos = document.querySelectorAll('video');
                    for (var i = 0; i < videos.length; i++) {
                        var v = videos[i];
                        if (!v.hasAttribute('playsinline')) v.setAttribute('playsinline', 'true');
                        if (!v.hasAttribute('webkit-playsinline')) v.setAttribute('webkit-playsinline', 'true');
                        v.playsInline = true;
                        if (!v.preload || v.preload === 'none' || v.preload === 'metadata') {
                            v.preload = 'auto';
                        }

                        if (!window.__xenoIgVideoHelper.observedVideos.has(v)) {
                            window.__xenoIgVideoHelper.observedVideos.add(v);
                            // Track manual tap-to-pause vs scroll-away pause
                            v.addEventListener('click', function() {
                                var self = this;
                                setTimeout(function() {
                                    window.__xenoIgVideoHelper.userPausedMap.set(self, self.paused);
                                }, 80);
                            }, { passive: true });

                            // Recover smoothly from mid-reel buffer stalls
                            v.addEventListener('stalled', function() {
                                var self = this;
                                if (!self.paused && self.readyState >= 2) {
                                    self.play().catch(function(){});
                                }
                            }, { passive: true });

                            if (window.__xenoIgVideoHelper.observer) {
                                window.__xenoIgVideoHelper.observer.observe(v);
                            }
                        }
                    }
                }

                optimizeInstagramVideos();

                if (!window.__xenoIgMutationObserver && window.MutationObserver && document.body) {
                    var throttleTimer = null;
                    window.__xenoIgMutationObserver = new MutationObserver(function() {
                        if (throttleTimer) return;
                        throttleTimer = setTimeout(function() {
                            throttleTimer = null;
                            optimizeInstagramVideos();
                        }, 180);
                    });
                    window.__xenoIgMutationObserver.observe(document.body, {
                        childList: true,
                        subtree: true
                    });
                }
            } catch(e) {}
        })();
    """.trimIndent()
    view?.evaluateJavascript(js, null)
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun CloneWorkspaceScreen(
    instance: ClonedAppInstance,
    onCloseWorkspace: () -> Unit,
    onUrlChanged: (String) -> Unit,
    onToggleDesktopMode: () -> Unit,
    onOpenInBrowser: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var loadProgress by remember { mutableIntStateOf(0) }
    var currentTitle by remember { mutableStateOf(instance.displayName) }
    var currentUrl by remember { mutableStateOf(instance.lastUrl) }
    var canGoBack by remember { mutableStateOf(false) }
    var hasRenderCrash by remember(instance.instanceId) { mutableStateOf(false) }
    var useSoftwareLayer by remember(instance.instanceId) { mutableStateOf(false) }
    var reloadKey by remember(instance.instanceId) { mutableIntStateOf(0) }

    val badgeColor = remember(instance.badgeColorHex) { parseHexColor(instance.badgeColorHex) }

    val isWhatsapp = remember(instance.instanceId, instance.packageName, instance.targetUrl) {
        instance.packageName.contains("whatsapp", ignoreCase = true) ||
                instance.originalName.contains("whatsapp", ignoreCase = true) ||
                instance.displayName.contains("whatsapp", ignoreCase = true) ||
                instance.targetUrl.contains("whatsapp.com", ignoreCase = true)
    }

    val isInstagram = remember(instance.instanceId, instance.packageName, instance.targetUrl) {
        instance.packageName.contains("instagram", ignoreCase = true) ||
                instance.originalName.contains("instagram", ignoreCase = true) ||
                instance.displayName.contains("instagram", ignoreCase = true) ||
                instance.targetUrl.contains("instagram.com", ignoreCase = true)
    }

    // Properly release WebView renderer resources when leaving CloneWorkspaceScreen
    DisposableEffect(instance.instanceId) {
        onDispose {
            try {
                webViewInstance?.let { wv ->
                    (wv.parent as? ViewGroup)?.removeView(wv)
                    wv.stopLoading()
                    wv.destroy()
                }
            } catch (_: Exception) {}
            webViewInstance = null
        }
    }

    BackHandler {
        if (webViewInstance?.canGoBack() == true) {
            webViewInstance?.goBack()
        } else {
            onCloseWorkspace()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .testTag("clone_workspace_${instance.instanceId}")
    ) {
        // Workspace Top Bar - Sleek, Compact & Space-Efficient
        Surface(
            tonalElevation = 2.dp,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onCloseWorkspace,
                    modifier = Modifier
                        .size(32.dp)
                        .testTag("workspace_back_btn")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                val appIcon = remember(instance.packageName) {
                    try {
                        if (instance.packageName.isNotBlank()) {
                            context.packageManager.getApplicationIcon(instance.packageName)
                        } else null
                    } catch (_: Exception) {
                        null
                    }
                }
                AppIconImage(
                    iconDrawable = appIcon,
                    fallbackName = instance.originalName,
                    modifier = Modifier.size(22.dp)
                )

                Spacer(modifier = Modifier.width(8.dp))

                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = instance.displayName,
                        style = MaterialTheme.typography.titleSmall.copy(fontSize = 13.5.sp),
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = badgeColor.copy(alpha = 0.15f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, badgeColor.copy(alpha = 0.5f))
                    ) {
                        Text(
                            text = instance.badgeTag,
                            color = badgeColor,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(4.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    IconButton(
                        onClick = {
                            if (isWhatsapp) {
                                webViewInstance?.loadUrl("https://web.whatsapp.com/", WHATSAPP_WEB_HEADERS)
                            } else {
                                onToggleDesktopMode()
                                webViewInstance?.let { wv ->
                                    wv.settings.userAgentString = if (!instance.isDesktopMode) DESKTOP_USER_AGENT else MOBILE_USER_AGENT
                                    wv.reload()
                                }
                            }
                        },
                        modifier = Modifier
                            .size(30.dp)
                            .testTag("toggle_desktop_mode_btn")
                    ) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (instance.isDesktopMode || isWhatsapp) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                            modifier = Modifier.size(26.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (instance.isDesktopMode || isWhatsapp) Icons.Filled.DesktopMac else Icons.Filled.PhoneAndroid,
                                    contentDescription = if (instance.isDesktopMode || isWhatsapp) "Desktop View Active" else "Mobile View Active",
                                    tint = if (instance.isDesktopMode || isWhatsapp) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }

                    if (onOpenInBrowser != null) {
                        IconButton(
                            onClick = { onOpenInBrowser(currentUrl.ifBlank { instance.targetUrl }) },
                            modifier = Modifier
                                .size(30.dp)
                                .testTag("workspace_open_in_browser_btn")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                contentDescription = "Open in Web Browser",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    IconButton(
                        onClick = {
                            hasRenderCrash = false
                            if (isWhatsapp) {
                                webViewInstance?.loadUrl("https://web.whatsapp.com/", WHATSAPP_WEB_HEADERS)
                            } else {
                                webViewInstance?.reload()
                            }
                        },
                        modifier = Modifier
                            .size(30.dp)
                            .testTag("workspace_reload_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = "Reload",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    IconButton(
                        onClick = onCloseWorkspace,
                        modifier = Modifier
                            .size(30.dp)
                            .testTag("workspace_close_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Close to Space",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }
            }
        }

        // Loading Progress Indicator
        if (isLoading && loadProgress < 100) {
            LinearProgressIndicator(
                progress = { loadProgress / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp),
                color = badgeColor,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        }

        // Isolated WebView Container with Automatic Renderer Recovery
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(if (isWhatsapp) Color(0xFF111B21) else Color.White)
        ) {
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
                            imageVector = Icons.Filled.Security,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(36.dp)
                        )
                        Text(
                            text = if (isWhatsapp) "WhatsApp Web Session Restored" else "Session Graphics Reset",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Tap below to continue in safe rendering mode.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    useSoftwareLayer = true
                                    hasRenderCrash = false
                                    reloadKey++
                                }
                            ) {
                                Icon(Icons.Filled.Refresh, null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Reload")
                            }

                            FilledTonalButton(
                                onClick = {
                                    try {
                                        val cookieManager = CookieManager.getInstance()
                                        cookieManager.removeAllCookies(null)
                                        cookieManager.flush()
                                        WebStorage.getInstance().deleteAllData()
                                    } catch (_: Exception) {}
                                    useSoftwareLayer = true
                                    hasRenderCrash = false
                                    reloadKey++
                                }
                            ) {
                                Text("Reset Cache")
                            }
                        }
                    }
                }
            } else {
                key(instance.instanceId, reloadKey) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            WebView(ctx).apply {
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                                // Use software layer if emulator GPU crashed, otherwise hardware layer for smooth Reels & scrolling
                                if (useSoftwareLayer || isWhatsapp) {
                                    setLayerType(View.LAYER_TYPE_SOFTWARE, null)
                                } else {
                                    setLayerType(View.LAYER_TYPE_HARDWARE, null)
                                }
                                isVerticalScrollBarEnabled = false
                                isHorizontalScrollBarEnabled = false
                                overScrollMode = View.OVER_SCROLL_NEVER
                                isNestedScrollingEnabled = true
                                setBackgroundColor(
                                    when {
                                        isWhatsapp -> android.graphics.Color.parseColor("#111B21")
                                        isInstagram -> android.graphics.Color.BLACK
                                        else -> android.graphics.Color.WHITE
                                    }
                                )

                                settings.apply {
                                    javaScriptEnabled = true
                                    domStorageEnabled = true
                                    databaseEnabled = true
                                    loadWithOverviewMode = true
                                    useWideViewPort = true
                                    // Disable pinch-zoom on Instagram so double-tap-to-like and fast vertical Reels swipes stay crisp
                                    builtInZoomControls = !isInstagram
                                    displayZoomControls = false
                                    safeBrowsingEnabled = false
                                    setSupportZoom(!isInstagram)
                                    mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                                    mediaPlaybackRequiresUserGesture = false
                                    cacheMode = WebSettings.LOAD_DEFAULT
                                    javaScriptCanOpenWindowsAutomatically = false
                                    setSupportMultipleWindows(false)
                                    allowFileAccess = true
                                    allowContentAccess = true
                                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                                        offscreenPreRaster = true
                                    }
                                    userAgentString = if (isWhatsapp || instance.isDesktopMode) {
                                        DESKTOP_USER_AGENT
                                    } else {
                                        MOBILE_USER_AGENT
                                    }
                                }

                                setDownloadListener { url, userAgent, contentDisposition, mimetype, _ ->
                                    try {
                                        val request = android.app.DownloadManager.Request(android.net.Uri.parse(url)).apply {
                                            setMimeType(mimetype)
                                            val cookies = CookieManager.getInstance().getCookie(url)
                                            addRequestHeader("cookie", cookies)
                                            addRequestHeader("User-Agent", userAgent)
                                            setDescription("Downloading file...")
                                            setTitle(android.webkit.URLUtil.guessFileName(url, contentDisposition, mimetype))
                                            setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                                            setDestinationInExternalPublicDir(
                                                android.os.Environment.DIRECTORY_DOWNLOADS,
                                                android.webkit.URLUtil.guessFileName(url, contentDisposition, mimetype)
                                            )
                                        }
                                        val dm = ctx.getSystemService(android.content.Context.DOWNLOAD_SERVICE) as android.app.DownloadManager
                                        dm.enqueue(request)
                                        android.widget.Toast.makeText(ctx, "Downloading file...", android.widget.Toast.LENGTH_SHORT).show()
                                    } catch (e: Exception) {
                                        android.widget.Toast.makeText(ctx, "Download failed: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                }

                                val cookieManager = CookieManager.getInstance()
                                cookieManager.setAcceptCookie(true)
                                cookieManager.setAcceptThirdPartyCookies(this, true)

                                webViewClient = object : WebViewClient() {
                                    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                        isLoading = true
                                        url?.let {
                                            currentUrl = it
                                            onUrlChanged(it)
                                        }
                                        canGoBack = view?.canGoBack() == true
                                    }

                                    override fun onPageCommitVisible(view: WebView?, url: String?) {
                                        super.onPageCommitVisible(view, url)
                                        if (isWhatsapp) {
                                            injectWhatsAppWebSupportEngine(view)
                                        } else if (isInstagram || url?.contains("instagram.com", ignoreCase = true) == true) {
                                            injectInstagramReelsAndScrollHelper(view)
                                        }
                                    }

                                    override fun onPageFinished(view: WebView?, url: String?) {
                                        isLoading = false
                                        if (isWhatsapp) {
                                            injectWhatsAppWebSupportEngine(view)
                                        } else if (isInstagram || url?.contains("instagram.com", ignoreCase = true) == true) {
                                            injectInstagramReelsAndScrollHelper(view)
                                        }
                                        url?.let {
                                            currentUrl = it
                                            onUrlChanged(it)
                                        }
                                        currentTitle = view?.title ?: instance.displayName
                                        canGoBack = view?.canGoBack() == true
                                    }

                                    override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                                        super.doUpdateVisitedHistory(view, url, isReload)
                                        if (isInstagram || url?.contains("instagram.com", ignoreCase = true) == true) {
                                            injectInstagramReelsAndScrollHelper(view)
                                        }
                                        url?.let {
                                            currentUrl = it
                                            onUrlChanged(it)
                                        }
                                        canGoBack = view?.canGoBack() == true
                                    }

                                    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                        val url = request?.url?.toString() ?: return false
                                        if (isWhatsapp) {
                                            if (url.contains("whatsapp.com/download") ||
                                                url.contains("whatsapp.com/android") ||
                                                (url.contains("www.whatsapp.com") && !url.contains("web.whatsapp.com"))
                                            ) {
                                                val curr = view?.url ?: ""
                                                if (!curr.startsWith("https://web.whatsapp.com")) {
                                                    view?.post {
                                                        view.loadUrl("https://web.whatsapp.com/", WHATSAPP_WEB_HEADERS)
                                                    }
                                                }
                                                return true
                                            }
                                        }
                                        if (url.startsWith("intent://")) {
                                            try {
                                                val parsed = Intent.parseUri(url, Intent.URI_INTENT_SCHEME)
                                                val fallbackUrl = parsed.getStringExtra("browser_fallback_url")
                                                if (!fallbackUrl.isNullOrBlank() && (fallbackUrl.startsWith("http://") || fallbackUrl.startsWith("https://"))) {
                                                    view?.loadUrl(fallbackUrl)
                                                }
                                            } catch (_: Exception) {}
                                            return true
                                        }
                                        val lowerUrl = url.lowercase()
                                        if (!lowerUrl.startsWith("http://") &&
                                            !lowerUrl.startsWith("https://") &&
                                            !lowerUrl.startsWith("file://") &&
                                            !lowerUrl.startsWith("about:") &&
                                            !lowerUrl.startsWith("javascript:") &&
                                            !lowerUrl.startsWith("blob:") &&
                                            !lowerUrl.startsWith("data:")
                                        ) {
                                            return true
                                        }
                                        return false
                                    }

                                    override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
                                        try {
                                            view?.let {
                                                val parent = it.parent as? ViewGroup
                                                parent?.removeView(it)
                                                it.stopLoading()
                                                it.destroy()
                                            }
                                        } catch (_: Exception) {}
                                        webViewInstance = null
                                        if (!useSoftwareLayer) {
                                            // Automatically recover in software layer mode without interrupting the user
                                            useSoftwareLayer = true
                                            reloadKey++
                                        } else {
                                            hasRenderCrash = true
                                        }
                                        return true
                                    }
                                }

                                webChromeClient = object : WebChromeClient() {
                                    override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                        loadProgress = newProgress
                                        if (newProgress >= 100) {
                                            isLoading = false
                                        }
                                    }

                                    override fun onReceivedTitle(view: WebView?, title: String?) {
                                        if (!title.isNullOrBlank()) {
                                            currentTitle = title
                                        }
                                    }

                                    override fun onPermissionRequest(request: PermissionRequest?) {
                                        try {
                                            request?.grant(request.resources)
                                        } catch (_: Exception) {}
                                    }

                                    override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                                        return true
                                    }
                                }

                                val startUrl = if (isWhatsapp) {
                                    "https://web.whatsapp.com/"
                                } else {
                                    instance.lastUrl
                                }
                                val extraHeaders = if (isWhatsapp) {
                                    WHATSAPP_WEB_HEADERS
                                } else emptyMap()

                                if (extraHeaders.isNotEmpty()) {
                                    loadUrl(startUrl, extraHeaders)
                                } else {
                                    loadUrl(startUrl)
                                }
                                webViewInstance = this
                            }
                        },
                        update = { wv ->
                            webViewInstance = wv
                            canGoBack = wv.canGoBack()
                        }
                    )
                }
            }

            if (isLoading && loadProgress < 25 && !hasRenderCrash) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(16.dp),
                    tonalElevation = 4.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(
                            color = badgeColor,
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.5.dp
                        )
                        Text(
                            text = if (isWhatsapp) "Loading WhatsApp Web..." else "Loading ${instance.displayName}...",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}
