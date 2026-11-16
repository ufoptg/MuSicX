package com.metrolist.shared.spotifymatch

import meow.nyx.spotlib.TrackItem
import java.util.concurrent.ConcurrentHashMap

object SpotifyMetadataRegistry {
    private val byYoutubeId = ConcurrentHashMap<String, TrackItem>()

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