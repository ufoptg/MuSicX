/**
 * MuSicX Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package dev.musicx.android.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.musicx.shared.viewmodels.SearchViewModel as SharedSearchViewModel

class SearchViewModel : ViewModel() {
    val shared = SharedSearchViewModel(viewModelScope)
    var hasRequestedInitialFocus = false
}
