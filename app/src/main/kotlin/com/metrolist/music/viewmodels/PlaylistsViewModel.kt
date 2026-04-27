/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

@file:OptIn(ExperimentalCoroutinesApi::class)

package com.metrolist.music.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.metrolist.music.constants.AddToPlaylistSortDescendingKey
import com.metrolist.music.constants.AddToPlaylistSortTypeKey
import com.metrolist.music.constants.PlaylistSortType
import com.metrolist.music.db.MusicDatabase
import com.metrolist.music.db.entities.Playlist
import com.metrolist.music.extensions.toEnum
import com.metrolist.innertube.YouTube
import com.metrolist.music.utils.SyncUtils
import com.metrolist.music.utils.dataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class PlaylistsViewModel
@Inject
constructor(
    @ApplicationContext context: Context,
    private val database: MusicDatabase,
    private val syncUtils: SyncUtils,
) : ViewModel() {
    val allPlaylists =
        context.dataStore.data
            .map {
                it[AddToPlaylistSortTypeKey].toEnum(PlaylistSortType.CREATE_DATE) to (it[AddToPlaylistSortDescendingKey]
                    ?: true)
            }.distinctUntilChanged()
            .flatMapLatest { (sortType, descending) ->
                database.playlists(sortType, descending)
            }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // Suspend function that waits for sync to complete
    suspend fun sync() {
        syncUtils.syncSavedPlaylists()
    }

    fun addSongsAndSync(
        targetPlaylist: Playlist,
        ids: List<String>,
        multiSelectParams: String? = null,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val localIds = ids.distinct()
            database.addSongsToPlaylist(targetPlaylist, localIds.map { it to null }, prepend = true)
            val browseId = targetPlaylist.playlist.browseId ?: return@launch
            val remoteIds =
                if (localIds.size > 1 && multiSelectParams != null) {
                    YouTube.getMultiSelectCommand(localIds, multiSelectParams)
                        .getOrNull()
                        ?.multiSelectCommand
                        ?.addToPlaylistEndpoint
                        ?.videoIds
                        .orEmpty()
                        .ifEmpty { localIds }
                } else {
                    localIds
                }

            remoteIds.forEach { songId ->
                syncUtils.registerPendingAdd(browseId, songId)
            }
            try {
                if (remoteIds.size == 1) {
                    YouTube.addToPlaylist(browseId, remoteIds.first())
                } else {
                    YouTube.addToPlaylist(browseId, remoteIds)
                }
            } finally {
                remoteIds.forEach { songId ->
                    syncUtils.unregisterPendingAdd(browseId, songId)
                }
            }
        }
    }
}
