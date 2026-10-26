package com.example.dualspace

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.Intent
import android.os.Bundle

/** Answers the system's questions during work-profile setup, then closes. */
class ProvisioningActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        when (intent.action) {
            "android.app.action.GET_PROVISIONING_MODE" -> {
                val result = Intent().putExtra(
                    DevicePolicyManager.EXTRA_PROVISIONING_MODE,
                    DevicePolicyManager.PROVISIONING_MODE_MANAGED_PROFILE
                )
                setResult(RESULT_OK, result)
            }
            "android.app.action.ADMIN_POLICY_COMPLIANCE" -> setResult(RESULT_OK)
            else -> setResult(RESULT_CANCELED)
        }
        finish()
    }
}
