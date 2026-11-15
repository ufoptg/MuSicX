package dev.musicx.shared.viewmodels

import dev.musicx.shared.auth.AuthManager
import dev.musicx.shared.db.entities.AlbumEntity
import dev.musicx.shared.db.getDesktopDatabaseBuilder
import dev.musicx.shared.model.AlbumItem
import dev.musicx.shared.model.HomeSection
import dev.musicx.shared.model.PodcastItem
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
import kotlinx.datetime.LocalDateTime
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class HomeLibraryAlbumsTest {
    @Test
    fun restoredHomeObservesTheLibraryAlbumBookmarksWithoutNetworkRequests() =
        runBlocking<Unit> {
            val previousNetwork = PlaybackNetworkStateStore.current
            val previousSession = GlobalYouTube.innerTube.sessionSnapshot()
            Settings.replaceSharedForTest(Settings(MapSettings()))
            AuthManager().restorePersistedSession()
            PlaybackNetworkStateStore.publish(PlaybackNetworkState("home-library-test", connected = false))
            val path = Files.createTempFile("home-library", ".db")
            Files.delete(path)
            val database = getDesktopDatabaseBuilder(path.toFile()).build()
            val job = Job()
            try {
                val bookmark = LocalDateTime(2026, 1, 1, 0, 0)
                val saved = AlbumEntity("saved", title = "Saved album", songCount = 0, duration = 0, bookmarkedAt = bookmark)
                val unsaved = AlbumEntity("unsaved", title = "Unsaved album", songCount = 0, duration = 0)
                database.dao().upsert(saved)
                database.dao().upsert(unsaved)
                val albums = listOf(saved, unsaved).map { AlbumItem(it.id, it.title, null, emptyList()) }
                val library = HomeSection("From your library", albums, browseId = "FEmusic_library_landing")
                val shows = HomeSection("Your shows", listOf(PodcastItem("MPSPshow", "Show", null)), browseId = "FEmusic_library_podcasts")
                val locale = GlobalYouTube.innerTube.locale
                val cached =
                    HomeViewModel.HomeUiState(
                        contentLocaleSignature = "${locale.hl}:${locale.gl}",
                        sessionKey = GlobalYouTube.innerTube.sessionSnapshot().homeCacheSessionKey(),
                        data = listOf(library, shows),
                        sections = listOf(library, shows),
                    )
                Settings.shared.lastHomePageJson = homeCacheJson.encodeToString(cached)
                val model = HomeViewModel(database, CoroutineScope(coroutineContext + job), likedSongsName = "Liked songs")

                suspend fun awaitLibraryAlbums(ids: List<String>): HomeViewModel.HomeUiState =
                    withTimeout(30_000) {
                        model.uiState.first { state ->
                            !state.isRestoringCache && state.sections.any { it == shows } &&
                                state.sections
                                    .firstOrNull { it.browseId == library.browseId }
                                    ?.items
                                    .orEmpty()
                                    .map { it.id } == ids
                        }
                    }

                assertEquals(listOf(library.copy(items = albums.take(1)), shows), awaitLibraryAlbums(listOf("saved")).sections)
                database.dao().upsert(unsaved.copy(bookmarkedAt = bookmark))
                assertEquals(listOf(library, shows), awaitLibraryAlbums(listOf("saved", "unsaved")).sections)
                database.dao().upsert(saved.copy(bookmarkedAt = null))
                assertEquals(listOf(library.copy(items = albums.drop(1)), shows), awaitLibraryAlbums(listOf("unsaved")).sections)
                database.dao().upsert(unsaved)
                val emptyLibrary = awaitLibraryAlbums(emptyList())
                assertEquals(listOf(shows), emptyLibrary.sections)
                assertEquals(cached.data, emptyLibrary.data)
                assertFalse(PlaybackNetworkStateStore.current.connected)
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
