package com.vincenthzr.locationspoofer.ui.screen.tabs.route

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vincenthzr.locationspoofer.data.model.RoutePoint
import com.vincenthzr.locationspoofer.ui.R
import com.vincenthzr.locationspoofer.ui.theme.AccentBlue
import com.vincenthzr.locationspoofer.ui.theme.AccentGreen
import com.vincenthzr.locationspoofer.utils.CoordinateUtils.LatLng
import com.vincenthzr.locationspoofer.utils.RoutePath
import java.util.Locale

/**
 * 循环圈数输入框，并实时显示预计总距离与预计耗时。
 *
 * 圈数直接键入数字，0 或留空表示不限。距离按"单圈距离 × 圈数"计算：
 * 闭环路线为周长，开放路线为一次往返。耗时按当前速度档位估算，
 * 不含配速起伏与起步斜坡，属于量级参考。
 */
@Composable
fun RouteLoopsCard(
    loops: Int,
    onChange: (Int) -> Unit,
    routePoints: List<RoutePoint>,
    speedMs: Double
) {
    // 输入过程中保留原始文本（允许清空、允许暂时非法），失焦或输入合法数字时再提交
    var text by remember(loops) { mutableStateOf(if (loops > 0) loops.toString() else "") }
    val typed = text.trim().toIntOrNull()?.coerceIn(0, MAX_LOOPS) ?: 0

    val lapDistance = remember(routePoints) {
        if (routePoints.size < 2) 0.0
        else RoutePath(routePoints.map { LatLng(it.lat, it.lng) }).lapDistance
    }
    val totalMeters = lapDistance * typed
    val seconds = if (speedMs > 0.0) totalMeters / speedMs else 0.0

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.route_loops_title),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        stringResource(R.string.route_loops_hint, MAX_LOOPS),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                    )
                }
                OutlinedTextField(
                    value = text,
                    onValueChange = { raw ->
                        // 只接受数字，最多 MAX_LOOPS
                        val digits = raw.filter { it.isDigit() }.take(MAX_LOOPS.toString().length)
                        text = digits
                        onChange(digits.toIntOrNull()?.coerceIn(0, MAX_LOOPS) ?: 0)
                    },
                    modifier = Modifier.width(96.dp),
                    singleLine = true,
                    placeholder = {
                        Text(
                            stringResource(R.string.route_loops_unlimited),
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                        )
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AccentBlue,
                        focusedLabelColor = AccentBlue
                    )
                )
            }

            when {
                typed > 0 && lapDistance > 0.0 -> Text(
                    stringResource(
                        R.string.route_estimate_format,
                        formatDuration(seconds),
                        formatDistance(totalMeters)
                    ),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AccentGreen
                )

                lapDistance > 0.0 -> Text(
                    stringResource(
                        R.string.route_estimate_format,
                        "—",
                        stringResource(R.string.route_per_lap, formatDistance(lapDistance))
                    ),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
        }
    }
}

/** 圈数上限，与 SettingsManager 中的约束保持一致 */
private const val MAX_LOOPS = 999

private fun formatDistance(meters: Double): String =
    if (meters >= 1000.0) String.format(Locale.US, "%.2f km", meters / 1000.0)
    else String.format(Locale.US, "%.0f m", meters)

private fun formatDuration(seconds: Double): String {
    val total = seconds.toLong().coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    return when {
        h > 0 -> String.format(Locale.US, "%d h %02d min", h, m)
        m > 0 -> String.format(Locale.US, "%d min", m)
        else -> String.format(Locale.US, "%d s", total)
    }
}
