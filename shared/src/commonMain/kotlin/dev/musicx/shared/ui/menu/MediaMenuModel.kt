@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package dev.musicx.shared.ui.menu

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.AnnotatedString
import dev.musicx.shared.generated.resources.Res
import dev.musicx.shared.generated.resources.action_download
import dev.musicx.shared.generated.resources.action_like
import dev.musicx.shared.generated.resources.add_to_playlist
import dev.musicx.shared.generated.resources.add_to_queue
import dev.musicx.shared.generated.resources.add_to_queue_desc
import dev.musicx.shared.generated.resources.artists
import dev.musicx.shared.generated.resources.block_artist
import dev.musicx.shared.generated.resources.cancel
import dev.musicx.shared.generated.resources.delete_downloaded_episodes
import dev.musicx.shared.generated.resources.delete_playlist
import dev.musicx.shared.generated.resources.details
import dev.musicx.shared.generated.resources.download_desc
import dev.musicx.shared.generated.resources.downloading_percent
import dev.musicx.shared.generated.resources.edit
import dev.musicx.shared.generated.resources.edit_artist
import dev.musicx.shared.generated.resources.edit_song_description
import dev.musicx.shared.generated.resources.export_album
import dev.musicx.shared.generated.resources.export_playlist
import dev.musicx.shared.generated.resources.hide_from_home
import dev.musicx.shared.generated.resources.hide_from_library
import dev.musicx.shared.generated.resources.hide_from_library_local
import dev.musicx.shared.generated.resources.monthly_audience_short
import dev.musicx.shared.generated.resources.move_down
import dev.musicx.shared.generated.resources.move_up
import dev.musicx.shared.generated.resources.percent_symbol
import dev.musicx.shared.generated.resources.pin_to_speed_dial
import dev.musicx.shared.generated.resources.play
import dev.musicx.shared.generated.resources.play_next
import dev.musicx.shared.generated.resources.play_next_desc
import dev.musicx.shared.generated.resources.play_oldest_first
import dev.musicx.shared.generated.resources.remove
import dev.musicx.shared.generated.resources.remove_all_downloads
import dev.musicx.shared.generated.resources.remove_download
import dev.musicx.shared.generated.resources.remove_from_library
import dev.musicx.shared.generated.resources.remove_from_playlist
import dev.musicx.shared.generated.resources.remove_from_saved_podcasts
import dev.musicx.shared.generated.resources.rename
import dev.musicx.shared.generated.resources.rename_playlist_description
import dev.musicx.shared.generated.resources.save_podcast
import dev.musicx.shared.generated.resources.select_songs
import dev.musicx.shared.generated.resources.share
import dev.musicx.shared.generated.resources.shuffle
import dev.musicx.shared.generated.resources.song_details
import dev.musicx.shared.generated.resources.subscribe
import dev.musicx.shared.generated.resources.subscribers
import dev.musicx.shared.generated.resources.unblock
import dev.musicx.shared.generated.resources.unlike
import dev.musicx.shared.generated.resources.unpin_from_speed_dial
import dev.musicx.shared.generated.resources.unsubscribe
import dev.musicx.shared.generated.resources.view_artist
import dev.musicx.shared.model.AlbumItem
import dev.musicx.shared.model.ArtistItem
import dev.musicx.shared.model.ArtistTiny
import dev.musicx.shared.model.PlaylistItem
import dev.musicx.shared.model.PodcastItem
import dev.musicx.shared.model.SongItem
import dev.musicx.shared.model.previewAspectRatio
import dev.musicx.shared.model.previewThumbnailUrl
import dev.musicx.shared.settings.ArtistNameAliases
import dev.musicx.shared.settings.HiddenHomeItems
import dev.musicx.shared.settings.displayName
import dev.musicx.shared.settings.displayTitle
import dev.musicx.shared.ui.components.AlertDialog
import dev.musicx.shared.ui.components.CollectionPreviews
import dev.musicx.shared.ui.components.Material3MenuGroup
import dev.musicx.shared.ui.components.Material3MenuItemData
import dev.musicx.shared.ui.components.PlayerMenuAction
import dev.musicx.shared.ui.components.PopupVisibility
import dev.musicx.shared.ui.icons.MuSicXIcons
import dev.musicx.shared.ui.utils.verticalScrollWithDrag
import dev.musicx.shared.utils.monthlyAudienceText
import org.jetbrains.compose.resources.stringResource

enum class MediaDownloadState {
    NOT_DOWNLOADED,
    DOWNLOADING,
    DOWNLOADED,
}

data class MediaMenuModel(
    val title: String,
    val subtitle: String?,
    val thumbnail: String?,
    val thumbnailAspectRatio: Float = 1f,
    val fallbackIcon: ImageVector? = null,
    val isExplicit: Boolean = false,
    val subtitleLinks: AnnotatedString? = null,
    val artistId: String? = null,
    val description: String? = null,
    val headerActions: List<PlayerMenuAction> = emptyList(),
    val primaryActions: List<PlayerMenuAction> = emptyList(),
    val actionGroups: List<List<PlayerMenuAction>> = emptyList(),
)

@Composable
fun artistNavigationMenuAction(
    songArtists: List<ArtistTiny>,
    onGoToArtist: ((String) -> Unit)?,
    onDismiss: () -> Unit,
): PlayerMenuAction? {
    val viewArtistLabel = stringResource(Res.string.view_artist)
    val artistsLabel = stringResource(Res.string.artists)
    val artists = songArtists.filter { !it.id.isNullOrBlank() }.distinctBy { it.id }
    var choosingArtist by remember(artists) { mutableStateOf(false) }
    PopupVisibility(visible = choosingArtist && onGoToArtist != null) {
        AlertDialog(
            onDismissRequest = { choosingArtist = false },
            title = { Text(artistsLabel) },
            text = {
                Column(Modifier.verticalScrollWithDrag(rememberScrollState())) {
                    Material3MenuGroup(
                        artists.map { artist ->
                            Material3MenuItemData(
                                title = { Text(artist.displayName) },
                                onClick = {
                                    choosingArtist = false
                                    CollectionPreviews.record(artist)
                                    onGoToArtist?.invoke(requireNotNull(artist.id))
                                    onDismiss()
                                },
                            )
                        },
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { choosingArtist = false },
                    shapes = ButtonDefaults.shapes(),
                ) { Text(stringResource(Res.string.cancel)) }
            },
        )
    }

    if (onGoToArtist == null || artists.isEmpty()) return null
    return PlayerMenuAction(
        MuSicXIcons.Outlined.Person,
        if (artists.size > 1) artistsLabel else viewArtistLabel,
        description = artists.singleOrNull()?.displayName,
        onClick = {
            if (artists.size == 1) {
                CollectionPreviews.record(artists.single())
                onGoToArtist(requireNotNull(artists.single().id))
                onDismiss()
            } else {
                choosingArtist = true
            }
        },
    )
}

@Composable
fun songMediaMenuModel(
    song: SongItem,
    onDismiss: () -> Unit,
    isLiked: Boolean = false,
    isDisliked: Boolean = false,
    isGuest: Boolean = false,
    isPinned: Boolean = false,
    downloadState: MediaDownloadState = MediaDownloadState.NOT_DOWNLOADED,
    downloadProgress: Int = 0,
    onPlay: (() -> Unit)? = null,
    onLike: (() -> Unit)? = null,
    onDislike: (() -> Unit)? = null,
    onStartRadio: (() -> Unit)? = null,
    onAddToPlaylist: (() -> Unit)? = null,
    onShare: (() -> Unit)? = null,
    onAltShare: (() -> Unit)? = null,
    onSuggestToHost: (() -> Unit)? = null,
    onPlayNext: (() -> Unit)? = null,
    onAddToQueue: (() -> Unit)? = null,
    onTogglePin: (() -> Unit)? = null,
    onDownload: (() -> Unit)? = null,
    onBlockArtist: (() -> Unit)? = null,
    onHideFromLibrary: (() -> Unit)? = null,
    onMoveUp: (() -> Unit)? = null,
    onMoveDown: (() -> Unit)? = null,
    onRemove: (() -> Unit)? = null,
    removeLabel: String? = null,
    onEdit: (() -> Unit)? = null,
    onGoToArtist: ((String) -> Unit)? = null,
    onGoToAlbum: (() -> Unit)? = null,
    additionalActions: List<PlayerMenuAction> = emptyList(),
    onShowDetails: (() -> Unit)? = null,
    isPlayerMenu: Boolean = false,
    leadingPrimaryActions: List<PlayerMenuAction> = emptyList(),
    trailingPrimaryActions: List<PlayerMenuAction> = emptyList(),
    deleteUploadAction: PlayerMenuAction? = null,
): MediaMenuModel {
    val unlikeLabel = stringResource(Res.string.unlike)
    val likeLabel = stringResource(Res.string.action_like)
    val playLabel = stringResource(Res.string.play)
    val moveUpLabel = stringResource(Res.string.move_up)
    val moveDownLabel = stringResource(Res.string.move_down)
    val removeFromPlaylistLabel = stringResource(Res.string.remove_from_playlist)
    val editLabel = stringResource(Res.string.edit)
    val editDesc = stringResource(Res.string.edit_song_description)
    val artistAction = artistNavigationMenuAction(song.artists, onGoToArtist, onDismiss)
    val download =
        downloadAction(downloadState, downloadProgress, false, onDismiss, onDownload)?.let {
            it.copy(enabled = it.enabled && (!song.isUnavailable || downloadState == MediaDownloadState.DOWNLOADED))
        }

    val headerActions =
        listOfNotNull(
            onLike?.let {
                PlayerMenuAction(
                    icon = if (isLiked) MuSicXIcons.Filled.Favorite else MuSicXIcons.Outlined.FavoriteBorder,
                    title = if (isLiked) unlikeLabel else likeLabel,
                    selected = isLiked,
                    onClick = it,
                )
            },
            onPlay?.let {
                action(
                    MuSicXIcons.Rounded.PlayArrow,
                    playLabel,
                    onDismiss,
                    it,
                    prominent = true,
                ).copy(enabled = !song.isUnavailable)
            },
        )

    val primaryActions =
        leadingPrimaryActions +
            songPrimaryActions(
                playbackEnabled = !song.isUnavailable,
                onDismiss = onDismiss,
                isGuest = isGuest,
                onStartRadio = onStartRadio,
                onAddToPlaylist = onAddToPlaylist,
                onShare = onShare,
                onAltShare = onAltShare,
            ) + trailingPrimaryActions

    val detailsAction =
        onShowDetails?.let {
            PlayerMenuAction(
                MuSicXIcons.Outlined.Info,
                stringResource(if (isPlayerMenu) Res.string.song_details else Res.string.details),
                onClick = it,
            )
        }
    val actionGroups =
        songMenuActionGroups(
            isPlayerMenu = isPlayerMenu,
            queue =
                if (isGuest) {
                    listOfNotNull(
                        onSuggestToHost?.let {
                            suggestToHostMenuAction(onDismiss, it)
                        },
                    )
                } else {
                    queueActions(onDismiss, onPlayNext, onAddToQueue)
                }.map { it.copy(enabled = it.enabled && !song.isUnavailable) },
            removal =
                listOfNotNull(
                    onMoveUp?.let {
                        action(MuSicXIcons.Default.ChevronLeft, moveUpLabel, onDismiss, it)
                    },
                    onMoveDown?.let {
                        action(MuSicXIcons.Default.ChevronRight, moveDownLabel, onDismiss, it)
                    },
                    onRemove?.let {
                        action(
                            MuSicXIcons.Outlined.Delete,
                            removeLabel ?: removeFromPlaylistLabel,
                            onDismiss,
                            it,
                        )
                    },
                ),
            library =
                listOfNotNull(
                    download.takeIf { isPlayerMenu },
                    pinAction(isPinned, onDismiss, onTogglePin),
                    hideAction(onDismiss, onHideFromLibrary),
                    dislikeMenuAction(isDisliked, onDismiss, onDislike),
                    blockArtistMenuAction(onDismiss, onBlockArtist),
                    download.takeUnless { isPlayerMenu },
                    deleteUploadAction,
                    onEdit?.let {
                        PlayerMenuAction(
                            MuSicXIcons.Outlined.Edit,
                            editLabel,
                            editDesc,
                            onClick = it,
                        )
                    },
                ),
            navigation =
                buildList {
                    artistAction?.let(::add)
                    albumNavigationMenuAction(onDismiss, onGoToAlbum, isPodcast = song.podcastId != null)?.let(::add)
                    if (isPlayerMenu) detailsAction?.let(::add)
                },
            additional = listOfNotNull(detailsAction.takeUnless { isPlayerMenu }) + additionalActions,
        )

    return MediaMenuModel(
        title = song.title,
        subtitle = song.artists.joinToString(", ") { it.displayName },
        subtitleLinks =
            artistLinksText(
                song.artists,
                onGoToArtist?.let { go ->
                    { id ->
                        go(id)
                        onDismiss()
                    }
                },
            ),
        thumbnail = song.previewThumbnailUrl,
        thumbnailAspectRatio = song.previewAspectRatio,
        isExplicit = song.isExplicit,
        headerActions = headerActions,
        primaryActions = primaryActions,
        actionGroups = actionGroups,
    )
}

internal fun <T> songMenuActionGroups(
    queue: List<T>,
    removal: List<T>,
    library: List<T>,
    navigation: List<T>,
    additional: List<T>,
    isPlayerMenu: Boolean,
): List<List<T>> =
    if (isPlayerMenu) listOf(queue, removal, navigation, library, additional) else listOf(queue, removal, library, navigation, additional)

@Composable
fun albumMediaMenuModel(
    album: AlbumItem,
    onDismiss: () -> Unit,
    isFavorite: Boolean = false,
    onFavorite: (() -> Unit)? = null,
    isPinned: Boolean = false,
    downloadState: MediaDownloadState = MediaDownloadState.NOT_DOWNLOADED,
    downloadProgress: Int = 0,
    onPlay: (() -> Unit)? = null,
    onShuffle: (() -> Unit)? = null,
    onShare: (() -> Unit)? = null,
    onPlayNext: (() -> Unit)? = null,
    onAddToQueue: (() -> Unit)? = null,
    onAddToPlaylist: (() -> Unit)? = null,
    onTogglePin: (() -> Unit)? = null,
    onDownload: (() -> Unit)? = null,
    onBlockArtist: (() -> Unit)? = null,
    onHideFromLibrary: (() -> Unit)? = null,
    onExport: (() -> Unit)? = null,
    onGoToArtist: ((String) -> Unit)? = null,
    additionalActions: List<PlayerMenuAction> = emptyList(),
): MediaMenuModel {
    val saveToAccount = rememberAlbumSaveToAccountAction(album.id)
    val addToPlaylistLabel = stringResource(Res.string.add_to_playlist)
    val exportAlbumLabel = stringResource(Res.string.export_album)
    val viewArtistLabel = stringResource(Res.string.view_artist)

    return collectionMediaMenuModel(
        title = album.title,
        subtitle = album.artists.joinToString(", ") { it.displayName },
        subtitleLinks =
            artistLinksText(
                album.artists,
                onGoToArtist?.let { go ->
                    { id ->
                        go(id)
                        onDismiss()
                    }
                },
            ),
        thumbnail = album.thumbnail,
        onDismiss = onDismiss,
        isPinned = isPinned,
        downloadState = downloadState,
        downloadProgress = downloadProgress,
        onPlay = onPlay,
        onShuffle = onShuffle,
        onShare = onShare,
        onPlayNext = onPlayNext,
        onAddToQueue = onAddToQueue,
        onTogglePin = onTogglePin,
        onDownload = onDownload,
        onHideFromLibrary = onHideFromLibrary,
        downloadIsPodcast = false,
        extraLibraryAction = collectionLibraryAction(isFavorite, onFavorite, onDismiss),
        trailingGroups =
            listOf(
                listOfNotNull(
                    onAddToPlaylist?.let {
                        action(
                            MuSicXIcons.AutoMirrored.Outlined.PlaylistAdd,
                            addToPlaylistLabel,
                            onDismiss,
                            it,
                        )
                    },
                ),
                listOfNotNull(
                    onExport?.let {
                        action(MuSicXIcons.Outlined.Share, exportAlbumLabel, onDismiss, it)
                    },
                ),
                if (onGoToArtist == null) {
                    emptyList()
                } else {
                    album.artists.mapNotNull { artist ->
                        artist.id?.let { artistId ->
                            action(
                                MuSicXIcons.Outlined.Person,
                                viewArtistLabel,
                                onDismiss,
                                description = artist.displayName,
                                onClick = {
                                    CollectionPreviews.record(artist)
                                    onGoToArtist(artistId)
                                },
                            )
                        }
                    }
                },
                listOfNotNull(saveToAccount) + additionalActions,
            ),
    )
}

@Composable
fun artistMediaMenuModel(
    artist: ArtistItem,
    onDismiss: () -> Unit,
    isPinned: Boolean = false,
    isBlocked: Boolean = false,
    isSubscribed: Boolean? = null,
    onPlay: (() -> Unit)? = null,
    onShuffle: (() -> Unit)? = null,
    onShare: (() -> Unit)? = null,
    onPlayNext: (() -> Unit)? = null,
    onAddToQueue: (() -> Unit)? = null,
    onTogglePin: (() -> Unit)? = null,
    onBlock: (() -> Unit)? = null,
    onToggleSubscription: (() -> Unit)? = null,
    onHideFromLibrary: (() -> Unit)? = null,
    additionalActions: List<PlayerMenuAction> = emptyList(),
): MediaMenuModel {
    val unblockLabel = stringResource(Res.string.unblock)
    val blockArtistLabel = stringResource(Res.string.block_artist)
    var editingAlias by remember(artist.id) { mutableStateOf(false) }
    PopupVisibility(visible = editingAlias) {
        ArtistNameAliasDialog(artist, onDismiss = { editingAlias = false })
    }
    val editArtistLabel = stringResource(Res.string.edit_artist)
    val unsubscribeLabel = stringResource(Res.string.unsubscribe)
    val subscribeLabel = stringResource(Res.string.subscribe)

    return MediaMenuModel(
        title = artist.displayTitle,
        subtitle =
            monthlyAudienceText(artist.monthlyListeners, stringResource(Res.string.monthly_audience_short))
                ?: artist.subscribers?.takeIf(String::isNotBlank)?.let { "${stringResource(Res.string.subscribers)}: $it" },
        thumbnail = artist.thumbnail,
        fallbackIcon = MuSicXIcons.Outlined.AccountCircle,
        artistId = artist.id,
        description = artist.description,
        primaryActions = primaryActions(onDismiss, onPlay, onShuffle, onShare),
        actionGroups =
            listOf(
                queueActions(onDismiss, onPlayNext, onAddToQueue),
                listOfNotNull(
                    pinAction(isPinned, onDismiss, onTogglePin),
                    onBlock?.let {
                        action(
                            if (isBlocked) MuSicXIcons.Filled.Remove else MuSicXIcons.Outlined.Block,
                            if (isBlocked) unblockLabel else blockArtistLabel,
                            onDismiss,
                            it,
                        )
                    },
                    PlayerMenuAction(MuSicXIcons.Outlined.Edit, editArtistLabel, onClick = { editingAlias = true }),
                    onToggleSubscription?.let {
                        action(
                            if (isSubscribed == true) MuSicXIcons.Outlined.NotificationsNone else MuSicXIcons.Outlined.AddAlert,
                            if (isSubscribed == true) unsubscribeLabel else subscribeLabel,
                            onDismiss,
                            it,
                        )
                    },
                    hideAction(onDismiss, { HiddenHomeItems.hide(artist) }, stringResource(Res.string.hide_from_home)),
                    hideAction(onDismiss, onHideFromLibrary),
                ),
                additionalActions,
            ),
    )
}

@Composable
fun playlistMediaMenuModel(
    playlist: PlaylistItem,
    onDismiss: () -> Unit,
    isFavorite: Boolean = false,
    onFavorite: (() -> Unit)? = null,
    fallbackIcon: ImageVector? = null,
    isPinned: Boolean = false,
    showPinAction: Boolean = true,
    downloadState: MediaDownloadState = MediaDownloadState.NOT_DOWNLOADED,
    downloadProgress: Int = 0,
    onPlay: (() -> Unit)? = null,
    onShuffle: (() -> Unit)? = null,
    onShare: (() -> Unit)? = null,
    onPlayNext: (() -> Unit)? = null,
    onAddToQueue: (() -> Unit)? = null,
    onTogglePin: (() -> Unit)? = null,
    onDownload: (() -> Unit)? = null,
    onHideFromLibrary: (() -> Unit)? = null,
    hideFromLibraryLabel: String? = null,
    onExport: (() -> Unit)? = null,
    onSelect: (() -> Unit)? = null,
    onRename: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    additionalActions: List<PlayerMenuAction> = emptyList(),
): MediaMenuModel {
    val exportPlaylistLabel = stringResource(Res.string.export_playlist)
    val selectSongsLabel = stringResource(Res.string.select_songs)
    val renameLabel = stringResource(Res.string.rename)
    val renameDesc = stringResource(Res.string.rename_playlist_description)
    val deletePlaylistLabel = stringResource(Res.string.delete_playlist)
    val removeFromLibraryLabel = stringResource(Res.string.remove_from_library)

    return collectionMediaMenuModel(
        title = playlist.title,
        subtitle = playlist.author?.let { ArtistNameAliases.resolve(null, it) },
        thumbnail = playlist.thumbnail,
        fallbackIcon = fallbackIcon,
        onDismiss = onDismiss,
        isPinned = isPinned,
        downloadState = downloadState,
        downloadProgress = downloadProgress,
        onPlay = onPlay,
        onShuffle = onShuffle,
        onShare = onShare,
        onPlayNext = onPlayNext,
        onAddToQueue = onAddToQueue,
        onTogglePin = onTogglePin.takeIf { showPinAction },
        onDownload = onDownload,
        onHideFromLibrary = onHideFromLibrary,
        hideFromLibraryLabel = hideFromLibraryLabel ?: stringResource(Res.string.hide_from_library_local),
        removeDownloadLabel =
            if (playlist.id == "auto:downloaded") stringResource(Res.string.remove_all_downloads) else null,
        downloadIsPodcast = false,
        extraLibraryAction = collectionLibraryAction(isFavorite, onFavorite, onDismiss),
        trailingGroups =
            listOf(
                listOfNotNull(
                    onExport?.let {
                        action(MuSicXIcons.Outlined.Share, exportPlaylistLabel, onDismiss, it)
                    },
                ),
                listOfNotNull(
                    onSelect?.let {
                        action(MuSicXIcons.Outlined.LibraryMusic, selectSongsLabel, onDismiss, it)
                    },
                    onRename?.let {
                        action(
                            MuSicXIcons.Outlined.Edit,
                            renameLabel,
                            onDismiss,
                            it,
                            renameDesc,
                        )
                    },
                    onDelete?.takeIf { onFavorite == null || playlist.isLocal || playlist.isEditable }?.let {
                        action(
                            MuSicXIcons.Outlined.Delete,
                            if (playlist.isLocal || playlist.isEditable) {
                                deletePlaylistLabel
                            } else {
                                removeFromLibraryLabel
                            },
                            onDismiss,
                            it,
                        )
                    },
                ),
                additionalActions,
            ),
    )
}

@Composable
fun podcastMediaMenuModel(
    podcast: PodcastItem,
    onDismiss: () -> Unit,
    isPinned: Boolean = false,
    isSaved: Boolean? = null,
    downloadState: MediaDownloadState = MediaDownloadState.NOT_DOWNLOADED,
    downloadProgress: Int = 0,
    onPlay: (() -> Unit)? = null,
    onShuffle: (() -> Unit)? = null,
    onPlayOldestFirst: (() -> Unit)? = null,
    onShare: (() -> Unit)? = null,
    onPlayNext: (() -> Unit)? = null,
    onAddToQueue: (() -> Unit)? = null,
    onTogglePin: (() -> Unit)? = null,
    onToggleSaved: (() -> Unit)? = null,
    onDownload: (() -> Unit)? = null,
    onRemoveFromLibrary: (() -> Unit)? = null,
    additionalActions: List<PlayerMenuAction> = emptyList(),
): MediaMenuModel {
    val removeFromSavedPodcastsLabel = stringResource(Res.string.remove_from_saved_podcasts)
    val savePodcastLabel = stringResource(Res.string.save_podcast)
    val playOldestFirstLabel = stringResource(Res.string.play_oldest_first)

    return collectionMediaMenuModel(
        title = podcast.title,
        subtitle = podcast.author?.let { ArtistNameAliases.resolve(podcast.channelId, it) },
        thumbnail = podcast.thumbnail,
        onDismiss = onDismiss,
        isPinned = isPinned,
        downloadState = downloadState,
        downloadProgress = downloadProgress,
        onPlay = onPlay,
        onShuffle = onShuffle,
        onShare = onShare,
        onPlayNext = onPlayNext,
        onAddToQueue = onAddToQueue,
        onTogglePin = onTogglePin,
        onDownload = onDownload,
        onHideFromLibrary = onRemoveFromLibrary,
        downloadIsPodcast = true,
        extraPrimaryAction = onPlayOldestFirst?.let { action(MuSicXIcons.Outlined.History, playOldestFirstLabel, onDismiss, it) },
        extraLibraryAction =
            onToggleSaved?.let {
                action(
                    MuSicXIcons.Outlined.LibraryAdd,
                    if (isSaved == true) removeFromSavedPodcastsLabel else savePodcastLabel,
                    onDismiss,
                    it,
                )
            },
        trailingGroups =
            listOf(
                listOfNotNull(hideAction(onDismiss, { HiddenHomeItems.hide(podcast) }, stringResource(Res.string.hide_from_home))),
                additionalActions,
            ),
    )
}

@Composable
private fun collectionMediaMenuModel(
    title: String,
    subtitle: String?,
    thumbnail: String?,
    fallbackIcon: ImageVector? = null,
    subtitleLinks: AnnotatedString? = null,
    onDismiss: () -> Unit,
    isPinned: Boolean,
    downloadState: MediaDownloadState,
    downloadProgress: Int,
    onPlay: (() -> Unit)?,
    onShuffle: (() -> Unit)?,
    onShare: (() -> Unit)?,
    onPlayNext: (() -> Unit)?,
    onAddToQueue: (() -> Unit)?,
    onTogglePin: (() -> Unit)?,
    onDownload: (() -> Unit)?,
    onHideFromLibrary: (() -> Unit)?,
    hideFromLibraryLabel: String? = null,
    downloadIsPodcast: Boolean,
    removeDownloadLabel: String? = null,
    extraPrimaryAction: PlayerMenuAction? = null,
    extraLibraryAction: PlayerMenuAction? = null,
    trailingGroups: List<List<PlayerMenuAction>>,
): MediaMenuModel {
    val playLabel = stringResource(Res.string.play)
    val shuffleLabel = stringResource(Res.string.shuffle)
    val shareLabel = stringResource(Res.string.share)

    return MediaMenuModel(
        title = title,
        subtitle = subtitle,
        subtitleLinks = subtitleLinks,
        thumbnail = thumbnail,
        fallbackIcon = fallbackIcon,
        headerActions =
            listOfNotNull(
                onPlay?.let {
                    action(
                        MuSicXIcons.Filled.PlayArrow,
                        playLabel,
                        onDismiss,
                        it,
                        prominent = true,
                    )
                },
            ),
        primaryActions =
            listOfNotNull(
                onShuffle?.let { action(MuSicXIcons.Rounded.Shuffle, shuffleLabel, onDismiss, it) },
                extraPrimaryAction,
                onShare?.let { action(MuSicXIcons.Outlined.Share, shareLabel, onDismiss, it) },
            ),
        actionGroups =
            listOf(
                queueActions(onDismiss, onPlayNext, onAddToQueue),
                listOfNotNull(
                    pinAction(isPinned, onDismiss, onTogglePin),
                    extraLibraryAction,
                    hideAction(onDismiss, onHideFromLibrary, hideFromLibraryLabel),
                    downloadAction(downloadState, downloadProgress, downloadIsPodcast, onDismiss, onDownload, removeDownloadLabel),
                ),
            ) + trailingGroups,
    )
}

@Composable
private fun primaryActions(
    onDismiss: () -> Unit,
    onPlay: (() -> Unit)?,
    onShuffle: (() -> Unit)?,
    onShare: (() -> Unit)?,
    onAltShare: (() -> Unit)? = null,
): List<PlayerMenuAction> {
    val playLabel = stringResource(Res.string.play)
    val shuffleLabel = stringResource(Res.string.shuffle)
    val shareLabel = stringResource(Res.string.share)
    return listOfNotNull(
        onPlay?.let { action(MuSicXIcons.Rounded.PlayArrow, playLabel, onDismiss, it) },
        onShuffle?.let { action(MuSicXIcons.Rounded.Shuffle, shuffleLabel, onDismiss, it) },
        onShare?.let {
            action(
                icon = MuSicXIcons.Outlined.Share,
                title = shareLabel,
                onDismiss = onDismiss,
                onClick = it,
                onLongClick = onAltShare,
            )
        },
    )
}

@Composable
internal fun queueActions(
    onDismiss: () -> Unit,
    onPlayNext: (() -> Unit)?,
    onAddToQueue: (() -> Unit)?,
): List<PlayerMenuAction> {
    val playNextLabel = stringResource(Res.string.play_next)
    val playNextDesc = stringResource(Res.string.play_next_desc)
    val addToQueueLabel = stringResource(Res.string.add_to_queue)
    val addToQueueDesc = stringResource(Res.string.add_to_queue_desc)

    return listOfNotNull(
        onPlayNext?.let {
            action(
                MuSicXIcons.AutoMirrored.Filled.PlaylistPlay,
                playNextLabel,
                onDismiss,
                it,
                playNextDesc,
            )
        },
        onAddToQueue?.let {
            action(
                MuSicXIcons.AutoMirrored.Outlined.QueueMusic,
                addToQueueLabel,
                onDismiss,
                it,
                addToQueueDesc,
            )
        },
    )
}

@Composable
internal fun pinAction(
    isPinned: Boolean,
    onDismiss: () -> Unit,
    onTogglePin: (() -> Unit)?,
): PlayerMenuAction? {
    val unpinLabel = stringResource(Res.string.unpin_from_speed_dial)
    val pinLabel = stringResource(Res.string.pin_to_speed_dial)
    return onTogglePin?.let {
        action(
            if (isPinned) MuSicXIcons.Outlined.KeepOff else MuSicXIcons.Filled.PushPin,
            if (isPinned) unpinLabel else pinLabel,
            onDismiss,
            it,
        )
    }
}

@Composable
private fun hideAction(
    onDismiss: () -> Unit,
    onHide: (() -> Unit)?,
    label: String? = null,
): PlayerMenuAction? {
    val hideLabel = stringResource(Res.string.hide_from_library)
    return onHide?.let {
        action(MuSicXIcons.Filled.VisibilityOff, label ?: hideLabel, onDismiss, it)
    }
}

@Composable
internal fun downloadAction(
    state: MediaDownloadState,
    progress: Int,
    isPodcast: Boolean,
    onDismiss: () -> Unit,
    onDownload: (() -> Unit)?,
    removeDownloadLabel: String? = null,
): PlayerMenuAction? {
    val downloadActionLabel = stringResource(Res.string.action_download)
    val downloadDescLabel = stringResource(Res.string.download_desc)
    val downloadingPercentLabel =
        stringResource(
            Res.string.downloading_percent,
            progress.coerceIn(0, 100),
            stringResource(Res.string.percent_symbol),
        )
    val removeLabel = removeDownloadLabel ?: stringResource(Res.string.remove_download)
    val deleteEpisodesLabel = stringResource(Res.string.delete_downloaded_episodes)

    return onDownload?.let {
        when (state) {
            MediaDownloadState.NOT_DOWNLOADED -> {
                action(
                    MuSicXIcons.Outlined.Download,
                    downloadActionLabel,
                    onDismiss,
                    it,
                    downloadDescLabel,
                )
            }

            MediaDownloadState.DOWNLOADING -> {
                PlayerMenuAction(
                    MuSicXIcons.Outlined.Download,
                    downloadingPercentLabel,
                    enabled = false,
                ) {}
            }

            MediaDownloadState.DOWNLOADED -> {
                action(
                    MuSicXIcons.Outlined.OfflinePin,
                    removeLabel,
                    onDismiss,
                    it,
                    if (isPodcast) deleteEpisodesLabel else null,
                )
            }
        }
    }
}

internal fun action(
    icon: ImageVector,
    title: String,
    onDismiss: () -> Unit,
    onClick: () -> Unit,
    description: String? = null,
    selected: Boolean? = null,
    prominent: Boolean = false,
    onLongClick: (() -> Unit)? = null,
) = PlayerMenuAction(
    icon = icon,
    title = title,
    description = description,
    selected = selected,
    prominent = prominent,
    onClick = {
        onClick()
        onDismiss()
    },
    onLongClick =
        onLongClick?.let {
            {
                it()
                onDismiss()
            }
        },
)

@Composable
private fun collectionLibraryAction(
    isFavorite: Boolean,
    onFavorite: (() -> Unit)?,
    onDismiss: () -> Unit,
): PlayerMenuAction? {
    val label = stringResource(if (isFavorite) Res.string.remove_from_library else Res.string.action_like)
    return onFavorite?.let {
        action(
            if (isFavorite) MuSicXIcons.Filled.Favorite else MuSicXIcons.Outlined.FavoriteBorder,
            label,
            onDismiss,
            it,
        )
    }
}
