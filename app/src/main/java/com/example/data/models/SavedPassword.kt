package com.example.data.models

data class SavedPassword(
    val id: Long = System.currentTimeMillis(),
    val domain: String,
    val title: String,
    val username: String,
    val password: String,
    val dateAdded: Long = System.currentTimeMillis()
)
