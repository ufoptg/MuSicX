package dev.musicx.desktop.ui.screens

import dev.musicx.desktop.history.AlbumStat
import dev.musicx.shared.model.AlbumItem
import dev.musicx.shared.ui.statistics.matchingStatisticsAlbum

internal fun statisticsArtistName(id: String): String = id.removePrefix("artist:").substringAfter(':')

internal fun List<AlbumItem>.matchingStatisticsAlbum(album: AlbumStat): AlbumItem? = matchingStatisticsAlbum(album.name, album.artists)
