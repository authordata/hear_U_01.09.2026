package com.hearu.app.ui.emergency

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Emergency
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmergencyScreen(
    onNavigateBack: () -> Unit,
    onNavigateToBreathing: () -> Unit = {}
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("24/7 Crisis & Emergency Hub") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    titleContentColor = MaterialTheme.colorScheme.onErrorContainer
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Immediate Warning Card
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Emergency,
                        contentDescription = "Urgent",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "You Are Not Alone",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "If you or someone you know is in immediate danger or experiencing thoughts of self-harm, confidential free help is available right now.",
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Resource 1: 988 Suicide & Crisis Lifeline
            EmergencyContactCard(
                title = "988 Suicide & Crisis Lifeline",
                description = "Free, 24/7, confidential support for people in distress across the United States & Canada.",
                onCall = {
                    launchSafeIntent(
                        context = context,
                        intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:988")),
                        fallbackMessage = "No phone app found. Please dial 988 directly.",
                        snackbarHostState = snackbarHostState,
                        scope = coroutineScope
                    )
                },
                onText = {
                    launchSafeIntent(
                        context = context,
                        intent = Intent(Intent.ACTION_VIEW, Uri.parse("sms:988")),
                        fallbackMessage = "No messaging app found. Please text 988 directly.",
                        snackbarHostState = snackbarHostState,
                        scope = coroutineScope
                    )
                }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Resource 2: Crisis Text Line
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Crisis Text Line",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Text HOME to 741741 to connect with a crisis counselor 24/7 via SMS.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = {
                            launchSafeIntent(
                                context = context,
                                intent = Intent(Intent.ACTION_VIEW, Uri.parse("sms:741741?body=HOME")),
                                fallbackMessage = "No messaging app found. Please text HOME to 741741.",
                                snackbarHostState = snackbarHostState,
                                scope = coroutineScope
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 48.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Message, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Text HOME to 741741")
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Resource 3: Veterans Crisis Line
            EmergencyContactCard(
                title = "Veterans Crisis Line",
                description = "24/7 confidential crisis support for Veterans, service members, and their loved ones. Dial 988, then press 1, or text 838255.",
                callText = "Call 988 (Press 1)",
                smsText = "Text 838255",
                onCall = {
                    launchSafeIntent(
                        context = context,
                        intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:988")),
                        fallbackMessage = "No phone app found. Please dial 988 and press 1.",
                        snackbarHostState = snackbarHostState,
                        scope = coroutineScope
                    )
                },
                onText = {
                    launchSafeIntent(
                        context = context,
                        intent = Intent(Intent.ACTION_VIEW, Uri.parse("sms:838255")),
                        fallbackMessage = "No messaging app found. Please text 838255.",
                        snackbarHostState = snackbarHostState,
                        scope = coroutineScope
                    )
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Resource 4: The Trevor Project (LGBTQ+ Youth)
            EmergencyContactCard(
                title = "The Trevor Project (LGBTQ+ Youth)",
                description = "24/7 suicide prevention and crisis intervention for LGBTQ youth.",
                callText = "Call Now",
                smsText = "Text START",
                onCall = {
                    launchSafeIntent(
                        context = context,
                        intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:18664887386")),
                        fallbackMessage = "No phone app found. Please dial 1-866-488-7386.",
                        snackbarHostState = snackbarHostState,
                        scope = coroutineScope
                    )
                },
                onText = {
                    launchSafeIntent(
                        context = context,
                        intent = Intent(Intent.ACTION_VIEW, Uri.parse("sms:678678?body=START")),
                        fallbackMessage = "No messaging app found. Please text START to 678678.",
                        snackbarHostState = snackbarHostState,
                        scope = coroutineScope
                    )
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Resource 5: International Resources
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "International Resources (Outside North America)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Befrienders Worldwide and IASP offer hotlines in 100+ countries.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = {
                            launchSafeIntent(
                                context = context,
                                intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.befrienders.org/find-support-now/")),
                                fallbackMessage = "No browser app found. Visit befrienders.org/find-support-now/",
                                snackbarHostState = snackbarHostState,
                                scope = coroutineScope
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 48.dp)
                    ) {
                        Text("Visit Befrienders Worldwide Directory")
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Calming / Grounding Action
            OutlinedButton(
                onClick = onNavigateToBreathing,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 48.dp)
            ) {
                Icon(Icons.Default.Spa, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Take a 2-Minute Calming Breath (4-7-8 Exercise)")
            }
        }
    }
}

private fun launchSafeIntent(
    context: Context,
    intent: Intent,
    fallbackMessage: String,
    snackbarHostState: SnackbarHostState,
    scope: CoroutineScope
) {
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        scope.launch {
            snackbarHostState.showSnackbar(fallbackMessage)
        }
    }
}

@Composable
private fun EmergencyContactCard(
    title: String,
    description: String,
    callText: String = "Call Now",
    smsText: String = "Text 988",
    onCall: () -> Unit,
    onText: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = description, style = MaterialTheme.typography.bodySmall)
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onCall,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .defaultMinSize(minHeight = 48.dp)
                ) {
                    Icon(Icons.Default.Call, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = callText,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )
                }
                OutlinedButton(
                    onClick = onText,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .defaultMinSize(minHeight = 48.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.Message, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = smsText,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}
