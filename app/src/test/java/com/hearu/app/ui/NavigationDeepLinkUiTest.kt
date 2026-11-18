package com.hearu.app.ui

import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.hearu.app.HearUApplication
import com.hearu.app.MainActivity
import com.hearu.app.data.RolePreferences
import com.hearu.app.data.dataStore
import com.hearu.app.service.HearUNotificationConfig
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = HearUApplication::class, sdk = [34], qualifiers = "w411dp-h915dp")
class NavigationDeepLinkUiTest {

    @get:Rule
    val composeTestRule = createEmptyComposeRule()

    private lateinit var appContext: HearUApplication

    @Before
    fun setUp() {
        appContext = RuntimeEnvironment.getApplication() as HearUApplication
        if (FirebaseApp.getApps(appContext).isEmpty()) {
            val options = FirebaseOptions.Builder()
                .setApplicationId("1:100000000000:android:abcdef0123456789")
                .setApiKey("AIzaSyFakeKeyForInitialScaffoldingTemplate")
                .setProjectId("hearu-production")
                .build()
            FirebaseApp.initializeApp(appContext, options)
        }
        runBlocking {
            appContext.dataStore.edit { it.clear() }
            val prefs = RolePreferences(appContext)
            prefs.setOnboardingCompleted(true)
            prefs.setActiveRole("seeker")
            prefs.setBiometricEnabled(false)
        }
        setFirebaseCurrentUser(
            mock<FirebaseUser>().apply {
                whenever(uid).thenReturn("uid_deeplink_test")
            }
        )
    }

    @After
    fun tearDown() {
        setFirebaseCurrentUser(null)
        runBlocking {
            appContext.dataStore.edit { it.clear() }
        }
    }

    private fun setFirebaseCurrentUser(user: FirebaseUser?) {
        val auth = FirebaseAuth.getInstance()
        val userField = FirebaseAuth::class.java.declaredFields.firstOrNull {
            FirebaseUser::class.java.isAssignableFrom(it.type)
        } ?: error("Unable to locate FirebaseUser field on FirebaseAuth")
        userField.isAccessible = true
        userField.set(auth, user)
        if (user != null) {
            assertNotNull(auth.currentUser)
        }
    }

    @Test
    fun mainActivity_deepLinkDoesNotReplayAfterBackAndRecreate_butRedeliversSameSessionViaOnNewIntent() {
        val launchIntent = Intent(appContext, MainActivity::class.java).apply {
            putExtra(HearUNotificationConfig.EXTRA_SESSION_ID, "sess_replay_guard_1")
        }

        ActivityScenario.launch<MainActivity>(launchIntent).use { scenario ->
            composeTestRule.waitUntil(timeoutMillis = 5_000L) {
                composeTestRule.onAllNodesWithText("Empathetic Listener")
                    .fetchSemanticsNodes()
                    .isNotEmpty()
            }
            composeTestRule.onNodeWithText("Empathetic Listener").assertIsDisplayed()

            composeTestRule.onNodeWithContentDescription("Back").performClick()
            composeTestRule.waitUntil(timeoutMillis = 5_000L) {
                composeTestRule.onAllNodesWithText("Connect with a Human Listener")
                    .fetchSemanticsNodes()
                    .isNotEmpty()
            }
            composeTestRule.onNodeWithText("Connect with a Human Listener").assertIsDisplayed()

            // Recreate after Back must NOT replay the consumed deep link
            scenario.recreate()
            composeTestRule.waitUntil(timeoutMillis = 5_000L) {
                composeTestRule.onAllNodesWithText("Connect with a Human Listener")
                    .fetchSemanticsNodes()
                    .isNotEmpty()
            }
            composeTestRule.onNodeWithText("Connect with a Human Listener").assertIsDisplayed()
            composeTestRule.onNodeWithText("Empathetic Listener").assertDoesNotExist()

            // Delivering a fresh notification with the SAME sessionId via onNewIntent opens Chat again
            scenario.onActivity { activity ->
                val newIntent = Intent(appContext, MainActivity::class.java).apply {
                    putExtra(HearUNotificationConfig.EXTRA_SESSION_ID, "sess_replay_guard_1")
                }
                InstrumentationRegistry.getInstrumentation().callActivityOnNewIntent(activity, newIntent)
            }

            composeTestRule.waitUntil(timeoutMillis = 5_000L) {
                composeTestRule.onAllNodesWithText("Empathetic Listener")
                    .fetchSemanticsNodes()
                    .isNotEmpty()
            }
            composeTestRule.onNodeWithText("Empathetic Listener").assertIsDisplayed()

            // Back + recreate again must NOT replay the re-delivered deep link
            composeTestRule.onNodeWithContentDescription("Back").performClick()
            composeTestRule.waitUntil(timeoutMillis = 5_000L) {
                composeTestRule.onAllNodesWithText("Connect with a Human Listener")
                    .fetchSemanticsNodes()
                    .isNotEmpty()
            }
            scenario.recreate()
            composeTestRule.waitUntil(timeoutMillis = 5_000L) {
                composeTestRule.onAllNodesWithText("Connect with a Human Listener")
                    .fetchSemanticsNodes()
                    .isNotEmpty()
            }
            composeTestRule.onNodeWithText("Connect with a Human Listener").assertIsDisplayed()
            composeTestRule.onNodeWithText("Empathetic Listener").assertDoesNotExist()
        }
    }
}
