package com.lograre.app.data.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PinRepositoryTest {

    private class InMemoryPinStorage : PinStorage {
        private var stored: String? = null
        override fun readRecord(): String? = stored
        override fun writeRecord(serializedRecord: String) {
            stored = serializedRecord
        }
        override fun clear() {
            stored = null
        }
    }

    private fun createRepository(): PinRepository =
        PinRepository(
            storage = InMemoryPinStorage(),
            hasher = PinHasher(iterations = 5_000)
        )

    @Test
    fun `setup accepts variable length numeric pins of at least 4 digits`() {
        val repo4 = createRepository()
        assertEquals(PinOperationResult.Success, repo4.setupPin("1234", "1234"))
        assertTrue(repo4.isPinConfigured())
        assertTrue(repo4.verifyPin("1234"))

        val repo10 = createRepository()
        assertEquals(PinOperationResult.Success, repo10.setupPin("9876543210", "9876543210"))
        assertTrue(repo10.verifyPin("9876543210"))
    }

    @Test
    fun `setup rejects too short, non-numeric, or mismatched confirmation`() {
        val repo = createRepository()

        assertEquals(
            PinOperationResult.ValidationError(PinValidationResult.TooShort(PinValidator.MIN_PIN_LENGTH)),
            repo.setupPin("123", "123")
        )
        assertEquals(
            PinOperationResult.ValidationError(PinValidationResult.InvalidCharacters),
            repo.setupPin("12a4", "12a4")
        )
        assertEquals(
            PinOperationResult.ConfirmationMismatch,
            repo.setupPin("1234", "1235")
        )
        assertFalse(repo.isPinConfigured())
    }

    @Test
    fun `setup cannot overwrite already configured pin`() {
        val repo = createRepository()
        assertEquals(PinOperationResult.Success, repo.setupPin("1234", "1234"))
        assertEquals(PinOperationResult.PinAlreadyConfigured, repo.setupPin("9999", "9999"))
        assertTrue(repo.verifyPin("1234"))
    }

    @Test
    fun `changePin requires valid current pin and replaces stored hash`() {
        val repo = createRepository()
        assertEquals(PinOperationResult.PinNotConfigured, repo.changePin("1234", "5678", "5678"))

        assertEquals(PinOperationResult.Success, repo.setupPin("1234", "1234"))
        assertEquals(PinOperationResult.InvalidCurrentPin, repo.changePin("0000", "567890", "567890"))
        assertEquals(PinOperationResult.Success, repo.changePin("1234", "567890", "567890"))

        assertFalse(repo.verifyPin("1234"))
        assertTrue(repo.verifyPin("567890"))
    }
}
