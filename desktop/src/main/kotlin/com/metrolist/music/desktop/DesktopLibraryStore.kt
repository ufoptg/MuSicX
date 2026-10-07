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

/** A song saved for offline playback. */
@Serializable
data class DownloadInfo(
    val videoId: String,
    val title: String,
    val artist: String? = null,
    val thumbnailUrl: String? = null,
    val durationSec: Long = 0,
    val filePath: String,
    val sizeBytes: Long = 0,
) {
    fun toHit(): SearchHit = SearchHit(videoId = videoId, title = title, subtitle = artist, thumbnailUrl = thumbnailUrl)
}

@Serializable
data class DesktopLibraryData(
    val favorites: List<SearchHit> = emptyList(),
    val history: List<SearchHit> = emptyList(),
    val downloads: List<DownloadInfo> = emptyList(),
)

/** %APPDATA%/MuSicX on Windows, ~/.config/MuSicX elsewhere. */
internal fun musicxDataDir(): File {
    val appData = System.getenv("APPDATA")
    val dir =
        if (!appData.isNullOrBlank()) {
            File(appData, "MuSicX")
        } else {
            File(System.getProperty("user.home") ?: ".", ".config/MuSicX")
        }
    dir.mkdirs()
    return dir
}

/**
 * Persists favorites, playback history and the downloads index across desktop app sessions.
 * Writes to <musicxDataDir>/library.json.
 */
object DesktopLibraryStore {
    private val json =
        Json {
            prettyPrint = true
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

    private val storeFile: File by lazy { File(musicxDataDir(), "library.json") }

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
