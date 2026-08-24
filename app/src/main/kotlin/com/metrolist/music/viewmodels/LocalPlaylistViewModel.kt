/**
 * MuSicX Project (C) 2026
 * Credits to Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.viewmodels

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.Album
import com.metrolist.innertube.models.Artist
import com.metrolist.innertube.models.PlaylistItem
import com.metrolist.innertube.models.SongItem
import com.metrolist.music.R
import com.metrolist.music.constants.HideVideoSongsKey
import com.metrolist.music.constants.PlaylistSongSortDescendingKey
import com.metrolist.music.constants.PlaylistSongSortType
import com.metrolist.music.constants.PlaylistSongSortTypeKey
import com.metrolist.music.db.MusicDatabase
import com.metrolist.music.db.entities.PlaylistSong
import com.metrolist.music.extensions.reversed
import com.metrolist.music.extensions.toEnum
import com.metrolist.music.models.toMediaMetadata
import com.metrolist.music.playback.YouTubeRecommendationEngine
import com.metrolist.music.utils.dataStore
import com.metrolist.music.utils.get
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.text.Collator
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class LocalPlaylistViewModel
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val database: MusicDatabase,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    val playlistId = savedStateHandle.get<String>("playlistId")!!
    val playlist =
        database
            .playlist(playlistId)
            .stateIn(viewModelScope, SharingStarted.Lazily, null)

    private val _enhanceTracks = MutableStateFlow<List<SongItem>>(emptyList())
    val enhanceTracks = _enhanceTracks.asStateFlow()

    private val _isEnhanceLoading = MutableStateFlow(false)
    val isEnhanceLoading = _isEnhanceLoading.asStateFlow()

    private val _enhanceError = MutableStateFlow<String?>(null)
    val enhanceError = _enhanceError.asStateFlow()

    fun clearEnhance() {
        _enhanceTracks.value = emptyList()
        _enhanceError.value = null
    }

    fun buildEnhance() {
        val current = playlistSongs.value
        if (current.isEmpty() || _isEnhanceLoading.value) return
        _isEnhanceLoading.value = true
        _enhanceError.value = null
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val hideVideoSongs = context.dataStore.get(HideVideoSongsKey, false)
                val seeds = current.map { it.toSongItem() }
                val recs = YouTubeRecommendationEngine.getRecommendationsForPlaylist(
                    playlistSongs = seeds,
                    limit = 20,
                    seedCount = 4,
                    hideVideoSongs = hideVideoSongs,
                )
                _enhanceTracks.value = recs
                if (recs.isEmpty()) {
                    _enhanceError.value = context.getString(R.string.enhance_no_results)
                }
            } catch (e: Exception) {
                Timber.e(e, "Enhance failed")
                _enhanceError.value = e.message ?: "Enhance failed"
            } finally {
                _isEnhanceLoading.value = false
            }
        }
    }

    fun addEnhanceTrackToPlaylist(song: SongItem) {
        viewModelScope.launch(Dispatchers.IO) {
            val pl = playlist.value ?: return@launch
            database.transaction {
                insert(song.toMediaMetadata())
                addSongsToPlaylist(pl, listOf(song.id to song.setVideoId))
            }
            pl.playlist.browseId?.let { browseId ->
                YouTube.addToPlaylist(browseId, song.id)
            }
        }
    }

    private fun PlaylistSong.toSongItem(): SongItem =
        SongItem(
            id = song.id,
            title = song.song.title,
            artists = song.artists.map { Artist(name = it.name, id = it.id) },
            album = song.album?.let { Album(name = it.title, id = it.id) },
            duration = song.song.duration.takeIf { it > 0 },
            thumbnail = song.song.thumbnailUrl.orEmpty(),
            explicit = song.song.explicit,
            setVideoId = map.setVideoId,
        )

    private val _onlinePlaylist = MutableStateFlow<PlaylistItem?>(null)
    val onlinePlaylist: StateFlow<PlaylistItem?> = _onlinePlaylist
    val playlistSongs: StateFlow<List<PlaylistSong>> =
        combine(
            database.playlistSongs(playlistId),
            context.dataStore.data
                .map {
                    Triple(
                        it[PlaylistSongSortTypeKey].toEnum(PlaylistSongSortType.CUSTOM),
                        it[PlaylistSongSortDescendingKey] ?: true,
                        it[HideVideoSongsKey] ?: false
                    )
                }.distinctUntilChanged(),
        ) { songs, (sortType, sortDescending, hideVideoSongs) ->
            val filteredSongs = if (hideVideoSongs) {
                songs.filter { !it.song.song.isVideo }
            } else {
                songs
            }
            when (sortType) {
                PlaylistSongSortType.CUSTOM -> filteredSongs
                PlaylistSongSortType.CREATE_DATE -> filteredSongs.sortedBy { it.map.id }
                PlaylistSongSortType.NAME -> {
                    val collator = Collator.getInstance(Locale.getDefault())
                    collator.strength = Collator.PRIMARY
                    filteredSongs.sortedWith(compareBy(collator) { it.song.song.title })
                }
                PlaylistSongSortType.ARTIST -> {
                    val collator = Collator.getInstance(Locale.getDefault())
                    collator.strength = Collator.PRIMARY
                    filteredSongs
                        .sortedWith(compareBy(collator) { song -> song.song.artists.joinToString("") { it.name } })
                        .groupBy { it.song.album?.title }
                        .flatMap { (_, songsByAlbum) ->
                            songsByAlbum.sortedBy {
                                it.song.artists.joinToString(
                                    ""
                                ) { it.name }
                            }
                        }
                }

                PlaylistSongSortType.PLAY_TIME -> filteredSongs.sortedBy { it.song.song.totalPlayTime }
            }.reversed(sortDescending && sortType != PlaylistSongSortType.CUSTOM)
        }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    init {
        viewModelScope.launch {
            val sortedSongs =
                playlistSongs.first().sortedWith(compareBy({ it.map.position }, { it.map.id }))
            database.transaction {
                sortedSongs.forEachIndexed { index, playlistSong ->
                    if (playlistSong.map.position != index) {
                        update(playlistSong.map.copy(position = index))
                    }
                }
            }
        }

        viewModelScope.launch {
            val localPlaylist = playlist.first { it != null }
            val browseId = localPlaylist?.playlist?.browseId
            if (browseId != null) {
                val page = withContext(Dispatchers.IO) {
                    YouTube.playlist(browseId).getOrNull()
                }
                val online = page?.playlist
                _onlinePlaylist.value = online
            }
        }
    }
}
