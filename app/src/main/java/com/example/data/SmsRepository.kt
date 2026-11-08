package com.example.data

import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Telephony
import android.telephony.SmsManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.data.models.SmsDeliveryStatus
import com.example.data.models.SmsMessage
import com.example.data.models.SmsThread
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SmsRepository(
    private val context: Context,
    private val contactsRepository: ContactsRepository
) {

    companion object {
        const val ACTION_SMS_SENT = "com.example.SMS_SENT_STATUS"
        const val ACTION_SMS_DELIVERED = "com.example.SMS_DELIVERED_STATUS"
        const val EXTRA_MESSAGE_ID = "extra_message_id"
        const val EXTRA_PROVIDER_ROW_ID = "extra_provider_row_id"

        private val processedDeliveryEvents = ConcurrentHashMap<String, Long>()
        @Volatile
        var activeInstance: SmsRepository? = null

        fun handleStaticDeliveryBroadcast(
            context: Context,
            action: String,
            resultCode: Int,
            messageId: Long,
            providerRowId: Long?
        ) {
            val key = "${action}_${messageId}_${providerRowId}_${resultCode}"
            val now = System.currentTimeMillis()
            val prev = processedDeliveryEvents[key]
            if (prev != null && (now - prev) < 30_000L) {
                Log.d("SmsRepository", "Deduplicating delivery event: $key")
                return
            }
            processedDeliveryEvents[key] = now

            val status = when (action) {
                ACTION_SMS_SENT -> {
                    when (resultCode) {
                        Activity.RESULT_OK -> SmsDeliveryStatus.SENT
                        SmsManager.RESULT_ERROR_GENERIC_FAILURE,
                        SmsManager.RESULT_ERROR_NO_SERVICE,
                        SmsManager.RESULT_ERROR_NULL_PDU,
                        SmsManager.RESULT_ERROR_RADIO_OFF -> SmsDeliveryStatus.FAILED
                        else -> if (resultCode == Activity.RESULT_OK) SmsDeliveryStatus.SENT else SmsDeliveryStatus.FAILED
                    }
                }
                ACTION_SMS_DELIVERED -> {
                    if (resultCode == Activity.RESULT_OK) {
                        SmsDeliveryStatus.DELIVERED
                    } else {
                        SmsDeliveryStatus.FAILED
                    }
                }
                else -> return
            }

            val repo = activeInstance
            if (repo != null) {
                repo.updateMessageDeliveryStatus(messageId, status, providerRowId)
            } else {
                // Static persistence when UI is closed
                val prefs = context.getSharedPreferences("xeno_fast_sms_cache", Context.MODE_PRIVATE)
                val provId = providerRowId ?: prefs.getLong("link_msg_$messageId", -1L).takeIf { it != -1L }
                prefs.edit().apply {
                    if (messageId != -1L) putString("status_msg_$messageId", status.name)
                    if (provId != null) putString("status_prov_$provId", status.name)
                }.apply()

                if (provId != null) {
                    try {
                        val values = ContentValues()
                        when (status) {
                            SmsDeliveryStatus.SENT -> values.put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_SENT)
                            SmsDeliveryStatus.DELIVERED -> {
                                values.put(Telephony.Sms.STATUS, Telephony.Sms.STATUS_COMPLETE)
                                values.put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_SENT)
                            }
                            SmsDeliveryStatus.FAILED -> values.put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_FAILED)
                            SmsDeliveryStatus.SENDING -> values.put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_OUTBOX)
                        }
                        context.contentResolver.update(
                            Telephony.Sms.CONTENT_URI,
                            values,
                            "${Telephony.Sms._ID} = ?",
                            arrayOf(provId.toString())
                        )
                    } catch (e: Exception) {
                        Log.e("SmsRepository", "Failed updating SMS provider in static receiver: ${e.message}")
                    }
                }
            }
        }

        private val MULTIPART_PAREN_SUFFIX_REGEX = Regex("""\(\d+/\d+\)$""")
        private val MULTIPART_PAREN_PREFIX_REGEX = Regex("""^\(\d+/\d+\)""")
        private val MULTIPART_PLAIN_SUFFIX_REGEX = Regex("""\d+/\d+$""")
        private val MULTIPART_PLAIN_PREFIX_REGEX = Regex("""^\d+/\d+""")
        private val CLEAN_PAREN_SUFFIX_REGEX = Regex("""\s*\(\d+/\d+\)$""")
        private val CLEAN_PLAIN_SUFFIX_REGEX = Regex("""\s*\d+/\d+$""")
        private val CLEAN_PAREN_PREFIX_REGEX = Regex("""^\(\d+/\d+\)\s*""")
        private val CLEAN_PLAIN_PREFIX_REGEX = Regex("""^\d+/\d+\s*""")
    }

    private val contentResolver: ContentResolver = context.contentResolver
    private val fastSmsPrefs = context.getSharedPreferences("xeno_fast_sms_cache", Context.MODE_PRIVATE)

    // Memory cache for user interactions & instant 0ms thread/chat opening
    private val inMemoryMessages = mutableListOf<SmsMessage>()
    private val deliveryStatusMap = ConcurrentHashMap<Long, SmsDeliveryStatus>()
    private val deliveryStatusListeners = ConcurrentHashMap<Long, (SmsDeliveryStatus) -> Unit>()
    private val threadMessagesCache = ConcurrentHashMap<Long, List<SmsMessage>>()

    @Volatile
    private var cachedThreads: List<SmsThread> = emptyList()

    init {
        activeInstance = this
        if (!fastSmsPrefs.contains("last_seen_sms_baseline_ts")) {
            fastSmsPrefs.edit().putLong("last_seen_sms_baseline_ts", System.currentTimeMillis()).apply()
        }
        val persisted = loadPersistedLocalMessages()
        synchronized(inMemoryMessages) {
            inMemoryMessages.clear()
            inMemoryMessages.addAll(persisted)
        }
        registerDeliveryReceivers()
    }

    fun loadPersistedLocalMessages(): List<SmsMessage> {
        return try {
            val raw = fastSmsPrefs.getString("persisted_local_sms_messages", null) ?: return emptyList()
            val arr = org.json.JSONArray(raw)
            val list = ArrayList<SmsMessage>(arr.length())
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val statusStr = obj.optString("deliveryStatus", SmsDeliveryStatus.SENT.name)
                val status = try { SmsDeliveryStatus.valueOf(statusStr) } catch (_: Exception) { SmsDeliveryStatus.SENT }
                list.add(
                    SmsMessage(
                        id = obj.optLong("id", 0L),
                        threadId = obj.optLong("threadId", 0L),
                        address = obj.optString("address", ""),
                        body = obj.optString("body", ""),
                        date = obj.optLong("date", 0L),
                        isSent = obj.optBoolean("isSent", true),
                        read = obj.optBoolean("read", true),
                        deliveryStatus = status,
                        simName = obj.optString("simName", "").takeIf { it.isNotBlank() },
                        subId = obj.optInt("subId", -1)
                    )
                )
            }
            list
        } catch (e: Exception) {
            Log.e("SmsRepository", "Failed loading persisted local SMS", e)
            emptyList()
        }
    }

    private fun savePersistedLocalMessages(messages: List<SmsMessage>) {
        try {
            val arr = org.json.JSONArray()
            val limit = messages.size.coerceAtMost(300)
            for (i in 0 until limit) {
                val m = messages[i]
                val obj = org.json.JSONObject().apply {
                    put("id", m.id)
                    put("threadId", m.threadId)
                    put("address", m.address)
                    put("body", m.body)
                    put("date", m.date)
                    put("isSent", m.isSent)
                    put("read", m.read)
                    put("deliveryStatus", m.deliveryStatus.name)
                    put("simName", m.simName ?: "")
                    put("subId", m.subId)
                }
                arr.put(obj)
            }
            fastSmsPrefs.edit().putString("persisted_local_sms_messages", arr.toString()).apply()
        } catch (e: Exception) {
            Log.e("SmsRepository", "Failed saving persisted local SMS", e)
        }
    }

    fun getPersistedDeliveryStatus(rowOrMsgId: Long): SmsDeliveryStatus? {
        val provStatus = fastSmsPrefs.getString("status_prov_$rowOrMsgId", null)
        if (provStatus != null) {
            return try { SmsDeliveryStatus.valueOf(provStatus) } catch (_: Exception) { null }
        }
        val msgStatus = fastSmsPrefs.getString("status_msg_$rowOrMsgId", null)
        if (msgStatus != null) {
            return try { SmsDeliveryStatus.valueOf(msgStatus) } catch (_: Exception) { null }
        }
        return null
    }

    fun persistDeliveryStatus(id: Long, status: SmsDeliveryStatus) {
        fastSmsPrefs.edit()
            .putString("status_prov_$id", status.name)
            .putString("status_msg_$id", status.name)
            .apply()
    }

    fun linkMessageIdWithProviderId(messageId: Long, providerId: Long) {
        fastSmsPrefs.edit()
            .putLong("link_msg_$messageId", providerId)
            .putLong("link_prov_$providerId", messageId)
            .apply()
    }

    fun getLinkedProviderId(messageId: Long): Long? {
        val id = fastSmsPrefs.getLong("link_msg_$messageId", -1L)
        return if (id != -1L) id else null
    }

    fun getLinkedMessageId(providerId: Long): Long? {
        val id = fastSmsPrefs.getLong("link_prov_$providerId", -1L)
        return if (id != -1L) id else null
    }

    private fun normalizeAddressKey(address: String): String {
        val digits = address.filter { it.isDigit() }
        return if (digits.length >= 7) digits.takeLast(10) else address.trim().lowercase()
    }

    private fun isMessageMarkedReadLocally(threadId: Long, address: String, date: Long, providerRead: Boolean): Boolean {
        if (providerRead) return true
        val baselineTs = fastSmsPrefs.getLong("last_seen_sms_baseline_ts", 0L)
        if (baselineTs > 0L && date <= baselineTs) return true
        val threadReadTs = fastSmsPrefs.getLong("read_thread_$threadId", 0L)
        if (threadReadTs > 0L && date <= threadReadTs) return true
        val addrKey = normalizeAddressKey(address)
        if (addrKey.isNotEmpty()) {
            val addrReadTs = fastSmsPrefs.getLong("read_addr_$addrKey", 0L)
            if (addrReadTs > 0L && date <= addrReadTs) return true
        }
        return false
    }

    /**
     * Returns an instant snapshot of SMS conversation threads from memory or startup cache
     * so the Messages tab displays conversations on the very first frame.
     */
    fun getCachedThreadsSnapshot(): List<SmsThread> {
        if (cachedThreads.isNotEmpty()) return cachedThreads
        return try {
            val raw = fastSmsPrefs.getString("sms_threads_snapshot", null) ?: return emptyList()
            val arr = org.json.JSONArray(raw)
            val list = ArrayList<SmsThread>(arr.length())
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    SmsThread(
                        threadId = obj.optLong("threadId", 0L),
                        address = obj.optString("address", ""),
                        contactName = obj.optString("contactName", "")
                            .takeIf { it.isNotBlank() && !com.example.telecom.PriorityContactManager.isSyntheticPriorityName(it) },
                        snippet = obj.optString("snippet", ""),
                        date = obj.optLong("date", 0L),
                        read = obj.optBoolean("read", true),
                        messageCount = obj.optInt("messageCount", 1),
                        unreadCount = obj.optInt("unreadCount", 0)
                    )
                )
            }
            cachedThreads = list
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun saveThreadsSnapshot(threads: List<SmsThread>) {
        cachedThreads = threads
        try {
            val arr = org.json.JSONArray()
            val limit = threads.size.coerceAtMost(80)
            for (i in 0 until limit) {
                val t = threads[i]
                val obj = org.json.JSONObject().apply {
                    put("threadId", t.threadId)
                    put("address", t.address)
                    put("contactName", t.contactName ?: "")
                    put("snippet", t.snippet)
                    put("date", t.date)
                    put("read", t.read)
                    put("messageCount", t.messageCount)
                    put("unreadCount", t.unreadCount)
                }
                arr.put(obj)
            }
            fastSmsPrefs.edit().putString("sms_threads_snapshot", arr.toString()).apply()
        } catch (_: Exception) {}
    }

    /**
     * Returns pre-cached messages for a thread immediately (0ms latency) when opening a chat.
     */
    fun getCachedMessagesForThread(threadId: Long, address: String): List<SmsMessage> {
        threadMessagesCache[threadId]?.let { if (it.isNotEmpty()) return it }
        if (address.isNotBlank()) {
            val cleanAddr = address.filter { it.isDigit() }.takeLast(7)
            for ((_, msgs) in threadMessagesCache) {
                val first = msgs.firstOrNull() ?: continue
                if (first.address.equals(address, ignoreCase = true) ||
                    (cleanAddr.length >= 5 && first.address.filter { it.isDigit() }.endsWith(cleanAddr))
                ) {
                    return msgs
                }
            }
        }
        synchronized(inMemoryMessages) {
            val mem = inMemoryMessages.filter {
                it.threadId == threadId || it.address.equals(address, ignoreCase = true)
            }
            if (mem.isNotEmpty()) return stitchMultipartMessages(mem)
        }
        return emptyList()
    }

    private fun registerDeliveryReceivers() {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                val action = intent?.action ?: return
                val msgId = intent.getLongExtra(EXTRA_MESSAGE_ID, -1L)
                val provId = intent.getLongExtra(EXTRA_PROVIDER_ROW_ID, -1L).takeIf { it != -1L }
                val rCode = resultCode
                handleStaticDeliveryBroadcast(context, action, rCode, msgId, provId)
            }
        }
        val filter = IntentFilter().apply {
            addAction(ACTION_SMS_SENT)
            addAction(ACTION_SMS_DELIVERED)
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.applicationContext.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                context.applicationContext.registerReceiver(receiver, filter)
            }
        } catch (e: Exception) {
            Log.w("SmsRepository", "Could not register SMS delivery receiver: ${e.message}")
        }
    }

    fun updateMessageDeliveryStatus(
        messageId: Long,
        status: SmsDeliveryStatus,
        explicitProviderRowId: Long? = null
    ) {
        val provId = explicitProviderRowId ?: getLinkedProviderId(messageId)
        deliveryStatusMap[messageId] = status
        if (provId != null) {
            deliveryStatusMap[provId] = status
        }

        persistDeliveryStatus(messageId, status)
        if (provId != null) {
            persistDeliveryStatus(provId, status)
        }

        // On SENT result, update provider row type to MESSAGE_TYPE_SENT
        // On FAILED, set MESSAGE_TYPE_FAILED
        // On DELIVERED, set STATUS to STATUS_COMPLETE
        if (provId != null) {
            try {
                val values = ContentValues()
                when (status) {
                    SmsDeliveryStatus.SENT -> values.put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_SENT)
                    SmsDeliveryStatus.DELIVERED -> {
                        values.put(Telephony.Sms.STATUS, Telephony.Sms.STATUS_COMPLETE)
                        values.put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_SENT)
                    }
                    SmsDeliveryStatus.FAILED -> values.put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_FAILED)
                    SmsDeliveryStatus.SENDING -> values.put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_OUTBOX)
                }
                contentResolver.update(
                    Telephony.Sms.CONTENT_URI,
                    values,
                    "${Telephony.Sms._ID} = ?",
                    arrayOf(provId.toString())
                )
            } catch (e: Exception) {
                Log.e("SmsRepository", "Failed updating provider row: ${e.message}")
            }
        }

        synchronized(inMemoryMessages) {
            val idx = inMemoryMessages.indexOfFirst { it.id == messageId || (provId != null && it.id == provId) }
            if (idx != -1) {
                val updated = inMemoryMessages[idx].copy(deliveryStatus = status)
                inMemoryMessages[idx] = updated
                savePersistedLocalMessages(inMemoryMessages)
                val cachedList = threadMessagesCache[updated.threadId]
                if (cachedList != null) {
                    threadMessagesCache[updated.threadId] = cachedList.map {
                        if (it.id == messageId || (provId != null && it.id == provId)) it.copy(deliveryStatus = status) else it
                    }
                }
            }
        }
        deliveryStatusListeners[messageId]?.invoke(status)
        if (provId != null) {
            deliveryStatusListeners[provId]?.invoke(status)
        }
        if (status == SmsDeliveryStatus.DELIVERED || status == SmsDeliveryStatus.FAILED) {
            deliveryStatusListeners.remove(messageId)
            if (provId != null) {
                deliveryStatusListeners.remove(provId)
            }
        }
    }

    suspend fun getConversations(): List<SmsThread> = withContext(Dispatchers.IO) {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.READ_SMS
        ) == PackageManager.PERMISSION_GRANTED

        val threadsMap = LinkedHashMap<Long, MutableList<SmsMessage>>()
        val projection = arrayOf(
            Telephony.Sms._ID,
            Telephony.Sms.THREAD_ID,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE,
            Telephony.Sms.READ,
            Telephony.Sms.TYPE,
            Telephony.Sms.STATUS
        )

        if (hasPermission) {
            try {
                contentResolver.query(
                    Telephony.Sms.CONTENT_URI,
                    projection,
                    null,
                    null,
                    "${Telephony.Sms.DATE} DESC"
                )?.use { cursor ->
                    val idIdx = cursor.getColumnIndex(Telephony.Sms._ID)
                    val threadIdx = cursor.getColumnIndex(Telephony.Sms.THREAD_ID)
                    val addressIdx = cursor.getColumnIndex(Telephony.Sms.ADDRESS)
                    val bodyIdx = cursor.getColumnIndex(Telephony.Sms.BODY)
                    val dateIdx = cursor.getColumnIndex(Telephony.Sms.DATE)
                    val readIdx = cursor.getColumnIndex(Telephony.Sms.READ)
                    val typeIdx = cursor.getColumnIndex(Telephony.Sms.TYPE)
                    val statusIdx = cursor.getColumnIndex(Telephony.Sms.STATUS)

                    while (cursor.moveToNext()) {
                        val id = if (idIdx != -1) cursor.getLong(idIdx) else 0L
                        val threadId = if (threadIdx != -1) cursor.getLong(threadIdx) else 0L
                        val address = if (addressIdx != -1) cursor.getString(addressIdx) ?: "" else ""
                        val body = if (bodyIdx != -1) cursor.getString(bodyIdx) ?: "" else ""
                        val date = if (dateIdx != -1) cursor.getLong(dateIdx) else System.currentTimeMillis()
                        val providerRead = if (readIdx != -1) cursor.getInt(readIdx) == 1 else true
                        val type = if (typeIdx != -1) cursor.getInt(typeIdx) else Telephony.Sms.MESSAGE_TYPE_INBOX
                        val statusCol = if (statusIdx != -1) cursor.getInt(statusIdx) else Telephony.Sms.STATUS_NONE

                        val effectiveThreadId = when {
                            threadId != 0L -> threadId
                            address.isNotEmpty() -> address.hashCode().toLong()
                            else -> System.currentTimeMillis() + threadsMap.size
                        }
                        val effectiveId = if (id != 0L) id else (System.currentTimeMillis() + threadsMap.size)
                        val isSentMsg = type == Telephony.Sms.MESSAGE_TYPE_SENT ||
                            type == Telephony.Sms.MESSAGE_TYPE_OUTBOX ||
                            type == Telephony.Sms.MESSAGE_TYPE_QUEUED ||
                            type == Telephony.Sms.MESSAGE_TYPE_FAILED
                        val read = isSentMsg || isMessageMarkedReadLocally(effectiveThreadId, address, date, providerRead)
                        val resolvedStatus = getPersistedDeliveryStatus(effectiveId)
                            ?: deliveryStatusMap[effectiveId]
                            ?: run {
                                val linked = getLinkedMessageId(effectiveId)
                                if (linked != null) deliveryStatusMap[linked] else null
                            }
                            ?: when {
                                type == Telephony.Sms.MESSAGE_TYPE_FAILED || statusCol == Telephony.Sms.STATUS_FAILED -> SmsDeliveryStatus.FAILED
                                type == Telephony.Sms.MESSAGE_TYPE_OUTBOX || type == Telephony.Sms.MESSAGE_TYPE_QUEUED -> SmsDeliveryStatus.SENDING
                                type == Telephony.Sms.MESSAGE_TYPE_SENT -> {
                                    if (statusCol == Telephony.Sms.STATUS_COMPLETE) SmsDeliveryStatus.DELIVERED else SmsDeliveryStatus.SENT
                                }
                                else -> SmsDeliveryStatus.DELIVERED
                            }
                        val msg = SmsMessage(
                            id = effectiveId,
                            threadId = effectiveThreadId,
                            address = address,
                            body = body,
                            date = date,
                            isSent = isSentMsg,
                            read = read,
                            deliveryStatus = resolvedStatus
                        )
                        threadsMap.getOrPut(effectiveThreadId) { ArrayList() }.add(msg)
                    }
                }
            } catch (e: Exception) {
                Log.e("SmsRepository", "Failed to query SMS conversations", e)
            }
        }

        // Merge inMemoryMessages so all persisted and sent SMS are visible
        synchronized(inMemoryMessages) {
            for (memMsg in inMemoryMessages) {
                val matchingThread = threadsMap.entries.find { entry ->
                    entry.key == memMsg.threadId ||
                    entry.value.any { it.address.equals(memMsg.address, ignoreCase = true) || (normalizeAddressKey(it.address).isNotEmpty() && normalizeAddressKey(it.address) == normalizeAddressKey(memMsg.address)) }
                }
                val list = matchingThread?.value ?: threadsMap.getOrPut(memMsg.threadId) { ArrayList() }
                if (list.none { it.id == memMsg.id || (it.isSent == memMsg.isSent && it.body == memMsg.body && Math.abs(it.date - memMsg.date) < 3000L) }) {
                    list.add(memMsg)
                }
            }
        }

        if (threadsMap.isEmpty()) {
            cachedThreads = emptyList()
            return@withContext emptyList()
        }

        // Convert grouped messages into SmsThread summary and pre-cache per-thread messages
        val result = ArrayList<SmsThread>(threadsMap.size)
        for ((threadId, msgs) in threadsMap) {
            val stitched = stitchMultipartMessages(msgs)
            threadMessagesCache[threadId] = stitched
            val latest = stitched.lastOrNull() ?: stitched.maxByOrNull { it.date } ?: continue
            val rawAddress = latest.address.ifBlank {
                msgs.firstOrNull { it.address.isNotBlank() }?.address ?: ""
            }
            // Robust contact and sender name resolution (saved contact, alphanumeric sender, or formatted number)
            val contactName = when {
                rawAddress.isBlank() -> null
                rawAddress.any { it.isLetter() } -> rawAddress // Alphanumeric business sender e.g. "MPESA", "GOOGLE", "BANK"
                else -> contactsRepository.resolveContactNameFast(rawAddress)
                    ?: contactsRepository.resolveContactName(rawAddress)
                    ?: com.example.telecom.XenoNotificationHelper.resolveContactNameQuick(context, rawAddress)
            }
            val unreadCount = stitched.count { !it.read && !it.isSent }

            result.add(
                SmsThread(
                    threadId = threadId,
                    address = rawAddress,
                    contactName = contactName,
                    snippet = latest.body,
                    date = latest.date,
                    read = unreadCount == 0,
                    messageCount = stitched.size,
                    unreadCount = unreadCount
                )
            )
        }

        val sortedResult = result.sortedByDescending { it.date }
        saveThreadsSnapshot(sortedResult)
        sortedResult
    }

    /**
     * Reassembles multipart SMS messages into single, complete messages.
     * Large messages fragmented across PDUs/carrier segments are stitched together seamlessly.
     */
    fun stitchMultipartMessages(rawList: List<SmsMessage>): List<SmsMessage> {
        if (rawList.size <= 1) return rawList

        val sorted = rawList.sortedBy { it.date }
        val result = ArrayList<SmsMessage>(sorted.size)
        var currentMsg: SmsMessage? = null

        for (next in sorted) {
            if (currentMsg == null) {
                currentMsg = next
                continue
            }

            val sameDirection = currentMsg.isSent == next.isSent
            val sameSender = currentMsg.address.equals(next.address, ignoreCase = true) || currentMsg.threadId == next.threadId
            val timeDiff = Math.abs(next.date - currentMsg.date)

            // If exact duplicate message row within 5 seconds, collapse into one message
            if (sameDirection && sameSender && currentMsg.body.trim() == next.body.trim() && timeDiff <= 5000L) {
                currentMsg = currentMsg.copy(
                    date = Math.max(currentMsg.date, next.date),
                    read = currentMsg.read || next.read
                )
                continue
            }

            val isContinuation = sameDirection && sameSender && looksLikeMultipartPart(currentMsg.body, next.body)

            if (isContinuation) {
                val cleanedCurrent = cleanMultipartSuffix(currentMsg.body)
                val cleanedNext = cleanMultipartPrefix(next.body)
                currentMsg = currentMsg.copy(
                    body = cleanedCurrent + cleanedNext,
                    date = Math.max(currentMsg.date, next.date),
                    read = currentMsg.read && next.read
                )
            } else {
                result.add(currentMsg)
                currentMsg = next
            }
        }

        if (currentMsg != null) {
            result.add(currentMsg)
        }

        return result
    }

    private fun looksLikeMultipartPart(body1: String, body2: String): Boolean {
        if (!body1.contains('/') && !body2.contains('/')) return false
        val b1 = body1.trim()
        val b2 = body2.trim()
        if (b1.contains(MULTIPART_PAREN_SUFFIX_REGEX) || b2.contains(MULTIPART_PAREN_PREFIX_REGEX)) return true
        if (b1.contains(MULTIPART_PLAIN_SUFFIX_REGEX) || b2.contains(MULTIPART_PLAIN_PREFIX_REGEX)) return true
        return false
    }

    private fun cleanMultipartSuffix(text: String): String {
        if (!text.contains('/')) return text
        return text.replace(CLEAN_PAREN_SUFFIX_REGEX, "")
            .replace(CLEAN_PLAIN_SUFFIX_REGEX, "")
    }

    private fun cleanMultipartPrefix(text: String): String {
        if (!text.contains('/')) return text
        return text.replace(CLEAN_PAREN_PREFIX_REGEX, "")
            .replace(CLEAN_PLAIN_PREFIX_REGEX, "")
    }

    suspend fun getMessagesForThread(threadId: Long, address: String): List<SmsMessage> = withContext(Dispatchers.IO) {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.READ_SMS
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasPermission) {
            val fallback = synchronized(inMemoryMessages) {
                inMemoryMessages.filter { it.threadId == threadId }.sortedBy { it.date }
            }
            threadMessagesCache[threadId] = fallback
            return@withContext fallback
        }

        val messages = ArrayList<SmsMessage>()
        val availableSims = com.example.telecom.TelecomHelper.getAvailableSimCards(context)
        val defaultSimName = availableSims.firstOrNull { it.carrierName.isNotBlank() && !it.carrierName.startsWith("SIM ") }?.carrierName
            ?: availableSims.firstOrNull()?.carrierName

        val projList = mutableListOf(
            Telephony.Sms._ID,
            Telephony.Sms.THREAD_ID,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE,
            Telephony.Sms.READ,
            Telephony.Sms.TYPE,
            Telephony.Sms.STATUS
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            projList.add(Telephony.Sms.SUBSCRIPTION_ID)
        }
        val projection = projList.toTypedArray()

        try {
            val selection = "${Telephony.Sms.THREAD_ID} = ? OR ${Telephony.Sms.ADDRESS} = ?"
            val selectionArgs = arrayOf(threadId.toString(), address)
            contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                "${Telephony.Sms.DATE} ASC"
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndex(Telephony.Sms._ID)
                val threadIdx = cursor.getColumnIndex(Telephony.Sms.THREAD_ID)
                val addressIdx = cursor.getColumnIndex(Telephony.Sms.ADDRESS)
                val bodyIdx = cursor.getColumnIndex(Telephony.Sms.BODY)
                val dateIdx = cursor.getColumnIndex(Telephony.Sms.DATE)
                val readIdx = cursor.getColumnIndex(Telephony.Sms.READ)
                val typeIdx = cursor.getColumnIndex(Telephony.Sms.TYPE)
                val statusIdx = cursor.getColumnIndex(Telephony.Sms.STATUS)
                val subIdIdx = cursor.getColumnIndex("sub_id").takeIf { it != -1 }
                    ?: if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) cursor.getColumnIndex(Telephony.Sms.SUBSCRIPTION_ID) else -1

                while (cursor.moveToNext()) {
                    val id = if (idIdx != -1) cursor.getLong(idIdx) else 0L
                    val tId = if (threadIdx != -1) cursor.getLong(threadIdx) else threadId
                    val addr = if (addressIdx != -1) cursor.getString(addressIdx) ?: address else address
                    val body = if (bodyIdx != -1) cursor.getString(bodyIdx) ?: "" else ""
                    val date = if (dateIdx != -1) cursor.getLong(dateIdx) else System.currentTimeMillis()
                    val providerRead = if (readIdx != -1) cursor.getInt(readIdx) == 1 else true
                    val type = if (typeIdx != -1) cursor.getInt(typeIdx) else Telephony.Sms.MESSAGE_TYPE_INBOX
                    val statusCol = if (statusIdx != -1) cursor.getInt(statusIdx) else Telephony.Sms.STATUS_NONE
                    val subId = if (subIdIdx != -1) cursor.getInt(subIdIdx) else -1

                    val resolvedSimName = if (subId >= 0) {
                        availableSims.find { it.subscriptionId == subId }?.carrierName
                            ?: availableSims.getOrNull(subId)?.carrierName
                            ?: defaultSimName
                            ?: "SIM ${subId + 1}"
                    } else {
                        defaultSimName ?: "SIM 1"
                    }

                    val isSentMsg = type == Telephony.Sms.MESSAGE_TYPE_SENT ||
                        type == Telephony.Sms.MESSAGE_TYPE_OUTBOX ||
                        type == Telephony.Sms.MESSAGE_TYPE_QUEUED ||
                        type == Telephony.Sms.MESSAGE_TYPE_FAILED
                    val read = isSentMsg || isMessageMarkedReadLocally(tId, addr, date, providerRead)
                    val deliveryStatus = getPersistedDeliveryStatus(id)
                        ?: deliveryStatusMap[id]
                        ?: run {
                            val linked = getLinkedMessageId(id)
                            if (linked != null) deliveryStatusMap[linked] else null
                        }
                        ?: when {
                            type == Telephony.Sms.MESSAGE_TYPE_FAILED || statusCol == Telephony.Sms.STATUS_FAILED -> SmsDeliveryStatus.FAILED
                            type == Telephony.Sms.MESSAGE_TYPE_OUTBOX || type == Telephony.Sms.MESSAGE_TYPE_QUEUED -> SmsDeliveryStatus.SENDING
                            type == Telephony.Sms.MESSAGE_TYPE_SENT -> {
                                if (statusCol == Telephony.Sms.STATUS_COMPLETE) SmsDeliveryStatus.DELIVERED else SmsDeliveryStatus.SENT
                            }
                            else -> SmsDeliveryStatus.DELIVERED
                        }

                    messages.add(
                        SmsMessage(
                            id = id,
                            threadId = tId,
                            address = addr,
                            body = body,
                            date = date,
                            isSent = isSentMsg,
                            read = read,
                            deliveryStatus = deliveryStatus,
                            simName = resolvedSimName,
                            subId = subId
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("SmsRepository", "Failed to query thread messages", e)
        }

        val memMatches = synchronized(inMemoryMessages) {
            inMemoryMessages.filter { mem ->
                (mem.threadId == threadId || mem.address.equals(address, ignoreCase = true) ||
                 (normalizeAddressKey(mem.address).isNotEmpty() && normalizeAddressKey(mem.address) == normalizeAddressKey(address))) &&
                messages.none { prov ->
                    prov.id == mem.id ||
                    getLinkedProviderId(mem.id) == prov.id ||
                    (prov.isSent && mem.isSent && prov.body == mem.body && Math.abs(prov.date - mem.date) < 10000L)
                }
            }
        }
        val allMessages = if (messages.isEmpty()) {
            memMatches
        } else if (memMatches.isEmpty()) {
            messages
        } else {
            (messages + memMatches).distinctBy { it.id }
        }
        val stitched = stitchMultipartMessages(allMessages)
        threadMessagesCache[threadId] = stitched
        stitched
    }

    suspend fun sendSms(
        destinationAddress: String,
        text: String,
        currentThreadId: Long?,
        messageId: Long = System.nanoTime(),
        subscriptionId: Int? = null,
        simName: String? = null,
        onStatusUpdate: ((SmsDeliveryStatus) -> Unit)? = null
    ): Result<SmsMessage> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (onStatusUpdate != null) {
            deliveryStatusListeners[messageId] = onStatusUpdate
        }
        deliveryStatusMap[messageId] = SmsDeliveryStatus.SENDING

        val hasSendPermission = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.SEND_SMS
        ) == PackageManager.PERMISSION_GRANTED

        val pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT or
            (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)

        // Attempt actual SmsManager dispatch if permission is available
        if (hasSendPermission) {
            try {
                val effectiveSubId = if (subscriptionId != null && subscriptionId != -1) {
                    subscriptionId
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    val defaultSmsSub = android.telephony.SubscriptionManager.getDefaultSmsSubscriptionId()
                    if (defaultSmsSub != android.telephony.SubscriptionManager.INVALID_SUBSCRIPTION_ID && defaultSmsSub >= 0) {
                        defaultSmsSub
                    } else null
                } else null

                val smsManager: SmsManager = if (effectiveSubId != null) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        context.getSystemService(SmsManager::class.java).createForSubscriptionId(effectiveSubId)
                    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                        @Suppress("DEPRECATION")
                        SmsManager.getSmsManagerForSubscriptionId(effectiveSubId)
                    } else {
                        @Suppress("DEPRECATION")
                        SmsManager.getDefault()
                    }
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    context.getSystemService(SmsManager::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    SmsManager.getDefault()
                }

                var providerRowId: Long? = null
                try {
                    val values = ContentValues().apply {
                        put(Telephony.Sms.ADDRESS, destinationAddress)
                        put(Telephony.Sms.BODY, text)
                        put(Telephony.Sms.DATE, now)
                        put(Telephony.Sms.READ, 1)
                        put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_OUTBOX)
                        if (currentThreadId != null && currentThreadId > 0) {
                            put(Telephony.Sms.THREAD_ID, currentThreadId)
                        }
                        if (subscriptionId != null && subscriptionId != -1) {
                            put("sub_id", subscriptionId)
                            put("subscription_id", subscriptionId)
                        }
                    }
                    val insertedUri = contentResolver.insert(Telephony.Sms.CONTENT_URI, values)
                        ?: contentResolver.insert(Telephony.Sms.Sent.CONTENT_URI, values)
                    providerRowId = insertedUri?.lastPathSegment?.toLongOrNull()
                } catch (e: Exception) {
                    Log.d("SmsRepository", "Sent SMS stored in provider note: ${e.message}")
                }

                if (providerRowId != null) {
                    linkMessageIdWithProviderId(messageId, providerRowId)
                    persistDeliveryStatus(providerRowId, SmsDeliveryStatus.SENDING)
                    deliveryStatusMap[providerRowId] = SmsDeliveryStatus.SENDING
                }
                persistDeliveryStatus(messageId, SmsDeliveryStatus.SENDING)
                deliveryStatusMap[messageId] = SmsDeliveryStatus.SENDING

                val sentIntent = PendingIntent.getBroadcast(
                    context,
                    (messageId and 0x7FFFFFFF).toInt(),
                    Intent(ACTION_SMS_SENT).apply {
                        setPackage(context.packageName)
                        putExtra(EXTRA_MESSAGE_ID, messageId)
                        if (providerRowId != null) {
                            putExtra(EXTRA_PROVIDER_ROW_ID, providerRowId)
                        }
                    },
                    pendingFlags
                )
                val deliveryIntent = PendingIntent.getBroadcast(
                    context,
                    ((messageId + 1) and 0x7FFFFFFF).toInt(),
                    Intent(ACTION_SMS_DELIVERED).apply {
                        setPackage(context.packageName)
                        putExtra(EXTRA_MESSAGE_ID, messageId)
                        if (providerRowId != null) {
                            putExtra(EXTRA_PROVIDER_ROW_ID, providerRowId)
                        }
                    },
                    pendingFlags
                )

                val parts = smsManager.divideMessage(text)
                if (parts.size > 1) {
                    val sentIntents = ArrayList<PendingIntent>(parts.map { sentIntent })
                    val deliveryIntents = ArrayList<PendingIntent>(parts.map { deliveryIntent })
                    smsManager.sendMultipartTextMessage(destinationAddress, null, parts, sentIntents, deliveryIntents)
                } else {
                    smsManager.sendTextMessage(destinationAddress, null, text, sentIntent, deliveryIntent)
                }
            } catch (e: Exception) {
                Log.e("SmsRepository", "Carrier SMS send error: ${e.message}", e)
                updateMessageDeliveryStatus(messageId, SmsDeliveryStatus.FAILED)
                return@withContext Result.failure(e)
            }
        } else {
            Log.w("SmsRepository", "SEND_SMS permission not granted")
            updateMessageDeliveryStatus(messageId, SmsDeliveryStatus.FAILED)
            return@withContext Result.failure(SecurityException("SEND_SMS permission not granted"))
        }

        // Create the message object
        val availableSims = com.example.telecom.TelecomHelper.getAvailableSimCards(context)
        val matchedSim = if (subscriptionId != null && subscriptionId != -1) {
            availableSims.find { it.subscriptionId == subscriptionId }
        } else null
        val defaultSim = matchedSim
            ?: availableSims.firstOrNull { it.carrierName.isNotBlank() && !it.carrierName.startsWith("SIM ") }
            ?: availableSims.firstOrNull()

        val resolvedSimName = simName?.takeIf { it.isNotBlank() }
            ?: defaultSim?.carrierName
            ?: "SIM 1"
        val resolvedSubId = subscriptionId?.takeIf { it != -1 }
            ?: defaultSim?.subscriptionId
            ?: 0

        val effectiveThreadId = currentThreadId ?: destinationAddress.hashCode().toLong()
        val newMessage = SmsMessage(
            id = messageId,
            threadId = effectiveThreadId,
            address = destinationAddress,
            body = text,
            date = now,
            isSent = true,
            read = true,
            deliveryStatus = SmsDeliveryStatus.SENDING,
            simName = resolvedSimName,
            subId = resolvedSubId
        )

        synchronized(inMemoryMessages) {
            inMemoryMessages.add(newMessage)
            savePersistedLocalMessages(inMemoryMessages)
        }
        val currentCached = threadMessagesCache[effectiveThreadId] ?: emptyList()
        threadMessagesCache[effectiveThreadId] = currentCached + newMessage
        Result.success(newMessage)
    }

    suspend fun deleteMessage(messageId: Long): Boolean = withContext(Dispatchers.IO) {
        synchronized(inMemoryMessages) {
            inMemoryMessages.removeAll { it.id == messageId }
            savePersistedLocalMessages(inMemoryMessages)
        }
        for ((tId, list) in threadMessagesCache) {
            if (list.any { it.id == messageId }) {
                threadMessagesCache[tId] = list.filter { it.id != messageId }
            }
        }

        try {
            val rows = contentResolver.delete(
                Telephony.Sms.CONTENT_URI,
                "${Telephony.Sms._ID} = ?",
                arrayOf(messageId.toString())
            )
            return@withContext rows > 0
        } catch (e: Exception) {
            Log.e("SmsRepository", "Cannot delete SMS from provider: ${e.message}")
            return@withContext true
        }
    }

    suspend fun deleteThread(threadId: Long, address: String): Boolean = withContext(Dispatchers.IO) {
        synchronized(inMemoryMessages) {
            inMemoryMessages.removeAll { it.threadId == threadId || it.address == address }
            savePersistedLocalMessages(inMemoryMessages)
        }
        threadMessagesCache.remove(threadId)
        cachedThreads = cachedThreads.filter { it.threadId != threadId && it.address != address }
        saveThreadsSnapshot(cachedThreads)
        try {
            val rows = contentResolver.delete(
                Telephony.Sms.CONTENT_URI,
                "${Telephony.Sms.THREAD_ID} = ? OR ${Telephony.Sms.ADDRESS} = ?",
                arrayOf(threadId.toString(), address)
            )
            return@withContext rows > 0
        } catch (e: Exception) {
            Log.e("SmsRepository", "Failed deleting thread from provider: ${e.message}")
            return@withContext true
        }
    }

    suspend fun markMessageAsRead(messageId: Long): Boolean = withContext(Dispatchers.IO) {
        val values = ContentValues().apply {
            put(Telephony.Sms.READ, 1)
        }
        try {
            val rows = contentResolver.update(
                Telephony.Sms.CONTENT_URI,
                values,
                "${Telephony.Sms._ID} = ?",
                arrayOf(messageId.toString())
            )
            return@withContext rows > 0
        } catch (e: Exception) {
            Log.e("SmsRepository", "Failed marking SMS as read: ${e.message}")
            return@withContext false
        }
    }

    suspend fun markThreadAsRead(threadId: Long, address: String): Boolean = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val addrKey = normalizeAddressKey(address)
        fastSmsPrefs.edit().apply {
            putLong("read_thread_$threadId", now)
            if (addrKey.isNotEmpty()) {
                putLong("read_addr_$addrKey", now)
            }
        }.apply()

        // Update in-memory message states
        synchronized(inMemoryMessages) {
            inMemoryMessages.forEachIndexed { idx, msg ->
                if (msg.threadId == threadId || msg.address == address) {
                    inMemoryMessages[idx] = msg.copy(read = true)
                }
            }
        }
        threadMessagesCache[threadId]?.let { cached ->
            threadMessagesCache[threadId] = cached.map { if (!it.read) it.copy(read = true) else it }
        }
        cachedThreads = cachedThreads.map {
            if (it.threadId == threadId || it.address == address) {
                it.copy(read = true, unreadCount = 0)
            } else {
                it
            }
        }
        saveThreadsSnapshot(cachedThreads)

        val values = ContentValues().apply {
            put(Telephony.Sms.READ, 1)
        }
        try {
            val selection = "(${Telephony.Sms.THREAD_ID} = ? OR ${Telephony.Sms.ADDRESS} = ?) AND ${Telephony.Sms.READ} = 0"
            val selectionArgs = arrayOf(threadId.toString(), address)
            contentResolver.update(
                Telephony.Sms.CONTENT_URI,
                values,
                selection,
                selectionArgs
            )
            true
        } catch (e: Exception) {
            Log.e("SmsRepository", "Failed marking thread as read: ${e.message}")
            true
        }
    }

    suspend fun insertIncomingSms(
        address: String,
        body: String,
        date: Long = System.currentTimeMillis()
    ): Long = withContext(Dispatchers.IO) {
        val effectiveThreadId = address.hashCode().toLong()
        var insertedId = System.currentTimeMillis()

        // Check if a recent incoming SMS fragment from this address exists within 45s to stitch seamlessly
        val recentPart = synchronized(inMemoryMessages) {
            inMemoryMessages.lastOrNull {
                it.address.equals(address, ignoreCase = true) && !it.isSent && Math.abs(date - it.date) < 45000L
            }
        }
        if (recentPart != null) {
            val cleanedPrev = cleanMultipartSuffix(recentPart.body)
            val cleanedNext = cleanMultipartPrefix(body)
            val combinedBody = cleanedPrev + cleanedNext
            val updatedMsg = recentPart.copy(body = combinedBody, date = date)
            synchronized(inMemoryMessages) {
                val idx = inMemoryMessages.indexOf(recentPart)
                if (idx != -1) {
                    inMemoryMessages[idx] = updatedMsg
                    return@withContext updatedMsg.id
                }
            }
        }

        val values = ContentValues().apply {
            put(Telephony.Sms.ADDRESS, address)
            put(Telephony.Sms.BODY, body)
            put(Telephony.Sms.DATE, date)
            put(Telephony.Sms.READ, 0)
            put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_INBOX)
        }
        try {
            val uri = contentResolver.insert(Telephony.Sms.Inbox.CONTENT_URI, values)
            insertedId = uri?.lastPathSegment?.toLongOrNull() ?: insertedId
        } catch (e: Exception) {
            Log.e("SmsRepository", "Error inserting incoming SMS: ${e.message}")
        }

        val incomingMsg = SmsMessage(
            id = insertedId,
            threadId = effectiveThreadId,
            address = address,
            body = body,
            date = date,
            isSent = false,
            read = false
        )
        synchronized(inMemoryMessages) {
            inMemoryMessages.add(incomingMsg)
        }
        val existingCached = threadMessagesCache[effectiveThreadId] ?: emptyList()
        threadMessagesCache[effectiveThreadId] = existingCached + incomingMsg
        return@withContext insertedId
    }

    suspend fun getThreadIdForAddress(address: String): Long? = withContext(Dispatchers.IO) {
        val clean = address.filter { it.isDigit() }
        val threads = cachedThreads.ifEmpty { getConversations() }
        val match = threads.find {
            it.address.filter { c -> c.isDigit() } == clean
        }
        match?.threadId
    }
}
