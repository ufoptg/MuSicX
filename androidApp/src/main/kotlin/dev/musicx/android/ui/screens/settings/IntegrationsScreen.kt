/*
 * MuSicX Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package dev.musicx.android.ui.screens.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import dev.musicx.android.LocalMiniPlayerBottomInset
import dev.musicx.android.playback.LocalPlayerConnection
import dev.musicx.android.spotifyimport.SpotifyLoginScreen
import dev.musicx.android.spotifyimport.androidSpotifyImportManager
import dev.musicx.android.ui.screens.rememberDeviceConnectSettingsHost
import dev.musicx.shared.discord.DiscordRpcManager
import dev.musicx.shared.discord.openDiscordUrl
import dev.musicx.shared.generated.resources.Res
import dev.musicx.shared.generated.resources.brand_spotify
import dev.musicx.shared.generated.resources.copied
import dev.musicx.shared.generated.resources.spotify_import_error_sign_in_failed
import dev.musicx.shared.generated.resources.spotify_status_not_initialized
import dev.musicx.shared.spotify.SpotifyYouTubeMapperFactory
import dev.musicx.shared.spotifyimport.SpotifyPlaybackResolverFactory
import dev.musicx.shared.state.GlobalYouTubeRepository
import dev.musicx.shared.ui.settings.IntegrationPlaybackState
import dev.musicx.shared.ui.settings.SettingsPageId
import dev.musicx.shared.ui.settings.SettingsPlatform
import dev.musicx.shared.ui.settings.SharedIntegrationsHost
import dev.musicx.shared.ui.settings.SharedIntegrationsScreen
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

@Composable
fun IntegrationsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToListenTogether: () -> Unit,
    initialPage: SettingsPageId? = null,
) {
    val copiedMessage = stringResource(Res.string.copied)
    val context = LocalContext.current
    val spotifyBrandName = stringResource(Res.brand_spotify)
    val playerConnection = LocalPlayerConnection.current
    val scope = rememberCoroutineScope()
    val currentSong by playerConnection?.currentSong?.collectAsState(initial = null) ?: remember { mutableStateOf(null) }
    val duration by playerConnection?.duration?.collectAsState(initial = 0L) ?: remember { mutableLongStateOf(0L) }
    val playbackSpeed by playerConnection?.playbackSpeedFlow?.collectAsState() ?: remember { mutableFloatStateOf(1f) }
    val isPlaying by playerConnection?.isPlaying?.collectAsState(initial = false) ?: remember { mutableStateOf(false) }
    val discordLoginInProgress = DiscordLoginFlow.isActive

    LaunchedEffect(discordLoginInProgress) {
        if (discordLoginInProgress) {
            delay(120_000L)
            if (DiscordLoginFlow.isActive && !DiscordLoginFlow.exchanging) {
                DiscordLoginFlow.clear()
                DiscordRpcManager.setError("discord_error_timeout")
            }
        }
    }

    var showSpotifyLogin by remember { mutableStateOf(false) }
    var spotifyLoginInProgress by remember { mutableStateOf(false) }
    var spotifyError by remember { mutableStateOf<String?>(null) }

    Box(Modifier.fillMaxSize()) {
        SharedIntegrationsScreen(
            host =
                SharedIntegrationsHost(
                    platform = SettingsPlatform.ANDROID,
                    onClose = onNavigateBack,
                    bottomContentPadding = LocalMiniPlayerBottomInset.current,
                    initialPage = initialPage,
                    discordLoginInProgress = discordLoginInProgress,
                    playbackPosition = playerConnection?.currentPosition,
                    currentPositionMs = { playerConnection?.currentPositionMs() ?: 0L },
                    playback =
                        currentSong?.let { song ->
                            IntegrationPlaybackState(
                                song = song,
                                artworkUrl = song.thumbnail,
                                artistArtworkUrl = song.artists.firstOrNull()?.thumbnail,
                                durationMs = duration,
                                isPlaying = isPlaying,
                                playbackSpeed = playbackSpeed,
                            )
                        },
                    onStartDiscordLogin = { session ->
                        DiscordLoginFlow.start(session)
                        runCatching { context.openDiscordUrl(session.authorizeUrl) }
                            .onFailure {
                                DiscordLoginFlow.clear()
                                DiscordRpcManager.setError("discord_error_no_browser")
                            }
                    },
                    deviceConnect = rememberDeviceConnectSettingsHost(),
                    onOpenListenTogether = onNavigateToListenTogether,
                    onOpenUrl = { url -> runCatching { context.openDiscordUrl(url) } },
                    onCopyText = { label, text -> context.copyToClipboard(label, text, copiedMessage) },
                    onMessage = { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() },
                    spotifyLoginInProgress = spotifyLoginInProgress,
                    onStartSpotifyLogin = { showSpotifyLogin = true },
                    onSpotifySettingsChanged = {},
                    onSpotifyLogout = { androidSpotifyImportManager.logout() },
                    onStartSpotifyPlayback = {
                        if (!androidSpotifyImportManager.isAuthenticated) {
                            showSpotifyLogin = true
                        } else {
                            scope.launch {
                                val mapper = SpotifyYouTubeMapperFactory.create(GlobalYouTubeRepository)
                                val resolver =
                                    SpotifyPlaybackResolverFactory.create(androidSpotifyImportManager, mapper)
                                runCatching { resolver.resolveLikedSongs() }
                                    .onSuccess { songs ->
                                        if (songs.isEmpty()) {
                                            Toast.makeText(
                                                context,
                                                getString(Res.string.spotify_status_not_initialized),
                                                Toast.LENGTH_SHORT,
                                            ).show()
                                        } else {
                                            playerConnection?.playQueue(
                                                songs,
                                                startIndex = 0,
                                                queueTitle = spotifyBrandName,
                                                finite = true,
                                            )
                                        }
                                    }
                                    .onFailure { error ->
                                        Toast.makeText(
                                            context,
                                            error.message ?: getString(Res.string.spotify_import_error_sign_in_failed),
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                    }
                            }
                        }
                    },
                ),
        )

        if (showSpotifyLogin) {
            SpotifyLoginScreen(
                isAuthenticating = spotifyLoginInProgress,
                authenticationError = spotifyError,
                onClearAuthenticationError = { spotifyError = null },
                onSessionCookie = { cookie ->
                    if (!spotifyLoginInProgress) {
                        spotifyLoginInProgress = true
                        scope.launch {
                            try {
                                androidSpotifyImportManager.authenticate(cookie)
                                spotifyLoginInProgress = false
                                showSpotifyLogin = false
                            } catch (error: CancellationException) {
                                throw error
                            } catch (_: Exception) {
                                spotifyError = getString(Res.string.spotify_import_error_sign_in_failed)
                                spotifyLoginInProgress = false
                            }
                        }
                    }
                },
                onNavigateBack = {
                    showSpotifyLogin = false
                    spotifyError = null
                },
            )
        }
    }
}

private fun Context.copyToClipboard(
    label: String,
    text: String,
    copiedMessage: String,
) {
    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
    Toast.makeText(this, copiedMessage, Toast.LENGTH_SHORT).show()
}
