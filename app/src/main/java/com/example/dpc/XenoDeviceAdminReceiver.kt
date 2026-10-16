package com.example.dpc

import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.UserHandle
import android.util.Log
import android.widget.Toast

/**
 * DeviceAdminReceiver implementation to handle Managed Profile (Work Profile)
 * provisioning callbacks and profile owner initialization.
 */
class XenoDeviceAdminReceiver : DeviceAdminReceiver() {

    override fun onProfileProvisioningComplete(context: Context, intent: Intent) {
        super.onProfileProvisioningComplete(context, intent)
        Log.d(TAG, "Managed Profile provisioning completed successfully.")

        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
        val adminComponent = getComponentName(context)

        try {
            // Enable Profile Owner policies
            dpm?.let { policyManager ->
                // Enable default system apps if needed
                policyManager.setProfileName(adminComponent, "Xeno Work Profile")
                
                // Allow cross-profile intent filters for standard actions if desired
                val filter = IntentFilter().apply {
                    addAction(Intent.ACTION_SEND)
                    addAction(Intent.ACTION_SENDTO)
                    addCategory(Intent.CATEGORY_DEFAULT)
                    addDataType("*/*")
                }
                policyManager.addCrossProfileIntentFilter(
                    adminComponent,
                    filter,
                    DevicePolicyManager.FLAG_MANAGED_CAN_ACCESS_PARENT
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error configuring profile owner policies: ${e.message}", e)
        }

        // Notify user
        Toast.makeText(context, "Xeno Work Profile successfully provisioned!", Toast.LENGTH_LONG).show()
    }

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Log.d(TAG, "Xeno Device Admin enabled.")
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        Log.d(TAG, "Xeno Device Admin disabled.")
    }

    companion object {
        private const val TAG = "XenoDeviceAdmin"

        fun getComponentName(context: Context): ComponentName {
            return ComponentName(context.applicationContext, XenoDeviceAdminReceiver::class.java)
        }
    }
}
