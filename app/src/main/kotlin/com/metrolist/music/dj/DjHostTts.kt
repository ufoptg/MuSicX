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
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

/**
 * DJ host voice: OpenRouter cloud TTS (Deepgram Flux by default), else on-device TextToSpeech.
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
            val model =
                normalizeTtsModel(appContext.dataStore.get(AiDjTtsModelKey, DEFAULT_AI_DJ_TTS_MODEL))
            val voice =
                normalizeTtsVoice(appContext.dataStore.get(AiDjTtsVoiceKey, DEFAULT_AI_DJ_TTS_VOICE))

            // Prefer mp3; Deepgram Flux often returns PCM — retry pcm if needed.
            for (format in listOf("mp3", "pcm")) {
                val ok = requestAndPlay(speechUrl, apiKey, model, voice, text, format)
                if (ok) return@withContext true
            }
            false
        }

    private suspend fun requestAndPlay(
        speechUrl: String,
        apiKey: String,
        model: String,
        voice: String,
        text: String,
        format: String,
    ): Boolean {
        val body =
            buildJsonObject {
                put("model", model)
                put("input", text)
                put("voice", voice)
                put("response_format", format)
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
        return try {
            val (bytes, contentType) =
                withContext(Dispatchers.IO) {
                    http.newCall(request).execute().use { response ->
                        val bodyBytes = response.body.bytes()
                        if (!response.isSuccessful) {
                            val err = bodyBytes.decodeToString().take(300)
                            Timber.w(
                                "DjHostTts: speech HTTP ${response.code} format=$format " +
                                    "model=$model voice=$voice err=$err",
                            )
                            return@withContext null to ""
                        }
                        bodyBytes to response.header("Content-Type").orEmpty()
                    }
                }
            if (bytes == null || bytes.isEmpty()) return false
            val file =
                when {
                    format == "mp3" || contentType.contains("mpeg") || contentType.contains("mp3") -> {
                        File(appContext.cacheDir, "dj6_${UUID.randomUUID()}.mp3").also {
                            it.writeBytes(bytes)
                        }
                    }
                    else -> {
                        File(appContext.cacheDir, "dj6_${UUID.randomUUID()}.wav").also {
                            it.writeBytes(pcmToWav(bytes, sampleRate = 24000))
                        }
                    }
                }
            try {
                playFile(file)
            } finally {
                file.delete()
            }
        } catch (e: Exception) {
            Timber.w(e, "DjHostTts: cloud speak failed format=$format")
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
                player.setOnErrorListener { _, what, extra ->
                    Timber.w("DjHostTts: MediaPlayer error what=$what extra=$extra")
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

        /** Migrate away from the old OpenAI TTS defaults. */
        fun normalizeTtsModel(model: String): String {
            val m = model.trim()
            return if (m.isBlank() || m.startsWith("openai/")) DEFAULT_AI_DJ_TTS_MODEL else m
        }

        fun normalizeTtsVoice(voice: String): String {
            val v = voice.trim()
            return if (v.isBlank() || !v.startsWith("flux-")) DEFAULT_AI_DJ_TTS_VOICE else v
        }

        fun pcmToWav(
            pcm: ByteArray,
            sampleRate: Int,
            channels: Int = 1,
            bitsPerSample: Int = 16,
        ): ByteArray {
            val byteRate = sampleRate * channels * bitsPerSample / 8
            val blockAlign = (channels * bitsPerSample / 8).toShort()
            val out = ByteArrayOutputStream(44 + pcm.size)
            out.write("RIFF".toByteArray())
            out.write(intLE(36 + pcm.size))
            out.write("WAVE".toByteArray())
            out.write("fmt ".toByteArray())
            out.write(intLE(16))
            out.write(shortLE(1)) // PCM
            out.write(shortLE(channels.toShort()))
            out.write(intLE(sampleRate))
            out.write(intLE(byteRate))
            out.write(shortLE(blockAlign))
            out.write(shortLE(bitsPerSample.toShort()))
            out.write("data".toByteArray())
            out.write(intLE(pcm.size))
            out.write(pcm)
            return out.toByteArray()
        }

        private fun intLE(value: Int): ByteArray =
            ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array()

        private fun shortLE(value: Short): ByteArray =
            ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(value).array()
    }
}
