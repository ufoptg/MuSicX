package dev.musicx.desktop.storage

import dev.musicx.shared.db.MusicDatabase
import dev.musicx.shared.db.entities.toSongItem
import dev.musicx.shared.model.SongItem
import dev.musicx.shared.model.isPodcast
import dev.musicx.shared.utils.Logger
import kotlinx.coroutines.CancellationException

internal suspend fun storedAudioReportingSong(
    song: SongItem?,
    database: MusicDatabase?,
): SongItem? {
    if (song == null || song.isLocal || song.isUploaded || song.isPodcast || song.id.startsWith("local_")) return song
    return try {
        database?.dao()?.getSongById(song.id)?.toSongItem() ?: song
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        Logger.w("StoredAudioHistory", "Could not read playback reporting metadata", error)
        null
    }
}
