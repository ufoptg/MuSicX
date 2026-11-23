package com.metrolist.shared.ui.menu

import androidx.compose.runtime.Composable
import com.metrolist.shared.api.playlistSongs
import com.metrolist.shared.generated.resources.Res
import com.metrolist.shared.generated.resources.open_in_spotify
import com.metrolist.shared.generated.resources.spotify_override_match_description
import com.metrolist.shared.generated.resources.spotify_override_match_title
import com.metrolist.shared.model.AlbumItem
import com.metrolist.shared.model.ArtistItem
import com.metrolist.shared.model.EpisodeItem
import com.metrolist.shared.model.PlaylistItem
import com.metrolist.shared.model.PodcastItem
import com.metrolist.shared.model.SongItem
import com.metrolist.shared.model.YTItem
import com.metrolist.shared.model.toSongItem
import com.metrolist.shared.state.GlobalYouTubeRepository
import com.metrolist.shared.ui.components.PlayerMenuAction
import com.metrolist.shared.ui.icons.MetrolistIcons
import com.metrolist.shared.ui.screens.spotifyWebUrl
import org.jetbrains.compose.resources.stringResource

suspend fun loadMediaItemSongs(item: YTItem): List<SongItem> {
    val repository = GlobalYouTubeRepository.instance
    return when (item) {
        is SongItem -> {
            listOf(item)
        }

        is EpisodeItem -> {
            listOf(item.toSongItem())
        }

        is AlbumItem -> {
            repository.getAlbum(item.id)?.songs.orEmpty()
        }

        is ArtistItem -> {
            repository.getArtistSongs(item.id)
        }

        is PlaylistItem -> {
            repository.getPlaylist(item.id).playlistSongs()
        }

        is PodcastItem -> {
            // Every page, so playback covers the whole podcast and can start from its first episode.
            buildList {
                repository.getPodcastPages(item.id).collect { page -> page.episodes.mapTo(this) { it.toSongItem(page.podcast.title) } }
            }
        }

        else -> {
            emptyList()
        }
    }
}

/**
 * Creates a "Open in Spotify" [PlayerMenuAction] for the given [item].
 * The caller should provide an [onOpenUrl] callback to handle the URL.
 */
@Composable
internal fun spotifyOpenInAction(
    item: YTItem,
    onDismiss: () -> Unit,
    onOpenUrl: (String) -> Unit,
): PlayerMenuAction? {
    val spotifyUrl = item.spotifyWebUrl() ?: return null
    return action(
        icon = MetrolistIcons.AutoMirrored.Filled.OpenInNew,
        title = stringResource(Res.string.open_in_spotify),
        onDismiss = onDismiss,
        onClick = { onOpenUrl(spotifyUrl) },
    )
}

/**
 * Creates an "Override match" [PlayerMenuAction] for the given Spotify track and YouTube ID.
 * The caller should provide an [onOverrideMatch] callback to handle the manual override.
 */
@Composable
internal fun spotifyOverrideMatchAction(
    spotifyUri: String,
    youtubeId: String,
    title: String,
    artist: String,
    onDismiss: () -> Unit,
    onOverrideMatch: (String, String, String, String) -> Unit,
): PlayerMenuAction =
    action(
        icon = MetrolistIcons.Outlined.Edit,
        title = stringResource(Res.string.spotify_override_match_title),
        description = stringResource(Res.string.spotify_override_match_description),
        onDismiss = onDismiss,
        onClick = { onOverrideMatch(spotifyUri, youtubeId, title, artist) },
    )
