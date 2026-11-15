@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package dev.musicx.desktop.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import dev.musicx.desktop.backup.DesktopBackupRestoreManager
import dev.musicx.desktop.spotifyimport.DesktopAmazonMusicImportFlow
import dev.musicx.desktop.spotifyimport.DesktopAppleMusicImportFlow
import dev.musicx.desktop.spotifyimport.DesktopDeezerImportFlow
import dev.musicx.desktop.spotifyimport.DesktopSpotifyImportFlow
import dev.musicx.desktop.spotifyimport.DesktopTidalImportFlow
import dev.musicx.desktop.state.app.AppState
import dev.musicx.desktop.state.content.LibraryContentState
import dev.musicx.desktop.state.playback.validatedLocalMediaUrl
import dev.musicx.shared.backup.defaultBackupFilename
import dev.musicx.shared.backup.foreignImportMessage
import dev.musicx.shared.backup.importForeignBackup
import dev.musicx.shared.db.JdbcSQLiteDriver
import dev.musicx.shared.db.MusicDatabase
import dev.musicx.shared.db.entities.PlaylistEntity
import dev.musicx.shared.generated.resources.Res
import dev.musicx.shared.generated.resources.action_backup
import dev.musicx.shared.generated.resources.action_restore
import dev.musicx.shared.generated.resources.app_data
import dev.musicx.shared.generated.resources.backup_create_failed
import dev.musicx.shared.generated.resources.backup_create_success
import dev.musicx.shared.generated.resources.backup_created
import dev.musicx.shared.generated.resources.backup_failed
import dev.musicx.shared.generated.resources.backup_filename_prefix
import dev.musicx.shared.generated.resources.backup_picker_title
import dev.musicx.shared.generated.resources.backup_restore_busy
import dev.musicx.shared.generated.resources.backup_restore_export_desc
import dev.musicx.shared.generated.resources.backup_restore_import_desc
import dev.musicx.shared.generated.resources.backup_sensitive_message
import dev.musicx.shared.generated.resources.cancel
import dev.musicx.shared.generated.resources.close
import dev.musicx.shared.generated.resources.could_not_open_file_picker
import dev.musicx.shared.generated.resources.create_backup
import dev.musicx.shared.generated.resources.create_backup_countdown
import dev.musicx.shared.generated.resources.create_backup_desc
import dev.musicx.shared.generated.resources.create_backup_dialog_title
import dev.musicx.shared.generated.resources.foreign_import_desc
import dev.musicx.shared.generated.resources.foreign_import_title
import dev.musicx.shared.generated.resources.import_action_choose_file
import dev.musicx.shared.generated.resources.import_action_start
import dev.musicx.shared.generated.resources.import_cancel
import dev.musicx.shared.generated.resources.import_connect_amazon_music_account
import dev.musicx.shared.generated.resources.import_connect_apple_music_account
import dev.musicx.shared.generated.resources.import_connect_deezer_account
import dev.musicx.shared.generated.resources.import_connect_spotify_account
import dev.musicx.shared.generated.resources.import_connect_tidal_account
import dev.musicx.shared.generated.resources.import_destination_favorites
import dev.musicx.shared.generated.resources.import_duplicate_playlist_import_anyway
import dev.musicx.shared.generated.resources.import_duplicate_playlist_message
import dev.musicx.shared.generated.resources.import_duplicate_playlist_skip
import dev.musicx.shared.generated.resources.import_duplicate_playlist_title
import dev.musicx.shared.generated.resources.import_error_details_summary
import dev.musicx.shared.generated.resources.import_error_details_title
import dev.musicx.shared.generated.resources.import_error_empty_input
import dev.musicx.shared.generated.resources.import_error_empty_playlist
import dev.musicx.shared.generated.resources.import_error_file_read_failed
import dev.musicx.shared.generated.resources.import_error_generic
import dev.musicx.shared.generated.resources.import_error_incomplete_source
import dev.musicx.shared.generated.resources.import_error_load_failed
import dev.musicx.shared.generated.resources.import_error_local_files_require_local_playlist
import dev.musicx.shared.generated.resources.import_error_no_library_matches
import dev.musicx.shared.generated.resources.import_error_no_tracks_recognized
import dev.musicx.shared.generated.resources.import_error_no_tracks_to_import
import dev.musicx.shared.generated.resources.import_error_resolution_failed
import dev.musicx.shared.generated.resources.import_error_track_limit
import dev.musicx.shared.generated.resources.import_error_unsupported_link
import dev.musicx.shared.generated.resources.import_error_unsupported_playlist_entries
import dev.musicx.shared.generated.resources.import_error_youtube_dedicated_flow
import dev.musicx.shared.generated.resources.import_paste_amazon_hint
import dev.musicx.shared.generated.resources.import_paste_amazon_prompt
import dev.musicx.shared.generated.resources.import_paste_apple_music_hint
import dev.musicx.shared.generated.resources.import_paste_apple_music_link
import dev.musicx.shared.generated.resources.import_paste_deezer_hint
import dev.musicx.shared.generated.resources.import_paste_deezer_link
import dev.musicx.shared.generated.resources.import_paste_spotify_hint
import dev.musicx.shared.generated.resources.import_paste_spotify_link
import dev.musicx.shared.generated.resources.import_paste_tidal_hint
import dev.musicx.shared.generated.resources.import_paste_tidal_prompt
import dev.musicx.shared.generated.resources.import_paste_youtube_hint
import dev.musicx.shared.generated.resources.import_paste_youtube_link
import dev.musicx.shared.generated.resources.import_playlist
import dev.musicx.shared.generated.resources.import_playlists
import dev.musicx.shared.generated.resources.import_playlists_notice
import dev.musicx.shared.generated.resources.import_provider_amazon
import dev.musicx.shared.generated.resources.import_provider_amazon_desc
import dev.musicx.shared.generated.resources.import_provider_apple_music
import dev.musicx.shared.generated.resources.import_provider_apple_music_desc
import dev.musicx.shared.generated.resources.import_provider_deezer
import dev.musicx.shared.generated.resources.import_provider_deezer_desc
import dev.musicx.shared.generated.resources.import_provider_file
import dev.musicx.shared.generated.resources.import_provider_file_desc
import dev.musicx.shared.generated.resources.import_provider_spotify
import dev.musicx.shared.generated.resources.import_provider_spotify_desc
import dev.musicx.shared.generated.resources.import_provider_tidal
import dev.musicx.shared.generated.resources.import_provider_tidal_desc
import dev.musicx.shared.generated.resources.import_provider_youtube
import dev.musicx.shared.generated.resources.import_provider_youtube_desc
import dev.musicx.shared.generated.resources.import_status_fetching_metadata
import dev.musicx.shared.generated.resources.import_status_fetching_tracks
import dev.musicx.shared.generated.resources.import_status_saving_tracks
import dev.musicx.shared.generated.resources.import_tracks_found
import dev.musicx.shared.generated.resources.local_playlist
import dev.musicx.shared.generated.resources.menu_paste
import dev.musicx.shared.generated.resources.n_song
import dev.musicx.shared.generated.resources.or
import dev.musicx.shared.generated.resources.or_choose_existing_playlist
import dev.musicx.shared.generated.resources.playlist_imports_title
import dev.musicx.shared.generated.resources.playlist_name
import dev.musicx.shared.generated.resources.playlist_type
import dev.musicx.shared.generated.resources.restore
import dev.musicx.shared.generated.resources.restore_backup
import dev.musicx.shared.generated.resources.restore_backup_confirm
import dev.musicx.shared.generated.resources.restore_backup_desc
import dev.musicx.shared.generated.resources.restore_failed
import dev.musicx.shared.generated.resources.restore_replaces_data
import dev.musicx.shared.generated.resources.restoring_backup
import dev.musicx.shared.generated.resources.sensitive_backup
import dev.musicx.shared.generated.resources.youtube_music
import dev.musicx.shared.playlistimport.ImportErrorKey
import dev.musicx.shared.playlistimport.ImportException
import dev.musicx.shared.playlistimport.ImportProgressState
import dev.musicx.shared.playlistimport.ImportSourceType
import dev.musicx.shared.playlistimport.ImportedPlaylistData
import dev.musicx.shared.playlistimport.PlaylistImportManager
import dev.musicx.shared.playlistimport.PlaylistImportStatusManager
import dev.musicx.shared.playlistimport.UnifiedPlaylistImporter
import dev.musicx.shared.playlistimport.UniversalPlaylistParser
import dev.musicx.shared.playlistimport.readPlaylistText
import dev.musicx.shared.state.GlobalYouTubeRepository
import dev.musicx.shared.ui.components.AlertDialog
import dev.musicx.shared.ui.components.Dialog
import dev.musicx.shared.ui.components.ExpressiveFilterChip
import dev.musicx.shared.ui.components.PopupContent
import dev.musicx.shared.ui.components.PopupVisibility
import dev.musicx.shared.ui.menu.CsvColumnMappingDialog
import dev.musicx.shared.ui.utils.DragScrollableColumn
import dev.musicx.shared.ui.utils.verticalScrollWithDrag
import dev.musicx.shared.utils.XdgUtils
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.dialogs.FileKitDialogSettings
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.openFilePicker
import io.github.vinceglb.filekit.dialogs.openFileSaver
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.io.File
import kotlin.time.Duration.Companion.milliseconds

private enum class DesktopDestinationType {
    LOCAL,
    FAVORITES,
    SYNCED,
}

private suspend fun resolveImportError(throwable: Throwable?): String {
    val key = (throwable as? ImportException)?.errorKey ?: (throwable?.cause as? ImportException)?.errorKey
    return when (key) {
        ImportErrorKey.EmptyInput -> getString(Res.string.import_error_empty_input)
        ImportErrorKey.YouTubeDedicatedFlow -> getString(Res.string.import_error_youtube_dedicated_flow)
        ImportErrorKey.InvalidYouTubeUrl -> getString(Res.string.import_error_empty_playlist)
        ImportErrorKey.NoTracksRecognized -> getString(Res.string.import_error_no_tracks_recognized)
        ImportErrorKey.NoTracksToImport -> getString(Res.string.import_error_no_tracks_to_import)
        ImportErrorKey.ResolutionFailed -> getString(Res.string.import_error_resolution_failed)
        ImportErrorKey.IncompleteSource -> getString(Res.string.import_error_incomplete_source)
        ImportErrorKey.NoLibraryMatches -> getString(Res.string.import_error_no_library_matches)
        ImportErrorKey.UnsupportedPlaylistEntries -> getString(Res.string.import_error_unsupported_playlist_entries)
        ImportErrorKey.LocalFilesRequireLocalPlaylist -> getString(Res.string.import_error_local_files_require_local_playlist)
        ImportErrorKey.FileReadFailed -> getString(Res.string.import_error_file_read_failed)
        ImportErrorKey.TooManyTracks -> getString(Res.string.import_error_track_limit, PlaylistImportManager.MAX_PLAYLIST_TRACKS)
        ImportErrorKey.Generic -> getString(Res.string.import_error_generic)
        null -> throwable?.message ?: getString(Res.string.import_error_unsupported_link)
    }
}

@Composable
@Suppress("FunctionName")
private fun ProviderImportDialog(
    title: StringResource,
    accountTitle: StringResource,
    description: StringResource,
    pastePrompt: StringResource,
    pasteHint: StringResource,
    value: String,
    onValueChange: (String) -> Unit,
    colorScheme: ColorScheme,
    onDismissRequest: () -> Unit,
    onConnect: () -> Unit,
    onSubmit: (String) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(stringResource(title)) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().widthIn(max = 520.dp).verticalScrollWithDrag(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = colorScheme.secondaryContainer),
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text = stringResource(description),
                            style = MaterialTheme.typography.bodyMedium,
                            color = colorScheme.onSecondaryContainer,
                        )
                        Button(onClick = onConnect, modifier = Modifier.fillMaxWidth(), shapes = ButtonDefaults.shapes()) {
                            Text(stringResource(accountTitle))
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    HorizontalDivider(modifier = Modifier.weight(1f))
                    Text(
                        text = stringResource(Res.string.or),
                        style = MaterialTheme.typography.labelMedium,
                        color = colorScheme.onSurfaceVariant,
                    )
                    HorizontalDivider(modifier = Modifier.weight(1f))
                }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = stringResource(pastePrompt),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Medium,
                    )
                    OutlinedTextField(
                        value = value,
                        onValueChange = onValueChange,
                        placeholder = { Text(stringResource(pasteHint)) },
                        shape = MaterialTheme.shapes.medium,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    TextButton(
                        onClick = {
                            val text =
                                try {
                                    Toolkit.getDefaultToolkit().systemClipboard.getData(DataFlavor.stringFlavor) as? String
                                } catch (_: Exception) {
                                    null
                                }
                            if (!text.isNullOrBlank()) onValueChange(text)
                        },
                        shapes = ButtonDefaults.shapes(),
                    ) {
                        Text(stringResource(Res.string.menu_paste))
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = value.isNotBlank(),
                onClick = {
                    onDismissRequest()
                    onSubmit(value)
                },
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(stringResource(Res.string.import_action_start))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest, shapes = ButtonDefaults.shapes()) {
                Text(stringResource(Res.string.cancel))
            }
        },
    )
}

@Composable
@Suppress("FunctionName")
fun BackupRestoreSettingsScreen(colorScheme: ColorScheme) {
    val scope = rememberCoroutineScope()
    val database: MusicDatabase = koinInject()

    val playlistImportManager =
        remember(database) {
            PlaylistImportManager(database, GlobalYouTubeRepository.instance)
        }

    val playlists by database.dao().allPlaylists().collectAsState(emptyList())
    val editablePlaylists = remember(playlists) { playlists.filter { it.isEditable } }

    var showBackupWarning by remember { mutableStateOf(false) }
    var warningSeconds by remember { mutableIntStateOf(5) }
    var restoreFile by remember { mutableStateOf<File?>(null) }
    var isRestoring by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }

    var showSpotifyAccountFlow by remember { mutableStateOf(false) }
    var showSpotifyProviderDialog by remember { mutableStateOf(false) }
    var spotifyInputUrl by remember { mutableStateOf("") }
    var showAppleMusicAccountFlow by remember { mutableStateOf(false) }
    var showAppleMusicProviderDialog by remember { mutableStateOf(false) }
    var appleMusicInputUrl by remember { mutableStateOf("") }
    var showDeezerAccountFlow by remember { mutableStateOf(false) }
    var showDeezerProviderDialog by remember { mutableStateOf(false) }
    var deezerInputUrl by remember { mutableStateOf("") }
    var showTidalAccountFlow by remember { mutableStateOf(false) }
    var showTidalProviderDialog by remember { mutableStateOf(false) }
    var tidalInputUrl by remember { mutableStateOf("") }
    var showAmazonAccountFlow by remember { mutableStateOf(false) }
    var showAmazonProviderDialog by remember { mutableStateOf(false) }
    var amazonInputUrl by remember { mutableStateOf("") }
    var inputDialogTitle by remember { mutableStateOf<String?>(null) }
    var inputDialogPrompt by remember { mutableStateOf<String?>(null) }
    var inputDialogHint by remember { mutableStateOf<String?>(null) }
    var inputDialogValue by remember { mutableStateOf("") }
    var inputDialogIsMultiline by remember { mutableStateOf(false) }
    var onInputSubmitted by remember { mutableStateOf<((String) -> Unit)?>(null) }

    var isBusy by remember { mutableStateOf(false) }
    var liveProgressState by remember { mutableStateOf<ImportProgressState>(ImportProgressState.Idle) }

    var pendingImportFile by remember { mutableStateOf<File?>(null) }
    var pendingImportData by remember { mutableStateOf<ImportedPlaylistData?>(null) }
    var showDestinationPicker by remember { mutableStateOf(false) }
    var destinationType by remember { mutableStateOf(DesktopDestinationType.LOCAL) }
    var selectedDestinationPlaylist by remember { mutableStateOf<PlaylistEntity?>(null) }
    var customPlaylistTitle by remember { mutableStateOf("") }

    var errorDetails by remember { mutableStateOf<Pair<String, List<PlaylistImportManager.CsvTrack>>?>(null) }

    var duplicateWarning by remember { mutableStateOf<ImportProgressState.DuplicateDetected?>(null) }
    var pendingDuplicateDestination by remember { mutableStateOf<PlaylistImportManager.Destination?>(null) }

    val appData = stringResource(Res.string.app_data)
    val restorePickerTitle = stringResource(Res.string.backup_picker_title)
    val createBackupDialogTitle = stringResource(Res.string.create_backup_dialog_title)
    val backupFilenamePrefix = stringResource(Res.string.backup_filename_prefix)
    val backupCreated = stringResource(Res.string.backup_created)

    LaunchedEffect(showBackupWarning) {
        if (showBackupWarning) {
            warningSeconds = 5
            while (warningSeconds > 0) {
                delay(1_000.milliseconds)
                warningSeconds--
            }
        }
    }

    fun startUnifiedImport(
        data: ImportedPlaylistData,
        destination: PlaylistImportManager.Destination,
        ignoreDuplicate: Boolean = false,
    ) {
        val targetTitle = customPlaylistTitle.ifBlank { data.title }
        val statusId = PlaylistImportStatusManager.startImport(targetTitle, data.source)
        val job =
            AppState.scope.launch(Dispatchers.Default) {
                try {
                    if (PlaylistImportStatusManager.activeImport.value?.id != statusId) return@launch
                    isBusy = true
                    if (!ignoreDuplicate) {
                        val dup = UnifiedPlaylistImporter.checkForDuplicate(database, targetTitle, destination)
                        if (dup != null) {
                            duplicateWarning = dup
                            pendingDuplicateDestination = destination
                            PlaylistImportStatusManager.clear(statusId)
                            return@launch
                        }
                    }
                    pendingImportData = null

                    val result =
                        UnifiedPlaylistImporter.executeImport(
                            manager = playlistImportManager,
                            title = targetTitle,
                            tracks = data.tracks,
                            destination = destination,
                            provider = data.provider,
                            sourceYouTubePlaylistId = data.sourceYouTubePlaylistId,
                            coverUrl = data.coverUrl,
                        ) { state ->
                            liveProgressState = state
                            PlaylistImportStatusManager.updateProgress(statusId, state)
                        }

                    result
                        .onSuccess { completed ->
                            PlaylistImportStatusManager.complete(statusId, completed)
                            if (completed.importedTracks > 0) {
                                LibraryContentState.fetchLibraryData(force = true)
                            }
                            if (completed.unresolvedTracks.isNotEmpty()) {
                                val summary =
                                    getString(
                                        Res.string.import_error_details_summary,
                                        completed.unresolvedTracks.size,
                                    )
                                errorDetails = summary to completed.unresolvedTracks
                            }
                        }.onFailure { err ->
                            val failMsg = resolveImportError(err)
                            PlaylistImportStatusManager.fail(statusId, ImportProgressState.Failed(failMsg, err))
                            errorDetails = failMsg to emptyList()
                        }
                } catch (e: Exception) {
                    if (e !is CancellationException) {
                        val failMsg = resolveImportError(e)
                        PlaylistImportStatusManager.fail(statusId, ImportProgressState.Failed(failMsg, e))
                        errorDetails = failMsg to emptyList()
                    }
                } finally {
                    isBusy = false
                    liveProgressState = ImportProgressState.Idle
                }
            }
        PlaylistImportStatusManager.attachJob(statusId, job)
    }

    fun handleLinkOrTextSubmitted(rawInput: String) {
        val trimmed = rawInput.trim()
        if (trimmed.isBlank()) return

        if (trimmed.contains("collection/tracks")) {
            showSpotifyAccountFlow = true
            return
        }

        AppState.scope.launch(Dispatchers.Default) {
            isBusy = true
            val initialSource =
                getString(
                    when (UnifiedPlaylistImporter.detectSource(trimmed)) {
                        ImportSourceType.SPOTIFY -> Res.string.import_provider_spotify
                        ImportSourceType.APPLE_MUSIC -> Res.string.import_provider_apple_music
                        ImportSourceType.DEEZER -> Res.string.import_provider_deezer
                        ImportSourceType.YOUTUBE -> Res.string.import_provider_youtube
                        ImportSourceType.TIDAL -> Res.string.import_provider_tidal
                        ImportSourceType.AMAZON -> Res.string.import_provider_amazon
                        ImportSourceType.FILE_OR_TEXT, ImportSourceType.UNKNOWN -> Res.string.import_provider_file
                    },
                )
            val guessedTitle =
                trimmed
                    .substringAfterLast('/')
                    .substringBefore('?')
                    .take(30)
                    .ifBlank { getString(Res.string.playlist_type) }
            val statusId = PlaylistImportStatusManager.startImport(guessedTitle, initialSource)
            PlaylistImportStatusManager.attachJob(statusId, coroutineContext[Job]!!)
            liveProgressState = ImportProgressState.FetchingMetadata(initialSource)
            try {
                val ytId = PlaylistImportManager.parsePlaylistId(trimmed)
                if (ytId != null && (trimmed.contains("youtube.com") || trimmed.contains("youtu.be"))) {
                    PlaylistImportStatusManager.updateProgress(statusId, ImportProgressState.FetchingMetadata("YouTube Music"))
                    val ytResult = playlistImportManager.loadYouTubePlaylist(trimmed)
                    ytResult
                        .onSuccess { (title, songs, coverUrl) ->
                            customPlaylistTitle = title
                            val tracks =
                                songs.map {
                                    PlaylistImportManager.CsvTrack(
                                        it.title,
                                        it.artists.joinToString { a -> a.name },
                                        it.id,
                                    )
                                }
                            pendingImportData =
                                ImportedPlaylistData(
                                    title = title,
                                    tracks = tracks,
                                    source = getString(Res.string.youtube_music),
                                    sourceYouTubePlaylistId = ytId,
                                    coverUrl = coverUrl,
                                )
                            showDestinationPicker = true
                            PlaylistImportStatusManager.clear(statusId)
                        }.onFailure { err ->
                            val msg = err.message ?: getString(Res.string.import_error_load_failed, trimmed)
                            PlaylistImportStatusManager.fail(statusId, ImportProgressState.Failed(msg, err))
                            errorDetails = msg to emptyList()
                        }
                } else {
                    val parsedResult =
                        UnifiedPlaylistImporter.parseFromInput(trimmed) { loaded, total ->
                            val state = ImportProgressState.LoadingTracks(initialSource, loaded, total)
                            liveProgressState = state
                            PlaylistImportStatusManager.updateProgress(statusId, state)
                        }
                    parsedResult
                        .onSuccess { data ->
                            customPlaylistTitle = data.title
                            pendingImportData = data
                            showDestinationPicker = true
                            PlaylistImportStatusManager.clear(statusId)
                        }.onFailure { err ->
                            val msg = resolveImportError(err)
                            PlaylistImportStatusManager.fail(statusId, ImportProgressState.Failed(msg, err))
                            errorDetails = msg to emptyList()
                        }
                }
            } catch (e: CancellationException) {
                PlaylistImportStatusManager.finishCancellation(statusId, e)
                throw e
            } catch (e: Exception) {
                val msg = resolveImportError(e)
                PlaylistImportStatusManager.fail(statusId, ImportProgressState.Failed(msg, e))
                errorDetails = msg to emptyList()
            } finally {
                isBusy = false
                liveProgressState = ImportProgressState.Idle
            }
        }
    }

    fun openLinkOrTextInput(
        titleRes: StringResource,
        promptRes: StringResource,
        hintRes: StringResource,
        isMultiline: Boolean = false,
        onSubmit: (String) -> Unit,
    ) {
        scope.launch {
            inputDialogTitle = getString(titleRes)
            inputDialogPrompt = getString(promptRes)
            inputDialogHint = getString(hintRes)
            inputDialogIsMultiline = isMultiline
            inputDialogValue = ""
            onInputSubmitted = onSubmit
        }
    }

    Column(Modifier.fillMaxSize()) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScrollWithDrag(rememberScrollState())
                    .padding(vertical = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (isBusy) LinearWavyProgressIndicator(Modifier.fillMaxWidth())
            Material3SettingsGroup(
                title = appData,
                colorScheme = colorScheme,
                searchKeywords = listOf("backup", "restore", "export", "import"),
                items =
                    listOf(
                        Material3SettingsItem(
                            title = { Text(stringResource(Res.string.create_backup)) },
                            description = { Text(stringResource(Res.string.create_backup_desc)) },
                            onClick = { showBackupWarning = true },
                        ),
                        Material3SettingsItem(
                            title = { Text(stringResource(Res.string.restore_backup)) },
                            description = { Text(stringResource(Res.string.restore_backup_desc)) },
                            onClick = {
                                scope.launch {
                                    runCatching {
                                        FileKit.openFilePicker(
                                            type = FileKitType.File(listOf("zip", "backup")),
                                            dialogSettings = FileKitDialogSettings(title = restorePickerTitle),
                                        )
                                    }.onSuccess { selected -> restoreFile = selected?.file }
                                        .onFailure { status = getString(Res.string.could_not_open_file_picker, it.message ?: "") }
                                }
                            },
                        ),
                        Material3SettingsItem(
                            title = { Text(stringResource(Res.string.foreign_import_title)) },
                            description = {
                                Text(stringResource(if (isBusy) Res.string.backup_restore_busy else Res.string.foreign_import_desc))
                            },
                            enabled = !isBusy,
                            onClick = {
                                scope.launch {
                                    val file =
                                        runCatching {
                                            FileKit.openFilePicker(dialogSettings = FileKitDialogSettings(title = restorePickerTitle))
                                        }.onFailure { status = getString(Res.string.could_not_open_file_picker, it.message ?: "") }
                                            .getOrNull()
                                            ?.file ?: return@launch
                                    isBusy = true
                                    val result =
                                        withContext(Dispatchers.IO) {
                                            runCatching {
                                                file.inputStream().use { input ->
                                                    database.importForeignBackup(
                                                        input,
                                                        File(XdgUtils.getXdgCacheHome(), "metrolist/foreign-import"),
                                                        JdbcSQLiteDriver(),
                                                    )
                                                }
                                            }
                                        }
                                    isBusy = false
                                    status = result.foreignImportMessage()
                                }
                            },
                        ),
                    ),
            )

            Material3SettingsGroup(
                title = stringResource(Res.string.playlist_imports_title),
                colorScheme = colorScheme,
                searchKeywords = listOf("playlist", "import", "spotify", "apple music", "youtube", "deezer", "tidal", "amazon", "csv"),
                items =
                    listOf(
                        Material3SettingsItem(
                            title = { Text(stringResource(Res.string.import_provider_spotify)) },
                            description = { Text(stringResource(Res.string.import_provider_spotify_desc)) },
                            onClick = {
                                spotifyInputUrl = ""
                                showSpotifyProviderDialog = true
                            },
                        ),
                        Material3SettingsItem(
                            title = { Text(stringResource(Res.string.import_provider_apple_music)) },
                            description = { Text(stringResource(Res.string.import_provider_apple_music_desc)) },
                            onClick = {
                                appleMusicInputUrl = ""
                                showAppleMusicProviderDialog = true
                            },
                        ),
                        Material3SettingsItem(
                            title = { Text(stringResource(Res.string.import_provider_youtube)) },
                            description = { Text(stringResource(Res.string.import_provider_youtube_desc)) },
                            onClick = {
                                openLinkOrTextInput(
                                    titleRes = Res.string.import_provider_youtube,
                                    promptRes = Res.string.import_paste_youtube_link,
                                    hintRes = Res.string.import_paste_youtube_hint,
                                ) { url -> handleLinkOrTextSubmitted(url) }
                            },
                        ),
                        Material3SettingsItem(
                            title = { Text(stringResource(Res.string.import_provider_deezer)) },
                            description = { Text(stringResource(Res.string.import_provider_deezer_desc)) },
                            onClick = {
                                deezerInputUrl = ""
                                showDeezerProviderDialog = true
                            },
                        ),
                        Material3SettingsItem(
                            title = { Text(stringResource(Res.string.import_provider_tidal)) },
                            description = { Text(stringResource(Res.string.import_provider_tidal_desc)) },
                            onClick = {
                                tidalInputUrl = ""
                                showTidalProviderDialog = true
                            },
                        ),
                        Material3SettingsItem(
                            title = { Text(stringResource(Res.string.import_provider_amazon)) },
                            description = { Text(stringResource(Res.string.import_provider_amazon_desc)) },
                            onClick = {
                                amazonInputUrl = ""
                                showAmazonProviderDialog = true
                            },
                        ),
                        Material3SettingsItem(
                            title = { Text(stringResource(Res.string.import_provider_file)) },
                            description = { Text(stringResource(Res.string.import_provider_file_desc)) },
                            onClick = {
                                scope.launch {
                                    runCatching {
                                        FileKit.openFilePicker(
                                            type = FileKitType.File(listOf("csv", "tsv", "m3u", "m3u8", "json", "txt")),
                                            dialogSettings = FileKitDialogSettings(title = getString(Res.string.import_action_choose_file)),
                                        )
                                    }.onSuccess { selected ->
                                        val file = selected?.file ?: return@onSuccess
                                        scope.launch(Dispatchers.Default) {
                                            runCatching {
                                                val content = withContext(Dispatchers.IO) { file.inputStream().use(::readPlaylistText) }
                                                UniversalPlaylistParser.prepareFile(
                                                    content,
                                                    file.name,
                                                    file.nameWithoutExtension,
                                                    getString(Res.string.import_provider_file),
                                                ) { reference ->
                                                    validatedLocalMediaUrl(reference, file.parentFile?.toPath())
                                                }
                                            }.onSuccess { data ->
                                                if (data.csv == null && data.tracks.isEmpty()) {
                                                    errorDetails = getString(Res.string.import_error_empty_playlist) to emptyList()
                                                } else {
                                                    customPlaylistTitle = file.nameWithoutExtension
                                                    pendingImportFile = file
                                                    pendingImportData = data
                                                    showDestinationPicker = true
                                                }
                                            }.onFailure {
                                                errorDetails = getString(Res.string.import_error_file_read_failed) to emptyList()
                                            }
                                        }
                                    }
                                }
                            },
                        ),
                    ),
            )

            status?.let {
                Text(
                    it,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    color = colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    PopupVisibility(visible = showBackupWarning) {
        AlertDialog(
            onDismissRequest = { showBackupWarning = false },
            title = { Text(stringResource(Res.string.sensitive_backup)) },
            text = {
                Text(
                    stringResource(Res.string.backup_sensitive_message),
                    color = colorScheme.error,
                )
            },
            confirmButton = {
                Button(
                    enabled = warningSeconds == 0,
                    onClick = {
                        showBackupWarning = false
                        scope.launch {
                            runCatching {
                                FileKit.openFileSaver(
                                    suggestedName = defaultBackupFilename(backupFilenamePrefix),
                                    defaultExtension = "zip",
                                    allowedExtensions = setOf("zip"),
                                    dialogSettings = FileKitDialogSettings(title = createBackupDialogTitle),
                                )
                            }.onSuccess { selected ->
                                val destination = selected?.file ?: return@onSuccess
                                val result = withContext(Dispatchers.IO) { DesktopBackupRestoreManager.backup(destination) }
                                status = result.fold({ backupCreated }, { getString(Res.string.backup_failed, it.message ?: "") })
                            }.onFailure { status = getString(Res.string.could_not_open_file_picker, it.message ?: "") }
                        }
                    },
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(
                        if (warningSeconds == 0) {
                            stringResource(Res.string.create_backup)
                        } else {
                            stringResource(Res.string.create_backup_countdown, warningSeconds)
                        },
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showBackupWarning = false },
                    shapes = ButtonDefaults.shapes(),
                ) { Text(stringResource(Res.string.cancel)) }
            },
        )
    }

    PopupContent(restoreFile) { file ->
        AlertDialog(
            onDismissRequest = { restoreFile = null },
            title = { Text(stringResource(Res.string.restore_backup_confirm)) },
            text = {
                Text(
                    stringResource(Res.string.restore_replaces_data),
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        restoreFile = null
                        isRestoring = true
                        scope.launch {
                            val result = withContext(Dispatchers.IO) { DesktopBackupRestoreManager.stageRestoreAndRestart(file) }
                            result
                                .onFailure {
                                    isRestoring = false
                                    status = getString(Res.string.restore_failed, it.message ?: "")
                                }
                        }
                    },
                    shapes = ButtonDefaults.shapes(),
                ) { Text(stringResource(Res.string.restore)) }
            },
            dismissButton = {
                TextButton(onClick = { restoreFile = null }, shapes = ButtonDefaults.shapes()) { Text(stringResource(Res.string.cancel)) }
            },
        )
    }

    PopupVisibility(visible = isRestoring) {
        Dialog(
            onDismissRequest = {},
            properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        ) {
            Surface(shape = MaterialTheme.shapes.extraLarge) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    LinearWavyProgressIndicator(Modifier.fillMaxWidth())
                    Text(stringResource(Res.string.restoring_backup), textAlign = TextAlign.Center)
                }
            }
        }
    }

    PopupVisibility(visible = showSpotifyProviderDialog) {
        ProviderImportDialog(
            title = Res.string.import_provider_spotify,
            accountTitle = Res.string.import_connect_spotify_account,
            description = Res.string.import_provider_spotify_desc,
            pastePrompt = Res.string.import_paste_spotify_link,
            pasteHint = Res.string.import_paste_spotify_hint,
            value = spotifyInputUrl,
            onValueChange = { spotifyInputUrl = it },
            colorScheme = colorScheme,
            onDismissRequest = { showSpotifyProviderDialog = false },
            onConnect = {
                showSpotifyProviderDialog = false
                showSpotifyAccountFlow = true
            },
            onSubmit = ::handleLinkOrTextSubmitted,
        )
    }

    if (showSpotifyAccountFlow) {
        DesktopSpotifyImportFlow(
            database = database,
            onDismissRequest = { showSpotifyAccountFlow = false },
            onStatus = { status = it },
        )
    }

    PopupVisibility(visible = showAppleMusicProviderDialog) {
        ProviderImportDialog(
            title = Res.string.import_provider_apple_music,
            accountTitle = Res.string.import_connect_apple_music_account,
            description = Res.string.import_provider_apple_music_desc,
            pastePrompt = Res.string.import_paste_apple_music_link,
            pasteHint = Res.string.import_paste_apple_music_hint,
            value = appleMusicInputUrl,
            onValueChange = { appleMusicInputUrl = it },
            colorScheme = colorScheme,
            onDismissRequest = { showAppleMusicProviderDialog = false },
            onConnect = {
                showAppleMusicProviderDialog = false
                showAppleMusicAccountFlow = true
            },
            onSubmit = ::handleLinkOrTextSubmitted,
        )
    }

    if (showAppleMusicAccountFlow) {
        DesktopAppleMusicImportFlow(
            database = database,
            onDismissRequest = { showAppleMusicAccountFlow = false },
            onStatus = { status = it },
        )
    }

    PopupVisibility(visible = showDeezerProviderDialog) {
        ProviderImportDialog(
            title = Res.string.import_provider_deezer,
            accountTitle = Res.string.import_connect_deezer_account,
            description = Res.string.import_provider_deezer_desc,
            pastePrompt = Res.string.import_paste_deezer_link,
            pasteHint = Res.string.import_paste_deezer_hint,
            value = deezerInputUrl,
            onValueChange = { deezerInputUrl = it },
            colorScheme = colorScheme,
            onDismissRequest = { showDeezerProviderDialog = false },
            onConnect = {
                showDeezerProviderDialog = false
                showDeezerAccountFlow = true
            },
            onSubmit = ::handleLinkOrTextSubmitted,
        )
    }

    if (showDeezerAccountFlow) {
        DesktopDeezerImportFlow(
            database = database,
            onDismissRequest = { showDeezerAccountFlow = false },
            onStatus = { status = it },
        )
    }

    PopupVisibility(visible = showTidalProviderDialog) {
        ProviderImportDialog(
            title = Res.string.import_provider_tidal,
            accountTitle = Res.string.import_connect_tidal_account,
            description = Res.string.import_provider_tidal_desc,
            pastePrompt = Res.string.import_paste_tidal_prompt,
            pasteHint = Res.string.import_paste_tidal_hint,
            value = tidalInputUrl,
            onValueChange = { tidalInputUrl = it },
            colorScheme = colorScheme,
            onDismissRequest = { showTidalProviderDialog = false },
            onConnect = {
                showTidalProviderDialog = false
                showTidalAccountFlow = true
            },
            onSubmit = ::handleLinkOrTextSubmitted,
        )
    }

    if (showTidalAccountFlow) {
        DesktopTidalImportFlow(
            database = database,
            onDismissRequest = { showTidalAccountFlow = false },
            onStatus = { status = it },
        )
    }

    PopupVisibility(visible = showAmazonProviderDialog) {
        ProviderImportDialog(
            title = Res.string.import_provider_amazon,
            accountTitle = Res.string.import_connect_amazon_music_account,
            description = Res.string.import_provider_amazon_desc,
            pastePrompt = Res.string.import_paste_amazon_prompt,
            pasteHint = Res.string.import_paste_amazon_hint,
            value = amazonInputUrl,
            onValueChange = { amazonInputUrl = it },
            colorScheme = colorScheme,
            onDismissRequest = { showAmazonProviderDialog = false },
            onConnect = {
                showAmazonProviderDialog = false
                showAmazonAccountFlow = true
            },
            onSubmit = ::handleLinkOrTextSubmitted,
        )
    }

    if (showAmazonAccountFlow) {
        DesktopAmazonMusicImportFlow(
            database = database,
            onDismissRequest = { showAmazonAccountFlow = false },
            onStatus = { status = it },
        )
    }

    PopupContent(inputDialogTitle) { title ->
        AlertDialog(
            onDismissRequest = {
                inputDialogTitle = null
                onInputSubmitted = null
            },
            title = { Text(title) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    inputDialogPrompt?.let { prompt -> Text(prompt, style = MaterialTheme.typography.bodyMedium) }
                    OutlinedTextField(
                        value = inputDialogValue,
                        onValueChange = { inputDialogValue = it },
                        placeholder = { inputDialogHint?.let { Text(it) } },
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .then(if (inputDialogIsMultiline) Modifier.heightIn(min = 120.dp, max = 240.dp) else Modifier),
                        singleLine = !inputDialogIsMultiline,
                        maxLines = if (inputDialogIsMultiline) 8 else 1,
                    )
                }
            },
            confirmButton = {
                Button(
                    enabled = inputDialogValue.isNotBlank(),
                    onClick = {
                        val value = inputDialogValue
                        val callback = onInputSubmitted
                        inputDialogTitle = null
                        onInputSubmitted = null
                        callback?.invoke(value)
                    },
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(stringResource(Res.string.import_action_start))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        inputDialogTitle = null
                        onInputSubmitted = null
                    },
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(stringResource(Res.string.cancel))
                }
            },
        )
    }

    pendingImportData?.let { data ->
        PopupContent(data.csv) { csv ->
            CsvColumnMappingDialog(
                csv = csv,
                onDismiss = {
                    pendingImportData = null
                    pendingImportFile = null
                    showDestinationPicker = false
                },
                onConfirm = { mapping ->
                    val file = pendingImportFile
                    scope.launch {
                        val result =
                            withContext(Dispatchers.Default) {
                                runCatching {
                                    val tracks =
                                        csv.tracks(mapping) { reference ->
                                            validatedLocalMediaUrl(reference, file?.parentFile?.toPath())
                                        }
                                    if (tracks.isEmpty()) throw ImportException(ImportErrorKey.NoTracksRecognized)
                                    data.copy(tracks = tracks, csv = null)
                                }
                            }
                        pendingImportFile = null
                        result.onSuccess { pendingImportData = it }.onFailure { error ->
                            pendingImportData = null
                            showDestinationPicker = false
                            errorDetails = resolveImportError(error) to emptyList()
                        }
                    }
                },
            )
        }
    }

    if (showDestinationPicker && pendingImportData != null && pendingImportData?.csv == null) {
        val data = pendingImportData!!
        AlertDialog(
            onDismissRequest = {
                showDestinationPicker = false
                pendingImportData = null
                selectedDestinationPlaylist = null
            },
            title = { Text(pluralStringResource(Res.plurals.import_playlists, data.sourcePlaylistCount)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        stringResource(Res.string.import_tracks_found, data.tracks.size),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )

                    if (data.tracks.size >= 1000) {
                        Text(
                            pluralStringResource(
                                Res.plurals.import_playlists_notice,
                                data.sourcePlaylistCount,
                                pluralStringResource(Res.plurals.n_song, data.tracks.size, data.tracks.size),
                                data.sourcePlaylistCount,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.error,
                        )
                    }

                    OutlinedTextField(
                        value = customPlaylistTitle,
                        onValueChange = { customPlaylistTitle = it },
                        label = { Text(stringResource(Res.string.playlist_name)) },
                        enabled = destinationType != DesktopDestinationType.FAVORITES && selectedDestinationPlaylist == null,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ExpressiveFilterChip(
                            selected = selectedDestinationPlaylist == null && destinationType == DesktopDestinationType.LOCAL,
                            onClick = {
                                selectedDestinationPlaylist = null
                                destinationType = DesktopDestinationType.LOCAL
                            },
                            label = { Text(stringResource(Res.string.local_playlist)) },
                        )
                        ExpressiveFilterChip(
                            selected = selectedDestinationPlaylist == null && destinationType == DesktopDestinationType.FAVORITES,
                            onClick = {
                                selectedDestinationPlaylist = null
                                destinationType = DesktopDestinationType.FAVORITES
                            },
                            label = { Text(stringResource(Res.string.import_destination_favorites)) },
                        )
                        if (GlobalYouTubeRepository.instance.canManagePlaylists()) {
                            ExpressiveFilterChip(
                                selected = selectedDestinationPlaylist == null && destinationType == DesktopDestinationType.SYNCED,
                                onClick = {
                                    selectedDestinationPlaylist = null
                                    destinationType = DesktopDestinationType.SYNCED
                                },
                                label = { Text(stringResource(Res.string.youtube_music)) },
                            )
                        }
                    }

                    if (editablePlaylists.isNotEmpty()) {
                        Text(
                            stringResource(Res.string.or_choose_existing_playlist),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                        )
                        DragScrollableColumn(modifier = Modifier.heightIn(max = 140.dp)) {
                            items(editablePlaylists) { playlist ->
                                Row(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .selectable(
                                                selected = selectedDestinationPlaylist?.id == playlist.id,
                                                role = Role.RadioButton,
                                                onClick = { selectedDestinationPlaylist = playlist },
                                            ).padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    RadioButton(
                                        selected = selectedDestinationPlaylist?.id == playlist.id,
                                        onClick = null,
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(playlist.name, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val destination =
                            selectedDestinationPlaylist?.let(PlaylistImportManager.Destination::EXISTING)
                                ?: when (destinationType) {
                                    DesktopDestinationType.LOCAL -> PlaylistImportManager.Destination.NewLocal
                                    DesktopDestinationType.FAVORITES -> PlaylistImportManager.Destination.Favorites
                                    DesktopDestinationType.SYNCED -> PlaylistImportManager.Destination.NewSynced
                                }
                        showDestinationPicker = false
                        pendingImportData = null
                        selectedDestinationPlaylist = null
                        startUnifiedImport(data, destination)
                    },
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(stringResource(Res.string.import_action_start))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showDestinationPicker = false
                        pendingImportData = null
                        selectedDestinationPlaylist = null
                        PlaylistImportStatusManager.activeImport.value
                            ?.id
                            ?.let(PlaylistImportStatusManager::dismiss)
                    },
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(stringResource(Res.string.cancel))
                }
            },
        )
    }

    errorDetails?.let { (summary, failedTracks) ->
        AlertDialog(
            onDismissRequest = { errorDetails = null },
            title = { Text(stringResource(Res.string.import_error_details_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(summary, style = MaterialTheme.typography.bodyMedium)
                    if (failedTracks.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        DragScrollableColumn(modifier = Modifier.heightIn(max = 200.dp)) {
                            items(failedTracks) { track ->
                                Text(
                                    "• ${track.title} -\u00a0${track.artist}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { errorDetails = null }, shapes = ButtonDefaults.shapes()) {
                    Text(stringResource(Res.string.close))
                }
            },
        )
    }

    PopupContent(duplicateWarning) { dup ->
        AlertDialog(
            onDismissRequest = {
                duplicateWarning = null
                pendingDuplicateDestination = null
                pendingImportData = null
            },
            title = { Text(stringResource(Res.string.import_duplicate_playlist_title)) },
            text = {
                Text(
                    stringResource(Res.string.import_duplicate_playlist_message, dup.existingPlaylistName, dup.trackCount),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val dest = pendingDuplicateDestination ?: return@TextButton
                        val data = pendingImportData ?: return@TextButton
                        duplicateWarning = null
                        pendingDuplicateDestination = null
                        startUnifiedImport(data, dest, ignoreDuplicate = true)
                    },
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(stringResource(Res.string.import_duplicate_playlist_import_anyway))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        duplicateWarning = null
                        pendingDuplicateDestination = null
                        pendingImportData = null
                    },
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(stringResource(Res.string.import_duplicate_playlist_skip))
                }
            },
        )
    }
}
