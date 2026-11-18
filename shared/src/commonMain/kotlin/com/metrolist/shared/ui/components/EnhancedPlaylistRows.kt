package com.metrolist.shared.ui.components

import com.metrolist.shared.model.SongItem

const val RECOMMENDATION_INTERVAL = 3
const val MAX_ENHANCE_RECOMMENDATIONS = 200

sealed interface PlaylistRow {
    data class SongEntry(
        val song: SongItem,
        /** Index in the source [SongItem] list; this is the queue seek index. */
        val queueIndex: Int,
    ) : PlaylistRow

    data class Recommendation(
        val song: SongItem,
        /** Index in the recommendation list before interleaving. */
        val index: Int,
        /** Index in the resulting suggestion section. */
        val offset: Int,
    ) : PlaylistRow
}

/**
 * Builds the row sequence for enhancement mode: one recommendation after every
 * [RECOMMENDATION_INTERVAL] source songs, with any unused recommendations
 * appended at the end. Recommendation output is capped before interleaving.
 */
fun buildEnhancedPlaylistRows(
    songs: List<SongItem>,
    recommendations: List<SongItem>,
    enableEnhancement: Boolean,
): List<PlaylistRow> {
    if (!enableEnhancement) {
        return songs.mapIndexed { index, song ->
            PlaylistRow.SongEntry(song, index)
        }
    }
    val effectiveRecommendations = recommendations.take(MAX_ENHANCE_RECOMMENDATIONS)
    if (effectiveRecommendations.isEmpty()) {
        return songs.mapIndexed { index, song ->
            PlaylistRow.SongEntry(song, index)
        }
    }

    val rows = ArrayList<PlaylistRow>(songs.size + effectiveRecommendations.size)
    var recommendationIndex = 0
    songs.forEachIndexed { index, song ->
        rows += PlaylistRow.SongEntry(song, index)
        if ((index + 1) % RECOMMENDATION_INTERVAL == 0 && recommendationIndex < effectiveRecommendations.size) {
            rows +=
                PlaylistRow.Recommendation(
                    song = effectiveRecommendations[recommendationIndex],
                    index = recommendationIndex,
                    offset = songs.size + recommendationIndex,
                )
            recommendationIndex++
        }
    }
    while (recommendationIndex < effectiveRecommendations.size) {
        rows +=
            PlaylistRow.Recommendation(
                song = effectiveRecommendations[recommendationIndex],
                index = recommendationIndex,
                offset = songs.size + recommendationIndex,
            )
        recommendationIndex++
    }
    return rows
}
