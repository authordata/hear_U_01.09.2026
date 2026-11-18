package com.hearu.app.e2e.tier1_2

import android.content.Context
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.EventListener
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.hearu.app.audio.VoiceNoteManager
import com.hearu.app.data.local.AppDatabase
import com.hearu.app.data.local.dao.MessageDao
import com.hearu.app.data.local.entity.MessageEntity
import com.hearu.app.e2e.harness.E2ETestHarness
import com.hearu.app.e2e.harness.MainDispatcherRule
import com.hearu.app.model.ChatSession
import com.hearu.app.model.Message
import com.hearu.app.navigation.Screen
import com.hearu.app.repository.ChatRepository
import com.hearu.app.ui.chat.ChatScreen
import com.hearu.app.ui.chat.ChatViewModel
import com.hearu.app.ui.theme.HearUTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

/**
 * Opaque-box E2E tests for Feature F7: Peer Chat, Room SSOT & Sender Ownership Alignment.
 * Covers Tier 1 (6 happy-path tests) and Tier 2 (6 boundary/corner/error tests).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PeerChatE2ETest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var messageDao: MessageDao
    private lateinit var mockFirestore: FirebaseFirestore
    private lateinit var mockSessionsCollection: CollectionReference
    private lateinit var mockSessionDoc: DocumentReference
    private lateinit var mockMessagesCollection: CollectionReference
    private lateinit var mockQuery: Query
    private lateinit var mockListenerRegistration: ListenerRegistration
    private lateinit var mockAuth: FirebaseAuth
    private lateinit var mockUser: FirebaseUser
    private lateinit var mockVoiceNoteManager: VoiceNoteManager
    private lateinit var chatRepository: ChatRepository

    private val generatedDocRefs = mutableListOf<DocumentReference>()
    private val idCounter = AtomicInteger(0)
    private var capturedSnapshotListener: EventListener<QuerySnapshot>? = null
    private var setTaskFactory: () -> com.google.android.gms.tasks.Task<Void> = { Tasks.forResult(null) }

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = E2ETestHarness.createInMemoryDb(context)
        messageDao = db.messageDao()

        mockFirestore = mock()
        mockSessionsCollection = mock()
        mockSessionDoc = mock()
        mockMessagesCollection = mock()
        mockQuery = mock()
        mockListenerRegistration = mock()
        mockAuth = mock()
        mockUser = mock()
        mockVoiceNoteManager = createStubbedVoiceNoteManager()

        whenever(mockAuth.currentUser).thenReturn(mockUser)
        whenever(mockUser.uid).thenReturn("uid_seeker_self")

        whenever(mockFirestore.collection("sessions")).thenReturn(mockSessionsCollection)
        whenever(mockSessionsCollection.document(any())).thenReturn(mockSessionDoc)
        whenever(mockSessionDoc.update(any<String>(), any())).thenReturn(Tasks.forResult(null))
        whenever(mockSessionDoc.collection("messages")).thenReturn(mockMessagesCollection)
        whenever(mockMessagesCollection.orderBy("timestamp", Query.Direction.ASCENDING))
            .thenReturn(mockQuery)
        whenever(mockQuery.addSnapshotListener(any())).thenAnswer { invocation ->
            @Suppress("UNCHECKED_CAST")
            capturedSnapshotListener = invocation.getArgument(0) as EventListener<QuerySnapshot>
            mockListenerRegistration
        }
        whenever(mockMessagesCollection.document()).thenAnswer {
            val docRef: DocumentReference = mock()
            val generatedId = "msg_gen_${idCounter.incrementAndGet()}"
            whenever(docRef.id).thenReturn(generatedId)
            whenever(docRef.set(any())).thenAnswer { setTaskFactory() }
            generatedDocRefs.add(docRef)
            docRef
        }

        chatRepository = ChatRepository(mockFirestore, messageDao)
    }

    @After
    fun tearDown() {
        chatRepository.stopFirestoreSync("s1")
        chatRepository.stopFirestoreSync("s2")
        db.close()
    }

    private fun createStubbedVoiceNoteManager(): VoiceNoteManager {
        val manager: VoiceNoteManager = mock()
        whenever(manager.isRecording).thenReturn(MutableStateFlow(false))
        whenever(manager.recordingDuration).thenReturn(MutableStateFlow(0))
        whenever(manager.liveAmplitudes).thenReturn(MutableStateFlow(emptyList()))
        whenever(manager.playingMessageId).thenReturn(MutableStateFlow(null))
        whenever(manager.playbackProgress).thenReturn(MutableStateFlow(0f))
        whenever(manager.isPlaying).thenReturn(MutableStateFlow(false))
        return manager
    }

    private fun createChatViewModel(): ChatViewModel =
        ChatViewModel(chatRepository, mockAuth, mockVoiceNoteManager)

    private fun drainAsync() {
        ShadowLooper.idleMainLooper()
        mainDispatcherRule.testDispatcher.scheduler.runCurrent()
        ShadowLooper.idleMainLooper()
        composeTestRule.waitForIdle()
    }

    private fun <T> runOnIo(block: suspend () -> T): T = runBlocking(Dispatchers.IO) { block() }

    // =========================================================================
    // Tier 1 — Happy Path (6 Tests)
    // =========================================================================

    @Test
    fun tier1_emptyState_displaysSafeSpaceAnd30DayNotice() {
        val vm = createChatViewModel()

        composeTestRule.setContent {
            HearUTheme(darkTheme = false) {
                ChatScreen(
                    sessionId = "s1",
                    userId = "uid_seeker_self",
                    onNavigateBack = {},
                    viewModel = vm
                )
            }
        }
        drainAsync()

        composeTestRule.onNodeWithText("Empathetic Listener").assertIsDisplayed()
        composeTestRule.onNodeWithText("Confidential 30-day ephemeral peer chat").assertIsDisplayed()
        composeTestRule.onNodeWithText("Your Safe Space is Connected").assertIsDisplayed()
        composeTestRule.onNodeWithText("30 days", substring = true).assertIsDisplayed()
        listOf("❤️", "🫂", "🙏", "🌸", "✨").forEach { emoji ->
            composeTestRule.onNodeWithText(emoji).assertIsDisplayed()
        }
    }

    @Test
    fun tier1_sendTextMessage_optimisticallyPersistsInRoomAndRendersOnRight() {
        val vm = createChatViewModel()

        composeTestRule.setContent {
            HearUTheme(darkTheme = false) {
                ChatScreen(
                    sessionId = "s1",
                    userId = "uid_seeker_self",
                    onNavigateBack = {},
                    viewModel = vm
                )
            }
        }
        drainAsync()

        composeTestRule.onNodeWithText("Share with compassion...")
            .performTextInput("Hello, thank you for listening")
        composeTestRule.onNodeWithContentDescription("Send").performClick()
        composeTestRule.waitUntil(timeoutMillis = 3000L) { vm.messages.value.size == 1 }
        drainAsync()

        val stored = runOnIo { messageDao.getMessagesForSession("s1").first() }
        assertEquals(1, stored.size)
        assertEquals("Hello, thank you for listening", stored[0].text)
        assertEquals("uid_seeker_self", stored[0].senderId)

        composeTestRule.onNodeWithText("Hello, thank you for listening").assertIsDisplayed()
        composeTestRule.onNodeWithText("Your Safe Space is Connected").assertDoesNotExist()
    }

    @Test
    fun tier1_roomSsotAndFirestoreSnapshot_streamsOrderedMessagesToUi() {
        val vm = createChatViewModel()

        composeTestRule.setContent {
            HearUTheme(darkTheme = false) {
                ChatScreen(
                    sessionId = "s1",
                    userId = "uid_seeker_self",
                    onNavigateBack = {},
                    viewModel = vm
                )
            }
        }
        drainAsync()

        val docLate: DocumentSnapshot = mock()
        whenever(docLate.id).thenReturn("remote_2")
        whenever(docLate.toObject(Message::class.java)).thenReturn(
            Message(id = "remote_2", senderId = "uid_seeker_self", text = "Second message", timestamp = 2000L)
        )

        val docEarly: DocumentSnapshot = mock()
        whenever(docEarly.id).thenReturn("remote_1")
        whenever(docEarly.toObject(Message::class.java)).thenReturn(
            Message(id = "remote_1", senderId = "uid_giver_peer", text = "First message", timestamp = 1000L)
        )

        val snapshot: QuerySnapshot = mock()
        whenever(snapshot.documents).thenReturn(listOf(docLate, docEarly))

        capturedSnapshotListener?.onEvent(snapshot, null)
        composeTestRule.waitUntil(timeoutMillis = 3000L) {
            vm.messages.value.size == 2
        }
        drainAsync()

        assertEquals("First message", vm.messages.value[0].text)
        assertEquals("Second message", vm.messages.value[1].text)
        composeTestRule.onNodeWithText("First message").assertIsDisplayed()
        composeTestRule.onNodeWithText("Second message").assertIsDisplayed()
    }

    @Test
    fun tier1_senderOwnershipAlignment_alignsMineRightAndPeerLeftWhenUidMatches() {
        whenever(mockUser.uid).thenReturn("user_me")
        runOnIo {
            messageDao.insertMessages(
                listOf(
                    MessageEntity(
                        id = "m_peer",
                        sessionId = "s1",
                        senderId = "peer_1",
                        text = "Peer msg",
                        timestamp = 1000L
                    ),
                    MessageEntity(
                        id = "m_mine",
                        sessionId = "s1",
                        senderId = "user_me",
                        text = "My msg",
                        timestamp = 2000L
                    )
                )
            )
        }
        val vm = createChatViewModel()

        composeTestRule.setContent {
            HearUTheme(darkTheme = false) {
                ChatScreen(
                    sessionId = "s1",
                    userId = "user_me",
                    onNavigateBack = {},
                    viewModel = vm
                )
            }
        }
        composeTestRule.waitUntil(timeoutMillis = 3000L) { vm.messages.value.size == 2 }
        drainAsync()

        val myBounds = composeTestRule.onNodeWithText("My msg").fetchSemanticsNode().boundsInRoot
        val peerBounds = composeTestRule.onNodeWithText("Peer msg").fetchSemanticsNode().boundsInRoot
        assertTrue(
            "Own message (left=${myBounds.left}) must align right of peer message (left=${peerBounds.left})",
            myBounds.left > peerBounds.left
        )
    }

    @Test
    fun tier1_quickEmpathyEmojiChips_sendAllFiveReactions() {
        val vm = createChatViewModel()

        composeTestRule.setContent {
            HearUTheme(darkTheme = false) {
                ChatScreen(
                    sessionId = "s1",
                    userId = "uid_seeker_self",
                    onNavigateBack = {},
                    viewModel = vm
                )
            }
        }
        drainAsync()

        val emojis = listOf("❤️", "🫂", "🙏", "🌸", "✨")
        emojis.forEachIndexed { index, emoji ->
            composeTestRule.onNodeWithText(emoji).performClick()
            composeTestRule.waitUntil(timeoutMillis = 3000L) { vm.messages.value.size == index + 1 }
            drainAsync()
        }

        val stored = runOnIo { messageDao.getMessagesForSession("s1").first() }
        assertEquals(5, stored.size)
        assertEquals(emojis.toSet(), stored.map { it.text }.toSet())
    }

    @Test
    fun tier1_sessionLifecycle_updateStatusClearSessionAnd30DayPurgeContract() =
        runTest(mainDispatcherRule.testDispatcher) {
            messageDao.insertMessages(
                listOf(
                    MessageEntity(id = "s1_m1", sessionId = "s1", senderId = "u1", text = "Session 1", timestamp = 100L),
                    MessageEntity(id = "s2_m1", sessionId = "s2", senderId = "u2", text = "Session 2", timestamp = 200L)
                )
            )
            chatRepository.getMessages("s1").first()

            chatRepository.updateSessionStatus("s1", "ended")
            verify(mockSessionDoc).update("status", "ended")

            chatRepository.clearLocalSession("s1")
            verify(mockListenerRegistration).remove()
            assertTrue(messageDao.getMessagesForSession("s1").first().isEmpty())
            assertEquals(1, messageDao.getMessagesForSession("s2").first().size)

            val defaultSession = ChatSession(sessionId = "s1", seekerId = "u1", giverId = "u2")
            assertEquals("active", defaultSession.status)
            assertFalse(defaultSession.identityRevealed)
            assertEquals("chat/s1/u2", Screen.Chat.createRoute("s1", "u2"))

            val functionsContent = File(E2ETestHarness.resolveProjectRoot(), "functions/index.js").readText()
            assertTrue(functionsContent.contains("purgeExpiredChats"))
            assertTrue(functionsContent.contains("every 24 hours"))
            assertTrue(functionsContent.contains("30 * 24 * 60 * 60 * 1000"))
        }

    // =========================================================================
    // Tier 2 — Boundary / Corner / Error (6 Tests)
    // =========================================================================

    @Test
    fun tier2_senderOwnershipBug_peerIdNavArgDoesNotMisalignMyMessages() {
        whenever(mockUser.uid).thenReturn("user_me")
        runOnIo {
            messageDao.insertMessages(
                listOf(
                    MessageEntity(
                        id = "m_mine",
                        sessionId = "s1",
                        senderId = "user_me",
                        text = "My own message",
                        timestamp = 1000L
                    ),
                    MessageEntity(
                        id = "m_peer",
                        sessionId = "s1",
                        senderId = "demo_listener",
                        text = "Listener reply",
                        timestamp = 2000L
                    )
                )
            )
        }
        val vm = createChatViewModel()

        // Reproduces SeekerDashboard / GiverDashboard / HearUNavigation passing peerId into userId
        composeTestRule.setContent {
            HearUTheme(darkTheme = false) {
                ChatScreen(
                    sessionId = "s1",
                    userId = "demo_listener",
                    onNavigateBack = {},
                    viewModel = vm
                )
            }
        }
        composeTestRule.waitUntil(timeoutMillis = 3000L) { vm.messages.value.size == 2 }
        drainAsync()

        val myBounds = composeTestRule.onNodeWithText("My own message").fetchSemanticsNode().boundsInRoot
        val peerBounds = composeTestRule.onNodeWithText("Listener reply").fetchSemanticsNode().boundsInRoot
        assertTrue(
            "Own message must align on the RIGHT even when nav arg userId contains peerId 'demo_listener'",
            myBounds.left > peerBounds.left
        )
    }

    @Test
    fun tier2_blankAndWhitespaceMessages_showMicAndBlockSubmission() {
        val vm = createChatViewModel()

        composeTestRule.setContent {
            HearUTheme(darkTheme = false) {
                ChatScreen(
                    sessionId = "s1",
                    userId = "uid_seeker_self",
                    onNavigateBack = {},
                    viewModel = vm
                )
            }
        }
        drainAsync()

        composeTestRule.onNodeWithContentDescription("Send").assertDoesNotExist()
        composeTestRule.onNodeWithContentDescription("Record Voice Note").assertIsDisplayed()

        composeTestRule.onNodeWithText("Share with compassion...").performTextInput("   \n\t  ")
        drainAsync()
        composeTestRule.onNodeWithContentDescription("Send").assertDoesNotExist()
        composeTestRule.onNodeWithContentDescription("Record Voice Note").assertIsDisplayed()

        vm.sendMessage("   \n\t  ")
        drainAsync()

        verify(mockMessagesCollection, never()).document()
        assertTrue(runOnIo { messageDao.getMessagesForSession("s1").first().isEmpty() })
    }

    @Test
    fun tier2_specialCharactersUnicodeAnd2000CharBoundary_preservedAndTrimmed() {
        val vm = createChatViewModel()

        composeTestRule.setContent {
            HearUTheme(darkTheme = false) {
                ChatScreen(
                    sessionId = "s1",
                    userId = "uid_seeker_self",
                    onNavigateBack = {},
                    viewModel = vm
                )
            }
        }
        drainAsync()

        val oneChar = "A"
        val specialWithPadding = "  Hi 🌟 <tag> & 'q'\nLine2  "
        val max2000 = "x".repeat(2000)

        vm.sendMessage(oneChar)
        composeTestRule.waitUntil(timeoutMillis = 3000L) { vm.messages.value.size == 1 }
        vm.sendMessage(specialWithPadding)
        composeTestRule.waitUntil(timeoutMillis = 3000L) { vm.messages.value.size == 2 }
        vm.sendMessage(max2000)
        composeTestRule.waitUntil(timeoutMillis = 3000L) { vm.messages.value.size == 3 }
        drainAsync()

        val stored = runOnIo { messageDao.getMessagesForSession("s1").first() }
        assertEquals(3, stored.size)
        val storedTexts = stored.map { it.text }.toSet()
        assertTrue(storedTexts.contains("A"))
        assertTrue(storedTexts.contains("Hi 🌟 <tag> & 'q'\nLine2"))
        assertTrue(storedTexts.contains(max2000))

        composeTestRule.onNode(
            hasScrollToNodeAction() and SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange)
        ).performScrollToNode(hasText("Hi 🌟 <tag> & 'q'\nLine2"))
        composeTestRule.onNodeWithText("Hi 🌟 <tag> & 'q'\nLine2").assertIsDisplayed()

        val rulesContent = File(E2ETestHarness.resolveProjectRoot(), "firestore.rules").readText()
        assertTrue(
            "firestore.rules must enforce text length > 0 and <= 2000",
            rulesContent.contains("text.size() > 0") && rulesContent.contains("text.size() <= 2000")
        )
    }

    @Test
    fun tier2_sendMessageRemoteFailure_retainsLocalOptimisticRoomRowAndSurfacesUiError() {
        setTaskFactory = { Tasks.forException(RuntimeException("Network offline")) }
        val vm = createChatViewModel()

        composeTestRule.setContent {
            HearUTheme(darkTheme = false) {
                ChatScreen(
                    sessionId = "s1",
                    userId = "uid_seeker_self",
                    onNavigateBack = {},
                    viewModel = vm
                )
            }
        }
        drainAsync()

        composeTestRule.onNodeWithText("Share with compassion...").performTextInput("Offline test")
        composeTestRule.onNodeWithContentDescription("Send").performClick()
        composeTestRule.waitUntil(timeoutMillis = 3000L) {
            vm.errorState.value != null && !vm.isSending.value
        }
        drainAsync()

        val stored = runOnIo { messageDao.getMessagesForSession("s1").first() }
        assertEquals(1, stored.size)
        assertEquals("Offline test", stored[0].text)
        assertFalse(vm.isSending.value)
        assertNotNull(vm.errorState.value)
        assertTrue(vm.errorState.value!!.contains("Failed to send message"))

        composeTestRule.onNodeWithText("Failed to send message", substring = true).assertIsDisplayed()

        vm.clearError()
        assertNull(vm.errorState.value)
    }

    @Test
    fun tier2_unauthenticatedSend_setsSessionExpiredErrorWithoutWriting() =
        runTest(mainDispatcherRule.testDispatcher) {
            whenever(mockAuth.currentUser).thenReturn(null)
            val vm = createChatViewModel()
            vm.joinSession("s1", "uid_seeker_self")
            drainAsync()

            vm.sendMessage("Hello")
            drainAsync()

            assertEquals("Session expired. Please log in again.", vm.errorState.value)
            verify(mockMessagesCollection, never()).document()
            assertTrue(messageDao.getMessagesForSession("s1").first().isEmpty())
        }

    @Test
    fun tier2_firestoreRulesAndListenerLeakGuard_multiSessionIsolationAndCleanup() {
        runOnIo {
            chatRepository.getMessages("s1").first()
            chatRepository.getMessages("s1").first()
        }
        verify(mockQuery, times(2)).addSnapshotListener(any())
        verify(mockListenerRegistration, times(2)).remove()

        val rulesContent = File(E2ETestHarness.resolveProjectRoot(), "firestore.rules").readText()
        assertTrue(rulesContent.contains("senderId == request.auth.uid"))
        assertTrue(rulesContent.contains("allow update, delete: if false;"))
        assertTrue(rulesContent.contains("allow create: if false;"))

        val vm = createChatViewModel()
        var backClicks = 0
        var crisisClicks = 0

        composeTestRule.setContent {
            HearUTheme(darkTheme = false) {
                ChatScreen(
                    sessionId = "s1",
                    userId = "uid_seeker_self",
                    onNavigateBack = { backClicks++ },
                    onNavigateToCrisis = { crisisClicks++ },
                    viewModel = vm
                )
            }
        }
        drainAsync()

        composeTestRule.onNodeWithContentDescription("Crisis SOS").performClick()
        composeTestRule.onNodeWithContentDescription("Back").performClick()

        assertEquals(1, crisisClicks)
        assertEquals(1, backClicks)
    }
}
