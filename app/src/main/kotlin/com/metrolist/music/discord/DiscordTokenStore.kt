package com.metrolist.music.discord

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import kotlinx.coroutines.CompletableDeferred
import timber.log.Timber

object DiscordTokenStore {
    private const val PREFS_NAME = "discord_token"
    private const val TOKEN_KEY = "access_token"
    private const val REFRESH_TOKEN_KEY = "refresh_token"
    private const val EXPIRES_AT_KEY = "expires_at"

    @Volatile
    private var prefs: EncryptedSharedPreferences? = null

    private val initDeferred = CompletableDeferred<Unit>()

    fun init(context: Context) {
        if (prefs != null) return
        synchronized(this) {
            if (prefs != null) return
            try {
                val masterKey = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
                prefs = EncryptedSharedPreferences.create(
                    PREFS_NAME,
                    masterKey,
                    context,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
                ) as EncryptedSharedPreferences
                initDeferred.complete(Unit)
            } catch (e: Exception) {
                Timber.e(e, "DiscordTokenStore init failed")
                initDeferred.completeExceptionally(e)
            }
        }
    }

    suspend fun retrieveSuspend(): String? {
        try {
            initDeferred.await()
        } catch (_: Exception) {
            return null
        }
        return retrieve()
    }

    fun store(token: String) {
        storeFull(token, refreshToken = "", expiresInSec = 0L)
    }

    fun storeFull(accessToken: String, refreshToken: String, expiresInSec: Long) {
        val editor = prefs?.edit() ?: return
        editor.putString(TOKEN_KEY, accessToken)
        if (refreshToken.isNotEmpty()) {
            editor.putString(REFRESH_TOKEN_KEY, refreshToken)
        }
        if (expiresInSec > 0L) {
            val expiresAt = (System.currentTimeMillis() / 1000L) + expiresInSec
            editor.putLong(EXPIRES_AT_KEY, expiresAt)
        }
        editor.apply()
    }

    fun retrieve(): String? = prefs?.getString(TOKEN_KEY, null)

    fun getRefreshToken(): String? = prefs?.getString(REFRESH_TOKEN_KEY, null)

    fun getExpiresAt(): Long = prefs?.getLong(EXPIRES_AT_KEY, 0L) ?: 0L

    fun clear() {
        prefs?.edit()
            ?.remove(TOKEN_KEY)
            ?.remove(REFRESH_TOKEN_KEY)
            ?.remove(EXPIRES_AT_KEY)
            ?.apply()
    }
}
