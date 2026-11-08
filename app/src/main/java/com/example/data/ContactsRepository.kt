package com.example.data

import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.data.models.ContactItem
import com.example.telecom.PriorityContactManager
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

import org.json.JSONArray
import org.json.JSONObject

class ContactsRepository(private val context: Context) {

    private val contentResolver: ContentResolver = context.contentResolver
    private val fastCachePrefs = context.getSharedPreferences("xeno_fast_contacts_cache", Context.MODE_PRIVATE)

    @Volatile
    private var cachedContacts: List<ContactItem> = emptyList()

    @Volatile
    private var lastContactsLoadTimeMs: Long = 0L

    private val phoneToNameCache = ConcurrentHashMap<String, String>()
    private val unresolvedPhonesCache = ConcurrentHashMap<String, Long>()

    init {
        try {
            getCachedContactsSnapshot()
        } catch (_: Exception) {}
    }

    companion object {
        private val globalPhoneToNameCache = ConcurrentHashMap<String, String>()

        @Volatile
        private var instance: ContactsRepository? = null

        fun getInstance(context: Context): ContactsRepository {
            return instance ?: synchronized(this) {
                instance ?: ContactsRepository(context.applicationContext).also { instance = it }
            }
        }

        fun indexFast(rawNumber: String, displayName: String) {
            if (rawNumber.isBlank() || displayName.isBlank()) return
            if (PriorityContactManager.isSyntheticPriorityName(displayName)) return
            val trimmed = rawNumber.trim()
            globalPhoneToNameCache[trimmed] = displayName
            val digits = trimmed.filter { it.isDigit() }
            if (digits.isNotEmpty()) {
                globalPhoneToNameCache[digits] = displayName
                if (digits.length >= 7) globalPhoneToNameCache[digits.takeLast(7)] = displayName
                if (digits.length >= 9) globalPhoneToNameCache[digits.takeLast(9)] = displayName
                if (digits.length >= 10) globalPhoneToNameCache[digits.takeLast(10)] = displayName
            }
        }

        fun resolveFromCache(phoneNumber: String): String? {
            if (phoneNumber.isBlank()) return null
            val trimmed = phoneNumber.trim()
            globalPhoneToNameCache[trimmed]?.takeIf { !PriorityContactManager.isSyntheticPriorityName(it) }?.let { return it }
            val digits = trimmed.filter { it.isDigit() }
            if (digits.isNotEmpty()) {
                globalPhoneToNameCache[digits]?.takeIf { !PriorityContactManager.isSyntheticPriorityName(it) }?.let { return it }
                if (digits.length >= 10) {
                    globalPhoneToNameCache[digits.takeLast(10)]?.takeIf { !PriorityContactManager.isSyntheticPriorityName(it) }?.let { return it }
                }
                if (digits.length >= 9) {
                    globalPhoneToNameCache[digits.takeLast(9)]?.takeIf { !PriorityContactManager.isSyntheticPriorityName(it) }?.let { return it }
                }
                if (digits.length >= 7) {
                    globalPhoneToNameCache[digits.takeLast(7)]?.takeIf { !PriorityContactManager.isSyntheticPriorityName(it) }?.let { return it }
                }
            }
            return null
        }
    }

    /**
     * Returns an instant snapshot of Contacts from memory or lightweight disk cache
     * so Contacts and Call History names render immediately on Frame 1 with 0ms delay.
     */
    fun getCachedContactsSnapshot(): List<ContactItem> {
        if (cachedContacts.isNotEmpty()) return cachedContacts
        return try {
            val raw = fastCachePrefs.getString("contacts_snapshot", null) ?: return emptyList()
            val arr = JSONArray(raw)
            val list = ArrayList<ContactItem>(arr.length())
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val id = obj.optLong("id", 0L)
                val lookupKey = obj.optString("lookupKey", "")
                val name = obj.optString("displayName", "Unknown")
                val number = obj.optString("primaryNumber", "")
                val photoUri = obj.optString("photoUri", "").takeIf { it.isNotBlank() }

                val item = ContactItem(
                    id = id,
                    lookupKey = lookupKey,
                    displayName = name,
                    primaryNumber = number,
                    allNumbers = if (number.isNotBlank()) listOf(number) else emptyList(),
                    photoUri = photoUri
                )
                list.add(item)
                if (number.isNotBlank() && name.isNotBlank()) {
                    indexContactNumber(number, name)
                }
            }
            cachedContacts = list
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveContactsSnapshot(contacts: List<ContactItem>) {
        cachedContacts = contacts
        try {
            val arr = JSONArray()
            val limit = contacts.size.coerceAtMost(250)
            for (i in 0 until limit) {
                val c = contacts[i]
                val obj = JSONObject().apply {
                    put("id", c.id)
                    put("lookupKey", c.lookupKey)
                    put("displayName", c.displayName)
                    put("primaryNumber", c.primaryNumber)
                    put("photoUri", c.photoUri ?: "")
                }
                arr.put(obj)
            }
            fastCachePrefs.edit().putString("contacts_snapshot", arr.toString()).apply()
        } catch (_: Exception) {}
    }

    private fun normalizeDigits(number: String): String {
        val sb = StringBuilder(number.length)
        for (i in 0 until number.length) {
            val c = number[i]
            if (c in '0'..'9') sb.append(c)
        }
        return sb.toString()
    }

    private fun indexContactNumber(rawNumber: String, displayName: String) {
        if (rawNumber.isBlank() || displayName.isBlank()) return
        if (PriorityContactManager.isSyntheticPriorityName(displayName)) return
        val trimmed = rawNumber.trim()
        phoneToNameCache[trimmed] = displayName
        val digits = normalizeDigits(trimmed)
        if (digits.isNotEmpty()) {
            phoneToNameCache[digits] = displayName
            if (digits.length >= 7) {
                phoneToNameCache[digits.takeLast(7)] = displayName
            }
            if (digits.length >= 9) {
                phoneToNameCache[digits.takeLast(9)] = displayName
            }
            if (digits.length >= 10) {
                phoneToNameCache[digits.takeLast(10)] = displayName
            }
        }
        indexFast(rawNumber, displayName)
    }

    /**
     * Synchronous O(1) in-memory contact name lookup.
     * Avoids ContentResolver Binder IPC overhead when enriching Call History and SMS Threads.
     * Always returns the real contact name saved in the user's phone (never synthetic VIP labels).
     */
    fun resolveContactNameFast(phoneNumber: String): String? {
        if (phoneNumber.isBlank()) return null
        resolveFromCache(phoneNumber)?.let { return it }
        val trimmed = phoneNumber.trim()
        phoneToNameCache[trimmed]?.takeIf { !PriorityContactManager.isSyntheticPriorityName(it) }?.let { return it }

        val digits = normalizeDigits(trimmed)
        if (digits.isNotEmpty()) {
            phoneToNameCache[digits]?.takeIf { !PriorityContactManager.isSyntheticPriorityName(it) }?.let { return it }
            if (digits.length >= 10) {
                phoneToNameCache[digits.takeLast(10)]?.takeIf { !PriorityContactManager.isSyntheticPriorityName(it) }?.let { return it }
            }
            if (digits.length >= 9) {
                phoneToNameCache[digits.takeLast(9)]?.takeIf { !PriorityContactManager.isSyntheticPriorityName(it) }?.let { return it }
            }
            if (digits.length >= 7) {
                phoneToNameCache[digits.takeLast(7)]?.takeIf { !PriorityContactManager.isSyntheticPriorityName(it) }?.let { return it }
            }
        }
        return null
    }

    private fun mergePriorityContactsWithDeviceContacts(deviceContacts: List<ContactItem>): List<ContactItem> {
        return deviceContacts
            .filterNot { PriorityContactManager.isSyntheticPriorityName(it.displayName) }
            .sortedBy { it.displayName.lowercase() }
    }

    suspend fun getContacts(forceRefresh: Boolean = false): List<ContactItem> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (!forceRefresh && cachedContacts.isNotEmpty() && (now - lastContactsLoadTimeMs) < 15_000L) {
            return@withContext cachedContacts
        }

        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasPermission) {
            val seededOnly = mergePriorityContactsWithDeviceContacts(emptyList())
            cachedContacts = seededOnly
            return@withContext seededOnly
        }

        // Sync the priority numbers into Android OS Starred Contacts (STARRED = 1) for system DND bypass
        com.example.telecom.PriorityContactManager.syncPriorityContactsToOs(context)

        val contactsMap = mutableMapOf<Long, ContactItem>()
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            ContactsContract.CommonDataKinds.Phone.LOOKUP_KEY,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.PHOTO_THUMBNAIL_URI
        )

        try {
            contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                projection,
                null,
                null,
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} COLLATE NOCASE ASC"
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
                val lookupIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.LOOKUP_KEY)
                val nameIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numberIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                val photoIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.PHOTO_THUMBNAIL_URI)

                while (cursor.moveToNext()) {
                    val id = if (idIdx != -1) cursor.getLong(idIdx) else 0L
                    val lookupKey = if (lookupIdx != -1) cursor.getString(lookupIdx) ?: "" else ""
                    val name = if (nameIdx != -1) cursor.getString(nameIdx) ?: "Unknown" else "Unknown"
                    val number = if (numberIdx != -1) cursor.getString(numberIdx) ?: "" else ""
                    val photoUri = if (photoIdx != -1) cursor.getString(photoIdx) else null

                    if (PriorityContactManager.isSyntheticPriorityName(name)) {
                        continue
                    }

                    if (number.isNotBlank() && name.isNotBlank()) {
                        indexContactNumber(number, name)
                    }

                    val existing = contactsMap[id]
                    if (existing == null) {
                        contactsMap[id] = ContactItem(
                            id = id,
                            lookupKey = lookupKey,
                            displayName = name,
                            primaryNumber = number,
                            allNumbers = if (number.isNotBlank()) listOf(number) else emptyList(),
                            photoUri = photoUri
                        )
                    } else {
                        if (number.isNotBlank() && !existing.allNumbers.contains(number)) {
                            contactsMap[id] = existing.copy(
                                allNumbers = existing.allNumbers + number
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("ContactsRepository", "Failed to query contacts", e)
            return@withContext mergePriorityContactsWithDeviceContacts(cachedContacts)
        }

        val result = mergePriorityContactsWithDeviceContacts(contactsMap.values.toList())
        cachedContacts = result
        lastContactsLoadTimeMs = System.currentTimeMillis()
        saveContactsSnapshot(result)
        return@withContext result
    }

    suspend fun resolveContactName(phoneNumber: String): String? {
        if (phoneNumber.isBlank()) return null
        val trimmed = phoneNumber.trim()
        if (trimmed.any { it.isLetter() }) {
            return trimmed
        }
        // 1. Check O(1) in-memory index first
        resolveContactNameFast(trimmed)?.let { return it }

        val now = System.currentTimeMillis()
        val lastMiss = unresolvedPhonesCache[trimmed]
        if (lastMiss != null && (now - lastMiss) < 4_000L) {
            return null
        }

        return withContext(Dispatchers.IO) {
            resolveContactNameFast(trimmed)?.let { return@withContext it }

            val hasPermission = ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.READ_CONTACTS
            ) == PackageManager.PERMISSION_GRANTED
            if (!hasPermission) {
                Log.w("CallerNameResolve", "READ_CONTACTS not granted in resolveContactName, skipping lookup and miss-cache")
                return@withContext null
            }

            try {
                val uri = ContactsContract.PhoneLookup.CONTENT_FILTER_URI.buildUpon()
                    .appendPath(trimmed)
                    .build()
                val projection = arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME)
                contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                    while (cursor.moveToNext()) {
                        val idx = cursor.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME)
                        if (idx != -1) {
                            val resolved = cursor.getString(idx)
                            if (!resolved.isNullOrBlank() && !PriorityContactManager.isSyntheticPriorityName(resolved)) {
                                indexContactNumber(trimmed, resolved)
                                return@withContext resolved
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.d("ContactsRepository", "Could not lookup name for $phoneNumber", e)
            }

            // Fallback to robust number lookup
            val fallback = com.example.telecom.XenoNotificationHelper.resolveContactNameQuick(context, trimmed)
            if (fallback != null) {
                indexContactNumber(trimmed, fallback)
                return@withContext fallback
            }

            unresolvedPhonesCache[trimmed] = System.currentTimeMillis()
            null
        }
    }

    fun isWhatsAppAccountPresent(context: Context, phoneNumber: String): Boolean {
        val cleanNumber = phoneNumber.filter { it.isDigit() }
        if (cleanNumber.length < 5) return false
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasPermission) return true

        return try {
            val cursor = context.contentResolver.query(
                ContactsContract.Data.CONTENT_URI,
                arrayOf(ContactsContract.Data._ID),
                "${ContactsContract.Data.MIMETYPE} IN ('vnd.android.cursor.item/vnd.com.whatsapp.voip.call', 'vnd.android.cursor.item/vnd.com.whatsapp.video.call') AND (${ContactsContract.Data.DATA1} LIKE ? OR ${ContactsContract.Data.DATA1} LIKE ?)",
                arrayOf("%$cleanNumber%", "%${cleanNumber.takeLast(7)}%"),
                null
            )
            val found = (cursor?.use { it.count } ?: 0) > 0
            found || cleanNumber.length >= 7
        } catch (_: Exception) {
            cleanNumber.length >= 7
        }
    }
}

