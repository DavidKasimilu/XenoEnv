package com.example.telecom

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class CallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == TelephonyManager.ACTION_PHONE_STATE_CHANGED) {
            // If XenoInCallService is already actively bound to a Telecom Call, let it manage state transitions directly.
            if (XenoInCallService.currentCall != null) {
                return
            }

            val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
            @Suppress("DEPRECATION")
            val incomingNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)
                ?: intent.getStringExtra("incoming_number")
                ?: ""
            Log.d("CallReceiver", "Phone state changed: $state, incoming: $incomingNumber")

            when (state) {
                TelephonyManager.EXTRA_STATE_RINGING -> {
                    val rawNum = incomingNumber.trim()
                    Log.d("IncomingCallFlow", "CallReceiver RINGING: broadcast number='$rawNum'")
                    if (rawNum.isBlank()) {
                        if (XenoInCallService.currentCall != null) {
                            Log.d("IncomingCallFlow", "CallReceiver: currentCall already present, ignoring blank broadcast number")
                            return
                        }
                        Log.d("IncomingCallFlow", "CallReceiver: blank broadcast number and currentCall is null. Waiting 800ms for onCallAdded...")
                        val pendingResult = goAsync()
                        CoroutineScope(Dispatchers.Main).launch {
                            try {
                                delay(800)
                                val currentCall = XenoInCallService.currentCall
                                val activePopup = OngoingCallHelper.incomingPopupFlow.value
                                val currentSession = OngoingCallHelper.sessionFlow.value
                                val isRinging = currentSession.phase == CallPhase.INCOMING_RINGING || currentCall?.state == android.telecom.Call.STATE_RINGING

                                if (currentCall != null || activePopup != null) {
                                    Log.d("IncomingCallFlow", "CallReceiver 800ms wait finished: currentCall or activePopup already present, skipping duplicate creation")
                                    return@launch
                                }

                                val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
                                val currentState = telephonyManager?.callState
                                val stillRinging = currentState == TelephonyManager.CALL_STATE_RINGING || isRinging

                                if (stillRinging) {
                                    val sessionNum = OngoingCallHelper.sessionFlow.value.phoneNumber.trim()
                                    val phone = if (sessionNum.isNotBlank()) sessionNum else "Private Number"
                                    Log.d("IncomingCallFlow", "CallReceiver 800ms wait expired, still ringing. Fallback number='$phone'")
                                    if (OngoingCallHelper.shouldProcessIncomingCall(phone, context.applicationContext)) {
                                        IncomingCallPopupHelper.handleIncomingCall(
                                            context = context.applicationContext,
                                            phoneNumber = phone
                                        )
                                        XenoInCallService.onIncomingCallListener?.invoke(phone)
                                    }
                                } else {
                                    Log.d("IncomingCallFlow", "CallReceiver 800ms wait finished: phone is no longer ringing")
                                }
                            } finally {
                                pendingResult.finish()
                            }
                        }
                    } else {
                        if (OngoingCallHelper.shouldProcessIncomingCall(rawNum, context.applicationContext)) {
                            IncomingCallPopupHelper.handleIncomingCall(
                                context = context.applicationContext,
                                phoneNumber = rawNum
                            )
                            XenoInCallService.onIncomingCallListener?.invoke(rawNum)
                        }
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
