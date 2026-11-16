/**
 * MuSicX Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.shared.playback.queues

import com.metrolist.shared.model.SongItem
import com.metrolist.shared.playback.MusicQueue
import com.metrolist.shared.spotifymatch.SpotifyYouTubeMapper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.array
import kotlinx.serialization.json.obj
import kotlinx.serialization.json.put
import kotlinx.serialization.json.string
import meow.nyx.spotlib.Queries
import meow.nyx.spotlib.SpotifyClient
import meow.nyx.spotlib.SpotifyPriorityGate
import meow.nyx.spotlib.TrackItem

/**
 * Queue implementation that loads tracks from a Spotify album.
 *
 * Fetches album tracks from the Spotify API and progressively resolves them
 * to YouTube Music tracks. Uses [SpotifyYouTubeMapper.mapToYouTube(track)]
 * for each track resolution.
 */
class SpotifyAlbumQueue(
    private val client: SpotifyClient,
    private val albumUri: String,
    private val startIndex: Int = 0,
    private val mapper: SpotifyYouTubeMapper,
) {

    companion object {
        private const val SPOTIFY_PAGE_SIZE = 50
        private const val RESOLVE_BATCH_SIZE = 20
        private const val FAST_START_BEFORE = 0
        private const val FAST_START_AFTER = 2
    }

    // All Spotify tracks fetched so far (may span multiple API pages)
    private val allTracks = mutableListOf<TrackItem>()

    // Index into [allTracks] for the next batch to resolve to YouTube
    private var resolveOffset = 0

    // Spotify API pagination state
    private var apiFetchOffset = 0
    private var apiHasMore = true

    /** Serializes concurrent [appendNextPage] invocations. */
    private val pageMutex = Mutex()

    /**
     * Loads the album tracks and sets the queue with a fast-start window.
     *
     * @param queue the [MusicQueue] to populate
     * @param startIndex the index of the target track within the album
     */
    suspend fun loadInto(queue: MusicQueue, startIndex: Int = 0) {
        withContext(Dispatchers.IO) {
            val fetched = fetchAlbumPage(limit = SPOTIFY_PAGE_SIZE)
            allTracks.addAll(fetched)
            apiFetchOffset = fetched.size
            apiHasMore = fetched.size == SPOTIFY_PAGE_SIZE

            while (startIndex >= allTracks.size && apiHasMore) {
                fetchNextApiPage()
            }

            val targetIndex = startIndex.coerceIn(0, (allTracks.size - 1).coerceAtLeast(0))
            val windowStart = (targetIndex - FAST_START_BEFORE).coerceAtLeast(0)
            val windowEnd = (targetIndex + FAST_START_AFTER + 1).coerceAtMost(allTracks.size)
            val windowTracks = allTracks.subList(windowStart, windowEnd)

            val resolvedItems = coroutineScope {
                windowTracks.map { track -> async { mapper.mapToYouTube(track) } }
                    .awaitAll()
                    .filterNotNull()
            }

            if (resolvedItems.isEmpty()) {
                println("SpotifyAlbumQueue: Could not resolve any track in initial window")
                queue.setQueue(emptyList(), 0)
                return
            }

            resolveOffset = windowEnd
            val mediaItemIndex = (targetIndex - windowStart)
                .coerceIn(0, (resolvedItems.size - 1).coerceAtLeast(0))

            queue.setQueue(resolvedItems, mediaItemIndex)
        }
    }

    /**
     * Fetches the next batch of tracks from the Spotify API and appends them to the queue.
     *
     * @param queue the [MusicQueue] to append the batch to
     */
    suspend fun appendNextPage(queue: MusicQueue) {
        withContext(Dispatchers.IO) {
            pageMutex.withLock {
                if (resolveOffset >= allTracks.size && apiHasMore) {
                    fetchNextApiPage()
                }

                if (resolveOffset >= allTracks.size) {
                    queue.appendPage(emptyList())
                    return
                }

                val end = (resolveOffset + RESOLVE_BATCH_SIZE).coerceAtMost(allTracks.size)
                val batch = allTracks.subList(resolveOffset, end)
                resolveOffset = end

                coroutineScope {
                    batch.map { track -> async { mapper.mapToYouTube(track) } }
                        .awaitAll()
                        .filterNotNull()
                }.also { resolved ->
                    queue.appendPage(resolved)
                }
            }
        }
    }

    private suspend fun fetchAlbumPage(limit: Int): List<TrackItem> {
        return SpotifyPriorityGate.withPlaybackPriority {
            val page = client.query(
                "getAlbum",
                Queries.GET_ALBUM_HASH,
                buildJsonObject {
                    put("uri", albumUri)
                    put("offset", apiFetchOffset)
                    put("limit", SPOTIFY_PAGE_SIZE)
                },
            )
            parseAlbumTracksPage(page)
        }
    }

    private suspend fun fetchNextApiPage() {
        if (!apiHasMore) return
        try {
            val newTracks = SpotifyPriorityGate.withPlaybackPriority {
                val page = client.query(
                    "getAlbum",
                    Queries.GET_ALBUM_HASH,
                    buildJsonObject {
                        put("uri", albumUri)
                        put("offset", apiFetchOffset)
                        put("limit", SPOTIFY_PAGE_SIZE)
                    },
                )
                parseAlbumTracksPage(page)
            }
            allTracks.addAll(newTracks)
            apiFetchOffset += newTracks.size
            apiHasMore = newTracks.size == SPOTIFY_PAGE_SIZE
        } catch (e: Exception) {
            apiHasMore = false
        }
    }

    /**
     * Parses a Spotify `getAlbum` GraphQL response page into a list of [TrackItem].
     * Expects the `data.me.libraryV3.library.tracks.items[].item` structure.
     */
    private fun parseAlbumTracksPage(page: JsonObject): List<TrackItem> {
        val items = page.obj("data")
            ?.obj("me")
            ?.obj("libraryV3")
            ?.obj("library")
            ?.obj("tracks")
            ?.obj("items")
            ?.array("items") ?: return emptyList()

        return items.mapIndexedNotNull { index, item ->
            val wrapper = (item as? JsonObject)?.obj("item") ?: return@mapIndexedNotNull null
            val node = wrapper.obj("data") ?: wrapper

            val name = node.string("name").orEmpty()
            val uri = node.string("uri").orEmpty()
            if (uri.isEmpty() && name.isEmpty()) return@mapIndexedNotNull null

            val durationRaw = node["duration"]
            val durationMs = when (durationRaw) {
                is JsonObject -> durationRaw.obj("totalMilliseconds")?.jsonPrimitive?.content?.toLongOrNull()
                null -> node.obj("trackDuration")?.string("totalMilliseconds")?.toLongOrNull()
                else -> durationRaw.jsonPrimitive.content.toLongOrNull()
            }

            val artists = node.obj("artists")
                ?.array("items")
                ?.mapNotNull { it.obj("profile")?.string("name") }
                .orEmpty()

            val albumNode = node.obj("albumOfTrack")
            TrackItem(
                uri = uri,
                name = name,
                type = node.string("__typename") ?: "Track",
                artists = artists,
                album = albumNode?.string("name"),
                albumUri = albumNode?.string("uri"),
                albumArtists = albumNode?.array("items")?.mapNotNull { it.obj("profile")?.string("name") }.orEmpty(),
                releaseDate = albumNode?.obj("date")?.string("isoString"),
                durationMs = durationMs,
                trackNumber = node.obj("trackNumber")?.jsonPrimitive?.content?.toIntOrNull(),
                discNumber = node.obj("discNumber")?.jsonPrimitive?.content?.toIntOrNull(),
                explicit = node.obj("contentRating")?.string("label") == "EXPLICIT",
                isLocal = uri.startsWith("spotify:local:"),
                position = index + 1,
            )
        }
    }
}