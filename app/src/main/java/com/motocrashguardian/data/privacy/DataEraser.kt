package com.motocrashguardian.data.privacy

import com.motocrashguardian.data.remote.AuthSession
import com.motocrashguardian.data.remote.McgApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.ResponseBody
import retrofit2.Response
import java.io.IOException

sealed interface EraseResult {
    /** Todo borrado. [remoteDeleted] es falso si nunca hubo copia en el servidor. */
    data class Success(val remoteDeleted: Boolean) : EraseResult

    /** No se pudo llegar al servidor; no se borro nada. */
    data object NoConnection : EraseResult

    /** El servidor respondio con un error que reintentar no arregla; no se borro nada. */
    data class ServerRejected(val httpCode: Int) : EraseResult

    /** El servidor se borro pero fallo la limpieza local; hay que reintentar. */
    data object LocalFailure : EraseResult
}

fun interface DataErasing {
    suspend fun erase(): EraseResult
}

/** Borra el estado local: incidentes, trazas y ajustes (incluido el consentimiento). */
fun interface EraseLocalStore {
    suspend fun wipe()
}

/**
 * Borrado de datos del usuario (Ley 1581 de 2012: supresion y revocatoria).
 *
 * El orden es deliberado: primero el servidor y solo si responde se borra el telefono. Si se
 * borrara primero lo local se perderia el token y los datos del servidor quedarian huerfanos
 * e imposibles de borrar. Un 401 cuenta como exito: la instalacion ya no existe en el servidor.
 */
class DataEraser(
    private val api: McgApi,
    private val auth: AuthSession,
    private val local: EraseLocalStore,
    /** Mismo cerrojo que usa la sincronizacion, para que no se crucen. */
    private val lock: Mutex,
    private val stopProtection: suspend () -> Unit,
    private val cancelPendingSync: () -> Unit
) : DataErasing {

    override suspend fun erase(): EraseResult = lock.withLock {
        val hadRemoteCopy = auth.token() != null
        if (hadRemoteCopy) {
            when (val outcome = deleteRemote()) {
                RemoteOutcome.Deleted -> Unit
                RemoteOutcome.NoConnection -> return@withLock EraseResult.NoConnection
                is RemoteOutcome.Rejected -> return@withLock EraseResult.ServerRejected(outcome.code)
            }
            auth.clear()
        }

        cancelPendingSync()
        try {
            stopProtection()
            local.wipe()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            return@withLock EraseResult.LocalFailure
        }
        EraseResult.Success(remoteDeleted = hadRemoteCopy)
    }

    private suspend fun deleteRemote(): RemoteOutcome {
        val response: Response<ResponseBody> = try {
            api.deleteInstallation()
        } catch (error: CancellationException) {
            throw error
        } catch (error: IOException) {
            return RemoteOutcome.NoConnection
        } catch (error: Exception) {
            return RemoteOutcome.Rejected(-1)
        }
        response.body()?.close()
        response.errorBody()?.close()
        return when {
            response.isSuccessful || response.code() == 401 -> RemoteOutcome.Deleted
            response.code() == 408 || response.code() == 429 || response.code() >= 500 ->
                RemoteOutcome.NoConnection
            else -> RemoteOutcome.Rejected(response.code())
        }
    }

    private sealed interface RemoteOutcome {
        data object Deleted : RemoteOutcome
        data object NoConnection : RemoteOutcome
        data class Rejected(val code: Int) : RemoteOutcome
    }
}
