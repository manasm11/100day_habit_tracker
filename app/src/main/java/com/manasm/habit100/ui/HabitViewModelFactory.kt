package com.manasm.habit100.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import com.manasm.habit100.AppContainer
import com.manasm.habit100.ui.newhabit.NewHabitViewModel
import com.manasm.habit100.ui.shelf.ShelfViewModel
import com.manasm.habit100.ui.tracker.TrackerViewModel

class HabitViewModelFactory(private val container: AppContainer) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T =
        when (modelClass) {
            TrackerViewModel::class.java -> TrackerViewModel(container.repository)
            NewHabitViewModel::class.java -> NewHabitViewModel(container.repository)
            ShelfViewModel::class.java -> ShelfViewModel(container.repository)
            else -> error("Unknown ViewModel class: $modelClass")
        } as T
}
