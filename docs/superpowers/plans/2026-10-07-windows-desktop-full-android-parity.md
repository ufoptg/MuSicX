# Windows Desktop Full Android Parity — Features & Settings Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Bring every non-Android-specific Android capability over to the Windows desktop build, in priority order, so the desktop app feels and flows like the Android app.

**Architecture:** Desktop code lives in `desktop/src/main/kotlin/com/metrolist/music/desktop/`. The desktop already has `Main.kt` (all composables), `DesktopInnerTube.kt` (InnerTubeX client), `DesktopAudioPlayer.kt` (VLC/vlcj), `DesktopLibraryStore.kt`, `DesktopSessionStore.kt`, `DesktopPrefsStore.kt`, `DesktopLog.kt`. Android code is the spec only. Android-specific APIs (WebView except JCEF, Car, widgets, QuickSettings tiles, Notifications/MediaSession, AlarmManager, Google Cast SDK) are explicitly excluded. Executable verification happens in GitHub Actions Windows CI — no local gradle runs.

**Tech Stack:** Kotlin, Compose Desktop Material 3, materialkolor, JCEF, vlcj/LibVLC, InnerTubeX desktop, `:spotify` module (pure Kotlin/network), Ktor.

**Spec:** Android source under `app/src/main/kotlin/com/metrolist/music/` — particularly `ui/screens/`, `ui/screens/settings/`, `viewmodels/`, `playback/`, plus `spotify/` module. Gap inventory: this plan's companion analysis (Android 32 routes/screens vs desktop destinations).

## Global Constraints

- Do NOT edit Android source (`app/`), `spotify/` module code, or `innertube` models unless a desktop bug forces it.
- Do NOT bump the version. No DB schema changes.
- Strings edits only in `Metrolist/app/src/main/res/values/metrolist_strings.xml` (English default) if needed.
- Remove `settings/android_auto` from the desktop nav — Android Auto is Android-specific.
- Do NOT port: Google Cast, Android widgets, QuickSettings tiles, Notifications/MediaSession (OS equivalent TBD), Android Auto, AlarmManager-based alarms, crash activities, Android WebView, ShazamKit microphone widget.
- Desktop build/test via `:app:assembleFossDebug` must keep passing; CI compiles desktop.

## Review Focus

1. Downloads work offline after app restart — downloaded songs survive, history/favorites still persist.
2. Home sections match Android's set when Spotify is not the home source.
3. Album/Artist/Playlist/Podcast detail screens navigate correctly and play through the existing player.
4. Downloads settings rows actually control the download dir/quality; Storage settings clear the real cache.
5. Spotify login (embedded browser) stores sp_dc and flips Settings integration state; sign-out clears it.
6. Equalizer/SponsorBlock settings either apply via VLC filters or stay honestly disabled — no fake toggles.
7. Every settings row on desktop either does something real or is explicitly marked "Not available on desktop".

---

## Task 1: Remove Android Auto from desktop

**Files:**
- Modify: `desktop/src/main/kotlin/com/metrolist/music/desktop/Main.kt`

- [ ] **Step 1:** Find the `android_auto` settings sub-screen branch (search `android_auto`); remove the branch and any nav entry to it.
- [ ] **Step 2:** Remove any `AndroidAuto` strings/rows from settings lists.
- [ ] **Step 3:** Commit `chore(desktop): drop Android Auto settings (Android-specific)`.

## Task 2: Real desktop preferences store for all settings

**Files:**
- Modify: `desktop/src/main/kotlin/com/metrolist/music/desktop/DesktopPrefsStore.kt`
- Modify: `desktop/src/main/kotlin/com/metrolist/music/desktop/Main.kt`

- [ ] **Step 1:** Extend `DesktopPrefsStore` JSON schema with keys for every desktop-exposed preference: dynamicTheme, selectedThemeColor, darkMode, pureBlack, content language/country, ai provider/key, player quality/loudness/crossfade/gapless, storage (download dir, cache size caps), privacy (pause history, clear cache on exit), spotify spDc, enableSpotify, useSpotifyHome, spotifyHomeOnly, discord/lastfm tokens, sponsorblock toggles, equalizer enabled + active profile.
- [ ] **Step 2:** Wire the existing placeholder `SettingsToggleItem`/`SettingsRowItem` rows in `Main.kt` to read/write these keys where a real backend exists; leave the rest disabled with the existing "Not available on desktop" subtitle.
- [ ] **Step 3:** Commit `feat(desktop): persist settings via DesktopPrefsStore`.

## Task 3: Downloads / offline songs

**Files:**
- Create: `desktop/src/main/kotlin/com/metrolist/music/desktop/DesktopDownloads.kt`
- Modify: `desktop/src/main/kotlin/com/metrolist/music/desktop/DesktopInnerTube.kt` (stream URL + metadata), `DesktopLibraryStore.kt` (downloaded index), `Main.kt` (Downloads row in Library, download action in player/queue, Storage settings values), `DesktopAudioPlayer.kt` (prefer local file if present).

- [ ] **Step 1:** Implement `DesktopDownloads` with a coroutine-based downloader that resolves a stream URL via InnerTubeX, writes audio to `%APPDATA%/MuSicX/downloads/<videoId>.<ext>`, and records metadata (videoId, title, artist, duration, thumbnail path) in `DesktopLibraryStore`.
- [ ] **Step 2:** Add a Library "Downloads" auto-list and a download/remove action on the player overlay + queue rows.
- [ ] **Step 3:** `DesktopAudioPlayer` plays from the downloaded file when present instead of streaming.
- [ ] **Step 4:** Wire Storage settings rows to show real cache/download sizes and clear them.
- [ ] **Step 5:** Commit `feat(desktop): offline downloads and storage management`.

## Task 4: Home sections parity

**Files:**
- Modify: `desktop/src/main/kotlin/com/metrolist/music/desktop/Main.kt`, `DesktopInnerTube.kt`

- [ ] **Step 1:** Audit current Home rendering; map existing shelves to Android's `HomeSection` list: SpeedDial, QuickPicks, RecentlyPlayed, DailyDiscover, KeepListening, AccountPlaylists, ForgottenFavorites, FromTheCommunity, SimilarRecommendation, HomePageSection, MoodAndGenres.
- [ ] **Step 2:** Ensure rows exist for: Recently Played, Quick Picks, Speed Dial, Keep Listening, Forgotten Favorites, From the Community, Mixes, Mood and Genres, New Releases — wired to InnerTubeX home/explore/related data where available; empty-state parity copy otherwise.
- [ ] **Step 3:** Gate the YouTube home list behind the same condition as Android: hidden when `enableSpotify && useSpotifyHome && spDc != ""`.
- [ ] **Step 4:** Commit `feat(desktop): home sections parity with Android`.

## Task 5: Detail screens (Album / Artist / Playlist / Podcast)

**Files:**
- Modify: `desktop/src/main/kotlin/com/metrolist/music/desktop/Main.kt`, `DesktopInnerTube.kt`

- [ ] **Step 1:** Add composables and a navigation stack for `album/{id}`, `artist/{id}`, `online_playlist/{id}`, `online_podcast/{id}` with hero, metadata, song list, play-all/shuffle actions.
- [ ] **Step 2:** Make Library playlist rows, Home playlist rows, and search results tappable into these screens.
- [ ] **Step 3:** Commit `feat(desktop): album/artist/playlist/podcast detail screens`.

## Task 6: History, Stats, Search results

**Files:**
- Modify: `Main.kt`, `DesktopInnerTube.kt`, `DesktopLibraryStore.kt`

- [ ] **Step 1:** Promote History from a Library tab to its own screen matching Android HistoryScreen (grouped by date, clear-all action).
- [ ] **Step 2:** Add Stats screen (play counts, top artists/tracks from DesktopLibraryStore) matching Android StatsScreen summary level.
- [ ] **Step 3:** Extend Search to show albums/artists/playlists/songs sections (online results) instead of songs only.
- [ ] **Step 4:** Commit `feat(desktop): history screen, stats, richer search`.

## Task 7: Sleep timer + player settings that VLC supports

**Files:**
- Modify: `DesktopAudioPlayer.kt`, `Main.kt`, `DesktopPrefsStore.kt`

- [ ] **Step 1:** Implement a sleep timer (15/30/45/60 min + end-of-track) with fade-out, persisted default in prefs, surfaced in the player overlay.
- [ ] **Step 2:** Wire Player settings rows to VLC options that actually work on desktop: gapless, crossfade (via VLC filters), default quality (choose stream bitrate), normalization/loudnorm via VLC filter.
- [ ] **Step 3:** Commit `feat(desktop): sleep timer and VLC-backed player settings`.

## Task 8: Lyrics

**Files:**
- Create: `desktop/.../DesktopLyrics.kt`, modify `Main.kt`, `DesktopInnerTube.kt`

- [ ] **Step 1:** Fetch synced/plain lyrics via the same providers Android uses (LRCLIB etc. — reuse InnerTubeX where possible).
- [ ] **Step 2:** Show a lyrics panel in the full player overlay with current-line highlighting.
- [ ] **Step 3:** Commit `feat(desktop): synced lyrics panel`.

## Task 9: SponsorBlock + Equalizer via VLC

**Files:**
- Modify: `DesktopAudioPlayer.kt`, `Main.kt`, `DesktopPrefsStore.kt`

- [ ] **Step 1:** Port `SponsorBlockManager` logic (network + position polling) to skip segments during VLC playback; make the SponsorBlock settings rows real toggles.
- [ ] **Step 2:** Apply equalizer profiles via VLC's `equalizer` audio filter; Eq Wizard row shows "Not available on desktop" (microphone calibration is Android-specific).
- [ ] **Step 3:** Commit `feat(desktop): SponsorBlock skipping and VLC equalizer`.

## Task 10: Spotify integration on desktop

**Files:**
- Modify: `desktop/build.gradle.kts` (add `implementation(project(":spotify"))`)
- Create: `desktop/.../SpotifyLoginWindow.kt` (JCEF reuse of LoginWebViewWindow), `DesktopSpotify.kt` wrapper around `SpotifyAuth`/`SpotifyTokenManager`
- Modify: `Main.kt` (Spotify settings rows, login flow state, home gating), `DesktopInnerTube.kt`

- [ ] **Step 1:** Add the spotify module dependency; create `DesktopSpotify` that persists sp_dc, fetches access tokens via `SpotifyAuth`/`SpotifyTokenManager` patterns, and clears state on sign-out.
- [ ] **Step 2:** Login flow reuses the JCEF embedded browser window against accounts.spotify.com, polling cookies for `sp_dc` (mirror LoginWebViewWindow).
- [ ] **Step 3:** Wire Settings > Integrations > Spotify to show Connected/Disconnected and a login button; honor `enableSpotify`/`useSpotifyHome`/`spotifyHomeOnly` prefs for home gating.
- [ ] **Step 4:** Commit `feat(desktop): Spotify login and integration state`.

## Task 11: Remaining settings honesty pass

**Files:** `Main.kt`, `DesktopPrefsStore.kt`, new small files as needed

- [ ] **Step 1:** Content settings: wire language/country to InnerTubeX client config if exposed; otherwise keep disabled with note.
- [ ] **Step 2:** AI settings: store provider/key/model; if AI translation is not wired, keep rows disabled with note.
- [ ] **Step 3:** Privacy settings: wire pause-history, clear-cache-on-exit to real behavior; mark screenshot/picture-in-picture as unavailable.
- [ ] **Step 4:** Backup/Restore: implement JSON export of library+prefs and import; file picker instead of SAF.
- [ ] **Step 5:** Updater: check latest GitHub release tag vs current version and show result; no silent install.
- [ ] **Step 6:** Storage: show real download/cache sizes and Clear buttons.
- [ ] **Step 7:** Commit `feat(desktop): wire remaining settings or mark unavailable`.

## Task 12: Docs / PR sync

- [ ] Update roadmap landed slices and PR #53 with the parity work.
- [ ] Commit `docs: sync roadmap/PR with desktop parity features`.
