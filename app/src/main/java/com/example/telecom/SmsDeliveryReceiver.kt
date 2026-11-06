package com.example.telecom

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.data.SmsRepository

/**
 * Manifest-declared BroadcastReceiver for SMS sent and delivered callbacks.
 * Operates even when the application UI is closed, ensuring delivery states
 * survive app termination and reload.
 */
class SmsDeliveryReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null) return
        val action = intent.action ?: return
        if (action != SmsRepository.ACTION_SMS_SENT && action != SmsRepository.ACTION_SMS_DELIVERED) {
            return
        }

        val messageId = intent.getLongExtra(SmsRepository.EXTRA_MESSAGE_ID, -1L)
        val providerRowId = intent.getLongExtra(SmsRepository.EXTRA_PROVIDER_ROW_ID, -1L).takeIf { it != -1L }
        val rCode = resultCode

        Log.d(
            "SmsDeliveryReceiver",
            "Manifest delivery broadcast: action=$action, msgId=$messageId, provId=$providerRowId, resultCode=$rCode"
        )

        SmsRepository.handleStaticDeliveryBroadcast(
            context = context.applicationContext,
            action = action,
            resultCode = rCode,
            messageId = messageId,
            providerRowId = providerRowId
        )
    }
}
