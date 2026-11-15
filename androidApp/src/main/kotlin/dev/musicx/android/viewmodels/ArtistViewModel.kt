package dev.musicx.android.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.musicx.shared.api.artwork.AnimatedArtworkService
import kotlinx.coroutines.CoroutineScope
import dev.musicx.shared.viewmodels.ArtistViewModel as SharedArtistViewModel

class ArtistViewModel(
    artistId: String? = null,
    animatedArtworkService: AnimatedArtworkService,
    applicationScope: CoroutineScope,
) : ViewModel() {
    val shared = SharedArtistViewModel(artistId, viewModelScope, animatedArtworkService, applicationScope)
}
