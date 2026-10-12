package com.motocrashguardian.ui.onboarding

import android.app.Activity
import android.companion.CompanionDeviceManager
import android.bluetooth.BluetoothDevice
import android.os.Build
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.motocrashguardian.R

/**
 * S-04 Pantalla de emparejamiento.
 * Se añadió la opción de omitir para que no sea obligatorio
 * conectar el dispositivo para terminar el onboarding.
 */
@Composable
fun PairingScreen(
    onBack: () -> Unit = {},
    onFinish: () -> Unit = {},
    viewModel: PairingViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                result.data?.getParcelableExtra(
                    CompanionDeviceManager.EXTRA_DEVICE,
                    BluetoothDevice::class.java
                )
            } else {
                @Suppress("DEPRECATION")
                result.data?.getParcelableExtra(CompanionDeviceManager.EXTRA_DEVICE)
            }
            device?.address?.let { viewModel.onDeviceSelected(it) }
        } else {
            viewModel.reset()
        }
    }

    LaunchedEffect(uiState) {
        if (uiState is PairingUiState.DeviceFound) {
            val sender = (uiState as PairingUiState.DeviceFound).intentSender
            launcher.launch(IntentSenderRequest.Builder(sender).build())
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
            TextButton(onClick = onBack, enabled = uiState !is PairingUiState.Searching) {
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
                PairingUiState.Searching -> SearchingContent()
                is PairingUiState.DeviceFound -> SearchingContent()
                PairingUiState.Success -> SuccessContent()
                is PairingUiState.Error -> ErrorContent((uiState as PairingUiState.Error).message)
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // 4. Acciones
        PairingActions(
            uiState = uiState,
            onStart = { viewModel.startPairing() },
            onFinish = onFinish,
            onRetry = { viewModel.startPairing() },
            onSkip = { viewModel.completeWithoutPairing() }
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
private fun SearchingContent() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator(modifier = Modifier.size(64.dp))
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.pairing_searching),
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
    Icon(
        imageVector = Icons.Default.Error,
        contentDescription = null,
        modifier = Modifier.size(120.dp),
        tint = MaterialTheme.colorScheme.errorContainer
    )
}

@Composable
private fun PairingActions(
    uiState: PairingUiState,
    onStart: () -> Unit,
    onFinish: () -> Unit,
    onRetry: () -> Unit,
    onSkip: () -> Unit
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
            PairingUiState.Searching, is PairingUiState.DeviceFound -> {
                // Diálogo del sistema activo
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
        color = MaterialTheme.colorScheme.surfaceContainerLowest
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = stringResource(R.string.pairing_info_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.pairing_info_body),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
