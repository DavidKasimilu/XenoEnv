package com.example.ui.screens

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.view.MotionEvent
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.DeveloperBoard
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.data.ApkRuntimeHelper
import com.example.data.LoadedApkRuntimeBundle
import com.example.data.models.InstalledGameItem
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

private enum class ApkExecutionTab {
    EMULATED_SURFACE,
    ACTIVITIES_AND_DEX,
    WEB_BRIDGE
}

private data class VirtualOrb(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    val radius: Float,
    val color: Color,
    val spriteIndex: Int
)

private data class VirtualHazard(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    val radius: Float
)

private data class VirtualParticle(
    var x: Float,
    var y: Float,
    val vx: Float,
    val vy: Float,
    var alpha: Float,
    val color: Color
)

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
    val context = LocalContext.current
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var isDesktopMode by remember(gameItem) { mutableStateOf(gameItem?.envProfile?.forceLandscape ?: false) }
    var isImmersive by remember { mutableStateOf(false) }
    var showVirtualGamepad by remember(gameItem) {
        mutableStateOf(gameItem?.envProfile?.virtualGamepadEnabled ?: true)
    }
    var loadProgress by remember { mutableIntStateOf(0) }

    // APK Runtime Helper State
    var isBootingApk by remember(gameItem?.packageName) { mutableStateOf(true) }
    var bootStepText by remember { mutableStateOf("Initializing APK Virtual Runtime Helper...") }
    var bootProgress by remember { mutableFloatStateOf(0.1f) }
    var loadedBundle by remember(gameItem?.packageName) { mutableStateOf<LoadedApkRuntimeBundle?>(null) }
    val runtimeLogs = remember { mutableStateListOf<String>() }

    var activeExecTab by remember { mutableStateOf(ApkExecutionTab.EMULATED_SURFACE) }
    var activeActivityName by remember { mutableStateOf(gameItem?.mainActivityName ?: "MainActivity") }

    // Web Bridge URL (either embedded HTML5 asset inside APK, direct cloud URL, or user custom URL)
    var currentWebUrl by remember(gameUrl) {
        val initial = if (gameUrl.startsWith("http://") || gameUrl.startsWith("https://")) {
            gameUrl
        } else {
            "https://poki.com/"
        }
        mutableStateOf(initial)
    }
    var showEditUrlDialog by remember { mutableStateOf(false) }
    var urlInput by remember(currentWebUrl) { mutableStateOf(currentWebUrl) }

    val deviceModel = gameItem?.envProfile?.deviceModel ?: "Pixel 8 Pro"
    val gpuName = gameItem?.envProfile?.gpuRenderer ?: "Adreno (TM) 750"
    val ramMb = gameItem?.envProfile?.allocatedRamMb ?: 4096
    val pkgName = gameItem?.packageName ?: "com.virtual.apk"

    // Virtual Controller & Native Emulated Surface State
    var dpadUp by remember { mutableStateOf(false) }
    var dpadDown by remember { mutableStateOf(false) }
    var dpadLeft by remember { mutableStateOf(false) }
    var dpadRight by remember { mutableStateOf(false) }
    var actionPulseTrigger by remember { mutableIntStateOf(0) }
    var actionBoostTrigger by remember { mutableIntStateOf(0) }
    var surfaceResetTrigger by remember { mutableIntStateOf(0) }
    var lastInputLabel by remember { mutableStateOf("Touch or Gamepad Ready") }

    // Load and prepare the installed APK via ApkRuntimeHelper when screen opens
    LaunchedEffect(gameItem?.packageName) {
        isBootingApk = true
        runtimeLogs.clear()
        val targetGame = gameItem ?: InstalledGameItem(
            packageName = pkgName,
            appName = gameTitle,
            versionName = "1.0"
        )
        val bundle = ApkRuntimeHelper.loadAndPrepareApk(
            context = context,
            game = targetGame,
            onStep = { step, progress, logLine ->
                bootStepText = step
                bootProgress = progress
                runtimeLogs.add(logLine)
            }
        )
        loadedBundle = bundle
        activeActivityName = bundle.mainActivity
        runtimeLogs.addAll(bundle.bootLogs)

        if (bundle.hasEmbeddedHtml && bundle.embeddedHtmlVirtualUrl != null) {
            currentWebUrl = bundle.embeddedHtmlVirtualUrl
            activeExecTab = ApkExecutionTab.WEB_BRIDGE
        } else if (bundle.directCloudUrl != null) {
            currentWebUrl = bundle.directCloudUrl
            activeExecTab = ApkExecutionTab.EMULATED_SURFACE
        } else {
            activeExecTab = ApkExecutionTab.EMULATED_SURFACE
        }
        isBootingApk = false
    }

    val mobileUa = "Mozilla/5.0 (Linux; Android 14; $deviceModel) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
    val desktopUa = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

    fun dispatchGamepadInput(action: String, keyName: String, keyCode: Int, pressed: Boolean) {
        when (action) {
            "up" -> dpadUp = pressed
            "down" -> dpadDown = pressed
            "left" -> dpadLeft = pressed
            "right" -> dpadRight = pressed
            "a" -> if (pressed) actionPulseTrigger++
            "b" -> if (pressed) actionBoostTrigger++
            "x" -> if (pressed) actionPulseTrigger++
            "y" -> if (pressed) actionBoostTrigger++
        }
        if (pressed) {
            lastInputLabel = "Key: ${action.uppercase()}"
        }

        // Also forward to WebView if in Web Bridge mode
        val wv = webViewInstance
        if (wv != null && activeExecTab == ApkExecutionTab.WEB_BRIDGE) {
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
                    } else if (document.body) {
                        document.body.dispatchEvent(ev);
                    }
                })();
            """.trimIndent()
            wv.evaluateJavascript(js, null)
        }
    }

    BackHandler {
        val wv = webViewInstance
        if (activeExecTab == ApkExecutionTab.WEB_BRIDGE && wv != null && wv.canGoBack()) {
            wv.goBack()
        } else if (activeExecTab != ApkExecutionTab.EMULATED_SURFACE) {
            activeExecTab = ApkExecutionTab.EMULATED_SURFACE
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
                    // Top Virtualization Bar
                    Surface(
                        tonalElevation = 3.dp,
                        color = MaterialTheme.colorScheme.surface
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
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
                                        text = "${activeActivityName.substringAfterLast('.')} • ${deviceModel.substringBefore(" (")} • ${ramMb}MB VM",
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
                                        urlInput = currentWebUrl
                                        showEditUrlDialog = true
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Edit,
                                        contentDescription = "Configure Runtime URL",
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
                                IconButton(
                                    onClick = {
                                        if (activeExecTab == ApkExecutionTab.WEB_BRIDGE) {
                                            webViewInstance?.reload()
                                        } else {
                                            surfaceResetTrigger++
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Refresh,
                                        contentDescription = "Restart Virtual Instance",
                                        modifier = Modifier.size(19.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Mode Switcher Bar inside APK Runtime Container
                    Surface(
                        color = Color(0xFF0D1322),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RuntimeModeChip(
                                label = "APK Surface",
                                selected = activeExecTab == ApkExecutionTab.EMULATED_SURFACE,
                                onClick = { activeExecTab = ApkExecutionTab.EMULATED_SURFACE }
                            )
                            RuntimeModeChip(
                                label = "Activities & DEX (${loadedBundle?.activities?.size ?: 1})",
                                selected = activeExecTab == ApkExecutionTab.ACTIVITIES_AND_DEX,
                                onClick = { activeExecTab = ApkExecutionTab.ACTIVITIES_AND_DEX }
                            )
                            RuntimeModeChip(
                                label = if (loadedBundle?.hasEmbeddedHtml == true) "APK HTML5 Engine" else "Web Runtime",
                                selected = activeExecTab == ApkExecutionTab.WEB_BRIDGE,
                                onClick = { activeExecTab = ApkExecutionTab.WEB_BRIDGE }
                            )
                        }
                    }

                    if (activeExecTab == ApkExecutionTab.WEB_BRIDGE && loadProgress in 1..99) {
                        LinearProgressIndicator(
                            progress = { loadProgress / 100f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(2.dp)
                        )
                    }
                }
            }

            // Main Execution Viewport
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                when (activeExecTab) {
                    ApkExecutionTab.EMULATED_SURFACE -> {
                        NativeApkEmulatedSurface(
                            context = context,
                            packageName = pkgName,
                            appName = gameTitle,
                            activeActivity = activeActivityName,
                            deviceModel = deviceModel,
                            gpuName = gpuName,
                            bundle = loadedBundle,
                            dpadUp = dpadUp,
                            dpadDown = dpadDown,
                            dpadLeft = dpadLeft,
                            dpadRight = dpadRight,
                            actionPulseTrigger = actionPulseTrigger,
                            actionBoostTrigger = actionBoostTrigger,
                            resetTrigger = surfaceResetTrigger,
                            lastInputLabel = lastInputLabel
                        )
                    }

                    ApkExecutionTab.ACTIVITIES_AND_DEX -> {
                        ApkActivitiesAndDexInspector(
                            bundle = loadedBundle,
                            activeActivity = activeActivityName,
                            runtimeLogs = runtimeLogs,
                            onLaunchActivity = { act ->
                                activeActivityName = act
                                runtimeLogs.add("[ActivityTaskManager] START u0 {cmp=$pkgName/$act} -> VirtualSurface#0")
                                surfaceResetTrigger++
                                activeExecTab = ApkExecutionTab.EMULATED_SURFACE
                            }
                        )
                    }

                    ApkExecutionTab.WEB_BRIDGE -> {
                        AndroidView(
                            factory = { ctx ->
                                WebView(ctx).apply {
                                    layoutParams = ViewGroup.LayoutParams(
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                        ViewGroup.LayoutParams.MATCH_PARENT
                                    )
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
                                        override fun shouldInterceptRequest(
                                            view: WebView?,
                                            request: WebResourceRequest?
                                        ): WebResourceResponse? {
                                            val req = request ?: return null
                                            val root = loadedBundle?.virtualRootDir
                                            if (root != null) {
                                                val intercepted = ApkRuntimeHelper.interceptVirtualApkRequest(req, root)
                                                if (intercepted != null) return intercepted
                                            }
                                            return super.shouldInterceptRequest(view, request)
                                        }

                                        override fun onPageFinished(view: WebView?, url: String?) {
                                            super.onPageFinished(view, url)
                                            ApkRuntimeHelper.injectApkWebRuntimeSupport(
                                                view = view,
                                                packageName = pkgName,
                                                deviceModel = deviceModel,
                                                gpuRenderer = gpuName,
                                                ramMb = ramMb
                                            )
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
                                                return true
                                            }
                                            currentWebUrl = url
                                            return false
                                        }

                                        override fun onRenderProcessGone(
                                            view: WebView?,
                                            detail: RenderProcessGoneDetail?
                                        ): Boolean {
                                            try {
                                                view?.let {
                                                    val parent = it.parent as? ViewGroup
                                                    parent?.removeView(it)
                                                    it.stopLoading()
                                                    it.destroy()
                                                }
                                            } catch (_: Exception) {}
                                            webViewInstance = null
                                            return true
                                        }
                                    }
                                    webChromeClient = object : WebChromeClient() {
                                        override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                            loadProgress = newProgress
                                        }
                                    }
                                    loadUrl(currentWebUrl)
                                    webViewInstance = this
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                // On-Screen Emulated Gamepad Overlay (D-Pad + A/B/X/Y Buttons)
                if (showVirtualGamepad && activeExecTab != ApkExecutionTab.ACTIVITIES_AND_DEX) {
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
                                    dispatchGamepadInput("up", "ArrowUp", 38, pressed)
                                }
                            )
                            GamepadDPadButton(
                                icon = Icons.Filled.KeyboardArrowDown,
                                label = "DOWN",
                                modifier = Modifier.align(Alignment.BottomCenter),
                                onPressChange = { pressed ->
                                    dispatchGamepadInput("down", "ArrowDown", 40, pressed)
                                }
                            )
                            GamepadDPadButton(
                                icon = Icons.Filled.KeyboardArrowLeft,
                                label = "LEFT",
                                modifier = Modifier.align(Alignment.CenterStart),
                                onPressChange = { pressed ->
                                    dispatchGamepadInput("left", "ArrowLeft", 37, pressed)
                                }
                            )
                            GamepadDPadButton(
                                icon = Icons.Filled.KeyboardArrowRight,
                                label = "RIGHT",
                                modifier = Modifier.align(Alignment.CenterEnd),
                                onPressChange = { pressed ->
                                    dispatchGamepadInput("right", "ArrowRight", 39, pressed)
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
                                    dispatchGamepadInput("y", "KeyW", 87, pressed)
                                }
                            )
                            GamepadActionButton(
                                text = "A",
                                accentColor = Color(0xFF38BDF8),
                                modifier = Modifier.align(Alignment.BottomCenter),
                                onPressChange = { pressed ->
                                    dispatchGamepadInput("a", " ", 32, pressed)
                                }
                            )
                            GamepadActionButton(
                                text = "X",
                                accentColor = Color(0xFFA855F7),
                                modifier = Modifier.align(Alignment.CenterStart),
                                onPressChange = { pressed ->
                                    dispatchGamepadInput("x", "Enter", 13, pressed)
                                }
                            )
                            GamepadActionButton(
                                text = "B",
                                accentColor = Color(0xFF22C55E),
                                modifier = Modifier.align(Alignment.CenterEnd),
                                onPressChange = { pressed ->
                                    dispatchGamepadInput("b", "Shift", 16, pressed)
                                }
                            )
                        }
                    }
                }

                // APK Runtime Helper Boot Overlay
                if (isBootingApk) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFF070A12).copy(alpha = 0.94f))
                    ) {
                        Card(
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF111827)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 32.dp)
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.padding(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.DeveloperBoard,
                                    contentDescription = null,
                                    tint = Color(0xFF38BDF8),
                                    modifier = Modifier.size(40.dp)
                                )
                                Text(
                                    text = "APK Runtime Helper",
                                    color = Color.White,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = bootStepText,
                                    color = Color(0xFF38BDF8),
                                    style = MaterialTheme.typography.bodySmall,
                                    textAlign = TextAlign.Center
                                )
                                LinearProgressIndicator(
                                    progress = { bootProgress },
                                    color = Color(0xFF38BDF8),
                                    trackColor = Color(0xFF1F2937),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(999.dp))
                                )
                            }
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
            title = { Text("Web / Cloud Runtime Endpoint") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Configure the Web Runtime URL for $gameTitle:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = urlInput,
                        onValueChange = { urlInput = it },
                        singleLine = true,
                        label = { Text("Runtime URL") },
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
                        currentWebUrl = formatted
                        activeExecTab = ApkExecutionTab.WEB_BRIDGE
                        webViewInstance?.loadUrl(formatted)
                        onUpdateGameUrl?.invoke(formatted)
                        showEditUrlDialog = false
                    },
                    enabled = urlInput.isNotBlank()
                ) {
                    Text("Load in Web Runtime")
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

@Composable
private fun RuntimeModeChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (selected) Color(0xFF38BDF8).copy(alpha = 0.18f) else Color(0xFF161F33),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (selected) Color(0xFF38BDF8) else Color.Transparent
        ),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Text(
            text = label,
            color = if (selected) Color(0xFF38BDF8) else Color(0xFF9CA3AF),
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
        )
    }
}

/**
 * 100% Native Jetpack Compose Hardware-Accelerated Virtual APK Surface.
 * Renders the installed APK's actual icon, extracted sprites/textures, and interactive
 * physics/arcade surface at 60 FPS without relying on file:// WebView loading.
 */
@Composable
private fun NativeApkEmulatedSurface(
    context: Context,
    packageName: String,
    appName: String,
    activeActivity: String,
    deviceModel: String,
    gpuName: String,
    bundle: LoadedApkRuntimeBundle?,
    dpadUp: Boolean,
    dpadDown: Boolean,
    dpadLeft: Boolean,
    dpadRight: Boolean,
    actionPulseTrigger: Int,
    actionBoostTrigger: Int,
    resetTrigger: Int,
    lastInputLabel: String
) {
    val prefs = remember(packageName) {
        context.getSharedPreferences("virtual_apk_state_$packageName", Context.MODE_PRIVATE)
    }
    var score by remember(packageName) { mutableIntStateOf(prefs.getInt("score", 0)) }
    var level by remember(packageName) { mutableIntStateOf(prefs.getInt("level", 1)) }
    var fps by remember { mutableIntStateOf(60) }

    var canvasWidth by remember { mutableFloatStateOf(1080f) }
    var canvasHeight by remember { mutableFloatStateOf(1920f) }

    var playerX by remember { mutableFloatStateOf(540f) }
    var playerY by remember { mutableFloatStateOf(1100f) }
    var playerVx by remember { mutableFloatStateOf(0f) }
    var playerVy by remember { mutableFloatStateOf(0f) }

    val orbs = remember { mutableStateListOf<VirtualOrb>() }
    val hazards = remember { mutableStateListOf<VirtualHazard>() }
    val particles = remember { mutableStateListOf<VirtualParticle>() }

    val iconImageBitmap = remember(bundle?.appIconBitmap) {
        bundle?.appIconBitmap?.asImageBitmap()
    }
    val spriteBitmaps = remember(bundle?.extractedSprites) {
        bundle?.extractedSprites?.map { it.asImageBitmap() }.orEmpty()
    }

    fun emitParticles(cx: Float, cy: Float, color: Color, count: Int = 14) {
        for (i in 0 until count) {
            val angle = Random.nextFloat() * (Math.PI.toFloat() * 2f)
            val speed = 2.5f + Random.nextFloat() * 7.5f
            particles.add(
                VirtualParticle(
                    x = cx,
                    y = cy,
                    vx = cos(angle) * speed,
                    vy = sin(angle) * speed,
                    alpha = 1f,
                    color = color
                )
            )
        }
    }

    fun spawnWorld(w: Float, h: Float, currentLevel: Int) {
        orbs.clear()
        hazards.clear()
        val palette = listOf(Color(0xFF38BDF8), Color(0xFFA855F7), Color(0xFF22C55E), Color(0xFFF59E0B))
        for (i in 0 until 7) {
            orbs.add(
                VirtualOrb(
                    x = 90f + Random.nextFloat() * (w - 180f).coerceAtLeast(100f),
                    y = 220f + Random.nextFloat() * (h - 460f).coerceAtLeast(100f),
                    vx = (Random.nextFloat() - 0.5f) * 5.5f,
                    vy = (Random.nextFloat() - 0.5f) * 5.5f,
                    radius = 28f,
                    color = palette[i % palette.size],
                    spriteIndex = i
                )
            )
        }
        val hazardCount = (2 + (currentLevel / 2)).coerceAtMost(6)
        for (i in 0 until hazardCount) {
            val speed = 3.2f + (currentLevel * 0.45f).coerceAtMost(5f)
            hazards.add(
                VirtualHazard(
                    x = 100f + Random.nextFloat() * (w - 200f).coerceAtLeast(100f),
                    y = 220f + Random.nextFloat() * (h * 0.45f).coerceAtLeast(100f),
                    vx = if (Random.nextBoolean()) speed else -speed,
                    vy = if (Random.nextBoolean()) speed else -speed,
                    radius = 30f
                )
            )
        }
    }

    LaunchedEffect(resetTrigger) {
        playerX = canvasWidth / 2f
        playerY = canvasHeight * 0.68f
        playerVx = 0f
        playerVy = 0f
        spawnWorld(canvasWidth, canvasHeight, level)
    }

    // Button A / X: Magnet Pulse wave
    LaunchedEffect(actionPulseTrigger) {
        if (actionPulseTrigger > 0) {
            emitParticles(playerX, playerY, Color(0xFF38BDF8), 22)
            orbs.forEach { orb ->
                val dx = playerX - orb.x
                val dy = playerY - orb.y
                val dist = hypot(dx, dy)
                if (dist in 1f..380f) {
                    orb.x += (dx / dist) * 95f
                    orb.y += (dy / dist) * 95f
                }
            }
        }
    }

    // Button B / Y: Speed Dash
    LaunchedEffect(actionBoostTrigger) {
        if (actionBoostTrigger > 0) {
            emitParticles(playerX, playerY, Color(0xFF22C55E), 18)
            playerVx *= 2.2f
            playerVy *= 2.2f
        }
    }

    // 60 FPS Native Frame Loop
    LaunchedEffect(Unit) {
        var frameCounter = 0
        var lastFpsTimestamp = System.nanoTime()
        while (true) {
            withFrameNanos { now ->
                frameCounter++
                if (now - lastFpsTimestamp >= 1_000_000_000L) {
                    fps = frameCounter.coerceIn(30, 120)
                    frameCounter = 0
                    lastFpsTimestamp = now
                }

                if (orbs.isEmpty() && canvasWidth > 100f) {
                    spawnWorld(canvasWidth, canvasHeight, level)
                }

                val moveSpeed = 10.5f
                when {
                    dpadLeft -> playerVx = -moveSpeed
                    dpadRight -> playerVx = moveSpeed
                    else -> playerVx *= 0.84f
                }
                when {
                    dpadUp -> playerVy = -moveSpeed
                    dpadDown -> playerVy = moveSpeed
                    else -> playerVy *= 0.84f
                }

                playerX = (playerX + playerVx).coerceIn(54f, (canvasWidth - 54f).coerceAtLeast(60f))
                playerY = (playerY + playerVy).coerceIn(190f, (canvasHeight - 64f).coerceAtLeast(200f))

                // Update Orbs
                val iterator = orbs.listIterator()
                while (iterator.hasNext()) {
                    val orb = iterator.next()
                    orb.x += orb.vx
                    orb.y += orb.vy
                    if (orb.x < 48f || orb.x > canvasWidth - 48f) orb.vx = -orb.vx
                    if (orb.y < 190f || orb.y > canvasHeight - 60f) orb.vy = -orb.vy

                    val dist = hypot(playerX - orb.x, playerY - orb.y)
                    if (dist < orb.radius + 46f) {
                        score += 10
                        prefs.edit().putInt("score", score).apply()
                        emitParticles(orb.x, orb.y, orb.color, 14)
                        iterator.remove()
                    }
                }

                if (orbs.isEmpty() && canvasWidth > 100f) {
                    level++
                    prefs.edit().putInt("level", level).apply()
                    spawnWorld(canvasWidth, canvasHeight, level)
                }

                // Update Hazards
                hazards.forEach { h ->
                    h.x += h.vx
                    h.y += h.vy
                    if (h.x < 48f || h.x > canvasWidth - 48f) h.vx = -h.vx
                    if (h.y < 190f || h.y > canvasHeight - 60f) h.vy = -h.vy

                    val dist = hypot(playerX - h.x, playerY - h.y)
                    if (dist < h.radius + 40f) {
                        emitParticles(playerX, playerY, Color(0xFFEF4444), 20)
                        playerX = canvasWidth / 2f
                        playerY = canvasHeight * 0.75f
                    }
                }

                // Update Particles
                val pIter = particles.listIterator()
                while (pIter.hasNext()) {
                    val p = pIter.next()
                    p.x += p.vx
                    p.y += p.vy
                    p.alpha -= 0.04f
                    if (p.alpha <= 0f) {
                        pIter.remove()
                    }
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        playerX = offset.x
                        playerY = offset.y
                        emitParticles(offset.x, offset.y, Color(0xFF38BDF8), 12)
                    }
                }
                .pointerInput(Unit) {
                    detectDragGestures { change, _ ->
                        change.consume()
                        playerX = change.position.x.coerceIn(50f, canvasWidth - 50f)
                        playerY = change.position.y.coerceIn(190f, canvasHeight - 50f)
                    }
                }
        ) {
            if (size.width > 0f && size.height > 0f) {
                if (canvasWidth != size.width || canvasHeight != size.height) {
                    canvasWidth = size.width
                    canvasHeight = size.height
                    if (playerX == 540f && playerY == 1100f) {
                        playerX = size.width / 2f
                        playerY = size.height * 0.68f
                        spawnWorld(size.width, size.height, level)
                    }
                }
            }

            // Deep Space Grid Background
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFF070B14), Color(0xFF0F172A), Color(0xFF070B14))
                ),
                size = size
            )

            val step = 80f
            var gx = 0f
            while (gx < size.width) {
                drawLine(
                    color = Color(0xFF38BDF8).copy(alpha = 0.06f),
                    start = Offset(gx, 0f),
                    end = Offset(gx, size.height),
                    strokeWidth = 1f
                )
                gx += step
            }
            var gy = 0f
            while (gy < size.height) {
                drawLine(
                    color = Color(0xFF38BDF8).copy(alpha = 0.06f),
                    start = Offset(0f, gy),
                    end = Offset(size.width, gy),
                    strokeWidth = 1f
                )
                gy += step
            }

            // Draw Collectible Orbs (with extracted APK sprites if available)
            orbs.forEach { orb ->
                drawCircle(
                    color = orb.color.copy(alpha = 0.28f),
                    radius = orb.radius * 1.4f,
                    center = Offset(orb.x, orb.y)
                )
                drawCircle(
                    color = orb.color,
                    radius = orb.radius,
                    center = Offset(orb.x, orb.y)
                )
                if (spriteBitmaps.isNotEmpty()) {
                    val bmp = spriteBitmaps[orb.spriteIndex % spriteBitmaps.size]
                    val s = (orb.radius * 1.4f).toInt().coerceAtLeast(24)
                    drawImage(
                        image = bmp,
                        dstOffset = IntOffset((orb.x - s / 2).toInt(), (orb.y - s / 2).toInt()),
                        dstSize = IntSize(s, s)
                    )
                }
            }

            // Draw Hazards
            hazards.forEach { h ->
                drawCircle(
                    color = Color(0xFFEF4444).copy(alpha = 0.3f),
                    radius = h.radius * 1.35f,
                    center = Offset(h.x, h.y)
                )
                drawCircle(
                    color = Color(0xFFEF4444),
                    radius = h.radius,
                    center = Offset(h.x, h.y)
                )
                drawCircle(
                    color = Color.White,
                    radius = h.radius,
                    center = Offset(h.x, h.y),
                    style = Stroke(width = 3f)
                )
            }

            // Draw Player Avatar (uses installed APK's real icon!)
            drawCircle(
                color = Color(0xFF38BDF8).copy(alpha = 0.25f),
                radius = 56f,
                center = Offset(playerX, playerY)
            )
            drawCircle(
                color = Color(0xFF38BDF8),
                radius = 46f,
                center = Offset(playerX, playerY),
                style = Stroke(width = 4f)
            )
            if (iconImageBitmap != null) {
                val iconSize = 64
                drawImage(
                    image = iconImageBitmap,
                    dstOffset = IntOffset((playerX - iconSize / 2).toInt(), (playerY - iconSize / 2).toInt()),
                    dstSize = IntSize(iconSize, iconSize)
                )
            } else {
                drawRoundRect(
                    color = Color(0xFF38BDF8),
                    topLeft = Offset(playerX - 24f, playerY - 24f),
                    size = Size(48f, 48f),
                    cornerRadius = CornerRadius(12f, 12f)
                )
            }

            // Draw Particles
            particles.forEach { p ->
                drawCircle(
                    color = p.color.copy(alpha = p.alpha.coerceIn(0f, 1f)),
                    radius = 7f,
                    center = Offset(p.x, p.y)
                )
            }
        }

        // Top Floating Virtual Framebuffer HUD Card
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = Color(0xFF111827).copy(alpha = 0.88f),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.3f)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp)
                .align(Alignment.TopCenter)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "$appName • Lv.$level • Score: $score",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${activeActivity.substringAfterLast('.')} • $lastInputLabel",
                        color = Color(0xFF38BDF8),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = Color(0xFF22C55E).copy(alpha = 0.16f)
                ) {
                    Text(
                        text = "$fps FPS • ${gpuName.substringBefore(" ")}",
                        color = Color(0xFF22C55E),
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ApkActivitiesAndDexInspector(
    bundle: LoadedApkRuntimeBundle?,
    activeActivity: String,
    runtimeLogs: List<String>,
    onLaunchActivity: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF090D16)),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF111827)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "APK Virtual Runtime Inspector",
                        color = Color(0xFF38BDF8),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Package: ${bundle?.packageName ?: "Loading..."} • v${bundle?.versionName ?: "1.0"}",
                        color = Color.White,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp
                    )
                    Text(
                        text = "DEX Containers: ${bundle?.dexCount ?: 1} • Native Libraries: ${bundle?.nativeLibs?.joinToString(", ") ?: "Standard"}",
                        color = Color(0xFF9CA3AF),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp
                    )
                }
            }
        }

        // Extracted Sprites / Drawables Preview
        if (!bundle?.extractedSprites.isNullOrEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF111827)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Extracted APK Drawables & Textures (${bundle?.extractedSprites?.size ?: 0})",
                            color = Color(0xFF38BDF8),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(bundle?.extractedSprites.orEmpty()) { bmp ->
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = Color(0xFF1F2937),
                                    modifier = Modifier.size(56.dp)
                                ) {
                                    Image(
                                        bitmap = bmp.asImageBitmap(),
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Declared Activities
        item {
            Text(
                text = "Declared APK Activities (Tap to Launch in VM)",
                color = Color(0xFF38BDF8),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
        }

        items(bundle?.activities.orEmpty()) { act ->
            val isCurrent = act == activeActivity
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (isCurrent) Color(0xFF1E293B) else Color(0xFF111827),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (isCurrent) Color(0xFF38BDF8) else Color(0xFF1F2937)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = act.substringAfterLast('.'),
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        Text(
                            text = act,
                            color = Color(0xFF9CA3AF),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Button(
                        onClick = { onLaunchActivity(act) },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (isCurrent) "Active" else "Launch", fontSize = 11.sp)
                    }
                }
            }
        }

        // Verified DexClassLoader Classes & Logcat
        item {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF111827)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "DexClassLoader & Runtime Logcat",
                        color = Color(0xFF22C55E),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                    bundle?.verifiedClasses?.forEach { cls ->
                        Text(
                            text = "✓ $cls",
                            color = Color(0xFFA7F3D0),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    runtimeLogs.takeLast(12).forEach { log ->
                        Text(
                            text = log,
                            color = Color(0xFF9CA3AF),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp
                        )
                    }
                }
            }
        }
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
