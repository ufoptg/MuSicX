package dev.musicx.desktop.state

import dev.musicx.desktop.state.content.canRestoreAlbum
import com.metrolist.innertubex.InnerTube
import com.metrolist.innertubex.models.YouTubeLocale
import dev.musicx.shared.model.AlbumItem
import dev.musicx.shared.model.AlbumPage
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RetainedAlbumTest {
    @Test
    fun onlyMatchingLoadedAlbumsFromTheSameSessionCanBeRestored() {
        val page = AlbumPage(AlbumItem("album", "Album", null, emptyList()), emptyList())
        val session = InnerTube.SessionSnapshot()
        assertTrue(canRestoreAlbum(page, "album", session, session))
        assertFalse(canRestoreAlbum(page, "other", session, session))
        assertFalse(canRestoreAlbum(null, "album", session, session))
        assertFalse(canRestoreAlbum(page, "album", null, session))
        assertFalse(canRestoreAlbum(page, "album", session, session.copy(cookie = "other")))
        assertFalse(canRestoreAlbum(page, "album", session, session.copy(locale = YouTubeLocale(gl = "PL", hl = "pl"))))
    }
}
