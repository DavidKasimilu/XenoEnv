package com.example.ui.screens

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.view.View
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.PermissionRequest
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
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
import androidx.compose.material3.OutlinedButton
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
    "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"

private const val DESKTOP_USER_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"

private fun injectDesktopCompatibility(view: WebView?) {
    val js = """
        (function() {
            try {
                if (navigator.userAgentData) {
                    Object.defineProperty(navigator, 'userAgentData', {
                        get: function() {
                            return {
                                brands: [
                                    { brand: 'Chromium', version: '128' },
                                    { brand: 'Google Chrome', version: '128' },
                                    { brand: 'Not(A:Brand', version: '24' }
                                ],
                                mobile: false,
                                platform: 'Windows'
                            };
                        },
                        configurable: true
                    });
                }
            } catch(e) {}

            try {
                Object.defineProperty(navigator, 'platform', { get: function() { return 'Win32'; }, configurable: true });
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
    var reloadKey by remember(instance.instanceId) { mutableIntStateOf(0) }

    val badgeColor = remember(instance.badgeColorHex) { parseHexColor(instance.badgeColorHex) }

    val isWhatsapp = remember(instance) {
        instance.packageName.contains("whatsapp", ignoreCase = true) ||
                instance.originalName.contains("whatsapp", ignoreCase = true) ||
                instance.displayName.contains("whatsapp", ignoreCase = true) ||
                instance.targetUrl.contains("whatsapp.com", ignoreCase = true)
    }

    // Intercept back button to navigate backwards in web session or return to Space
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
        // Workspace Top Bar - Sleek, Compact & Space-Efficient (44dp Height)
        Surface(
            tonalElevation = 2.dp,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Back Button (Compact 32dp button)
                IconButton(
                    onClick = {
                        onCloseWorkspace()
                    },
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

                // App Icon (22dp)
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

                // Title & Badge Tag (Single Line, neatly sharing available width)
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

                // Action Icons (Desktop, Open In Browser, Reload, Close)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    // Desktop Mode Toggle
                    IconButton(
                        onClick = {
                            if (isWhatsapp) {
                                webViewInstance?.settings?.userAgentString = DESKTOP_USER_AGENT
                                val headers = mapOf(
                                    "Sec-CH-UA-Mobile" to "?0",
                                    "Sec-CH-UA-Platform" to "\"Windows\""
                                )
                                webViewInstance?.loadUrl("https://web.whatsapp.com/", headers)
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

                    // Open in Browser Tab (if callback available)
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

                    // Reload Button
                    IconButton(
                        onClick = {
                            hasRenderCrash = false
                            if (isWhatsapp) {
                                val headers = mapOf(
                                    "Sec-CH-UA-Mobile" to "?0",
                                    "Sec-CH-UA-Platform" to "\"Windows\""
                                )
                                webViewInstance?.loadUrl("https://web.whatsapp.com/", headers)
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

                    // Close to Space Button
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

        // Isolated WebView Container with Renderer Recovery
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White)
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
                            text = if (isWhatsapp) {
                                "The renderer refreshed to optimize memory. Tap below to reload WhatsApp Web with stable graphics."
                            } else {
                                "The isolated web renderer process reset. Tap reload to restore the session safely."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    hasRenderCrash = false
                                    reloadKey++
                                }
                            ) {
                                Icon(Icons.Filled.Refresh, null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Reload WhatsApp")
                            }

                            FilledTonalButton(
                                onClick = {
                                    try {
                                        val cookieManager = CookieManager.getInstance()
                                        cookieManager.removeAllCookies(null)
                                        cookieManager.flush()
                                        android.webkit.WebStorage.getInstance().deleteAllData()
                                    } catch (_: Exception) {}
                                    hasRenderCrash = false
                                    reloadKey++
                                }
                            ) {
                                Text("Reset Cache")
                            }

                            if (onOpenInBrowser != null) {
                                OutlinedButton(
                                    onClick = {
                                        onOpenInBrowser(if (isWhatsapp) "https://web.whatsapp.com/" else currentUrl)
                                    }
                                ) {
                                    Text("Open in Browser")
                                }
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
                                setLayerType(View.LAYER_TYPE_HARDWARE, null)
                                setBackgroundColor(android.graphics.Color.WHITE)

                                settings.apply {
                                    javaScriptEnabled = true
                                    domStorageEnabled = true
                                    databaseEnabled = true
                                    loadWithOverviewMode = true
                                    useWideViewPort = true
                                    builtInZoomControls = true
                                    displayZoomControls = false
                                    safeBrowsingEnabled = false
                                    setSupportZoom(true)
                                    mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                                    mediaPlaybackRequiresUserGesture = false
                                    cacheMode = WebSettings.LOAD_DEFAULT
                                    javaScriptCanOpenWindowsAutomatically = true
                                    setSupportMultipleWindows(false)
                                    allowFileAccess = true
                                    allowContentAccess = true
                                    userAgentString = if (isWhatsapp || instance.isDesktopMode) {
                                        DESKTOP_USER_AGENT
                                    } else {
                                        MOBILE_USER_AGENT
                                    }
                                }

                                setDownloadListener { url, userAgent, contentDisposition, mimetype, contentLength ->
                                    try {
                                        val request = android.app.DownloadManager.Request(android.net.Uri.parse(url)).apply {
                                            setMimeType(mimetype)
                                            val cookies = CookieManager.getInstance().getCookie(url)
                                            addRequestHeader("cookie", cookies)
                                            addRequestHeader("User-Agent", userAgent)
                                            setDescription("Downloading file...")
                                            setTitle(android.webkit.URLUtil.guessFileName(url, contentDisposition, mimetype))
                                            setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                                            setDestinationInExternalPublicDir(android.os.Environment.DIRECTORY_DOWNLOADS, android.webkit.URLUtil.guessFileName(url, contentDisposition, mimetype))
                                        }
                                        val dm = ctx.getSystemService(android.content.Context.DOWNLOAD_SERVICE) as android.app.DownloadManager
                                        dm.enqueue(request)
                                        android.widget.Toast.makeText(ctx, "Downloading file...", android.widget.Toast.LENGTH_SHORT).show()
                                    } catch (e: Exception) {
                                        android.widget.Toast.makeText(ctx, "Download failed: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                }

                                // Enable cookies
                                val cookieManager = CookieManager.getInstance()
                                cookieManager.setAcceptCookie(true)
                                cookieManager.setAcceptThirdPartyCookies(this, true)
                                cookieManager.flush()

                                webViewClient = object : WebViewClient() {
                                    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                        isLoading = true
                                        if (isWhatsapp) {
                                            injectDesktopCompatibility(view)
                                        }
                                        url?.let {
                                            currentUrl = it
                                            onUrlChanged(it)
                                        }
                                        canGoBack = view?.canGoBack() == true
                                    }

                                    override fun onPageFinished(view: WebView?, url: String?) {
                                        isLoading = false
                                        if (isWhatsapp) {
                                            injectDesktopCompatibility(view)
                                        }
                                        url?.let {
                                            currentUrl = it
                                            onUrlChanged(it)
                                        }
                                        currentTitle = view?.title ?: instance.displayName
                                        canGoBack = view?.canGoBack() == true
                                    }

                                    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                        val url = request?.url?.toString() ?: return false
                                        if (isWhatsapp) {
                                            if (url.contains("whatsapp.com/download") || (url.contains("www.whatsapp.com") && !url.contains("web.whatsapp.com"))) {
                                                val curr = view?.url ?: ""
                                                if (!curr.startsWith("https://web.whatsapp.com")) {
                                                    view?.post {
                                                        val headers = mapOf(
                                                            "Sec-CH-UA-Mobile" to "?0",
                                                            "Sec-CH-UA-Platform" to "\"Windows\""
                                                        )
                                                        view.loadUrl("https://web.whatsapp.com/", headers)
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
                                            // Strictly block external app launch; remain in sandbox workspace
                                            return true
                                        }
                                        return false
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
                                        webViewInstance = null
                                        hasRenderCrash = true
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
                                    mapOf(
                                        "Sec-CH-UA-Mobile" to "?0",
                                        "Sec-CH-UA-Platform" to "\"Windows\""
                                    )
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

    DisposableEffect(instance.instanceId) {
        onDispose {
            try {
                webViewInstance?.stopLoading()
                webViewInstance?.destroy()
            } catch (_: Exception) {}
            webViewInstance = null
        }
    }
}
