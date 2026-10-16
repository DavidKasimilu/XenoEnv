package com.example.data

import android.content.Context
import android.util.Log
import com.example.data.models.SavedPassword
import org.json.JSONArray
import org.json.JSONObject

class PasswordRepository(context: Context) {

    private val prefs = context.getSharedPreferences("xeno_passwords_prefs", Context.MODE_PRIVATE)

    fun getSavedPasswords(): List<SavedPassword> {
        val jsonStr = prefs.getString("saved_passwords_list", null) ?: return emptyList()
        val list = mutableListOf<SavedPassword>()
        try {
            val jsonArray = JSONArray(jsonStr)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(
                    SavedPassword(
                        id = obj.optLong("id", System.currentTimeMillis()),
                        domain = obj.optString("domain", ""),
                        title = obj.optString("title", ""),
                        username = obj.optString("username", ""),
                        password = obj.optString("password", ""),
                        dateAdded = obj.optLong("dateAdded", System.currentTimeMillis())
                    )
                )
            }
        } catch (e: Exception) {
            Log.e("PasswordRepository", "Error parsing saved passwords", e)
        }
        return list.sortedByDescending { it.dateAdded }
    }

    fun getPasswordsForDomain(domainStr: String): List<SavedPassword> {
        val cleanDomain = domainStr.lowercase()
            .replace("https://", "")
            .replace("http://", "")
            .replace("www.", "")
            .split("/").firstOrNull() ?: ""

        if (cleanDomain.isBlank()) return emptyList()

        return getSavedPasswords().filter {
            val d = it.domain.lowercase()
                .replace("https://", "")
                .replace("http://", "")
                .replace("www.", "")
                .split("/").firstOrNull() ?: ""
            d.contains(cleanDomain) || cleanDomain.contains(d)
        }
    }

    fun savePassword(savedPassword: SavedPassword) {
        val current = getSavedPasswords().toMutableList()
        // Replace existing if same domain & username
        current.removeAll {
            it.domain.equals(savedPassword.domain, ignoreCase = true) &&
                    it.username.equals(savedPassword.username, ignoreCase = true)
        }
        current.add(0, savedPassword)
        saveList(current)
    }

    fun deletePassword(id: Long) {
        val current = getSavedPasswords().toMutableList()
        current.removeAll { it.id == id }
        saveList(current)
    }

    fun clearAllPasswords() {
        prefs.edit().remove("saved_passwords_list").apply()
    }

    private fun saveList(list: List<SavedPassword>) {
        try {
            val jsonArray = JSONArray()
            list.forEach { p ->
                val obj = JSONObject().apply {
                    put("id", p.id)
                    put("domain", p.domain)
                    put("title", p.title)
                    put("username", p.username)
                    put("password", p.password)
                    put("dateAdded", p.dateAdded)
                }
                jsonArray.put(obj)
            }
            prefs.edit().putString("saved_passwords_list", jsonArray.toString()).apply()
        } catch (e: Exception) {
            Log.e("PasswordRepository", "Error saving passwords list", e)
        }
    }
}
