package com.motocrashguardian.data.incidents

import androidx.room.Room
import android.database.sqlite.SQLiteDatabase
import com.motocrashguardian.core.model.CallStatus
import com.motocrashguardian.core.model.Incident
import com.motocrashguardian.core.model.IncidentStatus
import com.motocrashguardian.core.model.IncidentType
import com.motocrashguardian.core.model.LocationSource
import com.motocrashguardian.core.model.SmsStatus
import com.motocrashguardian.core.model.SyncState
import com.motocrashguardian.core.model.Trace
import com.motocrashguardian.core.model.TriggerType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File
import java.time.Instant
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
class IncidentRepositoryTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private var database: GuardianDatabase? = null

    @After
    fun closeDatabase() {
        database?.close()
        database = null
    }

    @Test
    fun `guarda y recupera incidente y traza con metadatos y bytes intactos`() = runTest {
        val repository = newRepository()
        val incident = sampleIncident()
        val trace = sampleTrace(incident.id)

        assertTrue(repository.recordIncident(incident, trace))

        assertEquals(incident, repository.getIncident(incident.id))
        val savedTrace = requireNotNull(repository.getTrace(incident.id))
        assertEquals(trace.incidentId, savedTrace.incidentId)
        assertEquals(trace.sampleRateHz, savedTrace.sampleRateHz)
        assertEquals(trace.preTriggerSamples, savedTrace.preTriggerSamples)
        assertEquals(trace.totalSamples, savedTrace.totalSamples)
        assertEquals(trace.accelLsbPerG, savedTrace.accelLsbPerG)
        assertEquals(trace.gyroLsbPerDpsX10, savedTrace.gyroLsbPerDpsX10)
        assertEquals(trace.syncState, savedTrace.syncState)
        assertTrue(trace.samples.contentEquals(savedTrace.samples))
    }

    @Test
    fun `persiste incidentes al cerrar y volver a abrir la base local`() = runTest {
        val incident = sampleIncident()
        val databaseFile = File(tempFolder.root, "guardian.db")
        var repository = newRepository(databaseFile)
        assertTrue(repository.recordIncident(incident))
        database?.close()

        repository = newRepository(databaseFile)

        assertEquals(incident, repository.getIncident(incident.id))
    }

    @Test
    fun `migra esquema v1 conservando incidentes y deadline nulo`() = runTest {
        val databaseFile = File(tempFolder.root, "guardian-v1.db")
        createVersionOneDatabase(databaseFile)
        database = Room.databaseBuilder(
            RuntimeEnvironment.getApplication(),
            GuardianDatabase::class.java,
            databaseFile.absolutePath
        )
            .addMigrations(GuardianDatabase.MIGRATION_1_2)
            .allowMainThreadQueries()
            .build()
        val repository = IncidentRepository(requireNotNull(database))

        val migrated = requireNotNull(repository.getIncident(UUID.fromString("d23f7244-06a9-4f46-a615-4e6b1c650001")))

        assertEquals(IncidentStatus.NOT_CONFIRMED, migrated.status)
        assertEquals(Instant.ofEpochMilli(1_791_260_000_000), migrated.detectedAt)
        assertNull(migrated.countdownDeadline)
    }

    @Test
    fun `deduplica eventos por bootCount y eventId aunque cambie el UUID del incidente`() = runTest {
        val repository = newRepository()
        val original = sampleIncident(deviceEventKey = "57:3")
        val duplicate = original.copy(id = UUID.randomUUID())

        assertTrue(repository.recordIncident(original))
        assertFalse(repository.recordIncident(duplicate))
        assertEquals(1, database?.incidentDao()?.countIncidents())
    }

    @Test
    fun `elimina la traza en cascada al borrar el incidente`() = runTest {
        val repository = newRepository()
        val incident = sampleIncident()
        assertTrue(repository.recordIncident(incident, sampleTrace(incident.id)))

        repository.deleteIncident(incident.id)

        assertNull(repository.getIncident(incident.id))
        assertNull(repository.getTrace(incident.id))
    }

    @Test
    fun `conserva los cien incidentes mas recientes y borra por cascada los anteriores`() = runTest {
        val repository = newRepository()
        val oldest = sampleIncident(detectedAt = Instant.parse("2026-01-01T00:00:00Z"))
        assertTrue(repository.recordIncident(oldest, sampleTrace(oldest.id, totalSamples = 1)))

        repeat(100) { index ->
            val incident = sampleIncident(
                detectedAt = Instant.parse("2026-01-02T00:00:00Z").plusSeconds(index.toLong()),
                deviceEventKey = "20:$index"
            )
            assertTrue(repository.recordIncident(incident))
        }

        assertEquals(100, database?.incidentDao()?.countIncidents())
        assertEquals(0, database?.incidentDao()?.countTraces())
        assertNull(repository.getIncident(oldest.id))
    }

    @Test
    fun `retira trazas anteriores al limite sin borrar los incidentes ni trazas recientes`() = runTest {
        val repository = newRepository()
        val cutoff = Instant.parse("2026-10-06T00:00:00Z")
        val oldIncident = sampleIncident(detectedAt = cutoff.minusSeconds(1))
        val recentIncident = sampleIncident(detectedAt = cutoff.plusSeconds(1))
        assertTrue(repository.recordIncident(oldIncident, sampleTrace(oldIncident.id, totalSamples = 1)))
        assertTrue(repository.recordIncident(recentIncident, sampleTrace(recentIncident.id, totalSamples = 1)))

        assertEquals(1, repository.deleteTracesOlderThan(cutoff))

        assertEquals(2, database?.incidentDao()?.countIncidents())
        assertNull(repository.getTrace(oldIncident.id))
        assertTrue(repository.getTrace(recentIncident.id) != null)
    }

    @Test
    fun `flujo de observacion refleja inserciones`() = runTest {
        val repository = newRepository()
        val incident = sampleIncident()

        assertTrue(repository.recordIncident(incident))

        assertEquals(listOf(incident), repository.incidents.first())
    }

    private fun newRepository(
        file: File = File(tempFolder.root, "guardian-${UUID.randomUUID()}.db")
    ): IncidentRepository {
        database = Room.databaseBuilder(
            RuntimeEnvironment.getApplication(),
            GuardianDatabase::class.java,
            file.absolutePath
        )
            .allowMainThreadQueries()
            .build()
        return IncidentRepository(requireNotNull(database))
    }

    private fun sampleIncident(
        detectedAt: Instant = Instant.parse("2026-10-06T01:00:00Z"),
        deviceEventKey: String? = null
    ): Incident = Incident(
        type = IncidentType.REAL,
        triggerType = TriggerType.IMPACT,
        status = IncidentStatus.NOT_CONFIRMED,
        deviceEventKey = deviceEventKey,
        detectedAt = detectedAt,
        resolvedAt = detectedAt.plusSeconds(3),
        peakAccelMg = 5_230,
        peakGyroDps = 412,
        pitchCdeg = -350,
        rollCdeg = 8_420,
        latitude = 4.711,
        longitude = -74.072,
        locationAccuracyMeters = 8.5f,
        locationSource = LocationSource.PHONE_GPS,
        locationFixAt = detectedAt.minusSeconds(1),
        primarySmsStatus = SmsStatus.SENT,
        secondarySmsStatus = SmsStatus.FAILED,
        callStatus = CallStatus.NOT_ATTEMPTED,
        firmwareVersion = "1.2.3",
        syncState = SyncState.PENDING,
        syncAttempts = 2
    )

    private fun sampleTrace(incidentId: UUID, totalSamples: Int = 1_200): Trace = Trace(
        incidentId = incidentId,
        sampleRateHz = 200,
        preTriggerSamples = minOf(600, totalSamples),
        totalSamples = totalSamples,
        accelLsbPerG = 2_048,
        gyroLsbPerDpsX10 = 164,
        samples = ByteArray(totalSamples * 12) { (it % 251).toByte() },
        syncState = SyncState.PENDING
    )

    private fun createVersionOneDatabase(file: File) {
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `incidents` (
                    `id` TEXT NOT NULL, `type` TEXT NOT NULL, `trigger_type` TEXT NOT NULL,
                    `status` TEXT NOT NULL, `device_event_key` TEXT, `detected_at` INTEGER NOT NULL,
                    `resolved_at` INTEGER, `peak_accel_mg` INTEGER, `peak_gyro_dps` INTEGER,
                    `pitch_cdeg` INTEGER, `roll_cdeg` INTEGER, `lat` REAL, `lng` REAL,
                    `location_accuracy_m` REAL, `location_source` TEXT NOT NULL,
                    `location_fix_at` INTEGER, `sms_primary` TEXT NOT NULL,
                    `sms_secondary` TEXT NOT NULL, `call_status` TEXT NOT NULL,
                    `firmware_version` TEXT, `sync_state` TEXT NOT NULL,
                    `sync_attempts` INTEGER NOT NULL, PRIMARY KEY(`id`)
                )
                """.trimIndent()
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_incidents_device_event_key` ON `incidents` (`device_event_key`)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_incidents_detected_at` ON `incidents` (`detected_at`)"
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `incident_traces` (
                    `incident_id` TEXT NOT NULL, `sample_rate_hz` INTEGER NOT NULL,
                    `pre_trigger_samples` INTEGER NOT NULL, `total_samples` INTEGER NOT NULL,
                    `accel_lsb_per_g` INTEGER NOT NULL, `gyro_lsb_per_dps_x10` INTEGER NOT NULL,
                    `samples` BLOB NOT NULL, `sync_state` TEXT NOT NULL, PRIMARY KEY(`incident_id`),
                    FOREIGN KEY(`incident_id`) REFERENCES `incidents`(`id`)
                    ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `room_master_table` (`id` INTEGER PRIMARY KEY, `identity_hash` TEXT)"
            )
            db.execSQL(
                "INSERT OR REPLACE INTO `room_master_table` (`id`, `identity_hash`) VALUES (42, '81fa63854a53c50aef926b06cf687dd3')"
            )
            db.execSQL(
                """
                INSERT INTO incidents (
                    id, type, trigger_type, status, detected_at, location_source,
                    sms_primary, sms_secondary, call_status, sync_state, sync_attempts
                ) VALUES (
                    'd23f7244-06a9-4f46-a615-4e6b1c650001', 'REAL', 'IMPACT',
                    'NOT_CONFIRMED', 1791260000000, 'NONE', 'NOT_ATTEMPTED',
                    'NOT_ATTEMPTED', 'NOT_ATTEMPTED', 'PENDING', 0
                )
                """.trimIndent()
            )
            db.version = 1
        }
    }
}
