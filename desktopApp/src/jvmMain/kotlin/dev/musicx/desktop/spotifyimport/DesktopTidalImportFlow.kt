package dev.musicx.desktop.spotifyimport

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.musicx.shared.db.MusicDatabase
import dev.musicx.shared.generated.resources.Res
import dev.musicx.shared.generated.resources.import_provider_tidal
import dev.musicx.shared.generated.resources.import_status_fetching_metadata
import dev.musicx.shared.generated.resources.import_status_fetching_tracks
import dev.musicx.shared.generated.resources.import_tidal_picker_description
import dev.musicx.shared.generated.resources.import_tidal_picker_title
import dev.musicx.shared.generated.resources.import_tidal_selected_count
import dev.musicx.shared.generated.resources.liked_songs
import dev.musicx.shared.generated.resources.spotify_import_error_no_playlists
import dev.musicx.shared.generated.resources.spotify_import_error_sign_in_failed
import dev.musicx.shared.playlistimport.ImportSourceType
import dev.musicx.shared.spotifyimport.SpotifyImportManager
import dev.musicx.shared.tidalimport.TidalImportManager
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import java.util.logging.Logger

@Composable
@Suppress("FunctionName")
internal fun DesktopTidalImportFlow(
    database: MusicDatabase,
    onDismissRequest: () -> Unit,
    onStatus: (String) -> Unit,
) {
    val providerName = stringResource(Res.string.import_provider_tidal)
    val fetchingMetadata = stringResource(Res.string.import_status_fetching_metadata)
    val fetchingTracks = stringResource(Res.string.import_status_fetching_tracks)
    val noPlaylists = stringResource(Res.string.spotify_import_error_no_playlists)
    val signInFailed = stringResource(Res.string.spotify_import_error_sign_in_failed)
    val logger = remember { Logger.getLogger("TidalImport") }
    val manager = remember { TidalImportManager() }
    var tidalPlaylists by remember { mutableStateOf(emptyList<TidalImportManager.LoadedPlaylist>()) }

    DesktopAccountImportFlow(
        database = database,
        providerName = providerName,
        provider = ImportSourceType.TIDAL,
        logger = logger,
        authenticationTitle = fetchingMetadata,
        playlistLoadingTitle = fetchingTracks,
        noPlaylists = noPlaylists,
        signInFailed = signInFailed,
        authenticationFailureLog = "Tidal authentication failed",
        playlistFailureLog = "Failed to load selected Tidal playlists",
        onDismissRequest = onDismissRequest,
        onStatus = onStatus,
        onClose = manager::close,
        loginContent = { onCredentials ->
            TidalLoginDialog(onDismissRequest) { sessionId, userId ->
                onCredentials(
                    { manager.authenticate(sessionId, userId) },
                    {
                        tidalPlaylists = manager.loadPlaylists(likedSongsName = getString(Res.string.liked_songs))
                        tidalPlaylists.map { DesktopImportPlaylist(it.id, it.name, it.image, it.trackCount) }
                    },
                )
            }
        },
        playlistPicker = { playlists, selectedIds, onSelectionChange, onReview, dismiss ->
            DesktopAccountPlaylistPickerDialog(
                title = Res.string.import_tidal_picker_title,
                description = Res.string.import_tidal_picker_description,
                selectedCount = Res.string.import_tidal_selected_count,
                playlists = playlists,
                selectedIds = selectedIds,
                onSelectionChange = onSelectionChange,
                onReview = onReview,
                onDismissRequest = dismiss,
            )
        },
        loadSelectedPlaylists = { selected, onProgress ->
            buildList {
                for ((index, option) in selected.withIndex()) {
                    val playlist = tidalPlaylists.firstOrNull { it.id == option.id } ?: continue
                    onProgress(index + 1, selected.size)
                    val importedData =
                        manager.loadPlaylist(playlist, likedSongsName = getString(Res.string.liked_songs)) { loaded, total ->
                            onProgress(loaded, total ?: 0)
                        }
                    add(
                        SpotifyImportManager.LoadedPlaylist(
                            uri = playlist.id,
                            name = playlist.name,
                            image = playlist.image,
                            tracks =
                                importedData.tracks.map {
                                    SpotifyImportManager.ImportTrack(
                                        it.title,
                                        it.artist,
                                        it.album,
                                        it.durationSeconds,
                                    )
                                },
                            reachedTrackLimit = false,
                        ),
                    )
                }
            }
        },
    )
}
