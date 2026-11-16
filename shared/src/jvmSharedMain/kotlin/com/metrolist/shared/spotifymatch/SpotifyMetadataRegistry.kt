package com.metrolist.shared.spotifymatch

import com.metrolist.spotify.TrackItem
import java.util.concurrent.ConcurrentHashMap

object SpotifyMetadataRegistry {
    private val byYoutubeId = ConcurrentHashMap<TrackItem>()

    fun register(youtubeId: String, track: TrackItem) {
        byYoutubeId[youtubeId] = track
    }

    fun get(youtubeId: String): TrackItem? = byYoutubeId[youtubeId]

    fun invalidate(youtubeId: String) {
        byYoutubeId.remove(youtubeId)
    }

    fun clearAll() {
        byYoutubeId.clear()
    }
}