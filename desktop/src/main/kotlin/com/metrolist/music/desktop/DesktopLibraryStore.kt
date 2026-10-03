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
data class DesktopLibraryData(
    val favorites: List<SearchHit> = emptyList(),
    val history: List<SearchHit> = emptyList(),
)

/**
 * Persists favorites and playback history across desktop app sessions.
 * Writes to %APPDATA%/MuSicX/library.json on Windows, or ~/.config/MuSicX/library.json elsewhere.
 */
object DesktopLibraryStore {
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
        File(baseDir, "library.json")
    }

    fun load(): DesktopLibraryData =
        runCatching {
            if (storeFile.isFile) {
                json.decodeFromString<DesktopLibraryData>(storeFile.readText())
            } else {
                DesktopLibraryData()
            }
        }.getOrElse { error ->
            DesktopLog.log("Failed to load library store", error)
            DesktopLibraryData()
        }

    fun save(data: DesktopLibraryData) {
        runCatching {
            val parent = storeFile.parentFile ?: File(".")
            parent.mkdirs()
            val temp = File(parent, "library.json.tmp")
            temp.writeText(json.encodeToString(data))
            if (storeFile.exists()) storeFile.delete()
            temp.renameTo(storeFile)
        }.onFailure { error ->
            DesktopLog.log("Failed to save library store", error)
        }
    }
}
