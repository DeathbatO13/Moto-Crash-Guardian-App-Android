package com.motocrashguardian.di

import com.motocrashguardian.ble.DeviceRepository
import com.motocrashguardian.ble.TraceDownloadCoordinator
import com.motocrashguardian.ble.TraceDownloader
import com.motocrashguardian.ble.TraceFetcher
import com.motocrashguardian.ble.TraceStore
import com.motocrashguardian.core.model.Incident
import com.motocrashguardian.core.model.Trace
import com.motocrashguardian.data.incidents.IncidentRepository
import com.motocrashguardian.detection.GuardianStateMachine
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object TraceModule {

    @Provides
    @Singleton
    fun provideTraceFetcher(
        device: DeviceRepository,
        incidents: IncidentRepository
    ): TraceFetcher = TraceFetcher(
        source = TraceDownloader(device),
        store = object : TraceStore {
            override suspend fun incidentsMissingTrace(limit: Int): List<Incident> =
                incidents.incidentsMissingTrace(limit)

            override suspend fun attachTrace(trace: Trace): Boolean = incidents.attachTrace(trace)
        },
        currentBootCount = { device.deviceInfo.value?.bootCount }
    )

    @Provides
    @Singleton
    fun provideTraceDownloadCoordinator(
        device: DeviceRepository,
        stateMachine: GuardianStateMachine,
        incidents: IncidentRepository,
        fetcher: TraceFetcher
    ): TraceDownloadCoordinator = TraceDownloadCoordinator(
        connectionState = device.connectionState,
        guardianState = stateMachine.state,
        incidents = incidents.incidents,
        fetcher = fetcher
    )
}
