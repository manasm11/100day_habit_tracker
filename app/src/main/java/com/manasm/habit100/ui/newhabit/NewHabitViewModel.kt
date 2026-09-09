package com.manasm.habit100.ui.newhabit

import androidx.lifecycle.ViewModel
import com.manasm.habit100.data.HabitRepository
import com.manasm.habit100.domain.HabitTarget
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

    enum class TargetChoice { NONE, DURATION, REPS }

    private val _name = MutableStateFlow("")
    val name: StateFlow<String> = _name.asStateFlow()

    private val _targetChoice = MutableStateFlow(TargetChoice.NONE)
    val targetChoice: StateFlow<TargetChoice> = _targetChoice.asStateFlow()

    /** The minutes (DURATION) or rep count (REPS) as raw text. */
    private val _targetValue = MutableStateFlow("")
    val targetValue: StateFlow<String> = _targetValue.asStateFlow()

    private val _canCreate = MutableStateFlow(false)
    val canCreateEnabled: StateFlow<Boolean> = _canCreate.asStateFlow()

    fun onNameChange(s: String) { _name.value = s; recompute() }
    fun onTargetChoice(c: TargetChoice) { _targetChoice.value = c; recompute() }
    fun onTargetValueChange(s: String) { _targetValue.value = s.filter { it.isDigit() }.take(6); recompute() }

    /** The typed value, if it's a positive integer within the sane range for the chosen kind. */
    private fun parsedValue(): Int? {
        val v = _targetValue.value.toIntOrNull() ?: return null
        val max = if (_targetChoice.value == TargetChoice.DURATION) MAX_MINUTES else MAX_REPS
        return v.takeIf { it in 1..max }
    }

    private fun recompute() {
        val targetOk = _targetChoice.value == TargetChoice.NONE || parsedValue() != null
        _canCreate.value = _name.value.isNotBlank() && targetOk
    }

    private fun buildTarget(): HabitTarget {
        val v = parsedValue() ?: return HabitTarget.None
        return when (_targetChoice.value) {
            TargetChoice.NONE -> HabitTarget.None
            TargetChoice.DURATION -> HabitTarget.Duration(v * 60)
            TargetChoice.REPS -> HabitTarget.Reps(v)
        }
    }

    /** Returns true on success, false if a habit is already forming. */
    suspend fun create(zoneId: ZoneId): Boolean = try {
        repo.createHabit(_name.value, zoneId, buildTarget())
        true
    } catch (e: IllegalStateException) {
        false
    }

    private companion object {
        const val MAX_MINUTES = 600   // 10 hours
        const val MAX_REPS = 100_000
    }
}
