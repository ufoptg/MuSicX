package com.metrolist.shared.youtube.playback

import com.metrolist.innertubex.extraction.AudioQuality
import com.metrolist.innertubex.extraction.ContentHints
import com.metrolist.shared.model.SongItem
import com.metrolist.shared.playback.PlaybackFailureCategory
import com.metrolist.shared.qobuz.QobuzAudioProvider
import com.metrolist.shared.qobuz.QobuzMatchOverrides
import com.metrolist.shared.settings.Settings
import com.metrolist.shared.state.GlobalYouTube
import com.metrolist.shared.utils.Logger
import com.metrolist.shared.utils.systemCountryCode
import kotlinx.coroutines.CancellationException
import kotlin.time.Instant

class QobuzStreamResolver(
    private val defaultStreamResolver: DefaultStreamResolver,
    private val qobuzAudioProvider: QobuzAudioProvider = QobuzAudioProvider,
    private val isrcResolver: suspend (String) -> String? = { null },
) : StreamResolver {

    companion object {
        private const val TAG = "QobuzStreamResolver"
        private const val CLIENT_NAME = "Qobuz"
    }

    override suspend fun resolve(
        videoId: String,
        hints: ContentHints,
        audioQuality: AudioQuality,
        sourceSong: SongItem?,
    ): PlaybackResource {
        val qobuzResource = resolveOrNull(videoId, audioQuality, sourceSong, refresh = false)
        return qobuzResource ?: defaultStreamResolver.resolve(videoId, hints, audioQuality, sourceSong)
    }

    override suspend fun refresh(
        videoId: String,
        hints: ContentHints,
        audioQuality: AudioQuality,
        sourceSong: SongItem?,
    ): PlaybackResource {
        val qobuzResource = resolveOrNull(videoId, audioQuality, sourceSong, refresh = true)
        return qobuzResource ?: defaultStreamResolver.refresh(videoId, hints, audioQuality, sourceSong)
    }

    override suspend fun resolveRemote(
        videoId: String,
        hints: ContentHints,
        audioQuality: AudioQuality,
        sourceSong: SongItem?,
        refresh: Boolean,
        excludedProfiles: Set<String>,
    ): PlaybackResource =
        if (refresh) refresh(videoId, hints, audioQuality, sourceSong)
        else resolve(videoId, hints, audioQuality, sourceSong)

    override suspend fun prewarm() {
        defaultStreamResolver.prewarm()
    }

    override suspend fun clearPlaybackFailures(videoId: String) {
        defaultStreamResolver.clearPlaybackFailures(videoId)
    }

    override suspend fun invalidateNetworkScope(state: PlaybackNetworkState) {
        defaultStreamResolver.invalidateNetworkScope(state)
    }

    override suspend fun resetExtractionBackoff() {
        defaultStreamResolver.resetExtractionBackoff()
    }

    override suspend fun invalidateAttestation() {
        defaultStreamResolver.invalidateAttestation()
    }

    override suspend fun refreshPlayerConfigAfterStreamRejection(): Boolean =
        defaultStreamResolver.refreshPlayerConfigAfterStreamRejection()

    override suspend fun close() {
        defaultStreamResolver.close()
    }

    override suspend fun reportPlaybackFailure(
        videoId: String,
        clientName: String?,
        responseCode: Int?,
        profileId: String?,
        hints: ContentHints,
        category: PlaybackFailureCategory,
    ) {
        defaultStreamResolver.reportPlaybackFailure(
            videoId = videoId,
            clientName = clientName,
            responseCode = responseCode,
            profileId = profileId,
            hints = hints,
            category = category,
        )
    }

    override suspend fun reportPlaybackSuccess(
        profileId: String?,
        hints: ContentHints,
    ) {
        defaultStreamResolver.reportPlaybackSuccess(profileId = profileId, hints = hints)
    }

    /**
     * Attempts Qobuz resolution for [sourceSong]/[videoId]. Returns null on any
     * failure so the caller falls back to the YouTube backend. Never throws.
     */
    private suspend fun resolveOrNull(
        videoId: String,
        audioQuality: AudioQuality,
        sourceSong: SongItem?,
        refresh: Boolean,
    ): PlaybackResource? {
        val query =
            try {
                buildQuery(videoId, audioQuality, sourceSong)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Logger.w(
                    TAG,
                    "Qobuz query build failed for $videoId, falling back to YouTube: ${error.message ?: error::class.simpleName}",
                    error,
                )
                return null
            } ?: return null

        return try {
            val resolved =
                if (refresh) qobuzAudioProvider.refresh(query)
                else qobuzAudioProvider.resolve(query)
            resolved?.let { it.toPlaybackResource(videoId, sourceSong?.duration) }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Logger.w(
                TAG,
                "Qobuz resolution failed for $videoId, falling back to YouTube: ${error.message ?: error::class.simpleName}",
                error,
            )
            null
        }
    }

    private suspend fun buildQuery(
        videoId: String,
        audioQuality: AudioQuality,
        sourceSong: SongItem?,
    ): QobuzAudioProvider.Query {
        val title = sourceSong?.title?.ifBlank { videoId } ?: videoId
        val artists = sourceSong?.artists?.map { it.name }?.filter { it.isNotBlank() }.orEmpty()
        val album = sourceSong?.album?.name?.takeIf { it.isNotBlank() }
        val durationMs = sourceSong?.duration?.takeIf { it > 0L }

        // Manual override short-circuits fuzzy matching; otherwise fall back to metadata matching.
        val overrideTrackId =
            QobuzMatchOverrides.overrides[videoId]?.qobuzTrackId
                ?: sourceSong?.id?.let { mediaId ->
                    QobuzMatchOverrides.overrides[mediaId]?.qobuzTrackId
                }
        val mediaId = overrideTrackId ?: sourceSong?.id ?: videoId

        // Map AudioQuality → Qobuz quality code (27→7→6→5)
        val qualityCode = when (audioQuality) {
            AudioQuality.MP4 -> QobuzAudioProvider.QobuzAudioQuality.AAC_320
            AudioQuality.LOW -> QobuzAudioProvider.QobuzAudioQuality.CD_QUALITY
            AudioQuality.HIGH -> QobuzAudioProvider.QobuzAudioQuality.HI_RES_LOSSLESS
            AudioQuality.AUTO -> QobuzAudioProvider.QobuzAudioQuality.HI_RES_LOSSLESS
            else -> QobuzAudioProvider.QobuzAudioQuality.HI_RES_LOSSLESS
        }

        // ISRC comes from the injected resolver (Spotify registry on JVM) when available;
        // otherwise the provider falls back to title/artist/album/duration matching.
        val isrc = sourceSong?.id?.let { runCatching { isrcResolver(it) }.getOrNull() }?.takeIf { it.isNotBlank() }

        val countryCode =
            Settings.shared.contentCountry
                .takeIf { it != GlobalYouTube.SYSTEM }
                ?: systemCountryCode()

        return QobuzAudioProvider.Query(
            mediaId = mediaId,
            title = title,
            artists = artists,
            album = album,
            isrc = isrc,
            durationMs = durationMs,
            countryCode = countryCode,
            backend = QobuzAudioProvider.ResolverBackend.SQUID,
            qualityCode = QobuzAudioProvider.qualityCodeFor(qualityCode),
        )
    }

    private fun QobuzAudioProvider.Resolved.toPlaybackResource(
        videoId: String,
        sourceDurationMs: Long?,
    ): PlaybackResource {
        val durationSeconds = sourceDurationMs?.takeIf { it > 0L }?.div(1000L)
        return PlaybackResource(
            videoId = videoId,
            url = mediaUri,
            headers = emptyMap(),
            loudnessDb = null,
            expiresAt = Instant.fromEpochMilliseconds(expiresAtMs),
            bitrate = bitrate,
            sampleRate = sampleRate,
            mimeType = mimeType,
            codecs = codecs,
            clientName = CLIENT_NAME,
            profileId = trackId,
            durationSeconds = durationSeconds,
            requireBoundedRange = false,
            isLive = false,
        )
    }
}