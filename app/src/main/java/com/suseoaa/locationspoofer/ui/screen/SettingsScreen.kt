package com.suseoaa.locationspoofer.ui.screen

import android.widget.Toast
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.VpnKey
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.os.LocaleListCompat
import com.suseoaa.locationspoofer.R
import com.suseoaa.locationspoofer.data.model.AppState
import com.suseoaa.locationspoofer.data.model.MapEngine
import com.suseoaa.locationspoofer.ui.theme.AccentBlue
import com.suseoaa.locationspoofer.ui.theme.AppColors
import com.suseoaa.locationspoofer.ui.theme.noRippleClickable
import com.suseoaa.locationspoofer.viewmodel.MainViewModel
import top.yukonga.miuix.kmp.basic.Card as MiuixCard

@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    uiState: AppState,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    var localAmapApiKey by remember(uiState.amapApiKey) { mutableStateOf(uiState.amapApiKey) }
    var localBaiduApiKey by remember(uiState.baiduApiKey) { mutableStateOf(uiState.baiduApiKey) }
    var localGoogleApiKey by remember(uiState.googleApiKey) { mutableStateOf(uiState.googleApiKey) }
    var localWigleToken by remember(uiState.wigleToken) { mutableStateOf(uiState.wigleToken) }
    var localOpencellidToken by remember(uiState.opencellidToken) { mutableStateOf(uiState.opencellidToken) }
    val clipboardManager = LocalClipboardManager.current
    val isDark = androidx.compose.foundation.isSystemInDarkTheme()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Language Card
        MiuixCard(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 16.dp,
            insideMargin = PaddingValues(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(AccentBlue.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.Language, null, tint = AccentBlue, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    stringResource(R.string.select_language),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
            Spacer(Modifier.height(12.dp))

            LANGUAGES.forEach { lang ->
                val isSelected = viewModel.getSavedLanguage() == lang.code
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (isSelected) AccentBlue.copy(alpha = 0.12f)
                            else if (isDark) Color.White.copy(alpha = 0.05f)
                            else Color.Black.copy(alpha = 0.03f)
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
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
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
                Spacer(Modifier.height(6.dp))
            }
        }

        // 2. Map Engine & Keys Card
        MiuixCard(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 16.dp,
            insideMargin = PaddingValues(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(AccentBlue.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.Map, null, tint = AccentBlue, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    stringResource(R.string.map_config),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
            Spacer(Modifier.height(12.dp))

            // Package Name & SHA1 Info
            OutlinedTextField(
                value = context.packageName,
                onValueChange = {},
                readOnly = true,
                label = { Text(stringResource(R.string.app_package_name), fontSize = 12.sp) },
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = {
                    IconButton(onClick = {
                        clipboardManager.setText(AnnotatedString(context.packageName))
                        Toast.makeText(context, context.getString(R.string.copied_to_clipboard), Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(Icons.Outlined.ContentCopy, stringResource(R.string.copy), modifier = Modifier.size(18.dp))
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AccentBlue,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                    focusedLabelColor = AccentBlue
                ),
                shape = RoundedCornerShape(12.dp)
            )
            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = uiState.appSha1,
                onValueChange = {},
                readOnly = true,
                label = { Text(stringResource(R.string.app_sha1), fontSize = 12.sp) },
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = {
                    IconButton(onClick = {
                        clipboardManager.setText(AnnotatedString(uiState.appSha1))
                        Toast.makeText(context, context.getString(R.string.copied_to_clipboard), Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(Icons.Outlined.ContentCopy, stringResource(R.string.copy), modifier = Modifier.size(18.dp))
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AccentBlue,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                    focusedLabelColor = AccentBlue
                ),
                shape = RoundedCornerShape(12.dp)
            )
            Spacer(Modifier.height(14.dp))

            // Map Engine Chips
            Text(
                "地图引擎",
                fontSize = 13.sp,
                color = AppColors.textSecondary(isDark),
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(8.dp))
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
                                if (isSelected) AccentBlue.copy(alpha = 0.15f)
                                else if (isDark) Color.White.copy(alpha = 0.05f)
                                else Color.Black.copy(alpha = 0.04f)
                            )
                            .border(
                                width = 1.dp,
                                color = if (isSelected) AccentBlue else Color.Transparent,
                                shape = RoundedCornerShape(10.dp)
                            )
                            .noRippleClickable { viewModel.setMapEngine(engine) }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            color = if (isSelected) AccentBlue else MaterialTheme.colorScheme.onSurface,
                            fontSize = 13.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }

            // Animated Map API Key Input
            AnimatedVisibility(visible = uiState.mapEngine == MapEngine.AMAP) {
                Column {
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = localAmapApiKey,
                        onValueChange = { localAmapApiKey = it },
                        label = { Text(stringResource(R.string.custom_amap_key), fontSize = 12.sp) },
                        placeholder = { Text(stringResource(R.string.custom_amap_key_hint), color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AccentBlue)
                    )
                }
            }

            AnimatedVisibility(visible = uiState.mapEngine == MapEngine.BAIDU) {
                Column {
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = localBaiduApiKey,
                        onValueChange = { localBaiduApiKey = it },
                        label = { Text(stringResource(R.string.custom_baidu_key), fontSize = 12.sp) },
                        placeholder = { Text(stringResource(R.string.custom_baidu_key_hint), color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AccentBlue)
                    )
                }
            }

            AnimatedVisibility(visible = uiState.mapEngine == MapEngine.GOOGLE) {
                Column {
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = localGoogleApiKey,
                        onValueChange = { localGoogleApiKey = it },
                        label = { Text(stringResource(R.string.custom_google_key), fontSize = 12.sp) },
                        placeholder = { Text(stringResource(R.string.custom_google_key_hint), color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AccentBlue)
                    )
                }
            }
        }

        // 3. Sensor & Base Station Database Tokens Card
        MiuixCard(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 16.dp,
            insideMargin = PaddingValues(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(AccentBlue.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.VpnKey, null, tint = AccentBlue, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    "环境数据源 Token",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = localWigleToken,
                onValueChange = { localWigleToken = it },
                label = { Text(stringResource(R.string.custom_wigle_token), fontSize = 12.sp) },
                placeholder = { Text(stringResource(R.string.custom_wigle_token_hint), color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AccentBlue)
            )
            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = localOpencellidToken,
                onValueChange = { localOpencellidToken = it },
                label = { Text(stringResource(R.string.custom_opencellid_token), fontSize = 12.sp) },
                placeholder = { Text(stringResource(R.string.custom_opencellid_token_hint), color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AccentBlue)
            )
        }

        // 4. Save Button
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
            Text(stringResource(R.string.save), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}
