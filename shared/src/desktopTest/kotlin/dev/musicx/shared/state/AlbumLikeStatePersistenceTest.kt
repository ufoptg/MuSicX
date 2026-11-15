package dev.musicx.shared.state

import com.metrolist.innertubex.InnerTube
import dev.musicx.shared.api.YouTubeRepository
import dev.musicx.shared.db.getDesktopDatabaseBuilder
import dev.musicx.shared.model.AlbumItem
import dev.musicx.shared.model.ArtistTiny
import dev.musicx.shared.model.SongItem
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class AlbumLikeStatePersistenceTest {
    @Test
    fun favoritesPersistLoadedMetadataAndRepairPreviouslyEmptyAlbums() =
        runBlocking {
            val path = Files.createTempFile("metrolist-album-favorite", ".db")
            Files.deleteIfExists(path)
            val database = getDesktopDatabaseBuilder(path.toFile()).build()
            val client = HttpClient(MockEngine { error("Signed-out album saves must stay local") })
            val repository = YouTubeRepository(InnerTube(client))
            try {
                val album =
                    AlbumItem(
                        "album",
                        "Album",
                        "cover.jpg",
                        listOf(ArtistTiny("artist", "Artist"), ArtistTiny(null, "Guest")),
                        "2026",
                        "playlist",
                        isExplicit = true,
                    )
                val songs =
                    listOf(30L, 45L, null, -1L).mapIndexed { index, duration ->
                        SongItem("song-$index", "Song", null, emptyList(), null, duration = duration)
                    }
                val dao = database.dao()
                assertNull(dao.album(album.id).first())
                database.toggleAlbumLiked(album, songs, repository)
                val saved = assertNotNull(dao.album(album.id).first()?.album)
                assertEquals(album.title, saved.title)
                assertEquals(album.thumbnail, saved.thumbnailUrl)
                assertEquals(album.playlistId, saved.playlistId)
                assertEquals(2026, saved.year)
                assertEquals(true, saved.explicit)
                assertEquals(4, saved.songCount)
                assertEquals(75, saved.duration)
                assertNotNull(saved.bookmarkedAt)
                assertEquals(
                    setOf("Artist", "Guest"),
                    dao
                        .album(album.id)
                        .first()!!
                        .artists
                        .map { it.name }
                        .toSet(),
                )

                database.toggleAlbumLiked(album, emptyList(), repository)
                assertEquals(saved.copy(bookmarkedAt = null), dao.album(album.id).first()?.album)

                dao.upsert(saved.copy(songCount = 0, duration = 0, bookmarkedAt = null))
                database.toggleAlbumLiked(album, songs, repository)
                val repaired = assertNotNull(dao.album(album.id).first()?.album)
                assertEquals(4, repaired.songCount)
                assertEquals(75, repaired.duration)
                assertNotNull(repaired.bookmarkedAt)

                val complete = repaired.copy(songCount = 20, duration = 900)
                dao.upsert(complete)
                database.toggleAlbumLiked(album, songs, repository)
                assertEquals(complete.copy(bookmarkedAt = null), dao.album(album.id).first()?.album)

                dao.upsert(saved.copy(duration = 0))
                database.toggleAlbumLiked(album, listOf(songs.first().copy(duration = Long.MAX_VALUE)), repository)
                assertEquals(
                    Int.MAX_VALUE,
                    dao
                        .album(album.id)
                        .first()
                        ?.album
                        ?.duration,
                )
                assertNull(
                    dao
                        .album(album.id)
                        .first()
                        ?.album
                        ?.bookmarkedAt,
                )
                coroutineScope {
                    repeat(2) {
                        launch { database.toggleAlbumLiked(album, songs, repository) }
                    }
                }
                assertNull(
                    dao
                        .album(album.id)
                        .first()
                        ?.album
                        ?.bookmarkedAt,
                )
            } finally {
                client.close()
                database.close()
                Files.deleteIfExists(path)
            }
        }
}
