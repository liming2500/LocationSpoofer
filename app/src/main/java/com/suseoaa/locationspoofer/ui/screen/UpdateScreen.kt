package com.suseoaa.locationspoofer.ui.screen

import android.app.DownloadManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.suseoaa.locationspoofer.BuildConfig
import com.suseoaa.locationspoofer.R
import com.suseoaa.locationspoofer.data.model.GithubRelease
import com.suseoaa.locationspoofer.ui.theme.AccentBlue
import com.suseoaa.locationspoofer.ui.theme.AccentGreen
import com.suseoaa.locationspoofer.ui.theme.AppColors
import com.suseoaa.locationspoofer.ui.theme.noRippleClickable
import com.suseoaa.locationspoofer.viewmodel.MainViewModel
import com.suseoaa.locationspoofer.viewmodel.UpdateUiState
import com.suseoaa.locationspoofer.viewmodel.UpdateViewModel
import top.yukonga.miuix.kmp.basic.Card as MiuixCard
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop

@Composable
fun UpdateScreen(
    updateViewModel: UpdateViewModel,
    viewModel: MainViewModel,
    isDark: Boolean = isSystemInDarkTheme(),
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val uiState by updateViewModel.uiState.collectAsState()
    val currentVersion = BuildConfig.VERSION_NAME
    val listState = rememberLazyListState()
    val backdrop = rememberLayerBackdrop()

    BackHandler(onBack = onBack)

    // 进入页面时自动检查一次最新版本
    LaunchedEffect(Unit) {
        if (uiState.releases.isEmpty()) {
            updateViewModel.fetchReleases()
        }
    }

    // 查找比当前版本更新的未升级版本
    val missed = remember(uiState.releases) {
        uiState.releases.filter { isNewerVersion(it.versionName, currentVersion) }
    }

    val hasNewVersion = missed.isNotEmpty()
    val latestRelease = uiState.releases.firstOrNull()

    val displayList = remember(uiState.releases, missed) {
        if (missed.size > 1) {
            val latest = missed.first()
            val grouped = parseAndCategorizeReleaseNotes(missed)
            val mergedBody = generateMergedMarkdown(context, grouped)
            val mergedRelease = latest.copy(body = mergedBody)
            val historical = uiState.releases.filter { it !in missed }
            listOf(mergedRelease) + historical
        } else {
            uiState.releases
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.background(isDark))
    ) {
        // 主内容列表
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .layerBackdrop(backdrop)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 112.dp, bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 大标题区域（距离顶部充裕舒展）
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "软件更新",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "检查并获取最新发布版本与功能改进",
                        fontSize = 13.5.sp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f)
                    )
                }
            }

            // 当前版本卡片（无大图标，Miuix 质感卡片，仅最新版支持忽略）
            item {
                CurrentVersionHeroCard(
                    uiState = uiState,
                    hasNewVersion = hasNewVersion,
                    currentVersion = currentVersion,
                    latestRelease = latestRelease,
                    isDark = isDark,
                    onCheckAgain = { updateViewModel.fetchReleases() },
                    onDownload = { url, version -> updateViewModel.startDownload(url, version) },
                    onInstall = { updateViewModel.installApk() },
                    onCancelDownload = { updateViewModel.cancelDownload() },
                    onIgnore = { version ->
                        viewModel.setIgnoredVersion(version)
                        onBack()
                    }
                )
            }

            // 更新日志及历史版本列表
            if (displayList.isNotEmpty()) {
                item {
                    Text(
                        text = if (hasNewVersion) "更新日志" else "版本历史",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                    )
                }

                items(displayList) { release ->
                    val isCurrent =
                        release.versionName.contains(BuildConfig.VERSION_NAME) ||
                                BuildConfig.VERSION_NAME.contains(release.versionName)
                    val isMerged = missed.size > 1 && release == displayList.first()

                    ReleaseItemCard(
                        release = release,
                        isCurrentVersion = isCurrent,
                        isMergedRelease = isMerged,
                        mergedCount = missed.size,
                        uiState = uiState,
                        onDownload = { url, version -> updateViewModel.startDownload(url, version) }
                    )
                }
            }
        }

        // 顶部边界模糊渐变遮罩（消除生硬边缘，平滑模糊溶解）
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(96.dp)
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            AppColors.background(isDark),
                            AppColors.background(isDark).copy(alpha = 0.92f),
                            AppColors.background(isDark).copy(alpha = 0.45f),
                            Color.Transparent
                        )
                    )
                )
        )

        // 顶部悬浮栏（独立圆形返回按钮，无顶栏文字干扰）
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .align(Alignment.TopStart),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 独立的圆形立体纯白返回按钮
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .shadow(elevation = 6.dp, shape = CircleShape, clip = false)
                    .clip(CircleShape)
                    .background(if (isDark) Color(0xFF22272E) else Color.White)
                    .border(
                        width = 1.dp,
                        color = if (isDark) Color.White.copy(alpha = 0.14f) else Color(0xFFE5E8EC),
                        shape = CircleShape
                    )
                    .noRippleClickable(onClick = onBack),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = stringResource(R.string.back),
                    tint = if (isDark) Color.White else Color(0xFF1A1D20),
                    modifier = Modifier.size(21.dp)
                )
            }
        }
    }
}

@Composable
private fun CurrentVersionHeroCard(
    uiState: UpdateUiState,
    hasNewVersion: Boolean,
    currentVersion: String,
    latestRelease: GithubRelease?,
    isDark: Boolean,
    onCheckAgain: () -> Unit,
    onDownload: (String, String) -> Unit,
    onInstall: () -> Unit,
    onCancelDownload: () -> Unit,
    onIgnore: (String) -> Unit
) {
    MiuixCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 18.dp,
        insideMargin = PaddingValues(horizontal = 20.dp, vertical = 18.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 第一行：应用名与当前安装版本号徽章
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "位置模拟器",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "Location Spoofer",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }

                val cleanCurrentVersion = currentVersion.trim().removePrefix("v").removePrefix("V")
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.05f)
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "v$cleanCurrentVersion",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))

            // 状态标签
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(13.dp),
                        strokeWidth = 2.dp,
                        color = AccentBlue
                    )
                    Text(
                        text = "正在检索服务器最新发布版本...",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                } else if (uiState.error != null) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.error)
                    )
                    Text(
                        text = "检查失败，请检查网络连接",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.error
                    )
                } else if (hasNewVersion && latestRelease != null) {
                    val cleanLatestVersion =
                        latestRelease.versionName.trim().removePrefix("v").removePrefix("V")
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFE53935))
                    )
                    Text(
                        text = "发现新版本 v$cleanLatestVersion",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFE53935)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(AccentGreen)
                    )
                    Text(
                        text = "当前已是最新版本",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = AccentGreen
                    )
                }
            }

            // 下载进度或操作按钮（非全宽，精致尺寸，最新版本独有忽略此版本选项）
            val isDownloading = uiState.activeDownloadId != null
            if (isDownloading) {
                if (uiState.downloadStatus == DownloadManager.STATUS_SUCCESSFUL) {
                    Button(
                        onClick = onInstall,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentGreen),
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                        modifier = Modifier.height(40.dp)
                    ) {
                        Text("安装新版本", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "正在下载更新 (${uiState.downloadProgress}%)",
                                fontSize = 12.5.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                            )
                            Text(
                                text = "取消",
                                fontSize = 12.5.sp,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.noRippleClickable(onClick = onCancelDownload)
                            )
                        }
                        LinearProgressIndicator(
                            progress = { uiState.downloadProgress / 100f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = AccentBlue,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    }
                }
            } else if (hasNewVersion && latestRelease != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (latestRelease.downloadUrl != null) {
                            Button(
                                onClick = {
                                    onDownload(latestRelease.downloadUrl, latestRelease.versionName)
                                },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
                                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                                modifier = Modifier.height(40.dp)
                            ) {
                                Text(
                                    text = if (latestRelease.downloadUrl32Bit != null) "立即更新 (64位)" else "立即更新",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        if (latestRelease.downloadUrl32Bit != null) {
                            OutlinedButton(
                                onClick = {
                                    onDownload(
                                        latestRelease.downloadUrl32Bit,
                                        "${latestRelease.versionName}_32bit"
                                    )
                                },
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                                modifier = Modifier.height(40.dp)
                            ) {
                                Text("32位兼容包", fontSize = 13.sp, color = AccentBlue)
                            }
                        }
                    }

                    // 仅最新版显示“忽略此版本”
                    Text(
                        text = "忽略此版本",
                        fontSize = 12.5.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                        modifier = Modifier
                            .padding(end = 4.dp)
                            .noRippleClickable { onIgnore(latestRelease.versionName) }
                    )
                }
            } else {
                OutlinedButton(
                    onClick = onCheckAgain,
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                    modifier = Modifier.height(38.dp)
                ) {
                    Text("重新检查", fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun ReleaseItemCard(
    release: GithubRelease,
    isCurrentVersion: Boolean,
    isMergedRelease: Boolean,
    mergedCount: Int,
    uiState: UpdateUiState,
    onDownload: (String, String) -> Unit
) {
    MiuixCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 18.dp,
        insideMargin = PaddingValues(16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val cleanReleaseVersion =
                        release.versionName.trim().removePrefix("v").removePrefix("V")
                    Text(
                        text = "v$cleanReleaseVersion",
                        fontSize = 16.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    if (isCurrentVersion) {
                        Spacer(Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(AccentGreen.copy(alpha = 0.15f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "当前版本",
                                fontSize = 10.5.sp,
                                color = AccentGreen,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    if (isMergedRelease) {
                        Spacer(Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(AccentBlue.copy(alpha = 0.15f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "聚合 $mergedCount 个更新",
                                fontSize = 10.5.sp,
                                color = AccentBlue,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                if (release.publishedAt.isNotBlank()) {
                    Text(
                        text = release.publishedAt.take(10),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))

            // 更新说明详情（结构化渲染）
            RenderMarkdownContent(markdown = release.body)

            // 操作按键（历史版本仅提供下载操作，不显示忽略）
            if (!isCurrentVersion && (release.downloadUrl != null || release.downloadUrl32Bit != null)) {
                Spacer(Modifier.height(2.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (release.downloadUrl != null) {
                            Button(
                                onClick = {
                                    onDownload(release.downloadUrl, release.versionName)
                                },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                                modifier = Modifier.height(34.dp)
                            ) {
                                Text(
                                    text = if (release.downloadUrl32Bit != null) "下载 (64位)" else "下载",
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // 仅当包含 32 位安装包时展示
                        if (release.downloadUrl32Bit != null) {
                            OutlinedButton(
                                onClick = {
                                    onDownload(
                                        release.downloadUrl32Bit,
                                        "${release.versionName}_32bit"
                                    )
                                },
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier.height(34.dp)
                            ) {
                                Text("下载 32位", fontSize = 12.sp, color = AccentBlue)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StartupUpdateDialog(
    latestRelease: GithubRelease,
    onDismiss: () -> Unit,
    onNavigateToUpdate: () -> Unit,
    onIgnore: (String) -> Unit
) {
    val cleanVersion = remember(latestRelease.versionName) {
        latestRelease.versionName.trim().removePrefix("v").removePrefix("V")
    }

    Dialog(onDismissRequest = onDismiss) {
        MiuixCard(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 22.dp,
            insideMargin = PaddingValues(22.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // 顶部标题与红点徽章
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(9.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFE53935))
                        )
                        Text(
                            text = "发现新版本",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(AccentBlue.copy(alpha = 0.12f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "v$cleanVersion",
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = AccentBlue
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))

                // 更新日志简报
                if (latestRelease.body.isNotBlank()) {
                    Box(modifier = Modifier.heightIn(max = 260.dp)) {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            item {
                                RenderMarkdownContent(markdown = latestRelease.body)
                            }
                        }
                    }
                } else {
                    Text(
                        text = "新版本已发布，推荐立即更新以获取全新特性与性能优化。",
                        fontSize = 13.5.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))

                // 底部操作区（左侧忽略此版本，右侧以后再说与前往更新）
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "忽略此版本",
                        fontSize = 12.5.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                        modifier = Modifier.noRippleClickable {
                            onIgnore(latestRelease.versionName)
                            onDismiss()
                        }
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TextButton(
                            onClick = onDismiss,
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(
                                "以后再说",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }

                        Button(
                            onClick = {
                                onDismiss()
                                onNavigateToUpdate()
                            },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Text("前往更新", fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

