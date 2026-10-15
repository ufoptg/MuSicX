package com.metrolist.music.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.metrolist.music.ui.theme.Dimensions

@Composable
fun DetailHeader(
    label: String,
    title: String,
    subtitle: String?,
    metadata: String?,
    thumbnailUrl: String?,
    image: ImageLoader,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    modifier: Modifier = Modifier,
    fullBleed: Boolean = false,
    enhanced: Boolean = false,
    onToggleEnhance: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(top = 8.dp, bottom = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (fullBleed) {
            Box(modifier = Modifier.fillMaxWidth().aspectRatio(1f)) {
                Thumbnail(thumbnailUrl, title, image, Modifier.matchParentSize())
                Box(
                    modifier =
                        Modifier.matchParentSize().background(
                            Brush.verticalGradient(listOf(Color.Transparent, MaterialTheme.colorScheme.background)),
                        ),
                )
            }
        } else {
            Thumbnail(
                url = thumbnailUrl,
                contentDescription = title,
                image = image,
                modifier =
                    Modifier
                        .size(Dimensions.AlbumHeroSize)
                        .shadow(
                            24.dp,
                            RoundedCornerShape(Dimensions.ThumbnailCornerRadius),
                            spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                        ),
                shape = RoundedCornerShape(Dimensions.ThumbnailCornerRadius),
            )
        }

        Spacer(Modifier.height(20.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.secondary,
        )
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 2.dp, start = 32.dp, end = 32.dp),
        )
        if (!subtitle.isNullOrBlank()) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.secondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        if (!metadata.isNullOrBlank()) {
            Text(
                text = metadata,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
        }

        Spacer(Modifier.height(24.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            CircleActionButton(
                icon = Icons.Default.PlayArrow,
                contentDescription = "Play",
                onClick = onPlay,
                size = 72.dp,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                iconSize = 32.dp,
            )
            CircleActionButton(
                icon = Icons.Default.Shuffle,
                contentDescription = "Shuffle",
                onClick = onShuffle,
            )
            if (onToggleEnhance != null) {
                FilledIconToggleButton(
                    checked = enhanced,
                    onCheckedChange = { onToggleEnhance() },
                    modifier = Modifier.size(40.dp),
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = "Enhance")
                }
            }
        }
    }
}
