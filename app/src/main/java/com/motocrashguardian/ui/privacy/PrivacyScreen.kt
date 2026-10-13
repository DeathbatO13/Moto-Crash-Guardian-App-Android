package com.motocrashguardian.ui.privacy

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** Privacidad y datos: qué guarda la app, dónde, el consentimiento y el borrado total. */
@Composable
fun PrivacyScreen(
    onBack: () -> Unit,
    onErased: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PrivacyViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    PrivacyContent(
        state = state,
        onBack = onBack,
        onRequestErase = viewModel::requestErase,
        onDismissErase = viewModel::dismissErase,
        onConfirmErase = viewModel::confirmErase,
        onErased = onErased,
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyContent(
    state: PrivacyUiState,
    onBack: () -> Unit,
    onRequestErase: () -> Unit,
    onDismissErase: () -> Unit,
    onConfirmErase: () -> Unit,
    onErased: () -> Unit,
    modifier: Modifier = Modifier
) {
    LaunchedEffect(state.erase) {
        if (state.erase == EraseUiState.Done) onErased()
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Privacidad y datos", fontWeight = FontWeight.Bold) },
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
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            LocalDataSection(state)
            ServerCopySection(state)
            ConsentSection(state)
            ErasureSection(onRequestErase)
        }
    }

    EraseDialog(
        state = state.erase,
        onConfirm = onConfirmErase,
        onDismiss = onDismissErase,
        onRetry = onRequestErase
    )
}

@Composable
private fun LocalDataSection(state: PrivacyUiState) {
    InfoCard(icon = Icons.Default.PhoneAndroid, title = "En este teléfono") {
        Line(if (state.hasRiderName) "Tu nombre" else "Tu nombre (aún sin configurar)")
        Line(
            when (state.contactCount) {
                0 -> "Contactos de emergencia (aún sin configurar)"
                1 -> "1 contacto de emergencia"
                else -> "${state.contactCount} contactos de emergencia"
            }
        )
        Line(
            when (state.incidentCount) {
                0 -> "Historial de incidentes (vacío)"
                1 -> "1 incidente, con su ubicación y datos del sensor"
                else -> "${state.incidentCount} incidentes, con su ubicación y datos del sensor"
            }
        )
    }
}

@Composable
private fun ServerCopySection(state: PrivacyUiState) {
    InfoCard(icon = Icons.Default.CloudDone, title = "Copia en el servidor") {
        if (!state.hasServerCopy) {
            Line("Todavía no se ha creado ninguna copia en el servidor.")
        } else {
            Line("Tus datos se respaldan en un servidor en Estados Unidos (Render).")
            Line(
                if (state.pendingUploads == 0) {
                    "Todo está al día."
                } else {
                    "${state.pendingUploads} elemento(s) pendientes de subir."
                }
            )
        }
        Line("La emergencia nunca depende del servidor: funciona sin internet.")
    }
}

@Composable
private fun ConsentSection(state: PrivacyUiState) {
    InfoCard(icon = Icons.Default.Gavel, title = "Tu consentimiento") {
        Line(
            state.consentAcceptedAt?.let { "Aceptado el ${formatDate(it)}." }
                ?: "Aún no has dado tu consentimiento."
        )
        Line(
            "Conforme a la Ley 1581 de 2012 puedes conocer, actualizar y suprimir tus datos, " +
                "y revocar tu autorización."
        )
    }
}

@Composable
private fun ErasureSection(onRequestErase: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Borrar mis datos",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.error
        )
        Text(
            text = "Elimina todo lo anterior del teléfono y del servidor, y revoca tu " +
                "consentimiento. No se puede deshacer.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedButton(
            onClick = onRequestErase,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
        ) {
            Icon(Icons.Default.DeleteForever, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("BORRAR MIS DATOS")
        }
    }
}

@Composable
private fun EraseDialog(
    state: EraseUiState,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    onRetry: () -> Unit
) {
    when (state) {
        EraseUiState.Idle, EraseUiState.Done -> Unit
        EraseUiState.Confirming -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("¿Borrar todos tus datos?") },
            text = {
                Text(
                    "Se eliminarán tu nombre, tus contactos y todo tu historial de incidentes, " +
                        "tanto en este teléfono como en el servidor. La protección se desactivará " +
                        "y la app volverá al inicio. Esta acción no se puede deshacer."
                )
            },
            confirmButton = {
                Button(
                    onClick = onConfirm,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) { Text("BORRAR TODO") }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
        )
        EraseUiState.Erasing -> AlertDialog(
            onDismissRequest = {},
            title = { Text("Borrando…") },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp))
                    Spacer(Modifier.width(16.dp))
                    Text("Eliminando tus datos del servidor y del teléfono.")
                }
            },
            confirmButton = {}
        )
        EraseUiState.NoConnection -> ErrorDialog(
            message = "No se pudo conectar con el servidor, así que no se borró nada. " +
                "Vuelve a intentarlo con conexión a internet.",
            onRetry = onRetry,
            onDismiss = onDismiss
        )
        is EraseUiState.Rejected -> ErrorDialog(
            message = "El servidor rechazó la solicitud (código ${state.httpCode}) y no se borró nada. " +
                "Inténtalo más tarde.",
            onRetry = onRetry,
            onDismiss = onDismiss
        )
        EraseUiState.LocalFailure -> ErrorDialog(
            message = "Tus datos se borraron del servidor, pero no se pudieron limpiar del " +
                "teléfono. Vuelve a intentarlo.",
            onRetry = onRetry,
            onDismiss = onDismiss
        )
    }
}

@Composable
private fun ErrorDialog(message: String, onRetry: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("No se pudo completar") },
        text = { Text(message) },
        confirmButton = { TextButton(onClick = onRetry) { Text("Reintentar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } }
    )
}

@Composable
private fun InfoCard(
    icon: ImageVector,
    title: String,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            }
            content()
        }
    }
}

@Composable
private fun Line(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

private fun formatDate(instant: Instant): String =
    DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
        .withLocale(Locale.forLanguageTag("es-CO"))
        .withZone(ZoneId.systemDefault())
        .format(instant)
