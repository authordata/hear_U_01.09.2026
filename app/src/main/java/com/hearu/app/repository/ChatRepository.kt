package com.hearu.app.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.hearu.app.data.local.dao.MessageDao
import com.hearu.app.data.local.entity.MessageEntity
import com.hearu.app.model.Message
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val messageDao: MessageDao
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
                
                CoroutineScope(Dispatchers.IO).launch {
                    val entities = messages.map {
                        MessageEntity(
                            id = it.id,
                            sessionId = sessionId,
                            senderId = it.senderId,
                            text = it.text,
                            timestamp = it.timestamp,
                            isSystemMessage = it.isSystemMessage
                        )
                    }
                    messageDao.insertMessages(entities)
                }

                trySend(messages)
            }
        awaitClose { listener.remove() }
    }

    suspend fun sendMessage(sessionId: String, message: Message) {
        val docRef = firestore.collection("sessions").document(sessionId).collection("messages").document()
        val messageWithId = message.copy(id = docRef.id)

        messageDao.insertMessage(
            MessageEntity(
                id = messageWithId.id,
                sessionId = sessionId,
                senderId = messageWithId.senderId,
                text = messageWithId.text,
                timestamp = messageWithId.timestamp,
                isSystemMessage = messageWithId.isSystemMessage
            )
        )

        docRef.set(messageWithId).await()
    }

    suspend fun updateSessionStatus(sessionId: String, status: String) {
        firestore.collection("sessions").document(sessionId)
            .update("status", status).await()
    }
}
