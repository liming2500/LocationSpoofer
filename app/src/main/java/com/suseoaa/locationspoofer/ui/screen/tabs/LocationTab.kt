package com.suseoaa.locationspoofer.ui.screen.tabs

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
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
import kotlinx.coroutines.launch

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun LocationTab(
    viewModel: MainViewModel,
    uiState: AppState,
    mapController: AppMapController?,
    tabBarHeight: Dp = 90.dp,
    updateViewModel: com.suseoaa.locationspoofer.viewmodel.UpdateViewModel = org.koin.androidx.compose.koinViewModel()
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val spoofingUiState by viewModel.spoofingUiState.collectAsState()
    val updateUiState by updateViewModel.uiState.collectAsState()
    val isDark = androidx.compose.foundation.isSystemInDarkTheme()
    val coroutineScope = rememberCoroutineScope()
    val searchFocusRequester = remember { FocusRequester() }
    val isSearching = spoofingUiState.isSearchActive
    val onIntent = { intent: SpoofingIntent -> viewModel.handleSpoofingIntent(intent) }
    val searchCacheDurationMs = 30_000L
    var searchBounds by remember { mutableStateOf(Rect.Zero) }
    var searchResultBounds by remember { mutableStateOf(Rect.Zero) }

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
                Column(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 16.dp, top = 60.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
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

                LocationControlPanel(
                    modifier = Modifier.align(Alignment.BottomCenter),
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
                                shape = RoundedCornerShape(18.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surface
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 4.dp)
                                    .heightIn(max = 360.dp)
                                    .onGloballyPositioned { searchResultBounds = it.boundsInRoot() }
                            ) {
                                LazyColumn {
                                    items(spoofingUiState.searchResults.take(15)) { poi ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    viewModel.updateLatitude(String.format("%.6f", poi.lat))
                                                    viewModel.updateLongitude(String.format("%.6f", poi.lng))
                                                    mapController?.animateCamera(poi.lat, poi.lng, 16f)
                                                    onIntent(SpoofingIntent.HideSearchResults)
                                                    onIntent(SpoofingIntent.UpdateSearchQuery(poi.title))
                                                }
                                                .padding(horizontal = 16.dp, vertical = 12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                Icons.Rounded.Place,
                                                null,
                                                tint = AccentBlue,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(Modifier.width(10.dp))
                                            Column {
                                                Text(
                                                    poi.title,
                                                    fontSize = 14.sp,
                                                    fontWeight = FontWeight.Medium
                                                )
                                                Text(
                                                    poi.snippet,
                                                    fontSize = 12.sp,
                                                    color = MaterialTheme.colorScheme.outline
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

    if (spoofingUiState.showUpdateDialog) {
        UpdateDialog(
            uiState = updateUiState,
            onDismiss = { onIntent(SpoofingIntent.SetUpdateDialogVisible(false)) },
            onDownload = { url, version -> updateViewModel.startDownload(url, version) },
            onCancel = updateViewModel::cancelDownload,
            onInstall = updateViewModel::installApk,
            onIgnore = { version ->
                viewModel.setIgnoredVersion(version)
                onIntent(SpoofingIntent.SetUpdateDialogVisible(false))
            }
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
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(42.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, tint = AccentBlue, modifier = Modifier.size(20.dp))
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
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 18.dp
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
                        Spacer(Modifier.height(12.dp))
                        WifiStatusCard(uiState)
                    }

                    Spacer(Modifier.height(14.dp))

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
