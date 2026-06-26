package com.metrolist.listenbrainz.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TokenValidation(
    val code: Int,
    val message: String,
    val valid: Boolean,
    @SerialName("user_name")
    val userName: String? = null,
)

@Serializable
data class SubmitListensRequest(
    @SerialName("listen_type")
    val listenType: String,
    val payload: List<ListenPayload>,
)

@Serializable
data class ListenPayload(
    @SerialName("listened_at")
    val listenedAt: Long? = null,
    @SerialName("track_metadata")
    val trackMetadata: TrackMetadata,
)

@Serializable
data class TrackMetadata(
    @SerialName("artist_name")
    val artistName: String,
    @SerialName("track_name")
    val trackName: String,
    @SerialName("release_name")
    val releaseName: String? = null,
    @SerialName("additional_info")
    val additionalInfo: AdditionalInfo? = null,
)

@Serializable
data class AdditionalInfo(
    @SerialName("duration_ms")
    val durationMs: Int? = null,
    @SerialName("media_player")
    val mediaPlayer: String = "Metrolist",
    @SerialName("submission_client")
    val submissionClient: String = "Metrolist",
    @SerialName("submission_client_version")
    val submissionClientVersion: String = "13.6.0",
    @SerialName("music_service")
    val musicService: String = "music.youtube.com",
    @SerialName("origin_url")
    val originUrl: String? = null,
)

@Serializable
data class SubmitListensResponse(
    @SerialName("recording_msid")
    val recordingMsid: String? = null,
)

@Serializable
data class MetadataLookupResponse(
    @SerialName("recording_mbid")
    val recordingMbid: String? = null,
)

@Serializable
data class RecordingFeedbackRequest(
    @SerialName("recording_mbid")
    val recordingMbid: String? = null,
    @SerialName("recording_msid")
    val recordingMsid: String? = null,
    val score: Int,
)
