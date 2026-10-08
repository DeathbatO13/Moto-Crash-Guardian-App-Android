package com.motocrashguardian.emergency

import java.text.Normalizer
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

class GsmMessageBuilder {
    fun build(riderName: String, location: EmergencyMessageLocation, drill: Boolean = false): String {
        val name = normalizeName(riderName)
        require(name.isNotBlank() && name.length <= MaximumRiderNameLength) {
            "Rider name must contain 1 to $MaximumRiderNameLength characters."
        }
        require(name.none { it == '\n' || it == '\r' || it == '\u000C' }) {
            "Rider name cannot contain line breaks."
        }

        val message = when (location) {
            is EmergencyMessageLocation.PhoneFix -> {
                "ALERTA MOTO CRASH: posible accidente de $name. Ubicacion: " +
                    "${mapsUrl(location.latitude, location.longitude)} " +
                    "(+/-${location.accuracyMeters}m ${formatTime(location.localTime)}). " +
                    "Mensaje automatico."
            }
            is EmergencyMessageLocation.DeviceGpsFix -> {
                "ALERTA MOTO CRASH: posible accidente de $name. Ubicacion: " +
                    "${mapsUrl(location.latitude, location.longitude)} " +
                    "(GPS moto ${formatTime(location.localTime)}). Mensaje automatico."
            }
            is EmergencyMessageLocation.LastKnownFix -> {
                require(location.ageMinutes >= 0) { "Location age cannot be negative." }
                "ALERTA MOTO CRASH: posible accidente de $name. Ultima ubicacion conocida " +
                    "(hace ${location.ageMinutes} min): " +
                    "${mapsUrl(location.latitude, location.longitude)} " +
                    "(${formatTime(location.localTime)}). Mensaje automatico."
            }
            is EmergencyMessageLocation.Unavailable -> {
                "ALERTA MOTO CRASH: posible accidente de $name. Ubicacion no disponible. " +
                    "Hora ${formatTime(location.localTime)}. Mensaje automatico."
            }
        }
        val completeMessage = if (drill) {
            "$DrillPrefix$message $DrillSuffix"
        } else {
            message
        }
        require(completeMessage.all(::isGsm0338Character)) {
            "Emergency message contains characters outside GSM 03.38."
        }
        return completeMessage
    }

    private fun normalizeName(name: String): String = buildString(name.length) {
        for (character in name.trim()) {
            if (character == 'ñ' || character == 'Ñ') {
                append(character)
            } else {
                append(
                    Normalizer.normalize(character.toString(), Normalizer.Form.NFD)
                        .filterNot { Character.getType(it) == Character.NON_SPACING_MARK.toInt() }
                )
            }
        }
    }

    private fun mapsUrl(latitude: Double, longitude: Double): String {
        require(latitude.isFinite() && latitude in -90.0..90.0) {
            "Latitude must be between -90 and 90."
        }
        require(longitude.isFinite() && longitude in -180.0..180.0) {
            "Longitude must be between -180 and 180."
        }
        return String.format(
            Locale.ROOT,
            "https://maps.google.com/?q=%.6f,%.6f",
            latitude,
            longitude
        )
    }

    private fun formatTime(time: LocalTime): String = time.format(TimeFormat)

    private fun isGsm0338Character(character: Char): Boolean =
        character in GsmBasicAlphabet || character in GsmExtensionAlphabet

    private companion object {
        const val MaximumRiderNameLength = 40
        const val DrillPrefix = "[PRUEBA] "
        const val DrillSuffix = " Simulacro, no es una emergencia."
        const val GsmBasicAlphabet =
            "@£$¥èéùìòÇ\nØø\rÅåΔ_ΦΓΛΩΠΨΣΘΞÆæßÉ !\"#¤%&'()*+,-./0123456789:;<=>?¡" +
                "ABCDEFGHIJKLMNOPQRSTUVWXYZÄÖÑÜ§¿abcdefghijklmnopqrstuvwxyzäöñüà"
        const val GsmExtensionAlphabet = "\u000C^{}\\[~]|€"
        val TimeFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT)
    }
}

sealed interface EmergencyMessageLocation {
    val localTime: LocalTime

    data class PhoneFix(
        val latitude: Double,
        val longitude: Double,
        val accuracyMeters: Int,
        override val localTime: LocalTime
    ) : EmergencyMessageLocation {
        init {
            require(accuracyMeters >= 0) { "Location accuracy cannot be negative." }
        }
    }

    data class DeviceGpsFix(
        val latitude: Double,
        val longitude: Double,
        override val localTime: LocalTime
    ) : EmergencyMessageLocation

    data class LastKnownFix(
        val latitude: Double,
        val longitude: Double,
        val ageMinutes: Int,
        override val localTime: LocalTime
    ) : EmergencyMessageLocation

    data class Unavailable(
        override val localTime: LocalTime
    ) : EmergencyMessageLocation
}
