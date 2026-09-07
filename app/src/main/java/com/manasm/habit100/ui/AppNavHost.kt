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
import com.manasm.habit100.ui.tracker.TrackerScreen
import com.manasm.habit100.ui.tracker.TrackerViewModel

object Routes {
    const val TRACKER = "tracker"
    const val NEW_HABIT = "newHabit"
    const val GRADUATION = "graduation"
    const val SHELF = "shelf"
}

/**
 * Navigation skeleton. The tracker is always the start destination and routes internally
 * (Empty -> start a habit, Graduated -> graduation screen). Unbuilt routes render a placeholder.
 */
@Composable
fun AppNavHost(container: AppContainer) {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = Routes.TRACKER) {
        composable(Routes.TRACKER) {
            val vm: TrackerViewModel = viewModel(factory = HabitViewModelFactory(container))
            TrackerScreen(
                vm = vm,
                onStartHabit = { nav.navigate(Routes.NEW_HABIT) },
                onGraduated = { id ->
                    nav.navigate("${Routes.GRADUATION}/$id") {
                        popUpTo(Routes.TRACKER) { inclusive = false }
                    }
                },
                onOpenShelf = { nav.navigate(Routes.SHELF) },
            )
        }
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
        composable("${Routes.GRADUATION}/{habitId}") { Text("graduation") }
        composable(Routes.SHELF) { Text("shelf") }
    }
}
