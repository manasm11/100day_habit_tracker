package com.manasm.habit100.ui.shelf

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.manasm.habit100.ui.GridSize
import com.manasm.habit100.ui.HabitGrid
import com.manasm.habit100.ui.theme.HabitColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShelfScreen(vm: ShelfViewModel, onBack: () -> Unit) {
    val rows by vm.rows.collectAsStateWithLifecycle()
    val count by vm.masteredCount.collectAsStateWithLifecycle()
    val formingNow by vm.formingNow.collectAsStateWithLifecycle()

    var expandedRow by remember { mutableStateOf<Long?>(null) }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Mastered") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            },
        )
    }) { pad ->
        LazyColumn(
            modifier = Modifier
                .padding(pad)
                .fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Column {
                    Text(
                        "Mastered ($count)",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Tap a habit to check in this month",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            val loadedRows = rows
            when {
                loadedRows == null -> Unit // not loaded yet — skip the empty-state flash
                loadedRows.isEmpty() -> item {
                    Text(
                        "Nothing mastered yet — your first 100 days are how it starts.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                else -> items(loadedRows, key = { it.id }) { row ->
                    ShelfRowItem(
                        row = row,
                        expanded = expandedRow == row.id,
                        onToggle = {
                            expandedRow = if (expandedRow == row.id) null else row.id
                        },
                        onConfirm = { vm.confirm(row.id); expandedRow = null },
                        onSlip = { vm.slip(row.id); expandedRow = null },
                        onTuneUp = { vm.startTuneUp(row.id) },
                    )
                }
            }

            item { HorizontalDivider() }

            item {
                Column {
                    Text(
                        "Forming now",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(8.dp))
                    val f = formingNow
                    if (f == null) {
                        Text(
                            "No habit forming — start one.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    } else {
                        Text(f.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "Day ${f.dayNumber} of ${f.trackLength}",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            "${f.missesLeft} misses left",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { f.progress.coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ShelfRowItem(
    row: ShelfRow,
    expanded: Boolean,
    onToggle: () -> Unit,
    onConfirm: () -> Unit,
    onSlip: () -> Unit,
    onTuneUp: () -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = row.badge == Badge.CHECK_IN) { onToggle() },
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                HabitGrid(row.cells, GridSize.THUMBNAIL, Modifier.size(64.dp))
                Spacer(Modifier.size(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        row.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (row.subtitle.isNotEmpty()) {
                        Text(row.subtitle, style = MaterialTheme.typography.bodySmall)
                    }
                }
                BadgeChip(row.badge)
            }

            if (expanded && row.badge == Badge.CHECK_IN) {
                Spacer(Modifier.height(12.dp))
                Text("Still doing this?", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onConfirm) { Text("Confirm") }
                    TextButton(onClick = onSlip) { Text("Report a slip") }
                }
            }

            if (row.badge == Badge.SLIPPED) {
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = onTuneUp,
                    enabled = row.canTuneUp,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Start 30-day tune-up")
                }
                if (!row.canTuneUp) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (row.tuneUpInProgress) "Tune-up in progress"
                        else "Finish the habit you're forming first.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            } else {
                Spacer(Modifier.height(4.dp))
                TextButton(
                    onClick = onSlip,
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Text("I slipped")
                }
            }
        }
    }
}

@Composable
private fun BadgeChip(badge: Badge) {
    val (label, color) = when (badge) {
        Badge.CHECK_IN -> "Check in" to HabitColors.amber
        Badge.SLIPPED -> "Slipped" to HabitColors.missed
        Badge.GOING_STRONG -> "Going strong" to HabitColors.done
    }
    Surface(
        color = color.copy(alpha = 0.18f),
        contentColor = Color.Unspecified,
        shape = MaterialTheme.shapes.small,
        tonalElevation = 0.dp,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}
