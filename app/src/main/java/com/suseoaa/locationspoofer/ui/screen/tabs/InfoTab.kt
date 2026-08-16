package com.suseoaa.locationspoofer.ui.screen.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.remember
import com.suseoaa.locationspoofer.BuildConfig
import com.suseoaa.locationspoofer.data.model.AppState
import com.suseoaa.locationspoofer.ui.screen.SettingsScreen
import com.suseoaa.locationspoofer.ui.screen.UpdateCheckCard
import com.suseoaa.locationspoofer.ui.screen.isNewerVersion
import com.suseoaa.locationspoofer.ui.theme.AccentGreen
import com.suseoaa.locationspoofer.ui.theme.AppColors
import com.suseoaa.locationspoofer.viewmodel.MainViewModel
import com.suseoaa.locationspoofer.viewmodel.UpdateUiState

@Composable
fun InfoTab(
    viewModel: MainViewModel,
    uiState: AppState,
    updateUiState: UpdateUiState? = null,
    tabBarHeight: Dp = 90.dp,
    onNavigateToUpdate: () -> Unit = {}
) {
    val isDark = isSystemInDarkTheme()
    val currentVersion = BuildConfig.VERSION_NAME
    val latestRelease = updateUiState?.releases?.firstOrNull()
    val hasNewVersion = remember(updateUiState?.releases) {
        latestRelease != null && isNewerVersion(latestRelease.versionName, currentVersion)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.background(isDark))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = tabBarHeight + 24.dp)
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
                        .background(AccentGreen.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.Info, null, tint = AccentGreen)
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        text = "信息",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "版本、更新与应用设置",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                    )
                }
            }
            
            Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                UpdateCheckCard(
                    isDark = isDark,
                    hasNewVersion = hasNewVersion,
                    newVersionName = if (hasNewVersion) latestRelease?.versionName else null,
                    onCheckClick = onNavigateToUpdate
                )
            }

            SettingsScreen(
                viewModel = viewModel,
                uiState = uiState,
                onClose = {}
            )
        }
    }
}
