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

        val isPriority = PriorityContactManager.isPriorityNumber(cleanedNumber, context)
        if (isPriority) {
            PriorityContactManager.enforceOutgoingPriorityBypass(context)
        }

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

        // For priority numbers, auto-resolve the preferred/first SIM account handle to bypass OS Dual-SIM prompts
        val priorityHandle = if (isPriority) {
            try {
                val prefs = context.getSharedPreferences("xeno_telecom_prefs", Context.MODE_PRIVATE)
                val preferredSlot = prefs.getInt("preferred_sim_slot", 0)
                val sims = getAvailableSimCards(context)
                (sims.find { it.subscriptionId == preferredSlot } ?: sims.firstOrNull())?.phoneAccountHandle
            } catch (_: Exception) {
                null
            }
        } else null

        // 2. TelecomManager placeCall (only if default dialer & permission granted)
        if (isDefaultDialer(context) && telecomManager != null && hasCallPermission) {
            try {
                val extras = android.os.Bundle().apply {
                    if (priorityHandle != null) {
                        putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, priorityHandle)
                    }
                }
                telecomManager.placeCall(targetUri, extras)
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
                    if (priorityHandle != null) {
                        putExtra(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, priorityHandle)
                    }
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
    fun makeCallWithSim(
        context: Context,
        phoneNumber: String,
        phoneAccountHandle: android.telecom.PhoneAccountHandle?,
        simSlotIndex: Int = 0
    ) {
        val cleanedNumber = phoneNumber.filter { it.isDigit() || it == '+' || it == '*' || it == '#' }
        if (cleanedNumber.isBlank()) return

        if (PriorityContactManager.isPriorityNumber(cleanedNumber, context)) {
            PriorityContactManager.enforceOutgoingPriorityBypass(context)
        }

        val isUssd = isUssdCode(cleanedNumber)
        val targetUri = getCallUri(cleanedNumber)
        val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
        val hasCallPermission = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.CALL_PHONE
        ) == PackageManager.PERMISSION_GRANTED
        val hasPhoneStatePermission = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED

        val resolvedHandle = phoneAccountHandle ?: run {
            if (telecomManager != null && hasPhoneStatePermission) {
                try {
                    val handles = telecomManager.callCapablePhoneAccounts
                    handles.getOrNull(simSlotIndex) ?: handles.firstOrNull()
                } catch (_: Exception) {
                    null
                }
            } else null
        }

        if (!isUssd && telecomManager != null && hasCallPermission && isDefaultDialer(context)) {
            try {
                val extras = android.os.Bundle().apply {
                    if (resolvedHandle != null) {
                        putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, resolvedHandle)
                    }
                    putInt("com.android.phone.extra.slot", simSlotIndex)
                    putInt("simSlot", simSlotIndex)
                    putInt("slot", simSlotIndex)
                }
                telecomManager.placeCall(targetUri, extras)
                return
            } catch (e: Exception) {
                android.util.Log.w("TelecomHelper", "placeCall with account failed: ${e.message}")
            }
        }

        if (hasCallPermission) {
            try {
                val intent = Intent(Intent.ACTION_CALL, targetUri).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    if (resolvedHandle != null) {
                        putExtra(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, resolvedHandle)
                    }
                    putExtra("com.android.phone.extra.slot", simSlotIndex)
                    putExtra("simSlot", simSlotIndex)
                    putExtra("slot", simSlotIndex)
                    putExtra("subscription", simSlotIndex)
                    putExtra("extra_asus_dial_use_dualsim", simSlotIndex)
                }
                context.startActivity(intent)
                return
            } catch (e: Exception) {
                android.util.Log.w("TelecomHelper", "ACTION_CALL with account failed: ${e.message}")
            }
        }

        makeCall(context, phoneNumber)
    }

    fun getPreferredSimForPriorityCall(context: Context): SimCardInfo? {
        val prefs = context.getSharedPreferences("xeno_telecom_prefs", Context.MODE_PRIVATE)
        val preferredSlot = prefs.getInt("preferred_sim_slot", 0)
        val sims = getAvailableSimCards(context)
        return sims.find { it.subscriptionId == preferredSlot } ?: sims.firstOrNull()
    }

    @android.annotation.SuppressLint("MissingPermission")
    fun getAvailableSimCards(context: Context): List<SimCardInfo> {
        val simList = mutableListOf<SimCardInfo>()
        try {
            val hasPermission = ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.READ_PHONE_STATE
            ) == PackageManager.PERMISSION_GRANTED

            val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
            val handles = if (telecomManager != null && hasPermission) {
                try {
                    telecomManager.callCapablePhoneAccounts
                } catch (_: Exception) {
                    emptyList()
                }
            } else {
                emptyList()
            }

            if (hasPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                val subManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? android.telephony.SubscriptionManager
                val activeSubs = try {
                    subManager?.activeSubscriptionInfoList.orEmpty()
                } catch (_: Exception) {
                    emptyList()
                }
                if (activeSubs.isNotEmpty()) {
                    activeSubs.sortedBy { it.simSlotIndex }.forEachIndexed { idx, subInfo ->
                        val slotIndex = if (subInfo.simSlotIndex >= 0) subInfo.simSlotIndex else idx
                        val matchedHandle = handles.find { handle ->
                            handle.id.contains(subInfo.subscriptionId.toString()) ||
                                handle.id.contains(subInfo.iccId.orEmpty())
                        } ?: handles.getOrNull(slotIndex)
                        val carrier = subInfo.carrierName?.toString()?.takeIf { it.isNotBlank() }
                            ?: subInfo.displayName?.toString()?.takeIf { it.isNotBlank() }
                            ?: "SIM ${slotIndex + 1}"
                        val number = subInfo.number?.takeIf { it.isNotBlank() }
                        simList.add(
                            SimCardInfo(
                                phoneAccountHandle = matchedHandle,
                                carrierName = carrier,
                                subscriptionId = slotIndex,
                                phoneNumber = number
                            )
                        )
                    }
                }
            }

            if (simList.isEmpty() && telecomManager != null && handles.isNotEmpty()) {
                handles.forEachIndexed { index, handle ->
                    val account = try {
                        telecomManager.getPhoneAccount(handle)
                    } catch (_: Exception) {
                        null
                    }
                    val label = account?.label?.toString()?.takeIf { it.isNotBlank() } ?: "SIM ${index + 1}"
                    val address = account?.address?.schemeSpecificPart?.takeIf { it.isNotBlank() }
                    simList.add(
                        SimCardInfo(
                            phoneAccountHandle = handle,
                            carrierName = label,
                            subscriptionId = index,
                            phoneNumber = address
                        )
                    )
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("TelecomHelper", "getAvailableSimCards failed: ${e.message}")
        }
        return simList
    }

    /**
     * Returns selectable SIM options when initiating a call so the user can always choose
     * between SIM 1 and SIM 2 (populated with real carrier/account handles when available).
     */
    fun getSimChoicesForCall(context: Context): List<SimCardInfo> {
        val detected = getAvailableSimCards(context)
        if (detected.size >= 2) return detected
        val firstSim = detected.firstOrNull()
        return listOf(
            firstSim ?: SimCardInfo(
                phoneAccountHandle = null,
                carrierName = "SIM 1 (Primary)",
                subscriptionId = 0
            ),
            SimCardInfo(
                phoneAccountHandle = null,
                carrierName = "SIM 2 (Secondary)",
                subscriptionId = 1
            )
        )
    }
}

data class SimCardInfo(
    val phoneAccountHandle: android.telecom.PhoneAccountHandle?,
    val carrierName: String,
    val subscriptionId: Int,
    val phoneNumber: String? = null
)
