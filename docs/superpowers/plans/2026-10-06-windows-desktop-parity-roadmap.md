# Windows Desktop Parity Roadmap

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement a slice task-by-task. Each remaining slice below will get its own focused plan when picked up.

**Goal:** Bring the Compose Desktop (Windows) app to full Android parity — same UI and features — slice by slice; Spotify on desktop later.

**Architecture:** Desktop module (`desktop/`) reuses InnerTubeX and the shared Android codebase; Android app, Spotify module, and branding stay untouched. No desktop version bump. Builds via GitHub Actions only (`.exe` artifact per push/PR). No database schema changes.

**Tech Stack:** Kotlin, Compose Multiplatform Desktop, vlcj (bundled LibVLC), InnerTubeX, Gradle.

**Spec:** PR #53 (https://github.com/ufoptg/MuSicX/pull/53) description + owner slice log.

## Landed slices (commit on `feat/windows-desktop`)

1. Scaffold + CI (`e8ca4d366`)
2. Android launcher icons (`c24633aaa`)
3. Search via InnerTubeX (`9b2e5d539`)
4. Playback via bundled LibVLC (`b9cab76b3` … `1fef8e61a`)
5. EXE-only CI packaging, ProGuard off (`973746710`, `08ee66ccd`, `e265e97ec`)
6. Progressive streaming via VLC (`ff30bb8ee`)
7. Stage-timing file logging (`23fcda0ec`)
8. Startup perf: uncompressed VLC libs, plugin cache, prewarm (`db67b4708`)
9. Android-style UI: artwork, seek bar, prev/next, auto-advancing queue (`ea972f638`)
10. Volume control on now-playing bar (`a82b6dca0`)
11. Sync main → v13.14.2 (merge `41548816e`)
12. Navigation-rail scaffold (`099e5bc50`)
13. Android-style Home/Library/full player + smoother UI (`62fff9538`)
14. Search Enter key, LRU artwork cache, temp cleanup (`da951ee7d`)
15. Persistent favorites/history + queue panel with shuffle/repeat (`7edfe3e48`)
15. Persistent favorites/history + queue panel with shuffle/repeat (`7edfe3e48`)
18. Real YTM home browse with search-seeded fallback (`4a5e3ef`, PR #53 item 18)
19. **Desktop login shell** (`44565f0ed`): Account rail destination + LoginScreen — paste Cookie header (validated for SAPISID=), persisted to `%APPDATA%/MuSicX/session.json`, injected into InnerTubeX (`cookie` + `useLoginForBrowse`); Home reloads on sign-in/out.
20. **Embedded WebView login** (deferred/failed) (`a65f55c6d`) — JavaFX WebView; compiled and rendered but Google blocks it from completing sign-in (`youtube.com/oops`).
21. **Synced library** (`3ab5c86be`) — Liked + Playlists tabs via InnerTubeX browse, sign-in hint when logged out.
22. Browser CDP auto-import login (`531b2b9ca`) — superseded by #23 after the temp-profile spawn + DPAPI parsing proved unreliable.
23. **Embedded Chromium JCEF login** (`cead83c59`, native-version pin `0015f2191`, Windows API fix `d657503c0`, global CefApp init `2c1d68a82`, final nullable fix by human `c6a36fed6`) — primary desktop login: embedded Chromium window pointed at Google sign-in URL, 2s cookie poll via `CefCookieManager`, hand off `SAPISID` header to the existing session store. Stray committed `musicx-desktop.log` removed.
24. Queue panel now has a Close button (`ed0cac3bc`).
25. **Settings parity** (`52963cc34`, `5bf42b217`) — Settings destination with section list mirroring Android; all Android settings sections present on desktop, disabled where n/a.

## Global Constraints

- Do not bump the app version (core team only).
- Do not modify the database schema unless absolutely necessary.
- Strings edits only in `Metrolist/app/src/main/res/values/metrolist_strings.xml` (English default), if any.
- Desktop builds/tests via GitHub Actions Windows CI; `./gradlew :app:assembleFossDebug` must keep passing for Android.
- AGENTS.md rules apply (ponytail skill for minimal/clean changes, no readme/markdown edits unless verified with a human).

## Remaining roadmap (in priority order)

- [x] **Slice A: Real YTM home browse** (PR #53 item 18) — real `FEmusic_home` browse + carousel shelf parsing, search-seeded fallback kept. Landed in `4a5e3ef`.
- [x] **Slice B: Synced library** — Liked + Playlists tabs via InnerTubeX browse, sign-in hint when logged out. Landed in `3ab5c86be` (PR #53 item 21). — pull user playlists/library from YTM account once logged in; merge with local `DesktopLibraryStore` favorites/history.
- [x] **Slice C: Account shell / login on desktop** — paste-cookie sign-in shell persisting session.json; enables account-backed browse. Landed in `44565f0ed` (PR #53 item 19).
- [-] **Slice C2: Embedded WebView login** (deferred/failed) — JavaFX WebView compiled and rendered but Google blocks it from completing sign-in (`youtube.com/oops`), so it can never authenticate. Artefacts in `a65f55c6d`; theembedded option was removed in `531b2b9ca`'s rewrite.
- [-] **Slice C3: Browser CDP auto-import login** (not used now) — launched a temp-profile Chrome/Edge window, polled CDP `Network.getAllCookies` for `SAPISID`. Implemented in `531b2b9ca`; proved fragile because the temp-profile spawn exited before detection and DPAPI/cookie-store parsing was unreliable.
- [x] **Slice C4: Embedded Chromium JCEF login** — primary desktop login: Account → "Sign in (embedded browser)" opens an embedded Chromium window (`me.friwi:jcefmaven:152.0.6` + XP native bundle) straight at Google sign-in. Every 2s a cookie poll confirms `SAPISID` via `CefCookieManager`, then the cookie header is given to `DesktopSessionStore` + `client.setSessionCookie`. The window polls reliably (2026-10-07 log shows `JCEF cookies poll ... SAPISID present`). Landed in `cead83c59`, native-version pin in `0015f2191`, Windows API fix in `d657503c0`, global CefApp init in `2c1d68a82`, manual nullable-call fix by the human in `c6a36fed6`.
- [ ] **Slice D: Coral theme polish pass** — align desktop palette/typography/shapes with Android Material 3 coral theme.
- [ ] **Slice E: Spotify on desktop** — port the Spotify integration to desktop after YTM parity is done.
- [ ] **Slice F: MSI packaging** — switch CI to also produce `.msi` when ready for release.

## Review Focus

- Home shelves must still render when YTM home fetch fails → keep search-seeded fallback.
- Login on desktop cannot embed Android WebView assumptions → verify the flow actually authenticates before Slice B work.
- Queue/shuffle/repeat state must survive slice refactors.
- Artwork cache bound (LRU 200) must hold after theme/polish changes.
- Windows CI green (`Build Windows Installers`) before considering a slice done.
