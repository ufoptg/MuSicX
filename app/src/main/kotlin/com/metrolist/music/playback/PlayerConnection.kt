/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.playback

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM
import androidx.media3.common.Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM
import androidx.media3.common.Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM
import androidx.media3.common.Player.REPEAT_MODE_OFF
import androidx.media3.common.Player.STATE_ENDED
import androidx.media3.common.Timeline
import androidx.media3.exoplayer.ExoPlayer
import com.metrolist.music.constants.SleepTimerCustomDaysKey
import com.metrolist.music.constants.SleepTimerDayTimesKey
import com.metrolist.music.constants.SleepTimerDefaultKey
import com.metrolist.music.constants.SleepTimerEnabledKey
import com.metrolist.music.constants.SleepTimerEndTimeKey
import com.metrolist.music.constants.SleepTimerRepeatKey
import com.metrolist.music.constants.SleepTimerStartTimeKey
import com.metrolist.music.db.MusicDatabase
import com.metrolist.music.extensions.currentMetadata
import com.metrolist.music.models.MediaMetadata
import com.metrolist.music.extensions.getCurrentQueueIndex
import com.metrolist.music.extensions.getQueueWindows
import com.metrolist.music.extensions.metadata
import com.metrolist.music.extensions.togglePlayPause
import com.metrolist.music.playback.MusicService.MusicBinder
import com.metrolist.music.playback.queues.Queue
import com.metrolist.music.utils.dataStore
import com.metrolist.music.utils.get
import com.metrolist.music.utils.reportException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerConnection(
    context: Context,
    binder: MusicBinder,
    val database: MusicDatabase,
    scope: CoroutineScope,
) : Player.Listener {
    private companion object {
        private const val TAG = "PlayerConnection"
    }

    val service = binder.service
    private val playerReadinessFlow = service.isPlayerReady

    /**
     * Public accessor for player. Returns the player once available.
     * Throws IllegalStateException if player is not yet initialized.
     * Callers should check [isPlayerInitialized] or [isReady] before calling.
     */
    val player: ExoPlayer
        get() = _player ?: throw IllegalStateException(
            "Player not yet initialized. Check isPlayerInitialized or isReady first."
        )

    /** Tracks whether player initialization completed successfully */
    private val _isPlayerInitialized = MutableStateFlow(false)

    /** Read-only state flow for player initialization status */
    val isPlayerInitialized = _isPlayerInitialized.asStateFlow()

    /** Returns true when player is fully ready for use */
    val isReady: Boolean
        get() = _player != null && _isPlayerInitialized.value

    /** Captured player instance - set asynchronously after initialization */
    private var _player: ExoPlayer? = null

    /** Track players we've attached listeners to, to avoid duplicate listeners */
    private val playerListeners = mutableSetOf<ExoPlayer>()

    /** Flag to prevent re-attachment after disposal */
    @Volatile
    private var isDisposed = false

    /** Job for the readiness wait coroutine - can be cancelled on dispose */
    private var readinessJob: kotlinx.coroutines.Job? = null

    val playbackState = MutableStateFlow(Player.STATE_IDLE)
    private val playWhenReady = MutableStateFlow(false)
    val isPlaying: kotlinx.coroutines.flow.StateFlow<Boolean>

    init {
        Timber.tag(TAG).d("PlayerConnection init: playerReady=${playerReadinessFlow.value}")

        isPlaying =
            combine(playbackState, playWhenReady) { state, ready ->
                ready && state != STATE_ENDED
            }.stateIn(
                scope,
                SharingStarted.Eagerly, // Use Eagerly to get immediate initial value
                // Initial value will be set after player is initialized
                false,
            )

        // If player is already ready, initialize immediately
        if (playerReadinessFlow.value) {
            initializePlayer()
        } else {
            // Otherwise, wait for player to become ready asynchronously
            readinessJob = scope.launch {
                Timber.tag(TAG).d("Player not ready yet, waiting for initialization...")
                try {
                    val isReady = withTimeoutOrNull(15_000L) {
                        playerReadinessFlow.first { it }
                    }
                    if (isReady == true) {
                        initializePlayer()
                    } else {
                        Timber.tag(TAG).w("Player readiness timeout")
                        // Still try to initialize on timeout - service might have become ready
                        initializePlayer()
                    }
                } catch (e: java.util.concurrent.CancellationException) {
                    Timber.tag(TAG).d("Player readiness wait cancelled")
                    throw e // Re-throw cancellation
                } catch (e: Exception) {
                    Timber.tag(TAG).e(e, "Error waiting for player readiness")
                    initializePlayer()
                }
            }
        }
    }

    /**
     * Initialize player and attach listeners.
     * Must be called after player is ready.
     * @return true if initialization succeeded, false otherwise
     */
    private fun initializePlayer(): Boolean {
        // Guard against re-attachment after disposal
        if (isDisposed) {
            Timber.tag(TAG).d("initializePlayer called after disposal, ignoring")
            return false
        }

        return try {
            val exoPlayer = service.player
            _player = exoPlayer

            // Initialize state from player
            playbackState.value = exoPlayer.playbackState
            playWhenReady.value = exoPlayer.playWhenReady
            mediaMetadata.value = exoPlayer.currentMetadata

            // Attach listener only if not already attached
            if (exoPlayer !in playerListeners) {
                exoPlayer.addListener(this)
                playerListeners.add(exoPlayer)
            }

            // Only mark as initialized AFTER listener is attached
            _isPlayerInitialized.value = true

            Timber.tag(TAG).d("PlayerConnection initialized successfully with player: $exoPlayer")
            true
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to initialize player")
            false
        }
    }

    // Effective playing state, considers Cast when active
    val isEffectivelyPlaying =
        combine(
            isPlaying,
            service.castConnectionHandler?.isCasting ?: MutableStateFlow(false),
            service.castConnectionHandler?.castIsPlaying ?: MutableStateFlow(false),
        ) { localPlaying, isCasting, castPlaying ->
            if (isCasting) castPlaying else localPlaying
        }.stateIn(
            scope,
            SharingStarted.Lazily,
            false,
        )

    val mediaMetadata = MutableStateFlow<MediaMetadata?>(null)
    val currentSong =
        mediaMetadata.flatMapLatest {
            database.song(it?.id)
        }
    val currentLyrics =
        mediaMetadata.flatMapLatest { mediaMetadata ->
            database.lyrics(mediaMetadata?.id)
        }
    val currentFormat =
        mediaMetadata.flatMapLatest { mediaMetadata ->
            database.format(mediaMetadata?.id)
        }

    val queueTitle = MutableStateFlow<String?>(null)
    val queueWindows = MutableStateFlow<List<Timeline.Window>>(emptyList())
    val currentMediaItemIndex = MutableStateFlow(-1)
    val currentWindowIndex = MutableStateFlow(-1)

    val shuffleModeEnabled = MutableStateFlow(false)
    val repeatMode = MutableStateFlow(REPEAT_MODE_OFF)

    val canSkipPrevious = MutableStateFlow(true)
    val canSkipNext = MutableStateFlow(true)

    val error = MutableStateFlow<PlaybackException?>(null)
    val isMuted = service.isMuted
    val currentStreamClient = service.currentStreamClient

    val waitingForNetworkConnection = service.waitingForNetworkConnection

    // Callback to check if playback changes should be blocked (e.g., Listen Together guest)
    var shouldBlockPlaybackChanges: (() -> Boolean)? = null

    // Flag to allow internal sync operations to bypass blocking (set by ListenTogetherManager)
    @Volatile
    var allowInternalSync: Boolean = false

    var onSkipPrevious: (() -> Unit)? = null
    var onSkipNext: (() -> Unit)? = null

    private var attachedPlayer: Player? = null

    init {
        try {
            // Observe player changes (e.g. crossfade swap)
            scope.launch {
                service.playerFlow.collect { newPlayer ->
                    if (newPlayer != null && newPlayer != attachedPlayer) {
                        updateAttachedPlayer(newPlayer)
                    }
                }
            }
            // Initial setup if flow hasn't emitted yet but service is ready
            if (attachedPlayer == null && service.isPlayerReady.value) {
                try {
                    updateAttachedPlayer(service.player)
                } catch (e: Exception) {
                    Timber.tag(TAG).w(e, "Player not yet available during init, will retry when ready")
                }
            }

            Timber.tag(TAG).d("PlayerConnection flow observer registered")
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to initialize PlayerConnection listener or state")
            // Don't throw - allow non-blocking initialization
        }
    }

    private fun updateAttachedPlayer(newPlayer: Player) {
        // Guard against re-attachment after disposal
        if (isDisposed) {
            Timber.tag(TAG).d("updateAttachedPlayer called after disposal, ignoring")
            return
        }

        attachedPlayer?.removeListener(this)
        attachedPlayer = newPlayer

        // Keep _player in sync with the attached player for isReady getter
        // Cast to ExoPlayer since service.playerFlow emits ExoPlayer
        if (newPlayer is ExoPlayer) {
            // Remove listener from old player if tracked
            _player?.let { oldPlayer ->
                if (oldPlayer in playerListeners) {
                    oldPlayer.removeListener(this)
                    playerListeners.remove(oldPlayer)
                }
            }

            _player = newPlayer

            // Attach listener only if not already attached
            if (newPlayer !in playerListeners) {
                newPlayer.addListener(this)
                playerListeners.add(newPlayer)
            }

            // Only mark as initialized AFTER listener is attached
            _isPlayerInitialized.value = true
        }

        // Refresh all state from new player
        playbackState.value = newPlayer.playbackState
        playWhenReady.value = newPlayer.playWhenReady
        mediaMetadata.value = newPlayer.currentMetadata
        queueTitle.value = service.queueTitle
        queueWindows.value = newPlayer.getQueueWindows()
        currentWindowIndex.value = newPlayer.getCurrentQueueIndex()
        currentMediaItemIndex.value = newPlayer.currentMediaItemIndex
        shuffleModeEnabled.value = newPlayer.shuffleModeEnabled
        repeatMode.value = newPlayer.repeatMode
        Timber.tag(TAG).d("Attached to new player instance: $newPlayer")
    }

    fun playQueue(queue: Queue) {
        // Block if Listen Together guest (unless internal sync)
        if (!allowInternalSync && shouldBlockPlaybackChanges?.invoke() == true) {
            Timber.tag("PlayerConnection").d("playQueue blocked - Listen Together guest")
            return
        }
        if (!playerReadinessFlow.value) {
            Timber.tag(TAG).w("playQueue called before player ready; delegating to service")
        }
        try {
            service.playQueue(queue)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error in playQueue")
            throw e
        }
    }

    fun startRadioSeamlessly() {
        // Block if Listen Together guest
        if (shouldBlockPlaybackChanges?.invoke() == true) {
            Timber.tag("PlayerConnection").d("startRadioSeamlessly blocked - Listen Together guest")
            return
        }
        if (!playerReadinessFlow.value) {
            Timber.tag(TAG).w("startRadioSeamlessly called before player ready; delegating to service")
        }
        try {
            service.startRadioSeamlessly()
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error in startRadioSeamlessly")
            throw e
        }
    }

    fun playNext(item: MediaItem) = playNext(listOf(item))

    fun playNext(items: List<MediaItem>) {
        // Block if Listen Together guest (unless internal sync)
        if (!allowInternalSync && shouldBlockPlaybackChanges?.invoke() == true) {
            Timber.tag("PlayerConnection").d("playNext blocked - Listen Together guest")
            return
        }
        try {
            service.playNext(items)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error in playNext")
            throw e
        }
    }

    fun addToQueue(item: MediaItem) = addToQueue(listOf(item))

    fun addToQueue(items: List<MediaItem>) {
        // Block if Listen Together guest (unless internal sync)
        if (!allowInternalSync && shouldBlockPlaybackChanges?.invoke() == true) {
            Timber.tag("PlayerConnection").d("addToQueue blocked - Listen Together guest")
            return
        }
        try {
            service.addToQueue(items)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error in addToQueue")
            throw e
        }
    }

    fun toggleLike() {
        try {
            service.toggleLike()
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error in toggleLike")
        }
    }

    fun toggleMute() {
        service.toggleMute()
    }

    fun setMuted(muted: Boolean) {
        service.setMuted(muted)
    }

    fun toggleLibrary() {
        try {
            service.toggleLibrary()
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error in toggleLibrary")
        }
    }

    /**
     * Toggle play/pause - handles Cast when active
     */
    fun togglePlayPause() {
        if (!allowInternalSync && shouldBlockPlaybackChanges?.invoke() == true) return
        try {
            val castHandler = service.castConnectionHandler
            if (castHandler?.isCasting?.value == true) {
                if (castHandler.castIsPlaying.value) {
                    castHandler.pause()
                } else {
                    castHandler.play()
                }
            } else {
                player.togglePlayPause()
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error in togglePlayPause")
        }
    }

    /**
     * Start playback - handles Cast when active
     */
    fun play() {
        try {
            val castHandler = service.castConnectionHandler
            if (castHandler?.isCasting?.value == true) {
                castHandler.play()
            } else {
                if (player.playbackState == Player.STATE_IDLE) {
                    player.prepare()
                }
                player.playWhenReady = true
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error in play")
        }
    }

    /**
     * Pause playback - handles Cast when active
     */
    fun pause() {
        try {
            val castHandler = service.castConnectionHandler
            if (castHandler?.isCasting?.value == true) {
                castHandler.pause()
            } else {
                player.playWhenReady = false
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error in pause")
        }
    }

    /**
     * Seek to position - handles Cast when active
     */
    fun seekTo(position: Long) {
        try {
            val castHandler = service.castConnectionHandler
            if (castHandler?.isCasting?.value == true) {
                castHandler.seekTo(position)
            } else {
                player.seekTo(position)
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error in seekTo")
        }
    }

    fun seekToNext() {
        try {
            // When casting, use Cast skip instead of local player
            val castHandler = service.castConnectionHandler
            if (castHandler?.isCasting?.value == true) {
                castHandler.skipToNext()
                return
            }
            player.seekToNext()
            if (player.playbackState == Player.STATE_IDLE || player.playbackState == Player.STATE_ENDED) {
                player.prepare()
            }
            player.playWhenReady = true
            onSkipNext?.invoke()
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error in seekToNext")
        }
    }

    var onRestartSong: (() -> Unit)? = null

    fun seekToPrevious() {
        try {
            // When casting, use Cast skip instead of local player
            val castHandler = service.castConnectionHandler
            if (castHandler?.isCasting?.value == true) {
                castHandler.skipToPrevious()
                return
            }

            // Logic to mimic standard seekToPrevious behavior but with explicit callbacks
            // If we are more than 3 seconds in, just restart the song
            if (player.currentPosition > 3000 || !player.hasPreviousMediaItem()) {
                player.seekTo(0)
                if (player.playbackState == Player.STATE_IDLE || player.playbackState == Player.STATE_ENDED) {
                    player.prepare()
                }
                player.playWhenReady = true
                onRestartSong?.invoke()
            } else {
                // Otherwise go to previous media item
                player.seekToPreviousMediaItem()
                if (player.playbackState == Player.STATE_IDLE || player.playbackState == Player.STATE_ENDED) {
                    player.prepare()
                }
                player.playWhenReady = true
                onSkipPrevious?.invoke()
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error in seekToPrevious")
        }
    }

    /** Parses "0=09:00-23:00;1=22:00-06:00" into Map<dayIndex, Pair<start, end>>. */
    private fun parseDayTimes(raw: String): Map<Int, Pair<String, String>> {
        if (raw.isBlank()) return emptyMap()
        return raw
            .split(";")
            .mapNotNull { entry ->
                val parts = entry.split("=")
                if (parts.size != 2) return@mapNotNull null
                val dayIndex = parts[0].toIntOrNull() ?: return@mapNotNull null
                val times = parts[1].split("-")
                if (times.size != 2) return@mapNotNull null
                dayIndex to (times[0] to times[1])
            }.toMap()
    }

    private fun checkAndStartAutomaticSleepTimer(): Boolean {
        return try {
            val sleepTimerEnabled = service.applicationContext.dataStore.get(SleepTimerEnabledKey) ?: false
            Timber.tag(TAG).d("✓ Sleep Timer Check: enabled=$sleepTimerEnabled")

            if (!sleepTimerEnabled) {
                Timber.tag(TAG).d("✗ Sleep Timer disabled - skipping")
                return false
            }

            if (service.sleepTimer.isActive) {
                Timber.tag(TAG).d("✗ Sleep Timer already active - skipping")
                return false
            }

            val sleepTimerRepeat = service.applicationContext.dataStore.get(SleepTimerRepeatKey) ?: "daily"
            val sleepTimerStartTime = service.applicationContext.dataStore.get(SleepTimerStartTimeKey) ?: "09:00"
            val sleepTimerEndTime = service.applicationContext.dataStore.get(SleepTimerEndTimeKey) ?: "23:00"
            val sleepTimerDefaultMinutes = (service.applicationContext.dataStore.get(SleepTimerDefaultKey) ?: 30f).roundToInt()
            val sleepTimerCustomDaysStr = service.applicationContext.dataStore.get(SleepTimerCustomDaysKey) ?: "0,1,2,3,4"
            val sleepTimerDayTimesStr = service.applicationContext.dataStore.get(SleepTimerDayTimesKey) ?: ""

            Timber
                .tag(
                    TAG,
                ).d(
                    "Sleep Timer Config: repeat=$sleepTimerRepeat start=$sleepTimerStartTime end=$sleepTimerEndTime default=$sleepTimerDefaultMinutes custom=$sleepTimerCustomDaysStr",
                )

            val currentTime = LocalTime.now()
            val today = LocalDate.now()
            val dayOfWeek = today.dayOfWeek.value % 7
            val adjustedDayOfWeek = if (dayOfWeek == 0) 6 else dayOfWeek - 1

            Timber.tag(TAG).d("Current: time=$currentTime dayOfWeek=$adjustedDayOfWeek")

            val isDayAllowed =
                when (sleepTimerRepeat) {
                    "daily" -> {
                        true
                    }

                    "weekdays" -> {
                        adjustedDayOfWeek in 0..4
                    }

                    "weekends" -> {
                        adjustedDayOfWeek in 5..6
                    }

                    "weekdays_weekends" -> {
                        true
                    }

                    // both groups active; per-day time handles the distinction
                    "custom" -> {
                        val customDays = sleepTimerCustomDaysStr.split(",").mapNotNull { it.trim().toIntOrNull() }
                        Timber.tag(TAG).d("Custom days: $customDays, adjustedDayOfWeek=$adjustedDayOfWeek")
                        adjustedDayOfWeek in customDays
                    }

                    else -> {
                        false
                    }
                }

            if (!isDayAllowed) {
                Timber.tag(TAG).d("✗ Day not allowed for Sleep Timer")
                return false
            }

// "daily" uses the single global time window.
// All other modes store per-day times in the dayTimes map so that
// e.g. weekdays and weekends can have different windows.
            val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
            val usesDayTimesMap = sleepTimerRepeat != "daily"
            val (startStr, endStr) =
                if (usesDayTimesMap) {
                    parseDayTimes(sleepTimerDayTimesStr)[adjustedDayOfWeek]
                        ?: (sleepTimerStartTime to sleepTimerEndTime)
                } else {
                    sleepTimerStartTime to sleepTimerEndTime
                }

            val startTime = LocalTime.parse(startStr, timeFormatter)
            val endTime = LocalTime.parse(endStr, timeFormatter)

            // Support overnight ranges (e.g. 22:00–06:00) in addition to normal ranges
            val isTimeInRange =
                if (endTime.isAfter(startTime)) {
                    currentTime.isAfter(startTime) && currentTime.isBefore(endTime)
                } else {
                    currentTime.isAfter(startTime) || currentTime.isBefore(endTime)
                }

            Timber.tag(TAG).d("Time check: $currentTime between $startStr-$endStr? $isTimeInRange")

            if (isTimeInRange) {
                Timber.tag(TAG).i("AUTO SLEEP TIMER STARTED: $sleepTimerDefaultMinutes minutes")
                service.sleepTimer.start(sleepTimerDefaultMinutes)
                return true
            }

            Timber.tag(TAG).d("✗ Time not in range")
            return false
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Sleep Timer error")
            return false
        }
    }

    override fun onPlaybackStateChanged(state: Int) {
        playbackState.value = state
        error.value = player.playerError
    }

    override fun onPlayWhenReadyChanged(
        newPlayWhenReady: Boolean,
        reason: Int,
    ) {
        val wasPlaying = playWhenReady.value
        playWhenReady.value = newPlayWhenReady

        // Central sleep timer trigger: fires on every paused -> playing transition,
        if (newPlayWhenReady && !wasPlaying) {
            checkAndStartAutomaticSleepTimer()
        }
    }

    override fun onMediaItemTransition(
        mediaItem: MediaItem?,
        reason: Int,
    ) {
        mediaMetadata.value = mediaItem?.metadata
        currentMediaItemIndex.value = player.currentMediaItemIndex
        currentWindowIndex.value = player.getCurrentQueueIndex()
        updateCanSkipPreviousAndNext()
    }

    override fun onTimelineChanged(
        timeline: Timeline,
        reason: Int,
    ) {
        queueWindows.value = player.getQueueWindows()
        queueTitle.value = service.queueTitle
        currentMediaItemIndex.value = player.currentMediaItemIndex
        currentWindowIndex.value = player.getCurrentQueueIndex()
        updateCanSkipPreviousAndNext()
    }

    override fun onShuffleModeEnabledChanged(enabled: Boolean) {
        shuffleModeEnabled.value = enabled
        queueWindows.value = player.getQueueWindows()
        currentWindowIndex.value = player.getCurrentQueueIndex()
        updateCanSkipPreviousAndNext()
    }

    override fun onRepeatModeChanged(mode: Int) {
        repeatMode.value = mode
        updateCanSkipPreviousAndNext()
    }

    override fun onPlayerErrorChanged(playbackError: PlaybackException?) {
        if (playbackError != null) {
            reportException(playbackError)
        }
        error.value = playbackError
    }

    private fun updateCanSkipPreviousAndNext() {
        if (!player.currentTimeline.isEmpty) {
            val window =
                player.currentTimeline.getWindow(player.currentMediaItemIndex, Timeline.Window())
            canSkipPrevious.value = player.isCommandAvailable(COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM) ||
                !window.isLive ||
                player.isCommandAvailable(COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
            canSkipNext.value = window.isLive &&
                window.isDynamic ||
                player.isCommandAvailable(COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
        } else {
            canSkipPrevious.value = false
            canSkipNext.value = false
        }
    }

    fun dispose() {
        try {
            // Mark as disposed first to prevent re-attachment
            isDisposed = true

            // Cancel the readiness wait coroutine if still running
            readinessJob?.cancel()
            readinessJob = null

            // Remove listener from all tracked players
            playerListeners.forEach { player ->
                player.removeListener(this)
            }
            playerListeners.clear()

            attachedPlayer?.removeListener(this)
            attachedPlayer = null

            // Clear state to prevent stale references
            _player = null
            _isPlayerInitialized.value = false

            Timber.tag(TAG).d("PlayerConnection disposed successfully")
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error during PlayerConnection disposal")
        }
    }
}
