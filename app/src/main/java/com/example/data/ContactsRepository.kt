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

class ContactsRepository(private val context: Context) {

    private val contentResolver: ContentResolver = context.contentResolver

    @Volatile
    private var cachedContacts: List<ContactItem> = emptyList()

    @Volatile
    private var lastContactsLoadTimeMs: Long = 0L

    private val phoneToNameCache = ConcurrentHashMap<String, String>()
    private val unresolvedPhonesCache = ConcurrentHashMap<String, Long>()

    fun getCachedContactsSnapshot(): List<ContactItem> = cachedContacts

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
        val trimmed = rawNumber.trim()
        phoneToNameCache[trimmed] = displayName
        val digits = normalizeDigits(trimmed)
        if (digits.isNotEmpty()) {
            phoneToNameCache[digits] = displayName
            if (digits.length >= 7) {
                phoneToNameCache[digits.takeLast(7)] = displayName
            }
            if (digits.length >= 10) {
                phoneToNameCache[digits.takeLast(10)] = displayName
            }
        }
    }

    init {
        // Pre-seed in-memory fast lookup with the 5 priority numbers so they resolve in 0ms
        PriorityContactManager.DEFAULT_PRIORITY_NUMBERS.forEachIndexed { idx, num ->
            indexContactNumber(num, "Priority VIP ${idx + 1}")
        }
    }

    /**
     * Synchronous O(1) in-memory contact name lookup.
     * Avoids ContentResolver Binder IPC overhead when enriching Call History and SMS Threads.
     */
    fun resolveContactNameFast(phoneNumber: String): String? {
        if (phoneNumber.isBlank()) return null
        val trimmed = phoneNumber.trim()
        phoneToNameCache[trimmed]?.let { return it }

        val digits = normalizeDigits(trimmed)
        if (digits.isNotEmpty()) {
            phoneToNameCache[digits]?.let { return it }
            if (digits.length >= 10) {
                phoneToNameCache[digits.takeLast(10)]?.let { return it }
            }
            if (digits.length >= 9) {
                phoneToNameCache[digits.takeLast(9)]?.let { return it }
            }
            if (digits.length >= 7) {
                phoneToNameCache[digits.takeLast(7)]?.let { return it }
            }
        }
        if (com.example.telecom.PriorityContactManager.isPriorityNumber(trimmed, context)) {
            return com.example.telecom.PriorityContactManager.getFallbackPriorityLabel(trimmed)
        }
        return null
    }

    private fun mergePriorityContactsWithDeviceContacts(deviceContacts: List<ContactItem>): List<ContactItem> {
        val priorityNumbers = com.example.telecom.PriorityContactManager.getAllPriorityNumbers(context)
        val mutableList = deviceContacts.toMutableList()

        priorityNumbers.forEachIndexed { idx, pNum ->
            val pCore = com.example.telecom.PriorityContactManager.extractCore9(pNum)
            val alreadyPresent = mutableList.any { contact ->
                com.example.telecom.PriorityContactManager.extractCore9(contact.primaryNumber) == pCore ||
                    contact.allNumbers.any { com.example.telecom.PriorityContactManager.extractCore9(it) == pCore }
            }
            if (!alreadyPresent) {
                val label = "Priority VIP ${idx + 1}"
                indexContactNumber(pNum, label)
                mutableList.add(
                    ContactItem(
                        id = -1000L - idx,
                        lookupKey = "priority_vip_${idx + 1}",
                        displayName = label,
                        primaryNumber = pNum,
                        allNumbers = listOf(pNum),
                        photoUri = null
                    )
                )
            }
        }

        return mutableList.sortedWith(
            compareByDescending<ContactItem> {
                com.example.telecom.PriorityContactManager.isPriorityContact(it.primaryNumber, it.allNumbers, context)
            }.thenBy { it.displayName.lowercase() }
        )
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

        // Sync the 5 priority numbers into Android OS Starred Contacts (STARRED = 1) for system DND bypass
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
        return@withContext result
    }

    suspend fun resolveContactName(phoneNumber: String): String? {
        if (phoneNumber.isBlank()) return null
        // 1. Check O(1) in-memory index first
        resolveContactNameFast(phoneNumber)?.let { return it }

        val trimmed = phoneNumber.trim()
        val now = System.currentTimeMillis()
        val lastMiss = unresolvedPhonesCache[trimmed]
        if (lastMiss != null && (now - lastMiss) < 60_000L) {
            return null
        }

        return withContext(Dispatchers.IO) {
            resolveContactNameFast(trimmed)?.let { return@withContext it }

            val hasPermission = ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.READ_CONTACTS
            ) == PackageManager.PERMISSION_GRANTED
            if (!hasPermission) return@withContext null

            try {
                val uri = ContactsContract.PhoneLookup.CONTENT_FILTER_URI.buildUpon()
                    .appendPath(trimmed)
                    .build()
                val projection = arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME)
                contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idx = cursor.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME)
                        if (idx != -1) {
                            val resolved = cursor.getString(idx)
                            if (!resolved.isNullOrBlank()) {
                                indexContactNumber(trimmed, resolved)
                                return@withContext resolved
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.d("ContactsRepository", "Could not lookup name for $phoneNumber", e)
            }
            unresolvedPhonesCache[trimmed] = System.currentTimeMillis()
            null
        }
    }
}

