package com.motocrashguardian.ui.history

import com.motocrashguardian.core.model.Trace
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

class TraceAnalysisTest {

    /** Una muestra: 6 enteros de 16 bits little-endian (ax, ay, az, gx, gy, gz). */
    private fun sample(ax: Int = 0, ay: Int = 0, az: Int = 0, gx: Int = 0, gy: Int = 0, gz: Int = 0): ByteArray {
        val out = ByteArray(12)
        listOf(ax, ay, az, gx, gy, gz).forEachIndexed { index, value ->
            out[index * 2] = (value and 0xFF).toByte()
            out[index * 2 + 1] = ((value shr 8) and 0xFF).toByte()
        }
        return out
    }

    private fun trace(
        samples: List<ByteArray>,
        preTrigger: Int,
        rateHz: Int = 100,
        accelLsbPerG: Int = 2048,
        gyroLsbPerDpsX10: Int = 164
    ) = Trace(
        incidentId = UUID.randomUUID(),
        sampleRateHz = rateHz,
        preTriggerSamples = preTrigger,
        totalSamples = samples.size,
        accelLsbPerG = accelLsbPerG,
        gyroLsbPerDpsX10 = gyroLsbPerDpsX10,
        samples = samples.reduce { acc, bytes -> acc + bytes }
    )

    // --- Decodificacion ---

    @Test
    fun `convierte la aceleracion a g con la escala del sensor`() {
        val decoded = decodeTrace(trace(listOf(sample(az = 2048), sample(ax = 2048, az = 2048)), preTrigger = 0))

        assertEquals(1f, decoded.points[0].accelMagnitudeG, 0.001f)
        assertEquals(1.4142f, decoded.points[1].accelMagnitudeG, 0.001f)
    }

    @Test
    fun `convierte el giro a grados por segundo`() {
        // 164 = 16,4 LSB por grado/segundo: 1640 cuentas son 100 °/s.
        val decoded = decodeTrace(trace(listOf(sample(gx = 1640)), preTrigger = 0))

        assertEquals(100f, decoded.points[0].gyroMagnitudeDps, 0.01f)
    }

    @Test
    fun `lee enteros negativos con signo`() {
        val decoded = decodeTrace(trace(listOf(sample(az = -2048)), preTrigger = 0))

        assertEquals(1f, decoded.points[0].accelMagnitudeG, 0.001f)
    }

    @Test
    fun `el disparo es el indice preTrigger y el tiempo usa la frecuencia de muestreo`() {
        val decoded = decodeTrace(trace(List(5) { sample(az = 2048) }, preTrigger = 2, rateHz = 100))

        assertEquals(listOf(-0.02f, -0.01f, 0f, 0.01f, 0.02f), decoded.points.map { it.timeSeconds })
    }

    @Test
    fun `marca como saturadas las muestras que tocan el limite del sensor`() {
        val decoded = decodeTrace(
            trace(
                listOf(
                    sample(az = 2048),
                    sample(ax = 32_767),
                    sample(gy = -32_768),
                    sample(az = 32_000)
                ),
                preTrigger = 0
            )
        )

        assertEquals(listOf(false, true, true, false), decoded.points.map { it.saturated })
        assertEquals(2, decoded.saturatedCount)
    }

    @Test
    fun `reporta los picos de aceleracion y de giro`() {
        val decoded = decodeTrace(
            trace(listOf(sample(az = 2048), sample(az = 10240, gx = 3280), sample(az = 4096)), preTrigger = 1)
        )

        assertEquals(5f, decoded.peakAccelG, 0.001f)
        assertEquals(200f, decoded.peakGyroDps, 0.01f)
    }

    // --- Ventana de -3 a +3 s ---

    @Test
    fun `la ventana recorta a menos 3 y mas 3 segundos`() {
        // 10 Hz, disparo en la muestra 50: cubre de -5 s a +4,9 s.
        val decoded = decodeTrace(trace(List(100) { sample(az = 2048) }, preTrigger = 50, rateHz = 10))

        val window = decoded.window()

        assertEquals(-3f, window.points.first().timeSeconds, 0.001f)
        assertEquals(3f, window.points.last().timeSeconds, 0.001f)
        assertEquals(61, window.points.size)
    }

    @Test
    fun `una traza mas corta que la ventana se conserva entera`() {
        val decoded = decodeTrace(trace(List(11) { sample(az = 2048) }, preTrigger = 5, rateHz = 10))

        assertEquals(11, decoded.window().points.size)
    }

    // --- Geometria de la grafica ---

    private fun point(t: Float, g: Float) = TracePoint(t, g, 0f, saturated = false)

    @Test
    fun `mapea tiempo a X y g a Y invertido`() {
        val offsets = mapToCanvas(
            points = listOf(point(-3f, 0f), point(0f, 5f), point(3f, 10f)),
            startSeconds = -3f,
            endSeconds = 3f,
            maxG = 10f,
            width = 600f,
            height = 200f
        )

        assertEquals(0f, offsets[0].x, 0.001f)
        assertEquals(200f, offsets[0].y, 0.001f) // 0 g abajo
        assertEquals(300f, offsets[1].x, 0.001f) // disparo al centro
        assertEquals(100f, offsets[1].y, 0.001f)
        assertEquals(600f, offsets[2].x, 0.001f)
        assertEquals(0f, offsets[2].y, 0.001f) // maximo arriba
    }

    @Test
    fun `un valor por encima del maximo se recorta al borde superior`() {
        val offsets = mapToCanvas(listOf(point(0f, 99f)), -3f, 3f, maxG = 10f, width = 600f, height = 200f)

        assertEquals(0f, offsets[0].y, 0.001f)
    }

    @Test
    fun `rango temporal vacio no produce puntos`() {
        assertTrue(mapToCanvas(listOf(point(0f, 1f)), 1f, 1f, 10f, 600f, 200f).isEmpty())
    }

    @Test
    fun `el maximo de la grafica incluye el pico y el umbral con margen`() {
        assertEquals(11.5f, chartMaxG(listOf(point(0f, 10f)), thresholdG = 3.5f), 0.001f)
        // Si el umbral supera al pico, manda el umbral.
        assertEquals(6.9f, chartMaxG(listOf(point(0f, 1f)), thresholdG = 6f), 0.001f)
    }

    @Test
    fun `el maximo de la grafica tiene un minimo para trazas muy tranquilas`() {
        assertEquals(2f, chartMaxG(emptyList(), thresholdG = 0.5f), 0.001f)
    }

    @Test
    fun `el umbral se ubica proporcionalmente`() {
        assertEquals(100f, thresholdY(5f, 10f, 200f), 0.001f)
        assertFalse(thresholdY(50f, 10f, 200f) < 0f)
    }

    @Test
    fun `la descripcion accesible resume pico, umbral y saturacion`() {
        val decoded = DecodedTrace(
            points = listOf(point(0f, 9.5f), point(0.01f, 4f).copy(saturated = true)),
            sampleRateHz = 100
        )

        val description = chartDescription(decoded, impactThresholdG = 3.5f)

        assertTrue(description.contains("9,5 g"), description)
        assertTrue(description.contains("3,5 g"), description)
        assertTrue(description.contains("1 muestra(s) con el sensor saturado"), description)
    }
}
