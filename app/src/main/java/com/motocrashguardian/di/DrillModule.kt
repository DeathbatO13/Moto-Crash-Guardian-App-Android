package com.motocrashguardian.di

import com.motocrashguardian.ble.DeviceConnectionState
import com.motocrashguardian.ble.DeviceRepository
import com.motocrashguardian.ble.protocol.BleProtocol
import com.motocrashguardian.detection.GuardianStateMachine
import com.motocrashguardian.emergency.DrillStarter
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object DrillModule {

    @Provides
    fun provideDrillStarter(
        stateMachine: GuardianStateMachine,
        deviceRepository: DeviceRepository
    ): DrillStarter = DrillStarter(
        guardianState = stateMachine.state,
        deviceReady = { deviceRepository.connectionState.value == DeviceConnectionState.READY },
        triggerDeviceTest = {
            deviceRepository.triggerTestEvent().result == BleProtocol.RESULT_OK
        },
        startLocalDrill = { stateMachine.startDrill() }
    )
}
