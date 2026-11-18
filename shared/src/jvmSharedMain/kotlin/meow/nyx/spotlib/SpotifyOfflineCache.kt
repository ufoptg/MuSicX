package meow.nyx.spotlib

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.text.Regex
import kotlin.time.Duration.Companion.days

/**
 * File-backed offline cache for Spotify API responses.
 *
 * Caches home feed, library playlists, and playlist tracks.
 * On API failure, returns cached data if available and not stale (> 7 days).
 * Cache is bounded to prevent unbounded growth.
 */
object SpotifyOfflineCache {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val cacheDir = File(System.getProperty("java.io.tmpdir"), "metrolist/spotify-cache")
    private const val MAX_CACHE_ENTRIES = 20
    private val staleAfterMs = 7.days.inWholeMilliseconds

    init {
        cacheDir.mkdirs()
    }

    @Serializable
    private data class CacheEnvelope<T>(
        val data: T,
        val timestampMs: Long = System.currentTimeMillis(),
    )

    fun getCachedHomeFeed(): SpotifyHomeFeed? =
        runCatching {
            val file = File(cacheDir, "home_feed.json")
            if (!file.isFile) return@runCatching null
            val envelope = json.decodeFromString<CacheEnvelope<SpotifyHomeFeed>>(file.readText())
            if (System.currentTimeMillis() - envelope.timestampMs > staleAfterMs) return@runCatching null
            envelope.data
        }.getOrNull()

    fun cacheHomeFeed(feed: SpotifyHomeFeed) {
        runCatching {
            File(cacheDir, "home_feed.json").writeText(json.encodeToString(CacheEnvelope(feed)))
        }
    }

    fun getCachedPlaylists(): List<PlaylistMeta>? =
        runCatching {
            val file = File(cacheDir, "playlists.json")
            if (!file.isFile) return@runCatching null
            val envelope = json.decodeFromString<CacheEnvelope<List<PlaylistMeta>>>(file.readText())
            if (System.currentTimeMillis() - envelope.timestampMs > staleAfterMs) return@runCatching null
            envelope.data
        }.getOrNull()

    fun cachePlaylists(playlists: List<PlaylistMeta>) {
        runCatching {
            File(cacheDir, "playlists.json").writeText(json.encodeToString(CacheEnvelope(playlists)))
        }
    }

    fun getCachedPlaylistTracks(uri: String): List<TrackItem>? {
        val safeUri = uri.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        return runCatching {
            val file = File(cacheDir, "playlist_$safeUri.json")
            if (!file.isFile) return@runCatching null
            val envelope = json.decodeFromString<CacheEnvelope<List<TrackItem>>>(file.readText())
            if (System.currentTimeMillis() - envelope.timestampMs > staleAfterMs) return@runCatching null
            envelope.data
        }.getOrNull()
    }

    fun cachePlaylistTracks(uri: String, tracks: List<TrackItem>) {
        runCatching {
            val safeUri = uri.replace(Regex("[^a-zA-Z0-9_-]"), "_")
            File(cacheDir, "playlist_$safeUri.json").writeText(json.encodeToString(CacheEnvelope(tracks)))
            pruneCache()
        }
    }

    private fun pruneCache() {
        runCatching {
            val files =
                cacheDir
                    .listFiles { it.name.startsWith("playlist_") && it.name.endsWith(".json") }
                    ?.sortedBy { it.lastModified() }
                    ?: return
            if (files.size > MAX_CACHE_ENTRIES) {
                files.take(files.size - MAX_CACHE_ENTRIES).forEach { it.delete() }
            }
        }
    }

    fun clearAll() {
        runCatching { cacheDir.listFiles()?.forEach { it.delete() } }
    }
}
