package com.manasm.habit100.ui.graduation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.manasm.habit100.ui.GridSize
import com.manasm.habit100.ui.HabitGrid
import com.manasm.habit100.ui.components.StatCard
import com.manasm.habit100.ui.share.shareGrid
import com.manasm.habit100.ui.theme.HabitColors

@Composable
fun GraduationScreen(
    vm: GraduationViewModel,
    onStartNext: () -> Unit,
    onKeepGoing: () -> Unit,
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val u = ui
    if (u == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("✓", style = MaterialTheme.typography.displayMedium, color = HabitColors.done)
        Text("100 days complete", style = MaterialTheme.typography.titleMedium)
        Text(
            "It's a habit now",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Text(u.name, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)

        HabitGrid(u.cells, GridSize.TROPHY, Modifier.fillMaxWidth(0.9f))

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StatCard("Days done", "${u.daysDone}", Modifier.weight(1f))
            StatCard("Best streak", "${u.bestStreak}", Modifier.weight(1f))
            StatCard("Misses used", "${u.missesUsed}", Modifier.weight(1f))
        }

        Spacer(Modifier.height(8.dp))

        Button(
            onClick = { vm.startNext(onStartNext) },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
        ) { Text("Start your next habit") }

        OutlinedButton(
            onClick = { vm.keepGoing(onKeepGoing) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Keep this one going") }

        TextButton(
            onClick = {
                shareGrid(context, u.cells, "${u.name}: 100 days. It's a habit now.")
            },
        ) {
            Text("Share", style = MaterialTheme.typography.labelMedium)
        }
    }
}
