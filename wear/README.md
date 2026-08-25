# MuSicX Wear OS companion

A lightweight Wear OS app that lets you **search songs, browse your library/playlists, and play** — by
talking to MuSicX's existing `MusicService` running on your paired phone.

## How it works

A Wear OS watch **cannot** directly `MediaBrowser.connect()` to a `MediaLibraryService` that lives on
the paired phone (Media3's `SessionToken(ComponentName)` only resolves same-device services, and there
is no public cross-device browse API). So the companion is split in two:

```
 Watch (:wear)                         Phone (app, GMS flavor)
 ┌──────────────┐   Wear Data Layer    ┌──────────────────────────────┐
 │  Compose UI  │ ──MessageClient──▶  │ WearMediaBridgeService        │
 │  search/      │ ◀──────────────── │  └─ same-device MediaBrowser  │
 │  browse/play  │   results (JSON)    │      └─ MusicService          │
 └──────────────┘                    │         (MediaLibraryService) │
                                     │         onGetChildren/onSearch │
                                     └──────────────────────────────┘
```

The phone-side bridge connects to the app's **own** `MusicService` with a same-device `MediaBrowser`
(which works), then exposes the already-implemented browse/search/playback surface to the watch
over the Wear Data Layer. **No data or playback logic is duplicated** — the watch reuses the exact
same `MediaLibrarySessionCallback` code path that powers Android Auto.

## What was added

### Phone side (GMS flavor only — keeps FOSS/Izzy F-Droid-clean)
- `app/src/gms/kotlin/com/metrolist/music/wear/WearMediaBridgeService.kt` — `WearableListenerService`
  that builds a same-device `MediaBrowser` against `MusicService` and dispatches browse/search/play
  requests from the watch.
- `app/src/gms/kotlin/com/metrolist/music/wear/MediaItemJson.kt` — JSON projection of `MediaItem` +
  an LRU cache so playback replays the **exact** `MediaItem` the service produced (composite IDs like
  `search/<query>/<songId>` are not guaranteed to resolve through `onGetItem`).
- `app/src/gms/AndroidManifest.xml` — registers the bridge service (`BIND_LISTENER`).
- `app/src/gms/res/values/wear_capabilities.xml` — declares the `musicx_phone_bridge` capability the
  watch discovers via `CapabilityClient`.
- `app/build.gradle.kts` — `gmsImplementation("com.google.android.gms:play-services-wearable:18.2.0")`.

### Wear side (new `:wear` module)
- `wear/build.gradle.kts`, `wear/src/main/AndroidManifest.xml`, launcher icon.
- `wear/src/main/kotlin/dev/ufoptg/musicx/wear/PhoneBridge.kt` — `CapabilityClient` to find the phone
  node + `MessageClient` request/response correlated by `requestId`.
- `wear/src/main/kotlin/dev/ufoptg/musicx/wear/ui/WearApp.kt` — Compose for Wear UI: library browse,
  search, and a now-playing bar with play/pause/next.
- `settings.gradle.kts` — `include(":wear")`.

The wear app shares `applicationId = dev.ufoptg.musicx` (with a matching `.debug` suffix) so the Data
Layer pairs the two apps, which must be signed with the same key.

## Build

1. Open the project in Android Studio (AGP 9.3, Kotlin 2.4.10, JDK 17+).
2. Build the **GMS** phone variant (the only variant that ships the bridge):
   ```
   ./gradlew :app:assembleGmsDebug
   ```
3. Build the wear app:
   ```
   ./gradlew :wear:assembleDebug
   ```
   (Wear Compose 1.5.x targets Compose 1.10+ and is compatible with the project's Compose 1.11.4.
   If you hit a version clash, align `androidx.wear.compose:compose-material:1.5.1` to the Compose
   BOM the project resolves to.)

## Test on a paired watch

1. Pair a Wear OS 3+ watch (or Wear OS emulator) with your phone.
2. Install the GMS phone APK and the wear APK on the watch:
   ```
   adb install -r app/build/outputs/apk/gms/debug/app-gms-debug.apk
   adb -s <watch> install -r wear/build/outputs/apk/debug/wear-debug.apk
   ```
3. Open MuSicX on the phone and start playback at least once so `MusicService` is initialized.
4. Open **MuSicX** on the watch: browse Library → Liked/Songs/Playlists, or tap 🔍 to search, then
   tap a track to play. The now-playing bar shows the current track and transport controls.

## Notes & limitations

- **GMS only on the phone.** FOSS and Izzy (F-Droid) builds ship without the bridge, so on those
  builds the watch finds no reachable capability. This is intentional to preserve F-Droid compliance.
- **Transport-only without this app.** Even without installing the wear app, a paired Wear OS watch
  already mirrors the phone's media notification (play/pause/skip/seek) for any `MediaSessionService`.
  This companion adds search + library browse on top of that.
- The watch polls now-playing state every second. For richer live updates you could later push
  state changes from the phone via `DataClient`/`OnDataChangedListener`.
