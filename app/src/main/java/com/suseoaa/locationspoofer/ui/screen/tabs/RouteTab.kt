package com.suseoaa.locationspoofer.ui.screen.tabs

import android.annotation.SuppressLint
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.suseoaa.locationspoofer.R
import com.suseoaa.locationspoofer.data.model.*
import com.suseoaa.locationspoofer.ui.components.AppMapController
import com.suseoaa.locationspoofer.ui.components.AppMapMarker
import com.suseoaa.locationspoofer.ui.components.MapTypeDialog
import com.suseoaa.locationspoofer.ui.components.MarkerType
import com.suseoaa.locationspoofer.ui.screen.*
import com.suseoaa.locationspoofer.ui.theme.AccentBlue
import com.suseoaa.locationspoofer.ui.theme.AccentOrange
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
                RoutePlanStage.SELECTING, RoutePlanStage.READY, RoutePlanStage.IDLE -> bottomBarHeight + 130.dp
                RoutePlanStage.RUNNING -> if (isManual) 220.dp else bottomBarHeight + 84.dp
            }
        },
        label = "route_fab_bottom_padding"
    )

    Box(modifier = Modifier.fillMaxSize()) {
        // 中心瞄准标记（选点阶段显示）
        if (stage == RoutePlanStage.SELECTING || stage == RoutePlanStage.IDLE) {
            Icon(
                Icons.Rounded.AddLocationAlt, null,
                tint = AccentBlue.copy(alpha = 0.85f),
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(40.dp)
                    .padding(bottom = 16.dp)
            )
        }

        if (isRunning && isManual) {
            Icon(
                Icons.Rounded.PersonPin, null,
                tint = AccentOrange,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(48.dp)
            )
        }

        // 顶部搜索与选点状态栏（含系统状态栏避让）
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
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    androidx.compose.foundation.text.BasicTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        textStyle = androidx.compose.ui.text.TextStyle(
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onBackground
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
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .shadow(4.dp, RoundedCornerShape(22.dp))
                            .background(
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                                RoundedCornerShape(22.dp)
                            )
                            .padding(horizontal = 16.dp),
                        decorationBox = { innerTextField ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Rounded.Search,
                                    null,
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                                )
                                Spacer(Modifier.width(8.dp))
                                Box(modifier = Modifier.weight(1f)) {
                                    if (searchQuery.isEmpty()) {
                                        Text(
                                            stringResource(R.string.search_location_hint),
                                            fontSize = 14.sp,
                                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                                        )
                                    }
                                    innerTextField()
                                }
                            }
                        }
                    )
                }
            }

            AnimatedVisibility(visible = showSearchResults && searchResults.isNotEmpty()) {
                top.yukonga.miuix.kmp.basic.Card(
                    cornerRadius = 16.dp,
                    insideMargin = PaddingValues(0.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                        .heightIn(max = 280.dp)
                ) {
                    LazyColumn {
                        items(searchResults) { poi ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        mapController?.animateCamera(poi.lat, poi.lng, 16f)
                                        showSearchResults = false
                                        searchQuery = poi.title
                                    }
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Rounded.Place, null, tint = AccentBlue, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text(poi.title, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                    Text(poi.snippet, fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                                }
                            }
                        }
                    }
                }
            }

            TopBar(
                stage = stage,
                routePointCount = routePoints.size,
                isManual = isManual,
                onBack = { /* No back for tab */ },
                canUndo = (stage == RoutePlanStage.SELECTING || stage == RoutePlanStage.IDLE) && routePoints.isNotEmpty(),
                onUndo = { viewModel.undoLastRoutePoint() }
            )
        }

        // 右侧悬浮功能按钮（主动避让底部操作栏，永不重叠）
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

        // 底部动作控制区域（路线规划专属选点与控制栏）
        BottomActionBar(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = bottomBarHeight + 12.dp)
                .onGloballyPositioned { bottomActionHeightPx = it.size.height + ((bottomBarHeight.value + 12f) * density.density).toInt() },
            stage = if (stage == RoutePlanStage.IDLE) RoutePlanStage.SELECTING else stage,
            routePoints = routePoints,
            onConfirmPoint = {
                val tLat = mapController?.cameraTargetLat
                val tLng = mapController?.cameraTargetLng
                if (tLat != null && tLng != null) {
                    viewModel.addRoutePoint(tLat, tLng)
                    Toast.makeText(context, "已添加第 ${routePoints.size + 1} 个路点", Toast.LENGTH_SHORT).show()
                } else {
                    val fallbackLat = uiState.latitudeInput.toDoubleOrNull()
                    val fallbackLng = uiState.longitudeInput.toDoubleOrNull()
                    if (fallbackLat != null && fallbackLng != null) {
                        viewModel.addRoutePoint(fallbackLat, fallbackLng)
                        Toast.makeText(context, "已添加第 ${routePoints.size + 1} 个路点", Toast.LENGTH_SHORT).show()
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
        RoutePlanConfigDialog(
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
                .background(Color.Black.copy(alpha = 0.3f)),
            contentAlignment = Alignment.Center
        ) {
            top.yukonga.miuix.kmp.basic.Card(
                cornerRadius = 16.dp,
                insideMargin = PaddingValues(24.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = AccentBlue)
                    Text("正在规划真实路线...", fontSize = 16.sp)
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
        Dialog(onDismissRequest = { showSaveRouteDialog = false }) {
            top.yukonga.miuix.kmp.basic.Card(
                cornerRadius = 18.dp,
                insideMargin = PaddingValues(20.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text("收藏当前路线", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(16.dp))
                    OutlinedTextField(
                        value = routeName,
                        onValueChange = { routeName = it },
                        label = { Text(stringResource(R.string.route_name)) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(20.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { showSaveRouteDialog = false }) {
                            Text(stringResource(R.string.cancel))
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
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)
                        ) {
                            Text(stringResource(R.string.save))
                        }
                    }
                }
            }
        }
    }

    if (showSavedRoutesDialog) {
        Dialog(onDismissRequest = { showSavedRoutesDialog = false }) {
            top.yukonga.miuix.kmp.basic.Card(
                cornerRadius = 18.dp,
                insideMargin = PaddingValues(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 500.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        stringResource(R.string.route_library),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(16.dp))
                    if (uiState.savedRoutes.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                stringResource(R.string.no_saved_routes),
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                            )
                        }
                    } else {
                        LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                            items(uiState.savedRoutes) { route ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            viewModel.loadSavedRoute(route)
                                            showSavedRoutesDialog = false
                                        }
                                        .padding(vertical = 12.dp, horizontal = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            route.name,
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Text(
                                            stringResource(
                                                R.string.route_nodes_count,
                                                route.points.size
                                            ),
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.outline
                                        )
                                    }
                                    IconButton(onClick = { viewModel.deleteSavedRoute(route) }) {
                                        Icon(
                                            Icons.Rounded.Delete,
                                            null,
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.outline.copy(
                                        alpha = 0.2f
                                    )
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = {
                            showSavedRoutesDialog = false
                        }) {
                            Text(stringResource(R.string.close))
                        }
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
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, tint = AccentBlue, modifier = Modifier.size(22.dp))
    }
}
