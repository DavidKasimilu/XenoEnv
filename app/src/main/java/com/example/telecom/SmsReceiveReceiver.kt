package com.example.telecom

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log

class SmsReceiveReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            try {
                val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
                if (messages != null && messages.isNotEmpty()) {
                    val address = messages[0].displayOriginatingAddress ?: messages[0].originatingAddress ?: "Unknown"
                    val fullBody = messages.joinToString(separator = "") { it.displayMessageBody ?: it.messageBody ?: "" }
                    val date = messages[0].timestampMillis
                    Log.d("SmsReceiveReceiver", "SMS_RECEIVED from $address: $fullBody")
                    XenoNotificationHelper.showSmsNotification(
                        context = context,
                        senderName = address,
                        senderNumber = address,
                        body = fullBody,
                        threadId = address.hashCode().toLong()
                    )
                    SmsDeliverReceiver.onSmsReceivedListener?.invoke(address, fullBody, date)
                }
            } catch (e: Exception) {
                Log.e("SmsReceiveReceiver", "Error processing SMS_RECEIVED intent", e)
            }
        }
    }
}
