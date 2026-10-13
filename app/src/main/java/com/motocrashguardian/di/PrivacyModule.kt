package com.motocrashguardian.di

import android.content.Context
import android.content.Intent
import com.motocrashguardian.data.incidents.GuardianDatabase
import com.motocrashguardian.data.privacy.DataEraser
import com.motocrashguardian.data.privacy.DataErasing
import com.motocrashguardian.data.privacy.RoomEraseLocalStore
import com.motocrashguardian.data.remote.AuthSession
import com.motocrashguardian.data.remote.McgApi
import com.motocrashguardian.data.settings.SettingsRepository
import com.motocrashguardian.data.sync.SyncEngine
import com.motocrashguardian.data.sync.SyncScheduler
import com.motocrashguardian.detection.GuardianStateMachine
import com.motocrashguardian.service.GuardianService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object PrivacyModule {

    @Provides
    @Singleton
    fun provideDataEraser(
        @ApplicationContext context: Context,
        api: McgApi,
        auth: AuthSession,
        database: GuardianDatabase,
        settings: SettingsRepository,
        syncEngine: SyncEngine,
        scheduler: SyncScheduler,
        stateMachine: GuardianStateMachine
    ): DataErasing = DataEraser(
        api = api,
        auth = auth,
        local = RoomEraseLocalStore(database, settings),
        lock = syncEngine.lock,
        stopProtection = {
            // Sin viaje activo no hay nada que detener; un fallo aqui no debe impedir el borrado.
            try {
                stateMachine.stopTrip()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
            }
            context.stopService(Intent(context, GuardianService::class.java))
        },
        cancelPendingSync = scheduler::cancelPending
    )
}
