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
                    val phone = incomingNumber.trim().ifEmpty {
                        OngoingCallHelper.sessionFlow.value.phoneNumber.trim()
                    }.ifEmpty { "Private Number" }
                    if (OngoingCallHelper.shouldProcessIncomingCall(phone)) {
                        IncomingCallPopupHelper.handleIncomingCall(
                            context = context.applicationContext,
                            phoneNumber = phone
                        )
                        XenoInCallService.onIncomingCallListener?.invoke(phone)
                    }
                }
                TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                    val session = OngoingCallHelper.sessionFlow.value
                    if (session.phase == CallPhase.OUTGOING_DIALING || session.phase == CallPhase.INCOMING_RINGING) {
                        OngoingCallHelper.markCallConnectedLocally(context)
                    }
                }
                TelephonyManager.EXTRA_STATE_IDLE -> {
                    val currentSession = OngoingCallHelper.sessionFlow.value
                    val wasRingingMissed = !OngoingCallHelper.userInitiatedDecline && 
                        (currentSession.phase == CallPhase.INCOMING_RINGING || (currentSession.isIncoming && !currentSession.isConnected))
                    val missedNumber = currentSession.phoneNumber.ifBlank {
                        OngoingCallHelper.incomingPopupFlow.value?.callerNumber.orEmpty()
                    }.ifBlank { incomingNumber.trim() }
                    val missedName = currentSession.contactName ?: OngoingCallHelper.incomingPopupFlow.value?.callerName

                    if (wasRingingMissed && missedNumber.isNotBlank() && missedNumber != "Private Number") {
                        XenoNotificationHelper.showMissedCallNotification(
                            context.applicationContext,
                            missedName,
                            missedNumber
                        )
                    }
                    OngoingCallHelper.dismissIncomingCallPopup(context.applicationContext)
                    XenoInCallService.onCallEndedListener?.invoke()
                }
            }
        }
    }
}
