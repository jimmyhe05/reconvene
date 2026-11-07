package com.reconvene.ui.create

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.reconvene.ReconveneApplication
import com.reconvene.data.repository.PodRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import java.time.ZonedDateTime
import java.util.UUID

data class CreatePodUiState(
    val form: CreatePodForm,
    val today: LocalDate,
    val errors: FormErrors = FormErrors(),
    val isSaving: Boolean = false,
    val saveFailed: Boolean = false,
    val isCreated: Boolean = false,
)

class CreatePodViewModel(
    private val savedStateHandle: SavedStateHandle,
    private val podRepository: PodRepository,
    private val clock: Clock,
    private val hostName: String,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        ZonedDateTime.now(clock).let { now ->
            CreatePodUiState(form = savedStateHandle[FORM_KEY] ?: newPodForm(now), today = now.toLocalDate())
        }
    )
    val uiState: StateFlow<CreatePodUiState> = _uiState.asStateFlow()

    private var submitted = false

    fun updateForm(form: CreatePodForm) {
        savedStateHandle[FORM_KEY] = form
        _uiState.update { state ->
            state.copy(form = form, errors = if (submitted) validate(form) else FormErrors())
        }
    }

    fun create() {
        val form = _uiState.value.form
        if (_uiState.value.isSaving) return
        submitted = true
        val errors = validate(form)
        _uiState.update { it.copy(errors = errors) }
        if (!errors.isEmpty) return

        _uiState.update { it.copy(isSaving = true, saveFailed = false) }
        viewModelScope.launch {
            try {
                podRepository.createPod(form.toPod(UUID.randomUUID().toString(), hostName, clock.zone))
                _uiState.update { it.copy(isSaving = false, isCreated = true) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(isSaving = false, saveFailed = true) }
            }
        }
    }

    private fun validate(form: CreatePodForm) = form.validate(clock.instant(), clock.zone)

    companion object {
        private const val FORM_KEY = "form"

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as ReconveneApplication).container
                CreatePodViewModel(
                    savedStateHandle = createSavedStateHandle(),
                    podRepository = container.podRepository,
                    clock = container.clock,
                    hostName = container.currentUserName,
                )
            }
        }
    }
}
