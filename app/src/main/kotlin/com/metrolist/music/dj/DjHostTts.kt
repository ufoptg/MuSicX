/**
 * MuSicX Project (C) 2026
 * Credits to Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.dj

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.suspendCancellableCoroutine
import timber.log.Timber
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

/**
 * Thin on-device TTS host for AI DJ banter.
 * // ponytail: system TTS only; swap for cloud voice if quality becomes a real complaint
 */
class DjHostTts(
    context: Context,
) {
    private val appContext = context.applicationContext
    private var tts: TextToSpeech? = null
    private val ready = AtomicBoolean(false)
    private val speaking = AtomicBoolean(false)

    fun init() {
        if (tts != null) return
        tts =
            TextToSpeech(appContext) { status ->
                ready.set(status == TextToSpeech.SUCCESS)
                if (status == TextToSpeech.SUCCESS) {
                    tts?.language = Locale.getDefault()
                } else {
                    Timber.w("DjHostTts: init failed status=$status")
                }
            }
    }

    val isSpeaking: Boolean get() = speaking.get()

    fun stop() {
        speaking.set(false)
        tts?.stop()
    }

    suspend fun speak(text: String): Boolean {
        val engine = tts ?: return false
        if (!ready.get() || text.isBlank()) return false
        stop()
        return suspendCancellableCoroutine { cont ->
            speaking.set(true)
            val id = UUID.randomUUID().toString()
            engine.setOnUtteranceProgressListener(
                object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) = Unit

                    override fun onDone(utteranceId: String?) {
                        if (utteranceId != id) return
                        speaking.set(false)
                        if (cont.isActive) cont.resume(true)
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        if (utteranceId != id) return
                        speaking.set(false)
                        if (cont.isActive) cont.resume(false)
                    }

                    override fun onError(
                        utteranceId: String?,
                        errorCode: Int,
                    ) {
                        if (utteranceId != id) return
                        speaking.set(false)
                        if (cont.isActive) cont.resume(false)
                    }
                },
            )
            cont.invokeOnCancellation {
                speaking.set(false)
                engine.stop()
            }
            val result = engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, id)
            if (result != TextToSpeech.SUCCESS) {
                speaking.set(false)
                if (cont.isActive) cont.resume(false)
            }
        }
    }

    fun shutdown() {
        stop()
        tts?.shutdown()
        tts = null
        ready.set(false)
    }
}
