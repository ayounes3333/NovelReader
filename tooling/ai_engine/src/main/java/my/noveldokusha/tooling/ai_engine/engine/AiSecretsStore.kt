package my.noveldokusha.tooling.ai_engine.engine

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import dagger.hilt.android.qualifiers.ApplicationContext
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stores AI provider API keys encrypted at rest.
 *
 * An AES-256 key is generated inside the Android Keystore and never exposed;
 * values are encrypted with AES/GCM and persisted (iv + ciphertext, Base64) in a
 * private preferences file. Intentionally avoids the deprecated
 * androidx.security-crypto EncryptedSharedPreferences.
 */
@Singleton
class AiSecretsStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val preferences by lazy {
        context.getSharedPreferences("ai_secrets", Context.MODE_PRIVATE)
    }

    fun get(providerId: String): String? {
        val stored = preferences.getString(prefKey(providerId), null) ?: return null
        return runCatching { decrypt(stored) }.getOrNull()
    }

    fun set(providerId: String, secret: String) {
        if (secret.isEmpty()) {
            remove(providerId)
            return
        }
        preferences.edit().putString(prefKey(providerId), encrypt(secret)).apply()
    }

    fun remove(providerId: String) {
        preferences.edit().remove(prefKey(providerId)).apply()
    }

    fun has(providerId: String): Boolean =
        preferences.contains(prefKey(providerId))

    private fun prefKey(providerId: String) = "secret_$providerId"

    private fun masterKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(MASTER_KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                MASTER_KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
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
        cipher.init(Cipher.ENCRYPT_MODE, masterKey())
        val iv = cipher.iv
        val encrypted = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(iv + encrypted, Base64.NO_WRAP)
    }

    private fun decrypt(stored: String): String {
        val data = Base64.decode(stored, Base64.NO_WRAP)
        val iv = data.copyOfRange(0, IV_SIZE_BYTES)
        val encrypted = data.copyOfRange(IV_SIZE_BYTES, data.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, masterKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
        return String(cipher.doFinal(encrypted), Charsets.UTF_8)
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val MASTER_KEY_ALIAS = "noveldokusha_ai_master_key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_SIZE_BYTES = 12
        const val GCM_TAG_BITS = 128
    }
}
