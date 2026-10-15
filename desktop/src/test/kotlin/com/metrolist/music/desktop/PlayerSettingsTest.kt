package com.metrolist.music.desktop

import com.metrolist.innertubex.extraction.AudioQuality
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerSettingsTest {
    @Test
    fun replacingOrCancellingTimerDoesNotStopPlaybackLater() = runBlocking {
        var stops = 0
        val expired = CompletableDeferred<Unit>()
        val timer = SleepTimer(this, minuteMillis = 10L) {
            stops++
            expired.complete(Unit)
        }
        timer.start(1)
        timer.start(2)
        withTimeout(2_000) { expired.await() }
        assertEquals(1, stops)
        timer.start(1)
        timer.cancel()
        delay(160)
        assertEquals(1, stops)
    }

    @Test
    fun replacingTimerDuringExpiryDoesNotResetReplacement() = runBlocking {
        val expired = CompletableDeferred<Unit>()
        val timer = SleepTimer(this, minuteMillis = 1L) {
            expired.complete(Unit)
            delay(1_000)
        }
        timer.start(1)
        expired.await()
        timer.start(5)
        delay(10)
        assertEquals(5, timer.mode)
        assertTrue(timer.consumeEndOfTrack())
    }

    @Test
    fun endOfTrackTimerConsumesOnlyOneFinish() = runBlocking {
        val timer = SleepTimer(this) { error("Timed mode should not expire") }
        timer.start(5)
        assertTrue(timer.consumeEndOfTrack())
        assertFalse(timer.consumeEndOfTrack())
        assertEquals(0, timer.mode)
    }

    @Test
    fun qualityPreferenceSelectsInnerTubeExtractionQuality() {
        assertEquals(AudioQuality.AUTO, DesktopInnerTube.audioQualityForSetting(0))
        assertEquals(AudioQuality.HIGH, DesktopInnerTube.audioQualityForSetting(1))
        assertEquals(AudioQuality.LOW, DesktopInnerTube.audioQualityForSetting(2))
        assertEquals(AudioQuality.HIGH, DesktopInnerTube.audioQualityForSetting(99))
    }

    @Test
    fun vlcNormalizationIsOnlyRequestedWhenEnabled() {
        assertFalse(DesktopAudioPlayer.audioFactoryOptions(false).contains("--audio-filter=volnorm"))
        assertTrue(DesktopAudioPlayer.audioFactoryOptions(true).contains("--audio-filter=volnorm"))
    }
}
