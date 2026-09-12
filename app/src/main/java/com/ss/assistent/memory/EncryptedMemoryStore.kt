package com.ss.assistent.memory

import android.content.Context
import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Small local encryption layer for memory payloads.
 *
 * The payload is encrypted with AES-GCM using a key held by the Android Keystore. The
 * SharedPreferences file only contains the encrypted blob; the plaintext memory JSON is
 * never intentionally persisted after migration.
 */
internal class EncryptedMemoryStore(
    context: Context,
    private val preferencesName: String,
    private val encryptedKey: String = "encrypted_payload",
    private val legacyKey: String? = null,
    private val keystoreAlias: String
) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)

    fun read(): String? {
        val encrypted = preferences.getString(encryptedKey, null)
        if (!encrypted.isNullOrEmpty()) {
            return runCatching { decrypt(encrypted) }.getOrNull()
        }

        val legacy = legacyKey?.let { preferences.getString(it, null) }
        if (!legacy.isNullOrEmpty()) {
            // Migrate existing plaintext data exactly once. If encryption fails, leave the
            // legacy value untouched rather than destroying data the user already has.
            if (write(legacy)) {
                preferences.edit().remove(legacyKey).apply()
            }
            return legacy
        }
        return null
    }

    fun write(value: String): Boolean = runCatching {
        val encrypted = encrypt(value)
        preferences.edit()
            .putString(encryptedKey, encrypted)
            .apply()
        true
    }.getOrDefault(false)

    fun clear() {
        preferences.edit()
            .remove(encryptedKey)
            .apply()
    }

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val ciphertext = cipher.doFinal(value.toByteArray(StandardCharsets.UTF_8))
        val iv = cipher.iv
        return encode(iv) + ":" + encode(ciphertext)
    }

    private fun decrypt(payload: String): String {
        val separator = payload.indexOf(':')
        require(separator > 0) { "Invalid encrypted memory payload" }
        val iv = decode(payload.substring(0, separator))
        val ciphertext = decode(payload.substring(separator + 1))
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
        return String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8)
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existing = keyStore.getKey(keystoreAlias, null)
        if (existing is SecretKey) return existing

        val generator = KeyGenerator.getInstance(ALGORITHM, ANDROID_KEYSTORE)
        generator.init(
            android.security.keystore.KeyGenParameterSpec.Builder(
                keystoreAlias,
                android.security.keystore.KeyProperties.PURPOSE_ENCRYPT or
                    android.security.keystore.KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(KEY_SIZE_BITS)
                .build()
        )
        return generator.generateKey()
    }

    private fun encode(bytes: ByteArray): String =
        Base64.encodeToString(bytes, Base64.NO_WRAP)

    private fun decode(value: String): ByteArray =
        Base64.decode(value, Base64.NO_WRAP)

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val ALGORITHM = "AES"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_BITS = 128
        private const val KEY_SIZE_BITS = 256
    }
}
