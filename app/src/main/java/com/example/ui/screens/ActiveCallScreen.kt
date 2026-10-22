package com.example.ui.screens

import android.app.Activity
import android.media.AudioManager
import android.media.ToneGenerator
import android.view.WindowManager
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PhoneCallback
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.outlined.VolumeMute
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.telecom.CallPhase
import com.example.telecom.OngoingCallHelper
import com.example.telecom.XenoOverlayService
import com.example.ui.components.ContactAvatar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActiveCallScreen(
    phoneNumber: String,
    contactName: String?,
    isIncoming: Boolean = false,
    onAcceptCall: (() -> Unit)? = null,
    onDeclineCall: (() -> Unit)? = null,
    onEndCall: (durationSeconds: Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val callSession by OngoingCallHelper.sessionFlow.collectAsStateWithLifecycle()
    var showDialpadSheet by remember { mutableStateOf(false) }

    val toneGenerator = remember {
        try {
            ToneGenerator(AudioManager.STREAM_DTMF, 80)
        } catch (_: Exception) {
            null
        }
    }

    // Ensure OngoingCallHelper session is initialized and keep screen awake during active/incoming call
    LaunchedEffect(phoneNumber, contactName, isIncoming) {
        XenoOverlayService.dismissCallOverlay(context)
        val current = OngoingCallHelper.sessionFlow.value
        if (current.phase == CallPhase.IDLE || current.phoneNumber != phoneNumber) {
            if (isIncoming) {
                OngoingCallHelper.startIncomingSession(phoneNumber, contactName)
            } else {
                OngoingCallHelper.startOutgoingSession(phoneNumber, contactName)
            }
        } else if (!contactName.isNullOrBlank() && current.contactName.isNullOrBlank()) {
            OngoingCallHelper.updateContactNameIfMissing(phoneNumber, contactName)
        }
    }

    DisposableEffect(Unit) {
        val window = (context as? Activity)?.window
        try {
            window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } catch (_: Exception) {}

        onDispose {
            try {
                window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } catch (_: Exception) {}
            try {
                toneGenerator?.stopTone()
                toneGenerator?.release()
            } catch (_: Exception) {}
        }
    }

    val isIncomingRinging = callSession.phase == CallPhase.INCOMING_RINGING || (isIncoming && !callSession.isConnected)
    val isOutgoingDialing = callSession.phase == CallPhase.OUTGOING_DIALING && !isIncomingRinging
    val isCallConnected = callSession.isConnected
    val displayContactName = callSession.contactName?.takeIf { it.isNotBlank() }
        ?: contactName?.takeIf { it.isNotBlank() }
        ?: phoneNumber
    val displayPhoneNumber = callSession.phoneNumber.ifBlank { phoneNumber }

    fun handleAnswer() {
        OngoingCallHelper.answerIncomingCall(context)
        onAcceptCall?.invoke()
    }

    fun handleDecline() {
        OngoingCallHelper.declineIncomingCall(context)
        onDeclineCall?.invoke()
    }

    fun handleEndCall() {
        val finalDuration = OngoingCallHelper.getCurrentDurationSeconds()
        OngoingCallHelper.endCall(context)
        onEndCall(finalDuration)
    }

    // Smooth pulsing animation for incoming/dialing avatar rings
    val infiniteTransition = rememberInfiniteTransition(label = "call_pulse_transition")
    val outerPulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.32f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "outer_pulse_scale"
    )
    val innerPulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.16f,
        animationSpec = infiniteRepeatable(
            animation = tween(850, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "inner_pulse_scale"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.34f,
        targetValue = 0.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .testTag("active_call_screen"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Status & Call Duration Timer Area
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 24.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = when {
                        isIncomingRinging -> Color(0xFF2E7D32).copy(alpha = 0.15f)
                        callSession.isOnHold -> MaterialTheme.colorScheme.tertiaryContainer
                        isOutgoingDialing -> MaterialTheme.colorScheme.primaryContainer
                        else -> Color(0xFF2E7D32).copy(alpha = 0.15f)
                    },
                    modifier = Modifier.padding(bottom = 10.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        when {
                            isIncomingRinging -> {
                                Icon(
                                    imageVector = Icons.Filled.PhoneCallback,
                                    contentDescription = null,
                                    tint = Color(0xFF2E7D32),
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Incoming Voice Call",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color(0xFF2E7D32),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            callSession.isOnHold -> {
                                Icon(
                                    imageVector = Icons.Filled.Pause,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Call On Hold",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            isOutgoingDialing -> {
                                Icon(
                                    imageVector = Icons.Filled.Call,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Calling & Ringing...",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            else -> {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF2E7D32))
                                )
                                Text(
                                    text = "Connected • Ongoing Call",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color(0xFF2E7D32),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                // Call Timer / Status Headline: strictly shows timer ONLY when call is connected
                Text(
                    text = when {
                        isIncomingRinging -> if (callSession.isRingerSilenced) "Ringing (Silenced)" else "Ringing..."
                        isOutgoingDialing -> "Dialing..."
                        else -> callSession.formattedDuration
                    },
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.testTag("call_timer_text")
                )

                // If outgoing call has no bound Telecom Call (e.g., simulator/non-default dialer preview), allow manual connect
                AnimatedVisibility(
                    visible = isOutgoingDialing && !callSession.hasTelecomCall,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    FilledTonalButton(
                        onClick = { OngoingCallHelper.markCallConnectedLocally(context) },
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .testTag("simulate_answer_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Connect Call", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }

            // Center Contact Info with Smooth Pulsing Rings
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(164.dp)
                ) {
                    if (isIncomingRinging || isOutgoingDialing) {
                        val ringColor = if (isIncomingRinging) {
                            Color(0xFF2E7D32)
                        } else {
                            MaterialTheme.colorScheme.primary
                        }
                        Box(
                            modifier = Modifier
                                .size(142.dp)
                                .scale(outerPulseScale)
                                .clip(CircleShape)
                                .background(ringColor.copy(alpha = pulseAlpha * 0.7f))
                        )
                        Box(
                            modifier = Modifier
                                .size(124.dp)
                                .scale(innerPulseScale)
                                .clip(CircleShape)
                                .background(ringColor.copy(alpha = pulseAlpha))
                        )
                    }
                    ContactAvatar(
                        nameOrNumber = displayContactName,
                        size = 112.dp
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))
                Text(
                    text = displayContactName,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("call_contact_name")
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = displayPhoneNumber + if (callSession.dtmfDigits.isNotEmpty()) " #${callSession.dtmfDigits}" else "",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium
                )

                // Status badges for Mute / Speaker / Hold
                Row(
                    modifier = Modifier.padding(top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (callSession.isMuted) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.errorContainer
                        ) {
                            Text(
                                text = "Mic Muted",
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                    if (callSession.isSpeakerOn) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = "Speakerphone Active",
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            // Bottom Actions Area with Smooth Transition between Incoming and Ongoing Call Controls
            AnimatedContent(
                targetState = isIncomingRinging,
                transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(180)) },
                label = "call_controls_transition"
            ) { incomingState ->
                if (incomingState) {
                    // Incoming Call Screen Controls: Silence Ringer + Decline / Speaker / Answer
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        if (!callSession.isRingerSilenced) {
                            FilledTonalButton(
                                onClick = { OngoingCallHelper.silenceIncomingRinger(context) },
                                shape = RoundedCornerShape(20.dp),
                                modifier = Modifier.testTag("silence_ringer_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.NotificationsOff,
                                    contentDescription = "Silence Ringer",
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Silence Ringtone",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Decline Call Button
                            CallActionButton(
                                icon = Icons.Filled.CallEnd,
                                label = "Decline",
                                isActive = true,
                                activeContainerColor = MaterialTheme.colorScheme.error,
                                activeContentColor = MaterialTheme.colorScheme.onError,
                                buttonSize = 68.dp,
                                iconSize = 30.dp,
                                onClick = { handleDecline() },
                                testTag = "call_decline_btn"
                            )

                            // Speaker Pre-Toggle
                            CallActionButton(
                                icon = if (callSession.isSpeakerOn) Icons.Filled.VolumeUp else Icons.Outlined.VolumeMute,
                                label = if (callSession.isSpeakerOn) "Speaker ON" else "Speaker",
                                isActive = callSession.isSpeakerOn,
                                activeContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                activeContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                onClick = { OngoingCallHelper.toggleSpeakerphone(context) },
                                testTag = "call_speaker_btn"
                            )

                            // Answer Call Button
                            CallActionButton(
                                icon = Icons.Filled.Call,
                                label = "Answer",
                                isActive = true,
                                activeContainerColor = Color(0xFF2E7D32),
                                activeContentColor = Color.White,
                                buttonSize = 68.dp,
                                iconSize = 30.dp,
                                onClick = { handleAnswer() },
                                testTag = "call_answer_btn"
                            )
                        }
                    }
                } else {
                    // Ongoing / Dialing Call Controls: Mute / Speaker / Hold / Keypad + End Call
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(22.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Mute Button
                            CallActionButton(
                                icon = if (callSession.isMuted) Icons.Filled.MicOff else Icons.Filled.Mic,
                                label = if (callSession.isMuted) "Muted" else "Mute",
                                isActive = callSession.isMuted,
                                activeContainerColor = MaterialTheme.colorScheme.errorContainer,
                                activeContentColor = MaterialTheme.colorScheme.onErrorContainer,
                                onClick = { OngoingCallHelper.toggleMicrophoneMute(context) },
                                testTag = "call_mute_btn"
                            )

                            // Speaker Button
                            CallActionButton(
                                icon = if (callSession.isSpeakerOn) Icons.Filled.VolumeUp else Icons.Outlined.VolumeMute,
                                label = if (callSession.isSpeakerOn) "Speaker ON" else "Speaker",
                                isActive = callSession.isSpeakerOn,
                                activeContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                activeContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                onClick = { OngoingCallHelper.toggleSpeakerphone(context) },
                                testTag = "call_speaker_btn"
                            )

                            // Hold Button (when connected)
                            if (isCallConnected) {
                                CallActionButton(
                                    icon = if (callSession.isOnHold) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                                    label = if (callSession.isOnHold) "Resume" else "Hold",
                                    isActive = callSession.isOnHold,
                                    activeContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                    activeContentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                                    onClick = { OngoingCallHelper.toggleCallHold() },
                                    testTag = "call_hold_btn"
                                )
                            }

                            // Dialpad Button
                            CallActionButton(
                                icon = Icons.Filled.Dialpad,
                                label = "Keypad",
                                isActive = showDialpadSheet,
                                onClick = { showDialpadSheet = true },
                                testTag = "call_dialpad_btn"
                            )
                        }

                        // End Call Big Red Button
                        Button(
                            onClick = { handleEndCall() },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            shape = CircleShape,
                            modifier = Modifier
                                .size(72.dp)
                                .testTag("end_call_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.CallEnd,
                                contentDescription = "End Call",
                                tint = MaterialTheme.colorScheme.onError,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                }
            }
        }

        // In-call Dialpad Bottom Sheet for DTMF tones
        if (showDialpadSheet) {
            ModalBottomSheet(
                onDismissRequest = { showDialpadSheet = false },
                sheetState = rememberModalBottomSheetState()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "In-Call Keypad",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = callSession.dtmfDigits.ifBlank { "Tap digits..." },
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )

                    val rows = listOf(
                        listOf("1", "2", "3"),
                        listOf("4", "5", "6"),
                        listOf("7", "8", "9"),
                        listOf("*", "0", "#")
                    )

                    for (rowDigits in rows) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            for (digit in rowDigits) {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clip(CircleShape)
                                        .testTag("dtmf_$digit")
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                        .clickable {
                                            OngoingCallHelper.sendDtmfTone(digit[0], toneGenerator)
                                        }
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = digit,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    FilledTonalButton(
                        onClick = { showDialpadSheet = false },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Hide Keypad")
                    }
                }
            }
        }
    }
}

@Composable
private fun CallActionButton(
    icon: ImageVector,
    label: String,
    isActive: Boolean,
    activeContainerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    activeContentColor: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    buttonSize: androidx.compose.ui.unit.Dp = 60.dp,
    iconSize: androidx.compose.ui.unit.Dp = 26.dp,
    onClick: () -> Unit,
    testTag: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Surface(
            shape = CircleShape,
            color = if (isActive) activeContainerColor else MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier
                .size(buttonSize)
                .clip(CircleShape)
                .clickable { onClick() }
                .testTag(testTag)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = if (isActive) activeContentColor else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(iconSize)
                )
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium
        )
    }
}
