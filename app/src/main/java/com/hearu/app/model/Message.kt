package com.hearu.app.model

data class Message(
    val id: String = "",
    val senderId: String = "",
    val text: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val isSystemMessage: Boolean = false
)
