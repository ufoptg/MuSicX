/**
 * MuSicX Project (C) 2026
 * Credits to Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 *
 * Spotify login using an embedded WebView.
 * Loads Spotify's web login page, which supports all auth methods
 * (email/password, Facebook, Google, Apple). After successful login,
 * the WebView lands on accounts.spotify.com/status (not the web player);
 * sp_dc is extracted and used to fetch an access token.
 *
 * Token acquisition uses TOTP (Time-based One-Time Password) generated
 * from a community-maintained shared secret, following the approach used
 * by the Spotube Spotify plugin. The token is fetched entirely in the
 * background using HttpURLConnection — no web player, no rate limit issues.
 *
 * Reference: https://github.com/sonic-liberation/spotube-plugin-spotify
 */

package com.metrolist.music.ui.screens

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.Build
import android.view.ContextThemeWrapper
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.datastore.preferences.core.edit
import androidx.navigation.NavController
import com.metrolist.music.R
import com.metrolist.music.constants.SpotifyAccessTokenKey
import com.metrolist.music.constants.SpotifySpDcKey
import com.metrolist.music.constants.SpotifySpKeyKey
import com.metrolist.music.constants.SpotifyTokenExpiryKey
import com.metrolist.music.constants.SpotifyUserIdKey
import com.metrolist.music.constants.SpotifyUsernameKey
import com.metrolist.music.ui.component.IconButton
import com.metrolist.music.ui.utils.backToMain
import com.metrolist.music.utils.dataStore
import com.metrolist.spotify.Spotify
import com.metrolist.spotify.SpotifyAuth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun SpotifyLoginScreen(navController: NavController) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var isLoading by remember { mutableStateOf(true) }
    var isProcessing by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf("") }
    var hasError by remember { mutableStateOf(false) }
    var retryCount by remember { mutableIntStateOf(0) }
    val tokenFetchStarted = remember { AtomicBoolean(false) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    // Backup: poll for sp_dc in case redirect interception misses the cookie.
    LaunchedEffect(retryCount) {
        while (isActive) {
            delay(1000)
            if (tokenFetchStarted.get() || isProcessing) continue
            val spDc = extractSpDcCookie() ?: continue
            if (!tokenFetchStarted.compareAndSet(false, true)) continue
            Timber.d("SpotifyLogin: sp_dc found via cookie poll")
            extractAndFetchToken(
                view = webViewRef,
                context = context,
                scope = scope,
                navController = navController,
                setProcessing = { isProcessing = it },
                setStatus = { statusMessage = it },
                setError = { hasError = it },
                tokenFetchStarted = tokenFetchStarted,
            )
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(R.string.spotify_login)) },
            navigationIcon = {
                IconButton(
                    onClick = navController::navigateUp,
                    onLongClick = navController::backToMain,
                ) {
                    Icon(painterResource(R.drawable.arrow_back), contentDescription = null)
                }
            },
        )

        if (isLoading || isProcessing) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        Box(modifier = Modifier.fillMaxSize()) {
            // key(retryCount) so Retry after about:blank recreates the WebView
            key(retryCount) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        val cookieManager = CookieManager.getInstance()
                        cookieManager.setAcceptCookie(true)
                        // Fire-and-forget — never gate loadUrl on this callback (it can stall
                        // and leave a permanent blank WebView).
                        cookieManager.removeAllCookies(null)
                        cookieManager.flush()

                        // App is dark-themed; WebView inherits isLightTheme=false which can
                        // algorithmically crush Spotify's already-dark login into a blank page.
                        // Force a light DayNight context so the page paints normally.
                        WebView(lightWebViewContext(ctx)).apply {
                            webViewRef = this
                            layoutParams = android.view.ViewGroup.LayoutParams(
                                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                            )
                            cookieManager.setAcceptThirdPartyCookies(this, true)
                            setBackgroundColor(android.graphics.Color.WHITE)

                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.databaseEnabled = true
                            settings.loadWithOverviewMode = true
                            settings.useWideViewPort = true
                            settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                            settings.javaScriptCanOpenWindowsAutomatically = true
                            settings.setSupportMultipleWindows(false)
                            settings.mediaPlaybackRequiresUserGesture = false
                            settings.cacheMode = WebSettings.LOAD_DEFAULT
                            settings.userAgentString = desktopUserAgent(settings.userAgentString)
                            disableWebViewDarkening(settings)

                            webChromeClient = object : WebChromeClient() {
                                override fun onPermissionRequest(request: PermissionRequest?) {
                                    // Grant protected-media (and any bundled resources) so EME
                                    // pages don't hang blank. Spotube does the same.
                                    val resources = request?.resources ?: return
                                    Timber.d(
                                        "SpotifyLogin: granting WebView permissions: ${resources.toList()}",
                                    )
                                    request.grant(resources)
                                }

                                override fun onConsoleMessage(message: ConsoleMessage?): Boolean {
                                    message?.let {
                                        Timber.d(
                                            "SpotifyLogin[web]: ${it.message()} " +
                                                "@${it.sourceId()}:${it.lineNumber()}",
                                        )
                                    }
                                    return true
                                }
                            }

                            webViewClient = object : WebViewClient() {
                                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                    isLoading = true
                                    hasError = false
                                    Timber.d("SpotifyLogin: page started: $url")
                                }

                                override fun onPageFinished(view: WebView?, url: String?) {
                                    isLoading = false
                                    Timber.d("SpotifyLogin: page finished: $url")

                                    if (isPostLoginUrl(url) &&
                                        tokenFetchStarted.compareAndSet(false, true)
                                    ) {
                                        Timber.d("SpotifyLogin: extracting token from onPageFinished")
                                        extractAndFetchToken(
                                            view = view,
                                            context = context,
                                            scope = scope,
                                            navController = navController,
                                            setProcessing = { isProcessing = it },
                                            setStatus = { statusMessage = it },
                                            setError = { hasError = it },
                                            tokenFetchStarted = tokenFetchStarted,
                                        )
                                    }
                                }

                                override fun onReceivedError(
                                    view: WebView?,
                                    request: WebResourceRequest?,
                                    error: WebResourceError?,
                                ) {
                                    if (request?.isForMainFrame != true) return
                                    val desc = error?.description?.toString().orEmpty()
                                    Timber.w("SpotifyLogin: main-frame error: $desc (${request.url})")
                                    isLoading = false
                                    isProcessing = true
                                    statusMessage = context.getString(R.string.spotify_login_error_network)
                                    hasError = true
                                }

                                override fun shouldOverrideUrlLoading(
                                    view: WebView?,
                                    request: WebResourceRequest?,
                                ): Boolean {
                                    val requestUrl = request?.url?.toString() ?: return false
                                    Timber.d("SpotifyLogin: navigating to: $requestUrl")

                                    if (isPostLoginUrl(requestUrl)) {
                                        val spDc = extractSpDcCookie()
                                        if (spDc != null && tokenFetchStarted.compareAndSet(false, true)) {
                                            Timber.d("SpotifyLogin: sp_dc available at redirect, processing immediately")
                                            extractAndFetchToken(
                                                view = view,
                                                context = context,
                                                scope = scope,
                                                navController = navController,
                                                setProcessing = { isProcessing = it },
                                                setStatus = { statusMessage = it },
                                                setError = { hasError = it },
                                                tokenFetchStarted = tokenFetchStarted,
                                            )
                                            return true
                                        }
                                        Timber.d("SpotifyLogin: sp_dc not ready at redirect, deferring to onPageFinished")
                                        return false
                                    }

                                    return false
                                }
                            }

                            loadUrl(SpotifyAuth.LOGIN_URL)
                        }
                    },
                )
            }

            if (isProcessing) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surface),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (!hasError) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                        Text(
                            text = statusMessage.ifEmpty {
                                stringResource(R.string.spotify_logging_in)
                            },
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (hasError) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                        )
                        if (hasError) {
                            Spacer(modifier = Modifier.height(16.dp))
                            TextButton(
                                onClick = {
                                    hasError = false
                                    isProcessing = false
                                    statusMessage = ""
                                    tokenFetchStarted.set(false)
                                    retryCount++
                                },
                                modifier = Modifier.padding(horizontal = 16.dp),
                            ) {
                                Text(stringResource(R.string.retry))
                            }
                        }
                    }
                }
            }
        }
    }
}

/** WebView context that reports light theme so dark-app algorithmic darkening doesn't apply. */
private fun lightWebViewContext(base: Context): Context {
    val config = Configuration(base.resources.configuration).apply {
        uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or Configuration.UI_MODE_NIGHT_NO
    }
    return ContextThemeWrapper(
        base.createConfigurationContext(config),
        android.R.style.Theme_DeviceDefault_Light_NoActionBar,
    )
}

@Suppress("DEPRECATION")
private fun disableWebViewDarkening(settings: WebSettings) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        runCatching { settings.isAlgorithmicDarkeningAllowed = false }
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        runCatching { settings.forceDark = WebSettings.FORCE_DARK_OFF }
    }
}

/**
 * Attempts to read the sp_dc cookie from the CookieManager.
 * Returns null if the cookie is not yet available.
 */
private fun extractSpDcCookie(): String? {
    for (domain in COOKIE_DOMAINS) {
        val allCookies = CookieManager.getInstance().getCookie(domain) ?: continue
        if (allCookies.isBlank()) continue

        val spDc = allCookies.split(";")
            .mapNotNull { cookie ->
                val parts = cookie.trim().split("=", limit = 2)
                if (parts.size == 2) parts[0].trim() to parts[1].trim() else null
            }
            .firstOrNull { it.first == "sp_dc" && it.second.isNotBlank() }
            ?.second
        if (spDc != null) return spDc
    }
    return null
}

/**
 * Post-login landing pages where sp_dc should be available.
 * Prefer accounts/.../status (lightweight) over open.spotify.com (black web player).
 */
private fun isPostLoginUrl(url: String?): Boolean {
    if (url.isNullOrBlank()) return false
    if (url.startsWith("https://open.spotify.com")) return true
    // accounts.spotify.com/status or /en/status (optional query string)
    return STATUS_URL_REGEX.containsMatchIn(url)
}

private val STATUS_URL_REGEX =
    Regex("^https://accounts\\.spotify\\.com/(?:[^/]+/)?status(?:[/?#].*)?$", RegexOption.IGNORE_CASE)

private val COOKIE_DOMAINS = listOf(
    "https://accounts.spotify.com",
    "https://open.spotify.com",
)

/**
 * Desktop Chrome UA built from the WebView's real Chrome version so feature
 * checks don't see a fake Chrome/131 on a newer (or older) engine.
 * Desktop UA avoids Facebook/Google mobile JS incompatibilities in WebView.
 */
private fun desktopUserAgent(defaultUa: String): String {
    val chrome = Regex("""Chrome/[\d.]+""").find(defaultUa)?.value ?: "Chrome/131.0.0.0"
    return "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) $chrome Safari/537.36"
}

/**
 * Extracts sp_dc/sp_key cookies, stops the WebView, and fetches the access
 * token in the background using [SpotifyAuth.fetchAccessToken].
 *
 * Uses [tokenFetchStarted] as an atomic guard so only one invocation ever runs,
 * preventing the race between shouldOverrideUrlLoading and onPageFinished.
 */
private fun extractAndFetchToken(
    view: WebView?,
    context: Context,
    scope: kotlinx.coroutines.CoroutineScope,
    navController: NavController,
    setProcessing: (Boolean) -> Unit,
    setStatus: (String) -> Unit,
    setError: (Boolean) -> Unit,
    tokenFetchStarted: AtomicBoolean,
) {
    val cookieManager = CookieManager.getInstance()
    val cookies = buildMap {
        for (domain in COOKIE_DOMAINS) {
            val raw = cookieManager.getCookie(domain) ?: continue
            raw.split(";").forEach { cookie ->
                val parts = cookie.trim().split("=", limit = 2)
                if (parts.size == 2 && parts[0].trim().isNotEmpty()) {
                    put(parts[0].trim(), parts[1].trim())
                }
            }
        }
    }
    Timber.d("SpotifyLogin: cookies present: ${cookies.isNotEmpty()} keys=${cookies.keys}")

    val spDc = cookies["sp_dc"]
    if (spDc.isNullOrBlank()) {
        Timber.w("SpotifyLogin: sp_dc not found in cookies (keys: ${cookies.keys})")
        setProcessing(true)
        setStatus(context.getString(R.string.spotify_login_error_no_cookie))
        setError(true)
        tokenFetchStarted.set(false)
        return
    }

    val spKey = cookies["sp_key"] ?: ""
    Timber.d("SpotifyLogin: sp_dc found (${spDc.take(8)}...), starting token fetch")

    setProcessing(true)
    setError(false)
    setStatus(context.getString(R.string.spotify_status_verifying))

    view?.stopLoading()
    view?.loadUrl("about:blank")

    scope.launch(Dispatchers.IO) {
        try {
            context.dataStore.edit { prefs ->
                prefs[SpotifySpDcKey] = spDc
                prefs[SpotifySpKeyKey] = spKey
            }

            withContext(Dispatchers.Main) {
                setStatus(context.getString(R.string.spotify_status_connecting))
            }
            Timber.d("SpotifyLogin: fetching access token via SpotifyAuth (with TOTP)...")

            val token = SpotifyAuth.fetchAccessToken(spDc, spKey).getOrThrow()
            Timber.d("SpotifyLogin: token obtained (anonymous=${token.isAnonymous})")
            Spotify.accessToken = token.accessToken

            withContext(Dispatchers.Main) {
                setStatus(context.getString(R.string.spotify_status_loading_profile))
            }
            Timber.d("SpotifyLogin: fetching user profile...")

            Spotify.me().onSuccess { user ->
                Timber.d("SpotifyLogin: logged in as ${user.displayName} (${user.id})")
                context.dataStore.edit { prefs ->
                    prefs[SpotifyUsernameKey] = user.displayName ?: user.id
                    prefs[SpotifyUserIdKey] = user.id
                }
            }.onFailure { e ->
                Timber.w(e, "SpotifyLogin: could not fetch profile (non-fatal)")
            }

            context.dataStore.edit { prefs ->
                prefs[SpotifyAccessTokenKey] = token.accessToken
                prefs[SpotifyTokenExpiryKey] = token.accessTokenExpirationTimestampMs
            }

            withContext(Dispatchers.Main) {
                setStatus(context.getString(R.string.spotify_login_success))
            }
            Timber.d("SpotifyLogin: login complete, navigating back")

            delay(300)

            withContext(Dispatchers.Main) {
                navController.navigateUp()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "SpotifyLogin: login failed — ${e.message}")
            val errorMsg = classifyLoginError(context, e)
            withContext(Dispatchers.Main) {
                setStatus(errorMsg)
                setError(true)
            }
            tokenFetchStarted.set(false)
        }
    }
}

/**
 * Maps authentication exceptions to user-friendly error messages.
 */
private fun classifyLoginError(context: Context, e: Exception): String {
    val msg = e.message.orEmpty()
    return when {
        "anonymous" in msg || "expired" in msg ->
            context.getString(R.string.spotify_login_error_expired)
        "HTTP 403" in msg || "HTTP 401" in msg ->
            context.getString(R.string.spotify_login_error_rejected)
        "gist" in msg.lowercase() || "nuance" in msg.lowercase() ->
            context.getString(R.string.spotify_login_error_network)
        "UnknownHostException" in msg || "timeout" in msg.lowercase() ||
            "SocketTimeoutException" in e.javaClass.simpleName ->
            context.getString(R.string.spotify_login_error_network)
        else ->
            context.getString(R.string.spotify_login_error)
    }
}
