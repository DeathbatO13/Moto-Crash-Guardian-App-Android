package com.motocrashguardian.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import com.motocrashguardian.core.model.AppSettings
import com.motocrashguardian.core.model.ContactRole
import com.motocrashguardian.core.model.DetectionConfig
import com.motocrashguardian.core.model.DeviceInfo
import com.motocrashguardian.core.model.EmergencyContact
import com.motocrashguardian.data.settings.proto.AppSettingsProto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.time.Instant
import java.util.UUID

class SettingsRepositoryTest {

    @TempDir
    lateinit var tempDir: File

    @Test
    fun `primera lectura expone los valores por defecto documentados`() = runTest {
        val repository = newRepository()

        val settings = repository.settings.first()

        assertEquals(AppSettings(), settings)
    }

    @Test
    fun `persiste ajustes contactos configuracion dispositivo y consentimiento`() = runTest {
        val repository = newRepository()
        val settings = AppSettings(
            riderName = "Motociclista",
            primaryContact = EmergencyContact(ContactRole.PRIMARY, "Ana", "+573001234567"),
            secondaryContact = EmergencyContact(ContactRole.SECONDARY, "Luis", "+573009876543"),
            detection = DetectionConfig(
                impactThresholdMg = 4_000,
                gyroThresholdDps = 400,
                tiltThresholdDeg = 70,
                tiltHoldMs = 1_500,
                tiltDetectionEnabled = false,
                severeImpactThresholdMg = 9_000,
                confirmWindowMs = 6_000,
                stillnessToleranceMg = 250,
                pitchOffsetCdeg = -1_410,
                rollOffsetCdeg = -730
            ),
            countdownSeconds = 35,
            demoMode = true,
            drillPlaceCall = true,
            pairedDeviceAddress = "AA:BB:CC:DD:EE:FF",
            pairedDeviceInfo = DeviceInfo(
                protocolVersion = 1,
                firmwareMajor = 1,
                firmwareMinor = 2,
                firmwarePatch = 3,
                hardwareRevision = 1,
                capabilities = 7,
                bootCount = 57,
                deviceId = "AABBCCDDEEFF"
            ),
            tripActive = true,
            onboardingCompleted = true,
            consentAcceptedAt = Instant.parse("2026-10-05T23:00:00.123Z"),
            configDirty = true,
            contactsDirty = true
        )

        repository.replaceSettings(settings)

        assertEquals(settings, repository.settings.first())
    }

    @Test
    fun `actualizacion atomica conserva el resto y permite borrar datos opcionales`() = runTest {
        val original = AppSettings(
            riderName = "Motociclista",
            primaryContact = EmergencyContact(ContactRole.PRIMARY, "Ana", "+573001234567"),
            pairedDeviceAddress = "AA:BB:CC:DD:EE:FF",
            consentAcceptedAt = Instant.parse("2026-10-05T23:00:00Z")
        )
        val repository = SettingsRepository(InMemoryDataStore(original.toProto()))

        repository.updateSettings {
            it.copy(
                riderName = "Nueva persona",
                primaryContact = null,
                pairedDeviceAddress = null,
                consentAcceptedAt = null
            )
        }

        val updated = repository.settings.first()
        assertEquals("Nueva persona", updated.riderName)
        assertNull(updated.primaryContact)
        assertNull(updated.pairedDeviceAddress)
        assertNull(updated.consentAcceptedAt)
        assertEquals(original.detection, updated.detection)
        assertFalse(updated.tripActive)
    }

    private fun TestScope.newRepository(): SettingsRepository {
        val settingsFile = File(tempDir, "${UUID.randomUUID()}.pb")
        val dataStore = DataStoreFactory.create(
            serializer = SettingsProtoSerializer,
            scope = backgroundScope,
            produceFile = { settingsFile }
        )
        return SettingsRepository(dataStore)
    }

    private class InMemoryDataStore<T>(initialValue: T) : DataStore<T> {
        private val mutex = Mutex()
        private val current = MutableStateFlow(initialValue)

        override val data: Flow<T> = current

        override suspend fun updateData(transform: suspend (t: T) -> T): T =
            mutex.withLock {
                transform(current.value).also { current.value = it }
            }
    }
}
