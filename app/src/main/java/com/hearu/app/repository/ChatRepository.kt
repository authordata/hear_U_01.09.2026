package com.hearu.app.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.hearu.app.model.ChatSession
import com.hearu.app.model.Message
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatRepository @Inject constructor(
    private val firestore: FirebaseFirestore
) {
    fun getMessages(sessionId: String): Flow<List<Message>> = callbackFlow {
        val listener = firestore.collection("sessions")
            .document(sessionId)
            .collection("messages")
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val messages = snapshot?.toObjects(Message::class.java) ?: emptyList()
                trySend(messages)
            }
        awaitClose { listener.remove() }
    }

    suspend fun sendMessage(sessionId: String, message: Message) {
        val docRef = firestore.collection("sessions").document(sessionId).collection("messages").document()
        val messageWithId = message.copy(id = docRef.id)
        docRef.set(messageWithId).await()
    }

    suspend fun updateSessionStatus(sessionId: String, status: String) {
        firestore.collection("sessions").document(sessionId)
            .update("status", status).await()
    }
}
