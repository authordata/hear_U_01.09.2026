package com.hearu.app.model

data class ChatSession(
    val sessionId: String = "",
    val seekerId: String = "",
    val giverId: String = "",
    val status: String = "active",
    val createdAt: Long = System.currentTimeMillis(),
    val identityRevealed: Boolean = false
)
