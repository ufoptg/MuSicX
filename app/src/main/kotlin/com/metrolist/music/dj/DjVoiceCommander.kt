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
 * While DJ 6 is playing, continuously listen for utterances that start with "DJ 6 …".
 * Pauses while the host TTS is speaking so they don't fight over the mic.
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

    fun start() {
        if (!SpeechRecognizer.isRecognitionAvailable(appContext)) {
            Timber.w("DjVoiceCommander: speech recognition unavailable")
            return
        }
        if (active.getAndSet(true)) return
        mainHandler.post {
            ensureRecognizer()
            listenSoon(0)
        }
    }

    fun stop() {
        active.set(false)
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
        if (!value && active.get()) {
            listenSoon(250)
        } else if (value) {
            mainHandler.post {
                try {
                    recognizer?.cancel()
                } catch (_: Exception) {
                }
            }
        }
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
                            if (!active.get()) return
                            // Don't tight-loop on busy/client errors
                            val delay =
                                when (error) {
                                    SpeechRecognizer.ERROR_NO_MATCH,
                                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT,
                                    -> 400L
                                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> 800L
                                    else -> 600L
                                }
                            listenSoon(delay)
                        }

                        override fun onResults(results: Bundle?) {
                            val spoken =
                                results
                                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                                    ?.firstOrNull()
                                    .orEmpty()
                            if (spoken.isNotBlank()) {
                                Timber.d("DjVoiceCommander heard: $spoken")
                                DjCommandParser.parse(spoken)?.let(onCommand)
                            }
                            if (active.get()) listenSoon(350)
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

    private fun listenSoon(delayMs: Long) {
        mainHandler.postDelayed({
            if (!active.get() || paused.get()) return@postDelayed
            val sr = recognizer ?: return@postDelayed
            try {
                sr.cancel()
            } catch (_: Exception) {
            }
            try {
                sr.startListening(listenIntent())
            } catch (e: Exception) {
                Timber.w(e, "DjVoiceCommander: startListening failed")
                listenSoon(1000)
            }
        }, delayMs)
    }

    private fun listenIntent(): Intent =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            // Shorter silence so commands feel snappy
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1200)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1000)
        }
}
