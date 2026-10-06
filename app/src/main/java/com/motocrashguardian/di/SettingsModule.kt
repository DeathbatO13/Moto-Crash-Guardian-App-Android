package com.motocrashguardian.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.dataStoreFile
import com.motocrashguardian.data.settings.SettingsProtoSerializer
import com.motocrashguardian.data.settings.proto.AppSettingsProto
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SettingsModule {
    @Provides
    @Singleton
    fun provideSettingsDataStore(
        @ApplicationContext context: Context
    ): DataStore<AppSettingsProto> = DataStoreFactory.create(
        serializer = SettingsProtoSerializer,
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
        produceFile = { context.dataStoreFile("settings.pb") }
    )
}
