/**
 * MuSicX Project (C) 2026
 * Credits to Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.desktop

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class DesktopPrefs(
    /** "AUTO" (follow system), "ON" (dark), or "OFF" (light). */
    val darkMode: String = "AUTO",
    /** Pure black dark mode: replaces surface/background with black */
    val pureBlack: Boolean = false,
    /** Dynamic theme: follows system theme if "AUTO", otherwise "ON"/"OFF" */
    val dynamicTheme: String = "AUTO",
    /** Selected theme color ARGB value (for coral/seed color) */
    val selectedThemeColor: Long = 0xFFED5564L,
    /** Content language/country code */
    val contentLanguage: String = "en",
    /** AI provider/key settings */
    val aiProvider: String = "",
    val aiKey: String = "",
    /** Player quality/loudness/crossfade/gapless settings */
    val playerQuality: Int = 1,
    val playerLoudness: Boolean = false,
    val playerCrossfade: Boolean = false,
    val playerGapless: Boolean = false,
    /** Storage settings */
    val downloadDir: String = "%APPDATA%/MuSicX/Downloads",
    val cacheSizeCap: Int = 1024,  // in MB
    /** Privacy settings */
    val pauseHistory: Boolean = false,
    val clearCacheOnExit: Boolean = false,
    /** Spotify settings */
    /** Spotify integration enabled */
    val enableSpotify: Boolean = false,
    /** Spotify sp_dc cookie value (persisted after embedded-browser login) */
    val spDc: String = "",
    /** Use Spotify as home source */
    val useSpotifyHome: Boolean = false,
    /** Show only Spotify home, hide YouTube home */
    val spotifyHomeOnly: Boolean = false,
    /** Discord rich presence token */
    val discordToken: String = "",
    /** Last.fm scrobbling token */
    val lastFmToken: String = "",
    /** SponsorBlock enabled */
    val sponsorblockEnabled: Boolean = false,
    /** Equalizer enabled */
    val equalizerEnabled: Boolean = false,
    /** Equalizer active profile name */
    val equalizerProfile: String = "",
)

/**
 * Persists appearance preferences across desktop app sessions.
 * Writes to %APPDATA%/MuSicX/prefs.json on Windows, or ~/.config/MuSicX/prefs.json elsewhere.
 */
object DesktopPrefsStore {
    private val json =
        Json {
            prettyPrint = true
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

    private val storeFile: File by lazy {
        val appData = System.getenv("APPDATA")
        val baseDir =
            if (!appData.isNullOrBlank()) {
                File(appData, "MuSicX")
            } else {
                File(System.getProperty("user.home") ?: ".", ".config/MuSicX")
            }
        baseDir.mkdirs()
        File(baseDir, "prefs.json")
    }

    fun load(): DesktopPrefs =
        runCatching {
            if (storeFile.isFile) {
                json.decodeFromString<DesktopPrefs>(storeFile.readText())
            } else {
                DesktopPrefs()
            }
        }.getOrElse { error ->
            DesktopLog.log("Failed to load prefs store", error)
            DesktopPrefs()
        }

    fun save(data: DesktopPrefs) {
        runCatching {
            val parent = storeFile.parentFile ?: File(".")
            parent.mkdirs()
            val temp = File(parent, "prefs.json.tmp")
            temp.writeText(json.encodeToString(data))
            if (storeFile.exists()) storeFile.delete()
            temp.renameTo(storeFile)
        }.onFailure { error ->
            DesktopLog.log("Failed to save prefs store", error)
        }
    }
}
