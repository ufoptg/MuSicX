# Code Review Report: feat/windows-desktop vs main

## Change Summary
- **Branch**: `feat/windows-desktop` vs `main`
- **Lines changed**: +6907, -35 across 42 files
- **Purpose**: Add full desktop (Windows/macOS/Linux) support for MuSicX

## Reviewer's Notes
*This review follows the code-review-and-quality skill's five-axis framework. The change is extremely large and would normally require splitting into multiple PRs.*

---

## 1. Correctness

### ✅ Positive findings
- **Error handling**: All major functions use `runCatching` or try/catch/finally properly
- **Edge cases**: `trim().isEmpty()` checks in search functions, `require(option in 0..5)` in SleepTimer, `check()` for download size validation
- **State consistency**: `playFrom` properly resets `busyId`, `error`, `positionMs`, `durationMs` in finally block
- **Re-check pattern**: `loadHome` re-checks `DesktopSpotify.hideYoutubeHome(livePrefs)` after await — good defensive pattern
- **Mutex usage**: `DesktopSpotify.refresh` uses `Mutex()` for thread-safe token refresh

### ⚠️ Concerns
- **Missing null checks**: In `Main.kt`, `nowPlaying?.videoId` is used as `LaunchedEffect` key without null guard in some paths
- **Search empty state**: `runSearch` sets `error = "No results found"` only when ALL result lists are empty, but individual searches could fail differently
- **Download race condition**: `toggleDownload` checks `hit.videoId in downloadingIds` but the check and launch are not atomic — could lead to duplicate downloads
- **SABR not supported**: `play()` checks `check(stream.sabrBootstrap == null)` with message "SABR streams are not supported yet" — this is a hard failure rather than graceful degradation

---

## 2. Readability & Simplicity

### ✅ Positive findings
- **Descriptive names**: `DesktopSpotify`, `DesktopInnerTube`, `DesktopAudioPlayer` clearly indicate purpose
- **Consistent patterns**: `withContext(Dispatchers.IO)` for IO operations, `runCatching` for error handling
- **Good doc comments**: Most files have project header and function-level documentation
- **Kotlin idioms**: Proper use of `lateinit`, `by lazy`, `sealed classes`, `data classes`

### ⚠️ Concerns
- **Massive Main.kt (1140+ lines)**: Violates the "keep files under healthy boundary" guideline. The `MuSicXApp` composable is doing too much:
  - >40 `remember {}` state variables
  - Nested `when` blocks for navigation (698-941)
  - Multiple `LaunchedEffect` blocks (645-1065)
  - The file is essentially an "application kernel" rather than a focused composable

- **Repeated patterns**: The `when { hideYoutubeHome -> ... spotifyHomeActive -> ... else -> ... }` pattern appears in at least 3 places in Main.kt (lines 257-262, 614-619, 657-665)

- **Clever tricks**: 
  - `DesktopInnerTube.walk()` is a recursive JSON traverser that's efficient but hard to follow
  - `firstText()` helper is well-named but nested 4+ levels deep in `parseBrowseHit`

- **File size threshold**: Main.kt at 1140+ lines exceeds the ~1000 line inspection signal. The entire desktop change at 6907 lines is well beyond the "too large" threshold.

---

## 3. Architecture

### ✅ Positive findings
- **Pattern consistency**: Desktop module mirrors Android counterparts:
  - `DesktopInnerTube` ↔ Android `InnerTube`
  - `DesktopAudioPlayer` ↔ Android `ExoPlayer`
  - `DesktopSpotify` ↔ Android `SpotifyTokenManager`
  - `DesktopLibraryStore` ↔ DataStore/persistence
  - `DesktopPrefs`/`DesktopPrefsStore` ↔ Android SharedPreferences
- **Clean module boundaries**: All desktop code in `com.metrolist.music.desktop` package
- **Type boundaries**: `SearchHit`, `HomeRow`, `PlaylistHit`, `DownloadInfo`, `DesktopLibraryData` are well-defined data classes
- **Feature-specific logic staying put**: Spotify, InnerTube, and VLC logic are all in the desktop module, not leaking to shared code

### ⚠️ Concerns
- **God data class**: `DesktopPrefs` has 75 properties including Discord tokens, Last.fm tokens, SponsorBlock settings, Equalizer settings — this is a "kitchen sink" that should perhaps be broken into sub-modules:
  - `AppearancePrefs`
  - `PlaybackPrefs` 
  - `SpotifyPrefs`
  - `PrivacyPrefs`
  - `SponsorBlockPrefs`
  - `EqualizerPrefs`

- **Feature logic in shared-ish module**: The `DesktopPrefs` data class is purely desktop but has Android-equivalent concepts mapped differently — need to ensure this doesn't create drift

- **Type boundary explicitness**: The `DesktopInnerTube` companion has `audioQualityForSetting: Int` mapping — should this be a proper `Enum` or `SealedClass` instead of magic numbers?

- **Dependency graph**: Desktop adds `vlcj` (native libs), `innertubex-desktop` — these are appropriate but increase the bundle size significantly

- **Refactor opportunity**: The `homeFeed()` function has a real-browse-fallback-categories pattern that could be extracted into a reusable "feed loader" helper

---

## 4. Security

### ✅ Positive findings
- **No hardcoded secrets**: All tokens default to empty strings, loaded from user prefs
- **Cookie management**: `setSessionCookie` and `clearSpotifyCefCookies` properly manage auth state
- **Input validation**: `trim().isEmpty()` checks on queries, `require()` on sleep timer options
- **Output encoding**: Compose UI uses proper Material theming, no raw HTML rendering

### ⚠️ Concerns
- **Plaintext prefs persistence**: `DesktopPrefs` stores sensitive values in plain JSON:
  - `spDc` (Spotify cookie) — could be harvested
  - `spKey` (Spotify companion cookie)
  - `spotifyAccessToken` — full OAuth token
  - `discordToken` — Discord rich presence token
  - `lastFmToken` — Last.fm scrobbling token
  - `aiKey` — API key for AI providers

  **Recommendation**: These should be encrypted before persistence or stored in Android Keystore-equivalent for desktop (e.g., encrypted file, OS keyring).

- **CEF cookie access**: `SpotifyLoginWindow` uses `CefCookieManager.getGlobalManager()` — this accesses global browser cookies, which could include user's personal cookies. The `clearSpotifyCefCookies` function properly scopes to `spotify.com` domains, but the global manager access could be broader.

- **No input sanitization on display**: Track titles/subtitles from YouTube API are displayed directly in Compose without encoding — while unlikely to cause XSS in this context, it's a potential injection vector if YouTube titles contain special characters.

---

## 5. Performance

### ✅ Positive findings
- **Appropriate timeouts**: `startDirectStream` has 40s timeout with 100ms polling = 40 iterations max
- **Real-time throughput awareness**: Comments note that "googlevideo delivers progressive streams at ~real-time rate"
- **Coroutines properly scoped**: `Dispatchers.IO` for network/IO, `Dispatchers.Main` implicit for UI
- **Memory management**: `playFile` uses `tempFile.deleteOnExit()`, `clearSongCache` cleans temp files

### ⚠️ Concerns
- **`dirSize()` performance**: `dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }` walks entire directory tree — if user has many downloads, this could be slow. Called on every `sizeBytes()` invocation.
- **Cache warming on every prefs change**: `DesktopDownloads.songCacheFiles()` lists system temp dir every call
- **`homeFeed()` fallback**: When real home browse fails, it falls back to 13 category searches — each `searchSongs` is a network request. This could be slow on poor connections.
- **No pagination on list endpoints**: The `searchSongs`, `searchAlbums`, etc. return all results; no page parameter. For a desktop app this is probably fine, but worth noting.
- **`repeat(400)` in `startDirectStream`**: 40s wait for VLC to start playing — could be improved with callback-based detection if VLC supports it.

---

## Presumptive Blockers (Must Address Before Merge)

### 1. **File size decomposition** — HIGH priority
- Main.kt at 1140+ lines exceeds healthy file size boundary
- The entire change at 6907 lines is far too large for a single PR
- **Action**: Split into a stack of PRs; extract Main.kt components into separate files

### 2. **Sensitive data in plaintext prefs** — HIGH priority
- `DesktopPrefs` stores `spDc`, `spKey`, `spotifyAccessToken`, `discordToken`, `lastFmToken`, `aiKey` in plain JSON
- **Action**: Add encryption before saving, or use OS keyring/encrypted storage

### 3. **Structural decomposition of DesktopPrefs** — MEDIUM priority
- 75-property data class is unwieldy
- **Action**: Break into focused sub-modules (AppearancePrefs, PlaybackPrefs, etc.)

### 4. **Main.kt state management refactor** — MEDIUM priority
- 40+ `remember {}` states make the composable hard to understand
- **Action**: Extract navigation state, settings state, and playback state into separate ViewModels or state holders

---

## Optional Improvements

### 1. **Add more tests**
- Currently only 1 test file (`PlayerSettingsTest.kt`) with 4 tests
- Consider adding tests for: `DesktopInnerTube.searchSongs`, `DesktopAudioPlayer.playFile`, `DesktopLibraryStore save/load cycles`

### 2. **Optimize directory size calculation**
- `DesktopDownloads.dirSize()` walks entire directory tree
- **Action**: Cache the size or compute incrementally on file add/remove

### 3. **Reduce repeated conditional patterns**
- The `when { hideYoutubeHome -> ... }` pattern in Main.kt appears multiple times
- **Action**: Consider a derived boolean `showYouTubeHome = !hideYoutubeHome && !spotifyHomeActive` and derive other booleans from it

### 4. **SABR graceful degradation**
- Currently hard-stops with "SABR streams are not supported yet"
- **Action**: Consider falling back to lowest quality instead of erroring

### 5. **Enum for audio quality setting**
- `audioQualityForSetting: Int` uses magic numbers (0, 2, else)
- **Action**: Replace with `enum class AudioQualitySetting { AUTO, LOW, HIGH }`

---

## Verdict

**Overall: Request changes** — The change introduces significant desktop functionality but has structural issues that need addressing before merge.

**Approve after fixes**: If the above presumptive blockers are addressed (especially splitting the change and encrypting sensitive prefs), the change can be approved.

**Key recommendation**: Split this into a stack of PRs:
1. **PR 1**: Desktop module structure + core infrastructure (InnerTube, AudioPlayer,Prefs)
2. **PR 2**: UI composition (split Main.kt into components)
3. **PR 3**: Spotify integration and login flow
4. **PR 4**: Features (library, downloads, settings)
5. **PR 5**: Tests and bug fixes

This would make each change reviewable (~100-300 lines) while maintaining the overall feature cohesion.