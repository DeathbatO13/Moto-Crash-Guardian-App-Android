package com.motocrashguardian.ui.home

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.motocrashguardian.emergency.DemoState
import com.motocrashguardian.ui.theme.MotoCrashGuardianTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
// Pantalla alta: assertIsDisplayed exige que el nodo este dentro del viewport.
@Config(qualifiers = "w411dp-h2000dp-xxhdpi")
class DemoModeUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val now = Instant.parse("2026-10-19T15:30:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)

    // --- Formato del tiempo restante ---

    @Test
    fun formateaMinutosYSegundos() {
        assertEquals("10:00", formatRemaining(Duration.ofMinutes(10)))
        assertEquals("9:42", formatRemaining(Duration.ofSeconds(582)))
        assertEquals("0:05", formatRemaining(Duration.ofSeconds(5)))
        assertEquals("0:00", formatRemaining(Duration.ZERO))
    }

    @Test
    fun elTiempoRestanteNuncaEsNegativo() {
        assertEquals("0:00", formatRemaining(Duration.ofSeconds(-30)))
    }

    // --- Banner ---

    @Test
    fun elBannerMuestraElModoYElTiempoQueQueda() {
        composeRule.setContent {
            MotoCrashGuardianTheme {
                DemoBanner(
                    state = DemoState.On(now.plusSeconds(582)),
                    onDisable = {},
                    clock = clock
                )
            }
        }

        composeRule.onNodeWithText("MODO DEMO · 9:42").assertIsDisplayed()
        composeRule.onNodeWithText("Las alertas son simulacros.").assertIsDisplayed()
    }

    @Test
    fun elBannerSeOcultaConElDemoApagado() {
        composeRule.setContent {
            MotoCrashGuardianTheme { DemoBanner(state = DemoState.Off, onDisable = {}, clock = clock) }
        }

        assertEquals(0, composeRule.onAllNodes(hasText("MODO DEMO", substring = true)).fetchSemanticsNodes().size)
    }

    @Test
    fun desactivarDesdeElBannerNotifica() {
        var disabled = 0
        composeRule.setContent {
            MotoCrashGuardianTheme {
                DemoBannerContent(remainingText = "5:00", onDisable = { disabled++ })
            }
        }

        composeRule.onNodeWithText("DESACTIVAR").performClick()

        assertEquals(1, disabled)
    }

    // --- Tarjeta con el interruptor ---

    @Test
    fun laTarjetaExplicaParaQueSirve() {
        composeRule.setContent {
            MotoCrashGuardianTheme { DemoModeCard(state = DemoState.Off, onToggle = {}) }
        }

        composeRule.onNodeWithText("Modo demo").assertIsDisplayed()
        composeRule.onNode(hasText("cuenta regresiva de 10 s", substring = true)).assertIsDisplayed()
    }

    @Test
    fun elInterruptorNotificaLaNuevaOpcion() {
        val toggles = mutableListOf<Boolean>()
        composeRule.setContent {
            MotoCrashGuardianTheme {
                DemoModeCard(state = DemoState.Off, onToggle = { toggles += it })
            }
        }

        composeRule.onNode(isToggleable()).performClick()

        assertEquals(listOf(true), toggles)
    }

    @Test
    fun conElDemoActivoElInterruptorPideApagar() {
        val toggles = mutableListOf<Boolean>()
        composeRule.setContent {
            MotoCrashGuardianTheme {
                DemoModeCard(state = DemoState.On(now.plusSeconds(60)), onToggle = { toggles += it })
            }
        }

        composeRule.onNode(isToggleable()).performClick()

        assertEquals(listOf(false), toggles)
    }

    // --- Confirmacion ---

    @Test
    fun laConfirmacionExplicaQueCambiaYQueNoToca() {
        var confirmed = 0
        var dismissed = 0
        composeRule.setContent {
            MotoCrashGuardianTheme {
                DemoConfirmDialog(onConfirm = { confirmed++ }, onDismiss = { dismissed++ })
            }
        }

        composeRule.onNodeWithText("¿Activar el modo demo?").assertIsDisplayed()
        composeRule.onNode(hasText("Durante 10 minutos", substring = true)).assertIsDisplayed()
        composeRule.onNode(hasText("[PRUEBA]", substring = true)).assertIsDisplayed()
        composeRule.onNode(hasText("No cambia tu configuración ni la del módulo", substring = true))
            .assertIsDisplayed()
        composeRule.onNodeWithText("ACTIVAR").performClick()
        composeRule.onNodeWithText("Cancelar").performClick()

        assertEquals(1, confirmed)
        assertEquals(1, dismissed)
    }
}
