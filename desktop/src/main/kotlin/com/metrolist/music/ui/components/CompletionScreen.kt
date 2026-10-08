package com.metrolist.music.ui.components

import androidx.compose.foundation.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.unit.*
import com.metrolist.music.ui.theme.*
import com.metrolist.music.ui.theme.ColorTokens

@Composable
fun CompletionScreen(
    onOpenApp: () -> Unit,
    onShowDetails: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color(ColorTokens.SUCCESS),
                        Color(ColorTokens.SUCCESS).copy(alpha = 0.8f)
                    ),
                    startY = 0f,
                    endY = 1f
                )
            )
            .padding(horizontal = Spacing.LG, vertical = Spacing.XXXL),
        horizontalAlignment = Alignment.Center,
        verticalArrangement = Arrangement.Center
    ) {
        // Success Icon Container
        Card(
            modifier = Modifier
                .size(200.dp)
                .padding(Spacing.LG),
            colors = CardColors(
                containerColor = Color(ColorTokens.SURFACE),
                contentColor = Color(ColorTokens.TEXT_PRIMARY),
                disabledContainerColor = Color(ColorTokens.SURFACE_DISABLED),
                disabledContentColor = Color(ColorTokens.TEXT_DISABLED)
            ),
            elevation = Elevation.XXL,
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(
                modifier = Modifier.padding(Spacing.LG),
                horizontalAlignment = Alignment.Center,
                verticalArrangement = Arrangement.Center
            ) {
                // Success Icon
                Icon(
                    painter = painterResource(R.drawable.ic_check_circle),
                    contentDescription = "Success",
                    modifier = Modifier.size(IconSize.XXL),
                    tint = Color(ColorTokens.SUCCESS)
                )
                
                Spacer(modifier = Modifier.height(Spacing.MD))
                
                // Success Message
                Text(
                    text = "Installation Complete!",
                    style = Typography.Title.LARGE,
                    color = Color(ColorTokens.TEXT_PRIMARY)
                )
                
                Spacer(modifier = Modifier.height(Spacing.SM))
                
                Text(
                    text = "MuSicX has been successfully installed on your system.",
                    style = Typography.Body.MEDIUM,
                    color = Color(ColorTokens.TEXT_SECONDARY),
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
                    containerColor = Color(ColorTokens.ACCENT),
                    contentColor = Color(ColorTokens.BACKGROUND),
                    disabledContainerColor = Color(ColorTokens.ACCENT_HOVER),
                    disabledContentColor = Color(ColorTokens.TEXT_DISABLED)
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
                        tint = Color(ColorTokens.BACKGROUND)
                    )
                    
                    Spacer(modifier = Modifier.width(Spacing.SM))
                    
                    Text(
                        text = "Start MuSicX",
                        style = Typography.Label.LARGE,
                        color = Color(ColorTokens.BACKGROUND)
                    )
                }
            }
            
            Spacer(modifier = Modifier.width(Spacing.MD))
            
            // View Details Button
            OutlinedButton(
                onClick = onShowDetails,
                colors = ButtonColors(
                    containerColor = Color.Transparent,
                    contentColor = Color(ColorTokens.TEXT_PRIMARY),
                    disabledContainerColor = Color(ColorTokens.SURFACE_DISABLED),
                    disabledContentColor = Color(ColorTokens.TEXT_DISABLED)
                ),
                borderWidth = 1.dp,
                borderColor = Color(ColorTokens.BORDER_PRIMARY)
            ) {
                Row(
                    horizontalArrangement = Arrangement.Start,
                    verticalAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_info),
                        contentDescription = "Details",
                        modifier = Modifier.size(IconSize.SM),
                        tint = Color(ColorTokens.TEXT_PRIMARY)
                    )
                    
                    Spacer(modifier = Modifier.width(Spacing.SM))
                    
                    Text(
                        text = "View Details",
                        style = Typography.Label.LARGE,
                        color = Color(ColorTokens.TEXT_PRIMARY)
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
                color = Color(ColorTokens.TEXT_SECONDARY)
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
                        tint = Color(ColorTokens.TEXT_SECONDARY)
                    )
                    
                    Spacer(modifier = Modifier.width(Spacing.SM))
                    
                    Text(
                        text = "Start Tutorial",
                        style = Typography.Label.SMALL,
                        color = Color(ColorTokens.TEXT_SECONDARY)
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
                        tint = Color(ColorTokens.TEXT_SECONDARY)
                    )
                    
                    Spacer(modifier = Modifier.width(Spacing.SM))
                    
                    Text(
                        text = "Get Help",
                        style = Typography.Label.SMALL,
                        color = Color(ColorTokens.TEXT_SECONDARY)
                    )
                }
            }
        }
    }
}