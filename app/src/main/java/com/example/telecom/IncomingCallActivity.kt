package com.example.telecom

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * Full-screen call screen for the lock screen / screen-off case.
 *
 * Launched by the incoming-call notification's full-screen intent. It turns the display on and
 * shows over the keyguard, but ONLY this screen does: the rest of the app never appears over the
 * lock screen. It shows Accept / Decline while ringing, and a small in-call screen (timer, mute,
 * speaker, end) after the call is answered. It closes itself when the call is over.
 */
class IncomingCallActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        enableEdgeToEdge()

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                CallScreen(onClose = { finishAndRemoveTask() })
            }
        }
    }
}

@Composable
private fun CallScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    val session by OngoingCallHelper.sessionFlow.collectAsState()
    val popup by OngoingCallHelper.incomingPopupFlow.collectAsState()

    val phase = session.phase
    var callWasSeen by remember { mutableStateOf(false) }
    val isRinging = phase == CallPhase.INCOMING_RINGING
    val isInCall = phase == CallPhase.ACTIVE || phase == CallPhase.HOLDING || phase == CallPhase.OUTGOING_DIALING

    // Close when the call is over (declined, ended, or the caller hung up)
    LaunchedEffect(phase) {
        if (isRinging || isInCall) {
            callWasSeen = true
        } else if (callWasSeen) {
            delay(350L)
            onClose()
        }
    }
    // Safety: nothing to show (stale launch)
    LaunchedEffect(Unit) {
        delay(3_000L)
        if (!callWasSeen) onClose()
    }

    val number = session.phoneNumber.ifBlank { popup?.callerNumber.orEmpty() }
    val savedName = (session.contactName ?: popup?.callerName)
        ?.takeIf { it.isNotBlank() && it != number }
    val displayName = savedName ?: number.ifBlank { "Unknown caller" }
    val carrier = session.carrierName ?: popup?.carrierName
    val initials = XenoNotificationHelper.extractInitials(savedName, number)

    // While ringing, Back must not dismiss the call screen by accident
    BackHandler(enabled = isRinging) { }
    BackHandler(enabled = !isRinging) { onClose() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF1B1B1B), Color(0xFF0E0E0E))
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = when {
                    isRinging -> if (!carrier.isNullOrBlank()) "Incoming call via $carrier" else "Incoming call"
                    phase == CallPhase.OUTGOING_DIALING -> "Calling..."
                    phase == CallPhase.HOLDING -> "On hold"
                    isInCall -> session.formattedDuration
                    else -> "Call ended"
                },
                color = Color(0xFFB0B0B0),
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(48.dp))

            Box(
                modifier = Modifier
                    .size(132.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF8B1D1D)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = initials,
                    color = Color.White,
                    fontSize = 46.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            Text(
                text = displayName,
                color = Color.White,
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            if (savedName != null && number.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = number,
                    color = Color(0xFFB0B0B0),
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            if (isRinging) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CallActionButton(
                        color = Color(0xFFEF5350),
                        icon = Icons.Filled.CallEnd,
                        label = "Decline",
                        onClick = { IncomingCallPopupHelper.declineIncomingCall(context) }
                    )
                    CallActionButton(
                        color = Color(0xFF66BB6A),
                        icon = Icons.Filled.Call,
                        label = "Answer",
                        onClick = {
                            XenoOverlayService.dismissCallOverlay(context)
                            OngoingCallHelper.answerIncomingCallAndOpenFullScreen(
                                context = context,
                                phoneNumber = number,
                                contactName = savedName
                            )
                        }
                    )
                }
            } else if (isInCall) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CallToggleButton(
                        active = session.isMuted,
                        icon = if (session.isMuted) Icons.Filled.MicOff else Icons.Filled.Mic,
                        label = "Mute",
                        onClick = { OngoingCallHelper.toggleMute(context) }
                    )
                    CallToggleButton(
                        active = session.isSpeakerOn,
                        icon = Icons.Filled.VolumeUp,
                        label = "Speaker",
                        onClick = { OngoingCallHelper.toggleSpeaker(context) }
                    )
                }
                Spacer(modifier = Modifier.height(32.dp))
                CallActionButton(
                    color = Color(0xFFEF5350),
                    icon = Icons.Filled.CallEnd,
                    label = "End call",
                    onClick = { OngoingCallHelper.endCall(context) }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun CallActionButton(
    color: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(76.dp)
                .clip(CircleShape)
                .background(color)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = Color.White,
                modifier = Modifier.size(36.dp)
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(text = label, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun CallToggleButton(
    active: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(if (active) Color.White else Color(0xFF2E2E2E))
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (active) Color.Black else Color.White,
                modifier = Modifier.size(28.dp)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = label, color = Color(0xFFCCCCCC), fontSize = 13.sp)
    }
}
