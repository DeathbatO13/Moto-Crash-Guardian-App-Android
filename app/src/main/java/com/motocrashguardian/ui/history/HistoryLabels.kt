package com.motocrashguardian.ui.history

import com.motocrashguardian.core.model.CallStatus
import com.motocrashguardian.core.model.IncidentStatus
import com.motocrashguardian.core.model.IncidentType
import com.motocrashguardian.core.model.LocationSource
import com.motocrashguardian.core.model.SmsStatus
import com.motocrashguardian.core.model.SyncState
import com.motocrashguardian.core.model.TriggerType
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** Textos para mostrar al usuario; separados de la UI para poder probarlos. */

fun IncidentStatus.label(): String = when (this) {
    IncidentStatus.ACTIVE -> "En curso"
    IncidentStatus.NOT_CONFIRMED -> "No confirmado"
    IncidentStatus.CANCELLED_BY_USER -> "Cancelado por ti"
    IncidentStatus.DISPATCHED -> "Alerta enviada"
    IncidentStatus.DISPATCH_PARTIAL -> "Enviada parcialmente"
    IncidentStatus.DISPATCH_FAILED -> "No se pudo enviar"
    IncidentStatus.STALE_EVENT -> "Evento antiguo"
}

fun TriggerType.label(): String = when (this) {
    TriggerType.IMPACT -> "Impacto"
    TriggerType.SEVERE_IMPACT -> "Impacto severo"
    TriggerType.TILT -> "Caída detectada por inclinación"
    TriggerType.TEST -> "Prueba"
    TriggerType.CONNECTION_LOST -> "Ayuda manual (sin módulo)"
}

fun IncidentType.label(): String = when (this) {
    IncidentType.REAL -> "Real"
    IncidentType.DRILL -> "Simulacro"
}

fun SmsStatus.label(): String = when (this) {
    SmsStatus.SENT -> "Enviado"
    SmsStatus.DELIVERED -> "Entregado"
    SmsStatus.FAILED -> "Falló"
    SmsStatus.NOT_ATTEMPTED -> "No se intentó"
}

fun CallStatus.label(): String = when (this) {
    CallStatus.PLACED -> "Llamada realizada"
    CallStatus.FAILED -> "Falló"
    CallStatus.NOT_ATTEMPTED -> "No se intentó"
    CallStatus.SKIPPED_DRILL -> "Omitida (simulacro)"
}

fun LocationSource.label(): String = when (this) {
    LocationSource.PHONE_GPS -> "GPS del teléfono"
    LocationSource.DEVICE_GPS -> "GPS de la moto"
    LocationSource.LAST_KNOWN -> "Última ubicación conocida"
    LocationSource.NONE -> "Sin ubicación"
}

fun SyncState.label(): String = when (this) {
    SyncState.PENDING -> "Pendiente de subir"
    SyncState.SYNCED -> "Respaldado en el servidor"
    SyncState.FAILED_PERMANENT -> "No se pudo respaldar"
}

/** `true` si el aviso de este estado llego a salir (al menos en parte). */
fun IncidentStatus.isDispatched(): Boolean =
    this == IncidentStatus.DISPATCHED || this == IncidentStatus.DISPATCH_PARTIAL

fun formatDateTime(instant: Instant, zone: ZoneId = ZoneId.systemDefault()): String =
    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
        .withLocale(Locale.forLanguageTag("es-CO"))
        .withZone(zone)
        .format(instant)

/** Milig a g con un decimal, p. ej. 9500 -> "9,5 g". */
fun formatG(milliG: Int): String = String.format(Locale.forLanguageTag("es-CO"), "%.1f g", milliG / 1000f)

/** Centigrados de grado a grados, p. ej. -4550 -> "-45,5°". */
fun formatDegrees(centiDegrees: Int): String =
    String.format(Locale.forLanguageTag("es-CO"), "%.1f°", centiDegrees / 100f)
