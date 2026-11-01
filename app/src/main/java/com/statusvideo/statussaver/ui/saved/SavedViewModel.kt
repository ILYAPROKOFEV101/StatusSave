package com.statusvideo.statussaver.ui.saved

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.statusvideo.statussaver.domain.model.Status
import com.statusvideo.statussaver.domain.usecase.DeleteSavedUseCase
import com.statusvideo.statussaver.domain.usecase.GetSavedStatusesUseCase
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

data class SavedState(
    val statuses: List<Status> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val deletingId: String? = null
)

sealed interface SavedIntent {
    data class DeleteStatus(val id: String) : SavedIntent
    data object DismissError : SavedIntent
}

sealed interface SavedEffect {
    data class ShowMessage(val message: String) : SavedEffect
}

// --- ViewModel ---

@HiltViewModel
class SavedViewModel @Inject constructor(
    private val getSavedStatusesUseCase: GetSavedStatusesUseCase,
    private val deleteSavedUseCase: DeleteSavedUseCase
) : ViewModel() {

    private val _state = MutableStateFlow(SavedState())
    val state: StateFlow<SavedState> = _state.asStateFlow()

    private val _effects = Channel<SavedEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    init {
        observeSaved()
    }

    fun onIntent(intent: SavedIntent) {
        when (intent) {
            is SavedIntent.DeleteStatus -> deleteStatus(intent.id)
            SavedIntent.DismissError -> _state.update { it.copy(error = null) }
        }
    }

    private fun observeSaved() {
        viewModelScope.launch {
            getSavedStatusesUseCase()
                .catch { e -> _state.update { it.copy(isLoading = false, error = e.message) } }
                .collect { statuses ->
                    _state.update { it.copy(statuses = statuses, isLoading = false) }
                }
        }
    }

    private fun deleteStatus(id: String) {
        viewModelScope.launch {
            _state.update { it.copy(deletingId = id) }
            deleteSavedUseCase(id)
                .onSuccess {
                    _state.update { it.copy(deletingId = null) }
                    _effects.send(SavedEffect.ShowMessage("Deleted successfully"))
                }
                .onFailure { e ->
                    _state.update { it.copy(deletingId = null, error = e.message) }
                }
        }
    }
}
