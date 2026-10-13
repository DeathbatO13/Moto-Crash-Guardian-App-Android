package com.motocrashguardian.data.privacy

import com.motocrashguardian.core.model.AppSettings
import com.motocrashguardian.data.incidents.GuardianDatabase
import com.motocrashguardian.data.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Vacia Room y devuelve los ajustes al estado de fabrica, lo que reinicia el onboarding. */
class RoomEraseLocalStore(
    private val database: GuardianDatabase,
    private val settings: SettingsRepository
) : EraseLocalStore {

    override suspend fun wipe() {
        withContext(Dispatchers.IO) { database.clearAllTables() }
        settings.replaceSettings(AppSettings())
    }
}
