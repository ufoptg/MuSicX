/**
 * MuSicX Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package dev.musicx.android.ui.screens.settings

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import dev.musicx.android.BuildConfig
import dev.musicx.android.LocalMiniPlayerBottomInset
import dev.musicx.android.playback.LocalPlayerConnection
import dev.musicx.android.playback.resolveIncomingPlaybackSong
import dev.musicx.shared.discord.openDiscordUrl
import dev.musicx.shared.generated.resources.Res
import dev.musicx.shared.generated.resources.app_name
import dev.musicx.shared.generated.resources.logo
import dev.musicx.shared.generated.resources.youtube_music
import dev.musicx.shared.state.GlobalYouTubeRepository
import dev.musicx.shared.ui.screens.AboutCommunityLinkKind
import dev.musicx.shared.ui.screens.AboutScreen
import dev.musicx.shared.ui.settings.LocalSettingsPageHasTopBar
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun AndroidAboutScreen(
    onNavigateBack: () -> Unit,
    onDevtoolsUnlocked: () -> Unit = {},
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val miniPlayerInset = LocalMiniPlayerBottomInset.current
    val playerConnection = LocalPlayerConnection.current
    val coroutineScope = rememberCoroutineScope()
    val youtubeMusicLabel = stringResource(Res.string.youtube_music)

    AboutScreen(
        logo = painterResource(Res.drawable.logo),
        appName = stringResource(Res.string.app_name),
        version = BuildConfig.VERSION_NAME,
        isDebug = BuildConfig.DEBUG,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 24.dp),
        bottomInset = miniPlayerInset,
        onNavigateBack = onNavigateBack.takeUnless { LocalSettingsPageHasTopBar.current },
        onOpenCommunityLink = { link ->
            if (link.kind == AboutCommunityLinkKind.DISCORD) {
                runCatching { context.openDiscordUrl(link.url) }
            } else {
                runCatching { uriHandler.openUri(link.url) }
            }
        },
        onDevtoolsUnlocked = onDevtoolsUnlocked,
        onPlayFavoriteSong =
            playerConnection?.let { connection ->
                { videoId ->
                    coroutineScope.launch {
                        val song =
                            resolveIncomingPlaybackSong(
                                videoId = videoId,
                                fallbackArtist = youtubeMusicLabel,
                                loadMetadata = GlobalYouTubeRepository.instance::getSongMetadata,
                                loadPlayerInformation = GlobalYouTubeRepository.instance::getSongPlayerInformation,
                            )
                        connection.playSong(song, "")
                    }
                }
            },
    )
}
