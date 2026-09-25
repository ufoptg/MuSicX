/**
 * MuSicX Project (C) 2026
 * Credits to Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.dj

import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper
import timber.log.Timber

/**
 * Short activation chirp when “Hey DJ 6” is heard — same role as Bixby/Google’s wake sound.
 */
object DjWakeSound {
    private val mainHandler = Handler(Looper.getMainLooper())

    fun playChirp() {
        mainHandler.post {
            try {
                val tone = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 90)
                // Short double-beep style acknowledgment
                tone.startTone(ToneGenerator.TONE_PROP_ACK, 120)
                mainHandler.postDelayed({
                    runCatching {
                        tone.startTone(ToneGenerator.TONE_PROP_BEEP, 80)
                    }
                }, 140)
                mainHandler.postDelayed({ runCatching { tone.release() } }, 400)
            } catch (e: Exception) {
                Timber.w(e, "DjWakeSound: chirp failed")
            }
        }
    }
}
