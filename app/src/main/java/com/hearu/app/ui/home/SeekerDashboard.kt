package com.hearu.app.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeekerDashboard() {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("HearU - Seeker") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp)) {
            Text("Welcome to your safe space.", style = MaterialTheme.typography.headlineSmall)
            Spacer(modifier = Modifier.height(24.dp))
            Button(onClick = { /* TODO: Match */ }, modifier = Modifier.fillMaxWidth()) {
                Text("Find a Listener")
            }
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedButton(onClick = { /* TODO: AI Chat */ }, modifier = Modifier.fillMaxWidth()) {
                Text("Chat with AI Companion")
            }
        }
    }
}
