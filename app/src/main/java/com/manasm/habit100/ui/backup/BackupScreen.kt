package com.manasm.habit100.ui.backup

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.manasm.habit100.backup.RestorePreview
import com.manasm.habit100.domain.HabitKind
import com.manasm.habit100.ui.theme.HabitColors
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen(vm: BackupViewModel<Uri>, onBack: () -> Unit) {
    val ui by vm.ui.collectAsStateWithLifecycle()

    val createFile = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri -> uri?.let(vm::exportTo) }

    val openFile = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(vm::loadForRestore) }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Backup & restore") },
            navigationIcon = { TextButton(onClick = onBack) { Text("Back") } },
        )
    }) { pad ->
        Column(
            Modifier
                .padding(pad)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                "Your record lives only on this phone. A backup is a single file you keep " +
                    "wherever you like — it holds every habit, mastered and in progress, with " +
                    "all their days.",
                style = MaterialTheme.typography.bodyMedium,
            )

            Button(
                onClick = { createFile.launch(vm.suggestedFileName()) },
                enabled = ui !is BackupUi.Working,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text("Save a backup") }

            OutlinedButton(
                onClick = { openFile.launch(arrayOf("application/json", "text/plain", "*/*")) },
                enabled = ui !is BackupUi.Working,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text("Restore from a backup") }

            Text(
                "Restoring replaces everything on this phone with what's in the file. " +
                    "You'll see exactly what it contains before anything changes.",
                style = MaterialTheme.typography.bodySmall,
            )

            when (val s = ui) {
                BackupUi.Idle -> Unit
                BackupUi.Working -> {
                    Spacer(Modifier.height(4.dp))
                    CircularProgressIndicator()
                }
                is BackupUi.Exported -> Note(
                    "Backup saved — ${habitWord(s.habitCount)}.",
                    HabitColors.done,
                )
                is BackupUi.Restored -> Note(
                    "Restored — ${habitWord(s.habitCount)} are back.",
                    HabitColors.done,
                )
                is BackupUi.Error -> Note(s.message, HabitColors.missed, onDismiss = vm::dismiss)
                is BackupUi.ConfirmRestore -> Unit // rendered as a dialog below
            }
        }
    }

    (ui as? BackupUi.ConfirmRestore)?.let { s ->
        RestoreDialog(s.preview, onConfirm = vm::confirmRestore, onCancel = vm::cancelRestore)
    }
}

@Composable
private fun Note(text: String, color: androidx.compose.ui.graphics.Color, onDismiss: (() -> Unit)? = null) {
    Surface(
        color = color.copy(alpha = 0.14f),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(text, style = MaterialTheme.typography.bodyMedium)
            if (onDismiss != null) {
                TextButton(onClick = onDismiss) { Text("OK") }
            }
        }
    }
}

@Composable
private fun RestoreDialog(p: RestorePreview, onConfirm: () -> Unit, onCancel: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Restore this backup?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(dateLine(p))
                Text(contentLine(p))
                if (p.formingName != null) {
                    val verb = if (p.formingKind == HabitKind.QUIT) "clean" else "in"
                    if (p.formingWillEnd) {
                        Text(
                            "\"${p.formingName}\" was on day ${p.formingDayNumber ?: "?"} " +
                                "${p.daysStale} days ago. Those days count as misses, which " +
                                "ends that attempt. Your mastered shelf restores unchanged.",
                            fontWeight = FontWeight.SemiBold,
                        )
                    } else {
                        Text("\"${p.formingName}\" picks up on day ${p.formingDayNumber ?: "?"} ($verb progress).")
                    }
                }
                Text(
                    "Everything currently on this phone is replaced.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Restore") } },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancel") } },
    )
}

private val DIALOG_DATE = DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a")

private fun dateLine(p: RestorePreview): String = p.exportedAt?.let {
    "Saved " + DIALOG_DATE.withZone(ZoneId.systemDefault()).format(it) +
        when (p.daysStale) {
            0L -> " (today)."
            1L -> " (yesterday)."
            else -> " (${p.daysStale} days ago)."
        }
} ?: "This backup doesn't say when it was made."

private fun contentLine(p: RestorePreview): String = when {
    p.habitCount == 0 -> "It's empty — restoring it clears this phone."
    p.masteredCount == 0 -> habitWord(p.habitCount) + ", none mastered yet."
    else -> "${habitWord(p.habitCount)}, ${p.masteredCount} mastered."
}

private fun habitWord(n: Int): String = if (n == 1) "1 habit" else "$n habits"
