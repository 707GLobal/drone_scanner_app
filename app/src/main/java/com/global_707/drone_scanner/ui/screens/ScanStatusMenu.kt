package com.global_707.drone_scanner.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.global_707.drone_scanner.R
import com.global_707.drone_scanner.data.AppPrefs
import com.global_707.drone_scanner.data.ScanMode
import com.global_707.drone_scanner.data.ScanState
import com.global_707.drone_scanner.data.ScannerController
import com.global_707.drone_scanner.ui.map.MapMenuActionRow
import com.global_707.drone_scanner.ui.map.mapMenuWidth
import com.global_707.drone_scanner.ui.theme.AppIcons
import com.global_707.drone_scanner.ui.theme.DroneTypography
import com.global_707.drone_scanner.ui.theme.LocalDroneColors

/**
 * 扫描状态菜单（右上角药丸点开）。
 *
 * 与图层菜单**同一套卡片样式**（252dp、16dp 圆角、card@0.82 + 1dp 描边、同一出入场动画）。
 * 结构：扫描模式（图标 + 标签 + 值）→ Wi-Fi / 蓝牙通道 → 底部动作行（与「图层设置」同款）。
 */
@Composable
fun ScanStatusMenu(
    permissionsGranted: Boolean,
    onRequestPermissions: () -> Unit,
    onOpenSearchSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalDroneColors.current
    val state = ScannerController.scanState

    Column(
        modifier = modifier
            .width(mapMenuWidth())
            .clip(RoundedCornerShape(16.dp))
            .background(colors.card.copy(alpha = 0.82f))
            .border(1.dp, colors.border.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
            .padding(vertical = 8.dp),
    ) {
        // 扫描模式（与下方通道行同一模板：图标 + 标签 + 右侧值）
        ModeRow(
            icon = AppIcons.Tune,
            label = stringResource(R.string.scan_mode_label),
            value = stringResource(
                if (AppPrefs.scanMode == ScanMode.EXTERNAL) {
                    R.string.scan_source_external
                } else {
                    R.string.scan_source_internal
                },
            ),
        )

        MenuDivider()

        if (!permissionsGranted) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 2.dp)) {
                Text(
                    text = stringResource(R.string.scan_status_perm),
                    style = DroneTypography.captionMedium,
                    color = colors.danger,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.scan_panel_solution),
                    style = DroneTypography.label,
                    color = colors.mutedForeground,
                )
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onRequestPermissions)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.permission_required),
                    style = DroneTypography.body,
                    color = colors.primary,
                )
            }
            MenuDivider()
        }

        if (AppPrefs.scanMode == ScanMode.EXTERNAL) {
            ModeRow(
                icon = AppIcons.Gamepad,
                label = stringResource(R.string.scan_channel_external),
                value = stringResource(R.string.scan_external_not_connected),
            )
            Text(
                text = stringResource(R.string.scan_external_hint),
                style = DroneTypography.label,
                color = colors.mutedForeground,
                modifier = Modifier.padding(start = 42.dp, end = 16.dp, top = 2.dp),
            )
        } else {
            ChannelStatusRow(
                icon = AppIcons.Wifi,
                isBluetooth = false,
                label = stringResource(R.string.scan_wifi),
                enabled = AppPrefs.wifiScanEnabled,
                scanning = state.wifiScanning,
                errorCode = state.wifiErrorCode,
            )
            ChannelStatusRow(
                icon = AppIcons.Bluetooth,
                isBluetooth = true,
                label = stringResource(R.string.scan_bluetooth),
                enabled = AppPrefs.bluetoothScanEnabled,
                scanning = state.bluetoothScanning,
                errorCode = state.bluetoothErrorCode,
            )
            if (state.hasError || !AppPrefs.wifiScanEnabled || !AppPrefs.bluetoothScanEnabled) {
                Text(
                    text = stringResource(R.string.scan_panel_solution),
                    style = DroneTypography.label,
                    color = colors.mutedForeground,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
        }

        MenuDivider()
        MapMenuActionRow(
            // 与「设置 → 搜索设置」同一个图标，保持入口一致性
            icon = Icons.Filled.Search,
            label = stringResource(R.string.search_settings),
            onClick = onOpenSearchSettings,
        )
    }
}

/** 菜单内一行：16dp 图标 + 标签（左）+ 值（右），三段（模式/Wi-Fi/蓝牙）共用同一对齐 */
@Composable
private fun ModeRow(icon: ImageVector, label: String, value: String) {
    val colors = LocalDroneColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = colors.foreground,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = label,
            style = DroneTypography.body,
            color = colors.foreground,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = DroneTypography.label,
            color = colors.mutedForeground,
        )
    }
}

@Composable
private fun MenuDivider() {
    val colors = LocalDroneColors.current
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        thickness = 1.dp,
        color = colors.border,
    )
}

/** 单个扫描通道状态行：与 [ModeRow] 完全同一套对齐 */
@Composable
private fun ChannelStatusRow(
    icon: ImageVector,
    isBluetooth: Boolean,
    label: String,
    enabled: Boolean,
    scanning: Boolean,
    errorCode: Int?,
) {
    val colors = LocalDroneColors.current
    val value = when {
        !enabled -> stringResource(R.string.scan_channel_off)
        scanning -> stringResource(if (isBluetooth) R.string.bluetooth_scanning else R.string.wifi_scanning)
        errorCode != null -> channelErrorText(isBluetooth = isBluetooth, errorCode = errorCode)
        else -> stringResource(R.string.scan_channel_idle)
    }
    val valueColor = when {
        !enabled -> colors.mutedForeground
        scanning -> colors.success
        errorCode != null -> colors.danger
        else -> colors.mutedForeground
    }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = colors.foreground,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = label,
            style = DroneTypography.body,
            color = colors.foreground,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = DroneTypography.label,
            color = valueColor,
        )
    }
}

/** 扫描错误码 → 文案（环境错误用自定义文案，其余回退显示错误码） */
@Composable
internal fun channelErrorText(isBluetooth: Boolean, errorCode: Int): String = when {
    isBluetooth && errorCode == ScanState.BT_ERR_UNAVAILABLE -> stringResource(R.string.bt_unavailable)
    isBluetooth && errorCode == ScanState.BT_ERR_DISABLED -> stringResource(R.string.bt_disabled)
    !isBluetooth && errorCode == ScanState.WIFI_ERR_UNAVAILABLE -> stringResource(R.string.wifi_unavailable)
    else -> stringResource(R.string.scan_failed_code, errorCode)
}
