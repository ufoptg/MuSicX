/**
 * MuSicX Project (C) 2026
 * Credits to Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 *
 * Desktop Settings Screen - adapted for desktop experience
 */

package com.metrolist.music.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Update
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
fun SettingsScreen(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 24.dp)
        )
        
        // Appearance settings
        Material3SettingsGroup(header = "Appearance") {
            Material3SettingsItem(
                icon = { Icon(Icons.Default.Palette, contentDescription = null) },
                text = "Theme",
                secondaryText = "Customize the look and feel of the app",
                onClick = { /* TODO: Navigate to theme settings */ }
            )
            
            Material3SettingsItem(
                icon = { Icon(Icons.Default.AutoAwesome, contentDescription = null) },
                text = "Display",
                secondaryText = "Adjust display settings and layout",
                onClick = { /* TODO: Navigate to display settings */ }
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Player settings
        Material3SettingsGroup(header = "Player") {
            Material3SettingsItem(
                icon = { Icon(Icons.Default.Audiotrack, contentDescription = null) },
                text = "Player Settings",
                secondaryText = "Configure audio playback options",
                onClick = { /* TODO: Navigate to player settings */ }
            )
            
            Material3SettingsItem(
                icon = { Icon(Icons.Default.Alarm, contentDescription = null) },
                text = "Sleep Timer",
                secondaryText = "Set automatic sleep timer for playback",
                onClick = { /* TODO: Navigate to sleep timer settings */ }
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Content settings
        Material3SettingsGroup(header = "Content") {
            Material3SettingsItem(
                icon = { Icon(Icons.Default.Language, contentDescription = null) },
                text = "Content Preferences",
                secondaryText = "Language and regional settings",
                onClick = { /* TODO: Navigate to content settings */ }
            )
            
            Material3SettingsItem(
                icon = { Icon(Icons.Default.Storage, contentDescription = null) },
                text = "Storage",
                secondaryText = "Manage storage and downloads",
                onClick = { /* TODO: Navigate to storage settings */ }
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Account settings
        Material3SettingsGroup(header = "Account") {
            Material3SettingsItem(
                icon = { Icon(Icons.Default.AccountCircle, contentDescription = null) },
                text = "Account",
                secondaryText = "Manage your account settings",
                onClick = { /* TODO: Navigate to account settings */ }
            )
            
            Material3SettingsItem(
                icon = { Icon(Icons.Default.Security, contentDescription = null) },
                text = "Privacy",
                secondaryText = "Privacy and security settings",
                onClick = { /* TODO: Navigate to privacy settings */ }
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // About section
        Material3SettingsGroup(header = "About") {
            Material3SettingsItem(
                icon = { Icon(Icons.Default.Info, contentDescription = null) },
                text = "About",
                secondaryText = "App version and credits",
                onClick = { /* TODO: Navigate to about screen */ }
            )
            
            Material3SettingsItem(
                icon = { Icon(Icons.Default.Update, contentDescription = null) },
                text = "Check for Updates",
                secondaryText = "Check for new versions of MuSicX",
                onClick = { /* TODO: Check for updates */ }
            )
        }
    }
}