package com.motocrashguardian.data.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException

@EntryPoint
@InstallIn(SingletonComponent::class)
interface SyncEntryPoint {
    fun syncEngine(): SyncEngine
}

/**
 * Ejecuta [SyncEngine] en segundo plano. WorkManager reintenta con espera exponencial; tras
 * [MaxAttempts] intentos se rinde hasta que un cambio nuevo o el trabajo periodico lo reactiven.
 */
class SyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val engine = EntryPointAccessors
            .fromApplication(applicationContext, SyncEntryPoint::class.java)
            .syncEngine()
        val result = try {
            engine.run()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            SyncResult.Retry("Error inesperado: ${error.javaClass.simpleName}")
        }
        return when (result) {
            is SyncResult.Success -> Result.success()
            is SyncResult.Failure -> Result.failure()
            is SyncResult.Retry ->
                if (runAttemptCount >= MaxAttempts) Result.failure() else Result.retry()
        }
    }

    companion object {
        const val MaxAttempts = 8
    }
}
