package dev.musicx.android.viewmodels

import dev.musicx.shared.db.entities.CachedAudioEntity
import dev.musicx.shared.model.CollectionMeta
import dev.musicx.shared.model.CollectionSort
import dev.musicx.shared.model.PlaylistSongSort
import dev.musicx.shared.model.SongItem
import dev.musicx.shared.model.SortDirection
import dev.musicx.shared.model.filterForPlaylistSearch
import dev.musicx.shared.model.presentCollection
import dev.musicx.shared.model.sortedForLikedPlaylist
import dev.musicx.shared.model.sortedForPlaylist
import dev.musicx.shared.model.sortedForTopPlaylist
import dev.musicx.shared.viewmodels.DownloadPlaylistFilterState
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal data class AutoPlaylistPresentationInput(
    val type: String,
    val songs: List<SongItem>,
    val metadataRevision: Long,
    val collectionEntries: List<CachedAudioEntity>,
    val query: String,
    val collectionSort: CollectionSort,
    val collectionDirection: SortDirection,
    val playCounts: Map<String, Int>,
    val playlistSort: PlaylistSongSort,
    val playlistDirection: SortDirection,
    val topDirection: SortDirection,
    val reorder: Boolean,
    val likedSongRecency: List<String> = emptyList(),
    val downloadFilter: DownloadPlaylistFilterState = DownloadPlaylistFilterState(),
)

internal suspend fun prepareAutoPlaylistSongs(
    input: AutoPlaylistPresentationInput,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
): List<SongItem> =
    withContext(dispatcher) {
        with(input) {
            when (type) {
                "downloaded", "cached" -> {
                    val entries = collectionEntries.associateBy(CachedAudioEntity::songId)
                    val filtered = if (type == "downloaded") downloadFilter.apply(songs) else songs
                    filtered.presentCollection(query, collectionSort, collectionDirection) { song ->
                        entries[song.id]?.let { entry ->
                            CollectionMeta(
                                timestamp = entry.cachedAt,
                                fileSize = entry.fileSize,
                                lastAccessed = entry.lastAccessedAt,
                                playCount = playCounts[song.id] ?: 0,
                            )
                        }
                    }
                }

                "top100" -> {
                    songs.sortedForTopPlaylist(topDirection).filterForPlaylistSearch(query)
                }

                "liked" -> {
                    val ordered = if (reorder) songs else songs.sortedForLikedPlaylist(playlistSort, playlistDirection, likedSongRecency)
                    ordered.filterForPlaylistSearch(query)
                }

                "uploaded" -> {
                    songs.sortedForPlaylist(playlistSort, playlistDirection).filterForPlaylistSearch(query)
                }

                else -> {
                    songs.filterForPlaylistSearch(query)
                }
            }
        }
    }
