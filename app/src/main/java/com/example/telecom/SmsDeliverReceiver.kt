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

        @Volatile
        private var lastProcessedSignature: String = ""

        @Volatile
        private var lastProcessedTimeMs: Long = 0L

        /**
         * Ensures an incoming SMS broadcast is processed only once across both
         * SmsDeliverReceiver (SMS_DELIVER) and SmsReceiveReceiver (SMS_RECEIVED).
         */
        @Synchronized
        fun markSmsProcessedIfNew(address: String, body: String, windowMs: Long = 15_000L): Boolean {
            val normAddress = XenoNotificationHelper.normalizePhoneKey(address)
            val normBody = body.trim()
            val signature = "$normAddress::$normBody"
            val now = System.currentTimeMillis()
            if (signature == lastProcessedSignature && (now - lastProcessedTimeMs) < windowMs) {
                return false
            }
            lastProcessedSignature = signature
            lastProcessedTimeMs = now
            return true
        }
    }

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Telephony.Sms.Intents.SMS_DELIVER_ACTION) {
            try {
                val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
                if (!messages.isNullOrEmpty()) {
                    val address = messages[0].displayOriginatingAddress ?: messages[0].originatingAddress ?: "Unknown"
                    val fullBody = messages.joinToString(separator = "") { it.displayMessageBody ?: it.messageBody ?: "" }
                    val date = messages[0].timestampMillis.takeIf { it > 0L } ?: System.currentTimeMillis()

                    if (!markSmsProcessedIfNew(address, fullBody)) {
                        Log.d("SmsDeliverReceiver", "Duplicate SMS_DELIVER ignored for $address")
                        return
                    }

                    // Avoid inserting duplicate row if already present in Inbox within 10s
                    val alreadyInInbox = try {
                        val minDate = date - 10_000L
                        context.contentResolver.query(
                            Telephony.Sms.Inbox.CONTENT_URI,
                            arrayOf(Telephony.Sms._ID),
                            "${Telephony.Sms.ADDRESS} = ? AND ${Telephony.Sms.BODY} = ? AND ${Telephony.Sms.DATE} >= ?",
                            arrayOf(address, fullBody, minDate.toString()),
                            null
                        )?.use { cursor -> cursor.moveToFirst() } ?: false
                    } catch (_: Exception) {
                        false
                    }

                    if (!alreadyInInbox) {
                        val values = ContentValues().apply {
                            put(Telephony.Sms.ADDRESS, address)
                            put(Telephony.Sms.BODY, fullBody)
                            put(Telephony.Sms.DATE, date)
                            put(Telephony.Sms.READ, 0)
                            put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_INBOX)
                        }
                        context.contentResolver.insert(Telephony.Sms.Inbox.CONTENT_URI, values)
                    }

                    val contactName = XenoNotificationHelper.resolveContactNameQuick(context, address) ?: address
                    XenoNotificationHelper.showSmsNotification(
                        context = context,
                        senderName = contactName,
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
