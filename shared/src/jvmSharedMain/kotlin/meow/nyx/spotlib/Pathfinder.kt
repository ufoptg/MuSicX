package meow.nyx.spotlib

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

internal object Queries {
    const val HOME_HASH = "23e37f2e58d82d567f27080101d36609009d8c3676457b1086cb0acc55b72a5d"
    const val SEARCH_DESKTOP_HASH = "4801118d4a100f756e833d33984436a3899cff359c532f8fd3aaf174b60b3b49"
    const val FETCH_PLAYLIST_HASH = "346811f856fb0b7e4f6c59f8ebea78dd081c6e2fb01b77c954b26259d5fc6763"
    const val ARTIST_OVERVIEW_HASH = "5b9e64f43843fa3a9b6a98543600299b0a2cbbbccfdcdcef2402eb9c1017ca4c"
    const val GET_ALBUM_HASH = "b9bfabef66ed756e5e13f68a942deb60bd4125ec1f1be8cc42769dc0259b4b10"
    const val WHATS_NEW_FEED_HASH = "3b53dede3c6054e8b7c962dd280eb6761c5d1c82b06b039f4110d76a62b4966b"
    const val ADD_TO_PLAYLIST_HASH = "47b2a1234b17748d332dd0431534f22450e9ecbb3d5ddcdacbd83368636a0990"
    const val MOVE_ITEMS_IN_PLAYLIST_HASH = "47b2a1234b17748d332dd0431534f22450e9ecbb3d5ddcdacbd83368636a0990"
    const val EDIT_PLAYLIST_ATTRIBUTES_HASH = "35a1a9ce3a2f4f8c32ee0e24c63c2069c6613c0a0b7e56d0e40dabe69a0b4f80"
    const val ADD_TO_LIBRARY_HASH = "7c5a69420e2bfae3da5cc4e14cbc8bb3f6090f80afc00ffc179177f19be3f33d"
    const val REMOVE_FROM_LIBRARY_HASH = "7c5a69420e2bfae3da5cc4e14cbc8bb3f6090f80afc00ffc179177f19be3f33d"

    const val PATHFINDER_URL = "https://api-partner.spotify.com/pathfinder/v2/query"
    const val LIBRARY_V3_HASH = "390c78e5b951029bad359785e69b07b536a509c581cbcd0aded5e5067f187455"
    const val PLAYLIST_CONTENTS_HASH = "e4b2953f160e58e38ac025d79b5a9b3aceee5c4c716598e9830bfceb69faff5f"
    const val LIBRARY_TRACKS_HASH = "087278b20b743578a6262c2b0b4bcd20d879c503cc359a2285baf083ef944240"
    const val PROFILE_ATTRIBUTES_HASH = "08ffb4730af3746e04a8301396f20875dbbce10c75243803091a9274eacc8ac0"
    const val SEARCH_SUGGESTIONS_HASH = "b50ebd72524415b132ddaca04158fd7aca529da28be322c9924643c0633df5bd"

    val LIBRARY_FEATURES =
        listOf(
            "LIKED_SONGS",
            "YOUR_EPISODES_V2",
            "PRERELEASES",
            "PRERELEASES_V2",
            "CLIPS",
            "EVENTS",
        )
}

internal fun JsonObject?.string(key: String): String? = this?.get(key)?.jsonPrimitive?.content

internal fun JsonObject?.obj(key: String): JsonObject? = this?.get(key) as? JsonObject

internal fun JsonObject?.array(key: String): JsonArray = this?.get(key) as? JsonArray ?: JsonArray(emptyList())

internal fun JsonObject?.objects(key: String): List<JsonObject> =
    (this?.get(key) as? JsonArray)?.filterIsInstance<JsonObject>() ?: emptyList()

internal data class LibraryPage(
    val playlists: List<PlaylistMeta> = emptyList(),
    val folders: List<Pair<String, String>> = emptyList(),
    val likedSongs: PlaylistMeta? = null,
    val totalCount: Int = 0,
)

internal fun parseLibraryPage(
    page: JsonObject,
    folderPath: List<String>,
): LibraryPage {
    val library = page.obj("data")?.obj("me")?.obj("libraryV3") ?: return LibraryPage()
    val playlists = ArrayList<PlaylistMeta>()
    val folders = ArrayList<Pair<String, String>>()
    var likedSongs: PlaylistMeta? = null

    for (entry in library.array("items")) {
        val wrapper = (entry as? JsonObject)?.obj("item") ?: continue
        val data = wrapper.obj("data")
        val type = wrapper.string("__typename").orEmpty()
        val uri = data?.string("uri") ?: wrapper.string("_uri") ?: continue
        val name = data?.string("name") ?: uri
        when (type) {
            "LibraryFolderResponseWrapper" -> {
                folders.add(uri to name)
            }

            "PlaylistResponseWrapper" -> {
                val image =
                    data
                        ?.obj("images")
                        ?.objects("items")
                        ?.firstOrNull()
                        ?.objects("sources")
                        ?.firstOrNull()
                        ?.string("url")
                playlists.add(
                    PlaylistMeta(
                        uri = uri,
                        name = name,
                        owner = data?.obj("ownerV2")?.obj("data")?.string("name"),
                        description = data?.string("description").orEmpty(),
                        image = image,
                        folderPath = folderPath,
                    ),
                )
            }

            "LibraryPseudoPlaylistResponseWrapper" -> {
                if (uri == "spotify:collection:tracks") {
                    likedSongs =
                        PlaylistMeta(
                            uri = uri,
                            name = name,
                            folderPath = folderPath,
                            source = PlaylistSource.LIKED_SONGS,
                            trackCount =
                                data
                                    ?.get("count")
                                    ?.jsonPrimitive
                                    ?.content
                                    ?.toIntOrNull() ?: 0,
                        )
                }
            }
        }
    }
    return LibraryPage(
        playlists,
        folders,
        likedSongs,
        library["totalCount"]?.jsonPrimitive?.content?.toIntOrNull() ?: playlists.size,
    )
}

internal fun parseTrackItem(
    entry: JsonObject,
    position: Int,
): TrackItem {
    val wrapper = entry.obj("itemV2") ?: entry.obj("track") ?: entry
    val node = wrapper.obj("data") ?: wrapper
    val albumNode = node.obj("albumOfTrack")
    val durationRaw = node["duration"]
    val durationMs =
        when (durationRaw) {
            is JsonObject -> durationRaw["totalMilliseconds"]?.jsonPrimitive?.content?.toLongOrNull()
            null -> node.obj("trackDuration")?.string("totalMilliseconds")?.toLongOrNull()
            else -> durationRaw.jsonPrimitive.content.toLongOrNull()
        }
    val playable =
        node
            .obj("playability")
            ?.get("playable")
            ?.jsonPrimitive
            ?.content
            ?.toBooleanStrictOrNull()
            ?: node["isPlayable"]?.jsonPrimitive?.content?.toBooleanStrictOrNull()
    val uri = node.string("uri") ?: wrapper.string("_uri") ?: wrapper.string("uri").orEmpty()
    return TrackItem(
        uri = uri,
        name = node.string("name").orEmpty(),
        type = node.string("__typename") ?: "Track",
        artists =
            node
                .obj("artists")
                ?.objects("items")
                ?.mapNotNull { it.obj("profile")?.string("name") }
                .orEmpty(),
        album = albumNode?.string("name"),
        albumUri = albumNode?.string("uri"),
        albumArtists =
            albumNode
                ?.obj("artists")
                ?.objects("items")
                ?.mapNotNull { it.obj("profile")?.string("name") }
                .orEmpty(),
        releaseDate = albumNode?.obj("date")?.string("isoString"),
        durationMs = durationMs,
        trackNumber = node["trackNumber"]?.jsonPrimitive?.content?.toIntOrNull(),
        discNumber = node["discNumber"]?.jsonPrimitive?.content?.toIntOrNull(),
        explicit = node.obj("contentRating")?.string("label") == "EXPLICIT",
        isLocal = uri.startsWith("spotify:local:"),
        isPlayable = playable,
        addedAt = entry.obj("addedAt")?.string("isoString"),
        addedBy = entry.obj("addedBy")?.obj("data")?.string("name") ?: entry.obj("addedBy")?.obj("data")?.string("username"),
        position = position,
    )
}

internal fun parseLikedSongsPage(page: JsonObject): Pair<List<TrackItem>, Int> {
    val tracks =
        page
            .obj("data")
            ?.obj("me")
            ?.obj("library")
            ?.obj("tracks") ?: return emptyList<TrackItem>() to 0
    val items =
        tracks.array("items").mapIndexedNotNull { index, item ->
            (item as? JsonObject)?.let { parseTrackItem(it, index + 1) }
        }
    return items to (tracks["totalCount"]?.jsonPrimitive?.content?.toIntOrNull() ?: items.size)
}

internal fun parsePlaylistContentsPage(page: JsonObject): Pair<List<TrackItem>, Int> {
    val content = page.obj("data")?.obj("playlistV2")?.obj("content") ?: return emptyList<TrackItem>() to 0
    val items =
        content.array("items").mapIndexedNotNull { index, item ->
            (item as? JsonObject)?.let { parseTrackItem(it, index + 1) }
        }
    return items to (content["totalCount"]?.jsonPrimitive?.content?.toIntOrNull() ?: items.size)
}

internal fun parseProfile(page: JsonObject): AccountProfile {
    val profile = page.obj("data")?.obj("me")?.obj("profile") ?: return AccountProfile()
    return AccountProfile(
        name = profile.string("name"),
        username = profile.string("username"),
        uri = profile.string("uri"),
        avatar = profile.string("avatar"),
    )
}

internal fun parseHomeSection(sectionObj: JsonObject): SpotifyHomeFeedSection? {
    val sectionData = sectionObj.obj("data") ?: return null
    val typename = sectionData.string("__typename") ?: return null
    val titleObj = sectionData.obj("title")
    val title =
        titleObj?.string("transformedLabel")
            ?: titleObj?.string("translatedBaseText")
            ?: titleObj?.string("text")

    val sectionItems = sectionObj.obj("sectionItems")
    val totalCount = sectionItems?.get("totalCount")?.jsonPrimitive?.content?.toIntOrNull() ?: 0
    val itemElements = sectionItems?.array("items") ?: return null

    val items =
        itemElements.mapNotNull { elem ->
            (elem as? JsonObject)?.let(::parseHomeItem)
        }
    if (items.isEmpty()) return null

    return SpotifyHomeFeedSection(
        sectionUri = sectionObj.string("uri") ?: "",
        title = title,
        typename = typename,
        totalCount = totalCount,
        items = items,
    )
}

internal fun parseHomeItem(itemObj: JsonObject): SpotifyHomeFeedItem? {
    val content = itemObj.obj("content") ?: return null
    val wrapper = content.string("__typename") ?: return null
    val data = content.obj("data") ?: return null

    return when (wrapper) {
        "PlaylistResponseWrapper" -> parseHomePlaylist(data)
        "AlbumResponseWrapper" -> parseHomeAlbum(data)
        "ArtistResponseWrapper" -> parseHomeArtist(data)
        else -> null
    }
}

internal fun parseHomePlaylist(data: JsonObject): SpotifyHomeFeedItem.Playlist? {
    val uri = data.string("uri") ?: return null
    val imageItem = data.obj("images")?.array("items")?.firstOrNull() as? JsonObject
    val imageUrl = imageItem?.array("sources")?.firstOrNull()?.let { it as? JsonObject }?.string("url")
    val colorHex = imageItem?.obj("extractedColors")?.obj("colorDark")?.string("hex")
    val madeFor =
        data
            .array("attributes")
            .firstOrNull { (it as? JsonObject)?.string("key") == "madeFor.username" }
            ?.let { it as? JsonObject }
            ?.string("value")

    return SpotifyHomeFeedItem.Playlist(
        uri = uri,
        id = uri.substringAfterLast(":"),
        name = data.string("name").orEmpty(),
        description = data.string("description"),
        format = data.string("format"),
        totalCount = data.obj("content")?.get("totalCount")?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
        imageUrl = imageUrl,
        extractedColorHex = colorHex,
        ownerName = data.obj("ownerV2")?.obj("data")?.string("name"),
        madeForUsername = madeFor,
    )
}

internal fun parseHomeAlbum(data: JsonObject): SpotifyHomeFeedItem.Album? {
    val uri = data.string("uri") ?: return null
    val artists =
        data
            .obj("artists")
            ?.array("items")
            ?.mapNotNull { elem -> (elem as? JsonObject)?.obj("profile")?.string("name") }
            ?: emptyList()
    val imageUrl = data.obj("coverArt")?.array("sources")?.firstOrNull()?.let { it as? JsonObject }?.string("url")

    return SpotifyHomeFeedItem.Album(
        uri = uri,
        id = uri.substringAfterLast(":"),
        name = data.string("name").orEmpty(),
        albumType = data.string("type")?.lowercase(),
        artists = artists,
        imageUrl = imageUrl,
    )
}

internal fun parseHomeArtist(data: JsonObject): SpotifyHomeFeedItem.Artist? {
    val uri = data.string("uri") ?: return null
    val imageUrl =
        data
            .obj("visuals")
            ?.obj("avatarImage")
            ?.array("sources")
            ?.firstOrNull()
            ?.let { it as? JsonObject }
            ?.string("url")

    return SpotifyHomeFeedItem.Artist(
        uri = uri,
        id = uri.substringAfterLast(":"),
        name = data.obj("profile")?.string("name").orEmpty(),
        imageUrl = imageUrl,
    )
}

// ===== Spotify Search =====

/**
 * Parsed result from a Spotify search query.
 * Contains tracks, albums, artists, and playlists matching the search term.
 */
data class SpotifySearchResult(
    val tracks: List<TrackItem> = emptyList(),
    val albums: List<SpotifyHomeFeedItem.Album> = emptyList(),
    val artists: List<SpotifyHomeFeedItem.Artist> = emptyList(),
    val playlists: List<SpotifyHomeFeedItem.Playlist> = emptyList(),
)

internal fun parseSearchResult(page: JsonObject): SpotifySearchResult {
    val searchV2 = page.obj("data")?.obj("searchV2")
    val topResults = searchV2?.obj("topResultsV2")
    val items = topResults?.array("itemsV2")?.filterIsInstance<JsonObject>() ?: emptyList()

    val tracks = mutableListOf<TrackItem>()
    val albums = mutableListOf<SpotifyHomeFeedItem.Album>()
    val artists = mutableListOf<SpotifyHomeFeedItem.Artist>()
    val playlists = mutableListOf<SpotifyHomeFeedItem.Playlist>()

    for (itemWrapper in items) {
        val item = itemWrapper.obj("item")?.obj("data") ?: continue
        val typename = item.string("__typename") ?: continue
        when (typename) {
            "Track" -> {
                val track = parseTrackItem(item, tracks.size + 1)
                tracks.add(track)
            }
            "Album" -> parseHomeAlbum(item)?.let { albums.add(it) }
            "Artist" -> parseHomeArtist(item)?.let { artists.add(it) }
            "Playlist" -> parseHomePlaylist(item)?.let { playlists.add(it) }
        }
    }

    return SpotifySearchResult(tracks, albums, artists, playlists)
}
