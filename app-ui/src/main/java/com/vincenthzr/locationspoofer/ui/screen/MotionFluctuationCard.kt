package com.vincenthzr.locationspoofer.ui.screen

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
import com.vincenthzr.locationspoofer.ui.R
import com.vincenthzr.locationspoofer.ui.theme.AccentBlue
import com.vincenthzr.locationspoofer.ui.theme.AccentOrange
import com.vincenthzr.locationspoofer.utils.MotionRealism
import kotlin.math.roundToInt

/**
 * 速度起伏 + 步频起伏，与海拔变化范围同一套滑块交互，放在开始模拟 / 路线配置弹窗里。
 * 步频只影响计步传感器，速度影响路线配速；两者都在会话开始时取快照，中途修改下次模拟生效。
 */
@Composable
fun MotionFluctuationCard(
    speedPct: Int,
    cadencePct: Int,
    onSpeedChange: (Int) -> Unit,
    onCadenceChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                stringResource(R.string.motion_fluctuation_title),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            FluctuationRow(
                label = stringResource(R.string.speed_fluctuation_title),
                value = speedPct,
                max = MotionRealism.MAX_SPEED_FLUCTUATION_PCT,
                color = AccentOrange,
                onChange = onSpeedChange
            )
            FluctuationRow(
                label = stringResource(R.string.cadence_fluctuation_title),
                value = cadencePct,
                max = MotionRealism.MAX_CADENCE_FLUCTUATION_PCT,
                color = AccentBlue,
                onChange = onCadenceChange
            )
            Text(
                stringResource(R.string.motion_fluctuation_desc),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
            )
        }
    }
}

@Composable
private fun FluctuationRow(
    label: String,
    value: Int,
    max: Int,
    color: androidx.compose.ui.graphics.Color,
    onChange: (Int) -> Unit
) {
    var local by remember(value) { mutableFloatStateOf(value.toFloat()) }
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                label,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            Text(
                if (local.roundToInt() == 0) stringResource(R.string.altitude_variation_off)
                else stringResource(R.string.speed_fluctuation_value, local.roundToInt()),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
        Slider(
            value = local,
            onValueChange = { local = it },
            onValueChangeFinished = { onChange(local.roundToInt()) },
            valueRange = 0f..max.toFloat(),
            steps = (max / 5 - 1).coerceAtLeast(0),
            colors = SliderDefaults.colors(
                thumbColor = color,
                activeTrackColor = color,
                inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        )
    }
}
