package com.statusvideo.statussaver.ui.statuslist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.statusvideo.statussaver.domain.model.Status
import com.statusvideo.statussaver.domain.usecase.GetStatusesUseCase
import com.statusvideo.statussaver.domain.usecase.SaveStatusUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

// --- MVI contracts ---

data class StatusListState(
    val statuses: List<Status> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val selectedIds: Set<String> = emptySet()
)

sealed interface StatusListIntent {
    data object Refresh : StatusListIntent
    data class ToggleSelect(val id: String) : StatusListIntent
    data object ClearSelection : StatusListIntent
    data object SaveSelected : StatusListIntent
}

sealed interface StatusListEffect {
    data class ShowMessage(val message: String) : StatusListEffect
}

// --- ViewModel ---

@HiltViewModel
class StatusListViewModel @Inject constructor(
    private val getStatusesUseCase: GetStatusesUseCase,
    private val saveStatusUseCase: SaveStatusUseCase
) : ViewModel() {

    private val _state = MutableStateFlow(StatusListState(isLoading = true))
    val state: StateFlow<StatusListState> = _state.asStateFlow()

    private val _effects = Channel<StatusListEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    init {
        observeStatuses()
    }

    fun onIntent(intent: StatusListIntent) {
        when (intent) {
            StatusListIntent.Refresh -> observeStatuses()
            is StatusListIntent.ToggleSelect -> toggleSelect(intent.id)
            StatusListIntent.ClearSelection -> _state.update { it.copy(selectedIds = emptySet()) }
            StatusListIntent.SaveSelected -> saveSelected()
        }
    }

    private fun observeStatuses() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            getStatusesUseCase()
                .catch { e -> _state.update { it.copy(isLoading = false, error = e.message) } }
                .collect { statuses ->
                    _state.update { it.copy(statuses = statuses, isLoading = false) }
                }
        }
    }

    private fun toggleSelect(id: String) {
        _state.update { current ->
            val updated = if (id in current.selectedIds) {
                current.selectedIds - id
            } else {
                current.selectedIds + id
            }
            current.copy(selectedIds = updated)
        }
    }

    private fun saveSelected() {
        val toSave = _state.value.statuses.filter { it.id in _state.value.selectedIds }
        if (toSave.isEmpty()) return

        viewModelScope.launch {
            var successCount = 0
            var failCount = 0
            toSave.forEach { status ->
                saveStatusUseCase(status)
                    .onSuccess { successCount++ }
                    .onFailure { failCount++ }
            }

            _state.update { it.copy(selectedIds = emptySet()) }

            val message = when {
                failCount == 0 -> "Saved $successCount status${if (successCount > 1) "es" else ""}"
                successCount == 0 -> "Failed to save statuses"
                else -> "Saved $successCount, failed $failCount"
            }
            _effects.send(StatusListEffect.ShowMessage(message))
        }
    }
}
