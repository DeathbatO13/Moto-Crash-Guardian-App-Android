package com.motocrashguardian.ui.onboarding

import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.companion.AssociationRequest
import android.companion.BluetoothLeDeviceFilter
import android.companion.CompanionDeviceManager
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.os.Build
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.motocrashguardian.ble.DeviceConnectionState
import com.motocrashguardian.ble.DeviceRepository
import com.motocrashguardian.ble.protocol.BleProtocol
import com.motocrashguardian.data.settings.SettingsRepository
import com.motocrashguardian.service.GuardianService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

@HiltViewModel
class PairingViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val deviceRepository: DeviceRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<PairingUiState>(PairingUiState.Idle)
    val uiState: StateFlow<PairingUiState> = _uiState.asStateFlow()

    private val deviceManager = context.getSystemService(Context.COMPANION_DEVICE_SERVICE) as? CompanionDeviceManager
    private var timeoutJob: Job? = null

    fun startPairing(activity: Activity) {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter = bluetoothManager?.adapter

        if (deviceManager == null) {
            _uiState.value = PairingUiState.Error("Tu dispositivo no soporta la vinculación de accesorios.")
            return
        }

        if (adapter == null || !adapter.isEnabled) {
            _uiState.value = PairingUiState.Error("Por favor, activa el Bluetooth para continuar.")
            return
        }

        _uiState.value = PairingUiState.Searching
        Log.d("PairingViewModel", "Iniciando búsqueda de dispositivos...")

        timeoutJob?.cancel()
        timeoutJob = viewModelScope.launch {
            delay(40000)
            if (_uiState.value is PairingUiState.Searching) {
                _uiState.value = PairingUiState.Error("No se encontró el sensor. Asegúrate de que esté encendido y cerca.")
            }
        }

        try {
            // CDM filtra por el UUID del servicio (docs/03 §1), no por el
            // nombre: con nombre y UUID juntos el módulo debe cumplir ambos.
            val filter = BluetoothLeDeviceFilter.Builder()
                .setScanFilter(android.bluetooth.le.ScanFilter.Builder()
                    .setServiceUuid(android.os.ParcelUuid.fromString(BleProtocol.SERVICE_UUID))
                    .build())
                .build()

            val pairingRequest = AssociationRequest.Builder()
                .addDeviceFilter(filter)
                .setSingleDevice(true) // Diálogo directo de vinculación
                .build()

            val callback = object : CompanionDeviceManager.Callback() {
                override fun onDeviceFound(chooserLauncher: IntentSender) {
                    Log.d("PairingViewModel", "¡Dispositivo encontrado! Lanzando selector...")
                    timeoutJob?.cancel()
                    _uiState.value = PairingUiState.DeviceFound(chooserLauncher)
                }

                override fun onFailure(error: CharSequence?) {
                    Log.e("PairingViewModel", "Error CDM: $error")
                    timeoutJob?.cancel()
                    _uiState.value = PairingUiState.Error(error?.toString() ?: "Error al buscar")
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                deviceManager.associate(pairingRequest, activity.mainExecutor, callback)
            } else {
                deviceManager.associate(pairingRequest, callback, null)
            }
        } catch (e: Exception) {
            Log.e("PairingViewModel", "Error al iniciar asociación", e)
            timeoutJob?.cancel()
            _uiState.value = PairingUiState.Error("Error técnico: ${e.localizedMessage}")
        }
    }

    /**
     * Guarda el módulo elegido y se conecta de verdad: el éxito solo se
     * muestra cuando el módulo respondió (servicios + DEVICE_INFO).
     */
    fun onDeviceSelected(address: String) {
        viewModelScope.launch {
            _uiState.value = PairingUiState.Connecting
            try {
                settingsRepository.updateSettings { current ->
                    current.copy(pairedDeviceAddress = address, onboardingCompleted = true)
                }
            } catch (e: Exception) {
                _uiState.value = PairingUiState.Error("No se pudo guardar el dispositivo")
                return@launch
            }
            // GuardianService mantiene la conexión y reintenta si falla.
            context.startForegroundService(
                Intent(context, GuardianService::class.java).setAction(GuardianService.ACTION_CONNECT)
            )
            val ready = withTimeoutOrNull(CONNECT_TIMEOUT_MS) {
                deviceRepository.connectionState.first { it == DeviceConnectionState.READY }
            }
            _uiState.value = if (ready != null) {
                PairingUiState.Success
            } else {
                PairingUiState.Error(
                    "El módulo quedó guardado, pero no respondió. Verifica que esté encendido " +
                        "y cerca; la app seguirá intentando conectarse."
                )
            }
        }
    }

    fun completeWithoutPairing() {
        viewModelScope.launch {
            settingsRepository.updateSettings { it.copy(onboardingCompleted = true) }
            _uiState.value = PairingUiState.Success
        }
    }

    fun reset() {
        timeoutJob?.cancel()
        _uiState.value = PairingUiState.Idle
    }

    override fun onCleared() {
        super.onCleared()
        timeoutJob?.cancel()
    }
}

private const val CONNECT_TIMEOUT_MS = 30_000L

sealed class PairingUiState {
    object Idle : PairingUiState()
    object Searching : PairingUiState()
    data class DeviceFound(val intentSender: IntentSender) : PairingUiState()
    object Connecting : PairingUiState()
    object Success : PairingUiState()
    data class Error(val message: String) : PairingUiState()
}
