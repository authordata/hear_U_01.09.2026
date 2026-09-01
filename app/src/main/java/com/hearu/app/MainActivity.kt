package com.hearu.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.hearu.app.navigation.HearUNavigation
import com.hearu.app.ui.theme.HearUTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            HearUTheme {
                Surface(
                    modifier = Modifier.fillMaxSize()
                ) {
                    HearUNavigation()
                }
            }
        }
    }
}
