package com.manasm.habit100.ui.shelf

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.manasm.habit100.data.HabitRepository
import com.manasm.habit100.data.MasteredHabitRow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * State for the mastered shelf: one [ShelfRow] per graduated habit with its monthly
 * maintenance badge, plus the [FormingNow] footer that enforces the one-at-a-time rule.
 * Constructor signature is load-bearing for [com.manasm.habit100.ui.HabitViewModelFactory].
 */
class ShelfViewModel(
    private val repo: HabitRepository,
) : ViewModel() {

    /** `null` until the first DB emission — lets the screen skip the empty-state flash. */
    val rows: StateFlow<List<ShelfRow>?> = repo.observeMastered()
        .map { list -> list.map { it.toRow() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val masteredCount: StateFlow<Int> = rows
        .map { it?.size ?: 0 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val formingNow: StateFlow<FormingNow?> = repo.observeActive()
        .map { a ->
            a?.let {
                FormingNow(
                    name = it.habit.name,
                    dayNumber = it.snapshot.currentDayNumber.coerceAtMost(it.habit.attemptTrackLength),
                    trackLength = it.habit.attemptTrackLength,
                    missesLeft = it.snapshot.missesLeft,
                    progress = it.snapshot.effectiveDay.toFloat() / it.habit.attemptTrackLength,
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun confirm(id: Long) = viewModelScope.launch { repo.checkIn(id, strong = true) }

    fun slip(id: Long) = viewModelScope.launch { repo.reportSlip(id) }

    fun startTuneUp(id: Long) = viewModelScope.launch { runCatching { repo.startTuneUp(id) } }
}

private val GRADUATED_DATE_FORMAT: DateTimeFormatter =
    DateTimeFormatter.ofPattern("MMM d, yyyy")

private fun MasteredHabitRow.toRow(): ShelfRow {
    val badge = when {
        slipped -> Badge.SLIPPED
        checkInDue -> Badge.CHECK_IN
        else -> Badge.GOING_STRONG
    }
    val graduatedAt = habit.graduatedAt
    val subtitle = when {
        slipped -> "Slipped — lock it back in"
        maintenanceStreakMonths > 0 -> "$maintenanceStreakMonths month streak"
        graduatedAt != null -> "Graduated ${
            GRADUATED_DATE_FORMAT.withZone(ZoneId.of(habit.timeZoneId)).format(graduatedAt)
        }"
        else -> "Graduated"
    }
    return ShelfRow(
        id = habit.id,
        name = habit.name,
        cells = trophyCells,
        badge = badge,
        subtitle = subtitle,
        slipped = slipped,
        canTuneUp = slipped && slotFree && habit.status == "mastered",
        tuneUpInProgress = habit.status == "tuning_up",
    )
}
