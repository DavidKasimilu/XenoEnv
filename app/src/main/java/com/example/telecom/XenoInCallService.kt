package com.example.telecom

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import android.telecom.TelecomManager
import android.telecom.VideoProfile
import android.util.Log
import androidx.core.content.ContextCompat
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

        fun answerCall(context: Context? = null) {
            try {
                if (currentCall != null) {
                    currentCall?.answer(VideoProfile.STATE_AUDIO_ONLY)
                    Log.d("XenoInCallService", "Answered call via currentCall.answer()")
                    return
                }
            } catch (e: Exception) {
                Log.w("XenoInCallService", "Failed to answer call via currentCall: ${e.message}")
            }

            // Fallback: TelecomManager.acceptRingingCall()
            if (context != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                try {
                    val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
                    val hasPerm = ContextCompat.checkSelfPermission(context, android.Manifest.permission.ANSWER_PHONE_CALLS) == PackageManager.PERMISSION_GRANTED
                    if (hasPerm) {
                        telecomManager?.acceptRingingCall()
                        Log.d("XenoInCallService", "Answered call via TelecomManager.acceptRingingCall()")
                    }
                } catch (e: Exception) {
                    Log.w("XenoInCallService", "Failed to answer call via TelecomManager: ${e.message}")
                }
            }
        }

        fun rejectCall(context: Context? = null) {
            try {
                if (currentCall != null) {
                    currentCall?.reject(false, null)
                    currentCall = null
                    return
                }
            } catch (e: Exception) {
                Log.w("XenoInCallService", "Failed to reject call via currentCall: ${e.message}")
            }

            if (context != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                try {
                    val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
                    val hasPerm = ContextCompat.checkSelfPermission(context, android.Manifest.permission.ANSWER_PHONE_CALLS) == PackageManager.PERMISSION_GRANTED
                    if (hasPerm) {
                        telecomManager?.endCall()
                        Log.d("XenoInCallService", "Rejected/ended call via TelecomManager.endCall()")
                    }
                } catch (e: Exception) {
                    Log.w("XenoInCallService", "Failed to end call via TelecomManager: ${e.message}")
                }
            }
            currentCall = null
        }

        fun endCall(context: Context? = null) {
            try {
                val call = currentCall
                if (call != null) {
                    if (call.state == Call.STATE_RINGING) {
                        call.reject(false, null)
                    } else {
                        call.disconnect()
                    }
                    currentCall = null
                    return
                }
            } catch (e: Exception) {
                Log.w("XenoInCallService", "Failed to disconnect call: ${e.message}")
            }

            if (context != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                try {
                    val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
                    val hasPerm = ContextCompat.checkSelfPermission(context, android.Manifest.permission.ANSWER_PHONE_CALLS) == PackageManager.PERMISSION_GRANTED
                    if (hasPerm) {
                        telecomManager?.endCall()
                    }
                } catch (e: Exception) {
                    Log.w("XenoInCallService", "Failed to end call via TelecomManager: ${e.message}")
                }
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

        val handle = call.details?.handle?.schemeSpecificPart?.trim().orEmpty()
        val extrasNumber = call.details?.extras?.getString(android.telephony.TelephonyManager.EXTRA_INCOMING_NUMBER)?.trim().orEmpty()
            .ifEmpty { call.details?.intentExtras?.getString("incoming_number")?.trim().orEmpty() }
            .ifEmpty { call.details?.extras?.getString("incoming_number")?.trim().orEmpty() }
            .ifEmpty { call.details?.intentExtras?.getString(android.telephony.TelephonyManager.EXTRA_INCOMING_NUMBER)?.trim().orEmpty() }
        val effectiveNumber = handle.ifEmpty { extrasNumber }.ifEmpty { "Private Number" }
        Log.d("XenoInCallService", "New call added: handle='$handle', extrasNumber='$extrasNumber' (state=${call.state})")
        Log.d("IncomingCallFlow", "onCallAdded entry: handle='$handle', extrasNumber='$extrasNumber', effectiveNumber='$effectiveNumber', state=${call.state}")

        call.registerCallback(callCallback)
        OngoingCallHelper.onTelecomCallAdded(call)

        val state = call.state
        callStateListener?.invoke(state)

        if (state == Call.STATE_RINGING) {
            if (OngoingCallHelper.shouldProcessIncomingCall(effectiveNumber)) {
                IncomingCallPopupHelper.handleIncomingCall(
                    context = applicationContext,
                    phoneNumber = effectiveNumber,
                    call = call
                )

                onIncomingCallListener?.invoke(effectiveNumber)
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
