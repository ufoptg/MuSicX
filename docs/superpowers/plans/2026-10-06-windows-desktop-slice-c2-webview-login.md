# Slice C2: Embedded WebView Login (Desktop)

> **For agentic workers:** REQUIRED SUB-SKILL: superpowers:executing-plans (native execution).

**Goal:** Match Android's login UX — an in-app WebView where the user signs in to YouTube Music/Google interactively and cookies are captured automatically, no DevTools copy-paste.

**Architecture:** Compose Desktop can't host Android's `WebView`, so embed JavaFX WebView via a Swing interop: a Compose `Window` hosting a `SwingPanel` → `JFXPanel` → `WebView`. Poll `document.cookie` after each successful load (YouTube web itself reads `SAPISID` from `document.cookie` when computing SAPISIDHASH, so it is JS-readable) and hand it to the existing slice-C flow (`DesktopSessionStore.save` + `client.setSessionCookie`). Keep the paste-cookie and system-browser options as fallbacks if Google blocks embedded sign-in.

**Tech Stack:** Kotlin, Compose Desktop, JavaFX (`javafx-base/graphics/web/swing`), SwingPanel interop.

**Spec:** PR #53 Slice C follow-up; user request to match Android login.

## Global Constraints

- No version bump; no DB schema changes; ponytail; Windows CI green; no local exe build.

## Review Focus

- `document.cookie` readable for `SAPISID=` after redirect back to music.youtube.com (Android reliability parity).
- Google blocking the embedded browser → user can still fall back to paste/browser sign-in.
- JFX toolkit initialized once; dialog closes and cookies captured exactly once (no double-save).
- Cookie value must never be written to DesktopLog.

---

### Task 1: JavaFX deps

**Files:**
- Modify: `desktop/build.gradle.kts`

- [ ] Step 1: Add `implementation("org.openjfx:javafx-base:21.0.5")`, `javafx-graphics`, `javafx-web`, `javafx-swing` (same version, classifier blank; use `org.openjfx` artifacts with the default platform jar). Keep versions consistent with JDK 21 toolchain.
- [ ] Step 2: Push; CI build verifies resolution.

### Task 2: Embedded login window

**Files:**
- Create: `desktop/src/main/kotlin/com/metrolist/music/desktop/LoginWebViewWindow.kt`

- [ ] Step 1: Composable `LoginWebViewWindow(onSignedIn: (String) -> Unit, onClose: () -> Unit)` — a Compose `Window` whose content is `SwingPanel(factory = { panel -> ... })`.
- [ ] Step 2: In the factory: `JFXPanel()` to start the JFX toolkit (`Platform.setImplicitExit(false)`), then `javafx.application.Platform.runLater { val webView = WebView(); ... }`. Create `WebEngine`, load `https://music.youtube.com`, attach a load-state listener; on `SUCCEEDED`, `executeScript("document.cookie") as? String`; when it contains `SAPISID=` and location host ends with `youtube.com`, invoke `onSignedIn(cookie)` once and close the window.
- [ ] Step 3: Handle script errors / null cookie silently (just retry on next load).

### Task 3: Wire into LoginScreen

**Files:**
- Modify: `desktop/src/main/kotlin/com/metrolist/music/desktop/Main.kt`

- [ ] Step 1: Add `var showLoginWindow by remember { mutableStateOf(false) }` in the Account branch state (or in MuSicXApp).
- [ ] Step 2: In `LoginScreen`, primary button "Sign in (embedded browser)" sets `showLoginWindow = true`; when true, render `LoginWebViewWindow(onSignedIn = { cookie -> same handler as paste path }, onClose = { showLoginWindow = false })`.
- [ ] Step 3: Keep "Open in system browser" + paste field as fallback controls under a divider.

### Task 4: Docs + CI

- [ ] Commit + push; verify CI Windows installer build passes (downloads javafx jars).
- [ ] Update roadmap (Slice C2 landed) + PR #53 body/comment; docs commit.
