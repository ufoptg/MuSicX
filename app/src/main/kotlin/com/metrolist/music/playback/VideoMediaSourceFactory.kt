/**
 * MuSicX Project (C) 2026
 * Credits to Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.playback

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.drm.DrmSessionManagerProvider
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy

/**
 * Wraps the app's media source factory and upgrades video-mode items into a merged
 * audio+video [MediaSource].
 *
 * YouTube's progressive video formats are video-only, so playing just [VideoPlaybackTarget.mediaUri]
 * would render a silent picture. The audio track is resolved through the normal app pipeline
 * (cache, normalization, stream headers) while the video track is streamed directly.
 */
internal class VideoMediaSourceFactory(
    private val delegate: MediaSource.Factory,
    private val videoTargets: Map<String, VideoPlaybackTarget>,
    private val context: Context,
) : MediaSource.Factory {
    override fun setDrmSessionManagerProvider(provider: DrmSessionManagerProvider) =
        apply { delegate.setDrmSessionManagerProvider(provider) }

    override fun setLoadErrorHandlingPolicy(policy: LoadErrorHandlingPolicy) =
        apply { delegate.setLoadErrorHandlingPolicy(policy) }

    override fun getSupportedTypes(): IntArray = delegate.supportedTypes

    override fun createMediaSource(mediaItem: MediaItem): MediaSource {
        val cacheKey = mediaItem.localConfiguration?.customCacheKey
        val videoId =
            cacheKey
                ?.takeIf { it.startsWith(VideoPlaybackTarget.CACHE_KEY_PREFIX) }
                ?.removePrefix(VideoPlaybackTarget.CACHE_KEY_PREFIX)
        val target = videoId?.let(videoTargets::get)
        val videoUri = target?.mediaUri

        // Always resolve the audio item through the normal pipeline (audio cache + normalization).
        val audioItem =
            mediaItem
                .buildUpon()
                .setCustomCacheKey(mediaItem.mediaId)
                .build()
        val audioSource = delegate.createMediaSource(audioItem)

        if (target == null || videoUri.isNullOrBlank()) return audioSource

        val httpFactory =
            DefaultHttpDataSource.Factory()
                .setDefaultRequestProperties(target.streamHeaders)
        val videoSource =
            ProgressiveMediaSource
                .Factory(DefaultDataSource.Factory(context, httpFactory))
                .createMediaSource(MediaItem.fromUri(videoUri))
        return MergingMediaSource(true, true, audioSource, videoSource)
    }
}
