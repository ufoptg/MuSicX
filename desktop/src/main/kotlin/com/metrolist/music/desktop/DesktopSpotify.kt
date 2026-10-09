/**
 * MuSicX Project (C) 2026
 * Credits to Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.desktop

import com.metrolist.spotify.Spotify
import com.metrolist.spotify.SpotifyAuth
import com.metrolist.spotify.models.SpotifySearchResult
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Desktop-only Spotify session helper. Mirrors Android [SpotifyTokenManager]
 * patterns without touching app/ code: persists sp_dc via [DesktopPrefs],
 * caches access tokens, refreshes via [SpotifyAuth.fetchAccessToken].
 */
object DesktopSpotify {
    private val refreshMutex = Mutex()

    fun isLoggedIn(prefs: DesktopPrefs): Boolean =
        prefs.spDc.isNotEmpty() && prefs.spotifyAccessToken.isNotEmpty()

    /** Android: enableSpotify && useSpotifyHome && spDc.isNotEmpty() */
    fun spotifyHomeActive(prefs: DesktopPrefs): Boolean =
        prefs.enableSpotify && prefs.useSpotifyHome && prefs.spDc.isNotEmpty()

    /** Android: spotifyHomeActive && spotifyHomeOnly — hide YouTube home shelves. */
    fun hideYoutubeHome(prefs: DesktopPrefs): Boolean =
        spotifyHomeActive(prefs) && prefs.spotifyHomeOnly

    suspend fun search(
        query: String,
        prefs: DesktopPrefs,
        onUpdated: (DesktopPrefs) -> Unit,
    ): Result<SpotifySearchResult> {
        if (!ensureAuthenticated(prefs, onUpdated)) {
            return Result.failure(IllegalStateException("Not signed in to Spotify"))
        }
        return Spotify.search(query, types = listOf("track", "album", "playlist"), limit = 8)
    }

    suspend fun completeLogin(        spDc: String,
        spKey: String,
        prefs: DesktopPrefs,
    ): Result<DesktopPrefs> =
        SpotifyAuth.fetchAccessToken(spDc, spKey).map { token ->
            Spotify.accessToken = token.accessToken
            prefs.copy(
                spDc = spDc,
                spKey = spKey,
                spotifyAccessToken = token.accessToken,
                spotifyTokenExpiry = token.accessTokenExpirationTimestampMs,
            )
        }

    fun signOut(prefs: DesktopPrefs): DesktopPrefs {
        Spotify.accessToken = null
        clearSpotifyCefCookies()
        return prefs.copy(
            spDc = "",
            spKey = "",
            spotifyAccessToken = "",
            spotifyTokenExpiry = 0L,
            enableSpotify = false,
            useSpotifyHome = false,
            spotifyHomeOnly = false,
        )
    }

    /**
     * Ensures [Spotify.accessToken] is valid. Refreshes via sp_dc when expired.
     * @return true if a usable token is set
     */
    suspend fun ensureAuthenticated(
        prefs: DesktopPrefs,
        onUpdated: (DesktopPrefs) -> Unit,
    ): Boolean {
        val accessToken = prefs.spotifyAccessToken
        val expiry = prefs.spotifyTokenExpiry

        if (accessToken.isEmpty()) {
            if (prefs.spDc.isEmpty()) return false
            return refresh(prefs, onUpdated)
        }

        if (System.currentTimeMillis() < expiry) {
            Spotify.accessToken = accessToken
            return true
        }

        return refresh(prefs, onUpdated)
    }

    private suspend fun refresh(
        prefs: DesktopPrefs,
        onUpdated: (DesktopPrefs) -> Unit,
    ): Boolean =
        refreshMutex.withLock {
            val fresh = DesktopPrefsStore.load()
            if (fresh.spotifyAccessToken.isNotEmpty() &&
                System.currentTimeMillis() < fresh.spotifyTokenExpiry
            ) {
                Spotify.accessToken = fresh.spotifyAccessToken
                return@withLock true
            }
            val spDc = fresh.spDc
            if (spDc.isEmpty()) return@withLock false

            SpotifyAuth.fetchAccessToken(spDc, fresh.spKey).fold(
                onSuccess = { token ->
                    Spotify.accessToken = token.accessToken
                    val updated =
                        fresh.copy(
                            spotifyAccessToken = token.accessToken,
                            spotifyTokenExpiry = token.accessTokenExpirationTimestampMs,
                        )
                    DesktopPrefsStore.save(updated)
                    onUpdated(updated)
                    true
                },
                onFailure = { e ->
                    DesktopLog.log("DesktopSpotify: token refresh failed", e)
                    val expired =
                        e.message?.contains("anonymous") == true ||
                            e.message?.contains("expired") == true
                    if (expired) {
                        val cleared = signOut(fresh)
                        DesktopPrefsStore.save(cleared)
                        onUpdated(cleared)
                    }
                    false
                },
            )
        }
}
