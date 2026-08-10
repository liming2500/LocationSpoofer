package com.suseoaa.locationspoofer.ui.screen

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import com.suseoaa.locationspoofer.data.model.AppState
import com.suseoaa.locationspoofer.ui.components.AppMapController
import com.suseoaa.locationspoofer.ui.components.AppMapView
import com.suseoaa.locationspoofer.viewmodel.MainViewModel
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddLocationAlt
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import com.suseoaa.locationspoofer.ui.screen.spoofing.SpoofingIntent
import top.yukonga.miuix.kmp.basic.FloatingNavigationBar
import top.yukonga.miuix.kmp.basic.FloatingNavigationBarItem
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurDefaults
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.blur.highlight.Highlight

enum class BottomTab(val label: String, val icon: ImageVector) {
    Location("定位", Icons.Rounded.MyLocation),
    Route("路线", Icons.Rounded.Route),
    Features("功能", Icons.Rounded.Extension),
    Info("信息", Icons.Rounded.Settings)
}

@Composable
fun MainScaffoldScreen(
    viewModel: MainViewModel,
    uiState: AppState
) {
    val pageCount = BottomTab.values().size
    var selectedTab by remember { mutableIntStateOf(BottomTab.Location.ordinal) }
    var mapController by remember { mutableStateOf<AppMapController?>(null) }
    val currentSelectedTab by rememberUpdatedState(selectedTab)
    val isDark = isSystemInDarkTheme()
    val navigationShape = remember { RoundedCornerShape(28.dp) }
    val backdrop: LayerBackdrop? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        rememberLayerBackdrop()
    } else {
        null
    }
    val glassModifier = if (backdrop != null) {
        val glassColors = BlurDefaults.blurColors(
            blendColors = listOf(
                BlendColorEntry(
                    MaterialTheme.colorScheme.surface.copy(alpha = if (isDark) 0.62f else 0.74f)
                )
            ),
            saturation = 1.08f
        )
        val glassHighlight = if (isDark) {
            Highlight.GlassStrokeMiddleDark
        } else {
            Highlight.GlassStrokeMiddleLight
        }
        Modifier.textureBlur(
            backdrop = backdrop,
            shape = navigationShape,
            blurRadius = 28f,
            colors = glassColors,
            highlight = glassHighlight
        )
    } else {
        Modifier
    }

    LaunchedEffect(selectedTab, mapController, uiState.mapType) {
        if (selectedTab == 0) {
            mapController?.clear()
            mapController?.setMapType(uiState.mapType)
        }
    }

    val selectPage: (Int) -> Unit = { index ->
        selectedTab = index.coerceIn(0, pageCount - 1)
    }
    
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            FloatingNavigationBar(
                modifier = glassModifier,
                color = Color.Transparent,
                cornerRadius = 28.dp,
                horizontalOutSidePadding = 24.dp,
                shadowElevation = 0.dp
            ) {
                BottomTab.values().forEachIndexed { index, tab ->
                    FloatingNavigationBarItem(
                        selected = selectedTab == index,
                        onClick = { selectPage(index) },
                        icon = tab.icon,
                        label = tab.label
                    )
                }
            }
        },
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (backdrop != null) {
                        Modifier.layerBackdrop(backdrop)
                    } else {
                        Modifier
                    }
                )
        ) {
            AppMapView(
                modifier = Modifier.fillMaxSize(),
                mapEngine = uiState.mapEngine,
                isDomestic = viewModel.isDomesticEnvironment(),
                onMapReady = { controller -> 
                    mapController = controller
                    controller.disableUiControls()
                    controller.setMapType(uiState.mapType)
                    val initLat = uiState.latitudeInput.toDoubleOrNull() ?: 39.9042
                    val initLng = uiState.longitudeInput.toDoubleOrNull() ?: 116.4074
                    controller.moveCamera(initLat, initLng, 15f)

                    controller.setOnCameraChangeListener { lat, lng ->
                        if (currentSelectedTab == BottomTab.Location.ordinal) {
                            viewModel.handleSpoofingIntent(SpoofingIntent.ConfirmMapPoint(lat, lng))
                        }
                    }
                    controller.setOnCameraMoveListener { lat, lng ->
                        if (currentSelectedTab == BottomTab.Location.ordinal) {
                            viewModel.handleSpoofingIntent(SpoofingIntent.MapPointMoved(lat, lng))
                        }
                    }
                }
            )

            // Crosshair for Location Selection (Only show on Location Tab)
            androidx.compose.animation.AnimatedVisibility(
                visible = selectedTab == 0,
                modifier = Modifier.align(Alignment.Center)
            ) {
                androidx.compose.material3.Icon(
                    Icons.Rounded.AddLocationAlt,
                    contentDescription = null,
                    tint = com.suseoaa.locationspoofer.ui.theme.AccentBlue.copy(alpha = 0.8f),
                    modifier = Modifier
                        .size(32.dp)
                        .padding(bottom = 16.dp) // Shifted up slightly to point at the exact center
                )
            }
        }
        
        when (selectedTab) {
            BottomTab.Location.ordinal -> com.suseoaa.locationspoofer.ui.screen.tabs.LocationTab(
                viewModel = viewModel,
                uiState = uiState,
                mapController = mapController,
                tabBarHeight = paddingValues.calculateBottomPadding()
            )
            BottomTab.Route.ordinal -> com.suseoaa.locationspoofer.ui.screen.tabs.RouteTab(
                viewModel = viewModel,
                uiState = uiState,
                mapController = mapController,
                isActive = true,
                bottomBarHeight = paddingValues.calculateBottomPadding()
            )
            BottomTab.Features.ordinal -> com.suseoaa.locationspoofer.ui.screen.tabs.FeaturesTab(viewModel, uiState)
            BottomTab.Info.ordinal -> com.suseoaa.locationspoofer.ui.screen.tabs.InfoTab(viewModel, uiState)
        }
    }
}
