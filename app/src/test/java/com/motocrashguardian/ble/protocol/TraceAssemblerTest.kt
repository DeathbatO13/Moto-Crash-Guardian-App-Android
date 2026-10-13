package com.motocrashguardian.ble.protocol

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TraceAssemblerTest {

    private fun metadata(
        total: Int = 50,
        perChunk: Int = 19,
        chunkCount: Int = (total + perChunk - 1) / perChunk,
        rate: Int = 200,
        pre: Int = 10
    ) = TraceMetadata(
        sampleRateHz = rate,
        preTriggerSamples = pre,
        totalSamples = total,
        accelLsbPerG = 2048,
        gyroLsbPerDpsX10 = 164,
        chunkCount = chunkCount,
        samplesPerChunk = perChunk
    )

    /** La muestra i lleva i en cada eje, para poder comprobar el orden. */
    private fun sample(i: Int) = ImuSample(i, i + 1, i + 2, -i, -(i + 1), -(i + 2))

    private fun chunks(meta: TraceMetadata): List<TraceChunk> =
        (0 until meta.chunkCount).map { index ->
            val start = index * meta.samplesPerChunk
            val end = minOf(start + meta.samplesPerChunk, meta.totalSamples)
            TraceChunk(
                eventId = 7,
                chunkIndex = index,
                chunkCount = meta.chunkCount,
                samples = (start until end).map(::sample)
            )
        }

    // --- Metadatos ---

    @Test
    fun `metadatos coherentes no tienen error`() {
        assertNull(validateTraceMetadata(metadata()))
    }

    @Test
    fun `detecta metadatos incoherentes`() {
        val invalid = listOf(
            metadata().copy(sampleRateHz = 0),
            metadata().copy(totalSamples = 0, chunkCount = 0),
            metadata(total = 10, pre = 11),
            metadata().copy(accelLsbPerG = 0),
            metadata().copy(gyroLsbPerDpsX10 = 0),
            metadata().copy(samplesPerChunk = 0),
            metadata().copy(chunkCount = 99)
        )

        invalid.forEach { assertNotNull(validateTraceMetadata(it), it.toString()) }
    }

    // --- Ensamblado ---

    @Test
    fun `une los chunks en orden y respeta el formato de 12 bytes por muestra`() {
        val meta = metadata(total = 50)

        val result = assembleTrace(meta, chunks(meta)) as TraceAssembly.Complete

        assertEquals(50 * 12, result.samples.size)
        // Muestra 0: ax=0, ay=1, az=2, gx=0, gy=-1, gz=-2 (int16 little-endian).
        assertEquals(listOf<Byte>(0, 0, 1, 0, 2, 0, 0, 0, -1, -1, -2, -1), result.samples.take(12))
        // Primera muestra del segundo chunk (indice 19): ax=19.
        assertEquals(19.toByte(), result.samples[19 * 12])
    }

    @Test
    fun `los chunks pueden llegar desordenados`() {
        val meta = metadata(total = 50)

        val ordered = assembleTrace(meta, chunks(meta)) as TraceAssembly.Complete
        val shuffled = assembleTrace(meta, chunks(meta).reversed()) as TraceAssembly.Complete

        assertTrue(ordered.samples.contentEquals(shuffled.samples))
    }

    @Test
    fun `un chunk repetido se ignora`() {
        val meta = metadata(total = 50)
        val all = chunks(meta)

        val result = assembleTrace(meta, all + all[1])

        assertTrue(result is TraceAssembly.Complete)
    }

    @Test
    fun `el ultimo chunk puede ser mas corto`() {
        val meta = metadata(total = 20, perChunk = 19) // 19 + 1

        val result = assembleTrace(meta, chunks(meta)) as TraceAssembly.Complete

        assertEquals(20 * 12, result.samples.size)
    }

    @Test
    fun `una traza de un solo chunk es valida`() {
        val meta = metadata(total = 5, perChunk = 19, pre = 2)

        assertTrue(assembleTrace(meta, chunks(meta)) is TraceAssembly.Complete)
    }

    // --- Incompleta ---

    @Test
    fun `informa que chunks faltan`() {
        val meta = metadata(total = 50)
        val all = chunks(meta)

        val result = assembleTrace(meta, listOf(all[0], all[2])) as TraceAssembly.Incomplete

        assertEquals(listOf(1), result.missingChunks)
    }

    @Test
    fun `sin chunks esta incompleta`() {
        val meta = metadata(total = 50)

        val result = assembleTrace(meta, emptyList()) as TraceAssembly.Incomplete

        assertEquals(listOf(0, 1, 2), result.missingChunks)
    }

    // --- Invalida ---

    @Test
    fun `un chunk con indice fuera de rango es invalido`() {
        val meta = metadata(total = 50)
        val stray = chunks(meta).first().copy(chunkIndex = 9)

        assertTrue(assembleTrace(meta, chunks(meta) + stray) is TraceAssembly.Invalid)
    }

    @Test
    fun `un chunk que declara otra cuenta es invalido`() {
        val meta = metadata(total = 50)
        val wrong = chunks(meta).first().copy(chunkCount = 7)

        assertTrue(assembleTrace(meta, listOf(wrong) + chunks(meta).drop(1)) is TraceAssembly.Invalid)
    }

    @Test
    fun `un chunk intermedio con muestras de menos es invalido`() {
        val meta = metadata(total = 50)
        val all = chunks(meta)
        val short = all[0].copy(samples = all[0].samples.drop(1))

        assertTrue(assembleTrace(meta, listOf(short, all[1], all[2])) is TraceAssembly.Invalid)
    }

    @Test
    fun `un ultimo chunk con muestras de mas es invalido`() {
        val meta = metadata(total = 50)
        val all = chunks(meta)
        val long = all[2].copy(samples = all[2].samples + sample(99))

        assertTrue(assembleTrace(meta, listOf(all[0], all[1], long)) is TraceAssembly.Invalid)
    }

    @Test
    fun `metadatos invalidos no intentan armar nada`() {
        val result = assembleTrace(metadata().copy(sampleRateHz = 0), emptyList())

        assertTrue(result is TraceAssembly.Invalid)
    }
}
