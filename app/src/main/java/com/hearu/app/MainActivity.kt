package com.hearu.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hearu.app.auth.BiometricHelper
import com.hearu.app.data.RolePreferences
import com.hearu.app.navigation.HearUNavigation
import com.hearu.app.service.HearUNotificationConfig
import com.hearu.app.ui.theme.HearUTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    @Inject
    lateinit var rolePreferences: RolePreferences

    private val biometricHelper by lazy { BiometricHelper() }
    private var targetSessionId by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        targetSessionId = intent.getStringExtra(HearUNotificationConfig.EXTRA_SESSION_ID)

        setContent {
            val darkThemePref by rolePreferences.isDarkThemeFlow.collectAsStateWithLifecycle(initialValue = null)
            val isBiometricEnabled by rolePreferences.isBiometricEnabledFlow.collectAsStateWithLifecycle(initialValue = false)
            val useDarkTheme = darkThemePref ?: isSystemInDarkTheme()

            var isUnlocked by remember { mutableStateOf(false) }

            LaunchedEffect(isBiometricEnabled) {
                if (isBiometricEnabled && !isUnlocked) {
                    biometricHelper.showBiometricPrompt(
                        activity = this@MainActivity,
                        onSuccess = { isUnlocked = true },
                        onError = { /* Allow manual unlock retry */ }
                    )
                }
            }

            HearUTheme(darkTheme = useDarkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize()
                ) {
                    if (isBiometricEnabled && !isUnlocked) {
                        BiometricLockOverlay(
                            onUnlockRequest = {
                                biometricHelper.showBiometricPrompt(
                                    activity = this@MainActivity,
                                    onSuccess = { isUnlocked = true },
                                    onError = { /* Keep locked */ }
                                )
                            }
                        )
                    } else {
                        HearUNavigation(initialSessionId = targetSessionId)
                    }
                }
            }
        }
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
private fun BiometricLockOverlay(onUnlockRequest: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
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
            Text("Unlock with Biometrics", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
    }
}
