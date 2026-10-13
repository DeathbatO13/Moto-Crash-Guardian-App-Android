package com.motocrashguardian.data.sync

import com.motocrashguardian.core.model.AppSettings
import com.motocrashguardian.core.model.Incident
import com.motocrashguardian.core.model.IncidentStatus
import com.motocrashguardian.core.model.SyncState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import java.util.UUID

/** Foto de lo que falta por subir; cambia solo cuando aparece trabajo nuevo. */
data class SyncSignature(
    val pendingIncidentIds: Set<UUID>,
    val configDirty: Boolean,
    val contactsDirty: Boolean
)

/** `null` si no hay nada que sincronizar o si el usuario aun no dio su consentimiento. */
fun syncSignature(incidents: List<Incident>, settings: AppSettings): SyncSignature? {
    if (settings.consentAcceptedAt == null) return null
    val pending = incidents
        .filter { it.syncState == SyncState.PENDING && it.status != IncidentStatus.ACTIVE }
        .map { it.id }
        .toSet()
    if (pending.isEmpty() && !settings.configDirty && !settings.contactsDirty) return null
    return SyncSignature(pending, settings.configDirty, settings.contactsDirty)
}

/**
 * Observa incidentes y ajustes y pide una sincronizacion cuando aparece algo pendiente.
 * El motor decide despues que subir; aqui solo se evita sincronizar en vano.
 */
@OptIn(FlowPreview::class)
class SyncCoordinator(
    private val incidents: Flow<List<Incident>>,
    private val settings: Flow<AppSettings>,
    private val trigger: SyncTrigger,
    private val debounceMillis: Long = 1_500L
) {
    fun start(scope: CoroutineScope): Job = scope.launch {
        combine(incidents, settings, ::syncSignature)
            .filterNotNull()
            .distinctUntilChanged()
            .debounce(debounceMillis)
            .collect { trigger.requestSync() }
    }
}
