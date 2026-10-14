package com.motocrashguardian.di

import com.motocrashguardian.ble.DeviceConnectionState
import com.motocrashguardian.ble.DeviceRepository
import com.motocrashguardian.ble.ModuleArmer
import com.motocrashguardian.ble.protocol.BleProtocol
import com.motocrashguardian.core.model.DeviceState
import com.motocrashguardian.detection.GuardianStateMachine
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ArmingModule {

    @OptIn(ExperimentalCoroutinesApi::class)
    @Provides
    @Singleton
    fun provideModuleArmer(
        device: DeviceRepository,
        stateMachine: GuardianStateMachine
    ): ModuleArmer {
        val connected = device.connectionState
            .map { it == DeviceConnectionState.READY }
            .distinctUntilChanged()
        // Sin conexion no hay estado del modulo: evita usar la ultima telemetria de una sesion vieja.
        val deviceState: Flow<DeviceState?> = connected.flatMapLatest { isConnected ->
            if (isConnected) device.telemetry.map<_, DeviceState?> { it.state } else flowOf(null)
        }.distinctUntilChanged()
        return ModuleArmer(
            connected = connected,
            guardianState = stateMachine.state,
            deviceState = deviceState,
            arm = { device.arm().result == BleProtocol.RESULT_OK },
            disarm = { device.disarm().result == BleProtocol.RESULT_OK }
        )
    }
}
