package dev.musicx.shared.viewmodels

import androidx.compose.runtime.snapshots.Snapshot
import dev.musicx.shared.auth.AuthManager
import dev.musicx.shared.db.getDesktopDatabaseBuilder
import dev.musicx.shared.model.ArtistItem
import dev.musicx.shared.model.ArtistTiny
import dev.musicx.shared.model.HomeSection
import dev.musicx.shared.model.SongItem
import dev.musicx.shared.settings.HiddenHomeItems
import dev.musicx.shared.settings.HiddenLibraryItem
import dev.musicx.shared.settings.Settings
import dev.musicx.shared.state.GlobalYouTube
import dev.musicx.shared.youtube.playback.PlaybackNetworkState
import dev.musicx.shared.youtube.playback.PlaybackNetworkStateStore
import com.russhwolf.settings.MapSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

class HomeHiddenItemsTest {
    @Test
    fun cachedHomeReactsToHideUndoAndSettingsRestoreWithoutLosingItsOriginalItems() =
        runBlocking<Unit> {
            val previousNetwork = PlaybackNetworkStateStore.current
            val previousSession = GlobalYouTube.innerTube.sessionSnapshot()
            Settings.replaceSharedForTest(Settings(MapSettings()))
            AuthManager().restorePersistedSession()
            PlaybackNetworkStateStore.publish(PlaybackNetworkState("home-hidden-test", connected = false))
            val path = Files.createTempFile("home-hidden", ".db")
            Files.delete(path)
            val database = getDesktopDatabaseBuilder(path.toFile()).build()
            val job = Job()
            try {
                val artist = ArtistItem("UChidden", "Hidden artist", null)
                val hiddenSong = SongItem("hidden", "Hidden track", null, listOf(ArtistTiny(artist.id, artist.title)), null)
                val visibleSong = hiddenSong.copy(id = "visible", artists = listOf(ArtistTiny("UCvisible", "Visible artist")))
                val shelf = HomeSection("Recommendations", listOf(hiddenSong, visibleSong), continuation = "next")
                val locale = GlobalYouTube.innerTube.locale
                val cached =
                    HomeViewModel.HomeUiState(
                        contentLocaleSignature = "${locale.hl}:${locale.gl}",
                        sessionKey = GlobalYouTube.innerTube.sessionSnapshot().homeCacheSessionKey(),
                        data = listOf(shelf),
                        sections = listOf(shelf),
                        quickPicks = listOf(hiddenSong, visibleSong),
                    )
                Settings.shared.lastHomePageJson = homeCacheJson.encodeToString(cached)
                HiddenHomeItems.hide(artist)
                val backup = Settings.shared.exportBackupSettings()
                val model = HomeViewModel(database, CoroutineScope(coroutineContext + job), likedSongsName = "Liked songs")

                suspend fun awaitItems(ids: List<String>): HomeViewModel.HomeUiState {
                    Snapshot.sendApplyNotifications()
                    return withTimeout(30_000) {
                        model.uiState.first { state ->
                            !state.isRestoringCache && state.sections
                                .firstOrNull()
                                ?.items
                                ?.map { it.id } == ids
                        }
                    }
                }

                assertEquals(listOf(visibleSong), awaitItems(listOf("visible")).quickPicks)
                HiddenHomeItems.restore(HiddenLibraryItem.from(artist).key)
                assertEquals(listOf(shelf), awaitItems(listOf("hidden", "visible")).data)
                Settings.shared.importBackupSettings(backup)
                assertEquals(listOf(shelf.copy(items = listOf(visibleSong))), awaitItems(listOf("visible")).sections)
                HiddenHomeItems.restore(HiddenLibraryItem.from(artist).key)
                assertEquals(listOf(shelf), awaitItems(listOf("hidden", "visible")).sections)
            } finally {
                job.cancelAndJoin()
                database.close()
                Files.deleteIfExists(path)
                PlaybackNetworkStateStore.publish(previousNetwork)
                GlobalYouTube.innerTube.replaceSession(
                    previousSession.cookie,
                    previousSession.visitorData,
                    previousSession.dataSyncId,
                    previousSession.authUser,
                    previousSession.useLoginForBrowse,
                )
                Settings.replaceSharedForTest(null)
            }
        }
}
