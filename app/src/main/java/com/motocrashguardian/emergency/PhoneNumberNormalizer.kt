package com.motocrashguardian.emergency

class PhoneNumberNormalizer {
    fun normalize(input: String): PhoneNumberNormalization {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return PhoneNumberNormalization.Invalid(PhoneNumberError.EMPTY)

        val compact = buildString(trimmed.length) {
            for (character in trimmed) {
                when {
                    character.isWhitespace() || character == '-' ||
                        character == '(' || character == ')' -> Unit
                    else -> append(character)
                }
            }
        }
        if (compact.isEmpty()) return PhoneNumberNormalization.Invalid(PhoneNumberError.INVALID_FORMAT)

        val international = when {
            compact.startsWith("00") -> "+${compact.drop(2)}"
            compact.startsWith("+") -> compact
            else -> null
        }
        if (international != null) {
            val digits = international.drop(1)
            if (digits.any { it !in '0'..'9' }) {
                return PhoneNumberNormalization.Invalid(PhoneNumberError.INVALID_FORMAT)
            }
            return validateE164(digits)
        }

        if (compact.any { it !in '0'..'9' }) {
            return PhoneNumberNormalization.Invalid(PhoneNumberError.INVALID_FORMAT)
        }

        if (compact.length == ColombiaNationalNumberLength) {
            return when {
                compact.startsWith(ColombiaMobilePrefix) -> valid("+57$compact")
                isColombiaLandline(compact) -> valid("+57$compact")
                else -> PhoneNumberNormalization.Invalid(PhoneNumberError.INVALID_COLOMBIAN_NUMBER)
            }
        }

        if (compact.length == ColombiaCountryCodeLength &&
            compact.startsWith(ColombiaCountryCode)
        ) {
            return validateE164(compact)
        }
        return PhoneNumberNormalization.Invalid(PhoneNumberError.COUNTRY_CODE_REQUIRED)
    }

    private fun validateE164(digits: String): PhoneNumberNormalization {
        if (digits.length !in MinimumE164Digits..MaximumE164Digits ||
            digits.firstOrNull() !in '1'..'9'
        ) {
            return PhoneNumberNormalization.Invalid(PhoneNumberError.INVALID_E164)
        }
        return valid("+$digits")
    }

    private fun isColombiaLandline(number: String): Boolean =
        number.length == ColombiaNationalNumberLength &&
            number.startsWith(ColombiaLandlinePrefix) &&
            number[2] in '1'..'8'

    private fun valid(number: String) = PhoneNumberNormalization.Valid(number)

    private companion object {
        const val ColombiaCountryCode = "57"
        const val ColombiaCountryCodeLength = 12
        const val ColombiaNationalNumberLength = 10
        const val ColombiaMobilePrefix = "3"
        const val ColombiaLandlinePrefix = "60"
        const val MinimumE164Digits = 8
        const val MaximumE164Digits = 15
    }
}

sealed interface PhoneNumberNormalization {
    data class Valid(val e164: String) : PhoneNumberNormalization

    data class Invalid(val error: PhoneNumberError) : PhoneNumberNormalization
}

enum class PhoneNumberError {
    EMPTY,
    INVALID_FORMAT,
    INVALID_E164,
    INVALID_COLOMBIAN_NUMBER,
    COUNTRY_CODE_REQUIRED
}
