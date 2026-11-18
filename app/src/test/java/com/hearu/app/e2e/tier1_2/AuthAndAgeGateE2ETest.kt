package com.hearu.app.e2e.tier1_2

import android.app.Application
import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import com.google.firebase.auth.FirebaseUser
import com.hearu.app.data.RolePreferences
import com.hearu.app.e2e.harness.E2ETestHarness
import com.hearu.app.e2e.harness.MainDispatcherRule
import com.hearu.app.navigation.HearUNavigation
import com.hearu.app.repository.AuthRepository
import com.hearu.app.repository.AuthResult
import com.hearu.app.ui.auth.AuthMode
import com.hearu.app.ui.auth.AuthState
import com.hearu.app.ui.auth.AuthViewModel
import com.hearu.app.ui.auth.LoginScreen
import com.hearu.app.ui.theme.HearUTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class AuthAndAgeGateE2ETest {

    @get:Rule(order = 0)
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule(order = 1)
    val composeTestRule = createComposeRule()

    private lateinit var context: Context
    private lateinit var rolePreferences: RolePreferences
    private lateinit var mockAuthRepository: AuthRepository
    private lateinit var mockUser: FirebaseUser
    private lateinit var authViewModel: AuthViewModel

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        runBlocking {
            E2ETestHarness.clearRolePreferences(context)
        }
        rolePreferences = E2ETestHarness.createRolePreferences(context)
        mockAuthRepository = mock()
        mockUser = mock()
        whenever(mockAuthRepository.currentUser).thenReturn(null)
        authViewModel = AuthViewModel(mockAuthRepository, rolePreferences)
    }

    @After
    fun tearDown() {
        runBlocking {
            E2ETestHarness.clearRolePreferences(context)
        }
    }

    // ==================== TIER 1: HAPPY PATHS ====================

    @Test
    fun tier1_signInWithValidCredentials_submitsTrimmedEmailAndAuthenticates() {
        runBlocking {
            whenever(mockAuthRepository.signInWithEmail("user@hearu.app", "SecurePass123!"))
                .thenReturn(AuthResult.Success(mockUser))
        }

        composeTestRule.setContent {
            HearUTheme {
                LoginScreen(
                    viewModel = authViewModel,
                    onLoginSubmit = { email, pass -> authViewModel.login(email, pass) }
                )
            }
        }

        composeTestRule.onNodeWithTag("email_field").performScrollTo().performTextInput("  user@hearu.app  ")
        composeTestRule.onNodeWithTag("password_field").performScrollTo().performTextInput("SecurePass123!")
        composeTestRule.onNodeWithTag("login_button").performScrollTo().assertIsEnabled().performClick()

        composeTestRule.waitForIdle()
        assertEquals(AuthState.Authenticated, authViewModel.authState.value)
        runBlocking {
            verify(mockAuthRepository).signInWithEmail("user@hearu.app", "SecurePass123!")
        }
    }

    @Test
    fun tier1_signUpMode_withMatchingPasswordsAnd18PlusChecked_registersSuccessfully() {
        runBlocking {
            whenever(mockAuthRepository.signUpWithEmail("new@hearu.app", "StrongPass99!"))
                .thenReturn(AuthResult.Success(mockUser))
        }

        composeTestRule.setContent {
            HearUTheme {
                LoginScreen(
                    viewModel = authViewModel,
                    onSignUpSubmit = { email, pass -> authViewModel.register(email, pass) }
                )
            }
        }

        composeTestRule.onNodeWithText("Sign Up").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("confirm_password_field").performScrollTo().assertIsDisplayed()

        composeTestRule.onNodeWithTag("email_field").performScrollTo().performTextInput("new@hearu.app")
        composeTestRule.onNodeWithTag("password_field").performScrollTo().performTextInput("StrongPass99!")
        composeTestRule.onNodeWithTag("confirm_password_field").performScrollTo().performTextInput("StrongPass99!")
        composeTestRule.onNodeWithTag("age_gate_checkbox").performScrollTo().performClick()

        composeTestRule.onNodeWithTag("signup_button").performScrollTo().assertIsEnabled().performClick()
        composeTestRule.waitForIdle()

        assertEquals(AuthState.Authenticated, authViewModel.authState.value)
        runBlocking {
            verify(mockAuthRepository).signUpWithEmail("new@hearu.app", "StrongPass99!")
        }
    }

    @Test
    fun tier1_anonymousGuest_confirms18PlusDialog_authenticatesAnonymously() {
        runBlocking {
            whenever(mockAuthRepository.signInAnonymously())
                .thenReturn(AuthResult.Success(mockUser))
        }

        composeTestRule.setContent {
            HearUTheme {
                LoginScreen(
                    viewModel = authViewModel,
                    onAnonymousSubmit = { authViewModel.signInAnonymously() }
                )
            }
        }

        composeTestRule.onNodeWithTag("anonymous_login_button").performScrollTo().performClick()
        composeTestRule.onNodeWithText("18+ Age Requirement").assertIsDisplayed()
        composeTestRule.onNodeWithText("I am 18 or Older").performClick()

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("18+ Age Requirement").assertDoesNotExist()
        assertEquals(AuthState.Authenticated, authViewModel.authState.value)
        runBlocking {
            verify(mockAuthRepository).signInAnonymously()
        }
    }

    @Test
    fun tier1_passwordVisibilityToggle_togglesMaskAndContentDescription() {
        composeTestRule.setContent {
            HearUTheme {
                LoginScreen(viewModel = authViewModel)
            }
        }

        composeTestRule.onNodeWithTag("password_field").performScrollTo().performTextInput("MySecretPass1")
        composeTestRule.onNodeWithContentDescription("Show password").performScrollTo().performClick()
        composeTestRule.onNodeWithContentDescription("Hide password").performScrollTo().assertIsDisplayed()

        composeTestRule.onNodeWithContentDescription("Hide password").performScrollTo().performClick()
        composeTestRule.onNodeWithContentDescription("Show password").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun tier1_modeToggleBetweenSignInAndSignUp_viaTabAndBottomTextButton() {
        composeTestRule.setContent {
            HearUTheme {
                LoginScreen(viewModel = authViewModel)
            }
        }

        composeTestRule.onNodeWithText("Don't have an account? Sign Up").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("confirm_password_field").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("age_gate_checkbox").performScrollTo().assertIsDisplayed()

        composeTestRule.onNodeWithText("Already have an account? Log In").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("login_button").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("confirm_password_field").assertDoesNotExist()
    }

    @Test
    fun tier1_authNavigation_loginSuccessRoutesToRoleSelectionWhenNoRoleSaved() {
        runBlocking {
            rolePreferences.setOnboardingCompleted(true)
            whenever(mockAuthRepository.signInWithEmail("seeker@hearu.app", "ValidPass123!"))
                .thenReturn(AuthResult.Success(mockUser))
        }
        val navViewModel = AuthViewModel(mockAuthRepository, rolePreferences)

        composeTestRule.setContent {
            HearUTheme {
                HearUNavigation(authViewModel = navViewModel)
            }
        }

        composeTestRule.waitUntil(timeoutMillis = 5_000L) {
            runCatching {
                composeTestRule.onNodeWithTag("email_field").assertIsDisplayed()
                true
            }.getOrDefault(false)
        }

        composeTestRule.onNodeWithTag("email_field").performScrollTo().performTextInput("seeker@hearu.app")
        composeTestRule.onNodeWithTag("password_field").performScrollTo().performTextInput("ValidPass123!")
        composeTestRule.onNodeWithTag("login_button").performScrollTo().performClick()

        composeTestRule.waitUntil(timeoutMillis = 5_000L) {
            runCatching {
                composeTestRule.onNodeWithText("I need support (Seeker)").assertIsDisplayed()
                true
            }.getOrDefault(false)
        }
        composeTestRule.onNodeWithText("I want to listen (Giver)").assertIsDisplayed()
    }

    // ==================== TIER 2: BOUNDARY / CORNER / ERROR ====================

    @Test
    fun tier2_anonymousGuest_cancelAgeGateDialog_blocksAuthentication() {
        composeTestRule.setContent {
            HearUTheme {
                LoginScreen(
                    viewModel = authViewModel,
                    onAnonymousSubmit = { authViewModel.signInAnonymously() }
                )
            }
        }

        composeTestRule.onNodeWithTag("anonymous_login_button").performScrollTo().performClick()
        composeTestRule.onNodeWithText("18+ Age Requirement").assertIsDisplayed()
        composeTestRule.onNodeWithText("Cancel").performClick()

        composeTestRule.onNodeWithText("18+ Age Requirement").assertDoesNotExist()
        assertEquals(AuthState.Idle, authViewModel.authState.value)
        runBlocking {
            verify(mockAuthRepository, never()).signInAnonymously()
        }
    }

    @Test
    fun tier2_signUp_without18PlusChecked_keepsSubmitDisabled() {
        composeTestRule.setContent {
            HearUTheme {
                LoginScreen(
                    viewModel = authViewModel,
                    initialMode = AuthMode.SIGN_UP
                )
            }
        }

        composeTestRule.onNodeWithTag("email_field").performScrollTo().performTextInput("adult@hearu.app")
        composeTestRule.onNodeWithTag("password_field").performScrollTo().performTextInput("ValidPass123!")
        composeTestRule.onNodeWithTag("confirm_password_field").performScrollTo().performTextInput("ValidPass123!")

        composeTestRule.onNodeWithTag("signup_button").performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun tier2_signUp_mismatchedPasswordsAndShortPassword_displayInlineErrors() {
        var signUpCalled = 0
        composeTestRule.setContent {
            HearUTheme {
                LoginScreen(
                    viewModel = authViewModel,
                    initialMode = AuthMode.SIGN_UP,
                    onSignUpSubmit = { _, _ -> signUpCalled++ }
                )
            }
        }

        composeTestRule.onNodeWithTag("email_field").performScrollTo().performTextInput("user@hearu.app")
        composeTestRule.onNodeWithTag("password_field").performScrollTo().performTextInput("ValidPass123!")
        composeTestRule.onNodeWithTag("confirm_password_field").performScrollTo().performTextInput("Different123!")
        composeTestRule.onNodeWithTag("age_gate_checkbox").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("signup_button").performScrollTo().performClick()

        composeTestRule.onNodeWithText("Passwords do not match").performScrollTo().assertIsDisplayed()
        assertEquals(0, signUpCalled)

        // Boundary length 7 (minLength - 1)
        composeTestRule.onNodeWithTag("password_field").performScrollTo().performTextReplacement("Short1!")
        composeTestRule.onNodeWithTag("confirm_password_field").performScrollTo().performTextReplacement("Short1!")
        composeTestRule.onNodeWithTag("signup_button").performScrollTo().performClick()

        composeTestRule.onNodeWithText("Password must be at least 8 characters.").performScrollTo().assertIsDisplayed()
        assertEquals(0, signUpCalled)
    }

    @Test
    fun tier2_signIn_invalidEmailAndRepositoryFailure_showErrorAndClearOnEdit() {
        runBlocking {
            whenever(mockAuthRepository.signInWithEmail(any(), any()))
                .thenReturn(AuthResult.Failure(Exception("Invalid credentials")))
        }

        composeTestRule.setContent {
            HearUTheme {
                LoginScreen(
                    viewModel = authViewModel,
                    onLoginSubmit = { email, pass -> authViewModel.login(email, pass) }
                )
            }
        }

        composeTestRule.onNodeWithTag("email_field").performScrollTo().performTextInput("invalid-email")
        composeTestRule.onNodeWithTag("password_field").performScrollTo().performTextInput("Password123!")
        composeTestRule.onNodeWithTag("login_button").performScrollTo().performClick()

        composeTestRule.onNodeWithText("Please enter a valid email address.").performScrollTo().assertIsDisplayed()
        assertTrue(authViewModel.authState.value is AuthState.Error)

        // Editing email clears the error
        composeTestRule.onNodeWithTag("email_field").performScrollTo().performTextInput("@hearu.app")
        composeTestRule.onNodeWithText("Please enter a valid email address.").assertDoesNotExist()
        assertEquals(AuthState.Idle, authViewModel.authState.value)

        // Repository failure surfaces error banner and keeps user on LoginScreen
        composeTestRule.onNodeWithTag("login_button").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        assertTrue(authViewModel.authState.value is AuthState.Error)
        composeTestRule.onNodeWithText("Invalid credentials", substring = true).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun tier2_signUp_tappingAgeGateTextRow_togglesCheckbox() {
        composeTestRule.setContent {
            HearUTheme {
                LoginScreen(
                    viewModel = authViewModel,
                    initialMode = AuthMode.SIGN_UP
                )
            }
        }

        composeTestRule.onNodeWithText("I certify that I am 18 years of age or older", substring = true)
            .performScrollTo()
            .performClick()

        composeTestRule.onNodeWithTag("age_gate_checkbox").performScrollTo().assertIsOn()
    }

    @Test
    fun tier2_signIn_forgotPasswordAction_isAccessibleAndPromptsEmailReset() {
        composeTestRule.setContent {
            HearUTheme {
                LoginScreen(
                    viewModel = authViewModel,
                    initialMode = AuthMode.SIGN_IN
                )
            }
        }

        composeTestRule.onNodeWithText("Forgot Password", substring = true, ignoreCase = true)
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()

        val resetPromptNodes = composeTestRule.onAllNodes(
            hasText("Reset", substring = true, ignoreCase = true) or
                hasText("Send", substring = true, ignoreCase = true)
        ).fetchSemanticsNodes()
        assertTrue(
            "Expected password reset prompt/dialog after clicking Forgot Password",
            resetPromptNodes.isNotEmpty()
        )
    }
}
