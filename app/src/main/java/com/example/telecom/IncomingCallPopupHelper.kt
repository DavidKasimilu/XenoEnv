package com.example.telecom

import android.content.Context
import android.content.Intent
import android.os.Build
import android.telecom.Call
import android.telecom.TelecomManager
import android.telephony.TelephonyManager
import android.util.Log
import com.example.MainActivity
import com.example.data.ContactsRepository
import com.example.data.models.IncomingCallPopup
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

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
    /**
     * Resolves the contact name instantly from in-memory cache only (0ms delay, no ContentResolver).
     */
    fun resolveCallerName(context: Context, phoneNumber: String): String? {
        val cleanNumber = phoneNumber.trim()
        if (cleanNumber.isBlank() || cleanNumber == "Unknown Caller" || cleanNumber == "Private Number") return null
        val resolved = ContactsRepository.resolveFromCache(cleanNumber)
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
        val startTimeMs = System.currentTimeMillis()
        val handleNum = call?.details?.handle?.schemeSpecificPart?.trim().orEmpty()
        val gatewayNum = call?.details?.gatewayInfo?.originalAddress?.schemeSpecificPart?.trim().orEmpty()
        val paramNum = phoneNumber.trim()
        val sessionNum = OngoingCallHelper.sessionFlow.value.phoneNumber.trim().takeIf { it != "Private Number" }.orEmpty()

        val presentation = call?.details?.handlePresentation
        val isRestricted = presentation == TelecomManager.PRESENTATION_RESTRICTED

        val (cleanNumber, sourceTag) = when {
            isRestricted -> Pair("Private Number", "restricted")
            handleNum.isNotBlank() -> Pair(handleNum, "handle")
            gatewayNum.isNotBlank() -> Pair(gatewayNum, "gateway")
            paramNum.isNotBlank() && paramNum != "Private Number" -> Pair(paramNum, "broadcast")
            sessionNum.isNotBlank() -> Pair(sessionNum, "session")
            else -> Pair("Private Number", "none")
        }

        Log.d("IncomingCallFlow", "Resolved number '$cleanNumber' via source=$sourceTag (presentation=$presentation, handle='$handleNum', gateway='$gatewayNum', param='$paramNum', session='$sessionNum')")

        val telecomCallerName = call?.details?.callerDisplayName?.takeIf { 
            it.isNotBlank() && !PriorityContactManager.isSyntheticPriorityName(it) && it != cleanNumber && it != "Unknown Caller" 
        }
        val resolvedName = rawCallerName?.takeIf { 
            it.isNotBlank() && !PriorityContactManager.isSyntheticPriorityName(it) && it != cleanNumber && it != "Unknown Caller" 
        } ?: resolveCallerName(context, cleanNumber) ?: telecomCallerName

        val callerName = resolvedName?.takeIf { it.isNotBlank() && it != cleanNumber && it != "Unknown Caller" }
        val carrierName = resolveCarrierName(context, call)

        val isScreenOffOrLocked = OngoingCallHelper.isScreenOffOrLocked(context)
        val isInApp = MainActivity.isAppInForeground && !isScreenOffOrLocked
        val canOverlay = XenoOverlayService.canDrawOverlays(context)

        val envDesc = when {
            isInApp -> "InApp"
            isScreenOffOrLocked -> "ScreenOffOrLocked"
            else -> "OtherAppOrHome"
        }

        val elapsedBeforePopup = System.currentTimeMillis() - startTimeMs
        Log.d("IncomingCallFlow", "Popup shown: env=$envDesc, elapsed=${elapsedBeforePopup}ms, number=$cleanNumber, name=$callerName, source=$sourceTag")

        val popup = IncomingCallPopup(
            callerName = callerName,
            callerNumber = cleanNumber,
            carrierName = carrierName
        )

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
                OngoingCallHelper.showIncomingCallPopup(context, cleanNumber, callerName, carrierName)
                XenoOverlayService.dismissCallOverlay(context)
                XenoNotificationHelper.showCallNotification(
                    context = context,
                    callerName = callerName,
                    callerNumber = cleanNumber,
                    useFullScreenIntent = false,
                    suppressHeadsUp = true,
                    carrierNameOverride = carrierName
                )
            }

            // Environment 4: Screen is off or locked -> wake the display and show the full-screen
            // call screen (Accept / Decline) over the lock screen via a full-screen intent.
            isScreenOffOrLocked -> {
                OngoingCallHelper.dismissInAppPopupOnly()
                XenoOverlayService.dismissCallOverlay(context)
                OngoingCallHelper.wakeDeviceScreenForIncomingCall(context)
                XenoNotificationHelper.showCallNotification(
                    context = context,
                    callerName = callerName,
                    callerNumber = cleanNumber,
                    useFullScreenIntent = true,
                    suppressHeadsUp = false,
                    carrierNameOverride = carrierName
                )
                launchCallScreen(context)
            }

            // Environment 2 & 3: User is in another app or at home screen (Screen is ON)
            else -> {
                OngoingCallHelper.dismissInAppPopupOnly()

                if (canOverlay) {
                    XenoOverlayService.showCallOverlay(
                        context = context,
                        callerName = callerName,
                        callerNumber = cleanNumber,
                        carrierName = carrierName
                    )
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

        // Background lookup: confirm the name with the exact system lookup (and find it if the cache missed)
        if (cleanNumber.isNotBlank() && cleanNumber != "Private Number" && cleanNumber != "Unknown Caller") {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    Log.d("IncomingCallFlow", "Name lookup started for $cleanNumber")
                    val deepResolved = withTimeoutOrNull(2000) {
                        try {
                            val exact = XenoNotificationHelper.resolveContactNameExact(context, cleanNumber)
                            if (!exact.isNullOrBlank()) {
                                return@withTimeoutOrNull exact
                            }
                            // Nothing better than the name already shown
                            if (callerName != null) return@withTimeoutOrNull null
                            val quick = try {
                                XenoNotificationHelper.resolveContactNameQuick(context, cleanNumber)
                            } catch (t: Throwable) {
                                Log.e("IncomingCallFlow", "Caught exception in resolveContactNameQuick for $cleanNumber", t)
                                null
                            }
                            if (!quick.isNullOrBlank()) {
                                return@withTimeoutOrNull quick
                            }
                            val repo = ContactsRepository.getInstance(context)
                            repo.resolveContactName(cleanNumber)
                        } catch (t: Throwable) {
                            Log.e("IncomingCallFlow", "Caught exception in deep name lookup for $cleanNumber", t)
                            null
                        }
                    }
                    Log.d("IncomingCallFlow", "Name lookup result for $cleanNumber: '$deepResolved'")

                    if (!deepResolved.isNullOrBlank() && deepResolved != callerName && deepResolved != cleanNumber && deepResolved != "Unknown Caller" && !PriorityContactManager.isSyntheticPriorityName(deepResolved)) {
                        val session = OngoingCallHelper.sessionFlow.value
                        val popupState = OngoingCallHelper.incomingPopupFlow.value
                        val isSessionRinging = session.phase == CallPhase.INCOMING_RINGING

                        if (isSessionRinging || popupState != null) {
                            Log.d("IncomingCallFlow", "Popup updated in place with name '$deepResolved' for $cleanNumber")
                            withContext(Dispatchers.Main) {
                                try {
                                    OngoingCallHelper.updateIncomingContactName(cleanNumber, deepResolved)
                                    XenoOverlayService.updateCallOverlayName(context, cleanNumber, deepResolved, carrierName)
                                    XenoNotificationHelper.updateCallNotificationName(
                                        context = context,
                                        callerName = deepResolved,
                                        callerNumber = cleanNumber,
                                        carrierNameOverride = carrierName
                                    )
                                } catch (t: Throwable) {
                                    Log.e("IncomingCallFlow", "Caught exception updating popup in place for $cleanNumber", t)
                                }
                            }
                        } else {
                            Log.d("IncomingCallFlow", "Skipping in-place update for $cleanNumber because call ended or answered")
                        }
                    }
                } catch (t: Throwable) {
                    Log.e("IncomingCallFlow", "Caught exception in background coroutine for $cleanNumber", t)
                }
            }
        }

        return popup
    }

    /**
     * UPDATES the existing popup in place when a real number (and optional name) arrives
     * after the popup was already shown (e.g. from onDetailsChanged or delayed onCallAdded).
     * Replaces a "Private Number" placeholder with the real number.
     * A real number is NEVER overwritten by "Private Number" later.
     */
    fun updateIncomingCallNumber(
        context: Context,
        phoneNumber: String,
        contactName: String? = null
    ) {
        val cleanNumber = phoneNumber.trim()
        if (cleanNumber.isBlank()) return

        val currentSession = OngoingCallHelper.sessionFlow.value
        val currentPopup = OngoingCallHelper.incomingPopupFlow.value
        val existingNumber = currentSession.phoneNumber.ifBlank { currentPopup?.callerNumber.orEmpty() }

        if (existingNumber.isNotBlank() && existingNumber != "Private Number" && cleanNumber == "Private Number") {
            Log.d("IncomingCallFlow", "updateIncomingCallNumber ignored: refusing to overwrite existing real number '$existingNumber' with 'Private Number'")
            return
        }

        if (currentPopup == null) {
            Log.d("IncomingCallFlow", "updateIncomingCallNumber: No popup showing yet. Showing new popup with number '$cleanNumber'")
            handleIncomingCall(context, cleanNumber, contactName, XenoInCallService.currentCall)
            return
        }

        val resolvedName = contactName?.takeIf { it.isNotBlank() && it != cleanNumber }
            ?: resolveCallerName(context, cleanNumber)
            ?: currentSession.contactName

        OngoingCallHelper.updateSessionAndPopupNumber(cleanNumber, resolvedName)

        val carrierName = resolveCarrierName(context, XenoInCallService.currentCall)

        XenoOverlayService.updateCallOverlayName(
            context = context,
            callerNumber = cleanNumber,
            callerName = resolvedName ?: cleanNumber,
            carrierName = carrierName
        )

        val isScreenOffOrLocked = OngoingCallHelper.isScreenOffOrLocked(context)
        val isInApp = MainActivity.isAppInForeground && !isScreenOffOrLocked
        val canOverlay = XenoOverlayService.canDrawOverlays(context)
        val suppressHeadsUp = isInApp || (canOverlay && !isScreenOffOrLocked)

        XenoNotificationHelper.showCallNotification(
            context = context,
            callerName = resolvedName,
            callerNumber = cleanNumber,
            useFullScreenIntent = isScreenOffOrLocked,
            suppressHeadsUp = suppressHeadsUp,
            carrierNameOverride = carrierName
        )

        Log.d("IncomingCallFlow", "Popup updated in place: number='$cleanNumber', name='$resolvedName', previousNumber='$existingNumber'")

        if (resolvedName == null && cleanNumber != "Private Number" && cleanNumber != "Unknown Caller") {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    Log.d("IncomingCallFlow", "Name lookup started for updated number $cleanNumber")
                    val deepResolved = withTimeoutOrNull(2000) {
                        try {
                            val quick = try {
                                XenoNotificationHelper.resolveContactNameQuick(context, cleanNumber)
                            } catch (t: Throwable) {
                                Log.e("IncomingCallFlow", "Caught exception in resolveContactNameQuick for $cleanNumber", t)
                                null
                            }
                            if (!quick.isNullOrBlank()) return@withTimeoutOrNull quick
                            val repo = ContactsRepository.getInstance(context)
                            repo.resolveContactName(cleanNumber)
                        } catch (t: Throwable) {
                            Log.e("IncomingCallFlow", "Caught exception in deep name lookup for $cleanNumber", t)
                            null
                        }
                    }
                    Log.d("IncomingCallFlow", "Name lookup result for updated number $cleanNumber: '$deepResolved'")

                    if (!deepResolved.isNullOrBlank() && deepResolved != cleanNumber && deepResolved != "Unknown Caller" && !PriorityContactManager.isSyntheticPriorityName(deepResolved)) {
                        val session = OngoingCallHelper.sessionFlow.value
                        val popupState = OngoingCallHelper.incomingPopupFlow.value
                        if (session.phase == CallPhase.INCOMING_RINGING || popupState != null) {
                            Log.d("IncomingCallFlow", "Popup updated in place with name '$deepResolved' for $cleanNumber")
                            withContext(Dispatchers.Main) {
                                try {
                                    OngoingCallHelper.updateIncomingContactName(cleanNumber, deepResolved)
                                    XenoOverlayService.updateCallOverlayName(context, cleanNumber, deepResolved, carrierName)
                                    XenoNotificationHelper.updateCallNotificationName(
                                        context = context,
                                        callerName = deepResolved,
                                        callerNumber = cleanNumber,
                                        carrierNameOverride = carrierName
                                    )
                                } catch (t: Throwable) {
                                    Log.e("IncomingCallFlow", "Caught exception updating popup in place for $cleanNumber", t)
                                }
                            }
                        }
                    }
                } catch (t: Throwable) {
                    Log.e("IncomingCallFlow", "Caught exception in background coroutine for $cleanNumber", t)
                }
            }
        }
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

        // Locked / screen off: show the in-call screen over the lock screen (the app itself stays hidden)
        if (!MainActivity.isAppInForeground && OngoingCallHelper.isScreenOffOrLocked(context)) {
            launchCallScreen(context)
            return
        }

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
     * Opens the full-screen call screen (shows over the lock screen and wakes the display).
     * The notification's full-screen intent is the main trigger; this direct launch is a backup
     * and is simply ignored by Android if background launches are not allowed.
     */
    private fun launchCallScreen(context: Context) {
        try {
            context.startActivity(
                Intent(context, IncomingCallActivity::class.java).addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                )
            )
        } catch (e: Exception) {
            Log.w(TAG, "Direct launch of call screen not allowed: ${e.message}")
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
