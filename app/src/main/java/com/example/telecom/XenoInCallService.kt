package com.example.telecom

import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import android.util.Log

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
                    val route = if (enabled) CallAudioState.ROUTE_SPEAKER else CallAudioState.ROUTE_EARPIECE
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

        fun answerCall() {
            try {
                currentCall?.answer(android.telecom.VideoProfile.STATE_AUDIO_ONLY)
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
                currentCall?.disconnect()
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

    override fun onCallAdded(call: Call?) {
        super.onCallAdded(call)
        instance = this
        currentCall = call
        Log.d("XenoInCallService", "New call added: ${call?.details?.handle}")
        call?.registerCallback(object : Call.Callback() {
            override fun onStateChanged(call: Call?, state: Int) {
                super.onStateChanged(call, state)
                Log.d("XenoInCallService", "Call state changed to $state")
                callStateListener?.invoke(state)
                if (state == Call.STATE_DISCONNECTED) {
                    XenoNotificationHelper.cancelCallNotification(applicationContext)
                    onCallEndedListener?.invoke()
                }
            }
        })
        val state = call?.state ?: Call.STATE_DIALING
        callStateListener?.invoke(state)
        if (state == Call.STATE_RINGING) {
            val handle = call?.details?.handle?.schemeSpecificPart ?: ""
            if (handle.isNotBlank()) {
                XenoNotificationHelper.showCallNotification(applicationContext, null, handle)
                onIncomingCallListener?.invoke(handle)
            }
        }
    }

    override fun onCallRemoved(call: Call?) {
        super.onCallRemoved(call)
        XenoNotificationHelper.cancelCallNotification(applicationContext)
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
