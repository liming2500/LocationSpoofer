package com.suseoaa.locationspoofer.ui.screen.tabs

import android.annotation.SuppressLint
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
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
    val focusManager = LocalFocusManager.current
    var bottomActionHeightPx by remember { mutableIntStateOf(0) }

    val stage = uiState.routePlanStage
    val isRunning = stage == RoutePlanStage.RUNNING
    val isManual = uiState.routeRunMode == RouteRunMode.MANUAL
    val routePoints = uiState.routePoints

    // 进入路线页面时，如果当前处于 IDLE，自动切换至选点模式 (SELECTING)
    LaunchedEffect(Unit) {
        if (uiState.routePlanStage == RoutePlanStage.IDLE) {
            viewModel.restartSelectingPoints()
        }
    }

    BackHandler(enabled = showSearchResults) {
        showSearchResults = false
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
            val type = when (idx) {
                0 -> MarkerType.GREEN
                routePoints.lastIndex -> MarkerType.RED
                else -> MarkerType.DEFAULT
            }
            if (uiState.useRealRoute && uiState.routePlanStage == RoutePlanStage.RUNNING && type == MarkerType.DEFAULT) {
                return@forEachIndexed
            }
            map.addMarker(
                p.lat,
                p.lng,
                if (type == MarkerType.RED && uiState.useRealRoute && uiState.routePlanStage == RoutePlanStage.RUNNING) "终点" else "${idx + 1}",
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
            val padding = 150
            mapController?.fitBounds(routePoints.map { Pair(it.lat, it.lng) }, padding)
        }
    }

    LaunchedEffect(stage) {
        if (stage == RoutePlanStage.READY) showConfigDialog = true
    }

    val density = LocalDensity.current
    val bottomActionHeightDp = with(density) { bottomActionHeightPx.toDp() }
    val fabBottomPadding by animateDpAsState(
        targetValue = if (bottomActionHeightDp > 0.dp) {
            bottomActionHeightDp + 14.dp
        } else {
            when (stage) {
                RoutePlanStage.SELECTING, RoutePlanStage.READY, RoutePlanStage.IDLE -> bottomBarHeight + 96.dp
                RoutePlanStage.RUNNING -> if (isManual) 220.dp else bottomBarHeight + 84.dp
            }
        },
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 400f),
        label = "route_fab_bottom_padding"
    )

    Box(modifier = Modifier.fillMaxSize()) {
        // 中心瞄准标记（选点阶段显示）
        if (stage == RoutePlanStage.SELECTING || stage == RoutePlanStage.IDLE) {
            Icon(
                Icons.Rounded.AddLocationAlt,
                contentDescription = null,
                tint = AccentBlue,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(36.dp)
                    .padding(bottom = 14.dp)
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

        // 顶部搜索与操作栏（集成搜索框与路点微计数/撤销按钮）
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(top = 8.dp)
        ) {
            if (stage == RoutePlanStage.SELECTING || stage == RoutePlanStage.IDLE) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // 极简搜索框
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                        shadowElevation = 6.dp,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Rounded.Search,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                            Spacer(Modifier.width(8.dp))
                            BasicTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                textStyle = TextStyle(
                                    fontSize = 13.5.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.Medium
                                ),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(onSearch = {
                                    focusManager.clearFocus()
                                    if (searchQuery.isNotBlank()) {
                                        performPoiSearch(
                                            context,
                                            uiState.mapEngine,
                                            searchQuery,
                                            isDomestic
                                        ) { r ->
                                            searchResults = r
                                            showSearchResults = r.isNotEmpty()
                                        }
                                    }
                                }),
                                modifier = Modifier.weight(1f),
                                decorationBox = { innerTextField ->
                                    if (searchQuery.isEmpty()) {
                                        Text(
                                            stringResource(R.string.search_location_hint),
                                            fontSize = 13.5.sp,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                        )
                                    }
                                    innerTextField()
                                }
                            )
                            if (searchQuery.isNotEmpty()) {
                                IconButton(
                                    onClick = { searchQuery = "" },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        Icons.Rounded.Clear,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }

                    // 撤销上一个点按钮（有路点时展开）
                    AnimatedVisibility(
                        visible = routePoints.isNotEmpty(),
                        enter = fadeIn() + expandHorizontally(),
                        exit = fadeOut() + shrinkHorizontally()
                    ) {
                        Surface(
                            onClick = { viewModel.undoLastRoutePoint() },
                            shape = RoundedCornerShape(24.dp),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                            shadowElevation = 6.dp,
                            modifier = Modifier.height(48.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .padding(horizontal = 12.dp),
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
                                    "撤销",
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = AccentBlue
                                )
                            }
                        }
                    }
                }
            }

            AnimatedVisibility(visible = showSearchResults && searchResults.isNotEmpty()) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                        .heightIn(max = 260.dp)
                ) {
                    LazyColumn {
                        items(searchResults) { poi ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .noRippleClickable {
                                        mapController?.animateCamera(poi.lat, poi.lng, 16f)
                                        showSearchResults = false
                                        searchQuery = poi.title
                                    }
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
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
                                Spacer(Modifier.width(10.dp))
                                Column {
                                    Text(
                                        poi.title,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        poi.snippet,
                                        fontSize = 11.5.sp,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 右侧悬浮功能按钮（主动避让底部操作栏，与定位页面样式完全一致）
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

        // 底部动作控制区域（Miuix 质感悬浮操作卡片）
        RouteBottomPanel(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 14.dp)
                .padding(bottom = bottomBarHeight + 12.dp)
                .onGloballyPositioned { bottomActionHeightPx = it.size.height },
            stage = if (stage == RoutePlanStage.IDLE) RoutePlanStage.SELECTING else stage,
            routePoints = routePoints,
            uiState = uiState,
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
    }

    if (showConfigDialog) {
        MiuixRouteConfigDialog(
            uiState = uiState,
            onDismiss = {
                showConfigDialog = false
                if (stage == RoutePlanStage.READY) {
                    viewModel.restartSelectingPoints()
                }
            },
            onStartRoute = {
                showConfigDialog = false
                viewModel.startRoutePlanning()
            },
            onRunModeChange = viewModel::setRouteRunMode,
            onSpeedChange = viewModel::setRouteSimMode,
            onCustomSpeedChange = viewModel::setCustomSpeedMs,
            onUseRealRouteChange = viewModel::setUseRealRoute
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
                        "正在规划路线轨迹...",
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
                                "收藏当前路线",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                "已选 ${routePoints.size} 个路点节点",
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
                                    Toast.makeText(context, context.getString(R.string.save_success), Toast.LENGTH_SHORT).show()
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
                                    if (uiState.savedRoutes.isEmpty()) "暂无保存路线" else "共 ${uiState.savedRoutes.size} 条已存路线",
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
                                "添加路点后点击「收藏」即可保存规划路线",
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
                                        route.points.firstOrNull()?.let { firstPoint ->
                                            mapController?.animateCamera(firstPoint.lat, firstPoint.lng, 16f)
                                        }
                                        showSavedRoutesDialog = false
                                        Toast.makeText(context, "已加载路线「${route.name}」", Toast.LENGTH_SHORT).show()
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
                                                stringResource(R.string.route_nodes_count, route.points.size),
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
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
                        Text(stringResource(R.string.close), fontSize = 14.sp, fontWeight = FontWeight.Medium)
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
    onStopRoute: () -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
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
                            Icon(Icons.Rounded.AddLocation, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                if (routePoints.isEmpty()) "添加起始点" else "添加第 ${routePoints.size + 1} 点",
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
                                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            )
                        ) {
                            Icon(Icons.Rounded.CheckCircle, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "完成选点",
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
                                .weight(0.9f)
                                .height(50.dp),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Icon(Icons.Rounded.Refresh, null, modifier = Modifier.size(17.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("重选", fontWeight = FontWeight.Medium, fontSize = 13.sp)
                        }

                        OutlinedButton(
                            onClick = onSaveRoute,
                            modifier = Modifier
                                .weight(0.9f)
                                .height(50.dp),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Icon(Icons.Rounded.BookmarkAdd, null, modifier = Modifier.size(17.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("收藏", fontWeight = FontWeight.Medium, fontSize = 13.sp)
                        }

                        Button(
                            onClick = onStartPlanning,
                            modifier = Modifier
                                .weight(1.3f)
                                .height(50.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)
                        ) {
                            Icon(Icons.Rounded.PlayArrow, null, modifier = Modifier.size(19.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("开始模拟", fontWeight = FontWeight.Bold, fontSize = 14.sp)
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
                                text = if (uiState.routeRunMode == RouteRunMode.MANUAL) "手柄操控中" else "巡航中 (${uiState.routeSimMode.speedMs.toInt()} m/s)",
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
                            Icon(Icons.Rounded.StopCircle, null, modifier = Modifier.size(19.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("停止模拟", fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
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
    onUseRealRouteChange: (Boolean) -> Unit = {}
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
                                Icons.Rounded.Route,
                                contentDescription = null,
                                tint = AccentBlue,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                "路线模拟配置",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                "配置运行方式与巡航速度",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
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

                // 模式选择
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "运行控制模式",
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        MiuixSelectionCard(
                            title = "摇杆手柄操控",
                            subtitle = "屏幕虚拟摇杆灵活移动",
                            icon = Icons.Rounded.SportsEsports,
                            isSelected = uiState.routeRunMode == RouteRunMode.MANUAL,
                            onClick = { onRunModeChange(RouteRunMode.MANUAL) },
                            modifier = Modifier.weight(1f)
                        )

                        MiuixSelectionCard(
                            title = "循环自动巡航",
                            subtitle = "沿路点往返平滑移动",
                            icon = Icons.Rounded.Loop,
                            isSelected = uiState.routeRunMode == RouteRunMode.LOOP,
                            onClick = { onRunModeChange(RouteRunMode.LOOP) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // 速度选择（仅在循环巡航模式下展开）
                AnimatedVisibility(visible = uiState.routeRunMode == RouteRunMode.LOOP) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "巡航移动速度",
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                        ) {
                            listOf(
                                SimMode.WALKING to ("步行" to "1.4 m/s"),
                                SimMode.RUNNING to ("跑步" to "3.5 m/s"),
                                SimMode.CYCLING to ("骑行" to "6.0 m/s"),
                                SimMode.DRIVING to ("驾车" to "12.0 m/s")
                            ).forEach { (mode, pair) ->
                                val (title, speedStr) = pair
                                val isSelected = uiState.routeSimMode == mode
                                Surface(
                                    onClick = { onSpeedChange(mode) },
                                    shape = RoundedCornerShape(14.dp),
                                    color = if (isSelected) AccentBlue.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                    border = if (isSelected) BorderStroke(1.5.dp, AccentBlue) else null,
                                    modifier = Modifier.width(80.dp).height(64.dp)
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(6.dp),
                                        verticalArrangement = Arrangement.Center,
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            title,
                                            fontSize = 13.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isSelected) AccentBlue else MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            speedStr,
                                            fontSize = 10.5.sp,
                                            color = if (isSelected) AccentBlue.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                        )
                                    }
                                }
                            }
                        }
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
                                "真实道路匹配",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                "自动计算并贴合城市真实行车道路",
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
                    Text("开始路线模拟", fontSize = 15.sp, fontWeight = FontWeight.Bold)
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
        color = if (isSelected) AccentBlue.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
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
                tint = if (isSelected) AccentBlue else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
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
