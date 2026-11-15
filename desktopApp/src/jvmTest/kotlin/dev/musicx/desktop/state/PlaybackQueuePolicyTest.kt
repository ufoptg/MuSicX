package dev.musicx.desktop.state

import dev.musicx.desktop.state.playback.PlaybackQueueAction
import dev.musicx.desktop.state.playback.PlaybackQueueDecision
import dev.musicx.desktop.state.playback.decidePlaybackQueueTransition
import dev.musicx.shared.model.RepeatMode
import dev.musicx.shared.model.SongItem
import kotlin.test.Test
import kotlin.test.assertEquals

class PlaybackQueuePolicyTest {
    private val first = SongItem("first", "First", null, emptyList(), null)
    private val last = first.copy(id = "last")
    private val queue = listOf(first, last)

    @Test
    fun repeatAndAutoplayRespectManualSkipAndQueueBoundaries() {
        val cases =
            listOf(
                Triple(RepeatMode.ONE, true, 0) to PlaybackQueueDecision(PlaybackQueueAction.REPLAY_CURRENT, first),
                Triple(RepeatMode.ONE, false, 0) to PlaybackQueueDecision(PlaybackQueueAction.PLAY_NEXT, last),
                Triple(RepeatMode.ALL, true, 0) to PlaybackQueueDecision(PlaybackQueueAction.PLAY_NEXT, last),
                Triple(RepeatMode.ALL, true, 1) to PlaybackQueueDecision(PlaybackQueueAction.RESTART_QUEUE, first),
                Triple(RepeatMode.OFF, true, 1) to PlaybackQueueDecision(PlaybackQueueAction.AUTOPLAY),
                Triple(RepeatMode.OFF, false, 1) to PlaybackQueueDecision(PlaybackQueueAction.AUTOPLAY),
            )
        for ((input, expected) in cases) {
            val (mode, fromEof, index) = input
            assertEquals(expected, decidePlaybackQueueTransition(mode, fromEof, queue[index], queue, index), input.toString())
        }
    }

    @Test
    fun disabledAutoLoadStopsAtTheEndButStillPlaysExistingQueueItems() {
        for (fromEof in listOf(false, true)) {
            assertEquals(
                PlaybackQueueDecision(PlaybackQueueAction.STOP),
                decidePlaybackQueueTransition(RepeatMode.OFF, fromEof, last, queue, 1, autoLoadMore = false),
            )
            assertEquals(
                PlaybackQueueDecision(PlaybackQueueAction.PLAY_NEXT, last),
                decidePlaybackQueueTransition(RepeatMode.OFF, fromEof, first, queue, 0, autoLoadMore = false),
            )
        }
    }
}
