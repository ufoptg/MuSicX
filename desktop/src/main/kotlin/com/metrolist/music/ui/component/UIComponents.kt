package com.metrolist.music.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.metrolist.music.ui.theme.Dimensions

typealias ImageLoader = @Composable (url: String?, contentDescription: String?, modifier: Modifier) -> Unit

@Composable
fun Thumbnail(
    url: String?,
    contentDescription: String?,
    image: ImageLoader,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(Dimensions.ThumbnailCornerRadius),
    isActive: Boolean = false,
) {
    image(url, contentDescription, modifier.clip(shape).alpha(if (isActive) 0.6f else 1f))
}

@Composable
fun PlayingIndicator(modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom) {
        listOf(8.dp, 14.dp, 10.dp).forEach { h ->
            Box(Modifier.width(2.dp).height(h).background(color, RoundedCornerShape(1.dp)))
        }
    }
}

@Composable
fun OverlayPlayButton(visible: Boolean, modifier: Modifier = Modifier) {
    if (!visible) return
    Box(
        modifier = modifier.size(40.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.55f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Default.PlayArrow, contentDescription = "Play", tint = Color.White)
    }
}

@Composable
fun GridItem(
    title: String,
    subtitle: String?,
    isActive: Boolean,
    isBusy: Boolean,
    onClick: () -> Unit,
    image: ImageLoader,
    modifier: Modifier = Modifier,
    thumbnailUrl: String? = null,
) {
    Column(
        modifier = modifier.width(160.dp).clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(8.dp),
    ) {
        Box(modifier = Modifier.fillMaxWidth().height(Dimensions.GridThumbnailHeight)) {
            Thumbnail(
                url = thumbnailUrl,
                contentDescription = title,
                image = image,
                modifier = Modifier.fillMaxWidth().height(Dimensions.GridThumbnailHeight),
                isActive = isActive,
            )
            OverlayPlayButton(visible = isActive, modifier = Modifier.align(Alignment.Center))
        }
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp),
        )
        if (!subtitle.isNullOrBlank()) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun ListItem(
    title: String,
    subtitle: String?,
    isActive: Boolean,
    isBusy: Boolean,
    onClick: () -> Unit,
    image: ImageLoader,
    modifier: Modifier = Modifier,
    thumbnailUrl: String? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth().height(Dimensions.ListItemHeight).clickable(onClick = onClick).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Thumbnail(
            url = thumbnailUrl,
            contentDescription = title,
            image = image,
            modifier = Modifier.size(Dimensions.ListThumbnailSize),
            isActive = isActive,
        )
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        trailing?.invoke()
    }
}
