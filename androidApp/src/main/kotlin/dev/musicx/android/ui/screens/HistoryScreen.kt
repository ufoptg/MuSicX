package dev.musicx.android.ui.screens

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.musicx.android.LocalMiniPlayerBottomInset
import dev.musicx.android.playback.LocalPlayerConnection
import dev.musicx.android.ui.menu.SongMenu
import dev.musicx.android.viewmodels.HistoryError
import dev.musicx.android.viewmodels.HistoryViewModel
import dev.musicx.shared.db.MusicDatabase
import dev.musicx.shared.generated.resources.Res
import dev.musicx.shared.generated.resources.delete
import dev.musicx.shared.generated.resources.delete_failed
import dev.musicx.shared.generated.resources.edit
import dev.musicx.shared.generated.resources.error_unknown
import dev.musicx.shared.model.BuiltinPlaylist
import dev.musicx.shared.model.HistoryEntryUi
import dev.musicx.shared.model.toSongItem
import dev.musicx.shared.settings.Settings
import dev.musicx.shared.state.SongLikeState
import dev.musicx.shared.ui.components.PlayerMenuAction
import dev.musicx.shared.ui.components.PopupContent
import dev.musicx.shared.ui.icons.MuSicXIcons
import dev.musicx.shared.ui.menu.BuiltinPlaylistMenuButton
import dev.musicx.shared.ui.screens.DeleteHistoryDialog
import dev.musicx.shared.ui.screens.EditHistoryDialog
import dev.musicx.shared.ui.screens.HistoryContent
import dev.musicx.shared.utils.formatHistoryMonth
import org.jetbrains.compose.resources.stringResource
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

@Composable
fun HistoryScreen(
    onNavigateBack: () -> Unit,
    viewModel: HistoryViewModel = koinViewModel(),
) {
    LaunchedEffect(
        Settings.shared.innerTubeCookie,
        Settings.shared.dataSyncId,
        Settings.shared.innerTubeAuthUser,
        Settings.shared.ytmSync,
        Settings.shared.pauseListenHistory,
    ) { viewModel.loadHistory() }
    val uiState by viewModel.uiState.collectAsState()
    val playerConnection = LocalPlayerConnection.current ?: return
    var menuEntry by remember { mutableStateOf<HistoryEntryUi?>(null) }
    var editEntry by remember { mutableStateOf<HistoryEntryUi?>(null) }
    var deleteEntry by remember { mutableStateOf<HistoryEntryUi?>(null) }
    val entries = uiState.uiEntries
    val displayedEntries = uiState.uiDisplayedEntries

    HistoryContent(
        headerActions = { BuiltinPlaylistMenuButton(BuiltinPlaylist.HISTORY, koinInject<MusicDatabase>()) },
        entries = entries,
        onLoadMore = if (uiState.hasMore) viewModel::loadMore else null,
        displayedEntries = displayedEntries,
        query = uiState.query,
        sortMode = uiState.sortMode,
        sortDirection = uiState.sortDirection,
        onSortDirectionChange = viewModel::setSortDirection,
        selectedIds = uiState.selectedIds.mapTo(mutableSetOf(), Long::toString),
        isLoading = uiState.isLoading,
        isDeleting = uiState.isDeleting,
        error =
            when (uiState.error) {
                HistoryError.DELETE -> stringResource(Res.string.delete_failed)
                HistoryError.LOAD -> stringResource(Res.string.error_unknown)
                null -> null
            },
        canDeleteRemote = uiState.canDeleteRemote,
        onQueryChange = viewModel::setQuery,
        onSortChange = viewModel::setSortMode,
        onRefresh = viewModel::retry,
        onPlay = { playerConnection.playSong(it.toSongItem(), url = "") },
        onSongMenu = { menuEntry = it },
        onToggleSelection = { viewModel.toggleSelection(it.toLong()) },
        onSelectAll = { viewModel.selectAll(it.mapTo(mutableSetOf(), String::toLong)) },
        onUpdate = { entry, title, artists, album ->
            viewModel.updateEntry(entry.id.toLong(), title, artists, album)
        },
        onDelete = { entry, alsoRemote ->
            viewModel.deleteEntry(entry.id.toLong(), deleteRemote = alsoRemote)
        },
        onDeleteSelected = { ids, alsoRemote ->
            viewModel.deleteEntries(ids.mapTo(mutableSetOf(), String::toLong), deleteRemote = alsoRemote)
        },
        formatMonth = { formatHistoryMonth(it) },
        onNavigateBack = onNavigateBack,
        showInlineEntryMenu = false,
        contentPadding = PaddingValues(bottom = LocalMiniPlayerBottomInset.current),
    )
    menuEntry?.let { entry ->
        val song = entry.toSongItem()
        SongMenu(
            song = song,
            isLiked = SongLikeState.isLiked(song.id),
            onDismiss = { menuEntry = null },
            onPlayNext = { playerConnection.addToQueue(song, playNext = true) },
            onAddToQueue = { playerConnection.addToQueue(song) },
            onLike = { SongLikeState.toggleLike(song) },
            showEditAction = false,
            leadingPrimaryActions =
                listOf(
                    PlayerMenuAction(MuSicXIcons.Outlined.Edit, stringResource(Res.string.edit)) {
                        menuEntry = null
                        editEntry = entry
                    },
                ),
            trailingPrimaryActions =
                listOf(
                    PlayerMenuAction(MuSicXIcons.Outlined.Delete, stringResource(Res.string.delete)) {
                        menuEntry = null
                        deleteEntry = entry
                    },
                ),
        )
    }
    PopupContent(editEntry) { entry ->
        EditHistoryDialog(
            entry = entry,
            onDismiss = { editEntry = null },
            onSave = { title, artists, album ->
                viewModel.updateEntry(entry.id.toLong(), title, artists, album)
                editEntry = null
            },
        )
    }
    PopupContent(deleteEntry) { entry ->
        DeleteHistoryDialog(
            entry = entry,
            canDeleteRemote = uiState.canDeleteRemote && !entry.remoteFeedbackToken.isNullOrBlank(),
            onDismiss = { deleteEntry = null },
            onDelete = { alsoRemote ->
                viewModel.deleteEntry(entry.id.toLong(), deleteRemote = alsoRemote)
                deleteEntry = null
            },
        )
    }
}
