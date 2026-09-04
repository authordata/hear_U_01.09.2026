package com.hearu.app.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.hearu.app.model.User
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserRepository @Inject constructor(
    private val firestore: FirebaseFirestore
) {
    suspend fun saveUser(user: User) {
        firestore.collection("users").document(user.uid).set(user).await()
    }

    suspend fun getUser(uid: String): User? {
        val snapshot = firestore.collection("users").document(uid).get().await()
        return snapshot.toObject(User::class.java)
    }

    suspend fun updateUserStatus(uid: String, status: String) {
        firestore.collection("users").document(uid).update("status", status).await()
    }

    suspend fun updateFcmToken(uid: String, token: String) {
        firestore.collection("users").document(uid).update(
            mapOf(
                "fcmToken" to token,
                "tokenUpdatedAt" to System.currentTimeMillis()
            )
        ).await()
    }
}
