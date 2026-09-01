package com.hearu.app.repository

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ModerationRepository @Inject constructor(
    private val firestore: FirebaseFirestore
) {
    suspend fun reportUser(
        reporterId: String,
        reportedUserId: String,
        sessionId: String,
        reason: String,
        description: String
    ) {
        val reportData = hashMapOf(
            "reporterId" to reporterId,
            "reportedUserId" to reportedUserId,
            "sessionId" to sessionId,
            "reason" to reason,
            "description" to description,
            "timestamp" to FieldValue.serverTimestamp(),
            "status" to "PENDING_REVIEW"
        )
        firestore.collection("reports").add(reportData).await()
    }

    suspend fun blockUser(userId: String, blockedUserId: String) {
        firestore.collection("users").document(userId)
            .update("blockedUsers", FieldValue.arrayUnion(blockedUserId))
            .await()
    }
}
