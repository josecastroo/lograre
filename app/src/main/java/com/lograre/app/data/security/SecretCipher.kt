package com.lograre.app.data.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

interface SecretCipher {
    fun encrypt(plaintext: ByteArray): ByteArray
    fun decrypt(ciphertext: ByteArray): ByteArray
}

class AndroidKeystoreCipher(
    private val keyAlias: String = DEFAULT_KEY_ALIAS
) : SecretCipher {

    override fun encrypt(plaintext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
        val iv = cipher.iv
        require(iv.size in 1..255) { "Unexpected IV length: ${iv.size}" }
        val encrypted = cipher.doFinal(plaintext)
        return ByteArray(1 + iv.size + encrypted.size).apply {
            this[0] = iv.size.toByte()
            iv.copyInto(this, destinationOffset = 1)
            encrypted.copyInto(this, destinationOffset = 1 + iv.size)
        }
    }

    override fun decrypt(ciphertext: ByteArray): ByteArray {
        require(ciphertext.size > 2) { "Ciphertext payload is too short" }
        val ivLength = ciphertext[0].toInt() and 0xFF
        require(ivLength > 0 && ciphertext.size > 1 + ivLength) { "Invalid IV length in payload" }
        val iv = ciphertext.copyOfRange(1, 1 + ivLength)
        val encrypted = ciphertext.copyOfRange(1 + ivLength, ciphertext.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateSecretKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
        return cipher.doFinal(encrypted)
    }

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existing = keyStore.getKey(keyAlias, null) as? SecretKey
        if (existing != null) {
            return existing
        }
        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            keyAlias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(AES_KEY_SIZE_BITS)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val DEFAULT_KEY_ALIAS = "lograre_pin_aes_key"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val AES_KEY_SIZE_BITS = 256
        private const val GCM_TAG_BITS = 128
    }
}
