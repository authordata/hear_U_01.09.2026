package com.hearu.app.ui.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun PrivacyPolicyDialog(
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Privacy Policy & 30-Day Auto Purge",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Strict 18+ Policy",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "HearU is restricted to adults aged 18 and older. We do not knowingly collect data from minors.",
                    style = MaterialTheme.typography.bodySmall
                )

                Text(
                    text = "Data Collection & Use",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "We store only your authentication identifier (email or anonymous Firebase UID), chosen alias, role preference, notification token, and block list. Profile avatars stay visually blurred by default until mutual, revocable consent.",
                    style = MaterialTheme.typography.bodySmall
                )

                Text(
                    text = "30-Day Ephemeral Chat Retention",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Peer chat sessions and messages are automatically purged after 30 days. Local cached messages and temporary .m4a voice note cache files are removed on sign-out, account deletion, or after 30 days.",
                    style = MaterialTheme.typography.bodySmall
                )

                Text(
                    text = "AI Companion & Device Security",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "AI Companion chats are processed via Gemini 2.0 Flash within your active session. Optional app lock uses AndroidX BiometricPrompt; biometric data never leaves your device. Android cloud backup is disabled.",
                    style = MaterialTheme.typography.bodySmall
                )

                Text(
                    text = "Account & Data Deletion",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "You can permanently erase your account and local data anytime via Profile -> Delete Account & Erase Data. Questions: privacy@hearu.app",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.height(4.dp))
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}
