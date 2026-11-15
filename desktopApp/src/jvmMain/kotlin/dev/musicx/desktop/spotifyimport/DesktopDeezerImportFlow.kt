package dev.musicx.desktop.spotifyimport

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.musicx.shared.db.MusicDatabase
import dev.musicx.shared.deezerimport.DeezerImportManager
import dev.musicx.shared.generated.resources.Res
import dev.musicx.shared.generated.resources.import_deezer_picker_description
import dev.musicx.shared.generated.resources.import_deezer_picker_title
import dev.musicx.shared.generated.resources.import_deezer_selected_count
import dev.musicx.shared.generated.resources.import_provider_deezer
import dev.musicx.shared.generated.resources.import_status_fetching_metadata
import dev.musicx.shared.generated.resources.import_status_fetching_tracks
import dev.musicx.shared.generated.resources.liked_songs
import dev.musicx.shared.generated.resources.spotify_import_error_no_playlists
import dev.musicx.shared.generated.resources.spotify_import_error_sign_in_failed
import dev.musicx.shared.playlistimport.ImportSourceType
import dev.musicx.shared.spotifyimport.SpotifyImportManager
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import java.util.logging.Logger

@Composable
@Suppress("FunctionName")
internal fun DesktopDeezerImportFlow(
    database: MusicDatabase,
    onDismissRequest: () -> Unit,
    onStatus: (String) -> Unit,
) {
    val providerName = stringResource(Res.string.import_provider_deezer)
    val fetchingMetadata = stringResource(Res.string.import_status_fetching_metadata)
    val fetchingTracks = stringResource(Res.string.import_status_fetching_tracks)
    val noPlaylists = stringResource(Res.string.spotify_import_error_no_playlists)
    val signInFailed = stringResource(Res.string.spotify_import_error_sign_in_failed)
    val logger = remember { Logger.getLogger("DeezerImport") }
    val manager = remember { DeezerImportManager() }
    var deezerPlaylists by remember { mutableStateOf(emptyList<DeezerImportManager.LoadedPlaylist>()) }

    DesktopAccountImportFlow(
        database = database,
        providerName = providerName,
        provider = ImportSourceType.DEEZER,
        logger = logger,
        authenticationTitle = fetchingMetadata,
        playlistLoadingTitle = fetchingTracks,
        noPlaylists = noPlaylists,
        signInFailed = signInFailed,
        authenticationFailureLog = "Deezer authentication failed",
        playlistFailureLog = "Failed to load selected Deezer playlists",
        onDismissRequest = onDismissRequest,
        onStatus = onStatus,
        onClose = manager::close,
        loginContent = { onCredentials ->
            DeezerLoginDialog(onDismissRequest) { arl ->
                onCredentials(
                    { manager.authenticate(arl) },
                    {
                        deezerPlaylists = manager.loadPlaylists(likedSongsName = getString(Res.string.liked_songs))
                        deezerPlaylists.map { DesktopImportPlaylist(it.id, it.name, it.image, it.trackCount) }
                    },
                )
            }
        },
        playlistPicker = { playlists, selectedIds, onSelectionChange, onReview, dismiss ->
            DesktopAccountPlaylistPickerDialog(
                title = Res.string.import_deezer_picker_title,
                description = Res.string.import_deezer_picker_description,
                selectedCount = Res.string.import_deezer_selected_count,
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
                    val playlist = deezerPlaylists.firstOrNull { it.id == option.id } ?: continue
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
