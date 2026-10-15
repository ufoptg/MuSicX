# Slice B: Synced Library (Liked + Playlists)

> **For agentic workers:** REQUIRED SUB-SKILL: superpowers:executing-plans (native execution).

**Goal:** Surface the user's real YouTube Music library on desktop: Liked songs and library playlists, playable in-app.

**Architecture:** `DesktopInnerTube` gains three setLogin=true browse calls mirroring Android's `YouTube.library` usage: `FEmusic_liked_videos` → songs (`extractHits`), `FEmusic_liked_playlists` → `PlaylistHit` list, and `VL<playlistId>` → playlist tracks (`extractHits`). Desktop `LibraryScreen` gains "Playlists" and "Liked" filter-chip tabs next to the local Favorites/History tabs; tapping a playlist opens its track list in-place. Tabs are hidden behind a sign-in hint when logged out.

**Tech Stack:** Kotlin, Compose Desktop, InnerTubeX browse API.

**Spec:** `docs/superpowers/plans/2026-10-06-windows-desktop-parity-roadmap.md` Slice B.

## Global Constraints

- No version bump; no DB schema changes; ponytail; CI Windows build green; no local exe build.

## Review Focus

- Logged-out: Library shows "Sign in to sync your library" instead of empty lists, no crash.
- Playlist rows without a `VL...` browseId are skipped safely.
- Tapping a playlist fetches its tracks; back returns to the playlist list.
- Local Favorites/History behavior unchanged (clear, play, persistence).

---

### Task 1: DesktopInnerTube library APIs

**Files:** `desktop/src/main/kotlin/com/metrolist/music/desktop/DesktopInnerTube.kt`

- [ ] Step 1: Add `data class PlaylistHit(val id: String, val title: String, val subtitle: String? = null, val thumbnailUrl: String? = null)`.
- [ ] Step 2: Add `suspend fun likedSongs(): List<SearchHit>` = browse `FEmusic_liked_videos` (setLogin=true) → `extractHits`.
- [ ] Step 3: Add `suspend fun likedPlaylists(): List<PlaylistHit>` = browse `FEmusic_liked_playlists` (setLogin=true) → walk for `musicTwoRowItemRenderer`, playlistId from `navigationEndpoint.browseEndpoint.browseId` minus `VL` prefix (skip if absent), title from `title.runs`, subtitle from `subtitle.runs`, thumb from `thumbnailRenderer.musicThumbnailRenderer.thumbnail.thumbnails.last().url`.
- [ ] Step 4: Add `suspend fun playlistTracks(playlistId: String): List<SearchHit>` = browse `VL$playlistId` (setLogin=true) → `extractHits`.

### Task 2: LibraryScreen tabs

**Files:** `desktop/src/main/kotlin/com/metrolist/music/desktop/Main.kt`

- [ ] Step 1: Add state in `MuSicXApp`: `libraryLiked`, `libraryLikedLoading`, `libraryPlaylists`, `libraryPlaylistsLoading`, `openPlaylist` (`Pair<String, String>?` id+title), `openPlaylistTracks`, `openPlaylistLoading`.
- [ ] Step 2: `LaunchedEffect(signedIn, destination)`: when destination == Library && signedIn && playlists empty && !loading → launch both fetches.
- [ ] Step 3: In `LibraryScreen`, replace the two-tab row with four chips: Favorites / History / Playlists / Liked. Clear button visible only for Favorites/History.
- [ ] Step 4: Playlists tab: list `PlaylistHit` rows (thumbnail, title, subtitle, chevron); tap → fetch tracks into `openPlaylistTracks`, render track list + back button. Empty-while-signed-out → sign-in hint.
- [ ] Step 5: Liked tab: render `libraryLiked` via the existing result row UI (same row composable SearchScreen uses — find it: likely `ResultRow` or song row used in SearchScreen; reuse whatever LibraryScreen already uses for favorites/history lists).

### Task 3: Docs + CI

- [ ] Commit + push; confirm CI green.
- [ ] Update roadmap + PR #53 body/comment; docs commit.
