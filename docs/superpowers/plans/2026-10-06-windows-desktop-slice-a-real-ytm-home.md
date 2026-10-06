# Slice A: Real YTM Home Browse (Desktop)

> **For agentic workers:** REQUIRED SUB-SKILL: superpowers:executing-plans (native is fine for this single slice).

**Goal:** Replace the search-seeded Home shelves on the desktop app with the real YouTube Music home feed, keeping the search-seeded shelves as offline fallback.

**Architecture:** In `DesktopInnerTube.homeFeed()`, first call `innerTube.browse(WEB_REMIX, browseId = "FEmusic_home")`, walk the response JSON for `musicCarouselShelfRenderer` sections (title from `header.musicCarouselShelfBasicHeaderRenderer.title.runs[0].text`), and parse each section's items with the existing `extractHits` walk extended to also handle `musicTwoRowItemRenderer`. If the browse yields no playable rows, fall back to the current category-search shelves.

**Tech Stack:** Kotlin, InnerTubeX (`InnerTube.browse`), kotlinx.serialization JSON, Compose Desktop.

**Spec:** `docs/superpowers/plans/2026-10-06-windows-desktop-parity-roadmap.md` Slice A.

## Global Constraints

- No version bump; no DB schema changes; strings only in default `metrolist_strings.xml`; no readme/markdown edits beyond plan docs; ponytail — keep it minimal.
- Android side must keep compiling; CI Windows build green before done.

## Review Focus

- Home renders when offline/browse fails → fallback shelves appear (not a blank Home).
- Shelf items without videoId (e.g. artist/album cards) don't crash the tap-to-play handler.
- Two-row items (songs/albums) carry title + subtitle + thumbnail like responsive items.
- `homeFeed()` remains a single suspend call — no signature change for `Main.kt` caller.

---

### Task 1: Extend hit extraction to two-row items

**Files:**
- Modify: `desktop/src/main/kotlin/com/metrolist/music/desktop/DesktopInnerTube.kt`

- [ ] Step 1: In `extractHits`'s `walk`, also match `el["musicTwoRowItemRenderer"]` and parse it.
- [ ] Step 2: Add `parseTwoRowRenderer(renderer: JsonObject): SearchHit?` — videoId from `navigationEndpoint.watchEndpoint.videoId` (or `watchPlaylistEndpoint.videoId`) deep-walk fallback via existing `findVideoId`; title from `renderer.title.runs[0].text`; subtitle from `renderer.subtitle.runs.joinToString(" ")`; thumbnail from `thumbnailRenderer.musicThumbnailRenderer.thumbnail.thumbnails.last().url` (fall back to `renderer.thumbnail.musicThumbnailRenderer...` path used by responsive items — reuse `findVideoId`-style walk for `thumbnails`).
- [ ] Step 3: Typecheck: `./gradlew :desktop:compileKotlin` expect SUCCESS.

### Task 2: Real YTM home in `homeFeed()`

**Files:**
- Modify: `desktop/src/main/kotlin/com/metrolist/music/desktop/DesktopInnerTube.kt`

- [ ] Step 1: Add `suspend fun homeRows(): List<HomeRow>` that browses `FEmusic_home`, walks the root for `musicCarouselShelfRenderer` objects, and for each: title via `header.musicCarouselShelfBasicHeaderRenderer.title.runs[*].text` joined, items via `extractHits(shelfSubtree)` (cap 12), keeping only shelves with ≥1 item.
- [ ] Step 2: Change `homeFeed()`: `val real = runCatching { homeRows() }.getOrDefault(emptyList()); if (real.isNotEmpty()) real else <existing search-seeded path>`.
- [ ] Step 3: Typecheck: `./gradlew :desktop:compileKotlin` expect SUCCESS.

### Task 3: Verify

- [ ] Run `./gradlew :desktop:compileKotlin` (Windows CI will package); confirm no other module changed.
- [ ] Commit: `feat(desktop): real YTM home browse with search-seeded fallback`
