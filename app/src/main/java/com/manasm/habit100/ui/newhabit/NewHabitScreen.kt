package com.manasm.habit100.ui.newhabit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
            Text(
                "One habit at a time. 100 days. Up to 10 misses — but never two days in a row.",
                style = MaterialTheme.typography.bodyMedium,
            )
            OutlinedTextField(
                value = name,
                onValueChange = vm::onNameChange,
                label = { Text("What will you do daily?") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
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
            ) { Text("Start 100 days") }
        }
    }
}
