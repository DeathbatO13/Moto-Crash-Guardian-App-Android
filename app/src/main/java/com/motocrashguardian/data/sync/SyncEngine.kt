package com.motocrashguardian.data.sync

import com.motocrashguardian.core.model.AppSettings
import com.motocrashguardian.core.model.Incident
import com.motocrashguardian.core.model.SyncState
import com.motocrashguardian.core.model.Trace
import com.motocrashguardian.data.remote.AuthSession
import com.motocrashguardian.data.remote.McgApi
import com.motocrashguardian.data.remote.RegisterInstallationRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.ResponseBody
import retrofit2.Response
import java.io.IOException
import java.time.Instant
import java.util.UUID

/** Lo que el motor necesita del almacenamiento local; lo implementan Room y DataStore. */
interface SyncLocalStore {
    suspend fun settings(): AppSettings
    suspend fun updateSettings(transform: (AppSettings) -> AppSettings)

    /** Incidentes pendientes de subir y ya finalizados (nunca `ACTIVE`), del mas antiguo al mas nuevo. */
    suspend fun pendingIncidents(limit: Int): List<Incident>
    suspend fun trace(incidentId: UUID): Trace?
    suspend fun markIncident(id: UUID, state: SyncState, countAttempt: Boolean)
    suspend fun markTrace(incidentId: UUID, state: SyncState)

    /** Tras perder la sesion el servidor no tiene nada: lo ya sincronizado vuelve a pendiente. */
    suspend fun resetSyncedToPending()
}

sealed interface SyncResult {
    data class Success(val incidentsSynced: Int, val permanentFailures: Int) : SyncResult
    data class Retry(val reason: String) : SyncResult
    data class Failure(val reason: String) : SyncResult
}

/**
 * Sube al backend la configuracion, los contactos, los incidentes y sus trazas, en ese orden.
 *
 * Nunca participa en la ruta de emergencia (countdown/SMS/llamada): corre en segundo plano y la
 * app funciona igual sin red ni backend. Politica de errores (specs/features/data-synchronization.md):
 * - Sin consentimiento no se sube nada.
 * - Red, 408, 429 y 5xx: se detiene y se reintenta mas tarde, sin perder el orden.
 * - 401: la sesion ya no existe en el servidor; se descarta y se vuelve a registrar.
 * - Otro 4xx: error permanente de ese elemento (se marca y se sigue con los demas).
 */
class SyncEngine(
    private val api: McgApi,
    private val auth: AuthSession,
    private val store: SyncLocalStore,
    private val appVersion: String
) {

    /**
     * Impide que una sincronizacion y un borrado de datos corran a la vez: sin esto, una subida
     * en curso podria volver a crear en el servidor datos que el usuario acaba de borrar.
     */
    val lock = Mutex()

    suspend fun run(): SyncResult = lock.withLock { runExclusive() }

    private suspend fun runExclusive(): SyncResult {
        val settings = store.settings()
        if (settings.consentAcceptedAt == null) return SyncResult.Success(0, 0)
        if (!settings.configDirty && !settings.contactsDirty &&
            store.pendingIncidents(1).isEmpty()
        ) {
            return SyncResult.Success(0, 0)
        }

        var registeredNow = false
        if (auth.token() == null) {
            when (val registration = register(settings.consentAcceptedAt)) {
                is Registration.Done -> registeredNow = true
                is Registration.Stop -> return registration.result
            }
        }

        var permanentFailures = 0

        // 1. Configuracion
        val configRequest = store.settings().takeIf { it.configDirty }?.toConfigRequest()
        if (configRequest != null) {
            when (val call = execute { api.putConfig(configRequest) }) {
                is Call.Ok -> store.updateSettings {
                    // Si el usuario cambio algo mientras subiamos, sigue pendiente.
                    if (it.toConfigRequest() == configRequest) it.copy(configDirty = false) else it
                }
                is Call.Permanent -> permanentFailures++
                else -> abortFor(call, registeredNow)?.let { return it }
            }
        }

        // 2. Contactos
        val contactsSettings = store.settings()
        if (contactsSettings.contactsDirty) {
            val contactsRequest = contactsSettings.toContactsRequest()
            if (contactsRequest == null) {
                permanentFailures++ // datos locales que el backend rechazaria
            } else {
                when (val call = execute { api.putContacts(contactsRequest) }) {
                    is Call.Ok -> store.updateSettings {
                        if (it.toContactsRequest() == contactsRequest) {
                            it.copy(contactsDirty = false)
                        } else {
                            it
                        }
                    }
                    is Call.Permanent -> permanentFailures++
                    else -> abortFor(call, registeredNow)?.let { return it }
                }
            }
        }

        // 3. Incidentes y trazas
        var synced = 0
        while (true) {
            val batch = store.pendingIncidents(BatchSize)
            if (batch.isEmpty()) break
            for (incident in batch) {
                when (val outcome = syncIncident(incident, registeredNow)) {
                    IncidentOutcome.Synced -> synced++
                    IncidentOutcome.PermanentFailure -> permanentFailures++
                    is IncidentOutcome.Abort -> return outcome.result
                }
            }
        }
        return SyncResult.Success(synced, permanentFailures)
    }

    private suspend fun syncIncident(incident: Incident, registeredNow: Boolean): IncidentOutcome {
        val upsert = incident.toUpsertRequest(appVersion)
        when (val call = execute { api.putIncident(incident.id.toString(), upsert) }) {
            is Call.Ok -> Unit
            is Call.Permanent -> {
                store.markIncident(incident.id, SyncState.FAILED_PERMANENT, countAttempt = true)
                return IncidentOutcome.PermanentFailure
            }
            else -> return IncidentOutcome.Abort(abortFor(call, registeredNow)!!)
        }

        val trace = store.trace(incident.id)
        if (trace != null && trace.syncState == SyncState.PENDING) {
            val request = trace.toRequest()
            if (request == null) {
                store.markTrace(incident.id, SyncState.FAILED_PERMANENT) // no cabe en 64 KB
            } else {
                when (val call = execute { api.putTrace(incident.id.toString(), request) }) {
                    is Call.Ok -> store.markTrace(incident.id, SyncState.SYNCED)
                    is Call.Permanent ->
                        store.markTrace(incident.id, SyncState.FAILED_PERMANENT)
                    else -> return IncidentOutcome.Abort(abortFor(call, registeredNow)!!)
                }
            }
        }
        store.markIncident(incident.id, SyncState.SYNCED, countAttempt = false)
        return IncidentOutcome.Synced
    }

    private suspend fun register(consentAcceptedAt: Instant): Registration {
        val request = RegisterInstallationRequest(appVersion, consentAcceptedAt.toString())
        val call = execute { api.registerInstallation(request) }
        return when (call) {
            is Call.Ok -> {
                val body = call.body
                if (body == null) {
                    Registration.Stop(SyncResult.Failure("El registro no devolvio credenciales"))
                } else {
                    auth.save(body.installationId, body.token)
                    Registration.Done
                }
            }
            is Call.Retry -> Registration.Stop(SyncResult.Retry(call.reason))
            is Call.Permanent ->
                Registration.Stop(SyncResult.Failure("Registro rechazado (HTTP ${call.code})"))
            Call.Unauthorized ->
                Registration.Stop(SyncResult.Failure("Registro rechazado (HTTP 401)"))
        }
    }

    /**
     * Traduce un resultado que corta la corrida. Un 401 con la sesion recien creada es un fallo
     * real (evita un bucle de registros); con una sesion vieja, se descarta y se rehace todo.
     */
    private suspend fun abortFor(call: Call<*>, registeredNow: Boolean): SyncResult? = when (call) {
        is Call.Retry -> SyncResult.Retry(call.reason)
        Call.Unauthorized -> if (registeredNow) {
            SyncResult.Failure("El servidor rechazo la sesion recien creada")
        } else {
            auth.clear()
            store.resetSyncedToPending()
            store.updateSettings { it.copy(configDirty = true, contactsDirty = true) }
            SyncResult.Retry("Sesion invalida; se registrara de nuevo")
        }
        else -> null
    }

    private suspend fun <T> execute(block: suspend () -> Response<T>): Call<T> =
        try {
            val response = block()
            val body = response.body()
            (body as? ResponseBody)?.close()
            response.errorBody()?.close()
            when {
                response.isSuccessful -> Call.Ok(body)
                response.code() == 401 -> Call.Unauthorized
                response.code() == 408 || response.code() == 429 || response.code() >= 500 ->
                    Call.Retry("HTTP ${response.code()}")
                else -> Call.Permanent(response.code())
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: IOException) {
            Call.Retry("Sin conexion con el servidor")
        } catch (error: Exception) {
            // Cuerpo ilegible u otro fallo que no mejora reintentando.
            Call.Permanent(-1)
        }

    private sealed interface Call<out T> {
        data class Ok<T>(val body: T?) : Call<T>
        data class Retry(val reason: String) : Call<Nothing>
        data object Unauthorized : Call<Nothing>
        data class Permanent(val code: Int) : Call<Nothing>
    }

    private sealed interface Registration {
        data object Done : Registration
        data class Stop(val result: SyncResult) : Registration
    }

    private sealed interface IncidentOutcome {
        data object Synced : IncidentOutcome
        data object PermanentFailure : IncidentOutcome
        data class Abort(val result: SyncResult) : IncidentOutcome
    }

    private companion object {
        const val BatchSize = 50
    }
}
