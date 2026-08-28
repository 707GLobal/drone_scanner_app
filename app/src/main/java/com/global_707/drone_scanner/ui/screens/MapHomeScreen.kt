package com.global_707.drone_scanner.ui.screens

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.global_707.drone_scanner.R
import com.global_707.drone_scanner.data.Drone
import com.global_707.drone_scanner.data.DroneStatus
import com.global_707.drone_scanner.data.MockData
import com.global_707.drone_scanner.ui.components.FrostedPill
import com.global_707.drone_scanner.ui.components.IconLabelRow
import com.global_707.drone_scanner.ui.components.MapGridBackground
import com.global_707.drone_scanner.ui.components.PulseDot
import com.global_707.drone_scanner.ui.components.ScanningSpinner
import com.global_707.drone_scanner.ui.components.StatusDot
import com.global_707.drone_scanner.ui.theme.DroneTypography
import com.global_707.drone_scanner.ui.theme.LocalDroneColors
import com.global_707.drone_scanner.ui.theme.MapBackgroundColor
import java.util.Locale
import kotlin.math.hypot
import kotlin.math.min

/**
 * 页面一：地图主页（Map Home，参考设计文档第 4 节）
 * 地图区域 + 无人机/飞手标记 + 毛玻璃按钮 + 可展开扫描面板 + 底部无人机列表。
 */
@Composable
fun MapHomeScreen(
    onOpenSettings: () -> Unit,
    onDroneClick: (Drone) -> Unit,
) {
    val colors = LocalDroneColors.current
    val drones = remember { MockData.drones }
    var scanPanelVisible by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        // ---- 地图区域 ----
        MapArea(
            drones = drones,
            scanPanelVisible = scanPanelVisible,
            onToggleScanPanel = { scanPanelVisible = !scanPanelVisible },
            onOpenSettings = onOpenSettings,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        )

        // ---- 底部无人机列表面板 ----
        DroneListPanel(drones = drones, onDroneClick = onDroneClick)
    }
}

/** 地图区域：底图 + 标记 + 毛玻璃按钮 + 扫描状态面板（参考设计文档第 4.1/4.2/4.3 节） */
@Composable
private fun MapArea(
    drones: List<Drone>,
    scanPanelVisible: Boolean,
    onToggleScanPanel: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalDroneColors.current

    BoxWithConstraints(modifier) {
        val mapWidth = maxWidth
        val mapHeight = maxHeight

        // 地图底图
        MapGridBackground(
            gridColor = colors.border,
            backgroundColor = MapBackgroundColor,
            modifier = Modifier.fillMaxSize(),
        )

        // 飞手 → 无人机 虚线连接
        Canvas(Modifier.fillMaxSize()) {
            val dash = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()))
            val lineLength = 32.dp.toPx()
            drones.forEachIndexed { index, _ ->
                val marker = MockData.markers[index]
                val opX = marker.operatorX ?: return@forEachIndexed
                val op = Offset(
                    size.width * opX,
                    size.height * marker.operatorY!!,
                )
                val dronePoint = Offset(
                    size.width * marker.droneX,
                    size.height * marker.droneY,
                )
                val dist = hypot(dronePoint.x - op.x, dronePoint.y - op.y)
                if (dist > 0f) {
                    val len = min(lineLength, dist)
                    val ux = (dronePoint.x - op.x) / dist
                    val uy = (dronePoint.y - op.y) / dist
                    drawLine(
                        color = colors.success.copy(alpha = 0.6f),
                        start = op,
                        end = Offset(op.x + ux * len, op.y + uy * len),
                        strokeWidth = 1.5.dp.toPx(),
                        pathEffect = dash,
                    )
                }
            }
        }

        // 无人机标记（蓝色脉冲）
        drones.forEachIndexed { index, drone ->
            val marker = MockData.markers[index]
            PulseDot(
                color = colors.primary,
                size = 18.dp,
                modifier = Modifier.offset(
                    x = mapWidth * marker.droneX - 9.dp,
                    y = mapHeight * marker.droneY - 9.dp,
                ),
            )
            // 无人机名称标签
            Text(
                text = drone.name,
                style = DroneTypography.micro,
                color = colors.primary,
                modifier = Modifier.offset(
                    x = mapWidth * marker.droneX + 8.dp,
                    y = mapHeight * marker.droneY - 6.dp,
                ),
            )
        }

        // 飞手标记（绿色脉冲）
        drones.forEachIndexed { index, _ ->
            val marker = MockData.markers[index]
            val opX = marker.operatorX ?: return@forEachIndexed
            PulseDot(
                color = colors.success,
                size = 12.dp,
                modifier = Modifier.offset(
                    x = mapWidth * opX - 6.dp,
                    y = mapHeight * marker.operatorY!! - 6.dp,
                ),
            )
        }

        // 左上角：设置入口
        FrostedPill(
            onClick = onOpenSettings,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 16.dp, top = 56.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Menu,
                contentDescription = stringResource(R.string.settings_title),
                tint = colors.foreground,
                modifier = Modifier.size(20.dp),
            )
        }

        // 右上角：扫描状态按钮
        FrostedPill(
            onClick = onToggleScanPanel,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(end = 16.dp, top = 56.dp),
        ) {
            PulseDot(
                color = colors.success,
                size = 8.dp,
                pulseScale = 2f,
            )
            Text(
                text = stringResource(R.string.scanning),
                style = DroneTypography.captionMedium,
                color = colors.foreground,
            )
        }

        // 可展开扫描状态面板
        AnimatedVisibility(
            visible = scanPanelVisible,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(start = 16.dp, end = 16.dp, top = 104.dp),
        ) {
            ScanStatusPanel()
        }
    }
}

/** 扫描状态面板（参考设计文档第 4.3 节） */
@Composable
private fun ScanStatusPanel() {
    val colors = LocalDroneColors.current
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.card)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        IconLabelRow(icon = Icons.Filled.Wifi, text = stringResource(R.string.wifi_scanning)) {
            ScanningSpinner(size = 14.dp)
        }
        HorizontalDivider(
            modifier = Modifier.padding(vertical = 10.dp),
            thickness = 1.dp,
            color = colors.border,
        )
        IconLabelRow(icon = Icons.Filled.Bluetooth, text = stringResource(R.string.bluetooth_scanning)) {
            ScanningSpinner(size = 14.dp)
        }
    }
}

/** 底部无人机列表面板（参考设计文档第 4.4 节） */
@Composable
private fun DroneListPanel(
    drones: List<Drone>,
    onDroneClick: (Drone) -> Unit,
) {
    val colors = LocalDroneColors.current
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            .background(colors.card),
    ) {
        // Drag handle
        Box(
            Modifier
                .align(Alignment.CenterHorizontally)
                .padding(top = 10.dp, bottom = 6.dp)
                .size(width = 36.dp, height = 5.dp)
                .background(colors.mutedForeground.copy(alpha = 0.3f), CircleShape),
        )

        // Header
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.detected_drones),
                style = DroneTypography.pageTitle.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold),
                color = colors.foreground,
                modifier = Modifier.weight(1f),
            )
            Box(
                Modifier
                    .background(colors.primary, RoundedCornerShape(50))
                    .padding(horizontal = 10.dp, vertical = 2.dp),
            ) {
                Text(
                    text = "${drones.size}",
                    style = DroneTypography.label,
                    color = colors.primaryForeground,
                )
            }
        }

        // 列表
        LazyColumn(
            modifier = Modifier.heightIn(max = 240.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 12.dp),
        ) {
            items(drones, key = { it.id }) { drone ->
                DroneListItem(drone = drone, onClick = { onDroneClick(drone) })
            }
        }
    }
}

/** 无人机列表项（参考设计文档第 4.4 节） */
@Composable
private fun DroneListItem(drone: Drone, onClick: () -> Unit) {
    val colors = LocalDroneColors.current
    val statusColor = when (drone.status) {
        DroneStatus.SAFE -> colors.success
        DroneStatus.WARNING -> colors.warning
        DroneStatus.DANGER -> colors.danger
    }
    val auxInfo = buildString {
        append(formatNumber(drone.heightM)).append("m · ")
        append(formatNumber(drone.distanceM)).append("m · ")
        append(stringResource(R.string.operator_distance_format, drone.operatorDistanceM))
    }
    val timeAgo = stringResource(R.string.time_ago_format, drone.lastSeenSeconds)

    Row(
        Modifier
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(colors.muted)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusDot(color = statusColor, size = 10.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = drone.name,
                style = DroneTypography.body,
                color = colors.foreground,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = auxInfo,
                style = DroneTypography.label,
                color = colors.mutedForeground,
            )
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = timeAgo,
                style = DroneTypography.label,
                color = colors.mutedForeground,
            )
            Spacer(Modifier.height(2.dp))
            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = colors.mutedForeground,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

/** 数值格式化：整数不带小数点，否则保留 1 位 */
internal fun formatNumber(value: Float): String =
    if (value % 1f == 0f) value.toInt().toString() else String.format(Locale.US, "%.1f", value)
