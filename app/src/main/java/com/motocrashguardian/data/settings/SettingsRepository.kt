package com.motocrashguardian.data.settings

import androidx.datastore.core.DataStore
import com.motocrashguardian.core.model.AppSettings
import com.motocrashguardian.data.settings.proto.AppSettingsProto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class SettingsRepository @Inject constructor(
    private val dataStore: DataStore<AppSettingsProto>
) {
    val settings: Flow<AppSettings> = dataStore.data.map(AppSettingsProto::toDomain)

    suspend fun updateSettings(transform: (AppSettings) -> AppSettings) {
        dataStore.updateData { stored -> transform(stored.toDomain()).toProto() }
    }

    suspend fun replaceSettings(settings: AppSettings) {
        dataStore.updateData { settings.toProto() }
    }
}
