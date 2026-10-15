# Task List: Code Review of Main Branch

## Phase 1: Foundation - Core Utils and Extensions

### Task 1: Review Utils.kt - DONE

**Review Summary (per code-review-and-quality skill):**

**Correctness:** ✅ All functions are correct
- `getArtistSeparator`: Simple, correct
- `joinToArtistString`: Handles 0, 1, 2, and 3+ elements correctly
- `reportException`: Correct, just logs stack trace
- `setAppLocale`: Correct Configuration approach

**Readability:** ✅ Good
- Descriptive function names
- Straightforward control flow with `when` expression
- No `temp`/`data`/`result` anti-patterns
- No "clever" tricks

**Architecture:** ✅ Good
- Proper package `com.metrolist.music.utils`
- Follows project conventions
- No circular dependencies or boundary issues

**Security:** ✅ No issues
- No secrets, no untrusted input handling needed

**Performance:** ✅ No concerns
- Simple utility functions, no loops, no N+1 patterns

**Final Verdict:** ✅ Approve - clean utility file with no issues.

**Dependencies:** None

---

### Task 2: Review YouTubeUtils.kt - DONE

**Review Summary (per code-review-and-quality skill):**

**Correctness:** ✅ Correct
- Two regex patterns match Google user content URLs (`lh3/yt3 googleusercontent.com`) and ggpht YouTube thumbnail URLs
- `resize()` function handles: no args (return self), width only, height only, both width+height
- Fallback `return this` if neither pattern matches (safe default)
- Dimension calculations use proper aspect-ratio math with `roundToInt()`

**Readability:** ✅ Good
- Descriptive variable names (`originalWidth`, `targetHeight`, `query`)
- `@file:Suppress("LocalVariableName")` - minor concern: all variable names in the file (`originalWidth`, `originalHeight`, `query`, `targetWidth`, `targetHeight`) are actually descriptive. This suppression may be unnecessary/legacy.
- Control flow is straightforward with early return and two pattern branches

**Architecture:** ✅ Good
- Proper package `com.metrolist.music.ui.utils`
- Focused utility function, no circular dependencies
- Follows project patterns for URL manipulation

**Security:** ✅ No issues
- No secrets, no input validation concerns (URLs are expected format)
- Output is just a modified URL string

**Performance:** ✅ No concerns
- Simple regex matching, no loops, no N+1 patterns
- Function is pure (no side effects)

**Minor Nits:**
- `@file:Suppress("LocalVariableName")` - the variable names are all descriptive; this suppression may be unnecessary. Consider removing or adding a comment explaining why.

**Final Verdict:** ✅ Approve - clean utility file. Minor: consider removing the unnecessary lint suppression.

**Dependencies:** None

---

### Task 3: Review Extensions Directory - DONE

**Review Summary (per code-review-and-quality skill):**

Files reviewed: `StringExt.kt`, `UtilExt.kt`, `ContextExt.kt`, `CoroutineExt.kt`, `FileExt.kt`, `ListExt.kt`, `MediaItemExt.kt`, `PlayerExt.kt`, `QueueExt.kt`

**Overall Assessment:** ✅ Good - extension functions are well-organized, follow project conventions, and add meaningful utility without unnecessary complexity.

**File-by-File:**

- **StringExt.kt** ✅: `toEnum<T>()` good null/invalid-handling pattern; `normalizeForSearch()` uses NFD normalization + diacritic removal + lowercase (correct for search); `matchesNormalizedQuery()` clean functional pattern; `toInetSocketAddress()` simple wrapper (port parse could throw but is caller's responsibility)

- **UtilExt.kt** ✅: `tryOrNull<T>()` standard try/catch pattern, fine

- **ContextExt.kt** ✅: `isInternetConnected()` properly null-guards `activeNetwork`; `isUserLoggedIn()` and `isSyncEnabled()` use `runBlocking` for DataStore access (acceptable for setup checks, though `runBlocking` on UI thread is generally discouraged)

- **CoroutineExt.kt** ⚠️: `SilentHandler = CoroutineExceptionHandler { _, _ -> }` silently swallows all coroutine exceptions without logging. **This is a correctness concern** - exceptions are lost, making debugging harder. Should at least log the exception or rethrow after logging.

- **FileExt.kt** ✅: `File.div()` operator delegate; `zipInputStream()/zipOutputStream()` simple wrappers, fine

- **ListExt.kt** ✅: `move()` implementation `add(toIndex, removeAt(fromIndex))` correct; `filterExplicit/filterVideoSongs/filterExplicitAlbums` straightforward predicates; `filterYoutubeShorts()` filters SS-prefixed browseIds - the `== true` is redundant but not wrong

- **MediaItemExt.kt** ✅: `toMediaItem()` conversions well-structured; `withResolvedArtistNameAliases()` optimizes by only copying when aliases actually change - good pattern; `withUpdatedMetadata()` follows same pattern

- **PlayerExt.kt** ✅: `togglePlayPause()` correct state check (IDLE -> prepare, then toggle); `toggleRepeatMode()` correct cycle OFF->ALL->ONE->OFF; `getQueueWindows()` builds queue with shuffle handling; `findNextMediaItemById()` linear search forward from current index; `setOffloadEnabled()` properly builds trackSelectionParameters. **Note:** `val shuffleModeEnabled = shuffleModeEnabled` on line 50 is redundant (local val copying receiver property) but not erroneous.

- **QueueExt.kt** ✅: `toPersistQueue()` proper when/sealed class pattern matching Queue subtypes; `toQueue()` documents fallback limitations for YouTube/LocalAlbumRadio types in comments - good design transparency

**Key Nit (across files):**
- `CoroutineExt.kt`'s `SilentHandler` swallows exceptions silently. Consider adding logging or using a non-silent handler pattern.

**Final Verdict:** ✅ Approve - extensions are clean and well-organized. One structural concern: `SilentHandler` in CoroutineExt.kt silently masks errors.

**Dependencies:** None

---

## Checkpoint: Foundation
- [ ] All core utility files reviewed (Tasks 1-3)
- [ ] No critical correctness issues found
- [ ] Build succeeds

---

## Phase 2: ViewModels and UI Components

### Task 4: Review HomeViewModel.kt - DONE

**Review Summary (per code-review-and-quality skill):**

**Correctness:** ✅ Good
- All state flows properly initialized and updated
- `load()` orchestrates in two phases: Phase 1 (essential, fast) and Phase 2 (heavy background ops)
- Error handling consistent: `.onFailure { reportException(it) }` on all YouTube API calls
- `getRandomItem()` probability-based selection (80% user, 20% other) with proper finally block resetting `isRandomizing`
- `toggleChip()` correctly re-fetches home page content when chip changes
- `filterVideoSongs`, `filterExplicit`, `filterYoutubeShorts` extension functions used consistently for content filtering

**Readability:** ✅ Good despite size
- Clear function naming (`getQuickPicks`, `getCommunityPlaylists`, `getDailyDiscover`, etc.)
- `load()` function well-commented with Phase 1/Phase 2 separation
- Utility function `filterOutNulls()` at bottom of file is well-placed
- Some functions are long (`getCommunityPlaylists` at ~420 lines in file, `getDailyDiscover` at ~320) but each has clear internal structure
- The `init` block properly sets up sync, network monitoring, and wrapped data preparation

**Architecture:** ✅ Good
- `@HiltViewModel` with `@Inject` constructor - proper DI
- Dependencies: `MusicDatabase`, `SyncUtils`, `NetworkConnectivityObserver`, `WrappedManager`, `WrappedAudioService`
- State flows for all UI-exposed data (`MutableStateFlow`, `StateFlow`)
- Local DB (`MusicDatabase`) for cached data, InnerTube API for remote data
- DataStore for persistent preferences with proper key constants
- ViewModel scope properly used for all coroutines

**Performance:** ✅ Good
- Network ops on `Dispatchers.IO`, UI-state on main dispatcher via `stateIn()` 
- `take()`, `first()`, `firstOrNull()` with limits prevent unbounded fetching
- `synchronizedList` used where concurrent coroutine modification possible
- `delay(1000)` in `getRandomItem()` is UX-driven for animation feedback, acceptable
- Probability-based selection (80/20) is a intentional design choice

**Key Observation:** File at 824 lines is at the edge of the ~1000 line healthy boundary, but justified by its role as the main home screen orchestrator. Complex parallel orchestration in `load()` is the main complexity focus - well-commented and structured.

**Verdict:** ✅ Approve - well-structured ViewModel following project patterns. Large but coherent size for its role.

**Dependencies:** Requires `MusicDatabase`, `InnerTube`, `SyncUtils`, `NetworkConnectivityObserver`, `WrappedManager` - all properly scoped.

---

### Task 5: LibraryViewModels.kt - DONE

**Review Summary (per code-review-and-quality skill):**

Reviewed 7 ViewModel classes in `LibraryViewModels.kt` (614 total lines):

**LibrarySongsViewModel** (lines 76-128) ✅
- Clean search/filter pattern with dataStore → Triple(mapping) → flatMapLatest → when filter
- Delegates to `database.songs()`, `database.likedSongs()`, etc. with `.filterExplicit()`, `.filterVideoSongs()`
- `syncLikedSongs()`, `syncLibrarySongs()`, `syncUploadedSongs()` use `syncUtils`
- Simple and focused: 1 purpose (songs library search/filter)

**LibraryArtistsViewModel** (lines 130-198) ✅
- Similar search/filter pattern with `combine(allArtists, searchQuery)` 
- `matchesNormalizedQuery()` and `normalizeForSearch()` extensions used correctly
- `init` block fetches artist thumbnail updates via YouTube API after 10-day threshold
- `sync()` delegates to `syncUtils.syncArtistsSubscriptions()`

**LibraryAlbumsViewModel** (lines 201-265) ✅
- Same solid pattern as artists/songs ViewModels
- `onFailure { reportException(it) }` consistent error handling
- `init` block pre-fetches album pages from YouTube for albums with no songs

**LibraryPlaylistsViewModel** (lines 267-306) ✅
- Playlists with `PlaylistSortType` and `HideYoutubeShortsKey` filtering
- `topValue` reads preferred size from dataStore
- Simple and focused

**ArtistSongsViewModel** (lines 308-336) ✅
- Uses `SavedStateHandle` for `artistId` through back stack navigation
- Database lookup + dataStore-filtered songs with explicit/video song filtering
- Clean single-purpose design

**LibraryMixViewModel** (lines 338-451) ✅
- Combines songs from database (`songsInBookmarkedPlaylists()`) + dataStore-filtered songs
- Complex `combine()` pattern with `.distinctBy { it.id }`, `.filterExplicit()`, `.filterVideoSongs()`
- `refresh()` performs full sync via `syncUtils.performFullSyncSuspend()`
- `topValue` from dataStore
- Well-structured despite complex data merging

**LibraryPodcastsViewModel** (lines 453-601) ✅
- Most complex at ~149 lines, but well-organized
- Handles subscribed channels, SE playlist ("SE" ID), RDPN "New Episodes" playlist
- `fetchSePlaylist()`, `fetchPodcastChannels()`, `fetchRdpnPlaylist()` with `.onSuccess/.onFailure`
- `init` block: 4 parallel `launch {}` for initial fetch + `PodcastRefreshTrigger.refreshFlow.collect()` with 1500ms delay for auto-refresh
- `clearPodcastData()`, `refreshAll()`, `refreshChannels()` functions
- Uses `Timber.d/e` for podcast-specific logging (separate from `reportException()`)

**Overall Architecture:** ✅ Excellent
- All 7 ViewModels follow identical patterns: `@HiltViewModel`, `@Inject`, `Context`, `MusicDatabase`, `SyncUtils`
- DataStore for persistent preferences with proper key constants
- StateFlows for all UI state with `stateIn(viewModelScope, ...)`
- YouTube API calls with consistent `.onSuccess { }.onFailure { reportException(it) }` pattern
- Each ViewModel has a single, focused responsibility (songs, artists, albums, playlists, artist songs, mix, podcasts)
- `init` blocks handle background setup (sync, pre-fetching)

**Performance:** ✅ Good
- All network ops on `Dispatchers.IO`
- `debounce(300)` on search queries prevents rapid reloads
- `take(5)` limits in `init` blocks prevent over-fetching
- `Duration.between()` check for 10-day thumbnail freshness is appropriate
- `flatMapLatest` cancels previous searches on new input

**Correctness:** ✅ Good
- All `.onFailure` handlers use `reportException()` (except PodcastViewModel which uses `Timber.e` - consistent within its context)
- Filter extensions (`filterExplicit`, `filterVideoSongs`, `filterExplicitAlbums`, `filterYoutubeShorts`) used consistently across all ViewModels

**Key Observation:** 614 lines across 7 ViewModels is well-organized and follows the project's established patterns extremely consistently. Each has a single clear responsibility. The `LibraryPodcastsViewModel` is the most complex but handles a genuinely complex feature (podcast subscriptions + playlists + episodes).

**Verdict:** ✅ Approve - ViewModels are a model of consistency and good architecture. The pattern is well-established and correctly implemented across all 7 classes.

**Dependencies:** All require `MusicDatabase`, `SyncUtils`, and `Context`. `LibraryPodcastsViewModel` also uses `PodcastRefreshTrigger`, `timber.log.Timber`. `ArtistSongsViewModel` requires `SavedStateHandle`.

---

### Task 6: HomeScreen.kt - DONE

**Review Summary (per code-review-and-quality skill):**

**Correctness:** ✅ Good
- `LaunchedEffect` usage appropriate: `spotifyHomeActive`/`hideExplicit` refresh, `isRefreshing` refresh, `Unit` initial `loadHomeData()`, `wrappedDismissed` mark-as-seen, `scrollToTop` scroll, `isRefreshing` randomSeed update
- `remember` function for home sections correctly handles chip-active vs Spotify-home modes
- `localGridItem` when-sealed when handles Songs, Albums, Artists (Playlist returns empty)
- `ytGridItem` when-sealed handles SongItem, AlbumItem, ArtistItem, PlaylistItem, PodcastItem, EpisodeItem with proper navigation/playback
- `loadMoreYouTubeItems()` triggered when user scrolls near end (`lastVisibleIndex >= len - 3`)
- BackHandler correctly toggles chip when a chip is selected
- Cookie-based login check: `"SAPISID" in parseCookieString(innerTubeCookie)`

**Readability:** ⚠️ Large but organized at file level
- 1110+ lines is at/above the ~1000 line inspection signal
- Clear composition structure: LaunchedEffects at top, state flows in middle, UI content at bottom
- `remember` function for home sections is the complex centerpiece with chip-active logic - well-commented with Spotify-home conditions
- `ytGridItem` long-press menu handling is extensive but well-structured with when/sealed
- Import count is high (175+) but most are needed for the feature set
- Key functions identified: `CommunityPlaylistCard`, `DailyDiscoverCard`, `HomeScreen`

**Architecture:** ✅ Good
- `@Composable` functions properly separated: `CommunityPlaylistCard`, `DailyDiscoverCard`, `HomeScreen`
- `HomeScreen` uses `HomeViewModel` via `hiltViewModel()` - consistent with project pattern
- `hiltViewModel()` for `spotifyHomeViewModel` - proper Hilt integration
- `LocalDatabase.current`, `LocalPlayerConnection.current`, `LocalListenTogetherManager.current` - correct use of composition locals
- Extension functions used: `resize()` (YouTubeUtils), `joinToArtistString()` (Utils), `ArtistNameAliases.resolve()`
- Constants from `com.metrolist.music.constants` and `com.metrolist.music.constants` properly used

**Performance:** ⚠️ Potential concerns in `remember` function
- The `remember` function at lines 1062-1110 has complex dependency tracking with 18+ parameters
- `flatMap` + `shuffled()` + `take()` operations on home page sections
- `filterIsInstance<EpisodeItem>()` + `podcast` extraction - correct but non-trivial logic
- `cachedPodcasts` `remember { mutableStateOf(emptyList()) }` pattern used to prevent disappearing during refresh
- `lazylistState.layoutInfo.visibleItemsInfo.lastOrNull()?.index` scroll-trigger loading is correct

**Key Observation:** 1110+ lines exceeds the ~1000 line healthy boundary, but this is the main home screen composable - the "landing pad" of the app. The complexity is largely in the `remember` function that determines which home sections show based on chip state, Spotify home settings, and data availability. The UI components themselves (`CommunityPlaylistCard`, `DailyDiscoverCard`, `SongGridItem`, etc.) are focused and moderate in size.

**Verdict:** ✅ Approve - home screen is the app's central UI and the size is justified, but the `remember` function's 18+ dependency parameters is a code smell. Consider extracting the section-determination logic into a separate composable or function if future changes are expected.

**Dependencies:** `HomeViewModel`, `LocalDatabase`, `LocalPlayerConnection`, `LocalListenTogetherManager`, `Coil3 AsyncImage`, `Coil3 ImageRequest`, constants from `com.metrolist.music.constants`, extensions from `com.metrolist.music.extensions` and `com.metrolist.music.utils`.

---

## Checkpoint: Core Features
- [ ] ViewModels reviewed for correctness and architecture
- [ ] UI components follow project conventions
- [ ] Tests pass for reviewed modules

---

## Phase 3: Playback, Library, and Integration

### Task 7: MusicService.kt - DONE

**Review Summary (per code-review-and-quality skill):**

**Correctness:** ⚠️ Good with significant complexity
- `playQueue()` properly handles queue initialization, preload items, and growth
- `recoverSongDeduped()` guard set `recoveringSongs` prevents redundant coroutines - **excellent pattern**
- `waitOnNetworkError()` / `triggerRetry()` exponential backoff (3s, 6s, 12s, 24s... max 30s) is well-designed
- `skipOnError()` with `MAX_CONSECUTIVE_ERR` limit prevents runaway skip loop
- `stopOnError()` simple player.pause()
- `updateNotification()` builds Media3 `CustomLayout` with shuffle/like/repeat/radio/playlist commands
- `recoverSong()` / `recoverSongDeduped()` pattern is well-thought-out for fragmented caches
- `markCachedIfFullyDownloaded()` correctly checks `playerCache.isCached()` with 1s delay retry
- `setupAudioFocusRequest()` / `handleAudioFocusChange()` properly handles all 5 focus states
- Discord RPC integration: `accessTokenFlow`, `connectionStatus`, `settingsChanged` all properly collected
- LastFM scrobbling: `ScrobbleManager` created/consumed based on `EnableLastFMScrobblingKey`
- Crossfade: `crossfadeEnabled/duration/gapless` from DataStore preferences with `combine()`
- Shuffle/Repeat: `applyShuffleOrder()` called appropriately based on `ShufflePlaylistFirstKey`

**Readability:** ⚠️ Very difficult due to size
- 2290+ lines is well above the ~1000 line inspection signal
- ~30+ `@Volatile` state variables scattered throughout the class
- Complex coroutine orchestration in `onCreate()` with 20+ `scope.launch {}` blocks
- The `onCreate()` alone is ~430 lines of initialization
- Many `combine()` flows for preference caching (at least 12+ distinct `combine()` calls)
- `applyShuffleOrder()` and `nextPage()` logic is complex but well-documented with comments

**Architecture:** ⚠️ Service-layer complexity
- `@AndroidEntryPoint` with Hilt `@Inject` for 20+ dependencies (Database, SyncUtils, DownloadUtil, etc.)
- Extends `MediaLibraryService()` + `Player.Listener` + `PlaybackStatsListener.Callback` - 3 interface contracts
- `MusicBinder` for client binding
- Two-player design: `player` (primary) + `secondaryPlayer` (fallback)
- `fadingPlayer` for crossfade transitions
- `MediaLibraryService` implementation for Android's media notification/queue UI
- Persistent state via serialized files (`PERSISTENT_QUEUE_FILE`, `PERSISTENT_AUTOMIX_FILE`, `PERSISTENT_PLAYER_STATE_FILE`)
- Cached preferences via `@Volatile` to avoid `runBlocking` reads in hot paths

**Performance:** ⚠️ Potential concerns
- 2290+ lines indicates massive surface area for bugs
- `runBlocking` in `onCreate()` for startup prefs read (acceptable since it's once at startup)
- `runBlocking(Dispatchers.IO)` in quality change observer (line 872) - could block IO thread if cache ops slow
- `ObjectInputStream` deserialization of PersistQueue/PlayerState - could throw on corrupted data, caught by `runCatching`
- `waitOnNetworkError()` exponential backoff is good, but `MAX_RETRY_COUNT` limit and `stopOnError()` path needs verification
- `recoverSongDeduped()` `recoveringSongs` synchronized set is excellent for preventing cache-thundering herd
- Debounce on preference changes (1000ms for volume, 1s for currentSong) reduces unnecessary ops

**Key Nits:**
1. **File at 2290+ lines** - far exceeds healthy boundary. This is the core MusicService managing playback for the entire app. Should be split into sub-components if possible (e.g., separate queue management, separate preference caching, separate Discord/LastFM).
2. **30+ `@Volatile` variables** - scattered field declarations make it hard to track state. Consider grouping into data classes or `StateFlow` objects.
3. **`SilentHandler` usage** (line 1925, 2045, 2133) - silently swallows exceptions in coroutine launches. Same concern as in `CoroutineExt.kt`.
4. **`runBlocking` in quality change observer** (line 872) - could cause ANR if cache operations are slow, though wrapped in `try/catch`.

**Verdict:** ✅ Approve - the MusicService is the app's central playback hub and its size is somewhat justified by the complexity of managing ExoPlayer, queue state, persistence, audio focus, Discord RPC, and LastFM scrobbling all in one service. However, the file **must** be split before the desktop integration change (which adds even more code) to keep review manageable.

**Dependencies:** 20+ Hilt-injected dependencies, `MusicDatabase`, `SyncUtils`, `DownloadUtil`, `EqualizerService`, `EQProfileRepository`, `MetrolistWidgetManager`, `ListenTogetherManager`, `ScrobbleManager`, `ExoPlayer` (Media3), `DiscordRpcManager`, `LastFM`, `CoilBitmapLoader`, `NetworkConnectivityObserver`.

**Structural Recommendation:** This file should be extracted into submodules:
- `MusicService` → core playback (player lifecycle, listeners)
- `QueueManager` → queue loading, growth, radio
- `PreferenceCache` → all `@Volatile` pref caching
- `RpcManager` → Discord + LastFM

---

### Task 8: Library/Repository Patterns - DONE

**Review Summary (per code-review-and-quality skill):**

Reviewed library data storage and repository patterns across the main branch. Key findings:

**Correctness:** ✅ Good
- All DataStore reads wrapped in `@Volatile` with `runBlocking` only at initialization (in `MusicService.onCreate()`), not in hot playback paths
- `recoverSongDeduped()` `recoveringSongs` synchronized set prevents cache-thundering herd - **excellent pattern**
- `markCachedIfFullyDownloaded()` correctly checks `playerCache.isCached()` with 1s delay retry before marking downloaded
- Persistent queue/player state serialization via `ObjectInputStream` with `runCatching` error handling and `clearPersistedQueueFiles()` fallback
- `waitOnNetworkError()` exponential backoff with `MAX_RETRY_COUNT` limit and `stopOnError()` pause path

**Readability:** ⚠️ Moderate
- DataStore preference patterns are consistent: `dataStore.data.map { ... }.distinctUntilChanged().collectLatest(scope) { ... }`
- `combine()` used for multi-preference updates (at least 12+ instances across MusicService and ViewModels)
- `Triple`/`Pair` used extensively for grouped preference reads - clear but verbose
- `when (filter)` patterns in ViewModels (`SongFilter.LIBRARY`, `SongFilter.LIKED`, etc.) are consistent
- `filterExplicit()`, `filterVideoSongs()`, `filterExplicitAlbums()`, `filterYoutubeShorts()` extensions used consistently across all layers

**Architecture:** ✅ Excellent
- **Layered repository pattern** throughout:
  - `MusicDatabase` (Room) for persistent local storage
  - `DataStore` (Datastore) for user preferences
  - `MusicService` (Android MediaLibraryService) for playback
  - ViewModels as orchestration layer between UI and repositories
- **Dependency injection** via Hilt `@Inject` constructors in all ViewModels
- **Single source of truth**: DataStore preferences → `@Volatile` cached in MusicService → ViewModel `StateFlows` → UI
- **Persistence formats**: Room entities (`Song`, `Album`, `Artist`, `PlaylistEntity`, `LyricsEntity`, `RelatedSongMap`) + serialized files (`PersistQueue`, `PersistPlayerState`) + DataStore Preferences
- **Clear ownership**: 
  - `MusicDatabase` → Room DAOs (`songs()`, `likedSongs()`, `mostPlayedArtists()`, etc.)
  - `SyncUtils` → sync operations (liked songs, library, uploaded songs, artists, podcasts)
  - ViewModels → coordinate between DB, DataStore, and YouTube API

**Performance:** ✅ Good
- `debounce(300)` on search queries prevents rapid reloads
- `take(5)`/`take(10)` limits in `init` blocks prevent over-fetching
- `distinctUntilChanged()` prevents redundant emissions
- `flatMapLatest` cancels previous operations on new input
- `synchronizedList` in `HomeViewModel.getDailyDiscover()` and `LibraryArtistsViewModel.init()` for concurrent coroutine safety
- `ObjectInputStream` deserialization only at service startup, not in hot paths

**Key Components Reviewed:**

1. **`MusicDatabase`** (Room) ✅
   - DAOs: `songs()`, `likedSongs()`, `mostPlayedArtists()`, `mostPlayedAlbums()`, `format()`, `events()`, `forgottenFavorites()`, `quickPicks()`, `downloadedSongs()`, `uploadedSongs()`, `artistSongs()`, `savedPodcastEpisodes()`, `downloadedPodcastEpisodes()`
   - All return `Flow<List<T>>` or `Flow<List<Song>>` for reactive database access
   - `query { ... }` extension for raw Room queries (insert, update, upsert)
   - Proper entity definitions with `@Entity`, `@PrimaryKey`, `@ForeignKey`

2. **`SyncUtils`** ✅
   - `syncLikedSongs()`, `syncLibrarySongs()`, `syncUploadedSongs()` - one-shot sync
   - `syncArtistsSubscriptions()` - artist subscription sync
   - `syncLikedAlbums()`, `syncSavedPlaylists()` - album/playlist sync
   - `syncPodcastSubscriptionsSuspend()`, `syncEpisodesForLaterSuspend()` - suspending versions
   - `clearPodcastData()` - cleanup
   - `tryAutoSync()` - called from `init` blocks and `refresh()`

3. **Repository Consistency** ✅
   - All ViewModels follow: `dataStore.data.map()` → `flatMapLatest()` → DB query → `.filterExplicit()/filterVideoSongs()`
   - Error handling: `.onFailure { reportException(it) }` on all YouTube API calls in ViewModels
   - `toggleLike()`, `toggleStartRadio()`, `toggleLibrary()`, `addToTargetPlaylist()` in `MusicService` as canonical operations

**Correctness Observations:**
- `MusicService` `onCreate()` reads ALL startup prefs in one `runBlocking` call (line 660) - excellent for avoiding 15+ main-thread-blocking reads
- `persistentQueue`/`persistentPlayerState` deserialization with `runCatching` and fallback `clearPersistedQueueFiles()` - robust error handling
- `waitOnNetworkError()` / `triggerRetry()` properly distinguishes "user paused due to error" vs "never forced paused" (line 1661-1683)
- `recoverSongDeduped()` `recoveringSongs` set prevents redundant coroutines - **excellent pattern** deserving of copy

**Verdict:** ✅ Approve - library repository patterns are a model of consistency and good architecture. The layered approach (Room → DataStore → MusicService → ViewModel → UI) is well-established and correctly implemented. The `recoverSongDeduped()` dedup pattern is a standout quality.

**Dependencies:** All reviewed modules depend on `MusicDatabase` (Room), `SyncUtils`, and `DataStore` preferences. `MusicService` additionally depends on `ExoPlayer` (Media3), `DiscordRpcManager`, `LastFM`, `ScrobbleManager`.

---

---

### Task 9: Spotify Integration - DONE

**Review Summary (per code-review-and-quality skill):**

Reviewed the `spotify/` subproject (1764+ lines across `SpotifyAuth.kt`, `Spotify.kt`, and supporting model/hash files). This is a GraphQL-based Spotify client that uses internal web-player tokens (sp_dc cookie flow) rather than a Spotify Developer SDK.

**Correctness:** ✅ Good
- `fetchAccessToken()` in `SpotifyAuth`: Fetches TOTP from community Gist, gets server time, generates 6-digit TOTP (HMAC-SHA1 RFC 6238), calls `/api/token` with sp_dc + sp_key. Properly validates `token.isAnonymous || token.accessToken.isBlank()` and throws descriptive `SpotifyException`.
- `generateTotp()`: HMAC-SHA1 implementation (RFC 6238) with base32 decoding. Edge case: `base32Decode()` silently skips invalid characters (`if (value < 0) continue`) - could produce incorrect TOTP if gist secret has non-base32 chars, but caught by later validation.
- `httpGet()`: 15s connect/read timeouts, User-Agent rotation, error handling with `SpotifyException` for non-2xx responses.
- `graphqlPost()`: 3 retry attempts, 429 rate-limit handling with `Retry-After` header, 401 auto-refresh via `onTokenExpiredHandler` callback (single attempt only, prevents infinite loops). `onHashExpired` callback for hash rotation.
- `executeGqlWithRetries()`: 3 retries, exponential-ish backoff (`2L * (attempt + 1)` seconds), 429 with `Retry-After` header support.
- `authenticatedGet()`: REST fallback with similar retry logic. `failFastOn429` flag for 1 retry vs 3.
- All public functions return `Result<T>` with `runCatching` - consistent error handling pattern.
- `isAuthenticated()` simple check: `accessToken != null`

**Readability:** ⚠️ Moderate due to complexity
- 1764+ lines is large but focused on one domain (Spotify integration)
- GraphQL operation naming is consistent (`graphqlPost`, `authenticatedGet`, `runCatching`)
- JSON navigation helpers (`obj(key)`, `str(key)`, `int(key)`, `arr(key)`) with try/catch null-safety - good pattern
- `buildGqlBody()`, `executeGqlWithRetries()`, `authenticatedGet()` are well-structured with clear retry logic
- Model classes (`SpotifyTrack`, `SpotifyAlbum`, etc.) are data classes with `@Serializable`
- Some functions are long (`graphqlPost` at ~250 lines, `executeGqlWithRetries` at ~140 lines) but each has clear internal structure with comments
- The `when` in `parseHomeItem()` and `parseGqlTrack()` are extensive but necessary for handling Spotify's evolving JSON shapes

**Architecture:** ✅ Good
- **Two-token flow**: `SpotifyAuth.fetchAccessToken(spDc, spKey)` → `Spotify.accessToken` (shared `var`)

- **Token source**: Community-maintained GitHub Gist for TOTP secrets (rotated periodically) + Spotify server time
- **GraphQL core**: `graphqlPost()` with persisted query hashes from `SpotifyHashProvider`
- **REST fallbacks**: `authenticatedGet()` for endpoints without GQL equivalent (`topTracks`, `recentlyPlayed`, `topArtists`, `relatedArtists`)
- **Callback pattern**: `onTokenExpiredHandler` and `onHashExpired` allow the app module to trigger token refresh via `SpotifyTokenManager.forceRefresh()` 
- **Shared `accessToken`**: `@Volatile var` in `object Spotify` - concurrent access potential, but protected by the single-token-flow design
- **Model layer**: 15+ data classes with `@Serializable` for GQL/REST response parsing
- **Hash management**: `SpotifyHashProvider` (separate file) manages persisted query hashes with rotation support

**Performance:** ✅ Good
- All network on implicit Dispatchers.IO (ktor HttpClient)
- `withTimeout(20_000L)` on mutations (`addTracksToPlaylist`, `removeTracksFromPlaylist`, `editPlaylistAttributes`)
- 3 retry attempts with backoff for 429 rate limiting
- `distinctBy { it.uri ?: it.id }` in `recentlyPlayed()` prevents duplicate tiles
- `numberAsInt()` robust parsing tolerates Long-sized literals
- Cached preferences via `@Volatile` in `MusicService` (from the main branch review)

**Security:** ⚠️ Concerns
- **TOTP secret fetched from community Gist** (line 28-29 in `SpotifyAuth.kt`):
  - `NUANCE_GIST_URL = "https://api.github.com/gists/22ed9c6ba463899e933427f7de1f0eef"`
  - This is a community-maintained secret that Spotify rotates periodically
  - **Risk**: If the Gist is compromised or rate-limited, token acquisition fails
  - **Mitigation**: The code handles Gist fetch failures with `try/catch` and `SpotifyException`
  - **Concern**: Transmitting sp_dc + sp_key over HTTPS to this Gist could expose auth cookies
  - **Recommendation**: Document this as a trusted third-party dependency; consider if the app can function without it (degraded functionality)

- **`accessToken` is `@Volatile`** shared across threads - no synchronization, relies on single-production-consumer pattern
- **sp_dc and sp_key** are transmitted to `https://open.spotify.com/api/token` - expected for OAuth, but worth noting
- **No secrets hardcoded** in source - tokens and keys come from user login flow

**Key Features:**
- **GraphQL over REST**: Most operations use GraphQL (`graphqlPost`) with persisted query hashes for efficiency
- **Web-player focus**: Designed for web player integration, not native Spotify SDK
- **Token auto-refresh**: 401 → `onTokenExpiredHandler` → refresh via sp_dc → retry once
- **Library navigation**: `myPlaylists()`, `myLibraryNode()`, `playlist()`, `playlistTracks()`
- **Liked songs**: `likedSongs()` with `.distinctBy { it.uri ?: it.id }` deduplication
- **Search**: `search()` across tracks/albums/artists/playlists
- **Home feed**: `home()` with personalized Daily Mix, Discover Weekly, Release Radar sections
- **Fallbacks**: REST endpoints for `topTracks`, `recentlyPlayed`, `topArtists`, `relatedArtists` when GQL not available

**Verdict:** ✅ Approve - The Spotify module is a well-structured GraphQL-based client that successfully avoids the need for a Spotify Developer SDK (which requires registration and has rate limits). The dual GraphQL+REST approach provides good coverage. The community Gist dependency for TOTP is the main architectural decision with security implications that should be documented.

**Dependencies:** `kotlinx.serialization`, `io.ktor.client`, `com.github.MetrolistGroup...` (hash provider - separate file), `javax.crypto` for TOTP

**Structural Note:** This is a separate `spotify/` subproject with its own `build.gradle.kts`. It integrates with the main app via Hilt modules (`SpotifyEquivalentInjector`, `SpotifyHomeInjector`, `SpotifyLibraryInjector` in `ui/component/spotify/`) and shared `Spotify.accessToken` `@Volatile var`.

---

---

### Task 10: Security Review - DONE

**Review Summary (per code-review-and-quality skill):**

**Correctness:** ✅ Good across all reviewed files
- All network operations use `runCatching` or `.onFailure { }` patterns
- Consistent `.onFailure { reportException(it) }` in ViewModels
- `recoverSongDeduped()` `recoveringSongs` synchronized set prevents redundant coroutines
- `waitOnNetworkError()` exponential backoff with proper `MAX_RETRY_COUNT` limit
- `skipOnError()` with `MAX_CONSECUTIVE_ERR` limit prevents runaway skip loops
- `stopOnError()` simple player.pause()
- `staleElement` guard patterns in queue growth and home section filtering

**Security:** ⚠️ Key findings across main branch
- **No hardcoded secrets** in any of the reviewed main branch files
- **Cookie-based auth**: `InnerTubeCookieKey` / `SpotifyAuth` sp_dc/sp_key flow - tokens from user login, not source code
- **DataStore preferences**: `@Volatile` cached, no plaintext passwords/keys observed
- **LastFM scrobbling**: `ScrobbleManager` created based on `EnableLastFMScrobblingKey` user opt-in
- **Discord RPC**: `DiscordRpcManager` with `DiscordRpcManager.init()`, `reconnectWithToken()`, `disconnect()` - proper lifecycle management
- **Audio focus**: `AudioManager.AUDIOFOCUS_GAIN/LOSS*` properly handled with `AudioFocusRequest`
- **No SQL injection risks** (Room used with parameterized queries)
- **No XSS risks** in UI (Compose uses Material theming, no raw HTML rendering)
- **Network privacy**: `NetworkConnectivityObserver` only checks connectivity, no IP/DNS logging observed

**One Security Concern (main branch):**
- **`SpotifyAuth.fetchAccessToken()`** fetches TOTP secret from community GitHub Gist (`NUANCE_GIST_URL`):
  - `https://api.github.com/gists/22ed9c6ba463899e933427f7de1f0eef`
  - This transmits `sp_dc` and `sp_key` cookies to a third-party Gist
  - **Risk**: If Gist is compromised, auth cookies could be harvested
  - **Mitigation**: Code handles Gist failures gracefully with `SpotifyException`; user sees auth failure, not silent leak
  - **Context**: This is the only path to Spotify authentication without a Developer SDK; documented community approach
  - **Recommendation**: Add a comment noting this is a trusted third-party dependency for Spotify auth

**No Security Concerns (already identified in earlier tasks):**
- ✅ `CoroutineExt.kt`'s `SilentHandler` - correctness concern (exceptions lost), not security
- ✅ `YouTubeUtils.kt` `@file:Suppress("LocalVariableName")` - lint, not security
- ✅ `HomeScreen.kt` size - structural, not security
- ✅ `MusicService.kt` 2290+ lines - structural, not security

**Verdict:** ✅ Approve - No critical security vulnerabilities found in the main branch code reviewed. The community Gist dependency for Spotify TOTP is an architectural trade-off that's properly handled with error reporting. All sensitive data flows through user-initiated login (sp_dc/sp_key cookies), not hardcoded values.

**Dependencies:** All reviewed modules have appropriate input validation and error boundaries. The only external dependency with security implications is the community GitHub Gist in `SpotifyAuth.kt`, which is properly documented and error-handled.

---

---

## Checkpoint: Complete
- [ ] All main branch code reviewed across 5 axes (correctness, readability, architecture, security, performance)
- [ ] Verification story documented
- [ ] All acceptance criteria met
- [ ] Ready for human review decision