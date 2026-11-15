package dev.musicx.android.viewmodels

import dev.musicx.shared.api.PlaylistSongAlreadyPresentException
import dev.musicx.shared.db.MusicDatabase
import dev.musicx.shared.db.entities.Playlist
import dev.musicx.shared.model.SongItem
import dev.musicx.shared.playlist.addSongToStoredPlaylist
import dev.musicx.shared.state.GlobalYouTubeRepository
import java.util.Collections

internal enum class AddSongToPlaylistResult {
    Added,
    AlreadyPresent,
    InProgress,
}

internal suspend fun addSongToPlaylist(
    database: MusicDatabase,
    playlist: Playlist,
    song: SongItem,
    allowDuplicate: Boolean = false,
): AddSongToPlaylistResult =
    playlistAddOperations.run(playlist.id, song.id) {
        val result =
            addSongToStoredPlaylist(
                database,
                GlobalYouTubeRepository.instance,
                playlist.id,
                song,
                allowDuplicate,
            )
        val error = result.exceptionOrNull()
        if (error is PlaylistSongAlreadyPresentException) return@run AddSongToPlaylistResult.AlreadyPresent
        if (error != null) throw error
        AddSongToPlaylistResult.Added
    }

private val playlistAddOperations = PlaylistAddOperations()

internal class PlaylistAddOperations {
    private val active = Collections.synchronizedSet(mutableSetOf<Pair<String, String>>())

    suspend fun run(
        playlistId: String,
        songId: String,
        add: suspend () -> AddSongToPlaylistResult,
    ): AddSongToPlaylistResult {
        val key = playlistId to songId
        if (!active.add(key)) return AddSongToPlaylistResult.InProgress
        try {
            return add()
        } finally {
            active.remove(key)
        }
    }
}
