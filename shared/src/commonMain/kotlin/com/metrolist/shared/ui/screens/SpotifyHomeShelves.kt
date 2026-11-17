/**
 * MuSicX Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.shared.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
 * This composable maps Spotify's home feed items to YTItem equivalents
 * ([PlaylistItem], [AlbumItem], [ArtistItem]). Returns an empty list
 * when no sections are found or feed is null.
 */
@Composable
fun SpotifyHomeShelves(
    spotifyFeed: SpotifyHomeFeed?,
    onItemClick: (YTItem) -> Unit,
    onVideoItemClick: (YTItem) -> Unit = onItemClick,
): List<HomeShelf> {
    var shelves by remember { mutableStateOf<List<HomeShelf>>(emptyList()) }

    LaunchedEffect(spotifyFeed) {
        if (spotifyFeed == null) {
            shelves = emptyList()
            return@LaunchedEffect
        }

        shelves = spotifyFeed.sections.mapNotNull { section ->
            val resolvedItems = section.items.mapNotNull { item ->
                when (item) {
                    is SpotifyHomeFeedItem.Playlist -> PlaylistItem(
                        id = item.uri,
                        title = item.name,
                        thumbnail = item.imageUrl,
                        author = item.ownerName,
                        songCount = item.totalCount.takeIf { it > 0 }?.toString(),
                        description = item.description,
                        isEditable = false,
                    )

                    is SpotifyHomeFeedItem.Album -> AlbumItem(
                        id = item.uri,
                        title = item.name,
                        thumbnail = item.imageUrl,
                        artists = item.artists.map { ArtistTiny(id = null, name = it) },
                        type = item.albumType,
                    )

                    is SpotifyHomeFeedItem.Artist -> ArtistItem(
                        id = item.uri,
                        title = item.name,
                        thumbnail = item.imageUrl,
                    )
                }
            }

            if (resolvedItems.isNotEmpty()) {
                HomeShelf(
                    key = "spotify_${section.sectionUri}",
                    title = section.title ?: "",
                    items = resolvedItems,
                    style = HomeShelfStyle.CAROUSEL,
                    headerAction = HomeShelfHeaderAction.BROWSE,
                    browseId = section.sectionUri,
                    params = null,
                )
            } else null
        }
    }

    return shelves
}