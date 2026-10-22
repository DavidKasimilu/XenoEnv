package com.example.telecom

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.MainActivity

class XenoNotificationActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null) return
        val action = intent.action ?: return

        when (action) {
            ACTION_ANSWER_CALL -> {
                val callerNumber = intent.getStringExtra(EXTRA_NUMBER) ?: ""
                val callerName = intent.getStringExtra(EXTRA_NAME)

                OngoingCallHelper.answerIncomingCallAndOpenFullScreen(
                    context = context,
                    phoneNumber = callerNumber,
                    contactName = callerName
                )

                val appIntent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    putExtra("open_tab", "CALLS")
                    putExtra("incoming_number", callerNumber)
                    putExtra("caller_name", callerName)
                    putExtra("auto_answer", true)
                }
                context.startActivity(appIntent)
            }

            ACTION_DECLINE_CALL -> {
                OngoingCallHelper.declineIncomingCall(context)
            }

            ACTION_MARK_SMS_READ -> {
                val threadId = intent.getLongExtra(EXTRA_THREAD_ID, -1L)
                val senderNumber = intent.getStringExtra(EXTRA_NUMBER) ?: ""
                XenoNotificationHelper.cancelSmsNotification(context, threadId, senderNumber)
            }

            ACTION_REPLY_SMS -> {
                val threadId = intent.getLongExtra(EXTRA_THREAD_ID, -1L)
                val senderNumber = intent.getStringExtra(EXTRA_NUMBER) ?: ""

                val appIntent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    putExtra("open_tab", "MESSAGES")
                    putExtra("thread_id", threadId)
                    putExtra("sender_number", senderNumber)
                }
                context.startActivity(appIntent)

                XenoNotificationHelper.cancelSmsNotification(context, threadId, senderNumber)
            }
        }
    }

    companion object {
        const val ACTION_ANSWER_CALL = "com.example.telecom.ACTION_ANSWER_CALL"
        const val ACTION_DECLINE_CALL = "com.example.telecom.ACTION_DECLINE_CALL"
        const val ACTION_MARK_SMS_READ = "com.example.telecom.ACTION_MARK_SMS_READ"
        const val ACTION_REPLY_SMS = "com.example.telecom.ACTION_REPLY_SMS"

        const val EXTRA_THREAD_ID = "extra_thread_id"
        const val EXTRA_NUMBER = "extra_number"
        const val EXTRA_NAME = "extra_name"
    }
}
