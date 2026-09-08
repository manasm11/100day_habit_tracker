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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import com.manasm.habit100.BuildConfig
import com.manasm.habit100.ui.GridSize
import com.manasm.habit100.ui.HabitGrid
import com.manasm.habit100.ui.components.StatCard
import com.manasm.habit100.ui.theme.HabitColors
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackerScreen(
    vm: TrackerViewModel,
    onStartHabit: () -> Unit,
    onGraduated: (Long) -> Unit,
    onOpenShelf: () -> Unit,
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
                    is TrackerUiState.Forming -> FormingContent(s, vm)
                }
            }
            // Dev panel: available in Empty / Forming / Failed (Graduated routes away).
            if (BuildConfig.DEBUG && s !is TrackerUiState.Graduated && s != TrackerUiState.Loading) {
                DevPanel(vm)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DevPanel(vm: TrackerViewModel) {
    var dateText by rememberSaveable { mutableStateOf("") }
    Column(
        Modifier.fillMaxWidth().padding(top = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = vm::devAdvanceDay) { Text("dev: +1 day") }
            OutlinedButton(
                onClick = {
                    runCatching { LocalDate.parse(dateText.trim()) }
                        .getOrNull()
                        ?.let(vm::devSetToday)
                },
            ) { Text("dev: set date") }
        }
        OutlinedTextField(
            value = dateText,
            onValueChange = { dateText = it },
            singleLine = true,
            label = { Text("yyyy-MM-dd") },
            modifier = Modifier.fillMaxWidth(),
        )
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
        Text("Pick one thing. Do it for 100 days.", style = MaterialTheme.typography.bodyMedium)
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
private fun FormingContent(s: TrackerUiState.Forming, vm: TrackerViewModel) {
    var confirmUndo by rememberSaveable { mutableStateOf(false) }
    val dayWord = if (s.isGraceDay) "yesterday" else "today"

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
            StatCard("Completed", "${s.doneCount} / ${s.trackLength}", Modifier.weight(1f))
            StatCard("Misses left", "${s.missesLeft} / 10", Modifier.weight(1f))
        }

        if (s.atRisk) {
            Surface(
                color = HabitColors.amber.copy(alpha = 0.18f),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text(
                        "Don't miss $dayWord",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (s.isGraceDay)
                            "The day before was a miss. Miss yesterday too and the attempt fails — two in a row."
                        else
                            "You missed yesterday. Miss today too and the attempt fails — two in a row.",
                    )
                }
            }
        } else if (s.isGraceDay && !s.alreadyDoneToday) {
            Surface(
                color = HabitColors.amber.copy(alpha = 0.12f),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text("Yesterday isn't marked yet", fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "You can still mark it done until ${s.graceDeadlineText ?: "this morning"}. " +
                            "After that it locks as a miss.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }

        HabitGrid(cells = s.cells, size = GridSize.HERO, modifier = Modifier.fillMaxWidth())

        Legend()

        Column {
            Button(
                onClick = vm::markDone,
                enabled = s.canMarkToday,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
            ) {
                Text(
                    when {
                        s.alreadyDoneToday -> "Marked $dayWord ✓"
                        s.isGraceDay -> "Mark yesterday done"
                        else -> "Mark today done"
                    },
                )
            }
            if (s.canUndo) {
                Spacer(Modifier.height(6.dp))
                TextButton(onClick = { confirmUndo = true }) {
                    Text("Undo — I didn't actually do it")
                }
            } else if (!s.canMarkToday) {
                Spacer(Modifier.height(6.dp))
                Text(
                    if (s.alreadyDoneToday) "Come back tomorrow."
                    else "Nothing to mark right now.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }

    if (confirmUndo) {
        AlertDialog(
            onDismissRequest = { confirmUndo = false },
            title = { Text("Un-mark day ${s.dayNumber}?") },
            text = {
                Text(
                    "This clears $dayWord's check-in. You can mark it again before it locks" +
                        (s.graceDeadlineText?.let { " at $it" } ?: "") + ".",
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
private fun Legend() {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        LegendEntry(label = "done") {
            Surface(
                color = HabitColors.done,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.size(12.dp),
            ) {}
        }
        LegendEntry(label = "missed") {
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
