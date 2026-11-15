package dev.musicx.android.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.musicx.shared.viewmodels.FRIENDS_REFRESH_INTERVAL_MS
import dev.musicx.shared.viewmodels.FriendsViewModel

class FriendsViewModel : ViewModel() {
    val sharedViewModel =
        FriendsViewModel(viewModelScope)
    val uiState = sharedViewModel.uiState

    fun startPolling(intervalMs: Long = FRIENDS_REFRESH_INTERVAL_MS) = sharedViewModel.startPolling(intervalMs)

    fun stopPolling() = sharedViewModel.stopPolling()

    override fun onCleared() {
        sharedViewModel.stopPolling()
    }
}
