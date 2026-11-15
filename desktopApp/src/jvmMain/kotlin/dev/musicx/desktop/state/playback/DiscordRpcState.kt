/*
 * MuSicX Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package dev.musicx.desktop.state.playback

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.musicx.desktop.state.content.ArtistState
import dev.musicx.shared.discord.DiscordActivityBuilder
import dev.musicx.shared.discord.DiscordDefaults
import dev.musicx.shared.discord.DiscordRpcManager
import dev.musicx.shared.discord.PresenceStatus
import dev.musicx.shared.discord.discordPlaybackTimestamps
import dev.musicx.shared.discord.resolveDiscordButtonLabel
import dev.musicx.shared.generated.resources.Res
import dev.musicx.shared.generated.resources.discord_button1_text_default
import dev.musicx.shared.generated.resources.discord_button2_text_default
import dev.musicx.shared.generated.resources.discord_unknown_album
import dev.musicx.shared.generated.resources.unknown_artist
import dev.musicx.shared.model.ArtistSource
import dev.musicx.shared.model.SongItem
import dev.musicx.shared.settings.Settings
import dev.musicx.shared.settings.displayName
import dev.musicx.shared.state.GlobalYouTubeRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.getString
import java.util.LinkedHashMap
import java.util.Locale

object DiscordRpcState {
    var discordRpcEnabled by mutableStateOf(Settings.shared.enableDiscordIntegration)
    var discordRpcShowArtistIcon by mutableStateOf(Settings.shared.discordRpcShowArtistIcon)

    var cachedArtistIcon by mutableStateOf<String?>(null)
    val artistIconCache =
        object : LinkedHashMap<String, String>(16, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>): Boolean = size > 30
        }

    private var artistIconJob: Job? = null

    internal fun onTrackChanged(
        song: SongItem?,
        scope: CoroutineScope,
    ) {
        artistIconJob?.cancel()
        cachedArtistIcon = null
        if (song == null || !discordRpcEnabled || !discordRpcShowArtistIcon || DiscordRpcManager.accessTokenFlow.value == null) return
        val artist = song.artists.firstOrNull() ?: return
        artistIconJob =
            scope.launch {
                val artistId = artist.id
                val cacheKey = artistId ?: artist.name
                val icon =
                    artistIconCache[cacheKey] ?: when (ArtistState.artistIconSource) {
                        ArtistSource.YOUTUBE -> {
                            artist.thumbnail ?: artistId?.takeIf(String::isNotEmpty)?.let {
                                GlobalYouTubeRepository.instance.fetchArtistThumbnail(it)
                            }
                        }

                        ArtistSource.ITUNES -> {
                            ArtistState.fetchArtistBannerOnDemand(
                                artistId ?: artist.name,
                                artist.name,
                                ArtistSource.ITUNES,
                            )
                        }

                        ArtistSource.LASTFM -> {
                            ArtistState.fetchLastFmIcon(artist.name)
                        }

                        ArtistSource.CUSTOM -> {
                            ArtistState.getCustomArtistBanner(artistId ?: artist.name)
                                ?: ArtistState.fetchLastFmIcon(artist.name)
                        }
                    }
                if (PlayerState.currentTrack?.id != song.id || DiscordRpcManager.accessTokenFlow.value == null) return@launch
                cachedArtistIcon = (icon ?: artist.thumbnail)?.substringBefore("=w")
                cachedArtistIcon?.let { artistIconCache[cacheKey] = it }
                updateDiscordRpc()
            }
    }

    private class Labels(
        val locale: Locale,
        val unknownArtist: String,
        val unknownAlbum: String,
        val button1: String,
        val button2: String,
    )

    private var cachedLabels: Labels? = null

    private fun labels(): Labels {
        val locale = Locale.getDefault()
        cachedLabels?.takeIf { it.locale == locale }?.let { return it }
        return runBlocking {
            Labels(
                locale,
                getString(Res.string.unknown_artist),
                getString(Res.string.discord_unknown_album),
                getString(Res.string.discord_button1_text_default),
                getString(Res.string.discord_button2_text_default),
            )
        }.also { cachedLabels = it }
    }

    internal fun updateDiscordRpc() {
        if (!PlayerState.externalReportingEnabled || !discordRpcEnabled) {
            DiscordRpcManager.clear()
            return
        }

        val song =
            PlayerState.currentTrack ?: run {
                DiscordRpcManager.clear()
                return
            }
        val isPlaying = PlayerState.isPlaying
        val labels = labels()

        val timestamps =
            discordPlaybackTimestamps(
                now = System.currentTimeMillis(),
                positionMs = PlayerState.player.currentPlaybackPositionMs(),
                durationMs = PlayerState.player.duration.value,
                speed = PlayerState.player.playbackSpeed(),
                playing = isPlaying,
            )

        val artistName = song.artists.joinToString { it.displayName }.ifEmpty { labels.unknownArtist }
        val songTitle = song.title

        val settings = Settings.shared
        val activity =
            DiscordActivityBuilder.build(
                song = song,
                artistName = artistName,
                albumName = song.album?.name,
                artistThumbnail = (cachedArtistIcon ?: song.artists.firstOrNull()?.thumbnail).takeIf { settings.discordRpcShowArtistIcon },
                songTitle = songTitle,
                startTimestamp = timestamps.start,
                endTimestamp = timestamps.end,
                advancedMode = settings.discordAdvancedMode,
                activityType = settings.discordActivityType.toIntOrNull() ?: 2,
                activityName = settings.discordActivityName,
                stateTemplate = settings.discordStateTemplate,
                detailsTemplate = settings.discordDetailsTemplate,
                btn1Enabled = settings.discordButton1Enabled,
                btn1Label =
                    resolveDiscordButtonLabel(
                        settings.discordButton1Text,
                        DiscordDefaults.BUTTON1_LABEL,
                        labels.button1,
                    ),
                btn1Url = settings.discordButton1Url,
                btn2Enabled = settings.discordButton2Enabled,
                btn2Label =
                    resolveDiscordButtonLabel(
                        settings.discordButton2Text,
                        DiscordDefaults.BUTTON2_LABEL,
                        labels.button2,
                    ),
                btn2Url = settings.discordButton2Url,
                basicBtn1Label = labels.button1,
                basicBtn2Label = labels.button2,
                unknownAlbum = labels.unknownAlbum,
            )

        DiscordRpcManager.setActivity(
            activity = activity,
            songId = song.id,
            isPlaying = isPlaying,
            status = PresenceStatus.Online,
        )
    }
}
