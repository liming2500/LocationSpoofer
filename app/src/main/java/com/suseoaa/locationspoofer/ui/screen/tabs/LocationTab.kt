package com.suseoaa.locationspoofer.ui.screen.tabs

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material.icons.rounded.StopCircle
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.suseoaa.locationspoofer.R
import com.suseoaa.locationspoofer.data.model.AppState
import com.suseoaa.locationspoofer.data.model.SavedLocation
import com.suseoaa.locationspoofer.data.model.SearchMode
import com.suseoaa.locationspoofer.ui.components.AppMapController
import com.suseoaa.locationspoofer.ui.components.LocalEnvironmentDataDialog
import com.suseoaa.locationspoofer.ui.screen.*
import com.suseoaa.locationspoofer.ui.screen.spoofing.SpoofingIntent
import com.suseoaa.locationspoofer.ui.theme.AccentBlue
import com.suseoaa.locationspoofer.ui.theme.AccentOrange
import com.suseoaa.locationspoofer.ui.theme.AppColors
import com.suseoaa.locationspoofer.ui.theme.noRippleClickable
import com.suseoaa.locationspoofer.viewmodel.MainViewModel
import com.suseoaa.locationspoofer.viewmodel.UpdateViewModel
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import top.yukonga.miuix.kmp.basic.Card as MiuixCard
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

enum class LocationPanelState {
    COLLAPSED, // 下滑到底部（仅搜索框 + 右侧定点模拟/停止模拟按钮并排，其余卡片自然沉入底部边缘）
    DEFAULT,   // 默认状态（定点模拟按钮微高于 TabBar，收藏地点卡片完全下沉延伸至屏幕下方边缘外）
    EXPANDED   // 上滑抽拉完全展开（整组卡片向上抽拉滑出，根据点位数量动态延伸完整展现）
}

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
    var showLocalDataDialog by remember { mutableStateOf(false) }

    // 在页面顶层持久记录测量高度，绝不随搜索页面切换而重置为 0，彻底消除返回时“先回高位再跳动”的测量延迟
    var persistentSavedCardHeightPx by remember { mutableIntStateOf(0) }
    var persistentCoordCardHeightPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current

    val savedCardHeightDp = if (persistentSavedCardHeightPx > 0) {
        with(density) { persistentSavedCardHeightPx.toDp() }
    } else {
        180.dp
    }

    val coordCardHeightDp = if (persistentCoordCardHeightPx > 0) {
        with(density) { persistentCoordCardHeightPx.toDp() }
    } else {
        210.dp
    }

    var panelState by remember {
        mutableStateOf(
            if (uiState.isSpoofingActive) LocationPanelState.COLLAPSED else LocationPanelState.DEFAULT
        )
    }

    LaunchedEffect(uiState.isSpoofingActive) {
        if (uiState.isSpoofingActive) {
            panelState = LocationPanelState.COLLAPSED
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            viewModel.importEnvironmentData(it) {
                viewModel.loadManageData()
                Toast.makeText(context, context.getString(R.string.import_merge_success), Toast.LENGTH_SHORT).show()
            }
        }
    }

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

    // 悬浮卡片避让底部控制栏高度：
    // COLLAPSED（仅搜索）：避让搜索框，停留在 tabBarHeight + 74dp
    // DEFAULT（展示坐标卡片）：避让坐标卡片与搜索框顶部，停留在 tabBarHeight + coordCardHeightDp + 82dp
    // EXPANDED（完全展开收藏卡片）：保持 DEFAULT 状态高度，无需进一步顶至屏幕最上方
    val rawFabBottomPadding by animateDpAsState(
        targetValue = when (panelState) {
            LocationPanelState.EXPANDED -> tabBarHeight + coordCardHeightDp + 82.dp
            LocationPanelState.DEFAULT -> tabBarHeight + coordCardHeightDp + 82.dp
            LocationPanelState.COLLAPSED -> tabBarHeight + 74.dp
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "location_fab_bottom_padding"
    )
    val fabBottomPadding = rawFabBottomPadding.coerceAtLeast(0.dp)

    SharedTransitionLayout {
        AnimatedContent(
            targetState = isSearching,
            transitionSpec = {
                androidx.compose.animation.EnterTransition.None togetherWith
                        androidx.compose.animation.ExitTransition.None
            },
            label = "location_search_transition"
        ) content@{ searchActive ->
            val searchModifier = Modifier
                .sharedElement(
                    sharedContentState = rememberSharedContentState(key = "location_search_bar"),
                    animatedVisibilityScope = this@content,
                    boundsTransform = { initialBounds, targetBounds ->
                        val isTopSearchTransition = (initialBounds.top < 350f && targetBounds.top > 350f) ||
                                (initialBounds.top > 350f && targetBounds.top < 350f)
                        if (isTopSearchTransition) {
                            spring(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = Spring.StiffnessMediumLow
                            )
                        } else {
                            snap()
                        }
                    }
                )
                .onGloballyPositioned { searchBounds = it.boundsInRoot() }

            Box(modifier = Modifier.fillMaxSize()) {
                // 悬浮功能按钮（主动避让底部面板，在面板高度变化时平滑跟随）
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 16.dp, bottom = fabBottomPadding),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalAlignment = Alignment.End
                ) {
                    MapControlButton(
                        icon = Icons.Rounded.MyLocation,
                        onClick = {
                            viewModel.fetchCurrentLocation(context) { lat, lng ->
                                mapController?.animateCamera(lat, lng, 16f)
                            }
                        }
                    )
                    MapControlButton(
                        icon = Icons.Rounded.Layers,
                        onClick = {
                            onIntent(SpoofingIntent.SetMapTypeDialogVisible(true))
                        }
                    )
                    MapControlButton(
                        icon = Icons.Rounded.Star,
                        onClick = {
                            onIntent(SpoofingIntent.SetSavedLocationsVisible(true))
                        }
                    )
                    MapControlButton(
                        icon = Icons.Rounded.Storage,
                        onClick = {
                            viewModel.loadManageData()
                            showLocalDataDialog = true
                        }
                    )
                }

                LocationControlPanel(
                    modifier = Modifier.align(Alignment.BottomCenter),
                    uiState = uiState,
                    isDark = isDark,
                    panelState = panelState,
                    onPanelStateChange = { panelState = it },
                    viewModel = viewModel,
                    tabBarHeight = tabBarHeight,
                    savedCardHeightDp = savedCardHeightDp,
                    coordCardHeightDp = coordCardHeightDp,
                    onSavedCardHeightMeasured = { persistentSavedCardHeightPx = it },
                    onCoordCardHeightMeasured = { persistentCoordCardHeightPx = it },
                    onSaveClick = { onIntent(SpoofingIntent.SetSaveDialogVisible(true)) },
                    onCustomClick = { onIntent(SpoofingIntent.SetCustomCoordDialogVisible(true)) },
                    onStartFixedSpoofing = {
                        onIntent(SpoofingIntent.SetStartSpoofingDialogVisible(true))
                    },
                    onStopSpoofing = { viewModel.stopSpoofing() },
                    onSelectSavedLocation = { location ->
                        viewModel.loadSavedLocation(location)
                        mapController?.animateCamera(location.lat, location.lng, 17.5f)
                        Toast.makeText(
                            context,
                            context.getString(R.string.located_to_saved_point, location.name),
                            Toast.LENGTH_SHORT
                        ).show()
                    },
                    onDeleteSavedLocation = { location ->
                        viewModel.removeSavedLocation(location)
                    },
                    onOpenManageSavedLocations = {
                        onIntent(SpoofingIntent.SetSavedLocationsVisible(true))
                    },
                    searchBar = { barModifier ->
                        if (!searchActive) {
                            HomeSearchBar(
                                query = spoofingUiState.searchQuery,
                                searchMode = uiState.searchMode,
                                onSearchModeChange = viewModel::setSearchMode,
                                onQueryChange = { onIntent(SpoofingIntent.UpdateSearchQuery(it)) },
                                onSearch = submitSearch,
                                onFocus = { onIntent(SpoofingIntent.SetSearchActive(true)) },
                                modifier = searchModifier.then(barModifier),
                                focusRequester = searchFocusRequester
                            )
                        } else {
                            // 保持占位高度与尺寸结构恒定，绝不在全屏搜索返回时发生二次重构与停顿
                            Spacer(modifier = barModifier.height(52.dp))
                        }
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
                            modifier = searchModifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp),
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
                                                .noRippleClickable {
                                                    viewModel.updateLatitude(poi.lat.toString())
                                                    viewModel.updateLongitude(poi.lng.toString())
                                                    mapController?.animateCamera(
                                                        poi.lat,
                                                        poi.lng,
                                                        17.5f
                                                    )
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
                                                    color = MaterialTheme.colorScheme.onBackground.copy(
                                                        alpha = 0.6f
                                                    )
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

    if (showLocalDataDialog) {
        LocalEnvironmentDataDialog(
            dataList = uiState.manageDataList,
            isLoading = uiState.manageDataIsLoading,
            onSelectPoint = { item ->
                val lat = item.location.lat
                val lng = item.location.lng
                viewModel.updateLatitude(lat.toString())
                viewModel.updateLongitude(lng.toString())
                mapController?.animateCamera(lat, lng, 17.5f)
                val label = when {
                    item.location.remark.isNotBlank() -> item.location.remark
                    item.location.placeName.isNotBlank() -> item.location.placeName
                    else -> "(${String.format(Locale.US, "%.5f", lat)}, ${
                        String.format(
                            Locale.US,
                            "%.5f",
                            lng
                        )
                    })"
                }
                Toast.makeText(
                    context,
                    context.getString(R.string.located_to_location, label),
                    Toast.LENGTH_SHORT
                ).show()
            },
            onImportClick = {
                importLauncher.launch(arrayOf("application/json", "*/*"))
            },
            onDismiss = { showLocalDataDialog = false }
        )
    }

    if (spoofingUiState.showSavedLocationsDialog) {
        SavedLocationsDialog(
            savedLocations = uiState.savedLocations,
            onDismiss = { onIntent(SpoofingIntent.SetSavedLocationsVisible(false)) },
            onSelect = { location ->
                viewModel.loadSavedLocation(location)
                mapController?.animateCamera(location.lat, location.lng, 17.5f)
                onIntent(SpoofingIntent.SetSavedLocationsVisible(false))
                Toast.makeText(
                    context,
                    context.getString(R.string.located_to_saved_point, location.name),
                    Toast.LENGTH_SHORT
                ).show()
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
                        mapController?.animateCamera(latVal, lngVal, 17.5f)
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
            .noRippleClickable(onClick = onClick),
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
    panelState: LocationPanelState,
    onPanelStateChange: (LocationPanelState) -> Unit,
    viewModel: MainViewModel,
    tabBarHeight: Dp,
    savedCardHeightDp: Dp,
    coordCardHeightDp: Dp,
    onSavedCardHeightMeasured: (Int) -> Unit,
    onCoordCardHeightMeasured: (Int) -> Unit,
    onSaveClick: () -> Unit,
    onCustomClick: () -> Unit,
    onStartFixedSpoofing: () -> Unit,
    onStopSpoofing: () -> Unit,
    onSelectSavedLocation: (SavedLocation) -> Unit,
    onDeleteSavedLocation: (SavedLocation) -> Unit,
    onOpenManageSavedLocations: () -> Unit,
    searchBar: @Composable (Modifier) -> Unit
) {
    var dragAccumulator by remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current

    val dragGestureModifier = Modifier.pointerInput(panelState) {
        detectVerticalDragGestures(
            onDragStart = { dragAccumulator = 0f },
            onVerticalDrag = { _, dragAmount ->
                dragAccumulator += dragAmount
            },
            onDragEnd = {
                if (dragAccumulator > 36f) {
                    when (panelState) {
                        LocationPanelState.EXPANDED -> onPanelStateChange(LocationPanelState.DEFAULT)
                        LocationPanelState.DEFAULT -> onPanelStateChange(LocationPanelState.COLLAPSED)
                        LocationPanelState.COLLAPSED -> {}
                    }
                } else if (dragAccumulator < -36f) {
                    when (panelState) {
                        LocationPanelState.COLLAPSED -> onPanelStateChange(LocationPanelState.DEFAULT)
                        LocationPanelState.DEFAULT -> onPanelStateChange(LocationPanelState.EXPANDED)
                        LocationPanelState.EXPANDED -> {}
                    }
                }
                dragAccumulator = 0f
            },
            onDragCancel = { dragAccumulator = 0f }
        )
    }

    // 抽屉单通道物理 Spring 滑动位移：
    // EXPANDED（完全展开）：偏移为 0dp，整张收藏卡片向上抽拉拉出至屏幕中央，完整展现
    // DEFAULT（默认下沉状态）：向下偏移 savedCardHeightDp + 8dp，定点模拟按钮正好保持在 TabBar 上方稍微一点点（~8dp），收藏卡片全部自然下沉延伸至屏幕下方边缘外
    // COLLAPSED（折叠状态）：向下偏移 savedCardHeightDp + coordCardHeightDp + 16dp，坐标卡片也顺畅下沉隐藏，仅留单行搜索操作栏稳稳停留在 TabBar 上方
    val rawDrawerOffsetY by animateDpAsState(
        targetValue = when (panelState) {
            LocationPanelState.EXPANDED -> 0.dp
            LocationPanelState.DEFAULT -> savedCardHeightDp + 8.dp
            LocationPanelState.COLLAPSED -> savedCardHeightDp + coordCardHeightDp + 16.dp
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "drawer_offset_y"
    )

    val drawerOffsetYPx = with(density) { rawDrawerOffsetY.toPx() }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp)
            .padding(bottom = (tabBarHeight + 10.dp).coerceAtLeast(0.dp))
            .offset { IntOffset(0, drawerOffsetYPx.roundToInt()) }
            .then(dragGestureModifier)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 顶部阻尼拖拽药丸指示条（支持点击快速折叠/展开与拖拽）
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp, bottom = 4.dp)
                    .noRippleClickable {
                        val next = when (panelState) {
                            LocationPanelState.COLLAPSED -> LocationPanelState.DEFAULT
                            LocationPanelState.DEFAULT -> LocationPanelState.EXPANDED
                            LocationPanelState.EXPANDED -> LocationPanelState.DEFAULT
                        }
                        onPanelStateChange(next)
                    },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(36.dp)
                        .height(4.5.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(
                            if (isDark) Color.White.copy(alpha = 0.25f) else Color.Black.copy(alpha = 0.20f)
                        )
                )
            }

            // 顶部常驻搜索栏行（无论是否折叠都平滑存在，右侧操作按钮采用柔和弥散阴影与平滑横向伸展）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                searchBar(
                    Modifier
                        .weight(1f)
                        .height(52.dp)
                )

                // 正在模拟时的停止模拟按钮
                AnimatedVisibility(
                    visible = uiState.isSpoofingActive,
                    enter = expandHorizontally(tween(200, easing = FastOutSlowInEasing)) + fadeIn(tween(200)),
                    exit = shrinkHorizontally(tween(180, easing = FastOutSlowInEasing)) + fadeOut(tween(180))
                ) {
                    val errorColor = MaterialTheme.colorScheme.error
                    val errorContainer = MaterialTheme.colorScheme.errorContainer
                    Box(
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .height(52.dp)
                            .shadow(
                                elevation = 6.dp,
                                shape = RoundedCornerShape(26.dp),
                                ambientColor = Color.Black.copy(alpha = 0.08f),
                                spotColor = errorColor.copy(alpha = 0.25f)
                            )
                            .clip(RoundedCornerShape(26.dp))
                            .background(errorContainer.copy(alpha = 0.95f))
                            .noRippleClickable(onClick = onStopSpoofing),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxHeight()
                                .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                Icons.Rounded.StopCircle,
                                contentDescription = null,
                                tint = errorColor,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                stringResource(R.string.stop_simulation),
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = errorColor
                            )
                        }
                    }
                }

                // 未模拟且处于 COLLAPSED 状态下的定点模拟胶囊按钮（间距内聚，收缩与渐隐严格同周期，绝无二次伸长）
                AnimatedVisibility(
                    visible = !uiState.isSpoofingActive && panelState == LocationPanelState.COLLAPSED,
                    enter = expandHorizontally(tween(200, easing = FastOutSlowInEasing)) + fadeIn(tween(200)),
                    exit = shrinkHorizontally(tween(180, easing = FastOutSlowInEasing)) + fadeOut(tween(180))
                ) {
                    Box(
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .height(52.dp)
                            .shadow(
                                elevation = 6.dp,
                                shape = RoundedCornerShape(26.dp),
                                ambientColor = Color.Black.copy(alpha = 0.08f),
                                spotColor = AccentBlue.copy(alpha = 0.32f)
                            )
                            .clip(RoundedCornerShape(26.dp))
                            .background(AccentBlue)
                            .noRippleClickable(onClick = onStartFixedSpoofing),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxHeight()
                                .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            if (uiState.isSavingConfig) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    stringResource(R.string.starting_ellipsis),
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            } else {
                                Icon(
                                    Icons.Rounded.MyLocation,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    stringResource(R.string.fixed_simulation),
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
            }

            // 目标坐标及操作卡片（包含定点模拟按钮，随抽屉 offset 顺畅沉降）
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .onGloballyPositioned {
                        if (it.size.height > 0) {
                            onCoordCardHeightMeasured(it.size.height)
                        }
                    },
                shape = RoundedCornerShape(22.dp),
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

                    Spacer(Modifier.height(12.dp))

                    ActionButtons(
                        viewModel = viewModel,
                        uiState = uiState,
                        onOpenMap = {},
                        onStartFixedSpoofing = onStartFixedSpoofing
                    )
                }
            }

            // 收藏地点卡片：高度完全根据数量动态伸缩，不设固定限制，支持抽拉下沉
            SavedLocationsCard(
                modifier = Modifier.onGloballyPositioned {
                    if (it.size.height > 0) {
                        onSavedCardHeightMeasured(it.size.height)
                    }
                },
                savedLocations = uiState.savedLocations,
                panelState = panelState,
                isDark = isDark,
                onSelect = onSelectSavedLocation,
                onDelete = onDeleteSavedLocation,
                onToggleExpand = {
                    val next = if (panelState == LocationPanelState.EXPANDED) {
                        LocationPanelState.DEFAULT
                    } else {
                        LocationPanelState.EXPANDED
                    }
                    onPanelStateChange(next)
                },
                onOpenManageDialog = onOpenManageSavedLocations
            )
        }
    }
}

// 动态高度的收藏地点卡片（高度随点位数量自然延伸，不限制在固定高度盒子内）
@Composable
private fun SavedLocationsCard(
    modifier: Modifier = Modifier,
    savedLocations: List<SavedLocation>,
    panelState: LocationPanelState,
    isDark: Boolean,
    onSelect: (SavedLocation) -> Unit,
    onDelete: (SavedLocation) -> Unit,
    onToggleExpand: () -> Unit,
    onOpenManageDialog: () -> Unit
) {
    val textSecondary = AppColors.textSecondary(isDark)

    MiuixCard(
        modifier = modifier.fillMaxWidth(),
        cornerRadius = 20.dp,
        insideMargin = PaddingValues(14.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 卡片头部
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(AccentOrange.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Rounded.Star,
                        contentDescription = null,
                        tint = AccentOrange,
                        modifier = Modifier.size(17.dp)
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.saved_locations_card_title),
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.05f)
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "${savedLocations.size}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }

                Spacer(Modifier.weight(1f))

                if (savedLocations.isNotEmpty()) {
                    TextButton(
                        onClick = onOpenManageDialog,
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.saved_locations),
                            fontSize = 12.sp,
                            color = AccentBlue,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                IconButton(
                    onClick = onToggleExpand,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        if (panelState == LocationPanelState.EXPANDED) Icons.Rounded.KeyboardArrowDown else Icons.Rounded.KeyboardArrowUp,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // 卡片主体内容（根据点位数量自然延伸高度，无多余空隙，无限制滑动容器）
            if (savedLocations.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (isDark) Color.White.copy(alpha = 0.03f) else Color.Black.copy(alpha = 0.02f)
                        )
                        .padding(vertical = 14.dp, horizontal = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Rounded.StarOutline,
                            contentDescription = null,
                            tint = textSecondary.copy(alpha = 0.4f),
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = stringResource(R.string.no_saved_locations_card_hint),
                            fontSize = 12.sp,
                            color = textSecondary.copy(alpha = 0.7f)
                        )
                    }
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    savedLocations.forEach { loc ->
                        SavedLocationItemRow(
                            location = loc,
                            isDark = isDark,
                            onSelect = { onSelect(loc) },
                            onDelete = { onDelete(loc) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SavedLocationItemRow(
    location: SavedLocation,
    isDark: Boolean,
    onSelect: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (isDark) Color.White.copy(alpha = 0.05f) else Color.Black.copy(alpha = 0.03f)
            )
            .noRippleClickable(onClick = onSelect)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(AccentBlue.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Rounded.Place,
                contentDescription = null,
                tint = AccentBlue,
                modifier = Modifier.size(16.dp)
            )
        }

        Spacer(Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = location.name,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${String.format(Locale.US, "%.5f", location.lat)}, ${String.format(Locale.US, "%.5f", location.lng)}",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
            )
        }

        IconButton(
            onClick = onDelete,
            modifier = Modifier.size(28.dp)
        ) {
            Icon(
                Icons.Rounded.DeleteOutline,
                contentDescription = stringResource(R.string.delete_saved_location_short),
                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.65f),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
