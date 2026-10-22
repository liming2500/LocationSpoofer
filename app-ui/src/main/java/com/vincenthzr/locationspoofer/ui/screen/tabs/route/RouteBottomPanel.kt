package com.vincenthzr.locationspoofer.ui.screen.tabs.route

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vincenthzr.locationspoofer.ui.R
import com.vincenthzr.locationspoofer.data.model.AppState
import com.vincenthzr.locationspoofer.data.model.RoutePlanStage
import com.vincenthzr.locationspoofer.data.model.RoutePoint
import com.vincenthzr.locationspoofer.data.model.RouteRunMode
import com.vincenthzr.locationspoofer.ui.theme.AccentBlue
import com.vincenthzr.locationspoofer.ui.theme.AccentGreen
import com.vincenthzr.locationspoofer.ui.theme.AccentOrange

@Composable
fun RouteBottomPanel(
    modifier: Modifier,
    stage: RoutePlanStage,
    routePoints: List<RoutePoint>,
    uiState: AppState,
    onConfirmPoint: () -> Unit,
    onFinishSelecting: () -> Unit,
    onRestartSelecting: () -> Unit,
    onSaveRoute: () -> Unit,
    onStartPlanning: () -> Unit,
    onStopRoute: () -> Unit,
    onBeginRun: () -> Unit = {},
    searchBar: (@Composable (Modifier) -> Unit)? = null
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            )
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (searchBar != null) {
                searchBar(Modifier.fillMaxWidth())
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 8.dp
            ) {
                Box(modifier = Modifier.padding(14.dp)) {
                    when (stage) {
                        RoutePlanStage.IDLE, RoutePlanStage.SELECTING -> {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // 确认添加路点
                                Button(
                                    onClick = onConfirmPoint,
                                    modifier = Modifier
                                        .weight(1.2f)
                                        .height(50.dp),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)
                                ) {
                                    Icon(
                                        Icons.Rounded.AddLocation,
                                        null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        if (routePoints.isEmpty()) stringResource(R.string.add_start_point) else stringResource(R.string.add_nth_point, routePoints.size + 1),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                }

                                // 完成规划
                                Button(
                                    onClick = onFinishSelecting,
                                    enabled = routePoints.size >= 2,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(50.dp),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = AccentGreen,
                                        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(
                                            alpha = 0.5f
                                        )
                                    )
                                ) {
                                    Icon(
                                        Icons.Rounded.CheckCircle,
                                        null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        stringResource(R.string.finish_selecting),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                }
                            }
                        }

                        RoutePlanStage.READY -> {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedButton(
                                    onClick = onRestartSelecting,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(50.dp),
                                    contentPadding = PaddingValues(horizontal = 4.dp),
                                    shape = RoundedCornerShape(16.dp)
                                ) {
                                    Icon(
                                        Icons.Rounded.Refresh,
                                        null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(3.dp))
                                    Text(
                                        stringResource(R.string.reselect),
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 13.5.sp,
                                        maxLines = 1
                                    )
                                }

                                OutlinedButton(
                                    onClick = onSaveRoute,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(50.dp),
                                    contentPadding = PaddingValues(horizontal = 4.dp),
                                    shape = RoundedCornerShape(16.dp)
                                ) {
                                    Icon(
                                        Icons.Rounded.BookmarkAdd,
                                        null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(3.dp))
                                    Text(
                                        stringResource(R.string.bookmark),
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 13.5.sp,
                                        maxLines = 1
                                    )
                                }

                                Button(
                                    onClick = onStartPlanning,
                                    modifier = Modifier
                                        .weight(1.35f)
                                        .height(50.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)
                                ) {
                                    Icon(
                                        Icons.Rounded.PlayArrow,
                                        null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(5.dp))
                                    Text(
                                        stringResource(R.string.start_simulation),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        maxLines = 1
                                    )
                                }
                            }
                        }

                        // 已就位等待：定位以定点形态喂给目标 App，等它把 GPS 状态判为稳定后，
                        // 用户在该 App 里点开始，再回来点这里才真正沿路线移动
                        RoutePlanStage.WAITING -> {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .clip(CircleShape)
                                            .background(AccentOrange)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = stringResource(R.string.route_waiting_hint),
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = onStopRoute,
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(50.dp),
                                        shape = RoundedCornerShape(16.dp)
                                    ) {
                                        Text(
                                            stringResource(R.string.cancel),
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 14.sp
                                        )
                                    }
                                    Button(
                                        onClick = onBeginRun,
                                        modifier = Modifier
                                            .weight(1.6f)
                                            .height(50.dp),
                                        shape = RoundedCornerShape(16.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = AccentGreen)
                                    ) {
                                        Icon(
                                            Icons.Rounded.PlayArrow,
                                            null,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            stringResource(R.string.route_begin_run),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp
                                        )
                                    }
                                }
                            }
                        }

                        RoutePlanStage.RUNNING -> {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(start = 6.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .clip(CircleShape)
                                            .background(AccentGreen)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = if (uiState.routeRunMode == RouteRunMode.MANUAL) stringResource(R.string.joystick_controlling_active) else stringResource(R.string.cruising_speed_format, uiState.routeSimMode.speedMs.toInt()),
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }

                                Button(
                                    onClick = onStopRoute,
                                    shape = RoundedCornerShape(16.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.errorContainer,
                                        contentColor = MaterialTheme.colorScheme.error
                                    ),
                                    modifier = Modifier.height(46.dp)
                                ) {
                                    Icon(
                                        Icons.Rounded.StopCircle,
                                        null,
                                        modifier = Modifier.size(19.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        stringResource(R.string.stop_simulation),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.5.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
