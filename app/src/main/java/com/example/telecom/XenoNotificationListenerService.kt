package com.example.telecom

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import com.example.data.models.DeviceNotification
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class XenoNotificationListenerService : NotificationListenerService() {

    companion object {
        private var instance: XenoNotificationListenerService? = null

        private val _notificationsFlow = MutableStateFlow<List<DeviceNotification>>(emptyList())
        val notificationsFlow: StateFlow<List<DeviceNotification>> = _notificationsFlow.asStateFlow()

        fun isNotificationAccessGranted(context: Context): Boolean {
            val enabledListeners = NotificationManagerCompat.getEnabledListenerPackages(context)
            return enabledListeners.contains(context.packageName)
        }

        fun tryRebind(context: Context) {
            if (isNotificationAccessGranted(context)) {
                if (instance == null) {
                    try {
                        requestRebind(ComponentName(context, XenoNotificationListenerService::class.java))
                    } catch (e: Exception) {
                        Log.d("NotificationListener", "requestRebind error: ${e.message}")
                    }
                } else {
                    instance?.syncActiveNotifications()
                }
            }
        }

        fun openNotificationSettings(context: Context) {
            val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }

        fun openAppDetailsSettings(context: Context) {
            try {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = android.net.Uri.fromParts("package", context.packageName, null)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                Log.e("NotificationListener", "Error opening app details settings", e)
            }
        }

        fun addSampleCommunicationAlerts() {
            val now = System.currentTimeMillis()
            val samples = listOf(
                DeviceNotification(
                    key = "sample_wa_1",
                    packageName = "com.whatsapp",
                    appName = "WhatsApp",
                    title = "Alex Vance (WhatsApp)",
                    text = "Hey! Let me know when you get the call details for tomorrow's meeting.",
                    postTime = now - 1000 * 60 * 2,
                    isClearable = true
                ),
                DeviceNotification(
                    key = "sample_gmail_1",
                    packageName = "com.google.android.gm",
                    appName = "Gmail",
                    title = "Google Cloud Team",
                    text = "Security alert: New deployment completed on Europe-West2 cluster.",
                    postTime = now - 1000 * 60 * 12,
                    isClearable = true
                ),
                DeviceNotification(
                    key = "sample_ig_1",
                    packageName = "com.instagram.android",
                    appName = "Instagram",
                    title = "Instagram • Direct",
                    text = "sarah_design sent you a message: Check out the new wireframe layout!",
                    postTime = now - 1000 * 60 * 25,
                    isClearable = true
                ),
                DeviceNotification(
                    key = "sample_wa_2",
                    packageName = "com.whatsapp",
                    appName = "WhatsApp",
                    title = "Carlos Ruiz (WhatsApp)",
                    text = "The audio mute and speaker functions are verified and working great.",
                    postTime = now - 1000 * 60 * 45,
                    isClearable = true
                ),
                DeviceNotification(
                    key = "sample_gmail_2",
                    packageName = "com.google.android.gm",
                    appName = "Gmail",
                    title = "GitHub Notifications",
                    text = "[PR #142] Merged: Enhanced notification scrolling & filter chips support.",
                    postTime = now - 1000 * 60 * 90,
                    isClearable = true
                ),
                DeviceNotification(
                    key = "sample_ig_2",
                    packageName = "com.instagram.android",
                    appName = "Instagram",
                    title = "Instagram Activity",
                    text = "alex_photography and 14 others liked your recent photo post.",
                    postTime = now - 1000 * 60 * 120,
                    isClearable = true
                ),
                DeviceNotification(
                    key = "sample_tg_1",
                    packageName = "org.telegram.messenger",
                    appName = "Telegram",
                    title = "Android Developers Community",
                    text = "Jetpack Compose: Best practices for LazyColumn scroll performance in 2026.",
                    postTime = now - 1000 * 60 * 180,
                    isClearable = true
                ),
                DeviceNotification(
                    key = "sample_wa_3",
                    packageName = "com.whatsapp",
                    appName = "WhatsApp",
                    title = "Dev Sprint Group",
                    text = "Reminder: Team standup scheduled at 10:00 AM UTC tomorrow.",
                    postTime = now - 1000 * 60 * 240,
                    isClearable = true
                ),
                DeviceNotification(
                    key = "sample_slack_1",
                    packageName = "com.Slack",
                    appName = "Slack",
                    title = "#announcements",
                    text = "Sprint 14 code freeze scheduled for Friday 5 PM.",
                    postTime = now - 1000 * 60 * 360,
                    isClearable = true
                )
            )
            val current = _notificationsFlow.value.filter { !it.key.startsWith("sample_") }
            _notificationsFlow.value = (samples + current)
                .distinctBy { "${it.packageName}::${it.title.trim()}::${it.text.trim()}" }
                .sortedByDescending { it.postTime }
        }

        fun simulateIncomingAlert(appName: String) {
            val now = System.currentTimeMillis()
            val notif = when (appName.lowercase()) {
                "whatsapp" -> DeviceNotification(
                    key = "sim_wa_$now",
                    packageName = "com.whatsapp",
                    appName = "WhatsApp",
                    title = "Liam Smith (WhatsApp)",
                    text = "Did you receive the ongoing call test just now?",
                    postTime = now,
                    isClearable = true
                )
                "gmail" -> DeviceNotification(
                    key = "sim_gm_$now",
                    packageName = "com.google.android.gm",
                    appName = "Gmail",
                    title = "Support Team (Gmail)",
                    text = "Ticket #4092: Your request has been successfully updated.",
                    postTime = now,
                    isClearable = true
                )
                "instagram" -> DeviceNotification(
                    key = "sim_ig_$now",
                    packageName = "com.instagram.android",
                    appName = "Instagram",
                    title = "Instagram Notification",
                    text = "elena.k liked your recent photo and commented on your story.",
                    postTime = now,
                    isClearable = true
                )
                "telegram" -> DeviceNotification(
                    key = "sim_tg_$now",
                    packageName = "org.telegram.messenger",
                    appName = "Telegram",
                    title = "Tech Channel",
                    text = "New security patch update available for download.",
                    postTime = now,
                    isClearable = true
                )
                else -> DeviceNotification(
                    key = "sim_alert_$now",
                    packageName = "com.whatsapp",
                    appName = "WhatsApp",
                    title = "New Incoming Alert",
                    text = "Sample message received from contact.",
                    postTime = now,
                    isClearable = true
                )
            }
            _notificationsFlow.value = (listOf(notif) + _notificationsFlow.value)
                .distinctBy { "${it.packageName}::${it.title.trim()}::${it.text.trim()}" }
                .sortedByDescending { it.postTime }
        }

        fun dismissNotification(key: String) {
            try {
                instance?.cancelNotification(key)
            } catch (e: Exception) {
                Log.e("NotificationListener", "Error cancelling notification $key", e)
            }
            _notificationsFlow.value = _notificationsFlow.value.filter { it.key != key }
        }

        fun clearAll() {
            try {
                instance?.cancelAllNotifications()
            } catch (e: Exception) {
                Log.e("NotificationListener", "Error cancelling all notifications", e)
            }
            _notificationsFlow.value = emptyList()
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        instance = this
        syncActiveNotifications()
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        if (instance == this) {
            instance = null
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        sbn?.let {
            syncActiveNotifications()
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        sbn?.let {
            syncActiveNotifications()
        }
    }

    private fun syncActiveNotifications() {
        try {
            val active = activeNotifications ?: return
            val pm = applicationContext.packageManager
            val myPackage = applicationContext.packageName

            val list = mutableListOf<DeviceNotification>()
            for (sbn in active) {
                val pkg = sbn.packageName

                // Skip notifications generated by this app itself
                if (pkg == myPackage) continue

                val extras = sbn.notification.extras
                val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
                val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
                    ?: extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
                    ?: ""

                // Skip completely empty notification stubs
                if (title.isBlank() && text.isBlank()) continue

                val appName = try {
                    val appInfo = pm.getApplicationInfo(sbn.packageName, 0)
                    pm.getApplicationLabel(appInfo).toString()
                } catch (_: Exception) {
                    sbn.packageName
                }

                list.add(
                    DeviceNotification(
                        key = sbn.key,
                        packageName = sbn.packageName,
                        appName = appName,
                        title = title.ifBlank { appName },
                        text = text,
                        postTime = sbn.postTime,
                        isClearable = sbn.isClearable
                    )
                )
            }
            val deduplicated = list
                .distinctBy { "${it.packageName}::${it.title.trim()}::${it.text.trim()}" }
                .sortedByDescending { it.postTime }
            _notificationsFlow.value = deduplicated
        } catch (e: Exception) {
            Log.e("NotificationListener", "Error syncing active notifications", e)
        }
    }
}
