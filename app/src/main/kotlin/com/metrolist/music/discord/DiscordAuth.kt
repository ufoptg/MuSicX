package com.metrolist.music.discord

import android.app.Activity
import android.content.ActivityNotFoundException
import android.net.Uri
import android.util.Base64
import androidx.browser.customtabs.CustomTabsIntent
import com.metrolist.music.BuildConfig
import com.metrolist.music.discord.DiscordDefaults.DISCORD_OAUTH_AUTHORIZE
import com.metrolist.music.discord.DiscordDefaults.DISCORD_OAUTH_TOKEN
import com.metrolist.music.discord.DiscordDefaults.DISCORD_SCOPES
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.forms.submitForm
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.parameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import org.json.JSONObject
import timber.log.Timber
import java.io.IOException
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom

data class DiscordAuthResult(
    val accessToken: String,
    val refreshToken: String,
    val expiresInSec: Long,
    val scope: String,
)

data class PkcePair(val verifier: String, val challenge: String)

sealed class DiscordAuthException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class UserCancelled(message: String = "User cancelled authorization") : DiscordAuthException(message)
    class NetworkFailure(cause: Throwable) : DiscordAuthException("Network failure: ${cause.message}", cause)
    class InvalidGrant(message: String = "Invalid or expired grant") : DiscordAuthException(message)
    class StateMismatch : DiscordAuthException("OAuth state mismatch")
}

class DiscordAuth {

    private val httpClient: HttpClient = HttpClient(CIO) {
        install(HttpTimeout) {
            requestTimeoutMillis = 15_000L
            connectTimeoutMillis = 10_000L
            socketTimeoutMillis = 15_000L
        }
        expectSuccess = false
    }

    suspend fun authorize(activity: Activity): DiscordAuthResult {
        val pkce = generatePkcePair()
        val state = generateState()
        val redirectUri = loopbackRedirectUri()
        val loopback = LoopbackAuthServer(expectedState = state)

        Timber.i("DiscordAuth: authorize starting (redirectUri=%s)", redirectUri)

        try {
            withContext(Dispatchers.IO) { loopback.start() }

            val authUrl = buildAuthorizeUrl(
                clientId = BuildConfig.DISCORD_APP_ID,
                redirectUri = redirectUri,
                state = state,
                challenge = pkce.challenge,
            )

            Timber.i("DiscordAuth: launching authorize URL (clientId prefix=%s)", BuildConfig.DISCORD_APP_ID.toString().take(8))
            try {
                CustomTabsIntent.Builder().build().launchUrl(activity, Uri.parse(authUrl))
            } catch (e: ActivityNotFoundException) {
                throw DiscordAuthException.NetworkFailure(e)
            }

            val callback = try {
                loopback.awaitCode(timeoutMs = 120_000L)
            } catch (e: TimeoutCancellationException) {
                throw DiscordAuthException.UserCancelled()
            }

            return withContext(Dispatchers.IO) {
                exchangeAuthorizationCode(
                    code = callback.code,
                    verifier = pkce.verifier,
                    redirectUri = redirectUri,
                )
            }
        } finally {
            loopback.stop()
        }
    }

    suspend fun refresh(refreshToken: String): DiscordAuthResult =
        withContext(Dispatchers.IO) { refreshAccessToken(refreshToken) }

    private suspend fun exchangeAuthorizationCode(
        code: String,
        verifier: String,
        redirectUri: String,
    ): DiscordAuthResult = performTokenExchange(
        grantType = "authorization_code",
        extraParams = parameters {
            append("code", code)
            append("redirect_uri", redirectUri)
            append("code_verifier", verifier)
        },
    )

    private suspend fun refreshAccessToken(refreshToken: String): DiscordAuthResult =
        performTokenExchange(
            grantType = "refresh_token",
            extraParams = parameters {
                append("refresh_token", refreshToken)
            },
        )

    private suspend fun performTokenExchange(
        grantType: String,
        extraParams: io.ktor.http.Parameters,
    ): DiscordAuthResult {
        val response: HttpResponse = httpClient.submitForm(
            url = DISCORD_OAUTH_TOKEN,
            formParameters = parameters {
                append("client_id", BuildConfig.DISCORD_APP_ID.toString())
                append("grant_type", grantType)
                extraParams.forEach { name, values ->
                    values.forEach { value -> append(name, value) }
                }
            },
        )

        val status = response.status
        val body = response.bodyAsText()

        if (status.value in 200..299) {
            val json = JSONObject(body)
            val accessToken = json.getString("access_token")
            val refreshToken = json.optString("refresh_token", "")
            val expiresIn = json.optLong("expires_in", 0L)
            val scope = json.optString("scope", DISCORD_SCOPES)
            Timber.i(
                "DiscordAuth: token exchange success (accessToken length=%d, refreshToken present=%s, expiresIn=%d)",
                accessToken.length,
                refreshToken.isNotEmpty(),
                expiresIn,
            )
            return DiscordAuthResult(
                accessToken = accessToken,
                refreshToken = refreshToken,
                expiresInSec = expiresIn,
                scope = scope,
            )
        }

        val errorCode = runCatching { JSONObject(body).optString("error", "") }
            .getOrDefault("")
        if (status == HttpStatusCode.BadRequest && errorCode == "invalid_grant") {
            Timber.w("DiscordAuth: invalid_grant on %s", grantType)
            throw DiscordAuthException.InvalidGrant()
        }
        Timber.w(
            "DiscordAuth: token endpoint HTTP %d (grantType=%s, error=%s, body=%s)",
            status.value,
            grantType,
            errorCode,
            body.take(200),
        )
        throw DiscordAuthException.NetworkFailure(IOException("HTTP ${status.value}: $body"))
    }

    private fun buildAuthorizeUrl(
        clientId: Long,
        redirectUri: String,
        state: String,
        challenge: String,
    ): String {
        val encodedRedirect = URLEncoder.encode(redirectUri, StandardCharsets.UTF_8.name())
        val encodedScope = URLEncoder.encode(DISCORD_SCOPES, StandardCharsets.UTF_8.name())
        return buildString {
            append(DISCORD_OAUTH_AUTHORIZE)
            append("?client_id=").append(clientId)
            append("&response_type=code")
            append("&redirect_uri=").append(encodedRedirect)
            append("&scope=").append(encodedScope)
            append("&state=").append(state)
            append("&code_challenge_method=S256")
            append("&code_challenge=").append(challenge)
        }
    }

    companion object {
        fun generatePkcePair(): PkcePair {
            val bytes = ByteArray(64)
            SecureRandom().nextBytes(bytes)
            val verifier = Base64.encodeToString(
                bytes,
                Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP,
            )
            val challenge = Base64.encodeToString(
                MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(StandardCharsets.UTF_8)),
                Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP,
            )
            return PkcePair(verifier = verifier, challenge = challenge)
        }

        private fun generateState(): String {
            val bytes = ByteArray(16)
            SecureRandom().nextBytes(bytes)
            return Base64.encodeToString(
                bytes,
                Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP,
            )
        }
    }
}
