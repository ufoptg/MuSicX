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
14. Search Enter key, LRU artwork cache, temp cleanup (`259fdcc`)
15. Persistent favorites/history + queue panel with shuffle/repeat (`fd1b19d`)
19. **Desktop login shell (`f7c39716c`)**: Account rail destination + LoginScreen — browser sign-in, paste Cookie header (validated for SAPISID=), persisted to `%APPDATA%/MuSicX/session.json`, injected into InnerTubeX (`cookie` + `useLoginForBrowse`); Home reloads on sign-in/out.
18. Real YTM home browse with search-seeded fallback (`9804215`, PR #53 item 18)

## Global Constraints

- Do not bump the app version (core team only).
- Do not modify the database schema unless absolutely necessary.
- Strings edits only in `Metrolist/app/src/main/res/values/metrolist_strings.xml` (English default), if any.
- Desktop builds/tests via GitHub Actions Windows CI; `./gradlew :app:assembleFossDebug` must keep passing for Android.
- AGENTS.md rules apply (ponytail skill for minimal/clean changes, no readme/markdown edits unless verified with a human).

## Remaining roadmap (in priority order)

- [x] **Slice A: Real YTM home browse** (PR #53 item 18) — real `FEmusic_home` browse + carousel shelf parsing, search-seeded fallback kept. Landed in `9804215`.
- [ ] **Slice B: Synced library/playlists** — pull user playlists/library from YTM account once logged in; merge with local `DesktopLibraryStore` favorites/history.
- [x] **Slice C: Account shell / login on desktop** — paste-cookie sign-in shell persisting session.json; enables account-backed browse. Landed in `f7c39716c` (PR #53 item 19).
- [x] **Slice C2: Embedded WebView login** — JavaFX WebView sign-in window capturing the session cookie automatically (Android parity), paste/system-browser kept as fallback. Landed in `0170e4613` (PR #53 item 20).
- [ ] **Slice D: Coral theme polish pass** — align desktop palette/typography/shapes with Android Material 3 coral theme.
- [ ] **Slice E: Spotify on desktop** — port the Spotify integration to desktop after YTM parity is done.
- [ ] **Slice F: MSI packaging** — switch CI to also produce `.msi` when ready for release.

## Review Focus

- Home shelves must still render when YTM home fetch fails → keep search-seeded fallback.
- Login on desktop cannot embed Android WebView assumptions → verify the flow actually authenticates before Slice B work.
- Queue/shuffle/repeat state must survive slice refactors.
- Artwork cache bound (LRU 200) must hold after theme/polish changes.
- Windows CI green (`Build Windows Installers`) before considering a slice done.
