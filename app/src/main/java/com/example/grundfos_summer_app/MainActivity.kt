package com.example.grundfos_summer_app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.grundfos_summer_app.ui.screen.MainScreen
import com.example.grundfos_summer_app.ui.screen.SettingsScreen
import com.example.grundfos_summer_app.ui.screen.ErrorScreen
import com.example.grundfos_summer_app.ui.theme.GrundfosControllerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GrundfosControllerTheme {
                AppNavigation()
            }
        }
    }
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "main") {
        composable("main") {
            MainScreen(
                onNavigateToSettings = { navController.navigate("settings") },
                onNavigateToErrors = { navController.navigate("errors") }
            )
        }
        composable("settings") {
            SettingsScreen(
                onBack = { navController.popBackStack() }
            )
        }
        composable("errors") {
            ErrorScreen(
                onBack = { navController.popBackStack() }
            )
        }
    }
}
