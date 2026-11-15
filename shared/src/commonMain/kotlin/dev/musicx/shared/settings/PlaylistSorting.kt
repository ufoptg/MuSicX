package dev.musicx.shared.settings

import dev.musicx.shared.model.CollectionSort
import dev.musicx.shared.model.PlaylistSongSort
import dev.musicx.shared.model.SortDirection
import kotlinx.serialization.Serializable

@Serializable
data class SavedPlaylistSongSorting(
    val sort: PlaylistSongSort,
    val direction: SortDirection,
)

@Serializable
data class SavedPlaylistCollectionSorting(
    val sort: CollectionSort,
    val direction: SortDirection,
)
