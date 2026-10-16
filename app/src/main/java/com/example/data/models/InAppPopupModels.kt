package com.example.data.models

import java.util.UUID

data class IncomingSmsPopup(
    val id: String = UUID.randomUUID().toString(),
    val threadId: Long,
    val senderName: String,
    val senderNumber: String,
    val body: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class IncomingCallPopup(
    val id: String = UUID.randomUUID().toString(),
    val callerName: String?,
    val callerNumber: String,
    val timestamp: Long = System.currentTimeMillis()
)
