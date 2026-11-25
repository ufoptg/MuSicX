# Audio/Video Toggle Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add an inline now-playing audio↔video toggle for Android and Windows desktop on `main`, matching the approved spec.

**Architecture:** Introduce a small per-platform video-mode state/controller that resolves a watchable video target for the current track, switches playback to a video-capable player/surface while preserving position/queue state, and falls back to audio with a toast on failure. Android uses Media3/ExoPlayer + Compose AndroidView; Windows uses VLCJ with video rendering enabled only in video mode.

**Tech Stack:** Kotlin, Jetpack Compose, Media3/ExoPlayer, AndroidX Media3 UI (`PlayerView`), VLCJ/Compose Desktop, existing InnerTube/YouTube resolution utilities, Gradle, JUnit/Kotlin test on JVM where practical.

**Spec:** `docs/superpowers/specs/2026-11-25-audio-video-toggle-design.md`

## Global Constraints

- Deliver on current `main` branch, covering both `app/` (Android) and `desktop/` (Windows).
- Video mode is inline in the now-playing artwork/thumbnail area; no separate video screen/navigation flow.
- Toggle remains tappable when video is unavailable; show a short toast/error and keep audio mode.
- Default to audio mode on track change.
- Preserve playback position/queue/play-pause state as closely as platform allows when switching modes.
- Reuse existing metadata/extraction/player patterns; avoid unrelated refactors.
- No fullscreen/PiP parity work, no video quality selector UI, no persistent “always video” setting, no video casting.

## Review Focus

- **Track with no watchable video:** tapping toggle shows toast and playback remains in audio mode without crashing or losing queue state.
- **Mode switch during active playback:** position is preserved within an acceptable tolerance and play/pause state remains coherent.
- **Track change while in video mode:** player resets to audio mode for the next track and does not leak video surface/resources.
- **Android lifecycle events:** background/collapse/rotation/recomposition do not crash, leak `PlayerView`, or leave audio/video desynced.
- **Desktop VLC fallback:** video render/init failure restores `:no-video` audio path and shows toast instead of breaking playback.

---

### Task 1: Add Android video-mode state and preference/constants

**Files:**
- Create: `app/src/main/kotlin/com/metrolist/music/playback/VideoModeController.kt`
- Modify: `app/src/main/kotlin/com/metrolist/music/constants/PreferenceKeys.kt`
- Modify: `app/src/main/kotlin/com/metrolist/music/playback/PlayerConnection.kt`
- Test: `app/src/test/kotlin/com/metrolist/music/playback/VideoModeControllerTest.kt`

**Interfaces:**
- Consumes: `com.metrolist.music.models.MediaMetadata`, `com.metrolist.music.LocalPlayerConnection`/`PlayerConnection.player`, existing coroutine scope patterns.
- Produces: `VideoModeController` (or equivalent) with `val isVideoModeActive: StateFlow<Boolean>`, `val isVideoModeResolving: StateFlow<Boolean>`, `fun toggleVideoMode()`, `fun setVideoMode(enabled: Boolean)`, `fun onMediaItemChanged(mediaId: String?)`, and a toast/error callback hook; `PlayerConnection` exposes video-mode state/functions used by UI.

- [ ] **Step 1: Write the failing test**

```kotlin
class VideoModeControllerTest {
    @Test
    fun trackChange_resetsVideoModeToAudio() {
        val controller = VideoModeControllerForTest()
        controller.setVideoModeActiveForTest(true)

        controller.onMediaItemChanged("new-media-id")

        assertFalse(controller.isVideoModeActive.value)
        assertFalse(controller.isVideoModeResolving.value)
    }

    @Test
    fun unavailableVideo_reportsToastAndStaysAudio() {
        val toasts = mutableListOf<String>()
        val controller = VideoModeControllerForTest(onToast = { toasts += it })
        controller.setResolvableVideoForTest(null)

        controller.requestVideoMode()

        assertFalse(controller.isVideoModeActive.value)
        assertEquals(listOf("Video unavailable for this track"), toasts)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests com.metrolist.music.playback.VideoModeControllerTest`
Expected: FAIL with unresolved `VideoModeController`/test helper references.

- [ ] **Step 3: Implement minimal controller/state wiring**

Create `VideoModeController` with StateFlows and callbacks; wire into `PlayerConnection` (or service-exposed connection) so UI can observe/toggle. Add any required preference keys only if needed by spec (no persistent always-video setting).

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests com.metrolist.music.playback.VideoModeControllerTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/metrolist/music/playback/VideoModeController.kt app/src/main/kotlin/com/metrolist/music/playback/PlayerConnection.kt app/src/main/kotlin/com/metrolist/music/constants/PreferenceKeys.kt app/src/test/kotlin/com/metrolist/music/playback/VideoModeControllerTest.kt
git commit -m "feat(android): add video mode playback state"
```

### Task 2: Resolve watchable video target for Android

**Files:**
- Create: `app/src/main/kotlin/com/metrolist/music/playback/VideoPlaybackResolver.kt`
- Modify: `app/src/main/kotlin/com/metrolist/music/playback/VideoModeController.kt`
- Modify: `app/src/main/kotlin/com/metrolist/music/models/MediaMetadata.kt` (only if helper needed)
- Test: `app/src/test/kotlin/com/metrolist/music/playback/VideoPlaybackResolverTest.kt`

**Interfaces:**
- Consumes: `MediaMetadata` (`id`, `isVideoSong`, `musicVideoType`, title/artists), existing YouTube/InnerTube/stream resolution utilities in `app/.../playback` or `innertube`.
- Produces: `VideoPlaybackResolver.resolve(mediaMetadata): VideoPlaybackTarget?` where `VideoPlaybackTarget` includes `videoId: String`, `mediaUri: String`/MediaItem data needed by ExoPlayer, and optional headers/format metadata.

- [ ] **Step 1: Write the failing test**

```kotlin
@Test
fun resolvesExistingVideoSongToItself() = runTest {
    val resolver = VideoPlaybackResolver(fakeLookup = FakeLookup(videoId = "abc123", uri = "https://example/video"))
    val metadata = mediaMetadata(id = "abc123", musicVideoType = "MUSIC_VIDEO_TYPE_OMV")

    val target = resolver.resolve(metadata)

    assertEquals("abc123", target?.videoId)
}

@Test
fun returnsNullWhenNoCounterpartFound() = runTest {
    val resolver = VideoPlaybackResolver(fakeLookup = FakeLookup(videoId = null, uri = null))
    val metadata = mediaMetadata(id = "audio1", musicVideoType = null)

    assertNull(resolver.resolve(metadata))
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests com.metrolist.music.playback.VideoPlaybackResolverTest`
Expected: FAIL with unresolved resolver types.

- [ ] **Step 3: Implement minimal resolver**

Implement resolver using existing metadata and YouTube/InnerTube lookup paths; keep it pure/testable by injecting lookup functions. Do not add unrelated caching/persistence.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests com.metrolist.music.playback.VideoPlaybackResolverTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/metrolist/music/playback/VideoPlaybackResolver.kt app/src/main/kotlin/com/metrolist/music/playback/VideoModeController.kt app/src/main/kotlin/com/metrolist/music/models/MediaMetadata.kt app/src/test/kotlin/com/metrolist/music/playback/VideoPlaybackResolverTest.kt
git commit -m "feat(android): resolve video targets for now playing"
```

### Task 3: Switch Android playback between audio and inline video

**Files:**
- Modify: `app/src/main/kotlin/com/metrolist/music/playback/MusicService.kt`
- Modify: `app/src/main/kotlin/com/metrolist/music/playback/PlayerConnection.kt`
- Modify: `app/src/main/kotlin/com/metrolist/music/playback/VideoModeController.kt`
- Test: `app/src/test/kotlin/com/metrolist/music/playback/VideoModeControllerTest.kt`

**Interfaces:**
- Consumes: `VideoPlaybackResolver`, `ExoPlayer`/`MediaItem` APIs, current player position/queue state.
- Produces: `switchToVideo(target)` and `switchToAudio()` behavior preserving `currentPosition`, `playWhenReady`, and current media id/queue index; errors call toast callback and revert to audio mode.

- [ ] **Step 1: Write the failing test**

```kotlin
@Test
fun switchToVideo_preservesPositionAndRequestsPlay() = runTest {
    val player = FakePlaybackPlayer(positionMs = 42_000, playWhenReady = true)
    val controller = VideoModeControllerForTest(player = player, resolver = FakeResolver(videoTarget))

    controller.requestVideoMode()
    advanceUntilIdle()

    assertEquals(42_000, player.lastSeekPositionMs)
    assertTrue(controller.isVideoModeActive.value)
    assertTrue(player.playWhenReady)
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests com.metrolist.music.playback.VideoModeControllerTest`
Expected: FAIL on missing switch behavior/assertions.

- [ ] **Step 3: Implement minimal audio/video switch**

Add video-capable media preparation in service/controller. Capture position before switch, prepare/set media item, seek, restore play/pause. On failure, emit toast and revert to audio. Keep implementation compatible with existing audio-only path.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests com.metrolist.music.playback.VideoModeControllerTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/metrolist/music/playback/MusicService.kt app/src/main/kotlin/com/metrolist/music/playback/PlayerConnection.kt app/src/main/kotlin/com/metrolist/music/playback/VideoModeController.kt app/src/test/kotlin/com/metrolist/music/playback/VideoModeControllerTest.kt
git commit -m "feat(android): switch playback between audio and video"
```

### Task 4: Render inline Android video surface in now playing

**Files:**
- Create: `app/src/main/kotlin/com/metrolist/music/ui/player/MusicVideoSurface.kt`
- Modify: `app/src/main/kotlin/com/metrolist/music/ui/player/Thumbnail.kt`
- Modify: `app/src/main/kotlin/com/metrolist/music/ui/player/Player.kt`
- Modify: `app/build.gradle.kts` (only if `media3-ui` dependency is missing)
- Test: `app/src/test/kotlin/com/metrolist/music/ui/player/VideoModeUiStateTest.kt`

**Interfaces:**
- Consumes: `PlayerConnection` video-mode StateFlows, ExoPlayer instance, existing thumbnail sizing/corner radius/layout values.
- Produces: `MusicVideoSurface(player, modifier, cornerRadius)` Composable; `Thumbnail`/`Player` show video surface when `isVideoModeActive` and video is available, otherwise existing artwork.

- [ ] **Step 1: Write the failing test**

```kotlin
@Test
fun shouldShowVideoSurface_onlyWhenVideoModeActiveAndPlayerHasVideo() {
    assertTrue(shouldShowVideoSurface(isVideoModeActive = true, hasVideo = true))
    assertFalse(shouldShowVideoSurface(isVideoModeActive = true, hasVideo = false))
    assertFalse(shouldShowVideoSurface(isVideoModeActive = false, hasVideo = true))
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests com.metrolist.music.ui.player.VideoModeUiStateTest`
Expected: FAIL with unresolved helper/function.

- [ ] **Step 3: Implement minimal UI**

Add pure helper for visibility decision and render `PlayerView` via `AndroidView` in the thumbnail slot. Keep existing artwork path unchanged when video mode is off. Ensure surface uses existing rounded corners/sizing.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests com.metrolist.music.ui.player.VideoModeUiStateTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/metrolist/music/ui/player/MusicVideoSurface.kt app/src/main/kotlin/com/metrolist/music/ui/player/Thumbnail.kt app/src/main/kotlin/com/metrolist/music/ui/player/Player.kt app/build.gradle.kts app/src/test/kotlin/com/metrolist/music/ui/player/VideoModeUiStateTest.kt
git commit -m "feat(android): show inline video in now playing"
```

### Task 5: Add Android now-playing video toggle and no-video toast

**Files:**
- Modify: `app/src/main/kotlin/com/metrolist/music/ui/player/Player.kt`
- Modify: `app/src/main/res/values/strings.xml` (and relevant locale files only if project convention requires; avoid mass edits)
- Modify: `app/src/main/kotlin/com/metrolist/music/playback/VideoModeController.kt`
- Test: `app/src/test/kotlin/com/metrolist/music/playback/VideoModeControllerTest.kt`

**Interfaces:**
- Consumes: `VideoModeController.toggleVideoMode()` / `isVideoModeActive` / `isVideoModeResolving`; Android `Toast`.
- Produces: Tappable now-playing control in player UI; unavailable tap emits toast “Video unavailable for this track”; active state visually indicated.

- [ ] **Step 1: Write the failing test**

```kotlin
@Test
fun toggleWhenUnavailable_emitsUnavailableToast() {
    val toasts = mutableListOf<String>()
    val controller = VideoModeControllerForTest(onToast = { toasts += it }, canResolveVideo = false)

    controller.toggleVideoMode()

    assertEquals("Video unavailable for this track", toasts.single())
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests com.metrolist.music.playback.VideoModeControllerTest`
Expected: FAIL.

- [ ] **Step 3: Implement minimal UI/control**

Add button near existing player controls (fit current layout/style). Wire click to controller; controller/UI shows toast through context. Keep button enabled/tappable in unavailable case.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests com.metrolist.music.playback.VideoModeControllerTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/metrolist/music/ui/player/Player.kt app/src/main/kotlin/com/metrolist/music/playback/VideoModeController.kt app/src/main/res/values/strings.xml
git commit -m "feat(android): add now playing video toggle"
```

### Task 6: Add desktop video-mode state and VLC video-capable playback

**Files:**
- Modify: `desktop/src/main/kotlin/com/metrolist/music/desktop/DesktopAudioPlayer.kt`
- Modify: `desktop/src/main/kotlin/com/metrolist/music/desktop/DesktopPrefsStore.kt` (only if a non-persistent runtime pref is needed; avoid persistent always-video setting)
- Create: `desktop/src/main/kotlin/com/metrolist/music/desktop/DesktopVideoModeController.kt`
- Test: `desktop/src/test/kotlin/com/metrolist/music/desktop/DesktopVideoModeControllerTest.kt` (create test dir if missing)

**Interfaces:**
- Consumes: existing `DesktopAudioPlayer` playback methods, current track/position/play state, VLCJ media player APIs.
- Produces: Desktop controller/state with `isVideoModeActive`, `isVideoModeResolving`, `toggleVideoMode()`, `onTrackChanged(videoId)`, and toast/error callback. Audio-only mode retains `:no-video`; video mode enables video rendering and can restore position.

- [ ] **Step 1: Write the failing test**

```kotlin
class DesktopVideoModeControllerTest {
    @Test
    fun trackChange_resetsToAudioMode() {
        val controller = DesktopVideoModeControllerForTest()
        controller.setVideoModeActiveForTest(true)

        controller.onTrackChanged("new-id")

        assertFalse(controller.isVideoModeActive.value)
    }

    @Test
    fun unavailableVideo_emitsToast() {
        val toasts = mutableListOf<String>()
        val controller = DesktopVideoModeControllerForTest(onToast = { toasts += it }, hasVideo = false)

        controller.toggleVideoMode()

        assertEquals(listOf("Video unavailable for this track"), toasts)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :desktop:test --tests com.metrolist.music.desktop.DesktopVideoModeControllerTest`
Expected: FAIL with missing classes/tests.

- [ ] **Step 3: Implement minimal desktop controller and player support**

Add state/controller and extend `DesktopAudioPlayer` with video-mode-aware play options. Keep default path audio-only. On video failure, revert to audio mode and invoke toast.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :desktop:test --tests com.metrolist.music.desktop.DesktopVideoModeControllerTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add desktop/src/main/kotlin/com/metrolist/music/desktop/DesktopAudioPlayer.kt desktop/src/main/kotlin/com/metrolist/music/desktop/DesktopVideoModeController.kt desktop/src/main/kotlin/com/metrolist/music/desktop/DesktopPrefsStore.kt desktop/src/test/kotlin/com/metrolist/music/desktop/DesktopVideoModeControllerTest.kt
git commit -m "feat(desktop): add video mode playback state"
```

### Task 7: Render inline desktop video and wire now-playing toggle

**Files:**
- Modify: `desktop/src/main/kotlin/com/metrolist/music/desktop/Main.kt`
- Modify: `desktop/src/main/kotlin/com/metrolist/music/desktop/DesktopAudioPlayer.kt`
- Create: `desktop/src/main/kotlin/com/metrolist/music/desktop/DesktopVideoSurface.kt`
- Test: `desktop/src/test/kotlin/com/metrolist/music/desktop/DesktopVideoModeControllerTest.kt`

**Interfaces:**
- Consumes: `DesktopVideoModeController` state, VLCJ player/component, existing desktop now-playing layout in `Main.kt`.
- Produces: Inline desktop video surface in now-playing artwork slot; toggle button in desktop controls; toast on unavailable/failure; audio artwork restored when video off.

- [ ] **Step 1: Write the failing test**

```kotlin
@Test
fun videoFailure_revertsToAudioAndToasts() {
    val toasts = mutableListOf<String>()
    val controller = DesktopVideoModeControllerForTest(onToast = { toasts += it }, failVideoStart = true)

    controller.toggleVideoMode()

    assertFalse(controller.isVideoModeActive.value)
    assertTrue(toasts.contains("Video unavailable for this track"))
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :desktop:test --tests com.metrolist.music.desktop.DesktopVideoModeControllerTest`
Expected: FAIL.

- [ ] **Step 3: Implement minimal desktop UI/surface**

Add `DesktopVideoSurface` using VLCJ Compose interop appropriate for project dependency. In `Main.kt`, place toggle in now-playing controls and swap artwork box for video surface when active. Catch render/start errors and revert to audio.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :desktop:test --tests com.metrolist.music.desktop.DesktopVideoModeControllerTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add desktop/src/main/kotlin/com/metrolist/music/desktop/Main.kt desktop/src/main/kotlin/com/metrolist/music/desktop/DesktopAudioPlayer.kt desktop/src/main/kotlin/com/metrolist/music/desktop/DesktopVideoSurface.kt desktop/src/test/kotlin/com/metrolist/music/desktop/DesktopVideoModeControllerTest.kt
git commit -m "feat(desktop): add inline now playing video toggle"
```

### Task 8: Verification, docs touch-up, and final checks

**Files:**
- Modify: `docs/superpowers/specs/2026-11-25-audio-video-toggle-design.md` (only if implementation required an approved spec clarification)
- Modify: `docs/superpowers/plans/2026-11-25-audio-video-toggle.md` (checklist/status updates during execution only)

**Interfaces:**
- Consumes: completed Android/desktop implementation and tests.
- Produces: Verified build/test results, notes for any GUI/manual checks not runnable in environment.

- [ ] **Step 1: Run focused Android unit tests**

Run: `./gradlew :app:testDebugUnitTest --tests com.metrolist.music.playback.VideoModeControllerTest --tests com.metrolist.music.playback.VideoPlaybackResolverTest --tests com.metrolist.music.ui.player.VideoModeUiStateTest`
Expected: PASS.

- [ ] **Step 2: Run focused desktop tests**

Run: `./gradlew :desktop:test --tests com.metrolist.music.desktop.DesktopVideoModeControllerTest`
Expected: PASS.

- [ ] **Step 3: Run module compile checks**

Run: `./gradlew :app:compileDebugKotlin :desktop:compileKotlin`
Expected: PASS; if environment/toolchain blocks, record exact blocker.

- [ ] **Step 4: Manual verification checklist (report if GUI unavailable)**

Run manual checks:
- Android: toggle on/off with video-capable track, no-video toast, track change resets to audio, position preserved, background/rotation no crash.
- Desktop: inline video renders, toggle off restores artwork/audio-only, no-video toast, resize/layout stable.
Expected: all behaviors match spec; report any environment-limited checks.

- [ ] **Step 5: Commit verification/doc updates**

```bash
git add docs/superpowers/specs/2026-11-25-audio-video-toggle-design.md docs/superpowers/plans/2026-11-25-audio-video-toggle.md
git commit -m "docs: record audio video toggle verification"
```
