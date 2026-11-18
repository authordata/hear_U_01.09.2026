package com.hearu.app.auth

import android.app.Application
import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.fragment.app.FragmentActivity
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class BiometricHelperTest {

    @Test
    fun `allowedAuthenticatorsForSdk selects BIOMETRIC_STRONG on API 30 plus and BIOMETRIC_WEAK below API 30`() {
        val helper = BiometricHelper()
        val expectedApi30Plus =
            BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
        val expectedPreApi30 =
            BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL

        assertEquals(expectedPreApi30, helper.allowedAuthenticatorsForSdk(26))
        assertEquals(expectedPreApi30, helper.allowedAuthenticatorsForSdk(28))
        assertEquals(expectedPreApi30, helper.allowedAuthenticatorsForSdk(29))
        assertEquals(expectedApi30Plus, helper.allowedAuthenticatorsForSdk(30))
        assertEquals(expectedApi30Plus, helper.allowedAuthenticatorsForSdk(34))
        assertEquals(expectedApi30Plus, helper.allowedAuthenticatorsForSdk(35))
    }

    @Test
    @Config(sdk = [28], application = Application::class)
    fun `isBiometricAvailable and showBiometricPrompt execute cleanly on API 28`() {
        val context: Context = ApplicationProvider.getApplicationContext()
        val activity = Robolectric.buildActivity(FragmentActivity::class.java).setup().get()
        val helper = BiometricHelper()

        helper.isBiometricAvailable(context)
        helper.showBiometricPrompt(
            activity = activity,
            onSuccess = {},
            onError = {}
        )
    }

    @Test
    @Config(sdk = [34], application = Application::class)
    fun `isBiometricAvailable and showBiometricPrompt execute cleanly on API 34`() {
        val context: Context = ApplicationProvider.getApplicationContext()
        val activity = Robolectric.buildActivity(FragmentActivity::class.java).setup().get()
        val helper = BiometricHelper()

        helper.isBiometricAvailable(context)
        helper.showBiometricPrompt(
            activity = activity,
            onSuccess = {},
            onError = {}
        )
    }

    @Test
    fun `onAuthenticationFailed does not invoke onError so user can retry`() {
        val helper = BiometricHelper()
        var successCalls = 0
        val errors = mutableListOf<String>()

        val callback = helper.createAuthenticationCallback(
            onSuccess = { successCalls++ },
            onError = { errors.add(it) }
        )

        callback.onAuthenticationFailed()
        assertEquals(0, successCalls)
        assertTrue("onAuthenticationFailed must not call onError", errors.isEmpty())

        val mockResult: BiometricPrompt.AuthenticationResult = mock()
        callback.onAuthenticationSucceeded(mockResult)
        assertEquals(1, successCalls)

        callback.onAuthenticationError(BiometricPrompt.ERROR_USER_CANCELED, "Canceled")
        assertEquals(listOf("Canceled"), errors)
    }
}
