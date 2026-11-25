package com.metrolist.music.playback

import androidx.media3.common.MediaItem
import com.metrolist.music.playback.queues.Queue
import com.metrolist.music.playback.queues.Queue.Status
import com.metrolist.music.playback.queues.shouldFillBeyondInitialWindow
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QueueFillTest {
    private class FakeQueue(
        override val isFinite: Boolean,
        private val hasMore: Boolean,
    ) : Queue {
        override val preloadItem = null
        override suspend fun getInitialStatus() = Status(null, emptyList(), 0)
        override fun hasNextPage() = hasMore
        override suspend fun nextPage(): List<MediaItem> = emptyList()
    }

    @Test
    fun `finite playlist keeps filling past the initial window`() {
        assertTrue(FakeQueue(isFinite = true, hasMore = true).shouldFillBeyondInitialWindow(500))
    }

    @Test
    fun `open ended queue stops filling at the initial window`() {
        assertFalse(FakeQueue(isFinite = false, hasMore = true).shouldFillBeyondInitialWindow(200))
        assertTrue(FakeQueue(isFinite = false, hasMore = true).shouldFillBeyondInitialWindow(199))
    }

    @Test
    fun `exhausted queue stops filling`() {
        assertFalse(FakeQueue(isFinite = true, hasMore = false).shouldFillBeyondInitialWindow(0))
    }
}
