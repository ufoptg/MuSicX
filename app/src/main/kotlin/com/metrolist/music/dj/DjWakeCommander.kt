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
 * Always-on on-device “Hey DJ 6” listening (Vosk), using tiny keyword grammars instead of
 * free-form decoding. Constrained decoding is fast, accurate and battery-friendly, and it
 * never launches an activity — the command is recognised in-process, so it works while the
 * app is backgrounded (the old Speak-now handoff was blocked by Android’s background
 * activity-launch restriction and added the Google STT cold-start lag).
 *
 * One recognizer runs at a time, in one of two modes:
 *  - [Mode.WAKE] — grammar of wake phrases plus “wake + command” one-shots.
 *  - [Mode.COMMAND] — grammar of the bare command set, for a short window after wake.
 */
class DjWakeCommander(
    context: Context,
    private val scope: CoroutineScope,
    private val onCommand: (DjCommand) -> Unit,
    /** Wake phrase heard; play the chirp/duck. The command follows from this engine. */
    private val onWake: () -> Unit,
    private val onReturnedToWake: (() -> Unit)? = null,
    private val onReady: (() -> Unit)? = null,
    private val onFailed: ((String) -> Unit)? = null,
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
    private var lastHeardLogged = ""

    fun start() {
        if (active.get()) {
            // Already supposed to be running — revive a dead mic session.
            mainHandler.post { ensureListening() }
            return
        }
        active.set(true)
        prepareJob?.cancel()
        prepareJob =
            scope.launch(Dispatchers.IO) {
                val ready =
                    DjVoskModel.ensureReady(appContext).getOrElse {
                        Timber.w(it, "DjWakeCommander: model not ready")
                        active.set(false)
                        withContext(Dispatchers.Main) {
                            onFailed?.invoke(it.message ?: "model")
                        }
                        return@launch
                    }
                try {
                    // Model load is heavy / JNI — keep it off the main thread.
                    val loaded = Model(ready.absolutePath)
                    withContext(Dispatchers.Main) {
                        if (!active.get()) {
                            loaded.close()
                            return@withContext
                        }
                        runCatching { model?.close() }
                        model = loaded
                        startWakeListening()
                        onReady?.invoke()
                        Timber.i("DjWakeCommander: listening for Hey DJ 6")
                    }
                } catch (t: Throwable) {
                    Timber.w(t, "DjWakeCommander: failed to start")
                    active.set(false)
                    withContext(Dispatchers.Main) {
                        onFailed?.invoke(t.javaClass.simpleName)
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
        if (!value && active.get()) {
            handling.set(false)
            mainHandler.postDelayed({
                if (active.get() && !paused.get() && speechService == null) {
                    ensureListening()
                }
            }, 300)
        }
    }

    private fun ensureListening() {
        if (!active.get() || paused.get() || model == null) return
        if (speechService == null) {
            if (mode == Mode.COMMAND) startCommandListening() else startWakeListening()
        }
    }

    private fun startWakeListening() {
        if (!active.get() || paused.get()) return
        val wasCommand = mode == Mode.COMMAND
        mode = Mode.WAKE
        handling.set(false)
        restartService(grammar = WAKE_GRAMMAR, timeoutSec = 0)
        if (wasCommand) {
            onReturnedToWake?.invoke()
        }
    }

    private fun startCommandListening() {
        if (!active.get() || paused.get()) return
        mode = Mode.COMMAND
        restartService(grammar = COMMAND_GRAMMAR, timeoutSec = COMMAND_TIMEOUT_SEC)
        clearCommandTimeout()
        val timeout =
            Runnable {
                if (mode == Mode.COMMAND && active.get()) {
                    Timber.i("DjWakeCommander: command window expired")
                    startWakeListening()
                }
            }
        commandTimeout = timeout
        mainHandler.postDelayed(timeout, COMMAND_TIMEOUT_SEC * 1000L)
    }

    private fun restartService(
        grammar: String,
        timeoutSec: Int,
    ) {
        val m = model ?: return
        shutdownService()
        try {
            val recognizer = Recognizer(m, SAMPLE_RATE, grammar)
            recognizer.setPartialWords(true)
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
                mainHandler.postDelayed({ ensureListening() }, 2000)
            }
        } catch (t: Throwable) {
            Timber.w(t, "DjWakeCommander: restartService failed")
            speechService = null
            mainHandler.postDelayed({ ensureListening() }, 2000)
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
                    startWakeListening()
                }
            }

            override fun onError(exception: Exception?) {
                Timber.w(exception, "DjWakeCommander: recognition error")
                speechService = null
                if (active.get() && !paused.get()) {
                    mainHandler.postDelayed({ ensureListening() }, 1500)
                }
            }

            override fun onTimeout() {
                if (mode == Mode.COMMAND) {
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
        if (spoken.isBlank() || UNKNOWN.matches(spoken)) return
        val normalized = DjCommandParser.normalizeSpoken(spoken)
        if (normalized.isBlank() || UNKNOWN.matches(normalized)) return
        if (normalized != lastHeardLogged) {
            lastHeardLogged = normalized
            Timber.i("DjWakeCommander[$mode] heard: \"$normalized\" final=$isFinal")
        }

        when (mode) {
            Mode.WAKE -> {
                if (handling.get()) return
                // One-shot “Hey DJ 6, skip”.
                val oneShot = DjCommandParser.parse(normalized, requireWake = true)
                if (oneShot != null) {
                    handling.set(true)
                    clearCommandTimeout()
                    Timber.i("DjWakeCommander: one-shot command $oneShot")
                    onWake()
                    onCommand(oneShot)
                    handling.set(false)
                    onReturnedToWake?.invoke()
                    return
                }
                if (!DjCommandParser.containsWake(normalized)) return
                // Wake only — keep the mic in-process and listen for the command.
                handling.set(true)
                clearCommandTimeout()
                Timber.i("DjWakeCommander: wake — listening for command")
                onWake()
                startCommandListening()
            }
            Mode.COMMAND -> {
                val command = DjCommandParser.parse(normalized, requireWake = false) ?: return
                Timber.i("DjWakeCommander: command $command")
                onCommand(command)
                startWakeListening()
            }
        }
    }

    private fun extractText(hypothesis: String): String? =
        try {
            val obj = JSONObject(hypothesis)
            when {
                obj.has("text") -> obj.optString("text").takeIf { it.isNotBlank() }
                obj.has("partial") -> obj.optString("partial").takeIf { it.isNotBlank() }
                else -> null
            }
        } catch (_: Exception) {
            hypothesis.takeIf { it.isNotBlank() }
        }

    private enum class Mode {
        WAKE,
        COMMAND,
    }

    companion object {
        private const val SAMPLE_RATE = 16000.0f
        private const val COMMAND_TIMEOUT_SEC = 7
        private val UNKNOWN = Regex("""(?i)^[\s\[\]]*unk[\s\[\]]*$""")

        private val COMMAND_WORDS =
            listOf("skip", "next", "previous", "back", "pause", "stop", "resume", "continue", "play")

        /**
         * Keyword-spotter grammar: wake phrases only, plus common “wake + command” one-shots,
         * plus [UNK] so non-keyword speech isn’t force-matched onto a phrase.
         *
         * ponytail: “dj” must exist in the Vosk model vocab for these to match. If wake stops
         * firing on-device, add alternates here (e.g. “hey d j six”) rather than re-enabling
         * free-form decoding.
         */
        internal val WAKE_GRAMMAR: String =
            buildGrammar(
                listOf("hey dj six", "dj six") +
                    COMMAND_WORDS.map { "hey dj six $it" },
            )

        internal val COMMAND_GRAMMAR: String = buildGrammar(COMMAND_WORDS)

        internal fun buildGrammar(phrases: List<String>): String =
            phrases.joinToString(separator = ",", prefix = "[", postfix = ",\"[unk]\"]") {
                "\"${it.replace("\"", "\\\"")}\""
            }
    }
}
