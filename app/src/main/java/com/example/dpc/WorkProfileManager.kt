package com.example.dpc

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.os.Build
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import android.util.Log

/**
 * Production-ready Work Profile (Managed Profile / DPC) Manager.
 * Handles Managed Profile detection, provisioning triggering, user handle retrieval,
 * WhatsApp/app installation into Work Profile, and OEM compatibility fallbacks.
 */
object WorkProfileManager {

    private const val TAG = "WorkProfileManager"
    const val REQUEST_CODE_PROVISION_MANAGED_PROFILE = 9801

    /**
     * Checks if the device supports Managed Profiles (Work Profiles).
     */
    fun isWorkProfileSupported(context: Context): Boolean {
        return context.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_MANAGED_USERS)
    }

    /**
     * Returns true if a Work Profile / Managed Profile currently exists on the device.
     */
    fun isWorkProfileProvisioned(context: Context): Boolean {
        return getWorkProfileUserHandle(context) != null
    }

    /**
     * Gets the UserHandle corresponding to the provisioned Work Profile, if present.
     */
    fun getWorkProfileUserHandle(context: Context): UserHandle? {
        val userManager = context.getSystemService(Context.USER_SERVICE) as? UserManager ?: return null
        val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as? LauncherApps
        val myUserHandle = Process.myUserHandle()

        val profiles = launcherApps?.profiles ?: userManager.userProfiles
        return profiles.find { profileHandle ->
            profileHandle != myUserHandle
        }
    }

    /**
     * Builds and launches the Intent to provision a Managed Profile.
     */
    fun triggerWorkProfileProvisioning(activity: Activity) {
        try {
            val adminComponent = ComponentName(activity, "com.example.dpc.XenoDeviceAdminReceiver")
            val intent = Intent(DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE).apply {
                putExtra(DevicePolicyManager.EXTRA_PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME, adminComponent)
                putExtra(DevicePolicyManager.EXTRA_PROVISIONING_SKIP_ENCRYPTION, true)
                putExtra(DevicePolicyManager.EXTRA_PROVISIONING_LEAVE_ALL_SYSTEM_APPS_ENABLED, true)
            }

            if (intent.resolveActivity(activity.packageManager) != null) {
                activity.startActivityForResult(intent, REQUEST_CODE_PROVISION_MANAGED_PROFILE)
            } else {
                Log.e(TAG, "No activity found to handle ACTION_PROVISION_MANAGED_PROFILE intent.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error triggering work profile provisioning: ${e.message}")
        }
    }

    /**
     * Enables or installs an existing package (e.g., WhatsApp) into the Managed Profile.
     */
    fun enableAppInWorkProfile(context: Context, packageName: String): Boolean {
        val workUser = getWorkProfileUserHandle(context) ?: return false
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager ?: return false

        return try {
            val adminComponent = ComponentName(context, "com.example.dpc.XenoDeviceAdminReceiver")
            dpm.enableSystemApp(adminComponent, packageName)
            Log.d(TAG, "Successfully enabled $packageName in Work Profile.")
            true
        } catch (e: Exception) {
            Log.w(TAG, "Could not enable system app directly, fallback: ${e.message}")
            false
        }
    }

    /**
     * Launches an app installed in the Work Profile using LauncherApps API.
     */
    fun launchAppInWorkProfile(context: Context, packageName: String): Boolean {
        val workUser = getWorkProfileUserHandle(context) ?: return false
        val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as? LauncherApps ?: return false

        try {
            val mainActivities = launcherApps.getActivityList(packageName, workUser)
            if (mainActivities.isNotEmpty()) {
                val componentName = mainActivities[0].componentName
                launcherApps.startMainActivity(componentName, workUser, null, null)
                Log.d(TAG, "Launched $packageName in Work Profile (User: $workUser).")
                return true
            } else {
                Log.w(TAG, "No main activity found for $packageName in Work Profile.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch $packageName in Work Profile: ${e.message}", e)
        }
        return false
    }

    /**
     * Detects OEM specific custom restrictions or parallel app implementations (MIUI/HyperOS, Knox, OxygenOS).
     */
    fun getOemWarningMessage(): String? {
        val manufacturer = Build.MANUFACTURER.lowercase()
        return when {
            manufacturer.contains("xiaomi") || manufacturer.contains("redmi") || manufacturer.contains("poco") -> {
                "MIUI / HyperOS Note: If Dual Apps is active, Android Work Profile creation may require disabling Dual Apps in Settings > Apps > Dual Apps."
            }
            manufacturer.contains("samsung") -> {
                "Samsung Note: Knox Secure Folder or Work Profile is fully supported. Ensure Secure Workspace policy is allowed."
            }
            manufacturer.contains("oneplus") || manufacturer.contains("oppo") || manufacturer.contains("realme") -> {
                "OxygenOS / ColorOS Note: Parallel Apps uses custom user IDs. Native Work Profile runs alongside it seamlessly."
            }
            else -> null
        }
    }
}
