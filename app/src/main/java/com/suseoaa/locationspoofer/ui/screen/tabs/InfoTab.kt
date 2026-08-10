package com.suseoaa.locationspoofer.ui.screen.tabs

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.suseoaa.locationspoofer.data.model.AppState
import com.suseoaa.locationspoofer.ui.screen.SettingsScreen
import com.suseoaa.locationspoofer.ui.screen.UpdateDialog
import com.suseoaa.locationspoofer.ui.screen.UpdateCheckCard
import com.suseoaa.locationspoofer.ui.screen.spoofing.SpoofingIntent
import com.suseoaa.locationspoofer.viewmodel.UpdateViewModel
import com.suseoaa.locationspoofer.viewmodel.MainViewModel
import org.koin.androidx.compose.koinViewModel

@Composable
fun InfoTab(viewModel: MainViewModel, uiState: AppState) {
    val isDark = isSystemInDarkTheme()
    val onIntent = { intent: SpoofingIntent -> viewModel.handleSpoofingIntent(intent) }
    val spoofingUiState by viewModel.spoofingUiState.collectAsState()
    val updateViewModel: UpdateViewModel = koinViewModel()
    val updateUiState by updateViewModel.uiState.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(com.suseoaa.locationspoofer.ui.theme.AppColors.background(isDark))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 100.dp)
        ) {
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
                        .background(com.suseoaa.locationspoofer.ui.theme.AccentGreen.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.Info, null, tint = com.suseoaa.locationspoofer.ui.theme.AccentGreen)
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    androidx.compose.material3.Text(
                        text = "信息",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    androidx.compose.material3.Text(
                        text = "版本、更新与应用设置",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                    )
                }
            }
            
            Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                UpdateCheckCard(isDark = isDark, onCheckClick = { 
                    onIntent(SpoofingIntent.SetUpdateDialogVisible(true)) 
                })
            }
            SettingsScreen(
                viewModel = viewModel,
                uiState = uiState,
                onClose = {}
            )
        }
    }

    if (spoofingUiState.showUpdateDialog) {
        UpdateDialog(
            uiState = updateUiState,
            onDismiss = { onIntent(SpoofingIntent.SetUpdateDialogVisible(false)) },
            onDownload = { url, version -> updateViewModel.startDownload(url, version) },
            onCancel = { updateViewModel.cancelDownload() },
            onInstall = { updateViewModel.installApk() },
            onIgnore = { version ->
                viewModel.setIgnoredVersion(version)
                onIntent(SpoofingIntent.SetUpdateDialogVisible(false))
            }
        )
    }
}
