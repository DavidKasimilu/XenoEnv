package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.models.SmsDeliveryStatus
import com.example.data.models.SmsMessage
import com.example.data.models.SmsThread
import com.example.telecom.SimCardInfo
import com.example.telecom.TelecomHelper
import com.example.ui.components.ContactAvatar
import com.example.ui.components.SimSelectionDialog
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    thread: SmsThread,
    messages: List<SmsMessage>,
    onBackClick: () -> Unit,
    onSendMessage: (String) -> Unit,
    onDeleteMessage: (Long) -> Unit,
    onDeleteThread: () -> Unit,
    onStartCall: ((String, String?) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    ChatScreen(
        thread = thread,
        messages = messages,
        onBackClick = onBackClick,
        onSendMessage = { text, _ -> onSendMessage(text) },
        onDeleteMessage = onDeleteMessage,
        onDeleteThread = onDeleteThread,
        onStartCall = onStartCall,
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    thread: SmsThread,
    messages: List<SmsMessage>,
    onBackClick: () -> Unit,
    onSendMessage: (String, SimCardInfo?) -> Unit,
    onDeleteMessage: (Long) -> Unit,
    onDeleteThread: () -> Unit,
    onStartCall: ((String, String?) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val systemClipboard = remember(context) {
        context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    }

    val prefs = remember(context) {
        context.getSharedPreferences("xeno_sim_prefs", Context.MODE_PRIVATE)
    }
    var selectedSimSubId by remember {
        mutableStateOf(prefs.getInt("default_sms_sub_id", -1))
    }
    var showSimPickerMenu by remember { mutableStateOf(false) }

    var inputText by remember { mutableStateOf("") }
    var showMenu by remember { mutableStateOf(false) }
    var selectedMessageForAction by remember { mutableStateOf<SmsMessage?>(null) }
    var showDeleteThreadDialog by remember { mutableStateOf(false) }
    var callNumberTarget by remember { mutableStateOf<String?>(null) }
    var availableSims by remember { mutableStateOf<List<SimCardInfo>>(emptyList()) }

    LaunchedEffect(Unit) {
        availableSims = TelecomHelper.getAvailableSimCards(context)
    }

    val activeSim = remember(selectedSimSubId, availableSims) {
        if (selectedSimSubId != -1) {
            availableSims.find { it.subscriptionId == selectedSimSubId } ?: availableSims.firstOrNull()
        } else {
            availableSims.firstOrNull()
        }
    }

    // Clipboard & Delivery Feedback UI state
    var clipboardFeedbackText by remember { mutableStateOf<String?>(null) }
    var lastCopiedMessageId by remember { mutableLongStateOf(-1L) }
    var clipboardPreviewText by remember { mutableStateOf<String?>(null) }
    var dismissedClipboardPreview by remember { mutableStateOf<String?>(null) }

    // Track active delivery feedback banner for newly sent SMS messages
    var trackedOutgoingMessageId by remember { mutableLongStateOf(-1L) }
    var showDeliveryBanner by remember { mutableStateOf(false) }

    fun readClipboardText(): String? {
        val composeClip = clipboardManager.getText()?.text?.takeIf { it.isNotBlank() }
        if (composeClip != null) return composeClip
        val primaryClip = systemClipboard?.primaryClip
        if (primaryClip != null && primaryClip.itemCount > 0) {
            val text = primaryClip.getItemAt(0)?.coerceToText(context)?.toString()
            if (!text.isNullOrBlank()) return text
        }
        return null
    }

    fun copyToClipboard(label: String, text: String, copiedMsgId: Long = -1L) {
        clipboardManager.setText(AnnotatedString(text))
        systemClipboard?.setPrimaryClip(ClipData.newPlainText(label, text))
        clipboardPreviewText = text
        dismissedClipboardPreview = null
        lastCopiedMessageId = copiedMsgId
        clipboardFeedbackText = "$label copied to clipboard"
    }

    // Refresh clipboard preview on entry or when clipboard changes
    LaunchedEffect(Unit) {
        clipboardPreviewText = readClipboardText()
    }

    // Auto-clear clipboard feedback badge after 2.4s
    LaunchedEffect(clipboardFeedbackText) {
        if (clipboardFeedbackText != null) {
            delay(2400)
            clipboardFeedbackText = null
            lastCopiedMessageId = -1L
        }
    }

    val trackedOutgoingMessage = remember(messages, trackedOutgoingMessageId) {
        messages.find { it.id == trackedOutgoingMessageId }
            ?: messages.lastOrNull { it.isSent && it.deliveryStatus != SmsDeliveryStatus.DELIVERED }
    }

    LaunchedEffect(trackedOutgoingMessage?.id, trackedOutgoingMessage?.deliveryStatus) {
        val msg = trackedOutgoingMessage ?: return@LaunchedEffect
        showDeliveryBanner = true
        if (msg.deliveryStatus == SmsDeliveryStatus.DELIVERED) {
            delay(3200)
            if (trackedOutgoingMessageId == msg.id) {
                showDeliveryBanner = false
            }
        }
    }

    fun placeCall(number: String) {
        val clean = number.trim()
        if (TelecomHelper.isUssdCode(clean)) {
            TelecomHelper.makeCall(context, clean)
            return
        }
        if (onStartCall != null) {
            onStartCall(number, thread.displayName)
            return
        }
        val sims = TelecomHelper.getAvailableSimCards(context)
        if (sims.size > 1) {
            callNumberTarget = number
            availableSims = sims
        } else {
            TelecomHelper.makeCallWithSim(context, number, sims.firstOrNull()?.phoneAccountHandle)
        }
    }

    // Calculate landing index directly to first unread / recent message
    val recentStartIndex = remember(messages.size, thread.threadId, thread.unreadCount) {
        if (messages.isEmpty()) 0
        else {
            val firstUnread = messages.indexOfFirst { !it.read && !it.isSent }
            when {
                firstUnread != -1 -> firstUnread
                thread.unreadCount > 0 && thread.unreadCount <= messages.size -> messages.size - thread.unreadCount
                else -> (messages.size - 1).coerceAtLeast(0)
            }
        }
    }

    // Instantly initialized at recentStartIndex without starting at index 0 or scrolling from top
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = recentStartIndex)

    BackHandler {
        onBackClick()
    }

    // Ensure state stays locked to recent message on thread or size changes
    LaunchedEffect(messages.size, thread.threadId) {
        if (messages.isNotEmpty()) {
            val target = if (recentStartIndex in messages.indices) recentStartIndex else (messages.size - 1).coerceAtLeast(0)
            listState.scrollToItem(target)
            val newest = messages.lastOrNull()
            if (newest != null && newest.isSent && (System.currentTimeMillis() - newest.date) < 5000L) {
                trackedOutgoingMessageId = newest.id
                showDeliveryBanner = true
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag("chat_screen")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
        ) {
            // Chat Top Bar
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.clickable {
                            copyToClipboard("Phone number", thread.address)
                        }
                    ) {
                        ContactAvatar(nameOrNumber = thread.displayName, size = 40.dp)
                        Column {
                            Text(
                                text = thread.displayName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                            Text(
                                text = thread.address,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier.testTag("chat_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to threads"
                        )
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(
                                imageVector = Icons.Filled.MoreVert,
                                contentDescription = "More options"
                            )
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Copy phone number") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Outlined.ContentCopy,
                                        contentDescription = null
                                    )
                                },
                                onClick = {
                                    showMenu = false
                                    copyToClipboard("Phone number", thread.address)
                                }
                            )
                            if (messages.isNotEmpty()) {
                                DropdownMenuItem(
                                    text = { Text("Copy chat transcript") },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Outlined.ContentCopy,
                                            contentDescription = null
                                        )
                                    },
                                    onClick = {
                                        showMenu = false
                                        val transcript = messages.joinToString("\n") { m ->
                                            val sender = if (m.isSent) "Me" else thread.displayName
                                            "[$sender - ${m.formattedTime}]: ${m.body}"
                                        }
                                        copyToClipboard("Chat transcript", transcript)
                                    }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Delete conversation") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Filled.Delete,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                },
                                onClick = {
                                    showMenu = false
                                    showDeleteThreadDialog = true
                                }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )

            HorizontalDivider()

            // Animated Clipboard Feedback Banner
            AnimatedVisibility(
                visible = clipboardFeedbackText != null,
                enter = slideInVertically() + fadeIn(),
                exit = slideOutVertically() + fadeOut()
            ) {
                clipboardFeedbackText?.let { feedback ->
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("clipboard_feedback_banner")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.CheckCircle,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = feedback,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }
            }

            val recentDividerIndex = remember(messages.size, thread.threadId, thread.unreadCount) {
                val firstUnread = messages.indexOfFirst { !it.read && !it.isSent }
                when {
                    firstUnread != -1 -> firstUnread
                    thread.unreadCount > 0 && thread.unreadCount <= messages.size -> messages.size - thread.unreadCount
                    else -> -1
                }
            }

            // Message bubbles list
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(
                    items = messages,
                    key = { index, msg -> "chat_msg_${msg.id}_${msg.date}_$index" },
                    contentType = { index, _ -> if (index == recentDividerIndex && recentDividerIndex != -1) "divider_bubble" else "message_bubble" }
                ) { index, msg ->
                    val isRecentlyCopied = lastCopiedMessageId == msg.id
                    if (index == recentDividerIndex && recentDividerIndex != -1) {
                        Column {
                            // Line break saying "Recent" separating already viewed messages from recent message
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 10.dp)
                                    .testTag("recent_messages_divider"),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                HorizontalDivider(
                                    modifier = Modifier.weight(1f),
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                                    thickness = 1.dp
                                )
                                Text(
                                    text = "Recent",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 12.dp)
                                )
                                HorizontalDivider(
                                    modifier = Modifier.weight(1f),
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                                    thickness = 1.dp
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            MessageBubble(
                                message = msg,
                                availableSims = availableSims,
                                onRetryClick = {
                                    onSendMessage(msg.body, activeSim)
                                },
                                onClick = {},
                                onLongClick = { selectedMessageForAction = msg }
                            )
                        }
                    } else {
                        MessageBubble(
                            message = msg,
                            availableSims = availableSims,
                            onRetryClick = {
                                onSendMessage(msg.body, activeSim)
                            },
                            onClick = {},
                            onLongClick = { selectedMessageForAction = msg }
                        )
                    }
                }
            }

            // Live SMS Delivery Feedback Banner above composer
            AnimatedVisibility(
                visible = showDeliveryBanner && trackedOutgoingMessage != null,
                enter = slideInVertically(initialOffsetY = { it / 2 }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it / 2 }) + fadeOut()
            ) {
                trackedOutgoingMessage?.let { outgoing ->
                    val status = outgoing.deliveryStatus
                    val bannerColor = when (status) {
                        SmsDeliveryStatus.SENDING -> MaterialTheme.colorScheme.surfaceVariant
                        SmsDeliveryStatus.SENT -> MaterialTheme.colorScheme.secondaryContainer
                        SmsDeliveryStatus.DELIVERED -> MaterialTheme.colorScheme.primaryContainer
                        SmsDeliveryStatus.FAILED -> MaterialTheme.colorScheme.errorContainer
                    }
                    val contentColor = when (status) {
                        SmsDeliveryStatus.SENDING -> MaterialTheme.colorScheme.onSurfaceVariant
                        SmsDeliveryStatus.SENT -> MaterialTheme.colorScheme.onSecondaryContainer
                        SmsDeliveryStatus.DELIVERED -> MaterialTheme.colorScheme.onPrimaryContainer
                        SmsDeliveryStatus.FAILED -> MaterialTheme.colorScheme.onErrorContainer
                    }

                    Surface(
                        color = bannerColor,
                        tonalElevation = 3.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("sms_delivery_feedback_banner")
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
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                when (status) {
                                    SmsDeliveryStatus.SENDING -> {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(14.dp),
                                            strokeWidth = 2.dp,
                                            color = contentColor
                                        )
                                        Text(
                                            text = "Sending...",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Medium,
                                            color = contentColor
                                        )
                                    }
                                    SmsDeliveryStatus.SENT -> {
                                        Icon(
                                            imageVector = Icons.Filled.Check,
                                            contentDescription = "Sent",
                                            modifier = Modifier.size(16.dp),
                                            tint = contentColor
                                        )
                                        Text(
                                            text = "Sent",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Medium,
                                            color = contentColor
                                        )
                                    }
                                    SmsDeliveryStatus.DELIVERED -> {
                                        Icon(
                                            imageVector = Icons.Filled.DoneAll,
                                            contentDescription = "Delivered",
                                            modifier = Modifier.size(16.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = "Delivered (${outgoing.formattedTime})",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = contentColor
                                        )
                                    }
                                    SmsDeliveryStatus.FAILED -> {
                                        Icon(
                                            imageVector = Icons.Filled.ErrorOutline,
                                            contentDescription = "Failed",
                                            modifier = Modifier.size(16.dp),
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                        Text(
                                            text = "Failed to send • Tap to retry",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = contentColor,
                                            modifier = Modifier.clickable {
                                                onSendMessage(outgoing.body, activeSim)
                                            }
                                        )
                                    }
                                }
                            }
                            IconButton(
                                onClick = { showDeliveryBanner = false },
                                modifier = Modifier.size(22.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = "Dismiss delivery status",
                                    modifier = Modifier.size(14.dp),
                                    tint = contentColor.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                }
            }

            // Bottom Composer Bar
            Surface(
                tonalElevation = 2.dp,
                color = MaterialTheme.colorScheme.surface
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("chat_input_field"),
                        placeholder = { Text("Text message (SMS)...") },
                        shape = RoundedCornerShape(24.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                        ),
                        maxLines = 4
                    )

                    // Select SIM for SMS icon next to the send icon
                    Box {
                        IconButton(
                            onClick = { showSimPickerMenu = true },
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .testTag("chat_select_sim_button")
                        ) {
                            val activeIndex = if (activeSim != null) (availableSims.indexOf(activeSim).coerceAtLeast(0) + 1) else 1
                            val activeLabel = activeSim?.carrierName?.takeIf { it.isNotBlank() && !it.startsWith("SIM ") } ?: "SIM $activeIndex"
                            Icon(
                                imageVector = Icons.Filled.SimCard,
                                contentDescription = "Select SIM for SMS ($activeLabel)",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        // Dropdown menu to select default SIM for SMS
                        DropdownMenu(
                            expanded = showSimPickerMenu,
                            onDismissRequest = { showSimPickerMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = "Select Default SIM for SMS",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                },
                                onClick = {},
                                enabled = false
                            )
                            HorizontalDivider()
                            val simList = if (availableSims.isNotEmpty()) availableSims else listOf(
                                SimCardInfo(
                                    phoneAccountHandle = null,
                                    carrierName = "SIM 1",
                                    subscriptionId = 0
                                )
                            )
                            simList.forEachIndexed { idx, sim ->
                                val isSelected = (activeSim?.subscriptionId == sim.subscriptionId)
                                DropdownMenuItem(
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Filled.SimCard,
                                            contentDescription = null,
                                            tint = if (isSelected) Color(0xFFFF4081) else Color.Gray,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    },
                                    trailingIcon = {
                                        if (isSelected) {
                                            Icon(
                                                imageVector = Icons.Filled.Check,
                                                contentDescription = "Selected",
                                                tint = Color(0xFFFF4081),
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    },
                                    text = {
                                        Column {
                                            Text(
                                                text = "SIM ${idx + 1}: ${sim.carrierName}",
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                            )
                                            val phone = sim.phoneNumber?.takeIf { it.isNotBlank() }
                                            if (phone != null) {
                                                Text(
                                                    text = phone,
                                                    fontSize = 11.sp,
                                                    color = Color.Gray
                                                )
                                            }
                                        }
                                    },
                                    onClick = {
                                        selectedSimSubId = sim.subscriptionId
                                        prefs.edit().putInt("default_sms_sub_id", sim.subscriptionId).apply()
                                        showSimPickerMenu = false
                                    }
                                )
                            }
                        }
                    }

                    Surface(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .testTag("chat_send_button"),
                        color = if (inputText.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        onClick = {
                            if (inputText.isNotBlank()) {
                                val messageToSend = inputText
                                inputText = ""
                                showDeliveryBanner = true
                                onSendMessage(messageToSend, activeSim)
                            }
                        }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Send message",
                                tint = if (inputText.isNotBlank()) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // Message Action Modal (Copy / Quote to Composer / Delete)
        selectedMessageForAction?.let { msg ->
            AlertDialog(
                onDismissRequest = { selectedMessageForAction = null },
                title = { Text("Message Options") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SelectionContainer {
                            Text(
                                text = msg.body,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        val simName = msg.simName?.takeIf { it.isNotBlank() }
                            ?: availableSims.find { it.subscriptionId == msg.subId }?.carrierName
                            ?: availableSims.firstOrNull()?.carrierName
                            ?: "SIM"
                        Text(
                            text = if (msg.isSent) {
                                "Sent via $simName • ${msg.deliveryStatus.label} • ${msg.formattedTime}"
                            } else {
                                "Received via $simName • ${msg.formattedTime}"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFFF4081),
                            fontWeight = FontWeight.Medium
                        )
                    }
                },
                confirmButton = {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(
                            onClick = {
                                inputText = if (inputText.isBlank()) msg.body else "$inputText ${msg.body}"
                                selectedMessageForAction = null
                                clipboardFeedbackText = "Inserted into message box"
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Filled.ContentPaste,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Paste to Input")
                        }
                        TextButton(
                            onClick = {
                                copyToClipboard("Message", msg.body, copiedMsgId = msg.id)
                                selectedMessageForAction = null
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.ContentCopy,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Copy")
                        }
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            onDeleteMessage(msg.id)
                            selectedMessageForAction = null
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                }
            )
        }

        // Delete Thread Dialog
        if (showDeleteThreadDialog) {
            AlertDialog(
                onDismissRequest = { showDeleteThreadDialog = false },
                title = { Text("Delete Conversation") },
                text = { Text("Are you sure you want to delete this entire chat conversation?") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showDeleteThreadDialog = false
                            onDeleteThread()
                        }
                    ) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteThreadDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        callNumberTarget?.let { num ->
            SimSelectionDialog(
                phoneNumber = num,
                simCards = availableSims,
                onSimSelected = { sim ->
                    callNumberTarget = null
                    TelecomHelper.makeCallWithSim(context, num, sim.phoneAccountHandle)
                },
                onDismiss = { callNumberTarget = null }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(
    message: SmsMessage,
    availableSims: List<SimCardInfo>,
    onRetryClick: () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val isSent = message.isSent
    val bubbleContainerColor = if (isSent) {
        Color(0xFF1E3A2F) // Deep refined emerald slate
    } else {
        Color(0xFF222328) // Deep elegant dark charcoal
    }
    val bubbleTextColor = if (isSent) {
        Color(0xFFF1F8F5)
    } else {
        Color(0xFFEDEDF2)
    }
    val bubbleBorderColor = if (isSent) {
        Color(0xFF2E533E)
    } else {
        Color(0xFF33333C)
    }

    val displaySimName = remember(message.simName, message.subId, availableSims) {
        message.simName?.takeIf { it.isNotBlank() }
            ?: availableSims.find { it.subscriptionId == message.subId }?.carrierName
            ?: availableSims.firstOrNull()?.carrierName
            ?: "SIM"
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isSent) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isSent) 16.dp else 4.dp,
                bottomEnd = if (isSent) 4.dp else 16.dp
            ),
            color = bubbleContainerColor,
            border = BorderStroke(1.dp, bubbleBorderColor),
            modifier = Modifier
                .widthIn(min = 110.dp, max = 295.dp)
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick
                )
                .testTag("message_bubble_${message.id}")
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                SelectionContainer {
                    Text(
                        text = message.body,
                        style = MaterialTheme.typography.bodyMedium,
                        color = bubbleTextColor
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End
                ) {
                    Text(
                        text = displaySimName,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFFFF4081)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "•",
                        fontSize = 10.sp,
                        color = bubbleTextColor.copy(alpha = 0.45f)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = message.formattedTime,
                        fontSize = 10.sp,
                        color = bubbleTextColor.copy(alpha = 0.7f)
                    )
                    if (isSent) {
                        Spacer(modifier = Modifier.width(4.dp))
                        DeliveryStatusBadge(
                            status = message.deliveryStatus,
                            baseColor = bubbleTextColor,
                            onRetryClick = onRetryClick
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DeliveryStatusBadge(
    status: SmsDeliveryStatus,
    baseColor: Color,
    onRetryClick: () -> Unit
) {
    when (status) {
        SmsDeliveryStatus.SENDING -> {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                modifier = Modifier.testTag("delivery_status_sending")
            ) {
                Icon(
                    imageVector = Icons.Filled.Schedule,
                    contentDescription = "Sending",
                    modifier = Modifier.size(12.dp),
                    tint = baseColor.copy(alpha = 0.75f)
                )
                Text(
                    text = "Sending...",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    color = baseColor.copy(alpha = 0.75f)
                )
            }
        }
        SmsDeliveryStatus.SENT -> {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.testTag("delivery_status_sent")
            ) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = "Sent",
                    modifier = Modifier.size(13.dp),
                    tint = baseColor.copy(alpha = 0.85f)
                )
                Text(
                    text = "Sent",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    color = baseColor.copy(alpha = 0.85f)
                )
            }
        }
        SmsDeliveryStatus.DELIVERED -> {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.testTag("delivery_status_delivered")
            ) {
                Icon(
                    imageVector = Icons.Filled.DoneAll,
                    contentDescription = "Delivered",
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Delivered",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
        SmsDeliveryStatus.FAILED -> {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier
                    .clickable(onClick = onRetryClick)
                    .testTag("delivery_status_failed")
            ) {
                Icon(
                    imageVector = Icons.Filled.ErrorOutline,
                    contentDescription = "Delivery Failed",
                    modifier = Modifier.size(13.dp),
                    tint = MaterialTheme.colorScheme.error
                )
                Text(
                    text = "Failed • Retry",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
