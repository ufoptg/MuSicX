/**
 * MuSicX Project (C) 2026
 * Credits to Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 *
 * Desktop Library Screen - adapted for desktop experience
 */

package com.metrolist.music.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.metrolist.music.ui.component.Material3SettingsGroup
import com.metrolist.music.ui.component.Material3SettingsItem

@Composable
fun LibraryScreen(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        Text(
            text = "Library",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 24.dp)
        )
        
        // Library sections
        Material3SettingsGroup(header = "Music Library") {
            Material3SettingsItem(
                icon = { Icon(Icons.Default.MusicNote, contentDescription = null) },
                text = "Songs",
                secondaryText = "All your downloaded and streamed songs",
                onClick = { /* TODO: Navigate to songs screen */ }
            )
            
            Material3SettingsItem(
                icon = { Icon(Icons.Default.Album, contentDescription = null) },
                text = "Albums",
                secondaryText = "Your album collection",
                onClick = { /* TODO: Navigate to albums screen */ }
            )
            
            Material3SettingsItem(
                icon = { Icon(Icons.Default.Person, contentDescription = null) },
                text = "Artists",
                secondaryText = "Artists you follow",
                onClick = { /* TODO: Navigate to artists screen */ }
            )
            
            Material3SettingsItem(
                icon = { Icon(Icons.Default.PlaylistPlay, contentDescription = null) },
                text = "Playlists",
                secondaryText = "Your custom playlists",
                onClick = { /* TODO: Navigate to playlists screen */ }
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Personal sections
        Material3SettingsGroup(header = "Personal") {
            Material3SettingsItem(
                icon = { Icon(Icons.Default.Favorite, contentDescription = null) },
                text = "Favorites",
                secondaryText = "Liked songs and albums",
                onClick = { /* TODO: Navigate to favorites screen */ }
            )
            
            Material3SettingsItem(
                icon = { Icon(Icons.Default.History, contentDescription = null) },
                text = "History",
                secondaryText = "Recently played tracks",
                onClick = { /* TODO: Navigate to history screen */ }
            )
        }
    }
}