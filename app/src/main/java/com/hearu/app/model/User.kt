package com.hearu.app.model

data class User(
    val uid: String = "",
    val displayName: String = "",
    val email: String = "",
    val role: String = "",
    val status: String = "offline",
    val emotionTags: List<String> = emptyList(),
    val photoUrl: String? = null,
    val fcmToken: String? = null,
    val tokenUpdatedAt: Long = 0L,
    val blockedUsers: List<String> = emptyList(),
    val createdAt: Long = System.currentTimeMillis()
)
