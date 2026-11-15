package dev.musicx.shared.model

import dev.musicx.shared.db.entities.PlaylistEntity
import dev.musicx.shared.generated.resources.Res
import dev.musicx.shared.generated.resources.cached_playlist
import dev.musicx.shared.generated.resources.episodes_for_later
import dev.musicx.shared.generated.resources.filter_downloaded
import dev.musicx.shared.generated.resources.history
import dev.musicx.shared.generated.resources.history_playlist_monthly
import dev.musicx.shared.generated.resources.history_playlist_weekly
import dev.musicx.shared.generated.resources.liked_songs
import dev.musicx.shared.generated.resources.my_top_100
import dev.musicx.shared.generated.resources.new_episodes
import dev.musicx.shared.generated.resources.uploaded_playlist_name
import org.jetbrains.compose.resources.StringResource

enum class BuiltinPlaylist(
    val id: String,
    val title: StringResource,
    val collectionType: String?,
    val icon: String,
) {
    LIKED("auto:liked", Res.string.liked_songs, "liked", "auto_playlist_icon_liked"),
    DOWNLOADED("auto:downloaded", Res.string.filter_downloaded, "downloaded", "auto_playlist_icon_downloaded"),
    TOP("auto:top100", Res.string.my_top_100, "top100", "auto_playlist_icon_top100"),
    CACHED("auto:cached", Res.string.cached_playlist, "cached", "auto_playlist_icon_cached"),
    UPLOADED("auto:uploaded", Res.string.uploaded_playlist_name, "uploaded", "auto_playlist_icon_uploaded"),
    NEW_EPISODES("auto:podcasts", Res.string.new_episodes, "podcasts", "auto_playlist_icon_podcasts"),
    SAVED_EPISODES("auto:SE", Res.string.episodes_for_later, "SE", "auto_playlist_icon_episodes"),
    HISTORY("auto:history", Res.string.history, "history", "auto_playlist_icon_history"),
    WEEKLY(PlaylistEntity.WEEKLY_MOST_PLAYLIST_ID, Res.string.history_playlist_weekly, null, "auto_playlist_icon_top100"),
    MONTHLY(PlaylistEntity.MONTHLY_MOST_PLAYLIST_ID, Res.string.history_playlist_monthly, null, "auto_playlist_icon_top100"),
    ;

    fun playlist(localizedTitle: String): PlaylistItem =
        PlaylistItem(id = id, title = localizedTitle, thumbnail = icon, isLocal = collectionType == null)

    fun resolve(
        playlist: PlaylistItem,
        localizedTitle: String,
    ): PlaylistItem =
        playlist.copy(
            title = localizedTitle,
            thumbnail = if (collectionType == null) playlist.thumbnail ?: icon else icon,
            isLocal = collectionType == null,
        )

    companion object {
        fun fromId(id: String): BuiltinPlaylist? =
            entries.firstOrNull { it.id == id || it.collectionType == id }
                ?: when {
                    (id == "LM" || id == "VLLM" || id.startsWith("LM:")) || id == PlaylistEntity.LIKED_PLAYLIST_ID -> LIKED
                    id == PlaylistEntity.DOWNLOADED_PLAYLIST_ID -> DOWNLOADED
                    id.removePrefix("VL") == "RDPN" -> NEW_EPISODES
                    id.removePrefix("VL") == "SE" -> SAVED_EPISODES
                    else -> null
                }
    }
}

fun speedDialItemId(id: String): String = BuiltinPlaylist.fromId(id)?.id ?: id
