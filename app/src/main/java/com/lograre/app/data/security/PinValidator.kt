package com.lograre.app.data.security

sealed interface PinValidationResult {
    data object Valid : PinValidationResult
    data class TooShort(val minLength: Int) : PinValidationResult
    data object InvalidCharacters : PinValidationResult
}

object PinValidator {
    const val MIN_PIN_LENGTH: Int = 4

    fun validate(pin: String): PinValidationResult {
        if (pin.any { it !in '0'..'9' }) {
            return PinValidationResult.InvalidCharacters
        }
        if (pin.length < MIN_PIN_LENGTH) {
            return PinValidationResult.TooShort(MIN_PIN_LENGTH)
        }
        return PinValidationResult.Valid
    }
}
