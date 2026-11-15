package dev.musicx.android.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.musicx.shared.viewmodels.AboutArtistViewModel as SharedAboutArtistViewModel

class AboutArtistViewModel(
    artistId: String,
    artistName: String,
) : ViewModel() {
    val shared = SharedAboutArtistViewModel(artistId, artistName, viewModelScope)
}
