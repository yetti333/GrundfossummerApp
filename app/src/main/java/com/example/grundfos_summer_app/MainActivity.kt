package com.example.grundfos_summer_app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.grundfos_summer_app.ui.screen.MainScreen
import com.example.grundfos_summer_app.ui.theme.GrundfosControllerTheme

class MainActivity : ComponentActivity() {
	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		enableEdgeToEdge()
		setContent {
			GrundfosControllerTheme {
				MainScreen()
			}
		}
	}
}
