package com.example.data

import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.data.models.ContactItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ContactsRepository(private val context: Context) {

    private val contentResolver: ContentResolver = context.contentResolver

    suspend fun getContacts(): List<ContactItem> = withContext(Dispatchers.IO) {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasPermission) {
            return@withContext emptyList()
        }

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
            return@withContext emptyList()
        }

        val result = contactsMap.values.sortedBy { it.displayName.lowercase() }
        return@withContext result
    }

    suspend fun resolveContactName(phoneNumber: String): String? = withContext(Dispatchers.IO) {
        if (phoneNumber.isBlank()) return@withContext null
        try {
            val uri = ContactsContract.PhoneLookup.CONTENT_FILTER_URI.buildUpon()
                .appendPath(phoneNumber)
                .build()
            val projection = arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME)
            contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME)
                    if (idx != -1) return@withContext cursor.getString(idx)
                }
            }
        } catch (e: Exception) {
            Log.d("ContactsRepository", "Could not lookup name for $phoneNumber", e)
        }
        null
    }
}
