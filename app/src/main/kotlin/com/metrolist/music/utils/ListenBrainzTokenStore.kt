package com.metrolist.music.utils

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.core.content.edit
import kotlinx.coroutines.CompletableDeferred
import timber.log.Timber
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * A secure storage manager for the ListenBrainz user token.
 * It uses the Android KeyStore and AES/GCM encryption to safely store the token in [SharedPreferences].
 */
object ListenBrainzTokenStore {
    private const val PREFS_NAME = "listenbrainz_token"
    private const val TOKEN_KEY = "user_token"

    @Volatile
    private var prefs: SharedPreferences? = null

    private val initDeferred = CompletableDeferred<Unit>()

    /**
     * Initializes the [ListenBrainzTokenStore] with the given application [Context].
     * Sets up the shared preferences and ensures the encryption keys are created.
     *
     * @param context The application context.
     */
    fun init(context: Context) {
        if (prefs != null) return
        synchronized(this) {
            if (prefs != null) return
            try {
                AesKeystore.getOrCreateKey()
                prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                initDeferred.complete(Unit)
            } catch (e: Exception) {
                Timber.e(e, "ListenBrainzTokenStore init failed")
                initDeferred.completeExceptionally(e)
            }
        }
    }

    /**
     * Encrypts the raw string token using AES key from KeyStore.
     *
     * @param value The plaintext token.
     * @return The encrypted token encoded in Base64, or null if encryption fails.
     */
    private fun encrypt(value: String?): String? {
        if (value == null) return null
        return try {
            AesKeystore.encrypt(value)
        } catch (e: Exception) {
            Timber.e(e, "ListenBrainzTokenStore: encrypt failed")
            null
        }
    }

    /**
     * Decrypts the Base64-encoded encrypted token using AES key from KeyStore.
     *
     * @param value The encrypted token.
     * @return The plaintext token, or null if decryption fails.
     */
    private fun decrypt(value: String?): String? {
        if (value == null) return null
        return try {
            AesKeystore.decrypt(value)
        } catch (e: Exception) {
            Timber.e(e, "ListenBrainzTokenStore: decrypt failed")
            null
        }
    }

    /**
     * Suspends until the store is initialized and then retrieves the decrypted token.
     *
     * @return The decrypted token if found, or null otherwise.
     */
    suspend fun retrieveSuspend(): String? {
        try {
            initDeferred.await()
        } catch (_: Exception) {
            return null
        }
        return retrieve()
    }

    /**
     * Encrypts and persists the given token in secure storage.
     *
     * @param token The token to store.
     * @return True if the token was successfully stored, false otherwise.
     */
    fun store(token: String): Boolean {
        val p = prefs ?: run {
            Timber.w("ListenBrainzTokenStore: cannot store, prefs not initialized")
            return false
        }
        val encryptedToken = encrypt(token) ?: run {
            Timber.w("ListenBrainzTokenStore: encryption failed, not persisting token")
            return false
        }
        return try {
            p.edit {
                putString(TOKEN_KEY, encryptedToken)
            }
            Timber.d("ListenBrainzTokenStore: stored token")
            true
        } catch (e: Exception) {
            Timber.e(e, "ListenBrainzTokenStore: failed to store token")
            false
        }
    }

    /**
     * Retrieves and decrypts the persisted token from secure storage.
     *
     * @return The decrypted token if it exists, or null otherwise.
     */
    fun retrieve(): String? {
        val encrypted = prefs?.getString(TOKEN_KEY, null)
        return decrypt(encrypted)
    }

    /**
     * Clears the stored token from secure storage.
     *
     * @return True if the token was successfully cleared, false otherwise.
     */
    fun clear(): Boolean {
        val p = prefs ?: run {
            Timber.w("ListenBrainzTokenStore: cannot clear, prefs not initialized")
            return false
        }
        return try {
            p.edit {
                remove(TOKEN_KEY)
            }
            Timber.d("ListenBrainzTokenStore: cleared")
            true
        } catch (e: Exception) {
            Timber.e(e, "ListenBrainzTokenStore: failed to clear token")
            false
        }
    }

    /**
     * Helper object that handles key generation and cipher operations using the Android KeyStore.
     */
    private object AesKeystore {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "metrolist_listenbrainz_token_key"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_IV_SIZE = 12
        private const val GCM_TAG_SIZE = 128

        private var keyStore: KeyStore? = null

        /**
         * Retrieves the Android KeyStore instance, initializing it if necessary.
         *
         * @return The [KeyStore] instance.
         */
        @Synchronized
        private fun getKeyStore(): KeyStore {
            keyStore?.let { return it }
            val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            keyStore = ks
            return ks
        }

        /**
         * Obtains or creates the secret AES key from the Android KeyStore.
         *
         * @return The [SecretKey] used for encryption and decryption.
         */
        fun getOrCreateKey(): SecretKey {
            getKeyStore().getKey(KEY_ALIAS, null)?.let { return it as SecretKey }
            val spec = KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build()
            return KeyGenerator.getInstance("AES", ANDROID_KEYSTORE).apply { init(spec) }.generateKey()
        }

        /**
         * Encrypts the plaintext string using AES/GCM/NoPadding.
         * Combines the IV and ciphertext, returning a Base64-encoded string.
         *
         * @param plaintext The plaintext to encrypt.
         * @return The Base64 encoded string containing IV and encrypted data.
         */
        fun encrypt(plaintext: String): String {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
            val iv = cipher.iv
            if (iv.size != GCM_IV_SIZE) {
                throw IllegalStateException("Unexpected IV size: ${iv.size}")
            }
            val ciphertext = cipher.doFinal(plaintext.toByteArray(StandardCharsets.UTF_8))
            val combined = ByteArray(GCM_IV_SIZE + ciphertext.size)
            iv.copyInto(combined, destinationOffset = 0, startIndex = 0, endIndex = GCM_IV_SIZE)
            ciphertext.copyInto(combined, destinationOffset = GCM_IV_SIZE)
            return Base64.encodeToString(combined, Base64.NO_WRAP)
        }

        /**
         * Decrypts the Base64 encoded ciphertext that contains a prepended IV.
         *
         * @param encrypted The Base64-encoded string containing IV and ciphertext.
         * @return The decrypted plaintext.
         */
        fun decrypt(encrypted: String): String {
            val combined = Base64.decode(encrypted, Base64.NO_WRAP)
            if (combined.size < GCM_IV_SIZE) {
                throw IllegalArgumentException("Encrypted data too short")
            }
            val iv = combined.copyOfRange(0, GCM_IV_SIZE)
            val ciphertext = combined.copyOfRange(GCM_IV_SIZE, combined.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(GCM_TAG_SIZE, iv))
            return String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8)
        }
    }
}
