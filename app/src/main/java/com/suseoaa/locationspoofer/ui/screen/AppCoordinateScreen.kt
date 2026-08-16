package com.suseoaa.locationspoofer.ui.screen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.suseoaa.locationspoofer.R
import com.suseoaa.locationspoofer.data.model.AppInfoItem
import com.suseoaa.locationspoofer.data.model.AppState
import com.suseoaa.locationspoofer.ui.theme.AccentBlue
import com.suseoaa.locationspoofer.ui.theme.AppColors
import com.suseoaa.locationspoofer.viewmodel.MainViewModel
import top.yukonga.miuix.kmp.basic.Card as MiuixCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppCoordinateScreen(
    viewModel: MainViewModel,
    uiState: AppState,
    onBack: () -> Unit
) {
    var showSystemApps by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedApp by remember { mutableStateOf<AppInfoItem?>(null) }
    val isDark = isSystemInDarkTheme()

    BackHandler {
        onBack()
    }

    val appsToShow = remember(uiState.hookedApps, showSystemApps, searchQuery) {
        uiState.hookedApps.filter { app ->
            (showSystemApps || !app.isSystem) &&
                    (searchQuery.isBlank() ||
                            app.appName.contains(searchQuery, ignoreCase = true) ||
                            app.packageName.contains(searchQuery, ignoreCase = true))
        }
    }

    Scaffold(
        containerColor = AppColors.background(isDark),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.custom_coordinate_algo),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(end = 12.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.show_system_apps),
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                        )
                        Spacer(Modifier.width(6.dp))
                        Switch(
                            checked = showSystemApps,
                            onCheckedChange = { showSystemApps = it }
                        )
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Search Input Card
            Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("搜索应用名称或包名...", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)) },
                    leadingIcon = { Icon(Icons.Rounded.Search, null, tint = AccentBlue, modifier = Modifier.size(20.dp)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AccentBlue,
                        unfocusedBorderColor = Color.Transparent,
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface
                    )
                )
            }

            if (appsToShow.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        stringResource(R.string.no_hooked_apps),
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                        fontSize = 14.sp
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(appsToShow, key = { it.packageName }) { app ->
                        val currentSys = uiState.appCoordinateSystems[app.packageName]
                        AppItemCard(
                            appInfo = app,
                            currentCoordinateSystem = currentSys,
                            isDark = isDark,
                            onClick = { selectedApp = app }
                        )
                    }
                }
            }
        }

        // Selection Dialog
        selectedApp?.let { app ->
            CoordinateSelectionDialog(
                appInfo = app,
                currentSystem = uiState.appCoordinateSystems[app.packageName] ?: "GCJ-02",
                onDismiss = { selectedApp = null },
                onSelect = { sys ->
                    if (sys == "GCJ-02") {
                        viewModel.removeAppCoordinateSystem(app.packageName)
                    } else {
                        viewModel.setAppCoordinateSystem(app.packageName, sys)
                    }
                    selectedApp = null
                }
            )
        }
    }
}

@Composable
fun AppItemCard(
    appInfo: AppInfoItem,
    currentCoordinateSystem: String?,
    isDark: Boolean,
    onClick: () -> Unit
) {
    MiuixCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        cornerRadius = 14.dp,
        insideMargin = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isDark) Color.White.copy(alpha = 0.05f) else Color.Black.copy(alpha = 0.03f)),
                contentAlignment = Alignment.Center
            ) {
                AppIconImage(
                    packageName = appInfo.packageName,
                    modifier = Modifier.size(36.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = appInfo.appName,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = appInfo.packageName,
                    fontSize = 11.5.sp,
                    color = AppColors.textSecondary(isDark),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(8.dp))

            // Coordinate System Chip
            val isCustom = currentCoordinateSystem != null && currentCoordinateSystem != "GCJ-02"
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (isCustom) AccentBlue.copy(alpha = 0.15f)
                        else if (isDark) Color.White.copy(alpha = 0.06f)
                        else Color.Black.copy(alpha = 0.05f)
                    )
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = currentCoordinateSystem ?: "GCJ-02",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isCustom) AccentBlue else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }
    }
}

@Composable
fun AppIconImage(packageName: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    AndroidView(
        factory = { ctx ->
            android.widget.ImageView(ctx).apply {
                scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
            }
        },
        update = { imageView ->
            try {
                val icon = context.packageManager.getApplicationIcon(packageName)
                imageView.setImageDrawable(icon)
            } catch (e: Exception) {
                imageView.setImageDrawable(null)
            }
        },
        modifier = modifier
    )
}

@Composable
fun CoordinateSelectionDialog(
    appInfo: AppInfoItem,
    currentSystem: String,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit
) {
    val isDark = isSystemInDarkTheme()
    val systems = listOf(
        "GCJ-02" to stringResource(R.string.gcj02_desc),
        "WGS-84" to stringResource(R.string.wgs84_desc),
        "BD-09" to stringResource(R.string.bd09_desc)
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.set_coordinate_algo),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.select_coord_sys_desc, appInfo.appName),
                    fontSize = 13.5.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                Spacer(modifier = Modifier.height(14.dp))
                systems.forEach { (sys, desc) ->
                    val isSelected = sys == currentSystem
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (isSelected) AccentBlue.copy(alpha = 0.14f)
                                else if (isDark) Color.White.copy(alpha = 0.05f)
                                else Color.Black.copy(alpha = 0.04f)
                            )
                            .clickable { onSelect(sys) }
                            .padding(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = sys,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) AccentBlue else MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = desc,
                                    fontSize = 11.5.sp,
                                    color = if (isSelected) AccentBlue.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            }
                            if (isSelected) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = "Selected",
                                    tint = AccentBlue,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel), color = AccentBlue)
            }
        }
    )
}
