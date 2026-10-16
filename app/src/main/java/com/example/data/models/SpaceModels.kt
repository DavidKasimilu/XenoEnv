package com.example.data.models

import android.graphics.drawable.Drawable

data class InstalledAppItem(
    val packageName: String,
    val appName: String,
    val icon: Drawable? = null
)

data class ClonedAppInstance(
    val instanceId: String,
    val packageName: String = "",
    val templateId: String = "",
    val displayName: String,
    val originalName: String,
    val targetUrl: String = "",
    val badgeTag: String,
    val badgeColorHex: String,
    val createdAt: Long = System.currentTimeMillis(),
    val lastActiveTime: Long = System.currentTimeMillis(),
    val lastUrl: String = targetUrl,
    val isDesktopMode: Boolean = false
)

object SpacePresets {
    val badgeColors = listOf(
        "#4CAF50", // Fresh Green
        "#2196F3", // Sky Blue
        "#9C27B0", // Deep Purple
        "#FF5722", // Vibrant Orange
        "#E91E63", // Rose Pink
        "#00BCD4", // Cyan
        "#FF9800", // Amber
        "#607D8B"  // Slate
    )

    val defaultBadgeLabels = listOf(
        "Clone 1",
        "Work",
        "Personal",
        "Account 2",
        "Alt",
        "Private"
    )
}
