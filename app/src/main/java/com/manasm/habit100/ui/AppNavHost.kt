package com.manasm.habit100.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.manasm.habit100.AppContainer

object Routes {
    const val TRACKER = "tracker"
    const val NEW_HABIT = "newHabit"
    const val GRADUATION = "graduation"
    const val SHELF = "shelf"
}

/**
 * Navigation skeleton. Each route renders a placeholder for Task 9; real screens are
 * wired in later tasks using [HabitViewModelFactory] against [container].
 */
@Composable
fun AppNavHost(container: AppContainer) {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = Routes.TRACKER) {
        composable(Routes.TRACKER) { Text("tracker") }
        composable(Routes.NEW_HABIT) { Text("newHabit") }
        composable(Routes.GRADUATION) { Text("graduation") }
        composable(Routes.SHELF) { Text("shelf") }
    }
}
