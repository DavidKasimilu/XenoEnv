package com.example.telecom

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.TelecomManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale

enum class CallPhase {
    IDLE,
    INCOMING_RINGING,
    OUTGOING_DIALING,
    ACTIVE,
    HOLDING,
    DISCONNECTED
}

data class OngoingCallSession(
    val phoneNumber: String = "",
    val contactName: String? = null,
    val isIncoming: Boolean = false,
    val phase: CallPhase = CallPhase.IDLE,
    val connectedAtMillis: Long? = null,
    val durationSeconds: Long = 0L,
    val isMuted: Boolean = false,
    val isSpeakerOn: Boolean = false,
    val isOnHold: Boolean = false,
    val isRingerSilenced: Boolean = false,
    val dtmfDigits: String = "",
    val hasTelecomCall: Boolean = false
) {
    val isConnected: Boolean
        get() = phase == CallPhase.ACTIVE || phase == CallPhase.HOLDING

    val isRingingOrDialing: Boolean
        get() = phase == CallPhase.INCOMING_RINGING || phase == CallPhase.OUTGOING_DIALING

    val formattedDuration: String
        get() = OngoingCallHelper.formatCallDuration(durationSeconds)
}

/**
 * Centralized helper for managing incoming and ongoing call screen state,
 * accurate call duration counting (starting strictly when Call.STATE_ACTIVE is reached),
 * and hardware audio routing (speakerphone, microphone mute, hold, DTMF, ringer silence).
 */
object OngoingCallHelper {

    private const val TAG = "OngoingCallHelper"

    private val helperScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var timerJob: Job? = null

    private val _sessionFlow = MutableStateFlow(OngoingCallSession())
    val sessionFlow: StateFlow<OngoingCallSession> = _sessionFlow.asStateFlow()

    @Volatile
    private var lastIncomingNumber: String = ""

    @Volatile
    private var lastIncomingTriggerTimestamp: Long = 0L

    fun formatCallDuration(seconds: Long): String {
        val safeSeconds = seconds.coerceAtLeast(0L)
        val hours = safeSeconds / 3600
        val minutes = (safeSeconds % 3600) / 60
        val secs = safeSeconds % 60
        return if (hours > 0) {
            String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, secs)
        } else {
            String.format(Locale.getDefault(), "%02d:%02d", minutes, secs)
        }
    }

    fun getCurrentDurationSeconds(): Long {
        val current = _sessionFlow.value
        val connectedAt = current.connectedAtMillis ?: return current.durationSeconds
        if (current.phase == CallPhase.ACTIVE || current.phase == CallPhase.HOLDING) {
            return ((System.currentTimeMillis() - connectedAt) / 1000L).coerceAtLeast(current.durationSeconds)
        }
        return current.durationSeconds
    }

    /**
     * Deduplication helper so XenoInCallService and CallReceiver do not double-trigger
     * incoming call notifications or reset the incoming call screen for the same call.
     */
    @Synchronized
    fun shouldProcessIncomingCall(phoneNumber: String): Boolean {
        val norm = phoneNumber.filter { it.isDigit() }.takeLast(9).ifEmpty { phoneNumber.trim() }
        val now = System.currentTimeMillis()
        val current = _sessionFlow.value
        if (current.phase == CallPhase.INCOMING_RINGING || current.phase == CallPhase.ACTIVE) {
            val currentNorm = current.phoneNumber.filter { it.isDigit() }.takeLast(9).ifEmpty { current.phoneNumber.trim() }
            if (currentNorm == norm && norm.isNotEmpty()) {
                return false
            }
        }
        if (norm == lastIncomingNumber && (now - lastIncomingTriggerTimestamp) < 8_000L) {
            return false
        }
        lastIncomingNumber = norm
        lastIncomingTriggerTimestamp = now
        return true
    }

    fun startOutgoingSession(phoneNumber: String, contactName: String?) {
        stopDurationTimer()
        val existingCall = XenoInCallService.currentCall
        val isAlreadyActive = existingCall?.state == Call.STATE_ACTIVE
        val now = System.currentTimeMillis()
        val connectTime = if (isAlreadyActive) {
            existingCall?.details?.connectTimeMillis?.takeIf { it > 0L } ?: now
        } else {
            null
        }

        _sessionFlow.value = OngoingCallSession(
            phoneNumber = phoneNumber,
            contactName = contactName,
            isIncoming = false,
            phase = if (isAlreadyActive) CallPhase.ACTIVE else CallPhase.OUTGOING_DIALING,
            connectedAtMillis = connectTime,
            durationSeconds = 0L,
            isMuted = false,
            isSpeakerOn = false,
            isOnHold = false,
            dtmfDigits = "",
            hasTelecomCall = existingCall != null
        )

        if (isAlreadyActive && connectTime != null) {
            startDurationTimer(connectTime)
        }
    }

    fun startIncomingSession(phoneNumber: String, contactName: String?) {
        val current = _sessionFlow.value
        val existingCall = XenoInCallService.currentCall
        val isAlreadyActive = existingCall?.state == Call.STATE_ACTIVE

        // If already showing this incoming call, only enrich contactName if needed
        if (current.phase == CallPhase.INCOMING_RINGING && current.phoneNumber == phoneNumber) {
            if (!contactName.isNullOrBlank() && current.contactName.isNullOrBlank()) {
                _sessionFlow.value = current.copy(contactName = contactName)
            }
            return
        }

        stopDurationTimer()
        val now = System.currentTimeMillis()
        val connectTime = if (isAlreadyActive) {
            existingCall?.details?.connectTimeMillis?.takeIf { it > 0L } ?: now
        } else {
            null
        }

        _sessionFlow.value = OngoingCallSession(
            phoneNumber = phoneNumber,
            contactName = contactName ?: current.contactName,
            isIncoming = true,
            phase = if (isAlreadyActive) CallPhase.ACTIVE else CallPhase.INCOMING_RINGING,
            connectedAtMillis = connectTime,
            durationSeconds = 0L,
            isMuted = false,
            isSpeakerOn = false,
            isOnHold = false,
            isRingerSilenced = false,
            dtmfDigits = "",
            hasTelecomCall = existingCall != null
        )

        if (isAlreadyActive && connectTime != null) {
            startDurationTimer(connectTime)
        }
    }

    fun updateContactNameIfMissing(phoneNumber: String, contactName: String?) {
        if (contactName.isNullOrBlank()) return
        val current = _sessionFlow.value
        if (current.phoneNumber == phoneNumber && current.contactName.isNullOrBlank()) {
            _sessionFlow.value = current.copy(contactName = contactName)
        }
    }

    fun onTelecomCallAdded(call: Call) {
        val number = call.details?.handle?.schemeSpecificPart ?: _sessionFlow.value.phoneNumber
        val state = call.state
        val current = _sessionFlow.value
        val effectiveNumber = number.ifBlank { current.phoneNumber }

        _sessionFlow.value = current.copy(
            phoneNumber = effectiveNumber,
            hasTelecomCall = true
        )
        onTelecomCallStateChanged(call, state)
    }

    fun onTelecomCallStateChanged(call: Call?, state: Int) {
        val current = _sessionFlow.value
        val number = call?.details?.handle?.schemeSpecificPart?.takeIf { it.isNotBlank() } ?: current.phoneNumber

        when (state) {
            Call.STATE_RINGING -> {
                stopDurationTimer()
                _sessionFlow.value = current.copy(
                    phoneNumber = number,
                    isIncoming = true,
                    phase = CallPhase.INCOMING_RINGING,
                    connectedAtMillis = null,
                    durationSeconds = 0L,
                    hasTelecomCall = true
                )
            }

            Call.STATE_DIALING, Call.STATE_CONNECTING, Call.STATE_SELECT_PHONE_ACCOUNT -> {
                // Outgoing call is dialing/ringing — counter MUST NOT start yet
                stopDurationTimer()
                _sessionFlow.value = current.copy(
                    phoneNumber = number,
                    isIncoming = false,
                    phase = CallPhase.OUTGOING_DIALING,
                    connectedAtMillis = null,
                    durationSeconds = 0L,
                    hasTelecomCall = true
                )
            }

            Call.STATE_ACTIVE -> {
                // Call is now connected! Start call duration timer from 00:00 if not started yet
                val now = System.currentTimeMillis()
                val telecomConnectMs = call?.details?.connectTimeMillis?.takeIf { it > 0L && (now - it) in 0..3600_000L }
                val connectTimestamp = current.connectedAtMillis ?: telecomConnectMs ?: now
                val initialElapsed = ((now - connectTimestamp) / 1000L).coerceAtLeast(0L)

                _sessionFlow.value = current.copy(
                    phoneNumber = number,
                    phase = CallPhase.ACTIVE,
                    connectedAtMillis = connectTimestamp,
                    durationSeconds = initialElapsed,
                    isOnHold = false,
                    hasTelecomCall = call != null
                )
                startDurationTimer(connectTimestamp)
            }

            Call.STATE_HOLDING -> {
                val connectTimestamp = current.connectedAtMillis ?: System.currentTimeMillis()
                _sessionFlow.value = current.copy(
                    phoneNumber = number,
                    phase = CallPhase.HOLDING,
                    connectedAtMillis = connectTimestamp,
                    isOnHold = true,
                    hasTelecomCall = call != null
                )
                startDurationTimer(connectTimestamp)
            }

            Call.STATE_DISCONNECTED, Call.STATE_DISCONNECTING -> {
                val finalDuration = getCurrentDurationSeconds()
                stopDurationTimer()
                _sessionFlow.value = current.copy(
                    phase = CallPhase.DISCONNECTED,
                    durationSeconds = finalDuration,
                    hasTelecomCall = false
                )
            }
        }
    }

    fun onCallAudioStateChanged(audioState: CallAudioState?) {
        audioState ?: return
        val current = _sessionFlow.value
        val speakerActive = audioState.route == CallAudioState.ROUTE_SPEAKER
        _sessionFlow.value = current.copy(
            isMuted = audioState.isMuted,
            isSpeakerOn = speakerActive
        )
    }

    fun markCallConnectedLocally(context: Context) {
        val current = _sessionFlow.value
        val now = System.currentTimeMillis()
        val connectTimestamp = current.connectedAtMillis ?: now
        configureCallAudioMode(context, active = true)
        _sessionFlow.value = current.copy(
            phase = CallPhase.ACTIVE,
            connectedAtMillis = connectTimestamp,
            durationSeconds = ((now - connectTimestamp) / 1000L).coerceAtLeast(0L),
            isOnHold = false
        )
        startDurationTimer(connectTimestamp)
    }

    fun answerIncomingCall(context: Context) {
        silenceIncomingRinger(context)
        XenoNotificationHelper.cancelCallNotification(context)
        configureCallAudioMode(context, active = true)
        XenoInCallService.answerCall()

        val now = System.currentTimeMillis()
        val current = _sessionFlow.value
        val connectTimestamp = current.connectedAtMillis ?: now
        _sessionFlow.value = current.copy(
            phase = CallPhase.ACTIVE,
            connectedAtMillis = connectTimestamp,
            durationSeconds = 0L,
            isOnHold = false,
            isRingerSilenced = true
        )
        startDurationTimer(connectTimestamp)

        // Re-apply speaker preference if user toggled speaker before answering
        if (current.isSpeakerOn) {
            setSpeakerphone(context, true)
        }
    }

    fun declineIncomingCall(context: Context) {
        silenceIncomingRinger(context)
        XenoNotificationHelper.cancelCallNotification(context)
        XenoInCallService.rejectCall()
        stopDurationTimer()
        resetAudioRouting(context)
        _sessionFlow.value = OngoingCallSession()
    }

    fun endCall(context: Context): Long {
        val finalDuration = if (_sessionFlow.value.isConnected) {
            getCurrentDurationSeconds().coerceAtLeast(1L)
        } else {
            0L
        }
        silenceIncomingRinger(context)
        XenoNotificationHelper.cancelCallNotification(context)
        XenoInCallService.endCall()
        stopDurationTimer()
        resetAudioRouting(context)
        _sessionFlow.value = OngoingCallSession()
        return finalDuration
    }

    fun silenceIncomingRinger(context: Context) {
        try {
            val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
            telecomManager?.silenceRinger()
        } catch (_: SecurityException) {
        } catch (_: Exception) {}
        _sessionFlow.value = _sessionFlow.value.copy(isRingerSilenced = true)
    }

    fun toggleSpeakerphone(context: Context): Boolean {
        val next = !_sessionFlow.value.isSpeakerOn
        setSpeakerphone(context, next)
        return next
    }

    fun setSpeakerphone(context: Context, enabled: Boolean) {
        _sessionFlow.value = _sessionFlow.value.copy(isSpeakerOn = enabled)
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            if (_sessionFlow.value.isConnected || !_sessionFlow.value.isIncoming) {
                audioManager?.mode = AudioManager.MODE_IN_COMMUNICATION
            }
            @Suppress("DEPRECATION")
            audioManager?.isSpeakerphoneOn = enabled
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (enabled) {
                    val speakerDevice = audioManager?.availableCommunicationDevices?.find {
                        it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
                    }
                    if (speakerDevice != null) {
                        audioManager.setCommunicationDevice(speakerDevice)
                    }
                } else {
                    audioManager?.clearCommunicationDevice()
                }
            }
            XenoInCallService.setSpeaker(enabled)
        } catch (e: Exception) {
            Log.w(TAG, "setSpeakerphone error: ${e.message}")
        }
    }

    fun toggleMicrophoneMute(context: Context): Boolean {
        val next = !_sessionFlow.value.isMuted
        setMicrophoneMute(context, next)
        return next
    }

    fun setMicrophoneMute(context: Context, muted: Boolean) {
        _sessionFlow.value = _sessionFlow.value.copy(isMuted = muted)
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            audioManager?.isMicrophoneMute = muted
            XenoInCallService.setMute(muted)
        } catch (e: Exception) {
            Log.w(TAG, "setMicrophoneMute error: ${e.message}")
        }
    }

    fun toggleCallHold(): Boolean {
        val next = !_sessionFlow.value.isOnHold
        XenoInCallService.setHold(next)
        _sessionFlow.value = _sessionFlow.value.copy(
            isOnHold = next,
            phase = if (next) CallPhase.HOLDING else CallPhase.ACTIVE
        )
        return next
    }

    fun sendDtmfTone(digit: Char, toneGenerator: ToneGenerator? = null) {
        _sessionFlow.value = _sessionFlow.value.copy(
            dtmfDigits = _sessionFlow.value.dtmfDigits + digit
        )
        try {
            val tone = when (digit) {
                '0' -> ToneGenerator.TONE_DTMF_0
                '1' -> ToneGenerator.TONE_DTMF_1
                '2' -> ToneGenerator.TONE_DTMF_2
                '3' -> ToneGenerator.TONE_DTMF_3
                '4' -> ToneGenerator.TONE_DTMF_4
                '5' -> ToneGenerator.TONE_DTMF_5
                '6' -> ToneGenerator.TONE_DTMF_6
                '7' -> ToneGenerator.TONE_DTMF_7
                '8' -> ToneGenerator.TONE_DTMF_8
                '9' -> ToneGenerator.TONE_DTMF_9
                '*' -> ToneGenerator.TONE_DTMF_S
                '#' -> ToneGenerator.TONE_DTMF_P
                else -> ToneGenerator.TONE_DTMF_0
            }
            toneGenerator?.startTone(tone, 120)
        } catch (_: Exception) {}
        XenoInCallService.sendDtmf(digit)
    }

    fun configureCallAudioMode(context: Context, active: Boolean) {
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
            if (active) {
                audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            }
        } catch (_: Exception) {}
    }

    fun resetAudioRouting(context: Context) {
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
            audioManager.isMicrophoneMute = false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                audioManager.clearCommunicationDevice()
            } else {
                @Suppress("DEPRECATION")
                audioManager.isSpeakerphoneOn = false
            }
            audioManager.mode = AudioManager.MODE_NORMAL
        } catch (_: Exception) {}
    }

    private fun startDurationTimer(connectedAtMillis: Long) {
        timerJob?.cancel()
        timerJob = helperScope.launch {
            while (isActive) {
                val now = System.currentTimeMillis()
                val elapsed = ((now - connectedAtMillis) / 1000L).coerceAtLeast(0L)
                val current = _sessionFlow.value
                if (current.phase == CallPhase.ACTIVE || current.phase == CallPhase.HOLDING) {
                    if (current.durationSeconds != elapsed) {
                        _sessionFlow.value = current.copy(durationSeconds = elapsed)
                    }
                } else {
                    break
                }
                delay(250L)
            }
        }
    }

    private fun stopDurationTimer() {
        timerJob?.cancel()
        timerJob = null
    }
}
