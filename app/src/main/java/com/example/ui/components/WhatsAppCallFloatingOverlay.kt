package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.telecom.OngoingCallHelper
import kotlin.math.roundToInt

data class InAppWhatsAppCallSession(
    val phoneNumber: String,
    val contactName: String?,
    val isVideo: Boolean = false,
    val isConnected: Boolean = false,
    val durationSeconds: Long = 0L,
    val isMuted: Boolean = false,
    val isSpeakerOn: Boolean = true,
    val isFrontCamera: Boolean = true
) {
    val displayName: String
        get() = contactName?.takeIf { it.isNotBlank() } ?: phoneNumber

    val formattedDuration: String
        get() = OngoingCallHelper.formatCallDuration(durationSeconds)
}

@Composable
fun WhatsAppCallFloatingOverlay(
    session: InAppWhatsAppCallSession?,
    onToggleMute: () -> Unit,
    onToggleSpeaker: () -> Unit,
    onSwitchCamera: () -> Unit,
    onEndCall: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (session == null) return

    var offsetX by remember { mutableFloatStateOf(20f) }
    var offsetY by remember { mutableFloatStateOf(100f) }

    AnimatedVisibility(
        visible = true,
        enter = fadeIn() + scaleIn(),
        exit = fadeOut() + scaleOut()
    ) {
        Box(
            modifier = modifier
                .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        offsetX = (offsetX + dragAmount.x).coerceIn(-100f, 600f)
                        offsetY = (offsetY + dragAmount.y).coerceIn(-50f, 1600f)
                    }
                }
                .testTag("whatsapp_floating_pip_overlay")
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color(0xFF111B21), // WhatsApp dark theme container
                tonalElevation = 8.dp,
                shadowElevation = 10.dp,
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF25D366).copy(alpha = 0.6f)),
                modifier = Modifier.width(290.dp)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Header Bar with WhatsApp branding pill
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFF25D366).copy(alpha = 0.2f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .background(Color(0xFF25D366), CircleShape)
                                )
                                Text(
                                    text = if (session.isVideo) "WhatsApp Video" else "WhatsApp Voice",
                                    color = Color(0xFF25D366),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Text(
                            text = if (session.isConnected) session.formattedDuration else "Ringing...",
                            color = if (session.isConnected) Color.White else Color(0xFF25D366),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Contact Info
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        ContactAvatar(
                            nameOrNumber = session.displayName,
                            size = 42.dp
                        )

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = session.displayName,
                                style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp),
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = if (session.isConnected) "Call Active" else session.phoneNumber,
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                                color = Color(0xFFAAAAAA),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Call Action Controls
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Mute Button
                        IconButton(
                            onClick = onToggleMute,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(if (session.isMuted) Color(0xFFE53935) else Color(0xFF2A3942))
                                .testTag("whatsapp_pip_mute_btn")
                        ) {
                            Icon(
                                imageVector = if (session.isMuted) Icons.Filled.MicOff else Icons.Filled.Mic,
                                contentDescription = "Mute",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // Speakerphone Button
                        IconButton(
                            onClick = onToggleSpeaker,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(if (session.isSpeakerOn) Color(0xFF25D366) else Color(0xFF2A3942))
                                .testTag("whatsapp_pip_speaker_btn")
                        ) {
                            Icon(
                                imageVector = if (session.isSpeakerOn) Icons.Filled.VolumeUp else Icons.Filled.VolumeOff,
                                contentDescription = "Speaker",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // Camera Switch (for Video)
                        if (session.isVideo) {
                            IconButton(
                                onClick = onSwitchCamera,
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF2A3942))
                                    .testTag("whatsapp_pip_camera_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Cameraswitch,
                                    contentDescription = "Switch Camera",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        // End Call Button
                        IconButton(
                            onClick = onEndCall,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFE53935))
                                .testTag("whatsapp_pip_end_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.CallEnd,
                                contentDescription = "End Call",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
