package dev.musicx.desktop.state.content

import dev.musicx.shared.db.entities.PlaylistEntity
import kotlinx.coroutines.Job

internal fun hasCompleteStoredPlaylist(
    playlist: PlaylistEntity,
    songCount: Int,
): Boolean = playlist.isLocal || playlist.browseId == null || playlist.remoteSongCount?.let { songCount >= it } == true

internal suspend fun awaitCompletePlaylist(
    loadJob: Job?,
    hasMore: () -> Boolean,
): Boolean {
    if (hasMore()) loadJob?.join()
    return !hasMore()
}
