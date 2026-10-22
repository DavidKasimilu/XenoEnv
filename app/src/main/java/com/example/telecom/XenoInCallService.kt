package com.example.telecom

import android.content.Intent
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import android.telecom.VideoProfile
import android.util.Log
import com.example.MainActivity

class XenoInCallService : InCallService() {

    companion object {
        var instance: XenoInCallService? = null
        var currentCall: Call? = null
        var callStateListener: ((Int) -> Unit)? = null
        var onIncomingCallListener: ((number: String) -> Unit)? = null
        var onCallEndedListener: (() -> Unit)? = null

        fun setSpeaker(enabled: Boolean) {
            try {
                instance?.let { service ->
                    val route = if (enabled) CallAudioState.ROUTE_SPEAKER else CallAudioState.ROUTE_WIRED_OR_EARPIECE
                    service.setAudioRoute(route)
                }
            } catch (e: Exception) {
                Log.w("XenoInCallService", "Failed to set speaker route: ${e.message}")
            }
        }

        fun setMute(muted: Boolean) {
            try {
                instance?.setMuted(muted)
            } catch (e: Exception) {
                Log.w("XenoInCallService", "Failed to set mute: ${e.message}")
            }
        }

        fun setHold(onHold: Boolean) {
            try {
                if (onHold) {
                    currentCall?.hold()
                } else {
                    currentCall?.unhold()
                }
            } catch (e: Exception) {
                Log.w("XenoInCallService", "Failed to set hold: ${e.message}")
            }
        }

        fun answerCall() {
            try {
                currentCall?.answer(VideoProfile.STATE_AUDIO_ONLY)
            } catch (e: Exception) {
                Log.w("XenoInCallService", "Failed to answer call: ${e.message}")
            }
        }

        fun rejectCall() {
            try {
                currentCall?.reject(false, null)
            } catch (e: Exception) {
                Log.w("XenoInCallService", "Failed to reject call: ${e.message}")
            }
            currentCall = null
        }

        fun endCall() {
            try {
                val call = currentCall
                if (call != null) {
                    if (call.state == Call.STATE_RINGING) {
                        call.reject(false, null)
                    } else {
                        call.disconnect()
                    }
                }
            } catch (e: Exception) {
                Log.w("XenoInCallService", "Failed to disconnect call: ${e.message}")
            }
            currentCall = null
        }

        fun sendDtmf(digit: Char) {
            try {
                currentCall?.playDtmfTone(digit)
                currentCall?.stopDtmfTone()
            } catch (e: Exception) {
                Log.w("XenoInCallService", "Failed to send DTMF: ${e.message}")
            }
        }

        fun playDtmf(digit: Char) = sendDtmf(digit)
    }

    private val callCallback = object : Call.Callback() {
        override fun onStateChanged(call: Call?, state: Int) {
            super.onStateChanged(call, state)
            Log.d("XenoInCallService", "Call state changed to $state")
            OngoingCallHelper.onTelecomCallStateChanged(call, state)
            callStateListener?.invoke(state)

            when (state) {
                Call.STATE_ACTIVE -> {
                    XenoNotificationHelper.cancelCallNotification(applicationContext)
                }
                Call.STATE_DISCONNECTED -> {
                    XenoNotificationHelper.cancelCallNotification(applicationContext)
                    onCallEndedListener?.invoke()
                }
            }
        }
    }

    override fun onCallAudioStateChanged(audioState: CallAudioState?) {
        super.onCallAudioStateChanged(audioState)
        OngoingCallHelper.onCallAudioStateChanged(audioState)
    }

    override fun onCallAdded(call: Call?) {
        super.onCallAdded(call)
        instance = this
        currentCall = call
        if (call == null) return

        val handle = call.details?.handle?.schemeSpecificPart ?: ""
        Log.d("XenoInCallService", "New call added: $handle (state=${call.state})")

        call.registerCallback(callCallback)
        OngoingCallHelper.onTelecomCallAdded(call)

        val state = call.state
        callStateListener?.invoke(state)

        if (state == Call.STATE_RINGING) {
            if (handle.isNotBlank() && OngoingCallHelper.shouldProcessIncomingCall(handle)) {
                val resolvedName = XenoNotificationHelper.resolveContactNameQuick(applicationContext, handle)
                OngoingCallHelper.handleIncomingCallEvent(
                    context = applicationContext,
                    phoneNumber = handle,
                    contactName = resolvedName,
                    showPopup = true
                )

                if (onIncomingCallListener != null) {
                    onIncomingCallListener?.invoke(handle)
                } else {
                    try {
                        val launchIntent = Intent(applicationContext, MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                            putExtra("open_tab", "CALLS")
                            putExtra("incoming_number", handle)
                            putExtra("caller_name", resolvedName)
                        }
                        applicationContext.startActivity(launchIntent)
                    } catch (e: Exception) {
                        Log.w("XenoInCallService", "Could not launch MainActivity for incoming call: ${e.message}")
                    }
                }
            }
        }
    }

    override fun onCallRemoved(call: Call?) {
        super.onCallRemoved(call)
        try {
            call?.unregisterCallback(callCallback)
        } catch (_: Exception) {}
        XenoNotificationHelper.cancelCallNotification(applicationContext)
        OngoingCallHelper.onTelecomCallStateChanged(call, Call.STATE_DISCONNECTED)
        if (currentCall == call) {
            currentCall = null
        }
        if (instance == this) {
            instance = null
        }
        onCallEndedListener?.invoke()
        Log.d("XenoInCallService", "Call removed")
    }
}
