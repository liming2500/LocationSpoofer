package com.suseoaa.locationspoofer.ui.screen.tabs

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.suseoaa.locationspoofer.R
import com.suseoaa.locationspoofer.data.model.AppState
import com.suseoaa.locationspoofer.data.model.SearchMode
import com.suseoaa.locationspoofer.ui.components.AppMapController
import com.suseoaa.locationspoofer.ui.screen.*
import com.suseoaa.locationspoofer.ui.screen.spoofing.SpoofingIntent
import com.suseoaa.locationspoofer.ui.theme.AccentBlue
import com.suseoaa.locationspoofer.viewmodel.MainViewModel
import com.suseoaa.locationspoofer.viewmodel.UpdateViewModel
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun LocationTab(
    viewModel: MainViewModel,
    uiState: AppState,
    mapController: AppMapController?,
    tabBarHeight: Dp = 90.dp,
    updateViewModel: UpdateViewModel = koinViewModel()
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val spoofingUiState by viewModel.spoofingUiState.collectAsState()
    val updateUiState by updateViewModel.uiState.collectAsState()
    val isDark = isSystemInDarkTheme()
    val coroutineScope = rememberCoroutineScope()
    val searchFocusRequester = remember { FocusRequester() }
    val isSearching = spoofingUiState.isSearchActive
    val onIntent = { intent: SpoofingIntent -> viewModel.handleSpoofingIntent(intent) }
    val searchCacheDurationMs = 30_000L
    var searchBounds by remember { mutableStateOf(Rect.Zero) }
    var searchResultBounds by remember { mutableStateOf(Rect.Zero) }
    var controlPanelHeightPx by remember { mutableIntStateOf(0) }

    val submitSearch: () -> Unit = {
        focusManager.clearFocus()
        keyboardController?.hide()
        val now = System.currentTimeMillis()
        val hasRecentSearchCache =
            spoofingUiState.searchResults.isNotEmpty() &&
                spoofingUiState.cachedSearchQuery == spoofingUiState.searchQuery &&
                now - spoofingUiState.cachedSearchAt <= searchCacheDurationMs

        if (hasRecentSearchCache) {
            onIntent(
                SpoofingIntent.SetSearchResults(
                    results = spoofingUiState.searchResults,
                    show = true,
                    query = spoofingUiState.searchQuery
                )
            )
        } else if (uiState.searchMode == SearchMode.LOCAL) {
            coroutineScope.launch {
                val results = viewModel.performLocalSearch()
                onIntent(
                    SpoofingIntent.SetSearchResults(
                        results = results,
                        show = true,
                        query = spoofingUiState.searchQuery
                    )
                )
            }
        } else if (spoofingUiState.searchQuery.isNotBlank()) {
            performPoiSearch(
                context = context,
                mapEngine = uiState.mapEngine,
                keyword = spoofingUiState.searchQuery,
                isDomestic = viewModel.isDomesticEnvironment()
            ) { results ->
                onIntent(
                    SpoofingIntent.SetSearchResults(
                        results = results,
                        show = true,
                        query = spoofingUiState.searchQuery
                    )
                )
            }
        }
    }

    LaunchedEffect(isSearching) {
        if (isSearching) {
            searchFocusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    BackHandler(enabled = isSearching) {
        focusManager.clearFocus()
        keyboardController?.hide()
        onIntent(SpoofingIntent.SetSearchActive(false))
    }

    val density = LocalDensity.current
    val controlPanelHeightDp = with(density) { controlPanelHeightPx.toDp() }
    val fabBottomPadding by animateDpAsState(
        targetValue = if (controlPanelHeightDp > 0.dp) {
            controlPanelHeightDp + 14.dp
        } else {
            tabBarHeight + 280.dp
        },
        label = "location_fab_bottom_padding"
    )

    SharedTransitionLayout {
        AnimatedContent(
            targetState = isSearching,
            transitionSpec = {
                androidx.compose.animation.EnterTransition.None togetherWith
                    androidx.compose.animation.ExitTransition.None
            },
            label = "location_search_transition"
        ) content@{ searchActive ->
            val searchModifier = Modifier.sharedElement(
                rememberSharedContentState(key = "location_search_bar"),
                this@content
            ).onGloballyPositioned { searchBounds = it.boundsInRoot() }

            Box(modifier = Modifier.fillMaxSize()) {
                // 悬浮功能按钮（主动避让底部面板与搜索状态，搜索激活时平滑隐退避免遮挡）
                AnimatedVisibility(
                    visible = !searchActive,
                    enter = fadeIn(tween(180)),
                    exit = fadeOut(tween(140)),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 16.dp, bottom = fabBottomPadding)
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        horizontalAlignment = Alignment.End
                    ) {
                        MapControlButton(Icons.Rounded.MyLocation) {
                            viewModel.fetchCurrentLocation(context) { lat, lng ->
                                mapController?.animateCamera(lat, lng, 16f)
                            }
                        }
                        MapControlButton(Icons.Rounded.Layers) {
                            onIntent(SpoofingIntent.SetMapTypeDialogVisible(true))
                        }
                        MapControlButton(Icons.Rounded.Star) {
                            onIntent(SpoofingIntent.SetSavedLocationsVisible(true))
                        }
                    }
                }

                LocationControlPanel(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .onGloballyPositioned { controlPanelHeightPx = it.size.height },
                    uiState = uiState,
                    isDark = isDark,
                    viewModel = viewModel,
                    tabBarHeight = tabBarHeight,
                    onSaveClick = { onIntent(SpoofingIntent.SetSaveDialogVisible(true)) },
                    onCustomClick = { onIntent(SpoofingIntent.SetCustomCoordDialogVisible(true)) },
                    onStartFixedSpoofing = {
                        onIntent(SpoofingIntent.SetStartSpoofingDialogVisible(true))
                    },
                    searchBar = if (!searchActive) {
                        {
                            HomeSearchBar(
                                query = spoofingUiState.searchQuery,
                                searchMode = uiState.searchMode,
                                onSearchModeChange = viewModel::setSearchMode,
                                onQueryChange = { onIntent(SpoofingIntent.UpdateSearchQuery(it)) },
                                onSearch = submitSearch,
                                onFocus = { onIntent(SpoofingIntent.SetSearchActive(true)) },
                                modifier = searchModifier,
                                focusRequester = searchFocusRequester
                            )
                        }
                    } else {
                        null
                    }
                )

                if (searchActive && spoofingUiState.showSearchResults) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(searchBounds, searchResultBounds) {
                                awaitEachGesture {
                                    val down = awaitFirstDown(requireUnconsumed = false)
                                    val up = waitForUpOrCancellation()
                                    if (up != null &&
                                        (up.position - down.position).getDistance() < viewConfiguration.touchSlop &&
                                        !searchBounds.contains(up.position) &&
                                        !searchResultBounds.contains(up.position)
                                    ) {
                                        onIntent(SpoofingIntent.HideSearchResults)
                                    }
                                }
                            }
                    )
                }

                if (searchActive) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(top = 8.dp)
                    ) {
                        HomeSearchBar(
                            query = spoofingUiState.searchQuery,
                            searchMode = uiState.searchMode,
                            onSearchModeChange = viewModel::setSearchMode,
                            onQueryChange = { onIntent(SpoofingIntent.UpdateSearchQuery(it)) },
                            onSearch = submitSearch,
                            onFocus = { onIntent(SpoofingIntent.SetSearchActive(true)) },
                            modifier = searchModifier,
                            focusRequester = searchFocusRequester
                        )

                        AnimatedVisibility(
                            visible = spoofingUiState.showSearchResults && spoofingUiState.searchResults.isNotEmpty(),
                            enter = fadeIn(tween(160)) + expandVertically(tween(220)),
                            exit = fadeOut(tween(120)) + shrinkVertically(tween(160))
                        ) {
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                elevation = CardDefaults.cardElevation(6.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 8.dp)
                                    .onGloballyPositioned { searchResultBounds = it.boundsInRoot() }
                            ) {
                                LazyColumn(modifier = Modifier.heightIn(max = 280.dp)) {
                                    items(spoofingUiState.searchResults) { poi ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    viewModel.updateLatitude(poi.lat.toString())
                                                    viewModel.updateLongitude(poi.lng.toString())
                                                    mapController?.animateCamera(poi.lat, poi.lng, 16f)
                                                    onIntent(SpoofingIntent.HideSearchResults)
                                                    onIntent(SpoofingIntent.SetSearchActive(false))
                                                }
                                                .padding(horizontal = 16.dp, vertical = 12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(36.dp)
                                                    .clip(RoundedCornerShape(10.dp))
                                                    .background(AccentBlue.copy(alpha = 0.12f)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    Icons.Rounded.Place,
                                                    null,
                                                    tint = AccentBlue,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                            Spacer(Modifier.width(12.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    poi.title,
                                                    fontWeight = FontWeight.Medium,
                                                    fontSize = 14.sp,
                                                    color = MaterialTheme.colorScheme.onBackground
                                                )
                                                Text(
                                                    poi.snippet,
                                                    fontSize = 11.5.sp,
                                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (spoofingUiState.showSavedLocationsDialog) {
        SavedLocationsDialog(
            savedLocations = uiState.savedLocations,
            onDismiss = { onIntent(SpoofingIntent.SetSavedLocationsVisible(false)) },
            onSelect = { location ->
                viewModel.loadSavedLocation(location)
                onIntent(SpoofingIntent.SetSavedLocationsVisible(false))
            },
            onDelete = viewModel::removeSavedLocation
        )
    }

    if (spoofingUiState.showSaveDialog) {
        SaveNameDialog(
            title = stringResource(R.string.save_current_location),
            onConfirm = { name ->
                viewModel.saveCurrentLocation(name)
                onIntent(SpoofingIntent.SetSaveDialogVisible(false))
            },
            onDismiss = { onIntent(SpoofingIntent.SetSaveDialogVisible(false)) }
        )
    }

    if (spoofingUiState.showStartSpoofingDialog) {
        StartSpoofingDialog(
            uiState = uiState,
            isDark = isDark,
            onDismiss = { onIntent(SpoofingIntent.SetStartSpoofingDialogVisible(false)) },
            onConfirm = {
                viewModel.startSpoofing()
                onIntent(SpoofingIntent.SetStartSpoofingDialogVisible(false))
            },
            onToggleWifi = viewModel::toggleMockWifi,
            onToggleCell = viewModel::toggleMockCell,
            onToggleBluetooth = viewModel::toggleMockBluetooth,
            onToggleJitter = viewModel::toggleEnableJitter,
            onAltitudeChange = viewModel::setAltitude,
            onSatelliteCountChange = viewModel::setSatelliteCount
        )
    }

    if (spoofingUiState.showCustomCoordDialog) {
        CustomCoordinateDialog(
            initialLat = uiState.latitudeInput,
            initialLng = uiState.longitudeInput,
            isDark = isDark,
            onDismiss = { onIntent(SpoofingIntent.SetCustomCoordDialogVisible(false)) },
            onConfirm = { lat, lng ->
                viewModel.updateLatitude(lat)
                viewModel.updateLongitude(lng)
                lat.toDoubleOrNull()?.let { latVal ->
                    lng.toDoubleOrNull()?.let { lngVal ->
                        mapController?.animateCamera(latVal, lngVal, 16f)
                    }
                }
                onIntent(SpoofingIntent.SetCustomCoordDialogVisible(false))
            }
        )
    }

    if (spoofingUiState.showMapTypeDialog) {
        com.suseoaa.locationspoofer.ui.components.MapTypeDialog(
            currentMapType = uiState.mapType,
            onMapTypeSelected = viewModel::setMapType,
            currentMapEngine = uiState.mapEngine,
            onMapEngineSelected = viewModel::setMapEngine,
            onDismiss = { onIntent(SpoofingIntent.SetMapTypeDialogVisible(false)) }
        )
    }
}

@Composable
private fun MapControlButton(
    icon: ImageVector,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(46.dp)
            .shadow(4.dp, RoundedCornerShape(14.dp))
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.94f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, tint = AccentBlue, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun LocationControlPanel(
    modifier: Modifier,
    uiState: AppState,
    isDark: Boolean,
    viewModel: MainViewModel,
    tabBarHeight: Dp,
    onSaveClick: () -> Unit,
    onCustomClick: () -> Unit,
    onStartFixedSpoofing: () -> Unit,
    searchBar: (@Composable () -> Unit)?
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .padding(bottom = tabBarHeight + 12.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            searchBar?.invoke()

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    CoordinateInputCard(
                        viewModel = viewModel,
                        uiState = uiState,
                        isDark = isDark,
                        onSaveClick = onSaveClick,
                        onCustomClick = onCustomClick
                    )

                    if (uiState.isSpoofingActive) {
                        Spacer(Modifier.height(10.dp))
                        WifiStatusCard(uiState)
                    }

                    Spacer(Modifier.height(12.dp))

                    ActionButtons(
                        viewModel = viewModel,
                        uiState = uiState,
                        onOpenMap = {},
                        onStartFixedSpoofing = onStartFixedSpoofing
                    )
                }
            }
        }
    }
}
