/**
 * MuSicX Project (C) 2026
 * Credits to Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.dj

import android.content.Context
import android.media.MediaPlayer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.metrolist.music.constants.AiDjTtsModelKey
import com.metrolist.music.constants.AiDjTtsVoiceKey
import com.metrolist.music.constants.AiDjVoiceEngineKey
import com.metrolist.music.constants.DEFAULT_AI_DJ_TTS_MODEL
import com.metrolist.music.constants.DEFAULT_AI_DJ_TTS_VOICE
import com.metrolist.music.constants.OpenRouterApiKey
import com.metrolist.music.constants.OpenRouterBaseUrlKey
import com.metrolist.music.constants.OpenRouterDefaultBaseUrl
import com.metrolist.music.utils.dataStore
import com.metrolist.music.utils.get
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import timber.log.Timber
import java.io.File
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

/**
 * DJ host voice: OpenRouter cloud TTS when configured, otherwise on-device TextToSpeech.
 */
class DjHostTts(
    context: Context,
) {
    private val appContext = context.applicationContext
    private var tts: TextToSpeech? = null
    private var mediaPlayer: MediaPlayer? = null
    private val ready = AtomicBoolean(false)
    private val speaking = AtomicBoolean(false)
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val http =
        OkHttpClient
            .Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()

    fun init() {
        if (tts != null) return
        tts =
            TextToSpeech(appContext) { status ->
                ready.set(status == TextToSpeech.SUCCESS)
                if (status == TextToSpeech.SUCCESS) {
                    tts?.language = Locale.getDefault()
                } else {
                    Timber.w("DjHostTts: system TTS init failed status=$status")
                }
            }
    }

    val isSpeaking: Boolean get() = speaking.get()

    fun stop() {
        speaking.set(false)
        tts?.stop()
        mediaPlayer?.run {
            try {
                if (isPlaying) stop()
            } catch (_: Exception) {
            }
            try {
                reset()
                release()
            } catch (_: Exception) {
            }
        }
        mediaPlayer = null
    }

    suspend fun speak(text: String): Boolean {
        if (text.isBlank()) return false
        stop()
        val engine = appContext.dataStore.get(AiDjVoiceEngineKey, "openrouter")
        if (engine != "system") {
            val cloudOk = speakCloud(text)
            if (cloudOk) return true
            Timber.w("DjHostTts: cloud TTS failed, falling back to system")
        }
        return speakSystem(text)
    }

    private suspend fun speakCloud(text: String): Boolean =
        withContext(Dispatchers.IO) {
            val apiKey = appContext.dataStore.get(OpenRouterApiKey, "")
            if (apiKey.isBlank()) return@withContext false
            val chatUrl = appContext.dataStore.get(OpenRouterBaseUrlKey, OpenRouterDefaultBaseUrl)
            val speechUrl = speechEndpoint(chatUrl)
            val model = appContext.dataStore.get(AiDjTtsModelKey, DEFAULT_AI_DJ_TTS_MODEL)
            val voice = appContext.dataStore.get(AiDjTtsVoiceKey, DEFAULT_AI_DJ_TTS_VOICE)
            val body =
                buildJsonObject {
                    put("model", model)
                    put("input", text)
                    put("voice", voice)
                    put("response_format", "mp3")
                }
            val request =
                Request
                    .Builder()
                    .url(speechUrl)
                    .addHeader("Authorization", "Bearer ${apiKey.trim()}")
                    .addHeader("Content-Type", "application/json")
                    .addHeader("HTTP-Referer", "https://github.com/MetrolistGroup/Metrolist")
                    .addHeader("X-Title", "Metrolist")
                    .post(body.toString().toRequestBody(jsonMediaType))
                    .build()
            try {
                http.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        Timber.w("DjHostTts: speech HTTP ${response.code}")
                        return@withContext false
                    }
                    val bytes = response.body.bytes()
                    if (bytes.isEmpty()) return@withContext false
                    val file = File(appContext.cacheDir, "dj6_${UUID.randomUUID()}.mp3")
                    file.writeBytes(bytes)
                    try {
                        playFile(file)
                    } finally {
                        file.delete()
                    }
                }
            } catch (e: Exception) {
                Timber.w(e, "DjHostTts: cloud speak failed")
                false
            }
        }

    private suspend fun playFile(file: File): Boolean =
        suspendCancellableCoroutine { cont ->
            speaking.set(true)
            val player = MediaPlayer()
            mediaPlayer = player
            try {
                player.setDataSource(file.absolutePath)
                player.setOnCompletionListener {
                    speaking.set(false)
                    try {
                        player.release()
                    } catch (_: Exception) {
                    }
                    if (mediaPlayer === player) mediaPlayer = null
                    if (cont.isActive) cont.resume(true)
                }
                player.setOnErrorListener { _, _, _ ->
                    speaking.set(false)
                    try {
                        player.release()
                    } catch (_: Exception) {
                    }
                    if (mediaPlayer === player) mediaPlayer = null
                    if (cont.isActive) cont.resume(false)
                    true
                }
                cont.invokeOnCancellation { stop() }
                player.prepare()
                player.start()
            } catch (e: Exception) {
                Timber.w(e, "DjHostTts: MediaPlayer failed")
                speaking.set(false)
                try {
                    player.release()
                } catch (_: Exception) {
                }
                mediaPlayer = null
                if (cont.isActive) cont.resume(false)
            }
        }

    private suspend fun speakSystem(text: String): Boolean {
        val engine = tts ?: return false
        if (!ready.get()) return false
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

    companion object {
        fun speechEndpoint(chatCompletionsUrl: String): String {
            val trimmed = chatCompletionsUrl.trim().trimEnd('/')
            return when {
                trimmed.endsWith("/chat/completions") ->
                    trimmed.removeSuffix("/chat/completions") + "/audio/speech"
                trimmed.endsWith("/v1") -> "$trimmed/audio/speech"
                else -> "https://openrouter.ai/api/v1/audio/speech"
            }
        }
    }
}
