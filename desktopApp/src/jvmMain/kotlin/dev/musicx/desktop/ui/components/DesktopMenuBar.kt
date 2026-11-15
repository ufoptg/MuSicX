/**
 * MuSicX Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package dev.musicx.desktop.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyShortcut
import androidx.compose.ui.window.FrameWindowScope
import androidx.compose.ui.window.MenuBar
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowState
import dev.musicx.desktop.input.triggerSearchShortcut
import dev.musicx.desktop.state.app.AppState
import dev.musicx.desktop.state.app.UiState
import dev.musicx.desktop.state.auth.AuthState
import dev.musicx.desktop.state.content.ArtistState
import dev.musicx.desktop.state.content.BrowseState
import dev.musicx.desktop.state.content.LibraryState
import dev.musicx.desktop.state.content.PlaylistAlbumState
import dev.musicx.desktop.state.content.PodcastState
import dev.musicx.desktop.state.playback.ListenTogetherState
import dev.musicx.desktop.state.playback.LyricsState
import dev.musicx.desktop.state.playback.PlayerState
import dev.musicx.desktop.storage.DownloadManager
import dev.musicx.desktop.storage.DownloadState
import dev.musicx.desktop.utils.MacMenuBarTemplateHelper
import dev.musicx.desktop.utils.ThemeUtils
import dev.musicx.shared.generated.resources.Res
import dev.musicx.shared.generated.resources.close
import dev.musicx.shared.generated.resources.home
import dev.musicx.shared.generated.resources.menu_about_metrolist
import dev.musicx.shared.generated.resources.menu_account
import dev.musicx.shared.generated.resources.menu_add_to_playlist
import dev.musicx.shared.generated.resources.menu_add_to_queue
import dev.musicx.shared.generated.resources.menu_application_logs
import dev.musicx.shared.generated.resources.menu_back
import dev.musicx.shared.generated.resources.menu_controls
import dev.musicx.shared.generated.resources.menu_copy
import dev.musicx.shared.generated.resources.menu_copy_link
import dev.musicx.shared.generated.resources.menu_create_station
import dev.musicx.shared.generated.resources.menu_cut
import dev.musicx.shared.generated.resources.menu_decrease_volume
import dev.musicx.shared.generated.resources.menu_download
import dev.musicx.shared.generated.resources.menu_edit
import dev.musicx.shared.generated.resources.menu_enter_full_screen
import dev.musicx.shared.generated.resources.menu_exit_full_screen
import dev.musicx.shared.generated.resources.menu_file
import dev.musicx.shared.generated.resources.menu_forward
import dev.musicx.shared.generated.resources.menu_fullscreen_player
import dev.musicx.shared.generated.resources.menu_go_to_current_song
import dev.musicx.shared.generated.resources.menu_help
import dev.musicx.shared.generated.resources.menu_hide_lyrics
import dev.musicx.shared.generated.resources.menu_hide_playing_next
import dev.musicx.shared.generated.resources.menu_history
import dev.musicx.shared.generated.resources.menu_increase_volume
import dev.musicx.shared.generated.resources.menu_keyboard_shortcuts
import dev.musicx.shared.generated.resources.menu_library
import dev.musicx.shared.generated.resources.menu_love_song
import dev.musicx.shared.generated.resources.menu_minimize
import dev.musicx.shared.generated.resources.menu_miniplayer
import dev.musicx.shared.generated.resources.menu_mute
import dev.musicx.shared.generated.resources.menu_new_playlist
import dev.musicx.shared.generated.resources.menu_next
import dev.musicx.shared.generated.resources.menu_paste
import dev.musicx.shared.generated.resources.menu_pause
import dev.musicx.shared.generated.resources.menu_play
import dev.musicx.shared.generated.resources.menu_play_next
import dev.musicx.shared.generated.resources.menu_previous
import dev.musicx.shared.generated.resources.menu_redo
import dev.musicx.shared.generated.resources.menu_reload
import dev.musicx.shared.generated.resources.menu_remove_download
import dev.musicx.shared.generated.resources.menu_repeat
import dev.musicx.shared.generated.resources.menu_repeat_all
import dev.musicx.shared.generated.resources.menu_repeat_off
import dev.musicx.shared.generated.resources.menu_repeat_one
import dev.musicx.shared.generated.resources.menu_report_issue
import dev.musicx.shared.generated.resources.menu_select_all
import dev.musicx.shared.generated.resources.menu_set_volume_maximum
import dev.musicx.shared.generated.resources.menu_set_volume_minimum
import dev.musicx.shared.generated.resources.menu_share
import dev.musicx.shared.generated.resources.menu_show_album_in_library
import dev.musicx.shared.generated.resources.menu_show_artist_in_library
import dev.musicx.shared.generated.resources.menu_show_in_youtube_music
import dev.musicx.shared.generated.resources.menu_show_lyrics
import dev.musicx.shared.generated.resources.menu_show_playing_next
import dev.musicx.shared.generated.resources.menu_shuffle
import dev.musicx.shared.generated.resources.menu_shuffle_off
import dev.musicx.shared.generated.resources.menu_shuffle_on
import dev.musicx.shared.generated.resources.menu_sign_in
import dev.musicx.shared.generated.resources.menu_sign_out
import dev.musicx.shared.generated.resources.menu_song
import dev.musicx.shared.generated.resources.menu_stop
import dev.musicx.shared.generated.resources.menu_switch_account
import dev.musicx.shared.generated.resources.menu_undo
import dev.musicx.shared.generated.resources.menu_unlove_song
import dev.musicx.shared.generated.resources.menu_unmute
import dev.musicx.shared.generated.resources.menu_view
import dev.musicx.shared.generated.resources.menu_window
import dev.musicx.shared.generated.resources.menu_zoom
import dev.musicx.shared.generated.resources.search
import dev.musicx.shared.generated.resources.settings
import dev.musicx.shared.generated.resources.statistics
import dev.musicx.shared.generated.resources.tray_quit_metrolist
import dev.musicx.shared.generated.resources.user
import dev.musicx.shared.model.RepeatMode
import dev.musicx.shared.settings.NavigationId
import dev.musicx.shared.settings.Settings
import dev.musicx.shared.ui.icons.MuSicXIcons
import dev.musicx.shared.ui.navigation.Navigator
import dev.musicx.shared.ui.navigation.Screen
import dev.musicx.shared.ui.navigation.screenForNavigationId
import dev.musicx.shared.utils.OsUtil
import dev.musicx.shared.utils.ShareUrls
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import java.awt.Desktop
import java.net.URI

@Composable
private fun rememberMenuPainter(
    image: ImageVector,
    tint: Color,
): Painter {
    val vectorPainter = rememberVectorPainter(image)
    return remember(vectorPainter, tint) {
        object : Painter() {
            override val intrinsicSize: Size
                get() = vectorPainter.intrinsicSize

            override fun DrawScope.onDraw() {
                with(vectorPainter) {
                    draw(size = size, colorFilter = ColorFilter.tint(tint))
                }
            }
        }
    }
}

@Composable
fun FrameWindowScope.DesktopMenuBar(
    navigator: Navigator,
    windowState: WindowState,
    toggleWindowFullscreen: () -> Unit,
    onRefresh: () -> Unit,
    onCloseWindow: () -> Unit,
    onQuit: () -> Unit,
) {
    val currentTrack = PlayerState.currentTrack
    val isTrackActive = currentTrack != null
    val isListenTogetherGuest = ListenTogetherState.isListenTogetherGuest()
    val isLiked =
        currentTrack?.let {
            PlayerState.isLiked(it.id)
        } == true
    val isDownloaded =
        currentTrack?.let {
            LibraryState.downloadStates[it.id] == DownloadState.DOWNLOADED
        } == true

    val isDark = Settings.shared.systemInDarkTheme || ThemeUtils.getCurrentThemeIsDark()
    val iconColor = if (isDark) Color(0xFFE8E8E8) else Color(0xFF242424)
    val likedColor = Color(0xFFFF2D55)

    val newPlaylistPainter = rememberMenuPainter(MuSicXIcons.AutoMirrored.Outlined.PlaylistAdd, iconColor)
    val playNextPainter = rememberMenuPainter(MuSicXIcons.AutoMirrored.Filled.PlaylistPlay, iconColor)
    val searchPainter = rememberMenuPainter(MuSicXIcons.Outlined.Search, iconColor)
    val reloadPainter = rememberMenuPainter(MuSicXIcons.Outlined.Refresh, iconColor)
    val settingsPainter = rememberMenuPainter(MuSicXIcons.Outlined.Settings, iconColor)
    val playPainter = rememberMenuPainter(MuSicXIcons.Outlined.PlayArrow, iconColor)
    val pausePainter = rememberMenuPainter(MuSicXIcons.Filled.Pause, iconColor)
    val stopPainter = rememberMenuPainter(MuSicXIcons.Filled.Pause, iconColor)
    val nextPainter = rememberMenuPainter(MuSicXIcons.Filled.SkipNext, iconColor)
    val previousPainter = rememberMenuPainter(MuSicXIcons.Rounded.SkipPrevious, iconColor)
    val musicNotePainter = rememberMenuPainter(MuSicXIcons.Outlined.MusicNote, iconColor)
    val volumeUpPainter = rememberMenuPainter(MuSicXIcons.AutoMirrored.Outlined.VolumeUp, iconColor)
    val volumeDownPainter = rememberMenuPainter(MuSicXIcons.AutoMirrored.Outlined.VolumeDown, iconColor)
    val volumeOffPainter = rememberMenuPainter(MuSicXIcons.AutoMirrored.Outlined.VolumeOff, iconColor)
    val shufflePainter = rememberMenuPainter(MuSicXIcons.Rounded.Shuffle, iconColor)
    val repeatPainter = rememberMenuPainter(MuSicXIcons.Rounded.Repeat, iconColor)
    val backPainter = rememberMenuPainter(MuSicXIcons.AutoMirrored.Filled.KeyboardArrowLeft, iconColor)
    val forwardPainter = rememberMenuPainter(MuSicXIcons.AutoMirrored.Filled.KeyboardArrowRight, iconColor)
    val sharePainter = rememberMenuPainter(MuSicXIcons.Outlined.Share, iconColor)
    val queuePainter = rememberMenuPainter(MuSicXIcons.AutoMirrored.Outlined.QueueMusic, iconColor)
    val sensorsPainter = rememberMenuPainter(MuSicXIcons.Outlined.Sensors, iconColor)
    val personPainter = rememberMenuPainter(MuSicXIcons.Outlined.Person, iconColor)
    val albumPainter = rememberMenuPainter(MuSicXIcons.Outlined.Album, iconColor)
    val favoriteFilledPainter = rememberMenuPainter(MuSicXIcons.Filled.Favorite, likedColor)
    val favoriteBorderPainter = rememberMenuPainter(MuSicXIcons.Outlined.FavoriteBorder, iconColor)
    val downloadPainter = rememberMenuPainter(MuSicXIcons.Outlined.Download, iconColor)
    val offlinePinPainter = rememberMenuPainter(MuSicXIcons.Outlined.OfflinePin, iconColor)
    val lyricsPainter = rememberMenuPainter(MuSicXIcons.Filled.Lyrics, iconColor)
    val homePainter = rememberMenuPainter(MuSicXIcons.Outlined.Home, iconColor)
    val libraryPainter = rememberMenuPainter(MuSicXIcons.Outlined.LibraryMusic, iconColor)
    val historyPainter = rememberMenuPainter(MuSicXIcons.Outlined.History, iconColor)
    val statsPainter = rememberMenuPainter(MuSicXIcons.Outlined.BarChart, iconColor)
    val fullscreenPainter = rememberMenuPainter(MuSicXIcons.Outlined.Fullscreen, iconColor)
    val miniplayerPainter = rememberMenuPainter(MuSicXIcons.Filled.PictureInPicture, iconColor)
    val infoPainter = rememberMenuPainter(MuSicXIcons.Outlined.Info, iconColor)

    val isMac = OsUtil.isMac

    fun menuIcon(painter: Painter): Painter? = if (isMac) null else painter

    LaunchedEffect(
        Settings.shared.appLanguage,
        currentTrack?.id,
        isLiked,
        isDownloaded,
        PlayerState.isPlaying,
        PlayerState.volume,
        PlayerState.repeatMode,
        PlayerState.shuffleEnabled,
        PlayerState.isExpanded,
        UiState.showLyricsDrawer,
        UiState.showPersistentPlayerDrawer,
        navigator.canGoBack,
        navigator.canGoForward,
        AuthState.isSignedIn,
        AuthState.profilePicUrl,
        windowState.placement,
        Settings.shared.systemInDarkTheme,
    ) {
        MacMenuBarTemplateHelper.applyNativeIconsAndTemplates()
    }

    MenuBar {
        Menu(stringResource(Res.string.menu_file)) {
            Item(
                text = stringResource(Res.string.menu_new_playlist),
                icon = menuIcon(newPlaylistPainter),
                onClick = {
                    UiState.createPlaylistRequested = true
                    navigateToMainTab(NavigationId.LIBRARY, navigator)
                },
                shortcut = KeyShortcut(Key.N, meta = true),
            )
            Item(
                text = stringResource(Res.string.search),
                icon = menuIcon(searchPainter),
                onClick = { triggerSearchShortcut() },
                shortcut = KeyShortcut(Key.K, meta = true),
            )
            Item(
                text = stringResource(Res.string.menu_reload),
                icon = menuIcon(reloadPainter),
                onClick = onRefresh,
                shortcut = KeyShortcut(Key.R, meta = true),
            )
            Separator()
            Item(
                text = stringResource(Res.string.settings),
                icon = menuIcon(settingsPainter),
                onClick = { UiState.showSettings = true },
                shortcut = KeyShortcut(Key.Comma, meta = true),
            )
            Separator()
            Item(
                text = stringResource(Res.string.close),
                onClick = onCloseWindow,
                shortcut = KeyShortcut(Key.W, meta = true),
            )
            Item(
                text = stringResource(Res.string.tray_quit_metrolist),
                onClick = onQuit,
                shortcut = KeyShortcut(Key.Q, meta = true),
            )
        }

        Menu(stringResource(Res.string.menu_edit)) {
            Item(
                text = stringResource(Res.string.menu_undo),
                onClick = {},
                enabled = false,
            )
            Item(
                text = stringResource(Res.string.menu_redo),
                onClick = {},
                enabled = false,
            )
            Separator()
            Item(
                text = stringResource(Res.string.menu_cut),
                onClick = {},
                enabled = false,
            )
            Item(
                text = stringResource(Res.string.menu_copy),
                onClick = {},
                enabled = false,
            )
            Item(
                text = stringResource(Res.string.menu_paste),
                onClick = {},
                enabled = false,
            )
            Item(
                text = stringResource(Res.string.menu_select_all),
                onClick = {},
                enabled = false,
            )
        }

        Menu(stringResource(Res.string.menu_song)) {
            Item(
                text = stringResource(Res.string.menu_add_to_playlist),
                icon = menuIcon(newPlaylistPainter),
                onClick = {
                    currentTrack?.let { PlayerState.showAddToPlaylistSong = it }
                },
                enabled = isTrackActive,
            )
            Menu(stringResource(Res.string.menu_share), enabled = isTrackActive) {
                Item(
                    text = stringResource(Res.string.menu_copy_link),
                    icon = menuIcon(sharePainter),
                    onClick = {
                        currentTrack?.let { AppState.copyToClipboard(ShareUrls.song(it.id)) }
                    },
                )
            }
            Separator()
            Item(
                text = stringResource(Res.string.menu_play_next),
                icon = menuIcon(playNextPainter),
                onClick = {
                    currentTrack?.let { PlayerState.playNext(it) }
                },
                enabled = isTrackActive,
            )
            Item(
                text = stringResource(Res.string.menu_add_to_queue),
                icon = menuIcon(queuePainter),
                onClick = {
                    currentTrack?.let { PlayerState.addToQueue(it) }
                },
                enabled = isTrackActive,
            )
            Item(
                text = stringResource(Res.string.menu_create_station),
                icon = menuIcon(sensorsPainter),
                onClick = {
                    currentTrack?.let { PlayerState.startRadio(it) }
                },
                enabled = isTrackActive,
            )
            Separator()
            Item(
                text = stringResource(Res.string.menu_show_artist_in_library),
                icon = menuIcon(personPainter),
                onClick = {
                    currentTrack?.artists?.firstOrNull { it.id != null }?.let {
                        ArtistState.fetchArtistData(it.id!!)
                    }
                },
                enabled = currentTrack?.artists?.any { it.id != null } == true,
            )
            Item(
                text = stringResource(Res.string.menu_show_album_in_library),
                icon = menuIcon(albumPainter),
                onClick = {
                    currentTrack?.album?.id?.let {
                        PlaylistAlbumState.fetchAlbumData(it)
                    }
                },
                enabled = currentTrack?.album?.id != null,
            )
            Item(
                text = stringResource(Res.string.menu_show_in_youtube_music),
                icon = menuIcon(musicNotePainter),
                onClick = {
                    currentTrack?.let {
                        try {
                            Desktop.getDesktop().browse(URI(ShareUrls.song(it.id)))
                        } catch (_: Exception) {
                        }
                    }
                },
                enabled = isTrackActive,
            )
            Separator()
            Item(
                text =
                    if (isLiked) {
                        stringResource(Res.string.menu_unlove_song)
                    } else {
                        stringResource(Res.string.menu_love_song)
                    },
                icon = menuIcon(if (isLiked) favoriteFilledPainter else favoriteBorderPainter),
                onClick = {
                    currentTrack?.let { PlayerState.toggleLike(it) }
                },
                enabled = isTrackActive,
            )
            Item(
                text =
                    if (isDownloaded) {
                        stringResource(Res.string.menu_remove_download)
                    } else {
                        stringResource(Res.string.menu_download)
                    },
                icon = menuIcon(if (isDownloaded) offlinePinPainter else downloadPainter),
                onClick = {
                    currentTrack?.let {
                        if (isDownloaded) {
                            DownloadManager.deleteDownload(it.id)
                        } else {
                            DownloadManager.download(it)
                        }
                    }
                },
                enabled = isTrackActive,
            )
        }

        Menu(stringResource(Res.string.menu_view)) {
            Item(
                text =
                    if (UiState.showLyricsDrawer) {
                        stringResource(Res.string.menu_hide_lyrics)
                    } else {
                        stringResource(Res.string.menu_show_lyrics)
                    },
                icon = menuIcon(lyricsPainter),
                onClick = { LyricsState.toggleLyricsDrawer() },
            )
            Item(
                text =
                    if (UiState.showPersistentPlayerDrawer) {
                        stringResource(Res.string.menu_hide_playing_next)
                    } else {
                        stringResource(Res.string.menu_show_playing_next)
                    },
                icon = menuIcon(queuePainter),
                onClick = UiState::togglePersistentPlayerDrawer,
            )
            Item(
                text = stringResource(Res.string.menu_fullscreen_player),
                icon = menuIcon(fullscreenPainter),
                onClick = { PlayerState.toggleExpandedMode() },
            )
            Separator()
            Item(
                text = stringResource(Res.string.home),
                icon = menuIcon(homePainter),
                onClick = { navigateToMainTab(NavigationId.HOME, navigator) },
            )
            Item(
                text = stringResource(Res.string.menu_library),
                icon = menuIcon(libraryPainter),
                onClick = { navigateToMainTab(NavigationId.LIBRARY, navigator) },
            )
            Item(
                text = stringResource(Res.string.menu_history),
                icon = menuIcon(historyPainter),
                onClick = { navigateToMainTab(NavigationId.HISTORY, navigator) },
            )
            Item(
                text = stringResource(Res.string.statistics),
                icon = menuIcon(statsPainter),
                onClick = { navigateToMainTab(NavigationId.STATISTICS, navigator) },
            )
            Separator()
            Item(
                text =
                    if (windowState.placement == WindowPlacement.Fullscreen) {
                        stringResource(Res.string.menu_exit_full_screen)
                    } else {
                        stringResource(Res.string.menu_enter_full_screen)
                    },
                icon = menuIcon(fullscreenPainter),
                onClick = toggleWindowFullscreen,
                shortcut = KeyShortcut(Key.F, meta = true, ctrl = true),
            )
        }

        Menu(stringResource(Res.string.menu_controls)) {
            Item(
                text =
                    if (PlayerState.isPlaying) {
                        stringResource(Res.string.menu_pause)
                    } else {
                        stringResource(Res.string.menu_play)
                    },
                icon = menuIcon(if (PlayerState.isPlaying) pausePainter else playPainter),
                onClick = { PlayerState.togglePlay() },
                enabled = isTrackActive && !isListenTogetherGuest,
            )
            Item(
                text = stringResource(Res.string.menu_stop),
                icon = menuIcon(stopPainter),
                onClick = { PlayerState.stop() },
                shortcut = KeyShortcut(Key.Period, meta = true),
                enabled = isTrackActive && !isListenTogetherGuest,
            )
            Item(
                text = stringResource(Res.string.menu_next),
                icon = menuIcon(nextPainter),
                onClick = { PlayerState.skipNext() },
                shortcut = KeyShortcut(Key.DirectionRight, meta = true),
                enabled = isTrackActive && !isListenTogetherGuest,
            )
            Item(
                text = stringResource(Res.string.menu_previous),
                icon = menuIcon(previousPainter),
                onClick = { PlayerState.skipPrevious() },
                shortcut = KeyShortcut(Key.DirectionLeft, meta = true),
                enabled = isTrackActive && !isListenTogetherGuest,
            )
            Item(
                text = stringResource(Res.string.menu_go_to_current_song),
                icon = menuIcon(musicNotePainter),
                onClick = {
                    if (isTrackActive) {
                        PlayerState.enterExpanded()
                    }
                },
                shortcut = KeyShortcut(Key.L, meta = true),
                enabled = isTrackActive,
            )
            Separator()
            Item(
                text = stringResource(Res.string.menu_set_volume_maximum),
                icon = menuIcon(volumeUpPainter),
                onClick = { PlayerState.setVolumeLevel(1f) },
                shortcut = KeyShortcut(Key.DirectionUp, meta = true, shift = true),
            )
            Item(
                text = stringResource(Res.string.menu_set_volume_minimum),
                icon = menuIcon(volumeOffPainter),
                onClick = { PlayerState.setVolumeLevel(0f) },
                shortcut = KeyShortcut(Key.DirectionDown, meta = true, shift = true),
            )
            Item(
                text = stringResource(Res.string.menu_increase_volume),
                icon = menuIcon(volumeUpPainter),
                onClick = { PlayerState.setVolumeLevel((PlayerState.volume + 0.1f).coerceIn(0f, 1f)) },
                shortcut = KeyShortcut(Key.DirectionUp, meta = true),
            )
            Item(
                text = stringResource(Res.string.menu_decrease_volume),
                icon = menuIcon(volumeDownPainter),
                onClick = { PlayerState.setVolumeLevel((PlayerState.volume - 0.1f).coerceIn(0f, 1f)) },
                shortcut = KeyShortcut(Key.DirectionDown, meta = true),
            )
            Item(
                text =
                    if (PlayerState.volume > 0f) {
                        stringResource(Res.string.menu_mute)
                    } else {
                        stringResource(Res.string.menu_unmute)
                    },
                icon = menuIcon(if (PlayerState.volume > 0f) volumeOffPainter else volumeUpPainter),
                onClick = {
                    if (PlayerState.volume > 0f) {
                        PlayerState.previousVolume = PlayerState.volume
                        PlayerState.setVolumeLevel(0f)
                    } else {
                        PlayerState.setVolumeLevel(PlayerState.previousVolume)
                    }
                },
            )
            if (!isListenTogetherGuest) {
                Separator()
                Menu(stringResource(Res.string.menu_shuffle)) {
                    RadioButtonItem(
                        text = stringResource(Res.string.menu_shuffle_off),
                        icon = menuIcon(shufflePainter),
                        selected = !PlayerState.shuffleEnabled,
                        onClick = {
                            if (PlayerState.shuffleEnabled) PlayerState.toggleShuffle()
                        },
                    )
                    RadioButtonItem(
                        text = stringResource(Res.string.menu_shuffle_on),
                        icon = menuIcon(shufflePainter),
                        selected = PlayerState.shuffleEnabled,
                        onClick = {
                            if (!PlayerState.shuffleEnabled) PlayerState.toggleShuffle()
                        },
                    )
                }
                Menu(stringResource(Res.string.menu_repeat)) {
                    RadioButtonItem(
                        text = stringResource(Res.string.menu_repeat_off),
                        icon = menuIcon(repeatPainter),
                        selected = PlayerState.repeatMode == RepeatMode.OFF,
                        onClick = { PlayerState.repeatMode = RepeatMode.OFF },
                    )
                    RadioButtonItem(
                        text = stringResource(Res.string.menu_repeat_all),
                        icon = menuIcon(repeatPainter),
                        selected = PlayerState.repeatMode == RepeatMode.ALL,
                        onClick = { PlayerState.repeatMode = RepeatMode.ALL },
                    )
                    RadioButtonItem(
                        text = stringResource(Res.string.menu_repeat_one),
                        icon = menuIcon(repeatPainter),
                        selected = PlayerState.repeatMode == RepeatMode.ONE,
                        onClick = { PlayerState.repeatMode = RepeatMode.ONE },
                    )
                }
            }
            Separator()
            Item(
                text = stringResource(Res.string.menu_back),
                icon = menuIcon(backPainter),
                onClick = { navigator.goBack() },
                shortcut = KeyShortcut(Key.LeftBracket, meta = true),
                enabled = navigator.canGoBack,
            )
            Item(
                text = stringResource(Res.string.menu_forward),
                icon = menuIcon(forwardPainter),
                onClick = { navigator.goForward() },
                shortcut = KeyShortcut(Key.RightBracket, meta = true),
                enabled = navigator.canGoForward,
            )
        }

        Menu(stringResource(Res.string.menu_account)) {
            if (AuthState.isSignedIn) {
                val accountDisplayName =
                    Settings.shared.accountName.ifBlank {
                        Settings.shared.accountEmail.ifBlank { stringResource(Res.string.user) }
                    }
                val accountSubtitle =
                    Settings.shared.accountChannelHandle.ifBlank {
                        Settings.shared.accountEmail
                    }

                Item(
                    text = accountDisplayName,
                    icon = menuIcon(personPainter),
                    onClick = {
                        navigateToMainTab(NavigationId.LIBRARY, navigator)
                    },
                )
                if (accountSubtitle.isNotBlank() && accountSubtitle != accountDisplayName) {
                    Item(
                        text = accountSubtitle,
                        onClick = {},
                        enabled = false,
                    )
                }
                Separator()
                Item(
                    text = stringResource(Res.string.menu_switch_account),
                    onClick = { UiState.showSwitchChannel = true },
                )
                Item(
                    text = stringResource(Res.string.settings),
                    icon = menuIcon(settingsPainter),
                    onClick = {
                        UiState.showSettings = true
                    },
                )
                Separator()
                Item(
                    text = stringResource(Res.string.menu_sign_out),
                    onClick = {
                        AppState.scope.launch {
                            AuthState.signOut()
                        }
                    },
                )
            } else {
                Item(
                    text = stringResource(Res.string.menu_sign_in),
                    icon = menuIcon(personPainter),
                    onClick = { UiState.showSignIn = true },
                )
            }
        }

        Menu(stringResource(Res.string.menu_window)) {
            Item(
                text = stringResource(Res.string.menu_minimize),
                onClick = { windowState.isMinimized = true },
                shortcut = KeyShortcut(Key.M, meta = true),
            )
            Item(
                text = stringResource(Res.string.menu_zoom),
                onClick = {
                    windowState.placement =
                        if (windowState.placement == WindowPlacement.Maximized) {
                            WindowPlacement.Floating
                        } else {
                            WindowPlacement.Maximized
                        }
                },
            )
            Separator()
            Item(
                text = stringResource(Res.string.menu_miniplayer),
                icon = menuIcon(miniplayerPainter),
                onClick = { PlayerState.showMiniplayer = !PlayerState.showMiniplayer },
                shortcut = KeyShortcut(Key.M, meta = true, shift = true),
            )
        }

        Menu(stringResource(Res.string.menu_help)) {
            Item(
                text = stringResource(Res.string.menu_keyboard_shortcuts),
                onClick = { UiState.showShortcutsDialog = true },
                shortcut = KeyShortcut(Key.Slash, meta = true),
            )
            Item(
                text = stringResource(Res.string.menu_application_logs),
                onClick = { UiState.showLogViewer = true },
                shortcut = KeyShortcut(Key.L, meta = true, shift = true),
            )
            Separator()
            Item(
                text = stringResource(Res.string.menu_report_issue),
                onClick = {
                    try {
                        Desktop.getDesktop().browse(URI("https://github.com/MuSicXGroup/MuSicX/issues"))
                    } catch (_: Exception) {
                    }
                },
            )
            Item(
                text = stringResource(Res.string.menu_about_metrolist),
                icon = menuIcon(infoPainter),
                onClick = {
                    UiState.showSettings = true
                    UiState.showAboutSettings = true
                },
            )
        }
    }
}

private fun navigateToMainTab(
    navId: String,
    navigator: Navigator,
) {
    screenForNavigationId(navId)?.let(navigator::navigate)
}
