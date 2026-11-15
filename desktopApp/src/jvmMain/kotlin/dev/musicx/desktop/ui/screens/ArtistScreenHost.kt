package dev.musicx.desktop.ui.screens

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import dev.musicx.desktop.state.app.AppState
import dev.musicx.desktop.state.app.UiState
import dev.musicx.desktop.state.content.ArtistState
import dev.musicx.desktop.state.content.BrowseState
import dev.musicx.desktop.state.content.HomeState
import dev.musicx.desktop.state.content.PlaylistAlbumState
import dev.musicx.desktop.state.content.PodcastState
import dev.musicx.desktop.state.playback.PlayerState
import dev.musicx.desktop.ui.components.AlbumContextMenu
import dev.musicx.desktop.ui.components.AnimatedCover
import dev.musicx.desktop.ui.components.ArtistContextMenu
import dev.musicx.desktop.ui.components.EpisodeContextMenu
import dev.musicx.desktop.ui.components.PlaylistContextMenu
import dev.musicx.desktop.ui.components.PodcastContextMenu
import dev.musicx.desktop.ui.components.SongContextMenu
import dev.musicx.shared.generated.resources.Res
import dev.musicx.shared.generated.resources.retry
import dev.musicx.shared.model.AlbumItem
import dev.musicx.shared.model.ArtistItem
import dev.musicx.shared.model.ArtistSource
import dev.musicx.shared.model.BrowseItem
import dev.musicx.shared.model.EpisodeItem
import dev.musicx.shared.model.PlaylistItem
import dev.musicx.shared.model.PodcastItem
import dev.musicx.shared.model.SongItem
import dev.musicx.shared.model.YTItem
import dev.musicx.shared.model.forArtistScreenMenu
import dev.musicx.shared.model.toSongItem
import dev.musicx.shared.playback.fullArtistShelfQueue
import dev.musicx.shared.settings.BlockedContent
import dev.musicx.shared.settings.Settings
import dev.musicx.shared.ui.components.CollectionPreviews
import dev.musicx.shared.ui.components.ContainedLoadingIndicator
import dev.musicx.shared.ui.menu.contextMenuAnchor
import dev.musicx.shared.ui.menu.cursorContextMenu
import dev.musicx.shared.ui.menu.rememberCursorContextMenuState
import dev.musicx.shared.ui.navigation.LocalNavigator
import dev.musicx.shared.ui.navigation.Screen
import dev.musicx.shared.ui.screens.ArtistScreen
import dev.musicx.shared.utils.Logger
import dev.musicx.shared.viewmodels.LastFmArtistCache
import dev.musicx.shared.viewmodels.artistCatalogTitles
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

@Composable
fun ArtistScreenHost() {
    val navigator = LocalNavigator.current
    val artistId =
        (navigator.state.currentRoute as? Screen.Artist)?.id ?: ArtistState.selectedArtistId ?: ArtistState.loadedArtistId ?: return
    val artist = (ArtistState.artistData["header"]?.firstOrNull() as? ArtistItem)?.takeIf { it.id == artistId }
    val artistMenu = rememberCursorContextMenuState()
    val itemMenu = rememberCursorContextMenuState()
    val selectedItem = remember { mutableStateOf<YTItem?>(null) }
    val banner =
        artist?.let { loadedArtist ->
            ArtistState.artistBannerCache[loadedArtist.id]
                ?: when (ArtistState.artistBannerSource) {
                    ArtistSource.YOUTUBE -> {
                        loadedArtist.banner ?: loadedArtist.thumbnail
                    }

                    ArtistSource.ITUNES -> {
                        ArtistState.appleMusicArtworkUrl
                            ?: (loadedArtist.banner ?: loadedArtist.thumbnail).takeIf {
                                !Settings.shared.enableAppleMusicArtistImages || ArtistState.isAppleMusicImageLookupComplete
                            }
                    }

                    ArtistSource.LASTFM -> {
                        ArtistState.lastFmImageUrl ?: loadedArtist.banner ?: loadedArtist.thumbnail
                    }

                    ArtistSource.CUSTOM -> {
                        ArtistState.getCustomArtistBanner(loadedArtist.id) ?: loadedArtist.banner ?: loadedArtist.thumbnail
                    }
                }
        }
    val thumbnail =
        artist?.thumbnail.takeUnless {
            ArtistState.artistBannerSource == ArtistSource.ITUNES &&
                Settings.shared.enableAppleMusicArtistImages &&
                (!ArtistState.isAppleMusicImageLookupComplete || ArtistState.appleMusicArtworkUrl != null)
        }
    val displayArtist = remember(artist, banner, thumbnail) { artist?.copy(banner = banner, thumbnail = thumbnail) }
    val initialArtist = remember(artistId) { CollectionPreviews.artist(artistId) ?: ArtistItem(artistId, "", null) }
    val menuArtist = displayArtist ?: initialArtist
    val listState = artist?.let { HomeState.rememberListState("artist_${it.id}") }
    val lastFmCache = LastFmArtistCache.shared
    val contentLanguage = Settings.shared.lastResolvedContentLanguage
    val lastFmArtist by remember(artistId, contentLanguage) { lastFmCache.artist(artistId) }
        .collectAsState(lastFmCache.get(artistId))
    LaunchedEffect(artistId, artist?.title, ArtistState.isArtistLoading, contentLanguage) {
        val artistName = artist?.title
        if (artistName == null || ArtistState.isArtistLoading) return@LaunchedEffect
        try {
            lastFmCache.refresh(artistId, artistName, artistCatalogTitles(ArtistState.artistData))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.w("ArtistScreenHost", "Last.fm refresh failed", e)
        }
    }
    LaunchedEffect(artist?.id) {
        artistMenu.dismiss()
        itemMenu.dismiss()
        selectedItem.value = null
    }
    LaunchedEffect(
        artist?.title,
        Settings.shared.enableAppleMusicArtistImages,
        Settings.shared.enableAppleMusicArtistVideos,
        Settings.shared.appleMusicArtistVideoResolution,
        Settings.shared.lastResolvedContentCountry,
        Settings.shared.lastResolvedContentLanguage,
    ) {
        ArtistState.refreshAppleMusicArtwork()
    }
    ArtistScreen(
        artist = displayArtist,
        artistId = artistId,
        currentSongId = PlayerState.currentTrack?.id,
        isPlaying = PlayerState.isPlaying,
        sections = ArtistState.artistData,
        onItemsVisible = ArtistState::loadItemMetadata,
        loadableSections = ArtistState.artistTracksState.loadableSections,
        sectionContinuation = { title ->
            LaunchedEffect(title, ArtistState.artistTracksState.sectionContinuations[title]) {
                if (title !in ArtistState.artistTracksState.failedSections) ArtistState.loadSection(title)
            }
            if (title in ArtistState.artistTracksState.failedSections) {
                TextButton(onClick = { ArtistState.loadSection(title) }, shapes = ButtonDefaults.shapes()) {
                    Text(stringResource(Res.string.retry))
                }
            } else {
                ContainedLoadingIndicator()
            }
        },
        sectionBrowseIds = ArtistState.artistSectionBrowseIds,
        isLoading = ArtistState.isArtistLoading || (artist == null && ArtistState.selectedArtistId != artistId),
        errorMessage = ArtistState.artistLoadError,
        onRefresh = {
            ArtistState.fetchArtistData(artistId, addToHistory = false)
        },
        onItemClick = { openArtistItem(it) },
        onAlbumArtworkClick = { album, token -> PlaylistAlbumState.fetchAlbumData(album.id, artworkSource = token) },
        onItemLongClick = { item ->
            if (item !is BrowseItem) {
                selectedItem.value = item
                itemMenu.open()
            }
        },
        onSectionClick = { browseId, params, title -> BrowseState.fetchBrowseData(browseId, params, title) },
        onPlayItem = { if (it is AlbumItem) PlaylistAlbumState.playAlbum(it.id) },
        isSongLiked = PlayerState::isLiked,
        onLikeSong = PlayerState::toggleLike,
        onBackClick = { navigator.goBack() },
        onMenuClick = artistMenu::open,
        onPlayAllClick = ArtistState::playAllArtistSongs,
        onShuffleClick = ArtistState::shuffleArtistSongs,
        onMixClick = ArtistState::startArtistRadio,
        isSubscribed = ArtistState.artistIsSubscribed,
        subscribeSuccessCount = ArtistState.artistSubscribeSuccessCount,
        showSubscribe = true,
        onSubscribeClick = ArtistState::toggleArtistSubscription,
        onUnblockClick = { displayArtist?.let { BlockedContent.unblockArtist(it.id) } },
        description = displayArtist?.description?.takeIf(String::isNotBlank) ?: lastFmArtist?.summary,
        aboutImageUrl = lastFmArtist?.photos?.firstOrNull()?.url ?: artist?.thumbnail ?: artist?.banner,
        onAboutClick = {
            if (artist != null) {
                navigator.navigate(Screen.AboutArtist(artistId, artist.title, artist))
            }
        },
        listState = listState,
        headerWindowInsets = WindowInsets(0),
        modifier =
            Modifier
                .cursorContextMenu(artistMenu, openOnSecondaryClick = false)
                .contextMenuAnchor(itemMenu),
        headerVideoLayer =
            ArtistState.appleMusicVideoUrl
                ?.takeIf { artist != null && Settings.shared.enableAppleMusicArtistVideos }
                ?.let { url ->
                    { modifier ->
                        AnimatedCover(
                            url,
                            modifier,
                            active =
                                !UiState.videoFullscreenObscuresPlayer && !PlayerState.isExpanded,
                        )
                    }
                },
        overlay = {
            ArtistContextMenu(
                artist = menuArtist.forArtistScreenMenu(),
                expanded = artistMenu.expanded,
                offset = artistMenu.offset,
                isSubscribed = ArtistState.artistIsSubscribed,
                onToggleSubscription = if (artist != null) ArtistState::toggleArtistSubscription else null,
                onDismiss = artistMenu::dismiss,
            )
            val dismissItemMenu = {
                itemMenu.dismiss()
            }
            when (val item = selectedItem.value) {
                is SongItem -> {
                    SongContextMenu(item, expanded = itemMenu.expanded, offset = itemMenu.offset, onDismiss = dismissItemMenu)
                }

                is AlbumItem -> {
                    AlbumContextMenu(item, expanded = itemMenu.expanded, offset = itemMenu.offset, onDismiss = dismissItemMenu)
                }

                is ArtistItem -> {
                    ArtistContextMenu(
                        item,
                        expanded = itemMenu.expanded,
                        offset = itemMenu.offset,
                        onDismiss = dismissItemMenu,
                    )
                }

                is PlaylistItem -> {
                    PlaylistContextMenu(
                        item,
                        expanded = itemMenu.expanded,
                        offset = itemMenu.offset,
                        onDismiss = dismissItemMenu,
                    )
                }

                is PodcastItem -> {
                    PodcastContextMenu(
                        item,
                        expanded = itemMenu.expanded,
                        offset = itemMenu.offset,
                        onDismiss = dismissItemMenu,
                    )
                }

                is EpisodeItem -> {
                    EpisodeContextMenu(
                        item,
                        expanded = itemMenu.expanded,
                        offset = itemMenu.offset,
                        onDismiss = dismissItemMenu,
                    )
                }

                else -> {}
            }
        },
    )
}

private fun openArtistItem(item: YTItem) {
    when (item) {
        is SongItem -> {
            val sections = ArtistState.artistData.toMap()
            val browseIds = ArtistState.artistSectionBrowseIds
            AppState.scope.launch { PlayerState.playTrack(item, fullArtistShelfQueue(item, sections, browseIds)) }
        }

        is AlbumItem -> {
            PlaylistAlbumState.fetchAlbumData(item.id)
        }

        is ArtistItem -> {
            ArtistState.fetchArtistData(item)
        }

        is PlaylistItem -> {
            PlaylistAlbumState.fetchPlaylistData(item.id, item.thumbnail)
        }

        is BrowseItem -> {
            BrowseState.fetchBrowseData(item.id, item.params, item.title)
        }

        is PodcastItem -> {
            PodcastState.fetch(item.id)
        }

        is EpisodeItem -> {
            PlayerState.playTrack(item.toSongItem())
        }
    }
}
