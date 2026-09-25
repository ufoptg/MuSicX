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
 * Silent on-device wake listening (Vosk) for “Hey DJ 6 …”.
 * Uses free-form recognition (not a tiny grammar) so real speech isn’t discarded as [unk].
 */
class DjWakeCommander(
    context: Context,
    private val scope: CoroutineScope,
    private val onCommand: (DjCommand) -> Unit,
    private val onWakeHeard: (() -> Unit)? = null,
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
            // Keep current mode (wake vs command); only revive a dead session.
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
        restartService(timeoutSec = 0)
        if (wasCommand) {
            onReturnedToWake?.invoke()
        }
    }

    private fun startCommandListening() {
        if (!active.get() || paused.get()) return
        mode = Mode.COMMAND
        restartService(timeoutSec = COMMAND_TIMEOUT_SEC)
        clearCommandTimeout()
        val timeout =
            Runnable {
                if (mode == Mode.COMMAND && active.get()) {
                    Timber.i("DjWakeCommander: command window expired")
                    handling.set(false)
                    startWakeListening()
                }
            }
        commandTimeout = timeout
        mainHandler.postDelayed(timeout, COMMAND_TIMEOUT_SEC * 1000L)
    }

    private fun restartService(timeoutSec: Int) {
        val m = model ?: return
        shutdownService()
        try {
            val recognizer = Recognizer(m, SAMPLE_RATE)
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
                    handling.set(false)
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
        if (normalized != lastHeardLogged) {
            lastHeardLogged = normalized
            Timber.i("DjWakeCommander[$mode] heard: \"$normalized\" final=$isFinal")
        }

        when (mode) {
            Mode.WAKE -> {
                if (!DjCommandParser.containsWake(normalized)) return

                val withWake = DjCommandParser.parse(normalized, requireWake = true)
                if (withWake != null) {
                    if (!handling.compareAndSet(false, true)) return
                    clearCommandTimeout()
                    Timber.i("DjWakeCommander: command $withWake")
                    // One-shot phrase still gets the assistant chirp for feedback.
                    onWakeHeard?.invoke()
                    onCommand(withWake)
                    handling.set(false)
                    onReturnedToWake?.invoke()
                    return
                }

                // Wake heard without a command yet (or only fillers after wake).
                if (isFinal || DjCommandParser.isWakeOnly(normalized) ||
                    DjCommandParser.stripWake(normalized).isBlank()
                ) {
                    if (!handling.compareAndSet(false, true)) return
                    Timber.i("DjWakeCommander: wake — listening for command")
                    startCommandListening()
                    onWakeHeard?.invoke()
                }
            }
            Mode.COMMAND -> {
                val cmd =
                    DjCommandParser.parse(normalized, requireWake = false)
                        ?: DjCommandParser.parse(normalized, requireWake = true)
                if (cmd != null) {
                    if (!isFinal && cmd is DjCommand.Play) {
                        // Wait for a fuller “play …” phrase on finals when possible.
                        return
                    }
                    clearCommandTimeout()
                    Timber.i("DjWakeCommander: command $cmd")
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
        private const val COMMAND_TIMEOUT_SEC = 8
    }
}
