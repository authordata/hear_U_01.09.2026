package com.hearu.app.model

data class User(
    val uid: String = "",
    val displayName: String = "",
    val email: String = "",
    val role: String = "",
    val status: String = "offline",
    val emotionTags: List<String> = emptyList(),
    val rating: Double = 5.0,
    val createdAt: Long = System.currentTimeMillis()
)
