---v13.8.5
# MuSicX 13.8.5 — Spotify Liked Songs queue depth + Shuffle button

## Fixed
- **Only 23 songs ended up in the queue when starting playback from a Spotify Liked Songs list with thousands of tracks, even after hitting shuffle.** The Play button was constructing a fresh `SpotifyLikedSongsQueue` that ignored the ViewModel's already-loaded track list and re-paginated the Spotify API from scratch — so `getInitialStatus` returned only a 3-item fast-start window, `nextPage()` grew it to 23, and MusicService's auto-load-more threshold (`mediaItemCount - currentIndex ≤ 5`) then refused to grow the queue further until the user had almost finished playing what was loaded. Toggling shuffle on top of a 23-item queue only randomised those same 23 songs.
- **Latent bug: tapping a track when the list was sorted by Name / Artist / Duration played the wrong song**, because the queue re-fetched Spotify's date-added order and used the visible-order index as the start index — a completely different track. Now the visible order IS the queue.

## Changed
- `SpotifyLikedSongsQueue` now accepts an optional `preloadedTracks` list (mirrors the same param on `SpotifyPlaylistQueue`) and skips the Spotify API pagination entirely when supplied. The screen passes its already-loaded list, so the queue reflects the actual visible tracks.
- The initial fast-start window is widened from 3 → 50 tracks when preloaded — playback still starts as soon as the first parallel `mapToYouTube` completes, but the visible queue no longer looks empty.
- **New Shuffle button** on the Liked Songs screen (next to Play). It ships a pre-shuffled ordering as the queue's backing list, which means shuffle randomises across ALL loaded tracks — not just whatever tiny window the shuffle toggle happened to catch mid-playback. If the ViewModel is still paging in tracks when you tap it, a `loaded / total` progress hint appears next to the buttons.


---v13.8.4
# MuSicX 13.8.4 — Instant playback from Spotify home

## Fixed
- **Large delay (~4–6 s) between tapping a song in the Spotify home and the Now Playing bar / audio actually starting.** The v13.8.0 Spotify personalized-radio queue ran the entire recommendation engine (multiple Spotify API calls for top-tracks × 3 time ranges + artist genre lookups, bounded by a 4 s timeout) *synchronously* inside `SpotifyQueue.getInitialStatus()`, blocking `MusicService.playQueue()` from calling `setMediaItems` + `prepare` + `play` until the whole engine had run — even on repeat plays where the initial track was already memory-cached in the Spotify→YouTube mapper (~50 ms). The engine's output is only ever consumed later by `nextPage()`, so making the user wait for it was pointless.
- **Now the queue starts playing the tapped track immediately** and defers the recommendation engine to the first `nextPage()` call, which MusicService fires from `onMediaItemTransition` on a background coroutine *after* the initial track is already playing. Tap-to-play drops from ~4–6 s → **~100–200 ms warm** / **~500 ms–1.5 s cold**. Auto-queue population still happens, just under the audio.

## Notes
- Recommendation engine timeout and fallback semantics are unchanged: if the engine times out / returns empty / throws, the queue still falls back to the seed artist's top tracks + same album, shuffled (now hardened with a nested try/catch so a network failure during backfill can't crash the queue coroutine).


---v13.8.3
# MuSicX 13.8.3 — Critical playback regression fix (release-only)

## Fixed
- **Songs failing to play after upgrading to v13.8.x on release builds**, even for users who never turned Qobuz on. The v13.8.0 Qobuz integration added a code path in the ExoPlayer resolver (`createDataSourceFactory`) and a preference observer that could — under R8 code shrinking (release builds only) — abort playback when the Qobuz block threw, or trigger a spurious `player.stop()` on cold start. Debug/test builds skip R8 entirely so the bug was invisible there.
- **Hardened the Qobuz observer**: only reloads the current stream when the master `EnableQobuzKey` toggle is currently ON. Users who never opted into Qobuz can no longer have their playback interrupted by preference writes. Switched from a fragile `isFirstQobuzEmit` sentinel to `flow.drop(1)` + an explicit last-projection guard.
- **Wrapped the Qobuz block in `createDataSourceFactory` in try/catch** so any failure inside the Qobuz path (DataStore read, DB lookup, resolver network / JSON / timeout, R8-stripped symbol, …) silently degrades to the YouTube pipeline instead of aborting the ResolvingDataSource callback.
- **Extracted the Qobuz resolve pipeline into its own `resolveQobuzDataSpec` function** so the try/catch actually covers the entire code path.
- **Added ProGuard keep rules** for `com.metrolist.music.qobuz.**`, `QobuzMatchEntity`, `com.metrolist.spotify.models.**` (used by SpotifyMetadataRegistry from the loader thread), and Room `Migration` subclasses. Prevents R8 from renaming/stripping members that are hit reflectively during resolve or during DB migration.


---v13.8.2
# MuSicX 13.8.2 — Qobuz settings parity with meld

## Added
- **Country code editor** in the Qobuz settings — was previously hard-coded to `US`. Tap the entry to change it via a two-letter ISO code (US, IT, FR, JP, DE, …). Affects the Qobuz catalog and track availability. Falls back to `US` if you clear the field.
- **Backend Status section** with live reachability probes of every resolver (Monokenny / Jumo / Squid / TrypT HiFi). Runs on first open of the settings page and on the **Refresh** button — colour-coded dot (green = online, yellow = reachable-but-degraded, red = offline) + status label + HTTP code + latency in ms. The currently-selected backend gets a small `•` marker and a primary-tint title so you can see at a glance whether the one you picked is up.

Ports the two Qobuz sections meld's SpotifySettings had that MuSicX v13.8.1 was still missing.


---v13.8.1
# MuSicX 13.8.1 — Critical hotfix for v13.8.0 crash on launch

## Fixed
- **App no longer crashes on launch after upgrading from v13.7.0 → v13.8.0**. The v13.8.0 Qobuz migration created the `qobuz_match` table with every column declared `NOT NULL`, but the `QobuzMatchEntity` marks `bitDepth`, `samplingRateKhz` (both nullable) and `hires` (defaults to `0`) with default values. Room's post-migration schema validator rejected the mismatch on every launch with `IllegalStateException: Migration didn't properly handle: qobuz_match`, hard-crashing before the home screen could render.
- **DB bumped to v41 with a recovery migration (40 → 41)** that DROPs and recreates `qobuz_match` with the correct schema — safe because it's a search-result cache; the table refills naturally on next playback. Users upgrading from v13.7.0 straight to v13.8.1 also get the fixed 39 → 40 migration, so both upgrade paths land at the same correct schema.

## Notes for anyone on v13.8.0
If for some reason auto-update didn't push v13.8.1, **manually install v13.8.1 from GitHub Releases** and the app will self-repair on the next launch. No data loss. If it still crashes for some reason, clearing app data will reset the DB completely as a last resort.


---v13.8.0
# MuSicX 13.8.0 — Qobuz lossless streaming (experimental)

## Added
- **Qobuz FLAC / Hi-Res streaming** (ported from meld). When enabled, MuSicX resolves each YouTube track against the Qobuz catalog and, if a match is found, streams from Qobuz instead — falling back silently to YouTube on any failure. Uses Spotify ISRC when available for tighter matching.
- **Settings → Spotify Integration → Audio quality (experimental)**:
  - `Use Qobuz for lossless playback` — master toggle
  - `Stream quality` — AAC 320 / CD (FLAC 16-bit 44.1 kHz) / Hi-Res (up to 24-bit / 192 kHz)
  - `Resolver backend` — Monokenny / Jumo / Squid / TrypT HiFi
  - Country fallback: `US` (adjustable in a follow-up)
- **DB schema v40** — new `qobuz_match` cache table (per-YouTube match with hires flag, bit depth, sampling rate) + new `song.isrc` column. AutoMigration 39→40.
- **QobuzMatchOverrideDialog** — manual "pick the right Qobuz track" UI for when the automatic matcher gets it wrong.
- **MusicService integration**:
  - `qobuzMissUntilMs` in-memory negative cache (24h TTL) so non-Qobuz tracks skip the search cascade
  - Cross-backend fallback cascade for known-Qobuz tracks (2 alt backends before giving up)
  - Persisted matches short-circuit future plays (no re-search)
  - Source-selection observer reloads the current stream when the user toggles Qobuz / changes quality / backend / country — no app restart

## Notes
- Third-party resolvers (not run by MuSicX). Playback falls back to YouTube if a backend is offline or the track isn't on Qobuz.
- Lossless streams use 3–10× more data + storage than YouTube's default AAC — mind your mobile-data allowance.


---v13.7.0
# MuSicX 13.7.0 — SponsorBlock entry moved to Misc

## Fixed
- **SponsorBlock settings entry is now visible under Player Settings → Misc**, matching meld's layout. It was previously nested inside the Player section (between the Skip Silence toggles) and was easy to miss. Same behaviour — tapping still opens the SponsorBlock category picker where you can toggle it on and choose which segment types (sponsor / intro / outro / self-promo / interaction / preview / music-offtopic / filler) to auto-skip.


---v13.6.9
# MuSicX 13.6.9 — Recently Played slotted under Your Top Tracks

## Fixed
- **Recently Played now renders directly under "Your Top Tracks"** (inside the Spotify sections block), not above Quick Picks. Added a `postTopTracks` composable slot to `spotifyHomeSections` — the injector renders any provided content immediately after it emits the Top Tracks row (matched by title key `spotify_top_tracks`). HomeScreen passes the same Recently Played row as this slot when Spotify home is active, and falls back to a normal `HomeSection.RecentlyPlayed` entry (at the top of the local sections list) when Spotify is disabled / logged out.
- **Deduplicated Recently Played rendering** — extracted the title + horizontal grid into a single reusable `recentlyPlayedContent` lambda so both call sites share one implementation.


---v13.6.8
# MuSicX 13.6.8 — Continue Listening card + Recently Played polish

## Added
- **Continue Listening hero card** at the very top of Home — big thumbnail + title + artist + a large play/pause button. Pulls from the currently-loaded player metadata, or falls back to your most recent play from `database.events()` when the app is fresh. One tap resumes/toggles playback; long-press opens the song menu. Perfect for jumping straight back into what you were listening to on app relaunch.

## Fixed
- **Recently Played now includes the currently-playing song at position 0** — previously the row could feel stale because events are only written to the DB after ≥30s of playtime, so an in-progress song never appeared. We now merge the live `mediaMetadata` id (deduped) so the most recent track is always first.
- **Recently Played is now pinned to the top of the local sections list** — appears immediately under Spotify's "Your Top Tracks" row instead of getting shuffled beneath Quick Picks / Daily Discover by the randomize-order feature. Weight bumped to 10 000 (fixed, non-randomized) so it never gets reordered.


---v13.6.7
# MuSicX 13.6.7 — Recently Played row + upstream sync

## Fixed
- **Home now shows a real "Recently Played" row** — ported meld's local play-history section. The row was previously overtaken by Spotify's own GQL "Recently played" tile (which surfaced random playlists like *Happy Hits!* instead of the tracks you actually played). Now driven by `database.events()`: horizontal grid of the last 40 tracks you played on-device, deduped by song id, with tap-to-play, long-press menu, and play-all button — same UX as meld's screenshot.
- **Duplicate "Recently played" row from Spotify home feed removed** — `SpotifyHomeViewModel.convertHomeSection` now skips Spotify's own "Recently played" GQL section (case-insensitive), since the native local row already covers that intent.
- **Spotify REST recently-played row renamed to "Recently Played on Spotify"** — disambiguates from the new native row when both are available.

## Chores
- Synced upstream `MetrolistGroup/Metrolist` (up to `ae4f1e4e`) — added zemer-cipher credit in README. Player configs / dates were already at parity.

## Previously undocumented (v13.6.5 – v13.6.6)
- v13.6.6: Recently Played + Your Top Tracks Spotify home rows; like-sync between local library and Spotify.
- v13.6.5: `LibraryMixScreen` Spotify tile ordering fix (Spotify tiles now render after local auto-playlists).

---v13.6.4
# MuSicX 13.6.4 — Updater + repo-link fixes

## Fixed
- **In-app updater now correctly points to the MuSicX repo** — previous builds were checking `MetrolistGroup/Metrolist` releases and offering that as an update. Now uses `ufoptg/MuSicX/releases`, and also recognizes the new `MuSicX.apk` / `MuSicX-with-Google-Cast.apk` / `MuSicX-izzy.apk` asset naming (with backward compatibility for old Metrolist-named assets).
- **Spotify playlists now appear in Library GRID view** — previous fix only wired the injector into LIST view, so users on the default GRID view saw no Spotify content. Grid view now shows a green Spotify Liked Songs tile plus each Spotify playlist as a proper grid tile with its cover art.
- Repo links across the app (About screen, Discord Rich Presence button, OpenRouter HTTP-Referer, 30 localized `github_releases_url` strings) updated from `MetrolistGroup/Metrolist` → `ufoptg/MuSicX`.

---v13.6.3
# MuSicX 13.6.3 — First MuSicX-branded release 🎉

This is the first release under the **MuSicX** brand, a maintained fork of Metrolist with new integrations and reliability improvements.

## Major additions
- **Spotify integration** — Log in with your own Spotify account via in-app WebView (uses `sp_dc` cookie, no client secret required). Home, Library, Search and Now-Playing hooks bridge Spotify tracks to YouTube Music equivalents for playback.
- **SponsorBlock** — Auto-skip sponsor / intro / outro segments in music videos, powered by the sponsor.ajay.app community API.
- **ANR Watchdog** — Detects UI freezes ≥ 5s and captures diagnostic stack dumps.
- **Crash reporter** — Unhandled crashes are packaged (sanitized stack + device/version info) and opened as GitHub Issues automatically.

## Rebrand
- App fully renamed **Metrolist → MuSicX** across UI, build files, CI artifact names (`MuSicX.apk`, `MuSicX-with-Google-Cast.apk`, `MuSicX-izzy.apk`, `MuSicX-Nightly.apk`), 343 Kotlin copyright headers, and store icons.
- New app icon set across all mipmap densities (adaptive icon with dark `#0a0a0a` background).
- Package id: `dev.ufoptg.musicx`.

## Fixes
- Spotify login button no longer crashes with `IllegalArgumentException: Navigation destination that matches route settings/spotify/login cannot be found` — route mismatch fixed.
- Spotify folder / playlist navigation from within a folder now resolves (legacy underscore routes `spotify_folder/…` / `spotify_playlist/…` replaced with the registered slash routes).
- Library → Playlists tab now actually shows your Spotify playlists and Liked Songs after login (previous build subscribed to StateFlows but never triggered the data load).
- Added a dedicated **Liked Songs** entry that navigates to the Spotify liked-songs screen (was previously unreachable).
- Fixed release build failure caused by leftover `R.drawable.ic_launcher_foreground` reference in ConclusionPage after the icon migration.

## Under the hood
- New `:spotify` Gradle module.
- Room DB migration `38 → 39` — adds `spotify_match` table mapping Spotify track IDs to local track IDs.
- GitHub Actions workflows refreshed: PR / nightly / quick-test / release builds all now emit MuSicX-branded artifacts.

## Coming soon (roadmap)
- Music-Recognition Widget (Shazam)
- LyricsPlus / ExperimentalLyrics
- Podcasts
- Qobuz integration
- New Player Design


---v13.5.0
# MAINTENANCE MODE
Metrolist is currently in maintenance mode. This means we will only be fixing bugs and making minor improvements. Please do not submit PRs for new features or major changes, as they will not be accepted.

# Major changes
- Rewrote the Discord RPC integration again (@adrielGGmotion @nyxiereal)
- Fixed random playback issues and pauses (@DanielSchmerber @isotjs)
- Fixed liked songs, playlists, albums, search results, etc. not displaying properly (@adrielGGmotion @nyxiereal)

## Notable new features
- Added a toggle for automatic radio queue generation (@FireLion137)
- Added automatic tablet UI scaling (@kairosci)
- Toggle from repeat(1) to repeat(all) after song change (@sunjeetkajla)

## Other improvements
- Fixe covers not loading sometimes (@Arjuanto)
- Artist names are now split properly, and are now clickable (@kairosci)
- Fixed multiple crashes (@kairosci @nyxiereal)
- Improved audio normalization (@kairosci)
- Fixed search results not being combined properly (@kairosci)
- Fixed 'High quality' option not choosing the highest quality option (@kairosci)
- Fixed history sync not working (@kairosci)

## New Contributors
- @DanielSchmerber made their first contribution in https://github.com/MetrolistGroup/Metrolist/pull/3777
- @Arjuanto made their first contribution in https://github.com/MetrolistGroup/Metrolist/pull/3780

---v13.4.3
# MAINTENANCE MODE
Metrolist is currently in maintenance mode. This means we will only be fixing bugs and making minor improvements. Please do not submit PRs for new features or major changes, as they will not be accepted.

# Major changes
- Rewrote the Discord RPC integration (@adrielGGmotion)
- Improved the look of playlist screens (@adrielGGmotion)
- Added a new playlist widget (@David-2765 @AntonioDionisio05)

## Notable new features
- Added proper apple music lyrics support (@adrielGGmotion)
- Added a normalization level selector (@Jeff0945)
- Added the ability to hide monthly/weekly most playlists (@isotjs)

## Other improvements
- Improve overall performance and stability (@adrielGGmotion @nyxiereal)
- Fixed devnagari lyrics not being displayed properly (@cloud-zip)
- Improve lyrics fetching speed (@nyxiereal)
- Fixed crashes and some memory leaks (@nyxiereal)
- Fixed images being low resolution for some users (@adrielGGmotion)
- Handle playlist paging properly (@kairosci)
- Multiple smaller improvements by @kairosci <3

## New Contributors
- @Jeff0945 made their first contribution in https://github.com/MetrolistGroup/Metrolist/pull/3358

---v13.4.2
# MAINTENANCE MODE
Metrolist is currently in maintenance mode. This means we will only be fixing bugs and making minor improvements. Please do not submit PRs for new features or major changes, as they will not be accepted.

# Major changes
- Fixed random crashes and some memory leaks (@nyxiereal)
- Fixed issues with uploading songs to YouTube (@kairosci)
- Fixed playback for uploaded songs (@punkscience)

## Notable new features
- EQ screen redesign and guided AutoEQ profile import (@ndellagrotte)
- Automatically create database backups before updates (@nyxiereal)

## Other improvements
- Improved support for Android Auto (@cmeka)
- Brought back the copy lyrics button for experimental lyrics (@nyxiereal)
- Fixed the re-sync button for experimental lyrics (@nyxiereal)
- Fixed listen together not working (@nyxiereal)
- Added back support for choosing an account upon login (@nyxiereal)
- Implemented concurrent fetching, fix lyrics fetch ordering, and optimize LyricsPlus server selection (@ibratabian17)
- Corrected the play-next shuffle order (@johannesbrauer)
- Improved the Android Auto icon (@ThatOneCalculator)

## New Contributors
- @ndellagrotte made their first contribution in https://github.com/MetrolistGroup/Metrolist/pull/3487
- @cmeka made their first contribution in https://github.com/MetrolistGroup/Metrolist/pull/3534
- @punkscience made their first contribution in https://github.com/MetrolistGroup/Metrolist/pull/3517

---v13.4.1
# MAINTENANCE MODE
Metrolist is currently in maintenance mode. This means we will only be fixing bugs and making minor improvements. Please do not submit PRs for new features or major changes, as they will not be accepted.

# Major changes
- Fixed cached songs showing up in the downloads playlist (@nyxiereal)
- Fixed multiple playback issues and prepared for YouTube's player changes (@mostafaalagamy @nyxiereal)

## Notable new features
- Added the ability to paste URLs to the search to play them directly (@nyxiereal)
- Added a search bar to the Library screen (@isotjs)
- Added a setting to bind pitch and speed together (@sasha-melech)
- Added support for Gemini voice playback (@FireLion137)
- Added an option choose the highest possible audio quality (@nyxiereal @kairosci)
- Added a button to create a playlist from the Library screen (@SunjeetKajla)

## Other improvements
- Moved the resync button to the lyrics menu (@nyxiereal)
- Properly reset player on IO errors (@kairosci)
- Multiple improvements to lyrics fetching and parsing (@kairosci @nyxiereal @ibratabian17)
- Made autoplay disablable from the settings (@kairosci)
- Fixed foreground/background service crashes (@kairosci)
- Fixed Play next not working (@johannesbrauer)
- Properly handle database updates on download removal (@kairosci)
- Use lyricsplus caching to lower server load (@binimum)
- Performance optimizations (@stopper2408)
- Prefetch lyrics for the next song if currently viewing lyrics (@nyxiereal)
- Fixed multiple issues with Listen Together (@nyxiereal)
- Fixed multiple issues with the experimental lyrics (@nyxiereal)
- Fixed pause music on task clear not working (@nyxiereal)

## New Contributors
* @ibratabian17 made their first contribution in https://github.com/MetrolistGroup/Metrolist/pull/3474
* @sasha-melech made their first contribution in https://github.com/MetrolistGroup/Metrolist/pull/3301
* @FireLion137 made their first contribution in https://github.com/MetrolistGroup/Metrolist/pull/3500
* @binimum made their first contribution in https://github.com/MetrolistGroup/Metrolist/pull/3493
* @stopper2408 made their first contribution in https://github.com/MetrolistGroup/Metrolist/pull/3506
* @SunjeetKajla made their first contribution in https://github.com/MetrolistGroup/Metrolist/pull/3505

**Full Changelog**: https://github.com/MetrolistGroup/Metrolist/compare/v13.4.0...v13.4.1
---v13.4.0
# MAINTENANCE MODE
Metrolist is currently in maintenance mode. This means we will only be fixing bugs and making minor improvements. Please do not submit PRs for new features or major changes, as they will not be accepted.

No, this is not an April Fools joke, even though this update is being released on April 1st.

We are working on something big for the future of Metrolist - this is not the end of the project.

# Major changes
- Multiple playback fixes and reliability improvements (@alltechdev)
- Revamped the entire Lyrics engine, improving lyric accuracy and usability (@adrielGGmotion)
- Fixed multiple crash issues (@kairosci, @nyxiereal)
- Multiple improvements to Android Auto support (@andker87)
- Fixed multiple grammar and text inconsistency issues in the project (@TheRebo)

## Notable new features
- Added support for treating cached songs as offline songs (@kairosci)
- Added music alarm scheduling (@0xarchit)
- Added miniplayer styles (@johannesbrauer)
- Added a button to copy all song lyrics to the clipboard (@kairosci)
- Added a time transfer feature to move listening time between songs in the stats page (@finley-webber)
- Added customization support for the AI prompt used for translations (@nyxiereal)
- Added a notification-based music recognition for the QS tile shortcut (@isotjs)

## Other improvements
- Fixed incorrect artist order for multi-artist songs (@AntonioDionisio05)
- Fixed playtime in the stats page not being fully visible (@David-2765)
- Improved radio to start seamlessly when initiated from the currently playing track (@luigiwwmf)
- Improved the UI for tablets (@adrielGGmotion)
- Improved the About Screen layout (@adrielGGmotion)
- Fixed ghost adds on playlists (@johannesbrauer)
- Improved search focus and navigation behavior (@saivijaychandan)
- Added album navigation on song title click regardless of play source (@gergesh)
- Prevented UI state reset when switching apps (@mostafaalagamy)
- Restored the Daily Discover title in the Home screen (@mostafaalagamy)
- Fixed listen together audio choppiness (@nyxiereal)
- Redesigned romanization and account settings (@omardotdev)
- Improved the design of the sleep timer dialog (@johannesbrauer)
- Redesigned some components to use Material 3 Expressive (@johannesbrauer)
- Fixed links in the README (@Lolen10 @nyxiereal)

## New Contributors
* @AntonioDionisio05 made their first contribution in https://github.com/MetrolistGroup/Metrolist/pull/3255
* @David-2765 made their first contribution in https://github.com/MetrolistGroup/Metrolist/pull/3271
* @luigiwwmf made their first contribution in https://github.com/MetrolistGroup/Metrolist/pull/3293
* @gergesh made their first contribution in https://github.com/MetrolistGroup/Metrolist/pull/3300
* @Lolen10 made their first contribution in https://github.com/MetrolistGroup/Metrolist/pull/3328

**Full Changelog**: https://github.com/MetrolistGroup/Metrolist/compare/v13.3.0...v13.3.1
---v13.3.0
# Major changes
- Implemented song upload and delete functionality (@alltechdev)
- Multiple playback fixes and reliability improvements (@alltechdev, @mostafaalagamy)
- Fixed proguard rules causing issues with Reproducible Builds (@nyxiereal)
- Fixed proguard rules removing Listen Together protobuf classes (@mostafaalagamy)
- Added a playlist export option to the playlist context menu (@nyxiereal)

## Notable new features
- Added a Play all action for the stats page (@isotjs)
- Added a quick settings tile for recognizing music (@nyxiereal)
- Added automatic sleep timer options and integrated fade-out volume handling (@isotjs)
- Added a profile search filter (@alltechdev)
- Added channel subscriptions for podcasts and artists (@alltechdev)

## Other improvements
- Fixed cached images not clearing properly and cached covers not showing when offline (@nyxiereal)
- Removed useless and stale strings from the codebase (@nyxiereal)
- Refined the song details view (@omardotdev)
- Added support for Mistral AI models (@nyxiereal)
- Redesigned the lastfm integration settings (@omardotdev)
- Fixed importing csv files crashing the app (@nyxiereal)
- Prevent guest playback while in listen together (@nyxiereal)
- Fixed podcasts not working for logged-out users (@alltechdev)
- Updated dependencies (@nyxiereal)

## New Contributors
* @isotjs made their first contribution in https://github.com/MetrolistGroup/Metrolist/pull/3090

**Full Changelog**: https://github.com/MetrolistGroup/Metrolist/compare/v13.2.1...v13.3.0
---v13.2.1
>[!WARNING]
>Listen Together doesn't work in v13.2.1! Use v13.2.0 if you need it.

## Hot Fixes
- Fix interface lag issue
- Fix navigate local playlists pinned in speed dial
- Removed "cache songs only after playback has started" option

**Full Changelog**: https://github.com/MetrolistGroup/Metrolist/compare/v13.2.0...v13.2.1
---v13.2.0
# Major changes
- Fixed playback breaking due to YouTube's February 2026 n-transform changes (@alltechdev)
- Added full podcast library support (@mostafaalagamy & @alltechdev)
- Redesigned loading, Changelog, and About screens (@adrielGGmotion)
- Improved app startup time via parallelized home screen loading (@mostafaalagamy)

## Notable new features
- Added an option to cache songs only after playback has started (@kairosci)
- Added a music recognizer home screen widget (@mostafaalagamy)
- Rewrote music recognizer in pure Kotlin, removing NDK dependency and reducing APK size (@mostafaalagamy)
- Overhauled lyrics: added LyricsPlus provider, AI lyric fixes, untranslation support, and provider priority settings (@nyxiereal)
- Changed listen together to use protobuf, lowering latency and improving reliability (@nyxiereal)
- Added auto-approve setting for listen together song requests (@nyxiereal)
- Added an option to persist the sleep timer default value (@johannesbrauer)
- Added a dialog on logout to keep or clear library data (@alltechdev)

## Other improvements
- Fixed backup restore causing playback errors due to stale auth credentials (@alltechdev)
- The CSV import dialog is now scrollable (@kairosci)
- Fixed Android 15 foreground service crashes (@kairosci)
- Fixed a crash on the About screen on some devices (@mostafaalagamy)
- Fixed home screen playlist navigation routing to wrong screen (@mostafaalagamy)
- Fixed crash when creating local playlists (@mostafaalagamy)

## New Contributors
* @johannesbrauer made their first contribution in https://github.com/MetrolistGroup/Metrolist/pull/2991

**Full Changelog**: https://github.com/MetrolistGroup/Metrolist/compare/v13.1.1...v13.2.0