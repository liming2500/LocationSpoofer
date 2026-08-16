package com.suseoaa.locationspoofer.ui.screen.tabs

import android.annotation.SuppressLint
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.suseoaa.locationspoofer.R
import com.suseoaa.locationspoofer.data.model.*
import com.suseoaa.locationspoofer.ui.components.AppMapController
import com.suseoaa.locationspoofer.ui.components.AppMapMarker
import com.suseoaa.locationspoofer.ui.components.MapTypeDialog
import com.suseoaa.locationspoofer.ui.components.MarkerType
import com.suseoaa.locationspoofer.ui.screen.AppPoiItem
import com.suseoaa.locationspoofer.ui.screen.JoystickPanel
import com.suseoaa.locationspoofer.ui.screen.performPoiSearch
import com.suseoaa.locationspoofer.ui.screen.tabs.route.*
import com.suseoaa.locationspoofer.ui.theme.AccentBlue
import com.suseoaa.locationspoofer.ui.theme.AccentOrange
import com.suseoaa.locationspoofer.utils.MapCoverageHelper
import com.suseoaa.locationspoofer.viewmodel.MainViewModel
import kotlinx.coroutines.launch

@SuppressLint("LocalContextGetResourceValueCall")
@Composable
fun RouteTab(
    viewModel: MainViewModel,
    uiState: AppState,
    mapController: AppMapController?,
    isActive: Boolean = true,
    bottomBarHeight: Dp = 90.dp
) {
    val context = LocalContext.current
    var showMapTypeDialog by remember { mutableStateOf(false) }
    var showConfigDialog by remember { mutableStateOf(false) }
    var showSaveRouteDialog by remember { mutableStateOf(false) }
    var showSavedRoutesDialog by remember { mutableStateOf(false) }
    val isDomestic = viewModel.isDomesticEnvironment()
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<AppPoiItem>>(emptyList()) }
    var showSearchResults by remember { mutableStateOf(false) }
    var isSearchActive by remember { mutableStateOf(false) }
    var searchBounds by remember { mutableStateOf(Rect.Zero) }
    var searchResultBounds by remember { mutableStateOf(Rect.Zero) }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val coroutineScope = rememberCoroutineScope()
    val searchFocusRequester = remember { FocusRequester() }
    var bottomActionHeightPx by remember { mutableIntStateOf(0) }

    val stage = uiState.routePlanStage
    val isRunning = stage == RoutePlanStage.RUNNING
    val isManual = uiState.routeRunMode == RouteRunMode.MANUAL
    val routePoints = uiState.routePoints

    val submitSearch: () -> Unit = {
        focusManager.clearFocus()
        keyboardController?.hide()
        if (uiState.searchMode == SearchMode.LOCAL) {
            coroutineScope.launch {
                val results = viewModel.performLocalSearch()
                searchResults = results
                showSearchResults = results.isNotEmpty()
                if (results.isEmpty()) {
                    Toast.makeText(context, context.getString(R.string.no_matching_local_data), Toast.LENGTH_SHORT).show()
                }
            }
        } else if (searchQuery.isNotBlank()) {
            performPoiSearch(
                context = context,
                mapEngine = uiState.mapEngine,
                keyword = searchQuery,
                isDomestic = isDomestic
            ) { r ->
                searchResults = r
                showSearchResults = r.isNotEmpty()
                if (r.isEmpty()) {
                    Toast.makeText(context, context.getString(R.string.no_search_results), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    LaunchedEffect(isSearchActive) {
        if (isSearchActive) {
            searchFocusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    BackHandler(enabled = isSearchActive || showSearchResults) {
        isSearchActive = false
        showSearchResults = false
        focusManager.clearFocus()
        keyboardController?.hide()
    }

    val density = LocalDensity.current
    val bottomActionHeightDp = with(density) { bottomActionHeightPx.toDp() }

    LaunchedEffect(mapController, uiState.mapType, isActive) {
        if (!isActive) return@LaunchedEffect
        mapController?.setMapType(uiState.mapType)
    }

    var liveMarker by remember { mutableStateOf<AppMapMarker?>(null) }
    LaunchedEffect(routePoints, mapController, uiState.manageDataList, isActive) {
        if (!isActive) return@LaunchedEffect
        val map = mapController ?: return@LaunchedEffect
        map.clear()
        liveMarker = null
        val locations = uiState.manageDataList.map { it.location }
        MapCoverageHelper.drawCoverage(map, locations)

        if (routePoints.size >= 2) {
            map.addPolyline(
                routePoints.map { Pair(it.lat, it.lng) },
                android.graphics.Color.parseColor("#FF388BFD"),
                8f
            )
        }
        routePoints.forEachIndexed { idx, p ->
            val type = when {
                idx == 0 -> MarkerType.GREEN
                idx == routePoints.lastIndex && routePoints.size > 1 -> MarkerType.RED
                else -> MarkerType.DEFAULT
            }
            if (uiState.useRealRoute && uiState.routePlanStage == RoutePlanStage.RUNNING && type == MarkerType.DEFAULT) {
                return@forEachIndexed
            }
            val startBadge = context.getString(R.string.route_start_badge)
            val endBadge = context.getString(R.string.route_end_badge)
            val label = when (type) {
                MarkerType.GREEN -> startBadge
                MarkerType.RED -> endBadge
                else -> "${idx + 1}"
            }
            map.addMarker(
                p.lat,
                p.lng,
                if (type == MarkerType.RED && uiState.useRealRoute && uiState.routePlanStage == RoutePlanStage.RUNNING) endBadge else label,
                type
            )
        }

        if (uiState.isSpoofingActive) {
            val currentLat = uiState.latitudeInput.toDoubleOrNull()
            val currentLng = uiState.longitudeInput.toDoubleOrNull()
            if (currentLat != null && currentLng != null) {
                liveMarker = map.addMarker(
                    currentLat, currentLng,
                    context.getString(R.string.current_location),
                    MarkerType.ORANGE
                )
            }
        }
    }

    val lat = uiState.latitudeInput.toDoubleOrNull()
    val lng = uiState.longitudeInput.toDoubleOrNull()
    LaunchedEffect(lat, lng, uiState.isSpoofingActive, uiState.routePlanStage) {
        if (uiState.isSpoofingActive && lat != null && lng != null) {
            if (uiState.routePlanStage != RoutePlanStage.RUNNING) {
                mapController?.animateCamera(lat, lng)
            }
            if (liveMarker != null) {
                liveMarker?.setPosition(lat, lng)
            } else {
                liveMarker = mapController?.addMarker(
                    lat, lng,
                    context.getString(R.string.current_location),
                    MarkerType.ORANGE
                )
            }
        }
    }

    LaunchedEffect(uiState.routePlanStage, routePoints, isActive) {
        if (!isActive) return@LaunchedEffect
        if (uiState.routePlanStage == RoutePlanStage.RUNNING && routePoints.size >= 2) {
            val padLeft = with(density) { 36.dp.roundToPx() }
            val padTop = with(density) { 80.dp.roundToPx() }
            val padRight = with(density) { 36.dp.roundToPx() }
            val effectiveBottomDp =
                if (bottomActionHeightDp > 0.dp) bottomActionHeightDp + bottomBarHeight + 24.dp else bottomBarHeight + 160.dp
            val padBottom =
                with(density) { effectiveBottomDp.roundToPx() }
            mapController?.fitBounds(
                points = routePoints.map { Pair(it.lat, it.lng) },
                paddingLeft = padLeft,
                paddingTop = padTop,
                paddingRight = padRight,
                paddingBottom = padBottom
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(isSearchActive, showSearchResults, searchBounds, searchResultBounds) {
                if (isSearchActive || showSearchResults) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val pos = down.position
                        val inSearch = searchBounds.contains(pos)
                        val inResults = showSearchResults && searchResultBounds.contains(pos)
                        if (!inSearch && !inResults) {
                            val up = waitForUpOrCancellation()
                            if (up != null) {
                                isSearchActive = false
                                showSearchResults = false
                                focusManager.clearFocus()
                                keyboardController?.hide()
                            }
                        }
                    }
                }
            }
    ) {
        // 顶部操作卡片：包含路径点统计与撤销操作
        if (routePoints.isNotEmpty() && (stage == RoutePlanStage.IDLE || stage == RoutePlanStage.SELECTING)) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 12.dp)
                    .animateContentSize(),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(AccentBlue.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "${routePoints.size}",
                            color = AccentBlue,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }

                    Text(
                        stringResource(R.string.selected_waypoints_count, routePoints.size),
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    VerticalDivider(
                        modifier = Modifier.height(18.dp),
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                    )

                    // 撤销上一个点
                    IconButton(
                        onClick = { viewModel.undoLastRoutePoint() },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Rounded.Undo,
                            contentDescription = stringResource(R.string.undo),
                            tint = AccentOrange,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // 全部清除 / 重新选点
                    IconButton(
                        onClick = { viewModel.restartSelectingPoints() },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            Icons.Rounded.DeleteSweep,
                            contentDescription = stringResource(R.string.reselect),
                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        // 摇杆控制面板（手动模拟时显示）
        if (isRunning && isManual) {
            JoystickPanel(
                viewModel = viewModel,
                maxSpeedMs = uiState.routeSimMode.speedMs.toFloat()
            )
        }

        // 右侧悬浮地图控制按钮组
        val animatedBottomPadding by animateDpAsState(
            targetValue = bottomBarHeight + bottomActionHeightDp + 16.dp,
            animationSpec = tween(durationMillis = 200),
            label = "fab_padding"
        )

        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = animatedBottomPadding)
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                RouteControlButton(
                    icon = Icons.Rounded.Layers,
                    onClick = { showMapTypeDialog = true }
                )
                RouteControlButton(
                    icon = Icons.Rounded.Bookmarks,
                    onClick = { showSavedRoutesDialog = true }
                )
                RouteControlButton(
                    icon = Icons.Rounded.MyLocation,
                    onClick = {
                        viewModel.fetchCurrentLocation(context) { lat, lng ->
                            mapController?.animateCamera(lat, lng, 16f)
                        }
                    }
                )
            }
        }

        // 底部多状态操作面板
        RouteBottomPanel(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = bottomBarHeight + 6.dp, start = 14.dp, end = 14.dp)
                .onGloballyPositioned { coordinates ->
                    bottomActionHeightPx = coordinates.size.height
                },
            stage = stage,
            routePoints = routePoints,
            uiState = uiState,
            onConfirmPoint = {
                val tLat = mapController?.cameraTargetLat
                val tLng = mapController?.cameraTargetLng
                if (tLat != null && tLng != null) {
                    viewModel.addRoutePoint(tLat, tLng)
                }
            },
            onFinishSelecting = {
                viewModel.finishSelectingPoints()
            },
            onRestartSelecting = {
                viewModel.restartSelectingPoints()
            },
            onSaveRoute = { showSaveRouteDialog = true },
            onStartPlanning = { showConfigDialog = true },
            onStopRoute = {
                viewModel.stopRoutePlanning()
            },
            searchBar = if (stage == RoutePlanStage.IDLE || stage == RoutePlanStage.SELECTING) {
                { searchModifier ->
                    val searchActive = isSearchActive

                    Box(modifier = searchModifier) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                                .onGloballyPositioned { coords ->
                                    searchBounds = coords.boundsInRoot()
                                },
                            shape = RoundedCornerShape(23.dp),
                            color = MaterialTheme.colorScheme.surface,
                            shadowElevation = 4.dp
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clickable {
                                        if (!searchActive) isSearchActive = true
                                    }
                                    .padding(horizontal = 14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Rounded.Search,
                                    contentDescription = null,
                                    tint = if (searchActive) AccentBlue else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.width(10.dp))

                                if (searchActive) {
                                    BasicTextField(
                                        value = searchQuery,
                                        onValueChange = { searchQuery = it },
                                        textStyle = TextStyle(
                                            fontSize = 14.sp,
                                            color = MaterialTheme.colorScheme.onSurface
                                        ),
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                        keyboardActions = KeyboardActions(onSearch = { submitSearch() }),
                                        modifier = Modifier
                                            .weight(1f)
                                            .focusRequester(searchFocusRequester),
                                        decorationBox = { innerTextField ->
                                            if (searchQuery.isEmpty()) {
                                                Text(
                                                    stringResource(R.string.search_location_hint),
                                                    fontSize = 13.5.sp,
                                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                                                )
                                            }
                                            innerTextField()
                                        }
                                    )

                                    if (searchQuery.isNotEmpty()) {
                                        IconButton(
                                            onClick = {
                                                searchQuery = ""
                                                showSearchResults = false
                                            },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                Icons.Rounded.Close,
                                                null,
                                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                } else {
                                    Text(
                                        stringResource(R.string.search_location_hint),
                                        fontSize = 13.5.sp,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }

                        // 搜索结果下拉弹出列表
                        AnimatedVisibility(
                            visible = showSearchResults && searchResults.isNotEmpty(),
                            enter = fadeIn() + expandVertically(expandFrom = Alignment.Bottom),
                            exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Bottom),
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .offset(y = (-56).dp)
                        ) {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 240.dp)
                                    .onGloballyPositioned { coords ->
                                        searchResultBounds = coords.boundsInRoot()
                                    },
                                shape = RoundedCornerShape(18.dp),
                                color = MaterialTheme.colorScheme.surface,
                                shadowElevation = 10.dp
                            ) {
                                LazyColumn(
                                    modifier = Modifier.fillMaxWidth(),
                                    contentPadding = PaddingValues(vertical = 6.dp)
                                ) {
                                    items(searchResults) { poi ->
                                        Surface(
                                            onClick = {
                                                mapController?.animateCamera(poi.lat, poi.lng, 17f)
                                                isSearchActive = false
                                                showSearchResults = false
                                                focusManager.clearFocus()
                                                keyboardController?.hide()
                                            },
                                            color = Color.Transparent,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
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
                                                        contentDescription = null,
                                                        tint = AccentBlue,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                                Spacer(Modifier.width(12.dp))
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        poi.title,
                                                        fontSize = 14.sp,
                                                        fontWeight = FontWeight.Medium,
                                                        color = MaterialTheme.colorScheme.onSurface
                                                    )
                                                    Text(
                                                        poi.snippet,
                                                        fontSize = 11.5.sp,
                                                        color = MaterialTheme.colorScheme.onSurface.copy(
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
            } else null
        )
    }

    if (showConfigDialog) {
        RouteConfigDialog(
            uiState = uiState,
            onDismiss = {
                showConfigDialog = false
            },
            onStartRoute = {
                showConfigDialog = false
                viewModel.startRoutePlanning()
            },
            onRunModeChange = viewModel::setRouteRunMode,
            onSpeedChange = viewModel::setRouteSimMode,
            onCustomSpeedChange = viewModel::setCustomSpeedMs,
            onUseRealRouteChange = viewModel::setUseRealRoute,
            onStopAtDestinationChange = viewModel::setStopAtDestination,
            onEnableStepSimulationChange = viewModel::setEnableStepSimulation,
            onStepCadenceChange = viewModel::setStepCadenceSpm,
            onIsAutoCadenceChange = viewModel::setIsAutoCadence
        )
    }

    if (uiState.isFetchingRoute) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.35f)),
            contentAlignment = Alignment.Center
        ) {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
                modifier = Modifier.padding(32.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = AccentBlue)
                    Text(
                        stringResource(R.string.planning_route_trajectory),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }

    if (showMapTypeDialog) {
        MapTypeDialog(
            currentMapType = uiState.mapType,
            onMapTypeSelected = { viewModel.setMapType(it) },
            currentMapEngine = uiState.mapEngine,
            onMapEngineSelected = { viewModel.setMapEngine(it) },
            onDismiss = { showMapTypeDialog = false }
        )
    }

    if (showSaveRouteDialog) {
        SaveRouteDialog(
            routePoints = routePoints,
            viewModel = viewModel,
            onDismiss = { showSaveRouteDialog = false }
        )
    }

    if (showSavedRoutesDialog) {
        SavedRoutesDialog(
            uiState = uiState,
            viewModel = viewModel,
            mapController = mapController,
            bottomActionHeightDp = bottomActionHeightDp,
            bottomBarHeight = bottomBarHeight,
            onDismiss = { showSavedRoutesDialog = false }
        )
    }
}
