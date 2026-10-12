package com.motocrashguardian.ui.onboarding

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.companion.AssociationRequest
import android.companion.BluetoothLeDeviceFilter
import android.companion.CompanionDeviceManager
import android.content.Context
import android.content.IntentSender
import android.os.Build
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.motocrashguardian.ble.protocol.BleProtocol
import com.motocrashguardian.core.model.AppSettings
import com.motocrashguardian.data.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PairingViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<PairingUiState>(PairingUiState.Idle)
    val uiState: StateFlow<PairingUiState> = _uiState.asStateFlow()

    private val deviceManager = context.getSystemService(Context.COMPANION_DEVICE_SERVICE) as? CompanionDeviceManager

    fun startPairing() {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter = bluetoothManager?.adapter

        if (deviceManager == null) {
            _uiState.value = PairingUiState.Error("Tu dispositivo no soporta la vinculación de accesorios.")
            return
        }

        if (adapter == null) {
            _uiState.value = PairingUiState.Error("Bluetooth no disponible en este dispositivo.")
            return
        }

        if (!adapter.isEnabled) {
            _uiState.value = PairingUiState.Error("Por favor, activa el Bluetooth para continuar.")
            return
        }

        _uiState.value = PairingUiState.Searching

        try {
            val deviceFilter = BluetoothLeDeviceFilter.Builder()
                .setScanFilter(
                    android.bluetooth.le.ScanFilter.Builder()
                        .setServiceUuid(android.os.ParcelUuid.fromString(BleProtocol.SERVICE_UUID))
                        .build()
                )
                .build()

            val pairingRequest = AssociationRequest.Builder()
                .addDeviceFilter(deviceFilter)
                .setSingleDevice(true)
                .build()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                deviceManager.associate(pairingRequest, context.mainExecutor, object : CompanionDeviceManager.Callback() {
                    override fun onDeviceFound(chooserLauncher: IntentSender) {
                        _uiState.value = PairingUiState.DeviceFound(chooserLauncher)
                    }

                    override fun onFailure(error: CharSequence?) {
                        Log.e("PairingViewModel", "CDM Failure: $error")
                        _uiState.value = PairingUiState.Error(error?.toString() ?: "No se encontró el dispositivo")
                    }
                })
            } else {
                deviceManager.associate(pairingRequest, object : CompanionDeviceManager.Callback() {
                    override fun onDeviceFound(chooserLauncher: IntentSender) {
                        _uiState.value = PairingUiState.DeviceFound(chooserLauncher)
                    }

                    override fun onFailure(error: CharSequence?) {
                        Log.e("PairingViewModel", "CDM Failure: $error")
                        _uiState.value = PairingUiState.Error(error?.toString() ?: "No se encontró el dispositivo")
                    }
                }, null)
            }
        } catch (e: Exception) {
            Log.e("PairingViewModel", "Error starting pairing", e)
            _uiState.value = PairingUiState.Error("Error al buscar: ${e.localizedMessage}")
        }
    }

    fun onDeviceSelected(address: String) {
        viewModelScope.launch {
            try {
                settingsRepository.updateSettings { current ->
                    current.copy(
                        pairedDeviceAddress = address,
                        onboardingCompleted = true
                    )
                }
                _uiState.value = PairingUiState.Success
            } catch (e: Exception) {
                _uiState.value = PairingUiState.Error("No se pudo guardar el dispositivo")
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
        _uiState.value = PairingUiState.Idle
    }
}

sealed class PairingUiState {
    object Idle : PairingUiState()
    object Searching : PairingUiState()
    data class DeviceFound(val intentSender: IntentSender) : PairingUiState()
    object Success : PairingUiState()
    data class Error(val message: String) : PairingUiState()
}
