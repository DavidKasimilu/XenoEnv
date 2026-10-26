package com.example.dualspace

import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager

/**
 * Runs inside the NEW work profile right after Android finishes creating it.
 * This app instance is now the "profile owner" and can manage the profile.
 */
class AdminReceiver : DeviceAdminReceiver() {

    override fun onProfileProvisioningComplete(context: Context, intent: Intent) {
        val dpm = context.getSystemService(DevicePolicyManager::class.java)
        val admin = ComponentName(context, AdminReceiver::class.java)

        dpm.setProfileName(admin, "Dual Space")

        // Enable the clone-request receiver ONLY in this profile.
        context.packageManager.setComponentEnabledSetting(
            ComponentName(context, CloneActivity::class.java),
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP
        )

        // Let the main profile send CLONE intents into this profile.
        val filter = IntentFilter(Actions.CLONE).apply {
            addCategory(Intent.CATEGORY_DEFAULT)
        }
        dpm.addCrossProfileIntentFilter(
            admin, filter, DevicePolicyManager.FLAG_PARENT_CAN_ACCESS_MANAGED
        )

        // Turn the profile on.
        dpm.setProfileEnabled(admin)
    }
}
