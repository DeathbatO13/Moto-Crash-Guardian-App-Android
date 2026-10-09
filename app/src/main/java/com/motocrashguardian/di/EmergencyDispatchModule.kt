package com.motocrashguardian.di

import android.content.Context
import com.motocrashguardian.data.incidents.IncidentRepository
import com.motocrashguardian.data.settings.SettingsRepository
import com.motocrashguardian.detection.GuardianStateMachine
import com.motocrashguardian.emergency.AlarmPlayer
import com.motocrashguardian.emergency.CallDispatcher
import com.motocrashguardian.emergency.CallTransport
import com.motocrashguardian.emergency.DispatchOrchestrator
import com.motocrashguardian.emergency.EmergencyFlowController
import com.motocrashguardian.emergency.GsmMessageBuilder
import com.motocrashguardian.emergency.LocationAcquirer
import com.motocrashguardian.emergency.platform.AndroidAlarmOutput
import com.motocrashguardian.emergency.platform.FusedLastKnownLocationSource
import com.motocrashguardian.emergency.platform.FusedPhoneLocationSource
import com.motocrashguardian.emergency.platform.GuardianAlertNotifications
import com.motocrashguardian.emergency.platform.NoDeviceGpsSource
import com.motocrashguardian.emergency.SmsDispatcher
import com.motocrashguardian.emergency.SmsTransport
import com.motocrashguardian.emergency.platform.AndroidCallPermission
import com.motocrashguardian.emergency.platform.AndroidSmsPermission
import com.motocrashguardian.emergency.platform.AndroidSmsTransport
import com.motocrashguardian.emergency.platform.AndroidTelecomCallTransport
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object EmergencyDispatchModule {
    @Provides
    @Singleton
    fun provideAndroidSmsTransport(
        @ApplicationContext context: Context
    ): AndroidSmsTransport = AndroidSmsTransport(context)

    @Provides
    fun provideSmsTransport(transport: AndroidSmsTransport): SmsTransport = transport

    @Provides
    fun provideSmsDispatcher(
        @ApplicationContext context: Context,
        transport: SmsTransport
    ): SmsDispatcher = SmsDispatcher(
        transport = transport,
        hasSendPermission = AndroidSmsPermission(context)::invoke
    )

    @Provides
    fun provideDispatchOrchestrator(
        smsDispatcher: SmsDispatcher,
        callDispatcher: CallDispatcher,
        settingsRepository: SettingsRepository,
        incidentRepository: IncidentRepository
    ): DispatchOrchestrator = DispatchOrchestrator(
        smsDispatcher = smsDispatcher,
        callDispatcher = callDispatcher,
        messageBuilder = GsmMessageBuilder(),
        loadSettings = { settingsRepository.settings.first() },
        saveIncident = incidentRepository::updateIncident
    )

    @Provides
    @Singleton
    fun provideAlarmPlayer(@ApplicationContext context: Context): AlarmPlayer =
        AlarmPlayer(AndroidAlarmOutput(context))

    @Provides
    @Singleton
    fun provideLocationAcquirer(@ApplicationContext context: Context): LocationAcquirer =
        LocationAcquirer(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
            phoneSource = FusedPhoneLocationSource(context),
            lastKnownSource = FusedLastKnownLocationSource(context),
            deviceGps = NoDeviceGpsSource()
        )

    @Provides
    @Singleton
    fun provideEmergencyFlowController(
        @ApplicationContext context: Context,
        machine: GuardianStateMachine,
        orchestrator: DispatchOrchestrator,
        alarm: AlarmPlayer,
        locationAcquirer: LocationAcquirer
    ): EmergencyFlowController {
        val notifications by lazy { GuardianAlertNotifications(context) }
        return EmergencyFlowController(
            machine = machine,
            orchestrator = orchestrator,
            alarm = alarm,
            locationAcquirer = locationAcquirer,
            showCountdown = { notifications.showCountdown(it) },
            cancelCountdownNotification = { notifications.cancelCountdown() }
        )
    }

    @Provides
    @Singleton
    fun provideCallTransport(
        @ApplicationContext context: Context
    ): CallTransport = AndroidTelecomCallTransport(context)

    @Provides
    fun provideCallDispatcher(
        @ApplicationContext context: Context,
        transport: CallTransport
    ): CallDispatcher = CallDispatcher(
        transport = transport,
        hasCallPermission = AndroidCallPermission(context)::invoke
    )
}
