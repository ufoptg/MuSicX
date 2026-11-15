package dev.musicx.shared.playback

import dev.musicx.shared.spotify.SpotifyYouTubeMapper
import meow.nyx.spotlib.TrackItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Base class for Spotify-backed queues that resolve Spotify tracks
 * to YouTube Music items via fuzzy matching.
 *
 * Subclasses define which tracks are included; this base class handles
 * resolution to SongItems through [SpotifyYouTubeMapper].
 */
abstract class SpotifyQueue(
    private val mapper: SpotifyYouTubeMapper,
) {

    /**
     * Returns the full list of Spotify tracks in queue order.
     * Subclasses must implement this to define what tracks are included.
     */
    abstract suspend fun loadTracks(): List<TrackItem>

    /**
     * Resolves all tracks to SongItems via YouTube matching.
     * Failed matches are silently dropped from the queue.
     */
    suspend fun resolveSongItems() =
        withContext(Dispatchers.IO) {
            loadTracks().mapNotNull { track -> mapper.resolveToYouTubeItem(track) }
        }

    /**
     * Resolves only [count] tracks starting from [startIndex]. Useful for
     * paginated or lazy loading of large queues.
     */
    suspend fun resolveSongItemsRange(
        startIndex: Int,
        count: Int,
    ) = withContext(Dispatchers.IO) {
        loadTracks().drop(startIndex).take(count)
            .mapNotNull { track -> mapper.resolveToYouTubeItem(track) }
    }
}
