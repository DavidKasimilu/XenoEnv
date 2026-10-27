package com.example.data.models

import android.provider.CallLog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private object FastDateFormatter {
    private val timeFormat = object : ThreadLocal<SimpleDateFormat>() {
        override fun initialValue() = SimpleDateFormat("h:mm a", Locale.getDefault())
    }
    private val dayTimeFormat = object : ThreadLocal<SimpleDateFormat>() {
        override fun initialValue() = SimpleDateFormat("EEE, h:mm a", Locale.getDefault())
    }
    private val fullDateFormat = object : ThreadLocal<SimpleDateFormat>() {
        override fun initialValue() = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
    }
    private val shortDateFormat = object : ThreadLocal<SimpleDateFormat>() {
        override fun initialValue() = SimpleDateFormat("MMM d", Locale.getDefault())
    }
    private val dayShortDateFormat = object : ThreadLocal<SimpleDateFormat>() {
        override fun initialValue() = SimpleDateFormat("EEE, MMM d", Locale.getDefault())
    }

    fun formatTime(timestamp: Long): String = timeFormat.get()!!.format(Date(timestamp))
    fun formatDayTime(timestamp: Long): String = dayTimeFormat.get()!!.format(Date(timestamp))
    fun formatFullDate(timestamp: Long): String = fullDateFormat.get()!!.format(Date(timestamp))
    fun formatShortDate(timestamp: Long): String = shortDateFormat.get()!!.format(Date(timestamp))
    fun formatDayShortDate(timestamp: Long): String = dayShortDateFormat.get()!!.format(Date(timestamp))
}

enum class CallType {
    INCOMING,
    OUTGOING,
    MISSED,
    VOICEMAIL,
    REJECTED,
    BLOCKED,
    UNKNOWN;

    fun toCallLogType(): Int {
        return when (this) {
            INCOMING -> CallLog.Calls.INCOMING_TYPE
            OUTGOING -> CallLog.Calls.OUTGOING_TYPE
            MISSED -> CallLog.Calls.MISSED_TYPE
            VOICEMAIL -> CallLog.Calls.VOICEMAIL_TYPE
            REJECTED -> CallLog.Calls.REJECTED_TYPE
            BLOCKED -> CallLog.Calls.BLOCKED_TYPE
            UNKNOWN -> CallLog.Calls.OUTGOING_TYPE
        }
    }

    companion object {
        fun fromCallLogType(type: Int): CallType {
            return when (type) {
                CallLog.Calls.INCOMING_TYPE -> INCOMING
                CallLog.Calls.OUTGOING_TYPE -> OUTGOING
                CallLog.Calls.MISSED_TYPE -> MISSED
                CallLog.Calls.VOICEMAIL_TYPE -> VOICEMAIL
                CallLog.Calls.REJECTED_TYPE -> REJECTED
                CallLog.Calls.BLOCKED_TYPE -> BLOCKED
                else -> UNKNOWN
            }
        }
    }
}

data class CallLogEntry(
    val id: Long,
    val number: String,
    val cachedName: String?,
    val type: CallType,
    val date: Long,
    val durationSeconds: Long,
    val photoUri: String? = null
) {
    val displayName: String
        get() = cachedName
            ?.takeIf { it.isNotBlank() && !com.example.telecom.PriorityContactManager.isSyntheticPriorityName(it) }
            ?: number.takeIf { it.isNotBlank() }
            ?: "Unknown Caller"

    val formattedDuration: String
        get() {
            if (durationSeconds <= 0) return if (type == CallType.MISSED) "Missed" else "0s"
            val mins = durationSeconds / 60
            val secs = durationSeconds % 60
            return if (mins > 0) "${mins}m ${secs}s" else "${secs}s"
        }

    val formattedTimeOfDay: String
        get() = FastDateFormatter.formatTime(date)

    val formattedDayLabel: String
        get() {
            val now = System.currentTimeMillis()
            val diff = now - date
            val oneDay = 24 * 60 * 60 * 1000L
            return when {
                diff < oneDay -> "Today"
                diff < 2 * oneDay -> "Yesterday"
                else -> FastDateFormatter.formatDayShortDate(date)
            }
        }

    val typeLabel: String
        get() = when (type) {
            CallType.INCOMING -> "Incoming"
            CallType.OUTGOING -> "Outgoing"
            CallType.MISSED -> "Missed"
            CallType.REJECTED -> "Rejected"
            CallType.VOICEMAIL -> "Voicemail"
            CallType.BLOCKED -> "Blocked"
            CallType.UNKNOWN -> "Call"
        }

    val formattedDate: String
        get() {
            val now = System.currentTimeMillis()
            val diff = now - date
            val oneDay = 24 * 60 * 60 * 1000L
            return when {
                diff < oneDay -> FastDateFormatter.formatTime(date)
                diff < 2 * oneDay -> "Yesterday, ${FastDateFormatter.formatTime(date)}"
                diff < 7 * oneDay -> FastDateFormatter.formatDayTime(date)
                else -> FastDateFormatter.formatFullDate(date)
            }
        }
}

/**
 * Represents a group of consecutive calls to/from the same number within a 3-hour window.
 */
data class CallLogGroup(
    val entries: List<CallLogEntry>
) {
    init {
        require(entries.isNotEmpty()) { "CallLogGroup must contain at least one CallLogEntry" }
    }

    val latestEntry: CallLogEntry
        get() = entries.first()

    val oldestEntry: CallLogEntry
        get() = entries.last()

    val count: Int
        get() = entries.size
}

private const val CONSECUTIVE_CALL_WINDOW_MS = 3 * 60 * 60 * 1000L // 3 hours

private fun normalizeCallerIdentity(entry: CallLogEntry): String {
    val raw = entry.number.trim()
    if (raw.isEmpty()) return "name:${entry.displayName.lowercase(Locale.ROOT)}"
    // Keep USSD / short codes distinct
    if (raw.contains("*") || raw.contains("#")) return raw.lowercase(Locale.ROOT)
    val digits = buildString(raw.length) {
        for (ch in raw) {
            if (ch in '0'..'9') append(ch)
        }
    }
    return when {
        digits.length >= 9 -> digits.takeLast(9)
        digits.length >= 7 -> digits.takeLast(7)
        digits.isNotEmpty() -> digits
        else -> raw.lowercase(Locale.ROOT)
    }
}

/**
 * Bundles consecutive call records to/from the same phone number when adjacent calls in the
 * consecutive sequence occur within [maxWindowMs] (default 3 hours) of each other.
 */
fun groupConsecutiveCalls(
    logs: List<CallLogEntry>,
    maxWindowMs: Long = CONSECUTIVE_CALL_WINDOW_MS
): List<CallLogGroup> {
    if (logs.isEmpty()) return emptyList()
    val groups = ArrayList<CallLogGroup>(logs.size)
    var currentBucket = ArrayList<CallLogEntry>(4)
    var currentIdentity = ""

    for (entry in logs) {
        val identity = normalizeCallerIdentity(entry)
        if (currentBucket.isEmpty()) {
            currentBucket.add(entry)
            currentIdentity = identity
        } else {
            val previousInRun = currentBucket.last()
            val timeGap = kotlin.math.abs(previousInRun.date - entry.date)
            if (identity == currentIdentity && timeGap <= maxWindowMs) {
                currentBucket.add(entry)
            } else {
                groups.add(CallLogGroup(currentBucket))
                currentBucket = ArrayList(4)
                currentBucket.add(entry)
                currentIdentity = identity
            }
        }
    }
    if (currentBucket.isNotEmpty()) {
        groups.add(CallLogGroup(currentBucket))
    }
    return groups
}

data class ContactItem(
    val id: Long,
    val lookupKey: String,
    val displayName: String,
    val primaryNumber: String,
    val allNumbers: List<String> = emptyList(),
    val photoUri: String? = null
) {
    val initials: String
        get() {
            val trimmed = displayName.trim()
            if (trimmed.isEmpty()) return "#"
            val parts = trimmed.split(" ").filter { it.isNotEmpty() }
            return when {
                parts.size >= 2 -> "${parts[0].first().uppercaseChar()}${parts[1].first().uppercaseChar()}"
                parts.isNotEmpty() -> "${parts[0].first().uppercaseChar()}"
                else -> "#"
            }
        }
}

data class SmsThread(
    val threadId: Long,
    val address: String,
    val contactName: String?,
    val snippet: String,
    val date: Long,
    val read: Boolean,
    val messageCount: Int = 1,
    val unreadCount: Int = 0
) {
    val displayName: String
        get() = contactName
            ?.takeIf { it.isNotBlank() && !com.example.telecom.PriorityContactManager.isSyntheticPriorityName(it) }
            ?: address.takeIf { it.isNotBlank() }
            ?: "Unknown"

    val formattedDate: String
        get() {
            val now = System.currentTimeMillis()
            val diff = now - date
            val oneDay = 24 * 60 * 60 * 1000L
            return when {
                diff < oneDay -> FastDateFormatter.formatTime(date)
                diff < 2 * oneDay -> "Yesterday"
                else -> FastDateFormatter.formatShortDate(date)
            }
        }
}

enum class SmsDeliveryStatus(val label: String) {
    SENDING("Sending..."),
    SENT("Sent"),
    DELIVERED("Delivered"),
    FAILED("Failed")
}

data class SmsMessage(
    val id: Long,
    val threadId: Long,
    val address: String,
    val body: String,
    val date: Long,
    val isSent: Boolean,
    val read: Boolean,
    val deliveryStatus: SmsDeliveryStatus = SmsDeliveryStatus.DELIVERED
) {
    val formattedTime: String
        get() = FastDateFormatter.formatTime(date)

    val formattedDate: String
        get() = FastDateFormatter.formatDayShortDate(date)
}

data class IncomingSmsPopupData(
    val id: Long = System.currentTimeMillis(),
    val threadId: Long,
    val senderNumber: String,
    val senderName: String?,
    val messageBody: String,
    val timestamp: Long = System.currentTimeMillis()
) {
    val displayName: String
        get() = senderName?.takeIf { it.isNotBlank() } ?: senderNumber.takeIf { it.isNotBlank() } ?: "Unknown Sender"
}

data class IncomingCallPopupData(
    val id: Long = System.currentTimeMillis(),
    val phoneNumber: String,
    val callerName: String?,
    val timestamp: Long = System.currentTimeMillis()
) {
    val displayName: String
        get() = callerName?.takeIf { it.isNotBlank() } ?: phoneNumber.takeIf { it.isNotBlank() } ?: "Unknown Caller"
}
