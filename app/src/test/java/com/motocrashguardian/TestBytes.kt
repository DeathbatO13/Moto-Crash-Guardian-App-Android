package com.motocrashguardian

/**
 * `byteArrayOf` solo acepta literales entre -128 y 127; los vectores del protocolo BLE usan
 * valores hexadecimales hasta 0xFF. Acepta Int o Byte y los trunca a un byte.
 */
fun bytesOf(vararg values: Number): ByteArray = ByteArray(values.size) { values[it].toByte() }
