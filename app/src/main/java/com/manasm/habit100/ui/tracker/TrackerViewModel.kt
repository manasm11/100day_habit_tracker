package com.manasm.habit100.ui.tracker

import androidx.lifecycle.ViewModel
import com.manasm.habit100.clock.Clock
import com.manasm.habit100.clock.DevClock
import com.manasm.habit100.clock.DevClockStore
import com.manasm.habit100.data.HabitRepository

/**
 * Stub for Task 9 wiring. State and behaviour are added in a later task.
 * Constructor signature is load-bearing for [com.manasm.habit100.ui.HabitViewModelFactory].
 */
class TrackerViewModel(
    private val repo: HabitRepository,
    private val clock: Clock,
    private val devClockStore: DevClockStore?,
    private val devClock: DevClock?,
) : ViewModel() {
    // TODO(task-11): tracker screen state
}
