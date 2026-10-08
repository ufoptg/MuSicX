package com.metrolist.music.ui.components

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.*
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.shape.RoundedCornerShape
import com.metrolist.music.ui.theme.*
import com.metrolist.music.ui.theme.Colors
import com.metrolist.music.ui.theme.Typography
import androidx.compose.material3.CardDefaults

@Composable
fun ProgressScreen(
    progress: Float,
    statusMessage: String,
    onCancel: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color(Colors.BACKGROUND),
                        Color(Colors.SURFACE),
                        Color(Colors.BACKGROUND)
                    ),
                    startY = 0f,
                    endY = 1f
                )
            )
            .padding(horizontal = Spacing.LG, vertical = Spacing.XXXL),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Progress Container
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.XXL),
            colors = CardColors(
                containerColor = Color(Colors.SURFACE),
                contentColor = Color(Colors.TEXT_PRIMARY),
                disabledContainerColor = Color(Colors.SURFACE_DISABLED),
                disabledContentColor = Color(Colors.TEXT_DISABLED)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Spacing.XXL),
                horizontalAlignment = Alignment.Start
            ) {
                // Progress Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = Spacing.MD),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Vertical.Center
                ) {
                    Text(
                        text = "Installing MuSicX",
                        style = Typography.Title.LARGE,
                        color = Color(Colors.TEXT_PRIMARY)
                    )
                    
                    TextButton(
                        onClick = onCancel,
                        colors = ButtonColors(
                            containerColor = Color.Transparent,
                            contentColor = Color(Colors.ERROR)
                        )
                    ) {
                        Text("Cancel")
                    }
                }
                
                // Progress Bar
                Column(modifier = Modifier.fillMaxWidth()) {
                    Spacer(modifier = Modifier.height(Spacing.MD))
                    
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .padding(horizontal = Spacing.XXXS),
                        color = Color(Colors.ACCENT),
                        trackColor = Color(Colors.SURFACE_DISABLED)
                    )
                    
                    Spacer(modifier = Modifier.height(Spacing.XXS))
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Spacing.XXXS),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Vertical.Center
                    ) {
                        Text(
                            text = "${(progress * 100).toInt()}%",
                            style = Typography.Label.MEDIUM,
                            color = Color(Colors.TEXT_PRIMARY)
                        )
                        
                        Text(
                            text = statusMessage,
                            style = Typography.Label.SMALL,
                            color = Color(Colors.TEXT_SECONDARY)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(Spacing.XL))
                
                // Installation Details
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Installation Details",
                        style = Typography.Title.MEDIUM,
                        color = Color(Colors.TEXT_PRIMARY)
                    )
                    
                    Spacer(modifier = Modifier.height(Spacing.SM))
                    
                    Divider(
                        color = Color(Colors.BORDER_PRIMARY),
                        thickness = 1.dp
                    )
                    
                    Spacer(modifier = Modifier.height(Spacing.SM))
                    
                    // Feature list
                    Column(modifier = Modifier.fillMaxWidth()) {
                        FeatureItem(
                            icon = "ic_check_circle.png",
                            text = "Downloading installer packages..."
                        )
                        
                        Spacer(modifier = Modifier.height(Spacing.XXS))
                        
                        FeatureItem(
                            icon = "ic_check_circle.png",
                            text = "Configuring application settings..."
                        )
                        
                        Spacer(modifier = Modifier.height(Spacing.XXS))
                        
                        FeatureItem(
                            icon = "ic_check_circle.png",
                            text = "Setting up shortcuts and integrations..."
                        )
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(Spacing.XL))
        
        // Tips Section
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardColors(
                containerColor = Color(Colors.SURFACE),
                contentColor = Color(Colors.TEXT_PRIMARY),
                disabledContainerColor = Color(Colors.SURFACE_DISABLED),
                disabledContentColor = Color(Colors.TEXT_DISABLED)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Spacing.MD),
                horizontalAlignment = Alignment.Start
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Start,
                    verticalAlignment = Alignment.Vertical.Center
                ) {
                    Icon(
                        painter = painterResource("ic_light_bulb.png"),
                        contentDescription = "Tip",
                        modifier = Modifier.size(IconSize.SM),
                        tint = Color(Colors.WARNING)
                    )
                    
                    Spacer(modifier = Modifier.width(Spacing.SM))
                    
                    Text(
                        text = "Tip: You can choose custom installation location during setup.",
                        style = Typography.Label.MEDIUM,
                        color = Color(Colors.TEXT_PRIMARY)
                    )
                }
            }
        }
    }
}

@Composable
private fun FeatureItem(
    icon: Int,
    text: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.XXS),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.Vertical.Center
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = text,
            modifier = Modifier.size(IconSize.XXS),
            tint = Color(Colors.SUCCESS)
        )
        
        Spacer(modifier = Modifier.width(Spacing.SM))
        
        Text(
            text = text,
            style = Typography.Label.MEDIUM,
            color = Color(Colors.TEXT_PRIMARY)
        )
    }
}