package com.motocrashguardian.emergency

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalTime

class GsmMessageBuilderTest {
    private val builder = GsmMessageBuilder()
    private val time = LocalTime.of(16, 43)

    @Test
    fun `construye mensaje de telefono con seis decimales hora y precision`() {
        val message = builder.build(
            riderName = "Daniel",
            location = EmergencyMessageLocation.PhoneFix(
                latitude = 4.927597,
                longitude = -74.020355,
                accuracyMeters = 8,
                localTime = time
            )
        )

        assertEquals(
            "ALERTA MOTO CRASH: posible accidente de Daniel. Ubicacion: " +
                "https://maps.google.com/?q=4.927597,-74.020355 (+/-8m 16:43). " +
                "Mensaje automatico.",
            message
        )
    }

    @Test
    fun `elimina tildes del nombre y conserva la ene`() {
        val message = builder.build(
            riderName = "José Ñúñez",
            location = EmergencyMessageLocation.Unavailable(time)
        )

        assertTrue(message.contains("Jose Ñuñez"))
        assertFalse(message.contains("é"))
        assertFalse(message.contains("ú"))
    }

    @Test
    fun `usa formato y metadatos propios de GPS de moto`() {
        val message = builder.build(
            riderName = "Ana",
            location = EmergencyMessageLocation.DeviceGpsFix(4.5, -74.25, time)
        )

        assertTrue(message.contains("https://maps.google.com/?q=4.500000,-74.250000"))
        assertTrue(message.contains("(GPS moto 16:43)"))
    }

    @Test
    fun `marca como ultima ubicacion conocida con edad`() {
        val message = builder.build(
            riderName = "Luis",
            location = EmergencyMessageLocation.LastKnownFix(4.1, -74.2, 9, time)
        )

        assertTrue(message.contains("Ultima ubicacion conocida (hace 9 min)"))
        assertTrue(message.contains("https://maps.google.com/?q=4.100000,-74.200000"))
    }

    @Test
    fun `construye el mensaje sin ubicacion disponible`() {
        val message = builder.build(
            riderName = "Nora",
            location = EmergencyMessageLocation.Unavailable(time)
        )

        assertEquals(
            "ALERTA MOTO CRASH: posible accidente de Nora. Ubicacion no disponible. " +
                "Hora 16:43. Mensaje automatico.",
            message
        )
    }

    @Test
    fun `el simulacro tiene prefijo y sufijo de seguridad`() {
        val message = builder.build(
            riderName = "Daniel",
            location = EmergencyMessageLocation.Unavailable(time),
            drill = true
        )

        assertTrue(message.startsWith("[PRUEBA] "))
        assertTrue(message.endsWith("Simulacro, no es una emergencia."))
    }

    @Test
    fun `rechaza nombres fuera de rango y caracteres fuera de GSM 03 38`() {
        assertThrows(IllegalArgumentException::class.java) {
            builder.build(" ", EmergencyMessageLocation.Unavailable(time))
        }
        assertThrows(IllegalArgumentException::class.java) {
            builder.build("A".repeat(41), EmergencyMessageLocation.Unavailable(time))
        }
        assertThrows(IllegalArgumentException::class.java) {
            builder.build("Rider 🚲", EmergencyMessageLocation.Unavailable(time))
        }
        assertThrows(IllegalArgumentException::class.java) {
            builder.build("Rider\nName", EmergencyMessageLocation.Unavailable(time))
        }
    }

    @Test
    fun `rechaza coordenadas o edades invalidas`() {
        assertThrows(IllegalArgumentException::class.java) {
            builder.build(
                "Daniel",
                EmergencyMessageLocation.PhoneFix(91.0, -74.0, 8, time)
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            builder.build(
                "Daniel",
                EmergencyMessageLocation.LastKnownFix(4.0, -74.0, -1, time)
            )
        }
    }

    @Test
    fun `el resultado solo usa caracteres del alfabeto GSM`() {
        val messages = listOf(
            builder.build(
                "Jose Ñunez",
                EmergencyMessageLocation.PhoneFix(4.927597, -74.020355, 8, time)
            ),
            builder.build("Ana", EmergencyMessageLocation.DeviceGpsFix(4.5, -74.25, time)),
            builder.build("Luis", EmergencyMessageLocation.LastKnownFix(4.1, -74.2, 9, time)),
            builder.build("Nora", EmergencyMessageLocation.Unavailable(time), drill = true)
        )

        assertTrue(messages.all { message -> message.all(::isGsmCharacter) })
    }

    private fun isGsmCharacter(character: Char): Boolean =
        character in GsmBasicAlphabet || character in GsmExtensionAlphabet

    private companion object {
        const val GsmBasicAlphabet =
            "@£$¥èéùìòÇ\nØø\rÅåΔ_ΦΓΛΩΠΨΣΘΞÆæßÉ !\"#¤%&'()*+,-./0123456789:;<=>?¡" +
                "ABCDEFGHIJKLMNOPQRSTUVWXYZÄÖÑÜ§¿abcdefghijklmnopqrstuvwxyzäöñüà"
        const val GsmExtensionAlphabet = "\u000C^{}\\[~]|€"
    }
}
