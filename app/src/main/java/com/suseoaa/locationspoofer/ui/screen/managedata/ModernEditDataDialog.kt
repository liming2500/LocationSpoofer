package com.suseoaa.locationspoofer.ui.screen.managedata

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.suseoaa.locationspoofer.R
import com.suseoaa.locationspoofer.data.db.CompleteLocation
import com.suseoaa.locationspoofer.ui.theme.AccentBlue
import com.suseoaa.locationspoofer.ui.theme.AccentGreen
import com.suseoaa.locationspoofer.ui.theme.AccentOrange
import com.suseoaa.locationspoofer.ui.theme.noRippleClickable
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ModernEditDataDialog(
    item: CompleteLocation,
    isDark: Boolean,
    onDismiss: () -> Unit,
    onSave: (placeName: String, remark: String, selectedWifiBssid: String?, selectedBluetoothAddress: String?, selectedCellKey: String?) -> Unit,
    onSaveWifi: (bssid: String, ssid: String, frequency: Int, level: Int, capabilities: String, vendor: String, isConnected: Boolean, isDesignated: Boolean) -> Unit,
    onDeleteWifi: (bssid: String) -> Unit,
    onSaveCell: (cellKey: String, type: String, mcc: Int, mnc: Int, tac: Int, ci: Int, pci: Int, lac: Int, cid: Int, psc: Int, nci: Long, networkId: Int, systemId: Int, basestationId: Int, dbm: Int, isRegistered: Boolean, isDesignated: Boolean) -> Unit,
    onDeleteCell: (cellKey: String) -> Unit,
    onSaveBluetooth: (address: String, name: String, scanRecordHex: String, rssi: Int, isDesignated: Boolean) -> Unit,
    onDeleteBluetooth: (address: String) -> Unit
) {
    var placeName by remember(item.location.id) { mutableStateOf(item.location.placeName) }
    var remark by remember(item.location.id) { mutableStateOf(item.location.remark) }
    var selectedWifiBssid by remember(item.location.id, item.location.selectedWifiBssid) {
        mutableStateOf(item.location.selectedWifiBssid)
    }
    var selectedBluetoothAddress by remember(item.location.id, item.location.selectedBluetoothAddress) {
        mutableStateOf(item.location.selectedBluetoothAddress)
    }
    var selectedCellKey by remember(item.location.id, item.location.selectedCellKey) {
        mutableStateOf(item.location.selectedCellKey)
    }

    var selectedTab by remember { mutableStateOf(EditDataTab.WIFI) }

    // Wi-Fi 弹窗状态
    var wifiBeingEdited by remember { mutableStateOf<EditableWifiItem?>(null) }
    var isNewWifiDialog by remember { mutableStateOf(false) }
    var wifiToDelete by remember { mutableStateOf<String?>(null) }

    // 基站弹窗状态
    var cellBeingEdited by remember { mutableStateOf<EditableCellItem?>(null) }
    var isNewCellDialog by remember { mutableStateOf(false) }
    var cellToDelete by remember { mutableStateOf<String?>(null) }

    // 蓝牙弹窗状态
    var btBeingEdited by remember { mutableStateOf<EditableBluetoothItem?>(null) }
    var isNewBtDialog by remember { mutableStateOf(false) }
    var btToDelete by remember { mutableStateOf<String?>(null) }

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

    val cellList = remember(item, selectedCellKey) {
        item.cells.map { lc ->
            EditableCellItem(
                cellKey = lc.device.cellKey,
                type = lc.device.type,
                mcc = lc.device.mcc,
                mnc = lc.device.mnc,
                tac = lc.device.tac,
                ci = lc.device.ci,
                pci = lc.device.pci,
                lac = lc.device.lac,
                cid = lc.device.cid,
                psc = lc.device.psc,
                nci = lc.device.nci,
                networkId = lc.device.networkId,
                systemId = lc.device.systemId,
                basestationId = lc.device.basestationId,
                dbm = lc.locationCell.dbm,
                isRegistered = lc.locationCell.isRegistered,
                isDesignated = selectedCellKey?.equals(lc.device.cellKey, ignoreCase = true) == true
            )
        }
    }

    val btList = remember(item, selectedBluetoothAddress) {
        item.bluetooths.map { lb ->
            EditableBluetoothItem(
                address = lb.device.address,
                name = lb.device.name,
                scanRecordHex = lb.device.scanRecordHex,
                rssi = lb.locationBluetooth.rssi,
                isDesignated = selectedBluetoothAddress?.equals(lb.device.address, ignoreCase = true) == true
            )
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.88f),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
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
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // 经纬度及环境数据概览条
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
                                Text(
                                    text = "${wifiList.size} Wi-Fi",
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
                                    text = stringResource(R.string.cells_count_badge, cellList.size),
                                    fontSize = 11.sp,
                                    color = AccentOrange,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "•",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                                )
                                Text(
                                    text = "${btList.size} BT",
                                    fontSize = 11.sp,
                                    color = Color(0xFF9C27B0),
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

                    // 选项卡切换器：[Wi-Fi] [基站] [蓝牙]
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isDark) Color.White.copy(alpha = 0.06f) else Color.Black.copy(alpha = 0.04f))
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        EditDataTab.values().forEach { tab ->
                            val isCurrent = selectedTab == tab
                            val tabTitle = when (tab) {
                                EditDataTab.WIFI -> stringResource(R.string.manage_tab_wifi, wifiList.size)
                                EditDataTab.CELL -> stringResource(R.string.manage_tab_cell, cellList.size)
                                EditDataTab.BLUETOOTH -> stringResource(R.string.manage_tab_bluetooth, btList.size)
                            }
                            val tabColor = when (tab) {
                                EditDataTab.WIFI -> AccentBlue
                                EditDataTab.CELL -> AccentOrange
                                EditDataTab.BLUETOOTH -> Color(0xFF9C27B0)
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(36.dp)
                                    .clip(RoundedCornerShape(9.dp))
                                    .background(
                                        if (isCurrent) {
                                            if (isDark) Color.White.copy(alpha = 0.12f) else Color.White
                                        } else Color.Transparent
                                    )
                                    .then(
                                        if (isCurrent) {
                                            Modifier.border(
                                                0.6.dp,
                                                if (isDark) Color.White.copy(alpha = 0.18f) else Color.Black.copy(alpha = 0.08f),
                                                RoundedCornerShape(9.dp)
                                             )
                                        } else Modifier
                                    )
                                    .noRippleClickable { selectedTab = tab },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = tabTitle,
                                    fontSize = 12.5.sp,
                                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isCurrent) tabColor else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            }
                        }
                    }

                    // 选项卡内容区
                    when (selectedTab) {
                        EditDataTab.WIFI -> {
                            // 模拟 Wi-Fi 选项与数据库管理模块
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(if (isDark) Color.White.copy(alpha = 0.04f) else Color.Black.copy(alpha = 0.03f))
                                    .border(0.8.dp, MaterialTheme.colorScheme.outline.copy(alpha = if (isDark) 0.12f else 0.06f), RoundedCornerShape(16.dp))
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Rounded.Wifi, contentDescription = null, tint = AccentBlue, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Text(text = stringResource(R.string.manage_wifi_list_title), fontSize = 14.5.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                    }

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
                                            Icon(Icons.Rounded.Add, contentDescription = null, tint = AccentBlue, modifier = Modifier.size(14.dp))
                                            Spacer(Modifier.width(3.dp))
                                            Text(text = stringResource(R.string.add_wifi_btn), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = AccentBlue)
                                        }
                                    }
                                }

                                Text(
                                    text = stringResource(R.string.designated_wifi_tip),
                                    fontSize = 11.5.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.48f),
                                    lineHeight = 16.sp
                                )

                                val isAutoSelected = selectedWifiBssid == null
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (isAutoSelected) AccentBlue.copy(alpha = 0.08f) else Color.Transparent)
                                        .border(0.8.dp, if (isAutoSelected) AccentBlue.copy(alpha = 0.35f) else Color.Transparent, RoundedCornerShape(10.dp))
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
                                        Text(text = stringResource(R.string.no_wifi_data_in_record), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                                    }
                                } else {
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
                                                    .noRippleClickable { selectedWifiBssid = wifi.bssid }
                                                    .padding(horizontal = 10.dp, vertical = 9.dp)
                                            ) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Icon(
                                                        imageVector = if (isSelected) Icons.Rounded.RadioButtonChecked else Icons.Rounded.RadioButtonUnchecked,
                                                        contentDescription = null,
                                                        tint = if (isSelected) AccentBlue else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                                        modifier = Modifier.size(17.dp)
                                                    )
                                                    Spacer(Modifier.width(8.dp))

                                                    Column(modifier = Modifier.weight(1f)) {
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
                                                        Icon(Icons.Rounded.Edit, contentDescription = null, tint = AccentBlue, modifier = Modifier.size(13.dp))
                                                    }

                                                    Spacer(Modifier.width(6.dp))

                                                    Box(
                                                        modifier = Modifier
                                                            .size(28.dp)
                                                            .clip(CircleShape)
                                                            .background(Color(0xFFE53935).copy(alpha = 0.08f))
                                                            .noRippleClickable { wifiToDelete = wifi.bssid },
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Icon(Icons.Rounded.DeleteOutline, contentDescription = null, tint = Color(0xFFE53935), modifier = Modifier.size(14.dp))
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        EditDataTab.CELL -> {
                            // 模拟基站选项与数据库管理模块
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(if (isDark) Color.White.copy(alpha = 0.04f) else Color.Black.copy(alpha = 0.03f))
                                    .border(0.8.dp, MaterialTheme.colorScheme.outline.copy(alpha = if (isDark) 0.12f else 0.06f), RoundedCornerShape(16.dp))
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Rounded.CellTower, contentDescription = null, tint = AccentOrange, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Text(text = stringResource(R.string.manage_cell_list_title), fontSize = 14.5.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                    }

                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(AccentOrange.copy(alpha = 0.12f))
                                            .noRippleClickable {
                                                isNewCellDialog = true
                                                cellBeingEdited = null
                                            }
                                            .padding(horizontal = 10.dp, vertical = 5.dp)
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Rounded.Add, contentDescription = null, tint = AccentOrange, modifier = Modifier.size(14.dp))
                                            Spacer(Modifier.width(3.dp))
                                            Text(text = stringResource(R.string.add_cell_btn), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = AccentOrange)
                                        }
                                    }
                                }

                                Text(
                                    text = stringResource(R.string.designated_cell_tip),
                                    fontSize = 11.5.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.48f),
                                    lineHeight = 16.sp
                                )

                                val isAutoSelected = selectedCellKey == null
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (isAutoSelected) AccentOrange.copy(alpha = 0.08f) else Color.Transparent)
                                        .border(0.8.dp, if (isAutoSelected) AccentOrange.copy(alpha = 0.35f) else Color.Transparent, RoundedCornerShape(10.dp))
                                        .noRippleClickable { selectedCellKey = null }
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (isAutoSelected) Icons.Rounded.RadioButtonChecked else Icons.Rounded.RadioButtonUnchecked,
                                        contentDescription = null,
                                        tint = if (isAutoSelected) AccentOrange else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                        modifier = Modifier.size(17.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = stringResource(R.string.no_designated_cell),
                                        fontSize = 13.sp,
                                        fontWeight = if (isAutoSelected) FontWeight.SemiBold else FontWeight.Normal,
                                        color = if (isAutoSelected) AccentOrange else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                                    )
                                }

                                if (cellList.isEmpty()) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 12.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(text = stringResource(R.string.no_cell_data_in_record), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                                    }
                                } else {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        cellList.forEach { cell ->
                                            val isSelected = selectedCellKey?.equals(cell.cellKey, ignoreCase = true) == true

                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(12.dp))
                                                    .background(
                                                        if (isSelected) AccentOrange.copy(alpha = 0.09f)
                                                        else if (isDark) Color.White.copy(alpha = 0.03f) else Color.Black.copy(alpha = 0.02f)
                                                    )
                                                    .border(
                                                        0.8.dp,
                                                        if (isSelected) AccentOrange.copy(alpha = 0.4f)
                                                        else MaterialTheme.colorScheme.outline.copy(alpha = if (isDark) 0.08f else 0.04f),
                                                        RoundedCornerShape(12.dp)
                                                    )
                                                    .noRippleClickable { selectedCellKey = cell.cellKey }
                                                    .padding(horizontal = 10.dp, vertical = 9.dp)
                                            ) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Icon(
                                                        imageVector = if (isSelected) Icons.Rounded.RadioButtonChecked else Icons.Rounded.RadioButtonUnchecked,
                                                        contentDescription = null,
                                                        tint = if (isSelected) AccentOrange else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                                        modifier = Modifier.size(17.dp)
                                                    )
                                                    Spacer(Modifier.width(8.dp))

                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Row(
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                        ) {
                                                            Text(
                                                                text = "${cell.type.uppercase()} • MCC:${cell.mcc} MNC:${cell.mnc}",
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
                                                                        text = stringResource(R.string.designated_cell_badge),
                                                                        fontSize = 9.5.sp,
                                                                        fontWeight = FontWeight.Bold,
                                                                        color = AccentBlue
                                                                    )
                                                                }
                                                            }

                                                            if (cell.isRegistered) {
                                                                Box(
                                                                    modifier = Modifier
                                                                        .clip(RoundedCornerShape(4.dp))
                                                                        .background(AccentOrange.copy(alpha = 0.15f))
                                                                        .padding(horizontal = 5.dp, vertical = 1.dp)
                                                                ) {
                                                                    Text(
                                                                        text = stringResource(R.string.registered_cell_badge),
                                                                        fontSize = 9.5.sp,
                                                                        fontWeight = FontWeight.Bold,
                                                                        color = AccentOrange
                                                                    )
                                                                }
                                                            }
                                                        }

                                                        Spacer(Modifier.height(3.dp))

                                                        val details = when (cell.type.uppercase()) {
                                                            "LTE" -> "TAC: ${cell.tac}  •  CI: ${cell.ci}  •  PCI: ${cell.pci}  •  ${cell.dbm} dBm"
                                                            "NR" -> "TAC: ${cell.tac}  •  NCI: ${cell.nci}  •  PCI: ${cell.pci}  •  ${cell.dbm} dBm"
                                                            "GSM" -> "LAC: ${cell.lac}  •  CID: ${cell.cid}  •  ${cell.dbm} dBm"
                                                            "WCDMA" -> "LAC: ${cell.lac}  •  CID: ${cell.cid}  •  PSC: ${cell.psc}  •  ${cell.dbm} dBm"
                                                            else -> "Key: ${cell.cellKey}  •  ${cell.dbm} dBm"
                                                        }

                                                        Text(
                                                            text = details,
                                                            fontSize = 11.5.sp,
                                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                                                        )
                                                    }

                                                    Box(
                                                        modifier = Modifier
                                                            .size(28.dp)
                                                            .clip(CircleShape)
                                                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
                                                            .noRippleClickable {
                                                                cellBeingEdited = cell
                                                                isNewCellDialog = false
                                                            },
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Icon(Icons.Rounded.Edit, contentDescription = null, tint = AccentOrange, modifier = Modifier.size(13.dp))
                                                    }

                                                    Spacer(Modifier.width(6.dp))

                                                    Box(
                                                        modifier = Modifier
                                                            .size(28.dp)
                                                            .clip(CircleShape)
                                                            .background(Color(0xFFE53935).copy(alpha = 0.08f))
                                                            .noRippleClickable { cellToDelete = cell.cellKey },
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Icon(Icons.Rounded.DeleteOutline, contentDescription = null, tint = Color(0xFFE53935), modifier = Modifier.size(14.dp))
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        EditDataTab.BLUETOOTH -> {
                            // 模拟蓝牙选项与数据库管理模块
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(if (isDark) Color.White.copy(alpha = 0.04f) else Color.Black.copy(alpha = 0.03f))
                                    .border(0.8.dp, MaterialTheme.colorScheme.outline.copy(alpha = if (isDark) 0.12f else 0.06f), RoundedCornerShape(16.dp))
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Rounded.Bluetooth, contentDescription = null, tint = Color(0xFF9C27B0), modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Text(text = stringResource(R.string.manage_bt_list_title), fontSize = 14.5.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                    }

                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color(0xFF9C27B0).copy(alpha = 0.12f))
                                            .noRippleClickable {
                                                isNewBtDialog = true
                                                btBeingEdited = null
                                            }
                                            .padding(horizontal = 10.dp, vertical = 5.dp)
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Rounded.Add, contentDescription = null, tint = Color(0xFF9C27B0), modifier = Modifier.size(14.dp))
                                            Spacer(Modifier.width(3.dp))
                                            Text(text = stringResource(R.string.add_bt_btn), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF9C27B0))
                                        }
                                    }
                                }

                                Text(
                                    text = stringResource(R.string.designated_bt_tip),
                                    fontSize = 11.5.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.48f),
                                    lineHeight = 16.sp
                                )

                                val isAutoSelected = selectedBluetoothAddress == null
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (isAutoSelected) Color(0xFF9C27B0).copy(alpha = 0.08f) else Color.Transparent)
                                        .border(0.8.dp, if (isAutoSelected) Color(0xFF9C27B0).copy(alpha = 0.35f) else Color.Transparent, RoundedCornerShape(10.dp))
                                        .noRippleClickable { selectedBluetoothAddress = null }
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (isAutoSelected) Icons.Rounded.RadioButtonChecked else Icons.Rounded.RadioButtonUnchecked,
                                        contentDescription = null,
                                        tint = if (isAutoSelected) Color(0xFF9C27B0) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                        modifier = Modifier.size(17.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = stringResource(R.string.no_designated_bt),
                                        fontSize = 13.sp,
                                        fontWeight = if (isAutoSelected) FontWeight.SemiBold else FontWeight.Normal,
                                        color = if (isAutoSelected) Color(0xFF9C27B0) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                                    )
                                }

                                if (btList.isEmpty()) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 12.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(text = stringResource(R.string.no_bt_data_in_record), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                                    }
                                } else {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        btList.forEach { bt ->
                                            val isSelected = selectedBluetoothAddress?.equals(bt.address, ignoreCase = true) == true

                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(12.dp))
                                                    .background(
                                                        if (isSelected) Color(0xFF9C27B0).copy(alpha = 0.09f)
                                                        else if (isDark) Color.White.copy(alpha = 0.03f) else Color.Black.copy(alpha = 0.02f)
                                                    )
                                                    .border(
                                                        0.8.dp,
                                                        if (isSelected) Color(0xFF9C27B0).copy(alpha = 0.4f)
                                                        else MaterialTheme.colorScheme.outline.copy(alpha = if (isDark) 0.08f else 0.04f),
                                                        RoundedCornerShape(12.dp)
                                                    )
                                                    .noRippleClickable { selectedBluetoothAddress = bt.address }
                                                    .padding(horizontal = 10.dp, vertical = 9.dp)
                                            ) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Icon(
                                                        imageVector = if (isSelected) Icons.Rounded.RadioButtonChecked else Icons.Rounded.RadioButtonUnchecked,
                                                        contentDescription = null,
                                                        tint = if (isSelected) Color(0xFF9C27B0) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                                        modifier = Modifier.size(17.dp)
                                                    )
                                                    Spacer(Modifier.width(8.dp))

                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Row(
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                        ) {
                                                            Text(
                                                                text = bt.name.ifBlank { "<Unknown Device>" },
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
                                                                        text = stringResource(R.string.designated_bt_badge),
                                                                        fontSize = 9.5.sp,
                                                                        fontWeight = FontWeight.Bold,
                                                                        color = AccentBlue
                                                                    )
                                                                }
                                                            }
                                                        }

                                                        Spacer(Modifier.height(3.dp))

                                                        Text(
                                                            text = "${bt.address}  •  ${bt.rssi} dBm",
                                                            fontSize = 11.5.sp,
                                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                                                        )

                                                        if (bt.scanRecordHex.isNotBlank()) {
                                                            Text(
                                                                text = "Hex: ${bt.scanRecordHex}",
                                                                fontSize = 10.sp,
                                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                                                                maxLines = 1
                                                            )
                                                        }
                                                    }

                                                    Box(
                                                        modifier = Modifier
                                                            .size(28.dp)
                                                            .clip(CircleShape)
                                                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
                                                            .noRippleClickable {
                                                                btBeingEdited = bt
                                                                isNewBtDialog = false
                                                            },
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Icon(Icons.Rounded.Edit, contentDescription = null, tint = Color(0xFF9C27B0), modifier = Modifier.size(13.dp))
                                                    }

                                                    Spacer(Modifier.width(6.dp))

                                                    Box(
                                                        modifier = Modifier
                                                            .size(28.dp)
                                                            .clip(CircleShape)
                                                            .background(Color(0xFFE53935).copy(alpha = 0.08f))
                                                            .noRippleClickable { btToDelete = bt.address },
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Icon(Icons.Rounded.DeleteOutline, contentDescription = null, tint = Color(0xFFE53935), modifier = Modifier.size(14.dp))
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
                        onClick = { onSave(placeName, remark, selectedWifiBssid, selectedBluetoothAddress, selectedCellKey) },
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
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .wrapContentHeight(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
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

    // 编辑或新增基站详细参数弹窗
    if (cellBeingEdited != null || isNewCellDialog) {
        EditCellDialog(
            initialItem = cellBeingEdited,
            isDark = isDark,
            onDismiss = {
                cellBeingEdited = null
                isNewCellDialog = false
            },
            onSave = { cellKey, type, mcc, mnc, tac, ci, pci, lac, cid, psc, nci, networkId, systemId, basestationId, dbm, isRegistered, isDesignated ->
                onSaveCell(cellKey, type, mcc, mnc, tac, ci, pci, lac, cid, psc, nci, networkId, systemId, basestationId, dbm, isRegistered, isDesignated)
                if (isDesignated) {
                    selectedCellKey = cellKey
                }
                cellBeingEdited = null
                isNewCellDialog = false
            }
        )
    }

    // 删除单项基站二次确认
    if (cellToDelete != null) {
        val targetCellKey = cellToDelete!!
        Dialog(onDismissRequest = { cellToDelete = null }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .wrapContentHeight(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.delete),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.delete_cell_confirm, targetCellKey),
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
                            .noRippleClickable { cellToDelete = null },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = stringResource(R.string.cancel), fontSize = 13.5.sp)
                        }

                        Button(
                            onClick = {
                                onDeleteCell(targetCellKey)
                                if (selectedCellKey?.equals(targetCellKey, ignoreCase = true) == true) {
                                    selectedCellKey = null
                                }
                                cellToDelete = null
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

    // 编辑或新增蓝牙详细参数弹窗
    if (btBeingEdited != null || isNewBtDialog) {
        EditBluetoothDialog(
            initialItem = btBeingEdited,
            isDark = isDark,
            onDismiss = {
                btBeingEdited = null
                isNewBtDialog = false
            },
            onSave = { address, name, scanRecordHex, rssi, isDesignated ->
                onSaveBluetooth(address, name, scanRecordHex, rssi, isDesignated)
                if (isDesignated) {
                    selectedBluetoothAddress = address
                }
                btBeingEdited = null
                isNewBtDialog = false
            }
        )
    }

    // 删除单项蓝牙二次确认
    if (btToDelete != null) {
        val targetAddress = btToDelete!!
        Dialog(onDismissRequest = { btToDelete = null }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .wrapContentHeight(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.delete),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.delete_bt_confirm, targetAddress),
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
                            .noRippleClickable { btToDelete = null },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = stringResource(R.string.cancel), fontSize = 13.5.sp)
                        }

                        Button(
                            onClick = {
                                onDeleteBluetooth(targetAddress)
                                if (selectedBluetoothAddress?.equals(targetAddress, ignoreCase = true) == true) {
                                    selectedBluetoothAddress = null
                                }
                                btToDelete = null
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
