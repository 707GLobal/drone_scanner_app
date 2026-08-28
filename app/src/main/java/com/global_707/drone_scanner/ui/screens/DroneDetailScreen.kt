package com.global_707.drone_scanner.ui.screens

import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.global_707.drone_scanner.R
import com.global_707.drone_scanner.data.Drone
import com.global_707.drone_scanner.data.DroneStatus
import com.global_707.drone_scanner.ui.components.FrostedBar
import com.global_707.drone_scanner.ui.components.MapGridBackground
import com.global_707.drone_scanner.ui.components.PulseDot
import com.global_707.drone_scanner.ui.components.SectionCard
import com.global_707.drone_scanner.ui.components.SignalBars
import com.global_707.drone_scanner.ui.components.StatusDot
import com.global_707.drone_scanner.ui.theme.DroneTypography
import com.global_707.drone_scanner.ui.theme.LocalDroneColors
import java.util.Locale

/**
 * 页面二：无人机详情（Drone Detail，参考设计文档第 5 节）
 */
@Composable
fun DroneDetailScreen(
    drone: Drone,
    onBack: () -> Unit,
) {
    val colors = LocalDroneColors.current
    val context = LocalContext.current
    val toastMessage = stringResource(R.string.watched_toast)
    val signalLabel = when {
        drone.signalStrength >= 4 -> stringResource(R.string.signal_strong)
        drone.signalStrength >= 3 -> stringResource(R.string.signal_medium)
        else -> stringResource(R.string.signal_weak)
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        // ---- 导航栏 ----
        HeaderBar(title = stringResource(R.string.detail_title), onBack = onBack)

        // ---- 滚动内容 ----
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(vertical = 16.dp),
        ) {
            HeroCard(drone)

            MetricsGrid(drone)

            SignalInfoCard(drone, signalLabel)

            LocationCard(drone)

            OperatorCard(drone)
        }

        // ---- 底部固定操作按钮 ----
        FrostedBar(Modifier.fillMaxWidth()) {
            Button(
                onClick = { Toast.makeText(context, toastMessage, Toast.LENGTH_SHORT).show() },
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.primary,
                    contentColor = colors.primaryForeground,
                ),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text(
                    text = stringResource(R.string.watch_button),
                    style = DroneTypography.body,
                )
            }
        }
    }
}

/** 全局导航栏：返回按钮 + 居中标题（参考设计文档第 3.1 节） */
@Composable
internal fun HeaderBar(title: String, onBack: () -> Unit) {
    val colors = LocalDroneColors.current
    Column(Modifier.fillMaxWidth()) {
        Box(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(44.dp)
                .background(colors.card.copy(alpha = 0.85f)),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.detail_title),
                tint = colors.primary,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 16.dp)
                    .size(20.dp)
                    .clickable(onClick = onBack),
            )
            Text(
                text = title,
                style = DroneTypography.pageTitle,
                color = colors.foreground,
                modifier = Modifier.align(Alignment.Center),
            )
            Spacer(Modifier.align(Alignment.CenterEnd).width(24.dp))
        }
        HorizontalDivider(thickness = 0.5.dp, color = colors.border)
    }
}

/** 无人机身份卡片（参考设计文档第 5.3 节） */
@Composable
private fun HeroCard(drone: Drone) {
    val colors = LocalDroneColors.current
    SectionCard(Modifier.padding(horizontal = 16.dp)) {
        Text(
            text = drone.name,
            style = DroneTypography.largeTitle,
            color = colors.cardForeground,
        )
        Spacer(Modifier.height(10.dp))
        LabelValueRow(
            label = stringResource(R.string.label_sn),
            value = drone.snCode,
            mono = true,
        )
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.label_reg_code),
                style = DroneTypography.caption,
                color = colors.mutedForeground,
                modifier = Modifier.weight(1f),
            )
            Box(
                Modifier
                    .background(colors.primary, RoundedCornerShape(6.dp))
                    .padding(horizontal = 10.dp, vertical = 2.dp),
            ) {
                Text(
                    text = drone.ridCode,
                    style = DroneTypography.label,
                    color = colors.primaryForeground,
                )
            }
        }
    }
}

/** 左标签右值行 */
@Composable
private fun LabelValueRow(label: String, value: String, mono: Boolean = false) {
    val colors = LocalDroneColors.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            style = DroneTypography.caption,
            color = colors.mutedForeground,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = if (mono) DroneTypography.mono else DroneTypography.caption,
            color = colors.cardForeground,
        )
    }
}

/** 实时指标网格：3 列（参考设计文档第 5.4 节） */
@Composable
private fun MetricsGrid(drone: Drone) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        MetricCard(
            label = stringResource(R.string.metric_height),
            value = formatNumber(drone.heightM),
            unit = stringResource(R.string.unit_m),
            icon = Icons.Filled.ArrowUpward,
            modifier = Modifier.weight(1f),
        )
        MetricCard(
            label = stringResource(R.string.metric_distance),
            value = formatNumber(drone.distanceM),
            unit = stringResource(R.string.unit_m),
            icon = Icons.Filled.MyLocation,
            modifier = Modifier.weight(1f),
        )
        MetricCard(
            label = stringResource(R.string.metric_speed),
            value = formatNumber(drone.speedMs),
            unit = stringResource(R.string.unit_ms),
            icon = Icons.Filled.Bolt,
            modifier = Modifier.weight(1f),
        )
    }
}

/** 单个指标卡片 */
@Composable
private fun MetricCard(
    label: String,
    value: String,
    unit: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
) {
    val colors = LocalDroneColors.current
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(colors.muted)
            .padding(16.dp),
    ) {
        Text(
            text = label,
            style = DroneTypography.caption,
            color = colors.mutedForeground,
        )
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = value,
                style = DroneTypography.metricValue,
                color = colors.foreground,
            )
            Spacer(Modifier.width(2.dp))
            Text(
                text = unit,
                style = DroneTypography.metricUnit,
                color = colors.mutedForeground,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }
        Spacer(Modifier.height(8.dp))
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = colors.primary,
            modifier = Modifier.size(18.dp),
        )
    }
}

/** 信号信息卡片（参考设计文档第 5.5 节） */
@Composable
private fun SignalInfoCard(drone: Drone, signalLabel: String) {
    val colors = LocalDroneColors.current
    SectionCard(Modifier.padding(horizontal = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SignalBars(level = drone.signalStrength, color = colors.primary)
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.signal_strength_format, signalLabel),
                style = DroneTypography.captionMedium,
                color = colors.foreground,
            )
        }
        Spacer(Modifier.height(12.dp))
        HorizontalDivider(thickness = 1.dp, color = colors.border)
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text(
                    text = stringResource(R.string.protocol),
                    style = DroneTypography.label,
                    color = colors.mutedForeground,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = drone.protocol,
                    style = DroneTypography.captionMedium,
                    color = colors.foreground,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = stringResource(R.string.frequency),
                    style = DroneTypography.label,
                    color = colors.mutedForeground,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = drone.frequency,
                    style = DroneTypography.captionMedium,
                    color = colors.foreground,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        HorizontalDivider(thickness = 1.dp, color = colors.border)
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            PulseDot(
                color = colors.success,
                size = 8.dp,
                pulseScale = 1.4f,
                durationMillis = 1800,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.realtime_updating),
                style = DroneTypography.captionMedium,
                color = colors.success,
            )
        }
    }
}

/** 位置信息卡片：地图缩略图 + 坐标列表（参考设计文档第 5.6 节） */
@Composable
private fun LocationCard(drone: Drone) {
    val colors = LocalDroneColors.current
    val droneCoords = stringResource(
        R.string.coord_format, drone.droneLat, drone.droneLng,
    )
    SectionCard(Modifier.padding(horizontal = 16.dp, vertical = 16.dp)) {
        Text(
            text = stringResource(R.string.location_title),
            style = DroneTypography.pageTitle,
            color = colors.foreground,
        )
        Spacer(Modifier.height(12.dp))
        DualLocationMap(drone)
        Spacer(Modifier.height(12.dp))
        CoordRow(color = colors.primary, label = stringResource(R.string.drone_label), value = droneCoords)
        Spacer(Modifier.height(8.dp))
        if (drone.operatorLat != null && drone.operatorLng != null) {
            val operatorCoords = stringResource(
                R.string.coord_format, drone.operatorLat, drone.operatorLng,
            )
            CoordRow(
                color = colors.success,
                label = stringResource(R.string.operator_label),
                value = operatorCoords,
            )
        } else {
            CoordRow(
                color = colors.success,
                label = stringResource(R.string.operator_label),
                value = "—",
            )
        }
    }
}

/** 地图缩略图：网格 + 无人机/飞手点 + 虚线连接 + 图例（参考设计文档第 5.6 节） */
@Composable
private fun DualLocationMap(drone: Drone) {
    val colors = LocalDroneColors.current
    val droneLabel = drone.name
    val droneDotX = 0.30f
    val droneDotY = 0.38f
    val operatorX = 0.66f
    val operatorY = 0.68f
    val hasOperator = drone.operatorLat != null && drone.operatorLng != null

    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .height(140.dp)
            .clip(RoundedCornerShape(12.dp)),
    ) {
        val w = maxWidth
        val h = maxHeight

        MapGridBackground(
            gridColor = colors.border,
            backgroundColor = colors.muted,
            modifier = Modifier.fillMaxSize(),
            cellSize = 25.dp,
        )
        // 虚线连接 + 圆点 + 外发光
        Canvas(Modifier.fillMaxSize()) {
            val start = Offset(size.width * operatorX, size.height * operatorY)
            val end = Offset(size.width * droneDotX, size.height * droneDotY)
            drawLine(
                color = colors.border.copy(alpha = 0.8f),
                start = start,
                end = end,
                strokeWidth = 0.5.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 2.dp.toPx())),
            )
            // 无人机蓝点 + 外发光
            drawCircle(color = colors.primary.copy(alpha = 0.3f), radius = 10.dp.toPx(), center = end)
            drawCircle(color = colors.primary, radius = 5.dp.toPx(), center = end)
            // 飞手绿点 + 外发光
            if (hasOperator) {
                drawCircle(color = colors.success.copy(alpha = 0.3f), radius = 10.dp.toPx(), center = start)
                drawCircle(color = colors.success, radius = 5.dp.toPx(), center = start)
            }
        }
        // 无人机标签
        Text(
            text = droneLabel,
            style = DroneTypography.micro,
            color = colors.primary,
            modifier = Modifier.offset(
                x = w * droneDotX + 14.dp,
                y = h * droneDotY - 6.dp,
            ),
        )
        // 飞手标签
        Text(
            text = stringResource(R.string.operator_label),
            style = DroneTypography.micro,
            color = colors.success,
            modifier = Modifier.offset(
                x = w * operatorX + 14.dp,
                y = h * operatorY - 6.dp,
            ),
        )
        // 图例
        Row(
            Modifier
                .align(Alignment.BottomStart)
                .padding(start = 12.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            LegendEntry(color = colors.primary, label = stringResource(R.string.drone_label))
            LegendEntry(color = colors.success, label = stringResource(R.string.operator_label))
        }
    }
}

/** 图例条目：小圆点 + 文字 */
@Composable
private fun LegendEntry(color: Color, label: String) {
    val colors = LocalDroneColors.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.size(6.dp).background(color, CircleShape))
        Text(
            text = label,
            style = DroneTypography.micro,
            color = colors.mutedForeground,
        )
    }
}

/** 坐标行：圆点 + 标签 + 等宽坐标值 */
@Composable
private fun CoordRow(color: Color, label: String, value: String) {
    val colors = LocalDroneColors.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        StatusDot(color = color, size = 8.dp)
        Spacer(Modifier.width(8.dp))
        Text(
            text = label,
            style = DroneTypography.label,
            color = colors.mutedForeground,
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = value,
            style = DroneTypography.mono,
            color = colors.foreground,
        )
    }
}

/** 飞手信息卡片（参考设计文档第 5.7 节） */
@Composable
private fun OperatorCard(drone: Drone) {
    val colors = LocalDroneColors.current
    val groundStation = if (drone.operatorLat != null && drone.operatorLng != null) {
        stringResource(R.string.coord_format, drone.operatorLat, drone.operatorLng)
    } else {
        "—"
    }
    SectionCard(Modifier.padding(horizontal = 16.dp)) {
        Text(
            text = stringResource(R.string.operator_title),
            style = DroneTypography.pageTitle,
            color = colors.foreground,
        )
        Spacer(Modifier.height(4.dp))
        OperatorRow(
            label = stringResource(R.string.operator_id),
            value = drone.operatorId ?: "—",
            showDivider = true,
        )
        OperatorRow(
            label = stringResource(R.string.ground_station),
            value = groundStation,
            showDivider = true,
        )
        OperatorRow(
            label = stringResource(R.string.contact),
            value = drone.operatorPhone ?: "—",
            showDivider = false,
        )
    }
}

/** 飞手信息行：上下分割线样式 */
@Composable
private fun OperatorRow(label: String, value: String, showDivider: Boolean) {
    val colors = LocalDroneColors.current
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = DroneTypography.caption,
                color = colors.mutedForeground,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = value,
                style = DroneTypography.mono,
                color = colors.foreground,
            )
        }
        if (showDivider) {
            HorizontalDivider(thickness = 1.dp, color = colors.border)
        }
    }
}
