package com.hearu.app.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

sealed class AuthResult {
    data class Success(val user: FirebaseUser) : AuthResult()
    data class Failure(val exception: Throwable) : AuthResult()
}

@Singleton
class AuthRepository @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore
) {
    constructor(auth: FirebaseAuth) : this(auth, FirebaseFirestore.getInstance())

    val currentUser: FirebaseUser?
        get() = auth.currentUser

    suspend fun signInWithEmail(email: String, pass: String): AuthResult {
        return try {
            val trimmedEmail = email.trim()
            val result = auth.signInWithEmailAndPassword(trimmedEmail, pass).await()
            val user = result.user ?: throw Exception("Authentication returned empty user")
            ensureUserDocument(user, trimmedEmail)
            AuthResult.Success(user)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AuthResult.Failure(e)
        }
    }

    suspend fun signUpWithEmail(email: String, pass: String): AuthResult {
        return try {
            val trimmedEmail = email.trim()
            val result = auth.createUserWithEmailAndPassword(trimmedEmail, pass).await()
            val user = result.user ?: throw Exception("User registration failed")
            ensureUserDocument(user, trimmedEmail)
            AuthResult.Success(user)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AuthResult.Failure(e)
        }
    }

    suspend fun signInAnonymously(): AuthResult {
        return try {
            val result = auth.signInAnonymously().await()
            val user = result.user ?: throw Exception("Anonymous authentication returned empty user")
            ensureUserDocument(user, fallbackEmail = null)
            AuthResult.Success(user)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AuthResult.Failure(e)
        }
    }

    suspend fun sendPasswordResetEmail(email: String): Result<Unit> {
        val trimmedEmail = email.trim()
        if (trimmedEmail.isBlank()) {
            return Result.failure(IllegalArgumentException("Email address is required."))
        }
        return try {
            auth.sendPasswordResetEmail(trimmedEmail).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Deletes user Firestore profile data before deleting the Firebase Auth account,
     * because firestore.rules requires request.auth.uid == userId during deletion.
     *
     * Snapshots the user document first so if user.delete() fails (e.g. due to
     * FirebaseAuthRecentLoginRequiredException or network error), the user profile
     * (including blockedUsers) is best-effort restored.
     */
    suspend fun deleteAccount(): Result<Unit> {
        val user = auth.currentUser
            ?: return Result.failure(IllegalStateException("No authenticated user found."))

        val uid = user.uid
        val userDoc = firestore.collection("users").document(uid)
        val userDocBackup: Map<String, Any>? = try {
            val snapshot = userDoc.get()?.await()
            if (snapshot != null && snapshot.exists()) {
                snapshot.data?.toMutableMap()?.apply { remove("rating") }
            } else {
                null
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }

        return try {
            userDoc.delete().await()
            try {
                firestore.collection("publicListeners").document(uid).delete().await()
            } catch (e: CancellationException) {
                throw e
            } catch (e: FirebaseFirestoreException) {
                if (e.code != FirebaseFirestoreException.Code.NOT_FOUND &&
                    e.code != FirebaseFirestoreException.Code.PERMISSION_DENIED
                ) {
                    throw e
                }
            }
            user.delete().await()
            auth.signOut()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: FirebaseAuthRecentLoginRequiredException) {
            restoreUserDocBestEffort(userDoc, userDocBackup)
            Result.failure(
                IllegalStateException(
                    "Please sign in again to confirm account deletion.",
                    e
                )
            )
        } catch (e: Exception) {
            restoreUserDocBestEffort(userDoc, userDocBackup)
            Result.failure(e)
        }
    }

    private suspend fun restoreUserDocBestEffort(
        userDoc: com.google.firebase.firestore.DocumentReference,
        backup: Map<String, Any>?
    ) {
        if (backup == null) return
        try {
            userDoc.set(backup, SetOptions.merge())?.await()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
        }
    }

    fun signOut() {
        auth.signOut()
    }

    private suspend fun ensureUserDocument(user: FirebaseUser, fallbackEmail: String?) {
        val docRef = firestore.collection("users").document(user.uid)
        val snapshot = docRef.get().await()
        if (!snapshot.exists()) {
            val resolvedEmail = user.email?.takeIf { it.isNotBlank() }
                ?: fallbackEmail?.takeIf { it.isNotBlank() }
            val resolvedDisplayName = user.displayName?.trim()?.take(100).orEmpty()
            val payload = hashMapOf<String, Any>(
                "uid" to user.uid,
                "displayName" to resolvedDisplayName,
                "role" to "",
                "status" to "offline",
                "emotionTags" to emptyList<String>(),
                "blockedUsers" to emptyList<String>(),
                "createdAt" to System.currentTimeMillis()
            )
            if (resolvedEmail != null) {
                payload["email"] = resolvedEmail
            }
            docRef.set(payload, SetOptions.merge()).await()
        }
    }
}
