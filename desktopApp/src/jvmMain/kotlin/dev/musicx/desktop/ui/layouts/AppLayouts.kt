/**
 * MuSicX Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

@file:OptIn(
    ExperimentalMaterial3Api::class,
    androidx.compose.ui.ExperimentalComposeUiApi::class,
    ExperimentalMaterial3ExpressiveApi::class,
    androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class,
)

package dev.musicx.desktop.ui.layouts

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.VerticalDragHandle
import androidx.compose.material3.WideNavigationRailDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowScope
import dev.musicx.desktop.state.app.DesktopNavigation
import dev.musicx.desktop.state.app.UiState
import dev.musicx.desktop.state.content.HomeState
import dev.musicx.desktop.state.content.LibraryContentState
import dev.musicx.desktop.state.content.LibraryState
import dev.musicx.desktop.state.content.SearchState
import dev.musicx.desktop.state.playback.PlayerState
import dev.musicx.desktop.ui.components.AddToPlaylistDialog
import dev.musicx.desktop.ui.components.CustomTitleBar
import dev.musicx.desktop.ui.components.DesktopLyricsContent
import dev.musicx.desktop.ui.components.DesktopPlayerVisibility
import dev.musicx.desktop.ui.components.DesktopQueueContent
import dev.musicx.desktop.ui.components.DeviceConnectModalSideSheet
import dev.musicx.desktop.ui.components.DeviceConnectSideSheet
import dev.musicx.desktop.ui.components.LyricsDrawer
import dev.musicx.desktop.ui.components.PersistentPlayerDrawer
import dev.musicx.desktop.ui.components.StandardBottomPlayer
import dev.musicx.desktop.ui.components.TopBarWithPlayer
import dev.musicx.desktop.ui.components.VideoMiniViewer
import dev.musicx.desktop.ui.components.deviceConnectSideSheetWidth
import dev.musicx.shared.generated.resources.Res
import dev.musicx.shared.generated.resources.close
import dev.musicx.shared.generated.resources.desktop_navigation_together
import dev.musicx.shared.generated.resources.done
import dev.musicx.shared.generated.resources.edit_sidebar
import dev.musicx.shared.generated.resources.filter_library
import dev.musicx.shared.generated.resources.home
import dev.musicx.shared.generated.resources.menu_history
import dev.musicx.shared.generated.resources.statistics
import dev.musicx.shared.settings.NavItem
import dev.musicx.shared.settings.NavigationId
import dev.musicx.shared.settings.Settings
import dev.musicx.shared.settings.defaultOpenTabRoot
import dev.musicx.shared.settings.desktopDefaultOpenTab
import dev.musicx.shared.ui.icons.MuSicXIcons
import dev.musicx.shared.ui.navigation.AdaptiveFormFactor
import dev.musicx.shared.ui.navigation.AdaptiveLayout
import dev.musicx.shared.ui.navigation.AdaptiveNavigationItem
import dev.musicx.shared.ui.navigation.AdaptiveNavigationScaffold
import dev.musicx.shared.ui.navigation.AdaptiveWidthBreakpoint
import dev.musicx.shared.ui.navigation.LocalNavigator
import dev.musicx.shared.ui.navigation.Screen
import dev.musicx.shared.ui.navigation.currentAdaptiveLayout
import dev.musicx.shared.ui.navigation.isNavigationBar
import dev.musicx.shared.ui.navigation.navigationId
import dev.musicx.shared.ui.navigation.screenForNavigationId
import org.jetbrains.compose.resources.stringResource
import java.awt.Cursor

data class LayoutState(
    val searchText: String,
    val onSearchChange: (String) -> Unit,
    val isSearchFocused: Boolean,
    val onSearchFocusChange: (Boolean) -> Unit,
    val selectedNavIndex: Int,
)

private var searchText by mutableStateOf("")
private var isSearchFocused by mutableStateOf(false)
private var selectedNavId by
    mutableStateOf(
        if (defaultOpenTabRoot(Settings.shared.desktopDefaultOpenTab()) == "LIBRARY") NavigationId.LIBRARY else NavigationId.HOME,
    )
private var isSidebarExpanded by mutableStateOf(false)

private fun currentSelectedNavIndex(
    visibleItems: List<NavItem>,
    navigationId: String?,
): Int = visibleItems.indexOfFirst { it.id == navigationId }.coerceAtLeast(0)

private fun refreshActionFor(route: Screen): (() -> Unit)? =
    when (route) {
        Screen.Home -> {
            HomeState::refresh
        }

        Screen.Library -> {
            { LibraryContentState.fetchLibraryData(force = true) }
        }

        Screen.History,
        Screen.Statistics,
        -> {
            { LayoutUtils.refreshCurrentPage(route) }
        }

        else -> {
            null
        }
    }

private fun navIcons(navId: String): Pair<ImageVector, ImageVector> =
    when (navId) {
        NavigationId.HOME -> MuSicXIcons.Outlined.Home to MuSicXIcons.Default.Home
        NavigationId.LIBRARY -> MuSicXIcons.Outlined.LibraryMusic to MuSicXIcons.Default.LibraryMusic
        NavigationId.HISTORY -> MuSicXIcons.Outlined.MusicHistory to MuSicXIcons.Filled.MusicHistory
        NavigationId.STATISTICS -> MuSicXIcons.Outlined.SsidChart to MuSicXIcons.Filled.SsidChart
        NavigationId.LISTEN_TOGETHER -> MuSicXIcons.Outlined.Group to MuSicXIcons.Filled.Groups
        else -> MuSicXIcons.Default.QuestionMark to MuSicXIcons.Default.QuestionMark
    }

internal fun reorderSidebarNavItems(
    items: List<NavItem>,
    orderedIds: List<String>,
): List<NavItem> {
    val ids = orderedIds.toSet()
    val reordered = orderedIds.mapNotNull { id -> items.firstOrNull { it.id == id } }.iterator()
    return items.map { item ->
        if (item.id in ids && reordered.hasNext()) reordered.next() else item
    }
}

@Composable
private fun DesktopNavigationScaffold(
    layoutState: LayoutState,
    adaptiveLayout: AdaptiveLayout,
    colorScheme: ColorScheme,
    onLibraryReselect: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (isNavigationRail: Boolean) -> Unit,
) {
    val isNavigationRail = !adaptiveLayout.navigationSuiteType.isNavigationBar
    val allItems =
        LibraryState.sidebarNavItems.mapNotNull { item ->
            if (item.id == NavigationId.LISTEN_TOGETHER && !Settings.shared.listenTogetherEnabled) return@mapNotNull null
            val icons = navIcons(item.id)
            val labelResource =
                when (item.id) {
                    NavigationId.HOME -> Res.string.home
                    NavigationId.LIBRARY -> Res.string.filter_library
                    NavigationId.HISTORY -> Res.string.menu_history
                    NavigationId.STATISTICS -> Res.string.statistics
                    NavigationId.LISTEN_TOGETHER -> Res.string.desktop_navigation_together
                    else -> return@mapNotNull null
                }
            AdaptiveNavigationItem(
                key = item.id,
                label = stringResource(labelResource),
                icon = icons.first,
                selectedIcon = icons.second,
                visible = item.visible,
            )
        }

    AdaptiveNavigationScaffold(
        layout = adaptiveLayout,
        items = allItems,
        selectedKey = selectedNavId,
        onSelect = { id ->
            val destination = screenForNavigationId(id) ?: return@AdaptiveNavigationScaffold
            val reselectLibrary =
                destination == Screen.Library && DesktopNavigation.navigator.state.topLevelRoute == Screen.Library
            selectedNavId = id
            searchText = ""
            DesktopNavigation.navigate(destination)
            if (reselectLibrary) onLibraryReselect()
        },
        modifier = modifier.fillMaxSize(),
        navigationSuiteColors =
            NavigationSuiteDefaults.colors(
                wideNavigationRailColors =
                    WideNavigationRailDefaults.colors(
                        containerColor = colorScheme.surfaceContainer,
                    ),
            ),
        containerColor = colorScheme.background,
        itemModifier = {
            if (isNavigationRail) {
                Modifier.fillMaxWidth().padding(horizontal = 4.dp)
            } else {
                Modifier
            }
        },
        isSidebarExpanded = isSidebarExpanded || LibraryState.isEditingSidebar,
        isSidebarExpandable = true,
        onToggleSidebar = {
            if (LibraryState.isEditingSidebar) {
                LibraryState.isEditingSidebar = false
            }
            isSidebarExpanded = !isSidebarExpanded
        },
        navigationHeader = { type ->
            if (type == NavigationSuiteType.WideNavigationRailExpanded) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    IconButton(
                        onClick = {
                            if (LibraryState.isEditingSidebar) {
                                LibraryState.isEditingSidebar = false
                            }
                            isSidebarExpanded = false
                        },
                        modifier = Modifier.size(48.dp),
                        shapes = IconButtonDefaults.shapes(),
                    ) {
                        Icon(
                            imageVector = MuSicXIcons.AutoMirrored.Filled.MenuOpen,
                            contentDescription = stringResource(Res.string.close),
                            tint = colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp),
                        )
                    }

                    IconButton(
                        onClick = { LibraryState.isEditingSidebar = !LibraryState.isEditingSidebar },
                        modifier = Modifier.size(36.dp),
                        shapes = IconButtonDefaults.shapes(),
                    ) {
                        Icon(
                            imageVector = if (LibraryState.isEditingSidebar) MuSicXIcons.Default.Check else MuSicXIcons.Default.Edit,
                            contentDescription =
                                stringResource(
                                    if (LibraryState.isEditingSidebar) Res.string.done else Res.string.edit_sidebar,
                                ),
                            tint = if (LibraryState.isEditingSidebar) colorScheme.primary else colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            } else {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    IconButton(
                        onClick = {
                            LibraryState.isEditingSidebar = !LibraryState.isEditingSidebar
                            if (LibraryState.isEditingSidebar) {
                                isSidebarExpanded = true
                            }
                        },
                        modifier = Modifier.size(36.dp),
                        shapes = IconButtonDefaults.shapes(),
                    ) {
                        Icon(
                            imageVector = if (LibraryState.isEditingSidebar) MuSicXIcons.Default.Check else MuSicXIcons.Default.Edit,
                            contentDescription =
                                stringResource(
                                    if (LibraryState.isEditingSidebar) Res.string.done else Res.string.edit_sidebar,
                                ),
                            tint = if (LibraryState.isEditingSidebar) colorScheme.primary else colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        },
        isEditing = LibraryState.isEditingSidebar,
        onReorderFinished = { order ->
            LibraryState.saveSidebarNavItems(reorderSidebarNavItems(LibraryState.sidebarNavItems, order))
        },
        onToggleVisibility = { id ->
            val newList =
                LibraryState.sidebarNavItems.map {
                    if (it.id == id) it.copy(visible = !it.visible) else it
                }
            LibraryState.saveSidebarNavItems(newList)
        },
    ) {
        content(isNavigationRail)
    }
}

@Composable
fun WindowScope.ExpressLayout(
    onMaximized: () -> Unit,
    toggleWindowFullscreen: () -> Unit,
    requestBackgroundFocus: () -> Unit,
) {
    AdaptiveDesktopLayout(
        onMaximized = onMaximized,
        toggleWindowFullscreen = toggleWindowFullscreen,
        requestBackgroundFocus = requestBackgroundFocus,
        playerInTopBar = false,
    )
}

@Composable
fun WindowScope.FocusedLayout(
    onMaximized: () -> Unit,
    toggleWindowFullscreen: () -> Unit,
    requestBackgroundFocus: () -> Unit,
) {
    AdaptiveDesktopLayout(
        onMaximized = onMaximized,
        toggleWindowFullscreen = toggleWindowFullscreen,
        requestBackgroundFocus = requestBackgroundFocus,
        playerInTopBar = true,
    )
}

@Composable
private fun WindowScope.AdaptiveDesktopLayout(
    onMaximized: () -> Unit,
    toggleWindowFullscreen: () -> Unit,
    requestBackgroundFocus: () -> Unit,
    playerInTopBar: Boolean,
) {
    val colorScheme = MaterialTheme.colorScheme
    val visibleItems by LayoutUtils.rememberVisibleNavItems()
    val navigator = LocalNavigator.current
    val libraryGridState = rememberLazyGridState()
    var activeLibraryGridState by remember { mutableStateOf(libraryGridState) }
    val route = navigator.state.currentRoute
    val routeNavigationId = route.navigationId
    val currentNavIndex = currentSelectedNavIndex(visibleItems, routeNavigationId ?: selectedNavId)
    val currentTitle by LayoutUtils.rememberCurrentTitle(route, visibleItems, currentNavIndex)
    val adaptiveLayout = currentAdaptiveLayout(AdaptiveFormFactor.DESKTOP, Settings.shared.forceBottomNavBar)

    LaunchedEffect(route) {
        routeNavigationId?.let { selectedNavId = it }
        if ((routeNavigationId != null) && (route != Screen.Search)) searchText = ""
    }

    val layoutState =
        LayoutState(
            searchText = searchText,
            onSearchChange = { value ->
                searchText = value
                SearchState.onQueryChange(value)
                if (value.isNotEmpty() && navigator.state.currentRoute != Screen.Search) {
                    navigator.navigate(Screen.Search)
                }
            },
            isSearchFocused = isSearchFocused,
            onSearchFocusChange = {
                isSearchFocused = it
                UiState.isSearchFocused = it
            },
            selectedNavIndex = currentNavIndex,
        )

    Column(Modifier.fillMaxSize()) {
        if (playerInTopBar) {
            TopBarWithPlayer(
                colorScheme = colorScheme,
                searchText = layoutState.searchText,
                onSearchChange = layoutState.onSearchChange,
                onSearchFocusChange = layoutState.onSearchFocusChange,
                onMaximized = onMaximized,
                toggleWindowFullscreen = toggleWindowFullscreen,
                showSearchInTopBar = true,
                onRefresh = refreshActionFor(route),
            )
        } else {
            CustomTitleBar(
                requestBackgroundFocus = requestBackgroundFocus,
                title = currentTitle,
                colorScheme = colorScheme,
                searchText = layoutState.searchText,
                onSearchChange = layoutState.onSearchChange,
                onSearchFocusChange = layoutState.onSearchFocusChange,
                onMaximize = onMaximized,
                selectedNavIndex = layoutState.selectedNavIndex,
                hideSearch = adaptiveLayout.widthBreakpoint < AdaptiveWidthBreakpoint.MEDIUM,
                onRefresh = refreshActionFor(route),
            )
        }

        DesktopNavigationScaffold(
            layoutState = layoutState,
            adaptiveLayout = adaptiveLayout,
            colorScheme = colorScheme,
            onLibraryReselect = { activeLibraryGridState.requestScrollToItem(0) },
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) { _ ->
            Column(Modifier.fillMaxSize()) {
                MainLayoutContent(
                    layoutState = layoutState,
                    colorScheme = colorScheme,
                    route = route,
                    libraryGridState = libraryGridState,
                    onLibraryScrollStateChange = { activeLibraryGridState = it },
                )

                AddToPlaylistDialog()
            }
        }

        if (!playerInTopBar && PlayerState.currentTrack != null) {
            DesktopPlayerVisibility(visible = !PlayerState.isExpanded) {
                StandardBottomPlayer(colorScheme, toggleWindowFullscreen)
            }
        }
    }
}

@Composable
private fun PaneResizeHandle(
    onDrag: (Float) -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val resizeCursor = remember { PointerIcon(Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR)) }

    Box(
        modifier =
            modifier
                .fillMaxHeight()
                .width(12.dp)
                .pointerHoverIcon(resizeCursor)
                .combinedClickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onDoubleClick = onReset,
                    onClick = {},
                ).draggable(
                    orientation = Orientation.Horizontal,
                    interactionSource = interactionSource,
                    state = rememberDraggableState { delta -> onDrag(delta) },
                ),
        contentAlignment = Alignment.Center,
    ) {
        VerticalDragHandle(
            interactionSource = interactionSource,
        )
    }
}

@Composable
private fun ColumnScope.MainLayoutContent(
    layoutState: LayoutState,
    colorScheme: ColorScheme,
    route: Screen,
    libraryGridState: LazyGridState,
    onLibraryScrollStateChange: (LazyGridState) -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxSize()) {
        val availableSupportingWidth = (maxWidth - LayoutConstants.MinimumPrimaryPaneWidth).coerceAtLeast(0.dp)
        val playerDrawersOverlayContent = availableSupportingWidth < 280.dp
        val overlayWidth = maxWidth
        val deviceConnectWidth = deviceConnectSideSheetWidth(maxWidth)
        val deviceConnectOverlaysContent = maxWidth - deviceConnectWidth < LayoutConstants.MinimumPrimaryPaneWidth

        SideEffect {
            if (!PlayerState.isExpanded) {
                UiState.playerPanels.canShowBoth = availableSupportingWidth >= 560.dp
            }
            UiState.isDeviceConnectPanelModal = deviceConnectOverlaysContent
        }

        val isQueueOpen = UiState.showPersistentPlayerDrawer && PlayerState.currentTrack != null
        val isLyricsOpen = UiState.showLyricsDrawer && PlayerState.currentTrack != null
        val density = LocalDensity.current

        var queuePaneWidth by remember { mutableStateOf(360.dp) }
        var lyricsPaneWidth by remember { mutableStateOf(360.dp) }

        val maxQueueWidth =
            if (isQueueOpen && isLyricsOpen) {
                (availableSupportingWidth - lyricsPaneWidth).coerceAtLeast(280.dp)
            } else {
                availableSupportingWidth.coerceAtLeast(280.dp)
            }
        val maxLyricsWidth =
            if (isQueueOpen && isLyricsOpen) {
                (availableSupportingWidth - queuePaneWidth).coerceAtLeast(280.dp)
            } else {
                availableSupportingWidth.coerceAtLeast(280.dp)
            }

        Box(Modifier.fillMaxSize()) {
            Row(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(colorScheme.surfaceContainer)
                        .padding(8.dp),
            ) {
                StandardContentSurface(
                    route = route,
                    layoutState = layoutState,
                    colorScheme = colorScheme,
                    libraryGridState = libraryGridState,
                    onLibraryScrollStateChange = onLibraryScrollStateChange,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )

                if (!playerDrawersOverlayContent) {
                    AnimatedVisibility(
                        visible = isQueueOpen,
                        enter =
                            expandHorizontally(
                                expandFrom = Alignment.End,
                                animationSpec =
                                    spring(
                                        dampingRatio = Spring.DampingRatioNoBouncy,
                                        stiffness = Spring.StiffnessMediumLow,
                                    ),
                            ) + fadeIn(animationSpec = tween(200)),
                        exit =
                            shrinkHorizontally(
                                shrinkTowards = Alignment.End,
                                animationSpec =
                                    spring(
                                        dampingRatio = Spring.DampingRatioNoBouncy,
                                        stiffness = Spring.StiffnessMedium,
                                    ),
                            ) + fadeOut(animationSpec = tween(150)),
                    ) {
                        Row(modifier = Modifier.fillMaxHeight()) {
                            PaneResizeHandle(
                                onDrag = { deltaPx ->
                                    val deltaDp = with(density) { -deltaPx.toDp() }
                                    queuePaneWidth = (queuePaneWidth + deltaDp).coerceIn(280.dp, maxQueueWidth)
                                },
                                onReset = { queuePaneWidth = 360.dp },
                            )
                            DesktopQueueContent(
                                onDismiss = { UiState.showPersistentPlayerDrawer = false },
                                colorScheme = colorScheme,
                                modifier =
                                    Modifier
                                        .width(
                                            queuePaneWidth.coerceIn(280.dp, maxQueueWidth),
                                        ).fillMaxHeight(),
                            )
                        }
                    }

                    AnimatedVisibility(
                        visible = isLyricsOpen,
                        enter =
                            expandHorizontally(
                                expandFrom = Alignment.End,
                                animationSpec =
                                    spring(
                                        dampingRatio = Spring.DampingRatioNoBouncy,
                                        stiffness = Spring.StiffnessMediumLow,
                                    ),
                            ) + fadeIn(animationSpec = tween(200)),
                        exit =
                            shrinkHorizontally(
                                shrinkTowards = Alignment.End,
                                animationSpec =
                                    spring(
                                        dampingRatio = Spring.DampingRatioNoBouncy,
                                        stiffness = Spring.StiffnessMedium,
                                    ),
                            ) + fadeOut(animationSpec = tween(150)),
                    ) {
                        Row(modifier = Modifier.fillMaxHeight()) {
                            PaneResizeHandle(
                                onDrag = { deltaPx ->
                                    val deltaDp = with(density) { -deltaPx.toDp() }
                                    lyricsPaneWidth = (lyricsPaneWidth + deltaDp).coerceIn(280.dp, maxLyricsWidth)
                                },
                                onReset = { lyricsPaneWidth = 360.dp },
                            )
                            DesktopLyricsContent(
                                onClose = { UiState.showLyricsDrawer = false },
                                colorScheme = colorScheme,
                                modifier =
                                    Modifier
                                        .width(
                                            lyricsPaneWidth.coerceIn(280.dp, maxLyricsWidth),
                                        ).fillMaxHeight(),
                                isVisible = !PlayerState.isExpanded && !UiState.videoFullscreenObscuresPlayer,
                            )
                        }
                    }
                }

                if (!deviceConnectOverlaysContent) {
                    DeviceConnectSideSheet(panelWidth = deviceConnectWidth)
                }
            }

            if (playerDrawersOverlayContent) {
                Row(Modifier.align(Alignment.CenterEnd).fillMaxHeight()) {
                    PersistentPlayerDrawer(colorScheme = colorScheme, availableWidth = overlayWidth)
                    LyricsDrawer(colorScheme = colorScheme, availableWidth = overlayWidth)
                }
            }

            if (deviceConnectOverlaysContent) {
                DeviceConnectModalSideSheet(panelWidth = deviceConnectWidth)
            }
        }
    }
}

@Composable
private fun StandardContentSurface(
    route: Screen,
    layoutState: LayoutState,
    colorScheme: ColorScheme,
    libraryGridState: LazyGridState,
    onLibraryScrollStateChange: (LazyGridState) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = colorScheme.surface,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AppContentHost(
                route = route,
                onSearchChange = layoutState.onSearchChange,
                colorScheme = colorScheme,
                libraryGridState = libraryGridState,
                onLibraryScrollStateChange = onLibraryScrollStateChange,
                modifier = Modifier.fillMaxSize(),
            )
            VideoMiniViewer(
                colorScheme = colorScheme,
                modifier =
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 16.dp, bottom = 16.dp),
            )
        }
    }
}
