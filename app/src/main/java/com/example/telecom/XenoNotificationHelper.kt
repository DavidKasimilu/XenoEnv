package com.example.telecom

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.MainActivity
import com.example.R

object XenoNotificationHelper {
    const val CHANNEL_SMS_ID = "xeno_sms_channel"
    const val CHANNEL_CALL_ID = "xeno_call_channel"

    private fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val smsChannel = NotificationChannel(
                CHANNEL_SMS_ID,
                "SMS Messages",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Custom notifications for incoming SMS messages"
                enableVibration(true)
                enableLights(true)
                setShowBadge(true)
            }

            val callChannel = NotificationChannel(
                CHANNEL_CALL_ID,
                "Incoming Calls",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Custom heads-up notifications for incoming phone calls"
                enableVibration(true)
                enableLights(true)
                setShowBadge(true)
            }

            notificationManager.createNotificationChannel(smsChannel)
            notificationManager.createNotificationChannel(callChannel)
        }
    }

    fun showSmsNotification(
        context: Context,
        senderName: String,
        senderNumber: String,
        body: String,
        threadId: Long
    ) {
        createNotificationChannels(context)

        // Show floating overlay popup if permission granted
        XenoOverlayService.showSmsOverlay(context, senderName, senderNumber, body, threadId)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("open_tab", "MESSAGES")
            putExtra("thread_id", threadId)
            putExtra("sender_number", senderNumber)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            (threadId and 0x7FFFFFFF).toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val markReadIntent = Intent(context, XenoNotificationActionReceiver::class.java).apply {
            action = XenoNotificationActionReceiver.ACTION_MARK_SMS_READ
            putExtra(XenoNotificationActionReceiver.EXTRA_THREAD_ID, threadId)
            putExtra(XenoNotificationActionReceiver.EXTRA_NUMBER, senderNumber)
        }
        val markReadPendingIntent = PendingIntent.getBroadcast(
            context,
            (threadId and 0x7FFFFFFF).toInt() + 200,
            markReadIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val replyIntent = Intent(context, XenoNotificationActionReceiver::class.java).apply {
            action = XenoNotificationActionReceiver.ACTION_REPLY_SMS
            putExtra(XenoNotificationActionReceiver.EXTRA_THREAD_ID, threadId)
            putExtra(XenoNotificationActionReceiver.EXTRA_NUMBER, senderNumber)
        }
        val replyPendingIntent = PendingIntent.getBroadcast(
            context,
            (threadId and 0x7FFFFFFF).toInt() + 300,
            replyIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val smsRemoteViews = RemoteViews(context.packageName, R.layout.notification_custom_sms).apply {
            setTextViewText(R.id.notif_sms_sender, senderName)
            setTextViewText(R.id.notif_sms_number, senderNumber)
            setTextViewText(R.id.notif_sms_body, body)

            setOnClickPendingIntent(R.id.btn_sms_mark_read, markReadPendingIntent)
            setOnClickPendingIntent(R.id.btn_sms_reply, replyPendingIntent)
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_SMS_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentTitle(senderName)
            .setContentText(body)
            .setCustomContentView(smsRemoteViews)
            .setCustomBigContentView(smsRemoteViews)
            .setCustomHeadsUpContentView(smsRemoteViews)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.checkbox_on_background, "Mark Read", markReadPendingIntent)
            .addAction(android.R.drawable.ic_menu_send, "Reply", replyPendingIntent)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setVibrate(longArrayOf(0, 250, 150, 250))
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(
                (threadId and 0x7FFFFFFF).toInt().coerceAtLeast(1001),
                notification
            )
        } catch (_: SecurityException) {
            } catch (_: Exception) {}
    }

    fun showCallNotification(
        context: Context,
        callerName: String?,
        callerNumber: String
    ) {
        createNotificationChannels(context)

        // Show floating overlay popup over other apps
        XenoOverlayService.showCallOverlay(context, callerName, callerNumber)

        val name = if (!callerName.isNullOrBlank()) callerName else callerNumber

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("open_tab", "CALLS")
            putExtra("incoming_number", callerNumber)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            8888,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val declineIntent = Intent(context, XenoNotificationActionReceiver::class.java).apply {
            action = XenoNotificationActionReceiver.ACTION_DECLINE_CALL
            putExtra(XenoNotificationActionReceiver.EXTRA_NUMBER, callerNumber)
        }
        val declinePendingIntent = PendingIntent.getBroadcast(
            context,
            9001,
            declineIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val answerIntent = Intent(context, XenoNotificationActionReceiver::class.java).apply {
            action = XenoNotificationActionReceiver.ACTION_ANSWER_CALL
            putExtra(XenoNotificationActionReceiver.EXTRA_NUMBER, callerNumber)
            putExtra(XenoNotificationActionReceiver.EXTRA_NAME, callerName)
        }
        val answerPendingIntent = PendingIntent.getBroadcast(
            context,
            9002,
            answerIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val callRemoteViews = RemoteViews(context.packageName, R.layout.notification_custom_call).apply {
            setTextViewText(R.id.notif_caller_name, name)
            setTextViewText(R.id.notif_caller_number, "Incoming Call ($callerNumber)")

            setOnClickPendingIntent(R.id.btn_call_decline, declinePendingIntent)
            setOnClickPendingIntent(R.id.btn_call_answer, answerPendingIntent)
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_CALL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_call)
            .setContentTitle(name)
            .setContentText("Incoming Call ($callerNumber)")
            .setCustomContentView(callRemoteViews)
            .setCustomBigContentView(callRemoteViews)
            .setCustomHeadsUpContentView(callRemoteViews)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setFullScreenIntent(pendingIntent, true)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Decline", declinePendingIntent)
            .addAction(android.R.drawable.ic_menu_call, "Answer", answerPendingIntent)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setOngoing(true)
            .setVibrate(longArrayOf(0, 500, 200, 500, 200, 500))
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(8888, notification)
        } catch (_: SecurityException) {
            } catch (_: Exception) {}
    }

    fun cancelCallNotification(context: Context) {
        XenoOverlayService.dismissCallOverlay(context)
        try {
            NotificationManagerCompat.from(context).cancel(8888)
        } catch (_: Exception) {}
    }

    fun cancelSmsNotification(context: Context, threadId: Long) {
        XenoOverlayService.dismissSmsOverlay(context)
        try {
            NotificationManagerCompat.from(context).cancel((threadId and 0x7FFFFFFF).toInt().coerceAtLeast(1001))
        } catch (_: Exception) {}
    }
}
