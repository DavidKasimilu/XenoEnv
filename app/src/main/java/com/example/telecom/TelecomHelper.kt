package com.example.telecom

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Telephony
import android.telecom.TelecomManager
import androidx.core.content.ContextCompat

object TelecomHelper {

    fun isDefaultDialer(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(RoleManager::class.java)
            roleManager?.isRoleHeld(RoleManager.ROLE_DIALER) == true
        } else {
            val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
            telecomManager?.defaultDialerPackage == context.packageName
        }
    }

    fun createRequestDefaultDialerIntent(context: Context): Intent? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(RoleManager::class.java)
            if (roleManager?.isRoleAvailable(RoleManager.ROLE_DIALER) == true) {
                roleManager.createRequestRoleIntent(RoleManager.ROLE_DIALER)
            } else {
                Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER).apply {
                    putExtra(TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME, context.packageName)
                }
            }
        } else {
            @Suppress("DEPRECATION")
            Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER).apply {
                putExtra(TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME, context.packageName)
            }
        }
    }

    fun isDefaultSmsApp(context: Context): Boolean {
        val defaultSmsPackage = Telephony.Sms.getDefaultSmsPackage(context)
        return defaultSmsPackage == context.packageName
    }

    fun createRequestDefaultSmsIntent(context: Context): Intent? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(RoleManager::class.java)
            if (roleManager?.isRoleAvailable(RoleManager.ROLE_SMS) == true) {
                roleManager.createRequestRoleIntent(RoleManager.ROLE_SMS)
            } else {
                Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT).apply {
                    putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, context.packageName)
                }
            }
        } else {
            Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT).apply {
                putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, context.packageName)
            }
        }
    }

    fun isUssdCode(number: String): Boolean {
        val clean = number.trim()
        return clean.contains("#") || clean.startsWith("*")
    }

    fun getCallUri(cleanedNumber: String): Uri {
        return if (isUssdCode(cleanedNumber)) {
            // USSD requires literal '*' and URL-encoded '#' as '%23'
            Uri.parse("tel:" + cleanedNumber.replace("#", "%23"))
        } else {
            Uri.fromParts("tel", cleanedNumber, null)
        }
    }

    fun makeCall(context: Context, phoneNumber: String) {
        val cleanedNumber = phoneNumber.filter { it.isDigit() || it == '+' || it == '*' || it == '#' }
        if (cleanedNumber.isBlank()) return

        val targetUri = getCallUri(cleanedNumber)
        val hasCallPermission = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.CALL_PHONE
        ) == PackageManager.PERMISSION_GRANTED

        val isUssd = isUssdCode(cleanedNumber)
        val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager

        // 1. If USSD, use ACTION_CALL if permitted, else ACTION_DIAL
        if (isUssd) {
            if (hasCallPermission) {
                try {
                    val intent = Intent(Intent.ACTION_CALL, targetUri).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    return
                } catch (_: Exception) {}
            }
            try {
                val intent = Intent(Intent.ACTION_DIAL, targetUri).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                return
            } catch (_: Exception) {}
        }

        // 2. TelecomManager placeCall (only if default dialer & permission granted)
        if (isDefaultDialer(context) && telecomManager != null && hasCallPermission) {
            try {
                telecomManager.placeCall(targetUri, android.os.Bundle())
                return
            } catch (e: Exception) {
                android.util.Log.w("TelecomHelper", "telecomManager.placeCall failed: ${e.message}")
            }
        }

        // 3. Direct ACTION_CALL Intent
        if (hasCallPermission) {
            try {
                val intent = Intent(Intent.ACTION_CALL, targetUri).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                return
            } catch (e: Exception) {
                android.util.Log.w("TelecomHelper", "ACTION_CALL failed: ${e.message}")
            }
        }

        // 4. Guaranteed fallback to ACTION_DIAL (always works without CALL_PHONE permission or default dialer restriction)
        try {
            val intent = Intent(Intent.ACTION_DIAL, targetUri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            android.util.Log.e("TelecomHelper", "ACTION_DIAL failed: ${e.message}")
        }
    }

    @android.annotation.SuppressLint("MissingPermission")
    fun makeCallWithSim(context: Context, phoneNumber: String, phoneAccountHandle: android.telecom.PhoneAccountHandle?) {
        val cleanedNumber = phoneNumber.filter { it.isDigit() || it == '+' || it == '*' || it == '#' }
        if (cleanedNumber.isBlank()) return

        val isUssd = isUssdCode(cleanedNumber)
        val targetUri = getCallUri(cleanedNumber)
        val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
        val hasCallPermission = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.CALL_PHONE
        ) == PackageManager.PERMISSION_GRANTED

        if (!isUssd && phoneAccountHandle != null && telecomManager != null && hasCallPermission && isDefaultDialer(context)) {
            try {
                val extras = android.os.Bundle().apply {
                    putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, phoneAccountHandle)
                }
                telecomManager.placeCall(targetUri, extras)
                return
            } catch (e: Exception) {
                android.util.Log.w("TelecomHelper", "placeCall with account failed: ${e.message}")
            }
        }

        if (phoneAccountHandle != null && hasCallPermission) {
            try {
                val intent = Intent(Intent.ACTION_CALL, targetUri).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    putExtra(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, phoneAccountHandle)
                }
                context.startActivity(intent)
                return
            } catch (e: Exception) {
                android.util.Log.w("TelecomHelper", "ACTION_CALL with account failed: ${e.message}")
            }
        }

        makeCall(context, phoneNumber)
    }

    @android.annotation.SuppressLint("MissingPermission")
    fun getAvailableSimCards(context: Context): List<SimCardInfo> {
        val simList = mutableListOf<SimCardInfo>()
        try {
            val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
            if (telecomManager != null) {
                val hasPermission = ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.READ_PHONE_STATE
                ) == PackageManager.PERMISSION_GRANTED

                if (hasPermission) {
                    val handles = telecomManager.callCapablePhoneAccounts
                    handles.forEachIndexed { index, handle ->
                        val account = telecomManager.getPhoneAccount(handle)
                        val label = account?.label?.toString() ?: "SIM ${index + 1}"
                        simList.add(SimCardInfo(handle, label, index))
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("TelecomHelper", "getAvailableSimCards failed: ${e.message}")
        }
        return simList
    }
}

data class SimCardInfo(
    val phoneAccountHandle: android.telecom.PhoneAccountHandle?,
    val carrierName: String,
    val subscriptionId: Int
)
