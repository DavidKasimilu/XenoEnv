package com.example.data

import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CallLog
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.data.models.CallLogEntry
import com.example.data.models.CallType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class CallLogRepository(private val context: Context) {

    private val contentResolver: ContentResolver = context.contentResolver
    private val fastCachePrefs = context.getSharedPreferences("xeno_fast_call_cache", Context.MODE_PRIVATE)

    @Volatile
    private var cachedCallLogs: List<CallLogEntry> = emptyList()

    /**
     * Returns an instant snapshot of Call History from memory or lightweight startup cache
     * so the Calls screen displays records immediately on the very first frame.
     */
    fun getCachedCallLogsSnapshot(): List<CallLogEntry> {
        if (cachedCallLogs.isNotEmpty()) return cachedCallLogs
        return try {
            val raw = fastCachePrefs.getString("call_logs_snapshot", null) ?: return emptyList()
            val arr = JSONArray(raw)
            val list = ArrayList<CallLogEntry>(arr.length())
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    CallLogEntry(
                        id = obj.optLong("id", 0L),
                        number = obj.optString("number", ""),
                        cachedName = obj.optString("cachedName", "")
                            .takeIf { it.isNotBlank() && !com.example.telecom.PriorityContactManager.isSyntheticPriorityName(it) },
                        type = runCatching { CallType.valueOf(obj.optString("type", "OUTGOING")) }.getOrDefault(CallType.OUTGOING),
                        date = obj.optLong("date", 0L),
                        durationSeconds = obj.optLong("durationSeconds", 0L),
                        photoUri = obj.optString("photoUri", "").takeIf { it.isNotBlank() },
                        simSlot = obj.optInt("simSlot", 0),
                        simName = obj.optString("simName", "").takeIf { it.isNotBlank() },
                        phoneAccountId = obj.optString("phoneAccountId", "").takeIf { it.isNotBlank() }
                    )
                )
            }
            cachedCallLogs = list
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveCallLogsSnapshot(entries: List<CallLogEntry>) {
        cachedCallLogs = entries
        try {
            val arr = JSONArray()
            val limit = entries.size.coerceAtMost(80)
            for (i in 0 until limit) {
                val e = entries[i]
                val obj = JSONObject().apply {
                    put("id", e.id)
                    put("number", e.number)
                    put("cachedName", e.cachedName ?: "")
                    put("type", e.type.name)
                    put("date", e.date)
                    put("durationSeconds", e.durationSeconds)
                    put("photoUri", e.photoUri ?: "")
                    put("simSlot", e.simSlot)
                    put("simName", e.simName ?: "")
                    put("phoneAccountId", e.phoneAccountId ?: "")
                }
                arr.put(obj)
            }
            fastCachePrefs.edit().putString("call_logs_snapshot", arr.toString()).apply()
        } catch (_: Exception) {}
    }

    suspend fun getCallLogs(): List<CallLogEntry> = withContext(Dispatchers.IO) {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.READ_CALL_LOG
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasPermission) {
            return@withContext cachedCallLogs
        }

        val entries = ArrayList<CallLogEntry>(150)
        val availableSims = com.example.telecom.TelecomHelper.getAvailableSimCards(context)
        val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as? android.telecom.TelecomManager
        val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as? android.telephony.TelephonyManager

        val detectedCarrierFallback = availableSims.firstOrNull { it.carrierName.isNotBlank() && !it.carrierName.startsWith("SIM ") }?.carrierName
            ?: telephonyManager?.simOperatorName?.takeIf { it.isNotBlank() }
            ?: telephonyManager?.networkOperatorName?.takeIf { it.isNotBlank() }
            ?: availableSims.firstOrNull()?.carrierName

        val projList = mutableListOf(
            CallLog.Calls._ID,
            CallLog.Calls.NUMBER,
            CallLog.Calls.CACHED_NAME,
            CallLog.Calls.TYPE,
            CallLog.Calls.DATE,
            CallLog.Calls.DURATION,
            CallLog.Calls.CACHED_PHOTO_URI
        )
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            projList.add(CallLog.Calls.PHONE_ACCOUNT_ID)
            projList.add(CallLog.Calls.PHONE_ACCOUNT_COMPONENT_NAME)
        }
        val projection = projList.toTypedArray()

        try {
            val cursor = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                val queryArgs = android.os.Bundle().apply {
                    putStringArray(ContentResolver.QUERY_ARG_SORT_COLUMNS, arrayOf(CallLog.Calls.DATE))
                    putInt(ContentResolver.QUERY_ARG_SORT_DIRECTION, ContentResolver.QUERY_SORT_DIRECTION_DESCENDING)
                    putInt(ContentResolver.QUERY_ARG_LIMIT, 150)
                }
                try {
                    contentResolver.query(CallLog.Calls.CONTENT_URI, projection, queryArgs, null)
                } catch (_: Exception) {
                    contentResolver.query(CallLog.Calls.CONTENT_URI, projection, null, null, "${CallLog.Calls.DATE} DESC")
                }
            } else {
                contentResolver.query(
                    CallLog.Calls.CONTENT_URI,
                    projection,
                    null,
                    null,
                    "${CallLog.Calls.DATE} DESC"
                )
            }

            cursor?.use { cur ->
                val idIdx = cur.getColumnIndex(CallLog.Calls._ID)
                val numberIdx = cur.getColumnIndex(CallLog.Calls.NUMBER)
                val nameIdx = cur.getColumnIndex(CallLog.Calls.CACHED_NAME)
                val typeIdx = cur.getColumnIndex(CallLog.Calls.TYPE)
                val dateIdx = cur.getColumnIndex(CallLog.Calls.DATE)
                val durationIdx = cur.getColumnIndex(CallLog.Calls.DURATION)
                val photoIdx = cur.getColumnIndex(CallLog.Calls.CACHED_PHOTO_URI)

                val phoneAccountIdx = cur.getColumnIndex(CallLog.Calls.PHONE_ACCOUNT_ID)
                val phoneAccountCompIdx = cur.getColumnIndex(CallLog.Calls.PHONE_ACCOUNT_COMPONENT_NAME)
                val subIdColIdx = cur.getColumnIndex("sub_id").takeIf { it != -1 }
                    ?: cur.getColumnIndex("subscription_id").takeIf { it != -1 }
                    ?: cur.getColumnIndex("sim_id").takeIf { it != -1 }
                    ?: cur.getColumnIndex("sim_slot")

                while (cur.moveToNext() && entries.size < 150) {
                    val id = if (idIdx != -1) cur.getLong(idIdx) else 0L
                    val number = if (numberIdx != -1) cur.getString(numberIdx) ?: "" else ""
                    val name = if (nameIdx != -1) {
                        cur.getString(nameIdx)?.takeIf { !com.example.telecom.PriorityContactManager.isSyntheticPriorityName(it) }
                    } else null
                    val rawType = if (typeIdx != -1) cur.getInt(typeIdx) else 1
                    val date = if (dateIdx != -1) cur.getLong(dateIdx) else System.currentTimeMillis()
                    val duration = if (durationIdx != -1) cur.getLong(durationIdx) else 0L
                    val photoUri = if (photoIdx != -1) cur.getString(photoIdx) else null

                    val phoneAccountId = if (phoneAccountIdx != -1) cur.getString(phoneAccountIdx) else null
                    val phoneAccountComp = if (phoneAccountCompIdx != -1) cur.getString(phoneAccountCompIdx) else null
                    val subIdFromCol = if (subIdColIdx != null && subIdColIdx != -1) cur.getInt(subIdColIdx) else -1

                    var matchedSimSlot = if (subIdFromCol >= 0) subIdFromCol else 0
                    var matchedSimName: String? = null

                    if (!phoneAccountId.isNullOrBlank()) {
                        val simMatch = availableSims.find { sim ->
                            val handleId = sim.phoneAccountHandle?.id
                            handleId == phoneAccountId ||
                                sim.subscriptionId.toString() == phoneAccountId ||
                                (handleId != null && handleId.contains(phoneAccountId)) ||
                                (sim.phoneNumber != null && phoneAccountId.contains(sim.phoneNumber))
                        }
                        if (simMatch != null) {
                            matchedSimSlot = simMatch.subscriptionId
                            matchedSimName = simMatch.carrierName
                        } else {
                            try {
                                if (phoneAccountComp != null && telecomManager != null) {
                                    val component = android.content.ComponentName.unflattenFromString(phoneAccountComp)
                                    if (component != null) {
                                        val handle = android.telecom.PhoneAccountHandle(component, phoneAccountId)
                                        val label = telecomManager.getPhoneAccount(handle)?.label?.toString()
                                        if (!label.isNullOrBlank()) {
                                            matchedSimName = label
                                        }
                                    }
                                }
                            } catch (_: Exception) {}

                            val parsedInt = phoneAccountId.toIntOrNull()
                            if (parsedInt != null) {
                                matchedSimSlot = parsedInt
                                val bySlot = availableSims.getOrNull(parsedInt) ?: availableSims.find { it.subscriptionId == parsedInt }
                                if (bySlot != null && bySlot.carrierName.isNotBlank()) {
                                    matchedSimName = bySlot.carrierName
                                }
                            }
                        }
                    } else if (subIdFromCol >= 0) {
                        val bySub = availableSims.find { it.subscriptionId == subIdFromCol } ?: availableSims.getOrNull(subIdFromCol)
                        if (bySub != null && bySub.carrierName.isNotBlank()) {
                            matchedSimName = bySub.carrierName
                            matchedSimSlot = bySub.subscriptionId
                        }
                    }

                    if (matchedSimName.isNullOrBlank()) {
                        matchedSimName = availableSims.getOrNull(matchedSimSlot)?.carrierName
                            ?: availableSims.find { it.subscriptionId == matchedSimSlot }?.carrierName
                            ?: detectedCarrierFallback
                            ?: "SIM ${matchedSimSlot + 1}"
                    }

                    entries.add(
                        CallLogEntry(
                            id = id,
                            number = number,
                            cachedName = name,
                            type = CallType.fromCallLogType(rawType),
                            date = date,
                            durationSeconds = duration,
                            photoUri = photoUri,
                            simSlot = matchedSimSlot,
                            simName = matchedSimName,
                            phoneAccountId = phoneAccountId
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("CallLogRepository", "Failed to query call logs", e)
            return@withContext cachedCallLogs
        }

        cachedCallLogs = entries
        return@withContext entries
    }

    suspend fun insertCallLog(
        number: String,
        name: String?,
        durationSeconds: Long,
        type: CallType = CallType.OUTGOING,
        simSlot: Int = 0,
        simName: String? = null
    ): Boolean = withContext(Dispatchers.IO) {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.WRITE_CALL_LOG
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasPermission) return@withContext false

        try {
            val values = android.content.ContentValues().apply {
                put(CallLog.Calls.NUMBER, number)
                put(CallLog.Calls.CACHED_NAME, name)
                put(CallLog.Calls.TYPE, type.toCallLogType())
                put(CallLog.Calls.DATE, System.currentTimeMillis())
                put(CallLog.Calls.DURATION, durationSeconds)
                put(CallLog.Calls.NEW, 1)

                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                    val available = com.example.telecom.TelecomHelper.getAvailableSimCards(context)
                    val sim = available.find { it.subscriptionId == simSlot } ?: available.getOrNull(simSlot) ?: available.firstOrNull()
                    if (sim?.phoneAccountHandle != null) {
                        put(CallLog.Calls.PHONE_ACCOUNT_ID, sim.phoneAccountHandle.id)
                        put(CallLog.Calls.PHONE_ACCOUNT_COMPONENT_NAME, sim.phoneAccountHandle.componentName.flattenToString())
                    }
                }
            }
            contentResolver.insert(CallLog.Calls.CONTENT_URI, values) != null
        } catch (e: Exception) {
            Log.e("CallLogRepository", "Failed to insert call log", e)
            false
        }
    }

    suspend fun deleteCallLog(id: Long): Boolean = withContext(Dispatchers.IO) {
        cachedCallLogs = cachedCallLogs.filter { it.id != id }
        saveCallLogsSnapshot(cachedCallLogs)
        try {
            val rows = contentResolver.delete(
                CallLog.Calls.CONTENT_URI,
                "${CallLog.Calls._ID} = ?",
                arrayOf(id.toString())
            )
            rows > 0
        } catch (e: Exception) {
            Log.e("CallLogRepository", "Failed to delete call log $id", e)
            false
        }
    }

    suspend fun clearMissedCallLogs(): Boolean = withContext(Dispatchers.IO) {
        cachedCallLogs = cachedCallLogs.filter {
            it.type != CallType.MISSED && it.type != CallType.REJECTED
        }
        saveCallLogsSnapshot(cachedCallLogs)
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.WRITE_CALL_LOG
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasPermission) return@withContext false

        try {
            val rows = contentResolver.delete(
                CallLog.Calls.CONTENT_URI,
                "${CallLog.Calls.TYPE} IN (?, ?)",
                arrayOf(
                    CallLog.Calls.MISSED_TYPE.toString(),
                    CallLog.Calls.REJECTED_TYPE.toString()
                )
            )
            rows >= 0
        } catch (e: Exception) {
            Log.e("CallLogRepository", "Failed to clear missed call logs", e)
            false
        }
    }

    suspend fun markMissedCallsAsRead() = withContext(Dispatchers.IO) {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.WRITE_CALL_LOG
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasPermission) return@withContext

        try {
            val values = android.content.ContentValues().apply {
                put(CallLog.Calls.NEW, 0)
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                    put(CallLog.Calls.IS_READ, 1)
                }
            }
            contentResolver.update(
                CallLog.Calls.CONTENT_URI,
                values,
                "${CallLog.Calls.TYPE} = ? AND ${CallLog.Calls.NEW} = 1",
                arrayOf(CallLog.Calls.MISSED_TYPE.toString())
            )
        } catch (e: Exception) {
            Log.w("CallLogRepository", "Failed to mark missed calls as read: ${e.message}")
        }
    }

    suspend fun clearCallLogs(): Boolean = withContext(Dispatchers.IO) {
        cachedCallLogs = emptyList()
        saveCallLogsSnapshot(emptyList())
        try {
            val rows = contentResolver.delete(CallLog.Calls.CONTENT_URI, null, null)
            rows >= 0
        } catch (e: Exception) {
            Log.e("CallLogRepository", "Failed to clear call logs", e)
            false
        }
    }
}

