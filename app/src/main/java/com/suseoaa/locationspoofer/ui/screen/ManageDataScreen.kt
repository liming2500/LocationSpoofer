package com.suseoaa.locationspoofer.ui.screen

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.suseoaa.locationspoofer.R
import com.suseoaa.locationspoofer.data.db.CompleteLocation
import com.suseoaa.locationspoofer.ui.components.AppMapController
import com.suseoaa.locationspoofer.ui.components.AppMapView
import com.suseoaa.locationspoofer.ui.theme.AccentBlue
import com.suseoaa.locationspoofer.ui.theme.AccentGreen
import com.suseoaa.locationspoofer.ui.theme.AccentOrange
import com.suseoaa.locationspoofer.ui.theme.AppColors
import com.suseoaa.locationspoofer.ui.theme.noRippleClickable
import com.suseoaa.locationspoofer.utils.MapCoverageHelper
import com.suseoaa.locationspoofer.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Card as MiuixCard

@Composable
fun ManageDataScreen(
    viewModel: MainViewModel,
    uiState: com.suseoaa.locationspoofer.data.model.AppState,
    isDark: Boolean,
    onClose: () -> Unit
) {
    var mapController by remember { mutableStateOf<AppMapController?>(null) }
    var editingItem by remember { mutableStateOf<CompleteLocation?>(null) }
    var itemToDelete by remember { mutableStateOf<CompleteLocation?>(null) }

    val dataList = uiState.manageDataList

    BackHandler(onBack = onClose)

    LaunchedEffect(mapController, uiState.mapType) {
        mapController?.setMapType(uiState.mapType)
    }

    LaunchedEffect(mapController, dataList) {
        val controller = mapController ?: return@LaunchedEffect
        controller.clear()
        val locations = dataList.map { it.location }
        val last = locations.lastOrNull()
        MapCoverageHelper.drawCoverage(controller, locations, last?.lat, last?.lng)
        if (last != null) {
            controller.moveCamera(last.lat, last.lng, 15f)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.background(isDark))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // 顶部导航栏（现代化独立圆形返回按键与标题，无右侧杂乱按钮）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 独立立体圆形返回按键
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .shadow(elevation = 6.dp, shape = CircleShape, clip = false)
                        .clip(CircleShape)
                        .background(if (isDark) Color(0xFF22272E) else Color.White)
                        .border(
                            width = 1.dp,
                            color = if (isDark) Color.White.copy(alpha = 0.14f) else Color(
                                0xFFE5E8EC
                            ),
                            shape = CircleShape
                        )
                        .noRippleClickable(onClick = onClose),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = stringResource(R.string.back),
                        tint = if (isDark) Color.White else Color(0xFF1A1D20),
                        modifier = Modifier.size(21.dp)
                    )
                }

                Spacer(Modifier.width(14.dp))

                Column {
                    Text(
                        text = stringResource(R.string.title_manage_data),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = stringResource(R.string.total_collected_data_count, dataList.size),
                        fontSize = 12.5.sp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f)
                    )
                }
            }

            if (uiState.manageDataIsLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = AccentBlue)
                }
            } else if (dataList.isEmpty()) {
                // 空数据状态质感呈现
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(RoundedCornerShape(22.dp))
                                .background(
                                    if (isDark) Color.White.copy(alpha = 0.05f) else Color.Black.copy(
                                        alpha = 0.03f
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Rounded.Storage,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                                modifier = Modifier.size(36.dp)
                            )
                        }
                        Text(
                            text = stringResource(R.string.no_data_collected),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = stringResource(R.string.manage_data_empty_desc),
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                        )
                    }
                }
            } else {
                // 地图概览卡片
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.36f)
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .border(
                            0.8.dp,
                            if (isDark) Color.White.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.06f),
                            RoundedCornerShape(18.dp)
                        )
                ) {
                    AppMapView(
                        mapEngine = uiState.mapEngine,
                        isDomestic = viewModel.isDomesticEnvironment(),
                        modifier = Modifier.fillMaxSize(),
                        onMapReady = { controller ->
                            mapController = controller
                            controller.disableUiControls()
                        }
                    )

                    // 地图右上角覆盖范围提示胶囊
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(10.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.90f))
                            .border(
                                0.5.dp,
                                MaterialTheme.colorScheme.outline.copy(alpha = 0.1f),
                                RoundedCornerShape(10.dp)
                            )
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(AccentGreen)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.points_drawn_count, dataList.size),
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                // 下部数据卡片列表（向左滑动显露编辑与删除操作，顶部平滑溶解边界）
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.64f)
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = 8.dp,
                            bottom = 24.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(dataList, key = { it.location.id }) { item ->
                            SwipeableDataListItem(
                                item = item,
                                isDark = isDark,
                                onClick = {
                                    viewModel.updateLatitude(
                                        String.format(
                                            Locale.US,
                                            "%.6f",
                                            item.location.lat
                                        )
                                    )
                                    viewModel.updateLongitude(
                                        String.format(
                                            Locale.US,
                                            "%.6f",
                                            item.location.lng
                                        )
                                    )
                                    mapController?.animateCamera(
                                        item.location.lat,
                                        item.location.lng,
                                        17f
                                    )
                                },
                                onEdit = { editingItem = item },
                                onDelete = { itemToDelete = item }
                            )
                        }
                    }

                    // 顶部边界模糊渐变过渡（消除生硬切边）
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(18.dp)
                            .align(Alignment.TopCenter)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        AppColors.background(isDark),
                                        AppColors.background(isDark).copy(alpha = 0.85f),
                                        AppColors.background(isDark).copy(alpha = 0.40f),
                                        Color.Transparent
                                    )
                                )
                            )
                    )
                }
            }
        }
    }

    // 现代化编辑数据弹窗
    if (editingItem != null) {
        val currentItem = dataList.find { it.location.id == editingItem?.location?.id } ?: editingItem!!
        ModernEditDataDialog(
            item = currentItem,
            isDark = isDark,
            onDismiss = { editingItem = null },
            onSave = { placeName, remark, selectedWifiBssid ->
                viewModel.updateManageDataMetadata(
                    currentItem.location.id,
                    placeName,
                    remark,
                    selectedWifiBssid,
                    currentItem.location.selectedBluetoothAddress,
                    currentItem.location.selectedCellKey
                )
                editingItem = null
            },
            onSaveWifi = { bssid, ssid, frequency, level, capabilities, vendor, isConnected, isDesignated ->
                viewModel.saveOrUpdateLocationWifi(
                    locationId = currentItem.location.id,
                    bssid = bssid,
                    ssid = ssid,
                    frequency = frequency,
                    level = level,
                    capabilities = capabilities,
                    vendor = vendor,
                    isConnected = isConnected,
                    isDesignatedSimulation = isDesignated
                )
            },
            onDeleteWifi = { bssid ->
                viewModel.deleteLocationWifi(
                    locationId = currentItem.location.id,
                    bssid = bssid
                )
            }
        )
    }

    // 删除单项采集数据二次确认弹窗
    if (itemToDelete != null) {
        val targetItem = itemToDelete!!
        val displayName = when {
            targetItem.location.placeName.isNotBlank() -> targetItem.location.placeName
            targetItem.location.remark.isNotBlank() -> targetItem.location.remark
            else -> "坐标 (${
                String.format(
                    Locale.US,
                    "%.4f",
                    targetItem.location.lat
                )
            }, ${String.format(Locale.US, "%.4f", targetItem.location.lng)})"
        }

        Dialog(
            onDismissRequest = { itemToDelete = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            MiuixCard(
                modifier = Modifier
                    .fillMaxWidth(0.88f)
                    .wrapContentHeight(),
                cornerRadius = 24.dp,
                insideMargin = PaddingValues(22.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(13.dp))
                                .background(Color(0xFFE53935).copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Rounded.DeleteOutline,
                                contentDescription = null,
                                tint = Color(0xFFE53935),
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Text(
                            text = stringResource(R.string.delete_data_title),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Text(
                        text = stringResource(R.string.delete_data_confirm_format, displayName),
                        fontSize = 13.5.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        lineHeight = 20.sp
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(42.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(
                                        alpha = 0.05f
                                    )
                                )
                                .noRippleClickable { itemToDelete = null },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(R.string.cancel),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
                            )
                        }

                        Button(
                            onClick = {
                                viewModel.deleteManageDataSingle(targetItem.location.id)
                                itemToDelete = null
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1.2f)
                                .height(42.dp)
                        ) {
                            Text(
                                stringResource(R.string.delete),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 具有细腻物理阻尼手感与弹簧回弹的向左滑动列表项
 */
@Composable
private fun SwipeableDataListItem(
    item: CompleteLocation,
    isDark: Boolean,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()

    // 显露操作区域总宽度（编辑 60dp + 删除 60dp + 间距）
    val maxRevealWidthDp = 136.dp
    val maxRevealWidthPx = with(density) { maxRevealWidthDp.toPx() }

    val offsetX = remember { Animatable(0f) }

    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }
    val timeStr =
        remember(item.location.timestamp) { dateFormat.format(Date(item.location.timestamp)) }

    val wifiCount = (if (item.connectedWifi != null) 1 else 0) + item.wifis.size
    val cellCount = item.cells.size
    val btCount = item.bluetooths.size

    val hasPlaceName = item.location.placeName.isNotBlank()
    val hasRemark = item.location.remark.isNotBlank()

    val defaultRecordTitle = stringResource(R.string.coord_record_title)
    // 优先显示地名或备注为卡片的主标题
    val primaryTitle = when {
        hasPlaceName -> item.location.placeName
        hasRemark -> item.location.remark
        else -> defaultRecordTitle
    }

    val subtitle = when {
        hasPlaceName && hasRemark -> item.location.remark
        else -> null
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
    ) {
        // 底层操作按键区（向左滑动时显露）
        Row(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .padding(vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 编辑按钮
            Box(
                modifier = Modifier
                    .width(60.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(16.dp))
                    .background(AccentBlue)
                    .noRippleClickable {
                        coroutineScope.launch {
                            offsetX.animateTo(
                                0f,
                                spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = 500f
                                )
                            )
                        }
                        onEdit()
                    },
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Icon(
                        Icons.Rounded.Edit,
                        contentDescription = stringResource(R.string.edit),
                        tint = Color.White,
                        modifier = Modifier.size(19.dp)
                    )
                    Text(
                        text = stringResource(R.string.edit),
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            // 删除按钮
            Box(
                modifier = Modifier
                    .width(60.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFFE53935))
                    .noRippleClickable {
                        coroutineScope.launch {
                            offsetX.animateTo(
                                0f,
                                spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = 500f
                                )
                            )
                        }
                        onDelete()
                    },
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Icon(
                        Icons.Rounded.DeleteOutline,
                        contentDescription = stringResource(R.string.delete),
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = stringResource(R.string.delete),
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }

        // 上层滑动卡片（带拖拽手势与非线性阻尼）
        Box(
            modifier = Modifier
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .fillMaxWidth()
        ) {
            MiuixCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .noRippleClickable {
                        if (offsetX.value < -10f) {
                            coroutineScope.launch {
                                offsetX.animateTo(
                                    0f,
                                    spring(dampingRatio = 0.8f, stiffness = 450f)
                                )
                            }
                        } else {
                            onClick()
                        }
                    },
                cornerRadius = 16.dp,
                insideMargin = PaddingValues(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // 主标题与时间
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = primaryTitle,
                                fontSize = 15.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f, fill = false)
                            )

                            Text(
                                text = timeStr,
                                fontSize = 11.5.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                            )
                        }

                        // 备注副标题
                        if (subtitle != null) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    Icons.Rounded.Description,
                                    contentDescription = null,
                                    tint = AccentBlue,
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = subtitle,
                                    fontSize = 12.5.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                )
                            }
                        }

                        // 经纬度坐标标签
                        Text(
                            text = "${
                                String.format(
                                    Locale.US,
                                    "%.5f",
                                    item.location.lat
                                )
                            }, ${String.format(Locale.US, "%.5f", item.location.lng)}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                        )

                        // 信号设备标签组（Wi-Fi、基站、蓝牙）
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SignalChip(
                                icon = Icons.Rounded.Wifi,
                                text = "$wifiCount",
                                tint = AccentBlue,
                                isDark = isDark
                            )
                            SignalChip(
                                icon = Icons.Rounded.CellTower,
                                text = "$cellCount",
                                tint = AccentOrange,
                                isDark = isDark
                            )
                            SignalChip(
                                icon = Icons.Rounded.Bluetooth,
                                text = "$btCount",
                                tint = AccentGreen,
                                isDark = isDark
                            )
                        }
                    }

                    // 右侧指示可左滑的尖头小图标（随着滑动展开自然淡出）
                    val chevronAlpha =
                        ((1f - abs(offsetX.value) / maxRevealWidthPx) * 0.4f).coerceIn(0f, 0.4f)
                    Icon(
                        imageVector = Icons.Rounded.ChevronLeft,
                        contentDescription = stringResource(R.string.slide_left_hint),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = chevronAlpha),
                        modifier = Modifier
                            .padding(start = 6.dp)
                            .size(18.dp)
                    )
                }
            }

            // 右侧 1/3 区域专用滑动手势触发层（左侧区域保留正常点击与纵向顺畅滚动）
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxWidth(0.35f)
                    .fillMaxHeight()
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                coroutineScope.launch {
                                    val target = if (offsetX.value < -maxRevealWidthPx * 0.45f) {
                                        -maxRevealWidthPx
                                    } else {
                                        0f
                                    }
                                    offsetX.animateTo(
                                        targetValue = target,
                                        animationSpec = spring(
                                            dampingRatio = 0.8f,
                                            stiffness = 450f
                                        )
                                    )
                                }
                            },
                            onHorizontalDrag = { _, dragAmount ->
                                coroutineScope.launch {
                                    val current = offsetX.value
                                    val newOffset = if (dragAmount < 0) {
                                        // 向左滑动
                                        if (current < -maxRevealWidthPx) {
                                            // 超过最大显露宽度时应用弹性阻尼
                                            val overDrag = abs(current) - maxRevealWidthPx
                                            val dampingFactor = 1f / (1f + overDrag / 60f)
                                            current + dragAmount * dampingFactor
                                        } else {
                                            current + dragAmount
                                        }
                                    } else {
                                        // 向右滑动
                                        if (current > 0) {
                                            // 右侧超出边界重阻尼
                                            current + dragAmount * 0.15f
                                        } else {
                                            current + dragAmount
                                        }
                                    }
                                    offsetX.snapTo(newOffset.coerceAtLeast(-maxRevealWidthPx * 1.35f))
                                }
                            }
                        )
                    }
            )
        }
    }
}

@Composable
private fun SignalChip(
    icon: ImageVector,
    text: String,
    tint: Color,
    isDark: Boolean
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(tint.copy(alpha = if (isDark) 0.12f else 0.08f))
            .padding(horizontal = 7.dp, vertical = 2.5.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(12.dp),
                tint = tint
            )
            Spacer(modifier = Modifier.width(3.5.dp))
            Text(
                text = text,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = tint
            )
        }
    }
}

data class EditableWifiItem(
    val bssid: String,
    val ssid: String,
    val frequency: Int,
    val level: Int,
    val capabilities: String,
    val vendor: String,
    val isConnected: Boolean,
    val isDesignated: Boolean
)

@Composable
private fun ModernEditDataDialog(
    item: CompleteLocation,
    isDark: Boolean,
    onDismiss: () -> Unit,
    onSave: (placeName: String, remark: String, selectedWifiBssid: String?) -> Unit,
    onSaveWifi: (bssid: String, ssid: String, frequency: Int, level: Int, capabilities: String, vendor: String, isConnected: Boolean, isDesignated: Boolean) -> Unit,
    onDeleteWifi: (bssid: String) -> Unit
) {
    var placeName by remember(item.location.id) { mutableStateOf(item.location.placeName) }
    var remark by remember(item.location.id) { mutableStateOf(item.location.remark) }
    var selectedWifiBssid by remember(item.location.id, item.location.selectedWifiBssid) {
        mutableStateOf(item.location.selectedWifiBssid)
    }

    var wifiBeingEdited by remember { mutableStateOf<EditableWifiItem?>(null) }
    var isNewWifiDialog by remember { mutableStateOf(false) }
    var wifiToDelete by remember { mutableStateOf<String?>(null) }

    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }
    val timeStr =
        remember(item.location.timestamp) { dateFormat.format(Date(item.location.timestamp)) }

    val wifiList = remember(item, selectedWifiBssid) {
        val list = mutableListOf<EditableWifiItem>()
        val seenBssids = mutableSetOf<String>()

        item.connectedWifi?.let { cw ->
            seenBssids.add(cw.bssid.uppercase())
            list.add(
                EditableWifiItem(
                    bssid = cw.bssid,
                    ssid = cw.ssid,
                    frequency = cw.frequency,
                    level = cw.level,
                    capabilities = cw.capabilities,
                    vendor = cw.vendor,
                    isConnected = true,
                    isDesignated = selectedWifiBssid?.equals(cw.bssid, ignoreCase = true) == true
                )
            )
        }

        item.wifis.forEach { lw ->
            val bssid = lw.device.bssid
            if (!seenBssids.contains(bssid.uppercase())) {
                seenBssids.add(bssid.uppercase())
                list.add(
                    EditableWifiItem(
                        bssid = bssid,
                        ssid = lw.device.ssid,
                        frequency = lw.device.frequency,
                        level = lw.locationWifi.level,
                        capabilities = lw.device.capabilities,
                        vendor = lw.device.vendor,
                        isConnected = false,
                        isDesignated = selectedWifiBssid?.equals(bssid, ignoreCase = true) == true
                    )
                )
            }
        }
        list
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        MiuixCard(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.88f),
            cornerRadius = 24.dp,
            insideMargin = PaddingValues(20.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // 顶部标题栏
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(13.dp))
                            .background(AccentBlue.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Rounded.EditLocation,
                            contentDescription = null,
                            tint = AccentBlue,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Spacer(Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.edit_location_data),
                            fontSize = 17.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = stringResource(R.string.collection_time_format, timeStr),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(
                                if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(
                                    alpha = 0.05f
                                )
                            )
                            .noRippleClickable(onClick = onDismiss),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Rounded.Close,
                            contentDescription = stringResource(R.string.close),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                // 可滚动内容区域
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // 经纬度及信号预览条
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (isDark) Color.White.copy(alpha = 0.05f) else Color.Black.copy(
                                    alpha = 0.03f
                                )
                            )
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Rounded.MyLocation,
                                    contentDescription = null,
                                    tint = AccentBlue,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = "${
                                        String.format(
                                            Locale.US,
                                            "%.5f",
                                            item.location.lat
                                        )
                                    }, ${String.format(Locale.US, "%.5f", item.location.lng)}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                                )
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                val totalWifi = (if (item.connectedWifi != null) 1 else 0) + item.wifis.size
                                Text(
                                    text = "$totalWifi Wi-Fi",
                                    fontSize = 11.sp,
                                    color = AccentBlue,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "•",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                                )
                                Text(
                                    text = stringResource(R.string.cells_count_badge, item.cells.size),
                                    fontSize = 11.sp,
                                    color = AccentOrange,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }

                    // 表单输入容器（地名与备注）
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                if (isDark) Color.White.copy(alpha = 0.04f) else Color.Black.copy(
                                    alpha = 0.03f
                                )
                            )
                            .border(
                                0.8.dp,
                                MaterialTheme.colorScheme.outline.copy(alpha = if (isDark) 0.12f else 0.06f),
                                RoundedCornerShape(16.dp)
                            )
                    ) {
                        // 位置名称输入
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Rounded.Place,
                                contentDescription = null,
                                tint = AccentBlue,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            BasicTextField(
                                value = placeName,
                                onValueChange = { placeName = it },
                                textStyle = TextStyle(
                                    fontSize = 14.5.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.Medium
                                ),
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                decorationBox = { innerTextField ->
                                    if (placeName.isEmpty()) {
                                        Text(
                                            text = stringResource(R.string.set_place_name_hint),
                                            fontSize = 13.5.sp,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                        )
                                    }
                                    innerTextField()
                                }
                            )
                            if (placeName.isNotEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                                        .noRippleClickable { placeName = "" },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Rounded.Close,
                                        null,
                                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = if (isDark) 0.10f else 0.05f))

                        // 备注说明输入
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                Icons.Rounded.Description,
                                contentDescription = null,
                                tint = AccentOrange,
                                modifier = Modifier
                                    .padding(top = 2.dp)
                                    .size(18.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            BasicTextField(
                                value = remark,
                                onValueChange = { remark = it },
                                textStyle = TextStyle(
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.Normal
                                ),
                                minLines = 2,
                                maxLines = 3,
                                modifier = Modifier.weight(1f),
                                decorationBox = { innerTextField ->
                                    if (remark.isEmpty()) {
                                        Text(
                                            text = stringResource(R.string.add_remark_hint),
                                            fontSize = 13.5.sp,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                        )
                                    }
                                    innerTextField()
                                }
                            )
                            if (remark.isNotEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                                        .noRippleClickable { remark = "" },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Rounded.Close,
                                        null,
                                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                            }
                        }
                    }

                    // 模拟 Wi-Fi 选项与数据库管理模块
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                if (isDark) Color.White.copy(alpha = 0.04f) else Color.Black.copy(
                                    alpha = 0.03f
                                )
                            )
                            .border(
                                0.8.dp,
                                MaterialTheme.colorScheme.outline.copy(alpha = if (isDark) 0.12f else 0.06f),
                                RoundedCornerShape(16.dp)
                            )
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Wi-Fi 标题栏与“+ 添加 Wi-Fi”按钮
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Rounded.Wifi,
                                    contentDescription = null,
                                    tint = AccentBlue,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = stringResource(R.string.manage_wifi_list_title),
                                    fontSize = 14.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            // 添加 Wi-Fi 按钮
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(AccentBlue.copy(alpha = 0.12f))
                                    .noRippleClickable {
                                        isNewWifiDialog = true
                                        wifiBeingEdited = null
                                    }
                                    .padding(horizontal = 10.dp, vertical = 5.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Rounded.Add,
                                        contentDescription = null,
                                        tint = AccentBlue,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(Modifier.width(3.dp))
                                    Text(
                                        text = stringResource(R.string.add_wifi_btn),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = AccentBlue
                                    )
                                }
                            }
                        }

                        Text(
                            text = stringResource(R.string.designated_wifi_tip),
                            fontSize = 11.5.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.48f),
                            lineHeight = 16.sp
                        )

                        // “自动默认”单选项
                        val isAutoSelected = selectedWifiBssid == null
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (isAutoSelected) AccentBlue.copy(alpha = 0.08f)
                                    else Color.Transparent
                                )
                                .border(
                                    0.8.dp,
                                    if (isAutoSelected) AccentBlue.copy(alpha = 0.35f) else Color.Transparent,
                                    RoundedCornerShape(10.dp)
                                )
                                .noRippleClickable { selectedWifiBssid = null }
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (isAutoSelected) Icons.Rounded.RadioButtonChecked else Icons.Rounded.RadioButtonUnchecked,
                                contentDescription = null,
                                tint = if (isAutoSelected) AccentBlue else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                modifier = Modifier.size(17.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.no_designated_wifi),
                                fontSize = 13.sp,
                                fontWeight = if (isAutoSelected) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (isAutoSelected) AccentBlue else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                            )
                        }

                        if (wifiList.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = stringResource(R.string.no_wifi_data_in_record),
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                )
                            }
                        } else {
                            // Wi-Fi 列表项
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                wifiList.forEach { wifi ->
                                    val isSelected = selectedWifiBssid?.equals(wifi.bssid, ignoreCase = true) == true

                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(
                                                if (isSelected) AccentBlue.copy(alpha = 0.09f)
                                                else if (isDark) Color.White.copy(alpha = 0.03f) else Color.Black.copy(alpha = 0.02f)
                                            )
                                            .border(
                                                0.8.dp,
                                                if (isSelected) AccentBlue.copy(alpha = 0.4f)
                                                else MaterialTheme.colorScheme.outline.copy(alpha = if (isDark) 0.08f else 0.04f),
                                                RoundedCornerShape(12.dp)
                                            )
                                            .noRippleClickable {
                                                selectedWifiBssid = wifi.bssid
                                            }
                                            .padding(horizontal = 10.dp, vertical = 9.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            // Radio 按钮
                                            Icon(
                                                imageVector = if (isSelected) Icons.Rounded.RadioButtonChecked else Icons.Rounded.RadioButtonUnchecked,
                                                contentDescription = null,
                                                tint = if (isSelected) AccentBlue else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                                modifier = Modifier.size(17.dp)
                                            )

                                            Spacer(Modifier.width(8.dp))

                                            Column(modifier = Modifier.weight(1f)) {
                                                // SSID + 标签行
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    Text(
                                                        text = wifi.ssid.ifBlank { "<Hidden SSID>" },
                                                        fontSize = 13.5.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.onSurface
                                                    )

                                                    if (isSelected) {
                                                        Box(
                                                            modifier = Modifier
                                                                .clip(RoundedCornerShape(4.dp))
                                                                .background(AccentBlue.copy(alpha = 0.15f))
                                                                .padding(horizontal = 5.dp, vertical = 1.dp)
                                                        ) {
                                                            Text(
                                                                text = stringResource(R.string.designated_simulation_badge),
                                                                fontSize = 9.5.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                color = AccentBlue
                                                            )
                                                        }
                                                    }

                                                    if (wifi.isConnected) {
                                                        Box(
                                                            modifier = Modifier
                                                                .clip(RoundedCornerShape(4.dp))
                                                                .background(AccentGreen.copy(alpha = 0.15f))
                                                                .padding(horizontal = 5.dp, vertical = 1.dp)
                                                        ) {
                                                            Text(
                                                                text = stringResource(R.string.connected_wifi_badge),
                                                                fontSize = 9.5.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                color = AccentGreen
                                                            )
                                                        }
                                                    }
                                                }

                                                Spacer(Modifier.height(3.dp))

                                                // BSSID + RSSI + 频率
                                                Text(
                                                    text = "${wifi.bssid}  •  ${wifi.level} dBm  •  ${wifi.frequency} MHz",
                                                    fontSize = 11.5.sp,
                                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                                                )

                                                if (wifi.capabilities.isNotBlank()) {
                                                    Text(
                                                        text = wifi.capabilities,
                                                        fontSize = 10.sp,
                                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                                                        maxLines = 1
                                                    )
                                                }
                                            }

                                            // 编辑按钮
                                            Box(
                                                modifier = Modifier
                                                    .size(28.dp)
                                                    .clip(CircleShape)
                                                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
                                                    .noRippleClickable {
                                                        wifiBeingEdited = wifi
                                                        isNewWifiDialog = false
                                                    },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    Icons.Rounded.Edit,
                                                    contentDescription = null,
                                                    tint = AccentBlue,
                                                    modifier = Modifier.size(13.dp)
                                                )
                                            }

                                            Spacer(Modifier.width(6.dp))

                                            // 删除按钮
                                            Box(
                                                modifier = Modifier
                                                    .size(28.dp)
                                                    .clip(CircleShape)
                                                    .background(Color(0xFFE53935).copy(alpha = 0.08f))
                                                    .noRippleClickable {
                                                        wifiToDelete = wifi.bssid
                                                    },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    Icons.Rounded.DeleteOutline,
                                                    contentDescription = null,
                                                    tint = Color(0xFFE53935),
                                                    modifier = Modifier.size(14.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))

                // 底部操作按钮
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(
                                    alpha = 0.05f
                                )
                            )
                            .noRippleClickable(onClick = onDismiss),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.cancel),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
                        )
                    }

                    Button(
                        onClick = { onSave(placeName, remark, selectedWifiBssid) },
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
                        modifier = Modifier
                            .weight(1.3f)
                            .height(44.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.save_changes),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }

    // 编辑或新增 Wi-Fi 详细参数弹窗
    if (wifiBeingEdited != null || isNewWifiDialog) {
        EditWifiDialog(
            initialItem = wifiBeingEdited,
            isDark = isDark,
            onDismiss = {
                wifiBeingEdited = null
                isNewWifiDialog = false
            },
            onSave = { bssid, ssid, frequency, level, capabilities, vendor, isConnected, isDesignated ->
                onSaveWifi(bssid, ssid, frequency, level, capabilities, vendor, isConnected, isDesignated)
                if (isDesignated) {
                    selectedWifiBssid = bssid
                }
                wifiBeingEdited = null
                isNewWifiDialog = false
            }
        )
    }

    // 删除单项 Wi-Fi 二次确认
    if (wifiToDelete != null) {
        val targetBssid = wifiToDelete!!
        Dialog(onDismissRequest = { wifiToDelete = null }) {
            MiuixCard(
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .wrapContentHeight(),
                cornerRadius = 20.dp,
                insideMargin = PaddingValues(20.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.delete),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.delete_wifi_confirm, targetBssid),
                        fontSize = 13.5.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.05f))
                                .noRippleClickable { wifiToDelete = null },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = stringResource(R.string.cancel), fontSize = 13.5.sp)
                        }

                        Button(
                            onClick = {
                                onDeleteWifi(targetBssid)
                                if (selectedWifiBssid?.equals(targetBssid, ignoreCase = true) == true) {
                                    selectedWifiBssid = null
                                }
                                wifiToDelete = null
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935)),
                            modifier = Modifier
                                .weight(1.2f)
                                .height(40.dp)
                        ) {
                            Text(text = stringResource(R.string.delete), fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EditWifiDialog(
    initialItem: EditableWifiItem?,
    isDark: Boolean,
    onDismiss: () -> Unit,
    onSave: (
        bssid: String,
        ssid: String,
        frequency: Int,
        level: Int,
        capabilities: String,
        vendor: String,
        isConnected: Boolean,
        isDesignated: Boolean
    ) -> Unit
) {
    val isEdit = initialItem != null
    var ssid by remember { mutableStateOf(initialItem?.ssid ?: "") }
    var bssid by remember { mutableStateOf(initialItem?.bssid ?: "") }
    var level by remember { mutableIntStateOf(initialItem?.level ?: -55) }
    var frequency by remember { mutableIntStateOf(initialItem?.frequency ?: 5180) }
    var capabilities by remember { mutableStateOf(initialItem?.capabilities ?: "[WPA2-PSK-CCMP][RSN]") }
    var vendor by remember { mutableStateOf(initialItem?.vendor ?: "") }
    var isConnected by remember { mutableStateOf(initialItem?.isConnected ?: false) }
    var isDesignated by remember { mutableStateOf(initialItem?.isDesignated ?: true) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        MiuixCard(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.85f),
            cornerRadius = 24.dp,
            insideMargin = PaddingValues(20.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
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
                                .size(36.dp)
                                .clip(RoundedCornerShape(11.dp))
                                .background(AccentBlue.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Rounded.Wifi,
                                contentDescription = null,
                                tint = AccentBlue,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = stringResource(if (isEdit) R.string.edit_wifi_title else R.string.new_wifi_title),
                            fontSize = 16.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.05f))
                            .noRippleClickable(onClick = onDismiss),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Rounded.Close,
                            contentDescription = stringResource(R.string.close),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                // 表单主体
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // SSID 输入
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (isDark) Color.White.copy(alpha = 0.04f) else Color.Black.copy(alpha = 0.03f))
                            .border(0.8.dp, MaterialTheme.colorScheme.outline.copy(alpha = if (isDark) 0.12f else 0.06f), RoundedCornerShape(14.dp))
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.wifi_ssid_label),
                            fontSize = 11.5.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(Modifier.height(4.dp))
                        BasicTextField(
                            value = ssid,
                            onValueChange = { ssid = it },
                            textStyle = TextStyle(
                                fontSize = 14.5.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.SemiBold
                            ),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            decorationBox = { inner ->
                                if (ssid.isEmpty()) {
                                    Text(
                                        text = stringResource(R.string.wifi_ssid_hint),
                                        fontSize = 13.5.sp,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
                                    )
                                }
                                inner()
                            }
                        )
                    }

                    // BSSID (MAC) 输入
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (isDark) Color.White.copy(alpha = 0.04f) else Color.Black.copy(alpha = 0.03f))
                            .border(0.8.dp, MaterialTheme.colorScheme.outline.copy(alpha = if (isDark) 0.12f else 0.06f), RoundedCornerShape(14.dp))
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.wifi_bssid_label),
                            fontSize = 11.5.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(Modifier.height(4.dp))
                        BasicTextField(
                            value = bssid,
                            onValueChange = { bssid = it.uppercase() },
                            textStyle = TextStyle(
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Medium
                            ),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            decorationBox = { inner ->
                                if (bssid.isEmpty()) {
                                    Text(
                                        text = stringResource(R.string.wifi_bssid_hint),
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
                                    )
                                }
                                inner()
                            }
                        )
                    }

                    // 信号强度 RSSI
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (isDark) Color.White.copy(alpha = 0.04f) else Color.Black.copy(alpha = 0.03f))
                            .border(0.8.dp, MaterialTheme.colorScheme.outline.copy(alpha = if (isDark) 0.12f else 0.06f), RoundedCornerShape(14.dp))
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.wifi_level_label, level),
                            fontSize = 11.5.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(Modifier.height(6.dp))

                        // 快速选择强度按钮
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(-40 to "-40 极强", -55 to "-55 良好", -70 to "-70 一般", -85 to "-85 微弱").forEach { (lvl, lbl) ->
                                val isChosen = level == lvl
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isChosen) AccentBlue else if (isDark) Color.White.copy(alpha = 0.06f) else Color.Black.copy(alpha = 0.04f))
                                        .noRippleClickable { level = lvl }
                                        .padding(vertical = 6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = lbl,
                                        fontSize = 10.5.sp,
                                        fontWeight = if (isChosen) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isChosen) Color.White else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
                                    )
                                }
                            }
                        }
                    }

                    // 工作频率 (Frequency)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (isDark) Color.White.copy(alpha = 0.04f) else Color.Black.copy(alpha = 0.03f))
                            .border(0.8.dp, MaterialTheme.colorScheme.outline.copy(alpha = if (isDark) 0.12f else 0.06f), RoundedCornerShape(14.dp))
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.wifi_freq_label),
                            fontSize = 11.5.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(Modifier.height(6.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(2412 to "2.4G (2412)", 5180 to "5G (5180)", 5745 to "5.8G (5745)").forEach { (freq, lbl) ->
                                val isChosen = frequency == freq
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isChosen) AccentBlue else if (isDark) Color.White.copy(alpha = 0.06f) else Color.Black.copy(alpha = 0.04f))
                                        .noRippleClickable { frequency = freq }
                                        .padding(vertical = 6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = lbl,
                                        fontSize = 11.sp,
                                        fontWeight = if (isChosen) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isChosen) Color.White else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
                                    )
                                }
                            }
                        }
                    }

                    // 加密属性 (Capabilities)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (isDark) Color.White.copy(alpha = 0.04f) else Color.Black.copy(alpha = 0.03f))
                            .border(0.8.dp, MaterialTheme.colorScheme.outline.copy(alpha = if (isDark) 0.12f else 0.06f), RoundedCornerShape(14.dp))
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.wifi_capabilities_label),
                            fontSize = 11.5.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(Modifier.height(4.dp))
                        BasicTextField(
                            value = capabilities,
                            onValueChange = { capabilities = it },
                            textStyle = TextStyle(
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("[WPA2-PSK-CCMP][RSN]" to "WPA2", "[WPA3-SAE]" to "WPA3", "[ESS]" to "OPEN").forEach { (cap, tag) ->
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (capabilities == cap) AccentBlue.copy(alpha = 0.15f) else if (isDark) Color.White.copy(alpha = 0.06f) else Color.Black.copy(alpha = 0.04f))
                                        .noRippleClickable { capabilities = cap }
                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    Text(
                                        text = tag,
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (capabilities == cap) AccentBlue else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                    )
                                }
                            }
                        }
                    }

                    // 开关选项容器
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (isDark) Color.White.copy(alpha = 0.04f) else Color.Black.copy(alpha = 0.03f))
                            .border(0.8.dp, MaterialTheme.colorScheme.outline.copy(alpha = if (isDark) 0.12f else 0.06f), RoundedCornerShape(14.dp))
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        // 设为当前已连接 Wi-Fi 开关
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = stringResource(R.string.wifi_is_connected_label),
                                fontSize = 13.5.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Switch(
                                checked = isConnected,
                                onCheckedChange = { isConnected = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = AccentGreen
                                )
                            )
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = if (isDark) 0.08f else 0.04f))

                        // 设为模拟首选 Wi-Fi 开关
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = stringResource(R.string.designated_simulation_badge),
                                fontSize = 13.5.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Switch(
                                checked = isDesignated,
                                onCheckedChange = { isDesignated = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = AccentBlue
                                )
                            )
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))

                // 底部操作按钮
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.05f))
                            .noRippleClickable(onClick = onDismiss),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = stringResource(R.string.cancel), fontSize = 13.5.sp)
                    }

                    Button(
                        onClick = {
                            val finalBssid = bssid.ifBlank {
                                String.format(Locale.US, "02:00:00:%02X:%02X:%02X", (0..255).random(), (0..255).random(), (0..255).random())
                            }
                            val finalSsid = ssid.ifBlank { "Mock_WiFi" }
                            onSave(
                                finalBssid,
                                finalSsid,
                                frequency,
                                level,
                                capabilities,
                                vendor,
                                isConnected,
                                isDesignated
                            )
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
                        modifier = Modifier
                            .weight(1.3f)
                            .height(42.dp)
                    ) {
                        Text(text = stringResource(R.string.save_wifi), fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
