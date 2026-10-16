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

class CallLogRepository(private val context: Context) {

    private val contentResolver: ContentResolver = context.contentResolver

    suspend fun getCallLogs(): List<CallLogEntry> = withContext(Dispatchers.IO) {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.READ_CALL_LOG
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasPermission) {
            return@withContext emptyList()
        }

        val entries = mutableListOf<CallLogEntry>()
        val projection = arrayOf(
            CallLog.Calls._ID,
            CallLog.Calls.NUMBER,
            CallLog.Calls.CACHED_NAME,
            CallLog.Calls.TYPE,
            CallLog.Calls.DATE,
            CallLog.Calls.DURATION,
            CallLog.Calls.CACHED_PHOTO_URI
        )

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

                while (cur.moveToNext() && entries.size < 150) {
                    val id = if (idIdx != -1) cur.getLong(idIdx) else 0L
                    val number = if (numberIdx != -1) cur.getString(numberIdx) ?: "" else ""
                    val name = if (nameIdx != -1) cur.getString(nameIdx) else null
                    val rawType = if (typeIdx != -1) cur.getInt(typeIdx) else 1
                    val date = if (dateIdx != -1) cur.getLong(dateIdx) else System.currentTimeMillis()
                    val duration = if (durationIdx != -1) cur.getLong(durationIdx) else 0L
                    val photoUri = if (photoIdx != -1) cur.getString(photoIdx) else null

                    entries.add(
                        CallLogEntry(
                            id = id,
                            number = number,
                            cachedName = name,
                            type = CallType.fromCallLogType(rawType),
                            date = date,
                            durationSeconds = duration,
                            photoUri = photoUri
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("CallLogRepository", "Failed to query call logs", e)
            return@withContext emptyList()
        }

        return@withContext entries
    }

    suspend fun insertCallLog(
        number: String,
        name: String?,
        durationSeconds: Long,
        type: CallType = CallType.OUTGOING
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
            }
            contentResolver.insert(CallLog.Calls.CONTENT_URI, values) != null
        } catch (e: Exception) {
            Log.e("CallLogRepository", "Failed to insert call log", e)
            false
        }
    }

    suspend fun deleteCallLog(id: Long): Boolean = withContext(Dispatchers.IO) {
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

    suspend fun clearCallLogs(): Boolean = withContext(Dispatchers.IO) {
        try {
            val rows = contentResolver.delete(CallLog.Calls.CONTENT_URI, null, null)
            rows >= 0
        } catch (e: Exception) {
            Log.e("CallLogRepository", "Failed to clear call logs", e)
            false
        }
    }
}
