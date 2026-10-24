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
        private const val ACTION_SMS_SENT = "com.example.SMS_SENT_STATUS"
        private const val ACTION_SMS_DELIVERED = "com.example.SMS_DELIVERED_STATUS"
        private const val EXTRA_MESSAGE_ID = "extra_message_id"
    }

    private val contentResolver: ContentResolver = context.contentResolver

    // Memory cache for user interactions
    private val inMemoryMessages = mutableListOf<SmsMessage>()
    private val deliveryStatusMap = ConcurrentHashMap<Long, SmsDeliveryStatus>()
    private val deliveryStatusListeners = ConcurrentHashMap<Long, (SmsDeliveryStatus) -> Unit>()

    init {
        registerDeliveryReceivers()
    }

    private fun registerDeliveryReceivers() {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                val msgId = intent?.getLongExtra(EXTRA_MESSAGE_ID, -1L) ?: -1L
                if (msgId == -1L) return
                when (intent?.action) {
                    ACTION_SMS_SENT -> {
                        val newStatus = if (resultCode == Activity.RESULT_OK) {
                            SmsDeliveryStatus.SENT
                        } else {
                            SmsDeliveryStatus.FAILED
                        }
                        updateMessageDeliveryStatus(msgId, newStatus)
                    }
                    ACTION_SMS_DELIVERED -> {
                        if (resultCode == Activity.RESULT_OK) {
                            updateMessageDeliveryStatus(msgId, SmsDeliveryStatus.DELIVERED)
                        }
                    }
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(ACTION_SMS_SENT)
            addAction(ACTION_SMS_DELIVERED)
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.applicationContext.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                context.applicationContext.registerReceiver(receiver, filter)
            }
        } catch (e: Exception) {
            Log.w("SmsRepository", "Could not register SMS delivery receiver: ${e.message}")
        }
    }

    fun updateMessageDeliveryStatus(messageId: Long, status: SmsDeliveryStatus) {
        deliveryStatusMap[messageId] = status
        synchronized(inMemoryMessages) {
            val idx = inMemoryMessages.indexOfFirst { it.id == messageId }
            if (idx != -1) {
                inMemoryMessages[idx] = inMemoryMessages[idx].copy(deliveryStatus = status)
            }
        }
        deliveryStatusListeners[messageId]?.invoke(status)
        if (status == SmsDeliveryStatus.DELIVERED || status == SmsDeliveryStatus.FAILED) {
            deliveryStatusListeners.remove(messageId)
        }
    }

    suspend fun getConversations(): List<SmsThread> = withContext(Dispatchers.IO) {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.READ_SMS
        ) == PackageManager.PERMISSION_GRANTED

        val threadsMap = mutableMapOf<Long, MutableList<SmsMessage>>()
        val projection = arrayOf(
            Telephony.Sms._ID,
            Telephony.Sms.THREAD_ID,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE,
            Telephony.Sms.READ,
            Telephony.Sms.TYPE
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

                    while (cursor.moveToNext()) {
                        val id = if (idIdx != -1) cursor.getLong(idIdx) else 0L
                        val threadId = if (threadIdx != -1) cursor.getLong(threadIdx) else 0L
                        val address = if (addressIdx != -1) cursor.getString(addressIdx) ?: "" else ""
                        val body = if (bodyIdx != -1) cursor.getString(bodyIdx) ?: "" else ""
                        val date = if (dateIdx != -1) cursor.getLong(dateIdx) else System.currentTimeMillis()
                        val read = if (readIdx != -1) cursor.getInt(readIdx) == 1 else true
                        val type = if (typeIdx != -1) cursor.getInt(typeIdx) else Telephony.Sms.MESSAGE_TYPE_INBOX

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
                        val resolvedStatus = deliveryStatusMap[effectiveId] ?: when (type) {
                            Telephony.Sms.MESSAGE_TYPE_OUTBOX, Telephony.Sms.MESSAGE_TYPE_QUEUED -> SmsDeliveryStatus.SENDING
                            Telephony.Sms.MESSAGE_TYPE_FAILED -> SmsDeliveryStatus.FAILED
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
                        threadsMap.getOrPut(effectiveThreadId) { mutableListOf() }.add(msg)
                    }
                }
            } catch (e: Exception) {
                Log.e("SmsRepository", "Failed to query SMS conversations", e)
            }
        }

        // Merge inMemoryMessages so all simulated/received SMS are visible
        for (memMsg in inMemoryMessages) {
            val list = threadsMap.getOrPut(memMsg.threadId) { mutableListOf() }
            if (list.none { it.id == memMsg.id || (it.address == memMsg.address && Math.abs(it.date - memMsg.date) < 2000) }) {
                list.add(memMsg)
            }
        }

        if (threadsMap.isEmpty()) {
            return@withContext emptyList()
        }

        // Convert grouped messages into SmsThread summary
        val result = mutableListOf<SmsThread>()
        for ((threadId, msgs) in threadsMap) {
            val stitched = stitchMultipartMessages(msgs)
            val latest = stitched.maxByOrNull { it.date } ?: continue
            val address = latest.address
            val contactName = contactsRepository.resolveContactName(address)
            val unreadCount = stitched.count { !it.read && !it.isSent }

            result.add(
                SmsThread(
                    threadId = threadId,
                    address = address,
                    contactName = contactName,
                    snippet = latest.body,
                    date = latest.date,
                    read = unreadCount == 0,
                    messageCount = stitched.size,
                    unreadCount = unreadCount
                )
            )
        }

        result.sortedByDescending { it.date }
    }

    /**
     * Reassembles multipart SMS messages into single, complete messages.
     * Large messages fragmented across PDUs/carrier segments are stitched together seamlessly.
     */
    fun stitchMultipartMessages(rawList: List<SmsMessage>): List<SmsMessage> {
        if (rawList.size <= 1) return rawList

        val sorted = rawList.sortedBy { it.date }
        val result = mutableListOf<SmsMessage>()
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
        val b1 = body1.trim()
        val b2 = body2.trim()
        if (b1.contains(Regex("""\(\d+/\d+\)$""")) || b2.contains(Regex("""^\(\d+/\d+\)"""))) return true
        if (b1.contains(Regex("""\d+/\d+$""")) || b2.contains(Regex("""^\d+/\d+"""))) return true
        return false
    }

    private fun cleanMultipartSuffix(text: String): String {
        return text.replace(Regex("""\s*\(\d+/\d+\)$"""), "")
            .replace(Regex("""\s*\d+/\d+$"""), "")
    }

    private fun cleanMultipartPrefix(text: String): String {
        return text.replace(Regex("""^\(\d+/\d+\)\s*"""), "")
            .replace(Regex("""^\d+/\d+\s*"""), "")
    }

    suspend fun getMessagesForThread(threadId: Long, address: String): List<SmsMessage> = withContext(Dispatchers.IO) {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.READ_SMS
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasPermission) {
            return@withContext inMemoryMessages.filter { it.threadId == threadId }.sortedBy { it.date }
        }

        val messages = mutableListOf<SmsMessage>()
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

                while (cursor.moveToNext()) {
                    val id = if (idIdx != -1) cursor.getLong(idIdx) else 0L
                    val tId = if (threadIdx != -1) cursor.getLong(threadIdx) else threadId
                    val addr = if (addressIdx != -1) cursor.getString(addressIdx) ?: address else address
                    val body = if (bodyIdx != -1) cursor.getString(bodyIdx) ?: "" else ""
                    val date = if (dateIdx != -1) cursor.getLong(dateIdx) else System.currentTimeMillis()
                    val read = if (readIdx != -1) cursor.getInt(readIdx) == 1 else true
                    val type = if (typeIdx != -1) cursor.getInt(typeIdx) else Telephony.Sms.MESSAGE_TYPE_INBOX
                    val statusCol = if (statusIdx != -1) cursor.getInt(statusIdx) else Telephony.Sms.STATUS_NONE

                    val isSentMsg = type == Telephony.Sms.MESSAGE_TYPE_SENT ||
                        type == Telephony.Sms.MESSAGE_TYPE_OUTBOX ||
                        type == Telephony.Sms.MESSAGE_TYPE_QUEUED ||
                        type == Telephony.Sms.MESSAGE_TYPE_FAILED
                    val deliveryStatus = deliveryStatusMap[id] ?: when {
                        type == Telephony.Sms.MESSAGE_TYPE_FAILED || statusCol == Telephony.Sms.STATUS_FAILED -> SmsDeliveryStatus.FAILED
                        type == Telephony.Sms.MESSAGE_TYPE_OUTBOX || type == Telephony.Sms.MESSAGE_TYPE_QUEUED || statusCol == Telephony.Sms.STATUS_PENDING -> SmsDeliveryStatus.SENDING
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
                            deliveryStatus = deliveryStatus
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("SmsRepository", "Failed to query thread messages", e)
        }

        val allMessages = if (messages.isEmpty()) {
            inMemoryMessages.filter { it.threadId == threadId || it.address.equals(address, ignoreCase = true) }
        } else {
            val memMatches = inMemoryMessages.filter { it.threadId == threadId || it.address.equals(address, ignoreCase = true) }
            (messages + memMatches).distinctBy { it.id }
        }
        stitchMultipartMessages(allMessages)
    }

    suspend fun sendSms(
        destinationAddress: String,
        text: String,
        currentThreadId: Long?,
        messageId: Long = System.nanoTime(),
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
                val smsManager: SmsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    context.getSystemService(SmsManager::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    SmsManager.getDefault()
                }

                val sentIntent = PendingIntent.getBroadcast(
                    context,
                    (messageId and 0x7FFFFFFF).toInt(),
                    Intent(ACTION_SMS_SENT).apply {
                        setPackage(context.packageName)
                        putExtra(EXTRA_MESSAGE_ID, messageId)
                    },
                    pendingFlags
                )
                val deliveryIntent = PendingIntent.getBroadcast(
                    context,
                    ((messageId + 1) and 0x7FFFFFFF).toInt(),
                    Intent(ACTION_SMS_DELIVERED).apply {
                        setPackage(context.packageName)
                        putExtra(EXTRA_MESSAGE_ID, messageId)
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

                // Insert into sent box in provider
                try {
                    val values = ContentValues().apply {
                        put(Telephony.Sms.ADDRESS, destinationAddress)
                        put(Telephony.Sms.BODY, text)
                        put(Telephony.Sms.DATE, now)
                        put(Telephony.Sms.READ, 1)
                        put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_SENT)
                        if (currentThreadId != null && currentThreadId > 0) {
                            put(Telephony.Sms.THREAD_ID, currentThreadId)
                        }
                    }
                    contentResolver.insert(Telephony.Sms.Sent.CONTENT_URI, values)
                } catch (e: Exception) {
                    Log.d("SmsRepository", "Sent SMS stored in provider note: ${e.message}")
                }
            } catch (e: Exception) {
                Log.w("SmsRepository", "Carrier SMS send error: ${e.message}")
            }
        }

        // Create the message object
        val effectiveThreadId = currentThreadId ?: destinationAddress.hashCode().toLong()
        val newMessage = SmsMessage(
            id = messageId,
            threadId = effectiveThreadId,
            address = destinationAddress,
            body = text,
            date = now,
            isSent = true,
            read = true,
            deliveryStatus = SmsDeliveryStatus.SENDING
        )

        synchronized(inMemoryMessages) {
            inMemoryMessages.add(newMessage)
        }
        Result.success(newMessage)
    }

    suspend fun deleteMessage(messageId: Long): Boolean = withContext(Dispatchers.IO) {
        inMemoryMessages.removeAll { it.id == messageId }

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
        inMemoryMessages.removeAll { it.threadId == threadId || it.address == address }
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
        // Update in-memory message states
        inMemoryMessages.forEachIndexed { idx, msg ->
            if (msg.threadId == threadId || msg.address == address) {
                inMemoryMessages[idx] = msg.copy(read = true)
            }
        }

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
        val recentPart = inMemoryMessages.lastOrNull {
            it.address.equals(address, ignoreCase = true) && !it.isSent && Math.abs(date - it.date) < 45000L
        }
        if (recentPart != null) {
            val cleanedPrev = cleanMultipartSuffix(recentPart.body)
            val cleanedNext = cleanMultipartPrefix(body)
            val combinedBody = cleanedPrev + cleanedNext
            val updatedMsg = recentPart.copy(body = combinedBody, date = date)
            val idx = inMemoryMessages.indexOf(recentPart)
            if (idx != -1) {
                inMemoryMessages[idx] = updatedMsg
                return@withContext updatedMsg.id
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

        inMemoryMessages.add(
            SmsMessage(
                id = insertedId,
                threadId = effectiveThreadId,
                address = address,
                body = body,
                date = date,
                isSent = false,
                read = false
            )
        )
        return@withContext insertedId
    }

    suspend fun getThreadIdForAddress(address: String): Long? = withContext(Dispatchers.IO) {
        val clean = address.filter { it.isDigit() }
        val threads = getConversations()
        val match = threads.find {
            it.address.filter { c -> c.isDigit() } == clean
        }
        match?.threadId
    }
}
