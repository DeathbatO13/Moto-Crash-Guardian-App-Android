package com.motocrashguardian.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.SavedStateHandle
import com.motocrashguardian.core.model.AppSettings
import com.motocrashguardian.core.model.Incident
import com.motocrashguardian.core.model.IncidentType
import com.motocrashguardian.core.model.Trace
import com.motocrashguardian.data.incidents.IncidentRepository
import com.motocrashguardian.data.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import java.util.UUID
import javax.inject.Inject

enum class HistoryFilter { ALL, REAL, DRILL }

data class HistoryUiState(
    val items: List<Incident> = emptyList(),
    val filter: HistoryFilter = HistoryFilter.ALL,
    /** Incidentes guardados sin filtrar; distingue "no hay ninguno" de "el filtro no coincide". */
    val totalCount: Int = 0
)

internal fun buildHistoryState(incidents: List<Incident>, filter: HistoryFilter): HistoryUiState =
    HistoryUiState(
        items = incidents
            .filter {
                when (filter) {
                    HistoryFilter.ALL -> true
                    HistoryFilter.REAL -> it.type == IncidentType.REAL
                    HistoryFilter.DRILL -> it.type == IncidentType.DRILL
                }
            }
            .sortedWith(compareByDescending<Incident> { it.detectedAt }.thenByDescending { it.id }),
        filter = filter,
        totalCount = incidents.size
    )

@HiltViewModel
class HistoryViewModel @Inject constructor(
    incidentRepository: IncidentRepository
) : ViewModel() {

    private val filter = MutableStateFlow(HistoryFilter.ALL)

    val state: StateFlow<HistoryUiState> = combine(incidentRepository.incidents, filter, ::buildHistoryState)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState())

    fun setFilter(value: HistoryFilter) {
        filter.value = value
    }
}

sealed interface DetailUiState {
    data object Loading : DetailUiState
    data object NotFound : DetailUiState
    data class Loaded(
        val incident: Incident,
        /** `null` si el incidente no tiene caja negra descargada. */
        val trace: DecodedTrace?,
        /** Umbral de impacto actual; el de cada incidente no se guarda. */
        val impactThresholdG: Float
    ) : DetailUiState
}

internal fun buildDetailState(
    incident: Incident?,
    trace: Trace?,
    settings: AppSettings
): DetailUiState =
    if (incident == null) {
        DetailUiState.NotFound
    } else {
        DetailUiState.Loaded(
            incident = incident,
            trace = trace?.let(::decodeTrace),
            impactThresholdG = settings.detection.impactThresholdMg / 1000f
        )
    }

@HiltViewModel
class IncidentDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val incidentRepository: IncidentRepository,
    settingsRepository: SettingsRepository
) : ViewModel() {

    private val incidentId: UUID? =
        savedStateHandle.get<String>(IncidentIdArg)?.let { runCatching { UUID.fromString(it) }.getOrNull() }

    val state: StateFlow<DetailUiState> =
        combine(
            incidentId?.let { incidentRepository.observeIncident(it) } ?: flowOf(null),
            settingsRepository.settings
        ) { incident, settings ->
            // La traza no cambia una vez descargada; se lee cada vez que cambia el incidente.
            buildDetailState(incident, incident?.let { incidentRepository.getTrace(it.id) }, settings)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DetailUiState.Loading)

    companion object {
        const val IncidentIdArg = "incidentId"
    }
}
