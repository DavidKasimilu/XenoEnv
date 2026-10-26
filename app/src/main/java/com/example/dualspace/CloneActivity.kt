package com.example.dualspace

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.os.Bundle
import android.widget.Toast

/**
 * Runs inside the work profile. Receives a package name from the main profile
 * and installs (or hides) that app's copy within the profile.
 */
class CloneActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val pkg = intent?.getStringExtra(Actions.EXTRA_PKG)
        val remove = intent?.getBooleanExtra(Actions.EXTRA_REMOVE, false) ?: false
        val dpm = getSystemService(DevicePolicyManager::class.java)
        val admin = ComponentName(this, AdminReceiver::class.java)

        val msg = when {
            pkg.isNullOrBlank() -> "No package given"
            dpm == null || !dpm.isProfileOwnerApp(packageName) -> "Not running inside the Dual Space profile"
            remove -> removeApp(dpm, admin, pkg)
            else -> cloneApp(dpm, admin, pkg)
        }
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
        finish()
    }

    private fun cloneApp(dpm: DevicePolicyManager, admin: ComponentName, pkg: String): String {
        return try {
            var ok = dpm.installExistingPackage(admin, pkg)
            if (!ok) {
                // System apps (e.g. Chrome preinstalled) are enabled instead.
                try {
                    dpm.enableSystemApp(admin, pkg)
                    ok = true
                } catch (_: Exception) {
                }
            }
            if (ok) {
                dpm.setApplicationHidden(admin, pkg, false)
                DualSpaceBridge.markPackageCloned(this, pkg, true)
                "Cloned! Look for it in your launcher (briefcase badge)."
            } else {
                "Could not clone $pkg"
            }
        } catch (e: Exception) {
            "Clone failed: ${e.message}"
        }
    }

    private fun removeApp(dpm: DevicePolicyManager, admin: ComponentName, pkg: String): String {
        return try {
            dpm.setApplicationHidden(admin, pkg, true)
            DualSpaceBridge.markPackageCloned(this, pkg, false)
            "Removed from Dual Space"
        } catch (e: Exception) {
            "Remove failed: ${e.message}"
        }
    }
}
