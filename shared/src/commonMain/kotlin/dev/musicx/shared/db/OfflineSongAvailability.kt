package dev.musicx.shared.db

fun interface OfflineSongAvailability {
    suspend fun songIds(): Set<String>
}
