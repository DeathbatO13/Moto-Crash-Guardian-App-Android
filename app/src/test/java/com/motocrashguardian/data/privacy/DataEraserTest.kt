package com.motocrashguardian.data.privacy

import com.motocrashguardian.data.remote.AuthSession
import com.motocrashguardian.data.remote.IncidentTraceRequest
import com.motocrashguardian.data.remote.IncidentUpsertRequest
import com.motocrashguardian.data.remote.McgApi
import com.motocrashguardian.data.remote.RegisterInstallationRequest
import com.motocrashguardian.data.remote.RegisterInstallationResponse
import com.motocrashguardian.data.remote.UpdateContactsRequest
import com.motocrashguardian.data.remote.UserConfigRequest
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import retrofit2.Response
import java.io.IOException

class DataEraserTest {

    private val events = mutableListOf<String>()
    private val auth = FakeAuth().apply { save("inst", "tok") }
    private val lock = Mutex()
    private var deleteResponse: () -> Response<ResponseBody> = { Response.success("".toResponseBody()) }
    private var wipeFailure: Exception? = null
    private var stopFailure: Exception? = null

    private val api = object : McgApi {
        override suspend fun deleteInstallation(): Response<ResponseBody> {
            events += "remote-delete"
            return deleteResponse()
        }

        override suspend fun registerInstallation(request: RegisterInstallationRequest):
            Response<RegisterInstallationResponse> = error("no se usa")
        override suspend fun putConfig(request: UserConfigRequest): Response<ResponseBody> = error("no se usa")
        override suspend fun putContacts(request: UpdateContactsRequest): Response<ResponseBody> = error("no se usa")
        override suspend fun putIncident(id: String, request: IncidentUpsertRequest):
            Response<ResponseBody> = error("no se usa")
        override suspend fun putTrace(id: String, request: IncidentTraceRequest):
            Response<ResponseBody> = error("no se usa")
    }

    private val eraser = DataEraser(
        api = api,
        auth = auth,
        local = EraseLocalStore {
            events += "wipe"
            wipeFailure?.let { throw it }
        },
        lock = lock,
        stopProtection = {
            events += "stop"
            stopFailure?.let { throw it }
        },
        cancelPendingSync = { events += "cancel-sync" }
    )

    private fun httpError(code: Int): Response<ResponseBody> =
        Response.error(code, "{}".toResponseBody())

    // --- Camino feliz ---

    @Test
    fun `borra primero el servidor y despues el telefono`() = runTest {
        val result = eraser.erase()

        assertEquals(EraseResult.Success(remoteDeleted = true), result)
        assertEquals(listOf("remote-delete", "cancel-sync", "stop", "wipe"), events)
        assertNull(auth.token())
        assertNull(auth.installationId())
    }

    @Test
    fun `sin copia en el servidor solo borra lo local`() = runTest {
        auth.clear()

        val result = eraser.erase()

        assertEquals(EraseResult.Success(remoteDeleted = false), result)
        assertFalse("remote-delete" in events)
        assertTrue("wipe" in events)
    }

    @Test
    fun `un 401 cuenta como borrado porque la instalacion ya no existe`() = runTest {
        deleteResponse = { httpError(401) }

        assertEquals(EraseResult.Success(remoteDeleted = true), eraser.erase())
        assertTrue("wipe" in events)
    }

    // --- El servidor no responde: no se borra nada ---

    @Test
    fun `sin conexion no borra nada y conserva el token para reintentar`() = runTest {
        deleteResponse = { throw IOException("sin red") }

        assertEquals(EraseResult.NoConnection, eraser.erase())

        assertEquals(listOf("remote-delete"), events)
        assertEquals("tok", auth.token())
    }

    @Test
    fun `errores transitorios del servidor se tratan como falta de conexion`() = runTest {
        for (code in listOf(408, 429, 500, 502, 503)) {
            events.clear()
            deleteResponse = { httpError(code) }

            assertEquals(EraseResult.NoConnection, eraser.erase(), "HTTP $code")
            assertFalse("wipe" in events)
            assertNotNull(auth.token())
        }
    }

    @Test
    fun `un rechazo permanente no borra nada y informa el codigo`() = runTest {
        deleteResponse = { httpError(403) }

        assertEquals(EraseResult.ServerRejected(403), eraser.erase())

        assertFalse("wipe" in events)
        assertFalse("stop" in events)
        assertEquals("tok", auth.token())
    }

    @Test
    fun `la proteccion no se detiene si el servidor no pudo borrarse`() = runTest {
        deleteResponse = { throw IOException() }

        eraser.erase()

        assertFalse("stop" in events)
        assertFalse("cancel-sync" in events)
    }

    // --- Fallo local tras borrar el servidor ---

    @Test
    fun `si falla la limpieza local informa el fallo y el reintento ya no llama al servidor`() = runTest {
        wipeFailure = IllegalStateException("disco")

        assertEquals(EraseResult.LocalFailure, eraser.erase())
        assertNull(auth.token())

        wipeFailure = null
        events.clear()

        assertEquals(EraseResult.Success(remoteDeleted = false), eraser.erase())
        assertFalse("remote-delete" in events)
        assertTrue("wipe" in events)
    }

    @Test
    fun `un fallo al detener la proteccion tambien se informa`() = runTest {
        stopFailure = IllegalStateException("servicio")

        assertEquals(EraseResult.LocalFailure, eraser.erase())
        assertFalse("wipe" in events)
    }

    // --- Exclusion con la sincronizacion ---

    @Test
    fun `espera a que termine una sincronizacion en curso`() = runTest {
        lock.lock() // la sincronizacion tiene el cerrojo
        val pending = async(start = CoroutineStart.UNDISPATCHED) { eraser.erase() }
        runCurrent()

        assertTrue(events.isEmpty(), "no debe borrar mientras sincroniza")
        assertFalse(pending.isCompleted)

        lock.unlock()
        advanceUntilIdle()

        assertEquals(EraseResult.Success(remoteDeleted = true), pending.await())
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
}
