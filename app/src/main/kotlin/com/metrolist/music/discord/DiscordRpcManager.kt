package com.metrolist.music.discord

import android.os.Handler
import android.os.Looper
import android.util.Base64
import com.discord.socialsdk.NativeCalls
import com.discord.socialsdk.AuthenticationClientCallback
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.security.SecureRandom
import timber.log.Timber

object DiscordRpcManager {
    private const val APP_ID = 1447278780795064401L
    private const val SCOPES = "openid sdk.social_layer_presence"
    private const val REDIRECT_URI = "discord-$APP_ID:///authorize/callback"
    private const val TOKEN_URL = "https://discord.com/api/v10/oauth2/token"
    private const val AUTH_URL = "https://discord.com/oauth2/authorize"

    @Volatile private var initialized = false
    @Volatile private var _authorized = false
    @Volatile private var _ready = false

    private val _connectionStatus = MutableStateFlow(Status.Disconnected)
    val connectionStatus: StateFlow<Status> = _connectionStatus

    enum class Status { Disconnected, Authorizing, Connected }

    private external fun nativeInit(appId: Long): Boolean
    private external fun nativeSetTokenAndConnect(token: String)
    private external fun nativeConnect()
    private external fun nativeIsReady(): Boolean
    private external fun nativeIsAuthorized(): Boolean
    private external fun nativeSetListening(
        state: String?, details: String?,
        startSecs: Long, endSecs: Long,
        largeImage: String?, largeText: String?,
        smallImage: String?, smallText: String?,
        button1Label: String?, button1Url: String?,
        button2Label: String?, button2Url: String?,
    )
    private external fun nativeClear()
    private external fun nativeRunCallbacks()
    private external fun nativeDestroy()
    private external fun nativeDisconnect()

    fun isInitialized(): Boolean = initialized
    fun isAuthorized(): Boolean = _authorized
    fun isReady(): Boolean = _ready

    fun init() = synchronized(this) {
        if (initialized) return
        try {
            System.loadLibrary("metrolist_discord")
        } catch (e: UnsatisfiedLinkError) {
            Timber.e(e, "Failed to load native library")
            return
        }
        initialized = nativeInit(APP_ID)
        if (initialized) {
            _connectionStatus.value = Status.Disconnected
            java.util.Timer("DiscordRPC", false).schedule(
                object : java.util.TimerTask() {
                    override fun run() {
                        try {
                            nativeRunCallbacks()
                            val nativeReady = nativeIsReady()
                            val nativeAuth = nativeIsAuthorized()
                            Timber.d("TIMER: _ready=%s _authorized=%s nativeReady=%s nativeAuth=%s",
                                _ready, _authorized, nativeReady, nativeAuth)
                            if (!_ready && _authorized && nativeReady) {
                                _ready = true
                                _connectionStatus.value = Status.Connected
                                Timber.d("TIMER: _ready set to true")
                            }
                            if (!_authorized && nativeAuth) {
                                _authorized = true
                                Timber.d("TIMER: _authorized set to true via native")
                            }
                        } catch (e: Exception) {
                            Timber.w(e, "TIMER: error")
                        }
                    }
                },
                1000, 1000
            )
        } else {
            Timber.w("init: nativeInit failed")
        }
    }

    private fun generateCodeVerifier(): String {
        val bytes = ByteArray(64)
        SecureRandom().nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }

    private fun generateCodeChallenge(verifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray())
        return Base64.encodeToString(digest, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }

    fun authorize(onComplete: (Boolean) -> Unit) {
        if (!initialized) { onComplete(false); return }

        _connectionStatus.value = Status.Authorizing
        val verifier = generateCodeVerifier()
        val challenge = generateCodeChallenge(verifier)

        val oauthUrl = "$AUTH_URL" +
            "?client_id=$APP_ID" +
            "&response_type=code" +
            "&redirect_uri=$REDIRECT_URI" +
            "&scope=${java.net.URLEncoder.encode(SCOPES, "UTF-8")}" +
            "&code_challenge_method=S256" +
            "&code_challenge=$challenge"

        Timber.d("authorize: oauthUrl=$oauthUrl")

        val callback = object : AuthenticationClientCallback(0) {
            private val codeVerifier = verifier
            private var callbackFired = false

            override fun onAuthorizationComplete(error: String?, authCode: String?, state: String?) {
                if (callbackFired) return
                callbackFired = true

                Timber.d("CALLBACK: error='$error' authCode='$authCode' state='$state'")

                if (!error.isNullOrEmpty() || authCode.isNullOrEmpty()) {
                    Timber.w("CALLBACK: error='$error' authCode='$authCode'")
                    onComplete(false)
                    return
                }

                exchangeCodeForToken(authCode, codeVerifier, onComplete)
            }
        }

        try {
            NativeCalls.authorize(oauthUrl, callback)
        } catch (e: Exception) {
            Timber.e(e, "authorize: NativeCalls.authorize threw")
            onComplete(false)
        }
    }

    private fun exchangeCodeForToken(
        authCode: String,
        codeVerifier: String,
        onComplete: (Boolean) -> Unit,
    ) {
        Thread {
            try {
                val body = "client_id=$APP_ID" +
                    "&grant_type=authorization_code" +
                    "&code=${java.net.URLEncoder.encode(authCode, "UTF-8")}" +
                    "&redirect_uri=${java.net.URLEncoder.encode(REDIRECT_URI, "UTF-8")}" +
                    "&code_verifier=$codeVerifier"

                Timber.d("exchange: body=$body")

                val conn = URL(TOKEN_URL).openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")

                OutputStreamWriter(conn.outputStream).use { it.write(body) }

                val responseCode = conn.responseCode
                val responseBody = if (responseCode in 200..299) {
                    conn.inputStream.bufferedReader().readText()
                } else {
                    conn.errorStream?.bufferedReader()?.readText() ?: ""
                }
                conn.disconnect()

                Timber.d("exchange: HTTP $responseCode body=$responseBody")

                if (responseCode in 200..299) {
                    val json = JSONObject(responseBody)
                    val accessToken = json.optString("access_token")
                    if (accessToken.isNotEmpty()) {
                        Timber.d("exchange: got access_token")
                        nativeSetTokenAndConnect(accessToken)
                        _authorized = true
                        _connectionStatus.value = Status.Authorizing
                        Timber.d("exchange: token set, connecting...")
                        Handler(Looper.getMainLooper()).post {
                            nativeConnect()
                            Timber.d("exchange: connect called on main thread")
                            onComplete(true)
                        }
                        return@Thread
                    }
                    Timber.w("exchange: no access_token in response")
                } else {
                    Timber.w("exchange: HTTP error $responseCode")
                }
                Handler(Looper.getMainLooper()).post {
                    onComplete(false)
                }
            } catch (e: Exception) {
                Timber.e(e, "exchange: exception")
                Handler(Looper.getMainLooper()).post {
                    onComplete(false)
                }
            }
        }.apply { name = "DiscordTokenExchange" }.start()
    }

    fun disconnect() {
        _connectionStatus.value = Status.Disconnected
        _ready = false
        _authorized = false
        nativeDisconnect()
    }

    fun setActivity(activity: DiscordActivity) {
        if (!_ready) return
        nativeSetListening(
            activity.state, activity.details,
            activity.startTimestamp, activity.endTimestamp ?: 0L,
            activity.largeImage, activity.largeText,
            activity.smallImage, activity.smallText,
            activity.button1Label, activity.button1Url,
            activity.button2Label, activity.button2Url,
        )
    }

    fun clear() {
        if (!_ready) return
        nativeClear()
    }

    fun destroy() = synchronized(this) {
        _ready = false
        _authorized = false
        initialized = false
        nativeDestroy()
    }
}
