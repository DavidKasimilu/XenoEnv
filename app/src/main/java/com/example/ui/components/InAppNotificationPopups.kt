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
    onHangUp: () -> Unit,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isExpanded by remember { mutableStateOf(true) }
    val displayName = popup.callerName?.takeIf { it.isNotBlank() } ?: popup.callerNumber
    val initial = displayName.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "I"

    Card(
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF303030)),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        modifier = modifier
            .fillMaxWidth()
            .clickable { onExpand() }
            .testTag("in_app_call_notification_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            // Top Row: Avatar with Phone Badge + Name/Subtitle + Chevron
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    // Avatar with phone badge
                    Box(modifier = Modifier.size(46.dp)) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFB71C1C)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = initial,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp
                            )
                        }

                        // Bottom-right phone badge
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF212121))
                                .align(Alignment.BottomEnd),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Call,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(11.dp)
                            )
                        }
                    }

                    // Contact Name & Status
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "$displayName • Phone",
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White,
                                    fontSize = 15.sp
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        val simCarrier = remember(context) {
                            com.example.telecom.TelecomHelper.getAvailableSimCards(context).firstOrNull()?.carrierName ?: "SIM 1"
                        }
                        Text(
                            text = "Calling via $simCarrier",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = Color(0xFFB0B0B0),
                                fontSize = 13.sp
                            )
                        )
                    }
                }

                IconButton(
                    onClick = { isExpanded = !isExpanded },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                        contentDescription = "Toggle notification details",
                        tint = Color(0xFFCCCCCC),
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            if (isExpanded) {
                Spacer(modifier = Modifier.height(14.dp))

                // Bottom Action Pills Row: Hang Up (Red), Speaker (Grey), Mute (Grey) (Screenshot 5)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 1. Hang Up Pill (Red)
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Color(0xFFEF5350),
                        modifier = Modifier
                            .clickable { onHangUp() }
                            .testTag("call_notification_hang_up")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.CallEnd,
                                contentDescription = null,
                                tint = Color.Black,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Hang Up",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color.Black,
                                    fontSize = 13.sp
                                )
                            )
                        }
                    }

                    // 2. Speaker Pill (Grey)
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Color(0xFF4A4A4A),
                        modifier = Modifier
                            .clickable {
                                OngoingCallHelper.toggleSpeaker(context)
                            }
                            .testTag("call_notification_speaker")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.VolumeUp,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Speaker",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Medium,
                                    color = Color.White,
                                    fontSize = 13.sp
                                )
                            )
                        }
                    }

                    // 3. Mute Pill (Grey)
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Color(0xFF4A4A4A),
                        modifier = Modifier
                            .clickable {
                                OngoingCallHelper.toggleMute(context)
                            }
                            .testTag("call_notification_mute")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Mic,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Mute",
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
                                text = "$",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp
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
                    val senderLabel = popup.senderName.takeIf { it.isNotBlank() } ?: "$"
                    Text(
                        text = "$senderLabel • Messages • now",
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

                Spacer(modifier = Modifier.height(12.dp))

                // Bottom row: "Mark as read" + expand window icon
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
