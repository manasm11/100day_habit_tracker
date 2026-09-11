package com.manasm.habit100.ui.backup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.manasm.habit100.backup.BackupCodec
import com.manasm.habit100.backup.BackupError
import com.manasm.habit100.backup.BackupFile
import com.manasm.habit100.backup.BackupFileIo
import com.manasm.habit100.backup.BackupRepository
import com.manasm.habit100.backup.RestorePreview
import com.manasm.habit100.clock.Clock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.ZoneId

sealed interface BackupUi {
    data object Idle : BackupUi
    data object Working : BackupUi
    data class Exported(val habitCount: Int) : BackupUi

    /** The file is parsed and understood, but nothing has been written yet. */
    data class ConfirmRestore(val preview: RestorePreview) : BackupUi
    data class Restored(val habitCount: Int) : BackupUi
    data class Error(val message: String) : BackupUi
}

/**
 * Drives the backup screen (§16). Nothing is written to the database until [confirmRestore];
 * choosing a file only parses and previews it.
 */
class BackupViewModel<D>(
    private val backup: BackupRepository,
    private val io: BackupFileIo<D>,
    private val clock: Clock,
) : ViewModel() {

    private val _ui = MutableStateFlow<BackupUi>(BackupUi.Idle)
    val ui: StateFlow<BackupUi> = _ui.asStateFlow()

    /** Held between preview and confirmation so the file is parsed exactly once. */
    private var pending: BackupFile? = null

    fun suggestedFileName(): String {
        val today = clock.now().atZone(ZoneId.systemDefault()).toLocalDate()
        return "100-day-habits-$today.json"
    }

    fun exportTo(destination: D) {
        _ui.value = BackupUi.Working
        viewModelScope.launch {
            try {
                val file = backup.export()
                io.write(destination, BackupCodec.encode(file))
                _ui.value = BackupUi.Exported(file.habits.size)
            } catch (e: Exception) {
                _ui.value = BackupUi.Error("The backup couldn't be saved: ${reason(e)}")
            }
        }
    }

    fun loadForRestore(destination: D) {
        _ui.value = BackupUi.Working
        viewModelScope.launch {
            try {
                val file = BackupCodec.decode(io.read(destination))
                pending = file
                _ui.value = BackupUi.ConfirmRestore(backup.preview(file))
            } catch (e: BackupError) {
                pending = null
                _ui.value = BackupUi.Error(e.message!!)
            } catch (e: Exception) {
                pending = null
                _ui.value = BackupUi.Error("That file couldn't be opened: ${reason(e)}")
            }
        }
    }

    fun confirmRestore() {
        val file = pending ?: return
        _ui.value = BackupUi.Working
        viewModelScope.launch {
            try {
                backup.restore(file)
                pending = null
                _ui.value = BackupUi.Restored(file.habits.size)
            } catch (e: BackupError) {
                _ui.value = BackupUi.Error(e.message!!)
            } catch (e: Exception) {
                _ui.value = BackupUi.Error("The restore failed: ${reason(e)}")
            }
        }
    }

    fun cancelRestore() {
        pending = null
        _ui.value = BackupUi.Idle
    }

    fun dismiss() {
        _ui.value = BackupUi.Idle
    }

    private fun reason(e: Exception): String =
        e.message?.takeIf { it.isNotBlank() } ?: e::class.simpleName ?: "unknown error"
}
