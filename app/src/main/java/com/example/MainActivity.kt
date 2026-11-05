package com.example

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.MotionEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.view.WindowCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import com.example.telecom.TelecomHelper
import com.example.telecom.XenoNotificationListenerService
import com.example.ui.components.GlobalSwipeDownTracker
import com.example.ui.components.InAppNotificationOverlay
import com.example.ui.components.NewChatDialog
import com.example.ui.components.VideoPlayerModal
import com.example.ui.components.XenoBottomBar
import com.example.ui.screens.ActiveCallScreen
import com.example.ui.screens.AppAlertsDrawerOverlay
import com.example.ui.screens.BrowserHubScreen
import com.example.ui.screens.CallsScreen
import com.example.ui.screens.CameraAppScreen
import com.example.ui.screens.ChatScreen
import com.example.ui.screens.ContactsScreen
import com.example.ui.screens.DocsManagerScreen
import com.example.ui.screens.GalleryScreen
import com.example.ui.screens.KeypadScreen
import com.example.ui.screens.MessagesScreen
import com.example.ui.screens.MusicPlayerScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.XenoTab
import com.example.ui.viewmodel.XenoViewModel

class MainActivity : ComponentActivity() {

    companion object {
        @Volatile
        var isAppInForeground: Boolean = false
    }

    private val currentIntentState = mutableStateOf<Intent?>(null)
    private val intentTriggerCounter = mutableIntStateOf(0)

    private fun applyLockScreenAndWakeFlags() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                setShowWhenLocked(true)
                setTurnScreenOn(true)
            }
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        } catch (_: Exception) {}
    }

    private fun applyHighRefreshRateAndPerformanceFlags() {
        try {
            window.addFlags(WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val supportedModes = display?.supportedModes
                val maxRefreshMode = supportedModes?.maxByOrNull { it.refreshRate }
                if (maxRefreshMode != null) {
                    window.attributes = window.attributes.apply {
                        preferredDisplayModeId = maxRefreshMode.modeId
                    }
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                window.attributes = window.attributes.apply {
                    preferredRefreshRate = 120f
                }
            }
        } catch (_: Exception) {}
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        applyLockScreenAndWakeFlags()
        applyHighRefreshRateAndPerformanceFlags()
        enableEdgeToEdge()
        currentIntentState.value = intent
        intentTriggerCounter.intValue++

        setContent {
            MyApplicationTheme {
                val viewModel: XenoViewModel = viewModel()
                val activeIntent by currentIntentState
                val triggerTick by intentTriggerCounter
                XenoApp(
                    viewModel = viewModel,
                    initialIntent = activeIntent,
                    intentTriggerTick = triggerTick
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        isAppInForeground = true
    }

    override fun onResume() {
        super.onResume()
        isAppInForeground = true
    }

    override fun onStop() {
        super.onStop()
        isAppInForeground = false
    }

    private var swipeTouchDownX = 0f
    private var swipeTouchDownY = 0f
    private var swipeTriggeredInGesture = false
    private var swipeIsMultiTouch = false
    private var swipeDisallowedByHorizontalOrUp = false

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                swipeTouchDownX = ev.rawX
                swipeTouchDownY = ev.rawY
                swipeTriggeredInGesture = false
                swipeIsMultiTouch = false
                swipeDisallowedByHorizontalOrUp = false
                GlobalSwipeDownTracker.onGestureStart()
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                swipeIsMultiTouch = true
            }
            MotionEvent.ACTION_MOVE -> {
                val handled = super.dispatchTouchEvent(ev)
                if (!GlobalSwipeDownTracker.isDrawerOpen &&
                    GlobalSwipeDownTracker.isSwipeEnabled &&
                    !swipeTriggeredInGesture &&
                    !swipeIsMultiTouch
                ) {
                    val dx = ev.rawX - swipeTouchDownX
                    val dy = ev.rawY - swipeTouchDownY
                    val absDx = kotlin.math.abs(dx)
                    val absDy = kotlin.math.abs(dy)
                    val density = resources.displayMetrics.density

                    if (dy < -12f * density || (absDx > 16f * density && absDx > absDy)) {
                        swipeDisallowedByHorizontalOrUp = true
                    }

                    if (!swipeDisallowedByHorizontalOrUp && dy > 0f && dy > absDx * 1.35f) {
                        val screenHeight = window.decorView.height.takeIf { it > 0 }
                            ?: resources.displayMetrics.heightPixels
                        val topQuarterMaxY = screenHeight * 0.25f
                        val topZoneThresholdPx = 36f * density

                        val shouldOpen = swipeTouchDownY <= topQuarterMaxY && dy >= topZoneThresholdPx

                        if (shouldOpen) {
                            swipeTriggeredInGesture = true
                            val cancelEvent = MotionEvent.obtain(ev).apply {
                                action = MotionEvent.ACTION_CANCEL
                            }
                            super.dispatchTouchEvent(cancelEvent)
                            cancelEvent.recycle()
                            GlobalSwipeDownTracker.openDrawer()
                            return true
                        }
                    }
                }
                return handled
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        currentIntentState.value = intent
        intentTriggerCounter.intValue++
    }
}

@Composable
fun XenoApp(
    viewModel: XenoViewModel,
    initialIntent: Intent?,
    intentTriggerTick: Int = 0
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val lifecycleOwner = LocalLifecycleOwner.current
    val callListState = androidx.compose.foundation.lazy.rememberLazyListState()
    val contactListState = androidx.compose.foundation.lazy.rememberLazyListState()

    val activity = context as? Activity
    LaunchedEffect(uiState.preventOverlayApps) {
        activity?.window?.let { win ->
            try {
                win.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            } catch (_: Throwable) {}
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    win.setHideOverlayWindows(uiState.preventOverlayApps)
                }
            } catch (_: Throwable) {}
            try {
                win.decorView.filterTouchesWhenObscured = uiState.preventOverlayApps
            } catch (_: Throwable) {}
        }
    }

    // Observe lifecycle to refresh status when returning from settings/role prompts
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.checkDefaultAppStatus()
                viewModel.checkNotificationAccess()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Role request launchers
    val defaultDialerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        viewModel.checkDefaultAppStatus()
    }

    val defaultSmsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        viewModel.checkDefaultAppStatus()
    }

    var hasPromptedDialer by remember { mutableStateOf(false) }
    LaunchedEffect(uiState.isDefaultDialer) {
        if (!uiState.isDefaultDialer && !hasPromptedDialer) {
            delay(2500L) // Fast launch speed: let the app open and render instantly before requesting role
            hasPromptedDialer = true
            val dialerIntent = TelecomHelper.createRequestDefaultDialerIntent(context)
            if (dialerIntent != null) {
                try {
                    defaultDialerLauncher.launch(dialerIntent)
                } catch (_: Exception) {}
            }
        }
    }

    // Runtime permissions launcher
    val permissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        viewModel.loadAllData()
        viewModel.checkNotificationAccess()
    }

    var hasPromptedPerms by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        // Defer secondary data loading
        delay(1000)
        viewModel.boostAppPerformanceAndFastLoadHelper()
        
        if (!hasPromptedPerms) {
            hasPromptedPerms = true
            val perms = mutableListOf(
            android.Manifest.permission.CALL_PHONE,
            android.Manifest.permission.READ_PHONE_STATE,
            android.Manifest.permission.READ_CALL_LOG,
            android.Manifest.permission.WRITE_CALL_LOG,
            android.Manifest.permission.READ_CONTACTS,
            android.Manifest.permission.WRITE_CONTACTS,
            android.Manifest.permission.READ_SMS,
            android.Manifest.permission.SEND_SMS,
            android.Manifest.permission.RECEIVE_SMS,
            android.Manifest.permission.RECORD_AUDIO,
            android.Manifest.permission.CAMERA
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            perms.add(android.Manifest.permission.READ_PHONE_NUMBERS)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.add(android.Manifest.permission.READ_MEDIA_IMAGES)
            perms.add(android.Manifest.permission.READ_MEDIA_VIDEO)
            perms.add(android.Manifest.permission.READ_MEDIA_AUDIO)
            perms.add(android.Manifest.permission.POST_NOTIFICATIONS)
        } else {
            perms.add(android.Manifest.permission.READ_EXTERNAL_STORAGE)
            perms.add(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
        if (Build.VERSION.SDK_INT >= 29) {
            perms.add(android.Manifest.permission.ACCESS_MEDIA_LOCATION)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            perms.add(android.Manifest.permission.BLUETOOTH_CONNECT)
        }
        val missingPerms = perms.filter {
            androidx.core.content.ContextCompat.checkSelfPermission(context, it) !=
                android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        if (missingPerms.isNotEmpty()) {
            permissionsLauncher.launch(missingPerms.toTypedArray())
        }
        }
    }

    // Handle intents (incoming calls, SMS reply, tel:, smsto:)
    LaunchedEffect(initialIntent, intentTriggerTick) {
        initialIntent?.let { intent ->
            val hasIncomingCallIntent = intent.hasExtra("incoming_number") ||
                intent.getBooleanExtra("auto_answer", false) ||
                intent.getBooleanExtra("open_full_screen_call", false)
            if (hasIncomingCallIntent) {
                val incomingNumber = intent.getStringExtra("incoming_number")?.takeIf { it.isNotBlank() } ?: "Unknown Caller"
                val callerName = intent.getStringExtra("caller_name")
                val autoAnswer = intent.getBooleanExtra("auto_answer", false)
                intent.removeExtra("incoming_number")
                intent.removeExtra("auto_answer")
                intent.removeExtra("wake_full_screen_call")
                intent.removeExtra("open_full_screen_call")
                if (autoAnswer) {
                    viewModel.answerIncomingCallFromIntent(incomingNumber, callerName)
                } else {
                    viewModel.openFullScreenIncomingCall(incomingNumber, callerName)
                }
                return@LaunchedEffect
            }

            val openTab = intent.getStringExtra("open_tab")
            if (openTab == "MESSAGES") {
                val senderNumber = intent.getStringExtra("sender_number")
                intent.removeExtra("open_tab")
                intent.removeExtra("sender_number")
                viewModel.selectTab(XenoTab.MESSAGES)
                if (!senderNumber.isNullOrBlank()) {
                    viewModel.startChatWithNumber(senderNumber)
                }
                return@LaunchedEffect
            } else if (openTab == "CALLS") {
                intent.removeExtra("open_tab")
                viewModel.selectTab(XenoTab.CALLS)
            }

            if (intent.getBooleanExtra("open_music_player", false)) {
                intent.removeExtra("open_music_player")
                viewModel.setShowMusicPlayer(true)
            }

            when (intent.action) {
                Intent.ACTION_DIAL, Intent.ACTION_VIEW -> {
                    val scheme = intent.data?.scheme
                    if (scheme == "tel") {
                        val number = intent.data?.schemeSpecificPart ?: ""
                        if (number.isNotBlank()) {
                            viewModel.setDialpadDigits(number)
                            viewModel.selectTab(XenoTab.KEYPAD)
                        }
                    }
                }
                Intent.ACTION_SENDTO, Intent.ACTION_SEND -> {
                    val scheme = intent.data?.scheme
                    if (scheme == "smsto" || scheme == "sms") {
                        val number = intent.data?.schemeSpecificPart ?: ""
                        if (number.isNotBlank()) {
                            viewModel.startChatWithNumber(number)
                        }
                    }
                }
            }
        }
    }

    // Clear user notices silently (removing blue notification snackbar popup completely)
    LaunchedEffect(uiState.userNotice) {
        uiState.userNotice?.let { notice ->
            viewModel.clearUserNotice()
        }
    }

    // Immersive system bars for video playback
    val isImmersiveVideoActive = uiState.isBrowserFullscreenVideo || uiState.selectedVideoItem != null
    LaunchedEffect(isImmersiveVideoActive) {
        val window = (context as? Activity)?.window ?: return@LaunchedEffect
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        if (isImmersiveVideoActive) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                window.attributes = window.attributes.apply {
                    layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
            }
            insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            insetsController.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            insetsController.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    val unreadSmsCount = remember(uiState.smsThreads) {
        uiState.smsThreads.sumOf { it.unreadCount }
    }

    // Back handling for standard navigation stack
    BackHandler {
        if (uiState.showCameraApp) {
            viewModel.setShowCameraApp(false)
        } else if (uiState.activeChatThread != null) {
            viewModel.closeChat()
        } else if (uiState.isAppAlertsDrawerOpen) {
            viewModel.setAppAlertsDrawerOpen(false)
        } else if (uiState.currentTab != XenoTab.CALLS) {
            viewModel.selectTab(XenoTab.CALLS)
        } else {
            (context as? Activity)?.finish()
        }
    }

    val isCloneWebViewOpen = (uiState.currentTab == XenoTab.BROWSER || uiState.currentTab == XenoTab.CLONE_SPACE) &&
        uiState.browserDualTab == com.example.ui.viewmodel.BrowserDualTab.SPACE &&
        uiState.activeSpaceInstance != null &&
        uiState.activeSpaceInstance!!.targetUrl.isNotBlank()
    val isBrowserWebViewOpen = (uiState.currentTab == XenoTab.BROWSER || uiState.currentTab == XenoTab.CLONE_SPACE) &&
        uiState.browserDualTab == com.example.ui.viewmodel.BrowserDualTab.BROWSER &&
        uiState.activeBrowserTab?.url?.isNotBlank() == true

    GlobalSwipeDownTracker.isDrawerOpen = uiState.isAppAlertsDrawerOpen
    GlobalSwipeDownTracker.isSwipeEnabled = !uiState.showNewChatDialog &&
        !uiState.showAddCloneDialog &&
        uiState.selectedCloneForEdit == null &&
        !uiState.showHistoryDialog &&
        !uiState.showTabSwitcher &&
        !uiState.showPasswordManager
    GlobalSwipeDownTracker.isWebViewScreenActive = (isCloneWebViewOpen || isBrowserWebViewOpen) &&
        !uiState.showMediaManager &&
        !uiState.showDocsManager &&
        !uiState.showMusicPlayer &&
        uiState.selectedVideoItem == null &&
        uiState.activeChatThread == null
    GlobalSwipeDownTracker.onOpenDrawer = {
        viewModel.setAppAlertsDrawerOpen(true)
    }

    val rootNestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                if (kotlin.math.abs(consumed.y) > 0.5f) {
                    GlobalSwipeDownTracker.didComposeChildScroll = true
                }
                return Offset.Zero
            }
        }
    }

    // If there is an active call, show ActiveCallScreen full screen (Screenshots 2 & 4)
    val activeCall = uiState.activeCallState
    if (activeCall != null) {
        ActiveCallScreen(
            phoneNumber = activeCall.phoneNumber,
            contactName = activeCall.contactName,
            isIncoming = activeCall.isIncoming,
            onAcceptCall = { viewModel.answerIncomingCall() },
            onDeclineCall = { viewModel.endCall() },
            onEndCall = { _ -> viewModel.endCall() },
            modifier = Modifier.fillMaxSize()
        )
        return
    }

    // Normal full-screen phone app Scaffold (no floating window)
    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            contentWindowInsets = WindowInsets.systemBars,
            snackbarHost = {},
            bottomBar = {
                if (uiState.activeChatThread == null &&
                    uiState.activeSpaceInstanceId == null &&
                    !uiState.showMediaManager &&
                    !uiState.showDocsManager &&
                    !uiState.showMusicPlayer &&
                    uiState.selectedVideoItem == null &&
                    !uiState.isBrowserHeaderHidden &&
                    !uiState.isBrowserFullscreenVideo
                ) {
                    XenoBottomBar(
                        currentTab = uiState.currentTab,
                        onTabSelected = {
                            viewModel.setAppAlertsDrawerOpen(false)
                            viewModel.selectTab(it)
                        },
                        missedCallsCount = 0,
                        unreadMessagesCount = unreadSmsCount
                    )
                }
            }
        ) { innerPadding ->
            val effectivePadding = if (isImmersiveVideoActive) PaddingValues(0.dp) else innerPadding
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(effectivePadding)
                    .nestedScroll(rootNestedScrollConnection)
            ) {
                if (uiState.showCameraApp) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        CameraAppScreen(
                            onClose = { viewModel.setShowCameraApp(false) },
                            onPhotoCaptured = { media ->
                                viewModel.addCustomMedia(media.uri, media.isVideo, media.displayName)
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                } else if (uiState.selectedVideoItem != null) {
                    VideoPlayerModal(
                        videoItem = uiState.selectedVideoItem!!,
                        isControlsHidden = uiState.isVideoControlsHidden,
                        onToggleHide = { viewModel.setVideoControlsHidden(it) },
                        onClose = { viewModel.selectVideoItem(null) },
                        onDeleteVideo = { viewModel.deleteMediaItem(it) },
                        modifier = Modifier.fillMaxSize()
                    )
                } else if (uiState.showDocsManager) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        DocsManagerScreen(
                            folders = uiState.documentFolders,
                            isLoading = uiState.isLoadingDocs,
                            onRefresh = { viewModel.refreshDocumentFolders() },
                            onOpenDocumentContent = { doc -> viewModel.openDocumentReadOnly(doc) },
                            onClose = { viewModel.setShowDocsManager(false) },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                } else if (uiState.showMusicPlayer) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        MusicPlayerScreen(
                            tracks = uiState.musicTracks,
                            isLoading = uiState.isLoadingMusic,
                            onRefreshScan = { viewModel.refreshMusicTracks() },
                            onToggleFavorite = { trackId -> viewModel.toggleFavoriteMusicTrack(trackId) },
                            onImportAudioUris = { uris -> viewModel.importMusicUris(uris) },
                            onClose = { viewModel.setShowMusicPlayer(false) },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                } else if (uiState.showMediaManager) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        GalleryScreen(
                            mediaItems = uiState.mediaItems,
                            filter = uiState.mediaFilter,
                            selectedImageItem = uiState.selectedImageItem,
                            selectedVideoItem = uiState.selectedVideoItem,
                            isControlsHidden = uiState.isVideoControlsHidden,
                            onToggleHide = { viewModel.setVideoControlsHidden(it) },
                            onFilterChange = { viewModel.setMediaFilter(it) },
                            onSelectImage = { viewModel.selectImageItem(it) },
                            onSelectVideo = { viewModel.selectVideoItem(it) },
                            onRefresh = { viewModel.refreshMedia() },
                            onAddCustomMedia = { uri, isVideo, name -> viewModel.addCustomMedia(uri, isVideo, name) },
                            onDeleteMedia = { viewModel.deleteMediaItem(it) },
                            onDeleteDemoMedia = { viewModel.removeDemoMedia() },
                            onClose = { viewModel.setShowMediaManager(false) },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                } else {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Main Content View
                        if (uiState.activeChatThread != null) {
                            ChatScreen(
                                thread = uiState.activeChatThread!!,
                                messages = uiState.activeChatMessages,
                                onBackClick = { viewModel.closeChat() },
                                onSendMessage = { text, sim -> viewModel.sendChatMessage(text, sim) },
                                onDeleteMessage = { viewModel.deleteChatMessage(it) },
                                onDeleteThread = {
                                    viewModel.deleteChatThread(uiState.activeChatThread!!)
                                }
                            )
                        } else {
                            when (uiState.currentTab) {
                                XenoTab.CALLS -> {
                                    CallsScreen(
                                        listState = callListState,
                                        callLogs = uiState.callLogs,
                                        filter = uiState.callFilter,
                                        onFilterChange = { viewModel.setCallFilter(it) },
                                        onDeleteCall = { viewModel.deleteCallLog(it) },
                                        onClearAll = { viewModel.clearAllCallLogs() },
                                        onClearMissedCalls = { viewModel.clearMissedCallLogs() },
                                        onMarkMissedSeen = { viewModel.markMissedCallsAsSeen() },
                                        onStartSms = { number, name -> viewModel.startChatWithNumber(number, name) },
                                        onOpenContacts = { viewModel.selectTab(XenoTab.CONTACTS) },
                                        onStartCall = { number, name -> viewModel.startCall(number = number, name = name) },
                                        onStartCallWithSim = { number, name, sim -> viewModel.startCall(number = number, name = name, simCard = sim) },
                                        onWhatsAppCall = { number, name -> viewModel.initiateWhatsAppCall(context, number, name) },
                                        onSpeedBoost = { viewModel.triggerSpeedBoost() },
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                                XenoTab.KEYPAD -> {
                                    KeypadScreen(
                                        digits = uiState.dialpadDigits,
                                        onDigitClick = { viewModel.appendDialpadDigit(it) },
                                        onBackspace = { viewModel.backspaceDialpad() },
                                        onClear = { viewModel.clearDialpad() },
                                        onSettingsClick = { viewModel.selectTab(XenoTab.CALLS) },
                                        contacts = uiState.contacts,
                                        onStartSms = { number, name -> viewModel.startChatWithNumber(number, name) },
                                        onStartCall = { number, name -> viewModel.startCall(number = number, name = name) },
                                        onStartCallWithSim = { number, name, sim -> viewModel.startCall(number = number, name = name, simCard = sim) },
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                                XenoTab.CONTACTS -> {
                                    ContactsScreen(
                                        listState = contactListState,
                                        contacts = uiState.contacts,
                                        searchQuery = uiState.contactSearchQuery,
                                        onSearchQueryChange = { viewModel.setContactSearchQuery(it) },
                                        onStartSms = { number, name -> viewModel.startChatWithNumber(number, name) },
                                        onStartCall = { number, name -> viewModel.startCall(number = number, name = name) },
                                        onWhatsAppCall = { number, name -> viewModel.initiateWhatsAppCall(context, number, name) },
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                                XenoTab.MESSAGES -> {
                                    MessagesScreen(
                                        threads = uiState.smsThreads,
                                        searchQuery = uiState.smsSearchQuery,
                                        onSearchQueryChange = { viewModel.setSmsSearchQuery(it) },
                                        onOpenThread = { viewModel.openChatThread(it) },
                                        onDeleteThread = { viewModel.deleteChatThread(it) },
                                        onStartNewChat = { viewModel.setShowNewChatDialog(true) },
                                        subTab = uiState.messagesSubTab,
                                        onSubTabChange = { viewModel.setMessagesSubTab(it) },
                                        notifications = uiState.deviceNotifications,
                                        hasNotificationAccess = uiState.hasNotificationAccess,
                                        onRequestNotificationAccess = {
                                            XenoNotificationListenerService.openNotificationSettings(context)
                                        },
                                        onDismissNotification = { viewModel.dismissNotification(it) },
                                        onClearAllNotifications = { viewModel.clearAllNotifications() },
                                        onAddSampleAlerts = { viewModel.addSampleCommunicationAlerts() },
                                        onSimulateAlert = { viewModel.simulateIncomingAlert(it) },
                                        onOpenAppDetailsSettings = { XenoNotificationListenerService.openAppDetailsSettings(context) },
                                        onSimulateSms = { viewModel.simulateIncomingSms() },
                                        onReplyNotification = { notif, text -> viewModel.replyToNotification(notif, text) }
                                    )
                                }
                                XenoTab.BROWSER, XenoTab.CLONE_SPACE -> {
                                    BrowserHubScreen(
                                        dualTab = uiState.browserDualTab,
                                        onDualTabChanged = { viewModel.setBrowserDualTab(it) },
                                        tabs = uiState.browserTabs,
                                        activeTabId = uiState.activeBrowserTabId,
                                        history = uiState.browserHistory,
                                        showTabSwitcher = uiState.showTabSwitcher,
                                        showHistoryDialog = uiState.showHistoryDialog,
                                        onOpenNewTab = { viewModel.openNewBrowserTab(it) },
                                        onSelectTab = { viewModel.selectBrowserTab(it) },
                                        onCloseTab = { viewModel.closeBrowserTab(it) },
                                        onUpdateTabUrl = { url, title -> viewModel.updateActiveTabUrl(url, title) },
                                        onUpdateTabLoading = { isLoading, progress -> viewModel.updateActiveTabLoading(isLoading, progress) },
                                        onUpdateTabNavState = { back, fwd -> viewModel.updateActiveTabNavState(back, fwd) },
                                        onToggleTabDesktopMode = { viewModel.toggleActiveTabDesktopMode() },
                                        onSetShowTabSwitcher = { viewModel.setShowTabSwitcher(it) },
                                        onSetShowHistoryDialog = { viewModel.setShowHistoryDialog(it) },
                                        onClearHistory = { viewModel.clearBrowserHistory() },
                                        isBrowserHeaderHidden = uiState.isBrowserHeaderHidden,
                                        onToggleBrowserHeader = { viewModel.toggleBrowserHeader() },
                                        isBrowserFullscreenVideo = uiState.isBrowserFullscreenVideo,
                                        onSetBrowserFullscreenVideo = { viewModel.setBrowserFullscreenVideo(it) },
                                        spaceClones = uiState.spaceClones,
                                        activeSpaceDock = uiState.activeSpaceDock,
                                        minimizedCloneIds = uiState.minimizedSpaceCloneIds,
                                        activeSpaceInstance = uiState.activeSpaceInstance,
                                        showAddCloneDialog = uiState.showAddCloneDialog,
                                        selectedCloneForEdit = uiState.selectedCloneForEdit,
                                        onLaunchClone = { viewModel.launchCloneWorkspace(it) },
                                        onCloseCloneWorkspace = { viewModel.closeCloneWorkspace() },
                                        onMinimizeCloneWorkspace = { viewModel.minimizeCloneWorkspace() },
                                        onOpenAddClone = { viewModel.openAddCloneDialog() },
                                        onCloseAddClone = { viewModel.closeAddCloneDialog() },
                                        onCreateClone = { pkg, appName, name, badgeTag, badgeColorHex ->
                                            viewModel.createAppClone(pkg, appName, name, badgeTag, badgeColorHex)
                                        },
                                        onOpenEditClone = { viewModel.openEditCloneDialog(it) },
                                        onCloseEditClone = { viewModel.closeEditCloneDialog() },
                                        onUpdateClone = { viewModel.updateAppClone(it) },
                                        onDeleteClone = { viewModel.deleteAppClone(it) },
                                        onCloseFromDock = { viewModel.closeCloneFromDock(it) },
                                        onCloneUrlChanged = { instanceId, url -> viewModel.updateCloneCurrentUrl(instanceId, url) },
                                        onToggleDesktopMode = { viewModel.toggleCloneDesktopMode(it) },
                                        downloads = uiState.downloads,
                                        onDeleteDownload = { viewModel.deleteDownloadItem(it) },
                                        onClearAllDownloads = { viewModel.clearAllDownloads() },
                                        onAddDownloadItem = { dmId, url, fileName, mimeType, fileSize ->
                                            viewModel.addDownloadItem(dmId, url, fileName, mimeType, fileSize)
                                        },
                                        savedPasswords = uiState.savedPasswords,
                                        showPasswordManager = uiState.showPasswordManager,
                                        onSetShowPasswordManager = { viewModel.setShowPasswordManager(it) },
                                        onSetShowMediaManager = { viewModel.setShowMediaManager(it) },
                                        onSetShowDocsManager = { viewModel.setShowDocsManager(it) },
                                        onSetShowMusicPlayer = { viewModel.setShowMusicPlayer(it) },
                                        onOpenCamera = { viewModel.setShowCameraApp(true) },
                                        onSavePassword = { domain, title, username, pass ->
                                            viewModel.savePassword(domain, title, username, pass)
                                        },
                                        onDeletePassword = { viewModel.deletePassword(it) },
                                        onPlayVideo = { viewModel.selectVideoItem(it) },
                                        onBackToHome = { viewModel.selectTab(XenoTab.CALLS) }
                                    )
                                }
                                XenoTab.SETTINGS -> {
                                    SettingsScreen(
                                        isDefaultDialer = uiState.isDefaultDialer,
                                        isDefaultSms = uiState.isDefaultSms,
                                        onRequestDefaultDialer = {
                                            TelecomHelper.createRequestDefaultDialerIntent(context)?.let { context.startActivity(it) }
                                        },
                                        onRequestDefaultSms = {
                                            TelecomHelper.createRequestDefaultSmsIntent(context)?.let { context.startActivity(it) }
                                        },
                                        onPermissionsUpdated = { viewModel.loadAllData(forceRefresh = true) },
                                        onSimulateSms = { viewModel.simulateIncomingSms() },
                                        preventOverlayApps = uiState.preventOverlayApps,
                                        onTogglePreventOverlayApps = { viewModel.setPreventOverlayApps(it) },
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                                else -> {
                                    CallsScreen(
                                        callLogs = uiState.callLogs,
                                        filter = uiState.callFilter,
                                        onFilterChange = { viewModel.setCallFilter(it) },
                                        onDeleteCall = { viewModel.deleteCallLog(it) },
                                        onClearAll = { viewModel.clearAllCallLogs() },
                                        onClearMissedCalls = { viewModel.clearMissedCallLogs() },
                                        onMarkMissedSeen = { viewModel.markMissedCallsAsSeen() },
                                        onStartSms = { number, name -> viewModel.startChatWithNumber(number, name) },
                                        onOpenContacts = { viewModel.selectTab(XenoTab.CONTACTS) },
                                        onStartCall = { number, name -> viewModel.startCall(number = number, name = name) },
                                        onStartCallWithSim = { number, name, sim -> viewModel.startCall(number = number, name = name, simCard = sim) },
                                        onWhatsAppCall = { number, name -> viewModel.initiateWhatsAppCall(context, number, name) },
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // App Alerts Drawer Overlay (full screen overlay)
        AppAlertsDrawerOverlay(
            isOpen = uiState.isAppAlertsDrawerOpen,
            onCloseDrawer = { viewModel.setAppAlertsDrawerOpen(false) },
            notifications = uiState.deviceNotifications,
            hasNotificationAccess = uiState.hasNotificationAccess,
            onRequestNotificationAccess = {
                XenoNotificationListenerService.openNotificationSettings(context)
            },
            onDismissNotification = { viewModel.dismissNotification(it) },
            onClearAllNotifications = { viewModel.clearAllNotifications() },
            onAddSampleAlerts = { viewModel.addSampleCommunicationAlerts() },
            onSimulateAlert = { viewModel.simulateIncomingAlert(it) },
            onOpenAppDetailsSettings = { XenoNotificationListenerService.openAppDetailsSettings(context) },
            onSimulateSms = { viewModel.simulateIncomingSms() },
            incomingWhatsAppCall = uiState.incomingWhatsAppCall,
            onAcceptWhatsAppCall = { viewModel.acceptWhatsAppCall(it) },
            onDeclineWhatsAppCall = { viewModel.declineWhatsAppCall() },
            onSpeedBoost = { viewModel.triggerSpeedBoost() },
            shape = RoundedCornerShape(0.dp),
            modifier = Modifier
                .fillMaxSize()
                .zIndex(25f)
        )



        // Top In-App Notification Popup Overlay (Call banner & SMS banner)
        InAppNotificationOverlay(
            incomingSms = uiState.incomingSmsPopup,
            incomingCall = uiState.incomingCallPopup,
            onMarkSmsRead = { viewModel.markSmsPopupAsRead(it) },
            onReplySms = { viewModel.replySmsPopup(it) },
            onDismissSms = { viewModel.dismissSmsPopup() },
            onAcceptCall = { viewModel.acceptCallPopup(it) },
            onDeclineCall = { viewModel.declineCallPopup(it) },
            onDismissCall = { viewModel.dismissIncomingCallPopup() },
            onExpandCall = { viewModel.openFullScreenIncomingCall(it.callerNumber, it.callerName) },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .zIndex(30f)
        )

        // New Chat Dialog
        if (uiState.showNewChatDialog) {
            NewChatDialog(
                contacts = uiState.contacts,
                onDismiss = { viewModel.setShowNewChatDialog(false) },
                onStartChat = { number, name ->
                    viewModel.startChatWithNumber(number, name)
                }
            )
        }

        // Super Speed Boost Overlay
        if (uiState.showSpeedBoostOverlay) {
            com.example.ui.components.SpeedBoostOverlay(
                isBoosting = uiState.isBoosting,
                result = uiState.speedBoostResult,
                onDismiss = { viewModel.dismissSpeedBoostOverlay() }
            )
        }
    }
}
