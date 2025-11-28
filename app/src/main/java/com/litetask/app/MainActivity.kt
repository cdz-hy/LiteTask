package com.litetask.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.litetask.app.ui.theme.LiteTaskTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LiteTaskTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    var currentScreen by remember { mutableStateOf("home") }

                    when (currentScreen) {
                        "home" -> {
                            com.litetask.app.ui.home.HomeScreen(
                                onNavigateToAdd = { currentScreen = "add_task" },
                                onNavigateToSettings = { currentScreen = "settings" }
                            )
                        }
                        "settings" -> {
                            com.litetask.app.ui.settings.SettingsScreen(
                                onBack = { currentScreen = "home" }
                            )
                        }
                    }
                }
            }
        }
    }
}
