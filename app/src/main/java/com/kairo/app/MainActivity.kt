package com.kairo.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.kairo.app.ui.MainScreen
import com.kairo.app.ui.theme.KAIROTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            KAIROTheme {
                MainScreen()
            }
        }
    }
}
