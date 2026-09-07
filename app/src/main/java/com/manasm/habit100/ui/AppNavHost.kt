package com.manasm.habit100.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.manasm.habit100.AppContainer
import com.manasm.habit100.ui.newhabit.NewHabitScreen
import com.manasm.habit100.ui.newhabit.NewHabitViewModel

object Routes {
    const val TRACKER = "tracker"
    const val NEW_HABIT = "newHabit"
    const val GRADUATION = "graduation"
    const val SHELF = "shelf"
}

/**
 * Navigation skeleton. Screens are wired in progressively; unwired routes render a
 * placeholder and real screens use [HabitViewModelFactory] against [container].
 */
@Composable
fun AppNavHost(container: AppContainer) {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = Routes.TRACKER) {
        composable(Routes.TRACKER) { Text("tracker") }
        composable(Routes.NEW_HABIT) {
            val vm: NewHabitViewModel = viewModel(factory = HabitViewModelFactory(container))
            NewHabitScreen(
                vm = vm,
                onCreated = {
                    nav.navigate(Routes.TRACKER) {
                        popUpTo(Routes.TRACKER) { inclusive = true }
                    }
                },
                onBack = { nav.popBackStack() },
            )
        }
        composable(Routes.GRADUATION) { Text("graduation") }
        composable(Routes.SHELF) { Text("shelf") }
    }
}
