/**
 * MuSicX Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.shared.ui.screens

import com.metrolist.shared.model.AlbumItem
import com.metrolist.shared.model.ArtistItem
import com.metrolist.shared.model.ArtistTiny
import com.metrolist.shared.model.PlaylistItem
import com.metrolist.shared.model.YTItem
import meow.nyx.spotlib.SpotifyHomeFeed
import meow.nyx.spotlib.SpotifyHomeFeedItem

/**
 * Builds [HomeShelf] objects from Spotify's home feed.
 *
 * Maps Spotify playlists, albums and artists to their YTItem equivalents.
 * Sections with no mappable items are dropped.
 */
fun buildSpotifyHomeShelves(spotifyFeed: SpotifyHomeFeed?): List<HomeShelf> =
    spotifyFeed?.sections.orEmpty().mapIndexedNotNull { index, section ->
        val resolvedItems =
            section.items.mapNotNull { item ->
                when (item) {
                    is SpotifyHomeFeedItem.Playlist -> {
                        PlaylistItem(
                            id = item.uri,
                            title = item.name,
                            thumbnail = item.imageUrl,
                            author = item.ownerName,
                            songCount = item.totalCount.takeIf { it > 0 }?.toString(),
                            description = item.description,
                            isEditable = false,
                        )
                    }

                    is SpotifyHomeFeedItem.Album -> {
                        AlbumItem(
                            id = item.uri,
                            title = item.name,
                            thumbnail = item.imageUrl,
                            artists = item.artists.map { ArtistTiny(id = null, name = it) },
                            type = item.albumType,
                        )
                    }

                    is SpotifyHomeFeedItem.Artist -> {
                        ArtistItem(
                            id = item.uri,
                            title = item.name,
                            thumbnail = item.imageUrl,
                        )
                    }
                }
            }

        if (resolvedItems.isEmpty()) {
            null
        } else {
            HomeShelf(
                key = "spotify_${index}_${section.sectionUri}",
                title = section.title ?: "",
                items = resolvedItems,
                style = HomeShelfStyle.CAROUSEL,
                headerAction = HomeShelfHeaderAction.NONE,
                params = null,
            )
        }
    }

/** Spotify items keep their Spotify URI as the item id. */
fun YTItem.spotifyWebUrl(): String? {
    if (!id.startsWith("spotify:")) return null
    val parts = id.split(":")
    if (parts.size != 3) return null
    // A collection (e.g. spotify:collection:tracks) is not a single item; treat as local MuSicX content instead of opening in Spotify.
    if (parts[1] == "collection") return null
    return "https://open.spotify.com/${parts[1]}/${parts[2]}"
}
