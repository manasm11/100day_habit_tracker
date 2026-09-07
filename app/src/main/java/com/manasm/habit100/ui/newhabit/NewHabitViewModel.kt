package com.manasm.habit100.ui.newhabit

import androidx.lifecycle.ViewModel
import com.manasm.habit100.data.HabitRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.ZoneId

/**
 * Drives the new-habit screen. Only one habit may be forming at a time; [create]
 * surfaces that guard by returning `false` when the repository rejects a second habit.
 */
class NewHabitViewModel(
    private val repo: HabitRepository,
) : ViewModel() {
    private val _name = MutableStateFlow("")
    val name: StateFlow<String> = _name.asStateFlow()

    private val _canCreate = MutableStateFlow(false)
    val canCreateEnabled: StateFlow<Boolean> = _canCreate.asStateFlow()

    fun onNameChange(s: String) {
        _name.value = s
        _canCreate.value = s.isNotBlank()
    }

    /** Returns true on success, false if a habit is already forming. */
    suspend fun create(zoneId: ZoneId): Boolean = try {
        repo.createHabit(_name.value, zoneId)
        true
    } catch (e: IllegalStateException) {
        false
    }
}
