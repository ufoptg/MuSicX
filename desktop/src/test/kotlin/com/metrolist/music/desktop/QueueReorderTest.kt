package com.metrolist.music.desktop

import org.junit.Assert.assertEquals
import org.junit.Test

class QueueReorderTest {
    private fun q(vararg ids: String) = ids.map { SearchHit(videoId = it, title = it) }

    @Test
    fun movesItemDownAndShiftsCurrentIndex() {
        val (list, index) = reorderQueue(q("a", "b", "c"), currentIndex = 0, from = 0, to = 2)
        assertEquals(listOf("b", "c", "a"), list.map { it.videoId })
        assertEquals(2, index)
    }

    @Test
    fun movingAnItemAboveCurrentShiftsCurrentUp() {
        val (list, index) = reorderQueue(q("a", "b", "c"), currentIndex = 2, from = 0, to = 1)
        assertEquals(listOf("b", "a", "c"), list.map { it.videoId })
        assertEquals(2, index)
    }

    @Test
    fun movingAcrossCurrentShiftsCurrentDown() {
        val (list, index) = reorderQueue(q("a", "b", "c"), currentIndex = 0, from = 2, to = 0)
        assertEquals(listOf("c", "a", "b"), list.map { it.videoId })
        assertEquals(1, index)
    }

    @Test
    fun movingAnItemDownAcrossCurrentShiftsCurrentDown() {
        // current is index 2; move item 0 down to index 2 (crosses current upward)
        val (list, index) = reorderQueue(q("a", "b", "c"), currentIndex = 2, from = 0, to = 2)
        assertEquals(listOf("b", "c", "a"), list.map { it.videoId })
        assertEquals(1, index)
    }

    @Test
    fun movingItemToCurrentIndexFromBelowShiftsCurrentUp() {
        // current is index 1; move item 2 up to index 1 (to == currentIndex)
        val (list, index) = reorderQueue(q("a", "b", "c"), currentIndex = 1, from = 2, to = 1)
        assertEquals(listOf("a", "c", "b"), list.map { it.videoId })
        assertEquals(2, index)
    }

    @Test
    fun noOpWhenIndicesEqualOrOutOfRange() {
        val input = q("a", "b", "c")
        assertEquals(input to 1, reorderQueue(input, currentIndex = 1, from = 1, to = 1))
        assertEquals(input to 1, reorderQueue(input, currentIndex = 1, from = 0, to = 9))
    }

    @Test
    fun detailReorderIndicesSubtractLeadingItems() {
        // lazy-list indices 0=header, 1=sort, 2..4=tracks
        assertEquals(0 to 2, detailReorderIndices(fromIndex = 2, toIndex = 4, trackCount = 3, leadingItems = 2))
        assertEquals(2 to 0, detailReorderIndices(fromIndex = 4, toIndex = 2, trackCount = 3, leadingItems = 2))
    }

    @Test
    fun detailReorderIndicesRejectOutOfRange() {
        // last track is lazy index 4; index 5 is the "Suggested" header, not a track
        assertEquals(null, detailReorderIndices(fromIndex = 5, toIndex = 2, trackCount = 3, leadingItems = 2))
        assertEquals(null, detailReorderIndices(fromIndex = 2, toIndex = 0, trackCount = 3, leadingItems = 2))
    }
}
