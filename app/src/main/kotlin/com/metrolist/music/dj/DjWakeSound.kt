/**
 * MuSicX Project (C) 2026
 * Credits to Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.dj

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.ToneGenerator
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import timber.log.Timber
import kotlin.math.sin

/**
 * Activation chirp + haptic when “Hey DJ 6” is heard (Bixby/Google-style feedback).
 * Uses STREAM_MUSIC so it isn’t silenced when notification volume is muted.
 */
object DjWakeSound {
    private val mainHandler = Handler(Looper.getMainLooper())

    fun playChirp(context: Context) {
        mainHandler.post {
            vibrate(context)
            if (!playToneChirp()) {
                playSineChirp()
            }
        }
    }

    private fun vibrate(context: Context) {
        try {
            val vibrator =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    context.getSystemService(VibratorManager::class.java)?.defaultVibrator
                } else {
                    @Suppress("DEPRECATION")
                    context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                }
            if (vibrator == null || !vibrator.hasVibrator()) return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(60, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(60)
            }
        } catch (e: Exception) {
            Timber.w(e, "DjWakeSound: vibrate failed")
        }
    }

    private fun playToneChirp(): Boolean =
        try {
            val tone = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
            tone.startTone(ToneGenerator.TONE_PROP_ACK, 150)
            mainHandler.postDelayed({
                runCatching { tone.startTone(ToneGenerator.TONE_PROP_BEEP2, 100) }
            }, 160)
            mainHandler.postDelayed({ runCatching { tone.release() } }, 450)
            true
        } catch (e: Exception) {
            Timber.w(e, "DjWakeSound: ToneGenerator failed")
            false
        }

    /** Fallback beep through AudioTrack on the music stream. */
    private fun playSineChirp() {
        try {
            val sampleRate = 22050
            val durationMs = 180
            val numSamples = sampleRate * durationMs / 1000
            val samples = ShortArray(numSamples)
            val freq = 880.0
            for (i in 0 until numSamples) {
                val env =
                    when {
                        i < numSamples / 10 -> i.toDouble() / (numSamples / 10)
                        i > numSamples * 9 / 10 -> (numSamples - i).toDouble() / (numSamples / 10)
                        else -> 1.0
                    }
                samples[i] =
                    (sin(2.0 * Math.PI * i * freq / sampleRate) * 28000 * env).toInt().toShort()
            }
            val track =
                AudioTrack
                    .Builder()
                    .setAudioAttributes(
                        AudioAttributes
                            .Builder()
                            .setUsage(AudioAttributes.USAGE_ASSISTANT)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build(),
                    ).setAudioFormat(
                        AudioFormat
                            .Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build(),
                    ).setBufferSizeInBytes(samples.size * 2)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()
            track.write(samples, 0, samples.size)
            track.play()
            mainHandler.postDelayed({
                runCatching {
                    track.stop()
                    track.release()
                }
            }, (durationMs + 80).toLong())
        } catch (e: Exception) {
            Timber.w(e, "DjWakeSound: sine chirp failed")
        }
    }
}
