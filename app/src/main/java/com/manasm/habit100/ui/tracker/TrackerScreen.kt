package com.manasm.habit100.ui.tracker

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import com.manasm.habit100.ui.GridSize
import com.manasm.habit100.ui.HabitCopy
import com.manasm.habit100.ui.HabitGrid
import com.manasm.habit100.ui.components.StatCard
import com.manasm.habit100.ui.theme.HabitColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackerScreen(
    vm: TrackerViewModel,
    onStartHabit: () -> Unit,
    onGraduated: (Long) -> Unit,
    onOpenShelf: () -> Unit,
    onStartTarget: (Long) -> Unit,
) {
    val state by vm.state.collectAsStateWithLifecycle()

    // Ruling 3: re-derive the snapshot against the current clock whenever we resume.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refresh() }

    LaunchedEffect(state) {
        (state as? TrackerUiState.Graduated)?.let { onGraduated(it.habitId) }
    }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("100 Day Habit Tracker") },
            actions = { TextButton(onClick = onOpenShelf) { Text("Mastered") } },
        )
    }) { pad ->
        Column(
            Modifier
                .padding(pad)
                .fillMaxSize()
                .padding(20.dp)
        ) {
            val s = state
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (s) {
                    TrackerUiState.Loading -> Unit
                    TrackerUiState.Empty -> EmptyState(onStartHabit)
                    is TrackerUiState.Graduated -> Unit // routed away by LaunchedEffect
                    is TrackerUiState.Failed -> FailedState(s, vm)
                    is TrackerUiState.Forming -> FormingContent(s, vm, onStartTarget)
                }
            }
        }
    }
}

@Composable
private fun EmptyState(onStartHabit: () -> Unit) {
    Column(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("No habit forming yet.", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            "Pick one thing to build — or one to quit. 100 days either way.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(20.dp))
        Button(onClick = onStartHabit) { Text("Start a habit") }
    }
}

@Composable
private fun FailedState(s: TrackerUiState.Failed, vm: TrackerViewModel) {
    Column(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(s.habitName, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Text(
            "Attempt ended — ${s.reason} on day ${s.failedOnDay}.",
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(Modifier.height(8.dp))
        Button(onClick = vm::restart, modifier = Modifier.fillMaxWidth()) {
            Text("Restart — new 100 days")
        }
        OutlinedButton(onClick = vm::abandon, modifier = Modifier.fillMaxWidth()) {
            Text("Abandon this habit")
        }
    }
}

@Composable
private fun FormingContent(
    s: TrackerUiState.Forming,
    vm: TrackerViewModel,
    onStartTarget: (Long) -> Unit,
) {
    var confirmUndo by rememberSaveable { mutableStateOf(false) }
    var confirmSlip by rememberSaveable { mutableStateOf(false) }
    var confirmUndoSlip by rememberSaveable { mutableStateOf(false) }
    val dayWord = if (s.isGraceDay) "yesterday" else "today"
    val previousWord = if (s.isGraceDay) "the day before" else "yesterday"
    val copy = remember(s.kind) { HabitCopy.of(s.kind) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(s.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Text(
            "Day ${s.dayNumber} of ${s.trackLength}",
            style = MaterialTheme.typography.titleLarge,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard(copy.doneStatLabel, "${s.doneCount} / ${s.trackLength}", Modifier.weight(1f))
            StatCard(copy.missStatLabel, "${s.missesLeft} / 10", Modifier.weight(1f))
        }

        if (s.atRisk) {
            Surface(
                color = HabitColors.amber.copy(alpha = 0.18f),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text(
                        copy.atRiskTitle(dayWord),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(copy.atRiskBody(previousWord))
                }
            }
        } else if (s.isGraceDay) {
            Surface(
                color = HabitColors.amber.copy(alpha = 0.12f),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text(copy.graceCardTitle, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        copy.graceCardBody(s.graceDeadlineText ?: "this morning"),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }

        HabitGrid(cells = s.cells, size = GridSize.HERO, modifier = Modifier.fillMaxWidth())

        Legend(copy)

        Column {
            if (s.hasTarget && s.canMarkToday) {
                Button(
                    onClick = { onStartTarget(s.habitId) },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                ) { Text(if (s.isGraceDay) "Start (for yesterday)" else "Start") }
                Spacer(Modifier.height(6.dp))
                TextButton(onClick = vm::markDone) { Text("I did it elsewhere — mark $dayWord done") }
            } else {
                Button(
                    onClick = vm::markDone,
                    enabled = s.canMarkToday,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                ) { Text(copy.markButton(dayWord, s.alreadyDoneToday)) }

                if (s.canUndo) {
                    Spacer(Modifier.height(6.dp))
                    TextButton(onClick = { confirmUndo = true }) { Text(copy.undoMarkLabel) }
                } else if (s.todaySlipped) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Day ${s.dayNumber} is logged as a slip.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    if (s.canUndoSlip) {
                        TextButton(onClick = { confirmUndoSlip = true }) { Text(copy.undoSlipLabel) }
                    }
                } else if (!s.canMarkToday) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        if (s.alreadyDoneToday) copy.comeBackTomorrow else copy.nothingToMark,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            // §15: naming the slip out loud, instead of letting the day lapse into one.
            if (s.canSlip) {
                Spacer(Modifier.height(2.dp))
                TextButton(onClick = { confirmSlip = true }) {
                    Text(
                        copy.slipButton(dayWord),
                        color = HabitColors.missed,
                    )
                }
            }
        }
    }

    if (confirmSlip) {
        AlertDialog(
            onDismissRequest = { confirmSlip = false },
            title = { Text(copy.slipDialogTitle(dayWord)) },
            text = { Text(copy.slipDialogBody(s.slipEndsAttempt, s.dayNumber)) },
            confirmButton = {
                TextButton(onClick = { confirmSlip = false; vm.slip() }) { Text(copy.slipConfirm) }
            },
            dismissButton = {
                TextButton(onClick = { confirmSlip = false }) { Text(copy.slipDismiss) }
            },
        )
    }

    if (confirmUndoSlip) {
        AlertDialog(
            onDismissRequest = { confirmUndoSlip = false },
            title = { Text("Take back the slip on day ${s.dayNumber}?") },
            text = {
                Text(
                    "Day ${s.dayNumber} goes back to being open, and your slip is returned to " +
                        "the budget. Only do this if you logged it by mistake.",
                )
            },
            confirmButton = {
                TextButton(onClick = { confirmUndoSlip = false; vm.undoSlip() }) {
                    Text("Take it back")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmUndoSlip = false }) { Text("Leave it") }
            },
        )
    }

    if (confirmUndo) {
        val undoIsGraceDay = s.canMarkToday && s.undoDayNumber < s.dayNumber
        AlertDialog(
            onDismissRequest = { confirmUndo = false },
            title = { Text("Un-mark day ${s.undoDayNumber}?") },
            text = {
                Text(
                    "This clears the check-in for day ${s.undoDayNumber}. You can mark it again " +
                        if (undoIsGraceDay) {
                            "until ${s.graceDeadlineText ?: "10:00 AM"}, when it locks."
                        } else {
                            "before midnight."
                        },
                )
            },
            confirmButton = {
                TextButton(onClick = { confirmUndo = false; vm.undoMark() }) { Text("Un-mark") }
            },
            dismissButton = {
                TextButton(onClick = { confirmUndo = false }) { Text("Keep it") }
            },
        )
    }
}

@Composable
private fun Legend(copy: HabitCopy) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        LegendEntry(label = copy.doneLegend) {
            Surface(
                color = HabitColors.done,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.size(12.dp),
            ) {}
        }
        LegendEntry(label = copy.missedLegend) {
            Surface(
                color = HabitColors.missed,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.size(12.dp),
            ) {}
        }
        LegendEntry(label = "today") {
            Box(
                Modifier
                    .size(12.dp)
                    .border(2.dp, HabitColors.done, MaterialTheme.shapes.small)
            )
        }
    }
}

@Composable
private fun LegendEntry(label: String, swatch: @Composable () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        swatch()
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}
