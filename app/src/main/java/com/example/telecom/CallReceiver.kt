package com.example.telecom

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import android.util.Log

class CallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == TelephonyManager.ACTION_PHONE_STATE_CHANGED) {
            // If XenoInCallService is already actively bound to a Telecom Call, let it manage state transitions directly.
            if (XenoInCallService.currentCall != null) {
                return
            }

            val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
            @Suppress("DEPRECATION")
            val incomingNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER) ?: ""
            Log.d("CallReceiver", "Phone state changed: $state, incoming: $incomingNumber")

            when (state) {
                TelephonyManager.EXTRA_STATE_RINGING -> {
                    if (incomingNumber.isNotBlank() && OngoingCallHelper.shouldProcessIncomingCall(incomingNumber)) {
                        val resolvedName = XenoNotificationHelper.resolveContactNameQuick(context, incomingNumber)
                        val shouldWakeFullScreen = OngoingCallHelper.isScreenOffOrAppInBackground(context)
                        OngoingCallHelper.handleIncomingCallEvent(
                            context = context.applicationContext,
                            phoneNumber = incomingNumber,
                            contactName = resolvedName,
                            showPopup = !shouldWakeFullScreen
                        )
                        XenoInCallService.onIncomingCallListener?.invoke(incomingNumber)
                    }
                }
                TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                    val session = OngoingCallHelper.sessionFlow.value
                    if (session.phase == CallPhase.OUTGOING_DIALING || session.phase == CallPhase.INCOMING_RINGING) {
                        OngoingCallHelper.markCallConnectedLocally(context)
                    }
                }
                TelephonyManager.EXTRA_STATE_IDLE -> {
                    XenoInCallService.onCallEndedListener?.invoke()
                }
            }
        }
    }
}
