/**
 * MuSicX Project (C) 2026
 * Credits to Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.dj

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import timber.log.Timber
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/**
 * While DJ 6 is playing, listen for “DJ 6 …” commands.
 * Uses a long cooldown between sessions — Android beeps on every startListening,
 * so a tight retry loop is unusable during music playback.
 */
class DjVoiceCommander(
    context: Context,
    private val onCommand: (DjCommand) -> Unit,
) {
    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private val active = AtomicBoolean(false)
    private val paused = AtomicBoolean(false)
    private val listening = AtomicBoolean(false)
    private val listenRunnable =
        Runnable {
            beginListen()
        }

    fun start() {
        if (!SpeechRecognizer.isRecognitionAvailable(appContext)) {
            Timber.w("DjVoiceCommander: speech recognition unavailable")
            return
        }
        if (active.getAndSet(true)) return
        scheduleListen(FIRST_LISTEN_DELAY_MS)
    }

    fun stop() {
        active.set(false)
        paused.set(false)
        listening.set(false)
        mainHandler.removeCallbacks(listenRunnable)
        mainHandler.post {
            try {
                recognizer?.cancel()
                recognizer?.destroy()
            } catch (_: Exception) {
            }
            recognizer = null
        }
    }

    fun setPaused(value: Boolean) {
        paused.set(value)
        if (value) {
            mainHandler.removeCallbacks(listenRunnable)
            listening.set(false)
            mainHandler.post {
                try {
                    recognizer?.cancel()
                } catch (_: Exception) {
                }
            }
        } else if (active.get()) {
            scheduleListen(RESUME_DELAY_MS)
        }
    }

    private fun scheduleListen(delayMs: Long) {
        mainHandler.removeCallbacks(listenRunnable)
        if (!active.get() || paused.get()) return
        mainHandler.postDelayed(listenRunnable, delayMs)
    }

    private fun ensureRecognizer() {
        if (recognizer != null) return
        recognizer =
            SpeechRecognizer.createSpeechRecognizer(appContext).also { sr ->
                sr.setRecognitionListener(
                    object : RecognitionListener {
                        override fun onReadyForSpeech(params: Bundle?) = Unit

                        override fun onBeginningOfSpeech() = Unit

                        override fun onRmsChanged(rmsdB: Float) = Unit

                        override fun onBufferReceived(buffer: ByteArray?) = Unit

                        override fun onEndOfSpeech() = Unit

                        override fun onError(error: Int) {
                            listening.set(false)
                            if (!active.get() || paused.get()) return
                            val delay =
                                when (error) {
                                    SpeechRecognizer.ERROR_NO_MATCH,
                                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT,
                                    -> IDLE_COOLDOWN_MS
                                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY,
                                    SpeechRecognizer.ERROR_CLIENT,
                                    -> BUSY_COOLDOWN_MS
                                    else -> IDLE_COOLDOWN_MS
                                }
                            scheduleListen(delay)
                        }

                        override fun onResults(results: Bundle?) {
                            listening.set(false)
                            val spoken =
                                results
                                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                                    ?.firstOrNull()
                                    .orEmpty()
                            if (spoken.isNotBlank()) {
                                Timber.d("DjVoiceCommander heard: $spoken")
                                DjCommandParser.parse(spoken, requireWake = true)?.let(onCommand)
                            }
                            if (active.get() && !paused.get()) {
                                scheduleListen(AFTER_RESULT_COOLDOWN_MS)
                            }
                        }

                        override fun onPartialResults(partialResults: Bundle?) = Unit

                        override fun onEvent(
                            eventType: Int,
                            params: Bundle?,
                        ) = Unit
                    },
                )
            }
    }

    private fun beginListen() {
        if (!active.get() || paused.get()) return
        if (!listening.compareAndSet(false, true)) {
            scheduleListen(BUSY_COOLDOWN_MS)
            return
        }
        ensureRecognizer()
        val sr = recognizer
        if (sr == null) {
            listening.set(false)
            return
        }
        try {
            sr.startListening(listenIntent())
        } catch (e: Exception) {
            listening.set(false)
            Timber.w(e, "DjVoiceCommander: startListening failed")
            scheduleListen(BUSY_COOLDOWN_MS)
        }
    }

    private fun listenIntent(): Intent =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            // Longer windows = fewer restart beeps while music plays
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2500)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2000)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 1500)
        }

    companion object {
        private const val FIRST_LISTEN_DELAY_MS = 2500L
        private const val RESUME_DELAY_MS = 1500L
        private const val IDLE_COOLDOWN_MS = 4000L
        private const val AFTER_RESULT_COOLDOWN_MS = 2500L
        private const val BUSY_COOLDOWN_MS = 5000L
    }
}
