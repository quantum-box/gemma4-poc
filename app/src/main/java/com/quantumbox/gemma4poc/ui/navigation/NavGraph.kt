package com.quantumbox.gemma4poc.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.quantumbox.gemma4poc.ui.chat.ChatScreen
import com.quantumbox.gemma4poc.ui.download.DownloadScreen

object Routes {
    const val DOWNLOAD = "download"
    const val CHAT = "chat"
}

@Composable
fun GemmaNavGraph() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Routes.DOWNLOAD) {
        composable(Routes.DOWNLOAD) {
            DownloadScreen(
                onModelReady = {
                    navController.navigate(Routes.CHAT) {
                        popUpTo(Routes.DOWNLOAD) { inclusive = true }
                    }
                }
            )
        }
        composable(Routes.CHAT) {
            ChatScreen()
        }
    }
}
