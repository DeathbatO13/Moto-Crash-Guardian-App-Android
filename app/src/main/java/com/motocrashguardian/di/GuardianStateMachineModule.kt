package com.motocrashguardian.di

import com.motocrashguardian.data.incidents.IncidentRepository
import com.motocrashguardian.data.settings.SettingsRepository
import com.motocrashguardian.detection.ConfirmationEngine
import com.motocrashguardian.detection.GuardianStateMachine
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object GuardianStateMachineModule {
    @Provides
    @Singleton
    fun provideGuardianStateMachine(
        incidentRepository: IncidentRepository,
        settingsRepository: SettingsRepository
    ): GuardianStateMachine = GuardianStateMachine(
        incidentRepository = incidentRepository,
        settingsRepository = settingsRepository,
        confirmationEngine = ConfirmationEngine(),
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    )
}
