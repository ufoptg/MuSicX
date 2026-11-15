package dev.musicx.android.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.musicx.shared.db.MusicDatabase
import dev.musicx.shared.model.PlaylistItem
import dev.musicx.shared.playlist.createStoredPlaylist
import dev.musicx.shared.playlist.deleteStoredPlaylist
import dev.musicx.shared.playlist.renameStoredPlaylist
import dev.musicx.shared.state.GlobalYouTubeRepository
import dev.musicx.shared.viewmodels.LibraryViewModel
import kotlinx.coroutines.launch

class LibraryViewModel(
    private val database: MusicDatabase,
) : ViewModel() {
    private val sharedViewModel =
        LibraryViewModel(viewModelScope, database)
    val uiState = sharedViewModel.uiState
    val presentation = sharedViewModel.presentation
    val contentMatchedSongs = sharedViewModel.contentMatchedSongs
    val contentSearchHasMore = sharedViewModel.contentSearchHasMore

    fun loadMoreContentMatches() = sharedViewModel.loadMoreContentMatches()

    fun setSearchDescending(descending: Boolean) = sharedViewModel.setSearchDescending(descending)

    fun setSearchQuery(query: String) = sharedViewModel.setSearchQuery(query)

    fun setActive(active: Boolean) = sharedViewModel.setActive(active)

    fun refresh(force: Boolean = true) = sharedViewModel.refresh(force)

    fun refreshAfterHomeLoaded() = sharedViewModel.refreshAfterHomeLoaded()

    fun createPlaylist(
        name: String,
        onResult: (Result<String>) -> Unit,
    ) {
        viewModelScope.launch {
            val result = createStoredPlaylist(database, GlobalYouTubeRepository.instance, name)
            if (result.isSuccess) refresh()
            onResult(result)
        }
    }

    fun renamePlaylist(
        playlist: PlaylistItem,
        name: String,
        onResult: (Result<Unit>) -> Unit,
    ) {
        viewModelScope.launch {
            val result = renameStoredPlaylist(database, GlobalYouTubeRepository.instance, playlist.id, name)
            if (result.isSuccess) refresh()
            onResult(result)
        }
    }

    fun deletePlaylist(
        playlist: PlaylistItem,
        onResult: (Result<Unit>) -> Unit,
    ) {
        viewModelScope.launch {
            val result = deleteStoredPlaylist(database, GlobalYouTubeRepository.instance, playlist.id)
            if (result.isSuccess) {
                sharedViewModel.onItemDeleted(playlist)
                refresh(force = true)
            }
            onResult(result)
        }
    }
}
