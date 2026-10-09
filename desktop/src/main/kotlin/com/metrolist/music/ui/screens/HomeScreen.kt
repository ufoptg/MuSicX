/**
 * MuSicX Project (C) 2026
 * Credits to Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 *
 * Desktop Home screen — mirrors the Android app's Home layout language.
 */

package com.metrolist.music.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.metrolist.music.desktop.HomeRow
import com.metrolist.music.desktop.SearchHit
import com.metrolist.music.ui.component.GridItem
import com.metrolist.music.ui.component.HeroCard
import com.metrolist.music.ui.component.ImageLoader
import com.metrolist.music.ui.component.SectionHeader
import com.metrolist.music.ui.theme.Dimensions
import com.metrolist.spotify.models.SpotifyHomeFeedItem
import com.metrolist.spotify.models.SpotifyHomeFeedSection

private fun spotifyCardTitle(item: SpotifyHomeFeedItem): String =
    when (item) {
        is SpotifyHomeFeedItem.Playlist -> item.name
        is SpotifyHomeFeedItem.Album -> item.name
        is SpotifyHomeFeedItem.Artist -> item.name
    }

private fun spotifyCardSubtitle(item: SpotifyHomeFeedItem): String? =
    when (item) {
        is SpotifyHomeFeedItem.Playlist -> item.ownerName ?: "Playlist"
        is SpotifyHomeFeedItem.Album -> item.artists.joinToString(", ") { it.name }
        is SpotifyHomeFeedItem.Artist -> "Artist"
    }

private fun spotifyCardImage(item: SpotifyHomeFeedItem): String? =
    when (item) {
        is SpotifyHomeFeedItem.Playlist -> item.imageUrl
        is SpotifyHomeFeedItem.Album -> item.imageUrl
        is SpotifyHomeFeedItem.Artist -> item.imageUrl
    }

@Composable
private fun SpotifyCard(
    item: SpotifyHomeFeedItem,
    onOpen: (SpotifyHomeFeedItem) -> Unit,
    image: ImageLoader,
) {
    GridItem(
        title = spotifyCardTitle(item),
        subtitle = spotifyCardSubtitle(item),
        isActive = false,
        isBusy = false,
        onClick = { onOpen(item) },
        image = image,
        thumbnailUrl = spotifyCardImage(item),
    )
}

@Composable
fun HomeScreen(
    rows: List<HomeRow>,
    recentlyPlayed: List<SearchHit>,
    loading: Boolean,
    error: String?,
    hideYoutubeHome: Boolean,
    nowPlayingId: String?,
    busyId: String?,
    onPlay: (List<SearchHit>, Int) -> Unit,
    onShuffle: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenStats: () -> Unit,
    onRetry: () -> Unit,
    spotifySections: List<SpotifyHomeFeedSection> = emptyList(),
    spotifyLoading: Boolean = false,
    onOpenSpotifyItem: (SpotifyHomeFeedItem) -> Unit = {},
    image: ImageLoader,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().height(Dimensions.AppBarHeight).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Home",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onOpenHistory) {
                Icon(Icons.Default.History, contentDescription = "History")
            }
            IconButton(onClick = onOpenStats) {
                Icon(Icons.Default.Insights, contentDescription = "Stats")
            }
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when {
                hideYoutubeHome -> {
                    if (spotifySections.isNotEmpty()) {
                        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp)) {
                            spotifySections.forEachIndexed { sectionIndex, section ->
                                item(key = "sp_h_$sectionIndex") { SectionHeader(section.title ?: "For you") }
                                item(key = "sp_r_$sectionIndex") {
                                    LazyRow(
                                        contentPadding = PaddingValues(horizontal = 12.dp),
                                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                                    ) {
                                        items(section.items, key = { it.uri }) { card ->
                                            SpotifyCard(card, onOpenSpotifyItem, image)
                                        }
                                    }
                                }
                            }
                        }
                    } else if (spotifyLoading) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        }
                    } else {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = "Spotify home is empty — sign in to Spotify or check your connection.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(24.dp),
                            )
                        }
                    }
                }
                loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
                error != null -> Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = "Retry",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.clickable(onClick = onRetry).padding(8.dp),
                    )
                }
                else -> {
                    val heroIndex = recentlyPlayed.indexOfFirst { it.videoId == nowPlayingId }.takeIf { it >= 0 } ?: 0
                    val heroTrack = recentlyPlayed.getOrNull(heroIndex)

                    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp)) {
                        spotifySections.forEachIndexed { sectionIndex, section ->
                            item(key = "sp_h_$sectionIndex") { SectionHeader(section.title ?: "For you") }
                            item(key = "sp_r_$sectionIndex") {
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 12.dp),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                                ) {
                                    items(section.items, key = { it.uri }) { card ->
                                        SpotifyCard(card, onOpenSpotifyItem, image)
                                    }
                                }
                            }
                        }
                        if (heroTrack != null) {
                            item(key = "hero") {
                                HeroCard(
                                    title = heroTrack.title,
                                    subtitle = heroTrack.subtitle,
                                    thumbnailUrl = heroTrack.thumbnailUrl,
                                    isPlaying = heroTrack.videoId == nowPlayingId,
                                    onPlayPause = { onPlay(recentlyPlayed, heroIndex) },
                                    image = image,
                                )
                            }
                        }
                        if (recentlyPlayed.isNotEmpty()) {
                            item(key = "recently_played_header") {
                                SectionHeader("Recently played")
                            }
                            item(key = "recently_played_row") {
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 12.dp),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                                ) {
                                    itemsIndexed(recentlyPlayed, key = { _, hit -> hit.videoId }) { index, hit ->
                                        GridItem(
                                            title = hit.title,
                                            subtitle = hit.subtitle,
                                            isActive = hit.videoId == nowPlayingId,
                                            isBusy = hit.videoId == busyId,
                                            onClick = { onPlay(recentlyPlayed, index) },
                                            image = image,
                                            thumbnailUrl = hit.thumbnailUrl,
                                        )
                                    }
                                }
                            }
                        }

                        rows.forEachIndexed { rowIndex, row ->
                            item(key = "header_$rowIndex") {
                                SectionHeader(row.title)
                            }
                            item(key = "row_$rowIndex") {
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 12.dp),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                                ) {
                                    itemsIndexed(row.items, key = { _, hit -> hit.videoId }) { index, hit ->
                                        GridItem(
                                            title = hit.title,
                                            subtitle = hit.subtitle,
                                            isActive = hit.videoId == nowPlayingId,
                                            isBusy = hit.videoId == busyId,
                                            onClick = { onPlay(row.items, index) },
                                            image = image,
                                            thumbnailUrl = hit.thumbnailUrl,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (!hideYoutubeHome && rows.isNotEmpty()) {
                LargeFloatingActionButton(
                    onClick = onShuffle,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(24.dp),
                ) {
                    Icon(Icons.Default.Shuffle, contentDescription = "Shuffle")
                }
            }
        }
    }
}
