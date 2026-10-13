package com.motocrashguardian.ble.protocol

/** Resultado de armar la traza a partir de los metadatos y los chunks recibidos. */
sealed interface TraceAssembly {
    /** Traza completa y coherente: `samples` son `totalSamples * 12` bytes (6 x int16 LE por muestra). */
    class Complete(val samples: ByteArray) : TraceAssembly

    /** Faltan chunks (los indices indicados); normalmente basta con reintentar. */
    data class Incomplete(val missingChunks: List<Int>) : TraceAssembly

    /** Los datos recibidos no son coherentes entre si o con los metadatos. */
    data class Invalid(val reason: String) : TraceAssembly
}

/** `null` si los metadatos son coherentes; si no, el motivo. */
fun validateTraceMetadata(metadata: TraceMetadata): String? = when {
    metadata.sampleRateHz <= 0 -> "frecuencia de muestreo invalida"
    metadata.totalSamples <= 0 -> "traza vacia"
    metadata.preTriggerSamples > metadata.totalSamples -> "preTrigger mayor que el total"
    metadata.accelLsbPerG <= 0 || metadata.gyroLsbPerDpsX10 <= 0 -> "escala del sensor invalida"
    metadata.samplesPerChunk <= 0 -> "muestras por chunk invalido"
    metadata.chunkCount != expectedChunkCount(metadata) ->
        "chunkCount ${metadata.chunkCount} no coincide con el esperado ${expectedChunkCount(metadata)}"
    else -> null
}

private fun expectedChunkCount(metadata: TraceMetadata): Int =
    (metadata.totalSamples + metadata.samplesPerChunk - 1) / metadata.samplesPerChunk

/**
 * Une los chunks (en cualquier orden y con repetidos) en una sola traza validando que la cuenta
 * y el tamaño de cada chunk coincidan con los metadatos. Una traza mal armada no se guarda:
 * mostraria una grafica falsa de un evento real.
 */
fun assembleTrace(metadata: TraceMetadata, chunks: Collection<TraceChunk>): TraceAssembly {
    validateTraceMetadata(metadata)?.let { return TraceAssembly.Invalid(it) }

    // Un chunk repetido (reenvio) se ignora; el primero que llego manda.
    val byIndex = LinkedHashMap<Int, TraceChunk>()
    for (chunk in chunks) {
        if (chunk.chunkCount != metadata.chunkCount) {
            return TraceAssembly.Invalid(
                "chunk ${chunk.chunkIndex} declara ${chunk.chunkCount} chunks, se esperaban ${metadata.chunkCount}"
            )
        }
        if (chunk.chunkIndex !in 0 until metadata.chunkCount) {
            return TraceAssembly.Invalid("indice de chunk fuera de rango: ${chunk.chunkIndex}")
        }
        byIndex.putIfAbsent(chunk.chunkIndex, chunk)
    }

    val missing = (0 until metadata.chunkCount).filter { it !in byIndex }
    if (missing.isNotEmpty()) return TraceAssembly.Incomplete(missing)

    val out = ByteArray(metadata.totalSamples * BleProtocol.IMU_SAMPLE_SIZE)
    var written = 0
    for (index in 0 until metadata.chunkCount) {
        val chunk = byIndex.getValue(index)
        val expected = if (index < metadata.chunkCount - 1) {
            metadata.samplesPerChunk
        } else {
            metadata.totalSamples - (metadata.chunkCount - 1) * metadata.samplesPerChunk
        }
        if (chunk.samples.size != expected) {
            return TraceAssembly.Invalid(
                "chunk $index trae ${chunk.samples.size} muestras, se esperaban $expected"
            )
        }
        for (sample in chunk.samples) {
            written = sample.writeTo(out, written)
        }
    }
    check(written == out.size) { "longitud inesperada: $written de ${out.size}" }
    return TraceAssembly.Complete(out)
}

/** Escribe la muestra como 6 enteros de 16 bits little-endian y devuelve la nueva posicion. */
private fun ImuSample.writeTo(target: ByteArray, offset: Int): Int {
    var position = offset
    for (value in intArrayOf(ax, ay, az, gx, gy, gz)) {
        target[position++] = (value and 0xFF).toByte()
        target[position++] = ((value shr 8) and 0xFF).toByte()
    }
    return position
}
