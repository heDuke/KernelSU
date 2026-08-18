package me.weishu.kernelsu.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.weishu.kernelsu.data.model.EnvCheckReport
import me.weishu.kernelsu.data.repository.EnvCheckRepository
import me.weishu.kernelsu.data.repository.EnvCheckRepositoryImpl

data class EnvCheckUiState(
    val isLoading: Boolean = false,
    val report: EnvCheckReport? = null,
    val error: String? = null,
)

class EnvCheckViewModel(
    private val repo: EnvCheckRepository = EnvCheckRepositoryImpl(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(EnvCheckUiState())
    val uiState: StateFlow<EnvCheckUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            repo.runCheck().fold(
                onSuccess = { report ->
                    _uiState.update { it.copy(isLoading = false, report = report, error = null) }
                },
                onFailure = { e ->
                    _uiState.update {
                        it.copy(isLoading = false, error = e.message ?: "Environment check failed")
                    }
                },
            )
        }
    }
}
