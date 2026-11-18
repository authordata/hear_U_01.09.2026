package com.hearu.app.e2e.tier3

import android.Manifest
import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOn
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
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
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
import com.hearu.app.ai.AiResult
import com.hearu.app.ai.GeminiAiService
import com.hearu.app.audio.VoiceNoteManager
import com.hearu.app.data.RolePreferences
import com.hearu.app.data.dataStore
import com.hearu.app.data.local.AppDatabase
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
import org.mockito.kotlin.argumentCaptor
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
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicInteger
import kotlin.io.path.createTempDirectory
import kotlin.math.max
import kotlin.math.min

/**
 * Tier 3: Cross-feature pairwise combinatorial E2E tests (18 tests) covering
 * interactions across F1..F16 in TEST_INFRA.md.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CrossFeaturePairwiseE2ETest {

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
        tempDir = createTempDirectory("hearu_tier3_e2e").toFile()
        db = E2ETestHarness.createInMemoryDb(app)
        rolePreferences = E2ETestHarness.createRolePreferences(app)

        mockAuthRepository = mock()
        mockAuth = mock()
        mockUser = mock()
        whenever(mockAuthRepository.currentUser).thenReturn(null)
        whenever(mockAuth.currentUser).thenReturn(mockUser)
        whenever(mockUser.uid).thenReturn("uid_default_1")

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
            val id = "msg_t3_${msgIdCounter.incrementAndGet()}"
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
                    p.isAssignableFrom(db.messageDao()::class.java) ||
                        p == com.hearu.app.data.local.dao.MessageDao::class.java ||
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
                    p == com.hearu.app.data.local.dao.MessageDao::class.java -> db.messageDao()
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

    private fun todayStartEpoch(): Long {
        return LocalDate.now(ZoneId.systemDefault())
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
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
    // T3-01: F1 x F2 x F4
    // =========================================================================

    @Test
    fun T3_01_pairwise_onboardingToSignUpWith18PlusAndRoleSelection_persistsAllFlags() {
        runBlocking {
            whenever(mockAuthRepository.signUpWithEmail("new@hearu.app", "StrongPass99!"))
                .thenReturn(AuthResult.Success(mockUser))
        }
        val authVm = buildAuthViewModel(mockAuthRepository, rolePreferences)
        val matchVm = buildMatchViewModel()
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
                                OnboardingScreen(
                                    onFinishOnboarding = {
                                        authVm.setOnboardingCompleted(true)
                                        navController.navigate(Screen.Login.route) {
                                            popUpTo(Screen.Onboarding.route) { inclusive = true }
                                        }
                                    }
                                )
                            }
                            composable(Screen.Login.route) {
                                LoginScreen(
                                    viewModel = authVm,
                                    onSignUpSubmit = { email, pass ->
                                        authVm.register(email, pass)
                                        navController.navigate(Screen.RoleSelection.route) {
                                            popUpTo(Screen.Login.route) { inclusive = true }
                                        }
                                    }
                                )
                            }
                            composable(Screen.RoleSelection.route) {
                                RoleSelectionScreen(onRoleSelected = { role ->
                                    authVm.saveRole(role)
                                    val dest = if (role == "seeker") Screen.SeekerHome.route else Screen.GiverHome.route
                                    navController.navigate(dest) {
                                        popUpTo(Screen.RoleSelection.route) { inclusive = true }
                                    }
                                })
                            }
                            composable(Screen.SeekerHome.route) {
                                SeekerDashboard(
                                    onNavigateToAIChat = {},
                                    onNavigateToMatch = { _, _ -> },
                                    matchViewModel = matchVm
                                )
                            }
                            composable(Screen.GiverHome.route) {
                                GiverDashboard()
                            }
                        }
                    }
                }
            }

            composeTestRule.onNodeWithText("Continue").performClick()
            composeTestRule.onNodeWithText("Continue").performClick()
            composeTestRule.onNodeWithText("Get Started").performClick()
            drainAsync()

            composeTestRule.onNodeWithText("Sign Up").performScrollTo().performClick()
            composeTestRule.onNodeWithTag("email_field").performScrollTo().performTextInput("  new@hearu.app  ")
            composeTestRule.onNodeWithTag("password_field").performScrollTo().performTextInput("StrongPass99!")
            composeTestRule.onNodeWithTag("confirm_password_field").performScrollTo().performTextInput("StrongPass99!")
            composeTestRule.onNodeWithTag("age_gate_checkbox").performScrollTo().performClick()
            composeTestRule.onNodeWithTag("age_gate_checkbox").assertIsOn()
            composeTestRule.onNodeWithTag("signup_button").performScrollTo().performClick()
            drainAsync()

            composeTestRule.onNodeWithText("I need support (Seeker)").performClick()
            drainAsync()

            composeTestRule.onNodeWithText("Connect with a Human Listener").assertIsDisplayed()
            assertTrue(runBlocking { rolePreferences.onboardingCompletedFlow.first() })
            assertEquals("seeker", runBlocking { rolePreferences.activeRoleFlow.first() })
            runBlocking {
                verify(mockAuthRepository).signUpWithEmail("new@hearu.app", "StrongPass99!")
            }
        }
    }

    // =========================================================================
    // T3-02: F4 x F5 x F7
    // =========================================================================

    @Test
    fun T3_02_pairwise_roleSelectionToSeekerMatchmakingAndChat_alignsSenderByAuthUid() {
        whenever(mockUser.uid).thenReturn("uid_seeker_1")
        runBlocking {
            db.messageDao().insertMessage(
                MessageEntity(
                    id = "peer_intro_1",
                    sessionId = "sess_p1",
                    senderId = "giver_42",
                    text = "I'm here to listen",
                    timestamp = 1_000L
                )
            )
        }
        val mockResult: HttpsCallableResult = mock()
        whenever(mockResult.data).thenReturn(
            mapOf(
                "matchFound" to true,
                "sessionId" to "sess_p1",
                "giverId" to "giver_42",
                "giverDisplayName" to "KindListener"
            )
        )
        whenever(mockCallable.call(any())).thenReturn(Tasks.forResult(mockResult))

        val authVm = buildAuthViewModel(mockAuthRepository, rolePreferences)
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
                            startDestination = Screen.RoleSelection.route
                        ) {
                            composable(Screen.RoleSelection.route) {
                                RoleSelectionScreen(onRoleSelected = { role ->
                                    authVm.saveRole(role)
                                    navController.navigate(Screen.SeekerHome.route)
                                })
                            }
                            composable(Screen.SeekerHome.route) {
                                SeekerDashboard(
                                    onNavigateToAIChat = {},
                                    onNavigateToMatch = { sid, uid ->
                                        navController.navigate(Screen.Chat.createRoute(sid, uid))
                                    },
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
                        }
                    }
                }
            }

            composeTestRule.onNodeWithText("I need support (Seeker)").performClick()
            drainAsync()

            composeTestRule.onNodeWithText("Anxiety 😰").performClick()
            composeTestRule.onNodeWithText("Loneliness 🌧️").performClick()
            composeTestRule.onNodeWithText("Find a Listener Now").performClick()
            drainAsync()

            composeTestRule.waitUntil(timeoutMillis = 5_000L) {
                composeTestRule.onAllNodesWithText("I'm here to listen").fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithText("Share with compassion...").performTextInput("Hello peer")
            composeTestRule.onNodeWithContentDescription("Send").performClick()
            drainAsync()

            val stored = runBlocking { db.messageDao().getMessagesForSession("sess_p1").first() }
            assertTrue(stored.any { it.text == "Hello peer" && it.senderId == "uid_seeker_1" })

            val myBounds = composeTestRule.onNodeWithText("Hello peer").fetchSemanticsNode().boundsInRoot
            val peerBounds = composeTestRule.onNodeWithText("I'm here to listen").fetchSemanticsNode().boundsInRoot
            assertTrue(
                "Own message (left=${myBounds.left}) must align right of peer message (left=${peerBounds.left})",
                myBounds.left > peerBounds.left
            )
        }
        chatRepo.stopFirestoreSync("sess_p1")
    }

    // =========================================================================
    // T3-03: F6 x F7 x F9
    // =========================================================================

    @Test
    fun T3_03_pairwise_giverAcceptsQueueToChatAndPhotoConsent_keepsBlurredUntilMutual() {
        val chatRepo = ChatRepository(mockFirestore, db.messageDao())
        val chatVm = ChatViewModel(chatRepo, mockAuth, mockVoiceNoteManager)
        val navController = E2ETestHarness.createTestNavController(app)

        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.setContent {
                    HearUTheme {
                        NavHost(
                            navController = navController,
                            startDestination = Screen.GiverHome.route
                        ) {
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

            composeTestRule.onNode(isToggleable()).performClick()
            composeTestRule.onNodeWithText("Status: Offline").assertIsDisplayed()
            composeTestRule.onNode(isToggleable()).performClick()
            composeTestRule.onNodeWithText("Active & Ready to Listen").assertIsDisplayed()

            composeTestRule.onNodeWithText("Accept & Connect Anonymously").performScrollTo().performClick()
            drainAsync()

            composeTestRule.onNodeWithText("End-to-end ephemeral peer chat").assertIsDisplayed()
            assertFalse(chatVm.isPhotoRevealed.value)

            composeTestRule.onNodeWithContentDescription("Photo Reveal Consent").performClick()
            composeTestRule.onNodeWithText("Grant Mutual Consent").performClick()
            drainAsync()

            assertTrue(chatVm.myPhotoConsent.value)
            assertFalse(
                "Unilateral photo consent must NOT unblur photos without peer consent",
                chatVm.isPhotoRevealed.value
            )
            composeTestRule.onNodeWithText("End-to-end ephemeral peer chat").assertIsDisplayed()
        }
    }

    // =========================================================================
    // T3-04: F15 x F2 x F4
    // =========================================================================

    @Test
    fun T3_04_pairwise_signOutFromProfileResetsBackstackAndAllowsReLoginAsGiver() {
        runBlocking {
            rolePreferences.setOnboardingCompleted(true)
            rolePreferences.setActiveRole("seeker")
            whenever(mockAuthRepository.currentUser).thenReturn(mockUser)
            whenever(mockAuthRepository.signInWithEmail("giver@hearu.app", "StrongPass1!"))
                .thenReturn(AuthResult.Success(mockUser))
        }
        val authVm = buildAuthViewModel(mockAuthRepository, rolePreferences)

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.setContent {
                    HearUTheme {
                        HearUNavigation(authViewModel = authVm)
                    }
                }
            }

            composeTestRule.waitUntil(timeoutMillis = 5_000L) {
                composeTestRule.onAllNodesWithText("Connect with a Human Listener")
                    .fetchSemanticsNodes().isNotEmpty()
            }

            composeTestRule.onNodeWithContentDescription("Profile").performClick()
            composeTestRule.onNodeWithText("My Profile & Security").assertIsDisplayed()

            composeTestRule.onNodeWithContentDescription("Sign Out").performClick()
            composeTestRule.onNodeWithText("Sign Out of HearU").assertIsDisplayed()
            composeTestRule.onNodeWithText("Sign Out").performClick()

            composeTestRule.waitUntil(timeoutMillis = 5_000L) {
                composeTestRule.onAllNodesWithText("Continue as Anonymous Guest")
                    .fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithText("Connect with a Human Listener").assertDoesNotExist()
            runCatching {
                composeTestRule.waitUntil(timeoutMillis = 3_000L) {
                    runBlocking { rolePreferences.activeRoleFlow.first() == null }
                }
            }
            assertNull(
                "Sign Out must clear activeRole in RolePreferences",
                runBlocking { rolePreferences.activeRoleFlow.first() }
            )
            verify(mockAuthRepository).signOut()

            composeTestRule.onNodeWithTag("email_field").performScrollTo().performTextInput("giver@hearu.app")
            composeTestRule.onNodeWithTag("password_field").performScrollTo().performTextInput("StrongPass1!")
            composeTestRule.onNodeWithTag("login_button").performScrollTo().performClick()

            composeTestRule.waitUntil(timeoutMillis = 5_000L) {
                composeTestRule.onAllNodesWithText("Continue as Anonymous Guest")
                    .fetchSemanticsNodes().isEmpty()
            }
            runBlocking {
                verify(mockAuthRepository).signInWithEmail("giver@hearu.app", "StrongPass1!")
            }
        }
    }

    // =========================================================================
    // T3-05: F3 x F15
    // =========================================================================

    @Test
    fun T3_05_pairwise_biometricUnavailableNoBrickAndEnabledLockSurvivesRotation() {
        val authVm = buildAuthViewModel(mockAuthRepository, rolePreferences)
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

        runBlocking { rolePreferences.setBiometricEnabled(true) }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            composeTestRule.waitUntil(timeoutMillis = 5_000L) {
                composeTestRule.onAllNodesWithText("HearU is Locked").fetchSemanticsNodes().isNotEmpty()
            }
            scenario.recreate()
            composeTestRule.waitUntil(timeoutMillis = 5_000L) {
                composeTestRule.onAllNodesWithText("HearU is Locked").fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithText("Unlock with Biometrics").performClick()
            composeTestRule.waitForIdle()

            composeTestRule.onNodeWithText("HearU is Locked").assertIsDisplayed()
            val recoveryOrErrorNodes = composeTestRule.onAllNodes(
                hasText("Sign Out", substring = true, ignoreCase = true) or
                    hasText("Reset", substring = true, ignoreCase = true) or
                    hasText("unavailable", substring = true, ignoreCase = true) or
                    hasText("failed", substring = true, ignoreCase = true) or
                    hasText("error", substring = true, ignoreCase = true) or
                    hasText("not available", substring = true, ignoreCase = true) or
                    hasText("No biometric", substring = true, ignoreCase = true)
            ).fetchSemanticsNodes()
            assertTrue(
                "Lock overlay must expose error feedback or recovery action when biometrics fail",
                recoveryOrErrorNodes.isNotEmpty()
            )
        }
    }

    // =========================================================================
    // T3-06: F10 x F12
    // =========================================================================

    @Test
    fun T3_06_pairwise_aiOfflineCrisisKeyword_triggersDialogAndSafeEmergencyIntentFallback() {
        val realGemini = GeminiAiService()
        val aiVm = AIChatViewModel(realGemini, rolePreferences, mockAuth)
        val navController = E2ETestHarness.createTestNavController(app)

        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.setContent {
                    val base = LocalContext.current
                    CompositionLocalProvider(LocalContext provides noHandlersContext(base)) {
                        HearUTheme {
                            NavHost(
                                navController = navController,
                                startDestination = Screen.AIChat.route
                            ) {
                                composable(Screen.AIChat.route) {
                                    AIChatScreen(
                                        onNavigateBack = { navController.popBackStack() },
                                        onNavigateToCrisis = {
                                            navController.navigate(Screen.EmergencyHub.route)
                                        },
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

            composeTestRule.onNode(hasSetTextAction()).performTextInput("I want to kill myself")
            composeTestRule.onNodeWithContentDescription("Send").performClick()
            composeTestRule.waitUntil(timeoutMillis = 5_000L) { !aiVm.isLoading.value }

            assertTrue(
                "Offline crisis keyword must preserve crisisEvent=true",
                aiVm.crisisEvent.value
            )
            composeTestRule.onNodeWithText("We're Here For You").assertIsDisplayed()
            composeTestRule.onNodeWithText("Call 988 Crisis Lifeline").performClick()
            composeTestRule.onNodeWithText("Chat with Lifeline Online").performClick()
            composeTestRule.onNodeWithText("Return to App").performClick()

            composeTestRule.runOnUiThread {
                navController.navigate(Screen.EmergencyHub.route)
            }
            composeTestRule.onNodeWithText("24/7 Crisis & Emergency Hub").assertIsDisplayed()
            composeTestRule.onNodeWithText("Text 988").performClick()
            composeTestRule.onNodeWithText("Visit Befrienders Worldwide Directory")
                .performScrollTo()
                .performClick()
            composeTestRule.onNodeWithText("24/7 Crisis & Emergency Hub").assertIsDisplayed()
        }
    }

    // =========================================================================
    // T3-07: F8 x F7
    // =========================================================================

    @Test
    fun T3_07_pairwise_voiceNoteRecord_persistsToRoomAndDelegatesPlaybackSeek() {
        Shadows.shadowOf(app).grantPermissions(Manifest.permission.RECORD_AUDIO)
        val voiceDispatcher = StandardTestDispatcher()
        val manager = VoiceNoteManager(mainDispatcher = voiceDispatcher).also {
            realVoiceNoteManager = it
        }
        val chatRepo = ChatRepository(mockFirestore, db.messageDao())
        val chatVm = ChatViewModel(chatRepo, mockAuth, manager)

        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.setContent {
                    HearUTheme {
                        ChatScreen(
                            sessionId = "s_vn_t3",
                            userId = "uid_default_1",
                            onNavigateBack = {},
                            viewModel = chatVm
                        )
                    }
                }
            }
            drainAsync()

            composeTestRule.onNodeWithContentDescription("Record Voice Note").performClick()
            voiceDispatcher.scheduler.advanceTimeBy(1200L)
            voiceDispatcher.scheduler.runCurrent()
            composeTestRule.waitForIdle()

            composeTestRule.onNodeWithContentDescription("Send Voice Note").performClick()
            voiceDispatcher.scheduler.runCurrent()
            drainAsync()

            val persisted = runBlocking { db.messageDao().getMessagesForSession("s_vn_t3").first() }
            assertEquals(1, persisted.size)
            assertTrue(persisted[0].isVoiceNote)
            assertTrue(persisted[0].voiceDurationSeconds >= 1)

            val sentMsg = capturedSentPayloads.lastOrNull() as? Message
            assertNotNull(sentMsg)
            assertEquals(28, sentMsg!!.waveformAmplitudes.size)

            composeTestRule.onNodeWithContentDescription("Play").performClick()
            voiceDispatcher.scheduler.runCurrent()
            drainAsync()

            assertTrue(
                "VoiceNoteBubble Play must delegate to ChatViewModel/VoiceNoteManager",
                chatVm.isPlaying.value && chatVm.playingMessageId.value != null
            )
            chatVm.seekVoicePlayback(sentMsg, 0.5f)
            assertEquals(0.5f, chatVm.playbackProgress.value, 0.05f)
        }
        chatRepo.stopFirestoreSync("s_vn_t3")
    }

    // =========================================================================
    // T3-08: F8 x F7
    // =========================================================================

    @Test
    fun T3_08_pairwise_deniedAudioPermissionBlocksRecordAndCancelCleansTempFile() {
        Shadows.shadowOf(app).denyPermissions(Manifest.permission.RECORD_AUDIO)
        val voiceDispatcher = StandardTestDispatcher()
        val manager = VoiceNoteManager(mainDispatcher = voiceDispatcher).also {
            realVoiceNoteManager = it
        }
        val chatRepo = ChatRepository(mockFirestore, db.messageDao())
        val chatVm = ChatViewModel(chatRepo, mockAuth, manager)

        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.setContent {
                    HearUTheme {
                        ChatScreen(
                            sessionId = "s_perm_t3",
                            userId = "uid_default_1",
                            onNavigateBack = {},
                            viewModel = chatVm
                        )
                    }
                }
            }
            drainAsync()

            composeTestRule.onNodeWithContentDescription("Record Voice Note").performClick()
            drainAsync()
            composeTestRule.onNodeWithText("Recording:", substring = true).assertDoesNotExist()
            assertFalse(manager.isRecording.value)

            Shadows.shadowOf(app).grantPermissions(Manifest.permission.RECORD_AUDIO)
            composeTestRule.onNodeWithContentDescription("Record Voice Note").performClick()
            voiceDispatcher.scheduler.advanceTimeBy(500L)
            voiceDispatcher.scheduler.runCurrent()
            drainAsync()

            composeTestRule.onNodeWithText("Cancel").performClick()
            drainAsync()

            assertFalse(manager.isRecording.value)
            val leftovers = app.cacheDir.listFiles { _, name ->
                name.startsWith("voice_note_") && name.endsWith(".m4a")
            }.orEmpty()
            assertTrue("Cancelled recording must clean up temp audio files", leftovers.isEmpty())
            composeTestRule.onNodeWithContentDescription("Record Voice Note").assertIsDisplayed()
            composeTestRule.onNodeWithContentDescription("Send").assertDoesNotExist()
        }
        chatRepo.stopFirestoreSync("s_perm_t3")
    }

    // =========================================================================
    // T3-09: F13 x F7
    // =========================================================================

    @Test
    fun T3_09_pairwise_reportAndBlockLeaveInChat_writesModerationAndExitsSession() {
        val mockDocRef: DocumentReference = mock()
        whenever(mockReportsCollection.add(any())).thenReturn(Tasks.forResult(mockDocRef))
        whenever(mockUserDoc.update(eq("blockedUsers"), any())).thenReturn(Tasks.forResult(null))

        val moderationRepo = ModerationRepository(mockFirestore)
        runBlocking {
            moderationRepo.reportUser("uid_default_1", "bad_peer_9", "s_mod", "Inappropriate Content", "Abusive listener")
            moderationRepo.blockUser("uid_default_1", "bad_peer_9")
        }
        val captor = argumentCaptor<Any>()
        verify(mockReportsCollection).add(captor.capture())
        assertEquals("PENDING_REVIEW", (captor.firstValue as Map<*, *>)["status"])
        verify(mockUserDoc).update(eq("blockedUsers"), any())

        val chatRepo = ChatRepository(mockFirestore, db.messageDao())
        val chatVm = ChatViewModel(chatRepo, mockAuth, mockVoiceNoteManager)
        var exited = false

        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.setContent {
                    HearUTheme {
                        ChatScreen(
                            sessionId = "s_mod",
                            userId = "bad_peer_9",
                            onNavigateBack = { exited = true },
                            viewModel = chatVm
                        )
                    }
                }
            }
            drainAsync()

            composeTestRule.onNodeWithContentDescription("Options").performClick()
            drainAsync()
            composeTestRule.onNodeWithText("Report User / Safety").performClick()
            drainAsync()
            composeTestRule.onNodeWithText("Inappropriate Content").performClick()
            composeTestRule.onNodeWithText("Additional details (optional)")
                .performTextInput("Abusive listener")
            composeTestRule.onNodeWithText("Submit Report").performClick()
            drainAsync()

            composeTestRule.onNodeWithContentDescription("Options").performClick()
            drainAsync()
            composeTestRule.onNodeWithText(app.getString(R.string.block_user)).performClick()
            drainAsync()

            if (!exited) {
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

            assertTrue("Block & Leave must exit chat session", exited)
            assertNull(chatVm.errorState.value)
        }
        chatRepo.stopFirestoreSync("s_mod")
    }

    // =========================================================================
    // T3-10: F15 x F5 x F7 x F10 x F12
    // =========================================================================

    @Test
    fun T3_10_pairwise_darkThemeTogglePersistsAndAllCoreScreensMeetWcagContrast() {
        val authVm = buildAuthViewModel(mockAuthRepository, rolePreferences)
        val matchVm = buildMatchViewModel()
        val chatRepo = ChatRepository(mockFirestore, db.messageDao())
        val chatVm = ChatViewModel(chatRepo, mockAuth, mockVoiceNoteManager)
        val aiVm = AIChatViewModel(mock(), rolePreferences, mockAuth)

        var onPrimaryContrast = 0f
        var containerContrast = 0f
        var surfaceContrast = 0f
        var surfaceVariantContrast = 0f

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

            scenario.onActivity { activity ->
                activity.setContent {
                    HearUTheme(darkTheme = true) {
                        val scheme = MaterialTheme.colorScheme
                        onPrimaryContrast = contrastRatio(scheme.onPrimary, scheme.primary)
                        containerContrast = contrastRatio(scheme.onPrimaryContainer, scheme.primaryContainer)
                        surfaceContrast = contrastRatio(scheme.onSurface, scheme.surface)
                        surfaceVariantContrast = contrastRatio(scheme.onSurfaceVariant, scheme.surfaceVariant)
                        SeekerDashboard(
                            onNavigateToAIChat = {},
                            onNavigateToMatch = { _, _ -> },
                            matchViewModel = matchVm
                        )
                    }
                }
            }
            composeTestRule.onNodeWithText("Connect with a Human Listener").assertIsDisplayed()

            scenario.onActivity { activity ->
                activity.setContent {
                    HearUTheme(darkTheme = true) {
                        GiverDashboard()
                    }
                }
            }
            composeTestRule.onNodeWithText("HearU - Listener Hub").assertIsDisplayed()

            scenario.onActivity { activity ->
                activity.setContent {
                    HearUTheme(darkTheme = true) {
                        ChatScreen(sessionId = "s_dark", userId = "uid_default_1", onNavigateBack = {}, viewModel = chatVm)
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
                        EmergencyScreen(onNavigateBack = {})
                    }
                }
            }
            composeTestRule.onNodeWithText("24/7 Crisis & Emergency Hub").assertIsDisplayed()
        }

        assertTrue("Dark onPrimary/primary contrast $onPrimaryContrast < 4.5", onPrimaryContrast >= 4.5f)
        assertTrue("Dark primaryContainer contrast $containerContrast < 4.5", containerContrast >= 4.5f)
        assertTrue("Dark surface contrast $surfaceContrast < 4.5", surfaceContrast >= 4.5f)
        assertTrue("Dark surfaceVariant contrast $surfaceVariantContrast < 4.5", surfaceVariantContrast >= 4.5f)

        val seekerSource = File(
            E2ETestHarness.resolveProjectRoot(),
            "app/src/main/java/com/hearu/app/ui/home/SeekerDashboard.kt"
        ).readText()
        assertFalse(
            "SeekerDashboard must use onPrimary instead of hardcoded Color.White",
            seekerSource.contains("color = Color.White") || seekerSource.contains("tint = Color.White")
        )
        chatRepo.stopFirestoreSync("s_dark")
    }

    // =========================================================================
    // T3-11: F14 x F7
    // =========================================================================

    @Test
    fun T3_11_pairwise_fcmDeepLinkToChat_alignsMessagesAndPopsToDashboard() {
        Shadows.shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val service = Robolectric.buildService(HearUFirebaseMessagingService::class.java).create().get()
        service.onMessageReceived(
            RemoteMessage.Builder("sender@fcm.googleapis.com")
                .setData(
                    mapOf(
                        HearUNotificationConfig.EXTRA_NOTIFICATION_TYPE to HearUNotificationConfig.TYPE_CHAT_MESSAGE,
                        HearUNotificationConfig.EXTRA_SESSION_ID to "dl_sess_3",
                        HearUNotificationConfig.EXTRA_USER_ID to "peer_giver_3"
                    )
                )
                .build()
        )
        val nm = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val posted = Shadows.shadowOf(nm).allNotifications.single()
        val savedIntent = Shadows.shadowOf(posted.contentIntent).savedIntent
        val deepLinkSession = savedIntent.getStringExtra(HearUNotificationConfig.EXTRA_SESSION_ID)
        assertEquals("dl_sess_3", deepLinkSession)

        runBlocking {
            rolePreferences.setOnboardingCompleted(true)
            rolePreferences.setActiveRole("seeker")
        }
        whenever(mockAuthRepository.currentUser).thenReturn(mockUser)
        whenever(mockUser.uid).thenReturn("uid_seeker_dl")
        val authVm = buildAuthViewModel(mockAuthRepository, rolePreferences)

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.setContent {
                    HearUTheme {
                        HearUNavigation(authViewModel = authVm, initialSessionId = deepLinkSession)
                    }
                }
            }
            composeTestRule.waitUntil(timeoutMillis = 5_000L) {
                composeTestRule.onAllNodesWithText("Empathetic Listener").fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithContentDescription("Back").performClick()
            composeTestRule.waitUntil(timeoutMillis = 5_000L) {
                composeTestRule.onAllNodesWithText("Connect with a Human Listener").fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithText("Empathetic Listener").assertDoesNotExist()
        }

        runBlocking {
            db.messageDao().insertMessage(
                MessageEntity(
                    id = "1",
                    sessionId = "dl_sess_3",
                    senderId = "peer_giver_3",
                    text = "Peer hello",
                    timestamp = 1000L
                )
            )
            db.messageDao().insertMessage(
                MessageEntity(
                    id = "2",
                    sessionId = "dl_sess_3",
                    senderId = "uid_seeker_dl",
                    text = "My reply",
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
                            sessionId = "dl_sess_3",
                            userId = "peer_giver_3",
                            onNavigateBack = {},
                            viewModel = chatVm
                        )
                    }
                }
            }
            composeTestRule.waitUntil(timeoutMillis = 5_000L) {
                composeTestRule.onAllNodesWithText("Peer hello").fetchSemanticsNodes().isNotEmpty() &&
                    composeTestRule.onAllNodesWithText("My reply").fetchSemanticsNodes().isNotEmpty()
            }
            val peerBounds = composeTestRule.onNodeWithText("Peer hello").fetchSemanticsNode().boundsInRoot
            val myBounds = composeTestRule.onNodeWithText("My reply").fetchSemanticsNode().boundsInRoot
            assertTrue(myBounds.left > peerBounds.left && myBounds.right > peerBounds.right)
        }
        chatRepo.stopFirestoreSync("dl_sess_3")

        val navSource = File(
            E2ETestHarness.resolveProjectRoot(),
            "app/src/main/java/com/hearu/app/navigation/HearUNavigation.kt"
        ).readText()
        assertFalse(
            "HearUNavigation must not pass hardcoded \"peer\" to createRoute",
            Regex("""createRoute\([^)]*"peer"[^)]*\)""").containsMatchIn(navSource)
        )
    }

    // =========================================================================
    // T3-12: F14 x F3 x F2
    // =========================================================================

    @Test
    fun T3_12_pairwise_fcmDeepLinkBlockedWhenUnauthenticatedOrBiometricLocked() {
        FirebaseAuth.getInstance().signOut()
        runBlocking { rolePreferences.setOnboardingCompleted(true) }

        val unauthIntent = Intent(app, MainActivity::class.java).apply {
            putExtra(HearUNotificationConfig.EXTRA_SESSION_ID, "secret_1")
        }
        ActivityScenario.launch<MainActivity>(unauthIntent).use {
            composeTestRule.waitUntil(timeoutMillis = 5_000L) {
                composeTestRule.onAllNodesWithText("Welcome to HearU").fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithText("Your Safe Space is Connected").assertDoesNotExist()
            composeTestRule.onNodeWithText("Empathetic Listener").assertDoesNotExist()
        }

        runBlocking { rolePreferences.setBiometricEnabled(true) }
        val lockedIntent = Intent(app, MainActivity::class.java).apply {
            putExtra(HearUNotificationConfig.EXTRA_SESSION_ID, "secret_2")
        }
        ActivityScenario.launch<MainActivity>(lockedIntent).use {
            composeTestRule.waitUntil(timeoutMillis = 5_000L) {
                composeTestRule.onAllNodesWithText("HearU is Locked").fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithText("Empathetic Listener").assertDoesNotExist()
            composeTestRule.onNodeWithText("Your Safe Space is Connected").assertDoesNotExist()
        }
    }

    // =========================================================================
    // T3-13: F11 x F12
    // =========================================================================

    @Test
    @Config(qualifiers = "w411dp-h915dp")
    fun T3_13_pairwise_breathingPauseResumeGroundingAndEmergencyRoundTrip() {
        composeTestRule.mainClock.autoAdvance = false
        val navController = E2ETestHarness.createTestNavController(app)

        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.setContent {
                    HearUTheme {
                        NavHost(
                            navController = navController,
                            startDestination = Screen.BreathingExercise.route
                        ) {
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

            composeTestRule.onNodeWithText("Begin Session").performClick()
            composeTestRule.mainClock.advanceTimeByFrame()
            advanceBreathingSeconds(2, extraTailMs = 50L)
            composeTestRule.onNodeWithText("2").assertIsDisplayed()

            composeTestRule.onNodeWithText("Pause").performClick()
            composeTestRule.mainClock.advanceTimeByFrame()
            composeTestRule.mainClock.advanceTimeBy(3000L)
            composeTestRule.mainClock.advanceTimeByFrame()
            composeTestRule.onNodeWithText("Hold Gently...").assertDoesNotExist()

            composeTestRule.onNodeWithText("Begin Session").performClick()
            composeTestRule.mainClock.advanceTimeByFrame()
            composeTestRule.mainClock.advanceTimeBy(100L)
            composeTestRule.mainClock.advanceTimeByFrame()
            composeTestRule.onNodeWithText("2").assertIsDisplayed()

            advanceBreathingSeconds(2, extraTailMs = 200L)
            composeTestRule.onNodeWithText("Hold Gently...").assertIsDisplayed()

            composeTestRule.onNodeWithText("5-4-3-2-1 Grounding").performClick()
            composeTestRule.mainClock.advanceTimeByFrame()
            composeTestRule.onNodeWithText("5 Things You Can SEE").assertIsDisplayed()

            composeTestRule.onNodeWithContentDescription("Crisis Hub").performClick()
            composeTestRule.mainClock.advanceTimeByFrame()
            composeTestRule.onNodeWithText("24/7 Crisis & Emergency Hub").assertIsDisplayed()

            composeTestRule.onNodeWithText("Take a 2-Minute Calming Breath (4-7-8 Exercise)")
                .performScrollTo()
                .performClick()
            composeTestRule.mainClock.advanceTimeByFrame()
            composeTestRule.onNodeWithText("Calming & Grounding Tools").assertIsDisplayed()
        }
    }

    // =========================================================================
    // T3-14: F4 x F6 x F15
    // =========================================================================

    @Test
    fun T3_14_pairwise_profileRoleSwitcherUpdatesDataStoreAndRoutesGiverDashboard() {
        runBlocking { rolePreferences.setActiveRole("seeker") }
        val authVm = buildAuthViewModel(mockAuthRepository, rolePreferences)
        val matchVm = buildMatchViewModel()
        val userRepo = UserRepository(mockFirestore)
        whenever(mockUserDoc.update(eq("status"), any())).thenReturn(Tasks.forResult(null))
        val navController = E2ETestHarness.createTestNavController(app)

        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.setContent {
                    HearUTheme {
                        NavHost(
                            navController = navController,
                            startDestination = Screen.SeekerHome.route
                        ) {
                            composable(Screen.SeekerHome.route) {
                                SeekerDashboard(
                                    onNavigateToAIChat = {},
                                    onNavigateToMatch = { _, _ -> },
                                    onNavigateToProfile = { navController.navigate(Screen.Profile.route) },
                                    matchViewModel = matchVm
                                )
                            }
                            composable(Screen.Profile.route) {
                                ProfileScreen(
                                    onNavigateBack = { navController.popBackStack() },
                                    authViewModel = authVm
                                )
                            }
                            composable(Screen.GiverHome.route) {
                                GiverDashboard(
                                    onNavigateToProfile = { navController.navigate(Screen.Profile.route) }
                                )
                            }
                        }
                    }
                }
            }

            composeTestRule.onNodeWithContentDescription("Profile").performClick()
            drainAsync()

            val switcherNodes = composeTestRule.onAllNodes(
                hasText("Switch Role", substring = true, ignoreCase = true) or
                    hasText("Switch to", substring = true, ignoreCase = true) or
                    hasText("Change Role", substring = true, ignoreCase = true) or
                    (hasText("Giver", substring = true, ignoreCase = true) and hasClickAction())
            ).fetchSemanticsNodes()
            assertTrue(
                "ProfileScreen must expose an interactive Role Switcher control",
                switcherNodes.isNotEmpty()
            )
            composeTestRule.onAllNodes(
                hasText("Switch Role", substring = true, ignoreCase = true) or
                    hasText("Switch to", substring = true, ignoreCase = true) or
                    hasText("Change Role", substring = true, ignoreCase = true) or
                    (hasText("Giver", substring = true, ignoreCase = true) and hasClickAction())
            )[0].performScrollTo().performClick()
            drainAsync()

            val giverChoiceNodes = composeTestRule.onAllNodes(
                hasText("Giver", substring = true, ignoreCase = true) and hasClickAction()
            ).fetchSemanticsNodes()
            if (runBlocking { rolePreferences.activeRoleFlow.first() } != "giver" && giverChoiceNodes.isNotEmpty()) {
                composeTestRule.onAllNodes(
                    hasText("Giver", substring = true, ignoreCase = true) and hasClickAction()
                )[0].performClick()
                drainAsync()
            }

            assertEquals("giver", runBlocking { rolePreferences.activeRoleFlow.first() })

            composeTestRule.runOnUiThread {
                navController.navigate(Screen.GiverHome.route)
            }
            composeTestRule.onNodeWithText("HearU - Listener Hub").assertIsDisplayed()
            runBlocking { userRepo.updateUserStatus("uid_1", "offline") }
            verify(mockUserDoc).update("status", "offline")
        }
    }

    // =========================================================================
    // T3-15: F5 x F10
    // =========================================================================

    @Test
    fun T3_15_pairwise_matchmakingFailureOrNoMatch_routesToAiCompanionWithQuotaGuard() {
        val mockGemini = mock<GeminiAiService>()
        runBlocking {
            app.dataStore.edit { prefs ->
                prefs[intPreferencesKey("ai_msg_count")] = 49
                prefs[longPreferencesKey("ai_msg_date")] = todayStartEpoch()
            }
            val quotaReply = AiResult.Success("50th message support.", isCrisisDetected = false)
            whenever(mockGemini.generateEmpatheticResponse(any())).thenReturn(quotaReply)
            whenever(mockGemini.generateEmpatheticResponse(any(), any())).thenReturn(quotaReply)
        }

        whenever(mockCallable.call(any())).thenReturn(
            Tasks.forException(RuntimeException("Network down"))
        )
        val matchVm = buildMatchViewModel()
        val aiVm = AIChatViewModel(mockGemini, rolePreferences, mockAuth)
        val navController = E2ETestHarness.createTestNavController(app)

        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.setContent {
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
                                    viewModel = aiVm
                                )
                            }
                        }
                    }
                }
            }

            composeTestRule.onNodeWithText("Find a Listener Now").performClick()
            mainDispatcherRule.testDispatcher.scheduler.advanceTimeBy(3000L)
            drainAsync()

            assertTrue(
                "Matchmaking exception must yield MatchState.Error, was ${matchVm.matchState.value}",
                matchVm.matchState.value is MatchState.Error
            )

            val noMatchResult: HttpsCallableResult = mock()
            whenever(noMatchResult.data).thenReturn(
                mapOf("matchFound" to false, "message" to "No listeners available.")
            )
            whenever(mockCallable.call(any())).thenReturn(Tasks.forResult(noMatchResult))
            matchVm.reset()
            composeTestRule.onNodeWithText("Find a Listener Now").performClick()
            drainAsync()

            composeTestRule.onNodeWithText("Chat with 24/7 AI Companion Instead")
                .performScrollTo()
                .performClick()
            drainAsync()

            composeTestRule.onNodeWithText("Daily quota: 49/50 messages used").assertIsDisplayed()
            composeTestRule.onNode(hasSetTextAction()).performTextInput("Final message")
            composeTestRule.onNodeWithContentDescription("Send").performClick()
            composeTestRule.waitUntil(timeoutMillis = 5_000L) { !aiVm.isLoading.value }
            drainAsync()

            assertEquals(50, aiVm.quotaUsed.value)
            assertTrue(aiVm.quotaExhausted.value)
            composeTestRule.onNodeWithText("Daily quota reached").assertIsDisplayed()

            aiVm.sendMessage("51st attempt")
            drainAsync()
            val aiCalls = Mockito.mockingDetails(mockGemini).invocations
                .count { it.method.name == "generateEmpatheticResponse" }
            assertEquals(1, aiCalls)
        }
    }

    // =========================================================================
    // T3-16: F10 x F11
    // =========================================================================

    @Test
    fun T3_16_pairwise_aiCompanionBreathingChip_navigatesToGroundingAndReturnsTranscript() {
        val mockGemini = mock<GeminiAiService>()
        runBlocking {
            val reply = AiResult.Success("Take a slow breath.", isCrisisDetected = false)
            whenever(mockGemini.generateEmpatheticResponse("I'm feeling anxious")).thenReturn(reply)
            whenever(mockGemini.generateEmpatheticResponse(eq("I'm feeling anxious"), any())).thenReturn(reply)
        }
        val aiVm = AIChatViewModel(mockGemini, rolePreferences, mockAuth)
        val navController = E2ETestHarness.createTestNavController(app)

        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.setContent {
                    HearUTheme {
                        NavHost(
                            navController = navController,
                            startDestination = Screen.AIChat.route
                        ) {
                            composable(Screen.AIChat.route) {
                                AIChatScreen(
                                    onNavigateBack = { navController.popBackStack() },
                                    onNavigateToBreathing = {
                                        navController.navigate(Screen.BreathingExercise.route)
                                    },
                                    viewModel = aiVm
                                )
                            }
                            composable(Screen.BreathingExercise.route) {
                                BreathingExerciseScreen(
                                    onNavigateBack = { navController.popBackStack() }
                                )
                            }
                        }
                    }
                }
            }

            composeTestRule.onNodeWithText("🌬️ Can we do a breathing exercise?").performClick()
            drainAsync()
            composeTestRule.onNodeWithText("Calming & Grounding Tools").assertIsDisplayed()
            composeTestRule.onNodeWithText("5-4-3-2-1 Grounding").performClick()
            composeTestRule.onNodeWithText("I've Found All 5 → Next Sense").performScrollTo().performClick()
            composeTestRule.onNodeWithText("I've Found All 4 → Next Sense").performScrollTo().performClick()
            composeTestRule.onNodeWithText("I've Found All 3 → Next Sense").performScrollTo().performClick()
            composeTestRule.onNodeWithText("I've Found All 2 → Next Sense").performScrollTo().performClick()
            composeTestRule.onNodeWithText("Complete Grounding Session").performScrollTo().performClick()
            composeTestRule.onNodeWithText("You Are Here. You Are Safe.").assertIsDisplayed()

            composeTestRule.onNodeWithContentDescription("Back").performClick()
            drainAsync()

            composeTestRule.onNode(hasSetTextAction()).performTextInput("I'm feeling anxious")
            composeTestRule.onNodeWithContentDescription("Send").performClick()
            composeTestRule.waitUntil(timeoutMillis = 5_000L) { !aiVm.isLoading.value }
            drainAsync()
            runCatching {
                composeTestRule.onNode(
                    androidx.compose.ui.test.hasScrollToNodeAction() and
                        androidx.compose.ui.test.SemanticsMatcher.keyIsDefined(
                            androidx.compose.ui.semantics.SemanticsProperties.VerticalScrollAxisRange
                        )
                ).performScrollToNode(hasText("Take a slow breath."))
            }

            val aiBounds = composeTestRule.onNodeWithText("Take a slow breath.").fetchSemanticsNode().boundsInRoot
            val userBounds = composeTestRule.onNodeWithText("I'm feeling anxious").fetchSemanticsNode().boundsInRoot
            assertTrue(
                "AI reply (left=${aiBounds.left}) must align LEFT of user prompt (left=${userBounds.left})",
                aiBounds.left < userBounds.left
            )

            composeTestRule.onNodeWithContentDescription("Calm Breath").performClick()
            composeTestRule.onNodeWithText("Calming & Grounding Tools").assertIsDisplayed()
            composeTestRule.onNodeWithContentDescription("Back").performClick()
            composeTestRule.onNodeWithText("I'm feeling anxious").assertIsDisplayed()
            composeTestRule.onNodeWithText("Take a slow breath.").assertIsDisplayed()
        }
    }

    // =========================================================================
    // T3-17: F15 x F7
    // =========================================================================

    @Test
    fun T3_17_pairwise_privacyPolicyViewerAndAccountDeletion_wipesRoomAndPrefs() {
        runBlocking {
            rolePreferences.setDisplayName("BraveOwl")
            rolePreferences.setActiveRole("seeker")
            db.messageDao().insertMessage(
                MessageEntity(
                    id = "m_del_1",
                    sessionId = "s_del",
                    senderId = "uid_default_1",
                    text = "Private message",
                    timestamp = 1_700_000_000_000L
                )
            )
        }
        val authVm = buildAuthViewModel(mockAuthRepository, rolePreferences)

        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
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
            val policyNodes = composeTestRule.onAllNodes(
                hasText("privacy@hearu.app", substring = true, ignoreCase = true) or
                    hasText("18 and older", substring = true, ignoreCase = true) or
                    hasText("30 days", substring = true, ignoreCase = true)
            ).fetchSemanticsNodes()
            assertTrue("Privacy Policy viewer must render policy details", policyNodes.isNotEmpty())
            val closeNodes = composeTestRule.onAllNodes(
                (hasText("Close", substring = true, ignoreCase = true) or
                    hasText("Done", substring = true, ignoreCase = true) or
                    hasText("OK", ignoreCase = true)) and hasClickAction()
            ).fetchSemanticsNodes()
            if (closeNodes.isNotEmpty()) {
                composeTestRule.onAllNodes(
                    (hasText("Close", substring = true, ignoreCase = true) or
                        hasText("Done", substring = true, ignoreCase = true) or
                        hasText("OK", ignoreCase = true)) and hasClickAction()
                )[0].performClick()
            }

            composeTestRule.onNodeWithText("Delete Account", substring = true, ignoreCase = true)
                .performScrollTo()
                .performClick()
            composeTestRule.onNodeWithText("Cancel", substring = true, ignoreCase = true).performClick()
            assertEquals("BraveOwl", runBlocking { rolePreferences.displayNameFlow.first() })
            assertEquals(1, runBlocking { db.messageDao().getMessagesForSession("s_del").first().size })

            composeTestRule.onNodeWithText("Delete Account", substring = true, ignoreCase = true)
                .performScrollTo()
                .performClick()
            val confirmDeleteNodes = composeTestRule.onAllNodes(
                (hasText("Delete", substring = true, ignoreCase = true) or
                    hasText("Erase", substring = true, ignoreCase = true) or
                    hasText("Confirm", substring = true, ignoreCase = true)) and
                    hasClickAction()
            ).fetchSemanticsNodes()
            assertTrue("Delete Account dialog must offer a confirm action", confirmDeleteNodes.isNotEmpty())
            composeTestRule.onAllNodes(
                (hasText("Delete", substring = true, ignoreCase = true) or
                    hasText("Erase", substring = true, ignoreCase = true) or
                    hasText("Confirm", substring = true, ignoreCase = true)) and
                    hasClickAction()
            ).let { nodes ->
                val count = nodes.fetchSemanticsNodes().size
                nodes[count - 1].performClick()
            }
            drainAsync()

            assertNull(
                "Account deletion must clear RolePreferences activeRole",
                runBlocking { rolePreferences.activeRoleFlow.first() }
            )
            assertTrue(
                "Account deletion must wipe local Room messages",
                runBlocking { db.messageDao().getMessagesForSession("s_del").first().isEmpty() }
            )
        }
    }

    // =========================================================================
    // T3-18: F15 x F13 x F16
    // =========================================================================

    @Test
    fun T3_18_pairwise_aliasUpdateAndModerationBackendRules_enforceSafetyAndRetention() {
        val authVm = buildAuthViewModel(mockAuthRepository, rolePreferences)

        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.setContent {
                    HearUTheme {
                        ProfileScreen(onNavigateBack = {}, authViewModel = authVm)
                    }
                }
            }

            composeTestRule.onNodeWithText("Change Alias Name").performClick()
            composeTestRule.onNodeWithText("Alias Name").performTextReplacement("  CalmOwl  ")
            composeTestRule.onNodeWithText("Save").performClick()
            composeTestRule.waitUntil(timeoutMillis = 5_000L) {
                runBlocking { rolePreferences.displayNameFlow.first() == "CalmOwl" }
            }
            assertEquals("CalmOwl", runBlocking { rolePreferences.displayNameFlow.first() })
        }

        val mockDocRef: DocumentReference = mock()
        whenever(mockReportsCollection.add(any())).thenReturn(Tasks.forResult(mockDocRef))
        whenever(mockUserDoc.update(eq("blockedUsers"), any())).thenReturn(Tasks.forResult(null))
        val modRepo = ModerationRepository(mockFirestore)
        runBlocking {
            modRepo.reportUser("u_self", "u_bad", "s_18", "Spam / Scams", "Links")
            modRepo.blockUser("u_self", "u_bad")
        }
        verify(mockReportsCollection).add(any())
        verify(mockUserDoc).update(eq("blockedUsers"), any())

        val root = E2ETestHarness.resolveProjectRoot()
        val rules = File(root, "firestore.rules").readText()
        val functionsJs = File(root, "functions/index.js").readText()

        assertTrue(rules.contains("blockedUsers"))
        assertTrue(rules.contains("allow read, update, delete: if false;"))
        assertTrue(rules.contains("identityRevealed"))
        assertTrue(functionsJs.contains("purgeExpiredChats"))
        assertTrue(functionsJs.contains("blockedUsers"))
        assertTrue(functionsJs.contains("giverId"))
    }
}
