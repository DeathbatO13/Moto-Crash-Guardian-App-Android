package com.motocrashguardian.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.motocrashguardian.core.model.Incident
import com.motocrashguardian.core.model.IncidentType
import com.motocrashguardian.core.model.LocationSource
import java.util.Locale

@Composable
fun IncidentDetailScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: IncidentDetailViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current
    IncidentDetailContent(
        state = state,
        onBack = onBack,
        onOpenMap = { latitude, longitude ->
            uriHandler.openUri(
                String.format(Locale.ROOT, "https://maps.google.com/?q=%.6f,%.6f", latitude, longitude)
            )
        },
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IncidentDetailContent(
    state: DetailUiState,
    onBack: () -> Unit,
    onOpenMap: (latitude: Double, longitude: Double) -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Detalle del incidente", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                }
            )
        }
    ) { padding ->
        when (state) {
            DetailUiState.Loading -> Box(
                Modifier
                    .padding(padding)
                    .fillMaxSize(),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }

            DetailUiState.NotFound -> Box(
                Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Este incidente ya no existe en el historial.",
                    style = MaterialTheme.typography.bodyLarge
                )
            }

            is DetailUiState.Loaded -> Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                SummaryCard(state.incident)
                WhatHappenedCard(state.incident)
                DispatchCard(state.incident)
                LocationCard(state.incident, onOpenMap)
                SensorCard(state)
                SyncCard(state.incident)
            }
        }
    }
}

@Composable
private fun SummaryCard(incident: Incident) {
    val (icon, tint) = statusVisual(incident.status)
    Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(36.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    incident.status.label(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = tint
                )
                Text(
                    formatDateTime(incident.detectedAt),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (incident.type == IncidentType.DRILL) DrillTag()
        }
    }
}

@Composable
private fun WhatHappenedCard(incident: Incident) {
    Card(title = "Qué pasó") {
        Row2("Detonante", incident.triggerType.label())
        incident.peakAccelMg?.let { Row2("Pico de aceleración", formatG(it)) }
        incident.peakGyroDps?.let { Row2("Pico de giro", "$it °/s") }
        if (incident.pitchCdeg != null && incident.rollCdeg != null) {
            Row2(
                "Inclinación",
                "cabeceo ${formatDegrees(incident.pitchCdeg)} · alabeo ${formatDegrees(incident.rollCdeg)}"
            )
        }
    }
}

@Composable
private fun DispatchCard(incident: Incident) {
    Card(title = "Aviso a tus contactos") {
        Row2("SMS al contacto principal", incident.primarySmsStatus.label())
        Row2("SMS al contacto secundario", incident.secondarySmsStatus.label())
        Row2("Llamada", incident.callStatus.label())
    }
}

@Composable
private fun LocationCard(incident: Incident, onOpenMap: (Double, Double) -> Unit) {
    val latitude = incident.latitude
    val longitude = incident.longitude
    Card(title = "Ubicación") {
        if (incident.locationSource == LocationSource.NONE || latitude == null || longitude == null) {
            Text(
                "No se pudo obtener la ubicación.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Row2("Fuente", incident.locationSource.label())
            Row2("Coordenadas", String.format(Locale.ROOT, "%.6f, %.6f", latitude, longitude))
            incident.locationAccuracyMeters?.let {
                Row2("Precisión", String.format(Locale.forLanguageTag("es-CO"), "±%.0f m", it))
            }
            incident.locationFixAt?.let { Row2("Obtenida", formatDateTime(it)) }
            OutlinedButton(
                onClick = { onOpenMap(latitude, longitude) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Map, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Abrir en el mapa")
            }
        }
    }
}

@Composable
private fun SensorCard(state: DetailUiState.Loaded) {
    Card(title = "Datos del sensor") {
        val trace = state.trace
        if (trace == null || trace.window().points.isEmpty()) {
            Text(
                "Sin datos del sensor para este incidente.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            TraceChart(trace = trace, impactThresholdG = state.impactThresholdG)
        }
    }
}

@Composable
private fun SyncCard(incident: Incident) {
    Card(title = "Respaldo") {
        Row2("Estado", incident.syncState.label())
        incident.firmwareVersion?.let { Row2("Firmware del módulo", it) }
    }
}

@Composable
private fun Card(title: String? = null, content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (title != null) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            }
            content()
        }
    }
}

@Composable
private fun Row2(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(
            label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            value,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}
