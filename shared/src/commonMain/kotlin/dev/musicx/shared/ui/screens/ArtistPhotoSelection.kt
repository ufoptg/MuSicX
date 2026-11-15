package dev.musicx.shared.ui.screens

import dev.musicx.shared.api.lastfm.LastFM
import dev.musicx.shared.viewmodels.ArtistPhoto

internal fun artistGalleryPhotos(
    artistName: String,
    thumbnail: String?,
    photos: List<ArtistPhoto>,
): List<ArtistPhoto> =
    photos
        .ifEmpty { listOfNotNull(thumbnail?.takeIf(String::isNotBlank)?.let(::ArtistPhoto)) }
        .map { it.copy(pageUrl = it.pageUrl ?: LastFM.photoPageUrl(artistName, it.url)) }

internal fun artistPhotoIndex(
    photos: List<ArtistPhoto>,
    selectedUrl: String?,
    previousIndex: Int,
): Int? {
    if (photos.isEmpty()) return null
    return photos.indexOfFirst { it.url == selectedUrl }.takeIf { it >= 0 }
        ?: previousIndex.coerceIn(photos.indices)
}
