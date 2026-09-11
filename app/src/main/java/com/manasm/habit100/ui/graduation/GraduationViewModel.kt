package com.manasm.habit100.ui.graduation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.manasm.habit100.data.HabitRepository
import com.manasm.habit100.data.habitKind
import com.manasm.habit100.data.toRuleInput
import com.manasm.habit100.domain.DayStatus
import com.manasm.habit100.domain.HabitKind
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
    val trackLength: Int,
    val isTuneUp: Boolean,
    val kind: HabitKind,
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
            val trophy = repo.trophyView(habitId) ?: return@launch
            val habit = trophy.habit
            val logs = trophy.logs
            val trackLength = trophy.trackLength
            val zone = ZoneId.of(habit.timeZoneId)
            // Evaluate well past the trophy window so the snapshot is the final one. After a
            // tune-up, habit.attemptStartDate is the *tune-up's* start, not the trophy attempt's,
            // so add a wide margin — only the day NUMBERS in the trophy logs (1..trackLength)
            // drive done/miss/streak once we're past the window.
            val past = habit.attemptStartDate
                .plusDays(trackLength.toLong() + 300)
                .atStartOfDay(zone)
                .toInstant()
            val input = habit.toRuleInput(logs).copy(trackLength = trackLength)
            val snap = HabitRules.evaluate(input, past)
            // Evaluated past the window, so every day in 1..trackLength is definitively DONE
            // or a miss. Derive misses positionally rather than from persisted MISSED rows —
            // the "mark day 100" graduation path flips to mastered without materializing them.
            val done = logs.filter { it.status == DayStatus.DONE }.map { it.dayNumber }.toSet()
            val missed = (1..trackLength).filterNot { it in done }.toSet()
            _ui.value = GraduationUi(
                name = habit.name,
                daysDone = snap.doneCount,
                bestStreak = snap.bestStreak,
                missesUsed = snap.missCount,
                trackLength = trackLength,
                isTuneUp = trophy.isTuneUp,
                kind = habit.habitKind(),
                cells = gridCells(
                    trackLength = trackLength,
                    currentDay = trackLength + 1,
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
