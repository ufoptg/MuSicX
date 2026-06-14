package com.metrolist.music.discord

import android.app.Activity
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import timber.log.Timber
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicLong

data class DiscordUser(
    val id: String,
    val username: String,
    val name: String,
    val avatar: String?,
)

object DiscordRpcManager {
    private const val TAG = "DiscordSvc"

    @Volatile
    private var initialized: Boolean = false

    @Volatile
    private var _ready: Boolean = false

    @Volatile
    private var _authorized: Boolean = false

    @Volatile
    private var accessToken: String? = null

    @Volatile
    private var authorizeInProgress: Boolean = false

    @Volatile
    private var lastActivitySentAtMs: Long = 0L

    @Volatile
    private var lastActivity: ActivityPayload? = null

    @Volatile private var currentSongId: String? = null
    @Volatile private var currentIsPlaying: Boolean = false
    private val currentActivityId = AtomicLong(0L)
    @Volatile private var imageResolutionJob: Job? = null
    private val reconnectMutex = Mutex()

    private val _accessTokenFlow = MutableStateFlow<String?>(null)
    val accessTokenFlow: StateFlow<String?> = _accessTokenFlow

    private val _connectionStatus = MutableStateFlow(Status.Disconnected)
    val connectionStatus: StateFlow<Status> = _connectionStatus

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError

    private val _currentUser = MutableStateFlow<DiscordUser?>(null)
    val currentUser: StateFlow<DiscordUser?> = _currentUser

    private val _settingsChanged = MutableStateFlow(0)
    val settingsChanged: StateFlow<Int> = _settingsChanged

    fun notifySettingsChanged() {
        Timber.tag(TAG).d("notifySettingsChanged: incrementing (count=%d)", _settingsChanged.value + 1)
        _settingsChanged.value++
    }

    enum class Status { Disconnected, Authorizing, Connected }

    enum class StatusType(val value: Int) {
        Online(0),
        Idle(3),
        Dnd(4),
    }

    @JvmStatic
    fun onNativeStatusChanged(statusCode: Int, ready: Boolean, authorized: Boolean) {
        Timber.tag(TAG).i(
            "onNativeStatusChanged: no-op stub (statusCode=%d, ready=%s, authorized=%s)",
            statusCode,
            ready,
            authorized,
        )
    }

    fun getAccessToken(): String? = accessToken

    fun isInitialized(): Boolean = initialized

    fun isAuthorized(): Boolean = _authorized

    fun isReady(): Boolean = _ready

    fun isShowingSong(songId: String, isPlaying: Boolean): Boolean {
        val showing = currentSongId == songId && currentIsPlaying == isPlaying && lastActivity != null
        if (!showing) {
            Timber.tag(TAG).d(
                "isShowingSong: false (currentSongId=%s, requestedSongId=%s, currentIsPlaying=%s, requestedIsPlaying=%s, hasActivity=%s)",
                currentSongId, songId, currentIsPlaying, isPlaying, lastActivity != null,
            )
        }
        return showing
    }

    fun clearLastError() {
        _lastError.value = null
    }

    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val appId: String = BuildConfigProvider.appId

    private val auth: DiscordAuth = DiscordAuth()

    private val gateway: DiscordGateway = DiscordGateway(
        appId = appId,
        tokenProvider = { "Bearer ${accessToken ?: ""}" },
        externalScope = scope,
    )

    init {
        scope.launch {
            gateway.events.collect { event -> handleGatewayEvent(event) }
        }
    }

    fun init(context: Context) {
        DiscordTokenStore.init(context.applicationContext)
        if (initialized) {
            Timber.tag(TAG).i("init: already initialized, skipping")
            return
        }
        initialized = true
        _connectionStatus.value = Status.Disconnected
        Timber.tag(TAG).i("init: token store initialized, scheduling auto-rehydrate")

        scope.launch {
            val saved = DiscordTokenStore.retrieveSuspend()
            if (!saved.isNullOrEmpty()) {
                Timber.tag(TAG).i("init: found persisted token, reconnecting")
                reconnectWithToken(saved)
            } else {
                Timber.tag(TAG).i("init: no persisted token, waiting for explicit authorize")
            }
        }
    }

    fun authorize(activity: Activity, onComplete: (Boolean) -> Unit) {
        if (authorizeInProgress) {
            Timber.tag(TAG).w("authorize: already in progress, ignoring double-tap")
            return
        }
        authorizeInProgress = true

        // Short-circuit: already authorized and ready
        if (_ready && _authorized) {
            Timber.tag(TAG).d("authorize: short-circuit — already ready and authorized")
            authorizeInProgress = false
            onComplete(true)
            return
        }
        // Short-circuit: authorized but not ready — reconnect with existing token
        if (_authorized) {
            Timber.tag(TAG).d("authorize: short-circuit — authorized but not ready, reconnecting")
            authorizeInProgress = false
            reconnectWithToken(accessToken ?: "")
            onComplete(true)
            return
        }

        _connectionStatus.value = Status.Authorizing
        _lastError.value = null

        scope.launch {
            try {
                val result = auth.authorize(activity)
                DiscordTokenStore.storeFull(
                    result.accessToken,
                    result.refreshToken,
                    result.expiresInSec,
                )
                accessToken = result.accessToken
                _accessTokenFlow.value = result.accessToken
                _authorized = true

                try {
                    runCatching { gateway.close(4000, "re-authorizing") }
                    gateway.connect()
                    gateway.identify("Bearer ${result.accessToken}")
                    onComplete(true)
                } catch (e: Throwable) {
                    Timber.tag(TAG).e(e, "authorize: gateway connect/identify failed")
                    _lastError.value = "discord_error_loopback_timeout"
                    _connectionStatus.value = Status.Disconnected
                    _ready = false
                    _authorized = false
                    onComplete(false)
                }
            } catch (e: DiscordAuthException.UserCancelled) {
                Timber.tag(TAG).i("authorize: user cancelled")
                _lastError.value = "discord_error_loopback_timeout"
                _connectionStatus.value = Status.Disconnected
                onComplete(false)
            } catch (e: DiscordAuthException.StateMismatch) {
                Timber.tag(TAG).w(e, "authorize: state mismatch")
                _lastError.value = "discord_error_invalid_scope"
                _connectionStatus.value = Status.Disconnected
                onComplete(false)
            } catch (e: DiscordAuthException.NetworkFailure) {
                Timber.tag(TAG).e(e, "authorize: network failure")
                _lastError.value = "discord_error_loopback_timeout"
                _connectionStatus.value = Status.Disconnected
                onComplete(false)
            } catch (e: DiscordAuthException.InvalidGrant) {
                Timber.tag(TAG).w(e, "authorize: invalid grant")
                _lastError.value = "discord_error_token_refresh_failed"
                _connectionStatus.value = Status.Disconnected
                onComplete(false)
            } catch (e: Throwable) {
                Timber.tag(TAG).e(e, "authorize: unexpected failure")
                _lastError.value = "discord_error_loopback_timeout"
                _connectionStatus.value = Status.Disconnected
                onComplete(false)
            } finally {
                authorizeInProgress = false
            }
        }
    }

    fun fetchCurrentUser(token: String): DiscordUser? {
        return try {
            val url = URL("https://discord.com/api/v10/users/@me")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            conn.requestMethod = "GET"
            conn.setRequestProperty("Authorization", "Bearer $token")
            conn.setRequestProperty("Accept", "application/json")

            val responseCode = conn.responseCode
            val responseBody = if (responseCode in 200..299) {
                conn.inputStream.bufferedReader().readText()
            } else {
                conn.errorStream?.bufferedReader()?.readText() ?: ""
            }
            conn.disconnect()

            if (responseCode !in 200..299) {
                Timber.tag(TAG).w("fetchCurrentUser: HTTP $responseCode body=$responseBody")
                return null
            }

            val json = JSONObject(responseBody)
            val id = json.getString("id")
            val username = json.getString("username")
            val name = json.optString("global_name", username)
            val avatarHash = json.optString("avatar")
            val avatar = if (avatarHash.isNotEmpty() && avatarHash != "null") {
                "https://cdn.discordapp.com/avatars/$id/$avatarHash.png?t=${System.currentTimeMillis()}"
            } else null

            DiscordUser(id, username, name, avatar)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "fetchCurrentUser: exception")
            null
        }
    }

    fun setActivity(
        activity: DiscordActivity,
        songId: String? = null,
        isPlaying: Boolean = true,
        status: PresenceStatus = PresenceStatus.Online,
    ) {
        if (!_ready) {
            Timber.tag(TAG).w("setActivity: skipping — not ready (name=%s)", activity.name)
            return
        }

        val stateChanged = songId != currentSongId || isPlaying != currentIsPlaying

        val now = System.currentTimeMillis()
        if (!stateChanged &&
            lastActivitySentAtMs > 0L && (now - lastActivitySentAtMs) < 2_000L
        ) {
            currentActivityId.incrementAndGet()
            imageResolutionJob?.cancel()
            Timber.tag(TAG).i("setActivity: debounced (<2s since last, stateChanged=%s)", stateChanged)
            return
        }
        lastActivitySentAtMs = now

        currentSongId = songId
        currentIsPlaying = isPlaying
        currentActivityId.incrementAndGet()

        val buttons = buildList {
            if (!activity.button1Label.isNullOrEmpty() && !activity.button1Url.isNullOrEmpty()) {
                add(activity.button1Label to activity.button1Url)
            }
            if (!activity.button2Label.isNullOrEmpty() && !activity.button2Url.isNullOrEmpty()) {
                add(activity.button2Label to activity.button2Url)
            }
        }
        Timber.tag(TAG).d("setActivity: built %d buttons", buttons.size)

        val payloadNoImages = DiscordPresence.buildActivity(
            name = activity.name.orEmpty(),
            type = activityTypeToEnum(activity.activityType),
            details = activity.details,
            state = activity.state,
            startMs = activity.startTimestamp.takeIf { it > 0L },
            endMs = activity.endTimestamp?.takeIf { it > 0L },
            buttons = buttons,
        )

        lastActivity = payloadNoImages

        try {
            val presenceJson = DiscordPresence.buildPresenceUpdate(
                status = status,
                activities = listOf(payloadNoImages),
            )
            Timber.tag(TAG).i("setActivity: sending (type=%d, name=%s, details=%s, state=%s, songId=%s, isPlaying=%s, buttons=%d)",
                activity.activityType, activity.name, activity.details, activity.state, songId, isPlaying, buttons.size)
            gateway.presenceUpdate(presenceJson)
        } catch (e: IllegalStateException) {
            Timber.tag(TAG).w(e, "setActivity: gateway not open")
        } catch (e: Throwable) {
            Timber.tag(TAG).e(e, "setActivity: send failed")
        }

        imageResolutionJob?.cancel()

        val currentToken = accessToken ?: return
        val largeImageUrl = activity.largeImage
        val smallImageUrl = activity.smallImage
        if (largeImageUrl.isNullOrEmpty() && smallImageUrl.isNullOrEmpty()) return

        Timber.tag(TAG).d(
            "setActivity: resolving images — large=%s, small=%s",
            largeImageUrl?.take(80),
            smallImageUrl?.take(80),
        )

        val activityIdAtLaunch = currentActivityId.get()
        val songIdAtLaunch = songId

        imageResolutionJob = scope.launch {
            val tokenHeader = "Bearer $currentToken"
            val largeResolved = if (!largeImageUrl.isNullOrEmpty()) {
                DiscordExternalAssets.resolve(largeImageUrl, appId, tokenHeader)
            } else null
            val smallResolved = if (!smallImageUrl.isNullOrEmpty()) {
                DiscordExternalAssets.resolve(smallImageUrl, appId, tokenHeader)
            } else null

            if (largeResolved == null && smallResolved == null) {
                Timber.tag(TAG).i("setActivity: image resolution returned null, keeping text-only presence")
                return@launch
            }

            if (activityIdAtLaunch != currentActivityId.get()) {
                Timber.tag(TAG).i(
                    "setActivity: stale image resolution (launched activityId=%d, current=%d), skipping re-send",
                    activityIdAtLaunch, currentActivityId.get(),
                )
                return@launch
            }

            val payloadWithImages = DiscordPresence.buildActivity(
                name = activity.name.orEmpty(),
                type = activityTypeToEnum(activity.activityType),
                details = activity.details,
                state = activity.state,
                largeImage = largeResolved,
                largeText = activity.largeText,
                smallImage = smallResolved,
                smallText = activity.smallText,
                startMs = activity.startTimestamp.takeIf { it > 0L },
                endMs = activity.endTimestamp?.takeIf { it > 0L },
                buttons = buttons,
            )

            lastActivity = payloadWithImages

            try {
                val presenceJson = DiscordPresence.buildPresenceUpdate(
                    status = status,
                    activities = listOf(payloadWithImages),
                )
                Timber.tag(TAG).i("setActivity: re-sending with images for songId=%s", songIdAtLaunch)
                gateway.presenceUpdate(presenceJson)
            } catch (e: IllegalStateException) {
                Timber.tag(TAG).w(e, "setActivity: image re-send gateway not open")
            } catch (e: Throwable) {
                Timber.tag(TAG).e(e, "setActivity: image re-send failed")
            }
        }
    }

    fun setOnlineStatus(status: StatusType) {
        if (!_ready) {
            Timber.tag(TAG).w("setOnlineStatus: skipping — not ready (status=%s)", status)
            return
        }
        val presenceStatus = when (status) {
            StatusType.Online -> PresenceStatus.Online
            StatusType.Idle -> PresenceStatus.Idle
            StatusType.Dnd -> PresenceStatus.Dnd
        }
        val currentActivities = lastActivity?.let { listOf(it) } ?: emptyList()
        val onlineJson = DiscordPresence.buildPresenceUpdate(
            status = presenceStatus,
            activities = currentActivities,
        )
        Timber.tag(TAG).i("setOnlineStatus: body=%s", onlineJson)
        try {
            gateway.presenceUpdate(onlineJson)
        } catch (e: IllegalStateException) {
            Timber.tag(TAG).w(e, "setOnlineStatus: gateway not open")
        } catch (e: Throwable) {
            Timber.tag(TAG).e(e, "setOnlineStatus: send failed")
        }
    }

    fun clear() {
        if (!_ready) {
            Timber.tag(TAG).w("clear: skipping — not ready")
            return
        }
        if (lastActivity == null && currentSongId == null) {
            Timber.tag(TAG).d("clear: already cleared, skipping")
            return
        }
        lastActivity = null
        currentSongId = null
        currentIsPlaying = false
        currentActivityId.incrementAndGet()
        imageResolutionJob?.cancel()
        try {
            gateway.presenceUpdate(
                DiscordPresence.buildPresenceUpdate(
                    status = PresenceStatus.Online,
                    activities = emptyList(),
                ),
            )
        } catch (e: IllegalStateException) {
            Timber.tag(TAG).w(e, "clear: gateway not open")
        } catch (e: Throwable) {
            Timber.tag(TAG).e(e, "clear: send failed")
        }
    }

    fun reconnectWithToken(token: String) {
        if (!initialized) {
            Timber.tag(TAG).w("reconnectWithToken: not initialized, ignoring")
            return
        }
        accessToken = token
        _accessTokenFlow.value = token
        DiscordTokenStore.storeAccessToken(token)
        _connectionStatus.value = Status.Authorizing

        scope.launch {
            reconnectMutex.withLock {
                try {
                    val refreshToken = DiscordTokenStore.getRefreshToken()
                    val expiresAt = DiscordTokenStore.getExpiresAt()
                    val nowSec = System.currentTimeMillis() / 1000L
                    val needsRefresh = !refreshToken.isNullOrEmpty() &&
                        expiresAt > 0L &&
                        (expiresAt - nowSec) < 3600L

                    Timber.tag(TAG).i(
                        "reconnectWithToken: hasRefreshToken=%s, expiresAt=%d, now=%d, needsRefresh=%s",
                        !refreshToken.isNullOrEmpty(),
                        expiresAt,
                        nowSec,
                        needsRefresh,
                    )

                    if (needsRefresh) {
                        Timber.tag(TAG).i("reconnectWithToken: proactive token refresh")
                        val refreshed = try {
                            auth.refresh(refreshToken)
                        } catch (e: DiscordAuthException.InvalidGrant) {
                            Timber.tag(TAG).w(e, "reconnectWithToken: refresh invalid_grant, logging out")
                            _lastError.value = "discord_error_token_refresh_failed"
                            logout()
                            return@withLock
                        } catch (e: Throwable) {
                            Timber.tag(TAG).w(e, "reconnectWithToken: refresh failed, continuing with old token")
                            null
                        }
                        if (refreshed != null) {
                            Timber.tag(TAG).i(
                                "reconnectWithToken: refresh succeeded (new token length=%d, expiresIn=%d)",
                                refreshed.accessToken.length,
                                refreshed.expiresInSec,
                            )
                            accessToken = refreshed.accessToken
                            _accessTokenFlow.value = refreshed.accessToken
                            DiscordTokenStore.storeFull(
                                refreshed.accessToken,
                                refreshed.refreshToken,
                                refreshed.expiresInSec,
                            )
                        }
                    }

                    runCatching { gateway.close(4000, "reconnecting") }
                    gateway.connect()
                    gateway.identify("Bearer ${accessToken ?: token}")
                } catch (e: Throwable) {
                    Timber.tag(TAG).e(e, "reconnectWithToken: connect/identify failed")
                    _lastError.value = "discord_error_loopback_timeout"
                    _connectionStatus.value = Status.Disconnected
                }
            }
        }
    }

    fun disconnect() {
        Timber.tag(TAG).i("disconnect: closing gateway, clearing ready/authorized")
        currentActivityId.incrementAndGet()
        imageResolutionJob?.cancel()
        runCatching { gateway.close(1000, "user disconnect") }
        _connectionStatus.value = Status.Disconnected
        _ready = false
        _authorized = false
        currentSongId = null
        currentIsPlaying = false
    }

    fun destroy() {
        Timber.tag(TAG).i("destroy: cancelling scope and tearing down (initialized=%s)", initialized)
        currentActivityId.incrementAndGet()
        imageResolutionJob?.cancel()
        runCatching { gateway.close(1000, "destroy") }
        scope.cancel()
        _ready = false
        _authorized = false
        initialized = false
        _connectionStatus.value = Status.Disconnected
        lastActivity = null
        currentSongId = null
        currentIsPlaying = false
    }

    fun logout() {
        Timber.tag(TAG).i("logout: clearing tokens and disconnecting")
        disconnect()
        accessToken = null
        _accessTokenFlow.value = null
        _currentUser.value = null
        DiscordTokenStore.clear()
        _lastError.value = null
        lastActivity = null
    }

    private suspend fun handleGatewayEvent(event: GatewayEvent) {
        when (event) {
            is GatewayEvent.Ready -> {
                Timber.tag(TAG).i("gateway: READY (sessionId prefix=%s)", event.sessionId.take(8))
                _ready = true
                _authorized = true
                _connectionStatus.value = Status.Connected
                _lastError.value = null
                val token = accessToken ?: return
                scope.launch {
                    val user = fetchCurrentUser(token)
                    _currentUser.value = user
                    if (user != null) {
                        Timber.tag(TAG).i("gateway READY: fetched user %s", user.username)
                    }
                }
            }
            is GatewayEvent.Resumed -> {
                Timber.tag(TAG).i("gateway: RESUMED")
                _ready = true
                _authorized = true
                _connectionStatus.value = Status.Connected
                _lastError.value = null
            }
            is GatewayEvent.Disconnected -> {
                Timber.tag(TAG).i("gateway: Disconnected (code=%d, remote=%s, reason=%s)",
                    event.code, event.remote, event.reason)
                currentSongId = null
                currentIsPlaying = false
                imageResolutionJob?.cancel()
                imageResolutionJob = null
                if (event.code == 1000 && event.remote) {
                    _ready = false
                    _authorized = false
                    _connectionStatus.value = Status.Disconnected
                    return
                }
                if (event.code in setOf(4001, 4004) && event.reason.contains("max reconnect", ignoreCase = true)) {
                    _lastError.value = when (event.code) {
                        4004 -> "discord_error_token_refresh_failed"
                        4001 -> "discord_error_invalid_scope"
                        else -> _lastError.value
                    }
                }
            }
            is GatewayEvent.InvalidSession -> {
                Timber.tag(TAG).w("gateway: InvalidSession (resumable=%s), closing WS to trigger reconnect", event.resumable)
                imageResolutionJob?.cancel()
                imageResolutionJob = null
            }
            is GatewayEvent.Hello -> Unit
            is GatewayEvent.HeartbeatAck -> Unit
            is GatewayEvent.TextDispatch -> {
                Timber.tag(TAG).v("gateway: TextDispatch op=%d t=%s", event.op, event.t)
            }
        }
    }

    private fun activityTypeToEnum(code: Int): ActivityType = when (code) {
        0 -> ActivityType.Playing
        1 -> ActivityType.Streaming
        2 -> ActivityType.Listening
        3 -> ActivityType.Watching
        4 -> ActivityType.Custom
        5 -> ActivityType.Competing
        else -> ActivityType.Listening
    }
}

private object BuildConfigProvider {
    val appId: String = com.metrolist.music.BuildConfig.DISCORD_APP_ID.toString()
}
