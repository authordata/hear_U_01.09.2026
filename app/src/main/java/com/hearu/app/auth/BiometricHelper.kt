package com.hearu.app.auth

import android.content.Context
import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BiometricHelper @Inject constructor() {

    internal fun allowedAuthenticatorsForSdk(sdkInt: Int = Build.VERSION.SDK_INT): Int {
        return if (sdkInt >= Build.VERSION_CODES.R) {
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
        } else {
            BiometricManager.Authenticators.BIOMETRIC_WEAK or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
        }
    }

    fun isBiometricAvailable(context: Context): Boolean {
        val biometricManager = BiometricManager.from(context)
        return biometricManager.canAuthenticate(allowedAuthenticatorsForSdk()) ==
            BiometricManager.BIOMETRIC_SUCCESS
    }

    internal fun createAuthenticationCallback(
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ): BiometricPrompt.AuthenticationCallback {
        return object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                onError(errString.toString())
            }

            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                onSuccess()
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                // Allow the system BiometricPrompt to remain open for retries.
            }
        }
    }

    fun showBiometricPrompt(
        activity: FragmentActivity,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val executor = ContextCompat.getMainExecutor(activity)
        val biometricPrompt = BiometricPrompt(
            activity,
            executor,
            createAuthenticationCallback(onSuccess = onSuccess, onError = onError)
        )

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("HearU Authentication")
            .setSubtitle("Log in using your biometric credential")
            .setAllowedAuthenticators(allowedAuthenticatorsForSdk())
            .build()

        biometricPrompt.authenticate(promptInfo)
    }
}
