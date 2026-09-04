package com.hearu.app.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.hearu.app.data.local.dao.MessageDao
import com.hearu.app.data.local.entity.MessageEntity
import com.hearu.app.model.Message
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val messageDao: MessageDao
) {
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val activeListeners = ConcurrentHashMap<String, ListenerRegistration>()

    /**
     * Returns Room as Single Source of Truth.
     * Firestore listener syncs to Room in background — UI always reads from local DB.
     */
    fun getMessages(sessionId: String): Flow<List<Message>> {
        startFirestoreSync(sessionId)
        return messageDao.getMessagesForSession(sessionId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    private fun startFirestoreSync(sessionId: String) {
        if (activeListeners.containsKey(sessionId)) return

        val registration = firestore.collection("sessions")
            .document(sessionId)
            .collection("messages")
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                val entities = snapshot.documents.mapNotNull { doc ->
                    val msg = doc.toObject(Message::class.java) ?: return@mapNotNull null
                    MessageEntity(
                        id = doc.id,
                        sessionId = sessionId,
                        senderId = msg.senderId,
                        text = msg.text,
                        timestamp = msg.timestamp,
                        isSystemMessage = msg.isSystemMessage,
                        isVoiceNote = msg.isVoiceNote,
                        voiceDurationSeconds = msg.voiceDurationSeconds
                    )
                }
                repositoryScope.launch {
                    messageDao.insertMessages(entities)
                }
            }

        activeListeners[sessionId] = registration
    }

    fun stopFirestoreSync(sessionId: String) {
        activeListeners.remove(sessionId)?.remove()
    }

    suspend fun sendMessage(sessionId: String, message: Message) {
        val docRef = firestore.collection("sessions")
            .document(sessionId)
            .collection("messages")
            .document()
        val messageWithId = message.copy(id = docRef.id)

        // Optimistic local insert (Room = SSOT)
        messageDao.insertMessage(
            MessageEntity(
                id = messageWithId.id,
                sessionId = sessionId,
                senderId = messageWithId.senderId,
                text = messageWithId.text,
                timestamp = messageWithId.timestamp,
                isSystemMessage = messageWithId.isSystemMessage,
                isVoiceNote = messageWithId.isVoiceNote,
                voiceDurationSeconds = messageWithId.voiceDurationSeconds
            )
        )
        // Remote sync
        docRef.set(messageWithId).await()
    }

    suspend fun updateSessionStatus(sessionId: String, status: String) {
        firestore.collection("sessions").document(sessionId)
            .update("status", status).await()
    }

    suspend fun clearLocalSession(sessionId: String) {
        stopFirestoreSync(sessionId)
        messageDao.clearSession(sessionId)
    }
}

private fun MessageEntity.toDomain(): Message = Message(
    id = id,
    senderId = senderId,
    text = text,
    timestamp = timestamp,
    isSystemMessage = isSystemMessage,
    isVoiceNote = isVoiceNote,
    voiceDurationSeconds = voiceDurationSeconds
)
