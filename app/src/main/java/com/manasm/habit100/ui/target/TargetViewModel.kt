package com.manasm.habit100.ui.target

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.manasm.habit100.clock.Clock
import com.manasm.habit100.data.HabitRepository
import com.manasm.habit100.data.target
import com.manasm.habit100.domain.HabitState
import com.manasm.habit100.domain.HabitTarget
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface TargetUi {
    data object Loading : TargetUi

    data class Reps(
        val habitName: String,
        val count: Int,
        val target: Int,
        val done: Boolean,
        val alreadyDone: Boolean,
    ) : TargetUi

    data class Duration(
        val habitName: String,
        val remainingSeconds: Int,
        val totalSeconds: Int,
        val running: Boolean,
        val done: Boolean,
        val alreadyDone: Boolean,
    ) : TargetUi

    /** The habit has no target — the screen shouldn't have been opened; caller should pop. */
    data object NoTarget : TargetUi
}

/**
 * Drives the rep counter or the duration timer for a targeted habit, and auto-marks the
 * day in play once the target is met. The duration timer is wall-clock based: [tick] is
 * called ~1/s by the screen; backgrounding does not pause it.
 */
class TargetViewModel(
    private val repo: HabitRepository,
    private val clock: Clock,
    private val habitId: Long,
) : ViewModel() {

    private val _ui = MutableStateFlow<TargetUi>(TargetUi.Loading)
    val ui: StateFlow<TargetUi> = _ui.asStateFlow()

    private var loaded = false
    private var habitName = ""
    private var target: HabitTarget = HabitTarget.None
    private var alreadyDone = false

    private var reps = 0
    private var endAtMs: Long? = null
    private var pausedRemaining: Int? = null

    init {
        viewModelScope.launch {
            val active = repo.currentActive()
            if (active == null || active.habit.id != habitId) return@launch
            habitName = active.habit.name
            target = active.habit.target()
            alreadyDone = repo.snapshotOf(active.habit, active.logs).todayMarkedDone
            loaded = true
            recompute()
        }
    }

    fun increment() { if (!loaded) return; reps++; recompute(); maybeAutoMark() }

    fun decrement() { if (!loaded) return; reps = (reps - 1).coerceAtLeast(0); recompute() }

    fun start() {
        if (!loaded) return
        val total = (target as? HabitTarget.Duration)?.seconds ?: return
        val startFrom = pausedRemaining ?: total
        endAtMs = clock.now().toEpochMilli() + startFrom * 1000L
        pausedRemaining = null
        recompute()
        maybeAutoMark()
    }

    fun pause() {
        if (!loaded) return
        if (endAtMs != null) {
            pausedRemaining = remainingSeconds()
            endAtMs = null
        }
        recompute()
    }

    fun reset() {
        if (!loaded) return
        endAtMs = null
        pausedRemaining = null
        recompute()
    }

    fun tick() { if (!loaded) return; recompute(); maybeAutoMark() }

    private fun remainingSeconds(): Int {
        val total = (target as? HabitTarget.Duration)?.seconds ?: return 0
        return endAtMs?.let { end ->
            ((end - clock.now().toEpochMilli()) / 1000L).coerceAtLeast(0L).toInt()
        } ?: (pausedRemaining ?: total)
    }

    private fun isComplete(): Boolean = when (target) {
        is HabitTarget.Reps -> reps >= (target as HabitTarget.Reps).count
        is HabitTarget.Duration -> endAtMs != null && remainingSeconds() == 0
        HabitTarget.None -> false
    }

    private var marking = false

    private fun maybeAutoMark() {
        if (alreadyDone || marking || !isComplete()) return
        marking = true
        viewModelScope.launch {
            try {
                val active = repo.currentActive()
                if (active != null && active.habit.id == habitId) {
                    val snap = repo.snapshotOf(active.habit, active.logs)
                    if (snap.state == HabitState.FORMING && snap.canMarkToday) {
                        runCatching { repo.markTodayDone(habitId) }
                    }
                    // Only claim "done" if the day is genuinely marked now.
                    alreadyDone = repo.currentActive()?.let {
                        repo.snapshotOf(it.habit, it.logs).todayMarkedDone
                    } ?: false
                }
            } finally {
                marking = false
                recompute()
            }
        }
    }

    private fun recompute() {
        if (!loaded) return
        val complete = isComplete() || alreadyDone
        _ui.value = when (val t = target) {
            HabitTarget.None -> TargetUi.NoTarget
            is HabitTarget.Reps -> TargetUi.Reps(
                habitName = habitName,
                count = reps,
                target = t.count,
                done = complete,
                alreadyDone = alreadyDone,
            )
            is HabitTarget.Duration -> TargetUi.Duration(
                habitName = habitName,
                remainingSeconds = remainingSeconds(),
                totalSeconds = t.seconds,
                running = endAtMs != null && !complete,
                done = complete,
                alreadyDone = alreadyDone,
            )
        }
    }
}
