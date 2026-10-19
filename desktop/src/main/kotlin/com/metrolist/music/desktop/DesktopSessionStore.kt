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
data class DesktopSessionData(
    val cookie: String? = null,
)

/**
 * Persists the YouTube Music session cookie across desktop app sessions.
 * Writes to %APPDATA%/MuSicX/session.json on Windows, or ~/.config/MuSicX/session.json elsewhere.
 */
object DesktopSessionStore {
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
        File(baseDir, "session.json")
    }

    fun load(): DesktopSessionData =
        runCatching {
            if (storeFile.isFile) {
                json.decodeFromString<DesktopSessionData>(storeFile.readText())
            } else {
                DesktopSessionData()
            }
        }.getOrElse { error ->
            DesktopLog.log("Failed to load session store", error)
            DesktopSessionData()
        }

    fun save(data: DesktopSessionData) {
        runCatching {
            val parent = storeFile.parentFile ?: File(".")
            parent.mkdirs()
            val temp = File(parent, "session.json.tmp")
            temp.writeText(json.encodeToString(data))
            if (storeFile.exists()) storeFile.delete()
            temp.renameTo(storeFile)
        }.onFailure { error ->
            DesktopLog.log("Failed to save session store", error)
        }
    }

    fun clear() {
        runCatching { if (storeFile.exists()) storeFile.delete() }
            .onFailure { error -> DesktopLog.log("Failed to clear session store", error) }
    }
}
