package com.example.telecom

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.provider.ContactsContract
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object XenoNotificationHelper {
    const val CHANNEL_SMS_ID = "xeno_sms_channel_v2"
    const val CHANNEL_CALL_ID = "xeno_call_channel_v2"

    @Volatile
    private var lastSmsSignature: String = ""

    @Volatile
    private var lastSmsNotificationTimeMs: Long = 0L

    @Volatile
    private var lastCallNotificationNumber: String = ""

    @Volatile
    private var lastCallNotificationTimeMs: Long = 0L

    fun normalizePhoneKey(address: String): String {
        val digits = address.filter { it.isDigit() }
        return if (digits.length >= 7) digits.takeLast(9) else address.trim().lowercase()
    }

    fun getSmsNotificationId(senderNumber: String, fallbackThreadId: Long = 0L): Int {
        val key = normalizePhoneKey(senderNumber)
        val hash = if (key.isNotEmpty()) key.hashCode() else fallbackThreadId.toInt()
        return (hash and 0x7FFFFFFF).coerceAtLeast(1001)
    }

    fun extractInitials(displayName: String?, fallbackNumber: String): String {
        val clean = displayName?.trim().orEmpty()
        if (clean.isNotEmpty() && clean.any { it.isLetter() }) {
            val parts = clean.split(Regex("\\s+")).filter { it.isNotBlank() }
            return if (parts.size >= 2) {
                "${parts[0].first().uppercaseChar()}${parts[1].first().uppercaseChar()}"
            } else {
                parts[0].take(2).uppercase(Locale.getDefault())
            }
        }
        val digits = fallbackNumber.filter { it.isDigit() }
        return if (digits.isNotEmpty()) digits.takeLast(2) else "#"
    }

    fun formatNotificationTime(timestampMs: Long = System.currentTimeMillis()): String {
        return try {
            SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(timestampMs))
        } catch (_: Exception) {
            "Just now"
        }
    }

    /**
     * Thread-safe deduplication check to ensure the exact same incoming SMS
     * is never notified more than once even if multiple receivers/listeners fire.
     */
    @Synchronized
    fun shouldNotifySms(
        senderNumber: String,
        body: String,
        dedupWindowMs: Long = 15_000L
    ): Boolean {
        val normSender = normalizePhoneKey(senderNumber)
        val normBody = body.trim()
        val signature = "$normSender::$normBody"
        val now = System.currentTimeMillis()
        if (signature == lastSmsSignature && (now - lastSmsNotificationTimeMs) < dedupWindowMs) {
            return false
        }
        lastSmsSignature = signature
        lastSmsNotificationTimeMs = now
        return true
    }

    fun resolveContactNameQuick(context: Context, phoneNumber: String): String? {
        if (phoneNumber.isBlank()) return null
        val hasContactsPerm = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasContactsPerm) return null

        return try {
            val lookupUri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(phoneNumber)
            )
            context.contentResolver.query(
                lookupUri,
                arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME)
                    if (idx != -1) cursor.getString(idx)?.takeIf { it.isNotBlank() } else null
                } else null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val smsChannel = NotificationChannel(
                CHANNEL_SMS_ID,
                "SMS Messages",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Redesigned notifications for incoming SMS messages"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 220, 120, 220)
                enableLights(true)
                setShowBadge(true)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            }

            val ringtoneUri = try {
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            } catch (_: Exception) {
                null
            }
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

            val callChannel = NotificationChannel(
                CHANNEL_CALL_ID,
                "Incoming Calls",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Heads-up popup and lockscreen notifications for incoming phone calls"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 250, 500, 250, 500)
                enableLights(true)
                setShowBadge(true)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
                if (ringtoneUri != null) {
                    setSound(ringtoneUri, audioAttributes)
                }
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
        // Prevent duplicate notifications for the same incoming SMS
        if (!shouldNotifySms(senderNumber, body)) {
            return
        }

        createNotificationChannels(context)

        val resolvedName = if (senderName.isBlank() || senderName == senderNumber) {
            resolveContactNameQuick(context, senderNumber) ?: senderName.ifBlank { senderNumber }
        } else {
            senderName
        }

        val initials = extractInitials(resolvedName, senderNumber)
        val formattedTime = formatNotificationTime()
        val displaySubtitle = if (resolvedName != senderNumber && senderNumber.isNotBlank()) {
            senderNumber
        } else {
            "Direct SMS Message"
        }

        val notificationId = getSmsNotificationId(senderNumber, threadId)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("open_tab", "MESSAGES")
            putExtra("thread_id", threadId)
            putExtra("sender_number", senderNumber)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
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
            notificationId + 200,
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
            notificationId + 300,
            replyIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val smsCollapsedRemoteViews = RemoteViews(context.packageName, R.layout.notification_custom_sms_collapsed).apply {
            setTextViewText(R.id.notif_sms_avatar_text, initials)
            setTextViewText(R.id.notif_sms_sender, resolvedName)
            setTextViewText(R.id.notif_sms_time, formattedTime)
            setTextViewText(R.id.notif_sms_body, body)
        }

        val smsExpandedRemoteViews = RemoteViews(context.packageName, R.layout.notification_custom_sms).apply {
            setTextViewText(R.id.notif_sms_avatar_text, initials)
            setTextViewText(R.id.notif_sms_sender, resolvedName)
            setTextViewText(R.id.notif_sms_number, displaySubtitle)
            setTextViewText(R.id.notif_sms_time, formattedTime)
            setTextViewText(R.id.notif_sms_body, body)

            setOnClickPendingIntent(R.id.btn_sms_mark_read, markReadPendingIntent)
            setOnClickPendingIntent(R.id.btn_sms_reply, replyPendingIntent)
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_SMS_ID)
            .setSmallIcon(android.R.drawable.sym_action_chat)
            .setContentTitle(resolvedName)
            .setContentText(body)
            .setSubText("SMS • $formattedTime")
            .setCustomContentView(smsCollapsedRemoteViews)
            .setCustomBigContentView(smsExpandedRemoteViews)
            .setCustomHeadsUpContentView(smsExpandedRemoteViews)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(pendingIntent)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setVibrate(longArrayOf(0, 220, 120, 220))
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()

        var notifiedSystem = false
        try {
            val nm = NotificationManagerCompat.from(context)
            if (nm.areNotificationsEnabled()) {
                nm.notify(notificationId, notification)
                notifiedSystem = true
            }
        } catch (_: SecurityException) {
        } catch (_: Exception) {}

        // Only show floating overlay if system notifications are disabled, avoiding double popups
        if (!notifiedSystem) {
            XenoOverlayService.showSmsOverlay(context, resolvedName, senderNumber, body, threadId)
        }
    }

    @Synchronized
    fun showCallNotification(
        context: Context,
        callerName: String?,
        callerNumber: String
    ) {
        val normCaller = normalizePhoneKey(callerNumber)
        val now = System.currentTimeMillis()
        if (normCaller == lastCallNotificationNumber && (now - lastCallNotificationTimeMs) < 3_000L) {
            return
        }
        lastCallNotificationNumber = normCaller
        lastCallNotificationTimeMs = now

        createNotificationChannels(context)

        val resolvedName = if (!callerName.isNullOrBlank()) {
            callerName
        } else {
            resolveContactNameQuick(context, callerNumber) ?: callerNumber
        }
        val initials = extractInitials(resolvedName, callerNumber)
        val subtitleText = if (resolvedName != callerNumber && callerNumber.isNotBlank()) {
            "Incoming Voice Call • $callerNumber"
        } else {
            "Incoming Voice Call"
        }

        val fullScreenIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("open_tab", "CALLS")
            putExtra("incoming_number", callerNumber)
            putExtra("caller_name", resolvedName)
            putExtra("open_full_screen_call", true)
            putExtra("wake_full_screen_call", true)
        }

        val fullScreenPendingIntent = PendingIntent.getActivity(
            context,
            8888,
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val declineIntent = Intent(context, XenoNotificationActionReceiver::class.java).apply {
            action = XenoNotificationActionReceiver.ACTION_DECLINE_CALL
            putExtra(XenoNotificationActionReceiver.EXTRA_NUMBER, callerNumber)
            putExtra(XenoNotificationActionReceiver.EXTRA_NAME, resolvedName)
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
            putExtra(XenoNotificationActionReceiver.EXTRA_NAME, resolvedName)
        }
        val answerPendingIntent = PendingIntent.getBroadcast(
            context,
            9002,
            answerIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val callCollapsedViews = RemoteViews(context.packageName, R.layout.notification_custom_call_collapsed).apply {
            setTextViewText(R.id.notif_call_avatar_text, initials)
            setTextViewText(R.id.notif_caller_name, resolvedName)
            setTextViewText(R.id.notif_caller_number, subtitleText)

            setOnClickPendingIntent(R.id.btn_call_decline, declinePendingIntent)
            setOnClickPendingIntent(R.id.btn_call_answer, answerPendingIntent)
        }

        val callExpandedViews = RemoteViews(context.packageName, R.layout.notification_custom_call).apply {
            setTextViewText(R.id.notif_call_avatar_text, initials)
            setTextViewText(R.id.notif_caller_name, resolvedName)
            setTextViewText(R.id.notif_caller_number, subtitleText)

            setOnClickPendingIntent(R.id.btn_call_decline, declinePendingIntent)
            setOnClickPendingIntent(R.id.btn_call_answer, answerPendingIntent)
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_CALL_ID)
            .setSmallIcon(android.R.drawable.sym_action_call)
            .setContentTitle(resolvedName)
            .setContentText(subtitleText)
            .setCustomContentView(callCollapsedViews)
            .setCustomBigContentView(callExpandedViews)
            .setCustomHeadsUpContentView(callExpandedViews)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setAutoCancel(false)
            .setOnlyAlertOnce(false)
            .setContentIntent(fullScreenPendingIntent)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setOngoing(true)
            .setVibrate(longArrayOf(0, 500, 250, 500, 250, 500))
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(8888, notification)
        } catch (_: SecurityException) {
        } catch (_: Exception) {}
    }

    fun cancelCallNotification(context: Context) {
        lastCallNotificationNumber = ""
        lastCallNotificationTimeMs = 0L
        XenoOverlayService.dismissCallOverlay(context)
        try {
            NotificationManagerCompat.from(context).cancel(8888)
        } catch (_: Exception) {}
    }

    fun cancelSmsNotification(context: Context, threadId: Long, senderNumber: String = "") {
        XenoOverlayService.dismissSmsOverlay(context)
        try {
            val nm = NotificationManagerCompat.from(context)
            if (senderNumber.isNotBlank()) {
                nm.cancel(getSmsNotificationId(senderNumber, threadId))
            }
            nm.cancel((threadId and 0x7FFFFFFF).toInt().coerceAtLeast(1001))
        } catch (_: Exception) {}
    }
}
