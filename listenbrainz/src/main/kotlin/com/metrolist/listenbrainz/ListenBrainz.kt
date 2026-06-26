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

    private fun tokenHeader(token: String = userToken.orEmpty()) = "Token $token"

    suspend fun validateToken(token: String) = runCatching {
        client.get("1/validate-token") {
            header(HttpHeaders.Authorization, tokenHeader(token))
        }.body<TokenValidation>()
    }

    suspend fun scrobble(
        artist: String,
        track: String,
        timestamp: Long,
        songId: String? = null,
        album: String? = null,
        duration: Int? = null,
    ) = runCatching {
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

    suspend fun updateNowPlaying(
        artist: String,
        track: String,
        songId: String? = null,
        album: String? = null,
        duration: Int? = null,
    ) = runCatching {
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

    suspend fun setLoveStatus(
        artist: String,
        track: String,
        album: String? = null,
        love: Boolean,
    ) = runCatching {
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
