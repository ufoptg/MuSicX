/**
 * MuSicX Project (C) 2026
 * Credits to Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 *
 * Desktop Stats Screen - adapted for desktop experience
 */

package com.metrolist.music.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Timer
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
fun StatsScreen(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        Text(
            text = "Statistics",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 24.dp)
        )
        
        // Overview stats
        Material3SettingsGroup(header = "Listening Overview") {
            Material3SettingsItem(
                icon = { Icon(Icons.Default.Timer, contentDescription = null) },
                text = "Total Listening Time",
                secondaryText = "120 hours this month",
                onClick = { /* TODO: Show detailed stats */ }
            )
            
            Material3SettingsItem(
                icon = { Icon(Icons.Default.Audiotrack, contentDescription = null) },
                text = "Songs Played",
                secondaryText = "1,250 songs this month",
                onClick = { /* TODO: Show detailed stats */ }
            )
            
            Material3SettingsItem(
                icon = { Icon(Icons.Default.CalendarToday, contentDescription = null) },
                text = "Days Active",
                secondaryText = "25 days this month",
                onClick = { /* TODO: Show detailed stats */ }
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Detailed stats
        Material3SettingsGroup(header = "Detailed Statistics") {
            Material3SettingsItem(
                icon = { Icon(Icons.Default.Analytics, contentDescription = null) },
                text = "View Detailed Analytics",
                secondaryText = "See comprehensive listening statistics",
                onClick = { /* TODO: Navigate to detailed analytics */ }
            )
        }
    }
}