package com.manasm.habit100.ui.target

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TargetScreen(vm: TargetViewModel, onDone: () -> Unit, onBack: () -> Unit) {
    val ui by vm.ui.collectAsStateWithLifecycle()

    // Tick the wall-clock timer while it is running.
    val running = (ui as? TargetUi.Duration)?.running == true
    LaunchedEffect(running) {
        while (running) {
            delay(500)
            vm.tick()
        }
    }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text((ui as? TargetUi.Reps)?.habitName ?: (ui as? TargetUi.Duration)?.habitName ?: "") },
            navigationIcon = { TextButton(onClick = onBack) { Text("Back") } },
        )
    }) { pad ->
        Box(
            Modifier.padding(pad).fillMaxSize().padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            when (val s = ui) {
                TargetUi.Loading, TargetUi.NoTarget -> Unit
                is TargetUi.Reps -> RepsBody(s, vm, onDone)
                is TargetUi.Duration -> DurationBody(s, vm, onDone)
            }
        }
    }
}

@Composable
private fun RepsBody(s: TargetUi.Reps, vm: TargetViewModel, onDone: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Text("${s.count} / ${s.target}", style = MaterialTheme.typography.displayLarge, fontWeight = FontWeight.Bold)
        if (s.done) {
            Text(if (s.alreadyDone) "Already done today ✓" else "Nice — day marked ✓")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            OutlinedButton(onClick = vm::decrement) { Text("−1") }
            Button(onClick = vm::increment, modifier = Modifier.size(width = 140.dp, height = 64.dp)) {
                Text("+1", style = MaterialTheme.typography.headlineSmall)
            }
        }
        Spacer(Modifier.height(8.dp))
        if (s.done) Button(onClick = onDone) { Text("Done") }
    }
}

@Composable
private fun DurationBody(s: TargetUi.Duration, vm: TargetViewModel, onDone: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
        val m = s.remainingSeconds / 60
        val sec = s.remainingSeconds % 60
        Text("%d:%02d".format(m, sec), style = MaterialTheme.typography.displayLarge, fontWeight = FontWeight.Bold)
        if (s.done) {
            Text(if (s.alreadyDone) "Already done today ✓" else "Time — day marked ✓")
            Button(onClick = onDone) { Text("Done") }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (s.running) {
                    OutlinedButton(onClick = vm::pause) { Text("Pause") }
                } else {
                    Button(onClick = vm::start) {
                        Text(if (s.remainingSeconds < s.totalSeconds) "Resume" else "Start")
                    }
                }
                OutlinedButton(onClick = vm::reset) { Text("Reset") }
            }
        }
    }
}
