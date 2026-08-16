package com.suseoaa.locationspoofer.ui.screen.tabs

import android.annotation.SuppressLint
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.suseoaa.locationspoofer.R
import com.suseoaa.locationspoofer.data.model.*
import com.suseoaa.locationspoofer.ui.components.MapTypeDialog
import com.suseoaa.locationspoofer.ui.components.AppMapController
import com.suseoaa.locationspoofer.ui.components.AppMapMarker
import com.suseoaa.locationspoofer.ui.components.MarkerType
import com.suseoaa.locationspoofer.ui.screen.*
import com.suseoaa.locationspoofer.ui.theme.AccentBlue
import com.suseoaa.locationspoofer.ui.theme.AccentOrange
import com.suseoaa.locationspoofer.viewmodel.MainViewModel
import kotlinx.coroutines.Dispatchers
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
    var showSavedLocations by remember { mutableStateOf(false) }
    var showMapTypeDialog by remember { mutableStateOf(false) }
    var showConfigDialog by remember { mutableStateOf(false) }
    var showSaveRouteDialog by remember { mutableStateOf(false) }
    var showSavedRoutesDialog by remember { mutableStateOf(false) }
    val isDomestic = viewModel.isDomesticEnvironment()
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<AppPoiItem>>(emptyList()) }
    var showSearchResults by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    val stage = uiState.routePlanStage
    val isRunning = stage == RoutePlanStage.RUNNING
    val isManual = uiState.routeRunMode == RouteRunMode.MANUAL
    val routePoints = uiState.routePoints

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

    Box(modifier = Modifier.fillMaxSize()) {
        if (stage == RoutePlanStage.SELECTING || stage == RoutePlanStage.IDLE) {
            Icon(
                Icons.Rounded.AddLocationAlt, null,
                tint = AccentBlue.copy(alpha = 0.8f),
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

        Column(modifier = Modifier.align(Alignment.TopCenter)) {

            if (stage == RoutePlanStage.SELECTING || stage == RoutePlanStage.IDLE) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
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
                    Spacer(Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .shadow(4.dp, RoundedCornerShape(24.dp))
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.95f), RoundedCornerShape(24.dp))
                            .clickable { showSavedRoutesDialog = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Rounded.Bookmarks,
                            null,
                            tint = AccentBlue,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            AnimatedVisibility(visible = showSearchResults && searchResults.isNotEmpty()) {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .heightIn(max = 300.dp)
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
                canUndo = stage == RoutePlanStage.SELECTING && routePoints.isNotEmpty(),
                onUndo = { viewModel.undoLastRoutePoint() }
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 12.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f))
                    .clickable { viewModel.fetchCurrentLocation(context) { _, _ -> } },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Rounded.MyLocation,
                    null,
                    tint = AccentBlue,
                    modifier = Modifier.size(22.dp)
                )
            }
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f))
                    .clickable { showMapTypeDialog = true },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Rounded.Layers,
                    null,
                    tint = AccentBlue,
                    modifier = Modifier.size(22.dp)
                )
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

        if (stage == RoutePlanStage.IDLE) {
            Button(
                onClick = {
                    val tLat = mapController?.cameraTargetLat
                    val tLng = mapController?.cameraTargetLng
                    if (tLat != null && tLng != null) {
                        viewModel.confirmMapPoint(tLat, tLng)
                        Toast.makeText(context, context.getString(R.string.coordinate_selected), Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = bottomBarHeight + 12.dp)
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)
            ) {
                Icon(Icons.Rounded.CheckCircle, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.confirm_point), fontWeight = FontWeight.Bold)
            }
        } else {
            BottomActionBar(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = bottomBarHeight + 12.dp),
                stage = stage,
                routePoints = routePoints,
                onConfirmPoint = {
                    val tLat = mapController?.cameraTargetLat
                    val tLng = mapController?.cameraTargetLng
                    if (tLat != null && tLng != null) {
                        viewModel.addRoutePoint(tLat, tLng)
                    }
                },
                onFinishSelecting = { viewModel.finishSelectingPoints() },
                onRestartSelecting = { viewModel.restartSelectingPoints() },
                onSaveRoute = { showSaveRouteDialog = true },
                onStartPlanning = { showConfigDialog = true },
                onStopRoute = { viewModel.stopRoutePlanning() }
            )
        }
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
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    modifier = Modifier.padding(24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    androidx.compose.material3.CircularProgressIndicator(modifier = Modifier.size(24.dp))
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
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text("收藏当前路线", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(16.dp))
                    androidx.compose.material3.OutlinedTextField(
                        value = routeName,
                        onValueChange = { routeName = it },
                        label = { Text(stringResource(R.string.route_name)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(24.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        androidx.compose.material3.TextButton(onClick = { showSaveRouteDialog = false }) {
                            Text(stringResource(R.string.cancel))
                        }
                        Spacer(Modifier.width(8.dp))
                        Button(onClick = {
                            if (routeName.isNotBlank()) {
                                viewModel.saveRoute(routeName, routePoints)
                                Toast.makeText(context, context.getString(R.string.save_success), Toast.LENGTH_SHORT).show()
                                showSaveRouteDialog = false
                            }
                        }) {
                            Text(stringResource(R.string.save))
                        }
                    }
                }
            }
        }
    }

    if (showSavedRoutesDialog) {
        Dialog(onDismissRequest = { showSavedRoutesDialog = false }) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 500.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
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
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                stringResource(R.string.no_saved_routes),
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                            )
                        }
                    } else {
                        LazyColumn(modifier = Modifier.weight(1f)) {
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
                        androidx.compose.material3.TextButton(onClick = {
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
