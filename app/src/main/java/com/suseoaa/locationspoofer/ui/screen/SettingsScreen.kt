package com.suseoaa.locationspoofer.ui.screen

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.ContentCopy
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.os.LocaleListCompat
import com.suseoaa.locationspoofer.R
import com.suseoaa.locationspoofer.data.model.AppState
import com.suseoaa.locationspoofer.data.model.MapEngine
import com.suseoaa.locationspoofer.data.model.RootSetupTestResult
import com.suseoaa.locationspoofer.data.model.RootSolution
import com.suseoaa.locationspoofer.ui.theme.AccentBlue
import com.suseoaa.locationspoofer.ui.theme.AccentGreen
import com.suseoaa.locationspoofer.ui.theme.AccentOrange
import com.suseoaa.locationspoofer.ui.theme.AppColors
import com.suseoaa.locationspoofer.ui.theme.noRippleClickable
import com.suseoaa.locationspoofer.viewmodel.MainViewModel
import top.yukonga.miuix.kmp.basic.Card as MiuixCard

@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    uiState: AppState,
    isDark: Boolean = isSystemInDarkTheme(),
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    var localAmapApiKey by remember(uiState.amapApiKey) { mutableStateOf(uiState.amapApiKey) }
    var localBaiduApiKey by remember(uiState.baiduApiKey) { mutableStateOf(uiState.baiduApiKey) }
    var localGoogleApiKey by remember(uiState.googleApiKey) { mutableStateOf(uiState.googleApiKey) }
    var localWigleToken by remember(uiState.wigleToken) { mutableStateOf(uiState.wigleToken) }
    var localOpencellidToken by remember(uiState.opencellidToken) { mutableStateOf(uiState.opencellidToken) }

    BackHandler(onBack = onClose)

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
            // 顶部导航栏（独立 44dp 圆形返回胶囊 + 标题）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 独立立体陶瓷白圆形返回按钮
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
                        text = stringResource(R.string.software_config),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = stringResource(R.string.settings_subtitle),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                    )
                }
            }

            // 内容可滚动区域
            Box(modifier = Modifier
                .weight(1f)
                .fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // 1. 语言设置卡片
                    MiuixCard(
                        modifier = Modifier.fillMaxWidth(),
                        cornerRadius = 18.dp,
                        insideMargin = PaddingValues(16.dp)
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
                                    Icons.Rounded.Language,
                                    null,
                                    tint = AccentBlue,
                                    modifier = Modifier.size(19.dp)
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = stringResource(R.string.select_language),
                                    fontSize = 15.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = stringResource(R.string.switch_language_desc),
                                    fontSize = 11.5.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                                )
                            }
                        }

                        Spacer(Modifier.height(14.dp))

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            LANGUAGES.forEach { lang ->
                                val isSelected = viewModel.getSavedLanguage() == lang.code
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(
                                            if (isSelected) AccentBlue.copy(alpha = if (isDark) 0.14f else 0.09f)
                                            else if (isDark) Color.White.copy(alpha = 0.04f)
                                            else Color.Black.copy(alpha = 0.03f)
                                        )
                                        .border(
                                            width = if (isSelected) 1.2.dp else 0.5.dp,
                                            color = if (isSelected) AccentBlue else MaterialTheme.colorScheme.outline.copy(
                                                alpha = if (isDark) 0.08f else 0.04f
                                            ),
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                        .noRippleClickable {
                                            viewModel.selectLanguage(lang.code)
                                            AppCompatDelegate.setApplicationLocales(
                                                LocaleListCompat.forLanguageTags(lang.code)
                                            )
                                        }
                                        .padding(horizontal = 14.dp, vertical = 12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = lang.nativeName,
                                            fontSize = 14.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isSelected) AccentBlue else MaterialTheme.colorScheme.onSurface
                                        )
                                        if (isSelected) {
                                            Icon(
                                                Icons.Rounded.Check,
                                                contentDescription = null,
                                                tint = AccentBlue,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 2. 地图引擎与密钥配置卡片
                    MiuixCard(
                        modifier = Modifier.fillMaxWidth(),
                        cornerRadius = 18.dp,
                        insideMargin = PaddingValues(16.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(11.dp))
                                    .background(AccentGreen.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Rounded.Map,
                                    null,
                                    tint = AccentGreen,
                                    modifier = Modifier.size(19.dp)
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = stringResource(R.string.map_config),
                                    fontSize = 15.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = stringResource(R.string.map_engine_and_keys),
                                    fontSize = 11.5.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                                )
                            }
                        }

                        Spacer(Modifier.height(14.dp))

                        // 地图引擎 4 分段选择器
                        Text(
                            text = stringResource(R.string.current_engine),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                        Spacer(Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val engines = listOf(
                                MapEngine.AUTO to stringResource(R.string.engine_auto_short),
                                MapEngine.AMAP to stringResource(R.string.engine_amap_short),
                                MapEngine.BAIDU to stringResource(R.string.engine_baidu_short),
                                MapEngine.GOOGLE to stringResource(R.string.engine_google_short)
                            )
                            engines.forEach { (engine, label) ->
                                val isSelected = uiState.mapEngine == engine
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(
                                            if (isSelected) AccentBlue
                                            else if (isDark) Color.White.copy(alpha = 0.05f)
                                            else Color.Black.copy(alpha = 0.04f)
                                        )
                                        .noRippleClickable { viewModel.setMapEngine(engine) }
                                        .padding(vertical = 9.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = label,
                                        color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface,
                                        fontSize = 12.5.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }

                        // 各引擎自定义 API Key 输入
                        AnimatedVisibility(
                            visible = uiState.mapEngine == MapEngine.AMAP || uiState.mapEngine == MapEngine.BAIDU || uiState.mapEngine == MapEngine.GOOGLE,
                            enter = fadeIn(),
                            exit = fadeOut()
                        ) {
                            Column(modifier = Modifier.padding(top = 14.dp)) {
                                when (uiState.mapEngine) {
                                    MapEngine.AMAP -> {
                                        ModernSettingsInput(
                                            label = stringResource(R.string.custom_amap_key),
                                            value = localAmapApiKey,
                                            onValueChange = { localAmapApiKey = it },
                                            placeholder = stringResource(R.string.custom_amap_key_hint),
                                            isDark = isDark
                                        )
                                    }

                                    MapEngine.BAIDU -> {
                                        ModernSettingsInput(
                                            label = stringResource(R.string.custom_baidu_key),
                                            value = localBaiduApiKey,
                                            onValueChange = { localBaiduApiKey = it },
                                            placeholder = stringResource(R.string.custom_baidu_key_hint),
                                            isDark = isDark
                                        )
                                    }

                                    MapEngine.GOOGLE -> {
                                        ModernSettingsInput(
                                            label = stringResource(R.string.custom_google_key),
                                            value = localGoogleApiKey,
                                            onValueChange = { localGoogleApiKey = it },
                                            placeholder = stringResource(R.string.custom_google_key_hint),
                                            isDark = isDark
                                        )
                                    }

                                    else -> Unit
                                }
                            }
                        }
                    }

                    // 3. 运行环境与签名鉴权卡片 (用于第三方地图SDK鉴权与开放平台Key申请)
                    MiuixCard(
                        modifier = Modifier.fillMaxWidth(),
                        cornerRadius = 18.dp,
                        insideMargin = PaddingValues(16.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(11.dp))
                                    .background(AccentOrange.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Rounded.Security,
                                    null,
                                    tint = AccentOrange,
                                    modifier = Modifier.size(19.dp)
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = stringResource(R.string.env_and_signature),
                                    fontSize = 15.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = stringResource(R.string.api_key_binding_desc),
                                    fontSize = 11.5.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                                )
                            }
                        }

                        Spacer(Modifier.height(14.dp))

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            // 应用包名条目（点击复制）
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        if (isDark) Color.White.copy(alpha = 0.04f) else Color.Black.copy(
                                            alpha = 0.03f
                                        )
                                    )
                                    .noRippleClickable {
                                        clipboardManager.setText(AnnotatedString(context.packageName))
                                        Toast.makeText(
                                            context,
                                            context.getString(R.string.copied_to_clipboard),
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(R.string.app_package_name),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                    )
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        text = context.packageName,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Icon(
                                    Icons.Outlined.ContentCopy,
                                    contentDescription = stringResource(R.string.copy),
                                    tint = AccentBlue,
                                    modifier = Modifier.size(16.dp)
                                )
                            }

                            // 签名 SHA1 条目（点击复制）
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        if (isDark) Color.White.copy(alpha = 0.04f) else Color.Black.copy(
                                            alpha = 0.03f
                                        )
                                    )
                                    .noRippleClickable {
                                        clipboardManager.setText(AnnotatedString(uiState.appSha1))
                                        Toast.makeText(
                                            context,
                                            context.getString(R.string.copied_to_clipboard),
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(R.string.app_sha1),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                    )
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        text = uiState.appSha1.ifBlank { stringResource(R.string.reading_signature) },
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Icon(
                                    Icons.Outlined.ContentCopy,
                                    contentDescription = stringResource(R.string.copy),
                                    tint = AccentBlue,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }

                    // 3.5 Root 方案与权限诊断卡片
                    MiuixCard(
                        modifier = Modifier.fillMaxWidth(),
                        cornerRadius = 18.dp,
                        insideMargin = PaddingValues(16.dp)
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
                                    Icons.Rounded.Shield,
                                    null,
                                    tint = AccentBlue,
                                    modifier = Modifier.size(19.dp)
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = stringResource(R.string.root_solution_title),
                                    fontSize = 15.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = stringResource(R.string.root_solution_desc),
                                    fontSize = 11.5.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                                )
                            }
                        }

                        Spacer(Modifier.height(14.dp))

                        val rootSolutions = listOf(
                            RootSolution.AUTO to stringResource(R.string.root_solution_auto),
                            RootSolution.MAGISK to stringResource(R.string.root_solution_magisk),
                            RootSolution.KERNELSU to stringResource(R.string.root_solution_kernelsu),
                            RootSolution.APATCH to stringResource(R.string.root_solution_apatch),
                            RootSolution.SUKISU_ULTRA to stringResource(R.string.root_solution_sukisu_ultra),
                            RootSolution.RESUKISU_ULTRA to stringResource(R.string.root_solution_resukisu_ultra)
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            rootSolutions.chunked(3).forEach { rowItems ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    rowItems.forEach { (solution, label) ->
                                        val isSelected = uiState.rootSolution == solution
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(
                                                    if (isSelected) AccentBlue
                                                    else if (isDark) Color.White.copy(alpha = 0.05f)
                                                    else Color.Black.copy(alpha = 0.04f)
                                                )
                                                .noRippleClickable { viewModel.setRootSolution(solution) }
                                                .padding(vertical = 9.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = label,
                                                color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface,
                                                fontSize = 11.5.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(14.dp))

                        // 测试 Root 权限与 sepolicy 规则注入
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (isDark) Color.White.copy(alpha = 0.04f) else Color.Black.copy(
                                        alpha = 0.03f
                                    )
                                )
                                .noRippleClickable {
                                    if (!uiState.isTestingRootSetup) viewModel.testRootSetup()
                                }
                                .padding(horizontal = 12.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.test_root_setup_title),
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = stringResource(R.string.test_root_setup_desc),
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                                )
                            }

                            if (uiState.isTestingRootSetup) {
                                CircularProgressIndicator(
                                    color = AccentBlue,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(18.dp)
                                )
                            } else {
                                Icon(
                                    Icons.Rounded.ChevronRight,
                                    contentDescription = stringResource(R.string.test_root_setup_title),
                                    tint = AccentBlue,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        Spacer(Modifier.height(8.dp))

                        // 重启目标应用以应用最新规则：检测通过但目标 App 仍读不到配置时，
                        // 让它们以全新状态重新走一次 SELinux 判定，不再受历史缓存影响。
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (isDark) Color.White.copy(alpha = 0.04f) else Color.Black.copy(
                                        alpha = 0.03f
                                    )
                                )
                                .noRippleClickable {
                                    if (!uiState.isRestartingHookedApps) viewModel.requestRestartHookedApps()
                                }
                                .padding(horizontal = 12.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.restart_hooked_apps_title),
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = stringResource(R.string.restart_hooked_apps_desc),
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                                )
                            }

                            if (uiState.isRestartingHookedApps) {
                                CircularProgressIndicator(
                                    color = AccentOrange,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(18.dp)
                                )
                            } else {
                                Icon(
                                    Icons.Rounded.ChevronRight,
                                    contentDescription = stringResource(R.string.restart_hooked_apps_title),
                                    tint = AccentOrange,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    // 4. 后台持续模拟与保活设置卡片
                    MiuixCard(
                        modifier = Modifier.fillMaxWidth(),
                        cornerRadius = 18.dp,
                        insideMargin = PaddingValues(16.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(11.dp))
                                    .background(AccentOrange.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Rounded.BatteryChargingFull,
                                    null,
                                    tint = AccentOrange,
                                    modifier = Modifier.size(19.dp)
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = stringResource(R.string.background_keep_alive_title),
                                    fontSize = 15.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = stringResource(R.string.background_keep_alive_desc),
                                    fontSize = 11.5.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                                )
                            }
                        }

                        Spacer(Modifier.height(14.dp))

                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            // 忽略电池优化按钮
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        if (isDark) Color.White.copy(alpha = 0.04f) else Color.Black.copy(
                                            alpha = 0.03f
                                        )
                                    )
                                    .noRippleClickable {
                                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                                            try {
                                                val intent =
                                                    android.content.Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                                                        .apply {
                                                            data =
                                                                android.net.Uri.parse("package:${context.packageName}")
                                                        }
                                                context.startActivity(intent)
                                            } catch (_: Exception) {
                                                try {
                                                    val intent =
                                                        android.content.Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                                    context.startActivity(intent)
                                                } catch (e: Exception) {
                                                    Toast.makeText(
                                                        context,
                                                        context.getString(R.string.open_battery_opt_failed, e.message ?: ""),
                                                        Toast.LENGTH_SHORT
                                                    ).show()
                                                }
                                            }
                                        }
                                    }
                                    .padding(horizontal = 12.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(R.string.ignore_battery_optimizations_title),
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        text = stringResource(R.string.ignore_battery_optimizations_desc),
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                                    )
                                }

                                Icon(
                                    Icons.Rounded.ChevronRight,
                                    contentDescription = stringResource(R.string.go_to_settings),
                                    tint = AccentOrange,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            // 允许应用自启动与后台活动
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        if (isDark) Color.White.copy(alpha = 0.04f) else Color.Black.copy(
                                            alpha = 0.03f
                                        )
                                    )
                                    .noRippleClickable {
                                        try {
                                            val intent =
                                                android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                                                    .apply {
                                                        data =
                                                            android.net.Uri.parse("package:${context.packageName}")
                                                    }
                                            context.startActivity(intent)
                                        } catch (e: Exception) {
                                            Toast.makeText(
                                                context,
                                                context.getString(R.string.open_app_info_failed, e.message ?: ""),
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    }
                                    .padding(horizontal = 12.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(R.string.app_info_settings_title),
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        text = stringResource(R.string.app_info_settings_desc),
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                                    )
                                }

                                Icon(
                                    Icons.Rounded.ChevronRight,
                                    contentDescription = stringResource(R.string.go_to_settings),
                                    tint = AccentOrange,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    // 5. 环境数据源 Token 卡片
                    MiuixCard(
                        modifier = Modifier.fillMaxWidth(),
                        cornerRadius = 18.dp,
                        insideMargin = PaddingValues(16.dp)
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
                                    Icons.Rounded.VpnKey,
                                    null,
                                    tint = AccentBlue,
                                    modifier = Modifier.size(19.dp)
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = stringResource(R.string.env_datasource_tokens_title),
                                    fontSize = 15.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = stringResource(R.string.env_datasource_tokens_desc),
                                    fontSize = 11.5.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                                )
                            }
                        }

                        Spacer(Modifier.height(14.dp))

                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            ModernSettingsInput(
                                label = stringResource(R.string.custom_wigle_token),
                                value = localWigleToken,
                                onValueChange = { localWigleToken = it },
                                placeholder = stringResource(R.string.custom_wigle_token_hint),
                                isDark = isDark
                            )

                            ModernSettingsInput(
                                label = stringResource(R.string.custom_opencellid_token),
                                value = localOpencellidToken,
                                onValueChange = { localOpencellidToken = it },
                                placeholder = stringResource(R.string.custom_opencellid_token_hint),
                                isDark = isDark
                            )
                        }
                    }

                    // 5. 保存配置按钮
                    Button(
                        onClick = {
                            viewModel.setAmapApiKey(localAmapApiKey)
                            viewModel.setBaiduApiKey(localBaiduApiKey)
                            viewModel.setGoogleApiKey(localGoogleApiKey)
                            viewModel.setWigleApiToken(localWigleToken)
                            viewModel.setOpencellidApiToken(localOpencellidToken)
                            Toast.makeText(
                                context,
                                context.getString(R.string.restart_required_hint),
                                Toast.LENGTH_SHORT
                            ).show()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)
                    ) {
                        Text(
                            stringResource(R.string.save),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(Modifier.height(16.dp))
                }

                // 顶部平滑溶解渐变遮罩
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

    val testResult = uiState.rootSetupTestResult
    if (testResult != null) {
        RootSetupTestResultDialog(
            result = testResult,
            isDark = isDark,
            onDismiss = { viewModel.dismissRootSetupTestResult() }
        )
    }

    val appsToRestart = uiState.hookedAppsToRestart
    if (appsToRestart != null) {
        RestartHookedAppsConfirmDialog(
            apps = appsToRestart,
            isDark = isDark,
            onDismiss = { viewModel.dismissRestartHookedAppsDialog() },
            onConfirm = {
                viewModel.confirmRestartHookedApps { count ->
                    Toast.makeText(
                        context,
                        context.getString(R.string.restart_hooked_apps_done, count),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        )
    }
}

@Composable
private fun ModernSettingsInput(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    isDark: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (isDark) Color.White.copy(alpha = 0.04f) else Color.Black.copy(alpha = 0.03f))
            .border(
                0.8.dp,
                MaterialTheme.colorScheme.outline.copy(alpha = if (isDark) 0.10f else 0.05f),
                RoundedCornerShape(14.dp)
            )
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Text(
            text = label,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = AccentBlue
        )
        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                textStyle = TextStyle(
                    fontSize = 13.5.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Normal
                ),
                singleLine = true,
                modifier = Modifier.weight(1f),
                decorationBox = { innerTextField ->
                    if (value.isEmpty()) {
                        Text(
                            text = placeholder,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
                        )
                    }
                    innerTextField()
                }
            )
            if (value.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                        .noRippleClickable { onValueChange("") },
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
}

@Composable
private fun DiagnosticItemRow(label: String, ok: Boolean, detail: String? = null) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            if (ok) Icons.Rounded.CheckCircle else Icons.Rounded.Cancel,
            contentDescription = null,
            tint = if (ok) AccentGreen else Color(0xFFE53935),
            modifier = Modifier
                .size(16.dp)
                .padding(top = 1.dp)
        )
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (!detail.isNullOrBlank()) {
                Text(
                    text = detail,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
        }
    }
}

@Composable
private fun RootSetupTestResultDialog(
    result: RootSetupTestResult,
    isDark: Boolean,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .wrapContentHeight(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(22.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val overallOk = result.hasRoot && result.overallVerified
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(13.dp))
                            .background((if (overallOk) AccentGreen else Color(0xFFE53935)).copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            if (overallOk) Icons.Rounded.CheckCircle else Icons.Rounded.Cancel,
                            contentDescription = null,
                            tint = if (overallOk) AccentGreen else Color(0xFFE53935),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Text(
                        text = stringResource(R.string.root_test_result_title),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(Modifier.height(16.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    DiagnosticItemRow(
                        label = stringResource(R.string.root_test_item_root),
                        ok = result.hasRoot,
                        detail = result.idOutput.take(120)
                    )
                    DiagnosticItemRow(
                        label = stringResource(R.string.root_test_item_tool),
                        ok = result.toolUsed != null,
                        detail = result.toolUsed ?: stringResource(R.string.root_test_tool_not_found)
                    )
                    DiagnosticItemRow(
                        label = stringResource(R.string.root_test_item_type_rule),
                        ok = result.typeRuleOk
                    )
                    result.allowRuleResults.forEach { (domain, ok) ->
                        DiagnosticItemRow(
                            label = stringResource(R.string.root_test_item_allow_domain, domain),
                            ok = ok
                        )
                    }
                    DiagnosticItemRow(
                        label = stringResource(R.string.root_test_item_label_check),
                        ok = result.labelVerified,
                        detail = result.labelCheckRaw
                    )
                    DiagnosticItemRow(
                        label = stringResource(R.string.root_test_item_app_read),
                        ok = result.appCanReadProbe,
                        detail = stringResource(R.string.root_test_item_app_read_detail)
                    )
                    result.configFileChconResults.forEach { (path, ok) ->
                        DiagnosticItemRow(
                            label = path,
                            ok = ok
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

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
                            .noRippleClickable {
                                val summary = buildString {
                                    appendLine("solution=${result.solution}")
                                    appendLine("hasRoot=${result.hasRoot}")
                                    appendLine("idOutput=${result.idOutput}")
                                    appendLine("toolUsed=${result.toolUsed}")
                                    appendLine("typeRuleOk=${result.typeRuleOk}")
                                    appendLine("allowRuleResults=${result.allowRuleResults}")
                                    appendLine("labelVerified=${result.labelVerified}")
                                    appendLine("labelCheckRaw=${result.labelCheckRaw}")
                                    appendLine("configFileChconResults=${result.configFileChconResults}")
                                    appendLine("rawScriptOutput=${result.rawScriptOutput}")
                                }
                                clipboardManager.setText(AnnotatedString(summary))
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.copied_to_clipboard),
                                    Toast.LENGTH_SHORT
                                ).show()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.copy_diagnostic_info),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
                        )
                    }

                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp)
                    ) {
                        Text(
                            stringResource(R.string.close),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RestartHookedAppsConfirmDialog(
    apps: List<com.suseoaa.locationspoofer.data.model.AppInfoItem>,
    isDark: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.88f)
                .wrapContentHeight(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(22.dp),
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
                            Icons.Rounded.RestartAlt,
                            contentDescription = null,
                            tint = Color(0xFFE53935),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Text(
                        text = stringResource(R.string.restart_hooked_apps_confirm_title),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                if (apps.isEmpty()) {
                    Text(
                        text = stringResource(R.string.restart_hooked_apps_empty),
                        fontSize = 13.5.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        lineHeight = 20.sp
                    )
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth().height(44.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)
                    ) {
                        Text(stringResource(R.string.close), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Text(
                        text = stringResource(R.string.restart_hooked_apps_confirm_message, apps.size),
                        fontSize = 13.5.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        lineHeight = 20.sp
                    )

                    Text(
                        text = apps.joinToString("、") { it.appName },
                        fontSize = 12.5.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                        lineHeight = 18.sp
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
                                .noRippleClickable(onDismiss),
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
                            onClick = onConfirm,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1.2f)
                                .height(42.dp)
                        ) {
                            Text(
                                stringResource(R.string.restart_hooked_apps_confirm_button),
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
