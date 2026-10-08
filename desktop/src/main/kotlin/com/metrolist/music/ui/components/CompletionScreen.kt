package com.metrolist.music.ui.components

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.*
import com.metrolist.music.ui.theme.*
import com.metrolist.music.ui.theme.Colors
import com.metrolist.music.ui.theme.Spacing
import com.metrolist.music.ui.theme.Typography
import com.metrolist.music.ui.theme.BorderRadius
import com.metrolist.music.ui.theme.Elevation
import com.metrolist.music.ui.theme.IconSize

@Composable
fun CompletionScreen(
    onOpenApp: () -> Unit,
    onShowDetails: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(Colors.BACKGROUND))
            .padding(horizontal = Spacing.MD, vertical = Spacing.XXXL),
        horizontalAlignment = Alignment.Center,
        verticalArrangement = Arrangement.Center
    ) {
        // Success Icon Container
        Card(
            modifier = Modifier
                .size(200.dp)
                .padding(Spacing.LG)
                .background(Color(Colors.SURFACE))
                .elevation(Elevation.XXL),
            shape = BorderRadius.XXL
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Spacing.LG),
                horizontalAlignment = Alignment.Center,
                verticalArrangement = Arrangement.Center
            ) {
                // Success Icon
                Icon(
                    painter = painterResource(R.drawable.ic_check_circle),
                    contentDescription = "Success",
                    modifier = Modifier.size(IconSize.XXL),
                    tint = Color(AndroidColor.parseColor(Colors.SUCCESS))
                )
                
                Spacer(modifier = Modifier.height(Spacing.MD))
                
                // Success Message
                Text(
                    text = "Installation Complete!",
                    style = Typography.Title.LARGE,
                    color = Color(AndroidColor.parseColor(Colors.TEXT_PRIMARY))
                )
                
                Spacer(modifier = Modifier.height(Spacing.SM))
                
                Text(
                    text = "MuSicX has been successfully installed on your system.",
                    style = Typography.Body.MEDIUM,
                    color = Color(AndroidColor.parseColor(Colors.TEXT_SECONDARY)),
                    textAlign = TextAlign.Center
                )
            }
        }
        
        Spacer(modifier = Modifier.height(Spacing.XXL))
        
        // Action Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            // Open App Button
            Button(
                onClick = onOpenApp,
                colors = ButtonColors(
                    containerColor = Color(Colors.ACCENT),
                    contentColor = Color(Colors.BACKGROUND),
                    disabledContainerColor = Color(Colors.ACCENT_HOVER),
                    disabledContentColor = Color(Colors.TEXT_DISABLED)
                )
            ) {
                Row(
                    horizontalArrangement = Arrangement.Start,
                    verticalAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_play),
                        contentDescription = "Open App",
                        modifier = Modifier.size(IconSize.SM),
                        tint = Color(Colors.BACKGROUND)
                    )
                    
                    Spacer(modifier = Modifier.width(Spacing.SM))
                    
                    Text(
                        text = "Start MuSicX",
                        style = Typography.Label.LARGE,
                        color = Color(Colors.BACKGROUND)
                    )
                }
            }
            
            Spacer(modifier = Modifier.width(Spacing.MD))
            
            // View Details Button
            OutlinedButton(
                onClick = onShowDetails,
                colors = ButtonColors(
                    containerColor = Color.Transparent,
                    contentColor = Color(Colors.TEXT_PRIMARY),
                    disabledContainerColor = Color(Colors.SURFACE_DISABLED),
                    disabledContentColor = Color(Colors.TEXT_DISABLED)
                ),
                borderWidth = 1.dp,
                borderColor = Color(Colors.BORDER_PRIMARY)
            ) {
                Row(
                    horizontalArrangement = Arrangement.Start,
                    verticalAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_info),
                        contentDescription = "Details",
                        modifier = Modifier.size(IconSize.SM),
                        tint = Color(Colors.TEXT_PRIMARY)
                    )
                    
                    Spacer(modifier = Modifier.width(Spacing.SM))
                    
                    Text(
                        text = "View Details",
                        style = Typography.Label.LARGE,
                        color = Color(Colors.TEXT_PRIMARY)
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(Spacing.XXL))
        
        // Additional Options
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.Center
        ) {
            Text(
                text = "What would you like to do next?",
                style = Typography.Label.MEDIUM,
                color = Color(Colors.TEXT_SECONDARY)
            )
            
            Spacer(modifier = Modifier.height(Spacing.SM))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // Start Tutorial Option
                Row(
                    modifier = Modifier.clickable { /* TODO: Handle click */ },
                    horizontalArrangement = Arrangement.Start,
                    verticalAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_tutorial),
                        contentDescription = "Tutorial",
                        modifier = Modifier.size(IconSize.XXS),
                        tint = Color(Colors.TEXT_SECONDARY)
                    )
                    
                    Spacer(modifier = Modifier.width(Spacing.SM))
                    
                    Text(
                        text = "Start Tutorial",
                        style = Typography.Label.SMALL,
                        color = Color(Colors.TEXT_SECONDARY)
                    )
                }
                
                Spacer(modifier = Modifier.width(Spacing.LG))
                
                // Support Option
                Row(
                    modifier = Modifier.clickable { /* TODO: Handle click */ },
                    horizontalArrangement = Arrangement.Start,
                    verticalAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_help),
                        contentDescription = "Help",
                        modifier = Modifier.size(IconSize.XXS),
                        tint = Color(Colors.TEXT_SECONDARY)
                    )
                    
                    Spacer(modifier = Modifier.width(Spacing.SM))
                    
                    Text(
                        text = "Get Help",
                        style = Typography.Label.SMALL,
                        color = Color(Colors.TEXT_SECONDARY)
                    )
                }
            }
        }
    }
}