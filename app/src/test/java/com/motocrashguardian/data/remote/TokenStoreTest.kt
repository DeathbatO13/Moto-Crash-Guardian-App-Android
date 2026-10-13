package com.motocrashguardian.data.remote

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class TokenStoreTest {

    private val preferences = RuntimeEnvironment.getApplication()
        .getSharedPreferences("test_auth", Context.MODE_PRIVATE)

    /** Cifrado de prueba reversible; el real (Keystore) solo existe en un dispositivo. */
    private class ReversingCipher(var broken: Boolean = false) : SecretCipher {
        override fun encrypt(plain: ByteArray) = plain.reversedArray() + byteArrayOf(0x7F)
        override fun decrypt(blob: ByteArray): ByteArray {
            check(!broken) { "clave invalidada" }
            return blob.copyOf(blob.size - 1).reversedArray()
        }
    }

    private val cipher = ReversingCipher()
    private val store = TokenStore(preferences, cipher)

    @Before
    fun clean() {
        preferences.edit().clear().commit()
    }

    @Test
    fun guardaYRecuperaLaSesion() {
        store.save("inst-1", "secreto-123")

        assertEquals("inst-1", store.installationId())
        assertEquals("secreto-123", store.token())
    }

    @Test
    fun elTokenNoSeGuardaEnTextoPlano() {
        store.save("inst-1", "secreto-123")

        val raw = preferences.all.values.joinToString { it.toString() }

        assertFalse(raw.contains("secreto-123"))
    }

    @Test
    fun sinSesionDevuelveNull() {
        assertNull(store.token())
        assertNull(store.installationId())
    }

    @Test
    fun clearBorraIdYToken() {
        store.save("inst-1", "secreto-123")

        store.clear()

        assertNull(store.token())
        assertNull(store.installationId())
    }

    @Test
    fun unTokenQueNoSePuedeDescifrarSeDescarta() {
        store.save("inst-1", "secreto-123")
        cipher.broken = true

        assertNull(store.token())

        cipher.broken = false
        assertNull(store.installationId())
        assertTrue(preferences.all.isEmpty())
    }
}
