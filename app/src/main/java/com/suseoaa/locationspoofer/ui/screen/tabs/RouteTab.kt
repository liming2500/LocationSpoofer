package com.suseoaa.locationspoofer.ui.screen.tabs

import android.annotation.SuppressLint
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.suseoaa.locationspoofer.R
import com.suseoaa.locationspoofer.data.model.*
import com.suseoaa.locationspoofer.ui.components.AppMapController
import com.suseoaa.locationspoofer.ui.components.AppMapMarker
import com.suseoaa.locationspoofer.ui.components.MapTypeDialog
import com.suseoaa.locationspoofer.ui.components.MarkerType
import com.suseoaa.locationspoofer.ui.screen.*
import com.suseoaa.locationspoofer.ui.theme.AccentBlue
import com.suseoaa.locationspoofer.ui.theme.AccentGreen
import com.suseoaa.locationspoofer.ui.theme.AccentOrange
import com.suseoaa.locationspoofer.ui.theme.noRippleClickable
import com.suseoaa.locationspoofer.viewmodel.MainViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalSharedTransitionApi::class)
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

    // 进入路线页面时，如果当前处于 IDLE，自动切换至选点模式 (SELECTING)
    LaunchedEffect(Unit) {
        if (uiState.routePlanStage == RoutePlanStage.IDLE) {
            viewModel.restartSelectingPoints()
        }
    }

    LaunchedEffect(isSearchActive) {
        if (isSearchActive) {
            searchFocusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    BackHandler(enabled = isSearchActive) {
        isSearchActive = false
        showSearchResults = false
        focusManager.clearFocus()
        keyboardController?.hide()
    }

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
        com.suseoaa.locationspoofer.utils.MapCoverageHelper.drawCoverage(map, locations)

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
                else -> "$idx"
            }
            map.addMarker(
                p.lat,
                p.lng,
                label,
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

    val density = LocalDensity.current
    val bottomActionHeightDp = with(density) { bottomActionHeightPx.toDp() }
    val fabBottomPadding by animateDpAsState(
        targetValue = if (bottomActionHeightDp > 0.dp) {
            bottomActionHeightDp + bottomBarHeight + 24.dp
        } else {
            when (stage) {
                RoutePlanStage.SELECTING, RoutePlanStage.READY, RoutePlanStage.IDLE -> bottomBarHeight + 110.dp
                RoutePlanStage.RUNNING -> if (isManual) 240.dp else bottomBarHeight + 96.dp
            }
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "route_fab_bottom_padding"
    )

    LaunchedEffect(uiState.routePlanStage, routePoints, isActive, bottomActionHeightDp) {
        if (!isActive) return@LaunchedEffect
        if ((uiState.routePlanStage == RoutePlanStage.READY || uiState.routePlanStage == RoutePlanStage.RUNNING) && routePoints.size >= 2) {
            val padLeft = with(density) { 36.dp.roundToPx() }
            val padTop = with(density) { 80.dp.roundToPx() }
            val padRight = with(density) { 36.dp.roundToPx() }
            val effectiveBottomDp =
                if (bottomActionHeightDp > 0.dp) bottomActionHeightDp + bottomBarHeight + 24.dp else bottomBarHeight + 160.dp
            val padBottom = with(density) { effectiveBottomDp.roundToPx() }
            mapController?.fitBounds(
                points = routePoints.map { Pair(it.lat, it.lng) },
                paddingLeft = padLeft,
                paddingTop = padTop,
                paddingRight = padRight,
                paddingBottom = padBottom
            )
        }
    }

    SharedTransitionLayout {
        AnimatedContent(
            targetState = isSearchActive,
            transitionSpec = {
                androidx.compose.animation.EnterTransition.None togetherWith
                        androidx.compose.animation.ExitTransition.None
            },
            label = "route_search_transition"
        ) content@{ searchActive ->
            val searchModifier = Modifier
                .sharedElement(
                    rememberSharedContentState(key = "route_search_bar"),
                    this@content
                )
                .onGloballyPositioned { searchBounds = it.boundsInRoot() }

            Box(modifier = Modifier.fillMaxSize()) {
                // 中心瞄准标记（选点阶段显示，搜索激活时隐藏）
                if ((stage == RoutePlanStage.SELECTING || stage == RoutePlanStage.IDLE) && !searchActive) {
                    Icon(
                        Icons.Rounded.AddLocationAlt,
                        contentDescription = null,
                        tint = AccentBlue,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(36.dp)
                            .padding(bottom = 16.dp)
                    )
                }

                if (isRunning && isManual) {
                    Icon(
                        Icons.Rounded.PersonPin,
                        contentDescription = null,
                        tint = AccentOrange,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(44.dp)
                    )
                }

                // 右侧悬浮功能按钮（始终显示，主动避让底部操作卡片）
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 16.dp, bottom = fabBottomPadding),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalAlignment = Alignment.End
                ) {
                    RouteControlButton(Icons.Rounded.MyLocation) {
                        viewModel.fetchCurrentLocation(context) { lat, lng ->
                            mapController?.animateCamera(lat, lng, 16f)
                        }
                    }
                    RouteControlButton(Icons.Rounded.Layers) {
                        showMapTypeDialog = true
                    }
                    RouteControlButton(Icons.Rounded.Bookmarks) {
                        showSavedRoutesDialog = true
                    }
                }

                if (stage == RoutePlanStage.RUNNING) {
                    if (isManual) {
                        JoystickPanel(
                            viewModel = viewModel,
                            maxSpeedMs = uiState.routeSimMode.speedMs.toFloat()
                        )
                    }
                }

                // 底部动作控制区域（Miuix 质感悬浮操作卡片，集成底部搜索栏）
                RouteBottomPanel(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 14.dp)
                        .padding(bottom = bottomBarHeight + 12.dp)
                        .onGloballyPositioned { bottomActionHeightPx = it.size.height },
                    stage = if (stage == RoutePlanStage.IDLE) RoutePlanStage.SELECTING else stage,
                    routePoints = routePoints,
                    uiState = uiState,
                    searchBar = if (!searchActive && (stage == RoutePlanStage.IDLE || stage == RoutePlanStage.SELECTING)) {
                        { barModifier ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                HomeSearchBar(
                                    query = searchQuery,
                                    searchMode = uiState.searchMode,
                                    onSearchModeChange = viewModel::setSearchMode,
                                    onQueryChange = { searchQuery = it },
                                    onSearch = submitSearch,
                                    onFocus = { isSearchActive = true },
                                    modifier = searchModifier
                                        .then(barModifier)
                                        .weight(1f),
                                    focusRequester = searchFocusRequester
                                )

                                // 撤销上一个点按钮（有路点时展开）
                                AnimatedVisibility(
                                    visible = routePoints.isNotEmpty(),
                                    enter = fadeIn() + expandHorizontally(),
                                    exit = fadeOut() + shrinkHorizontally()
                                ) {
                                    Surface(
                                        onClick = { viewModel.undoLastRoutePoint() },
                                        shape = RoundedCornerShape(26.dp),
                                        color = MaterialTheme.colorScheme.surface,
                                        shadowElevation = 6.dp,
                                        modifier = Modifier.height(52.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxHeight()
                                                .padding(horizontal = 14.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(
                                                Icons.AutoMirrored.Rounded.Undo,
                                                contentDescription = stringResource(R.string.undo),
                                                tint = AccentBlue,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Text(
                                                stringResource(R.string.undo),
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = AccentBlue
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } else null,
                    onConfirmPoint = {
                        val tLat = mapController?.cameraTargetLat
                        val tLng = mapController?.cameraTargetLng
                        if (tLat != null && tLng != null) {
                            viewModel.addRoutePoint(tLat, tLng)
                        } else {
                            val fallbackLat = uiState.latitudeInput.toDoubleOrNull()
                            val fallbackLng = uiState.longitudeInput.toDoubleOrNull()
                            if (fallbackLat != null && fallbackLng != null) {
                                viewModel.addRoutePoint(fallbackLat, fallbackLng)
                            }
                        }
                    },
                    onFinishSelecting = { viewModel.finishSelectingPoints() },
                    onRestartSelecting = { viewModel.restartSelectingPoints() },
                    onSaveRoute = { showSaveRouteDialog = true },
                    onStartPlanning = { showConfigDialog = true },
                    onStopRoute = { viewModel.stopRoutePlanning() }
                )

                // 搜索激活时：点击外部遮罩退出搜索
                if (searchActive && showSearchResults) {
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
                                        showSearchResults = false
                                    }
                                }
                            }
                    )
                }

                // 搜索激活时：顶部搜索栏与联想结果卡片
                if (searchActive) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(top = 8.dp)
                    ) {
                        HomeSearchBar(
                            query = searchQuery,
                            searchMode = uiState.searchMode,
                            onSearchModeChange = viewModel::setSearchMode,
                            onQueryChange = { searchQuery = it },
                            onSearch = submitSearch,
                            onFocus = { isSearchActive = true },
                            modifier = searchModifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp),
                            focusRequester = searchFocusRequester
                        )

                        AnimatedVisibility(
                            visible = showSearchResults && searchResults.isNotEmpty(),
                            enter = fadeIn(tween(160)) + expandVertically(tween(220)),
                            exit = fadeOut(tween(120)) + shrinkVertically(tween(160))
                        ) {
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 6.dp)
                                    .onGloballyPositioned { searchResultBounds = it.boundsInRoot() }
                            ) {
                                LazyColumn(modifier = Modifier.heightIn(max = 280.dp)) {
                                    items(searchResults) { poi ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .noRippleClickable {
                                                    mapController?.animateCamera(
                                                        poi.lat,
                                                        poi.lng,
                                                        17.5f
                                                    )
                                                    showSearchResults = false
                                                    isSearchActive = false
                                                    searchQuery = poi.title
                                                    focusManager.clearFocus()
                                                    keyboardController?.hide()
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
        }
    }

    if (showConfigDialog) {
        MiuixRouteConfigDialog(
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
        var routeName by remember { mutableStateOf("") }
        Dialog(
            onDismissRequest = { showSaveRouteDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .padding(vertical = 20.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 10.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(AccentBlue.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Rounded.BookmarkAdd,
                                contentDescription = null,
                                tint = AccentBlue,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                stringResource(R.string.bookmark_current_route),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                stringResource(R.string.selected_waypoints_count, routePoints.size),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }

                    OutlinedTextField(
                        value = routeName,
                        onValueChange = { routeName = it },
                        label = { Text(stringResource(R.string.route_name)) },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = { showSaveRouteDialog = false }) {
                            Text(
                                stringResource(R.string.cancel),
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (routeName.isNotBlank()) {
                                    viewModel.saveRoute(routeName, routePoints)
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.save_success),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    showSaveRouteDialog = false
                                }
                            },
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
                            modifier = Modifier.height(42.dp)
                        ) {
                            Text(stringResource(R.string.save), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    if (showSavedRoutesDialog) {
        Dialog(
            onDismissRequest = { showSavedRoutesDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .padding(vertical = 20.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 10.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // 顶部标题
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(AccentBlue.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Rounded.Bookmarks,
                                    contentDescription = null,
                                    tint = AccentBlue,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(
                                    stringResource(R.string.route_library),
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    if (uiState.savedRoutes.isEmpty()) stringResource(R.string.no_saved_routes) else stringResource(R.string.total_saved_routes, uiState.savedRoutes.size),
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            }
                        }

                        IconButton(
                            onClick = { showSavedRoutesDialog = false },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Rounded.Close,
                                contentDescription = stringResource(R.string.close),
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    if (uiState.savedRoutes.isEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                Icons.Rounded.Route,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(Modifier.height(10.dp))
                            Text(
                                stringResource(R.string.no_saved_routes),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                stringResource(R.string.add_points_save_hint),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 320.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(uiState.savedRoutes) { route ->
                                Surface(
                                    onClick = {
                                        viewModel.loadSavedRoute(route)
                                        if (route.points.size >= 2) {
                                            val padLeft = with(density) { 36.dp.roundToPx() }
                                            val padTop = with(density) { 80.dp.roundToPx() }
                                            val padRight = with(density) { 36.dp.roundToPx() }
                                            val effectiveBottomDp =
                                                if (bottomActionHeightDp > 0.dp) bottomActionHeightDp + bottomBarHeight + 24.dp else bottomBarHeight + 160.dp
                                            val padBottom =
                                                with(density) { effectiveBottomDp.roundToPx() }
                                            mapController?.fitBounds(
                                                points = route.points.map { Pair(it.lat, it.lng) },
                                                paddingLeft = padLeft,
                                                paddingTop = padTop,
                                                paddingRight = padRight,
                                                paddingBottom = padBottom
                                            )
                                        } else {
                                            route.points.firstOrNull()?.let { firstPoint ->
                                                mapController?.animateCamera(
                                                    firstPoint.lat,
                                                    firstPoint.lng,
                                                    17.5f
                                                )
                                            }
                                        }
                                        showSavedRoutesDialog = false
                                        Toast.makeText(
                                            context,
                                            context.getString(R.string.loaded_route, route.name),
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    },
                                    shape = RoundedCornerShape(16.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 14.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .clip(RoundedCornerShape(11.dp))
                                                .background(AccentBlue.copy(alpha = 0.12f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                Icons.Rounded.Route,
                                                contentDescription = null,
                                                tint = AccentBlue,
                                                modifier = Modifier.size(19.dp)
                                            )
                                        }

                                        Spacer(Modifier.width(12.dp))

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                route.name,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.5.sp,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Spacer(Modifier.height(2.dp))
                                            Text(
                                                stringResource(
                                                    R.string.route_nodes_count,
                                                    route.points.size
                                                ),
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSurface.copy(
                                                    alpha = 0.55f
                                                )
                                            )
                                        }

                                        IconButton(
                                            onClick = { viewModel.deleteSavedRoute(route) },
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(
                                                Icons.Rounded.DeleteOutline,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Button(
                        onClick = { showSavedRoutesDialog = false },
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            contentColor = MaterialTheme.colorScheme.onSurface
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                    ) {
                        Text(
                            stringResource(R.string.close),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RouteControlButton(
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
private fun RouteBottomPanel(
    modifier: Modifier,
    stage: RoutePlanStage,
    routePoints: List<RoutePoint>,
    uiState: AppState,
    onConfirmPoint: () -> Unit,
    onFinishSelecting: () -> Unit,
    onRestartSelecting: () -> Unit,
    onSaveRoute: () -> Unit,
    onStartPlanning: () -> Unit,
    onStopRoute: () -> Unit,
    searchBar: (@Composable (Modifier) -> Unit)? = null
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            )
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (searchBar != null) {
                searchBar(Modifier.fillMaxWidth())
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 8.dp
            ) {
                Box(modifier = Modifier.padding(14.dp)) {
                    when (stage) {
                        RoutePlanStage.IDLE, RoutePlanStage.SELECTING -> {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // 确认添加路点
                                Button(
                                    onClick = onConfirmPoint,
                                    modifier = Modifier
                                        .weight(1.2f)
                                        .height(50.dp),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)
                                ) {
                                    Icon(
                                        Icons.Rounded.AddLocation,
                                        null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        if (routePoints.isEmpty()) stringResource(R.string.add_start_point) else stringResource(R.string.add_nth_point, routePoints.size + 1),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                }

                                // 完成规划
                                Button(
                                    onClick = onFinishSelecting,
                                    enabled = routePoints.size >= 2,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(50.dp),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = AccentGreen,
                                        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(
                                            alpha = 0.5f
                                        )
                                    )
                                ) {
                                    Icon(
                                        Icons.Rounded.CheckCircle,
                                        null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        stringResource(R.string.finish_selecting),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                }
                            }
                        }

                        RoutePlanStage.READY -> {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedButton(
                                    onClick = onRestartSelecting,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(50.dp),
                                    contentPadding = PaddingValues(horizontal = 4.dp),
                                    shape = RoundedCornerShape(16.dp)
                                ) {
                                    Icon(
                                        Icons.Rounded.Refresh,
                                        null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(3.dp))
                                    Text(
                                        stringResource(R.string.reselect),
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 13.5.sp,
                                        maxLines = 1
                                    )
                                }

                                OutlinedButton(
                                    onClick = onSaveRoute,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(50.dp),
                                    contentPadding = PaddingValues(horizontal = 4.dp),
                                    shape = RoundedCornerShape(16.dp)
                                ) {
                                    Icon(
                                        Icons.Rounded.BookmarkAdd,
                                        null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(3.dp))
                                    Text(
                                        stringResource(R.string.bookmark),
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 13.5.sp,
                                        maxLines = 1
                                    )
                                }

                                Button(
                                    onClick = onStartPlanning,
                                    modifier = Modifier
                                        .weight(1.35f)
                                        .height(50.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)
                                ) {
                                    Icon(
                                        Icons.Rounded.PlayArrow,
                                        null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(5.dp))
                                    Text(
                                        stringResource(R.string.start_simulation),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        maxLines = 1
                                    )
                                }
                            }
                        }

                        RoutePlanStage.RUNNING -> {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(start = 6.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .clip(CircleShape)
                                            .background(AccentGreen)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = if (uiState.routeRunMode == RouteRunMode.MANUAL) stringResource(R.string.joystick_controlling_active) else stringResource(R.string.cruising_speed_format, uiState.routeSimMode.speedMs.toInt()),
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }

                                Button(
                                    onClick = onStopRoute,
                                    shape = RoundedCornerShape(16.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.errorContainer,
                                        contentColor = MaterialTheme.colorScheme.error
                                    ),
                                    modifier = Modifier.height(46.dp)
                                ) {
                                    Icon(
                                        Icons.Rounded.StopCircle,
                                        null,
                                        modifier = Modifier.size(19.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        stringResource(R.string.stop_simulation),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.5.sp
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

@Composable
private fun MiuixRouteConfigDialog(
    uiState: AppState,
    onDismiss: () -> Unit,
    onStartRoute: () -> Unit,
    onRunModeChange: (RouteRunMode) -> Unit,
    onSpeedChange: (SimMode) -> Unit,
    onCustomSpeedChange: (Double) -> Unit = {},
    onUseRealRouteChange: (Boolean) -> Unit = {},
    onStopAtDestinationChange: (Boolean) -> Unit = {},
    onEnableStepSimulationChange: (Boolean) -> Unit = {},
    onStepCadenceChange: (Int) -> Unit = {},
    onIsAutoCadenceChange: (Boolean) -> Unit = {}
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .padding(vertical = 20.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 10.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 顶部标题
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        stringResource(R.string.route_sim_settings),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            Icons.Rounded.Close,
                            contentDescription = stringResource(R.string.close),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // 运行模式选择 (手动 / 循环)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(R.string.run_mode),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        MiuixSelectionCard(
                            title = stringResource(R.string.loop_auto_cruise),
                            subtitle = stringResource(R.string.loop_auto_cruise_desc),
                            icon = Icons.Rounded.Autorenew,
                            isSelected = uiState.routeRunMode == RouteRunMode.LOOP,
                            onClick = { onRunModeChange(RouteRunMode.LOOP) },
                            modifier = Modifier.weight(1f)
                        )
                        MiuixSelectionCard(
                            title = stringResource(R.string.joystick_manual_control),
                            subtitle = stringResource(R.string.joystick_manual_control_desc),
                            icon = Icons.Rounded.SportsEsports,
                            isSelected = uiState.routeRunMode == RouteRunMode.MANUAL,
                            onClick = { onRunModeChange(RouteRunMode.MANUAL) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // 速度选择
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(R.string.cruise_speed),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            Triple(SimMode.WALKING, stringResource(R.string.walking), "1.4m/s"),
                            Triple(SimMode.RUNNING, stringResource(R.string.running), "3.0m/s"),
                            Triple(SimMode.CYCLING, stringResource(R.string.cycling), "5.5m/s"),
                            Triple(SimMode.DRIVING, stringResource(R.string.driving), "15m/s"),
                            Triple(
                                SimMode.CUSTOM,
                                stringResource(R.string.custom),
                                "${
                                    String.format(
                                        java.util.Locale.US,
                                        "%.1f",
                                        uiState.customSpeedMs
                                    )
                                }m/s"
                            )
                        ).forEach { (mode, name, speed) ->
                            val isSelected = uiState.routeSimMode == mode
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = if (isSelected) AccentBlue.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(
                                    alpha = 0.35f
                                ),
                                border = if (isSelected) BorderStroke(1.5.dp, AccentBlue) else null,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { onSpeedChange(mode) }
                            ) {
                                Column(
                                    modifier = Modifier.padding(vertical = 10.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        name,
                                        fontSize = 13.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) AccentBlue else MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        speed,
                                        fontSize = 10.5.sp,
                                        color = if (isSelected) AccentBlue.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurface.copy(
                                            alpha = 0.45f
                                        )
                                    )
                                }
                            }
                        }
                    }

                    // 自定义速度调节模块
                    if (uiState.routeSimMode == SimMode.CUSTOM) {
                        var sliderValue by remember(uiState.customSpeedMs) {
                            mutableFloatStateOf(
                                uiState.customSpeedMs.toFloat()
                            )
                        }
                        val paceSec = if (sliderValue > 0.2f) (1000.0 / sliderValue).toInt() else 0
                        val paceMin = paceSec / 60
                        val paceRem = paceSec % 60
                        val paceStr = if (paceSec in 60..3599) "${paceMin}'${
                            String.format(
                                java.util.Locale.US,
                                "%02d",
                                paceRem
                            )
                        }\"/km" else "--"

                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            stringResource(R.string.custom_speed),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            stringResource(R.string.pace_format, paceStr),
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                        )
                                    }
                                    Text(
                                        "${
                                            String.format(
                                                java.util.Locale.US,
                                                "%.1f",
                                                sliderValue
                                            )
                                        } m/s (${
                                            String.format(
                                                java.util.Locale.US,
                                                "%.1f",
                                                sliderValue * 3.6
                                            )
                                        } km/h)",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = AccentBlue
                                    )
                                }

                                Slider(
                                    value = sliderValue,
                                    onValueChange = {
                                        sliderValue = it
                                        onCustomSpeedChange(it.toDouble())
                                    },
                                    valueRange = 0.2f..45.0f,
                                    steps = 447,
                                    colors = SliderDefaults.colors(
                                        thumbColor = AccentBlue,
                                        activeTrackColor = AccentBlue,
                                        inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                                    )
                                )

                                // 微调步进按钮
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    listOf(-1.0f, -0.2f, 0.2f, 1.0f).forEach { step ->
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = MaterialTheme.colorScheme.surfaceVariant.copy(
                                                alpha = 0.6f
                                            ),
                                            modifier = Modifier
                                                .weight(1f)
                                                .clickable {
                                                    val newVal =
                                                        (sliderValue + step).coerceIn(0.2f, 45.0f)
                                                    sliderValue = newVal
                                                    onCustomSpeedChange(newVal.toDouble())
                                                }
                                        ) {
                                            Text(
                                                if (step > 0) "+${step}m/s" else "${step}m/s",
                                                fontSize = 10.5.sp,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                                modifier = Modifier.padding(vertical = 4.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 步频与计步模拟卡片
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    stringResource(R.string.step_cadence_simulation),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    stringResource(R.string.step_cadence_simulation_desc),
                                    fontSize = 11.5.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                                )
                            }
                            Switch(
                                checked = uiState.enableStepSimulation,
                                onCheckedChange = onEnableStepSimulationChange,
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = AccentBlue
                                )
                            )
                        }

                        if (uiState.enableStepSimulation) {
                            // 步频模式切换：自适应 vs 固定步频
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (uiState.isAutoCadence) AccentBlue.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(
                                        alpha = 0.5f
                                    ),
                                    border = if (uiState.isAutoCadence) BorderStroke(
                                        1.dp,
                                        AccentBlue
                                    ) else null,
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { onIsAutoCadenceChange(true) }
                                ) {
                                    Text(
                                        stringResource(R.string.smart_adaptive_cadence),
                                        fontSize = 12.sp,
                                        fontWeight = if (uiState.isAutoCadence) FontWeight.Bold else FontWeight.Normal,
                                        color = if (uiState.isAutoCadence) AccentBlue else MaterialTheme.colorScheme.onSurface,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                        modifier = Modifier.padding(vertical = 6.dp)
                                    )
                                }

                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (!uiState.isAutoCadence) AccentBlue.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(
                                        alpha = 0.5f
                                    ),
                                    border = if (!uiState.isAutoCadence) BorderStroke(
                                        1.dp,
                                        AccentBlue
                                    ) else null,
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { onIsAutoCadenceChange(false) }
                                ) {
                                    Text(
                                        stringResource(R.string.custom_fixed_cadence),
                                        fontSize = 12.sp,
                                        fontWeight = if (!uiState.isAutoCadence) FontWeight.Bold else FontWeight.Normal,
                                        color = if (!uiState.isAutoCadence) AccentBlue else MaterialTheme.colorScheme.onSurface,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                        modifier = Modifier.padding(vertical = 6.dp)
                                    )
                                }
                            }

                            if (!uiState.isAutoCadence) {
                                var cadenceSlider by remember(uiState.stepCadenceSpm) {
                                    mutableFloatStateOf(
                                        uiState.stepCadenceSpm.toFloat()
                                    )
                                }
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 2.dp, vertical = 2.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            stringResource(R.string.set_cadence),
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                        )
                                        Text(
                                            "${cadenceSlider.toInt()} 步/分 (约 ${
                                                String.format(
                                                    java.util.Locale.US,
                                                    "%.1f",
                                                    cadenceSlider / 60.0
                                                )
                                            } 步/秒)",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = AccentBlue
                                        )
                                    }
                                    Slider(
                                        value = cadenceSlider,
                                        onValueChange = {
                                            cadenceSlider = it
                                            onStepCadenceChange(it.toInt())
                                        },
                                        valueRange = 60f..240f,
                                        steps = 179,
                                        colors = SliderDefaults.colors(
                                            thumbColor = AccentBlue,
                                            activeTrackColor = AccentBlue,
                                            inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                                        )
                                    )
                                }
                            } else {
                                Text(
                                    stringResource(R.string.smart_cadence_hint),
                                    fontSize = 11.sp,
                                    color = AccentBlue.copy(alpha = 0.85f),
                                    modifier = Modifier.padding(horizontal = 2.dp)
                                )
                            }
                        }
                    }
                }

                // 到达终点后停下开关
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.stop_at_destination_title),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                if (uiState.stopAtDestination) stringResource(R.string.stop_at_destination_desc_on) else stringResource(R.string.stop_at_destination_desc_off),
                                fontSize = 11.5.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                            )
                        }
                        Switch(
                            checked = uiState.stopAtDestination,
                            onCheckedChange = onStopAtDestinationChange,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = AccentBlue
                            )
                        )
                    }
                }

                // 真实道路匹配开关
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.real_road_matching),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                stringResource(R.string.real_road_matching_desc),
                                fontSize = 11.5.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                            )
                        }
                        Switch(
                            checked = uiState.useRealRoute,
                            onCheckedChange = onUseRealRouteChange,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = AccentBlue
                            )
                        )
                    }
                }

                // 启动按钮
                Button(
                    onClick = onStartRoute,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Icon(Icons.Rounded.PlayArrow, null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.start_route_simulation), fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun MiuixSelectionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = if (isSelected) AccentBlue.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surfaceVariant.copy(
            alpha = 0.35f
        ),
        border = if (isSelected) BorderStroke(1.5.dp, AccentBlue) else null,
        modifier = modifier.height(90.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (isSelected) AccentBlue else MaterialTheme.colorScheme.onSurface.copy(
                    alpha = 0.6f
                ),
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.height(4.dp))
            Text(
                title,
                fontSize = 13.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) AccentBlue else MaterialTheme.colorScheme.onSurface
            )
            Text(
                subtitle,
                fontSize = 10.5.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                maxLines = 1
            )
        }
    }
}
