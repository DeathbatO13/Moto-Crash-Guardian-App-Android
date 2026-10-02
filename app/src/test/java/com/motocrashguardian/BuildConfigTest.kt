package com.motocrashguardian

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BuildConfigTest {

    @Test
    fun `cada variante apunta al backend esperado`() {
        if (BuildConfig.DEBUG) {
            assertEquals("com.motocrashguardian.debug", BuildConfig.APPLICATION_ID)
            assertEquals("http://10.0.2.2:8080/", BuildConfig.BASE_URL)
        } else {
            assertEquals("com.motocrashguardian", BuildConfig.APPLICATION_ID)
            assertTrue(BuildConfig.BASE_URL.startsWith("https://"), BuildConfig.BASE_URL)
        }
        assertTrue(BuildConfig.BASE_URL.endsWith("/"), "Retrofit exige '/' final")
    }
}
