package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.PublicShareSnapshot
import com.example.data.remote.VortexFirestoreRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class GuestShareUiState(
    val loading: Boolean = false,
    val snapshot: PublicShareSnapshot? = null,
    val error: String? = null
)

class GuestShareViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = VortexFirestoreRepository(application)

    private val _uiState = MutableStateFlow(GuestShareUiState())
    val uiState: StateFlow<GuestShareUiState> = _uiState.asStateFlow()

    fun openShare(codeOrUrl: String) {
        if (codeOrUrl.isBlank()) {
            _uiState.update { it.copy(error = "Enter a shared link or 16+ character share code.") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null) }
            repository.getPublicShare(codeOrUrl)
                .onSuccess { snap ->
                    _uiState.update { it.copy(loading = false, snapshot = snap, error = null) }
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(
                            loading = false,
                            snapshot = null,
                            error = err.message ?: "Unable to open shared repository."
                        )
                    }
                }
        }
    }

    fun closeShare() {
        _uiState.value = GuestShareUiState()
    }
}
