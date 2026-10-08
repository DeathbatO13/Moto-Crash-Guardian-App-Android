package com.motocrashguardian.emergency

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PhoneNumberNormalizerTest {
    private val normalizer = PhoneNumberNormalizer()

    @Test
    fun `normaliza celular colombiano local con formato`() {
        assertEquals(
            PhoneNumberNormalization.Valid("+573001234567"),
            normalizer.normalize("300 123 4567")
        )
        assertEquals(
            PhoneNumberNormalization.Valid("+573001234567"),
            normalizer.normalize("(300) 123-4567")
        )
    }

    @Test
    fun `conserva pais explicito en formato mas o prefijo internacional`() {
        assertEquals(
            PhoneNumberNormalization.Valid("+573001234567"),
            normalizer.normalize("+57 (300) 123-4567")
        )
        assertEquals(
            PhoneNumberNormalization.Valid("+573001234567"),
            normalizer.normalize("0057 300 123 4567")
        )
        assertEquals(
            PhoneNumberNormalization.Valid("+14155552671"),
            normalizer.normalize("+1 415 555 2671")
        )
    }

    @Test
    fun `admite codigo colombiano sin simbolo mas y telefono fijo`() {
        assertEquals(
            PhoneNumberNormalization.Valid("+573001234567"),
            normalizer.normalize("573001234567")
        )
        assertEquals(
            PhoneNumberNormalization.Valid("+576012345678"),
            normalizer.normalize("601 234 5678")
        )
    }

    @Test
    fun `rechaza entrada vacia y formatos con letras o extensiones`() {
        assertEquals(
            PhoneNumberNormalization.Invalid(PhoneNumberError.EMPTY),
            normalizer.normalize("  ")
        )
        assertEquals(
            PhoneNumberNormalization.Invalid(PhoneNumberError.INVALID_FORMAT),
            normalizer.normalize("300ABC4567")
        )
        assertEquals(
            PhoneNumberNormalization.Invalid(PhoneNumberError.INVALID_FORMAT),
            normalizer.normalize("+57 300 123 4567 ext 2")
        )
        assertEquals(
            PhoneNumberNormalization.Invalid(PhoneNumberError.INVALID_FORMAT),
            normalizer.normalize("٣٠٠١٢٣٤٥٦٧")
        )
    }

    @Test
    fun `rechaza numeros colombianos locales que no cumplen el plan permitido`() {
        assertEquals(
            PhoneNumberNormalization.Invalid(PhoneNumberError.INVALID_COLOMBIAN_NUMBER),
            normalizer.normalize("2001234567")
        )
        assertEquals(
            PhoneNumberNormalization.Invalid(PhoneNumberError.COUNTRY_CODE_REQUIRED),
            normalizer.normalize("1234567")
        )
    }

    @Test
    fun `valida estructura E164 en entradas internacionales`() {
        assertEquals(
            PhoneNumberNormalization.Invalid(PhoneNumberError.INVALID_E164),
            normalizer.normalize("+01234567")
        )
        assertEquals(
            PhoneNumberNormalization.Invalid(PhoneNumberError.INVALID_E164),
            normalizer.normalize("+1234567890123456")
        )
        assertEquals(
            PhoneNumberNormalization.Invalid(PhoneNumberError.INVALID_E164),
            normalizer.normalize("+1234567")
        )
    }
}
