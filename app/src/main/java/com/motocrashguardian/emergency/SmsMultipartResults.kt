package com.motocrashguardian.emergency

internal class SmsMultipartResultCollector(private val expectedParts: Int) {
    private val results = arrayOfNulls<Int>(expectedParts)

    init {
        require(expectedParts > 0)
    }

    @Synchronized
    fun record(partIndex: Int, resultCode: Int): List<Int>? {
        if (partIndex !in results.indices || results[partIndex] != null) return null
        results[partIndex] = resultCode
        return results.takeIf { values -> values.all { it != null } }
            ?.map { requireNotNull(it) }
    }

    @Synchronized
    fun snapshot(): List<Int> = results.mapNotNull { it }
}

internal fun allSmsPartsSucceeded(
    results: List<Int>,
    expectedParts: Int,
    resultOk: Int
): Boolean = results.size == expectedParts && results.all { it == resultOk }

internal fun selectSmsSubscriptionId(
    defaultSmsSubscriptionId: Int,
    defaultVoiceSubscriptionId: Int,
    invalidSubscriptionId: Int
): Int? = when {
    defaultSmsSubscriptionId != invalidSubscriptionId -> defaultSmsSubscriptionId
    defaultVoiceSubscriptionId != invalidSubscriptionId -> defaultVoiceSubscriptionId
    else -> null
}
