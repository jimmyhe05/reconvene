package com.reconvene.ui.discover

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.reconvene.ReconveneApplication
import com.reconvene.data.repository.PodRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import java.time.Clock

class DiscoverViewModel(
    private val podRepository: PodRepository,
    private val clock: Clock,
) : ViewModel() {

    private val _uiState = MutableStateFlow<DiscoverUiState>(DiscoverUiState.Loading)
    val uiState: StateFlow<DiscoverUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null

    init {
        loadPods()
    }

    fun retry() = loadPods()

    private fun loadPods() {
        loadJob?.cancel()
        _uiState.value = DiscoverUiState.Loading
        loadJob = viewModelScope.launch {
            podRepository.observePods()
                .catch { _uiState.value = DiscoverUiState.Error }
                .collect { pods ->
                    val now = clock.instant()
                    val sections = groupIntoSections(pods, now, clock.zone)
                    _uiState.value = if (sections.isEmpty()) {
                        DiscoverUiState.Empty
                    } else {
                        DiscoverUiState.Content(sections, now)
                    }
                }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as ReconveneApplication).container
                DiscoverViewModel(container.podRepository, container.clock)
            }
        }
    }
}
