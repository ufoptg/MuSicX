/**
 * MuSicX Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package dev.musicx.desktop.di

import dev.musicx.desktop.state.playback.PlayerState
import dev.musicx.desktop.storage.DownloadManager
import dev.musicx.desktop.storage.SongCacheManager
import dev.musicx.desktop.storage.desktopSongOfflineAvailability
import dev.musicx.desktop.youtube.DesktopPageBoundTokenRuntime
import com.metrolist.innertubex.extraction.TokenProvider
import dev.musicx.shared.db.MusicDatabase
import dev.musicx.shared.db.OfflineSongAvailability
import dev.musicx.shared.db.daos.DatabaseDao
import dev.musicx.shared.db.getDatabaseBuilder
import dev.musicx.shared.youtube.playback.DesktopTokenProvider
import dev.musicx.shared.youtube.playback.LocalAudioSource
import org.koin.dsl.module

val composeStateModule =
    module {
        single<MusicDatabase> { getDatabaseBuilder().build() }
        single<LocalAudioSource> {
            LocalAudioSource { id, song ->
                DownloadManager.getLocalPlaybackResource(id, song) ?: SongCacheManager.getCachedPlaybackResource(id, song)
            }
        }
        single<DatabaseDao> { get<MusicDatabase>().dao() }
        single<OfflineSongAvailability> { desktopSongOfflineAvailability(get()) }
        single { DesktopPageBoundTokenRuntime() }
        single<TokenProvider> {
            DesktopTokenProvider(get<DesktopPageBoundTokenRuntime>()) {
                val queue = PlayerState.queue.snapshots.value
                queue.displayedItems
                    .asSequence()
                    .drop(queue.currentIndex + 1)
                    .take(2)
                    .map { it.id }
                    .toList()
            }
        }
    }
