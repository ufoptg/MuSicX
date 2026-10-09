# Windows Desktop UI Parity And Settings Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the Windows desktop app present the same look-and-feel and settings sections as the Android app.

**Architecture:** Main.kt already holds all desktop composables. We add a Settings destination (Home, Search, Library, Settings, Account) that opens a SettingsScreen mirroring Android's `settings/*` routes. Each section screen is a small composable with the same controls the Android counterpart uses (collapsed set to the existing shared state where it exists, blank toggles with desktop-reasonable defaults where not).

**Tech Stack:** Kotlin, Compose Desktop Material 3, vlcj (unchanged), InnerTubeX (unchanged).

**Spec:** PR #53 conversation notes plus `app/src/main/kotlin/com/metrolist/music/ui/screens/NavigationBuilder.kt` settings routes. No public API changes.

## Global Constraints

- Only edit `docs/superpowers/plans/*` for docs; do not touch Android app code unless the spec mandates it.
- No version bump, no database schema changes.
- Strings edits only in `Metrolist/app/src/main/res/values/metrolist_strings.xml` if any.
- Desktop build/test through GitHub Actions Windows CI; local gradle build of the app is okay for typechecking but never package an installer locally.

## Review Focus

1. **Section list parity** — every Android settings section must be present on desktop (appearance, content, ai, player, player/sponsorblock, storage, privacy, backup_restore, integrations, integrations/discord|lastfm|listen_together|spotify|spotify/preload, updater, about, login, android_auto, equalizer, eq_wizard). An extra empty stub is a defect: it's either wired or omitted with an explicit note.
2. **Toggles honor preference values** — where an Android setting stores a preference key for a toggle, the desktop counterpart must read/write the same key where practical, or document why not.
3. **No Android screens regress** — creating desktop settings does not touch the Android UI code path.
4. **Window/navigation parity** — after adding Settings to the desktop rail, users can reach every section and return to Home without re-launching.
5. **Logout / Restore parity** — desktop must keep the same "sign-in/out and reinstall" UX where provided; UI changes must not break the sign-in flow (cead83c59+).

---

### Task 1: Add Settings destination to the desktop

**Files:**
- Modify: `desktop/src/main/kotlin/com/metrolist/music/desktop/Main.kt`

- [ ] **Step 1:** Add `Settings(...)` to the `Destination` enum (icon Icons.Default.Settings).
- [ ] **Step 2:** Add a `SettingsScreen` composable skeleton that renders a scrollable list of section tiles (Appearance, Content, AI, Player, Storage, Privacy, Backup & restore, Integrations, Updater, About, Equalizer) and calls `onSelectSection(dest)`.
- [ ] **Step 3:** In `MuSicXApp`, add branches `Destination.Settings -> SettingsScreen(sections)` and account-tab wiring; pick `Destination.Settings` in rail.
- [ ] **Step 4:** Build check: `./gradlew :desktop:compileKotlin` succeeds.
- [ ] **Step 5:** Commit `feat(desktop): add Settings destination with section list mirroring Android`.

### Task 2: Implement each settings section as a small screen

**Files:**
- Modify: `desktop/src/main/kotlin/com/metrolist/music/desktop/Main.kt`

- [ ] **Step 1:** Create composables `SettingsAppearanceScreen`, `SettingsContentScreen`, `SettingsAiScreen`, `SettingsPlayerScreen`, `SettingsStorageScreen`, `SettingsPrivacyScreen`, `SettingsBackupScreen`, `SettingsIntegrationsScreen`, `SettingsUpdaterScreen`, `SettingsAboutScreen`, `SettingsEqualizerScreen` mirroring Android UI structure (headers, toggles, slider rows) where the UI exists on Android.
- [ ] **Step 2:** Implement sub-screens for SponsorBlock, Discord, Last.fm, ListenTogether, Spotify, Android Auto, Eq Wizard, Login where the Android screen exists and expose the equivalent toggles/fields on desktop; where the underlying desktop API does not exist yet, render the same menu rows but disabled with a "Not available on desktop" note.
- [ ] **Step 3:** Inline tests: create `desktop/src/test/kotlin/com/metrolist/music/desktop/DesktopInnerTubeTest.kt` style presence check? Not needed if no unit API; instead verify typechecking and crosscheck section count by grepping Android routes.
- [ ] **Step 4:** Typecheck + commit `feat(desktop): add all Android settings sections to desktop, disabled where n/a`.

### Task 3: Update plans, roadmap and PR summary

**Files:**
- Modify: `docs/superpowers/plans/2026-10-06-windows-desktop-parity-roadmap.md`
- Modify: PR #53 body (via `gh pr edit 53` or by patching the issue details body field).

- [ ] **Step 1:** Append a new row for "Settings parity" to the roadmap's landed slices (item 25?) once Task 2 compiles.
- [ ] **Step 2:** Update PR #53's "Still to come" list: add "Settings screen parity (desktop)" as landed item 24 in the slice log.
- [ ] **Step 3:** Commit/push docs.
