package com.example.ui.screens

import android.app.Activity
import android.media.AudioManager
import android.media.ToneGenerator
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.telecom.CallPhase
import com.example.telecom.OngoingCallHelper
import com.example.telecom.TelecomHelper

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
    var showMoreSheet by remember { mutableStateOf(false) }
    var showDialpadSheet by remember { mutableStateOf(false) }

    val toneGenerator = remember {
        try {
            ToneGenerator(AudioManager.STREAM_DTMF, 80)
        } catch (_: Exception) {
            null
        }
    }

    LaunchedEffect(phoneNumber, contactName, isIncoming) {
        OngoingCallHelper.dismissIncomingCallPopup(context)
        val current = OngoingCallHelper.sessionFlow.value
        if (current.phase == CallPhase.IDLE || current.phoneNumber != phoneNumber) {
            if (isIncoming) {
                OngoingCallHelper.startIncomingSession(
                    phoneNumber = phoneNumber,
                    contactName = contactName,
                    openFullScreenImmediately = true
                )
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

    val isIncomingRinging = callSession.phase == CallPhase.INCOMING_RINGING
    val isOutgoingDialing = callSession.phase == CallPhase.OUTGOING_DIALING && !isIncomingRinging
    val isCallConnected = callSession.isConnected
    val displayContactName = callSession.contactName?.takeIf { it.isNotBlank() }
        ?: contactName?.takeIf { it.isNotBlank() }
        ?: phoneNumber
    val displayPhoneNumber = callSession.phoneNumber.ifBlank { phoneNumber }

    fun handleAnswer() {
        OngoingCallHelper.answerIncomingCallAndOpenFullScreen(
            context = context,
            phoneNumber = displayPhoneNumber,
            contactName = displayContactName
        )
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

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF161616))
            .systemBarsPadding()
            .testTag("active_call_screen")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // 1. Top Section: Status, Contact Name, Subtitle (Screenshots 2 & 4)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
            ) {
                // "Calling via Airtel"
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    val statusPrefix = when {
                        isIncomingRinging -> "Incoming call via "
                        isCallConnected -> "Connected via "
                        else -> "Calling via "
                    }
                    Text(
                        text = statusPrefix,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 15.sp,
                            color = Color(0xFFE0E0E0),
                            fontWeight = FontWeight.Normal
                        )
                    )
                    val simCarrier: String = remember(callSession.carrierName, context) {
                        callSession.carrierName?.takeIf { it.isNotBlank() }
                            ?: TelecomHelper.getAvailableSimCards(context).firstOrNull()?.carrierName
                            ?: "SIM"
                    }
                    Text(
                        text = simCarrier,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 15.sp,
                            color = Color(0xFF66BB6A),
                            fontWeight = FontWeight.Bold
                        )
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Contact Name
                Text(
                    text = displayContactName,
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Normal,
                        color = Color.White
                    ),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Subtitle: Mobile + phone number
                Text(
                    text = "Mobile $displayPhoneNumber",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 15.sp,
                        color = Color(0xFFAAAAAA)
                    ),
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )

                if (isCallConnected) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = callSession.formattedDuration,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 13.sp,
                            color = Color(0xFF81C784),
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }

            // 2. Center Caller Avatar inside smooth CircleShape
            Box(
                modifier = Modifier
                    .size(175.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF333333)),
                contentAlignment = Alignment.Center
            ) {
                // If there's an initial, display large initial on colored gradient or styled background
                val initial = displayContactName.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "C"
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF8D6E63)), // Warm earthy Mona-Lisa tone
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = initial,
                        fontSize = 72.sp,
                        fontWeight = FontWeight.Light,
                        color = Color.White
                    )
                }
            }

            // 3. Bottom Controls Area
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (isIncomingRinging) {
                    // Incoming call controls
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 24.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Decline
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFEF5350))
                                    .clickable { handleDecline() }
                                    .testTag("decline_call_button"),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.CallEnd,
                                    contentDescription = "Decline",
                                    tint = Color.White,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Decline",
                                color = Color.White,
                                style = MaterialTheme.typography.labelMedium
                            )
                        }

                        // Answer
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF4CAF50))
                                    .clickable { handleAnswer() }
                                    .testTag("answer_call_button"),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Call,
                                    contentDescription = "Answer",
                                    tint = Color.White,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Answer",
                                color = Color.White,
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                } else {
                    // Expanded "More" Card (Screenshot 2)
                    AnimatedVisibility(
                        visible = showMoreSheet,
                        enter = slideInVertically(initialOffsetY = { it / 2 }) + fadeIn(),
                        exit = slideOutVertically(targetOffsetY = { it / 2 }) + fadeOut()
                    ) {
                        Surface(
                            shape = RoundedCornerShape(24.dp),
                            color = Color(0xFF282828),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp, horizontal = 16.dp)
                            ) {
                                // Header: "More" and close "X"
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "More",
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color.White,
                                            fontSize = 17.sp
                                        )
                                    )
                                    IconButton(
                                        onClick = { showMoreSheet = false },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Close,
                                            contentDescription = "Close More",
                                            tint = Color.White,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                // Action 1: Add call
                                MoreSheetItem(
                                    icon = Icons.Filled.Add,
                                    label = "Add call",
                                    onClick = {
                                        showMoreSheet = false
                                        showDialpadSheet = true
                                    }
                                )

                                Spacer(modifier = Modifier.height(6.dp))

                                // Action 2: Change SIM
                                MoreSheetItem(
                                    icon = Icons.Filled.SimCard,
                                    label = "Change SIM",
                                    onClick = {
                                        showMoreSheet = false
                                        android.widget.Toast.makeText(context, "SIM swapped to Secondary SIM", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                )

                                Spacer(modifier = Modifier.height(6.dp))

                                // Action 3: Record
                                MoreSheetItem(
                                    icon = Icons.Filled.RadioButtonChecked,
                                    label = "Record",
                                    onClick = {
                                        showMoreSheet = false
                                        android.widget.Toast.makeText(context, "Call recording started", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                )
                            }
                        }
                    }

                    // 4 Control Action Buttons Row: Keypad, Mute, Speaker, More (Screenshots 2 & 4)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 20.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 1. Keypad
                        CallControlButton(
                            icon = Icons.Filled.Dialpad,
                            label = "Keypad",
                            isActive = false,
                            onClick = { showDialpadSheet = true }
                        )

                        // 2. Mute
                        CallControlButton(
                            icon = if (callSession.isMuted) Icons.Filled.MicOff else Icons.Filled.Mic,
                            label = "Mute",
                            isActive = callSession.isMuted,
                            onClick = { OngoingCallHelper.toggleMute(context) }
                        )

                        // 3. Speaker
                        CallControlButton(
                            icon = Icons.Filled.VolumeUp,
                            label = "Speaker",
                            isActive = callSession.isSpeakerOn,
                            onClick = { OngoingCallHelper.toggleSpeaker(context) }
                        )

                        // 4. More (highlighted rounded rectangle when open)
                        CallControlButton(
                            icon = Icons.Filled.MoreVert,
                            label = "More",
                            isActive = showMoreSheet,
                            isMoreButton = true,
                            onClick = { showMoreSheet = !showMoreSheet }
                        )
                    }

                    // Wide coral/red pill "End Call" button (Screenshots 2 & 4)
                    Box(
                        modifier = Modifier
                            .width(180.dp)
                            .height(58.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFF28B82)) // Soft coral / red pill color
                            .clickable { handleEndCall() }
                            .testTag("end_call_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CallEnd,
                            contentDescription = "End Call",
                            tint = Color.Black,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }

    // Dialpad sheet for DTMF input during active call
    if (showDialpadSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showDialpadSheet = false },
            sheetState = sheetState,
            containerColor = Color(0xFF1E1E1E)
        ) {
            DtmfDialpadSheetContent(
                onDigit = { digit ->
                    OngoingCallHelper.playDtmf(digit)
                    toneGenerator?.startTone(
                        when (digit) {
                            '1' -> ToneGenerator.TONE_DTMF_1
                            '2' -> ToneGenerator.TONE_DTMF_2
                            '3' -> ToneGenerator.TONE_DTMF_3
                            '4' -> ToneGenerator.TONE_DTMF_4
                            '5' -> ToneGenerator.TONE_DTMF_5
                            '6' -> ToneGenerator.TONE_DTMF_6
                            '7' -> ToneGenerator.TONE_DTMF_7
                            '8' -> ToneGenerator.TONE_DTMF_8
                            '9' -> ToneGenerator.TONE_DTMF_9
                            '0' -> ToneGenerator.TONE_DTMF_0
                            '*' -> ToneGenerator.TONE_DTMF_S
                            '#' -> ToneGenerator.TONE_DTMF_P
                            else -> ToneGenerator.TONE_DTMF_0
                        },
                        150
                    )
                },
                onClose = { showDialpadSheet = false }
            )
        }
    }
}

@Composable
private fun CallControlButton(
    icon: ImageVector,
    label: String,
    isActive: Boolean,
    isMoreButton: Boolean = false,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable { onClick() }
    ) {
        val containerShape = if (isMoreButton && isActive) RoundedCornerShape(16.dp) else CircleShape
        val containerColor = if (isMoreButton && isActive) {
            Color(0xFFE0E0E0) // Light grey background when active
        } else if (isActive) {
            Color(0xFF424242)
        } else {
            Color(0xFF262626)
        }
        val iconColor = if (isMoreButton && isActive) Color.Black else Color.White

        Box(
            modifier = Modifier
                .size(width = if (isMoreButton && isActive) 64.dp else 56.dp, height = 56.dp)
                .clip(containerShape)
                .background(containerColor),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = iconColor,
                modifier = Modifier.size(26.dp)
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 12.sp,
                color = Color(0xFFCCCCCC)
            )
        )
    }
}

@Composable
private fun MoreSheetItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFF1E1E1E),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF333333)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color(0xFFCCCCCC),
                    modifier = Modifier.size(18.dp)
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 15.sp,
                    color = Color.White,
                    fontWeight = FontWeight.Medium
                )
            )
        }
    }
}

@Composable
private fun DtmfDialpadSheetContent(
    onDigit: (Char) -> Unit,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Keypad",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White
            )
            IconButton(onClick = onClose) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Close Keypad",
                    tint = Color.White
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        val keys = listOf(
            listOf("1", "2", "3"),
            listOf("4", "5", "6"),
            listOf("7", "8", "9"),
            listOf("*", "0", "#")
        )

        keys.forEach { row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                row.forEach { digit ->
                    Surface(
                        shape = CircleShape,
                        color = Color(0xFF303030),
                        modifier = Modifier
                            .size(60.dp)
                            .clickable { onDigit(digit[0]) }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = digit,
                                style = MaterialTheme.typography.titleLarge.copy(
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}
