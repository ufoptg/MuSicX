package dev.musicx.shared.spotify

import dev.musicx.shared.api.YouTubeRepository
import dev.musicx.shared.model.SearchFilter
import dev.musicx.shared.model.SongItem

/**
 * Factory for creating a SpotifyYouTubeMapper wired to a YouTubeRepository.
 *
 * The mapper uses the repository's `searchFiltered` API to find YouTube Music
 * candidates for Spotify tracks, then scores them with the fuzzy matching
 * algorithm in [SpotifyMapper].
 */
object SpotifyYouTubeMapperFactory {

    /** Creates a mapper that searches YouTube Music via [repository]. */
    fun create(repository: YouTubeRepository): SpotifyYouTubeMapper =
        SpotifyYouTubeMapper { query ->
            repository.searchFiltered(query, SearchFilter.SONG).items
                .filterIsInstance<SongItem>()
        }
}