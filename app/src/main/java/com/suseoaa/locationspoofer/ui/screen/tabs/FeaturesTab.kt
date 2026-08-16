package com.suseoaa.locationspoofer.ui.screen.tabs

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.suseoaa.locationspoofer.data.model.AppState
import com.suseoaa.locationspoofer.ui.screen.AppCoordinateConfigCard
import com.suseoaa.locationspoofer.ui.screen.ImportExportDataCard
import com.suseoaa.locationspoofer.ui.screen.ManageDataCard
import com.suseoaa.locationspoofer.ui.screen.ScannerMapCard
import com.suseoaa.locationspoofer.viewmodel.MainViewModel

@Composable
fun FeaturesTab(
    viewModel: MainViewModel,
    uiState: AppState,
    tabBarHeight: Dp = 90.dp,
    onNavigateToCoordinate: () -> Unit,
    onNavigateToScanner: () -> Unit,
    onNavigateToManageData: () -> Unit
) {
    val isDark = isSystemInDarkTheme()
    val context = LocalContext.current
    
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let {
            viewModel.exportEnvironmentData(it)
            Toast.makeText(context, "导出成功", Toast.LENGTH_SHORT).show()
        }
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            viewModel.importEnvironmentData(it) {
                Toast.makeText(context, "导入合并成功", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(com.suseoaa.locationspoofer.ui.theme.AppColors.background(isDark))
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(13.dp))
                        .background(com.suseoaa.locationspoofer.ui.theme.AccentBlue.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.Extension, null, tint = com.suseoaa.locationspoofer.ui.theme.AccentBlue)
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        text = "功能",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "管理工具与本地环境数据",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                    )
                }
            }
            
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(bottom = tabBarHeight + 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    AppCoordinateConfigCard(
                        isDark = isDark,
                        onClick = onNavigateToCoordinate
                    )
                }

                item {
                    ScannerMapCard(
                        uiState = uiState,
                        isDark = isDark,
                        onClick = onNavigateToScanner
                    )
                }
                
                item {
                    ManageDataCard(
                        isDark = isDark,
                        onClick = onNavigateToManageData
                    )
                }
                
                item {
                    ImportExportDataCard(
                        isDark = isDark,
                        onImportClick = {
                            importLauncher.launch(arrayOf("application/json", "*/*"))
                        },
                        onExportClick = {
                            exportLauncher.launch("environment_data.json")
                        }
                    )
                }
            }
        }
    }
}

