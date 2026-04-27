package com.metrolist.innertube.models.response

import kotlinx.serialization.Serializable

@Serializable
data class GetMultiSelectCommandResponse(
    val multiSelectCommand: MultiSelectCommand? = null,
) {
    @Serializable
    data class MultiSelectCommand(
        val addToPlaylistEndpoint: AddToPlaylistEndpoint? = null,
    ) {
        @Serializable
        data class AddToPlaylistEndpoint(
            val videoIds: List<String> = emptyList(),
        )
    }
}
