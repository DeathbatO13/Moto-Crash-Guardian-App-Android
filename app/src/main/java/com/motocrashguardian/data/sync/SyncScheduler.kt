package com.motocrashguardian.data.sync

import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

fun interface SyncTrigger {
    fun requestSync()
}

/** Programa la sincronizacion con WorkManager: solo con red y sin competir entre si. */
class SyncScheduler(private val workManagerProvider: () -> WorkManager) : SyncTrigger {

    private val constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    /** Pide una sincronizacion; si ya hay una en curso, otra la sigue para no perder cambios. */
    override fun requestSync() {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BackoffSeconds, TimeUnit.SECONDS)
            .build()
        withWorkManager { enqueueUniqueWork(OneTimeName, ExistingWorkPolicy.APPEND_OR_REPLACE, request) }
    }

    /** Red de seguridad: reintenta periodicamente lo que haya quedado pendiente. */
    fun ensurePeriodic() {
        val request = PeriodicWorkRequestBuilder<SyncWorker>(PeriodHours, TimeUnit.HOURS)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BackoffSeconds, TimeUnit.SECONDS)
            .build()
        withWorkManager {
            enqueueUniquePeriodicWork(PeriodicName, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }

    /**
     * Programar la sincronizacion nunca debe tumbar la app: si WorkManager no esta disponible
     * (p. ej. en pruebas sin inicializar) se registra y se sigue.
     */
    private fun withWorkManager(block: WorkManager.() -> Unit) {
        try {
            workManagerProvider().block()
        } catch (error: IllegalStateException) {
            Log.w(Tag, "WorkManager no disponible; sincronizacion no programada.")
        }
    }

    private companion object {
        const val Tag = "SyncScheduler"
        const val OneTimeName = "mcg-sync"
        const val PeriodicName = "mcg-sync-periodic"
        const val BackoffSeconds = 30L
        const val PeriodHours = 6L
    }
}
