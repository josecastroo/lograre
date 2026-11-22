package com.lograre.app.data.security

import android.content.Context
import android.content.SharedPreferences
import java.util.Base64

interface PinStorage {
    fun readRecord(): String?
    fun writeRecord(serializedRecord: String)
    fun clear()
}

class KeystorePinStorage(
    context: Context,
    private val cipher: SecretCipher = AndroidKeystoreCipher()
) : PinStorage {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun readRecord(): String? {
        val encoded = prefs.getString(KEY_PIN_RECORD, null) ?: return null
        return try {
            val encrypted = Base64.getDecoder().decode(encoded)
            cipher.decrypt(encrypted).toString(Charsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }

    override fun writeRecord(serializedRecord: String) {
        val encrypted = cipher.encrypt(serializedRecord.toByteArray(Charsets.UTF_8))
        val encoded = Base64.getEncoder().encodeToString(encrypted)
        prefs.edit().putString(KEY_PIN_RECORD, encoded).commit()
    }

    override fun clear() {
        prefs.edit().remove(KEY_PIN_RECORD).commit()
    }

    companion object {
        private const val PREFS_NAME = "lograre_pin_store"
        private const val KEY_PIN_RECORD = "pin_hash_record"
    }
}
