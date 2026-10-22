package com.example.telecom

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log

class SmsReceiveReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            // When Xeno is the default SMS app, SmsDeliverReceiver (SMS_DELIVER_ACTION) handles incoming SMS.
            // Ignoring SMS_RECEIVED_ACTION here prevents duplicate notifications and double callbacks.
            if (TelecomHelper.isDefaultSmsApp(context)) {
                return
            }

            try {
                val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
                if (!messages.isNullOrEmpty()) {
                    val address = messages[0].displayOriginatingAddress ?: messages[0].originatingAddress ?: "Unknown"
                    val fullBody = messages.joinToString(separator = "") { it.displayMessageBody ?: it.messageBody ?: "" }
                    val date = messages[0].timestampMillis.takeIf { it > 0L } ?: System.currentTimeMillis()

                    if (!SmsDeliverReceiver.markSmsProcessedIfNew(address, fullBody)) {
                        Log.d("SmsReceiveReceiver", "Duplicate SMS_RECEIVED ignored for $address")
                        return
                    }

                    Log.d("SmsReceiveReceiver", "SMS_RECEIVED from $address: $fullBody")
                    val contactName = XenoNotificationHelper.resolveContactNameQuick(context, address) ?: address
                    XenoNotificationHelper.showSmsNotification(
                        context = context,
                        senderName = contactName,
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
