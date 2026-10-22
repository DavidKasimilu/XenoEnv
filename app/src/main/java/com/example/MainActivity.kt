package com.example

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.zIndex
import com.example.ui.components.InAppNotificationOverlay
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.models.CallType
import com.example.telecom.TelecomHelper
import com.example.telecom.XenoNotificationListenerService
import com.example.ui.components.DefaultAppBanner
import com.example.ui.components.NewChatDialog
import com.example.ui.components.XenoBottomBar
import com.example.ui.components.VideoPlayerModal
import com.example.ui.screens.ActiveCallScreen
import com.example.ui.screens.BrowserHubScreen
import com.example.ui.screens.BrowserScreen
import com.example.ui.screens.CallsScreen
import com.example.ui.screens.ChatScreen
import com.example.ui.screens.ContactsScreen
import com.example.ui.screens.FileManagerScreen
import com.example.ui.screens.GalleryScreen
import com.example.ui.screens.KeypadScreen
import com.example.ui.screens.MessagesScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.XenoTab
import com.example.ui.viewmodel.XenoViewModel
import com.example.util.AppPerformanceHelper

class MainActivity : ComponentActivity() {

    private val currentIntentState = androidx.compose.runtime.mutableStateOf<Intent?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppPerformanceHelper.optimizeWindowForSmoothness(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        enableEdgeToEdge()
        currentIntentState.value = intent

        setContent {
            MyApplicationTheme {
                val viewModel: XenoViewModel = viewModel()
                val activeIntent by currentIntentState
                XenoApp(
                    viewModel = viewModel,
                    initialIntent = activeIntent
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        currentIntentState.value = intent
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        AppPerformanceHelper.onTrimMemory(level)
    }
}

@Composable
fun XenoApp(
    viewModel: XenoViewModel,
    initialIntent: Intent?
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val lifecycleOwner = LocalLifecycleOwner.current

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
                viewModel.loadAllData()
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

    // Runtime permissions launcher
    val permissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        viewModel.loadAllData()
        viewModel.checkNotificationAccess()
    }

    val apkPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            viewModel.installApk(it, context)
        }
    }

    // Request permissions on first launch
    LaunchedEffect(Unit) {
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
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            perms.add(android.Manifest.permission.READ_PHONE_NUMBERS)
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            perms.add(android.Manifest.permission.READ_MEDIA_IMAGES)
            perms.add(android.Manifest.permission.READ_MEDIA_VIDEO)
            perms.add(android.Manifest.permission.READ_MEDIA_AUDIO)
            perms.add(android.Manifest.permission.POST_NOTIFICATIONS)
        } else {
            perms.add(android.Manifest.permission.READ_EXTERNAL_STORAGE)
            perms.add(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            perms.add(android.Manifest.permission.ACCESS_MEDIA_LOCATION)
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            perms.add(android.Manifest.permission.BLUETOOTH_CONNECT)
        }
        permissionsLauncher.launch(perms.toTypedArray())
    }

    // Handle intents (e.g. incoming call notification, SMS reply notification, tel: or smsto:)
    LaunchedEffect(initialIntent) {
        initialIntent?.let { intent ->
            val incomingNumber = intent.getStringExtra("incoming_number")
            if (!incomingNumber.isNullOrBlank()) {
                val callerName = intent.getStringExtra("caller_name")
                val autoAnswer = intent.getBooleanExtra("auto_answer", false)
                intent.removeExtra("incoming_number")
                intent.removeExtra("auto_answer")
                if (autoAnswer) {
                    viewModel.startActiveCall(incomingNumber, callerName, isIncoming = false)
                    viewModel.acceptActiveCall()
                } else {
                    viewModel.startActiveCall(incomingNumber, callerName, isIncoming = true)
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

    // Show user notices
    LaunchedEffect(uiState.userNotice) {
        uiState.userNotice?.let { notice ->
            snackbarHostState.showSnackbar(notice)
            viewModel.clearUserNotice()
        }
    }

    // Immersive system bars for browser fullscreen video and built-in video player (no top or bottom phone bars)
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

    val missedCount = if (uiState.currentTab == XenoTab.CALLS) 0 else uiState.unseenMissedCallsCount
    val unreadSmsCount = remember(uiState.smsThreads) {
        uiState.smsThreads.sumOf { it.unreadCount }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (uiState.activeChatThread == null && uiState.activeSpaceInstanceId == null && !uiState.showMediaManager && uiState.selectedVideoItem == null && !uiState.isBrowserHeaderHidden && uiState.activeCallState == null && !uiState.isBrowserFullscreenVideo) {
                XenoBottomBar(
                    currentTab = uiState.currentTab,
                    onTabSelected = { viewModel.selectTab(it) },
                    missedCallsCount = missedCount,
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
        ) {
            if (uiState.selectedVideoItem != null) {
                VideoPlayerModal(
                    videoItem = uiState.selectedVideoItem!!,
                    isControlsHidden = uiState.isVideoControlsHidden,
                    onToggleHide = { viewModel.setVideoControlsHidden(it) },
                    onClose = { viewModel.selectVideoItem(null) },
                    onDeleteVideo = { viewModel.deleteMediaItem(it) },
                    modifier = Modifier.fillMaxSize()
                )
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
            } else if (uiState.activeCallState != null) {
                ActiveCallScreen(
                    phoneNumber = uiState.activeCallState!!.phoneNumber,
                    contactName = uiState.activeCallState!!.contactName,
                    isIncoming = uiState.activeCallState!!.isIncoming,
                    onAcceptCall = { viewModel.acceptActiveCall() },
                    onDeclineCall = { viewModel.declineActiveCall() },
                    onEndCall = { duration -> viewModel.endActiveCall(duration) },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    // If on main tab and not default dialer, show prompt banner (unless on settings or in chat/camera/workspace or browser full screen)
                    if (uiState.activeChatThread == null && uiState.currentTab != XenoTab.SETTINGS && !uiState.isDefaultDialer && uiState.activeSpaceInstanceId == null && !uiState.isBrowserHeaderHidden) {
                        DefaultAppBanner(
                            isDefaultDialer = uiState.isDefaultDialer,
                            onSetDefaultClick = {
                                TelecomHelper.createRequestDefaultDialerIntent(context)?.let {
                                    defaultDialerLauncher.launch(it)
                                }
                            }
                        )
                    }

                    // Main Content View
                    if (uiState.activeChatThread != null) {
                        ChatScreen(
                            thread = uiState.activeChatThread!!,
                            messages = uiState.activeChatMessages,
                            onBackClick = { viewModel.closeChat() },
                            onSendMessage = { viewModel.sendChatMessage(it) },
                            onDeleteMessage = { viewModel.deleteChatMessage(it) },
                            onDeleteThread = {
                                viewModel.deleteChatThread(uiState.activeChatThread!!)
                            },
                            onStartCall = { num, name -> viewModel.startCall(num, name) }
                        )
                    } else {
                        when (uiState.currentTab) {
                            XenoTab.CALLS -> {
                                CallsScreen(
                                    callLogs = uiState.callLogs,
                                    filter = uiState.callFilter,
                                    onFilterChange = { viewModel.setCallFilter(it) },
                                    onDeleteCall = { viewModel.deleteCallLog(it) },
                                    onClearAll = { viewModel.clearAllCallLogs() },
                                    onClearMissedCalls = { viewModel.clearMissedCallLogs() },
                                    onMarkMissedSeen = { viewModel.markMissedCallsAsSeen() },
                                    onStartSms = { num, name -> viewModel.startChatWithNumber(num, name) },
                                    onOpenContacts = { viewModel.selectTab(XenoTab.CONTACTS) },
                                    onStartCall = { num, name -> viewModel.startCall(num, name) },
                                    onSimulateIncomingCall = { viewModel.simulateIncomingCall() }
                                )
                            }
                            XenoTab.CONTACTS -> {
                                ContactsScreen(
                                    contacts = uiState.contacts,
                                    searchQuery = uiState.contactSearchQuery,
                                    onSearchQueryChange = { viewModel.setContactSearchQuery(it) },
                                    onStartSms = { num, name -> viewModel.startChatWithNumber(num, name) },
                                    onStartCall = { num, name -> viewModel.startCall(num, name) }
                                )
                            }
                            XenoTab.KEYPAD -> {
                                KeypadScreen(
                                    digits = uiState.dialpadDigits,
                                    onDigitClick = { viewModel.appendDialpadDigit(it) },
                                    onBackspace = { viewModel.backspaceDialpad() },
                                    onClear = { viewModel.clearDialpad() },
                                    onSettingsClick = { viewModel.selectTab(XenoTab.SETTINGS) },
                                    contacts = uiState.contacts,
                                    onStartSms = { num, name -> viewModel.startChatWithNumber(num, name) },
                                    onStartCall = { num, name -> viewModel.startCall(num, name) }
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
                                onSimulateCall = { viewModel.simulateIncomingCall() },
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
                                activeSpaceInstance = uiState.activeSpaceInstance,
                                showAddCloneDialog = uiState.showAddCloneDialog,
                                selectedCloneForEdit = uiState.selectedCloneForEdit,
                                onLaunchClone = { viewModel.launchCloneWorkspace(it) },
                                onCloseCloneWorkspace = { viewModel.closeCloneWorkspace() },
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
                                onSavePassword = { domain, title, username, pass ->
                                    viewModel.savePassword(domain, title, username, pass)
                                },
                                onDeletePassword = { viewModel.deletePassword(it) },
                                onPlayVideo = { viewModel.selectVideoItem(it) }
                            )
                        }
                        XenoTab.SETTINGS -> {
                            SettingsScreen(
                                isDefaultDialer = uiState.isDefaultDialer,
                                isDefaultSms = uiState.isDefaultSms,
                                onRequestDefaultDialer = {
                                    TelecomHelper.createRequestDefaultDialerIntent(context)?.let {
                                        defaultDialerLauncher.launch(it)
                                    }
                                },
                                onRequestDefaultSms = {
                                    TelecomHelper.createRequestDefaultSmsIntent(context)?.let {
                                        defaultSmsLauncher.launch(it)
                                    }
                                },
                                onPermissionsUpdated = {
                                    viewModel.loadAllData()
                                    viewModel.checkDefaultAppStatus()
                                },
                                onSimulateSms = { viewModel.simulateIncomingSms() },
                                onSimulateCall = { viewModel.simulateIncomingCall() },
                                preventOverlayApps = uiState.preventOverlayApps,
                                onTogglePreventOverlayApps = { viewModel.setPreventOverlayApps(it) }
                            )
                        }
                    }
                }
            }
        }
    }

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
    }
}
