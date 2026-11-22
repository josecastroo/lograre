package com.lograre.app.data.security

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

class PinHasher(
    private val iterations: Int = DEFAULT_ITERATIONS,
    private val secureRandom: SecureRandom = SecureRandom()
) {
    init {
        require(iterations > 0) { "iterations must be > 0" }
    }

    fun hash(pin: String): String {
        val salt = ByteArray(SALT_LENGTH_BYTES).also { secureRandom.nextBytes(it) }
        val derived = deriveKey(pin, salt, iterations)
        val encoder = Base64.getEncoder()
        return "$FORMAT_VERSION:$iterations:${encoder.encodeToString(salt)}:${encoder.encodeToString(derived)}"
    }

    fun verify(pin: String, encodedRecord: String): Boolean {
        val parts = encodedRecord.split(':')
        if (parts.size != 4 || parts[0] != FORMAT_VERSION) {
            return false
        }
        val storedIterations = parts[1].toIntOrNull() ?: return false
        if (storedIterations <= 0) {
            return false
        }
        val decoder = Base64.getDecoder()
        val salt = try {
            decoder.decode(parts[2])
        } catch (_: IllegalArgumentException) {
            return false
        }
        val expectedHash = try {
            decoder.decode(parts[3])
        } catch (_: IllegalArgumentException) {
            return false
        }
        if (salt.isEmpty() || expectedHash.isEmpty()) {
            return false
        }
        val actualHash = deriveKey(pin, salt, storedIterations, expectedHash.size * 8)
        return MessageDigest.isEqual(expectedHash, actualHash)
    }

    private fun deriveKey(
        pin: String,
        salt: ByteArray,
        iterations: Int,
        keyLengthBits: Int = KEY_LENGTH_BITS
    ): ByteArray {
        val chars = pin.toCharArray()
        val spec = PBEKeySpec(chars, salt, iterations, keyLengthBits)
        return try {
            SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
            chars.fill('\u0000')
        }
    }

    companion object {
        private const val FORMAT_VERSION = "v1"
        private const val ALGORITHM = "PBKDF2WithHmacSHA256"
        private const val SALT_LENGTH_BYTES = 16
        private const val KEY_LENGTH_BITS = 256
        const val DEFAULT_ITERATIONS = 120_000
    }
}
