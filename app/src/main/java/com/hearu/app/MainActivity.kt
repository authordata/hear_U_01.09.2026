package com.hearu.app

import android.content.Intent
import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.hearu.app.auth.BiometricHelper
import com.hearu.app.data.RolePreferences
import com.hearu.app.navigation.HearUNavigation
import com.hearu.app.repository.ChatRepository
import com.hearu.app.service.HearUNotificationConfig
import com.hearu.app.ui.auth.AppStartupState
import com.hearu.app.ui.auth.AuthViewModel
import com.hearu.app.ui.theme.HearUTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    @Inject
    lateinit var rolePreferences: RolePreferences

    @Inject
    lateinit var biometricHelper: BiometricHelper

    @Inject
    lateinit var chatRepository: ChatRepository

    private companion object {
        private const val KEY_PENDING_SESSION_ID = "pending_deep_link_session_id"
    }

    private var targetSessionId by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        targetSessionId = if (savedInstanceState == null) {
            intent?.getStringExtra(HearUNotificationConfig.EXTRA_SESSION_ID)
                ?.takeIf { it.isNotBlank() }
        } else {
            savedInstanceState.getString(KEY_PENDING_SESSION_ID)
                ?.takeIf { it.isNotBlank() }
        }

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                chatRepository.purgeExpiredLocalMessages()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            }
            try {
                purgeExpiredVoiceNoteCacheFiles(cacheDir)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            }
        }

        setContent {
            val authViewModel: AuthViewModel = hiltViewModel()
            val startupState by authViewModel.startupState.collectAsStateWithLifecycle()
            val darkThemePref = (startupState as? AppStartupState.Ready)?.isDarkTheme
            val useDarkTheme = darkThemePref ?: isSystemInDarkTheme()

            DisposableEffect(useDarkTheme) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(
                        AndroidColor.TRANSPARENT,
                        AndroidColor.TRANSPARENT
                    ) { useDarkTheme },
                    navigationBarStyle = SystemBarStyle.auto(
                        AndroidColor.TRANSPARENT,
                        AndroidColor.TRANSPARENT
                    ) { useDarkTheme }
                )
                onDispose {}
            }

            var isUnlocked by rememberSaveable { mutableStateOf(false) }
            var biometricError by rememberSaveable { mutableStateOf<String?>(null) }

            HearUTheme(darkTheme = useDarkTheme) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    when (val ready = startupState) {
                        is AppStartupState.Loading -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .systemBarsPadding(),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator()
                            }
                        }

                        is AppStartupState.Ready -> {
                            LaunchedEffect(ready.isBiometricEnabled) {
                                if (!ready.isBiometricEnabled) {
                                    isUnlocked = true
                                    biometricError = null
                                } else if (!isUnlocked) {
                                    if (biometricHelper.isBiometricAvailable(this@MainActivity)) {
                                        biometricError = null
                                        biometricHelper.showBiometricPrompt(
                                            activity = this@MainActivity,
                                            onSuccess = {
                                                isUnlocked = true
                                                biometricError = null
                                            },
                                            onError = { err ->
                                                biometricError = err.ifBlank {
                                                    "Biometric authentication failed. Please try again or sign out."
                                                }
                                            }
                                        )
                                    } else {
                                        biometricError = "Biometric authentication is unavailable on this device. Please retry or sign out."
                                    }
                                }
                            }

                            if (ready.isBiometricEnabled && !isUnlocked) {
                                BiometricLockOverlay(
                                    errorMessage = biometricError,
                                    onUnlockRequest = {
                                        if (biometricHelper.isBiometricAvailable(this@MainActivity)) {
                                            biometricError = null
                                            biometricHelper.showBiometricPrompt(
                                                activity = this@MainActivity,
                                                onSuccess = {
                                                    isUnlocked = true
                                                    biometricError = null
                                                },
                                                onError = { err ->
                                                    biometricError = err.ifBlank {
                                                        "Biometric authentication failed. Please try again or sign out."
                                                    }
                                                }
                                            )
                                        } else {
                                            biometricError = "Biometric authentication is unavailable on this device. Please retry or sign out."
                                        }
                                    },
                                    onSignOut = {
                                        targetSessionId = null
                                        intent?.let { currentIntent ->
                                            currentIntent.removeExtra(HearUNotificationConfig.EXTRA_SESSION_ID)
                                            setIntent(currentIntent)
                                        }
                                        authViewModel.signOut()
                                        biometricError = null
                                    }
                                )
                            } else {
                                HearUNavigation(
                                    authViewModel = authViewModel,
                                    initialSessionId = targetSessionId,
                                    onDeepLinkConsumed = {
                                        targetSessionId = null
                                        intent?.let { currentIntent ->
                                            currentIntent.removeExtra(HearUNotificationConfig.EXTRA_SESSION_ID)
                                            setIntent(currentIntent)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(KEY_PENDING_SESSION_ID, targetSessionId)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val newSessionId = intent.getStringExtra(HearUNotificationConfig.EXTRA_SESSION_ID)
        if (!newSessionId.isNullOrBlank()) {
            targetSessionId = newSessionId
        }
    }
}

@Composable
private fun BiometricLockOverlay(
    errorMessage: String?,
    onUnlockRequest: () -> Unit,
    onSignOut: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(88.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "Locked",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(44.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "HearU is Locked",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Biometric authentication is required to access your conversations and emotional support sessions.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (!errorMessage.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(16.dp))
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onUnlockRequest,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            Icon(Icons.Default.Fingerprint, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                "Unlock with Biometrics",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedButton(
            onClick = onSignOut,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
        ) {
            Text("Sign Out & Reset Lock")
        }
    }
}

internal fun purgeExpiredVoiceNoteCacheFiles(
    cacheDir: File?,
    retentionDays: Int = 30,
    nowMillis: Long = System.currentTimeMillis()
): Int {
    if (cacheDir == null || !cacheDir.isDirectory) return 0
    val safeDays = retentionDays.coerceAtLeast(1)
    val cutoffMillis = nowMillis - safeDays * 24L * 60L * 60L * 1000L
    var deletedCount = 0
    cacheDir.listFiles { _, name ->
        name.startsWith("voice_note_") && name.endsWith(".m4a")
    }?.forEach { file ->
        if (file.isFile && file.lastModified() in 1L..cutoffMillis && file.delete()) {
            deletedCount++
        }
    }
    return deletedCount
}
