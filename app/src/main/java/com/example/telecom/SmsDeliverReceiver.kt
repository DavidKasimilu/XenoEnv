package com.example.telecom

import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log

class SmsDeliverReceiver : BroadcastReceiver() {

    companion object {
        var onSmsReceivedListener: ((address: String, body: String, date: Long) -> Unit)? = null
    }

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Telephony.Sms.Intents.SMS_DELIVER_ACTION) {
            try {
                val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
                if (messages != null && messages.isNotEmpty()) {
                    val address = messages[0].displayOriginatingAddress ?: messages[0].originatingAddress ?: "Unknown"
                    val fullBody = messages.joinToString(separator = "") { it.displayMessageBody ?: it.messageBody ?: "" }
                    val date = messages[0].timestampMillis

                    val values = ContentValues().apply {
                        put(Telephony.Sms.ADDRESS, address)
                        put(Telephony.Sms.BODY, fullBody)
                        put(Telephony.Sms.DATE, date)
                        put(Telephony.Sms.READ, 0)
                        put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_INBOX)
                    }
                    context.contentResolver.insert(Telephony.Sms.Inbox.CONTENT_URI, values)
                    XenoNotificationHelper.showSmsNotification(
                        context = context,
                        senderName = address,
                        senderNumber = address,
                        body = fullBody,
                        threadId = address.hashCode().toLong()
                    )
                    onSmsReceivedListener?.invoke(address, fullBody, date)
                }
            } catch (e: Exception) {
                Log.e("SmsDeliverReceiver", "Error saving received SMS", e)
            }
        }
    }
}
