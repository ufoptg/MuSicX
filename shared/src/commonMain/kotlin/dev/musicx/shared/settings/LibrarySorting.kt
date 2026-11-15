package dev.musicx.shared.settings

import dev.musicx.shared.model.SortDirection
import kotlinx.serialization.Serializable

@Serializable
data class LibrarySorting(
    val sort: LibraryContentSort,
    val direction: SortDirection,
)
