package com.motocrashguardian.emergency

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class DemoModeControllerTest {

    private val start = Instant.parse("2026-10-19T15:30:00Z")

    private class TestClock(private val scheduler: TestCoroutineScheduler, private val start: Instant) : Clock() {
        var skewMillis = 0L
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId?): Clock = this
        override fun instant(): Instant = start.plusMillis(scheduler.currentTime + skewMillis)
    }

    private fun TestScope.controller(
        duration: Duration = Duration.ofMinutes(10),
        clock: TestClock = TestClock(testScheduler, start)
    ) = DemoModeController(backgroundScope, clock, duration)

    @Test
    fun `empieza apagado`() = runTest {
        val demo = controller()

        assertEquals(DemoState.Off, demo.state.value)
        assertFalse(demo.isActive())
    }

    @Test
    fun `al activarlo queda encendido hasta la hora de caducidad`() = runTest {
        val demo = controller()

        demo.enable()

        assertEquals(DemoState.On(start.plus(Duration.ofMinutes(10))), demo.state.value)
        assertTrue(demo.isActive())
    }

    @Test
    fun `caduca solo al cumplirse el plazo`() = runTest {
        val demo = controller(Duration.ofMinutes(10))
        demo.enable()

        advanceTimeBy(Duration.ofMinutes(10).toMillis() - 1)
        runCurrent()
        assertTrue(demo.isActive())

        advanceTimeBy(2)
        runCurrent()
        assertEquals(DemoState.Off, demo.state.value)
        assertFalse(demo.isActive())
    }

    @Test
    fun `una demo vencida no cuenta como activa aunque la corrutina de caducidad se retrase`() = runTest {
        val clock = TestClock(testScheduler, start)
        val demo = controller(Duration.ofMinutes(10), clock)
        demo.enable()

        clock.skewMillis = Duration.ofMinutes(11).toMillis() // el reloj avanza sin que corra el delay

        assertFalse(demo.isActive())
    }

    @Test
    fun `desactivarlo es inmediato y cancela la caducidad pendiente`() = runTest {
        val demo = controller()
        demo.enable()

        demo.disable()

        assertEquals(DemoState.Off, demo.state.value)
        assertFalse(demo.isActive())
        advanceTimeBy(Duration.ofMinutes(30).toMillis())
        runCurrent()
        assertEquals(DemoState.Off, demo.state.value)
    }

    @Test
    fun `reactivarlo reinicia el plazo y la caducidad vieja no lo apaga`() = runTest {
        val demo = controller(Duration.ofMinutes(10))
        demo.enable()
        advanceTimeBy(Duration.ofMinutes(8).toMillis())
        runCurrent()

        demo.enable() // nuevo plazo: hasta el minuto 18
        advanceTimeBy(Duration.ofMinutes(3).toMillis()) // minuto 11: la primera caducidad ya habria vencido
        runCurrent()

        assertTrue(demo.isActive())
        assertEquals(DemoState.On(start.plus(Duration.ofMinutes(18))), demo.state.value)
    }

    @Test
    fun `desactivar sin haber activado no falla`() = runTest {
        val demo = controller()

        demo.disable()

        assertEquals(DemoState.Off, demo.state.value)
    }

    @Test
    fun `el plazo por defecto es de diez minutos`() {
        assertEquals(Duration.ofMinutes(10), DemoModeController.DefaultDuration)
    }
}
