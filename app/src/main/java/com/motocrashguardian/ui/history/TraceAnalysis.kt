package com.motocrashguardian.ui.history

import androidx.compose.ui.geometry.Offset
import com.motocrashguardian.core.model.Trace
import kotlin.math.max
import kotlin.math.sqrt

/** Una muestra de la caja negra ya convertida a unidades fisicas. */
data class TracePoint(
    /** Segundos respecto al disparo: negativo antes, 0 en el disparo, positivo despues. */
    val timeSeconds: Float,
    val accelMagnitudeG: Float,
    val gyroMagnitudeDps: Float,
    /** Algun eje llego al limite del sensor, asi que el valor real pudo ser mayor. */
    val saturated: Boolean
)

data class DecodedTrace(
    val points: List<TracePoint>,
    val sampleRateHz: Int
) {
    val peakAccelG: Float get() = points.maxOfOrNull { it.accelMagnitudeG } ?: 0f
    val peakGyroDps: Float get() = points.maxOfOrNull { it.gyroMagnitudeDps } ?: 0f
    val saturatedCount: Int get() = points.count { it.saturated }

    /** Solo la ventana pedida (por defecto, de -3 a +3 s alrededor del disparo). */
    fun window(startSeconds: Float = WindowStartSeconds, endSeconds: Float = WindowEndSeconds): DecodedTrace =
        copy(points = points.filter { it.timeSeconds in startSeconds..endSeconds })

    companion object {
        const val WindowStartSeconds = -3f
        const val WindowEndSeconds = 3f
    }
}

private const val BytesPerSample = 12
private const val Int16Max = 32_767
private const val Int16Min = -32_768

/**
 * Convierte la traza cruda (6 enteros de 16 bits little-endian por muestra: ax, ay, az, gx, gy, gz)
 * a unidades fisicas. El disparo esta en el indice `preTriggerSamples`.
 */
fun decodeTrace(trace: Trace): DecodedTrace {
    val rate = trace.sampleRateHz.toFloat()
    val accelScale = trace.accelLsbPerG.toFloat()
    val gyroScale = trace.gyroLsbPerDpsX10 / 10f
    val samples = trace.samples

    val points = (0 until trace.totalSamples).map { index ->
        val offset = index * BytesPerSample
        val raw = IntArray(6) { axis -> int16(samples, offset + axis * 2) }
        TracePoint(
            timeSeconds = (index - trace.preTriggerSamples) / rate,
            accelMagnitudeG = magnitude(raw[0], raw[1], raw[2]) / accelScale,
            gyroMagnitudeDps = magnitude(raw[3], raw[4], raw[5]) / gyroScale,
            saturated = raw.any { it >= Int16Max || it <= Int16Min }
        )
    }
    return DecodedTrace(points, trace.sampleRateHz)
}

private fun int16(bytes: ByteArray, offset: Int): Int =
    ((bytes[offset + 1].toInt() shl 8) or (bytes[offset].toInt() and 0xFF)).toShort().toInt()

private fun magnitude(x: Int, y: Int, z: Int): Float =
    sqrt(x.toDouble() * x + y.toDouble() * y + z.toDouble() * z).toFloat()

/** Rango vertical de la grafica: deja aire sobre el pico y siempre incluye el umbral. */
fun chartMaxG(points: List<TracePoint>, thresholdG: Float): Float =
    max(max(points.maxOfOrNull { it.accelMagnitudeG } ?: 0f, thresholdG) * 1.15f, MinChartMaxG)

private const val MinChartMaxG = 2f

/**
 * Pasa puntos (tiempo, g) a coordenadas del lienzo. El origen del lienzo esta arriba a la
 * izquierda, asi que el eje Y se invierte.
 */
fun mapToCanvas(
    points: List<TracePoint>,
    startSeconds: Float,
    endSeconds: Float,
    maxG: Float,
    width: Float,
    height: Float
): List<Offset> {
    val span = (endSeconds - startSeconds).takeIf { it > 0f } ?: return emptyList()
    return points.map { point ->
        Offset(
            x = (point.timeSeconds - startSeconds) / span * width,
            y = height - (point.accelMagnitudeG / maxG).coerceIn(0f, 1f) * height
        )
    }
}

/** Posicion vertical del umbral en el lienzo. */
fun thresholdY(thresholdG: Float, maxG: Float, height: Float): Float =
    height - (thresholdG / maxG).coerceIn(0f, 1f) * height
