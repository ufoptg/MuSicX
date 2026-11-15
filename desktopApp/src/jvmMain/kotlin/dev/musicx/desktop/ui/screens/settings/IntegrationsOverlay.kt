/*
 * MuSicX Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package dev.musicx.desktop.ui.screens.settings

import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import dev.musicx.desktop.auth.DesktopDiscordLogin
import dev.musicx.desktop.spotifyimport.SpotifyLoginDialog
import dev.musicx.desktop.spotifyimport.desktopSpotifyImportManager
import dev.musicx.desktop.state.app.AppState
import dev.musicx.desktop.state.app.UiState
import dev.musicx.desktop.state.playback.DiscordRpcState
import dev.musicx.desktop.state.playback.ListenTogetherState
import dev.musicx.desktop.state.playback.PlayerState
import dev.musicx.desktop.ui.screens.DiscordLoginDialog
import dev.musicx.shared.discord.openDiscordUrl
import dev.musicx.shared.settings.Settings
import dev.musicx.shared.ui.components.Dialog
import dev.musicx.shared.ui.components.PopupContent
import dev.musicx.shared.ui.settings.IntegrationPlaybackState
import dev.musicx.shared.ui.settings.SettingsPlatform
import dev.musicx.shared.ui.settings.SharedIntegrationsHost
import dev.musicx.shared.ui.settings.SharedIntegrationsScreen
import dev.musicx.shared.generated.resources.Res
import dev.musicx.shared.generated.resources.spotify_import_error_sign_in_failed
import dev.musicx.shared.generated.resources.spotify_status_not_initialized
import dev.musicx.shared.spotify.SpotifyYouTubeMapperFactory
import dev.musicx.shared.spotifyimport.SpotifyPlaybackResolverFactory
import dev.musicx.shared.state.GlobalYouTubeRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString

@Composable
fun IntegrationsOverlay(colorScheme: ColorScheme) {
    val discordLogin = DesktopDiscordLogin.flow
    val scope = rememberCoroutineScope()
    val playbackSpeed by PlayerState.player.playbackSpeed.collectAsState()
    val playbackDuration by PlayerState.playbackDuration.collectAsState()
    var showSpotifyLogin by remember { mutableStateOf(false) }
    var spotifyLoginInProgress by remember { mutableStateOf(false) }
    var spotifyError by remember { mutableStateOf<String?>(null) }

    Dialog(
        onDismissRequest = { UiState.showIntegrationsSettings = false },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Card(
            modifier =
                Modifier
                    .padding(16.dp)
                    .widthIn(max = 1180.dp)
                    .fillMaxWidth()
                    .heightIn(max = 800.dp)
                    .fillMaxHeight(),
            shape = RoundedCornerShape(32.dp),
            colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        ) {
            SharedIntegrationsScreen(
                host =
                    SharedIntegrationsHost(
                        platform = SettingsPlatform.DESKTOP,
                        onClose = { UiState.showIntegrationsSettings = false },
                        playbackPosition = PlayerState.playbackPosition,
                        currentPositionMs = PlayerState::currentMediaPositionMs,
                        playback =
                            PlayerState.currentTrack?.let { song ->
                                IntegrationPlaybackState(
                                    song = song,
                                    artworkUrl = PlayerState.currentArtworkUrl,
                                    artistArtworkUrl = song.artists.firstOrNull()?.thumbnail,
                                    durationMs = playbackDuration,
                                    isPlaying = PlayerState.isPlaying,
                                    playbackSpeed = playbackSpeed,
                                )
                            },
                        onStartDiscordLogin = discordLogin::start,
                        discordLoginInProgress = discordLogin.session != null || discordLogin.exchanging,
                        onDiscordSettingsChanged = {
                            DiscordRpcState.discordRpcEnabled = Settings.shared.enableDiscordIntegration
                            DiscordRpcState.updateDiscordRpc()
                        },
                        deviceConnect = rememberDeviceConnectSettingsHost(),
                        listenTogetherManager = ListenTogetherState.listenTogetherManager,
                        onOpenUrl = { openDiscordUrl(it, null) },
                        onCopyText = { _, text -> AppState.copyToClipboard(text) },
                        spotifyLoginInProgress = spotifyLoginInProgress,
                        onStartSpotifyLogin = { showSpotifyLogin = true },
                        onSpotifySettingsChanged = {},
                        onSpotifyLogout = { desktopSpotifyImportManager.logout() },
                        onStartSpotifyPlayback = {
                            if (!desktopSpotifyImportManager.isAuthenticated) {
                                showSpotifyLogin = true
                            } else {
                                scope.launch {
                                    val mapper = SpotifyYouTubeMapperFactory.create(GlobalYouTubeRepository)
                                    val resolver =
                                        SpotifyPlaybackResolverFactory.create(desktopSpotifyImportManager, mapper)
                                    runCatching { resolver.resolveLikedSongs() }
                                        .onSuccess { songs ->
                                            if (songs.isEmpty()) {
                                                AppState.snackbarHostState.showSnackbar(
                                                    getString(Res.string.spotify_status_not_initialized),
                                                    duration = SnackbarDuration.Short,
                                                )
                                            } else {
                                                PlayerState.playTrack(
                                                    songs.first(),
                                                    songs,
                                                    songsOnly = true,
                                                )
                                            }
                                        }
                                        .onFailure { error ->
                                            AppState.snackbarHostState.showSnackbar(
                                                error.message ?: getString(Res.string.spotify_import_error_sign_in_failed),
                                                duration = SnackbarDuration.Short,
                                            )
                                        }
                                }
                            }
                        },
                    ),
            )

            if (showSpotifyLogin) {
                SpotifyLoginDialog(
                    isAuthenticating = spotifyLoginInProgress,
                    authenticationFailed = spotifyError != null,
                    onDismissRequest = { showSpotifyLogin = false; spotifyError = null },
                    onSessionCookie = { cookie ->
                        if (!spotifyLoginInProgress) {
                            spotifyLoginInProgress = true
                            scope.launch {
                                try {
                                    desktopSpotifyImportManager.authenticate(cookie)
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
                )
            }
        }
    }

    PopupContent(discordLogin.session) { session ->
        DiscordLoginDialog(
            session = session,
            onDismissRequest = discordLogin::cancel,
            onComplete = discordLogin::cancel,
        )
    }
}
