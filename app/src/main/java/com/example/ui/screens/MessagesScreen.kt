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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.ui.unit.dp
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
    modifier: Modifier = Modifier
) {
    var threadToDelete by remember { mutableStateOf<SmsThread?>(null) }

    val filteredThreads = remember(threads, searchQuery) {
        if (searchQuery.isBlank()) threads
        else {
            val q = searchQuery.trim().lowercase()
            threads.filter {
                it.displayName.lowercase().contains(q) ||
                    it.address.contains(q) ||
                    it.snippet.lowercase().contains(q)
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("messages_screen")
    ) {
        // Default SMS Messages Content (Swipe down anywhere to open the App Alerts Drawer)
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Search Bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .testTag("search_sms_input"),
                    placeholder = { Text("Search messages or senders...") },
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
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 80.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(
                            items = filteredThreads,
                            key = { index, thread -> "sms_thread_${thread.threadId}_${thread.address}_$index" },
                            contentType = { _, _ -> "sms_thread_row" }
                        ) { _, thread ->
                            SmsThreadItem(
                                thread = thread,
                                onClick = { onOpenThread(thread) },
                                onDeleteClick = { threadToDelete = thread }
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

    // Delete Thread Confirmation Dialog
    threadToDelete?.let { thread ->
        AlertDialog(
            onDismissRequest = { threadToDelete = null },
            title = { Text("Delete Conversation") },
            text = {
                Text("Delete conversation with ${thread.displayName}? This will remove all messages in this thread.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteThread(thread)
                        threadToDelete = null
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { threadToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

/**
 * Global Swipe-Down App Alerts Drawer that can be pulled down from any screen in the app
 * (Messages, Calls, Dialer, Contacts, Clone Space, Browser, Settings, and inside any Cloned App)
 * and closed via the small 'X' button.
 */
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

        Surface(
            modifier = Modifier
                .fillMaxSize()
                .testTag("app_alerts_drawer_overlay"),
            color = MaterialTheme.colorScheme.background,
            tonalElevation = 3.dp
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Top Drawer Bar with Title and Small 'X' Button to Draw Back
                Surface(
                    tonalElevation = 2.dp,
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .pointerInput(Unit) {
                            var totalDy = 0f
                            detectVerticalDragGestures(
                                onDragStart = { totalDy = 0f },
                                onVerticalDrag = { _, dragAmount ->
                                    totalDy += dragAmount
                                    if (totalDy < -28.dp.toPx()) {
                                        onCloseDrawer()
                                    }
                                }
                            )
                        }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            BadgedBox(
                                badge = {
                                    if (notifications.isNotEmpty()) {
                                        Badge(containerColor = MaterialTheme.colorScheme.primary) {
                                            Text(text = notifications.size.toString())
                                        }
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.NotificationsActive,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Text(
                                text = "App Alerts",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        // Small 'X' button to draw the App Alerts drawer back up
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .clickable { onCloseDrawer() }
                                .testTag("close_app_alerts_drawer_btn")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = "Close App Alerts drawer",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                // Existing App Alerts Drawer Body (Quick Toggles + Device & App Notifications)
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
                    onAddSampleAlerts = onAddSampleAlerts,
                    onSimulateAlert = onSimulateAlert,
                    onOpenAppDetailsSettings = onOpenAppDetailsSettings,
                    onSimulateSms = onSimulateSms,
                    onSimulateCall = onSimulateCall,
                    onCloseDrawer = onCloseDrawer,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                )

                // Compact Bottom Handle Bar with Small 'X' to Draw Back from Bottom as well
                Surface(
                    tonalElevation = 2.dp,
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .pointerInput(Unit) {
                            var totalDy = 0f
                            detectVerticalDragGestures(
                                onDragStart = { totalDy = 0f },
                                onVerticalDrag = { _, dragAmount ->
                                    totalDy += dragAmount
                                    if (totalDy < -24.dp.toPx()) {
                                        onCloseDrawer()
                                    }
                                }
                            )
                        }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .clickable { onCloseDrawer() }
                                .testTag("close_app_alerts_drawer_bottom_btn")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = "Draw back App Alerts",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }
                    }
                }
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
            // 1. Quick Toggles
            item(key = "quick_toggles") {
                QuickTogglesPanel(modifier = Modifier.padding(bottom = 4.dp))
            }

            // 2. Permission Notice / Status Banner
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
                                text = "Android 14 restricts notification access on sideloaded apps. Follow these 3 simple steps:",
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

            // Clean Header Bar with Total Count and Clear All
            item(key = "alerts_header_bar") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Device & App Notifications",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (notifications.isNotEmpty()) "${notifications.size} active notifications" else "No active notifications",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (notifications.isNotEmpty()) {
                        IconButton(
                            onClick = onClearAll,
                            modifier = Modifier.testTag("clear_all_notifications_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.ClearAll,
                                contentDescription = "Clear all notifications",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Alerts Items or Clean Empty State
            if (notifications.isEmpty()) {
                item(key = "empty_state") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Outlined.NotificationsNone,
                                contentDescription = null,
                                modifier = Modifier.size(56.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "No active notifications",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Incoming notifications from the device and your installed apps will appear here",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
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
    onClick: () -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("sms_thread_${thread.threadId}"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
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
            ContactAvatar(nameOrNumber = thread.displayName, size = 46.dp)

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

                    if (thread.unreadCount > 0) {
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

            IconButton(
                onClick = onDeleteClick,
                modifier = Modifier
                    .size(36.dp)
                    .testTag("delete_thread_${thread.threadId}")
            ) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = "Delete thread",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
