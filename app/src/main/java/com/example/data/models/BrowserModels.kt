package com.example.data.models

data class WebTab(
    val id: String,
    val title: String = "New Tab",
    val url: String = "https://www.google.com",
    val isLoading: Boolean = false,
    val progress: Int = 0,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false
)

data class BrowserHistoryItem(
    val id: Long = System.currentTimeMillis(),
    val title: String,
    val url: String,
    val timestamp: Long = System.currentTimeMillis()
)
