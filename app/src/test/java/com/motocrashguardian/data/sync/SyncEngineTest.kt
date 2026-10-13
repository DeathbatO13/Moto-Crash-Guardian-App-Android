package com.motocrashguardian.data.sync

import com.motocrashguardian.core.model.AppSettings
import com.motocrashguardian.core.model.ContactRole
import com.motocrashguardian.core.model.EmergencyContact
import com.motocrashguardian.core.model.Incident
import com.motocrashguardian.core.model.IncidentStatus
import com.motocrashguardian.core.model.IncidentType
import com.motocrashguardian.core.model.SyncState
import com.motocrashguardian.core.model.Trace
import com.motocrashguardian.core.model.TriggerType
import com.motocrashguardian.data.remote.AuthSession
import com.motocrashguardian.data.remote.IncidentTraceRequest
import com.motocrashguardian.data.remote.IncidentUpsertRequest
import com.motocrashguardian.data.remote.McgApi
import com.motocrashguardian.data.remote.RegisterInstallationRequest
import com.motocrashguardian.data.remote.RegisterInstallationResponse
import com.motocrashguardian.data.remote.UpdateContactsRequest
import com.motocrashguardian.data.remote.UserConfigRequest
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import retrofit2.Response
import java.io.IOException
import java.time.Instant
import java.util.UUID

class SyncEngineTest {

    private val now = Instant.parse("2026-10-19T15:30:00Z")
    private val api = FakeApi()
    private val auth = FakeAuth()
    private val store = FakeStore()
    private val engine = SyncEngine(api, auth, store, appVersion = "1.0.0")

    private fun consenting(configDirty: Boolean = false, contactsDirty: Boolean = false) =
        AppSettings(
            riderName = "Carlos",
            primaryContact = EmergencyContact(ContactRole.PRIMARY, "Ana", "+573001112233"),
            secondaryContact = EmergencyContact(ContactRole.SECONDARY, "Luis", "+573004445566"),
            consentAcceptedAt = now,
            configDirty = configDirty,
            contactsDirty = contactsDirty
        )

    private fun incident(offsetSeconds: Long = 0, status: IncidentStatus = IncidentStatus.DISPATCHED) =
        Incident(
            type = IncidentType.REAL,
            triggerType = TriggerType.IMPACT,
            status = status,
            detectedAt = now.plusSeconds(offsetSeconds)
        )

    private fun trace(incidentId: UUID, bytes: Int = 24) = Trace(
        incidentId = incidentId,
        sampleRateHz = 100,
        preTriggerSamples = 1,
        totalSamples = bytes / 12,
        accelLsbPerG = 2048,
        gyroLsbPerDpsX10 = 164,
        samples = ByteArray(bytes)
    )

    // --- Condiciones previas ---

    @Test
    fun `sin consentimiento no se registra ni se sube nada`() = runTest {
        store.settings = consenting(configDirty = true).copy(consentAcceptedAt = null)
        store.incidents += incident()

        val result = engine.run()

        assertEquals(SyncResult.Success(0, 0), result)
        assertTrue(api.calls.isEmpty())
        assertNull(auth.token())
    }

    @Test
    fun `sin nada pendiente no hace ninguna llamada`() = runTest {
        store.settings = consenting()

        assertEquals(SyncResult.Success(0, 0), engine.run())

        assertTrue(api.calls.isEmpty())
    }

    // --- Camino feliz ---

    @Test
    fun `registra y sube config, contactos, incidente y traza en ese orden`() = runTest {
        store.settings = consenting(configDirty = true, contactsDirty = true)
        val pending = incident()
        store.incidents += pending
        store.traces[pending.id] = trace(pending.id)

        val result = engine.run()

        assertEquals(SyncResult.Success(1, 0), result)
        assertEquals(
            listOf("register", "config", "contacts", "incident", "trace"),
            api.calls
        )
        assertEquals("tok-1", auth.token())
        assertFalse(store.settings.configDirty)
        assertFalse(store.settings.contactsDirty)
        assertEquals(SyncState.SYNCED, store.state(pending.id))
        assertEquals(SyncState.SYNCED, store.traceStates[pending.id])
    }

    @Test
    fun `el registro envia la version y la fecha de consentimiento que exige el backend`() = runTest {
        store.settings = consenting(configDirty = true)

        engine.run()

        assertEquals(RegisterInstallationRequest("1.0.0", now.toString()), api.lastRegister)
    }

    @Test
    fun `con sesion existente no vuelve a registrarse`() = runTest {
        auth.save("inst", "tok-existing")
        store.settings = consenting(configDirty = true)

        engine.run()

        assertEquals(listOf("config"), api.calls)
    }

    @Test
    fun `los incidentes se suben del mas antiguo al mas nuevo`() = runTest {
        auth.save("inst", "tok")
        store.settings = consenting()
        val newer = incident(offsetSeconds = 60)
        val older = incident(offsetSeconds = 0)
        store.incidents += listOf(newer, older)

        engine.run()

        assertEquals(listOf(older.id.toString(), newer.id.toString()), api.incidentIds)
    }

    @Test
    fun `un incidente ACTIVE no se sube`() = runTest {
        auth.save("inst", "tok")
        store.settings = consenting()
        store.incidents += incident(status = IncidentStatus.ACTIVE)

        assertEquals(SyncResult.Success(0, 0), engine.run())

        assertTrue(api.calls.isEmpty())
    }

    // --- Configuracion y contactos ---

    @Test
    fun `no sube config mientras el perfil no tenga nombre`() = runTest {
        auth.save("inst", "tok")
        store.settings = consenting(configDirty = true).copy(riderName = "  ")

        engine.run()

        assertTrue("config" !in api.calls)
        assertTrue(store.settings.configDirty)
    }

    @Test
    fun `un cambio de config durante la subida la deja pendiente`() = runTest {
        auth.save("inst", "tok")
        store.settings = consenting(configDirty = true)
        api.onConfig = {
            // El usuario edita el nombre mientras la peticion esta en vuelo.
            store.settings = store.settings.copy(riderName = "Carlos Perez")
            Response.success<ResponseBody>("".toResponseBody())
        }

        engine.run()

        assertTrue(store.settings.configDirty)
    }

    @Test
    fun `contactos invalidos no se envian y no frenan los incidentes`() = runTest {
        auth.save("inst", "tok")
        store.settings = consenting(contactsDirty = true).copy(
            primaryContact = EmergencyContact(ContactRole.PRIMARY, "Ana", "3001112233")
        )
        val pending = incident()
        store.incidents += pending

        val result = engine.run()

        assertEquals(SyncResult.Success(1, 1), result)
        assertTrue("contacts" !in api.calls)
        assertTrue(store.settings.contactsDirty)
        assertEquals(SyncState.SYNCED, store.state(pending.id))
    }

    @Test
    fun `sin contactos se envia la lista vacia para borrar los remotos`() = runTest {
        auth.save("inst", "tok")
        store.settings = consenting(contactsDirty = true).copy(
            primaryContact = null,
            secondaryContact = null
        )

        engine.run()

        assertEquals(listOf("contacts"), api.calls)
        assertEquals(emptyList<Any>(), api.lastContacts?.contacts)
    }

    @Test
    fun `una config rechazada por el servidor se cuenta como fallo permanente y sigue`() = runTest {
        auth.save("inst", "tok")
        store.settings = consenting(configDirty = true)
        store.incidents += incident()
        api.onConfig = { error(400) }

        val result = engine.run()

        assertEquals(SyncResult.Success(1, 1), result)
        assertTrue(store.settings.configDirty)
    }

    // --- Errores transitorios ---

    @Test
    fun `sin red el registro pide reintento`() = runTest {
        store.settings = consenting(configDirty = true)
        api.onRegister = { throw IOException("sin red") }

        assertTrue(engine.run() is SyncResult.Retry)
        assertNull(auth.token())
    }

    @Test
    fun `429 y 5xx piden reintento`() = runTest {
        store.settings = consenting(configDirty = true)
        for (code in listOf(429, 500, 503, 408)) {
            api.onRegister = { error(code) }
            assertTrue(engine.run() is SyncResult.Retry, "HTTP $code deberia reintentarse")
        }
    }

    @Test
    fun `un fallo transitorio detiene la corrida sin saltarse el orden`() = runTest {
        auth.save("inst", "tok")
        store.settings = consenting()
        val first = incident(offsetSeconds = 0)
        val second = incident(offsetSeconds = 10)
        store.incidents += listOf(first, second)
        api.onIncident = { id -> if (id == first.id.toString()) error(503) else ok() }

        val result = engine.run()

        assertTrue(result is SyncResult.Retry)
        assertEquals(listOf(first.id.toString()), api.incidentIds)
        assertEquals(SyncState.PENDING, store.state(first.id))
        assertEquals(SyncState.PENDING, store.state(second.id))
    }

    @Test
    fun `un reintento posterior retoma los pendientes`() = runTest {
        auth.save("inst", "tok")
        store.settings = consenting()
        val pending = incident()
        store.incidents += pending
        api.onIncident = { error(503) }
        assertTrue(engine.run() is SyncResult.Retry)

        api.onIncident = { ok() }

        assertEquals(SyncResult.Success(1, 0), engine.run())
        assertEquals(SyncState.SYNCED, store.state(pending.id))
    }

    // --- Errores permanentes ---

    @Test
    fun `un incidente rechazado se marca permanente y continua con el siguiente`() = runTest {
        auth.save("inst", "tok")
        store.settings = consenting()
        val bad = incident(offsetSeconds = 0)
        val good = incident(offsetSeconds = 10)
        store.incidents += listOf(bad, good)
        api.onIncident = { id -> if (id == bad.id.toString()) error(409) else ok() }

        val result = engine.run()

        assertEquals(SyncResult.Success(1, 1), result)
        assertEquals(SyncState.FAILED_PERMANENT, store.state(bad.id))
        assertEquals(1, store.attempts[bad.id])
        assertEquals(SyncState.SYNCED, store.state(good.id))
    }

    @Test
    fun `no se reintenta para siempre un incidente permanente`() = runTest {
        auth.save("inst", "tok")
        store.settings = consenting()
        store.incidents += incident()
        api.onIncident = { error(400) }

        engine.run()
        api.calls.clear()
        engine.run()

        assertTrue(api.calls.isEmpty())
    }

    @Test
    fun `una traza rechazada no impide dar el incidente por sincronizado`() = runTest {
        auth.save("inst", "tok")
        store.settings = consenting()
        val pending = incident()
        store.incidents += pending
        store.traces[pending.id] = trace(pending.id)
        api.onTrace = { error(413) }

        val result = engine.run()

        assertEquals(SyncResult.Success(1, 0), result)
        assertEquals(SyncState.SYNCED, store.state(pending.id))
        assertEquals(SyncState.FAILED_PERMANENT, store.traceStates[pending.id])
    }

    @Test
    fun `una traza que no cabe en 64 KB ni se intenta`() = runTest {
        auth.save("inst", "tok")
        store.settings = consenting()
        val pending = incident()
        store.incidents += pending
        store.traces[pending.id] = trace(pending.id, bytes = 12 * 5_000)

        engine.run()

        assertTrue("trace" !in api.calls)
        assertEquals(SyncState.FAILED_PERMANENT, store.traceStates[pending.id])
        assertEquals(SyncState.SYNCED, store.state(pending.id))
    }

    @Test
    fun `una traza transitoria deja el incidente pendiente para reintentar`() = runTest {
        auth.save("inst", "tok")
        store.settings = consenting()
        val pending = incident()
        store.incidents += pending
        store.traces[pending.id] = trace(pending.id)
        api.onTrace = { error(502) }

        assertTrue(engine.run() is SyncResult.Retry)

        assertEquals(SyncState.PENDING, store.state(pending.id))
        assertNull(store.traceStates[pending.id]) // sin marcar: sigue pendiente
    }

    // --- Autenticacion ---

    @Test
    fun `401 con sesion vieja descarta el token y reprograma todo`() = runTest {
        auth.save("inst", "tok-old")
        store.settings = consenting()
        val synced = incident()
        store.incidents += synced
        store.states[synced.id] = SyncState.SYNCED
        val pending = incident(offsetSeconds = 30)
        store.incidents += pending
        api.onIncident = { error(401) }

        val result = engine.run()

        assertTrue(result is SyncResult.Retry)
        assertNull(auth.token())
        assertTrue(store.settings.configDirty)
        assertTrue(store.settings.contactsDirty)
        assertEquals(SyncState.PENDING, store.state(synced.id))
    }

    @Test
    fun `tras un 401 la siguiente corrida se registra y vuelve a subir todo`() = runTest {
        auth.save("inst", "tok-old")
        store.settings = consenting()
        val pending = incident()
        store.incidents += pending
        api.onIncident = { error(401) }
        engine.run()
        api.onIncident = { ok() }
        api.calls.clear()

        val result = engine.run()

        assertEquals(SyncResult.Success(1, 0), result)
        assertEquals("register", api.calls.first())
        assertEquals("tok-1", auth.token())
        assertTrue("config" in api.calls && "contacts" in api.calls)
    }

    @Test
    fun `401 justo despues de registrarse es un fallo y no un bucle`() = runTest {
        store.settings = consenting()
        store.incidents += incident()
        api.onIncident = { error(401) }

        val result = engine.run()

        assertTrue(result is SyncResult.Failure)
    }

    @Test
    fun `un registro rechazado es un fallo`() = runTest {
        store.settings = consenting(configDirty = true)
        api.onRegister = { error(400) }

        assertTrue(engine.run() is SyncResult.Failure)
    }

    // --- Fakes ---

    private fun <T> error(code: Int): Response<T> =
        Response.error(code, "{}".toResponseBody())

    private fun ok(): Response<ResponseBody> = Response.success("".toResponseBody())

    private inner class FakeApi : McgApi {
        val calls = mutableListOf<String>()
        val incidentIds = mutableListOf<String>()
        var lastContacts: UpdateContactsRequest? = null
        var lastRegister: RegisterInstallationRequest? = null
        private var registrations = 0

        var onRegister: () -> Response<RegisterInstallationResponse> = {
            registrations++
            Response.success(RegisterInstallationResponse("inst-$registrations", "tok-$registrations"))
        }
        var onConfig: () -> Response<ResponseBody> = { ok() }
        var onContacts: () -> Response<ResponseBody> = { ok() }
        var onIncident: (String) -> Response<ResponseBody> = { ok() }
        var onTrace: () -> Response<ResponseBody> = { ok() }

        override suspend fun registerInstallation(
            request: RegisterInstallationRequest
        ): Response<RegisterInstallationResponse> {
            calls += "register"
            lastRegister = request
            return onRegister()
        }

        override suspend fun putConfig(request: UserConfigRequest): Response<ResponseBody> {
            calls += "config"
            return onConfig()
        }

        override suspend fun putContacts(request: UpdateContactsRequest): Response<ResponseBody> {
            calls += "contacts"
            lastContacts = request
            return onContacts()
        }

        override suspend fun putIncident(
            id: String,
            request: IncidentUpsertRequest
        ): Response<ResponseBody> {
            calls += "incident"
            incidentIds += id
            return onIncident(id)
        }

        override suspend fun putTrace(
            id: String,
            request: IncidentTraceRequest
        ): Response<ResponseBody> {
            calls += "trace"
            return onTrace()
        }

        override suspend fun deleteInstallation(): Response<ResponseBody> = ok()
    }

    private class FakeAuth : AuthSession {
        private var id: String? = null
        private var token: String? = null
        override fun installationId() = id
        override fun token() = token
        override fun save(installationId: String, token: String) {
            id = installationId
            this.token = token
        }

        override fun clear() {
            id = null
            token = null
        }
    }

    private class FakeStore : SyncLocalStore {
        var settings = AppSettings()
        val incidents = mutableListOf<Incident>()
        val traces = mutableMapOf<UUID, Trace>()
        val states = mutableMapOf<UUID, SyncState>()
        val attempts = mutableMapOf<UUID, Int>()
        val traceStates = mutableMapOf<UUID, SyncState>()

        fun state(id: UUID): SyncState = states[id] ?: SyncState.PENDING

        override suspend fun settings(): AppSettings = settings

        override suspend fun updateSettings(transform: (AppSettings) -> AppSettings) {
            settings = transform(settings)
        }

        override suspend fun pendingIncidents(limit: Int): List<Incident> = incidents
            .filter { state(it.id) == SyncState.PENDING && it.status != IncidentStatus.ACTIVE }
            .sortedBy { it.detectedAt }
            .take(limit)

        override suspend fun trace(incidentId: UUID): Trace? = traces[incidentId]?.let {
            it.copy(syncState = traceStates[incidentId] ?: SyncState.PENDING)
        }

        override suspend fun markIncident(id: UUID, state: SyncState, countAttempt: Boolean) {
            states[id] = state
            if (countAttempt) attempts[id] = (attempts[id] ?: 0) + 1
        }

        override suspend fun markTrace(incidentId: UUID, state: SyncState) {
            traceStates[incidentId] = state
        }

        override suspend fun resetSyncedToPending() {
            states.replaceAll { _, value -> if (value == SyncState.SYNCED) SyncState.PENDING else value }
            traceStates.replaceAll { _, value ->
                if (value == SyncState.SYNCED) SyncState.PENDING else value
            }
        }
    }
}
