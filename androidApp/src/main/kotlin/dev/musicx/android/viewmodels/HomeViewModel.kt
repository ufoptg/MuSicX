package dev.musicx.android.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.musicx.shared.db.MusicDatabase
import dev.musicx.shared.viewmodels.HomeViewModel as SharedHomeViewModel

class HomeViewModel(
    database: MusicDatabase,
    likedSongsName: String,
) : ViewModel() {
    val shared = SharedHomeViewModel(database, viewModelScope, likedSongsName = likedSongsName)
}
