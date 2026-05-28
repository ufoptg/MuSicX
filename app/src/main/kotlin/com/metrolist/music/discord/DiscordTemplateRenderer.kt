package com.metrolist.music.discord

import com.metrolist.music.db.entities.Song

object DiscordTemplateRenderer {
    fun render(template: String, song: Song): String {
        var result = template
            .replace("{song.name}", song.song.title)
            .replace("{song.id}", song.song.id)
            .replace("{artist.name}", song.artists.joinToString { it.name }.ifEmpty { "Unknown Artist" })
            .replace("{album.name}", song.album?.title ?: "Unknown Album")
        return result
    }

    fun render(
        template: String,
        title: String,
        artist: String,
        album: String?,
        songId: String = "",
    ): String {
        var result = template
            .replace("{song.name}", title)
            .replace("{song.id}", songId)
            .replace("{artist.name}", artist)
            .replace("{album.name}", album ?: "Unknown Album")
        return result
    }

    val PLACEHOLDERS = listOf(
        "{song.name}",
        "{artist.name}",
        "{album.name}",
        "{song.id}",
    )
}
