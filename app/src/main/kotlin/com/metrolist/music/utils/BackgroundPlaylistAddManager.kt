/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.utils

import com.metrolist.innertube.YouTube
import com.metrolist.music.db.MusicDatabase
import com.metrolist.music.db.entities.Playlist
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

data class PlaylistAddProgressState(
    val playlistName: String = "",
    val total: Int = 0,
    val completed: Int = 0,
    val isRunning: Boolean = false,
    val isVisible: Boolean = false,
    val isCancelling: Boolean = false,
) {
    val progress: Float
        get() = if (total == 0) 0f else completed.toFloat() / total
}

object BackgroundPlaylistAddManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow(PlaylistAddProgressState())
    private var currentJob: Job? = null

    val state: StateFlow<PlaylistAddProgressState> = _state.asStateFlow()

    fun start(
        database: MusicDatabase,
        syncUtils: SyncUtils,
        playlist: Playlist,
        songIds: List<String>,
    ) {
        scope.launch {
            currentJob?.cancelAndJoin()
            _state.value = PlaylistAddProgressState(
                playlistName = playlist.title,
                total = songIds.size,
                isRunning = true,
                isVisible = true,
            )
            currentJob = launch {
                runAdd(database, syncUtils, playlist, songIds)
            }
        }
    }

    fun hide() {
        _state.value = _state.value.copy(isVisible = false)
    }

    fun cancel() {
        _state.value = _state.value.copy(isCancelling = true)
        currentJob?.cancel()
    }

    fun dismissFinished() {
        if (!_state.value.isRunning) {
            _state.value = PlaylistAddProgressState()
        }
    }

    private suspend fun runAdd(
        database: MusicDatabase,
        syncUtils: SyncUtils,
        playlist: Playlist,
        songIds: List<String>,
    ) {
        try {
            if (songIds.isEmpty()) return

            database.addSongsToPlaylist(playlist, songIds.map { it to null }, prepend = true)
            val browseId = playlist.playlist.browseId
            if (browseId == null) {
                _state.value = _state.value.copy(completed = songIds.size)
                return
            }

            songIds.forEach { songId ->
                currentCoroutineContext().ensureActive()
                syncUtils.registerPendingAdd(browseId, songId)
                try {
                    YouTube.addToPlaylist(browseId, songId)
                } finally {
                    syncUtils.unregisterPendingAdd(browseId, songId)
                }
                _state.value = _state.value.copy(completed = _state.value.completed + 1)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Failed to add songs to playlist")
        } finally {
            _state.value = _state.value.copy(isRunning = false, isCancelling = false)
        }
    }
}
