package com.metrolist.listenbrainz

import com.metrolist.listenbrainz.models.AdditionalInfo
import com.metrolist.listenbrainz.models.ListenPayload
import com.metrolist.listenbrainz.models.MetadataLookupResponse
import com.metrolist.listenbrainz.models.RecordingFeedbackRequest
import com.metrolist.listenbrainz.models.SubmitListensRequest
import com.metrolist.listenbrainz.models.SubmitListensResponse
import com.metrolist.listenbrainz.models.TokenValidation
import com.metrolist.listenbrainz.models.TrackMetadata
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

object ListenBrainz {
    var userToken: String? = null
    var clientVersion: String = "13.6.0"

    private val json = Json {
        isLenient = true
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
    }

    private val client by lazy {
        HttpClient(OkHttp) {
            install(ContentNegotiation) {
                json(json)
            }
            defaultRequest {
                url("https://api.listenbrainz.org/")
                header(HttpHeaders.UserAgent, "Metrolist (https://github.com/MetrolistGroup/Metrolist)")
            }
            expectSuccess = false
        }
    }

    /**
     * Constructs the authorization header value with the given or configured user token.
     * Throws an [IllegalArgumentException] if the token is missing or blank.
     *
     * @param token The ListenBrainz user token.
     * @return The formatted authorization header value.
     */
    private fun tokenHeader(token: String = userToken.orEmpty()): String {
        if (token.isBlank()) {
            throw IllegalArgumentException("User token is missing or blank")
        }
        return "Token $token"
    }

    /**
     * Validates the provided ListenBrainz user token with the API.
     *
     * @param token The ListenBrainz token to validate.
     * @return A [Result] wrapping the [TokenValidation] response.
     */
    suspend fun validateToken(token: String) = runCatching {
        client.get("1/validate-token") {
            header(HttpHeaders.Authorization, tokenHeader(token))
        }.body<TokenValidation>()
    }

    /**
     * Submits a single scrobble (listened-to track) to the ListenBrainz API.
     *
     * @param artist The artist name.
     * @param track The track name.
     * @param timestamp The epoch timestamp in seconds when the song was listened to.
     * @param songId The optional YouTube Music video/song ID.
     * @param album The optional album name.
     * @param duration The optional track duration in seconds.
     * @return A [Result] representing the network response.
     */
    suspend fun scrobble(
        artist: String,
        track: String,
        timestamp: Long,
        songId: String? = null,
        album: String? = null,
        duration: Int? = null,
    ) = runCatching {
        if (userToken.isNullOrBlank()) {
            throw IllegalArgumentException("User token is missing or blank")
        }
        client.post("1/submit-listens") {
            header(HttpHeaders.Authorization, tokenHeader())
            contentType(ContentType.Application.Json)
            setBody(
                SubmitListensRequest(
                    listenType = "single",
                    payload = listOf(
                        ListenPayload(
                            listenedAt = timestamp,
                            trackMetadata = trackMetadata(
                                artist = artist,
                                track = track,
                                songId = songId,
                                album = album,
                                duration = duration,
                            ),
                        ),
                    ),
                ),
            )
        }
    }

    /**
     * Updates the "playing now" status on the ListenBrainz profile.
     *
     * @param artist The artist name.
     * @param track The track name.
     * @param songId The optional YouTube Music video/song ID.
     * @param album The optional album name.
     * @param duration The optional track duration in seconds.
     * @return A [Result] wrapping the [SubmitListensResponse].
     */
    suspend fun updateNowPlaying(
        artist: String,
        track: String,
        songId: String? = null,
        album: String? = null,
        duration: Int? = null,
    ) = runCatching {
        if (userToken.isNullOrBlank()) {
            throw IllegalArgumentException("User token is missing or blank")
        }
        client.post("1/submit-listens") {
            header(HttpHeaders.Authorization, tokenHeader())
            contentType(ContentType.Application.Json)
            parameter("return_msid", true)
            setBody(
                SubmitListensRequest(
                    listenType = "playing_now",
                    payload = listOf(
                        ListenPayload(
                            trackMetadata = trackMetadata(
                                artist = artist,
                                track = track,
                                songId = songId,
                                album = album,
                                duration = duration,
                            ),
                        ),
                    ),
                ),
            )
        }.body<SubmitListensResponse>()
    }

    /**
     * Looks up the MusicBrainz Recording MBID for the given metadata.
     *
     * @param artist The artist name.
     * @param track The track name.
     * @param album The optional release/album name.
     * @return A [Result] wrapping the [MetadataLookupResponse].
     */
    suspend fun lookupRecordingMbid(
        artist: String,
        track: String,
        album: String? = null,
    ) = runCatching {
        client.get("1/metadata/lookup/") {
            parameter("artist_name", artist)
            parameter("recording_name", track)
            album?.takeIf { it.isNotBlank() }?.let { parameter("release_name", it) }
        }.body<MetadataLookupResponse>()
    }

    /**
     * Sets or updates the feedback (like/love status) for a track on ListenBrainz.
     *
     * @param artist The artist name.
     * @param track The track name.
     * @param album The optional album name.
     * @param love True if the track is liked/loved, false if not.
     * @return A [Result] representing the network response.
     */
    suspend fun setLoveStatus(
        artist: String,
        track: String,
        album: String? = null,
        love: Boolean,
    ) = runCatching {
        if (userToken.isNullOrBlank()) {
            throw IllegalArgumentException("User token is missing or blank")
        }
        val recordingMbid = lookupRecordingMbid(artist, track, album)
            .getOrNull()
            ?.recordingMbid
            ?.takeIf { it.isNotBlank() }
            ?: return@runCatching

        client.post("1/feedback/recording-feedback") {
            header(HttpHeaders.Authorization, tokenHeader())
            contentType(ContentType.Application.Json)
            setBody(
                RecordingFeedbackRequest(
                    recordingMbid = recordingMbid,
                    score = if (love) 1 else 0,
                ),
            )
        }
    }

    /**
     * Prepares the track metadata object for submissions.
     *
     * @param artist The artist name.
     * @param track The track name.
     * @param songId The optional YouTube Music video/song ID.
     * @param album The optional album name.
     * @param duration The optional track duration in seconds.
     * @return The constructed [TrackMetadata] object.
     */
    private fun trackMetadata(
        artist: String,
        track: String,
        songId: String?,
        album: String?,
        duration: Int?,
    ) = TrackMetadata(
        artistName = artist,
        trackName = track,
        releaseName = album?.takeIf { it.isNotBlank() },
        additionalInfo = AdditionalInfo(
            durationMs = duration?.takeIf { it > 0 }?.times(1000),
            originUrl = songId?.takeIf { it.isNotBlank() }?.let { "https://music.youtube.com/watch?v=$it" },
            submissionClientVersion = clientVersion,
        ),
    )

    const val DEFAULT_SCROBBLE_DELAY_PERCENT = 0.5f
    const val DEFAULT_SCROBBLE_MIN_SONG_DURATION = 30
    const val DEFAULT_SCROBBLE_DELAY_SECONDS = 180
}
