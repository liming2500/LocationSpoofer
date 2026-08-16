package com.suseoaa.locationspoofer.ui.screen

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.border
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.suseoaa.locationspoofer.R
import com.suseoaa.locationspoofer.data.model.AppState
import com.suseoaa.locationspoofer.ui.theme.AccentBlue
import com.suseoaa.locationspoofer.ui.theme.AccentGreen
import com.suseoaa.locationspoofer.ui.theme.AccentOrange
import com.suseoaa.locationspoofer.ui.theme.AppColors
import com.suseoaa.locationspoofer.ui.theme.noRippleClickable
import com.suseoaa.locationspoofer.viewmodel.MainViewModel
import top.yukonga.miuix.kmp.basic.Button as MiuixButton
import top.yukonga.miuix.kmp.basic.ButtonDefaults as MiuixButtonDefaults
import top.yukonga.miuix.kmp.basic.Card as MiuixCard
import top.yukonga.miuix.kmp.theme.MiuixTheme


// 坐标输入卡片 (Miuix 风格)
@Composable
fun CoordinateInputCard(
    viewModel: MainViewModel,
    uiState: AppState,
    isDark: Boolean,
    onSaveClick: () -> Unit,
    onCustomClick: () -> Unit
) {
    val textSecondary = AppColors.textSecondary(isDark)

    MiuixCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 18.dp,
        insideMargin = PaddingValues(16.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionHeader(
                    Icons.Outlined.PinDrop,
                    stringResource(R.string.target_coordinates),
                    isDark
                )
                Spacer(Modifier.weight(1f))
                TextButton(
                    onClick = onCustomClick,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Icon(
                        Icons.Outlined.Edit,
                        null,
                        modifier = Modifier.size(15.dp),
                        tint = AccentBlue
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.custom), fontSize = 13.sp, color = AccentBlue)
                }
                Spacer(Modifier.width(4.dp))
                TextButton(
                    onClick = onSaveClick,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Icon(
                        Icons.Rounded.StarBorder,
                        null,
                        modifier = Modifier.size(16.dp),
                        tint = AccentBlue
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.save), fontSize = 13.sp, color = AccentBlue)
                }
            }
            Spacer(Modifier.height(10.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // Longitude Chip
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (isDark) Color.White.copy(alpha = 0.06f) else Color.Black.copy(alpha = 0.04f)
                        )
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Column {
                        Text(
                            text = stringResource(R.string.longitude),
                            fontSize = 11.sp,
                            color = textSecondary
                        )
                        Text(
                            text = uiState.longitudeInput.ifEmpty { "0.0" },
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )
                    }
                }

                // Latitude Chip
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (isDark) Color.White.copy(alpha = 0.06f) else Color.Black.copy(alpha = 0.04f)
                        )
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Column {
                        Text(
                            text = stringResource(R.string.latitude),
                            fontSize = 11.sp,
                            color = textSecondary
                        )
                        Text(
                            text = uiState.latitudeInput.ifEmpty { "0.0" },
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )
                    }
                }
            }

            if (uiState.pinnedCollectedLocationId != null) {
                Spacer(Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(AccentBlue.copy(alpha = 0.10f))
                        .border(0.8.dp, AccentBlue.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Rounded.PinDrop,
                            contentDescription = null,
                            tint = AccentBlue,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = stringResource(
                                R.string.pinned_collected_location_badge,
                                uiState.pinnedLocationName ?: ""
                            ),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = AccentBlue,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(AccentBlue.copy(alpha = 0.15f))
                                .noRippleClickable { viewModel.clearPinnedCollectedLocation() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Rounded.Close,
                                contentDescription = stringResource(R.string.unpin_collected_location),
                                tint = AccentBlue,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                }
            }

            if (uiState.showCoordinateError) {
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.ErrorOutline,
                        null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        stringResource(R.string.invalid_coordinates),
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

@Composable
fun coordinateFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = AccentBlue,
    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
    focusedLabelColor = AccentBlue,
    unfocusedLabelColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
    focusedTextColor = MaterialTheme.colorScheme.onBackground,
    unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
    focusedContainerColor = Color.Transparent,
    unfocusedContainerColor = Color.Transparent,
    disabledContainerColor = Color.Transparent,
    disabledBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
    disabledTextColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
    cursorColor = AccentBlue
)

@Composable
fun ActionButtons(
    viewModel: MainViewModel,
    uiState: AppState,
    onOpenMap: () -> Unit,
    onStartFixedSpoofing: () -> Unit
) {
    if (uiState.isSpoofingActive) {
        val stopColor by animateColorAsState(
            targetValue = MaterialTheme.colorScheme.error,
            animationSpec = tween(300), label = "stop_color"
        )
        Button(
            onClick = { viewModel.stopSpoofing() },
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = stopColor)
        ) {
            Icon(Icons.Rounded.Stop, null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                stringResource(R.string.stop_simulation),
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    } else {
        Button(
            onClick = onStartFixedSpoofing,
            enabled = !uiState.isSavingConfig,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)
        ) {
            if (uiState.isSavingConfig) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp
                )
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.starting_ellipsis), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            } else {
                Icon(Icons.Rounded.MyLocation, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    stringResource(R.string.fixed_simulation),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
fun UpdateCheckCard(
    isDark: Boolean,
    hasNewVersion: Boolean = false,
    newVersionName: String? = null,
    onCheckClick: () -> Unit
) {
    val textSecondary = AppColors.textSecondary(isDark)
    val cleanNewVersion = remember(newVersionName) {
        newVersionName?.trim()?.removePrefix("v")?.removePrefix("V")
    }

    MiuixCard(
        modifier = Modifier
            .fillMaxWidth()
            .noRippleClickable { onCheckClick() },
        cornerRadius = 16.dp,
        insideMargin = PaddingValues(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(
                        if (hasNewVersion) Color(0xFFE53935).copy(alpha = 0.12f)
                        else AccentBlue.copy(alpha = 0.12f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Rounded.SystemUpdateAlt,
                    null,
                    tint = if (hasNewVersion) Color(0xFFE53935) else AccentBlue,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        stringResource(R.string.check_updates),
                        color = MaterialTheme.colorScheme.onBackground,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium
                    )
                    // 发现新版本时的醒目红点指示
                    if (hasNewVersion) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFE53935))
                        )
                    }
                }
                Spacer(Modifier.height(2.dp))
                if (hasNewVersion && cleanNewVersion != null) {
                    Text(
                        text = stringResource(R.string.new_version_available_badge, cleanNewVersion),
                        color = Color(0xFFE53935),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                } else {
                    Text(
                        stringResource(R.string.check_updates_desc),
                        color = textSecondary,
                        fontSize = 12.sp
                    )
                }
            }
            if (hasNewVersion && cleanNewVersion != null) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFFE53935).copy(alpha = 0.12f))
                        .padding(horizontal = 7.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "v$cleanNewVersion",
                        color = Color(0xFFE53935),
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.width(6.dp))
            }
            Icon(
                Icons.Outlined.ChevronRight,
                null,
                tint = textSecondary.copy(alpha = 0.7f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
fun SectionHeader(icon: ImageVector, title: String, isDark: Boolean) {
    val textSecondary = AppColors.textSecondary(isDark)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = textSecondary, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            title.uppercase(),
            color = textSecondary,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.8.sp
        )
    }
}

@Composable
fun AppCoordinateConfigCard(isDark: Boolean, onClick: () -> Unit) {
    MiuixCard(
        modifier = Modifier
            .fillMaxWidth()
            .noRippleClickable { onClick() },
        cornerRadius = 16.dp,
        insideMargin = PaddingValues(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(AccentBlue.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Rounded.Extension,
                    null,
                    tint = AccentBlue,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.config_app_coordinate),
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    stringResource(R.string.config_app_coordinate_desc),
                    color = AppColors.textSecondary(isDark),
                    fontSize = 12.sp
                )
            }
            Icon(
                Icons.Rounded.ChevronRight,
                null,
                tint = AppColors.textSecondary(isDark).copy(alpha = 0.7f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
fun ScannerMapCard(
    isDark: Boolean,
    uiState: AppState,
    onClick: () -> Unit
) {
    MiuixCard(
        modifier = Modifier
            .fillMaxWidth()
            .noRippleClickable { onClick() },
        cornerRadius = 16.dp,
        insideMargin = PaddingValues(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(AccentGreen.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.Map, null, tint = AccentGreen, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.env_map_scan),
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.height(2.dp))
                val statusText = if (uiState.isContinuousScanning) {
                    stringResource(
                        R.string.scanning_reference_points,
                        uiState.environmentRecordCount
                    )
                } else {
                    stringResource(R.string.view_heatmap_start_scan)
                }
                Text(statusText, color = AppColors.textSecondary(isDark), fontSize = 12.sp)
            }
            if (uiState.isContinuousScanning) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(AccentGreen)
                )
                Spacer(Modifier.width(8.dp))
            }
            Icon(
                Icons.Rounded.ChevronRight,
                null,
                tint = AppColors.textSecondary(isDark).copy(alpha = 0.7f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
fun ManageDataCard(isDark: Boolean, onClick: () -> Unit) {
    MiuixCard(
        modifier = Modifier
            .fillMaxWidth()
            .noRippleClickable { onClick() },
        cornerRadius = 16.dp,
        insideMargin = PaddingValues(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(AccentOrange.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Rounded.FolderShared,
                    null,
                    tint = AccentOrange,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.title_manage_data),
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    stringResource(R.string.manage_collected_data_desc),
                    color = AppColors.textSecondary(isDark),
                    fontSize = 12.sp
                )
            }
            Icon(
                Icons.Rounded.ChevronRight,
                null,
                tint = AppColors.textSecondary(isDark).copy(alpha = 0.7f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
fun ImportExportDataCard(isDark: Boolean, onImportClick: () -> Unit, onExportClick: () -> Unit) {
    MiuixCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 16.dp,
        insideMargin = PaddingValues(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(AccentBlue.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Rounded.ImportExport,
                    null,
                    tint = AccentBlue,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.env_data_sharing),
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    stringResource(R.string.env_data_sharing_desc),
                    color = AppColors.textSecondary(isDark),
                    fontSize = 12.sp
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TextButton(
                    onClick = onImportClick,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(stringResource(R.string.import_data), color = AccentBlue, fontSize = 13.sp)
                }
                TextButton(
                    onClick = onExportClick,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(stringResource(R.string.export_data), color = AccentBlue, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
fun FooterLinks(isDark: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current

        // GitHub 胶囊
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(22.dp))
                .background(if (isDark) Color(0xFF24292E) else Color(0xFF24292E))
                .noRippleClickable { uriHandler.openUri("https://github.com/HuangZhuoRui/LocationSpoofer") }
                .padding(horizontal = 16.dp, vertical = 9.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = androidx.compose.ui.res.painterResource(R.drawable.ic_github),
                    contentDescription = stringResource(R.string.brand_github),
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.brand_github),
                    color = Color.White,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Spacer(Modifier.width(16.dp))

        // Telegram 胶囊
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(22.dp))
                .background(if (isDark) Color(0xFF24A1DE).copy(alpha = 0.22f) else Color(0xFFE8F4FA))
                .noRippleClickable { uriHandler.openUri("https://t.me/+CsxZGItXdW40ZWVl") }
                .padding(horizontal = 16.dp, vertical = 9.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = androidx.compose.ui.res.painterResource(R.drawable.ic_telegram),
                    contentDescription = stringResource(R.string.brand_telegram),
                    tint = Color.Unspecified,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.brand_telegram),
                    color = Color(0xFF24A1DE),
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
