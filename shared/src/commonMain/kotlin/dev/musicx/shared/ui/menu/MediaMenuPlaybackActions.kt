package dev.musicx.shared.ui.menu

import dev.musicx.shared.model.AlbumItem
import dev.musicx.shared.model.ArtistItem
import dev.musicx.shared.model.SongItem
import dev.musicx.shared.model.YTItem
import dev.musicx.shared.ui.playback.PlaybackController
import kotlinx.coroutines.CancellationException

class MediaMenuPlaybackActions(
    private val item: YTItem,
    private val playback: PlaybackController,
    private val withSongs: ((List<SongItem>) -> Unit) -> Unit,
    private val resetArtistShuffle: Boolean = false,
) {
    fun play() = withSongs { songs -> play(songs.filterNot { it.isUnavailable }, shuffle = false) }

    fun shuffle() = withSongs { songs -> play(songs.filterNot { it.isUnavailable }, shuffle = true) }

    // Podcast episodes load newest first.
    fun playOldestFirst() = withSongs { songs -> play(songs.filterNot { it.isUnavailable }.asReversed(), shuffle = false) }

    fun playNext() = withSongs { songs -> songs.filterNot { it.isUnavailable }.takeIf { it.isNotEmpty() }?.let(playback::playNext) }

    fun addToQueue() = withSongs { songs -> songs.filterNot { it.isUnavailable }.takeIf { it.isNotEmpty() }?.let(playback::addToQueue) }

    private fun play(
        songs: List<SongItem>,
        shuffle: Boolean,
    ) {
        if (songs.isEmpty()) return
        val songsOnly = item is ArtistItem
        if (!shuffle && songsOnly && resetArtistShuffle && playback.state.value.isShuffled) playback.toggleShuffle()
        if (shuffle) {
            playback.shuffleQueue(songs, songsOnly = songsOnly)
        } else if (item is AlbumItem) {
            playback.playQueue(songs)
        } else {
            playback.play(songs.first(), songs, songsOnly = songsOnly)
        }
    }
}

internal suspend fun withMediaMenuSongs(
    resolveSongs: suspend () -> List<SongItem>,
    action: (List<SongItem>) -> Unit,
) {
    val songs =
        try {
            resolveSongs()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            return
        }
    if (songs.isNotEmpty()) action(songs)
}
