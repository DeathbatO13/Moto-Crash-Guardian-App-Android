package com.motocrashguardian.ui.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.motocrashguardian.core.model.Incident
import com.motocrashguardian.core.model.IncidentStatus
import com.motocrashguardian.core.model.IncidentType
import com.motocrashguardian.ui.theme.appColors
import java.util.UUID

@Composable
fun HistoryScreen(
    onBack: () -> Unit,
    onOpenIncident: (UUID) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HistoryViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    HistoryContent(
        state = state,
        onBack = onBack,
        onFilterChange = viewModel::setFilter,
        onOpenIncident = onOpenIncident,
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryContent(
    state: HistoryUiState,
    onBack: () -> Unit,
    onFilterChange: (HistoryFilter) -> Unit,
    onOpenIncident: (UUID) -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Historial de incidentes", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterOption("Todos", HistoryFilter.ALL, state.filter, onFilterChange)
                FilterOption("Reales", HistoryFilter.REAL, state.filter, onFilterChange)
                FilterOption("Simulacros", HistoryFilter.DRILL, state.filter, onFilterChange)
            }

            when {
                state.totalCount == 0 -> EmptyState(
                    title = "Aún no hay incidentes",
                    detail = "Cuando se detecte una caída, o hagas un simulacro, aparecerá aquí."
                )
                state.items.isEmpty() -> EmptyState(
                    title = "Ningún incidente de este tipo",
                    detail = "Prueba con otro filtro."
                )
                else -> LazyColumn(
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(state.items, key = { it.id }) { incident ->
                        IncidentRow(incident, onClick = { onOpenIncident(incident.id) })
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterOption(
    label: String,
    value: HistoryFilter,
    selected: HistoryFilter,
    onChange: (HistoryFilter) -> Unit
) {
    FilterChip(
        selected = value == selected,
        onClick = { onChange(value) },
        label = { Text(label) }
    )
}

@Composable
private fun EmptyState(title: String, detail: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.History,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.size(12.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(
            detail,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun IncidentRow(incident: Incident, onClick: () -> Unit) {
    val (icon, tint) = statusVisual(incident.status)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        incident.triggerType.label(),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    if (incident.type == IncidentType.DRILL) {
                        Spacer(Modifier.width(8.dp))
                        DrillTag()
                    }
                }
                Text(
                    formatDateTime(incident.detectedAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    incident.status.label(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = tint
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
internal fun DrillTag() {
    Surface(color = MaterialTheme.appColors.warningContainer, shape = RoundedCornerShape(4.dp)) {
        Text(
            "SIMULACRO",
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.appColors.onWarningContainer,
            fontWeight = FontWeight.Bold
        )
    }
}

/** Icono y color por estado: verde si el aviso salio, rojo si fallo, gris si no hizo falta. */
@Composable
internal fun statusVisual(status: IncidentStatus): Pair<ImageVector, Color> = when (status) {
    IncidentStatus.DISPATCHED -> Icons.Default.CheckCircle to MaterialTheme.appColors.success
    IncidentStatus.DISPATCH_PARTIAL -> Icons.Default.Warning to MaterialTheme.appColors.warning
    IncidentStatus.DISPATCH_FAILED -> Icons.Default.Error to MaterialTheme.colorScheme.error
    IncidentStatus.ACTIVE -> Icons.Default.HourglassTop to MaterialTheme.colorScheme.primary
    IncidentStatus.CANCELLED_BY_USER,
    IncidentStatus.NOT_CONFIRMED,
    IncidentStatus.STALE_EVENT ->
        Icons.Default.RemoveCircleOutline to MaterialTheme.colorScheme.onSurfaceVariant
}
