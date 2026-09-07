package com.manasm.habit100.ui.graduation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.manasm.habit100.data.HabitRepository
import com.manasm.habit100.data.toRuleInput
import com.manasm.habit100.domain.DayStatus
import com.manasm.habit100.domain.HabitRules
import com.manasm.habit100.ui.CellState
import com.manasm.habit100.ui.gridCells
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.ZoneId

data class GraduationUi(
    val name: String,
    val daysDone: Int,
    val bestStreak: Int,
    val missesUsed: Int,
    val cells: List<CellState>,
)

class GraduationViewModel(
    private val repo: HabitRepository,
    private val habitId: Long,
) : ViewModel() {

    private val _ui = MutableStateFlow<GraduationUi?>(null)
    val ui: StateFlow<GraduationUi?> = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            val (habit, logs) = repo.trophyView(habitId) ?: return@launch
            val zone = ZoneId.of(habit.timeZoneId)
            // Evaluate at a time past the window so the snapshot is the final one.
            val past = habit.attemptStartDate
                .plusDays(habit.attemptTrackLength.toLong())
                .atStartOfDay(zone)
                .toInstant()
            val snap = HabitRules.evaluate(habit.toRuleInput(logs), past)
            // Evaluated past the window, so every day in 1..trackLength is definitively DONE
            // or a miss. Derive misses positionally rather than from persisted MISSED rows —
            // the "mark day 100" graduation path flips to mastered without materializing them.
            val done = logs.filter { it.status == DayStatus.DONE }.map { it.dayNumber }.toSet()
            val missed = (1..habit.attemptTrackLength).filterNot { it in done }.toSet()
            _ui.value = GraduationUi(
                name = habit.name,
                daysDone = snap.doneCount,
                bestStreak = snap.bestStreak,
                missesUsed = snap.missCount,
                cells = gridCells(
                    trackLength = habit.attemptTrackLength,
                    currentDay = habit.attemptTrackLength + 1,
                    doneDays = done,
                    missedDays = missed,
                ),
            )
        }
    }

    fun startNext(onDone: () -> Unit) = viewModelScope.launch {
        repo.acknowledgeGraduation(habitId)
        onDone()
    }

    fun keepGoing(onDone: () -> Unit) = viewModelScope.launch {
        repo.acknowledgeGraduation(habitId)
        onDone()
    }
}
