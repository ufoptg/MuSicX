# Windows Desktop Coral Theme, Spotify Login, Avatar, and Home Parity Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the Windows desktop app look and flow like the Android app: Material 3 coral theme (default seed `0xFFED5564`, dark-mode prefs, pure-black option), Spotify sign-in using the embedded browser, Google-account avatar in the account surface, and the same Home sections Android shows when Spotify is not the home source.

**Architecture:** All desktop UI and settings live in `desktop/src/main/kotlin/com/metrolist/music/desktop/Main.kt`. Shared client logic in `DesktopInnerTube.kt`. Android is the spec only; do not modify Android code. The desktop has no DataStore/Hilt/Android WebView, so we reuse the `:spotify` module's Ktor-based token/auth logic and replace Android WebView cookie extraction with the existing JCEF-based `LoginWebViewWindow` cookie poller.

**Tech Stack:** Kotlin, Compose Desktop Material 3, materialkolor, JCEF (`me.friwi:jcefmaven:152.0.6`), Ktor, InnerTubeX desktop.

**Spec:** Android counterparts —
- Theme: `app/src/main/kotlin/com/metrolist/music/ui/theme/Theme.kt` (seed `Color(0xFFED5564)`, dynamic color on S+, dark/pure-black prefs in `PreferenceKeys.kt`)
- Avatar: `app/src/main/kotlin/com/metrolist/music/ui/screens/HomeScreen.kt` (~line 704/744) using `YouTube.accountInfo()` → `AccountInfo.thumbnailUrl`
- Spotify: `spotify/` module + `SpotifyLoginScreen.kt` (WebView → `sp_dc` cookie → `SpotifyAuth.fetchAccessToken`)
- Home sections: `app/src/main/kotlin/com/metrolist/music/ui/screens/HomeScreen.kt` (`HomeSection` sealed type, lines ~188-212)

## Global Constraints

- No Android source edits; desktop only.
- No version bump; no DB schema changes.
- Strings edits only in `Metrolist/app/src/main/res/values/metrolist_strings.xml` if any (prefer hardcoded English parity notes on desktop for now).
- Do not run gradle locally; CI compiles (Windows).
- ponytail skill: minimal, clean changes.

## Review Focus

1. Theme parity — default seed color and dark/pure-black preference behavior match Android defaults.
2. Spotify login — embedded browser extracts `sp_dc`, token fetch uses reused `:spotify` logic, prefs persist across restarts; sign-out clears state.
3. Avatar — after YouTube sign-in the account surface shows the Google display picture URL from `accountInfo().thumbnailUrl`; falls back cleanly when logged out.
4. Home parity — when Spotify is not the home source, desktop Home shows the Android section set (Speed Dial, Quick Picks, Recently Played, Keep Listening, Forgotten Favorites, From the Community, Mixes, Mood and Genres, New Releases) wired to real data where the desktop client already provides it, otherwise disabled/empty-state consistent with Android.
5. No regressions to existing desktop login/JCEF flow, queue, favorites, or CI.

---

### Task 1: Coral theme on desktop

**Files:** `desktop/src/main/kotlin/com/metrolist/music/desktop/Main.kt`, possibly `DesktopInnerTube.kt` for prefs persistence, `desktop/build.gradle.kts` (add materialkolor dep).

- [ ] Step 1: Replace fixed `MuSicXColors` with a `MetrolistTheme`-style composable: seed default `Color(0xFFED5564)`, dark/light from a persisted dark-mode preference, `pureBlack` support, using materialkolor `rememberDynamicColorScheme` like Android.
- [ ] Step 2: Wire a desktop prefs holder (simple file/SharedPreferences-style JSON next to session.json) for darkMode and pureBlack; Settings > Appearance toggles it.
- [ ] Step 3: Build via CI; commit `feat(desktop): coral Material 3 theme with dark/pure-black prefs`.

### Task 2: Google account avatar on desktop

**Files:** `Main.kt`, `DesktopInnerTube.kt`.

- [ ] Step 1: After session load, call `client.accountInfo()` (or equivalent) to get `thumbnailUrl`; store alongside `accountName`.
- [ ] Step 2: Show it in the Account destination header and the nav-rail account icon (AsyncImage or painter with fallback icon).
- [ ] Step 3: Commit `feat(desktop): use Google account avatar in Account and nav rail`.

### Task 3: Spotify login via embedded browser on desktop

**Files:** `desktop/build.gradle.kts` (add `:spotify` dependency), `Main.kt`, new `SpotifyLoginWindow.kt` (JCEF reuse), prefs persistence.

- [ ] Step 1: Add `implementation(project(":spotify"))` to desktop; expose Spotify settings row → login opens embedded Chromium window to accounts.spotify.com.
- [ ] Step 2: Poll cookies for `sp_dc` via `CefCookieManager` (mirroring LoginWebViewWindow), persist Spotify sp_dc in desktop prefs.
- [ ] Step 3: Stub `SpotifyAuth.fetchAccessToken(spDc, spKey)` integration path; show connected state and sign-out clears the pref.
- [ ] Step 4: Commit `feat(desktop): embedded-browser Spotify login`.

### Task 4: Home sections parity when Spotify is not the home source

**Files:** `Main.kt`, `DesktopInnerTube.kt` as needed.

- [ ] Step 1: Enumerate current desktop Home shelves; map to Android's `HomeSection` list.
- [ ] Step 2: Ensure Speed Dial, Quick Picks, Recently Played, Keep Listening, Forgotten Favorites, From the Community, Mixes, Mood and Genres, New Releases rows exist (real data via InnerTubeX where available; otherwise same empty-state copy as Android).
- [ ] Step 3: Gate Spotify-home injection behind the same `enableSpotify && useSpotifyHome && spDc != ""` conditions as Android once Task 3 lands.
- [ ] Step 4: Commit `feat(desktop): home sections parity with Android`.

### Task 5: Docs/PR sync

- [ ] Step 1: Update roadmap slices 25-28 and PR #53 body with these landed items.
- [ ] Step 2: Commit `docs: sync roadmap/PR with theme, avatar, spotify, home parity`.
