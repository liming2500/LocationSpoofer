package com.suseoaa.locationspoofer.ui.screen.tabs

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.suseoaa.locationspoofer.BuildConfig
import com.suseoaa.locationspoofer.R
import com.suseoaa.locationspoofer.data.model.AppState
import com.suseoaa.locationspoofer.data.model.MapEngine
import com.suseoaa.locationspoofer.ui.screen.FooterLinks
import com.suseoaa.locationspoofer.ui.screen.LANGUAGES
import com.suseoaa.locationspoofer.ui.screen.UpdateCheckCard
import com.suseoaa.locationspoofer.ui.screen.isNewerVersion
import com.suseoaa.locationspoofer.ui.theme.AccentBlue
import com.suseoaa.locationspoofer.ui.theme.AccentGreen
import com.suseoaa.locationspoofer.ui.theme.AppColors
import com.suseoaa.locationspoofer.ui.theme.noRippleClickable
import com.suseoaa.locationspoofer.viewmodel.MainViewModel
import com.suseoaa.locationspoofer.viewmodel.UpdateUiState
import top.yukonga.miuix.kmp.basic.Card as MiuixCard

@Composable
fun InfoTab(
    viewModel: MainViewModel,
    uiState: AppState,
    updateUiState: UpdateUiState? = null,
    tabBarHeight: Dp = 90.dp,
    onNavigateToUpdate: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {}
) {
    val isDark = isSystemInDarkTheme()

    val currentVersion = BuildConfig.VERSION_NAME
    val latestRelease = updateUiState?.releases?.firstOrNull()
    val hasNewVersion = remember(updateUiState?.releases) {
        latestRelease != null && isNewerVersion(latestRelease.versionName, currentVersion)
    }

    // 获取当前生效的语言名称
    val savedLangCode = viewModel.getSavedLanguage()
    val currentLangName = remember(savedLangCode) {
        LANGUAGES.firstOrNull { it.code == savedLangCode }?.nativeName ?: "默认语言"
    }

    // 获取当前地图引擎名称
    val currentEngineName = remember(uiState.mapEngine) {
        when (uiState.mapEngine) {
            MapEngine.AUTO -> "自动选择"
            MapEngine.AMAP -> "高德地图"
            MapEngine.BAIDU -> "百度地图"
            MapEngine.GOOGLE -> "谷歌地图"
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.background(isDark))
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(bottom = tabBarHeight + 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 顶部软件品牌与简介 Hero 区域（替换原先单调的信息图标与标题）
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(top = 18.dp, bottom = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // 软件图标（原生平滑圆角与柔和阴影，无外层黑边缝隙）
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .shadow(
                                elevation = 6.dp,
                                shape = RoundedCornerShape(18.dp),
                                clip = false
                            )
                            .clip(RoundedCornerShape(18.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        AndroidView(
                            factory = { ctx ->
                                android.widget.ImageView(ctx).apply {
                                    scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
                                    try {
                                        val icon =
                                            ctx.packageManager.getApplicationIcon(ctx.packageName)
                                        setImageDrawable(icon)
                                    } catch (e: Exception) {
                                        // fallback
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    Spacer(Modifier.height(14.dp))

                    // 软件名称
                    Text(
                        text = stringResource(R.string.app_name),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )

                    Spacer(Modifier.height(6.dp))

                    // 软件简介介绍
                    Text(
                        text = "这是一个模拟定位软件，仅此而已",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 20.dp)
                    )
                }
            }

            // 1. 软件更新卡片
            item {
                UpdateCheckCard(
                    isDark = isDark,
                    hasNewVersion = hasNewVersion,
                    newVersionName = if (hasNewVersion) latestRelease?.versionName else null,
                    onCheckClick = onNavigateToUpdate
                )
            }

            // 2. 软件配置聚合卡片 (选择语言、地图配置、数据源Token合并入口)
            item {
                MiuixCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .noRippleClickable(onClick = onNavigateToSettings),
                    cornerRadius = 18.dp,
                    insideMargin = PaddingValues(16.dp)
                ) {
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
                                imageVector = Icons.Rounded.Tune,
                                contentDescription = null,
                                tint = AccentBlue,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        Spacer(Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "软件配置",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(Modifier.height(3.dp))
                            Text(
                                text = "语言设置、地图引擎与数据源",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )

                            Spacer(Modifier.height(8.dp))

                            // 状态预览芯片
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(AccentBlue.copy(alpha = if (isDark) 0.14f else 0.08f))
                                        .padding(horizontal = 7.dp, vertical = 2.5.dp)
                                ) {
                                    Text(
                                        text = currentLangName,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = AccentBlue
                                    )
                                }

                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(AccentGreen.copy(alpha = if (isDark) 0.14f else 0.08f))
                                        .padding(horizontal = 7.dp, vertical = 2.5.dp)
                                ) {
                                    Text(
                                        text = currentEngineName,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = AccentGreen
                                    )
                                }
                            }
                        }

                        Icon(
                            imageVector = Icons.Rounded.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // 3. 底部开源协议与项目链接
            item {
                Spacer(Modifier.height(6.dp))
                FooterLinks(isDark = isDark)
            }
        }
    }
}
