package com.example.floating

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            action == "android.intent.action.QUICKBOOT_POWERON"
        ) {
            Log.d("BootReceiver", "Device booted or app updated ($action). Initializing floating app service...")
            FloatingPreferences.init(context)
            val config = FloatingPreferences.configFlow.value
            if (config.startOnBoot && config.isServiceEnabled && FloatingBubbleService.isOverlayPermissionGranted(context)) {
                FloatingBubbleService.start(context)
            }
        }
    }
}
