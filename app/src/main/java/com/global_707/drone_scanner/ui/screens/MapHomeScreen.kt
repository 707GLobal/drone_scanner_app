package com.global_707.drone_scanner.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.global_707.drone_scanner.R
import com.global_707.drone_scanner.data.AppPrefs
import com.global_707.drone_scanner.data.Drone
import com.global_707.drone_scanner.data.DroneStatus
import com.global_707.drone_scanner.data.ScannerController
import com.global_707.drone_scanner.ui.components.FrostedPill
import com.global_707.drone_scanner.ui.components.PulseDot
import com.global_707.drone_scanner.ui.components.ScanningSpinner
import com.global_707.drone_scanner.ui.components.StatusDot
import com.global_707.drone_scanner.ui.map.DroneMap
import com.global_707.drone_scanner.ui.theme.DroneTypography
import com.global_707.drone_scanner.ui.theme.LocalDroneColors
import java.util.Locale

/**
 * 页面一：地图主页（Map Home）
 * 高德地图 + 真实 RID 扫描标记 + 毛玻璃按钮 + 可展开扫描面板 + 底部无人机列表。
 */
@Composable
fun MapHomeScreen(
    onOpenSettings: () -> Unit,
    onDroneClick: (Drone) -> Unit,
) {
    val colors = LocalDroneColors.current
    val context = LocalContext.current
    val appContext = context.applicationContext
    var scanPanelVisible by remember { mutableStateOf(false) }
    var permissionsGranted by remember { mutableStateOf(checkAllPermissions(context)) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        permissionsGranted = checkAllPermissions(context)
        if (grants.values.any { it }) {
            ScannerController.start(appContext)
        } else {
            ScannerController.markScanStopped()
        }
    }

    // 首次进入：请求权限并启动扫描
    LaunchedEffect(Unit) {
        if (!permissionsGranted) {
            permissionLauncher.launch(neededPermissions().toTypedArray())
        } else {
            ScannerController.start(appContext)
        }
    }

    // 离开页面停止扫描
    DisposableEffect(Unit) {
        onDispose { ScannerController.stop() }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        // ---- 地图区域 ----
        MapArea(
            permissionsGranted = permissionsGranted,
            scanPanelVisible = scanPanelVisible,
            onToggleScanPanel = { scanPanelVisible = !scanPanelVisible },
            onOpenSettings = onOpenSettings,
            onRequestPermissions = {
                permissionLauncher.launch(neededPermissions().toTypedArray())
            },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        )

        // ---- 底部无人机列表面板 ----
        DroneListPanel(
            drones = ScannerController.drones,
            isDemoMode = AppPrefs.demoMode,
            onDroneClick = onDroneClick,
        )
    }
}

/** 地图区域：高德地图 + 毛玻璃按钮 + 扫描状态面板 */
@Composable
private fun MapArea(
    permissionsGranted: Boolean,
    scanPanelVisible: Boolean,
    onToggleScanPanel: () -> Unit,
    onOpenSettings: () -> Unit,
    onRequestPermissions: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalDroneColors.current

    Box(modifier) {
        // 地图（预留接口：接入真实地图 SDK 后自动替换，见 ui/map/MapBackend.kt）
        DroneMap(
            drones = ScannerController.drones,
            satellite = AppPrefs.satelliteMap,
            showOperator = AppPrefs.showOperatorMarkers,
            showLabels = AppPrefs.showNameLabels,
            modifier = Modifier.fillMaxSize(),
        )

        // 权限未授予提示
        if (!permissionsGranted) {
            FrostedPill(
                onClick = onRequestPermissions,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 56.dp),
            ) {
                Text(
                    text = stringResource(R.string.permission_required),
                    style = DroneTypography.captionMedium,
                    color = colors.danger,
                )
            }
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
                color = if (ScannerController.scanState.isScanning) colors.success else colors.mutedForeground,
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

/** 扫描状态面板（真实扫描状态） */
@Composable
private fun ScanStatusPanel() {
    val colors = LocalDroneColors.current
    val state = ScannerController.scanState
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.card)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        ScanStatusRow(
            icon = Icons.Filled.Wifi,
            text = stringResource(R.string.wifi_scanning),
            scanning = state.wifiScanning,
        )
        HorizontalDivider(
            modifier = Modifier.padding(vertical = 10.dp),
            thickness = 1.dp,
            color = colors.border,
        )
        ScanStatusRow(
            icon = Icons.Filled.Bluetooth,
            text = stringResource(R.string.bluetooth_scanning),
            scanning = state.bluetoothScanning,
        )
    }
}

/** 扫描状态行 */
@Composable
private fun ScanStatusRow(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, scanning: Boolean) {
    val colors = LocalDroneColors.current
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (scanning) colors.foreground else colors.mutedForeground,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = text,
            style = DroneTypography.captionMedium,
            color = if (scanning) colors.foreground else colors.mutedForeground,
            modifier = Modifier.weight(1f),
        )
        if (scanning) {
            ScanningSpinner(size = 14.dp)
        } else {
            Text(
                text = stringResource(R.string.scan_off),
                style = DroneTypography.label,
                color = colors.mutedForeground,
            )
        }
    }
}

/** 底部无人机列表面板（含空状态与演示模式标识） */
@Composable
private fun DroneListPanel(
    drones: List<Drone>,
    isDemoMode: Boolean,
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
                style = DroneTypography.pageTitle.copy(fontWeight = FontWeight.Bold),
                color = colors.foreground,
                modifier = Modifier.weight(1f),
            )
            if (isDemoMode) {
                Text(
                    text = stringResource(R.string.demo_badge),
                    style = DroneTypography.label,
                    color = colors.warning,
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .clip(RoundedCornerShape(50))
                        .background(colors.warning.copy(alpha = 0.15f))
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
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

        // 列表 / 空状态
        if (drones.isEmpty()) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(R.string.empty_drones),
                    style = DroneTypography.body,
                    color = colors.foreground,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.empty_drones_hint),
                    style = DroneTypography.label,
                    color = colors.mutedForeground,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.heightIn(max = 240.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 12.dp),
            ) {
                items(drones, key = { it.id }) { drone ->
                    DroneListItem(drone = drone, onClick = { onDroneClick(drone) })
                }
            }
        }
    }
}

/** 无人机列表项 */
@Composable
private fun DroneListItem(drone: Drone, onClick: () -> Unit) {
    val colors = LocalDroneColors.current
    val statusColor = when (drone.status) {
        DroneStatus.SAFE -> colors.success
        DroneStatus.WARNING -> colors.warning
        DroneStatus.DANGER -> colors.danger
    }
    val auxInfo = listOf(
        drone.heightM?.let { "${formatNumber(it)}m" } ?: "—",
        drone.distanceM?.let { "${formatNumber(it)}m" } ?: "—",
        drone.operatorDistanceM?.let { stringResource(R.string.operator_distance_format, it) } ?: "—",
    ).joinToString(" · ")
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

// ---------- 权限辅助 ----------

private fun neededPermissions(): List<String> = buildList {
    add(Manifest.permission.ACCESS_FINE_LOCATION)
    add(Manifest.permission.ACCESS_COARSE_LOCATION)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        add(Manifest.permission.BLUETOOTH_SCAN)
        add(Manifest.permission.BLUETOOTH_CONNECT)
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        add(Manifest.permission.NEARBY_WIFI_DEVICES)
    }
}

private fun checkAllPermissions(context: Context): Boolean =
    neededPermissions().all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

/** 数值格式化：整数不带小数点，否则保留 1 位 */
internal fun formatNumber(value: Float): String =
    if (value % 1f == 0f) value.toInt().toString() else String.format(Locale.US, "%.1f", value)

@androidx.compose.ui.tooling.preview.Preview(showBackground = true, widthDp = 375, heightDp = 812)
@Composable
private fun MapHomeScreenPreview() {
    com.global_707.drone_scanner.ui.theme.DroneScannerTheme {
        MapHomeScreen(onOpenSettings = {}, onDroneClick = {})
    }
}
