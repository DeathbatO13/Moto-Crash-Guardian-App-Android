package com.motocrashguardian.ui.alert

import com.motocrashguardian.core.model.AppSettings
import com.motocrashguardian.core.model.ContactRole
import com.motocrashguardian.core.model.EmergencyContact
import com.motocrashguardian.core.model.Incident
import com.motocrashguardian.core.model.IncidentStatus
import com.motocrashguardian.core.model.IncidentType
import com.motocrashguardian.core.model.TriggerType
import com.motocrashguardian.detection.GuardianState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant

class AlertUiStateTest {
    private val now = Instant.parse("2026-10-19T15:30:00Z")

    private val settings = AppSettings(
        countdownSeconds = 20,
        primaryContact = EmergencyContact(ContactRole.PRIMARY, "Ana", "+573001112233"),
        secondaryContact = EmergencyContact(ContactRole.SECONDARY, "Luis", "+573004445566")
    )

    private fun incident(
        type: IncidentType = IncidentType.REAL,
        status: IncidentStatus = IncidentStatus.ACTIVE,
        accuracy: Float? = null
    ) = Incident(
        type = type,
        triggerType = TriggerType.IMPACT,
        status = status,
        detectedAt = now,
        locationAccuracyMeters = accuracy
    )

    @Test
    fun `cuenta regresiva muestra segundos, contactos y precision`() {
        val state = GuardianState.Countdown(incident(accuracy = 8.6f), now.plusSeconds(12))

        assertEquals(
            AlertUiState.Countdown(
                remainingSeconds = 12,
                totalSeconds = 20,
                contactNames = listOf("Ana", "Luis"),
                locationAccuracyMeters = 8,
                isSimulation = false
            ),
            state.toAlertUiState(now, settings)
        )
    }

    @Test
    fun `los segundos restantes redondean hacia arriba`() {
        val state = GuardianState.Countdown(incident(), now.plusMillis(11_001))

        val ui = state.toAlertUiState(now, settings) as AlertUiState.Countdown

        assertEquals(12, ui.remainingSeconds)
    }

    @Test
    fun `el total nunca es menor que los segundos restantes`() {
        val state = GuardianState.Countdown(incident(), now.plusSeconds(30))

        val ui = state.toAlertUiState(now, settings) as AlertUiState.Countdown

        assertEquals(30, ui.totalSeconds)
    }

    @Test
    fun `sin contactos ni ubicacion la lista queda vacia`() {
        val state = GuardianState.Countdown(incident(), now.plusSeconds(5))

        val ui = state.toAlertUiState(now, AppSettings()) as AlertUiState.Countdown

        assertEquals(emptyList<String>(), ui.contactNames)
        assertEquals(null, ui.locationAccuracyMeters)
    }

    @Test
    fun `un simulacro se marca como simulacion`() {
        val state = GuardianState.Countdown(incident(type = IncidentType.DRILL), now.plusSeconds(5))

        val ui = state.toAlertUiState(now, settings) as AlertUiState.Countdown

        assertEquals(true, ui.isSimulation)
    }

    @Test
    fun `despachando y resultado`() {
        assertEquals(
            AlertUiState.Dispatching,
            GuardianState.Dispatching(incident()).toAlertUiState(now, settings)
        )
        assertEquals(
            AlertUiState.Result(IncidentStatus.DISPATCH_PARTIAL, isSimulation = false),
            GuardianState.Dispatched(incident(status = IncidentStatus.DISPATCH_PARTIAL))
                .toAlertUiState(now, settings)
        )
    }

    @Test
    fun `sin alerta activa no se muestra nada`() {
        listOf(GuardianState.Idle, GuardianState.Monitoring, GuardianState.ConnectionLost)
            .forEach { assertEquals(AlertUiState.Hidden, it.toAlertUiState(now, settings)) }
    }
}
