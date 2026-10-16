package com.example.telecom

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import android.util.Log

class CallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == TelephonyManager.ACTION_PHONE_STATE_CHANGED) {
            val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
            @Suppress("DEPRECATION")
            val incomingNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER) ?: ""
            Log.d("CallReceiver", "Phone state changed: $state, incoming: $incomingNumber")
            when (state) {
                TelephonyManager.EXTRA_STATE_RINGING -> {
                    if (incomingNumber.isNotBlank()) {
                        XenoInCallService.onIncomingCallListener?.invoke(incomingNumber)
                    }
                }
                TelephonyManager.EXTRA_STATE_IDLE -> {
                    XenoInCallService.onCallEndedListener?.invoke()
                }
            }
        }
    }
}
