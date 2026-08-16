package com.suseoaa.locationspoofer.ui.screen

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.suseoaa.locationspoofer.R
import com.suseoaa.locationspoofer.data.db.CompleteLocation
import com.suseoaa.locationspoofer.ui.components.AppMapController
import com.suseoaa.locationspoofer.ui.components.AppMapView
import com.suseoaa.locationspoofer.ui.theme.AccentBlue
import com.suseoaa.locationspoofer.ui.theme.AppColors
import com.suseoaa.locationspoofer.utils.MapCoverageHelper
import com.suseoaa.locationspoofer.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import top.yukonga.miuix.kmp.basic.Card as MiuixCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageDataScreen(
    viewModel: MainViewModel,
    uiState: com.suseoaa.locationspoofer.data.model.AppState,
    isDark: Boolean,
    onClose: () -> Unit
) {
    var isSelectionMode by remember { mutableStateOf(false) }
    val selectedIds = remember { mutableStateListOf<Long>() }
    var showClearAllConfirm by remember { mutableStateOf(false) }
    var mapController by remember { mutableStateOf<AppMapController?>(null) }

    BackHandler {
        if (isSelectionMode) {
            isSelectionMode = false
            selectedIds.clear()
        } else {
            onClose()
        }
    }

    var editingItem by remember { mutableStateOf<CompleteLocation?>(null) }
    val dataList = uiState.manageDataList

    LaunchedEffect(mapController, uiState.mapType) {
        mapController?.setMapType(uiState.mapType)
    }

    LaunchedEffect(mapController, dataList) {
        val controller = mapController ?: return@LaunchedEffect
        controller.clear()
        val locations = dataList.map { it.location }
        MapCoverageHelper.drawCoverage(controller, locations)
        if (locations.isNotEmpty()) {
            val last = locations.last()
            controller.moveCamera(last.lat, last.lng, 15f)
        }
    }

    Scaffold(
        containerColor = AppColors.background(isDark),
        topBar = {
            TopAppBar(
                title = {
                    if (isSelectionMode) {
                        Text(stringResource(R.string.selected_items, selectedIds.size), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    } else {
                        Text(stringResource(R.string.title_manage_data), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (isSelectionMode) {
                            isSelectionMode = false
                            selectedIds.clear()
                        } else {
                            onClose()
                        }
                    }) {
                        Icon(
                            if (isSelectionMode) Icons.Rounded.Close else Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                },
                actions = {
                    if (isSelectionMode) {
                        IconButton(onClick = {
                            if (selectedIds.size == dataList.size) {
                                selectedIds.clear()
                            } else {
                                selectedIds.clear()
                                selectedIds.addAll(dataList.map { it.location.id })
                            }
                        }) {
                            Icon(
                                Icons.Rounded.SelectAll,
                                contentDescription = stringResource(R.string.select_all)
                            )
                        }
                        if (selectedIds.isNotEmpty()) {
                            IconButton(onClick = {
                                viewModel.deleteManageData(selectedIds.toList())
                                isSelectionMode = false
                                selectedIds.clear()
                            }) {
                                Icon(
                                    Icons.Rounded.Delete,
                                    contentDescription = stringResource(R.string.delete),
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    } else {
                        IconButton(onClick = { isSelectionMode = true }) {
                            Icon(
                                Icons.Rounded.Checklist,
                                contentDescription = stringResource(R.string.select_all)
                            )
                        }
                        IconButton(onClick = { showClearAllConfirm = true }) {
                            Icon(
                                Icons.Rounded.DeleteSweep,
                                contentDescription = stringResource(R.string.clear_all),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { paddingValues ->
        if (uiState.manageDataIsLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AccentBlue)
            }
        } else if (dataList.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(R.string.no_data_collected),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    fontSize = 14.sp
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                // Top Map View
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.38f)
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
                }

                // Bottom List View
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.62f),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(dataList, key = { it.location.id }) { item ->
                        val isSelected = selectedIds.contains(item.location.id)
                        DataListItem(
                            item = item,
                            isDark = isDark,
                            isSelectionMode = isSelectionMode,
                            isSelected = isSelected,
                            onSelect = {
                                if (isSelected) selectedIds.remove(item.location.id)
                                else selectedIds.add(item.location.id)
                            },
                            onLongClick = {
                                if (!isSelectionMode) {
                                    isSelectionMode = true
                                    selectedIds.add(item.location.id)
                                }
                            },
                            onClick = {
                                viewModel.updateLatitude(String.format(Locale.US, "%.6f", item.location.lat))
                                viewModel.updateLongitude(String.format(Locale.US, "%.6f", item.location.lng))
                                mapController?.animateCamera(item.location.lat, item.location.lng, 17f)
                            },
                            onDeleteSingle = {
                                viewModel.deleteManageDataSingle(item.location.id)
                            },
                            onEdit = {
                                editingItem = item
                            }
                        )
                    }
                }
            }
        }
    }

    if (editingItem != null) {
        var placeName by remember { mutableStateOf(editingItem!!.location.placeName) }
        var remark by remember { mutableStateOf(editingItem!!.location.remark) }

        AlertDialog(
            onDismissRequest = { editingItem = null },
            title = { Text(stringResource(R.string.edit_location_data), fontSize = 18.sp, fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    OutlinedTextField(
                        value = placeName,
                        onValueChange = { placeName = it },
                        label = { Text(stringResource(R.string.place_name)) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AccentBlue)
                    )
                    OutlinedTextField(
                        value = remark,
                        onValueChange = { remark = it },
                        label = { Text(stringResource(R.string.remark_note)) },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AccentBlue)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val current = editingItem!!
                        viewModel.updateManageDataMetadata(
                            current.location.id,
                            placeName,
                            remark,
                            current.location.selectedWifiBssid,
                            current.location.selectedBluetoothAddress,
                            current.location.selectedCellKey
                        )
                        editingItem = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(stringResource(R.string.save))
                }
            },
            dismissButton = {
                TextButton(onClick = { editingItem = null }) {
                    Text(stringResource(R.string.cancel), color = AccentBlue)
                }
            }
        )
    }

    if (showClearAllConfirm) {
        AlertDialog(
            onDismissRequest = { showClearAllConfirm = false },
            title = { Text(stringResource(R.string.clear_all_data), fontSize = 18.sp, fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.clear_all_data_confirm), fontSize = 14.sp) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearAllManageData()
                        showClearAllConfirm = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.clear_all))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearAllConfirm = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DataListItem(
    item: CompleteLocation,
    isDark: Boolean,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onLongClick: () -> Unit,
    onClick: () -> Unit,
    onDeleteSingle: () -> Unit,
    onEdit: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()) }
    val timeStr = remember(item.location.timestamp) { dateFormat.format(Date(item.location.timestamp)) }

    val wifiCount = item.wifis.size
    val cellCount = item.cells.size
    val btCount = item.bluetooths.size

    MiuixCard(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {
                    if (isSelectionMode) onSelect()
                    else onClick()
                },
                onLongClick = onLongClick
            ),
        cornerRadius = 16.dp,
        insideMargin = PaddingValues(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isSelectionMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onSelect() },
                    modifier = Modifier.padding(end = 12.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = timeStr,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Lat: ${String.format(Locale.US, "%.5f", item.location.lat)}, Lng: ${String.format(Locale.US, "%.5f", item.location.lng)}",
                    fontSize = 12.5.sp,
                    color = AppColors.textSecondary(isDark)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    IconTextBadge(Icons.Rounded.Wifi, "$wifiCount", isDark)
                    IconTextBadge(Icons.Rounded.CellTower, "$cellCount", isDark)
                    IconTextBadge(Icons.Rounded.Bluetooth, "$btCount", isDark)
                }

                if (item.location.placeName.isNotBlank() || item.location.remark.isNotBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                    Spacer(modifier = Modifier.height(6.dp))
                    if (item.location.placeName.isNotBlank()) {
                        Text(
                            text = "📍 ${item.location.placeName}",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    if (item.location.remark.isNotBlank()) {
                        Text(
                            text = "📝 ${item.location.remark}",
                            fontSize = 12.sp,
                            color = AppColors.textSecondary(isDark)
                        )
                    }
                }
            }

            if (!isSelectionMode) {
                Row {
                    IconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) {
                        Icon(
                            Icons.Rounded.Edit,
                            contentDescription = stringResource(R.string.edit_location_data),
                            tint = AccentBlue.copy(alpha = 0.8f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(onClick = onDeleteSingle, modifier = Modifier.size(36.dp)) {
                        Icon(
                            Icons.Rounded.DeleteOutline,
                            contentDescription = stringResource(R.string.delete),
                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun IconTextBadge(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, isDark: Boolean) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (isDark) Color.White.copy(alpha = 0.06f) else Color.Black.copy(alpha = 0.04f))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = AppColors.textSecondary(isDark)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = text,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Medium,
                color = AppColors.textSecondary(isDark)
            )
        }
    }
}
