package dev.musicx.android.ui.menu

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.SheetValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import dev.musicx.android.LocalListenTogetherManager
import dev.musicx.android.LocalPlaylistAdder
import dev.musicx.android.listentogether.AndroidListenTogetherManager
import dev.musicx.android.playback.PlayerConnection
import dev.musicx.android.utils.startActivitySafely
import dev.musicx.shared.db.MusicDatabase
import dev.musicx.shared.generated.resources.Res
import dev.musicx.shared.generated.resources.blocked_artists_count
import dev.musicx.shared.generated.resources.disliked_song
import dev.musicx.shared.generated.resources.refetch
import dev.musicx.shared.generated.resources.refetch_desc
import dev.musicx.shared.generated.resources.removed_dislike
import dev.musicx.shared.generated.resources.share_song
import dev.musicx.shared.generated.resources.song_updated
import dev.musicx.shared.generated.resources.suggested_to_host
import dev.musicx.shared.listentogether.ListenTogetherRoomRole
import dev.musicx.shared.model.SongItem
import dev.musicx.shared.model.editSongMetadata
import dev.musicx.shared.model.resetSongMetadata
import dev.musicx.shared.settings.BlockedContent
import dev.musicx.shared.state.SongLikeState
import dev.musicx.shared.state.SongMetadataState
import dev.musicx.shared.ui.LocalIsTv
import dev.musicx.shared.ui.components.PlayerMenuAction
import dev.musicx.shared.ui.components.rememberModalSheetState
import dev.musicx.shared.ui.icons.MuSicXIcons
import dev.musicx.shared.ui.menu.EditSongDialog
import dev.musicx.shared.ui.menu.LocalContextMenuAnchor
import dev.musicx.shared.ui.menu.MediaDownloadState
import dev.musicx.shared.ui.menu.MediaMenuSheetContent
import dev.musicx.shared.ui.menu.rememberDeleteUploadAction
import dev.musicx.shared.ui.menu.songMediaMenuModel
import dev.musicx.shared.utils.ShareUrls
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SongMenu(
    song: SongItem,
    isLiked: Boolean,
    onDismiss: () -> Unit,
    onPlay: (() -> Unit)? = null,
    onStartRadio: (() -> Unit)? = null,
    onPlayNext: (() -> Unit)? = null,
    onAddToQueue: (() -> Unit)? = null,
    onAddToPlaylist: (() -> Unit)? = null,
    onLike: (() -> Unit)? = null,
    onShare: (() -> Unit)? = null,
    onAltShare: (() -> Unit)? = null,
    onDownload: (() -> Unit)? = null,
    onGoToAlbum: (() -> Unit)? = null,
    onGoToArtist: ((String) -> Unit)? = null,
    onRemove: (() -> Unit)? = null,
    removeLabel: String? = null,
    onRefetch: (() -> Unit)? = null,
    onShowInfo: (() -> Unit)? = null,
    leadingPrimaryActions: List<PlayerMenuAction> = emptyList(),
    trailingPrimaryActions: List<PlayerMenuAction> = emptyList(),
    showEditAction: Boolean = true,
    onHideFromLibrary: (() -> Unit)? = null,
) {
    val suggestedToHostText = stringResource(Res.string.suggested_to_host)

    val blockedArtistText = pluralStringResource(Res.plurals.blocked_artists_count, 1, 1)
    val dislikedText = stringResource(Res.string.disliked_song)
    val removedDislikeText = stringResource(Res.string.removed_dislike)
    val context = LocalContext.current
    val shareSongText = stringResource(Res.string.share_song)
    val database: MusicDatabase = koinInject()
    val playerConnection: PlayerConnection = koinInject()
    val downloadAction = rememberMediaDownloadAction(song, onDismiss, onDownload)
    val speedDialToggle = rememberSpeedDialToggle(song)
    val addToPlaylist = LocalPlaylistAdder.current
    val listenTogetherManager = LocalListenTogetherManager.current
    val listenTogetherRole by listenTogetherManager?.role?.collectAsState()
        ?: remember { mutableStateOf(ListenTogetherRoomRole.NONE) }
    val isInListenTogetherRoom by listenTogetherManager?.isInRoom?.collectAsState()
        ?: remember { mutableStateOf(false) }
    val isGuest = isInListenTogetherRoom && listenTogetherRole == ListenTogetherRoomRole.GUEST
    var showEditDialog by remember { mutableStateOf(false) }
    var showDetails by remember(song.id) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val menuSheetState = rememberModalSheetState(confirmValueChange = { it != SheetValue.Hidden || !downloadAction.isConfirming() })
    val isTv = LocalIsTv.current
    val isMouseInput = LocalContextMenuAnchor.current.isMouseInput
    val showSongDetails: () -> Unit = {
        if (onShowInfo != null) {
            onShowInfo()
            onDismiss()
        } else {
            scope.launch {
                if (isTv || isMouseInput || menuSheetState.hide()) showDetails = true
            }
        }
    }

    val model =
        songMediaMenuModel(
            song = song,
            deleteUploadAction = rememberDeleteUploadAction(song, database, onDismiss),
            onDismiss = { if (!downloadAction.isConfirming()) onDismiss() },
            isLiked = isLiked,
            isDisliked = SongLikeState.isDisliked(song.id),
            isGuest = isGuest,
            isPinned = speedDialToggle.isPinned,
            downloadState =
                if (downloadAction.isDownloaded) {
                    MediaDownloadState.DOWNLOADED
                } else {
                    MediaDownloadState.NOT_DOWNLOADED
                },
            onPlay = onPlay,
            onLike = onLike,
            onDislike = {
                playerConnection.toggleDislike(song)
                val isDisliked = SongLikeState.isDisliked(song.id)
                Toast
                    .makeText(
                        context,
                        if (isDisliked) dislikedText else removedDislikeText,
                        Toast.LENGTH_SHORT,
                    ).show()
            },
            onStartRadio = onStartRadio,
            onAddToPlaylist = {
                if (onAddToPlaylist != null) {
                    onAddToPlaylist()
                } else {
                    addToPlaylist(listOf(song))
                    onDismiss()
                }
            },
            onShare =
                onShare ?: {
                    val intent =
                        Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, ShareUrls.song(song.id))
                        }
                    val chooser = Intent.createChooser(intent, shareSongText).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                    context.startActivitySafely(chooser)
                    Unit
                },
            onAltShare =
                onAltShare ?: {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                    clipboard?.setPrimaryClip(ClipData.newPlainText("MuSicX", ShareUrls.song(song.id)))
                    Unit
                },
            onSuggestToHost = {
                AndroidListenTogetherManager.suggestTrack(song)
                Toast.makeText(context, suggestedToHostText, Toast.LENGTH_SHORT).show()
            },
            onPlayNext = onPlayNext,
            onAddToQueue = onAddToQueue,
            onTogglePin = speedDialToggle.toggle,
            onDownload = downloadAction.invoke,
            onBlockArtist =
                song.artists.firstOrNull { it.id != null }?.let { artist ->
                    {
                        BlockedContent.blockArtist(artist)
                        Toast
                            .makeText(
                                context,
                                blockedArtistText,
                                Toast.LENGTH_SHORT,
                            ).show()
                    }
                },
            onHideFromLibrary = onHideFromLibrary,
            onRemove = onRemove,
            removeLabel = removeLabel,
            onShowDetails = showSongDetails,
            onEdit = if (showEditAction) ({ showEditDialog = true }) else null,
            onGoToArtist = onGoToArtist,
            onGoToAlbum = onGoToAlbum,
            leadingPrimaryActions = leadingPrimaryActions,
            trailingPrimaryActions = trailingPrimaryActions,
            additionalActions =
                listOfNotNull(
                    onRefetch?.let {
                        PlayerMenuAction(
                            MuSicXIcons.Outlined.Refresh,
                            stringResource(Res.string.refetch),
                            stringResource(Res.string.refetch_desc),
                        ) {
                            it()
                            onDismiss()
                        }
                    },
                ),
        )
    if (showDetails) {
        SongDetailsSheet(song = song, onDismiss = onDismiss)
    } else if (showEditDialog) {
        val effectiveSong = SongMetadataState.apply(song)
        EditSongDialog(
            title = effectiveSong.title,
            artist =
                effectiveSong.artists
                    .firstOrNull()
                    ?.name
                    .orEmpty(),
            onSave = { title, artist ->
                withContext(Dispatchers.IO) { editSongMetadata(database, song, title, artist) }
                Toast.makeText(context, getString(Res.string.song_updated), Toast.LENGTH_SHORT).show()
            },
            onSaved = {
                showEditDialog = false
                onDismiss()
            },
            onDismiss = { showEditDialog = false },
            onReset = { withContext(Dispatchers.IO) { resetSongMetadata(database, song.id) } },
        )
    }
    if (!showEditDialog) {
        MediaMenuBottomSheet(
            model = model,
            expanded = !showDetails,
            onDismissRequest = onDismiss,
            sheetState = menuSheetState,
            canDismiss = { !downloadAction.isConfirming() },
        ) {
            MediaMenuSheetContent(model)
        }
    }
}
