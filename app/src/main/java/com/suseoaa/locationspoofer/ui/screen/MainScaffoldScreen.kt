package com.suseoaa.locationspoofer.ui.screen

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddLocationAlt
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.suseoaa.locationspoofer.data.model.AppState
import com.suseoaa.locationspoofer.ui.components.AppMapController
import com.suseoaa.locationspoofer.ui.components.AppMapView
import com.suseoaa.locationspoofer.ui.screen.spoofing.SpoofingIntent
import com.suseoaa.locationspoofer.ui.theme.AccentBlue
import com.suseoaa.locationspoofer.viewmodel.MainViewModel
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurDefaults
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.highlight.Highlight
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur

enum class BottomTab(val label: String, val icon: ImageVector) {
    Location("定位", Icons.Rounded.MyLocation),
    Route("路线", Icons.Rounded.Route),
    Features("功能", Icons.Rounded.Extension),
    Info("信息", Icons.Rounded.Settings)
}

enum class MainSubScreen {
    None,
    CoordinateConfig,
    ScannerMap,
    ManageData
}

@Composable
fun MainScaffoldScreen(
    viewModel: MainViewModel,
    uiState: AppState
) {
    val pageCount = BottomTab.values().size
    var selectedTab by remember { mutableIntStateOf(BottomTab.Location.ordinal) }
    var currentSubScreen by remember { mutableStateOf(MainSubScreen.None) }
    var mapController by remember { mutableStateOf<AppMapController?>(null) }
    val currentSelectedTab by rememberUpdatedState(selectedTab)
    val isDark = isSystemInDarkTheme()
    
    val backdrop: LayerBackdrop? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        rememberLayerBackdrop()
    } else {
        null
    }

    // 针对非首页 Tab（路线/功能/信息）的系统返回拦截：返回到定位首页
    BackHandler(enabled = currentSubScreen == MainSubScreen.None && selectedTab != BottomTab.Location.ordinal) {
        selectedTab = BottomTab.Location.ordinal
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

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                AnimatedVisibility(
                    visible = currentSubScreen == MainSubScreen.None,
                    enter = fadeIn(tween(180)) + expandVertically(tween(180)),
                    exit = fadeOut(tween(140)) + shrinkVertically(tween(140))
                ) {
                    AppBottomNavigationBar(
                        selectedTab = selectedTab,
                        onTabSelected = selectPage,
                        isDark = isDark,
                        backdrop = backdrop
                    )
                }
            }
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
                AnimatedVisibility(
                    visible = selectedTab == 0,
                    modifier = Modifier.align(Alignment.Center)
                ) {
                    Icon(
                        Icons.Rounded.AddLocationAlt,
                        contentDescription = null,
                        tint = AccentBlue.copy(alpha = 0.8f),
                        modifier = Modifier
                            .size(32.dp)
                            .padding(bottom = 16.dp)
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
                BottomTab.Features.ordinal -> com.suseoaa.locationspoofer.ui.screen.tabs.FeaturesTab(
                    viewModel = viewModel,
                    uiState = uiState,
                    tabBarHeight = paddingValues.calculateBottomPadding(),
                    onNavigateToCoordinate = { currentSubScreen = MainSubScreen.CoordinateConfig },
                    onNavigateToScanner = { currentSubScreen = MainSubScreen.ScannerMap },
                    onNavigateToManageData = { currentSubScreen = MainSubScreen.ManageData }
                )
                BottomTab.Info.ordinal -> com.suseoaa.locationspoofer.ui.screen.tabs.InfoTab(
                    viewModel = viewModel,
                    uiState = uiState,
                    tabBarHeight = paddingValues.calculateBottomPadding()
                )
            }
        }

        // 全屏子页面覆盖层（隐藏底部栏，拥有独立的完整全屏视口）
        AnimatedVisibility(
            visible = currentSubScreen != MainSubScreen.None,
            enter = slideInVertically(tween(300)) { it } + fadeIn(tween(200)),
            exit = slideOutVertically(tween(260)) { it } + fadeOut(tween(180))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
            ) {
                when (currentSubScreen) {
                    MainSubScreen.CoordinateConfig -> AppCoordinateScreen(
                        viewModel = viewModel,
                        uiState = uiState,
                        onBack = { currentSubScreen = MainSubScreen.None }
                    )
                    MainSubScreen.ScannerMap -> ScannerMapScreen(
                        viewModel = viewModel,
                        uiState = uiState,
                        isDark = isDark,
                        onClose = { currentSubScreen = MainSubScreen.None }
                    )
                    MainSubScreen.ManageData -> ManageDataScreen(
                        viewModel = viewModel,
                        uiState = uiState,
                        isDark = isDark,
                        onClose = { currentSubScreen = MainSubScreen.None }
                    )
                    MainSubScreen.None -> Unit
                }
            }
        }
    }
}

/**
 * 现代风格胶囊悬浮底部导航栏（图标 + 文字组合）
 */
@Composable
fun AppBottomNavigationBar(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    isDark: Boolean,
    backdrop: LayerBackdrop?
) {
    val navigationShape = remember { RoundedCornerShape(26.dp) }
    
    val glassModifier = if (backdrop != null) {
        val glassColors = BlurDefaults.blurColors(
            blendColors = listOf(
                BlendColorEntry(
                    MaterialTheme.colorScheme.surface.copy(alpha = if (isDark) 0.68f else 0.82f)
                )
            ),
            saturation = 1.1f
        )
        val glassHighlight = if (isDark) {
            Highlight.GlassStrokeMiddleDark
        } else {
            Highlight.GlassStrokeMiddleLight
        }
        Modifier.textureBlur(
            backdrop = backdrop,
            shape = navigationShape,
            blurRadius = 26f,
            colors = glassColors,
            highlight = glassHighlight
        )
    } else {
        Modifier
            .shadow(
                elevation = 10.dp,
                shape = navigationShape,
                spotColor = if (isDark) Color.Black.copy(alpha = 0.5f) else Color.Black.copy(alpha = 0.12f)
            )
            .background(
                color = MaterialTheme.colorScheme.surface.copy(alpha = if (isDark) 0.92f else 0.96f),
                shape = navigationShape
            )
            .border(
                width = 1.dp,
                color = if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f),
                shape = navigationShape
            )
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(68.dp)
                .then(glassModifier),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            BottomTab.values().forEachIndexed { index, tab ->
                val isSelected = selectedTab == index
                
                val indicatorWidth by animateDpAsState(
                    targetValue = if (isSelected) 54.dp else 40.dp,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    ),
                    label = "tab_indicator_width"
                )
                
                val indicatorColor by animateColorAsState(
                    targetValue = if (isSelected) {
                        AccentBlue.copy(alpha = if (isDark) 0.22f else 0.14f)
                    } else {
                        Color.Transparent
                    },
                    animationSpec = tween(220),
                    label = "tab_indicator_color"
                )

                val itemColor by animateColorAsState(
                    targetValue = if (isSelected) {
                        AccentBlue
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = if (isDark) 0.55f else 0.60f)
                    },
                    animationSpec = tween(220),
                    label = "tab_item_color"
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(20.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            onTabSelected(index)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(vertical = 4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .width(indicatorWidth)
                                .height(30.dp)
                                .clip(RoundedCornerShape(15.dp))
                                .background(indicatorColor),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = tab.label,
                                tint = itemColor,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = tab.label,
                            fontSize = 11.5.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = itemColor,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}
