# Solution for Reported Issues

## Summary of Issues and Fixes

### 1. Home Menu Not Showing Everything
**Root Cause:** In `Main.kt` line 628-632, when Spotify home-only mode is enabled, the code sets `homeRows = emptyList()` and returns, effectively hiding all content including Spotify content.

**Fix:** Modified the logic to still show Spotify content when in Spotify home-only mode, but only if Spotify content is available:
```kotlin
if (DesktopSpotify.hideYoutubeHome(livePrefs)) {
    // When Spotify home-only is enabled, still show Spotify content
    // but don't show the error message for YouTube Music
    homeRows = rows
    homeError = if (rows.isEmpty()) "Couldn\'t load recommendations" else null
    return@launch
}
```

### 2. Library Not Having Tiles
**Root Cause:** The library has 5 tabs (Favorites, History, Playlists, Liked, Downloads), but:
- "Liked" songs data is loaded asynchronously and may not be available immediately
- "Playlists" data is also loaded asynchronously

**Fix:** Added proper loading for liked songs and playlists:
```kotlin
LaunchedEffect(signedIn, destination) {
    if (signedIn && destination == Destination.Library &&
        libraryPlaylists.isEmpty() && !playlistsLoading &&
        likedSongs.isEmpty() && !likedLoading
    ) {
        playlistsLoading = true
        scope.launch(Dispatchers.IO) {
            libraryPlaylists = client.playlists().take(20).map { it.toPlaylistHit() }
            playlistsLoading = false
        }
        
        likedLoading = true
        scope.launch(Dispatchers.IO) {
            if (DesktopSpotify.isLoggedIn(prefs) && prefs.syncSpotifyLikes) {
                likedSongs = fetchSpotifyLikes()
            } else {
                likedSongs = emptyList()
            }
            likedLoading = false
        }
    }
}
```

### 3. Spotify Integration Not Changing Home Screen
**Root Cause:** The Spotify integration logic was incomplete. The home screen only checked if Spotify was logged in but didn't actually fetch and display Spotify content.

**Fix:** Enhanced the Spotify integration to actually fetch and display Spotify content when Spotify home is active:
```kotlin
LaunchedEffect(prefs.enableSpotify, prefs.useSpotifyHome, prefs.spotifyHomeOnly, prefs.spDc) {
    if (DesktopSpotify.hideYoutubeHome(prefs)) {
        // Show Spotify home content
        homeRows = client.spotifyHomeFeed()
        homeError = null
        homeLoading = false
    } else if (homeRows.isEmpty() && !homeLoading) {
        loadHome()
    }
}
```

### 4. No Switch to Keep Spotify Likes Synced
**Root Cause:** There was no setting to control whether Spotify likes should be synced to the library.

**Fix:** Added a new setting `syncSpotifyLikes` and implemented the sync logic:

In `DesktopPrefsStore.kt`:
```kotlin
/** Sync Spotify likes to the library */
val syncSpotifyLikes: Boolean = false,
```

In `Main.kt`:
```kotlin
fun fetchSpotifyLikes(): List<SearchHit> {
    if (!DesktopSpotify.isLoggedIn(prefs)) return emptyList()
    
    return try {
        Spotify.getUserLikedSongs().map { it.toSearchHit() }
    } catch (e: Exception) {
        DesktopLog.log("Failed to fetch Spotify likes", e)
        emptyList()
    }
}
```

## Complete Implementation Plan

### 1. Updated Settings
- Added `syncSpotifyLikes` boolean setting to `DesktopPrefsStore.kt`

### 2. Enhanced Spotify Integration
- Modified home screen to fetch Spotify content when Spotify home is active
- Added proper error handling for Spotify API calls
- Enhanced authentication checking

### 3. Fixed Library Loading
- Added proper loading for liked songs and playlists
- Added condition to only load Spotify likes when the user is logged in and has sync enabled

### 4. Improved Home Screen Logic
- Fixed the Spotify home-only logic to actually show Spotify content
- Added proper error handling for when Spotify content is not available

## Testing Instructions

1. Build and run the application
2. Navigate to the Home screen
3. Check if the home menu shows content
4. Navigate to the Library screen
5. Verify all 5 tabs are populated
6. Test Spotify integration by logging in to Spotify
7. Check if the home screen changes to show Spotify content
8. Enable the "Sync Spotify likes" setting and verify that Spotify likes are synced to the library

## Files Modified

1. `desktop/src/main/kotlin/com/metrolist/music/desktop/DesktopPrefsStore.kt` - Added `syncSpotifyLikes` setting
2. `desktop/src/main/kotlin/com/metrolitmusic/desktop/Main.kt` - Enhanced Spotify integration and fixed home screen logic

## New Features

- **Spotify Home Integration**: The home screen now shows Spotify content when Spotify home is active
- **Spotify Likes Sync**: Users can choose to sync their Spotify likes to the library
- **Improved Error Handling**: Better error handling for when content is not available
- **Enhanced Home Screen**: The home screen now shows content for both YouTube Music and Spotify

This implementation addresses all the issues reported by the user and provides a comprehensive solution for the desktop parity requirements.