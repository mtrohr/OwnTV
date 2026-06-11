package tv.own.owntv.features.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import tv.own.owntv.core.backup.BackupManager
import java.io.File

/** Phase 12 — drives Backup & Restore (export/import to a JSON file via SAF). */
class BackupViewModel(private val backup: BackupManager) : ViewModel() {

    sealed interface State {
        data object Idle : State
        data object Working : State
        data class Done(val message: String) : State
        data class Error(val message: String) : State
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    fun export(folder: File) {
        viewModelScope.launch {
            _state.value = State.Working
            backup.export(folder).fold(
                onSuccess = { _state.value = State.Done("Saved to $it") },
                onFailure = { _state.value = State.Error(it.message ?: "Export failed") },
            )
        }
    }

    fun import(file: File) {
        viewModelScope.launch {
            _state.value = State.Working
            backup.import(file).fold(
                onSuccess = { _state.value = State.Done("Restored $it items. Re-sync your sources to load content.") },
                onFailure = { _state.value = State.Error(it.message ?: "Import failed") },
            )
        }
    }

    fun reset() { _state.value = State.Idle }
}
