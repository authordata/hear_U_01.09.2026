package com.hearu.app.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.hearu.app.data.local.dao.MessageDao
import com.hearu.app.data.local.entity.MessageEntity
import com.hearu.app.di.IoDispatcher
import com.hearu.app.model.ChatSession
import com.hearu.app.model.Message
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val messageDao: MessageDao,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {
    constructor(
        firestore: FirebaseFirestore,
        messageDao: MessageDao
    ) : this(firestore, messageDao, Dispatchers.IO)

    private val activeListeners = ConcurrentHashMap<String, ListenerRegistration>()

    /**
     * Returns Room as Single Source of Truth while syncing Firestore snapshots via callbackFlow.
     * The Firestore SnapshotListener is automatically removed in awaitClose when collection stops,
     * preventing listener leaks.
     */
    fun getMessages(sessionId: String): Flow<List<Message>> = callbackFlow {
        if (sessionId.isBlank()) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        val registration = firestore.collection("sessions")
            .document(sessionId)
            .collection("messages")
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                if (snapshot == null) return@addSnapshotListener
                val entities = snapshot.documents.mapNotNull { doc ->
                    val msg = doc.toObject(Message::class.java) ?: return@mapNotNull null
                    msg.toEntity(
                        id = doc.id,
                        sessionId = sessionId,
                        syncStatus = MessageEntity.SYNC_SENT
                    )
                }
                launch(ioDispatcher) {
                    messageDao.insertMessages(entities)
                }
            }

        activeListeners.put(sessionId, registration)?.remove()

        val roomJob = launch(ioDispatcher) {
            messageDao.getMessagesForSession(sessionId).collect { entities ->
                trySend(entities.map { it.toDomain() })
            }
        }

        awaitClose {
            roomJob.cancel()
            if (activeListeners.remove(sessionId, registration)) {
                registration.remove()
            }
        }
    }

    fun observeSession(sessionId: String): Flow<ChatSession?> = callbackFlow {
        if (sessionId.isBlank()) {
            trySend(null)
            close()
            return@callbackFlow
        }

        val registration = firestore.collection("sessions")
            .document(sessionId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                trySend(snapshot?.toObject(ChatSession::class.java))
            }

        awaitClose {
            registration.remove()
        }
    }

    fun stopFirestoreSync(sessionId: String) {
        activeListeners.remove(sessionId)?.remove()
    }

    fun stopAllFirestoreSync() {
        val keys = activeListeners.keys.toList()
        keys.forEach { key ->
            activeListeners.remove(key)?.remove()
        }
    }

    suspend fun sendMessage(sessionId: String, message: Message) {
        require(sessionId.isNotBlank()) { "Session ID cannot be blank." }
        require(message.senderId.isNotBlank()) { "Sender ID cannot be blank." }

        val trimmedText = message.text.trim()
        require(trimmedText.length in 1..2000) { "Message text must be 1..2000 characters." }

        val normalizedType = if (message.isVoiceNote || message.messageType == Message.TYPE_VOICE) {
            Message.TYPE_VOICE
        } else {
            Message.TYPE_TEXT
        }
        val normalizedIsVoice = normalizedType == Message.TYPE_VOICE
        val clampedDuration = (message.audioDurationSec.takeIf { it > 0 } ?: message.voiceDurationSeconds)
            .coerceIn(0, 600)
        val clampedVoiceSeconds = message.voiceDurationSeconds.coerceIn(0, 600)
        val clampedAudioUrl = message.audioUrl?.take(2048)
        val clampedAmplitudes = message.waveformAmplitudes.take(64)

        val docRef = firestore.collection("sessions")
            .document(sessionId)
            .collection("messages")
            .document()

        val sanitizedMessage = message.copy(
            id = docRef.id,
            text = trimmedText,
            isSystemMessage = false,
            isVoiceNote = normalizedIsVoice,
            voiceDurationSeconds = clampedVoiceSeconds,
            messageType = normalizedType,
            audioUrl = clampedAudioUrl,
            audioDurationSec = clampedDuration,
            waveformAmplitudes = clampedAmplitudes
        )

        // Optimistic local insert with PENDING status (Room = SSOT)
        messageDao.insertMessage(
            sanitizedMessage.toEntity(
                id = sanitizedMessage.id,
                sessionId = sessionId,
                syncStatus = MessageEntity.SYNC_PENDING
            )
        )

        try {
            docRef.set(sanitizedMessage).await()
            messageDao.updateSyncStatus(sanitizedMessage.id, MessageEntity.SYNC_SENT)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            withContext(NonCancellable) {
                messageDao.updateSyncStatus(sanitizedMessage.id, MessageEntity.SYNC_FAILED)
            }
            throw e
        }
    }

    suspend fun updateSessionStatus(sessionId: String, status: String) {
        require(sessionId.isNotBlank()) { "Session ID cannot be blank." }
        require(status == "ended") { "Session status transition only supports 'ended'." }
        try {
            firestore.collection("sessions").document(sessionId)
                .update("status", status)
                .await()
        } catch (e: CancellationException) {
            throw e
        }
    }

    suspend fun updatePhotoConsent(
        sessionId: String,
        isSeeker: Boolean,
        consent: Boolean
    ): Boolean {
        require(sessionId.isNotBlank()) { "Session ID cannot be blank." }
        return try {
            val sessionRef = firestore.collection("sessions").document(sessionId)
            firestore.runTransaction { tx ->
                val snapshot = tx.get(sessionRef)
                val currentSeeker = snapshot.getBoolean("seekerConsent") ?: false
                val currentGiver = snapshot.getBoolean("giverConsent") ?: false
                val nextSeeker = if (isSeeker) consent else currentSeeker
                val nextGiver = if (!isSeeker) consent else currentGiver
                val nextRevealed = nextSeeker && nextGiver

                val consentField = if (isSeeker) "seekerConsent" else "giverConsent"
                tx.update(
                    sessionRef,
                    mapOf(
                        consentField to consent,
                        "identityRevealed" to nextRevealed
                    )
                )
                nextRevealed
            }.await()
        } catch (e: CancellationException) {
            throw e
        }
    }

    suspend fun clearLocalSession(sessionId: String) {
        stopFirestoreSync(sessionId)
        messageDao.clearSession(sessionId)
    }

    suspend fun purgeExpiredLocalMessages(retentionDays: Int = 30) {
        val safeDays = retentionDays.coerceAtLeast(1)
        val cutoff = System.currentTimeMillis() - safeDays * MILLIS_PER_DAY
        messageDao.deleteMessagesOlderThan(cutoff)
    }

    suspend fun clearAllLocalData() {
        stopAllFirestoreSync()
        messageDao.clearAll()
    }

    companion object {
        private const val MILLIS_PER_DAY = 24L * 60L * 60L * 1000L
    }
}

private fun Message.toEntity(
    id: String,
    sessionId: String,
    syncStatus: String
): MessageEntity = MessageEntity(
    id = id,
    sessionId = sessionId,
    senderId = senderId,
    text = text,
    timestamp = timestamp,
    isSystemMessage = isSystemMessage,
    isVoiceNote = isVoiceNote,
    voiceDurationSeconds = voiceDurationSeconds,
    messageType = messageType,
    audioUrl = audioUrl,
    audioDurationSec = audioDurationSec,
    waveformAmplitudes = waveformAmplitudes,
    syncStatus = syncStatus
)

private fun MessageEntity.toDomain(): Message = Message(
    id = id,
    senderId = senderId,
    text = text,
    timestamp = timestamp,
    isSystemMessage = isSystemMessage,
    isVoiceNote = isVoiceNote,
    voiceDurationSeconds = voiceDurationSeconds,
    messageType = messageType,
    audioUrl = audioUrl,
    audioDurationSec = audioDurationSec,
    waveformAmplitudes = waveformAmplitudes
)
