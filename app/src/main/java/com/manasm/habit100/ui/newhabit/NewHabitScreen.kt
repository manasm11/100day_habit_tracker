package com.manasm.habit100.ui.newhabit

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.manasm.habit100.domain.HabitKind
import com.manasm.habit100.ui.HabitCopy
import kotlinx.coroutines.launch
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewHabitScreen(
    vm: NewHabitViewModel,
    onCreated: () -> Unit,
    onBack: () -> Unit,
) {
    val name by vm.name.collectAsStateWithLifecycle()
    val canCreate by vm.canCreateEnabled.collectAsStateWithLifecycle()
    val targetChoice by vm.targetChoice.collectAsStateWithLifecycle()
    val targetValue by vm.targetValue.collectAsStateWithLifecycle()
    val kind by vm.kind.collectAsStateWithLifecycle()
    val copy = remember(kind) { HabitCopy.of(kind) }
    val scope = rememberCoroutineScope()
    var blocked by remember { mutableStateOf(false) }
    val zoneId = remember { ZoneId.systemDefault() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("New habit") },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("Cancel") }
                },
            )
        },
    ) { pad ->
        Column(
            modifier = Modifier
                .padding(pad)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(copy.rulesBlurb, style = MaterialTheme.typography.bodyMedium)

            Text("What kind of habit?", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HabitKind.entries.forEach { k ->
                    FilterChip(
                        selected = kind == k,
                        onClick = {
                            blocked = false
                            vm.onKindChange(k)
                        },
                        label = {
                            Text(
                                when (k) {
                                    HabitKind.BUILD -> "Build one"
                                    HabitKind.QUIT -> "Quit one"
                                },
                            )
                        },
                    )
                }
            }

            OutlinedTextField(
                value = name,
                onValueChange = {
                    blocked = false
                    vm.onNameChange(it)
                },
                label = { Text(copy.namePrompt) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            // Nothing to time or count when the whole point is not doing it.
            if (kind == HabitKind.BUILD) {
                Text("How do you do it?", style = MaterialTheme.typography.labelLarge)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                ) {
                    NewHabitViewModel.TargetChoice.entries.forEach { choice ->
                        FilterChip(
                            selected = targetChoice == choice,
                            onClick = { vm.onTargetChoice(choice) },
                            label = {
                                Text(
                                    when (choice) {
                                        NewHabitViewModel.TargetChoice.NONE -> "Just check it off"
                                        NewHabitViewModel.TargetChoice.DURATION -> "Time it"
                                        NewHabitViewModel.TargetChoice.REPS -> "Count reps"
                                    },
                                )
                            },
                        )
                    }
                }
            } else {
                Text(
                    "Each day you stay away from it, mark it clean. If you slip, say so — " +
                        "two slips in a row ends the attempt.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (kind == HabitKind.BUILD && targetChoice != NewHabitViewModel.TargetChoice.NONE) {
                OutlinedTextField(
                    value = targetValue,
                    onValueChange = vm::onTargetValueChange,
                    label = {
                        Text(
                            if (targetChoice == NewHabitViewModel.TargetChoice.DURATION) "Minutes"
                            else "Reps",
                        )
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.width(140.dp),
                )
            }

            Text(
                "Timezone: ${zoneId.id} (locked to this habit)",
                style = MaterialTheme.typography.labelMedium,
            )
            if (blocked) {
                Text(
                    "You already have a habit forming. Finish it first.",
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Button(
                onClick = {
                    scope.launch {
                        if (vm.create(zoneId)) onCreated() else blocked = true
                    }
                },
                enabled = canCreate,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(copy.startButton) }
        }
    }
}
