@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package dev.musicx.shared.ui.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.musicx.shared.generated.resources.Res
import dev.musicx.shared.generated.resources.active
import dev.musicx.shared.generated.resources.cast
import dev.musicx.shared.generated.resources.device_connect_title
import dev.musicx.shared.generated.resources.fullscreen
import dev.musicx.shared.generated.resources.lyrics
import dev.musicx.shared.generated.resources.queue
import dev.musicx.shared.generated.resources.shuffle
import dev.musicx.shared.generated.resources.sleep_timer
import dev.musicx.shared.generated.resources.subtitles
import dev.musicx.shared.generated.resources.video_viewer
import dev.musicx.shared.model.RepeatMode
import dev.musicx.shared.settings.Settings
import dev.musicx.shared.ui.LocalIsTv
import dev.musicx.shared.ui.icons.MuSicXIcons
import dev.musicx.shared.ui.navigation.navigationPopupSource
import dev.musicx.shared.ui.tv.tvFocusHighlight
import org.jetbrains.compose.resources.stringResource

@Composable
actual fun PlayerConnectedButtonGroup(
    onQueueClick: () -> Unit,
    onLyricsClick: (() -> Unit)?,
    isLyricsActive: Boolean,
    isQueueActive: Boolean,
    modifier: Modifier,
    compact: Boolean,
    volume: Float,
    onVolumeChange: (Float) -> Unit,
    onMiniplayerClick: (() -> Unit)?,
    showMiniplayer: Boolean,
    onFullscreenClick: (() -> Unit)?,
    isFullscreen: Boolean,
    onShuffleClick: (() -> Unit)?,
    isShuffled: Boolean,
    onRepeatClick: (() -> Unit)?,
    repeatMode: RepeatMode,
    onSleepTimerClick: (() -> Unit)?,
    isSleepTimerActive: Boolean,
    sleepTimerDisplay: String?,
    sleepTimerContentDescription: String?,
    onVideoClick: (() -> Unit)?,
    isVideoActive: Boolean,
    onSubtitleClick: (() -> Unit)?,
    isSubtitleActive: Boolean,
    onCastClick: (() -> Unit)?,
    isCasting: Boolean,
    onDeviceConnectClick: (() -> Unit)?,
    isDeviceConnectActive: Boolean,
    touchTarget: Dp,
    sectioned: Boolean,
) {
    val iconSize = if (compact) 20.dp else 24.dp
    val buttonWidth = touchTarget + 2.dp

    val actions =
        listOfNotNull(
            PlayerControlAction(
                icon = MuSicXIcons.AutoMirrored.Rounded.QueueMusic,
                contentDescription = stringResource(Res.string.queue),
                onClick = onQueueClick,
                checked = isQueueActive,
            ),
            onSleepTimerClick?.let {
                PlayerControlAction(
                    icon = MuSicXIcons.Default.Bedtime,
                    contentDescription = sleepTimerContentDescription ?: stringResource(Res.string.sleep_timer),
                    onClick = it,
                    checked = isSleepTimerActive,
                    text = sleepTimerDisplay,
                )
            },
            onShuffleClick?.let {
                PlayerControlAction(
                    icon = MuSicXIcons.Rounded.Shuffle,
                    contentDescription = stringResource(Res.string.shuffle),
                    onClick = it,
                    checked = isShuffled,
                )
            },
            onLyricsClick?.let {
                PlayerControlAction(
                    icon = MuSicXIcons.Default.Lyrics,
                    contentDescription = stringResource(Res.string.lyrics),
                    onClick = it,
                    checked = isLyricsActive,
                )
            },
            onRepeatClick?.let {
                PlayerControlAction(
                    icon = if (repeatMode == RepeatMode.ONE) MuSicXIcons.Rounded.RepeatOne else MuSicXIcons.Rounded.Repeat,
                    contentDescription = repeatContentDescription(repeatMode),
                    onClick = it,
                    checked = repeatMode != RepeatMode.OFF,
                )
            },
            onCastClick?.takeIf { !LocalIsTv.current && Settings.shared.isPlayerCastButtonVisible }?.let {
                PlayerControlAction(
                    icon = MuSicXIcons.Outlined.Cast,
                    contentDescription = stringResource(Res.string.cast),
                    onClick = it,
                    checked = isCasting,
                )
            },
            onDeviceConnectClick?.let {
                PlayerControlAction(
                    icon =
                        if (isDeviceConnectActive) {
                            MuSicXIcons.Filled.Devices
                        } else {
                            MuSicXIcons.Outlined.Devices
                        },
                    contentDescription = stringResource(Res.string.device_connect_title),
                    onClick = it,
                    checked = isDeviceConnectActive,
                )
            },
            onVideoClick?.takeIf { Settings.shared.showPlayerVideoButton }?.let {
                PlayerControlAction(
                    icon = MuSicXIcons.Filled.Movie,
                    contentDescription = stringResource(Res.string.video_viewer),
                    onClick = it,
                    checked = isVideoActive,
                )
            },
            onSubtitleClick?.let {
                PlayerControlAction(
                    icon = MuSicXIcons.Default.ClosedCaption,
                    contentDescription = stringResource(Res.string.subtitles),
                    onClick = it,
                    checked = isSubtitleActive,
                )
            },
            onFullscreenClick?.let {
                PlayerControlAction(
                    icon = if (isFullscreen) MuSicXIcons.Default.FullscreenExit else MuSicXIcons.Default.Fullscreen,
                    contentDescription = stringResource(Res.string.fullscreen),
                    onClick = it,
                    checked = isFullscreen,
                )
            },
        )

    if (actions.isEmpty()) return

    ButtonGroup(
        overflowIndicator = { menuState -> ButtonGroupDefaults.OverflowIndicator(menuState) },
        expandedRatio = 1f,
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.height(touchTarget).playerControlBlend(),
    ) {
        actions.forEachIndexed { index, action ->
            customItem(
                buttonGroupContent = {
                    val interactionSource = remember { MutableInteractionSource() }
                    val shapes =
                        when (index) {
                            0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                            actions.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                            else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                        }
                    ToggleButton(
                        checked = action.checked == true,
                        interactionSource = interactionSource,
                        onCheckedChange = { action.onClick() },
                        enabled = action.enabled,
                        shapes = shapes.withStaticCorners(),
                        contentPadding =
                            if (action.text != null) {
                                PaddingValues(horizontal = 8.dp)
                            } else {
                                PaddingValues(0.dp)
                            },
                        colors =
                            ToggleButtonDefaults.colors(
                                containerColor = playerInactiveControlContainerColor(),
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                checkedContainerColor = MaterialTheme.colorScheme.primary,
                                checkedContentColor = MaterialTheme.colorScheme.onPrimary,
                            ),
                        modifier =
                            if (action.text != null) {
                                Modifier.animateWidth(interactionSource, 12.dp).navigationPopupSource().widthIn(min = buttonWidth)
                            } else {
                                Modifier.animateWidth(interactionSource, 12.dp).navigationPopupSource().width(buttonWidth)
                            }.tvFocusHighlight(shapes.shape, bringIntoView = false),
                    ) {
                        if (action.text != null) {
                            Text(
                                text = action.text,
                                modifier = Modifier.semantics { contentDescription = action.contentDescription },
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                            )
                        } else {
                            Icon(
                                imageVector = action.icon,
                                contentDescription = action.contentDescription,
                                modifier = Modifier.size(iconSize),
                            )
                        }
                    }
                },
                menuContent = {
                    val isChecked = action.checked == true
                    val activeColor = MaterialTheme.colorScheme.primary
                    val defaultColor = MaterialTheme.colorScheme.onSurface
                    DropdownMenuItem(
                        modifier =
                            Modifier
                                .widthIn(min = 180.dp)
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                                .clip(RoundedCornerShape(8.dp)),
                        leadingIcon = {
                            Icon(
                                action.icon,
                                contentDescription = null,
                                tint =
                                    if (isChecked) {
                                        activeColor
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                            )
                        },
                        text = {
                            Text(
                                action.contentDescription,
                                color = if (isChecked) activeColor else defaultColor,
                                fontWeight =
                                    if (isChecked) {
                                        androidx.compose.ui.text.font.FontWeight.SemiBold
                                    } else {
                                        androidx.compose.ui.text.font.FontWeight.Normal
                                    },
                            )
                        },
                        trailingIcon =
                            if (isChecked || action.trailingText != null) {
                                @Composable {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        if (action.trailingText != null) {
                                            Text(
                                                action.trailingText,
                                                style = MaterialTheme.typography.labelMedium,
                                                color = activeColor,
                                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                            )
                                        }
                                        if (isChecked) {
                                            Icon(
                                                MuSicXIcons.Default.Check,
                                                contentDescription = stringResource(Res.string.active),
                                                tint = activeColor,
                                                modifier = Modifier.size(18.dp),
                                            )
                                        }
                                    }
                                }
                            } else {
                                null
                            },
                        onClick = { action.onClick() },
                    )
                },
            )
        }
    }
}
