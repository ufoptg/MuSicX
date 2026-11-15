package dev.musicx.desktop.state.playback

import dev.musicx.shared.model.RadioQueuePage
import dev.musicx.shared.model.RadioRecommendationHistory
import dev.musicx.shared.model.SongItem
import dev.musicx.shared.model.buildRadioQueueRecommendations
import dev.musicx.shared.playback.MusicQueue
import dev.musicx.shared.playback.QUEUE_SOURCE_AUTO_MIX
import dev.musicx.shared.settings.BlockedContent
import dev.musicx.shared.settings.Settings
import dev.musicx.shared.state.GlobalYouTubeRepository
import dev.musicx.shared.utils.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class DesktopQueueRadio(
    private val scope: CoroutineScope,
    private val queue: MusicQueue,
    private val onQueueChanged: () -> Unit,
    private val radio: suspend (String) -> RadioQueuePage = { GlobalYouTubeRepository.instance.startRadioQueue(it) },
    private val isBlocked: (SongItem) -> Boolean = BlockedContent::isBlocked,
    private val continueRadio: suspend (String) -> RadioQueuePage = { GlobalYouTubeRepository.instance.continueRadioQueue(it) },
    private val autoLoadMore: () -> Boolean = { Settings.shared.autoLoadMore },
    private val recommendationHistory: RadioRecommendationHistory = RadioRecommendationHistory(),
) {
    private val mutex = Mutex()
    private var prefetchJob: Job? = null
    private val queuedIds = linkedSetOf<String>()
    private var continuation: String? = null

    fun reset() {
        cancelPrefetch()
        continuation = null
    }

    fun cancelPrefetch() {
        prefetchJob?.cancel()
        prefetchJob = null
    }

    fun syncQueue() {
        queuedIds.clear()
        queuedIds.addAll(
            queue.items.value
                .map(SongItem::id)
                .filter(String::isNotBlank),
        )
    }

    fun prefetch(
        seed: SongItem,
        shouldCommit: () -> Boolean,
    ) {
        if (prefetchJob?.isActive == true) return
        var job: Job? = null
        job =
            scope.launch {
                try {
                    load(6, seed, playImmediately = false, shouldCommit)
                } finally {
                    if (prefetchJob === job) prefetchJob = null
                }
            }
        prefetchJob = job
    }

    suspend fun load(
        limit: Int,
        seed: SongItem,
        playImmediately: Boolean,
        shouldCommit: () -> Boolean,
    ): SongItem? {
        fun canLoad() = autoLoadMore() && shouldCommit()
        if (!canLoad()) return null
        return mutex.withLock {
            if (!canLoad()) return@withLock null
            if (playImmediately && queue.currentIndex.value + 1 < queue.items.value.size) {
                return@withLock queue.getNext()?.also { onQueueChanged() }
            }
            if (seed.id.isBlank()) return@withLock null
            try {
                val token = continuation
                val firstPage = if (token == null) radio(seed.id) else continueRadio(token)
                val page =
                    recommendationHistory.select(
                        firstPage = firstPage,
                        filter = { songs ->
                            buildRadioQueueRecommendations(
                                songs,
                                queue.items.value.mapTo(mutableSetOf(), SongItem::id) + queuedIds + seed.id,
                                QUEUE_SOURCE_AUTO_MIX,
                                isBlocked,
                                songsOnly = queue.recommendationsSongsOnly,
                            )
                        },
                        isCurrent = ::canLoad,
                        loadMore = continueRadio,
                    ) ?: return@withLock null
                continuation = page.continuation
                val candidates = page.songs.take(limit)
                var changed = false
                for (song in candidates) {
                    if (song.id in queuedIds || queue.items.value.any { it.id == song.id }) continue
                    queue.addToQueue(song.copy(queueSourceTitle = QUEUE_SOURCE_AUTO_MIX))
                    queuedIds.add(song.id)
                    changed = true
                }
                recommendationHistory.remember(candidates)
                val next = if (playImmediately) queue.getNext() else null
                if (changed || next != null) onQueueChanged()
                next
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Logger.w("PlayerState", "Failed to load recommendations (${error::class.simpleName})")
                null
            }
        }
    }
}
