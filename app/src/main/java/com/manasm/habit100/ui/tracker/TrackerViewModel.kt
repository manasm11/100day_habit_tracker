package com.manasm.habit100.ui.tracker

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.manasm.habit100.clock.Clock
import com.manasm.habit100.clock.DevClock
import com.manasm.habit100.clock.DevClockStore
import com.manasm.habit100.clock.SystemClock
import com.manasm.habit100.data.ActiveHabit
import com.manasm.habit100.data.HabitEntity
import com.manasm.habit100.data.HabitRepository
import com.manasm.habit100.domain.DayStatus
import com.manasm.habit100.domain.HabitState
import com.manasm.habit100.ui.gridCells
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

class TrackerViewModel(
    private val repo: HabitRepository,
    private val clock: Clock,
    private val devClockStore: DevClockStore?,
    private val devClock: DevClock?,
) : ViewModel() {

    // Ruling 3: an explicit hook so the snapshot re-derives against the current clock
    // even when nothing in the database changed (e.g. after advancing a dev/fake clock).
    private val refreshTicker = MutableStateFlow(0)

    fun refresh() { refreshTicker.value++ }

    val state: StateFlow<TrackerUiState> =
        combine(
            repo.observeActive(),
            repo.observeUnacknowledgedGraduation(),
            repo.observeFailedHabitFlow(),
            refreshTicker,
        ) { active, graduated, failed, _ ->
            // If the fresh snapshot says the active attempt is over, persist the transition.
            // A follow-up emission then routes to Graduated / Failed. Rollover normally does
            // this; here we cover the "clock advanced, nothing wrote to the DB" path.
            if (active != null) {
                val snap = repo.snapshotOf(active.habit, active.logs)
                if (snap.state != HabitState.FORMING) {
                    runCatching { repo.applyTransition(active.habit.id) }
                }
            }
            buildState(active, graduated, failed)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TrackerUiState.Loading)

    private fun buildState(
        active: ActiveHabit?,
        graduated: HabitEntity?,
        failed: HabitEntity?,
    ): TrackerUiState {
        if (active != null) return formingState(active)
        if (graduated != null) return TrackerUiState.Graduated(graduated.id)
        if (failed != null) return TrackerUiState.Failed(
            habitId = failed.id,
            habitName = failed.name,
            reason = friendlyReason(failed.failureReason),
            failedOnDay = failed.failedOnDay ?: 0,
        )
        return TrackerUiState.Empty
    }

    private fun formingState(a: ActiveHabit): TrackerUiState.Forming {
        val snap = repo.snapshotOf(a.habit, a.logs) // fresh clock read
        val trackLength = a.habit.attemptTrackLength
        val done = a.logs.filter { it.status == DayStatus.DONE }.map { it.dayNumber }.toSet()
        val missed = a.logs.filter { it.status == DayStatus.MISSED }.map { it.dayNumber }.toSet()
        // Include implied misses for elapsed unmarked past days so the grid is honest
        // before the rollover engine writes the MISSED rows.
        val impliedMissed = (1 until snap.currentDayNumber)
            .filter { it <= trackLength && it !in done && it !in missed }
            .toSet()
        return TrackerUiState.Forming(
            habitId = a.habit.id,
            name = a.habit.name,
            dayNumber = snap.currentDayNumber.coerceIn(1, trackLength),
            trackLength = trackLength,
            doneCount = snap.doneCount,
            missesLeft = snap.missesLeft,
            atRisk = snap.atRisk,
            canMarkToday = snap.canMarkToday,
            alreadyDoneToday = snap.todayMarkedDone,
            cells = gridCells(trackLength, snap.currentDayNumber, done, missed + impliedMissed),
            isTuneUp = a.habit.status == "tuning_up",
        )
    }

    private fun friendlyReason(raw: String?): String = when (raw) {
        "TWO_IN_A_ROW" -> "two misses in a row"
        "BUDGET_EXCEEDED" -> "used all 10 misses"
        else -> "attempt ended"
    }

    fun markDone() {
        viewModelScope.launch {
            val active = repo.observeActive().first() ?: return@launch
            runCatching { repo.markTodayDone(active.habit.id) }
            refreshTicker.value++
        }
    }

    fun restart() {
        viewModelScope.launch {
            repo.observeFailedHabitFlow().first()?.let {
                runCatching { repo.restartFailedHabit(it.id) }
            }
            refreshTicker.value++
        }
    }

    fun abandon() {
        viewModelScope.launch {
            repo.observeFailedHabitFlow().first()?.let {
                runCatching { repo.abandonHabit(it.id) }
            }
            refreshTicker.value++
        }
    }

    fun devAdvanceDay() {
        val store = devClockStore ?: return
        viewModelScope.launch {
            store.addDays(1)
            devClock?.update(store.offsetSeconds.first())
            repo.observeActive().first()?.let { repo.applyTransition(it.habit.id) }
            refreshTicker.value++
        }
    }

    fun devSetToday(date: LocalDate) {
        val store = devClockStore ?: return
        viewModelScope.launch {
            val active = repo.observeActive().first()
            val zone = active?.let { ZoneId.of(it.habit.timeZoneId) } ?: ZoneId.systemDefault()
            val target = date.atTime(9, 0).atZone(zone).toInstant()
            store.setOffsetSeconds(target.epochSecond - SystemClock().now().epochSecond)
            devClock?.update(store.offsetSeconds.first())
            active?.let { repo.applyTransition(it.habit.id) }
            refreshTicker.value++
        }
    }
}
