package com.hearu.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import com.hearu.app.navigation.HearUNavigation
import com.hearu.app.service.HearUNotificationConfig
import com.hearu.app.ui.theme.HearUTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    private var targetSessionId by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        targetSessionId = intent.getStringExtra(HearUNotificationConfig.EXTRA_SESSION_ID)

        setContent {
            HearUTheme {
                Surface(
                    modifier = Modifier.fillMaxSize()
                ) {
                    HearUNavigation(initialSessionId = targetSessionId)
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
