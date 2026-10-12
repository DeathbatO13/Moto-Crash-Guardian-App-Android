package com.motocrashguardian.di

import android.content.Context
import com.motocrashguardian.ble.DeviceRepository
import com.motocrashguardian.ble.DeviceRepositoryImpl
import com.motocrashguardian.ble.GuardianBleManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Named
import javax.inject.Singleton

/** Capa BLE (docs/05 §2-3): manager GATT, su scope y el repositorio. */
@Module
@InstallIn(SingletonComponent::class)
object BleModule {
    @Provides
    @Singleton
    fun provideGuardianBleManager(@ApplicationContext context: Context): GuardianBleManager =
        GuardianBleManager(context)

    /** Scope de los flujos calientes del repositorio (telemetría, eventos…). */
    @Provides
    @Singleton
    @Named("ble")
    fun provideBleScope(): CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Provides
    @Singleton
    fun provideDeviceRepository(
        manager: GuardianBleManager,
        @Named("ble") scope: CoroutineScope
    ): DeviceRepository = DeviceRepositoryImpl(manager, scope)
}
