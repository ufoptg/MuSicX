# Audio/Video Toggle Design

## Goal

Add a Metrolist v15-style now-playing control to MuSicX that lets users switch between **audio mode** and **inline video mode** for the current track, on both Android and Windows desktop, on the same `main` branch.

## Scope

- **In scope:** Android (`app/`) and Windows desktop (`desktop/`) now-playing UI, playback/state changes needed for inline audio↔video switching, video resolution/fallback behavior, and verification.
- **Out of scope (unless requested later):** fullscreen/PiP parity beyond existing behavior, video quality selector UI, persistent “always video” setting, casting video, separate video screen/navigation flow.

## User-Approved Decisions

- **Approach:** Pragmatic v15-style port (Approach 1).
- **Platforms:** Android `main` + Windows `feat/windows-desktop` work delivered on the same `main` branch.
- **Layout:** Inline in now-playing; video replaces the thumbnail/artwork area when active.
- **No-video case:** Toggle remains tappable; show a toast/error when no watchable video is available.
- **Design sections approved:** Behavior/state model, Android implementation, Windows implementation, stream resolution/fallbacks/testing.

## Current State

- Android `app/src/main/kotlin/com/metrolist/music/ui/player/Player.kt` and `Thumbnail.kt` render audio artwork only; `MusicService`/`PlayerConnection` manage ExoPlayer audio playback.
- Windows `desktop/src/main/kotlin/com/metrolist/music/desktop/DesktopAudioPlayer.kt` uses VLCJ with `:no-video` and audio-only playback; `Main.kt` renders desktop now-playing UI.
- v15 reference (`origin/v15`) provides the target UX pattern: `AndroidVideoModeController` toggles video mode and `MusicVideoSurface` renders inline video in the player/miniplayer.

## Behavior and State Model

### Core Behavior

- A now-playing toggle control switches `audio <-> video`.
- **Video mode ON:** Resolve a watchable video counterpart/resource for the current item. Replace the artwork/thumbnail region inline with a video surface. Audio comes from the active video playback so audio/video remain in sync.
- **Audio mode OFF:** Hide video surface and resume normal audio artwork/UI. Preserve playback position as closely as possible when switching modes.
- **No video available:** Keep toggle tappable. On tap, show a short toast such as “Video unavailable for this track” and leave mode unchanged.
- **Track changes:** Default to audio mode on track change initially. Video availability may be resolved lazily/async; UI must not block.

### State

Expose/track, per platform where applicable:

- `isVideoModeActive` — whether the user/player is in video mode.
- `isVideoModeResolving` — whether a toggle/resolution/switch is in progress.
- `currentVideoId` / `currentVideoResource` — resolved watchable video target for current media.
- `videoAvailable` or equivalent resolvability signal — optional optimization; absence must still be handled by toast on tap.
- Last playback position/seek target during mode switches.

State transitions must avoid desyncing queue, current media item, play/pause, and progress reporting.

## Android Design

### Playback

- Add a small video-mode controller/state in the playback layer (`MusicService`/`PlayerConnection` or dedicated helper) modeled after v15’s `AndroidVideoModeController` responsibilities, adapted to `main`’s `app/` architecture.
- When enabling video mode, resolve a video counterpart:
  - If current metadata is already a video song (`musicVideoType` indicates video / existing `isVideoSong` semantics), use its video-capable stream/resource.
  - Otherwise resolve a counterpart video ID/resource through existing InnerTube/metadata pathways.
- Use Media3/ExoPlayer for video-capable playback. Prefer switching the active media/resource in the existing player flow so queue and controls remain coherent, rather than running an unsynced second player.
- Attach a `PlayerView`/Compose AndroidView video surface only when video mode is visible/active; detach/release appropriately.

### UI

- Add a toggle button in `Player.kt` controls. It should fit MuSicX styling and remain tappable when unavailable.
- Update `Thumbnail.kt` or the player artwork area to render an inline video surface when `isVideoModeActive && hasVideo`; otherwise render existing artwork.
- Show resolving feedback (small loading/disabled visual state is acceptable if tap still reports unavailable when needed; do not block UI thread).
- Preserve rounded corners/layout/sizing behavior of the artwork area.

### Android Fallbacks

- Extraction/resolution/player error: toast, revert/stay audio mode, log reason.
- Surface lifecycle: handle background/collapse/recomposition without leaking surfaces or crashing.

## Windows Desktop Design

### Playback

- Extend `DesktopAudioPlayer.kt`/desktop playback model to support video mode. Current implementation always passes `:no-video`; video mode must use a VLCJ video-capable player/component path.
- Keep one active playback concept where possible. Toggling should preserve track, queue, position, play/pause, and volume. If seamless surface/mode swap is not possible, restart media at preserved position.
- Add desktop state: `isVideoModeActive`, `isVideoModeResolving`, resolved video target/availability, and error/toast reporting.

### UI

- Add the same toggle in desktop now-playing controls in `Main.kt`.
- When active and available, render inline video in the now-playing artwork area using VLCJ Compose/Desktop interop (e.g., `EmbeddedMediaPlayerComponent`/callback component as appropriate for the existing vlcj version).
- When inactive/unavailable/failure, show existing artwork and keep audio playback usable.

### Desktop Fallbacks

- If video embedding fails, log and revert to audio mode with toast.
- Preserve existing audio-only path (`:no-video`) for normal mode.

## Stream Resolution and Fallbacks

- **Availability:** Use metadata first (`musicVideoType`, video-song flags, existing video ID). If needed, perform counterpart lookup/resolution.
- **Position sync:** Capture position before mode switch; seek restored position after media/resource is ready. Tolerate small drift if platform/player requires restart.
- **Errors:** No counterpart, extraction failure, unplayable/DRM/age-restricted/resource failure, or video surface error => toast “Video unavailable for this track” (or equivalent), revert/stay in audio mode, keep playback alive when possible.
- **Cleanup:** Release video surfaces/resources when leaving video mode, changing to unavailable state, or disposing player UI.

## Testing and Verification

### Android

- Add/update unit tests for video-mode state/resolution decisions where practical (pure logic/helpers).
- Manual verification:
  - Toggle audio→video and video→audio on a track with video.
  - No-video track shows toast and remains in audio mode.
  - Track change resets to audio mode.
  - Position is preserved reasonably across switch.
  - Background/rotation/collapse does not crash or leak surface.

### Windows

- Manual verification:
  - Inline video renders in now-playing area.
  - Toggle back to audio restores artwork and audio-only behavior.
  - No-video track shows toast.
  - Resize/layout remains stable.
  - Playback continuity/position preserved as well as VLC allows.

### Build/Checks

- Run targeted checks for touched modules as environment allows: Android compile/unit tests/lint for `:app`; desktop compile/tests for `:desktop`.
- If environment cannot run emulator/GUI checks, report exact manual verification needed.

## Risks

- **Desktop video embedding:** Highest risk; VLCJ + Compose Desktop interop can be finicky. Mitigate with isolated component and audio fallback.
- **Playback continuity:** Switching resources may cause brief gap; mitigate by pre-resolving and seeking to saved position.
- **Architecture mismatch with v15:** v15 reference lives in `androidApp/shared`; port only the needed behavior into `main`’s structure.

## Implementation Notes

- Follow existing project style and architecture; avoid unrelated refactors.
- Reuse existing metadata/extraction utilities before adding new dependencies.
- Keep logs behind existing logging/timber patterns; no sensitive data in toasts/logs.
