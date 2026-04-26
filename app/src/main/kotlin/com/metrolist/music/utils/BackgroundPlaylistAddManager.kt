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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Tracks the current state of the background playlist add flow.
 */
data class PlaylistAddProgressState(
    val playlistName: String = "",
    val total: Int = 0,
    val completed: Int = 0,
    val failed: Int = 0,
    val isRunning: Boolean = false,
    val isVisible: Boolean = false,
    val isCancelling: Boolean = false,
) {
    val progress: Float
        get() = if (total == 0) 0f else (completed + failed).toFloat() / total
}

/**
 * Runs playlist add requests in the background and exposes progress state to the UI.
 */
object BackgroundPlaylistAddManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val jobMutex = Mutex()
    private val _state = MutableStateFlow(PlaylistAddProgressState())
    private var currentJob: Job? = null

    /**
     * Shared progress state for the current playlist add operation.
     */
    val state: StateFlow<PlaylistAddProgressState> = _state.asStateFlow()

    /**
     * Starts adding the provided songs to the given playlist in the background.
     */
    fun start(
        database: MusicDatabase,
        syncUtils: SyncUtils,
        playlist: Playlist,
        songIds: List<String>,
    ) {
        scope.launch {
            jobMutex.withLock {
                currentJob?.cancelAndJoin()
                _state.value = PlaylistAddProgressState(
                    playlistName = playlist.title,
                    total = songIds.size,
                    isRunning = true,
                    isVisible = true,
                )
                currentJob = scope.launch {
                    runAdd(database, syncUtils, playlist, songIds)
                }
            }
        }
    }

    /**
     * Hides the progress dialog without cancelling the running operation.
     */
    fun hide() {
        _state.value = _state.value.copy(isVisible = false)
    }

    /**
     * Cancels the active playlist add operation.
     */
    fun cancel() {
        _state.value = _state.value.copy(isCancelling = true)
        currentJob?.cancel()
    }

    /**
     * Clears the dialog state after the operation has finished.
     */
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
            if (songIds.isEmpty()) {
                _state.value = _state.value.copy(isVisible = false)
                return
            }

            database.addSongsToPlaylist(playlist, songIds.map { it to null }, prepend = true)
            val browseId = playlist.playlist.browseId
            if (browseId == null) {
                _state.value = _state.value.copy(completed = songIds.size)
                _state.value = _state.value.copy(isVisible = false)
                return
            }

            songIds.forEach { songId ->
                currentCoroutineContext().ensureActive()
                syncUtils.registerPendingAdd(browseId, songId)
                try {
                    val result = YouTube.addToPlaylist(browseId, songId)
                    if (result.isSuccess) {
                        _state.update { it.copy(completed = it.completed + 1) }
                    } else {
                        _state.update { it.copy(failed = it.failed + 1) }
                        Timber.e(result.exceptionOrNull(), "Failed to add song %s to playlist", songId)
                    }
                } finally {
                    syncUtils.unregisterPendingAdd(browseId, songId)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Failed to add songs to playlist")
        } finally {
            val currentState = _state.value
            val completedSuccessfully = currentState.failed == 0
            _state.value = _state.value.copy(
                isRunning = false,
                isCancelling = false,
                isVisible = if (completedSuccessfully) false else currentState.isVisible,
            )
        }
    }
}
