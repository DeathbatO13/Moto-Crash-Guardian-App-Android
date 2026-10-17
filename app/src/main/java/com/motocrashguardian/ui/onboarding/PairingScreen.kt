package com.motocrashguardian.ui.onboarding

import android.app.Activity
import android.bluetooth.BluetoothDevice
import android.bluetooth.le.ScanResult
import android.companion.AssociationInfo
import android.companion.CompanionDeviceManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Build
import android.os.Parcelable
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.motocrashguardian.R

/**
 * Función de utilidad para encontrar la Activity real desde el contexto de Compose.
 * Necesario porque LocalContext.current puede devolver un ContextWrapper.
 */
fun Context.findActivity(): Activity? {
    var context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}

/**
 * Dirección MAC elegida en el selector de CDM. Con un filtro BLE,
 * [CompanionDeviceManager.EXTRA_DEVICE] trae un [ScanResult], no un
 * [BluetoothDevice]; desde API 33 también viene [AssociationInfo].
 */
private fun Intent.associatedDeviceAddress(): String? {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getParcelableExtra(CompanionDeviceManager.EXTRA_ASSOCIATION, AssociationInfo::class.java)
            ?.deviceMacAddress?.toString()?.uppercase()
            ?.let { return it }
    }
    @Suppress("DEPRECATION")
    return when (val extra = getParcelableExtra<Parcelable>(CompanionDeviceManager.EXTRA_DEVICE)) {
        is ScanResult -> extra.device.address
        is BluetoothDevice -> extra.address
        else -> null
    }
}

/**
 * S-04 Pantalla de emparejamiento.
 */
@Composable
fun PairingScreen(
    onBack: () -> Unit = {},
    onFinish: () -> Unit = {},
    viewModel: PairingViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        Log.d("PairingScreen", "Resultado del selector: ${result.resultCode}")
        if (result.resultCode == Activity.RESULT_OK) {
            val address = result.data?.associatedDeviceAddress()
            if (address != null) {
                viewModel.onDeviceSelected(address)
            } else {
                Log.e("PairingScreen", "El selector no devolvió la dirección del módulo")
                viewModel.reset()
            }
        } else {
            viewModel.reset()
        }
    }

    LaunchedEffect(uiState) {
        if (uiState is PairingUiState.DeviceFound) {
            val sender = (uiState as PairingUiState.DeviceFound).intentSender
            Log.d("PairingScreen", "Lanzando IntentSender...")
            try {
                launcher.launch(IntentSenderRequest.Builder(sender).build())
            } catch (e: Exception) {
                Log.e("PairingScreen", "Error al lanzar IntentSender", e)
                viewModel.reset()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // 1. Barra superior
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(
                onClick = onBack,
                enabled = uiState !is PairingUiState.Searching && uiState !is PairingUiState.Connecting
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(text = "Volver")
            }
            AssistChip(
                onClick = {},
                label = {
                    Text(
                        text = stringResource(R.string.pairing_step),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // 2. Encabezado
        PairingHeader(uiState)

        Spacer(modifier = Modifier.height(32.dp))

        // 3. Contenido central
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center
        ) {
            when (uiState) {
                PairingUiState.Idle -> IdleContent()
                PairingUiState.Searching -> SearchingContent(stringResource(R.string.pairing_searching))
                is PairingUiState.DeviceFound -> SearchingContent("Abriendo selector de dispositivos...")
                PairingUiState.Connecting -> SearchingContent("Conectando con el módulo…")
                PairingUiState.Success -> SuccessContent()
                is PairingUiState.Error -> ErrorContent((uiState as PairingUiState.Error).message)
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // 4. Acciones
        PairingActions(
            uiState = uiState,
            onStart = { activity?.let { viewModel.startPairing(it) } },
            onFinish = onFinish,
            onRetry = { activity?.let { viewModel.startPairing(it) } },
            onSkip = { viewModel.completeWithoutPairing() },
            onCancel = { viewModel.reset() }
        )

        Spacer(modifier = Modifier.height(16.dp))
        
        if (uiState is PairingUiState.Idle) {
            InfoCard()
        }
        
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun PairingHeader(uiState: PairingUiState) {
    val title = when (uiState) {
        PairingUiState.Success -> stringResource(R.string.pairing_success_title)
        is PairingUiState.Error -> stringResource(R.string.pairing_error_title)
        else -> stringResource(R.string.pairing_title)
    }
    
    val icon = when (uiState) {
        PairingUiState.Success -> Icons.Default.CheckCircle
        is PairingUiState.Error -> Icons.Default.Error
        else -> Icons.Default.Bluetooth
    }
    
    val iconTint = when (uiState) {
        PairingUiState.Success -> MaterialTheme.colorScheme.primary
        is PairingUiState.Error -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.primary
    }

    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = iconTint
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "S-04 · CONEXIÓN",
                style = MaterialTheme.typography.labelSmall,
                color = iconTint
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = when (uiState) {
                PairingUiState.Success -> stringResource(R.string.pairing_success_description)
                is PairingUiState.Error -> (uiState as PairingUiState.Error).message
                else -> stringResource(R.string.pairing_description)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun IdleContent() {
    Icon(
        imageVector = Icons.Default.Bluetooth,
        contentDescription = null,
        modifier = Modifier.size(120.dp),
        tint = MaterialTheme.colorScheme.surfaceVariant
    )
}

@Composable
private fun SearchingContent(text: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator(modifier = Modifier.size(64.dp))
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun SuccessContent() {
    Icon(
        imageVector = Icons.Default.CheckCircle,
        contentDescription = null,
        modifier = Modifier.size(120.dp),
        tint = MaterialTheme.colorScheme.primary
    )
}

@Composable
private fun ErrorContent(message: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            imageVector = Icons.Default.Error,
            contentDescription = null,
            modifier = Modifier.size(120.dp),
            tint = MaterialTheme.colorScheme.error
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
    }
}

@Composable
private fun PairingActions(
    uiState: PairingUiState,
    onStart: () -> Unit,
    onFinish: () -> Unit,
    onRetry: () -> Unit,
    onSkip: () -> Unit,
    onCancel: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when (uiState) {
            PairingUiState.Idle -> {
                Button(
                    onClick = onStart,
                    modifier = Modifier.fillMaxWidth().height(56.dp)
                ) {
                    Icon(imageVector = Icons.Default.Search, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = stringResource(R.string.pairing_button_start))
                }
                OutlinedButton(
                    onClick = onSkip,
                    modifier = Modifier.fillMaxWidth().height(56.dp)
                ) {
                    Text(text = "OMITIR POR AHORA")
                }
            }
            PairingUiState.Connecting -> Unit
            PairingUiState.Searching, is PairingUiState.DeviceFound -> {
                TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
                    Text(text = "CANCELAR")
                }
            }
            PairingUiState.Success -> {
                Button(
                    onClick = onFinish,
                    modifier = Modifier.fillMaxWidth().height(56.dp)
                ) {
                    Text(text = stringResource(R.string.pairing_button_finish))
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(imageVector = Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
                }
            }
            is PairingUiState.Error -> {
                Button(
                    onClick = onRetry,
                    modifier = Modifier.fillMaxWidth().height(56.dp)
                ) {
                    Text(text = stringResource(R.string.pairing_error_retry))
                }
                TextButton(
                    onClick = onSkip,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = "OMITIR Y TERMINAR")
                }
            }
        }
    }
}

@Composable
private fun InfoCard() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = "Para vincular el dispositivo, asegúrate de que esté encendido y cerca de tu teléfono.",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
