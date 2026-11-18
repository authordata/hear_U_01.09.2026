package com.hearu.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hearu.app.ui.auth.LoginScreenContent
import com.hearu.app.ui.theme.HearUTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LoginScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun loginScreen_rendersInputs_and_submits() {
        var loginTriggered = false

        composeTestRule.setContent {
            HearUTheme {
                LoginScreenContent(onLoginSubmit = { _, _ -> loginTriggered = true })
            }
        }

        composeTestRule.onNodeWithText("Welcome to HearU").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Email").performScrollTo().performTextInput("test@hearu.app")
        composeTestRule.onNodeWithText("Password").performScrollTo().performTextInput("ValidPass123!")

        composeTestRule.onNodeWithText("Login").performScrollTo().performClick()
        assertTrue(loginTriggered)
    }
}
