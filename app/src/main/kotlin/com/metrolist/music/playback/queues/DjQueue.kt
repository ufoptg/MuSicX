/**
 * MuSicX Project (C) 2026
 * Credits to Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.playback.queues

import android.content.Context
import androidx.media3.common.MediaItem
import com.metrolist.innertube.models.WatchEndpoint
import com.metrolist.music.constants.AiDjPersonaKey
import com.metrolist.music.constants.AiDjTalkEnabledKey
import com.metrolist.music.constants.DEFAULT_AI_DJ_PERSONA
import com.metrolist.music.constants.OpenRouterApiKey
import com.metrolist.music.constants.OpenRouterBaseUrlKey
import com.metrolist.music.constants.OpenRouterDefaultBaseUrl
import com.metrolist.music.constants.OpenRouterDefaultModel
import com.metrolist.music.constants.OpenRouterModelKey
import com.metrolist.music.dj.DjEngine
import com.metrolist.music.dj.DjStartRequest
import com.metrolist.music.extensions.metadata
import com.metrolist.music.extensions.toMediaItem
import com.metrolist.music.models.MediaMetadata
import com.metrolist.music.utils.dataStore
import com.metrolist.music.utils.get
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap

/**
 * Endless DJ queue: LLM picks next songs (resolved via YouTube search),
 * with YTM radio as fallback when the LLM path fails.
 */
class DjQueue(
    private val seed: MediaMetadata,
    private val context: Context,
    private val userRequest: String? = null,
    private val persona: String = context.dataStore.get(AiDjPersonaKey, DEFAULT_AI_DJ_PERSONA),
) : Queue {
    override val preloadItem: MediaMetadata = seed

    private val pageMutex = Mutex()
    private val seenIds = LinkedHashSet<String>().apply { add(seed.id) }
    private val recentLabels = mutableListOf("${seed.title} — ${seed.artists.joinToString { it.name }}")
    private val banterByMediaId = ConcurrentHashMap<String, String>()
    private var introAttached = false
    private var radioFallback: YouTubeQueue? = null
    private var llmFailures = 0
    private val parsedRequest = userRequest?.let { DjStartRequest.parsePlayQuery(it) }?.takeIf { it.isNotBlank() }

    fun takeBanter(mediaId: String): String? = banterByMediaId.remove(mediaId)

    override suspend fun getInitialStatus(): Queue.Status =
        withContext(Dispatchers.IO) {
            pageMutex.withLock {
                val extras = fetchAndResolve(batchSize = INITIAL_BATCH)
                Queue.Status(
                    title = "DJ 6",
                    items = listOf(seed.toMediaItem()) + extras,
                    mediaItemIndex = 0,
                )
            }
        }

    override fun hasNextPage(): Boolean =
        llmFailures < MAX_LLM_FAILURES || radioFallback?.hasNextPage() == true

    override suspend fun nextPage(): List<MediaItem> =
        withContext(Dispatchers.IO) {
            pageMutex.withLock {
                val items = fetchAndResolve(batchSize = PAGE_BATCH)
                if (items.isNotEmpty()) return@withLock items
                ensureRadioFallback().nextPage().filter { seenIds.add(it.mediaId) }
            }
        }

    private suspend fun fetchAndResolve(batchSize: Int): List<MediaItem> {
        val talkEnabled = context.dataStore.get(AiDjTalkEnabledKey, true)
        val apiKey = context.dataStore.get(OpenRouterApiKey, "")
        if (apiKey.isBlank()) {
            Timber.w("DjQueue: no API key, using radio fallback")
            llmFailures = MAX_LLM_FAILURES
            return ensureRadioFallback()
                .getInitialStatus()
                .items
                .filter { it.mediaId != seed.id && seenIds.add(it.mediaId) }
                .take(batchSize)
        }

        val llm =
            DjEngine
                .fetchPicks(
                    seedTitle = seed.title,
                    seedArtist = seed.artists.joinToString { it.name },
                    recent = recentLabels.toList(),
                    persona = persona.ifBlank { DEFAULT_AI_DJ_PERSONA },
                    wantBanter = talkEnabled,
                    trackCount = batchSize,
                    apiKey = apiKey,
                    baseUrl = context.dataStore.get(OpenRouterBaseUrlKey, OpenRouterDefaultBaseUrl),
                    model = context.dataStore.get(OpenRouterModelKey, OpenRouterDefaultModel),
                    userRequest = parsedRequest,
                ).getOrElse {
                    Timber.w(it, "DjQueue: LLM pick failed")
                    llmFailures++
                    return emptyList()
                }

        llmFailures = 0
        val previousLabel = recentLabels.lastOrNull()
        val resolved = mutableListOf<MediaItem>()
        for (pick in llm.tracks) {
            val song = DjEngine.resolveTrack(pick.title, pick.artist, seenIds) ?: continue
            if (!seenIds.add(song.id)) continue
            resolved.add(song.toMediaItem())
            recentLabels.add("${song.title} — ${song.artists.joinToString { it.name }}")
            if (recentLabels.size > 40) recentLabels.removeAt(0)
        }

        if (resolved.isEmpty()) {
            llmFailures++
            return emptyList()
        }

        if (talkEnabled) {
            if (!introAttached) {
                val intro =
                    DjEngine.composeHostLine(
                        kind = DjEngine.TalkKind.BANTER,
                        isIntro = true,
                        previous = null,
                        nextTitle = seed.title,
                        nextArtist = seed.artists.joinToString { it.name },
                        flavor = llm.banter,
                        userRequest = parsedRequest,
                    )
                if (intro.isNotBlank()) banterByMediaId[seed.id] = intro
                introAttached = true
            }

            var prev = previousLabel
            var flavorAvailable = llm.banter
            for (item in resolved) {
                val meta = item.metadata ?: continue
                val title = meta.title
                val artist = meta.artists.joinToString { it.name }
                val kind = DjEngine.pickTalkKind(isIntro = false)
                val flavor =
                    if (kind != DjEngine.TalkKind.SILENT && flavorAvailable.isNotBlank()) {
                        flavorAvailable.also { flavorAvailable = "" }
                    } else {
                        ""
                    }
                val line =
                    DjEngine.composeHostLine(
                        kind = kind,
                        isIntro = false,
                        previous = prev,
                        nextTitle = title,
                        nextArtist = artist,
                        flavor = flavor,
                    )
                if (line.isNotBlank()) banterByMediaId[item.mediaId] = line
                prev = "$title — $artist"
            }
        }
        return resolved
    }

    private fun ensureRadioFallback(): YouTubeQueue {
        radioFallback?.let { return it }
        return YouTubeQueue(
            WatchEndpoint(videoId = seed.id, playlistId = "RDAMVM${seed.id}"),
            seed,
        ).also { radioFallback = it }
    }

    companion object {
        private const val INITIAL_BATCH = 3
        private const val PAGE_BATCH = 2
        private const val MAX_LLM_FAILURES = 3

        fun fromSeed(
            context: Context,
            seed: MediaMetadata,
            userRequest: String? = null,
        ): DjQueue =
            DjQueue(
                seed = seed,
                context = context.applicationContext,
                userRequest = userRequest,
            )
    }
}
