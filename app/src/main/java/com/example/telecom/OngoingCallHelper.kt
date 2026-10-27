package com.example.telecom

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.PowerManager
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.TelecomManager
import android.util.Log
import com.example.MainActivity
import com.example.data.models.IncomingCallPopup
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
    val hasTelecomCall: Boolean = false,
    val isFullScreenCallVisible: Boolean = false
) {
    val isConnected: Boolean
        get() = phase == CallPhase.ACTIVE || phase == CallPhase.HOLDING

    val isRingingOrDialing: Boolean
        get() = phase == CallPhase.INCOMING_RINGING || phase == CallPhase.OUTGOING_DIALING

    val formattedDuration: String
        get() = OngoingCallHelper.formatCallDuration(durationSeconds)
}

/**
 * Centralized helper for managing:
 * 1. Incoming calls (deduplication, contact name resolution, ringer control, state management),
 * 2. Incoming call popup banner (reactive popup state synchronized with system notification & overlay),
 * 3. Full-screen call screen transition when answered (accurate call duration counting starting at 00:00
 *    and hardware audio routing for speakerphone, microphone mute, hold, and DTMF).
 * 4. Screen wake-up & full-screen incoming call launch when the device screen is turned off, locked,
 *    or when the user is on the home screen / outside the app.
 */
object OngoingCallHelper {

    private const val TAG = "OngoingCallHelper"

    private val helperScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var timerJob: Job? = null

    @Volatile
    private var incomingCallWakeLock: PowerManager.WakeLock? = null

    private val _sessionFlow = MutableStateFlow(OngoingCallSession())
    val sessionFlow: StateFlow<OngoingCallSession> = _sessionFlow.asStateFlow()

    private val _incomingPopupFlow = MutableStateFlow<IncomingCallPopup?>(null)
    val incomingPopupFlow: StateFlow<IncomingCallPopup?> = _incomingPopupFlow.asStateFlow()

    @Volatile
    private var lastIncomingNumber: String = ""

    @Volatile
    private var lastIncomingTriggerTimestamp: Long = 0L

    var onCallDeclinedListener: ((phoneNumber: String, contactName: String?) -> Unit)? = null
    var onCallAnsweredListener: ((phoneNumber: String, contactName: String?) -> Unit)? = null

    /**
     * Checks whether the device display is currently turned off or locked on the keyguard.
     * When true, incoming calls wake the display and launch directly into the full-screen
     * incoming call screen (`ActiveCallScreen`).
     */
    fun isScreenOffOrLocked(context: Context): Boolean {
        return try {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
            val isScreenOff = powerManager?.isInteractive == false
            val isDeviceLocked = keyguardManager?.isKeyguardLocked == true
            isScreenOff || isDeviceLocked
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Checks whether the device screen is on and unlocked, and the user is currently in another app
     * (or on the home screen outside Xeno).
     */
    fun isUserInAnotherAppWithScreenOn(context: Context): Boolean {
        return !isScreenOffOrLocked(context) && !MainActivity.isAppInForeground
    }

    /**
     * Legacy compatibility helper: returns true when the screen is off or locked so full-screen
     * incoming call wake is triggered, while allowing interactive popups when the screen is on.
     */
    fun isScreenOffOrAppInBackground(context: Context): Boolean {
        return isScreenOffOrLocked(context)
    }

    /**
     * Wakes up the device display hardware when an incoming call arrives while the screen is off.
     */
    @Suppress("DEPRECATION")
    fun wakeDeviceScreenForIncomingCall(context: Context) {
        try {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
            releaseIncomingWakeLock()
            val wakeLock = powerManager.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or
                    PowerManager.ACQUIRE_CAUSES_WAKEUP or
                    PowerManager.ON_AFTER_RELEASE,
                "Xeno:IncomingCallWakeLock"
            ).apply {
                setReferenceCounted(false)
            }
            wakeLock.acquire(60_000L)
            incomingCallWakeLock = wakeLock
        } catch (e: Exception) {
            Log.w(TAG, "Failed to acquire incoming call wake lock: ${e.message}")
        }
    }

    fun releaseIncomingWakeLock() {
        try {
            incomingCallWakeLock?.let { wl ->
                if (wl.isHeld) {
                    wl.release()
                }
            }
        } catch (_: Exception) {}
        incomingCallWakeLock = null
    }

    /**
     * Wakes the screen and launches MainActivity directly into the full-screen incoming call screen
     * (`ActiveCallScreen`) so the user can pickup (Answer) or hangup (Decline) when the phone screen
     * was off or locked.
     */
    fun launchFullScreenIncomingCallActivity(
        context: Context,
        phoneNumber: String,
        contactName: String?
    ) {
        wakeDeviceScreenForIncomingCall(context)
        try {
            val launchIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                putExtra("open_tab", "CALLS")
                putExtra("incoming_number", phoneNumber)
                putExtra("caller_name", contactName)
                putExtra("open_full_screen_call", true)
                putExtra("wake_full_screen_call", true)
                putExtra("incoming_call_ts", System.currentTimeMillis())
            }
            context.startActivity(launchIntent)
        } catch (e: Exception) {
            Log.w(TAG, "Could not launch full-screen incoming call activity: ${e.message}")
        }
    }

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

    /**
     * Helper function to handle an incoming call across all 3 device states (for both VIP Priority and regular contacts):
     * 1. Screen is OFF or device is locked -> Wakes the display (`wakeDeviceScreenForIncomingCall`)
     *    and immediately opens the full-screen Incoming Call Screen (`ActiveCallScreen`).
     * 2. User is in the app (Screen ON) -> Shows the interactive in-app Incoming Call Popup banner
     *    at the top of the app + system notification with Answer and Decline buttons.
     * 3. User is in another app (Screen ON) -> Shows the system heads-up Incoming Call Notification
     *    AND floating overlay call popup (`XenoOverlayService.showCallOverlay`) with Answer and Decline buttons.
     */
    fun handleIncomingCallEvent(
        context: Context,
        phoneNumber: String,
        contactName: String? = null,
        showPopup: Boolean = true,
        forceScreenOffState: Boolean? = null
    ): IncomingCallPopup {
        val cleanNumber = phoneNumber.trim().ifEmpty { "Unknown Caller" }
        // Capture screen-off/locked state BEFORE waking the screen during priority bypass
        val wasScreenOffOrLocked = forceScreenOffState ?: isScreenOffOrLocked(context)
        val isPriority = PriorityContactManager.isPriorityNumber(cleanNumber, context)
        if (isPriority) {
            PriorityContactManager.enforceIncomingPriorityBypass(
                context = context,
                isCall = true,
                wakeScreen = wasScreenOffOrLocked
            )
        }

        val resolvedName = contactName?.takeIf { it.isNotBlank() && !PriorityContactManager.isSyntheticPriorityName(it) }
            ?: XenoNotificationHelper.resolveContactNameQuick(context, cleanNumber)

        val wakeForFullScreen = !showPopup || wasScreenOffOrLocked
        val inAnotherAppWithScreenOn = !wakeForFullScreen && !MainActivity.isAppInForeground

        startIncomingSession(
            phoneNumber = cleanNumber,
            contactName = resolvedName,
            openFullScreenImmediately = wakeForFullScreen
        )

        val popup = IncomingCallPopup(
            callerName = resolvedName,
            callerNumber = cleanNumber
        )

        if (!wakeForFullScreen) {
            _incomingPopupFlow.value = popup
        } else {
            _incomingPopupFlow.value = null
            XenoOverlayService.dismissCallOverlay(context)
        }

        XenoNotificationHelper.showCallNotification(
            context = context,
            callerName = resolvedName,
            callerNumber = cleanNumber,
            useFullScreenIntent = wakeForFullScreen
        )

        if (wakeForFullScreen) {
            launchFullScreenIncomingCallActivity(
                context = context,
                phoneNumber = cleanNumber,
                contactName = resolvedName
            )
        } else if (inAnotherAppWithScreenOn) {
            XenoOverlayService.showCallOverlay(
                context = context,
                callerName = resolvedName,
                callerNumber = cleanNumber
            )
        } else {
            XenoOverlayService.dismissCallOverlay(context)
        }

        return popup
    }

    fun showIncomingCallPopup(phoneNumber: String, contactName: String?): IncomingCallPopup {
        val popup = IncomingCallPopup(
            callerName = contactName?.takeIf { it.isNotBlank() },
            callerNumber = phoneNumber
        )
        _incomingPopupFlow.value = popup
        return popup
    }

    fun dismissIncomingCallPopup(context: Context) {
        _incomingPopupFlow.value = null
        XenoOverlayService.dismissCallOverlay(context)
    }

    /**
     * Expands an active incoming call popup into the full-screen incoming call view
     * while still ringing.
     */
    fun expandIncomingCallToFullScreen(context: Context) {
        _incomingPopupFlow.value = null
        XenoOverlayService.dismissCallOverlay(context)
        val current = _sessionFlow.value
        if (current.phoneNumber.isNotBlank()) {
            _sessionFlow.value = current.copy(isFullScreenCallVisible = true)
        }
    }

    fun startOutgoingSession(phoneNumber: String, contactName: String?) {
        stopDurationTimer()
        _incomingPopupFlow.value = null
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
            hasTelecomCall = existingCall != null,
            isFullScreenCallVisible = true
        )

        if (isAlreadyActive && connectTime != null) {
            startDurationTimer(connectTime)
        }
    }

    fun startIncomingSession(
        phoneNumber: String,
        contactName: String?,
        openFullScreenImmediately: Boolean = false
    ) {
        val current = _sessionFlow.value
        val existingCall = XenoInCallService.currentCall
        val isAlreadyActive = existingCall?.state == Call.STATE_ACTIVE

        // If already showing this incoming call, only enrich contactName or visibility if needed
        if ((current.phase == CallPhase.INCOMING_RINGING || current.phase == CallPhase.ACTIVE) && current.phoneNumber == phoneNumber) {
            val updatedName = if (!contactName.isNullOrBlank() && current.contactName.isNullOrBlank()) {
                contactName
            } else {
                current.contactName
            }
            _sessionFlow.value = current.copy(
                contactName = updatedName,
                isFullScreenCallVisible = current.isFullScreenCallVisible || openFullScreenImmediately || isAlreadyActive
            )
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
            hasTelecomCall = existingCall != null,
            isFullScreenCallVisible = openFullScreenImmediately || isAlreadyActive
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
        val currentPopup = _incomingPopupFlow.value
        if (currentPopup != null && currentPopup.callerNumber == phoneNumber && currentPopup.callerName.isNullOrBlank()) {
            _incomingPopupFlow.value = currentPopup.copy(callerName = contactName)
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
                // Call is now connected! Dismiss any incoming popup and open full-screen call screen with timer at 00:00
                PriorityContactManager.stopPriorityAlertSound()
                _incomingPopupFlow.value = null
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
                    hasTelecomCall = call != null,
                    isFullScreenCallVisible = true
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
                    hasTelecomCall = call != null,
                    isFullScreenCallVisible = true
                )
                startDurationTimer(connectTimestamp)
            }

            Call.STATE_DISCONNECTED, Call.STATE_DISCONNECTING -> {
                PriorityContactManager.stopPriorityAlertSound()
                releaseIncomingWakeLock()
                _incomingPopupFlow.value = null
                val finalDuration = getCurrentDurationSeconds()
                stopDurationTimer()
                _sessionFlow.value = current.copy(
                    phase = CallPhase.DISCONNECTED,
                    durationSeconds = finalDuration,
                    hasTelecomCall = false,
                    isFullScreenCallVisible = false
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
        _incomingPopupFlow.value = null
        val current = _sessionFlow.value
        val now = System.currentTimeMillis()
        val connectTimestamp = current.connectedAtMillis ?: now
        configureCallAudioMode(context, active = true)
        _sessionFlow.value = current.copy(
            phase = CallPhase.ACTIVE,
            connectedAtMillis = connectTimestamp,
            durationSeconds = ((now - connectTimestamp) / 1000L).coerceAtLeast(0L),
            isOnHold = false,
            isFullScreenCallVisible = true
        )
        startDurationTimer(connectTimestamp)
    }

    /**
     * Helper function that answers an incoming call from any UI surface (incoming call popup,
     * system notification, floating overlay, or full-screen incoming screen), dismisses all
     * popups/notifications, starts the call duration timer at 00:00, and transitions into the
     * full-screen active call screen.
     */
    fun answerIncomingCallAndOpenFullScreen(
        context: Context,
        phoneNumber: String? = null,
        contactName: String? = null
    ): OngoingCallSession {
        silenceIncomingRinger(context)
        dismissIncomingCallPopup(context)
        XenoNotificationHelper.cancelCallNotification(context)
        configureCallAudioMode(context, active = true)
        XenoInCallService.answerCall()

        val now = System.currentTimeMillis()
        val current = _sessionFlow.value
        val effectiveNumber = phoneNumber?.takeIf { it.isNotBlank() } ?: current.phoneNumber
        val effectiveName = contactName?.takeIf { it.isNotBlank() }
            ?: current.contactName
            ?: XenoNotificationHelper.resolveContactNameQuick(context, effectiveNumber)
        val connectTimestamp = current.connectedAtMillis ?: now

        val connectedSession = current.copy(
            phoneNumber = effectiveNumber,
            contactName = effectiveName,
            isIncoming = true,
            phase = CallPhase.ACTIVE,
            connectedAtMillis = connectTimestamp,
            durationSeconds = 0L,
            isOnHold = false,
            isRingerSilenced = true,
            isFullScreenCallVisible = true
        )
        _sessionFlow.value = connectedSession
        startDurationTimer(connectTimestamp)

        // Re-apply speaker preference if user toggled speaker before answering
        if (current.isSpeakerOn) {
            setSpeakerphone(context, true)
        }
        onCallAnsweredListener?.invoke(effectiveNumber, effectiveName)
        return connectedSession
    }

    fun answerIncomingCall(context: Context) {
        answerIncomingCallAndOpenFullScreen(context)
    }

    fun declineIncomingCall(context: Context) {
        val declinedNumber = _sessionFlow.value.phoneNumber.ifBlank {
            _incomingPopupFlow.value?.callerNumber.orEmpty()
        }
        val declinedName = _sessionFlow.value.contactName ?: _incomingPopupFlow.value?.callerName
        releaseIncomingWakeLock()
        silenceIncomingRinger(context)
        dismissIncomingCallPopup(context)
        XenoNotificationHelper.cancelCallNotification(context)
        XenoInCallService.rejectCall()
        stopDurationTimer()
        resetAudioRouting(context)
        _sessionFlow.value = OngoingCallSession()
        if (declinedNumber.isNotBlank()) {
            onCallDeclinedListener?.invoke(declinedNumber, declinedName)
        }
    }

    fun endCall(context: Context): Long {
        val finalDuration = if (_sessionFlow.value.isConnected) {
            getCurrentDurationSeconds().coerceAtLeast(1L)
        } else {
            0L
        }
        releaseIncomingWakeLock()
        silenceIncomingRinger(context)
        dismissIncomingCallPopup(context)
        XenoNotificationHelper.cancelCallNotification(context)
        XenoInCallService.endCall()
        stopDurationTimer()
        resetAudioRouting(context)
        _sessionFlow.value = OngoingCallSession()
        return finalDuration
    }

    fun silenceIncomingRinger(context: Context) {
        PriorityContactManager.stopPriorityAlertSound()
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
