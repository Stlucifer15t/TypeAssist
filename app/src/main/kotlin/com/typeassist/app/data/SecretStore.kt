package com.typeassist.app.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * ============================== KEY VAULT ==============================
 *
 * Encrypts the API keys with a key that lives in the phone's hardware-backed keystore, so the
 * keys stored in the app's preferences cannot be read by anything that only gets hold of the
 * file - a backup tool, another app with storage access, or someone who pulls the storage off
 * the device. The key itself never leaves the keystore: it is generated here and used here.
 *
 * AES-GCM, so the stored value is also tamper-proof: changing a single byte makes the value
 * undecryptable rather than quietly different.
 *
 * The encrypted value carries its own initialisation vector and looks like
 * `enc1:<base64 iv>:<base64 ciphertext>`, which is what [Secrets] looks for.
 *
 * If the keystore is unavailable - some heavily modified devices, or a keystore that was reset -
 * the value is stored as it is rather than crashing the app, and the reason is written to the
 * log. A key that cannot be decrypted is treated as no key at all: the user is asked for it
 * again.
 */
class SecretStore {

    /** Encrypts a value for storage. */
    fun encrypt(plain: String): String {
        if (plain.isEmpty() || Secrets.isEncrypted(plain)) return plain
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, key())
            val iv = Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
            val data = Base64.encodeToString(
                cipher.doFinal(plain.toByteArray(StandardCharsets.UTF_8)),
                Base64.NO_WRAP
            )
            Secrets.PREFIX + iv + ":" + data
        } catch (e: Exception) {
            Log.w(TAG, "Could not encrypt a secret, storing it unchanged: ${e.message}")
            plain
        }
    }

    /** Opens a stored value. A value that was never encrypted is returned unchanged. */
    fun decrypt(stored: String): String {
        if (!Secrets.isEncrypted(stored)) return stored
        return try {
            val parts = stored.removePrefix(Secrets.PREFIX).split(":", limit = 2)
            if (parts.size != 2) {
                Log.w(TAG, "A stored secret has an unexpected shape and was dropped")
                return ""
            }
            val iv = Base64.decode(parts[0], Base64.NO_WRAP)
            val data = Base64.decode(parts[1], Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_LENGTH_BITS, iv))
            String(cipher.doFinal(data), StandardCharsets.UTF_8)
        } catch (e: Exception) {
            // The keystore was reset or the value was tampered with: the key has to be typed again.
            Log.w(TAG, "Could not decrypt a secret, it has to be entered again: ${e.message}")
            ""
        }
    }

    /** The hardware-backed key, created the first time it is needed. */
    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return generator.generateKey()
    }

    companion object {
        private const val TAG = "SecretStore"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "typeassist_api_keys"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val TAG_LENGTH_BITS = 128
    }
}
