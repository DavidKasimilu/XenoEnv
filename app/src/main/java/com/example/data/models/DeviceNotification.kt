package com.example.data.models

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class DeviceNotification(
    val key: String,
    val packageName: String,
    val appName: String,
    val title: String,
    val text: String,
    val postTime: Long = System.currentTimeMillis(),
    val isClearable: Boolean = true
) {
    val isCommunicationOrSocial: Boolean
        get() {
            val pkg = packageName.lowercase()
            val name = appName.lowercase()
            return pkg.contains("gmail") || pkg.contains(".gm") || name.contains("gmail") ||
                    pkg.contains("whatsapp") || name.contains("whatsapp") ||
                    pkg.contains("instagram") || name.contains("instagram") ||
                    pkg.contains("telegram") || name.contains("telegram") ||
                    pkg.contains("outlook") || name.contains("outlook") ||
                    pkg.contains("messenger") || pkg.contains("orca") ||
                    pkg.contains("twitter") || pkg.contains(".x.") || name == "x" ||
                    pkg.contains("discord") || name.contains("discord") ||
                    pkg.contains("slack") || name.contains("slack") ||
                    pkg.contains("reddit") || name.contains("reddit") ||
                    pkg.contains("tiktok") || name.contains("tiktok") ||
                    pkg.contains("facebook") || name.contains("facebook") ||
                    pkg.contains("snapchat") || name.contains("snapchat") ||
                    pkg.contains("linkedin") || name.contains("linkedin") ||
                    pkg.contains("messaging") || pkg.contains("mms") || pkg.contains("sms")
        }

    val appCategory: String
        get() {
            val pkg = packageName.lowercase()
            val name = appName.lowercase()
            return when {
                pkg.contains("whatsapp") || name.contains("whatsapp") -> "WhatsApp"
                pkg.contains("gmail") || pkg.contains(".gm") || name.contains("gmail") -> "Gmail"
                pkg.contains("instagram") || name.contains("instagram") -> "Instagram"
                pkg.contains("telegram") || name.contains("telegram") -> "Telegram"
                pkg.contains("outlook") || name.contains("outlook") -> "Outlook"
                pkg.contains("discord") || name.contains("discord") -> "Discord"
                pkg.contains("slack") || name.contains("slack") -> "Slack"
                pkg.contains("twitter") || pkg.contains(".x.") || name == "x" -> "X / Twitter"
                pkg.contains("messenger") || pkg.contains("orca") -> "Messenger"
                pkg.contains("messaging") || pkg.contains("sms") -> "Messages"
                isCommunicationOrSocial -> "Communication"
                else -> "App"
            }
        }

    val appColorHex: String
        get() {
            val pkg = packageName.lowercase()
            return when {
                pkg.contains("whatsapp") -> "#25D366"
                pkg.contains("gmail") || pkg.contains(".gm") -> "#EA4335"
                pkg.contains("instagram") -> "#E4405F"
                pkg.contains("telegram") -> "#24A1DE"
                pkg.contains("outlook") -> "#0078D4"
                pkg.contains("discord") -> "#5865F2"
                pkg.contains("slack") -> "#4A154B"
                pkg.contains("twitter") || pkg.contains(".x.") -> "#1DA1F2"
                pkg.contains("messenger") || pkg.contains("orca") -> "#0084FF"
                pkg.contains("tiktok") -> "#000000"
                else -> "#2196F3"
            }
        }

    val formattedTime: String
        get() {
            val format = SimpleDateFormat("h:mm a", Locale.getDefault())
            return format.format(Date(postTime))
        }

    val formattedDate: String
        get() {
            val format = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
            return format.format(Date(postTime))
        }
}
