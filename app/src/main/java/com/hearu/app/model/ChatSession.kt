package com.hearu.app.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import java.util.Date

data class ChatSession(
    @DocumentId val sessionId: String = "",
    val seekerId: String = "",
    val giverId: String = "",
    val status: String = "active",
    val createdAt: Any? = System.currentTimeMillis(),
    val identityRevealed: Boolean = false,
    val seekerConsent: Boolean = false,
    val giverConsent: Boolean = false
) {
    fun createdAtMillis(): Long = when (createdAt) {
        is Timestamp -> createdAt.toDate().time
        is Long -> createdAt
        is Number -> createdAt.toLong()
        is Date -> createdAt.time
        else -> 0L
    }
}
