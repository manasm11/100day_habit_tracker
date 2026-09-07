package com.manasm.habit100

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.manasm.habit100.ui.AppNavHost
import com.manasm.habit100.ui.theme.HabitTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as HabitApplication).container
        setContent {
            HabitTheme {
                AppNavHost(container)
            }
        }
    }
}
