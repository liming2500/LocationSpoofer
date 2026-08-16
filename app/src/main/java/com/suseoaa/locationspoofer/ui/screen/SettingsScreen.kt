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
import androidx.core.os.LocaleListCompat
import com.suseoaa.locationspoofer.R
import com.suseoaa.locationspoofer.data.model.AppState
import com.suseoaa.locationspoofer.data.model.MapEngine
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
                            color = if (isDark) Color.White.copy(alpha = 0.14f) else Color(0xFFE5E8EC),
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
                        text = "软件配置",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "界面语言、地图引擎与数据源配置",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                    )
                }
            }

            // 内容可滚动区域
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
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
                                Icon(Icons.Rounded.Language, null, tint = AccentBlue, modifier = Modifier.size(19.dp))
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
                                    text = "切换应用界面显示语言",
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
                                            color = if (isSelected) AccentBlue else MaterialTheme.colorScheme.outline.copy(alpha = if (isDark) 0.08f else 0.04f),
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
                                Icon(Icons.Rounded.Map, null, tint = AccentGreen, modifier = Modifier.size(19.dp))
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
                                    text = "选择地图渲染引擎及自定义 Key",
                                    fontSize = 11.5.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                                )
                            }
                        }

                        Spacer(Modifier.height(14.dp))

                        // 地图引擎 4 分段选择器
                        Text(
                            text = "当前引擎",
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
                                MapEngine.AUTO to "自动",
                                MapEngine.AMAP to "高德",
                                MapEngine.BAIDU to "百度",
                                MapEngine.GOOGLE to "谷歌"
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
                                Icon(Icons.Rounded.Security, null, tint = AccentOrange, modifier = Modifier.size(19.dp))
                            }
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "运行环境与签名",
                                    fontSize = 15.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "用于第三方地图开放平台 API Key 申请绑定",
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
                                    .background(if (isDark) Color.White.copy(alpha = 0.04f) else Color.Black.copy(alpha = 0.03f))
                                    .noRippleClickable {
                                        clipboardManager.setText(AnnotatedString(context.packageName))
                                        Toast.makeText(context, context.getString(R.string.copied_to_clipboard), Toast.LENGTH_SHORT).show()
                                    }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "应用包名 (Package Name)",
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
                                    contentDescription = "复制",
                                    tint = AccentBlue,
                                    modifier = Modifier.size(16.dp)
                                )
                            }

                            // 签名 SHA1 条目（点击复制）
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isDark) Color.White.copy(alpha = 0.04f) else Color.Black.copy(alpha = 0.03f))
                                    .noRippleClickable {
                                        clipboardManager.setText(AnnotatedString(uiState.appSha1))
                                        Toast.makeText(context, context.getString(R.string.copied_to_clipboard), Toast.LENGTH_SHORT).show()
                                    }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "应用签名 (SHA1 证书指纹)",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                    )
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        text = uiState.appSha1.ifBlank { "正在读取签名..." },
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Icon(
                                    Icons.Outlined.ContentCopy,
                                    contentDescription = "复制",
                                    tint = AccentBlue,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }

                    // 4. 环境数据源 Token 卡片
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
                                Icon(Icons.Rounded.VpnKey, null, tint = AccentBlue, modifier = Modifier.size(19.dp))
                            }
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "环境数据源 Token",
                                    fontSize = 15.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "用于在线检索周边基站与 Wi-Fi 信号",
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
                            Toast.makeText(context, context.getString(R.string.restart_required_hint), Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)
                    ) {
                        Text(stringResource(R.string.save), fontSize = 15.sp, fontWeight = FontWeight.Bold)
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
