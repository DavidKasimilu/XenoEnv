package com.example.telecom

import android.content.Context
import android.content.Intent
import android.os.Build
import android.telecom.Call
import android.telecom.TelecomManager
import android.telephony.TelephonyManager
import android.util.Log
import com.example.MainActivity
import com.example.data.models.IncomingCallPopup
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Dedicated helper to streamline incoming call popups across all four environments:
 * 1. In the app (Foreground Compose overlay)
 * 2. In another app (Floating WindowManager card)
 * 3. At the home screen (Floating WindowManager card)
 * 4. At the lock screen (Wakes display + floating WindowManager card over keyguard)
 *
 * Guarantees:
 * - Exactly ONE popup appears (suppresses duplicate heads-up notifications or dual overlays).
 * - Dynamically detects SIM provider / carrier (e.g. Airtel, MTN, Safaricom, etc.) rather than hardcoding.
 * - Detects caller contact name accurately.
 * - Strict dark greyish theme (#222222, 32dp corner radius, red avatar #8B1D1D, decline #EF5350, answer #66BB6A).
 * - Transition to ongoing call screen upon answering.
 */
object IncomingCallPopupHelper {

    private const val TAG = "IncomingCallPopupHelper"

    /**
     * Accurately detects the active SIM carrier / provider for the incoming call.
     */
    fun resolveCarrierName(context: Context, call: Call? = null): String {
        val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager

        // 1. Check account handle on Telecom Call
        val handleCarrier = try {
            val handle = call?.details?.accountHandle ?: XenoInCallService.currentCall?.details?.accountHandle
            if (handle != null) {
                telecomManager?.getPhoneAccount(handle)?.label?.toString()?.takeIf { it.isNotBlank() }
            } else null
        } catch (_: Exception) {
            null
        }
        if (!handleCarrier.isNullOrBlank()) {
            return handleCarrier
        }

        // 2. Check detected SIM cards via TelecomHelper / SubscriptionManager
        val simList = TelecomHelper.getAvailableSimCards(context)
        val activeSim = simList.firstOrNull { it.carrierName.isNotBlank() && !it.carrierName.startsWith("SIM ") }
            ?: simList.firstOrNull()
        if (activeSim != null && activeSim.carrierName.isNotBlank()) {
            return activeSim.carrierName
        }

        // 3. Fallback to TelephonyManager network/sim operator name
        try {
            val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            val simOperator = telephonyManager?.simOperatorName?.takeIf { it.isNotBlank() }
            if (simOperator != null) return simOperator
            val netOperator = telephonyManager?.networkOperatorName?.takeIf { it.isNotBlank() }
            if (netOperator != null) return netOperator
        } catch (_: Exception) {}

        return "SIM 1"
    }

    /**
     * Resolves the contact name for the caller number.
     * Returns the saved contact name if found in the user's contacts, or null if the number is not saved.
     */
    fun resolveCallerName(context: Context, phoneNumber: String): String? {
        val cleanNumber = phoneNumber.trim()
        if (cleanNumber.isBlank() || cleanNumber == "Unknown Caller") return null
        val resolved = XenoNotificationHelper.resolveContactNameQuick(context, cleanNumber)
        return resolved?.takeIf { it.isNotBlank() && !PriorityContactManager.isSyntheticPriorityName(it) && it != cleanNumber }
    }

    /**
     * Primary entry point: Streamlines incoming call popup display with single-popup guarantee.
     */
    fun handleIncomingCall(
        context: Context,
        phoneNumber: String,
        rawCallerName: String? = null,
        call: Call? = null
    ): IncomingCallPopup {
        val callHandle = call?.details?.handle?.schemeSpecificPart?.trim().orEmpty()
        val callExtrasNumber = call?.details?.extras?.getString(TelephonyManager.EXTRA_INCOMING_NUMBER)?.trim().orEmpty()
            .ifEmpty { call?.details?.intentExtras?.getString("incoming_number")?.trim().orEmpty() }
            .ifEmpty { call?.details?.extras?.getString("incoming_number")?.trim().orEmpty() }
            .ifEmpty { call?.details?.intentExtras?.getString(TelephonyManager.EXTRA_INCOMING_NUMBER)?.trim().orEmpty() }
        val sessionNumber = OngoingCallHelper.sessionFlow.value.phoneNumber.trim()

        val cleanNumber = phoneNumber.trim()
            .ifEmpty { callHandle }
            .ifEmpty { callExtrasNumber }
            .ifEmpty { sessionNumber }
            .ifEmpty { "Private Number" }

        val telecomCallerName = call?.details?.callerDisplayName?.takeIf { 
            it.isNotBlank() && !PriorityContactManager.isSyntheticPriorityName(it) && it != cleanNumber && it != "Unknown Caller" 
        }
        val resolvedName = rawCallerName?.takeIf { 
            it.isNotBlank() && !PriorityContactManager.isSyntheticPriorityName(it) && it != cleanNumber && it != "Unknown Caller" 
        } ?: telecomCallerName ?: resolveCallerName(context, cleanNumber)

        val callerName = resolvedName?.takeIf { it.isNotBlank() && it != cleanNumber && it != "Unknown Caller" }
        val carrierName = resolveCarrierName(context, call)

        val isScreenOffOrLocked = OngoingCallHelper.isScreenOffOrLocked(context)
        val isInApp = MainActivity.isAppInForeground && !isScreenOffOrLocked
        val canOverlay = XenoOverlayService.canDrawOverlays(context)

        Log.d(TAG, "handleIncomingCall: isInApp=$isInApp, isScreenOffOrLocked=$isScreenOffOrLocked, canOverlay=$canOverlay, carrier=$carrierName, callerName=$callerName")

        val popup = IncomingCallPopup(
            callerName = callerName,
            callerNumber = cleanNumber,
            carrierName = carrierName
        )

        // Asynchronously re-resolve contact name if not found initially, and update in place
        if (callerName == null && cleanNumber.isNotBlank() && cleanNumber != "Private Number" && cleanNumber != "Unknown Caller") {
            CoroutineScope(Dispatchers.IO).launch {
                val asyncResolved = resolveCallerName(context, cleanNumber)
                    ?: com.example.data.ContactsRepository.getInstance(context).resolveContactName(cleanNumber)
                if (!asyncResolved.isNullOrBlank() && asyncResolved != cleanNumber && asyncResolved != "Unknown Caller") {
                    Log.d("CallerNameResolve", "Asynchronously re-resolved name for $cleanNumber -> $asyncResolved. Updating popup/overlay/notification in place.")
                    withContext(Dispatchers.Main) {
                        OngoingCallHelper.updateIncomingContactName(cleanNumber, asyncResolved)
                        XenoOverlayService.updateCallOverlayName(context, cleanNumber, asyncResolved, carrierName)
                        XenoNotificationHelper.updateCallNotificationName(
                            context = context,
                            callerName = asyncResolved,
                            callerNumber = cleanNumber,
                            carrierNameOverride = carrierName
                        )
                    }
                }
            }
        }

        // Priority VIP bypass handling
        val isPriority = PriorityContactManager.isPriorityNumber(cleanNumber, context)
        if (isPriority) {
            PriorityContactManager.enforceIncomingPriorityBypass(
                context = context,
                isCall = true,
                wakeScreen = isScreenOffOrLocked
            )
        }

        // Initialize incoming call session in OngoingCallHelper
        OngoingCallHelper.startIncomingSession(
            phoneNumber = cleanNumber,
            contactName = callerName,
            carrierName = carrierName,
            openFullScreenImmediately = false
        )

        when {
            // Environment 1: User is inside the app
            isInApp -> {
                // Show ONLY the in-app Compose popup card
                OngoingCallHelper.showIncomingCallPopup(context, cleanNumber, callerName, carrierName)
                XenoOverlayService.dismissCallOverlay(context)

                // Single Popup Guarantee: Suppress heads-up system banner so duplicate doesn't show
                XenoNotificationHelper.showCallNotification(
                    context = context,
                    callerName = callerName,
                    callerNumber = cleanNumber,
                    useFullScreenIntent = false,
                    suppressHeadsUp = true,
                    carrierNameOverride = carrierName
                )
            }

            // Environment 4: Screen is off or locked
            isScreenOffOrLocked -> {
                OngoingCallHelper.dismissInAppPopupOnly()
                OngoingCallHelper.wakeDeviceScreenForIncomingCall(context)

                if (canOverlay) {
                    // Show floating dark greyish card over lock screen
                    XenoOverlayService.showCallOverlay(
                        context = context,
                        callerName = callerName,
                        callerNumber = cleanNumber,
                        carrierName = carrierName
                    )
                    // Single Popup Guarantee: Post notification silently to prevent duplicate heads-up
                    XenoNotificationHelper.showCallNotification(
                        context = context,
                        callerName = callerName,
                        callerNumber = cleanNumber,
                        useFullScreenIntent = false,
                        suppressHeadsUp = true,
                        carrierNameOverride = carrierName
                    )
                } else {
                    XenoOverlayService.dismissCallOverlay(context)
                    // Fall back to single heads-up notification with fullScreenIntent on lock screen
                    XenoNotificationHelper.showCallNotification(
                        context = context,
                        callerName = callerName,
                        callerNumber = cleanNumber,
                        useFullScreenIntent = true,
                        suppressHeadsUp = false,
                        carrierNameOverride = carrierName
                    )
                }
            }

            // Environment 2 & 3: User is in another app or at home screen (Screen is ON)
            else -> {
                OngoingCallHelper.dismissInAppPopupOnly()

                if (canOverlay) {
                    // Show floating WindowManager dark greyish card
                    XenoOverlayService.showCallOverlay(
                        context = context,
                        callerName = callerName,
                        callerNumber = cleanNumber,
                        carrierName = carrierName
                    )
                    // Single Popup Guarantee: Suppress heads-up system banner so duplicate doesn't drop down
                    XenoNotificationHelper.showCallNotification(
                        context = context,
                        callerName = callerName,
                        callerNumber = cleanNumber,
                        useFullScreenIntent = false,
                        suppressHeadsUp = true,
                        carrierNameOverride = carrierName
                    )
                } else {
                    XenoOverlayService.dismissCallOverlay(context)
                    // Show single heads-up notification
                    XenoNotificationHelper.showCallNotification(
                        context = context,
                        callerName = callerName,
                        callerNumber = cleanNumber,
                        useFullScreenIntent = false,
                        suppressHeadsUp = false,
                        carrierNameOverride = carrierName
                    )
                }
            }
        }

        return popup
    }

    /**
     * Answers an incoming call from any popup/overlay surface, dismisses all popups,
     * starts duration counting at 00:00, and transitions to the ongoing call screen.
     */
    fun answerIncomingCall(
        context: Context,
        phoneNumber: String? = null,
        callerName: String? = null
    ) {
        dismissAllPopups(context)
        val session = OngoingCallHelper.answerIncomingCallAndOpenFullScreen(
            context = context,
            phoneNumber = phoneNumber,
            contactName = callerName
        )

        // If app is not in foreground, launch MainActivity to show ActiveCallScreen
        if (!MainActivity.isAppInForeground) {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("open_tab", "CALLS")
                putExtra("incoming_number", session.phoneNumber)
                putExtra("caller_name", session.contactName)
                putExtra("auto_answer", true)
            }
            context.startActivity(intent)
        }
    }

    /**
     * Declines an incoming call from any popup surface and cleans up all popups and notifications.
     */
    fun declineIncomingCall(context: Context) {
        dismissAllPopups(context)
        OngoingCallHelper.declineIncomingCall(context)
    }

    /**
     * Dismisses all active popups (in-app Compose popup and floating overlay).
     */
    fun dismissAllPopups(context: Context) {
        OngoingCallHelper.dismissIncomingCallPopup(context)
        XenoOverlayService.dismissCallOverlay(context)
    }
}
