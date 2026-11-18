package com.hearu.app.repository

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.EventListener
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QueryDocumentSnapshot
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.Transaction
import com.hearu.app.data.local.AppDatabase
import com.hearu.app.data.local.dao.MessageDao
import com.hearu.app.data.local.entity.MessageEntity
import com.hearu.app.model.Message
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ChatRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var messageDao: MessageDao
    private lateinit var mockFirestore: FirebaseFirestore
    private lateinit var mockSessionsCollection: CollectionReference
    private lateinit var mockSessionDoc: DocumentReference
    private lateinit var mockMessagesCollection: CollectionReference
    private lateinit var mockDocRef: DocumentReference
    private lateinit var mockQuery: Query
    private lateinit var mockRegistration: ListenerRegistration
    private var capturedListener: EventListener<QuerySnapshot>? = null

    @Before
    fun setUp() {
        val context: Context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        messageDao = db.messageDao()

        mockFirestore = mock()
        mockSessionsCollection = mock()
        mockSessionDoc = mock()
        mockMessagesCollection = mock()
        mockDocRef = mock()
        mockQuery = mock()
        mockRegistration = mock()

        whenever(mockFirestore.collection("sessions")).thenReturn(mockSessionsCollection)
        whenever(mockSessionsCollection.document(any())).thenReturn(mockSessionDoc)
        whenever(mockSessionDoc.collection("messages")).thenReturn(mockMessagesCollection)
        whenever(mockMessagesCollection.document()).thenReturn(mockDocRef)
        whenever(mockDocRef.id).thenReturn("msg_100")
        whenever(mockMessagesCollection.orderBy("timestamp", Query.Direction.ASCENDING)).thenReturn(mockQuery)
        whenever(mockQuery.limitToLast(any())).thenReturn(mockQuery)
        whenever(mockQuery.addSnapshotListener(any())).thenAnswer { inv ->
            @Suppress("UNCHECKED_CAST")
            capturedListener = inv.getArgument(0) as EventListener<QuerySnapshot>
            mockRegistration
        }
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `sendMessage validates text bounds and clamps voice fields`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repo = ChatRepository(mockFirestore, messageDao, dispatcher)

        var blankRejected = false
        try {
            repo.sendMessage("s1", Message(senderId = "u1", text = "   "))
        } catch (_: IllegalArgumentException) {
            blankRejected = true
        }
        assertTrue(blankRejected)

        var longRejected = false
        try {
            repo.sendMessage("s1", Message(senderId = "u1", text = "a".repeat(2001)))
        } catch (_: IllegalArgumentException) {
            longRejected = true
        }
        assertTrue(longRejected)
        verify(mockMessagesCollection, never()).document()

        whenever(mockDocRef.set(any())).thenReturn(Tasks.forResult(null))
        repo.sendMessage(
            "s1",
            Message(
                senderId = "u1",
                text = "  Voice note  ",
                isVoiceNote = true,
                voiceDurationSeconds = 999,
                messageType = Message.TYPE_VOICE,
                audioUrl = "/tmp/audio.m4a",
                audioDurationSec = 999,
                waveformAmplitudes = List(100) { 50 }
            )
        )

        val msgCaptor = argumentCaptor<Message>()
        verify(mockDocRef).set(msgCaptor.capture())
        val sent = msgCaptor.firstValue
        assertEquals("Voice note", sent.text)
        assertEquals(600, sent.audioDurationSec)
        assertEquals(600, sent.voiceDurationSeconds)
        assertEquals(64, sent.waveformAmplitudes.size)
        assertNull("Local filesystem path must never be uploaded to Firestore", sent.audioUrl)
        assertFalse(sent.isSystemMessage)

        val stored = messageDao.getMessagesForSession("s1").first()
        assertEquals(1, stored.size)
        assertEquals(MessageEntity.SYNC_SENT, stored[0].syncStatus)
        assertEquals("/tmp/audio.m4a", stored[0].audioUrl)
        assertEquals(64, stored[0].waveformAmplitudes.size)
    }

    @Test
    fun `sendMessage marks Room syncStatus FAILED and rethrows on Firestore error`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repo = ChatRepository(mockFirestore, messageDao, dispatcher)
        whenever(mockDocRef.set(any())).thenReturn(Tasks.forException(RuntimeException("Offline")))

        var failed = false
        try {
            repo.sendMessage("s1", Message(senderId = "u1", text = "Hello"))
        } catch (_: RuntimeException) {
            failed = true
        }
        assertTrue(failed)

        val stored = messageDao.getMessagesForSession("s1").first()
        assertEquals(1, stored.size)
        assertEquals(MessageEntity.SYNC_FAILED, stored[0].syncStatus)
    }

    @Test
    fun `getMessages uses callbackFlow removes listener in awaitClose and preserves voice metadata`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repo = ChatRepository(mockFirestore, messageDao, dispatcher)

        repo.getMessages("s_flow").test {
            testScheduler.advanceUntilIdle()
            assertEquals(emptyList<Message>(), awaitItem())

            val mockDoc: DocumentSnapshot = mock()
            whenever(mockDoc.id).thenReturn("remote_voice")
            whenever(mockDoc.toObject(Message::class.java)).thenReturn(
                Message(
                    id = "remote_voice",
                    senderId = "peer_1",
                    text = "Voice",
                    timestamp = 5000L,
                    isVoiceNote = true,
                    voiceDurationSeconds = 12,
                    messageType = Message.TYPE_VOICE,
                    audioUrl = "https://cdn.hearu.app/voice.m4a",
                    audioDurationSec = 12,
                    waveformAmplitudes = listOf(15, 55, 85)
                )
            )
            val mockSnapshot: QuerySnapshot = mock()
            whenever(mockSnapshot.documents).thenReturn(listOf(mockDoc))

            capturedListener?.onEvent(mockSnapshot, null)
            testScheduler.advanceUntilIdle()

            val updated = awaitItem()
            assertEquals(1, updated.size)
            assertEquals(Message.TYPE_VOICE, updated[0].messageType)
            assertEquals("https://cdn.hearu.app/voice.m4a", updated[0].audioUrl)
            assertEquals(12, updated[0].audioDurationSec)
            assertEquals(listOf(15, 55, 85), updated[0].waveformAmplitudes)

            cancelAndIgnoreRemainingEvents()
        }

        verify(mockRegistration).remove()
    }

    @Test
    fun `purgeExpiredLocalMessages and clearAllLocalData delete expected Room rows`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repo = ChatRepository(mockFirestore, messageDao, dispatcher)
        val now = System.currentTimeMillis()
        val fortyDaysAgo = now - 40L * 24L * 60L * 60L * 1000L

        messageDao.insertMessages(
            listOf(
                MessageEntity(id = "old_1", sessionId = "s1", senderId = "u1", text = "Old", timestamp = fortyDaysAgo),
                MessageEntity(id = "new_1", sessionId = "s1", senderId = "u1", text = "New", timestamp = now)
            )
        )

        repo.purgeExpiredLocalMessages(retentionDays = 30)
        val afterPurge = messageDao.getMessagesForSession("s1").first()
        assertEquals(listOf("new_1"), afterPurge.map { it.id })

        repo.clearAllLocalData()
        assertTrue(messageDao.getMessagesForSession("s1").first().isEmpty())
    }

    @Test
    fun `updatePhotoConsent runs transaction updating only caller key and identityRevealed`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repo = ChatRepository(mockFirestore, messageDao, dispatcher)

        val mockTx: Transaction = mock()
        val mockSnap: DocumentSnapshot = mock()
        whenever(mockTx.get(mockSessionDoc)).thenReturn(mockSnap)
        whenever(mockFirestore.runTransaction<Boolean>(any())).thenAnswer { inv ->
            @Suppress("UNCHECKED_CAST")
            val fn = inv.getArgument(0) as Transaction.Function<Boolean>
            Tasks.forResult(fn.apply(mockTx))
        }

        // Case 1: Seeker grants consent while giver has not -> identityRevealed = false
        whenever(mockSnap.getBoolean("seekerConsent")).thenReturn(false)
        whenever(mockSnap.getBoolean("giverConsent")).thenReturn(false)
        val revealed1 = repo.updatePhotoConsent("s1", isSeeker = true, consent = true)
        assertFalse(revealed1)
        verify(mockTx).update(mockSessionDoc, mapOf("seekerConsent" to true, "identityRevealed" to false))

        // Case 2: Giver grants consent while seeker already consented -> identityRevealed = true
        whenever(mockSnap.getBoolean("seekerConsent")).thenReturn(true)
        whenever(mockSnap.getBoolean("giverConsent")).thenReturn(false)
        val revealed2 = repo.updatePhotoConsent("s1", isSeeker = false, consent = true)
        assertTrue(revealed2)
        verify(mockTx).update(mockSessionDoc, mapOf("giverConsent" to true, "identityRevealed" to true))
    }

    @Test
    fun `CancellationException is rethrown from updateSessionStatus`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repo = ChatRepository(mockFirestore, messageDao, dispatcher)
        whenever(mockSessionDoc.update("status", "ended")).thenAnswer {
            throw CancellationException("cancelled")
        }

        var caught = false
        try {
            repo.updateSessionStatus("s1", "ended")
        } catch (_: CancellationException) {
            caught = true
        }
        assertTrue(caught)
    }

    @Test
    fun `getMessages processes incremental documentChanges without re-deserializing unchanged documents`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repo = ChatRepository(mockFirestore, messageDao, dispatcher)

        repo.getMessages("s_inc").test {
            testScheduler.advanceUntilIdle()
            assertEquals(emptyList<Message>(), awaitItem())

            val unchangedDoc: QueryDocumentSnapshot = mock()
            val addedDoc: QueryDocumentSnapshot = mock()
            whenever(addedDoc.id).thenReturn("inc_msg_2")
            whenever(addedDoc.toObject(Message::class.java)).thenReturn(
                Message(id = "inc_msg_2", senderId = "peer_1", text = "Incremental message", timestamp = 2000L)
            )
            val docChange: DocumentChange = mock()
            whenever(docChange.type).thenReturn(DocumentChange.Type.ADDED)
            whenever(docChange.document).thenReturn(addedDoc)

            val mockSnapshot: QuerySnapshot = mock()
            whenever(mockSnapshot.documentChanges).thenReturn(listOf(docChange))
            whenever(mockSnapshot.documents).thenReturn(listOf(unchangedDoc, addedDoc))

            capturedListener?.onEvent(mockSnapshot, null)
            testScheduler.advanceUntilIdle()

            val updated = awaitItem()
            assertEquals(1, updated.size)
            assertEquals("Incremental message", updated[0].text)
            verify(unchangedDoc, never()).toObject(Message::class.java)

            cancelAndIgnoreRemainingEvents()
        }
    }
}
