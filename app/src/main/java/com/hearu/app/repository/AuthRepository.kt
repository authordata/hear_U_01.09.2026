package com.hearu.app.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

sealed class AuthResult {
    data class Success(val user: FirebaseUser) : AuthResult()
    data class Failure(val exception: Throwable) : AuthResult()
}

@Singleton
class AuthRepository @Inject constructor(
    private val auth: FirebaseAuth
) {
    val currentUser: FirebaseUser?
        get() = auth.currentUser

    suspend fun signInWithEmail(email: String, pass: String): AuthResult {
        return try {
            val result = auth.signInWithEmailAndPassword(email, pass).await()
            val user = result.user ?: throw Exception("Authentication returned empty user")
            AuthResult.Success(user)
        } catch (e: Exception) {
            AuthResult.Failure(e)
        }
    }

    suspend fun signUpWithEmail(email: String, pass: String): AuthResult {
        return try {
            val result = auth.createUserWithEmailAndPassword(email, pass).await()
            val user = result.user ?: throw Exception("User registration failed")
            AuthResult.Success(user)
        } catch (e: Exception) {
            AuthResult.Failure(e)
        }
    }

    fun signOut() {
        auth.signOut()
    }
}
