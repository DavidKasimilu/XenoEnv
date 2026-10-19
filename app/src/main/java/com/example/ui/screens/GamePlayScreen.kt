package com.example.ui.screens

import android.annotation.SuppressLint
import android.content.Intent
import android.view.MotionEvent
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.data.models.InstalledGameItem

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun GamePlayScreen(
    gameTitle: String,
    gameUrl: String,
    gameItem: InstalledGameItem? = null,
    onBack: () -> Unit,
    onUpdateGameUrl: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var isDesktopMode by remember(gameItem) { mutableStateOf(gameItem?.envProfile?.forceLandscape ?: false) }
    var isImmersive by remember { mutableStateOf(false) }
    var showVirtualGamepad by remember(gameItem) {
        mutableStateOf(gameItem?.envProfile?.virtualGamepadEnabled ?: true)
    }
    var loadProgress by remember { mutableIntStateOf(0) }
    var currentUrl by remember(gameUrl) { mutableStateOf(gameUrl) }
    var showEditUrlDialog by remember { mutableStateOf(false) }
    var urlInput by remember(currentUrl) { mutableStateOf(currentUrl) }

    val deviceModel = gameItem?.envProfile?.deviceModel ?: "Pixel 8 Pro"
    val gpuName = gameItem?.envProfile?.gpuRenderer ?: "Adreno (TM) 750"
    val ramMb = gameItem?.envProfile?.allocatedRamMb ?: 4096

    val mobileUa = "Mozilla/5.0 (Linux; Android 14; $deviceModel) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"
    val desktopUa = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"

    fun sendGamepadInput(action: String, keyName: String, keyCode: Int, pressed: Boolean) {
        val wv = webViewInstance ?: return
        val eventType = if (pressed) "keydown" else "keyup"
        val js = """
            (function() {
                if (typeof window.dispatchVirtualInput === 'function') {
                    window.dispatchVirtualInput('$action', $pressed);
                }
                var ev = new KeyboardEvent('$eventType', {
                    key: '$keyName',
                    code: '$keyName',
                    keyCode: $keyCode,
                    which: $keyCode,
                    bubbles: true,
                    cancelable: true
                });
                window.dispatchEvent(ev);
                if (document.activeElement) {
                    document.activeElement.dispatchEvent(ev);
                } else {
                    document.body.dispatchEvent(ev);
                }
            })();
        """.trimIndent()
        wv.evaluateJavascript(js, null)
    }

    BackHandler {
        val wv = webViewInstance
        if (wv != null && wv.canGoBack()) {
            wv.goBack()
        } else {
            onBack()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF070A12))
            .testTag("game_play_screen")
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            AnimatedVisibility(visible = !isImmersive) {
                Column {
                    Surface(
                        tonalElevation = 3.dp,
                        color = MaterialTheme.colorScheme.surface
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(58.dp)
                                .padding(horizontal = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                IconButton(
                                    onClick = onBack,
                                    modifier = Modifier.testTag("game_play_back_btn")
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Exit to Game Space"
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = gameTitle,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${deviceModel.substringBefore(" (")} • ${gpuName.substringBefore(" ")} • ${ramMb}MB VM",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontSize = 10.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = { showVirtualGamepad = !showVirtualGamepad },
                                    modifier = Modifier.testTag("toggle_virtual_gamepad_btn")
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Gamepad,
                                        contentDescription = "Toggle Virtual Gamepad Overlay",
                                        tint = if (showVirtualGamepad) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        urlInput = currentUrl
                                        showEditUrlDialog = true
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Edit,
                                        contentDescription = "Configure Runtime URL",
                                        modifier = Modifier.size(19.dp)
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        isDesktopMode = !isDesktopMode
                                        webViewInstance?.settings?.apply {
                                            userAgentString = if (isDesktopMode) desktopUa else mobileUa
                                            useWideViewPort = true
                                            loadWithOverviewMode = true
                                        }
                                        webViewInstance?.reload()
                                    }
                                ) {
                                    Icon(
                                        imageVector = if (isDesktopMode) Icons.Filled.Computer else Icons.Filled.PhoneAndroid,
                                        contentDescription = "Toggle Viewport Mode",
                                        modifier = Modifier.size(19.dp)
                                    )
                                }
                                IconButton(onClick = { isImmersive = true }) {
                                    Icon(
                                        imageVector = Icons.Filled.Fullscreen,
                                        contentDescription = "Fullscreen Mode",
                                        modifier = Modifier.size(21.dp)
                                    )
                                }
                                IconButton(onClick = { webViewInstance?.reload() }) {
                                    Icon(
                                        imageVector = Icons.Filled.Refresh,
                                        contentDescription = "Restart Virtual Instance",
                                        modifier = Modifier.size(19.dp)
                                    )
                                }
                            }
                        }
                    }

                    if (loadProgress in 1..99) {
                        LinearProgressIndicator(
                            progress = { loadProgress / 100f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(2.dp)
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            CookieManager.getInstance().setAcceptCookie(true)
                            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                            settings.apply {
                                javaScriptEnabled = true
                                domStorageEnabled = true
                                databaseEnabled = true
                                allowFileAccess = true
                                allowContentAccess = true
                                @Suppress("DEPRECATION")
                                allowFileAccessFromFileURLs = true
                                @Suppress("DEPRECATION")
                                allowUniversalAccessFromFileURLs = true
                                mediaPlaybackRequiresUserGesture = false
                                loadWithOverviewMode = true
                                useWideViewPort = true
                                setSupportZoom(true)
                                builtInZoomControls = true
                                displayZoomControls = false
                                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                                cacheMode = WebSettings.LOAD_DEFAULT
                                userAgentString = if (isDesktopMode) desktopUa else mobileUa
                            }
                            webViewClient = object : WebViewClient() {
                                override fun onPageFinished(view: WebView?, url: String?) {
                                    super.onPageFinished(view, url)
                                    // Inject Android Virtual Environment hardware & package properties
                                    val injectJs = """
                                        window.AndroidVirtualEnv = {
                                            packageName: '${gameItem?.packageName ?: "com.virtual.apk"}',
                                            deviceModel: '$deviceModel',
                                            gpuRenderer: '$gpuName',
                                            allocatedRamMb: $ramMb,
                                            sdkInt: ${gameItem?.targetSdk ?: 34}
                                        };
                                    """.trimIndent()
                                    view?.evaluateJavascript(injectJs, null)
                                }

                                override fun shouldOverrideUrlLoading(
                                    view: WebView?,
                                    request: WebResourceRequest?
                                ): Boolean {
                                    val url = request?.url?.toString() ?: return false
                                    if (url.startsWith("intent://")) {
                                        try {
                                            val parsed = Intent.parseUri(url, Intent.URI_INTENT_SCHEME)
                                            val fallbackUrl = parsed.getStringExtra("browser_fallback_url")
                                            if (!fallbackUrl.isNullOrBlank() &&
                                                (fallbackUrl.startsWith("http://") || fallbackUrl.startsWith("https://"))
                                            ) {
                                                view?.loadUrl(fallbackUrl)
                                            }
                                        } catch (_: Exception) {}
                                        return true
                                    }
                                    val lower = url.lowercase()
                                    if (!lower.startsWith("http://") &&
                                        !lower.startsWith("https://") &&
                                        !lower.startsWith("file://") &&
                                        !lower.startsWith("about:") &&
                                        !lower.startsWith("javascript:") &&
                                        !lower.startsWith("blob:") &&
                                        !lower.startsWith("data:")
                                    ) {
                                        // Strictly block external app launches; stay inside our virtual runtime
                                        return true
                                    }
                                    currentUrl = url
                                    return false
                                }
                            }
                            webChromeClient = object : WebChromeClient() {
                                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                    loadProgress = newProgress
                                }
                            }
                            loadUrl(gameUrl)
                            webViewInstance = this
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // On-Screen Emulated Gamepad Overlay (D-Pad + A/B/X/Y Buttons)
                if (showVirtualGamepad) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 18.dp, vertical = 20.dp)
                    ) {
                        // Left Side: 4-Way D-Pad
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .size(136.dp)
                        ) {
                            GamepadDPadButton(
                                icon = Icons.Filled.KeyboardArrowUp,
                                label = "UP",
                                modifier = Modifier.align(Alignment.TopCenter),
                                onPressChange = { pressed ->
                                    sendGamepadInput("up", "ArrowUp", 38, pressed)
                                }
                            )
                            GamepadDPadButton(
                                icon = Icons.Filled.KeyboardArrowDown,
                                label = "DOWN",
                                modifier = Modifier.align(Alignment.BottomCenter),
                                onPressChange = { pressed ->
                                    sendGamepadInput("down", "ArrowDown", 40, pressed)
                                }
                            )
                            GamepadDPadButton(
                                icon = Icons.Filled.KeyboardArrowLeft,
                                label = "LEFT",
                                modifier = Modifier.align(Alignment.CenterStart),
                                onPressChange = { pressed ->
                                    sendGamepadInput("left", "ArrowLeft", 37, pressed)
                                }
                            )
                            GamepadDPadButton(
                                icon = Icons.Filled.KeyboardArrowRight,
                                label = "RIGHT",
                                modifier = Modifier.align(Alignment.CenterEnd),
                                onPressChange = { pressed ->
                                    sendGamepadInput("right", "ArrowRight", 39, pressed)
                                }
                            )
                        }

                        // Right Side: A / B / X / Y Action Diamond
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .size(136.dp)
                        ) {
                            GamepadActionButton(
                                text = "Y",
                                accentColor = Color(0xFFF59E0B),
                                modifier = Modifier.align(Alignment.TopCenter),
                                onPressChange = { pressed ->
                                    sendGamepadInput("y", "KeyW", 87, pressed)
                                }
                            )
                            GamepadActionButton(
                                text = "A",
                                accentColor = Color(0xFF38BDF8),
                                modifier = Modifier.align(Alignment.BottomCenter),
                                onPressChange = { pressed ->
                                    sendGamepadInput("a", " ", 32, pressed)
                                }
                            )
                            GamepadActionButton(
                                text = "X",
                                accentColor = Color(0xFFA855F7),
                                modifier = Modifier.align(Alignment.CenterStart),
                                onPressChange = { pressed ->
                                    sendGamepadInput("x", "Enter", 13, pressed)
                                }
                            )
                            GamepadActionButton(
                                text = "B",
                                accentColor = Color(0xFF22C55E),
                                modifier = Modifier.align(Alignment.CenterEnd),
                                onPressChange = { pressed ->
                                    sendGamepadInput("b", "Shift", 16, pressed)
                                }
                            )
                        }
                    }
                }
            }
        }

        // Floating Exit Fullscreen Pill when in Immersive Mode
        if (isImmersive) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                tonalElevation = 6.dp,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 4.dp)
                ) {
                    IconButton(
                        onClick = { showVirtualGamepad = !showVirtualGamepad },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Gamepad,
                            contentDescription = "Toggle Gamepad",
                            tint = if (showVirtualGamepad) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(
                        onClick = { isImmersive = false },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.FullscreenExit,
                            contentDescription = "Show Top Bar",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Exit Game",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }

    if (showEditUrlDialog) {
        AlertDialog(
            onDismissRequest = { showEditUrlDialog = false },
            title = { Text("Virtual Runtime Endpoint") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Configure the in-app virtual runtime or cloud mirror URL for $gameTitle:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = urlInput,
                        onValueChange = { urlInput = it },
                        singleLine = true,
                        label = { Text("Runtime Entry URL") },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val formatted = if (
                            urlInput.startsWith("http://") ||
                            urlInput.startsWith("https://") ||
                            urlInput.startsWith("file://")
                        ) {
                            urlInput.trim()
                        } else {
                            "https://${urlInput.trim()}"
                        }
                        currentUrl = formatted
                        webViewInstance?.loadUrl(formatted)
                        onUpdateGameUrl?.invoke(formatted)
                        showEditUrlDialog = false
                    },
                    enabled = urlInput.isNotBlank()
                ) {
                    Text("Apply & Reload")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditUrlDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun GamepadDPadButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    onPressChange: (Boolean) -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (isPressed) Color(0xFF38BDF8).copy(alpha = 0.55f)
                else Color(0xFF0F172A).copy(alpha = 0.68f)
            )
            .border(
                width = 1.dp,
                color = if (isPressed) Color(0xFF38BDF8) else Color.White.copy(alpha = 0.22f),
                shape = RoundedCornerShape(12.dp)
            )
            .pointerInteropFilter { event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        isPressed = true
                        onPressChange(true)
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        isPressed = false
                        onPressChange(false)
                        true
                    }
                    else -> false
                }
            }
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = Color.White,
            modifier = Modifier.size(26.dp)
        )
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun GamepadActionButton(
    text: String,
    accentColor: Color,
    modifier: Modifier = Modifier,
    onPressChange: (Boolean) -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(
                if (isPressed) accentColor.copy(alpha = 0.65f)
                else Color(0xFF0F172A).copy(alpha = 0.68f)
            )
            .border(
                width = 1.5.dp,
                color = if (isPressed) accentColor else accentColor.copy(alpha = 0.55f),
                shape = CircleShape
            )
            .pointerInteropFilter { event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        isPressed = true
                        onPressChange(true)
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        isPressed = false
                        onPressChange(false)
                        true
                    }
                    else -> false
                }
            }
    ) {
        Text(
            text = text,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp
        )
    }
}
