package com.vincenthzr.locationspoofer.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vincenthzr.locationspoofer.ui.R
import com.vincenthzr.locationspoofer.ui.theme.AccentBlue
import com.vincenthzr.locationspoofer.ui.theme.AccentGreen
import com.vincenthzr.locationspoofer.ui.theme.AccentOrange
import com.vincenthzr.locationspoofer.utils.MotionRealism
import kotlin.math.roundToInt

/**
 * 速度与步频起伏：总开关 + 速度起伏、步频起伏、起步斜坡、坡度影响四项参数。
 *
 * 放在"步频与计步模拟"下方。总开关关闭时，速度与步频都保持基准值，配速画面完全不浮动。
 * 参数在开始模拟时取快照，中途修改下次模拟生效。
 */
@Composable
fun MotionFluctuationCard(
    enabled: Boolean,
    speedPct: Int,
    cadencePct: Int,
    rampSec: Int,
    gradeSensitivity: Int,
    onEnabledChange: (Boolean) -> Unit,
    onSpeedChange: (Int) -> Unit,
    onCadenceChange: (Int) -> Unit,
    onRampChange: (Int) -> Unit,
    onGradeChange: (Int) -> Unit,
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.motion_fluctuation_title),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        stringResource(R.string.motion_fluctuation_desc),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                    )
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = onEnabledChange,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = AccentBlue
                    )
                )
            }

            if (enabled) {
                FluctuationRow(
                    label = stringResource(R.string.speed_fluctuation_title),
                    value = speedPct,
                    max = MotionRealism.MAX_SPEED_FLUCTUATION_PCT,
                    suffix = stringResource(R.string.speed_fluctuation_value, speedPct),
                    color = AccentOrange,
                    onChange = onSpeedChange
                )
                FluctuationRow(
                    label = stringResource(R.string.cadence_fluctuation_title),
                    value = cadencePct,
                    max = MotionRealism.MAX_CADENCE_FLUCTUATION_PCT,
                    suffix = stringResource(R.string.speed_fluctuation_value, cadencePct),
                    color = AccentGreen,
                    onChange = onCadenceChange
                )
                FluctuationRow(
                    label = stringResource(R.string.speed_ramp_title),
                    value = rampSec,
                    max = 10,
                    suffix = stringResource(R.string.speed_ramp_value, rampSec),
                    color = AccentBlue,
                    onChange = onRampChange
                )
                FluctuationRow(
                    label = stringResource(R.string.grade_sensitivity_title),
                    value = gradeSensitivity,
                    max = MotionRealism.MAX_GRADE_SENSITIVITY,
                    suffix = if (gradeSensitivity == 0) stringResource(R.string.altitude_variation_off)
                    else "$gradeSensitivity%",
                    color = AccentBlue,
                    onChange = onGradeChange
                )
                Text(
                    stringResource(R.string.motion_fluctuation_hint),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
        }
    }
}

@Composable
private fun FluctuationRow(
    label: String,
    value: Int,
    max: Int,
    suffix: String,
    color: Color,
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
                suffix,
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
            steps = (max - 1).coerceAtLeast(0),
            colors = SliderDefaults.colors(
                thumbColor = color,
                activeTrackColor = color,
                inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        )
    }
}
