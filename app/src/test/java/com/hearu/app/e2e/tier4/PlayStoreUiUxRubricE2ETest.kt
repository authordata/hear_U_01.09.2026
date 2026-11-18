package com.hearu.app.e2e.tier4

import android.app.Application
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Bundle
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.HttpsCallableReference
import com.hearu.app.R
import com.hearu.app.ai.GeminiAiService
import com.hearu.app.audio.VoiceNoteManager
import com.hearu.app.data.RolePreferences
import com.hearu.app.data.local.AppDatabase
import com.hearu.app.data.local.dao.MessageDao
import com.hearu.app.e2e.harness.E2ETestHarness
import com.hearu.app.e2e.harness.MainDispatcherRule
import com.hearu.app.repository.AuthRepository
import com.hearu.app.repository.ChatRepository
import com.hearu.app.repository.UserRepository
import com.hearu.app.ui.auth.AuthViewModel
import com.hearu.app.ui.auth.LoginScreen
import com.hearu.app.ui.auth.RoleSelectionScreen
import com.hearu.app.ui.chat.AIChatScreen
import com.hearu.app.ui.chat.AIChatViewModel
import com.hearu.app.ui.chat.ChatScreen
import com.hearu.app.ui.chat.ChatViewModel
import com.hearu.app.ui.chat.components.VoiceNoteBubble
import com.hearu.app.ui.dialogs.CrisisSupportDialog
import com.hearu.app.ui.emergency.EmergencyScreen
import com.hearu.app.ui.home.MatchState
import com.hearu.app.ui.home.MatchViewModel
import com.hearu.app.ui.home.ProfileScreen
import com.hearu.app.ui.home.SeekerDashboard
import com.hearu.app.ui.onboarding.OnboardingScreen
import com.hearu.app.ui.theme.HearUTheme
import com.hearu.app.ui.tools.BreathingExerciseScreen
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.MockedStatic
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.math.max
import kotlin.math.min

/**
 * Tier 4: Explicit item-by-item Play Store Release Readiness (R-PS-01..07)
 * and UI/UX Quality Rubric (R-UX-01..05) E2E verification suite.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class PlayStoreUiUxRubricE2ETest {

    @get:Rule(order = 0)
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule(order = 1)
    val composeTestRule = createComposeRule()

    private val root: File by lazy { E2ETestHarness.resolveProjectRoot() }
    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var rolePreferences: RolePreferences
    private lateinit var mockAuthRepository: AuthRepository
    private lateinit var mockAuth: FirebaseAuth
    private lateinit var mockUser: FirebaseUser
    private lateinit var mockFunctions: FirebaseFunctions
    private lateinit var mockCallable: HttpsCallableReference
    private var mockFunctionsStatic: MockedStatic<FirebaseFunctions>? = null
    private lateinit var mockChatRepository: ChatRepository
    private lateinit var mockVoiceNoteManager: VoiceNoteManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        E2ETestHarness.ensureFirebaseInitialized(context)
        runBlocking { E2ETestHarness.clearRolePreferences(context) }
        db = E2ETestHarness.createInMemoryDb(context)
        rolePreferences = E2ETestHarness.createRolePreferences(context)

        mockAuthRepository = mock()
        mockAuth = mock()
        mockUser = mock()
        whenever(mockAuthRepository.currentUser).thenReturn(null)
        runBlocking {
            whenever(mockAuthRepository.deleteAccount()).thenReturn(Result.success(Unit))
            whenever(mockAuthRepository.sendPasswordResetEmail(any())).thenReturn(Result.success(Unit))
        }
        whenever(mockAuth.currentUser).thenReturn(mockUser)
        whenever(mockUser.uid).thenReturn("rubric_uid_1")

        mockFunctions = mock()
        mockCallable = mock()
        mockFunctionsStatic = Mockito.mockStatic(FirebaseFunctions::class.java)
        mockFunctionsStatic?.`when`<FirebaseFunctions> { FirebaseFunctions.getInstance() }
            ?.thenReturn(mockFunctions)
        whenever(mockFunctions.getHttpsCallable("matchSeekerWithGiver")).thenReturn(mockCallable)

        mockChatRepository = mock()
        whenever(mockChatRepository.getMessages(any())).thenReturn(flowOf(emptyList()))
        runBlocking {
            whenever(mockChatRepository.sendMessage(any(), any())).thenReturn(Unit)
        }
        mockVoiceNoteManager = createStubbedVoiceNoteManager()
    }

    @After
    fun tearDown() {
        mockFunctionsStatic?.close()
        mockFunctionsStatic = null
        db.close()
        runBlocking { E2ETestHarness.clearRolePreferences(context) }
    }

    private fun projectFile(relativePath: String): File = File(root, relativePath)

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
                    p == Context::class.java -> context
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

    private fun contrastRatio(fg: Color, bg: Color): Float {
        val l1 = fg.luminance()
        val l2 = bg.luminance()
        return (max(l1, l2) + 0.05f) / (min(l1, l2) + 0.05f)
    }

    private fun noHandlersContext(base: Context): Context = object : ContextWrapper(base) {
        override fun startActivity(intent: Intent?) {
            throw ActivityNotFoundException("No activity found to handle $intent")
        }

        override fun startActivity(intent: Intent?, options: Bundle?) {
            throw ActivityNotFoundException("No activity found to handle $intent")
        }
    }

    private fun assertScrollableUnderLargeFont(
        targetText: String,
        content: @Composable () -> Unit
    ) {
        composeTestRule.setContent {
            val baseDensity = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density = baseDensity.density, fontScale = 1.5f)
            ) {
                HearUTheme {
                    Box(modifier = Modifier.size(width = 320.dp, height = 560.dp)) {
                        content()
                    }
                }
            }
        }
        composeTestRule.onNodeWithText(targetText, substring = true)
            .performScrollTo()
            .assertIsDisplayed()
    }

    // =========================================================================
    // Play Store Readiness Rubric (R-PS-01 .. R-PS-07)
    // =========================================================================

    @Test
    fun rubric_R_PS_01_sdkTarget35ToolchainAndRoomSchemaExport() {
        val buildGradle = stripCStyleComments(projectFile("app/build.gradle.kts").readText())
        val compileSdk = Regex("""\bcompileSdk\s*=?\s*(\d+)""").find(buildGradle)?.groupValues?.get(1)?.toIntOrNull()
        val targetSdk = Regex("""\btargetSdk\s*=?\s*(\d+)""").find(buildGradle)?.groupValues?.get(1)?.toIntOrNull()
        val minSdk = Regex("""\bminSdk\s*=?\s*(\d+)""").find(buildGradle)?.groupValues?.get(1)?.toIntOrNull()
        val versionCode = Regex("""\bversionCode\s*=\s*(\d+)""").find(buildGradle)?.groupValues?.get(1)?.toIntOrNull()
        val versionName = Regex("""\bversionName\s*=\s*"([^"]+)"""").find(buildGradle)?.groupValues?.get(1)

        assertTrue("compileSdk ($compileSdk) must be >= 35", (compileSdk ?: 0) >= 35)
        assertTrue("targetSdk ($targetSdk) must be >= 35", (targetSdk ?: 0) >= 35)
        assertTrue("minSdk ($minSdk) must be >= 26", (minSdk ?: 0) >= 26)
        assertTrue("versionCode must be >= 1", (versionCode ?: 0) >= 1)
        assertTrue("versionName must follow semver", versionName != null && Regex("""\d+\.\d+\.\d+""").matches(versionName))

        val gradleProps = projectFile("gradle.properties").readText()
        assertFalse("gradle.properties must not use suppressUnsupportedCompileSdk", gradleProps.contains("suppressUnsupportedCompileSdk"))

        val libsToml = projectFile("gradle/libs.versions.toml").readText()
        val biometricLine = libsToml.lineSequence().firstOrNull { it.trim().startsWith("biometric") }
            ?: error("biometric version missing")
        assertFalse(biometricLine.lowercase().contains("-alpha"))
        assertFalse(biometricLine.lowercase().contains("-beta"))
        assertFalse(biometricLine.lowercase().contains("-rc"))
        assertFalse(biometricLine.lowercase().contains("snapshot"))

        val appDatabaseSource = stripCStyleComments(
            projectFile("app/src/main/java/com/hearu/app/data/local/AppDatabase.kt").readText()
        )
        assertTrue(
            "AppDatabase must enable exportSchema = true",
            Regex("""exportSchema\s*=\s*true""").containsMatchIn(appDatabaseSource)
        )
        assertTrue("ksp room.schemaLocation must be configured", buildGradle.contains("room.schemaLocation"))
        val schemasDir = projectFile("app/schemas")
        val schemaJsons = if (schemasDir.isDirectory) {
            schemasDir.walkTopDown().filter { it.isFile && it.extension == "json" }.toList()
        } else {
            emptyList()
        }
        assertTrue("app/schemas/ must contain at least one exported .json schema", schemaJsons.isNotEmpty())
    }

    @Test
    fun rubric_R_PS_02_releaseSigningHygieneAndSecretProtection() {
        val buildGradle = stripCStyleComments(projectFile("app/build.gradle.kts").readText())
        val releaseBlock = extractRegexBraceBlock(
            extractBraceBlock(buildGradle, "buildTypes") ?: buildGradle,
            Regex("""\brelease\s*\{""")
        ) ?: error("release buildType block missing")

        assertTrue(Regex("""isMinifyEnabled\s*=\s*true""").containsMatchIn(releaseBlock))
        assertTrue(Regex("""isShrinkResources\s*=\s*true""").containsMatchIn(releaseBlock))
        assertFalse(releaseBlock.contains("""signingConfigs.getByName("debug")"""))
        assertFalse(releaseBlock.contains("""signingConfigs["debug"]"""))
        assertTrue(buildGradle.contains("keystore.properties") && (buildGradle.contains("exists()") || buildGradle.contains("isFile")))

        val trackedSecrets = root.listFiles().orEmpty().filter { f ->
            f.isFile && (f.name == "keystore.properties" || f.extension == "jks" || f.extension == "keystore")
        }
        assertTrue("No keystore secrets may exist in repo root: $trackedSecrets", trackedSecrets.isEmpty())

        val gitignore = projectFile(".gitignore").readText()
        assertTrue(gitignore.contains("build/"))
        assertTrue(gitignore.contains("keystore.properties"))
        assertTrue(gitignore.contains("*.jks"))
        assertTrue(gitignore.contains("*.keystore"))
    }

    @Test
    fun rubric_R_PS_03_manifestPermissionsQueriesAdIdAndNotifications() {
        val manifestFile = projectFile("app/src/main/AndroidManifest.xml")
        val doc = parseXml(manifestFile)
        val manifestEl = doc.documentElement
        val androidNs = "http://schemas.android.com/apk/res/android"
        val toolsNs = "http://schemas.android.com/tools"

        assertFalse("Deprecated package attribute must be omitted", manifestEl.hasAttribute("package"))

        val appEl = manifestEl.getElementsByTagName("application").item(0) as Element
        assertEquals("false", appEl.getAttributeNS(androidNs, "allowBackup"))
        assertEquals("true", appEl.getAttributeNS(androidNs, "supportsRtl"))
        assertNotEquals("true", appEl.getAttributeNS(androidNs, "usesCleartextTraffic"))
        assertNotEquals("true", appEl.getAttributeNS(androidNs, "debuggable"))

        val permissions = (0 until manifestEl.getElementsByTagName("uses-permission").length)
            .map { manifestEl.getElementsByTagName("uses-permission").item(it) as Element }
        val removedPermNames = permissions
            .filter { it.getAttributeNS(toolsNs, "node") == "remove" }
            .map { it.getAttributeNS(androidNs, "name") }
        assertTrue(removedPermNames.contains("com.google.android.gms.permission.AD_ID"))
        assertTrue(removedPermNames.contains("android.permission.ACCESS_ADSERVICES_ATTRIBUTION"))
        assertTrue(removedPermNames.contains("android.permission.ACCESS_ADSERVICES_AD_ID"))

        val queriesText = manifestFile.readText()
        assertTrue(queriesText.contains("""android:scheme="tel""""))
        assertTrue(queriesText.contains("""android:scheme="sms""""))
        assertTrue(queriesText.contains("""android:scheme="https""""))

        val activities = (0 until appEl.getElementsByTagName("activity").length)
            .map { appEl.getElementsByTagName("activity").item(it) as Element }
        val mainActivity = activities.first { it.getAttributeNS(androidNs, "name").endsWith("MainActivity") }
        assertEquals("true", mainActivity.getAttributeNS(androidNs, "exported"))
        assertEquals("adjustResize", mainActivity.getAttributeNS(androidNs, "windowSoftInputMode"))

        val services = (0 until appEl.getElementsByTagName("service").length)
            .map { appEl.getElementsByTagName("service").item(it) as Element }
        val fcmService = services.first { it.getAttributeNS(androidNs, "name").endsWith("HearUFirebaseMessagingService") }
        assertEquals("false", fcmService.getAttributeNS(androidNs, "exported"))

        val notifIconXml = projectFile("app/src/main/res/drawable/ic_notification.xml").readText()
        assertTrue(notifIconXml.contains("<vector") && notifIconXml.contains("#FFFFFFFF"))
        val colorsXml = projectFile("app/src/main/res/values/colors.xml").readText()
        assertTrue(colorsXml.contains("""name="hearu_primary""""))

        val backupXml = projectFile("app/src/main/res/xml/backup_rules.xml").readText()
        val dataXml = projectFile("app/src/main/res/xml/data_extraction_rules.xml").readText()
        assertTrue(backupXml.contains("hearu_local.db") && backupXml.contains("user_preferences.preferences_pb"))
        assertTrue(dataXml.contains("hearu_local.db") && dataXml.contains("user_preferences.preferences_pb"))
    }

    @Test
    fun rubric_R_PS_04_proguardR8KeepAndLogStrippingRules() {
        val proguard = projectFile("app/proguard-rules.pro").readText()
        listOf(
            "com.hearu.app.model.**",
            "com.hearu.app.data.local.**",
            "com.hearu.app.audio.**",
            "com.hearu.app.navigation.**",
            "BiometricHelper",
            "HearUFirebaseMessagingService",
            "RoomDatabase",
            "Entity",
            "Dao",
            "TypeConverter",
            "HiltAndroidApp",
            "AndroidEntryPoint",
            "HiltViewModel",
            "dagger.hilt.**",
            "kotlinx.serialization",
            "-assumenosideeffects"
        ).forEach { token ->
            assertTrue("proguard-rules.pro missing keep/strip rule for: $token", proguard.contains(token))
        }
        assertTrue(proguard.contains("android.util.Log"))
        assertTrue(proguard.contains("v(") && proguard.contains("d(") && proguard.contains("isLoggable("))

        val fcmSource = stripCStyleComments(
            projectFile("app/src/main/java/com/hearu/app/service/HearUFirebaseMessagingService.kt").readText()
        )
        val onNewTokenBody = extractBraceBlock(fcmSource, "fun onNewToken") ?: fcmSource.substringAfter("fun onNewToken")
        assertFalse(
            "onNewToken must never log raw FCM token",
            Regex("""Log\.\w+\([^)]*\$\{?token\b""").containsMatchIn(onNewTokenBody) ||
                Regex("""Log\.\w+\([^)]*(?:,\s*|\+\s*)token\s*[,)+]""").containsMatchIn(onNewTokenBody)
        )
    }

    @Test
    fun rubric_R_PS_05_storeListingPrivacyPolicyAndUiCopyTruthfulness() {
        val listingLines = projectFile("distribution/play_store_listing.md").readLines()
        val title = extractFirstContentLineAfterHeading(listingLines, "App Title").trim('*', '#', ' ', '\t')
        val shortDesc = extractFirstContentLineAfterHeading(listingLines, "Short Description").trim('*', '#', ' ', '\t')
        val fullDesc = extractSectionAfterHeading(listingLines, "Full Description")
        assertTrue("Title length ${title.length} must be <= 30", title.length in 1..30)
        assertTrue("Short Desc length ${shortDesc.length} must be <= 80", shortDesc.length in 1..80)
        assertTrue("Full Desc length ${fullDesc.length} must be in 100..4000", fullDesc.length in 100..4000)

        val privacyPolicy = projectFile("PRIVACY_POLICY.md").readText()
        val lowerPolicy = privacyPolicy.lowercase()
        assertTrue(lowerPolicy.contains("18+"))
        assertTrue(lowerPolicy.contains("record_audio"))
        assertTrue(lowerPolicy.contains("30 day") || lowerPolicy.contains("30-day"))
        assertTrue(lowerPolicy.contains("biometric"))
        assertTrue(lowerPolicy.contains("gemini"))
        assertTrue(lowerPolicy.contains("988"))
        assertTrue(lowerPolicy.contains("delete account"))
        assertTrue(lowerPolicy.contains("privacy@hearu.app"))

        val dbModule = projectFile("app/src/main/java/com/hearu/app/di/DatabaseModule.kt").readText()
        val buildGradle = projectFile("app/build.gradle.kts").readText()
        val usesSqlCipher = dbModule.contains("sqlcipher", ignoreCase = true) || buildGradle.contains("sqlcipher", ignoreCase = true)
        val geminiService = projectFile("app/src/main/java/com/hearu/app/ai/GeminiAiService.kt").readText()
        val configuredModel = Regex("""modelName\s*=\s*"([^"]+)"""").find(geminiService)?.groupValues?.get(1).orEmpty()

        val copyFiles = listOf(
            "app/src/main/res/values/strings.xml",
            "app/src/main/java/com/hearu/app/ui/onboarding/OnboardingScreen.kt",
            "app/src/main/java/com/hearu/app/ui/home/SeekerDashboard.kt",
            "app/src/main/java/com/hearu/app/ui/home/ProfileScreen.kt",
            "app/src/main/java/com/hearu/app/ui/chat/ChatScreen.kt",
            "PRIVACY_POLICY.md",
            "distribution/play_store_listing.md"
        )
        copyFiles.forEach { relPath ->
            val content = projectFile(relPath).readText()
            assertFalse("$relPath must not claim 'cryptographically blurred'", content.contains("cryptographically blurred", ignoreCase = true))
            if (!usesSqlCipher) {
                assertFalse("$relPath must not falsely claim 'remains encrypted'", content.contains("remains encrypted", ignoreCase = true))
                assertFalse("$relPath must not falsely claim 'Encrypted local database'", content.contains("Encrypted local database", ignoreCase = true))
            }
            if (!configuredModel.contains("3.7")) {
                assertFalse("$relPath must not reference 'Gemini 3.7'", content.contains("Gemini 3.7", ignoreCase = true))
            }
        }

        val authVm = buildAuthViewModel()
        composeTestRule.setContent {
            HearUTheme {
                LoginScreen(viewModel = authVm)
            }
        }
        assertTrue(
            "LoginScreen must expose Privacy Policy link/button",
            composeTestRule.onAllNodesWithText("Privacy Policy", substring = true, ignoreCase = true)
                .fetchSemanticsNodes().isNotEmpty()
        )
    }

    @Test
    fun rubric_R_PS_06_backendRulesFunctionsIndexesAndCiReleaseGate() {
        val rules = stripCStyleComments(projectFile("firestore.rules").readText())
        val usersBlock = extractMatchBlock(rules, "/users/{userId}") ?: error("/users/{userId} missing")
        assertTrue(usersBlock.contains("isOwner(userId)"))
        assertTrue(Regex("""allow\s+delete\s*:\s*if\s+isOwner\(userId\)""").containsMatchIn(usersBlock))
        assertTrue(usersBlock.contains("blockedUsers") && usersBlock.contains("500"))
        assertTrue(usersBlock.contains("'role'") && usersBlock.contains("'seeker'") && usersBlock.contains("'giver'") && usersBlock.contains("'both'"))

        val sessionsBlock = extractMatchBlock(rules, "/sessions/{sessionId}") ?: error("/sessions/{sessionId} missing")
        assertTrue(Regex("""allow\s+create\s*:\s*if\s+false""").containsMatchIn(sessionsBlock))
        assertTrue(Regex("""allow\s+delete\s*:\s*if\s+false""").containsMatchIn(sessionsBlock))

        val messagesBlock = extractMatchBlock(rules, "/messages/{messageId}") ?: error("/messages/{messageId} missing")
        assertTrue(messagesBlock.contains("senderId == request.auth.uid"))
        assertTrue(Regex("""allow\s+update\s*,\s*delete\s*:\s*if\s+false""").containsMatchIn(messagesBlock))

        val reportsBlock = extractMatchBlock(rules, "/reports/{reportId}") ?: error("/reports/{reportId} missing")
        assertTrue(Regex("""allow\s+read\s*,\s*update\s*,\s*delete\s*:\s*if\s+false""").containsMatchIn(reportsBlock))

        val functionsJs = stripCStyleComments(projectFile("functions/index.js").readText())
        val matchFn = extractBraceBlock(functionsJs, "exports.matchSeekerWithGiver")!!
        val purgeFn = extractBraceBlock(functionsJs, "exports.purgeExpiredChats")!!
        val reportFn = extractBraceBlock(functionsJs, "exports.onReportSubmitted")!!
        assertTrue(matchFn.contains("!context.auth") && matchFn.contains("blockedUsers") && matchFn.contains("runTransaction"))
        assertTrue(Regex("""\b(while|for)\b""").containsMatchIn(purgeFn))
        assertFalse(reportFn.contains("snap.data()") || reportFn.contains("reporterId") || reportFn.contains("reportedUserId"))

        assertTrue(projectFile("firebase.json").readText().contains("\"firestore\""))
        assertTrue(projectFile("firestore.indexes.json").readText().contains("\"indexes\""))
        assertTrue(projectFile("functions/package.json").readText().contains("\"dependencies\""))

        val ciYml = projectFile(".github/workflows/android_ci.yml").readText()
        assertTrue(ciYml.contains("testDebugUnitTest"))
        assertTrue(ciYml.contains("lintRelease"))
        assertTrue(ciYml.contains("assembleRelease"))
    }

    @Test
    fun rubric_R_PS_07_resourceAndLauncherDensityHygiene() {
        assertTrue("distribution/ic_launcher_store.png must exist", projectFile("distribution/ic_launcher_store.png").isFile)
        val resDir = projectFile("app/src/main/res")
        val storeInRes = resDir.walkTopDown().filter { it.isFile && it.name.startsWith("ic_launcher_store") }.toList()
        assertTrue("Store PNGs must not live in res/: $storeInRes", storeInRes.isEmpty())

        listOf("mipmap-mdpi", "mipmap-hdpi", "mipmap-xhdpi", "mipmap-xxhdpi", "mipmap-xxxhdpi").forEach { density ->
            listOf("ic_launcher.png", "ic_launcher_round.png", "ic_launcher_foreground.png").forEach { file ->
                assertTrue("Missing $density/$file", projectFile("app/src/main/res/$density/$file").isFile)
            }
        }
        assertTrue(projectFile("app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml").isFile)
        assertTrue(projectFile("app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml").isFile)

        val duplicateDrawables = resDir.walkTopDown().filter {
            it.isFile && it.parentFile?.name?.startsWith("drawable") == true && it.nameWithoutExtension == "ic_launcher_foreground"
        }.toList()
        assertTrue("Duplicate drawable*/ic_launcher_foreground* forbidden: $duplicateDrawables", duplicateDrawables.isEmpty())

        val themesXml = projectFile("app/src/main/res/values/themes.xml").readText()
        assertFalse(themesXml.contains("android:statusBarColor"))
        assertFalse(themesXml.contains("android:navigationBarColor"))
    }

    // =========================================================================
    // UI / UX Quality Rubric (R-UX-01 .. R-UX-05)
    // =========================================================================

    @Test
    fun rubric_R_UX_01_edgeToEdgeAndKeyboardInsetsCompliance() {
        val mainSource = projectFile("app/src/main/java/com/hearu/app/MainActivity.kt").readText()
        val loginSource = projectFile("app/src/main/java/com/hearu/app/ui/auth/LoginScreen.kt").readText()
        val roleSource = projectFile("app/src/main/java/com/hearu/app/ui/auth/RoleSelectionScreen.kt").readText()
        val aiChatSource = projectFile("app/src/main/java/com/hearu/app/ui/chat/AIChatScreen.kt").readText()

        assertTrue("MainActivity must call enableEdgeToEdge()", mainSource.contains("enableEdgeToEdge()"))
        assertTrue(
            "BiometricLockOverlay must handle window insets",
            mainSource.contains("systemBarsPadding()") || mainSource.contains("safeDrawingPadding()") || mainSource.contains("Scaffold")
        )
        assertTrue(
            "LoginScreen must handle systemBarsPadding() / safeDrawingPadding() and imePadding()",
            (loginSource.contains("systemBarsPadding()") || loginSource.contains("safeDrawingPadding()")) &&
                loginSource.contains("imePadding()")
        )
        assertTrue(
            "RoleSelectionScreen must handle systemBarsPadding() / safeDrawingPadding() / Scaffold",
            roleSource.contains("systemBarsPadding()") || roleSource.contains("safeDrawingPadding()") || roleSource.contains("Scaffold")
        )
        assertTrue("AIChatScreen bottomBar must apply imePadding()", aiChatSource.contains("imePadding()"))

        val authVm = buildAuthViewModel()
        composeTestRule.setContent {
            HearUTheme { LoginScreen(viewModel = authVm) }
        }
        composeTestRule.onNodeWithTag("anonymous_login_button").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun rubric_R_UX_02_lightAndDarkThemeContrastAndReadability() {
        val checkedRatios = mutableListOf<Pair<String, Float>>()
        composeTestRule.setContent {
            HearUTheme(darkTheme = false) {
                val light = MaterialTheme.colorScheme
                checkedRatios += "light onPrimary/primary" to contrastRatio(light.onPrimary, light.primary)
                checkedRatios += "light onBackground/background" to contrastRatio(light.onBackground, light.background)
                checkedRatios += "light onSurface/surface" to contrastRatio(light.onSurface, light.surface)
                checkedRatios += "light onError/error" to contrastRatio(light.onError, light.error)
                checkedRatios += "light onPrimaryContainer/primaryContainer" to contrastRatio(light.onPrimaryContainer, light.primaryContainer)
            }
            HearUTheme(darkTheme = true) {
                val dark = MaterialTheme.colorScheme
                checkedRatios += "dark onPrimary/primary" to contrastRatio(dark.onPrimary, dark.primary)
                checkedRatios += "dark onBackground/background" to contrastRatio(dark.onBackground, dark.background)
                checkedRatios += "dark onSurface/surface" to contrastRatio(dark.onSurface, dark.surface)
                checkedRatios += "dark onError/error" to contrastRatio(dark.onError, dark.error)
                checkedRatios += "dark onPrimaryContainer/primaryContainer" to contrastRatio(dark.onPrimaryContainer, dark.primaryContainer)
            }
        }
        checkedRatios.forEach { (label, ratio) ->
            assertTrue("$label contrast $ratio must be >= 4.5", ratio >= 4.5f)
        }

        val seekerSource = projectFile("app/src/main/java/com/hearu/app/ui/home/SeekerDashboard.kt").readText()
        val voicePlayerSource = projectFile("app/src/main/java/com/hearu/app/ui/chat/components/VoiceNotePlayer.kt").readText()
        assertFalse(
            "SeekerDashboard must not hardcode Color.White on MaterialTheme.colorScheme.primary",
            seekerSource.contains("color = Color.White") || seekerSource.contains("tint = Color.White")
        )
        assertFalse(
            "VoiceNotePlayer must not hardcode Color.White waveform bars on primary bubble",
            voicePlayerSource.contains("Color.White")
        )
    }

    @Test
    fun rubric_R_UX_03_fontScaling_roleSelectionScreen() {
        assertScrollableUnderLargeFont("I want to listen (Giver)") {
            RoleSelectionScreen(onRoleSelected = {})
        }
    }

    @Test
    fun rubric_R_UX_03_fontScaling_loginScreen() {
        val authVm = buildAuthViewModel()
        assertScrollableUnderLargeFont("Continue as Anonymous Guest") {
            LoginScreen(viewModel = authVm)
        }
    }

    @Test
    fun rubric_R_UX_03_fontScaling_profileScreen() {
        val authVm = buildAuthViewModel()
        assertScrollableUnderLargeFont("Return to Dashboard") {
            ProfileScreen(onNavigateBack = {}, authViewModel = authVm)
        }
    }

    @Test
    fun rubric_R_UX_03_fontScaling_emergencyScreen() {
        assertScrollableUnderLargeFont("Take a 2-Minute Calming Breath") {
            EmergencyScreen(onNavigateBack = {})
        }
    }

    @Test
    fun rubric_R_UX_03_fontScaling_breathingExerciseScreen() {
        assertScrollableUnderLargeFont("Begin Session") {
            BreathingExerciseScreen(onNavigateBack = {})
        }
    }

    @Test
    fun rubric_R_UX_03_touchTargets_loginScreen() {
        val authVm = buildAuthViewModel()
        composeTestRule.setContent {
            HearUTheme {
                LoginScreen(viewModel = authVm)
            }
        }
        composeTestRule.onNodeWithTag("login_button").assertHeightIsAtLeast(48.dp)
        composeTestRule.onNodeWithTag("anonymous_login_button").assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun rubric_R_UX_03_touchTargets_voiceNoteBubble() {
        composeTestRule.setContent {
            HearUTheme {
                VoiceNoteBubble(durationSeconds = 15, isMine = true)
            }
        }
        composeTestRule.onNodeWithContentDescription("Play")
            .assertHeightIsAtLeast(48.dp)
            .assertWidthIsAtLeast(48.dp)
    }

    @Test
    fun rubric_R_UX_03_contentDescriptionsAndTouchTargets_seekerDashboard() {
        val matchVm = buildMatchViewModel()
        composeTestRule.setContent {
            HearUTheme {
                SeekerDashboard(
                    onNavigateToAIChat = {},
                    onNavigateToMatch = { _, _ -> },
                    matchViewModel = matchVm
                )
            }
        }
        composeTestRule.onNodeWithContentDescription("Crisis 24/7").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Profile").assertIsDisplayed()
        composeTestRule.onNodeWithText("Find a Listener Now").assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun rubric_R_UX_04_emergencyScreenIntentFallbacks() {
        // 1. Live Compose ActivityNotFoundException safety across all EmergencyScreen triggers
        composeTestRule.setContent {
            val base = LocalContext.current
            CompositionLocalProvider(LocalContext provides noHandlersContext(base)) {
                HearUTheme {
                    EmergencyScreen(onNavigateBack = {}, onNavigateToBreathing = {})
                }
            }
        }
        composeTestRule.onAllNodesWithText("Call Now")[0].performClick()
        composeTestRule.onNodeWithText("Text 988").performClick()
        composeTestRule.onNodeWithText("Text HOME to 741741").performScrollTo().performClick()
        composeTestRule.onNodeWithText("Call 988 (Press 1)").performScrollTo().performClick()
        composeTestRule.onNodeWithText("Text 838255").performScrollTo().performClick()
        composeTestRule.onAllNodesWithText("Call Now")[1].performScrollTo().performClick()
        composeTestRule.onNodeWithText("Text START").performScrollTo().performClick()
        composeTestRule.onNodeWithText("Visit Befrienders Worldwide Directory").performScrollTo().performClick()
        composeTestRule.onNodeWithText("24/7 Crisis & Emergency Hub").assertIsDisplayed()
    }

    @Test
    fun rubric_R_UX_04_crisisSupportDialogIntentFallbacks() {
        composeTestRule.setContent {
            val base = LocalContext.current
            CompositionLocalProvider(LocalContext provides noHandlersContext(base)) {
                HearUTheme {
                    CrisisSupportDialog(onDismiss = {})
                }
            }
        }
        composeTestRule.onNodeWithText("Call 988 Crisis Lifeline").performClick()
        composeTestRule.onNodeWithText("Chat with Lifeline Online").performClick()
        composeTestRule.onNodeWithText("Return to App").performClick()
    }

    @Test
    fun rubric_R_UX_04_offlineAiCrisisPreservationAndAlignment() {
        // 3. Offline AI crisis preservation + "ai_companion" left alignment
        val aiVm = AIChatViewModel(GeminiAiService(), rolePreferences, mockAuth)
        composeTestRule.setContent {
            HearUTheme {
                AIChatScreen(onNavigateBack = {}, viewModel = aiVm)
            }
        }
        composeTestRule.onNode(hasSetTextAction()).performTextInput("I want to kill myself")
        composeTestRule.onNodeWithContentDescription("Send").performClick()
        composeTestRule.waitUntil(timeoutMillis = 10_000L) {
            !aiVm.isLoading.value && aiVm.messages.value.size >= 3
        }
        composeTestRule.waitForIdle()
        assertTrue("Offline crisis keyword must trigger crisisEvent=true", aiVm.crisisEvent.value)
        val aiWelcomeBounds = composeTestRule.onNodeWithText(
            "Hello, I'm your HearU AI Companion.",
            substring = true
        ).fetchSemanticsNode().boundsInRoot
        val userBounds = composeTestRule.onNodeWithText("I want to kill myself")
            .fetchSemanticsNode().boundsInRoot
        assertTrue(
            "ai_companion bubble must align LEFT of user bubble",
            aiWelcomeBounds.left < userBounds.left
        )
    }

    @Test
    fun rubric_R_UX_04_cancellationMatchErrorAndBiometricRetry() {
        // 2. CancellationException rethrow across repositories & GeminiAiService
        listOf(
            "app/src/main/java/com/hearu/app/repository/AuthRepository.kt",
            "app/src/main/java/com/hearu/app/repository/ChatRepository.kt",
            "app/src/main/java/com/hearu/app/repository/UserRepository.kt",
            "app/src/main/java/com/hearu/app/repository/ModerationRepository.kt",
            "app/src/main/java/com/hearu/app/ai/GeminiAiService.kt"
        ).forEach { rel ->
            val src = projectFile(rel).readText()
            assertTrue("$rel must rethrow CancellationException", src.contains("CancellationException"))
        }

        // 4. MatchState.Error without fake demo match + BiometricHelper retry preservation
        whenever(mockCallable.call(any())).thenReturn(Tasks.forException(RuntimeException("Offline")))
        val matchVm = buildMatchViewModel()
        matchVm.startMatch(listOf("Anxiety 😰"))
        mainDispatcherRule.testDispatcher.scheduler.advanceTimeBy(3000L)
        drainAsync()
        assertTrue("Matchmaking failure must produce MatchState.Error", matchVm.matchState.value is MatchState.Error)

        val biometricSource = projectFile("app/src/main/java/com/hearu/app/auth/BiometricHelper.kt").readText()
        val authFailedBlock = biometricSource.substringAfter("fun onAuthenticationFailed").substringBefore("override fun")
        assertFalse(
            "BiometricHelper.onAuthenticationFailed must not call onError and abort retry",
            authFailedBlock.contains("onError(")
        )
    }

    @Test
    fun rubric_R_UX_05_runtimePermissionsAndChatModeration() {
        // 1. Runtime permissions & VoiceNoteManager truthful start failure
        val chatSource = projectFile("app/src/main/java/com/hearu/app/ui/chat/ChatScreen.kt").readText()
        val voiceSource = projectFile("app/src/main/java/com/hearu/app/audio/VoiceNoteManager.kt").readText()
        val seekerSource = projectFile("app/src/main/java/com/hearu/app/ui/home/SeekerDashboard.kt").readText()
        val giverSource = projectFile("app/src/main/java/com/hearu/app/ui/home/GiverDashboard.kt").readText()
        val navSource = projectFile("app/src/main/java/com/hearu/app/navigation/HearUNavigation.kt").readText()

        assertTrue("ChatScreen must gate recording on RECORD_AUDIO permission", chatSource.contains("RECORD_AUDIO"))
        assertFalse(
            "VoiceNoteManager must not fake recorderStarted = true inside catch",
            Regex("""catch\s*\([^)]*\)\s*\{[^}]*recorderStarted\s*=\s*true""", RegexOption.DOT_MATCHES_ALL)
                .containsMatchIn(voiceSource)
        )
        assertTrue(
            "POST_NOTIFICATIONS runtime request must be wired post-auth",
            seekerSource.contains("POST_NOTIFICATIONS") ||
                giverSource.contains("POST_NOTIFICATIONS") ||
                navSource.contains("POST_NOTIFICATIONS")
        )

        // 2. Moderation & Chat ownership alignment + Block & Leave
        val reportLambda = chatSource.substringAfter("onSubmitReport =").substringBefore(")")
        assertTrue(
            "ChatScreen ReportUserDialog onSubmitReport must submit report via ViewModel",
            reportLambda.contains("viewModel")
        )
        val chatVm = ChatViewModel(mockChatRepository, mockAuth, mockVoiceNoteManager)
        composeTestRule.setContent {
            HearUTheme {
                ChatScreen(
                    sessionId = "s_rubric",
                    userId = "peer_99",
                    onNavigateBack = {},
                    viewModel = chatVm
                )
            }
        }
        composeTestRule.onNodeWithContentDescription("Options").performClick()
        composeTestRule.onNodeWithText(context.getString(R.string.block_user)).assertIsDisplayed()
    }

    @Test
    fun rubric_R_UX_05_profileRoleSwitcherPrivacyDeletionAndSignOutReset() {
        // 3. ProfileScreen Role Switcher, Privacy Policy, Delete Account + HearUNavigation sign-out reset
        val navSource = projectFile("app/src/main/java/com/hearu/app/navigation/HearUNavigation.kt").readText()
        val authVm = buildAuthViewModel()
        composeTestRule.setContent {
            HearUTheme {
                ProfileScreen(onNavigateBack = {}, authViewModel = authVm)
            }
        }
        assertTrue(
            "ProfileScreen must expose Role Switcher",
            composeTestRule.onAllNodes(
                hasText("Switch Role", substring = true, ignoreCase = true) or
                    hasText("Switch to", substring = true, ignoreCase = true) or
                    hasText("Change Role", substring = true, ignoreCase = true) or
                    (hasText("Role", substring = true, ignoreCase = true) and hasClickAction())
            ).fetchSemanticsNodes().isNotEmpty()
        )
        assertTrue(
            "ProfileScreen must expose Privacy Policy",
            composeTestRule.onAllNodesWithText("Privacy Policy", substring = true, ignoreCase = true)
                .fetchSemanticsNodes().isNotEmpty()
        )
        assertTrue(
            "ProfileScreen must expose Delete Account",
            composeTestRule.onAllNodesWithText("Delete Account", substring = true, ignoreCase = true)
                .fetchSemanticsNodes().isNotEmpty()
        )
        assertTrue(
            "HearUNavigation must reset backstack to Login when AuthState returns to Idle",
            navSource.contains("Screen.Login.route") && navSource.contains("AuthState.Idle")
        )
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private fun parseXml(file: File) = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = true
    }.newDocumentBuilder().parse(file)

    private fun stripCStyleComments(source: String): String {
        val out = StringBuilder(source.length)
        var i = 0
        var inSingle = false
        var inDouble = false
        while (i < source.length) {
            val c = source[i]
            val next = if (i + 1 < source.length) source[i + 1] else '\u0000'
            if (!inSingle && !inDouble && c == '/' && next == '/') {
                i += 2
                while (i < source.length && source[i] != '\n') i++
                continue
            }
            if (!inSingle && !inDouble && c == '/' && next == '*') {
                i += 2
                while (i + 1 < source.length && !(source[i] == '*' && source[i + 1] == '/')) i++
                i += 2
                continue
            }
            if (c == '\\' && (inSingle || inDouble) && i + 1 < source.length) {
                out.append(c).append(next)
                i += 2
                continue
            }
            if (c == '"' && !inSingle) inDouble = !inDouble
            else if (c == '\'' && !inDouble) inSingle = !inSingle
            out.append(c)
            i++
        }
        return out.toString()
    }

    private fun extractBraceBlock(text: String, headerToken: String): String? {
        val startIdx = text.indexOf(headerToken)
        if (startIdx < 0) return null
        val openBrace = text.indexOf('{', startIdx)
        if (openBrace < 0) return null
        return sliceBalancedBraces(text, openBrace)
    }

    private fun extractRegexBraceBlock(text: String, pattern: Regex): String? {
        val match = pattern.find(text) ?: return null
        val openBrace = text.indexOf('{', match.range.first)
        if (openBrace < 0) return null
        return sliceBalancedBraces(text, openBrace)
    }

    private fun sliceBalancedBraces(text: String, openBrace: Int): String? {
        var depth = 0
        for (i in openBrace until text.length) {
            when (text[i]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return text.substring(openBrace + 1, i)
                }
            }
        }
        return null
    }

    private fun extractMatchBlock(rules: String, pathFragment: String): String? {
        val token = "match $pathFragment"
        val matchIdx = rules.indexOf(token)
        if (matchIdx < 0) return null
        val openBrace = rules.indexOf('{', matchIdx + token.length)
        if (openBrace < 0) return null
        return sliceBalancedBraces(rules, openBrace)
    }

    private fun extractFirstContentLineAfterHeading(lines: List<String>, headingFragment: String): String {
        val headingIndex = lines.indexOfFirst { it.startsWith("#") && it.contains(headingFragment) }
        assertTrue("Heading '$headingFragment' missing", headingIndex >= 0)
        return lines.drop(headingIndex + 1)
            .firstOrNull { line ->
                val trimmed = line.trim()
                trimmed.isNotEmpty() && trimmed != "---" && !trimmed.startsWith("#")
            } ?: error("No content line found under '$headingFragment'")
    }

    private fun extractSectionAfterHeading(lines: List<String>, headingFragment: String): String {
        val headingIndex = lines.indexOfFirst { it.startsWith("#") && it.contains(headingFragment) }
        assertTrue("Heading '$headingFragment' missing", headingIndex >= 0)
        val sectionLines = mutableListOf<String>()
        for (i in (headingIndex + 1) until lines.size) {
            val line = lines[i]
            if (line.startsWith("## ") || line.trim() == "---") break
            sectionLines.add(line)
        }
        return sectionLines.joinToString("\n").trim()
    }
}
