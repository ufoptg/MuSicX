package com.metrolist.music.ui.components

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.*
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.*
import androidx.compose.foundation.shape.RoundedCornerShape
import com.metrolist.music.ui.theme.*
import com.metrolist.music.ui.theme.Colors
import androidx.compose.material3.CardDefaults

@Composable
fun WelcomeScreen(
    onInstall: () -> Unit,
    onSkip: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(Colors.BACKGROUND))
            .padding(horizontal = Spacing.MD, vertical = Spacing.XXXL),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Logo Section
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                painter = painterResource("musix_logo.png"),
                contentDescription = "MuSicX Logo",
                modifier = Modifier.size(IconSize.XL),
                tint = Color(Colors.TEXT_PRIMARY)
            )
            
            Spacer(modifier = Modifier.height(Spacing.LG))
            
            Text(
                text = "MuSicX",
                style = MaterialTheme.typography.titleLarge,
                color = Color(Colors.TEXT_PRIMARY)
            )
            
            Spacer(modifier = Modifier.height(Spacing.SM))
            
            Text(
                text = "Your music, perfectly organized",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(Colors.TEXT_SECONDARY)
            )
        }
        
        Spacer(modifier = Modifier.height(Spacing.XXXL))
        
        // Installation Options
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = "Choose Installation Type",
                style = MaterialTheme.typography.titleMedium,
                color = Color(Colors.TEXT_PRIMARY)
            )
            
            Spacer(modifier = Modifier.height(Spacing.MD))
            
            // Basic Installation
            InstallationOption(
                title = "Basic Install",
                description = "Standard installation with core features",
                icon = "ic_basic_install.png",
                onClick = onInstall
            )
            
            Spacer(modifier = Modifier.height(Spacing.SM))
            
            // Custom Installation
            InstallationOption(
                title = "Custom Install",
                description = "Choose specific components and installation location",
                icon = "ic_custom_install.png",
                onClick = onInstall
            )
        }
        
        Spacer(modifier = Modifier.height(Spacing.XXXL))
        
        // Skip Option
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(
                onClick = onSkip,
                colors = ButtonColors(
                    containerColor = Color.Transparent,
                    contentColor = Color(Colors.TEXT_SECONDARY),
                    disabledContainerColor = Color.Transparent,
                    disabledContentColor = Color(Colors.TEXT_DISABLED)
                )
            ) {
                Text("Skip installation")
            }
        }
        
        // Footer
        Spacer(modifier = Modifier.height(Spacing.XXL))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = "By installing, you agree to our Terms of Service and Privacy Policy",
                style = MaterialTheme.typography.labelSmall,
                color = Color(Colors.TEXT_TERTIARY)
            )
        }
    }
}

@Composable
private fun InstallationOption(
    title: String,
    description: String,
    icon: Int,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp)
            .clickable { onClick() },
        colors = CardColors(
            containerColor = Color(Colors.SURFACE),
            contentColor = Color(Colors.TEXT_PRIMARY),
            disabledContainerColor = Color(Colors.SURFACE_DISABLED),
            disabledContentColor = Color(Colors.TEXT_DISABLED)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.MD),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = title,
                modifier = Modifier.size(IconSize.LG),
                tint = Color(Colors.ACCENT)
            )
            
            Spacer(modifier = Modifier.width(Spacing.MD))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color(Colors.TEXT_PRIMARY)
                )
                
                Spacer(modifier = Modifier.height(Spacing.XXS))
                
                Text(
                    text = description,
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(Colors.TEXT_SECONDARY)
                )
            }
            
            Icon(
                painter = painterResource("ic_chevron_right.png"),
                contentDescription = "Continue",
                modifier = Modifier.size(IconSize.SM),
                tint = Color(Colors.TEXT_SECONDARY)
            )
        }
    }
}