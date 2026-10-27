package com.example.telecom

import android.app.NotificationManager
import android.content.ContentProviderOperation
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.ContactsContract
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages High-Priority / VIP Contacts and executes all achievable Android OS & App-level
 * restriction bypasses when receiving or placing calls and messages:
 *
 * 1. Pre-configured Priority Numbers:
 *    - +254712901245
 *    - +254792171695
 *    - +245782305395 (also matches +254782305395 / 0782305395 via 9-digit core 782305395)
 *    - +254108807866
 *    - +254768922032
 *
 * 2. OS Contacts Starred Sync (`STARRED = 1`, `SEND_TO_VOICEMAIL = 0`):
 *    Automatically syncs and stars these numbers in Android's native ContactsContract so
 *    system-wide Do Not Disturb (DND) "Starred Contacts" filters natively allow them through.
 *
 * 3. Incoming Call & SMS Bypass:
 *    - Lifts system DND (`NotificationManager.INTERRUPTION_FILTER_ALL`) when policy access is granted.
 *    - Overrides Silent/Vibrate ringer mode (`AudioManager.RINGER_MODE_NORMAL`).
 *    - Boosts `STREAM_RING`, `STREAM_ALARM`, and `STREAM_NOTIFICATION` to 100% max volume.
 *    - Plays an audible fallback ringtone on `USAGE_ALARM` so priority calls ring out loud even
 *      if the hardware ringer stream is muted by the OS.
 *    - Acquires an immediate bright `WakeLock` (`SCREEN_BRIGHT_WAKE_LOCK | ACQUIRE_CAUSES_WAKEUP`)
 *      to wake the display and bypass the lockscreen.
 *
 * 4. Outgoing Call Bypass:
 *    - Bypasses Dual-SIM chooser prompts by immediately dialing with the preferred/primary SIM.
 *    - Ensures call audio stream (`STREAM_VOICE_CALL`) is unmuted and boosted for clear connection.
 */
object PriorityContactManager {

    private const val TAG = "PriorityContactManager"
    private const val PREFS_NAME = "xeno_priority_contacts_prefs"
    private const val KEY_CUSTOM_PRIORITY_NUMBERS = "custom_priority_numbers"

    val DEFAULT_PRIORITY_NUMBERS: List<String> = listOf(
        "+254712901245",
        "+254792171695",
        "+245782305395",
        "+254108807866",
        "+254768922032"
    )

    // Pre-computed 9-digit subscriber cores so +254..., +245..., 07..., and 01... formats always match
    private val DEFAULT_PRIORITY_CORES: Set<String> = setOf(
        "712901245",
        "792171695",
        "782305395",
        "108807866",
        "768922032"
    )

    private val _priorityNumbersFlow = MutableStateFlow<List<String>>(DEFAULT_PRIORITY_NUMBERS)
    val priorityNumbersFlow: StateFlow<List<String>> = _priorityNumbersFlow.asStateFlow()

    @Volatile
    private var activePriorityRingtone: Ringtone? = null

    fun extractDigits(phoneNumber: String): String {
        val sb = StringBuilder(phoneNumber.length)
        for (i in 0 until phoneNumber.length) {
            val c = phoneNumber[i]
            if (c in '0'..'9') sb.append(c)
        }
        return sb.toString()
    }

    fun extractCore9(phoneNumber: String): String {
        val digits = extractDigits(phoneNumber)
        return if (digits.length >= 9) digits.takeLast(9) else digits
    }

    fun getAllPriorityNumbers(context: Context? = null): List<String> {
        if (context == null) return _priorityNumbersFlow.value
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedSet = prefs.getStringSet(KEY_CUSTOM_PRIORITY_NUMBERS, null).orEmpty()
        val combined = LinkedHashSet<String>()
        DEFAULT_PRIORITY_NUMBERS.forEach { combined.add(it) }
        savedSet.forEach { if (it.isNotBlank()) combined.add(it.trim()) }
        val list = combined.toList()
        if (_priorityNumbersFlow.value != list) {
            _priorityNumbersFlow.value = list
        }
        return list
    }

    fun addPriorityNumber(context: Context, phoneNumber: String) {
        val clean = phoneNumber.trim()
        if (clean.isBlank()) return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val current = prefs.getStringSet(KEY_CUSTOM_PRIORITY_NUMBERS, emptySet())?.toMutableSet() ?: mutableSetOf()
        current.add(clean)
        prefs.edit().putStringSet(KEY_CUSTOM_PRIORITY_NUMBERS, current).apply()
        getAllPriorityNumbers(context)
        syncPriorityContactsToOs(context)
    }

    fun removeCustomPriorityNumber(context: Context, phoneNumber: String): Boolean {
        val core = extractCore9(phoneNumber)
        if (DEFAULT_PRIORITY_CORES.contains(core)) {
            return false
        }
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val current = prefs.getStringSet(KEY_CUSTOM_PRIORITY_NUMBERS, emptySet())?.toMutableSet() ?: mutableSetOf()
        val removed = current.removeAll { extractCore9(it) == core || it.trim() == phoneNumber.trim() }
        if (removed) {
            prefs.edit().putStringSet(KEY_CUSTOM_PRIORITY_NUMBERS, current).apply()
            getAllPriorityNumbers(context)
        }
        return removed
    }

    /**
     * Fast O(1) check whether a phone number is one of the High-Priority VIP numbers.
     * Matches international (+254 / +245), national (07... / 01...), and raw digit formats.
     */
    fun isPriorityNumber(phoneNumber: String, context: Context? = null): Boolean {
        if (phoneNumber.isBlank()) return false
        val digits = extractDigits(phoneNumber)
        if (digits.length < 7) return false
        val core9 = if (digits.length >= 9) digits.takeLast(9) else digits

        if (DEFAULT_PRIORITY_CORES.contains(core9)) {
            return true
        }

        val allNumbers = if (context != null) getAllPriorityNumbers(context) else _priorityNumbersFlow.value
        return allNumbers.any { priorityNum ->
            val pDigits = extractDigits(priorityNum)
            val pCore9 = if (pDigits.length >= 9) pDigits.takeLast(9) else pDigits
            pDigits.isNotEmpty() && (pDigits == digits || pCore9 == core9)
        }
    }

    /**
     * Checks whether any number in a contact's number list is a Priority number.
     */
    fun isPriorityContact(primaryNumber: String, allNumbers: List<String> = emptyList(), context: Context? = null): Boolean {
        if (isPriorityNumber(primaryNumber, context)) return true
        return allNumbers.any { isPriorityNumber(it, context) }
    }

    /**
     * Checks if a contact name was previously generated as a synthetic "Priority VIP" label.
     */
    fun isSyntheticPriorityName(name: String?): Boolean {
        if (name.isNullOrBlank()) return false
        val trimmed = name.trim().removePrefix("★").trim()
        return trimmed.startsWith("Priority VIP", ignoreCase = true)
    }

    /**
     * Returns the raw phone number when no saved contact name exists on the phone.
     */
    fun getFallbackPriorityLabel(phoneNumber: String): String {
        return phoneNumber.trim()
    }

    fun isDndBypassAccessGranted(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            nm?.isNotificationPolicyAccessGranted == true
        } else {
            true
        }
    }

    fun createDndPolicyAccessIntent(context: Context): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        } else {
            Intent(Settings.ACTION_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
    }

    fun isBatteryOptimizationIgnored(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            pm?.isIgnoringBatteryOptimizations(context.packageName) == true
        } else {
            true
        }
    }

    @Suppress("BatteryLife")
    fun createBatteryOptimizationExemptionIntent(context: Context): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        } else {
            Intent(Settings.ACTION_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
    }

    /**
     * Executes all OS & hardware bypasses when an incoming Call or SMS arrives from a Priority Contact:
     * 1. Disables system Do Not Disturb (`INTERRUPTION_FILTER_ALL`) if policy access is granted.
     * 2. Overrides Silent/Vibrate mode to `RINGER_MODE_NORMAL`.
     * 3. Boosts Ring, Notification, and Alarm stream volumes to 100% maximum.
     * 4. Wakes the screen immediately via bright WakeLock.
     * 5. For calls, plays an alarm-channel fallback ringtone + priority vibration so it is always heard.
     */
    fun enforceIncomingPriorityBypass(
        context: Context,
        isCall: Boolean,
        wakeScreen: Boolean = OngoingCallHelper.isScreenOffOrLocked(context)
    ) {
        Log.d(TAG, "Enforcing High-Priority OS Bypass (isCall=$isCall, wakeScreen=$wakeScreen)")

        // 1. Wake screen immediately if screen is off or locked
        if (wakeScreen) {
            OngoingCallHelper.wakeDeviceScreenForIncomingCall(context)
        }

        // 2. Lift System Do Not Disturb (DND) filter if permitted
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                if (nm != null && nm.isNotificationPolicyAccessGranted) {
                    if (nm.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL) {
                        nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "DND interruption filter bypass failed: ${e.message}")
        }

        // 3. Override Silent / Vibrate ringer mode and maximize Ring, Notification, and Alarm volumes
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            if (audioManager != null) {
                try {
                    if (audioManager.ringerMode != AudioManager.RINGER_MODE_NORMAL) {
                        audioManager.ringerMode = AudioManager.RINGER_MODE_NORMAL
                    }
                } catch (_: SecurityException) {
                } catch (_: Exception) {}

                val streamsToBoost = if (isCall) {
                    intArrayOf(
                        AudioManager.STREAM_RING,
                        AudioManager.STREAM_ALARM,
                        AudioManager.STREAM_NOTIFICATION,
                        AudioManager.STREAM_VOICE_CALL
                    )
                } else {
                    intArrayOf(
                        AudioManager.STREAM_NOTIFICATION,
                        AudioManager.STREAM_RING,
                        AudioManager.STREAM_ALARM
                    )
                }

                for (stream in streamsToBoost) {
                    try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            audioManager.adjustStreamVolume(stream, AudioManager.ADJUST_UNMUTE, 0)
                        }
                        val maxVol = audioManager.getStreamMaxVolume(stream)
                        if (maxVol > 0) {
                            audioManager.setStreamVolume(stream, maxVol, 0)
                        }
                    } catch (_: SecurityException) {
                    } catch (_: Exception) {}
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Audio volume bypass failed: ${e.message}")
        }

        // 4. Trigger high-priority vibration & alarm-stream ringtone fallback
        try {
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            if (vibrator != null && vibrator.hasVibrator()) {
                val pattern = if (isCall) {
                    longArrayOf(0, 600, 200, 600, 200, 800, 250, 600)
                } else {
                    longArrayOf(0, 350, 120, 350, 120, 500)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val attrs = AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                    vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1), attrs)
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(pattern, -1)
                }
            }
        } catch (_: Exception) {}

        if (isCall) {
            startPriorityAlarmRingtoneFallback(context)
        }
    }

    private fun startPriorityAlarmRingtoneFallback(context: Context) {
        try {
            stopPriorityAlertSound()
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                ?: return
            val ringtone = RingtoneManager.getRingtone(context.applicationContext, uri) ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                ringtone.audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            } else {
                @Suppress("DEPRECATION")
                ringtone.streamType = AudioManager.STREAM_ALARM
            }
            ringtone.play()
            activePriorityRingtone = ringtone
        } catch (e: Exception) {
            Log.w(TAG, "Priority alarm ringtone fallback error: ${e.message}")
        }
    }

    fun stopPriorityAlertSound() {
        try {
            activePriorityRingtone?.let { rt ->
                if (rt.isPlaying) {
                    rt.stop()
                }
            }
        } catch (_: Exception) {}
        activePriorityRingtone = null
    }

    fun enforceOutgoingPriorityBypass(context: Context) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                if (nm != null && nm.isNotificationPolicyAccessGranted &&
                    nm.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL
                ) {
                    nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
                }
            }
        } catch (_: Exception) {}

        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
            audioManager.isMicrophoneMute = false
            val maxVoice = audioManager.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL)
            if (maxVoice > 0) {
                audioManager.setStreamVolume(AudioManager.STREAM_VOICE_CALL, maxVoice, 0)
            }
        } catch (_: Exception) {}
    }

    fun syncPriorityContactsToOs(context: Context) {
        val hasRead = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
        val hasWrite = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.WRITE_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasRead || !hasWrite) return

        try {
            val resolver = context.contentResolver
            val contactIdsToStar = mutableSetOf<Long>()
            val syntheticContactIdsToDelete = mutableSetOf<Long>()

            val projection = arrayOf(
                ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            )

            resolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                projection,
                null,
                null,
                null
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
                val nameIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                while (cursor.moveToNext()) {
                    val contactId = if (idIdx != -1) cursor.getLong(idIdx) else 0L
                    val displayName = if (nameIdx != -1) cursor.getString(nameIdx).orEmpty() else ""
                    val rawNumber = if (numIdx != -1) cursor.getString(numIdx).orEmpty() else ""
                    if (contactId > 0L) {
                        if (isSyntheticPriorityName(displayName)) {
                            syntheticContactIdsToDelete.add(contactId)
                        } else if (isPriorityNumber(rawNumber, context)) {
                            contactIdsToStar.add(contactId)
                        }
                    }
                }
            }

            // Remove any synthetic "Priority VIP ..." contacts that were previously inserted
            for (syntheticId in syntheticContactIdsToDelete) {
                try {
                    resolver.delete(
                        ContactsContract.RawContacts.CONTENT_URI,
                        "${ContactsContract.RawContacts.CONTACT_ID} = ?",
                        arrayOf(syntheticId.toString())
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to delete synthetic contact $syntheticId: ${e.message}")
                }
            }

            // Star user's real saved priority contacts in the OS so DND allows them through
            for (contactId in contactIdsToStar) {
                try {
                    val values = ContentValues().apply {
                        put(ContactsContract.Contacts.STARRED, 1)
                        put(ContactsContract.Contacts.SEND_TO_VOICEMAIL, 0)
                    }
                    resolver.update(
                        ContactsContract.Contacts.CONTENT_URI,
                        values,
                        "${ContactsContract.Contacts._ID} = ?",
                        arrayOf(contactId.toString())
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to star contactId $contactId: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "syncPriorityContactsToOs failed: ${e.message}")
        }
    }
}
