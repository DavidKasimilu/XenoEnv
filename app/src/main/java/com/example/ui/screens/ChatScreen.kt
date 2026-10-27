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
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val systemClipboard = remember(context) {
        context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    }

    var inputText by remember { mutableStateOf("") }
    var showMenu by remember { mutableStateOf(false) }
    var selectedMessageForAction by remember { mutableStateOf<SmsMessage?>(null) }
    var showDeleteThreadDialog by remember { mutableStateOf(false) }
    var callNumberTarget by remember { mutableStateOf<String?>(null) }
    var availableSims by remember { mutableStateOf<List<SimCardInfo>>(emptyList()) }

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

    // Calculate landing index directly to recent message
    val recentStartIndex = remember(messages.size, thread.threadId) {
        if (messages.isEmpty()) 0
        else {
            val unreadIdx = messages.indexOfFirst { !it.read && !it.isSent }
            if (unreadIdx != -1) unreadIdx else (messages.size - 1).coerceAtLeast(0)
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
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = thread.address,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                                Icon(
                                    imageVector = Icons.Outlined.ContentCopy,
                                    contentDescription = "Copy number",
                                    modifier = Modifier.size(12.dp),
                                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.75f)
                                )
                            }
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
                    IconButton(
                        onClick = { copyToClipboard("Phone number", thread.address) },
                        modifier = Modifier.testTag("chat_copy_number_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ContentCopy,
                            contentDescription = "Copy phone number",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = { placeCall(thread.address) },
                        modifier = Modifier.testTag("chat_call_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Call,
                            contentDescription = "Call ${thread.displayName}",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
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

            val recentDividerIndex = remember(messages.size, thread.threadId) {
                val unreadIdx = messages.indexOfFirst { !it.read && !it.isSent }
                if (unreadIdx != -1) unreadIdx else if (messages.size > 1) messages.size - 1 else -1
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
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                    thickness = 1.dp
                                )
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                                    modifier = Modifier.padding(horizontal = 8.dp)
                                ) {
                                    Text(
                                        text = "Recent",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                                    )
                                }
                                HorizontalDivider(
                                    modifier = Modifier.weight(1f),
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                    thickness = 1.dp
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            MessageBubble(
                                message = msg,
                                isCopied = isRecentlyCopied,
                                onCopyClick = {
                                    copyToClipboard("Message", msg.body, copiedMsgId = msg.id)
                                },
                                onRetryClick = {
                                    onSendMessage(msg.body)
                                },
                                onClick = { selectedMessageForAction = msg },
                                onLongClick = { selectedMessageForAction = msg }
                            )
                        }
                    } else {
                        MessageBubble(
                            message = msg,
                            isCopied = isRecentlyCopied,
                            onCopyClick = {
                                copyToClipboard("Message", msg.body, copiedMsgId = msg.id)
                            },
                            onRetryClick = {
                                onSendMessage(msg.body)
                            },
                            onClick = { selectedMessageForAction = msg },
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
                                            text = "Sending SMS to ${thread.displayName}...",
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
                                            text = "SMS sent to carrier • Awaiting delivery report...",
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
                                            text = "SMS delivered to ${thread.displayName} (${outgoing.formattedTime})",
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
                                            text = "SMS delivery failed • Tap to retry",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = contentColor,
                                            modifier = Modifier.clickable {
                                                onSendMessage(outgoing.body)
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
                                onSendMessage(messageToSend)
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
                        Text(
                            text = if (msg.isSent) {
                                "Status: ${msg.deliveryStatus.label} • ${msg.formattedTime}"
                            } else {
                                "Received • ${msg.formattedTime}"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
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
    isCopied: Boolean,
    onCopyClick: () -> Unit,
    onRetryClick: () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val isSent = message.isSent
    val bubbleContainerColor = if (isSent) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val bubbleTextColor = if (isSent) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
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
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Inline Quick Copy Button on every message bubble
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable(onClick = onCopyClick)
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                            .testTag("message_copy_button_${message.id}")
                    ) {
                        Icon(
                            imageVector = if (isCopied) Icons.Filled.Check else Icons.Outlined.ContentCopy,
                            contentDescription = "Copy message text",
                            modifier = Modifier.size(12.dp),
                            tint = if (isCopied) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                bubbleTextColor.copy(alpha = 0.65f)
                            }
                        )
                        Text(
                            text = if (isCopied) "Copied" else "Copy",
                            fontSize = 10.sp,
                            fontWeight = if (isCopied) FontWeight.Bold else FontWeight.Normal,
                            color = if (isCopied) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                bubbleTextColor.copy(alpha = 0.65f)
                            }
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Timestamp + Outgoing SMS Delivery Status Feedback
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = message.formattedTime,
                            fontSize = 10.sp,
                            color = bubbleTextColor.copy(alpha = 0.7f)
                        )
                        if (isSent) {
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
