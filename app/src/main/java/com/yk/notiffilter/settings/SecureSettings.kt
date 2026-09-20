package com.yk.notiffilter.settings

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.yk.notiffilter.classify.GroqClassifier
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Stores the API key encrypted under an AndroidKeystore-backed AES-GCM key.
 *
 * Rolled by hand rather than via androidx.security-crypto, which is deprecated. It is
 * about sixty lines, has no dependency, and sidesteps having to track that library's
 * replacement.
 *
 * The real argument for runtime entry over a BuildConfig field is not cryptographic
 * strength on an unrooted personal phone — it is that a key which was never in the
 * build can never be committed by accident.
 */
class SecureSettings(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("notiffilter_settings", Context.MODE_PRIVATE)

    var apiKey: String
        get() = prefs.getString(KEY_API, null)?.let(::decrypt).orEmpty()
        set(value) {
            if (value.isBlank()) {
                prefs.edit().remove(KEY_API).apply()
            } else {
                prefs.edit().putString(KEY_API, encrypt(value)).apply()
            }
        }

    var model: String
        get() = prefs.getString(KEY_MODEL, null) ?: GroqClassifier.DEFAULT_MODEL
        set(value) = prefs.edit().putString(KEY_MODEL, value.trim()).apply()

    var baseUrl: String
        get() = prefs.getString(KEY_BASE_URL, null) ?: GroqClassifier.DEFAULT_BASE_URL
        set(value) = prefs.edit().putString(KEY_BASE_URL, value.trim()).apply()

    /** How many of the user's corrections are replayed as few-shot examples. */
    var exampleLimit: Int
        get() = prefs.getInt(KEY_EXAMPLES, 50)
        set(value) = prefs.edit().putInt(KEY_EXAMPLES, value.coerceIn(0, 200)).apply()

    val isConfigured: Boolean get() = apiKey.isNotBlank()

    // --- Keystore-backed AES-GCM -------------------------------------------------

    private fun secretKey(): SecretKey {
        val store = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (store.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    private fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val ciphertext = cipher.doFinal(plain.toByteArray())
        // IV is prefixed rather than stored separately so the blob stays self-contained.
        val blob = cipher.iv + ciphertext
        return Base64.encodeToString(blob, Base64.NO_WRAP)
    }

    private fun decrypt(stored: String): String = runCatching {
        val blob = Base64.decode(stored, Base64.NO_WRAP)
        val iv = blob.copyOfRange(0, IV_BYTES)
        val ciphertext = blob.copyOfRange(IV_BYTES, blob.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, iv))
        String(cipher.doFinal(ciphertext))
    }.getOrDefault("")

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val ALIAS = "notiffilter_api_key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12

        const val KEY_API = "api_key_enc"
        const val KEY_MODEL = "model"
        const val KEY_BASE_URL = "base_url"
        const val KEY_EXAMPLES = "example_limit"
    }
}
