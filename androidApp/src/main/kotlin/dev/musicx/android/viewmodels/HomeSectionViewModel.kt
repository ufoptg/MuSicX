package dev.musicx.android.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.musicx.shared.model.HomeSection
import dev.musicx.shared.model.SongItem
import dev.musicx.shared.utils.withArtworkCacheRevision
import dev.musicx.shared.viewmodels.BrowseViewModel

class HomeSectionViewModel(
    section: HomeSection,
) : ViewModel() {
    val shared = BrowseViewModel(section.browseId, section.params, section.title, viewModelScope, initialSection = section)
    private var artworkCacheRevision = System.nanoTime()

    fun updateSongMetadata(song: SongItem) {
        shared.updateSongMetadata(song.copy(thumbnail = song.thumbnail.withArtworkCacheRevision(++artworkCacheRevision)))
    }
}
