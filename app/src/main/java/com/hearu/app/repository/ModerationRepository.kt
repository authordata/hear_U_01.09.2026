package com.hearu.app.repository

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CancellationException
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
        val cleanReporterId = reporterId.trim()
        val cleanReportedId = reportedUserId.trim()
        val cleanSessionId = sessionId.trim()
        val cleanReason = reason.trim()
        val cleanDescription = description.trim().take(2000)

        require(cleanReporterId.isNotBlank()) { "Reporter ID cannot be blank." }
        require(cleanReportedId.isNotBlank()) { "Reported user ID cannot be blank." }
        require(cleanReportedId != cleanReporterId) { "Users cannot report themselves." }
        require(cleanSessionId.length in 1..128) { "Session ID must be 1..128 characters." }
        require(cleanReason.length in 1..500) { "Reason must be 1..500 characters." }

        val reportData = hashMapOf(
            "reporterId" to cleanReporterId,
            "reportedUserId" to cleanReportedId,
            "sessionId" to cleanSessionId,
            "reason" to cleanReason,
            "description" to cleanDescription,
            "timestamp" to FieldValue.serverTimestamp(),
            "status" to "PENDING_REVIEW"
        )
        try {
            firestore.collection("reports").add(reportData).await()
        } catch (e: CancellationException) {
            throw e
        }
    }

    suspend fun blockUser(userId: String, blockedUserId: String) {
        require(userId.isNotBlank()) { "User ID cannot be blank." }
        require(blockedUserId.isNotBlank()) { "Blocked user ID cannot be blank." }
        require(userId != blockedUserId) { "Users cannot block themselves." }

        val docRef = firestore.collection("users").document(userId)
        try {
            docRef.update("blockedUsers", FieldValue.arrayUnion(blockedUserId)).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: FirebaseFirestoreException) {
            if (e.code == FirebaseFirestoreException.Code.NOT_FOUND) {
                docRef.set(
                    mapOf(
                        "uid" to userId,
                        "blockedUsers" to listOf(blockedUserId),
                        "createdAt" to System.currentTimeMillis()
                    ),
                    SetOptions.merge()
                ).await()
            } else {
                throw e
            }
        }
    }

    suspend fun getBlockedUsers(userId: String): List<String> {
        if (userId.isBlank()) return emptyList()
        return try {
            val snapshot = firestore.collection("users").document(userId).get().await()
            if (!snapshot.exists()) return emptyList()
            val raw = snapshot.get("blockedUsers") as? List<*> ?: return emptyList()
            raw.filterIsInstance<String>()
        } catch (e: CancellationException) {
            throw e
        }
    }
}
