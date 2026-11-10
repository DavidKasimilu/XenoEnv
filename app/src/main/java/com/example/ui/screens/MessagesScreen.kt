package com.example.ui.screens

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.outlined.NotificationsNone
import com.example.ui.viewmodel.WhatsAppCallState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.models.DeviceNotification
import com.example.data.models.SmsThread
import com.example.ui.components.ContactAvatar
import com.example.ui.components.QuickTogglesPanel
import com.example.ui.viewmodel.MessagesSubTab
import kotlinx.coroutines.launch

@Composable
fun MessagesScreen(
    threads: List<SmsThread>,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onOpenThread: (SmsThread) -> Unit,
    onDeleteThread: (SmsThread) -> Unit,
    onStartNewChat: () -> Unit,
    subTab: MessagesSubTab = MessagesSubTab.SMS,
    onSubTabChange: (MessagesSubTab) -> Unit = {},
    notifications: List<DeviceNotification> = emptyList(),
    hasNotificationAccess: Boolean = false,
    onRequestNotificationAccess: () -> Unit = {},
    onDismissNotification: (String) -> Unit = {},
    onClearAllNotifications: () -> Unit = {},
    onAddSampleAlerts: () -> Unit = {},
    onSimulateAlert: (String) -> Unit = {},
    onOpenAppDetailsSettings: () -> Unit = {},
    onSimulateSms: () -> Unit = {},
    onSimulateCall: () -> Unit = {},
    onReplyNotification: (DeviceNotification, String) -> Unit = { _, _ -> },
    listState: androidx.compose.foundation.lazy.LazyListState = androidx.compose.foundation.lazy.rememberLazyListState(),
    modifier: Modifier = Modifier
) {
    var selectedThreadIds by remember { mutableStateOf(setOf<Long>()) }
    val isSelectionMode = selectedThreadIds.isNotEmpty()

    val filteredThreads = remember(threads, searchQuery) {
        val uniqueThreads = threads.distinctBy { it.threadId.takeIf { id -> id != 0L } ?: it.address }
        if (searchQuery.isBlank()) uniqueThreads
        else {
            val q = searchQuery.trim().lowercase()
            uniqueThreads.filter {
                it.displayName.lowercase().contains(q) ||
                    it.address.contains(q) ||
                    it.snippet.lowercase().contains(q)
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Selection Action Bar
            if (isSelectionMode) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    tonalElevation = 2.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { selectedThreadIds = emptySet() }) {
                            Icon(Icons.Filled.Close, contentDescription = "Cancel selection")
                        }
                        Text(
                            text = "${selectedThreadIds.size} selected",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = {
                            selectedThreadIds.forEach { id ->
                                val thread = threads.find { it.threadId == id }
                                thread?.let { onDeleteThread(it) }
                            }
                            selectedThreadIds = emptySet()
                        }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete selected")
                        }
                    }
                }
            } else {
                // Search Bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .testTag("search_sms_input"),
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = "Search",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { onSearchQueryChange("") }) {
                                Icon(
                                    imageVector = Icons.Filled.Clear,
                                    contentDescription = "Clear search"
                                )
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            if (filteredThreads.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Filled.Message,
                            contentDescription = null,
                            modifier = Modifier.size(56.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (searchQuery.isBlank()) "No SMS conversations on device" else "No matching messages",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Tap the compose button below to start a new chat",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 80.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(
                        items = filteredThreads,
                        key = { _, thread -> "sms_thread_${thread.threadId}_${thread.address}" },
                        contentType = { _, _ -> "sms_thread_row" }
                    ) { _, thread ->
                        val isSelected = thread.threadId in selectedThreadIds
                        SmsThreadItem(
                            thread = thread,
                            isSelected = isSelected,
                            onToggleSelection = {
                                selectedThreadIds = if (isSelected) {
                                    selectedThreadIds - thread.threadId
                                } else {
                                    selectedThreadIds + thread.threadId
                                }
                            },
                            onClick = {
                                if (isSelectionMode) {
                                    selectedThreadIds = if (isSelected) {
                                        selectedThreadIds - thread.threadId
                                    } else {
                                        selectedThreadIds + thread.threadId
                                    }
                                } else {
                                    onOpenThread(thread)
                                }
                            }
                        )
                    }
                }
            }
        }

        // Start Chat FAB
        FloatingActionButton(
            onClick = onStartNewChat,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
                .testTag("start_chat_fab"),
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        ) {
            Icon(
                imageVector = Icons.Filled.Edit,
                contentDescription = "Start new chat"
            )
        }
    }
}

@Composable
fun AppAlertsDrawerOverlay(
    isOpen: Boolean,
    onCloseDrawer: () -> Unit,
    notifications: List<DeviceNotification>,
    hasNotificationAccess: Boolean,
    onRequestNotificationAccess: () -> Unit,
    onDismissNotification: (String) -> Unit,
    onClearAllNotifications: () -> Unit,
    onAddSampleAlerts: () -> Unit = {},
    onSimulateAlert: (String) -> Unit = {},
    onOpenAppDetailsSettings: () -> Unit = {},
    onSimulateSms: () -> Unit = {},
    onSimulateCall: () -> Unit = {},
    incomingWhatsAppCall: WhatsAppCallState? = null,
    onAcceptWhatsAppCall: (WhatsAppCallState) -> Unit = {},
    onDeclineWhatsAppCall: () -> Unit = {},
    onStopFloatingApp: (() -> Unit)? = null,
    onSpeedBoost: () -> Unit = {},
    shape: androidx.compose.ui.graphics.Shape = androidx.compose.foundation.shape.RoundedCornerShape(0.dp),
    modifier: Modifier = Modifier
) {
    val postNotificationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {}

    AnimatedVisibility(
        visible = isOpen,
        enter = slideInVertically(
            initialOffsetY = { fullHeight -> -fullHeight },
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMediumLow
            )
        ) + fadeIn(),
        exit = slideOutVertically(
            targetOffsetY = { fullHeight -> -fullHeight },
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMedium
            )
        ) + fadeOut(),
        modifier = modifier.fillMaxSize()
    ) {
        BackHandler(enabled = isOpen) {
            onCloseDrawer()
        }

        val drawerNestedScrollConnection = remember(onCloseDrawer) {
            object : NestedScrollConnection {
                var accumulatedUpDrag = 0f

                override fun onPostScroll(
                    consumed: Offset,
                    available: Offset,
                    source: NestedScrollSource
                ): Offset {
                    if (available.y < -8f) {
                        accumulatedUpDrag += kotlin.math.abs(available.y)
                        if (accumulatedUpDrag > 60f) {
                            onCloseDrawer()
                            accumulatedUpDrag = 0f
                        }
                    } else if (available.y > 0f) {
                        accumulatedUpDrag = 0f
                    }
                    return Offset.Zero
                }
            }
        }

        Surface(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    var totalDy = 0f
                    detectVerticalDragGestures(
                        onDragStart = { totalDy = 0f },
                        onVerticalDrag = { _, dragAmount ->
                            totalDy += dragAmount
                            if (totalDy < -50f) {
                                onCloseDrawer()
                            }
                        }
                    )
                }
                .testTag("app_alerts_drawer_overlay"),
            color = MaterialTheme.colorScheme.background,
            tonalElevation = 3.dp,
            shape = shape
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Drawer Body with swipe-up-to-dismiss support
                DeviceNotificationsWindow(
                    notifications = notifications,
                    hasNotificationAccess = hasNotificationAccess,
                    onRequestNotificationAccess = onRequestNotificationAccess,
                    onRequestPostNotification = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            postNotificationLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                        }
                    },
                    onDismissNotification = onDismissNotification,
                    onClearAll = onClearAllNotifications,
                    onOpenAppDetailsSettings = onOpenAppDetailsSettings,
                    onSimulateSms = onSimulateSms,
                    onSimulateCall = onSimulateCall,
                    incomingWhatsAppCall = incomingWhatsAppCall,
                    onAcceptWhatsAppCall = onAcceptWhatsAppCall,
                    onDeclineWhatsAppCall = onDeclineWhatsAppCall,
                    onCloseDrawer = onCloseDrawer,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .nestedScroll(drawerNestedScrollConnection)
                )
            }
        }
    }
}

@Composable
fun DeviceNotificationsWindow(
    notifications: List<DeviceNotification>,
    hasNotificationAccess: Boolean,
    onRequestNotificationAccess: () -> Unit,
    onRequestPostNotification: () -> Unit,
    onDismissNotification: (String) -> Unit,
    onClearAll: () -> Unit,
    onAddSampleAlerts: () -> Unit = {},
    onSimulateAlert: (String) -> Unit = {},
    onOpenAppDetailsSettings: () -> Unit = {},
    onSimulateSms: () -> Unit = {},
    onSimulateCall: () -> Unit = {},
    incomingWhatsAppCall: WhatsAppCallState? = null,
    onAcceptWhatsAppCall: (WhatsAppCallState) -> Unit = {},
    onDeclineWhatsAppCall: () -> Unit = {},
    onCloseDrawer: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    val showScrollToTop by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 1 }
    }
    val showScrollToBottom by remember {
        derivedStateOf {
            notifications.size > 2 && listState.firstVisibleItemIndex < notifications.size - 2
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .testTag("device_notifications_window")
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .testTag("notifications_scroll_container"),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            incomingWhatsAppCall?.let { whatsappCall ->
                item(key = "incoming_whatsapp_call_banner") {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("incoming_whatsapp_call_card"),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF25D366)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Phone,
                                        contentDescription = "WhatsApp Call",
                                        tint = Color.White
                                    )
                                }
                                Spacer(modifier = Modifier.width(16.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Incoming WhatsApp ${if (whatsappCall.isVideo) "Video" else "Voice"} Call",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                    Text(
                                        text = whatsappCall.callerName,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                    Text(
                                        text = whatsappCall.callerNumber,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Button(
                                    onClick = onDeclineWhatsAppCall,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.error,
                                        contentColor = MaterialTheme.colorScheme.onError
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("decline_whatsapp_call_button")
                                ) {
                                    Text("Decline")
                                }
                                Button(
                                    onClick = { onAcceptWhatsAppCall(whatsappCall) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFF25D366),
                                        contentColor = Color.White
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("accept_whatsapp_call_button")
                                ) {
                                    Text("Accept")
                                }
                            }
                        }
                    }
                }
            }
            // 1. Quick Toggles
            item(key = "quick_toggles") {
                QuickTogglesPanel(modifier = Modifier.padding(bottom = 4.dp))
            }

            // Permission Notice / Status Banner
            item(key = "permission_status") {
                if (!hasNotificationAccess) {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("notification_permission_card")
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Security,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "Allow Reading Device Notifications",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Android 14 restricts notification access on sideloaded apps. Follow these simple steps:",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.9f)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Step 1: Tap '1. Attempt Permission' below -> Select Xeno (shows 'Restricted setting').\nStep 2: Tap '2. Open App Info' below -> Tap 3 dots (⋮) top-right or scroll to 'Allow restricted settings' & enter PIN.\nStep 3: Return & toggle ON Xeno in Notification Access!",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = { onRequestNotificationAccess() },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("grant_notification_access_btn"),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text("1. Attempt Permission")
                                }
                                OutlinedButton(
                                    onClick = { onOpenAppDetailsSettings() },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("unlock_restricted_settings_btn"),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text("2. Open App Info")
                                }
                            }
                        }
                    }
                }
            }

            // Alerts Items or Clean Empty State
            if (notifications.isEmpty() && incomingWhatsAppCall == null) {
                item(key = "empty_state") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 120.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No active notifications",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )
                    }
                }
            } else {
                itemsIndexed(
                    items = notifications,
                    key = { index, notif -> "dev_notif_${notif.key}_${notif.postTime}_$index" }
                ) { _, notif ->
                    NotificationCard(
                        notification = notif,
                        onDismiss = { onDismissNotification(notif.key) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        // Floating Scroll navigation controls (Scroll to Top / Scroll to Bottom)
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 24.dp, end = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.End
        ) {
            AnimatedVisibility(
                visible = showScrollToTop,
                enter = fadeIn() + slideInVertically { it / 2 },
                exit = fadeOut() + slideOutVertically { it / 2 }
            ) {
                FilledTonalButton(
                    onClick = {
                        coroutineScope.launch {
                            listState.animateScrollToItem(0)
                        }
                    },
                    shape = CircleShape,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    modifier = Modifier.testTag("scroll_to_top_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.KeyboardArrowUp,
                        contentDescription = "Scroll to top",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Top", style = MaterialTheme.typography.labelMedium)
                }
            }

            AnimatedVisibility(
                visible = showScrollToBottom,
                enter = fadeIn() + slideInVertically { it / 2 },
                exit = fadeOut() + slideOutVertically { it / 2 }
            ) {
                FilledTonalButton(
                    onClick = {
                        coroutineScope.launch {
                            val count = listState.layoutInfo.totalItemsCount
                            if (count > 0) {
                                listState.animateScrollToItem(count - 1)
                            }
                        }
                    },
                    shape = CircleShape,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    modifier = Modifier.testTag("scroll_to_bottom_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.KeyboardArrowDown,
                        contentDescription = "Scroll to bottom",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Bottom", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
fun NotificationCard(
    notification: DeviceNotification,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val appColor = remember(notification.appColorHex) {
        com.example.ui.components.parseHexColor(notification.appColorHex)
    }

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = modifier
            .fillMaxWidth()
            .testTag("notification_card_${notification.key}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = appColor.copy(alpha = 0.15f),
                        modifier = Modifier.size(24.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Filled.Notifications,
                                contentDescription = null,
                                tint = appColor,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }

                    Text(
                        text = notification.appName,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = appColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Text(
                        text = "• ${notification.formattedTime}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (notification.isClearable) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Dismiss notification",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = notification.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )

            if (notification.text.isNotBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = notification.text,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun SmsThreadItem(
    thread: SmsThread,
    isSelected: Boolean,
    onToggleSelection: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                detectTapGestures(
                    onLongPress = { onToggleSelection() },
                    onTap = { onClick() }
                )
            }
            .testTag("sms_thread_${thread.threadId}"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (isSelected) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(46.dp),
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("✓", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    }
                }
            } else {
                ContactAvatar(nameOrNumber = thread.displayName, size = 46.dp)
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = thread.displayName,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (!thread.read) FontWeight.Bold else FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = thread.formattedDate,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (!thread.read) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (!thread.read) FontWeight.Bold else FontWeight.Normal
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = thread.snippet,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (!thread.read) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (!thread.read) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    if (thread.unreadCount > 0 && !isSelected) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 8.dp)
                        ) {
                            Text(
                                text = thread.unreadCount.toString(),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
