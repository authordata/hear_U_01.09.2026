package com.hearu.app.e2e.tier5

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import androidx.fragment.app.FragmentActivity
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.google.android.gms.tasks.Tasks
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.EventListener
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.util.CustomClassMapper
import com.google.firebase.messaging.RemoteMessage
import com.hearu.app.HearUApplication
import com.hearu.app.ai.AiResult
import com.hearu.app.ai.GeminiAiService
import com.hearu.app.audio.VoiceNoteManager
import com.hearu.app.auth.BiometricHelper
import com.hearu.app.data.RolePreferences
import com.hearu.app.data.local.AppDatabase
import com.hearu.app.data.local.dao.MessageDao
import com.hearu.app.model.Message
import com.hearu.app.purgeExpiredVoiceNoteCacheFiles
import com.hearu.app.repository.AuthRepository
import com.hearu.app.repository.ChatRepository
import com.hearu.app.repository.UserRepository
import com.hearu.app.service.HearUFirebaseMessagingService
import com.hearu.app.service.HearUNotificationConfig
import com.hearu.app.ui.auth.AuthViewModel
import com.hearu.app.ui.chat.ChatViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.io.File
import kotlin.io.path.createTempDirectory

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = HearUApplication::class)
class Tier5BackendAndServicesHardeningTest {

    @Test
    fun tier5_authViewModel_syncsRoleDisplayNameAndOfflineStatusToFirestore() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val mockAuthRepo: AuthRepository = mock()
            val mockPrefs: RolePreferences = mock()
            val mockDao: MessageDao = mock()
            val mockUserRepo: UserRepository = mock()
            val mockUser: FirebaseUser = mock()
            val app = ApplicationProvider.getApplicationContext<HearUApplication>()

            whenever(mockAuthRepo.currentUser).thenReturn(mockUser)
            whenever(mockUser.uid).thenReturn("uid_sync_1")
            whenever(mockPrefs.activeRoleFlow).thenReturn(flowOf(null))
            whenever(mockPrefs.isOnboardingCompletedFlow).thenReturn(flowOf(true))
            whenever(mockPrefs.isBiometricEnabledFlow).thenReturn(flowOf(false))
            whenever(mockPrefs.isDarkThemeFlow).thenReturn(flowOf(null))
            whenever(mockPrefs.displayNameFlow).thenReturn(flowOf("KindSoul"))

            val vm = AuthViewModel(mockAuthRepo, mockPrefs, mockDao, app, mockUserRepo)
            vm.saveRole("giver")
            vm.setDisplayName("GentleOak")
            vm.signOut()
            dispatcher.scheduler.advanceUntilIdle()

            verify(mockUserRepo).updateRole("uid_sync_1", "giver")
            verify(mockUserRepo).updateDisplayName("uid_sync_1", "GentleOak")
            verify(mockUserRepo).updateUserStatus("uid_sync_1", "offline")
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun tier5_voiceNoteManager_resetsPlayingStateOnPlaybackPrepareError() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val manager = VoiceNoteManager(mainDispatcher = dispatcher, ioDispatcher = dispatcher)
        val tmpDir = createTempDirectory("tier5_voice_err").toFile()
        val corruptLocalFile = File(tmpDir, "corrupt_voice.m4a").apply { writeText("not valid audio") }
        try {
            manager.togglePlayback("msg_bad", corruptLocalFile.absolutePath, 5)
            dispatcher.scheduler.advanceTimeBy(500L)
            dispatcher.scheduler.runCurrent()

            assertFalse("isPlaying must reset to false after playback error", manager.isPlaying.value)
            assertNull("playingMessageId must reset to null after playback error", manager.playingMessageId.value)
            assertEquals(0f, manager.playbackProgress.value, 0.001f)
        } finally {
            manager.cleanup()
            tmpDir.deleteRecursively()
        }
    }

    @Test
    fun tier5_geminiAiService_detectsSuicidalHurtMyselfAndMultilineCrisisVariantsOnError() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val service = GeminiAiService(
            aiClient = GeminiAiService.AiChatClient { _, _ ->
                throw RuntimeException("Safety filter blocked")
            },
            ioDispatcher = dispatcher
        )
        val variants = listOf(
            "I feel suicidal right now",
            "I want to hurt myself",
            "I want to kill\nmyself",
            "I don't want to live anymore"
        )
        variants.forEach { prompt ->
            val result = service.generateEmpatheticResponse(prompt)
            assertTrue("Expected AiResult.Error for '$prompt'", result is AiResult.Error)
            assertTrue("Expected isCrisisDetected=true for '$prompt'", (result as AiResult.Error).isCrisisDetected)
        }
    }

    @Test
    fun tier5_authRepository_deleteAccountRestoresUserDocWhenRecentLoginRequired() = runTest {
        val mockAuth: FirebaseAuth = mock()
        val mockFirestore: FirebaseFirestore = mock()
        val mockUsers: CollectionReference = mock()
        val mockPublic: CollectionReference = mock()
        val mockUserDoc: DocumentReference = mock()
        val mockPublicDoc: DocumentReference = mock()
        val mockSnapshot: DocumentSnapshot = mock()
        val mockUser: FirebaseUser = mock()
        val recentEx: FirebaseAuthRecentLoginRequiredException = mock()

        val savedProfile = mapOf(
            "uid" to "uid_1",
            "blockedUsers" to listOf("blocked_u2"),
            "role" to "seeker"
        )
        whenever(mockAuth.currentUser).thenReturn(mockUser)
        whenever(mockUser.uid).thenReturn("uid_1")
        whenever(mockFirestore.collection("users")).thenReturn(mockUsers)
        whenever(mockFirestore.collection("publicListeners")).thenReturn(mockPublic)
        whenever(mockUsers.document("uid_1")).thenReturn(mockUserDoc)
        whenever(mockPublic.document("uid_1")).thenReturn(mockPublicDoc)
        whenever(mockUserDoc.get()).thenReturn(Tasks.forResult(mockSnapshot))
        whenever(mockSnapshot.exists()).thenReturn(true)
        whenever(mockSnapshot.data).thenReturn(savedProfile)
        whenever(mockUserDoc.delete()).thenReturn(Tasks.forResult(null))
        whenever(mockUserDoc.set(any(), eq(SetOptions.merge()))).thenReturn(Tasks.forResult(null))
        whenever(mockPublicDoc.delete()).thenReturn(Tasks.forResult(null))
        whenever(mockUser.delete()).thenReturn(Tasks.forException(recentEx))

        val repo = AuthRepository(mockAuth, mockFirestore)
        val result = repo.deleteAccount()
        assertTrue(result.isFailure)
        verify(mockUserDoc).set(any<Map<String, Any>>(), eq(SetOptions.merge()))
    }

    @Test
    fun tier5_chatRepository_propagatesFirestoreListenerErrors() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val mockFirestore: FirebaseFirestore = mock()
        val mockSessions: CollectionReference = mock()
        val mockSessionDoc: DocumentReference = mock()
        val mockMessages: CollectionReference = mock()
        val mockQuery: Query = mock()
        val mockDao: MessageDao = mock()
        val mockReg: ListenerRegistration = mock()
        var msgListener: EventListener<QuerySnapshot>? = null
        var sessionListener: EventListener<DocumentSnapshot>? = null

        whenever(mockFirestore.collection("sessions")).thenReturn(mockSessions)
        whenever(mockSessions.document("s1")).thenReturn(mockSessionDoc)
        whenever(mockSessionDoc.collection("messages")).thenReturn(mockMessages)
        whenever(mockMessages.orderBy("timestamp", Query.Direction.ASCENDING)).thenReturn(mockQuery)
        whenever(mockQuery.addSnapshotListener(any())).thenAnswer { inv ->
            @Suppress("UNCHECKED_CAST")
            msgListener = inv.getArgument(0) as EventListener<QuerySnapshot>
            mockReg
        }
        whenever(mockSessionDoc.addSnapshotListener(any())).thenAnswer { inv ->
            @Suppress("UNCHECKED_CAST")
            sessionListener = inv.getArgument(0) as EventListener<DocumentSnapshot>
            mockReg
        }
        whenever(mockDao.getMessagesForSession("s1")).thenReturn(flowOf(emptyList()))

        val repo = ChatRepository(mockFirestore, mockDao, dispatcher)

        var msgError: Throwable? = null
        val msgJob = launch(dispatcher) {
            repo.getMessages("s1").catch { msgError = it }.toList()
        }
        dispatcher.scheduler.runCurrent()
        msgListener?.onEvent(
            null,
            FirebaseFirestoreException("PERMISSION_DENIED", FirebaseFirestoreException.Code.PERMISSION_DENIED)
        )
        dispatcher.scheduler.advanceUntilIdle()
        msgJob.cancel()
        assertNotNull("getMessages must propagate Firestore listener errors", msgError)

        var sessionError: Throwable? = null
        val sessionJob = launch(dispatcher) {
            repo.observeSession("s1").catch { sessionError = it }.toList()
        }
        dispatcher.scheduler.runCurrent()
        sessionListener?.onEvent(
            null,
            FirebaseFirestoreException("PERMISSION_DENIED", FirebaseFirestoreException.Code.PERMISSION_DENIED)
        )
        dispatcher.scheduler.advanceUntilIdle()
        sessionJob.cancel()
        assertNotNull("observeSession must propagate Firestore listener errors", sessionError)
    }

    @Test
    fun tier5_fcmService_handlesSessionEndedTitleAndBlankMessageFallback() {
        val app = ApplicationProvider.getApplicationContext<HearUApplication>()
        if (FirebaseApp.getApps(app).isEmpty()) {
            FirebaseApp.initializeApp(
                app,
                FirebaseOptions.Builder()
                    .setApplicationId("1:100000000000:android:abcdef0123456789")
                    .setApiKey("AIzaSyTestKeyForRobolectricUnitTests")
                    .setProjectId("hearu-production")
                    .build()
            )
        }
        Shadows.shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val controller = Robolectric.buildService(HearUFirebaseMessagingService::class.java).create()
        val service = controller.get()
        val nm = service.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancelAll()

        val msg = RemoteMessage.Builder("sender@fcm.googleapis.com")
            .setData(
                mapOf(
                    HearUNotificationConfig.EXTRA_NOTIFICATION_TYPE to HearUNotificationConfig.TYPE_SESSION_ENDED,
                    HearUNotificationConfig.EXTRA_SESSION_ID to "s1",
                    "message" to "   "
                )
            )
            .build()
        service.onMessageReceived(msg)
        val posted = Shadows.shadowOf(nm).allNotifications.single()
        val title = posted.extras.getCharSequence("android.title")?.toString()
        val text = posted.extras.getCharSequence("android.text")?.toString()
        controller.destroy()

        assertEquals("Session Ended", title)
        assertFalse("Blank data['message'] must fall back to default body", text.isNullOrBlank())
    }

    @Test
    fun tier5_messageCustomClassMapper_matchesFirestoreRulesAllowlist() {
        val allowedKeys = setOf(
            "senderId", "text", "timestamp",
            "isSystemMessage", "systemMessage",
            "isVoiceNote", "voiceNote",
            "voiceDurationSeconds", "messageType",
            "audioUrl", "audioDurationSec", "waveformAmplitudes"
        )
        val voiceMsg = Message(
            id = "doc_123",
            senderId = "u1",
            text = "Voice",
            timestamp = 1700000000L,
            isSystemMessage = false,
            isVoiceNote = true,
            voiceDurationSeconds = 12,
            messageType = Message.TYPE_VOICE,
            audioUrl = "https://cdn.hearu.app/v.m4a",
            audioDurationSec = 12,
            waveformAmplitudes = listOf(10, 50, 90)
        )
        @Suppress("UNCHECKED_CAST")
        val serialized = CustomClassMapper.convertToPlainJavaTypes(voiceMsg) as Map<String, Any?>
        assertTrue("Unexpected keys: ${serialized.keys - allowedKeys}", allowedKeys.containsAll(serialized.keys))
        assertFalse(serialized.containsKey("id"))
    }

    @Test
    @Config(sdk = [26], application = HearUApplication::class)
    fun tier5_biometricHelper_compatibleOnApi26() {
        val context: Context = ApplicationProvider.getApplicationContext()
        val activity = Robolectric.buildActivity(FragmentActivity::class.java).setup().get()
        val helper = BiometricHelper()
        helper.isBiometricAvailable(context)
        helper.showBiometricPrompt(activity, onSuccess = {}, onError = {})
    }

    @Test
    fun tier5_appDatabase_migratesV1WithIndicesToV3() = runTest {
        val context = ApplicationProvider.getApplicationContext<HearUApplication>()
        val dbName = "migration_v1_idx_${System.nanoTime()}.db"
        context.deleteDatabase(dbName)
        val callback = object : SupportSQLiteOpenHelper.Callback(1) {
            override fun onCreate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `cached_messages` (" +
                        "`id` TEXT NOT NULL, `sessionId` TEXT NOT NULL, `senderId` TEXT NOT NULL, " +
                        "`text` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, `isSystemMessage` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`id`))"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_cached_messages_sessionId_timestamp` ON `cached_messages` (`sessionId`, `timestamp`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_cached_messages_sessionId` ON `cached_messages` (`sessionId`)")
                db.execSQL("INSERT INTO cached_messages (id, sessionId, senderId, text, timestamp, isSystemMessage) VALUES ('m1', 's1', 'u1', 'hi', 1000, 0)")
            }
            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        }
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context).name(dbName).callback(callback).build()
        )
        helper.writableDatabase.close()
        helper.close()

        val db = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3)
            .allowMainThreadQueries()
            .build()
        assertEquals(1, db.messageDao().getMessagesForSession("s1").first().size)
        db.close()
        context.deleteDatabase(dbName)
    }

    @Test
    fun tier5_mainActivity_purgesExpiredVoiceNoteCacheFilesOlderThan30Days() {
        val tmpCache = createTempDirectory("tier5_voice_purge").toFile()
        try {
            val now = 1_750_000_000_000L
            val thirtyOneDaysAgo = now - 31L * 24L * 60L * 60L * 1000L
            val fiveDaysAgo = now - 5L * 24L * 60L * 60L * 1000L

            val oldVoice = File(tmpCache, "voice_note_old.m4a").apply {
                writeText("old")
                setLastModified(thirtyOneDaysAgo)
            }
            val recentVoice = File(tmpCache, "voice_note_recent.m4a").apply {
                writeText("recent")
                setLastModified(fiveDaysAgo)
            }
            val otherFile = File(tmpCache, "unrelated_cache.tmp").apply {
                writeText("keep")
                setLastModified(thirtyOneDaysAgo)
            }

            val deleted = purgeExpiredVoiceNoteCacheFiles(tmpCache, retentionDays = 30, nowMillis = now)
            assertEquals(1, deleted)
            assertFalse("Expired voice_note_*.m4a must be purged", oldVoice.exists())
            assertTrue("Recent voice_note_*.m4a must be kept", recentVoice.exists())
            assertTrue("Non-voice cache files must not be deleted", otherFile.exists())
        } finally {
            tmpCache.deleteRecursively()
        }
    }

    @Test
    fun tier5_chatViewModel_stopAndSendVoiceRecording_deletesTempFileWhenSessionBlankOrUnauthenticated() {
        val tmpDir = createTempDirectory("tier5_orphan_voice").toFile()
        try {
            val blankSessionFile = File(tmpDir, "voice_note_blank_sess.m4a").apply { writeText("audio") }
            val unauthFile = File(tmpDir, "voice_note_unauth.m4a").apply { writeText("audio") }

            val chatRepo: ChatRepository = mock()
            val auth: FirebaseAuth = mock()
            val vnm: VoiceNoteManager = mock()
            whenever(vnm.isRecording).thenReturn(MutableStateFlow(false))
            whenever(vnm.recordingDuration).thenReturn(MutableStateFlow(0))
            whenever(vnm.liveAmplitudes).thenReturn(MutableStateFlow(emptyList()))
            whenever(vnm.playingMessageId).thenReturn(MutableStateFlow(null))
            whenever(vnm.playbackProgress).thenReturn(MutableStateFlow(0f))
            whenever(vnm.isPlaying).thenReturn(MutableStateFlow(false))
            whenever(chatRepo.getMessages(any())).thenReturn(flowOf(emptyList()))
            whenever(chatRepo.observeSession(any())).thenReturn(flowOf(null))

            whenever(vnm.stopRecording()).thenReturn(
                VoiceNoteManager.RecordingResult(blankSessionFile, 3, listOf(20, 40))
            )
            val vmBlank = ChatViewModel(chatRepo, auth, vnm)
            vmBlank.stopAndSendVoiceRecording()
            assertFalse("Orphaned recording file must be deleted when currentSessionId is blank", blankSessionFile.exists())

            whenever(auth.currentUser).thenReturn(null)
            whenever(vnm.stopRecording()).thenReturn(
                VoiceNoteManager.RecordingResult(unauthFile, 3, listOf(20, 40))
            )
            val vmUnauth = ChatViewModel(chatRepo, auth, vnm)
            vmUnauth.joinSession("s_valid", "peer_1")
            vmUnauth.stopAndSendVoiceRecording()
            assertFalse("Orphaned recording file must be deleted when auth user is null", unauthFile.exists())
        } finally {
            tmpDir.deleteRecursively()
        }
    }
}
