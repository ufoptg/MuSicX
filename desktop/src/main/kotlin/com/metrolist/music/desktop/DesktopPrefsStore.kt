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
    val pureBlack: Boolean = false,
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
