package com.example.data

import android.content.Context
import android.content.SharedPreferences
import com.example.data.models.BrowserHistoryItem
import com.example.data.models.WebTab
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class BrowserRepository(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("xeno_browser_prefs", Context.MODE_PRIVATE)

    fun loadTabs(): Pair<List<WebTab>, String> {
        val tabsJson = prefs.getString("saved_tabs", null)
        val activeId = prefs.getString("active_tab_id", null)

        if (tabsJson.isNullOrBlank()) {
            val initialTab = WebTab(
                id = UUID.randomUUID().toString(),
                title = "Google",
                url = "https://www.google.com"
            )
            return listOf(initialTab) to initialTab.id
        }

        val list = mutableListOf<WebTab>()
        try {
            val array = JSONArray(tabsJson)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    WebTab(
                        id = obj.optString("id", UUID.randomUUID().toString()),
                        title = obj.optString("title", "New Tab"),
                        url = obj.optString("url", "https://www.google.com")
                    )
                )
            }
        } catch (_: Exception) {}

        if (list.isEmpty()) {
            val fallback = WebTab(
                id = UUID.randomUUID().toString(),
                title = "Google",
                url = "https://www.google.com"
            )
            return listOf(fallback) to fallback.id
        }

        val effectiveActiveId = if (list.any { it.id == activeId }) activeId!! else list.first().id
        return list to effectiveActiveId
    }

    fun saveTabs(tabs: List<WebTab>, activeTabId: String) {
        val array = JSONArray()
        for (tab in tabs) {
            val obj = JSONObject().apply {
                put("id", tab.id)
                put("title", tab.title)
                put("url", tab.url)
            }
            array.put(obj)
        }
        prefs.edit()
            .putString("saved_tabs", array.toString())
            .putString("active_tab_id", activeTabId)
            .apply()
    }

    fun loadHistory(): List<BrowserHistoryItem> {
        val historyJson = prefs.getString("browser_history", null) ?: return emptyList()
        val list = mutableListOf<BrowserHistoryItem>()
        try {
            val array = JSONArray(historyJson)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    BrowserHistoryItem(
                        id = obj.optLong("id", System.currentTimeMillis()),
                        title = obj.optString("title", "Untitled"),
                        url = obj.optString("url", ""),
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis())
                    )
                )
            }
        } catch (_: Exception) {}
        return list.sortedByDescending { it.timestamp }
    }

    fun addHistoryItem(title: String, url: String) {
        if (url.isBlank() || url.startsWith("about:") || url.startsWith("data:")) return
        val current = loadHistory().toMutableList()
        // Deduplicate recent same url
        current.removeAll { it.url == url }
        current.add(
            0,
            BrowserHistoryItem(
                title = if (title.isBlank()) url else title,
                url = url,
                timestamp = System.currentTimeMillis()
            )
        )
        // Keep max 50 history entries
        val trimmed = current.take(50)
        val array = JSONArray()
        for (item in trimmed) {
            val obj = JSONObject().apply {
                put("id", item.id)
                put("title", item.title)
                put("url", item.url)
                put("timestamp", item.timestamp)
            }
            array.put(obj)
        }
        prefs.edit().putString("browser_history", array.toString()).apply()
    }

    fun clearHistory() {
        prefs.edit().remove("browser_history").apply()
    }
}
