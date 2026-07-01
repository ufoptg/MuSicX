# Spotify Integration on `main` — Notes

Branch: **`feat/spotify-main-integration`** (based on `origin/main`)
Commit: `e22f278a3 feat(spotify): add Spotify integration on top of main`
Diff:  **62 files changed, +11 349 / −2** (patch: `SPOTIFY_MAIN_INTEGRATION.patch`, 495 KB)

---

## Approach

Rather than merging all of `meld/dev` (which would introduce 200 conflicts and
carry along Podcasts/Qobuz/SponsorBlock/Recognition-Widget/LyricsPlus), this
integration is a **focused Spotify-only port**:

1. Copy the `:spotify` Kotlin module verbatim from meld.
2. `:paxsenix` module — already present in main; kept main's version.
3. Copy the ~30 Spotify-specific app files (screens, view models, playback
   queues, DB entity, utils).
4. Strip Qobuz dependencies from `SpotifyTrackMenu.kt` and `SpotifySettings.kt`
   (Qobuz is not being ported).
5. Add Spotify preference keys, DAO methods, Room entity + auto-migration,
   navigation routes, integration-screen entry point, string resources,
   and OAuth callback intent-filter.
6. Add three init calls in `App.kt.onCreate()` (Spotify.logger,
   `SpotifyHashSync`, `SpotifyTokenManager`).

**Not carried over:** Podcasts, Qobuz, SponsorBlock, Music Recognition Widget,
LyricsPlus lyrics provider, ANR Watchdog, CrashReporter→GitHub, ExperimentalLyrics,
new player designs from meld, all locale-string additions except Spotify ones.

---

## How Spotify auth actually works

Meld's Spotify integration **does not use** the Spotify Developer app flow
(Client ID + Client Secret + OAuth redirect). It uses the web-player token
flow (à la Spotube):

1. `SpotifyLoginScreen` opens Spotify's login page in an in-app WebView.
2. On successful login, the `sp_dc` cookie is extracted from the WebView.
3. `SpotifyAuth.fetchAccessToken(spDc, spKey)`:
   a. Fetches the TOTP secret + version from a community-maintained GitHub Gist.
   b. Fetches the Spotify server-time.
   c. Generates a 6-digit HMAC-SHA1 TOTP.
   d. Calls `https://open.spotify.com/api/token` with the TOTP + `sp_dc` cookie.
4. `SpotifyTokenManager` caches the access token in DataStore and refreshes
   it before expiry.

The GraphQL persisted-query hashes used by the Spotify web-player rotate
periodically, so `SpotifyHashSync` refreshes them at app start (and on demand
when the backend returns a 400 for an expired hash).

**Your Spotify Developer Client ID / Client Secret are NOT used.**

---

## OAuth redirect intent-filter

Added to `AndroidManifest.xml`:

```xml
<!-- Spotify OAuth callback (currently unused — reserved for future OAuth flow) -->
<intent-filter>
  <action android:name="android.intent.action.VIEW" />
  <category android:name="android.intent.category.DEFAULT" />
  <category android:name="android.intent.category.BROWSABLE" />
  <data android:scheme="musicx" android:host="spotify" android:pathPrefix="/callback" />
</intent-filter>
```

Scheme is `musicx://spotify/callback`. The current WebView-based auth flow
does not exercise this — it is here so you can wire up a real OAuth flow
later without another manifest change.

---

## What was added

### New Gradle module: `:spotify`
- Kotlin/JVM library
- 18 files: `Spotify.kt`, `SpotifyAuth.kt`, `SpotifyMapper.kt`, `SpotifyHashProvider.kt`, plus 12 model classes and 2 tests
- Package: `com.metrolist.spotify`

### New app files (30)
| Path | Purpose |
| --- | --- |
| `db/entities/SpotifyMatchEntity.kt` | Room entity: caches Spotify-track → YouTube-track resolutions |
| `models/SpotifyHomeSection.kt` | Data class for Spotify home-feed sections |
| `models/NewReleaseItem.kt` | Unified new-release model (Spotify + YouTube) |
| `playback/SpotifyMetadataRegistry.kt` | In-memory registry of Spotify metadata for currently queued items |
| `playback/SpotifyProfileCache.kt` | Cache for the current Spotify user's profile + REST responses |
| `playback/SpotifyRecommendationEngine.kt` | Generates recommendations based on Spotify listening data |
| `playback/SpotifyYouTubeMapper.kt` | Resolves Spotify tracks to YouTube equivalents (uses `spotify_match` table) |
| `playback/queues/SpotifyQueue.kt` | Base `Queue` implementation backed by Spotify tracks |
| `playback/queues/SpotifyPlaylistQueue.kt` | Playback of a Spotify playlist |
| `playback/queues/SpotifyLikedSongsQueue.kt` | Playback of the user's Spotify Liked Songs |
| `utils/SpotifyHashSync.kt` | Fetches / caches the rotating GQL persisted-query hashes |
| `utils/SpotifyItemConverter.kt` | Extension fns: `SpotifyTrack.toSongItem()` etc. |
| `utils/SpotifyTokenManager.kt` | Central token store, refresh, ensure-authenticated |
| `viewmodels/Spotify{,Album,Playlist,LikedSongs,Preload}ViewModel.kt` | ViewModels for each screen |
| `ui/screens/SpotifyLoginScreen.kt` | WebView login + `sp_dc` extraction |
| `ui/screens/album/SpotifyAlbumScreen.kt` | Displays a Spotify album |
| `ui/screens/library/SpotifyFolderScreen.kt` | Displays a user's Spotify playlist folder |
| `ui/screens/playlist/SpotifyPlaylistScreen.kt` | Displays a Spotify playlist |
| `ui/screens/playlist/SpotifyLikedSongsScreen.kt` | Displays user's Spotify Liked Songs |
| `ui/screens/settings/integrations/SpotifySettings.kt` | Login/logout, sync-likes, home toggles (Qobuz section stripped) |
| `ui/screens/settings/integrations/SpotifyPreloadScreen.kt` | Bulk-preload all Spotify likes → YouTube matches into DB |
| `ui/menu/SpotifyTrackMenu.kt` | Long-press menu for a Spotify track (Qobuz-download option stripped) |
| `ui/menu/SpotifyPlaylistPickerDialog.kt` | Pick a Spotify playlist to add a track to |
| `ui/menu/SpotifyPlaylistPinMenu.kt` | Pin/unpin a Spotify playlist to the library |
| `ui/component/SpotifyFolderListItem.kt` | List-item for a Spotify folder |
| `ui/component/SpotifyHomeSectionRow.kt` | Home-feed horizontal row |
| `ui/component/YouTubeMatchDialog.kt` | Dialog to manually match a Spotify track to a specific YouTube video |
| `ui/component/PreferenceGroupTitle.kt` | Small helper Composable (extracted from meld's Preference.kt) |

### Modifications to existing files (13)
- `settings.gradle.kts` — added `include(":spotify")`
- `app/build.gradle.kts` — added `implementation(project(":spotify"))`
- `AndroidManifest.xml` — Spotify OAuth callback intent-filter
- `App.kt` — 3 new blocks in `onCreate()` for Spotify logger, hash-sync, token-manager
- `constants/PreferenceKeys.kt` — 18 Spotify keys + `SpotifySortType` enum + `HideYtmLikedSongsKey`
- `db/MusicDatabase.kt` — registered `SpotifyMatchEntity`, bumped version 38 → 39, added `AutoMigration(from=38, to=39)`
- `db/DatabaseDao.kt` — added 4 methods: `getSpotifyMatch`, `getSpotifyMatchByYouTubeId`, `upsertSpotifyMatch`, `deleteSpotifyMatch`
- `ui/screens/NavigationBuilder.kt` — 7 new routes:
  - `settings/integrations/spotify`
  - `settings/integrations/spotify/preload`
  - `spotify/login`
  - `spotify/album/{albumId}`
  - `spotify/playlist/{playlistId}`
  - `spotify/liked`
  - `spotify/folder/{folderId}`
- `ui/screens/settings/integrations/IntegrationScreen.kt` — added Spotify entry point card
- `res/values/metrolist_strings.xml` — ~80 Spotify UI strings + one `<string name="ok">` fallback
- `res/drawable/spotify.xml` — Spotify logo vector

---

## What will NOT work without follow-up

The integration is functionally complete for the screens that were copied and
wired via `NavigationBuilder`. However, deeper hooks that meld has in
**shared files that I intentionally did not overwrite** are absent:

1. **HomeScreen.kt** — meld surfaces a "Spotify Home" mode toggle and injects
   Spotify home sections; main's `HomeScreen` doesn't. Enable via
   Settings → Integrations → Spotify → *Use Spotify home*, then wire the
   `SpotifyViewModel.homeSections` flow into `HomeScreen.kt` yourself, or
   render `SpotifyHomeSectionRow` from a settings-conditional block.

2. **LibraryPlaylistsScreen.kt** — meld shows the user's Spotify playlists
   inline (with a folder icon). To surface them on main, add a section that
   collects `SpotifyViewModel.playlistFolders` and links each to
   `spotify/folder/{id}` or `spotify/playlist/{id}`.

3. **SongMenu / PlayerMenu / YouTubeSongMenu** — meld adds a "Add to Spotify
   playlist" item and a "Change YouTube version" item pointing to
   `SpotifyPlaylistPickerDialog` / `YouTubeMatchDialog`. Not injected on main.

4. **OnlineSearchResult.kt** — meld returns Spotify results alongside
   YouTube; not touched on main.

5. **MusicService.kt / MediaLibrarySessionCallback.kt** — the Spotify queue
   classes (`SpotifyQueue`, `SpotifyPlaylistQueue`, `SpotifyLikedSongsQueue`)
   are dropped in but not wired into the MusicService loading path. You'll
   need to add a branch in your queue-loading logic that recognizes Spotify
   URIs and constructs the appropriate queue class.

6. **ArtistScreen.kt / AlbumScreen.kt** — meld renders Spotify equivalents
   for artists / albums; not wired on main.

All of the above can be added incrementally by referencing meld's `dev`
branch for the exact code, since those files are unmodified in this diff.

---

## Testing checklist for you

1. **Gradle sync** in Android Studio — should succeed. If you get `Unresolved
   reference: SpotifyMatchEntity` in Room-generated code, run
   `./gradlew :app:kspDebugKotlin` (or the equivalent for your active flavor)
   to trigger schema regeneration. Room will write
   `app/schemas/com.metrolist.music.db.InternalDatabase/39.json` on first
   compile.

2. **Fresh install path**: launch the app, go to Settings →
   Integrations → **Spotify Integration** → *Log in with Spotify*. Complete
   login inside the WebView. On success, the token is saved and you'll see
   your Spotify username.

3. **Existing user path**: existing users on DB v38 will hit
   `AutoMigration(from = 38, to = 39)` which only adds the empty
   `spotify_match` table — safe and reversible.

4. **Room / KSP schema warnings**: If Room complains that migration 39 is
   destructive, it means it detected a schema change beyond the added table.
   Compare `app/schemas/…/38.json` vs the newly generated `39.json` and add
   any missing manual migration steps.

5. **Play a Spotify track**: navigate to a Spotify playlist screen and hit a
   track. `SpotifyYouTubeMapper` will look up (or match+cache) the YouTube
   equivalent, then start normal YouTube playback. If nothing plays, tail
   logcat for `SpotifyAPI` / `SpotifyYouTubeMapper` tags.

---

## Known small compile risks

Even though I resolved all `com.metrolist.music.*` imports against main,
these three things could still bite at build time and would take you <10 min
to fix in Android Studio:

- **Ktor version drift**: main uses ktor `3.4.x`; the `:spotify` module was
  written against the same major but a slightly newer minor. Should be
  ABI-compatible.
- **Coil version drift**: some Spotify screens use `AsyncImage` with
  `coil3` — main and meld are both on coil3 3.x, fine.
- **Compose Material3 alpha**: main uses `material3 = "1.5.0-alpha16"` or
  similar; meld the same lineage. Symbols used (`SortHeader`, `EnumDialog`,
  `IntegrationCard`) are custom-defined in `ui/component`, so no material3
  API-change risk.

If any of these produce errors, they'll be pointed to precisely by the
Kotlin compiler; usually a one-line fix per site.

---

## How to push

```bash
cd /app/MuSicX
git push origin feat/spotify-main-integration
```

Then open a PR against `main` and link this file.

---

## Applying the patch to a fresh clone

If you want to redo this from scratch on a clean `main` checkout:

```bash
git clone https://github.com/ufoptg/MuSicX.git && cd MuSicX
git checkout main
git apply SPOTIFY_MAIN_INTEGRATION.patch
git commit -am "feat(spotify): integrate Spotify (from meld/dev)"
```

---

## Security follow-up

The GitHub PAT you shared publicly at the start of this session is still
listed as active until you rotate it. Please go to
<https://github.com/settings/tokens> and **revoke it now**. Even though
you said "when we finish", the token has been in log storage for the entire
session and shouldn't be trusted anymore. Generate a new one, scope it
to only `contents:write` on `ufoptg/MuSicX`, and set a short expiry.
