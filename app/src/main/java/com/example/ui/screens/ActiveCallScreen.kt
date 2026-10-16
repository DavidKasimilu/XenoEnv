package com.example.ui.screens

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.telecom.Call
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.material.icons.filled.PhoneCallback
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
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.telecom.XenoInCallService
import com.example.ui.components.ContactAvatar
import kotlinx.coroutines.delay

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
    var isCallAnswered by remember { mutableStateOf(!isIncoming) }
    var isRinging by remember { mutableStateOf(true) }
    var secondsElapsed by remember { mutableIntStateOf(0) }
    var isMuted by remember { mutableStateOf(false) }
    var isSpeakerOn by remember { mutableStateOf(false) }
    var showDialpadSheet by remember { mutableStateOf(false) }
    var dtmfInput by remember { mutableStateOf("") }

    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager }
    val toneGenerator = remember {
        try {
            ToneGenerator(AudioManager.STREAM_VOICE_CALL, 80)
        } catch (_: Exception) {
            null
        }
    }

    // Set audio mode to communication for call duration and cleanup on dispose
    DisposableEffect(Unit) {
        try {
            audioManager?.mode = AudioManager.MODE_IN_COMMUNICATION
        } catch (_: Exception) {}

        onDispose {
            try {
                toneGenerator?.stopTone()
                toneGenerator?.release()
            } catch (_: Exception) {}
            try {
                audioManager?.mode = AudioManager.MODE_NORMAL
                audioManager?.isMicrophoneMute = false
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    audioManager?.clearCommunicationDevice()
                } else {
                    @Suppress("DEPRECATION")
                    audioManager?.isSpeakerphoneOn = false
                }
            } catch (_: Exception) {}
        }
    }

    // Listen to real telecom InCallService state changes if bound
    DisposableEffect(Unit) {
        XenoInCallService.callStateListener = { state ->
            when (state) {
                Call.STATE_ACTIVE -> {
                    isRinging = false
                    isCallAnswered = true
                }
                Call.STATE_DISCONNECTED -> {
                    onEndCall(secondsElapsed.toLong())
                }
                Call.STATE_RINGING, Call.STATE_DIALING, Call.STATE_CONNECTING -> {
                    isRinging = true
                }
            }
        }
        onDispose {
            XenoInCallService.callStateListener = null
        }
    }

    // Play ring tone while ringing
    LaunchedEffect(isRinging, isCallAnswered) {
        if (isRinging && !isCallAnswered) {
            try {
                while (isRinging && !isCallAnswered) {
                    toneGenerator?.startTone(ToneGenerator.TONE_SUP_RINGTONE, 1800)
                    delay(3800)
                }
            } catch (_: Exception) {}
        } else if (isRinging && isCallAnswered) {
            try {
                while (isRinging) {
                    toneGenerator?.startTone(ToneGenerator.TONE_SUP_RINGTONE, 1800)
                    delay(3800)
                }
            } catch (_: Exception) {}
        } else {
            try {
                toneGenerator?.stopTone()
            } catch (_: Exception) {}
        }
    }

    // For outgoing call: Auto-connect after ~4.8 seconds if no external telecom provider overrides
    LaunchedEffect(isRinging, isIncoming) {
        if (!isIncoming && isRinging && XenoInCallService.currentCall == null) {
            delay(4800)
            isRinging = false
            isCallAnswered = true
        }
    }

    // Count ongoing call duration once the call is active / answered
    LaunchedEffect(isCallAnswered, isRinging) {
        if (isCallAnswered && !isRinging) {
            while (true) {
                delay(1000)
                secondsElapsed++
            }
        }
    }

    // Apply speakerphone routing
    LaunchedEffect(isSpeakerOn) {
        try {
            audioManager?.mode = AudioManager.MODE_IN_COMMUNICATION
            @Suppress("DEPRECATION")
            audioManager?.isSpeakerphoneOn = isSpeakerOn
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val speakerDevice = audioManager?.availableCommunicationDevices?.find {
                    it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
                }
                if (isSpeakerOn && speakerDevice != null) {
                    audioManager?.setCommunicationDevice(speakerDevice)
                } else {
                    audioManager?.clearCommunicationDevice()
                }
            }
            XenoInCallService.setSpeaker(isSpeakerOn)
        } catch (e: Exception) {
            android.util.Log.e("ActiveCallScreen", "Speaker toggle error: ${e.message}")
        }
    }

    // Apply microphone mute
    LaunchedEffect(isMuted) {
        try {
            audioManager?.isMicrophoneMute = isMuted
            XenoInCallService.setMute(isMuted)
        } catch (e: Exception) {
            android.util.Log.e("ActiveCallScreen", "Mute toggle error: ${e.message}")
        }
    }

    fun handleAnswer() {
        try {
            toneGenerator?.stopTone()
        } catch (_: Exception) {}
        isCallAnswered = true
        isRinging = false
        onAcceptCall?.invoke()
    }

    fun handleDecline() {
        try {
            toneGenerator?.stopTone()
        } catch (_: Exception) {}
        try {
            XenoInCallService.rejectCall()
        } catch (_: Exception) {}
        try {
            audioManager?.mode = AudioManager.MODE_NORMAL
        } catch (_: Exception) {}
        onDeclineCall?.invoke()
    }

    fun handleEndCall() {
        try {
            toneGenerator?.stopTone()
        } catch (_: Exception) {}
        try {
            XenoInCallService.endCall()
        } catch (_: Exception) {}
        try {
            audioManager?.isMicrophoneMute = false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                audioManager?.clearCommunicationDevice()
            } else {
                @Suppress("DEPRECATION")
                audioManager?.isSpeakerphoneOn = false
            }
            audioManager?.mode = AudioManager.MODE_NORMAL
        } catch (_: Exception) {}
        onEndCall(secondsElapsed.toLong())
    }

    val formatDuration = remember(secondsElapsed) {
        val mins = secondsElapsed / 60
        val secs = secondsElapsed % 60
        String.format(java.util.Locale.getDefault(), "%02d:%02d", mins, secs)
    }

    // Pulsing radar animation for contact avatar during ringing
    val infiniteTransition = rememberInfiniteTransition(label = "pulse_transition")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
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
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Status Area
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 28.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = when {
                        isIncoming && !isCallAnswered -> Color(0xFF2E7D32).copy(alpha = 0.15f)
                        isRinging -> MaterialTheme.colorScheme.primaryContainer
                        else -> Color(0xFF2E7D32).copy(alpha = 0.15f)
                    },
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        when {
                            isIncoming && !isCallAnswered -> {
                                Icon(
                                    imageVector = Icons.Filled.PhoneCallback,
                                    contentDescription = null,
                                    tint = Color(0xFF2E7D32),
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Incoming Call...",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color(0xFF2E7D32),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            isRinging -> {
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

                Text(
                    text = when {
                        isIncoming && !isCallAnswered -> "Ringing..."
                        isRinging -> "Waiting for response..."
                        else -> formatDuration
                    },
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.testTag("call_timer_text")
                )

                // Quick Simulation Chip if user placed an outgoing call and wants to connect immediately
                AnimatedVisibility(visible = isRinging && !isIncoming, enter = fadeIn(), exit = fadeOut()) {
                    FilledTonalButton(
                        onClick = {
                            isRinging = false
                            isCallAnswered = true
                        },
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
                        Text("Connect Call (Simulate)", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }

            // Center Contact Info with Pulsing Rings when ringing
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(150.dp)
                ) {
                    if (isRinging) {
                        // Pulsing ripple ring
                        Box(
                            modifier = Modifier
                                .size(130.dp)
                                .scale(pulseScale)
                                .clip(CircleShape)
                                .background(
                                    if (isIncoming && !isCallAnswered) Color(0xFF2E7D32).copy(alpha = pulseAlpha)
                                    else MaterialTheme.colorScheme.primary.copy(alpha = pulseAlpha)
                                )
                        )
                    }
                    ContactAvatar(
                        nameOrNumber = contactName ?: phoneNumber,
                        size = 110.dp
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))
                Text(
                    text = contactName ?: "Unknown Contact",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.testTag("call_contact_name")
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = phoneNumber + if (dtmfInput.isNotEmpty()) " #$dtmfInput" else "",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium
                )
                if (isMuted) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.errorContainer
                    ) {
                        Text(
                            text = "Microphone is muted",
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            // Bottom Actions Grid
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(22.dp)
            ) {
                if (isIncoming && !isCallAnswered) {
                    // Incoming Call Actions: Decline / Speaker / Answer
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
                            onClick = { handleDecline() },
                            testTag = "call_decline_btn"
                        )

                        // Speaker Toggle
                        CallActionButton(
                            icon = if (isSpeakerOn) Icons.Filled.VolumeUp else Icons.Outlined.VolumeMute,
                            label = if (isSpeakerOn) "Speaker ON" else "Speaker",
                            isActive = isSpeakerOn,
                            activeContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            activeContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            onClick = { isSpeakerOn = !isSpeakerOn },
                            testTag = "call_speaker_btn"
                        )

                        // Answer Call Button
                        CallActionButton(
                            icon = Icons.Filled.Call,
                            label = "Answer",
                            isActive = true,
                            activeContainerColor = Color(0xFF2E7D32),
                            activeContentColor = Color.White,
                            onClick = { handleAnswer() },
                            testTag = "call_answer_btn"
                        )
                    }
                } else {
                    // Connected Ongoing Call Actions: Mute / Speaker / Dialpad / End Call
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        // Mute Button
                        CallActionButton(
                            icon = if (isMuted) Icons.Filled.MicOff else Icons.Filled.Mic,
                            label = if (isMuted) "Muted" else "Mute",
                            isActive = isMuted,
                            activeContainerColor = MaterialTheme.colorScheme.errorContainer,
                            activeContentColor = MaterialTheme.colorScheme.onErrorContainer,
                            onClick = { isMuted = !isMuted },
                            testTag = "call_mute_btn"
                        )

                        // Speaker Button
                        CallActionButton(
                            icon = if (isSpeakerOn) Icons.Filled.VolumeUp else Icons.Outlined.VolumeMute,
                            label = if (isSpeakerOn) "Speaker ON" else "Speaker",
                            isActive = isSpeakerOn,
                            activeContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            activeContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            onClick = { isSpeakerOn = !isSpeakerOn },
                            testTag = "call_speaker_btn"
                        )

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
                        text = dtmfInput.ifBlank { "Tap digits..." },
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
                                            dtmfInput += digit
                                            try {
                                                val tone = when (digit) {
                                                    "0" -> ToneGenerator.TONE_DTMF_0
                                                    "1" -> ToneGenerator.TONE_DTMF_1
                                                    "2" -> ToneGenerator.TONE_DTMF_2
                                                    "3" -> ToneGenerator.TONE_DTMF_3
                                                    "4" -> ToneGenerator.TONE_DTMF_4
                                                    "5" -> ToneGenerator.TONE_DTMF_5
                                                    "6" -> ToneGenerator.TONE_DTMF_6
                                                    "7" -> ToneGenerator.TONE_DTMF_7
                                                    "8" -> ToneGenerator.TONE_DTMF_8
                                                    "9" -> ToneGenerator.TONE_DTMF_9
                                                    "*" -> ToneGenerator.TONE_DTMF_S
                                                    "#" -> ToneGenerator.TONE_DTMF_P
                                                    else -> ToneGenerator.TONE_DTMF_0
                                                }
                                                toneGenerator?.startTone(tone, 120)
                                            } catch (_: Exception) {}
                                            XenoInCallService.sendDtmf(digit[0])
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
                .size(60.dp)
                .clip(CircleShape)
                .clickable { onClick() }
                .testTag(testTag)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = if (isActive) activeContentColor else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(26.dp)
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
