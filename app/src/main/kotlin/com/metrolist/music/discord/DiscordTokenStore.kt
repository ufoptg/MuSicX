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
    private const val TAG = "DiscordSvc"

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
        Timber.tag(TAG).d(
            "tokenStore: stored (accessToken length=%d, refreshToken present=%s, expiresIn=%d)",
            accessToken.length,
            refreshToken.isNotEmpty(),
            expiresInSec,
        )
    }

    fun storeAccessToken(accessToken: String) {
        val editor = prefs?.edit() ?: return
        editor.putString(TOKEN_KEY, accessToken)
        editor.apply()
        Timber.tag(TAG).d("tokenStore: access token updated (length=%d)", accessToken.length)
    }

    fun retrieve(): String? {
        val token = prefs?.getString(TOKEN_KEY, null)
        Timber.tag(TAG).d("tokenStore: retrieve (found=%s)", !token.isNullOrEmpty())
        return token
    }

    fun getRefreshToken(): String? {
        val token = prefs?.getString(REFRESH_TOKEN_KEY, null)
        Timber.tag(TAG).d("tokenStore: getRefreshToken (found=%s)", !token.isNullOrEmpty())
        return token
    }

    fun getExpiresAt(): Long {
        val expiresAt = prefs?.getLong(EXPIRES_AT_KEY, 0L) ?: 0L
        Timber.tag(TAG).d("tokenStore: getExpiresAt (value=%d)", expiresAt)
        return expiresAt
    }

    fun clear() {
        prefs?.edit()
            ?.remove(TOKEN_KEY)
            ?.remove(REFRESH_TOKEN_KEY)
            ?.remove(EXPIRES_AT_KEY)
            ?.apply()
        Timber.tag(TAG).d("tokenStore: cleared")
    }
}
