package com.lograre.app.data.security

sealed interface PinOperationResult {
    data object Success : PinOperationResult
    data class ValidationError(val reason: PinValidationResult) : PinOperationResult
    data object ConfirmationMismatch : PinOperationResult
    data object InvalidCurrentPin : PinOperationResult
    data object PinAlreadyConfigured : PinOperationResult
    data object PinNotConfigured : PinOperationResult
}

class PinRepository(
    private val storage: PinStorage,
    private val hasher: PinHasher = PinHasher()
) {
    fun isPinConfigured(): Boolean = storage.readRecord() != null

    fun setupPin(pin: String, confirmation: String): PinOperationResult {
        if (isPinConfigured()) {
            return PinOperationResult.PinAlreadyConfigured
        }
        val validation = PinValidator.validate(pin)
        if (validation != PinValidationResult.Valid) {
            return PinOperationResult.ValidationError(validation)
        }
        if (pin != confirmation) {
            return PinOperationResult.ConfirmationMismatch
        }
        storage.writeRecord(hasher.hash(pin))
        return PinOperationResult.Success
    }

    fun verifyPin(pin: String): Boolean {
        val record = storage.readRecord() ?: return false
        return hasher.verify(pin, record)
    }

    fun changePin(
        currentPin: String,
        newPin: String,
        newConfirmation: String
    ): PinOperationResult {
        val existingRecord = storage.readRecord() ?: return PinOperationResult.PinNotConfigured
        if (!hasher.verify(currentPin, existingRecord)) {
            return PinOperationResult.InvalidCurrentPin
        }
        val validation = PinValidator.validate(newPin)
        if (validation != PinValidationResult.Valid) {
            return PinOperationResult.ValidationError(validation)
        }
        if (newPin != newConfirmation) {
            return PinOperationResult.ConfirmationMismatch
        }
        storage.writeRecord(hasher.hash(newPin))
        return PinOperationResult.Success
    }
}
