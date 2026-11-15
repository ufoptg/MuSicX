package dev.musicx.shared.api

class PlaylistNotFoundException(
    val playlistId: String,
) : Exception()
