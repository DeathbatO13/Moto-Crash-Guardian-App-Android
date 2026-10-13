package com.motocrashguardian.di

import android.content.Context
import androidx.work.WorkManager
import com.motocrashguardian.BuildConfig
import com.motocrashguardian.data.incidents.IncidentRepository
import com.motocrashguardian.data.remote.AuthSession
import com.motocrashguardian.data.remote.McgApi
import com.motocrashguardian.data.settings.SettingsRepository
import com.motocrashguardian.data.sync.RoomSyncLocalStore
import com.motocrashguardian.data.sync.SyncCoordinator
import com.motocrashguardian.data.sync.SyncEngine
import com.motocrashguardian.data.sync.SyncLocalStore
import com.motocrashguardian.data.sync.SyncScheduler
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SyncModule {

    @Provides
    @Singleton
    fun provideSyncLocalStore(
        incidents: IncidentRepository,
        settings: SettingsRepository
    ): SyncLocalStore = RoomSyncLocalStore(incidents, settings)

    @Provides
    @Singleton
    fun provideSyncEngine(
        api: McgApi,
        auth: AuthSession,
        store: SyncLocalStore
    ): SyncEngine = SyncEngine(api, auth, store, appVersion = BuildConfig.VERSION_NAME)

    @Provides
    @Singleton
    fun provideSyncScheduler(@ApplicationContext context: Context): SyncScheduler =
        SyncScheduler { WorkManager.getInstance(context) }

    @Provides
    @Singleton
    fun provideSyncCoordinator(
        incidents: IncidentRepository,
        settings: SettingsRepository,
        scheduler: SyncScheduler
    ): SyncCoordinator = SyncCoordinator(incidents.incidents, settings.settings, scheduler)
}
