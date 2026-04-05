package com.quantumbox.gemma4poc

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.quantumbox.gemma4poc.ui.navigation.GemmaNavGraph
import com.quantumbox.gemma4poc.ui.theme.Gemma4PoCTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            Gemma4PoCTheme {
                GemmaNavGraph()
            }
        }
    }
}
