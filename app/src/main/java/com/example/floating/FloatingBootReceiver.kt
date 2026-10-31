package com.example.floating

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Automatically restarts FloatingBubbleService when the device reboots or the app package is updated,
 * ensuring continuous background persistence if enabled in FloatingPreferences.
 */
class FloatingBootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        Log.d("FloatingBootReceiver", "Received broadcast action: $action")

        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            action == "android.intent.action.QUICKBOOT_POWERON"
        ) {
            FloatingPreferences.init(context)
            val config = FloatingPreferences.configFlow.value

            if (config.startOnBoot && FloatingBubbleService.isOverlayPermissionGranted(context)) {
                Log.i("FloatingBootReceiver", "Auto-starting persistent FloatingBubbleService after boot/update")
                FloatingBubbleService.start(context)
            }
        }
    }
}
