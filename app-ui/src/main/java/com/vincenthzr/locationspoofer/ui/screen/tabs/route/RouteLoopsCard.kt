package com.vincenthzr.locationspoofer.ui.screen.tabs.route

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
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
 * 循环圈数设置，并实时显示预计总距离与预计耗时。
 *
 * 距离按"单圈距离 × 圈数"计算；单圈在闭环路线下是路线周长，开放路线下是一次往返。
 * 耗时按当前速度档位估算，不含配速起伏与起步斜坡，属于量级参考。
 */
@Composable
fun RouteLoopsCard(
    loops: Int,
    onChange: (Int) -> Unit,
    routePoints: List<RoutePoint>,
    speedMs: Double
) {
    var slider by remember(loops) { mutableFloatStateOf(loops.toFloat()) }
    val lapDistance = remember(routePoints) {
        if (routePoints.size < 2) 0.0
        else RoutePath(routePoints.map { LatLng(it.lat, it.lng) }).lapDistance
    }
    val totalMeters = lapDistance * slider.toInt()
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
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.route_loops_title),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    if (slider.toInt() == 0) stringResource(R.string.route_loops_unlimited)
                    else stringResource(R.string.route_loops_value, slider.toInt()),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = AccentBlue
                )
            }
            Slider(
                value = slider,
                onValueChange = { slider = it },
                onValueChangeFinished = { onChange(slider.toInt()) },
                valueRange = 0f..50f,
                steps = 49,
                colors = SliderDefaults.colors(
                    thumbColor = AccentBlue,
                    activeTrackColor = AccentBlue,
                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
            if (slider.toInt() > 0 && lapDistance > 0.0) {
                Text(
                    stringResource(
                        R.string.route_estimate_format,
                        formatDuration(seconds),
                        formatDistance(totalMeters)
                    ),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AccentGreen
                )
            } else if (lapDistance > 0.0) {
                Text(
                    stringResource(R.string.route_estimate_format, "—", formatDistance(lapDistance) + " / 圈"),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
        }
    }
}

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
