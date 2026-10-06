# Slice C: Desktop Login Shell

> **For agentic workers:** REQUIRED SUB-SKILL: superpowers:executing-plans (native execution).

**Goal:** Let a desktop user sign in to YouTube Music so future slices can use account-backed browse data (synced library/playlists).

**Architecture:** Since the desktop module can't reuse Android's WebView, sign-in is a paste-cookie shell: the app opens music.youtube.com in the system browser, the user copies their cookie header from DevTools, and the app persists it in `session.json` (next to `library.json`) and injects it into the InnerTubeX session (`cookie` + `useLoginForBrowse`). Keep it minimal (ponytail); a real embedded WebView flow is deferred.

**Tech Stack:** Kotlin, Compose Desktop Material 3, InnerTubeX session API.

**Spec:** `docs/superpowers/plans/2026-10-06-windows-desktop-parity-roadmap.md` Slice C.

## Global Constraints

- No version bump; no DB schema changes; ponytail; CI Windows build green.

## Review Focus

- Invalid cookie (no `SAPISID=`) → clear error, nothing persisted.
- Missing/corrupt `session.json` on launch → treated as signed out, no crash.
- Sign-out clears file + InnerTubeX session and Home reloads without account data.
- Cookie never logged (DesktopLog must not print it).

---

### Task 1: Session store + innertube wiring

**Files:**
- Create: `desktop/src/main/kotlin/com/metrolist/music/desktop/DesktopSessionStore.kt`
- Modify: `desktop/src/main/kotlin/com/metrolist/music/desktop/DesktopInnerTube.kt`

- [ ] Step 1: Create `DesktopSessionStore` mirroring `DesktopLibraryStore` (same base dir logic), data class `DesktopSessionData(val cookie: String? = null)`, `load()`, `save(data)`, `clear()`.
- [ ] Step 2: Add to `DesktopInnerTube`: `fun setSessionCookie(cookie: String?) { innerTube.cookie = cookie; innerTube.useLoginForBrowse = !cookie.isNullOrBlank() }` and `val signedIn: Boolean get() = !innerTube.sessionSnapshot().cookie.isNullOrBlank()`.
- [ ] Step 3: Push; CI typecheck covers compile (no local exe/gradle build).

### Task 2: Account destination + LoginScreen

**Files:**
- Modify: `desktop/src/main/kotlin/com/metrolist/music/desktop/Main.kt`

- [ ] Step 1: Add `Account("Account", Icons.Default.AccountCircle)` to `Destination` enum.
- [ ] Step 2: In `MuSicXApp`: on startup (`LaunchedEffect(Unit)`), load `DesktopSessionStore`; if cookie present, `client.setSessionCookie(cookie)`. Add `var signedIn by remember { mutableStateOf(false) }` set from store.
- [ ] Step 3: Add `Destination.Account -> LoginScreen(...)` branch in the Crossfade when-block.
- [ ] Step 4: Implement `LoginScreen(signedIn, onSignIn(cookie), onSignOut())`: explains steps, "Open YouTube Music in browser" button (`java.awt.Desktop.getDesktop().browse(URI("https://music.youtube.com"))`), cookie TextField, Sign in button (validates `SAPISID=` substring, shows error otherwise), Sign out button when signed in.
- [ ] Step 5: Wire `onSignIn`: persist to store, `client.setSessionCookie(cookie)`, `signedIn = true`, `homeRows = emptyList()` (triggers `loadHome` via retry path — or call loadHome through the existing pattern: set `homeRows = emptyList()` then `loadHome()` if accessible; simplest is resetting state so the `LaunchedEffect(homeRows.isEmpty())`-style reload or retry button works — confirm actual reload trigger in Main.kt before coding).
- [ ] Step 6: Commit + push.

### Task 3: Verify + docs

- [ ] CI green on push (Windows installer build).
- [ ] Update roadmap (Slice C checked) and PR #53 body/comment; commit docs.
