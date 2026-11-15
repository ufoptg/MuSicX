package dev.musicx.shared.ui.screens

import dev.musicx.shared.model.PlaylistItem
import dev.musicx.shared.model.SongItem
import dev.musicx.shared.model.SortDirection
import dev.musicx.shared.settings.HiddenLibraryItem
import dev.musicx.shared.settings.moveLibraryItems
import dev.musicx.shared.viewmodels.libraryAccountKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class LibrarySortingTest {
    @Test
    fun metadataSortComputesEachKeyOnceAndKeepsTiesAndUnknownValuesLast() {
        val items = listOf(playlist("a", "beta"), playlist("b", "Alpha"), playlist("c", "alpha"), playlist("d", ""))
        for (direction in SortDirection.entries) {
            val evaluated = mutableListOf<String>()
            val sorted =
                items.sortedByLibraryMetadata(direction) {
                    evaluated += it.id
                    it.title.lowercase().takeIf(String::isNotBlank)
                }
            assertEquals(items.map { it.id }, evaluated)
            assertEquals(
                if (direction == SortDirection.ASCENDING) listOf("b", "c", "a", "d") else listOf("a", "b", "c", "d"),
                sorted.map { it.id },
            )
        }
    }

    @Test
    fun manualTrackOrderRetainsCollectionOrder() {
        val first = SongItem("first", "First", null, emptyList(), null)
        val second = first.copy(id = "second", title = "Second")
        val playlists = listOf(playlist("a", "Alpha"), playlist("b", "Beta"))
        val items = playlists + listOf(first, second)
        val collectionOrder =
            moveLibraryItems(items, playlists, "", HiddenLibraryItem.from(playlists[0]).key, HiddenLibraryItem.from(playlists[1]).key)
        val trackOrder =
            moveLibraryItems(
                items,
                listOf(first, second),
                collectionOrder,
                HiddenLibraryItem.from(first).key,
                HiddenLibraryItem.from(second).key,
            )

        fun prepare(filter: LibraryFilter?) =
            prepareLibraryItems(items, filter, emptySet(), LibrarySortType.MANUAL, trackOrder, SortDirection.ASCENDING, "", emptyList())
        assertEquals(listOf(second, first), prepare(LibraryFilter.TRACKS))
        assertEquals(playlists.reversed(), prepare(null))
    }

    @Test
    fun accountCacheKeyIncludesTheSelectedYouTubeProfile() {
        assertNotEquals(
            libraryAccountKey("account", "", "0"),
            libraryAccountKey("account", "", "1"),
        )
    }

    private fun playlist(
        id: String,
        title: String,
    ) = PlaylistItem(id = id, title = title, thumbnail = null)
}
