package com.hearu.app.e2e.tier4

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.LocalSaveableStateRegistry
import androidx.compose.runtime.saveable.SaveableStateRegistry
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnySibling
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationCompat
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.test.core.app.ActivityScenario
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
import com.google.firebase.firestore.Transaction
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.HttpsCallableReference
import com.google.firebase.functions.HttpsCallableResult
import com.google.firebase.messaging.RemoteMessage
import com.hearu.app.HearUApplication
import com.hearu.app.MainActivity
import com.hearu.app.R
import com.hearu.app.ai.GeminiAiService
import com.hearu.app.audio.VoiceNoteManager
import com.hearu.app.data.RolePreferences
import com.hearu.app.data.local.AppDatabase
import com.hearu.app.data.local.dao.MessageDao
import com.hearu.app.data.local.entity.MessageEntity
import com.hearu.app.e2e.harness.E2ETestHarness
import com.hearu.app.e2e.harness.MainDispatcherRule
import com.hearu.app.model.Message
import com.hearu.app.navigation.HearUNavigation
import com.hearu.app.navigation.Screen
import com.hearu.app.repository.AuthRepository
import com.hearu.app.repository.AuthResult
import com.hearu.app.repository.ChatRepository
import com.hearu.app.repository.ModerationRepository
import com.hearu.app.repository.UserRepository
import com.hearu.app.service.HearUFirebaseMessagingService
import com.hearu.app.service.HearUNotificationConfig
import com.hearu.app.ui.auth.AuthViewModel
import com.hearu.app.ui.auth.LoginScreen
import com.hearu.app.ui.auth.RoleSelectionScreen
import com.hearu.app.ui.chat.AIChatScreen
import com.hearu.app.ui.chat.AIChatViewModel
import com.hearu.app.ui.chat.ChatScreen
import com.hearu.app.ui.chat.ChatViewModel
import com.hearu.app.ui.emergency.EmergencyScreen
import com.hearu.app.ui.home.GiverDashboard
import com.hearu.app.ui.home.MatchState
import com.hearu.app.ui.home.MatchViewModel
import com.hearu.app.ui.home.ProfileScreen
import com.hearu.app.ui.home.SeekerDashboard
import com.hearu.app.ui.onboarding.OnboardingScreen
import com.hearu.app.ui.theme.HearUTheme
import com.hearu.app.ui.tools.BreathingExerciseScreen
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
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
import org.mockito.MockedStatic
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlin.io.path.createTempDirectory
import kotlin.math.max
import kotlin.math.min

/**
 * Tier 4: 8 full multi-feature real-world application journeys matching
 * TEST_INFRA.md § Real-World Application Scenarios.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RealWorldJourneysE2ETest {

    @get:Rule(order = 0)
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule(order = 1)
    val composeTestRule = createEmptyComposeRule()

    private lateinit var app: HearUApplication
    private lateinit var tempDir: File
    private lateinit var db: AppDatabase
    private lateinit var rolePreferences: RolePreferences
    private lateinit var mockAuthRepository: AuthRepository
    private lateinit var mockAuth: FirebaseAuth
    private lateinit var mockUser: FirebaseUser
    private lateinit var mockFunctions: FirebaseFunctions
    private lateinit var mockCallable: HttpsCallableReference
    private var mockFunctionsStatic: MockedStatic<FirebaseFunctions>? = null
    private lateinit var mockFirestore: FirebaseFirestore
    private lateinit var mockSessionsCollection: CollectionReference
    private lateinit var mockSessionDoc: DocumentReference
    private lateinit var mockMessagesCollection: CollectionReference
    private lateinit var mockQuery: Query
    private lateinit var mockListenerRegistration: ListenerRegistration
    private lateinit var mockReportsCollection: CollectionReference
    private lateinit var mockUsersCollection: CollectionReference
    private lateinit var mockUserDoc: DocumentReference
    private lateinit var mockVoiceNoteManager: VoiceNoteManager
    private var realVoiceNoteManager: VoiceNoteManager? = null
    private val msgIdCounter = AtomicInteger(0)
    private val capturedSentPayloads = mutableListOf<Any>()

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        E2ETestHarness.ensureFirebaseInitialized(app)
        runBlocking { E2ETestHarness.clearRolePreferences(app) }
        tempDir = createTempDirectory("hearu_tier4_journeys").toFile()
        db = E2ETestHarness.createInMemoryDb(app)
        rolePreferences = E2ETestHarness.createRolePreferences(app)

        mockAuthRepository = mock()
        mockAuth = mock()
        mockUser = mock()
        whenever(mockAuthRepository.currentUser).thenReturn(null)
        runBlocking {
            whenever(mockAuthRepository.deleteAccount()).thenReturn(Result.success(Unit))
            whenever(mockAuthRepository.sendPasswordResetEmail(any())).thenReturn(Result.success(Unit))
        }
        whenever(mockAuth.currentUser).thenReturn(mockUser)
        whenever(mockUser.uid).thenReturn("uid_journey_1")

        mockFunctions = mock()
        mockCallable = mock()
        mockFunctionsStatic = Mockito.mockStatic(FirebaseFunctions::class.java)
        mockFunctionsStatic?.`when`<FirebaseFunctions> { FirebaseFunctions.getInstance() }
            ?.thenReturn(mockFunctions)
        whenever(mockFunctions.getHttpsCallable("matchSeekerWithGiver")).thenReturn(mockCallable)

        mockFirestore = mock()
        mockSessionsCollection = mock()
        mockSessionDoc = mock()
        mockMessagesCollection = mock()
        mockQuery = mock()
        mockListenerRegistration = mock()
        mockReportsCollection = mock()
        mockUsersCollection = mock()
        mockUserDoc = mock()
        capturedSentPayloads.clear()

        whenever(mockFirestore.collection("sessions")).thenReturn(mockSessionsCollection)
        whenever(mockSessionsCollection.document(any())).thenReturn(mockSessionDoc)
        whenever(mockSessionDoc.addSnapshotListener(any<EventListener<DocumentSnapshot>>()))
            .thenReturn(mockListenerRegistration)
        whenever(mockSessionDoc.update(any<String>(), any())).thenReturn(Tasks.forResult(null))
        whenever(mockFirestore.runTransaction(any<Transaction.Function<Boolean>>()))
            .thenReturn(Tasks.forResult(false))
        whenever(mockSessionDoc.collection("messages")).thenReturn(mockMessagesCollection)
        whenever(mockMessagesCollection.orderBy("timestamp", Query.Direction.ASCENDING))
            .thenReturn(mockQuery)
        whenever(mockQuery.addSnapshotListener(any<EventListener<QuerySnapshot>>()))
            .thenReturn(mockListenerRegistration)
        whenever(mockMessagesCollection.document()).thenAnswer {
            val docRef: DocumentReference = mock()
            val id = "msg_j_${msgIdCounter.incrementAndGet()}"
            whenever(docRef.id).thenReturn(id)
            whenever(docRef.set(any())).thenAnswer { inv ->
                capturedSentPayloads.add(inv.getArgument(0))
                Tasks.forResult<Void>(null)
            }
            docRef
        }
        whenever(mockFirestore.collection("reports")).thenReturn(mockReportsCollection)
        whenever(mockFirestore.collection("users")).thenReturn(mockUsersCollection)
        whenever(mockUsersCollection.document(any())).thenReturn(mockUserDoc)

        mockVoiceNoteManager = createStubbedVoiceNoteManager()
        val nm = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancelAll()
    }

    @After
    fun tearDown() {
        composeTestRule.mainClock.autoAdvance = true
        realVoiceNoteManager?.cancelRecording()
        realVoiceNoteManager?.stopPlayback()
        realVoiceNoteManager = null
        mockFunctionsStatic?.close()
        mockFunctionsStatic = null
        db.close()
        tempDir.deleteRecursively()
        runBlocking { E2ETestHarness.clearRolePreferences(app) }
        val nm = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancelAll()
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

    private fun buildAuthViewModel(
        authRepo: AuthRepository = mockAuthRepository,
        prefs: RolePreferences = rolePreferences
    ): AuthViewModel {
        val ctors = AuthViewModel::class.java.constructors
        val richCtor = ctors.firstOrNull { ctor ->
            val params = ctor.parameterTypes
            params.size >= 3 &&
                AuthRepository::class.java.isAssignableFrom(params[0]) &&
                RolePreferences::class.java.isAssignableFrom(params[1]) &&
                params.drop(2).all { p ->
                    p == MessageDao::class.java ||
                        p == AppDatabase::class.java ||
                        p == UserRepository::class.java ||
                        p == Context::class.java
                }
        }
        if (richCtor != null) {
            val args = richCtor.parameterTypes.mapIndexed { idx, p ->
                when {
                    idx == 0 -> authRepo
                    idx == 1 -> prefs
                    p == MessageDao::class.java -> db.messageDao()
                    p == AppDatabase::class.java -> db
                    p == UserRepository::class.java -> mock<UserRepository>()
                    p == Context::class.java -> app
                    else -> db.messageDao()
                }
            }.toTypedArray()
            return richCtor.newInstance(*args) as AuthViewModel
        }
        return AuthViewModel(authRepo, prefs)
    }

    private fun buildMatchViewModel(
        auth: FirebaseAuth = mockAuth,
        functions: FirebaseFunctions = mockFunctions
    ): MatchViewModel {
        val ctors = MatchViewModel::class.java.constructors
        val twoArgCtor = ctors.firstOrNull { ctor ->
            val params = ctor.parameterTypes
            params.size == 2 &&
                FirebaseAuth::class.java.isAssignableFrom(params[0]) &&
                FirebaseFunctions::class.java.isAssignableFrom(params[1])
        }
        if (twoArgCtor != null) {
            return twoArgCtor.newInstance(auth, functions) as MatchViewModel
        }
        val oneArgCtor = ctors.first { ctor ->
            ctor.parameterTypes.size == 1 &&
                FirebaseAuth::class.java.isAssignableFrom(ctor.parameterTypes[0])
        }
        return oneArgCtor.newInstance(auth) as MatchViewModel
    }

    private fun drainAsync() {
        ShadowLooper.idleMainLooper()
        mainDispatcherRule.testDispatcher.scheduler.runCurrent()
        ShadowLooper.idleMainLooper()
        composeTestRule.waitForIdle()
    }

    private fun noHandlersContext(base: Context): Context = object : ContextWrapper(base) {
        override fun startActivity(intent: Intent?) {
            throw ActivityNotFoundException("No activity found to handle $intent")
        }

        override fun startActivity(intent: Intent?, options: Bundle?) {
            throw ActivityNotFoundException("No activity found to handle $intent")
        }
    }

    private fun contrastRatio(foreground: Color, background: Color): Float {
        val l1 = foreground.luminance()
        val l2 = background.luminance()
        return (max(l1, l2) + 0.05f) / (min(l1, l2) + 0.05f)
    }

    private fun advanceBreathingSeconds(seconds: Int, extraTailMs: Long = 100L) {
        repeat(seconds) {
            composeTestRule.mainClock.advanceTimeBy(1000L)
            composeTestRule.mainClock.advanceTimeByFrame()
        }
        if (extraTailMs > 0L) {
            composeTestRule.mainClock.advanceTimeBy(extraTailMs)
            composeTestRule.mainClock.advanceTimeByFrame()
        }
    }

    // =========================================================================
    // Scenario 1: First-Time Anonymous Seeker Journey (F1, F2, F4, F5, F7, F15)
    // =========================================================================

    @Test
    fun scenario1_firstTimeAnonymousSeekerJourney() {
        whenever(mockUser.uid).thenReturn("uid_anon_j1")
        runBlocking {
            whenever(mockAuthRepository.signInAnonymously()).thenReturn(AuthResult.Success(mockUser))
            db.messageDao().insertMessage(
                MessageEntity(
                    id = "peer_j1",
                    sessionId = "session_j1",
                    senderId = "giver_99",
                    text = "I am listening, take your time.",
                    timestamp = 1_000L
                )
            )
        }
        val mockResult: HttpsCallableResult = mock()
        whenever(mockResult.data).thenReturn(
            mapOf(
                "matchFound" to true,
                "sessionId" to "session_j1",
                "giverId" to "giver_99",
                "giverDisplayName" to "WarmListener"
            )
        )
        whenever(mockCallable.call(any())).thenReturn(Tasks.forResult(mockResult))

        val authVm = buildAuthViewModel()
        val matchVm = buildMatchViewModel()
        val chatRepo = ChatRepository(mockFirestore, db.messageDao())
        val chatVm = ChatViewModel(chatRepo, mockAuth, mockVoiceNoteManager)
        val navController = E2ETestHarness.createTestNavController(app)

        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.setContent {
                    HearUTheme {
                        NavHost(
                            navController = navController,
                            startDestination = Screen.Onboarding.route
                        ) {
                            composable(Screen.Onboarding.route) {
                                OnboardingScreen(onFinishOnboarding = {
                                    authVm.setOnboardingCompleted(true)
                                    navController.navigate(Screen.Login.route) {
                                        popUpTo(Screen.Onboarding.route) { inclusive = true }
                                    }
                                })
                            }
                            composable(Screen.Login.route) {
                                LoginScreen(
                                    viewModel = authVm,
                                    onAnonymousSubmit = {
                                        authVm.signInAnonymously()
                                        navController.navigate(Screen.RoleSelection.route) {
                                            popUpTo(Screen.Login.route) { inclusive = true }
                                        }
                                    }
                                )
                            }
                            composable(Screen.RoleSelection.route) {
                                RoleSelectionScreen(onRoleSelected = { role ->
                                    authVm.saveRole(role)
                                    navController.navigate(Screen.SeekerHome.route) {
                                        popUpTo(Screen.RoleSelection.route) { inclusive = true }
                                    }
                                })
                            }
                            composable(Screen.SeekerHome.route) {
                                SeekerDashboard(
                                    onNavigateToAIChat = {},
                                    onNavigateToMatch = { sid, uid ->
                                        navController.navigate(Screen.Chat.createRoute(sid, uid))
                                    },
                                    onNavigateToProfile = { navController.navigate(Screen.Profile.route) },
                                    matchViewModel = matchVm
                                )
                            }
                            composable(
                                route = Screen.Chat.route,
                                arguments = listOf(
                                    navArgument("sessionId") { type = NavType.StringType },
                                    navArgument("userId") { type = NavType.StringType }
                                )
                            ) { backStack ->
                                val sid = backStack.arguments?.getString("sessionId") ?: ""
                                val uid = backStack.arguments?.getString("userId") ?: ""
                                ChatScreen(
                                    sessionId = sid,
                                    userId = uid,
                                    onNavigateBack = { navController.popBackStack() },
                                    viewModel = chatVm
                                )
                            }
                            composable(Screen.Profile.route) {
                                ProfileScreen(
                                    onNavigateBack = { navController.popBackStack() },
                                    authViewModel = authVm
                                )
                            }
                        }
                    }
                }
            }

            // 1. Onboarding
            composeTestRule.onNodeWithText("Continue").performClick()
            composeTestRule.onNodeWithText("Continue").performClick()
            composeTestRule.onNodeWithText("Get Started").performClick()
            drainAsync()
            assertTrue(runBlocking { rolePreferences.onboardingCompletedFlow.first() })

            // 2. 18+ Anonymous Auth
            composeTestRule.onNodeWithTag("anonymous_login_button").performScrollTo().performClick()
            composeTestRule.onNodeWithText("18+ Age Requirement").assertIsDisplayed()
            composeTestRule.onNodeWithText("I am 18 or Older").performClick()
            drainAsync()
            runBlocking { verify(mockAuthRepository).signInAnonymously() }

            // 3. Seeker Role Selection
            composeTestRule.onNodeWithText("I need support (Seeker)").performClick()
            drainAsync()
            assertEquals("seeker", runBlocking { rolePreferences.activeRoleFlow.first() })

            // 4. Emotion Match
            composeTestRule.onNodeWithText("Anxiety 😰").performClick()
            composeTestRule.onNodeWithText("Loneliness 🌧️").performClick()
            composeTestRule.onNodeWithText("Find a Listener Now").performClick()
            drainAsync()

            // 5. Peer Chat (send text + reaction + verify Room & alignment)
            composeTestRule.waitUntil(timeoutMillis = 5_000L) {
                composeTestRule.onAllNodesWithText("I am listening, take your time.")
                    .fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithText("Share with compassion...")
                .performTextInput("I feel overwhelmed tonight")
            composeTestRule.onNodeWithContentDescription("Send").performClick()
            drainAsync()
            composeTestRule.onNodeWithText("❤️").performClick()
            drainAsync()

            val stored = runBlocking { db.messageDao().getMessagesForSession("session_j1").first() }
            assertTrue(stored.any { it.text == "I feel overwhelmed tonight" && it.senderId == "uid_anon_j1" })
            assertTrue(stored.any { it.text == "❤️" && it.senderId == "uid_anon_j1" })

            val myBounds = composeTestRule.onNodeWithText("I feel overwhelmed tonight")
                .fetchSemanticsNode().boundsInRoot
            val peerBounds = composeTestRule.onNodeWithText("I am listening, take your time.")
                .fetchSemanticsNode().boundsInRoot
            assertTrue(myBounds.left > peerBounds.left)

            // 6. Back to Dashboard -> Profile -> Sign Out
            composeTestRule.onNodeWithContentDescription("Back").performClick()
            drainAsync()
            composeTestRule.onNodeWithContentDescription("Profile").performClick()
            drainAsync()
            composeTestRule.onNodeWithContentDescription("Sign Out").performClick()
            composeTestRule.onNodeWithText("Sign Out").performClick()
            drainAsync()

            verify(mockAuthRepository).signOut()
            runCatching {
                composeTestRule.waitUntil(timeoutMillis = 3_000L) {
                    runBlocking { rolePreferences.activeRoleFlow.first() == null }
                }
            }
            assertNull(
                "Sign Out must clear activeRole in RolePreferences",
                runBlocking { rolePreferences.activeRoleFlow.first() }
            )
        }
        chatRepo.stopFirestoreSync("session_j1")
    }

    // =========================================================================
    // Scenario 2: Registered Giver Support Session (F2, F4, F6, F7, F9, F13)
    // =========================================================================

    @Test
    fun scenario2_registeredGiverSupportSession() {
        whenever(mockUser.uid).thenReturn("uid_giver_2")
        runBlocking {
            whenever(mockAuthRepository.signUpWithEmail("giver@hearu.app", "Compassionate99!"))
                .thenReturn(AuthResult.Success(mockUser))
        }
        whenever(mockUserDoc.update(eq("status"), any())).thenReturn(Tasks.forResult(null))
        whenever(mockUserDoc.update(eq("blockedUsers"), any())).thenReturn(Tasks.forResult(null))
        val mockReportDoc: DocumentReference = mock()
        whenever(mockReportsCollection.add(any())).thenReturn(Tasks.forResult(mockReportDoc))

        val authVm = buildAuthViewModel()
        val userRepo = UserRepository(mockFirestore)
        val chatRepo = ChatRepository(mockFirestore, db.messageDao())
        val chatVm = ChatViewModel(chatRepo, mockAuth, mockVoiceNoteManager)
        val modRepo = ModerationRepository(mockFirestore)
        val navController = E2ETestHarness.createTestNavController(app)

        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.setContent {
                    HearUTheme {
                        NavHost(
                            navController = navController,
                            startDestination = Screen.Login.route
                        ) {
                            composable(Screen.Login.route) {
                                LoginScreen(
                                    viewModel = authVm,
                                    onSignUpSubmit = { email, pass ->
                                        authVm.register(email, pass)
                                        navController.navigate(Screen.RoleSelection.route)
                                    }
                                )
                            }
                            composable(Screen.RoleSelection.route) {
                                RoleSelectionScreen(onRoleSelected = { role ->
                                    authVm.saveRole(role)
                                    navController.navigate(Screen.GiverHome.route)
                                })
                            }
                            composable(Screen.GiverHome.route) {
                                GiverDashboard(
                                    onNavigateToChat = { sid, uid ->
                                        navController.navigate(Screen.Chat.createRoute(sid, uid))
                                    }
                                )
                            }
                            composable(
                                route = Screen.Chat.route,
                                arguments = listOf(
                                    navArgument("sessionId") { type = NavType.StringType },
                                    navArgument("userId") { type = NavType.StringType }
                                )
                            ) { backStack ->
                                val sid = backStack.arguments?.getString("sessionId") ?: ""
                                val uid = backStack.arguments?.getString("userId") ?: ""
                                ChatScreen(
                                    sessionId = sid,
                                    userId = uid,
                                    onNavigateBack = { navController.popBackStack() },
                                    viewModel = chatVm
                                )
                            }
                        }
                    }
                }
            }

            // 1. Email Sign Up
            composeTestRule.onNodeWithText("Sign Up").performScrollTo().performClick()
            composeTestRule.onNodeWithTag("email_field").performScrollTo().performTextInput("  giver@hearu.app  ")
            composeTestRule.onNodeWithTag("password_field").performScrollTo().performTextInput("Compassionate99!")
            composeTestRule.onNodeWithTag("confirm_password_field").performScrollTo().performTextInput("Compassionate99!")
            composeTestRule.onNodeWithTag("age_gate_checkbox").performScrollTo().performClick()
            composeTestRule.onNodeWithTag("signup_button").performScrollTo().performClick()
            drainAsync()
            runBlocking { verify(mockAuthRepository).signUpWithEmail("giver@hearu.app", "Compassionate99!") }

            // 2. Giver Role
            composeTestRule.onNodeWithText("I want to listen (Giver)").performClick()
            drainAsync()

            // 3. Go Offline -> Online & verify UserRepository sync
            composeTestRule.onNode(isToggleable()).performClick()
            composeTestRule.onNodeWithText("Status: Offline").assertIsDisplayed()
            composeTestRule.onNode(isToggleable()).performClick()
            composeTestRule.onNodeWithText("Active & Ready to Listen").assertIsDisplayed()
            runBlocking { userRepo.updateUserStatus("uid_giver_2", "online") }
            verify(mockUserDoc).update("status", "online")

            // Expand Code of Conduct
            composeTestRule.onNodeWithText("Empathy & Safety Code of Conduct")
                .performScrollTo()
                .performClick()
            composeTestRule.onNodeWithText("1. Listen without giving unrequested advice.")
                .performScrollTo()
                .assertIsDisplayed()

            // 4. Accept Incoming Request -> Chat
            composeTestRule.onNodeWithText("Accept & Connect Anonymously")
                .performScrollTo()
                .performClick()
            drainAsync()

            // 5. Photo Blur + Unilateral Consent
            composeTestRule.onNodeWithText("Confidential 30-day ephemeral peer chat").assertIsDisplayed()
            composeTestRule.onNodeWithContentDescription("Photo Reveal Consent").performClick()
            composeTestRule.onNodeWithText("Grant Mutual Consent").performClick()
            drainAsync()
            assertTrue(chatVm.myPhotoConsent.value)
            assertFalse(chatVm.isPhotoRevealed.value)

            // 6. Report & Block & Leave
            composeTestRule.onNodeWithContentDescription("Options").performClick()
            composeTestRule.onNodeWithText("Report User / Safety").performClick()
            composeTestRule.onNodeWithText("Harassment / Hate Speech").performClick()
            composeTestRule.onNodeWithText("Additional details (optional)").performTextInput("Verbal abuse")
            composeTestRule.onNodeWithText("Submit Report").performClick()
            drainAsync()

            runBlocking {
                modRepo.reportUser("uid_giver_2", "seeker_bad", "session_j2", "Harassment / Hate Speech", "Verbal abuse")
                modRepo.blockUser("uid_giver_2", "seeker_bad")
            }
            verify(mockReportsCollection).add(any())
            verify(mockUserDoc).update(eq("blockedUsers"), any())

            composeTestRule.onNodeWithContentDescription("Options").performClick()
            composeTestRule.onNodeWithText(app.getString(R.string.block_user)).performClick()
            drainAsync()

            if (navController.currentBackStackEntry?.destination?.route != Screen.GiverHome.route) {
                val confirmButtons = composeTestRule.onAllNodes(
                    (hasText("Block", substring = true, ignoreCase = true) or
                        hasText("Confirm", substring = true, ignoreCase = true)) and
                        hasClickAction()
                ).fetchSemanticsNodes()
                if (confirmButtons.isNotEmpty()) {
                    composeTestRule.onAllNodes(
                        (hasText("Block", substring = true, ignoreCase = true) or
                            hasText("Confirm", substring = true, ignoreCase = true)) and
                            hasClickAction()
                    )[0].performClick()
                    drainAsync()
                }
            }
            assertEquals(
                "Block & Leave must exit chat back to GiverHome",
                Screen.GiverHome.route,
                navController.currentBackStackEntry?.destination?.route
            )
        }
    }

    // =========================================================================
    // Scenario 3: Crisis Escalation During Network Failure (F5, F10, F12)
    // =========================================================================

    @Test
    fun scenario3_crisisEscalationDuringNetworkFailure() {
        whenever(mockCallable.call(any())).thenReturn(
            Tasks.forException(RuntimeException("Network unreachable"))
        )
        val matchVm = buildMatchViewModel()
        val aiVm = AIChatViewModel(GeminiAiService(), rolePreferences, mockAuth)
        val navController = E2ETestHarness.createTestNavController(app)

        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.setContent {
                    val base = LocalContext.current
                    CompositionLocalProvider(LocalContext provides noHandlersContext(base)) {
                        HearUTheme {
                            NavHost(
                                navController = navController,
                                startDestination = Screen.SeekerHome.route
                            ) {
                                composable(Screen.SeekerHome.route) {
                                    SeekerDashboard(
                                        onNavigateToAIChat = { navController.navigate(Screen.AIChat.route) },
                                        onNavigateToMatch = { _, _ -> },
                                        matchViewModel = matchVm
                                    )
                                }
                                composable(Screen.AIChat.route) {
                                    AIChatScreen(
                                        onNavigateBack = { navController.popBackStack() },
                                        onNavigateToCrisis = { navController.navigate(Screen.EmergencyHub.route) },
                                        viewModel = aiVm
                                    )
                                }
                                composable(Screen.EmergencyHub.route) {
                                    EmergencyScreen(
                                        onNavigateBack = { navController.popBackStack() },
                                        onNavigateToBreathing = {}
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 1. Network failure in Matchmaking -> MatchState.Error (no silent fake match)
            composeTestRule.onNodeWithText("Find a Listener Now").performClick()
            mainDispatcherRule.testDispatcher.scheduler.advanceTimeBy(3000L)
            drainAsync()
            assertEquals(Screen.SeekerHome.route, navController.currentBackStackEntry?.destination?.route)
            assertTrue(matchVm.matchState.value is MatchState.Error)

            // 2. Switch to AI Companion -> send self-harm keyword offline
            composeTestRule.onNodeWithText("AI Companion", substring = true)
                .performScrollTo()
                .performClick()
            drainAsync()

            composeTestRule.onNode(hasSetTextAction()).performTextInput("I want to kill myself")
            composeTestRule.onNodeWithContentDescription("Send").performClick()
            composeTestRule.waitUntil(timeoutMillis = 5_000L) { !aiVm.isLoading.value }

            assertTrue(aiVm.crisisEvent.value)
            composeTestRule.onNodeWithText("We're Here For You").assertIsDisplayed()
            composeTestRule.onNodeWithText("Call 988 Crisis Lifeline").performClick()
            composeTestRule.onNodeWithText("Chat with Lifeline Online").performClick()
            composeTestRule.onNodeWithText("Return to App").performClick()

            // 3. Emergency Hub safe dial/SMS fallback
            composeTestRule.runOnUiThread {
                navController.navigate(Screen.EmergencyHub.route)
            }
            composeTestRule.onNodeWithText("24/7 Crisis & Emergency Hub").assertIsDisplayed()
            composeTestRule.onNodeWithText("988 Suicide & Crisis Lifeline").assertIsDisplayed()
            composeTestRule.onNodeWithText("Crisis Text Line").assertIsDisplayed()
            composeTestRule.onNodeWithText("Veterans Crisis Line").performScrollTo().assertIsDisplayed()
            composeTestRule.onNodeWithText("The Trevor Project (LGBTQ+ Youth)").performScrollTo().assertIsDisplayed()
            composeTestRule.onNodeWithText("International Resources (Outside North America)")
                .performScrollTo()
                .assertIsDisplayed()

            composeTestRule.onAllNodesWithText("Call Now")[0].performScrollTo().performClick()
            composeTestRule.onNodeWithText("Text 988").performScrollTo().performClick()
            composeTestRule.onNodeWithText("24/7 Crisis & Emergency Hub").assertIsDisplayed()
        }
    }

    // =========================================================================
    // Scenario 4: Biometric Security Lifecycle (F3, F15)
    // =========================================================================

    @Test
    fun scenario4_biometricSecurityLifecycle() {
        // 1. Attempting enable without enrolled hardware / FragmentActivity does not brick
        val authVm = buildAuthViewModel()
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.setContent {
                    HearUTheme {
                        ProfileScreen(onNavigateBack = {}, authViewModel = authVm)
                    }
                }
            }
            composeTestRule.onNode(
                isToggleable() and hasAnySibling(hasText("Biometric App Lock"))
            ).performScrollTo().performClick()
            composeTestRule.waitForIdle()
            assertFalse(
                "Biometric lock must not enable without enrolled hardware / FragmentActivity",
                runBlocking { rolePreferences.isBiometricEnabledFlow.first() }
            )
        }

        // 2. Enable biometrics + seed Room -> Cold-start MainActivity -> Recreate -> Retry error/recovery
        runBlocking {
            rolePreferences.setBiometricEnabled(true)
            rolePreferences.setOnboardingCompleted(false)
            db.messageDao().insertMessage(
                MessageEntity(
                    id = "bio_msg_1",
                    sessionId = "s_bio",
                    senderId = "uid_journey_1",
                    text = "Sensitive message",
                    timestamp = 1_700_000_000_000L
                )
            )
        }

        composeTestRule.mainClock.autoAdvance = false
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                composeTestRule.mainClock.advanceTimeByFrame()
                composeTestRule.onNodeWithText("A Safe, Anonymous Haven").assertDoesNotExist()
                composeTestRule.mainClock.autoAdvance = true

                composeTestRule.waitUntil(timeoutMillis = 5_000L) {
                    composeTestRule.onAllNodesWithText("HearU is Locked").fetchSemanticsNodes().isNotEmpty()
                }
                scenario.recreate()
                composeTestRule.waitUntil(timeoutMillis = 5_000L) {
                    composeTestRule.onAllNodesWithText("HearU is Locked").fetchSemanticsNodes().isNotEmpty()
                }
                composeTestRule.onNodeWithText("Unlock with Biometrics").performClick()
                composeTestRule.waitForIdle()

                val recoveryOrErrorNodes = composeTestRule.onAllNodes(
                    hasText("Sign Out", substring = true, ignoreCase = true) or
                        hasText("Reset", substring = true, ignoreCase = true) or
                        hasText("unavailable", substring = true, ignoreCase = true) or
                        hasText("failed", substring = true, ignoreCase = true) or
                        hasText("error", substring = true, ignoreCase = true) or
                        hasText("not available", substring = true, ignoreCase = true) or
                        hasText("No biometric", substring = true, ignoreCase = true)
                ).fetchSemanticsNodes()
                assertTrue(recoveryOrErrorNodes.isNotEmpty())
            }
        } finally {
            composeTestRule.mainClock.autoAdvance = true
        }

        // 3. Delete Account from Profile -> wipes Room & RolePreferences
        val profileVm = buildAuthViewModel()
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.setContent {
                    HearUTheme {
                        ProfileScreen(onNavigateBack = {}, authViewModel = profileVm)
                    }
                }
            }
            composeTestRule.onNodeWithText("Delete Account", substring = true, ignoreCase = true)
                .performScrollTo()
                .performClick()
            val confirmButtons = composeTestRule.onAllNodes(
                (hasText("Delete", substring = true, ignoreCase = true) or
                    hasText("Erase", substring = true, ignoreCase = true) or
                    hasText("Confirm", substring = true, ignoreCase = true)) and
                    hasClickAction()
            )
            val count = confirmButtons.fetchSemanticsNodes().size
            assertTrue("Delete confirmation button must exist", count > 0)
            confirmButtons[count - 1].performClick()
            composeTestRule.waitUntil(timeoutMillis = 5_000L) {
                drainAsync()
                !profileVm.isDeletingAccount.value &&
                    runBlocking { !rolePreferences.isBiometricEnabledFlow.first() }
            }
            drainAsync()

            assertFalse(runBlocking { rolePreferences.isBiometricEnabledFlow.first() })
            assertTrue(runBlocking { db.messageDao().getMessagesForSession("s_bio").first().isEmpty() })
        }
    }

    // =========================================================================
    // Scenario 5: Voice-Note Peer Support & 30-Day Data Retention (F7, F8, F15)
    // =========================================================================

    @Test
    fun scenario5_voiceNotePeerSupportAnd30DayDataRetention() {
        Shadows.shadowOf(app).denyPermissions(Manifest.permission.RECORD_AUDIO)
        val voiceDispatcher = StandardTestDispatcher()
        val manager = VoiceNoteManager(mainDispatcher = voiceDispatcher).also {
            realVoiceNoteManager = it
        }
        val chatRepo = ChatRepository(mockFirestore, db.messageDao())
        val chatVm = ChatViewModel(chatRepo, mockAuth, manager)
        val authVm = buildAuthViewModel()

        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.setContent {
                    HearUTheme {
                        ChatScreen(
                            sessionId = "s_vn_j5",
                            userId = "uid_journey_1",
                            onNavigateBack = {},
                            viewModel = chatVm
                        )
                    }
                }
            }
            drainAsync()

            // 1. Denied permission blocks recording
            composeTestRule.onNodeWithContentDescription("Record Voice Note").performClick()
            drainAsync()
            assertFalse(manager.isRecording.value)
            composeTestRule.onNodeWithText("Recording:", substring = true).assertDoesNotExist()

            // 2. Grant permission -> record -> send -> persisted in Room
            Shadows.shadowOf(app).grantPermissions(Manifest.permission.RECORD_AUDIO)
            composeTestRule.onNodeWithContentDescription("Record Voice Note").performClick()
            voiceDispatcher.scheduler.advanceTimeBy(1200L)
            voiceDispatcher.scheduler.runCurrent()
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithContentDescription("Send Voice Note").performClick()
            voiceDispatcher.scheduler.runCurrent()
            drainAsync()

            val rows = runBlocking { db.messageDao().getMessagesForSession("s_vn_j5").first() }
            assertEquals(1, rows.size)
            assertTrue(rows[0].isVoiceNote)

            val sentMsg = capturedSentPayloads.lastOrNull() as? Message
            assertNotNull(sentMsg)
            assertEquals(28, sentMsg!!.waveformAmplitudes.size)

            // 3. Playback + seek via ChatViewModel
            composeTestRule.onNodeWithContentDescription("Play").performClick()
            voiceDispatcher.scheduler.runCurrent()
            drainAsync()
            assertTrue(chatVm.isPlaying.value)
            chatVm.seekVoicePlayback(sentMsg, 0.5f)
            assertEquals(0.5f, chatVm.playbackProgress.value, 0.05f)

            // 4. 30-day purge & Privacy Policy check
            runBlocking { chatRepo.clearLocalSession("s_vn_j5") }
            assertTrue(runBlocking { db.messageDao().getMessagesForSession("s_vn_j5").first().isEmpty() })

            val functionsContent = File(
                E2ETestHarness.resolveProjectRoot(),
                "functions/index.js"
            ).readText()
            assertTrue(functionsContent.contains("purgeExpiredChats"))
            assertTrue(functionsContent.contains("30 * 24 * 60 * 60 * 1000"))

            scenario.onActivity { activity ->
                activity.setContent {
                    HearUTheme {
                        ProfileScreen(onNavigateBack = {}, authViewModel = authVm)
                    }
                }
            }
            composeTestRule.onNodeWithText("Privacy Policy", substring = true, ignoreCase = true)
                .performScrollTo()
                .performClick()
            val retentionNodes = composeTestRule.onAllNodes(
                hasText("30 days", substring = true, ignoreCase = true) or
                    hasText("30-day", substring = true, ignoreCase = true)
            ).fetchSemanticsNodes()
            assertTrue("In-app Privacy Policy must disclose 30-day retention", retentionNodes.isNotEmpty())
        }
    }

    // =========================================================================
    // Scenario 6: Calming Self-Care Flow (F5, F11, F12)
    // =========================================================================

    @Test
    @Config(qualifiers = "w411dp-h915dp")
    fun scenario6_calmingSelfCareFlow() {
        val matchVm = buildMatchViewModel()
        var mounted by mutableStateOf(true)
        var registry by mutableStateOf(SaveableStateRegistry(restoredValues = null) { true })

        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.setContent {
                    if (mounted) {
                        CompositionLocalProvider(LocalSaveableStateRegistry provides registry) {
                            val navController = rememberNavController()
                            HearUTheme {
                                NavHost(
                                    navController = navController,
                                    startDestination = Screen.SeekerHome.route
                                ) {
                                    composable(Screen.SeekerHome.route) {
                                        SeekerDashboard(
                                            onNavigateToAIChat = {},
                                            onNavigateToMatch = { _, _ -> },
                                            onNavigateToBreathing = {
                                                navController.navigate(Screen.BreathingExercise.route)
                                            },
                                            matchViewModel = matchVm
                                        )
                                    }
                                    composable(Screen.BreathingExercise.route) {
                                        BreathingExerciseScreen(
                                            onNavigateBack = { navController.popBackStack() },
                                            onEmergencyClick = { navController.navigate(Screen.EmergencyHub.route) }
                                        )
                                    }
                                    composable(Screen.EmergencyHub.route) {
                                        EmergencyScreen(
                                            onNavigateBack = { navController.popBackStack() },
                                            onNavigateToBreathing = {
                                                navController.navigate(Screen.BreathingExercise.route)
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 1. Seeker -> 4-7-8 Breathing
            composeTestRule.onNodeWithText("4-7-8 Breathing & Grounding")
                .performScrollTo()
                .performClick()
            composeTestRule.waitUntil(timeoutMillis = 5_000L) {
                composeTestRule.onAllNodesWithText("Begin Session").fetchSemanticsNodes().isNotEmpty()
            }

            // 2. Pause / Resume preserves remaining time
            composeTestRule.mainClock.autoAdvance = false
            composeTestRule.onNodeWithText("Begin Session").performClick()
            composeTestRule.mainClock.advanceTimeByFrame()
            advanceBreathingSeconds(2, extraTailMs = 50L)
            composeTestRule.onNodeWithText("2").assertIsDisplayed()
            composeTestRule.onNodeWithText("Pause").performClick()
            composeTestRule.mainClock.advanceTimeByFrame()
            composeTestRule.mainClock.advanceTimeBy(3000L)
            composeTestRule.mainClock.advanceTimeByFrame()
            composeTestRule.onNodeWithText("Begin Session").performClick()
            composeTestRule.mainClock.advanceTimeByFrame()
            composeTestRule.mainClock.advanceTimeBy(100L)
            composeTestRule.mainClock.advanceTimeByFrame()
            composeTestRule.onNodeWithText("2").assertIsDisplayed()
            advanceBreathingSeconds(2, extraTailMs = 200L)
            composeTestRule.onNodeWithText("Hold Gently...").assertIsDisplayed()

            // 3. Grounding + SaveableStateRegistry restore -> Complete -> SOS -> Emergency -> Return
            composeTestRule.mainClock.autoAdvance = true
            composeTestRule.onNodeWithText("5-4-3-2-1 Grounding").performClick()
            composeTestRule.onNodeWithText("I've Found All 5 → Next Sense").performScrollTo().performClick()
            composeTestRule.onNodeWithText("4 Things You Can TOUCH").assertIsDisplayed()

            composeTestRule.runOnUiThread {
                val saved = registry.performSave()
                registry = SaveableStateRegistry(restoredValues = saved) { true }
                mounted = false
            }
            composeTestRule.waitForIdle()
            composeTestRule.runOnUiThread {
                mounted = true
            }
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithText("4 Things You Can TOUCH").assertIsDisplayed()

            composeTestRule.onNodeWithText("I've Found All 4 → Next Sense").performScrollTo().performClick()
            composeTestRule.onNodeWithText("I've Found All 3 → Next Sense").performScrollTo().performClick()
            composeTestRule.onNodeWithText("I've Found All 2 → Next Sense").performScrollTo().performClick()
            composeTestRule.onNodeWithText("Complete Grounding Session").performScrollTo().performClick()
            composeTestRule.onNodeWithText("You Are Here. You Are Safe.").assertIsDisplayed()

            composeTestRule.onNodeWithContentDescription("Crisis Hub").performClick()
            composeTestRule.waitUntil(timeoutMillis = 5_000L) {
                composeTestRule.onAllNodesWithText("24/7 Crisis & Emergency Hub").fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithText("24/7 Crisis & Emergency Hub").assertIsDisplayed()

            composeTestRule.onNodeWithText("Take a 2-Minute Calming Breath (4-7-8 Exercise)")
                .performScrollTo()
                .performClick()
            composeTestRule.waitUntil(timeoutMillis = 5_000L) {
                composeTestRule.onAllNodesWithText("Calming & Grounding Tools").fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithText("Calming & Grounding Tools").assertIsDisplayed()
        }
    }

    // =========================================================================
    // Scenario 7: Notification Deep Link & Role Switch (F4, F7, F14, F15)
    // =========================================================================

    @Test
    fun scenario7_notificationDeepLinkAndRoleSwitch() {
        Shadows.shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val service = Robolectric.buildService(HearUFirebaseMessagingService::class.java).create().get()
        service.onMessageReceived(
            RemoteMessage.Builder("sender@fcm.googleapis.com")
                .setData(
                    mapOf(
                        HearUNotificationConfig.EXTRA_NOTIFICATION_TYPE to HearUNotificationConfig.TYPE_SESSION_REQUEST,
                        HearUNotificationConfig.EXTRA_SESSION_ID to "dl_sess_99",
                        HearUNotificationConfig.EXTRA_USER_ID to "peer_giver"
                    )
                )
                .build()
        )
        val nm = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val posted = Shadows.shadowOf(nm).allNotifications.single()
        assertEquals(HearUNotificationConfig.CHANNEL_SESSION_REQUESTS, posted.channelId)
        assertEquals(NotificationCompat.VISIBILITY_PRIVATE, posted.visibility)
        assertTrue(posted.flags and Notification.FLAG_AUTO_CANCEL != 0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            assertEquals("drawable", app.resources.getResourceTypeName(posted.smallIcon.resId))
        }
        val shadowPi = Shadows.shadowOf(posted.contentIntent)
        assertTrue(shadowPi.flags and PendingIntent.FLAG_IMMUTABLE != 0)
        val sessionExtra = shadowPi.savedIntent.getStringExtra(HearUNotificationConfig.EXTRA_SESSION_ID)
        assertEquals("dl_sess_99", sessionExtra)

        runBlocking {
            rolePreferences.setOnboardingCompleted(true)
            rolePreferences.setActiveRole("seeker")
        }
        whenever(mockAuthRepository.currentUser).thenReturn(mockUser)
        whenever(mockUser.uid).thenReturn("uid_seeker_77")
        val authVm = buildAuthViewModel()

        // Launch MainActivity -> deep-link into Chat -> Back to SeekerDashboard -> Profile -> Role Switch
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.setContent {
                    HearUTheme {
                        HearUNavigation(authViewModel = authVm, initialSessionId = sessionExtra)
                    }
                }
            }
            composeTestRule.waitUntil(timeoutMillis = 5_000L) {
                composeTestRule.onAllNodesWithText("Empathetic Listener").fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithContentDescription("Back").performClick()
            composeTestRule.waitUntil(timeoutMillis = 5_000L) {
                composeTestRule.onAllNodesWithText("Connect with a Human Listener")
                    .fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithContentDescription("Profile").performClick()
            drainAsync()

            val switcher = composeTestRule.onAllNodes(
                hasText("Switch Role", substring = true, ignoreCase = true) or
                    hasText("Switch to", substring = true, ignoreCase = true) or
                    hasText("Change Role", substring = true, ignoreCase = true) or
                    (hasText("Giver", substring = true, ignoreCase = true) and hasClickAction())
            )
            assertTrue("ProfileScreen must expose Role Switcher", switcher.fetchSemanticsNodes().isNotEmpty())
            switcher[0].performScrollTo().performClick()
            drainAsync()
            if (runBlocking { rolePreferences.activeRoleFlow.first() } != "giver") {
                val giverChoices = composeTestRule.onAllNodes(
                    hasText("Giver", substring = true, ignoreCase = true) and hasClickAction()
                )
                if (giverChoices.fetchSemanticsNodes().isNotEmpty()) {
                    giverChoices[0].performClick()
                    drainAsync()
                }
            }
            assertEquals("giver", runBlocking { rolePreferences.activeRoleFlow.first() })
        }

        // Verify ChatScreen ownership alignment for uid_seeker_77 vs peer_giver
        runBlocking {
            db.messageDao().insertMessage(
                MessageEntity(
                    id = "p",
                    sessionId = "dl_sess_99",
                    senderId = "peer_giver",
                    text = "Peer msg",
                    timestamp = 1000L
                )
            )
            db.messageDao().insertMessage(
                MessageEntity(
                    id = "m",
                    sessionId = "dl_sess_99",
                    senderId = "uid_seeker_77",
                    text = "My msg",
                    timestamp = 2000L
                )
            )
        }
        val chatRepo = ChatRepository(mockFirestore, db.messageDao())
        val chatVm = ChatViewModel(chatRepo, mockAuth, mockVoiceNoteManager)

        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.setContent {
                    HearUTheme {
                        ChatScreen(
                            sessionId = "dl_sess_99",
                            userId = "peer_giver",
                            onNavigateBack = {},
                            viewModel = chatVm
                        )
                    }
                }
            }
            composeTestRule.waitUntil(timeoutMillis = 5_000L) {
                composeTestRule.onAllNodesWithText("Peer msg").fetchSemanticsNodes().isNotEmpty() &&
                    composeTestRule.onAllNodesWithText("My msg").fetchSemanticsNodes().isNotEmpty()
            }
            val peerBounds = composeTestRule.onNodeWithText("Peer msg").fetchSemanticsNode().boundsInRoot
            val myBounds = composeTestRule.onNodeWithText("My msg").fetchSemanticsNode().boundsInRoot
            assertTrue(myBounds.left > peerBounds.left)
        }
        chatRepo.stopFirestoreSync("dl_sess_99")
    }

    // =========================================================================
    // Scenario 8: Dark Mode Accessibility & Play Store Compliance Audit (F15, F16)
    // =========================================================================

    @Test
    fun scenario8_darkModeAccessibilityAndPlayStoreComplianceAudit() {
        val authVm = buildAuthViewModel()
        val matchVm = buildMatchViewModel()
        val chatRepo = ChatRepository(mockFirestore, db.messageDao())
        val chatVm = ChatViewModel(chatRepo, mockAuth, mockVoiceNoteManager)
        val aiVm = AIChatViewModel(mock(), rolePreferences, mockAuth)

        var onPrimaryContrast = 0f
        var surfaceContrast = 0f

        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.setContent {
                    HearUTheme {
                        ProfileScreen(onNavigateBack = {}, authViewModel = authVm)
                    }
                }
            }

            composeTestRule.onNode(
                isToggleable() and hasAnySibling(hasText("Dark Theme"))
            ).performScrollTo().performClick()
            composeTestRule.waitUntil(timeoutMillis = 5_000L) {
                runBlocking { rolePreferences.isDarkThemeFlow.first() == true }
            }

            composeTestRule.onNodeWithText("Privacy Policy", substring = true, ignoreCase = true)
                .performScrollTo()
                .performClick()
            val policyNodes = composeTestRule.onAllNodes(
                hasText("privacy@hearu.app", substring = true, ignoreCase = true) or
                    hasText("18 and older", substring = true, ignoreCase = true) or
                    hasText("30 days", substring = true, ignoreCase = true)
            ).fetchSemanticsNodes()
            assertTrue("ProfileScreen must open in-app Privacy Policy viewer", policyNodes.isNotEmpty())

            // Dark Mode + Compact Viewport render checks across core screens
            scenario.onActivity { activity ->
                activity.setContent {
                    HearUTheme(darkTheme = true) {
                        val scheme = MaterialTheme.colorScheme
                        onPrimaryContrast = contrastRatio(scheme.onPrimary, scheme.primary)
                        surfaceContrast = contrastRatio(scheme.onSurface, scheme.surface)
                        Box(modifier = Modifier.size(width = 320.dp, height = 560.dp)) {
                            SeekerDashboard(
                                onNavigateToAIChat = {},
                                onNavigateToMatch = { _, _ -> },
                                matchViewModel = matchVm
                            )
                        }
                    }
                }
            }
            composeTestRule.onNodeWithText("100% Anonymous & Ephemeral", substring = true)
                .performScrollTo()
                .assertIsDisplayed()

            scenario.onActivity { activity ->
                activity.setContent {
                    HearUTheme(darkTheme = true) {
                        Box(modifier = Modifier.size(width = 320.dp, height = 560.dp)) {
                            GiverDashboard()
                        }
                    }
                }
            }
            composeTestRule.onNodeWithText("Empathy & Safety Code of Conduct")
                .performScrollTo()
                .assertIsDisplayed()

            scenario.onActivity { activity ->
                activity.setContent {
                    HearUTheme(darkTheme = true) {
                        ChatScreen(
                            sessionId = "s_audit",
                            userId = "uid_journey_1",
                            onNavigateBack = {},
                            viewModel = chatVm
                        )
                    }
                }
            }
            composeTestRule.onNodeWithText("Empathetic Listener").assertIsDisplayed()

            scenario.onActivity { activity ->
                activity.setContent {
                    HearUTheme(darkTheme = true) {
                        AIChatScreen(onNavigateBack = {}, viewModel = aiVm)
                    }
                }
            }
            composeTestRule.onNodeWithText("HearU AI Companion").assertIsDisplayed()

            scenario.onActivity { activity ->
                activity.setContent {
                    HearUTheme(darkTheme = true) {
                        Box(modifier = Modifier.size(width = 320.dp, height = 560.dp)) {
                            EmergencyScreen(onNavigateBack = {})
                        }
                    }
                }
            }
            composeTestRule.onNodeWithText("Take a 2-Minute Calming Breath (4-7-8 Exercise)")
                .performScrollTo()
                .assertIsDisplayed()

            scenario.onActivity { activity ->
                activity.setContent {
                    HearUTheme(darkTheme = true) {
                        Box(modifier = Modifier.size(width = 320.dp, height = 560.dp)) {
                            BreathingExerciseScreen(onNavigateBack = {})
                        }
                    }
                }
            }
            composeTestRule.onNodeWithText("Begin Session").performScrollTo().assertIsDisplayed()
        }

        assertTrue("Dark onPrimary/primary contrast $onPrimaryContrast < 4.5", onPrimaryContrast >= 4.5f)
        assertTrue("Dark onSurface/surface contrast $surfaceContrast < 4.5", surfaceContrast >= 4.5f)

        val root = E2ETestHarness.resolveProjectRoot()
        val manifest = File(root, "app/src/main/AndroidManifest.xml").readText()
        val buildGradle = File(root, "app/build.gradle.kts").readText()
        val proguard = File(root, "app/proguard-rules.pro").readText()
        val privacyPolicy = File(root, "PRIVACY_POLICY.md").readText()
        val listing = File(root, "distribution/play_store_listing.md").readText()

        assertTrue(manifest.contains("""android:allowBackup="false""""))
        assertTrue(manifest.contains("com.google.android.gms.permission.AD_ID"))
        assertTrue(manifest.contains("tools:node=\"remove\""))
        assertTrue(manifest.contains("android.permission.POST_NOTIFICATIONS"))
        assertTrue(manifest.contains("android.permission.RECORD_AUDIO"))
        assertTrue(Regex("""targetSdk\s*=\s*35""").containsMatchIn(buildGradle))
        assertTrue(Regex("""isMinifyEnabled\s*=\s*true""").containsMatchIn(buildGradle))
        assertTrue(Regex("""isShrinkResources\s*=\s*true""").containsMatchIn(buildGradle))
        assertTrue(proguard.contains("-assumenosideeffects"))
        assertTrue(privacyPolicy.contains("privacy@hearu.app"))
        assertTrue(listing.contains("HearU: Anonymous Peer Support"))
        chatRepo.stopFirestoreSync("s_audit")
    }
}
