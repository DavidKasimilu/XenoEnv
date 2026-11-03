package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.models.IncomingCallPopup
import com.example.data.models.IncomingSmsPopup
import com.example.telecom.OngoingCallHelper
import kotlinx.coroutines.delay

@Composable
fun InAppNotificationOverlay(
    incomingSms: IncomingSmsPopup?,
    incomingCall: IncomingCallPopup?,
    onMarkSmsRead: (IncomingSmsPopup) -> Unit,
    onReplySms: (IncomingSmsPopup) -> Unit,
    onDismissSms: () -> Unit,
    onAcceptCall: (IncomingCallPopup) -> Unit,
    onDeclineCall: (IncomingCallPopup) -> Unit,
    onDismissCall: () -> Unit,
    onExpandCall: (IncomingCallPopup) -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 14.dp, vertical = 6.dp)
            .testTag("in_app_notification_overlay"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 1. Incoming / In-Call Notification Banner (Screenshot 5)
        AnimatedVisibility(
            visible = incomingCall != null,
            enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut()
        ) {
            incomingCall?.let { call ->
                InAppCallNotificationCard(
                    popup = call,
                    onAnswer = { onAcceptCall(call) },
                    onHangUp = { onDeclineCall(call) },
                    onExpand = { onExpandCall(call) }
                )
            }
        }

        // 2. Incoming SMS Notification Banner (Screenshots 1 & 8)
        AnimatedVisibility(
            visible = incomingSms != null && incomingCall == null,
            enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut()
        ) {
            incomingSms?.let { sms ->
                InAppSmsNotificationCard(
                    popup = sms,
                    onOpenLink = { onReplySms(sms) },
                    onMarkRead = { onMarkSmsRead(sms) },
                    onDismiss = onDismissSms
                )
            }
        }
    }
}

/**
 * Call notification matching Screenshot 5:
 * Avatar with phone badge, "Name • Phone", "Calling via Airtel", and Hang Up / Speaker / Mute action pills.
 */
@Composable
fun InAppCallNotificationCard(
    popup: IncomingCallPopup,
    onAnswer: () -> Unit,
    onHangUp: () -> Unit,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val displayName = popup.callerName?.takeIf { it.isNotBlank() } ?: popup.callerNumber
    val initial = displayName.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "I"
    
    val simCarrier = remember(context, popup.carrierName) {
        popup.carrierName?.takeIf { it.isNotBlank() }
            ?: com.example.telecom.TelecomHelper.getAvailableSimCards(context).firstOrNull()?.carrierName
            ?: "SIM 1"
    }

    Card(
        shape = RoundedCornerShape(32.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF222222)),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        modifier = modifier
            .fillMaxWidth()
            .clickable { onExpand() }
            .testTag("in_app_call_notification_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 18.dp)
        ) {
            // Top Row: Avatar + Name/SIM + Status
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Avatar with red background and phone badge
                Box(modifier = Modifier.size(54.dp)) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF8B1D1D)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = initial,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 24.sp
                        )
                    }

                    // Overlapping phone badge (bottom right)
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF333333))
                            .align(Alignment.BottomEnd)
                            .border(1.dp, Color(0xFF222222), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Call,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.8f),
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "$displayName ($simCarrier)",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 17.sp
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = " • Phone",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = Color(0xFFAAAAAA),
                                fontSize = 14.sp
                            )
                        )
                    }
                    Text(
                        text = "Incoming call via $simCarrier",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = Color(0xFFBBBBBB),
                            fontSize = 15.sp
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Action Pills: Decline (Red) and Answer (Green)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Decline Button
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = Color(0xFFEF5350),
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onHangUp() }
                        .testTag("call_notification_decline")
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CallEnd,
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Decline",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.Black,
                                fontSize = 15.sp
                            )
                        )
                    }
                }

                // Answer Button
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = Color(0xFF66BB6A),
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onAnswer() }
                        .testTag("call_notification_answer")
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Call,
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Answer",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.Black,
                                fontSize = 15.sp
                            )
                        )
                    }
                }
            }
        }
    }
}

/**
 * SMS Notification Card matching Screenshots 1 & 8:
 * App thumbnail with chat badge, "$ • Messages • now", expandable message body, "Open link", "Mark as read".
 */
@Composable
fun InAppSmsNotificationCard(
    popup: IncomingSmsPopup,
    onOpenLink: () -> Unit,
    onMarkRead: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(true) }

    // Auto dismiss after 14 seconds if user does not interact
    LaunchedEffect(popup.id) {
        delay(14000L)
        onDismiss()
    }

    val rawSender = popup.senderName.takeIf { !it.isNullOrBlank() && it != "$" } ?: popup.senderNumber
    val senderInitial = rawSender.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "M"
    val hasLink = remember(popup.body) {
        android.util.Patterns.WEB_URL.matcher(popup.body).find() ||
            popup.body.contains("http://", ignoreCase = true) ||
            popup.body.contains("https://", ignoreCase = true) ||
            popup.body.contains("www.", ignoreCase = true)
    }

    Card(
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2C2C2C)),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        modifier = modifier
            .fillMaxWidth()
            .testTag("in_app_sms_notification_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            // Top Row: Thumbnail + Title + Chevron
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    // App/Sender thumbnail with small chat badge
                    Box(modifier = Modifier.size(44.dp)) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF795548)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = senderInitial,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                        }

                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF1E88E5))
                                .align(Alignment.BottomEnd),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Chat,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(10.dp)
                            )
                        }
                    }

                    // Sender • Messages • now
                    Text(
                        text = "$rawSender • Messages • now",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                            fontSize = 15.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(
                    onClick = { isExpanded = !isExpanded },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                        contentDescription = "Expand SMS details",
                        tint = Color(0xFFCCCCCC),
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Body preview
            Text(
                text = popup.body,
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = Color(0xFFD6D6D6),
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                ),
                maxLines = if (isExpanded) 6 else 1,
                overflow = TextOverflow.Ellipsis
            )

            if (isExpanded) {
                if (hasLink) {
                    Spacer(modifier = Modifier.height(14.dp))

                    // Action: "Open link" pill
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Start
                    ) {
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = Color(0xFF1E1E1E),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF555555)),
                            modifier = Modifier
                                .clickable { onOpenLink() }
                                .testTag("sms_notification_open_link")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color(0xFFE64A19)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Public,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                                Text(
                                    text = "Open link",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = FontWeight.Medium,
                                        color = Color.White,
                                        fontSize = 13.sp
                                    )
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Bottom row: "Mark as read" + optional expand window icon
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Mark as read",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = Color(0xFFCCCCCC),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Normal
                        ),
                        modifier = Modifier
                            .clickable { onMarkRead() }
                            .padding(vertical = 4.dp)
                    )

                    if (hasLink) {
                        IconButton(
                            onClick = { onOpenLink() },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                contentDescription = "Open in Messages",
                                tint = Color(0xFFAAAAAA),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
