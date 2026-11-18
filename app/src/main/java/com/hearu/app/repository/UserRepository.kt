package com.hearu.app.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.SetOptions
import com.hearu.app.model.User
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserRepository @Inject constructor(
    private val firestore: FirebaseFirestore
) {
    suspend fun saveUser(user: User) {
        require(user.uid.isNotBlank()) { "User uid cannot be blank." }
        try {
            val docRef = firestore.collection("users").document(user.uid)
            val exists = docRef.get().await().exists()
            val payload = if (!exists) {
                buildCreatePayload(user)
            } else {
                buildUpdatePayload(user)
            }
            if (payload.isNotEmpty()) {
                docRef.set(payload, SetOptions.merge()).await()
            }
        } catch (e: CancellationException) {
            throw e
        }
    }

    suspend fun getUser(uid: String): User? {
        if (uid.isBlank()) return null
        return try {
            val snapshot = firestore.collection("users").document(uid).get().await()
            snapshot.toObject(User::class.java)
        } catch (e: CancellationException) {
            throw e
        }
    }

    suspend fun updateUserStatus(uid: String, status: String) {
        require(uid.isNotBlank()) { "User uid cannot be blank." }
        require(status in ALLOWED_STATUSES) { "Invalid status: $status" }
        updateFieldWithNotFoundFallback(uid, "status", status)
    }

    suspend fun updateDisplayName(uid: String, displayName: String) {
        require(uid.isNotBlank()) { "User uid cannot be blank." }
        val trimmed = displayName.trim()
        require(trimmed.length in 1..100) { "Display name must be between 1 and 100 characters." }
        updateFieldWithNotFoundFallback(uid, "displayName", trimmed)
    }

    suspend fun updateRole(uid: String, role: String) {
        require(uid.isNotBlank()) { "User uid cannot be blank." }
        require(role in ALLOWED_UPDATE_ROLES) { "Invalid role: $role" }
        updateFieldWithNotFoundFallback(uid, "role", role)
    }

    suspend fun updateAgeVerified(uid: String, verified: Boolean) {
        require(uid.isNotBlank()) { "User uid cannot be blank." }
        updateFieldWithNotFoundFallback(uid, "ageVerified", verified)
    }

    suspend fun updateFcmToken(uid: String, token: String) {
        require(uid.isNotBlank()) { "User uid cannot be blank." }
        require(token.length <= 512) { "FCM token must be at most 512 characters." }
        val now = System.currentTimeMillis()
        val fields = mapOf<String, Any>(
            "fcmToken" to token,
            "tokenUpdatedAt" to now
        )
        try {
            firestore.collection("users").document(uid)
                .update(fields)
                .await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: FirebaseFirestoreException) {
            if (e.code == FirebaseFirestoreException.Code.NOT_FOUND) {
                firestore.collection("users").document(uid)
                    .set(
                        fields + mapOf("uid" to uid, "createdAt" to now),
                        SetOptions.merge()
                    )
                    .await()
            } else {
                throw e
            }
        }
    }

    private suspend fun updateFieldWithNotFoundFallback(uid: String, field: String, value: Any) {
        try {
            firestore.collection("users").document(uid)
                .update(field, value)
                .await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: FirebaseFirestoreException) {
            if (e.code == FirebaseFirestoreException.Code.NOT_FOUND) {
                firestore.collection("users").document(uid)
                    .set(
                        mapOf(
                            "uid" to uid,
                            field to value,
                            "createdAt" to System.currentTimeMillis()
                        ),
                        SetOptions.merge()
                    )
                    .await()
            } else {
                throw e
            }
        }
    }

    private fun buildCreatePayload(user: User): Map<String, Any?> {
        val map = linkedMapOf<String, Any?>(
            "uid" to user.uid,
            "displayName" to user.displayName.trim().take(100),
            "role" to if (user.role in ALLOWED_CREATE_ROLES) user.role else "",
            "status" to if (user.status in ALLOWED_STATUSES) user.status else "offline",
            "emotionTags" to user.emotionTags.take(10),
            "blockedUsers" to user.blockedUsers.take(500),
            "createdAt" to user.createdAt
        )
        if (user.email.isNotBlank()) {
            map["email"] = user.email.trim()
        }
        if (user.photoUrl != null) {
            map["photoUrl"] = user.photoUrl.take(2048)
        }
        if (user.fcmToken != null) {
            map["fcmToken"] = user.fcmToken.take(512)
            map["tokenUpdatedAt"] = user.tokenUpdatedAt
        }
        return map
    }

    private fun buildUpdatePayload(user: User): Map<String, Any?> {
        val map = linkedMapOf<String, Any?>(
            "emotionTags" to user.emotionTags.take(10),
            "blockedUsers" to user.blockedUsers.take(500),
            "status" to if (user.status in ALLOWED_STATUSES) user.status else "offline",
            "photoUrl" to user.photoUrl?.take(2048)
        )
        val trimmedName = user.displayName.trim()
        if (trimmedName.isNotEmpty()) {
            map["displayName"] = trimmedName.take(100)
        }
        if (user.role in ALLOWED_UPDATE_ROLES) {
            map["role"] = user.role
        }
        if (user.fcmToken != null) {
            map["fcmToken"] = user.fcmToken.take(512)
            map["tokenUpdatedAt"] = user.tokenUpdatedAt
        }
        return map
    }

    companion object {
        private val ALLOWED_STATUSES = setOf("online", "offline")
        private val ALLOWED_CREATE_ROLES = setOf("", "seeker", "giver", "both")
        private val ALLOWED_UPDATE_ROLES = setOf("seeker", "giver", "both")
    }
}
