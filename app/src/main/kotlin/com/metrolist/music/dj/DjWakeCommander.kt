/**
 * MuSicX Project (C) 2026
 * Credits to Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.dj

import android.content.Context
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Silent on-device wake listening (Vosk) for “Hey DJ 6 …”, then captures the command.
 * Does not use Google SpeechRecognizer, so it will not chirp / steal focus in a loop.
 */
class DjWakeCommander(
    context: Context,
    private val scope: CoroutineScope,
    private val onCommand: (DjCommand) -> Unit,
) {
    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val active = AtomicBoolean(false)
    private val paused = AtomicBoolean(false)
    private val handling = AtomicBoolean(false)

    private var model: Model? = null
    private var speechService: SpeechService? = null
    private var mode = Mode.WAKE
    private var prepareJob: Job? = null
    private var commandTimeout: Runnable? = null

    fun start() {
        if (active.getAndSet(true)) return
        prepareJob?.cancel()
        prepareJob =
            scope.launch {
                val ready =
                    DjVoskModel.ensureReady(appContext).getOrElse {
                        Timber.w(it, "DjWakeCommander: model not ready")
                        active.set(false)
                        return@launch
                    }
                withContext(Dispatchers.Main) {
                    if (!active.get()) return@withContext
                    try {
                        model?.close()
                        model = Model(ready.absolutePath)
                        startWakeListening()
                    } catch (e: Exception) {
                        Timber.w(e, "DjWakeCommander: failed to start")
                        active.set(false)
                    }
                }
            }
    }

    fun stop() {
        active.set(false)
        paused.set(false)
        handling.set(false)
        prepareJob?.cancel()
        prepareJob = null
        clearCommandTimeout()
        mainHandler.post {
            shutdownService()
            runCatching { model?.close() }
            model = null
        }
    }

    fun setPaused(value: Boolean) {
        paused.set(value)
        speechService?.setPause(value)
        if (value) {
            clearCommandTimeout()
            handling.set(false)
            mode = Mode.WAKE
        } else if (active.get()) {
            // Resume in wake mode after host TTS
            mainHandler.post {
                if (active.get() && !paused.get()) {
                    startWakeListening()
                }
            }
        }
    }

    private fun startWakeListening() {
        if (!active.get() || paused.get()) return
        mode = Mode.WAKE
        restartService(grammar = WAKE_GRAMMAR, timeoutSec = 0)
    }

    private fun startCommandListening() {
        if (!active.get() || paused.get()) return
        mode = Mode.COMMAND
        restartService(grammar = null, timeoutSec = COMMAND_TIMEOUT_SEC)
        clearCommandTimeout()
        val timeout =
            Runnable {
                if (mode == Mode.COMMAND && active.get()) {
                    Timber.d("DjWakeCommander: command window expired")
                    handling.set(false)
                    startWakeListening()
                }
            }
        commandTimeout = timeout
        mainHandler.postDelayed(timeout, COMMAND_TIMEOUT_SEC * 1000L)
    }

    private fun restartService(
        grammar: String?,
        timeoutSec: Int,
    ) {
        val m = model ?: return
        shutdownService()
        try {
            val recognizer =
                if (grammar != null) {
                    Recognizer(m, SAMPLE_RATE, grammar)
                } else {
                    Recognizer(m, SAMPLE_RATE)
                }
            val service = SpeechService(recognizer, SAMPLE_RATE)
            speechService = service
            val started =
                if (timeoutSec > 0) {
                    service.startListening(listener, timeoutSec)
                } else {
                    service.startListening(listener)
                }
            if (!started) {
                Timber.w("DjWakeCommander: startListening returned false")
            }
        } catch (e: Exception) {
            Timber.w(e, "DjWakeCommander: restartService failed")
        }
    }

    private fun shutdownService() {
        runCatching {
            speechService?.stop()
            speechService?.shutdown()
        }
        speechService = null
    }

    private fun clearCommandTimeout() {
        commandTimeout?.let { mainHandler.removeCallbacks(it) }
        commandTimeout = null
    }

    private val listener =
        object : RecognitionListener {
            override fun onPartialResult(hypothesis: String?) {
                handleHypothesis(hypothesis, isFinal = false)
            }

            override fun onResult(hypothesis: String?) {
                handleHypothesis(hypothesis, isFinal = true)
            }

            override fun onFinalResult(hypothesis: String?) {
                handleHypothesis(hypothesis, isFinal = true)
                if (mode == Mode.COMMAND && active.get() && !paused.get()) {
                    handling.set(false)
                    startWakeListening()
                }
            }

            override fun onError(exception: Exception?) {
                Timber.w(exception, "DjWakeCommander: recognition error")
                if (active.get() && !paused.get()) {
                    mainHandler.postDelayed({ startWakeListening() }, 1500)
                }
            }

            override fun onTimeout() {
                if (mode == Mode.COMMAND) {
                    handling.set(false)
                    startWakeListening()
                }
            }
        }

    private fun handleHypothesis(
        hypothesis: String?,
        isFinal: Boolean,
    ) {
        if (!active.get() || paused.get() || hypothesis.isNullOrBlank()) return
        val spoken = extractText(hypothesis) ?: return
        if (spoken.isBlank()) return
        val normalized = DjCommandParser.normalizeSpoken(spoken)
        Timber.d("DjWakeCommander[$mode]: $normalized (final=$isFinal)")

        when (mode) {
            Mode.WAKE -> {
                val withWake = DjCommandParser.parse(normalized, requireWake = true)
                if (withWake != null) {
                    if (!handling.compareAndSet(false, true)) return
                    clearCommandTimeout()
                    onCommand(withWake)
                    handling.set(false)
                    // Stay in wake mode for the next utterance
                    if (isFinal) startWakeListening()
                    return
                }
                if (DjCommandParser.isWakeOnly(normalized)) {
                    if (!handling.compareAndSet(false, true)) return
                    Timber.i("DjWakeCommander: wake heard, listening for command")
                    startCommandListening()
                }
            }
            Mode.COMMAND -> {
                val cmd =
                    DjCommandParser.parse(normalized, requireWake = false)
                        ?: DjCommandParser.parse(normalized, requireWake = true)
                if (cmd != null) {
                    clearCommandTimeout()
                    onCommand(cmd)
                    handling.set(false)
                    startWakeListening()
                }
            }
        }
    }

    private fun extractText(hypothesis: String): String? =
        try {
            val obj = JSONObject(hypothesis)
            when {
                obj.has("text") -> obj.optString("text")
                obj.has("partial") -> obj.optString("partial")
                else -> null
            }
        } catch (_: Exception) {
            hypothesis
        }

    private enum class Mode {
        WAKE,
        COMMAND,
    }

    companion object {
        private const val SAMPLE_RATE = 16000.0f
        private const val COMMAND_TIMEOUT_SEC = 6

        /**
         * Restricted grammar keeps wake detection snappy and reduces lyric false-positives.
         * Free-form listening is used only after wake for arbitrary “play …” queries.
         */
        private val WAKE_GRAMMAR =
            """
            ["hey dj six",
             "hey dj 6",
             "dj six",
             "dj 6",
             "hey dj six skip",
             "hey dj six skip song",
             "hey dj six next",
             "hey dj six previous",
             "hey dj six pause",
             "hey dj six resume",
             "hey dj six play",
             "dj six skip",
             "dj six next",
             "dj six previous",
             "dj six pause",
             "dj six resume",
             "[unk]"]
            """.trimIndent().replace("\n", " ")
    }
}
