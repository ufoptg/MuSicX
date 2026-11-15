package dev.musicx.shared.spotify

import dev.musicx.shared.model.SongItem

/**
 * Registry that caches Spotify→YouTube match results.
 *
 * This is a lightweight cache that lives for the duration of the application.
 * It provides fast reverse lookup (YouTube song ID → Spotify TrackItem) and
 * supports manual overrides.
 */
object SpotifyMetadataRegistry {

    private val cache = java.util.concurrent.ConcurrentHashMap<String, SongItem?>()

    /**
     * Registers a YouTube song ID with its corresponding Spotify track item.
     *
     * @param youtubeId the YouTube song ID (used as the key)
     * @param track the Spotify track item to cache
     */
    fun register(youtubeId: String, track: SongItem?) {
        cache[youtubeId] = track
    }

    /**
     * Gets the cached Spotify track item for a YouTube song ID.
     *
     * @param youtubeId the YouTube song ID
     * @return the cached Spotify track item, or null if not found
     */
    fun get(youtubeId: String): SongItem? {
        return cache[youtubeId]
    }

    /**
     * Invalidates a cached entry, removing it from the registry.
     *
     * @param youtubeId the YouTube song ID to remove
     */
    fun invalidate(youtubeId: String) {
        cache.remove(youtubeId)
    }

    /**
     * Clears the entire registry.
     */
    fun clearAll() {
        cache.clear()
    }

    /**
     * Checks if a YouTube song ID has a cached Spotify track item.
     *
     * @param youtubeId the YouTube song ID
     * @return true if present, false otherwise
     */
    fun contains(youtubeId: String): Boolean {
        return cache.containsKey(youtubeId)
    }

    /**
     * Returns the size of the cache.
     */
    fun size(): Int {
        return cache.size
    }
}