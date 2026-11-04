package com.global_707.drone_scanner.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.global_707.drone_scanner.R
import com.global_707.drone_scanner.data.AppPrefs
import com.global_707.drone_scanner.data.Drone
import com.global_707.drone_scanner.data.DroneStatus
import com.global_707.drone_scanner.data.ScanMode
import com.global_707.drone_scanner.data.ScanState
import com.global_707.drone_scanner.data.ScannerController
import com.global_707.drone_scanner.ui.components.FrostedPill
import com.global_707.drone_scanner.ui.components.StatusDot
import com.global_707.drone_scanner.ui.map.DroneMap
import com.global_707.drone_scanner.ui.map.LocateTarget
import com.global_707.drone_scanner.ui.map.MapBackendProvider
import com.global_707.drone_scanner.ui.map.MapChromePill
import com.global_707.drone_scanner.ui.map.MapControls
import com.global_707.drone_scanner.ui.map.MapOverlay
import com.global_707.drone_scanner.ui.map.MapPopupSurface
import com.global_707.drone_scanner.ui.map.rememberLayerMenu
import com.global_707.drone_scanner.ui.map.rememberMapOverlayState
import com.global_707.drone_scanner.ui.theme.AppIcons
import com.global_707.drone_scanner.ui.theme.DroneTypography
import com.global_707.drone_scanner.ui.theme.LocalDroneColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.roundToInt

/**
 * 首页（Main）：
 *  - 上层：全屏地图，仅显示飞机标记（不显示遥控站）
 *  - 右下：扫描状态按钮（固定不动、显示真实状态），点击缩放展开扫描状态面板，
 *    点击面板外任意位置关闭
 *  - 左下：地图悬浮控件（图层 / 定位 / 北向）
 *  - 下层：可拖拽 Bottom Sheet（常驻 1/3 屏，上拉展开全屏列表）
 */
@Composable
fun MapHomeScreen(
    onOpenSettings: () -> Unit,
    onOpenSearchSettings: () -> Unit,
    onOpenLayerSettings: () -> Unit,
    onDroneClick: (Drone) -> Unit,
) {
    val colors = LocalDroneColors.current
    val context = LocalContext.current
    val appContext = context.applicationContext
    // 图层菜单（底图单选 + 叠加图层开关）：三处地图页共用同一份组装逻辑
    val layerMenu = rememberLayerMenu(context, onOpenLayerSettings)
    // 地图浮层状态（图层菜单 / 定位菜单 / 扫描菜单互斥）
    val overlay = rememberMapOverlayState()
    var permissionsGranted by remember { mutableStateOf(checkAllPermissions(context)) }
    val pendingFeatureHint = stringResource(R.string.map_feature_pending)

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

    // 返回键：先关闭地图浮层，再交给页面返回（主流地图 App 的一致行为）
    BackHandler(enabled = overlay.open != MapOverlay.NONE) { overlay.close() }

    LaunchedEffect(Unit) {
        if (!permissionsGranted) {
            permissionLauncher.launch(neededPermissions().toTypedArray())
        } else {
            ScannerController.start(appContext)
        }
    }

    // 实时轮询系统蓝牙 / Wi-Fi 开关：开关状态变化时重启扫描，保证扫描状态真实
    LaunchedEffect(Unit) {
        while (true) {
            val changed = ScannerController.refreshSystemState(context)
            if (changed) {
                ScannerController.restart(appContext)
            }
            delay(2000)
        }
    }

    // 系统开关提醒：设置中启用的扫描通道，但系统蓝牙/Wi-Fi 未开启 → 弹窗引导开启
    // 外置天线模式下手机射频不参与扫描，不做该提醒
    val internalMode = AppPrefs.scanMode == ScanMode.INTERNAL
    val needBluetooth = internalMode && AppPrefs.bluetoothScanEnabled && !ScannerController.bluetoothSystemOn
    val needWifi = internalMode && AppPrefs.wifiScanEnabled && !ScannerController.wifiSystemOn
    var reminderDismissed by remember { mutableStateOf(false) }
    val systemOk = !needBluetooth && !needWifi
    // 系统恢复后重置"暂不"标记，下次再关闭时重新提醒
    LaunchedEffect(systemOk) {
        if (systemOk) reminderDismissed = false
    }
    if ((needBluetooth || needWifi) && !reminderDismissed) {
        SystemSwitchReminderDialog(
            needBluetooth = needBluetooth,
            needWifi = needWifi,
            onDismiss = { reminderDismissed = true },
            onGoSettings = {
                val intent = when {
                    needBluetooth -> Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
                    else -> Intent(Settings.ACTION_WIFI_SETTINGS)
                }
                runCatching { context.startActivity(intent) }
            },
        )
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        // ---- 地图（铺满全屏） ----
        DroneMap(
            drones = ScannerController.drones,
            satellite = AppPrefs.satelliteMap,
            airspaceLayers = AppPrefs.airspaceVisible,
            showOperator = false,
            showMyLocation = true,
            showLabels = true,
            // 首页/返回首页时默认定位到本机位置（详情页不传，定位到无人机）
            centerOnMyLocationOnce = true,
            modifier = Modifier.fillMaxSize(),
        )

        // 浮层打开时：全屏点击遮罩（地图之上、按钮之下；点其它按钮可直接切换菜单）
        if (overlay.open != MapOverlay.NONE) {
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) { detectTapGestures { overlay.close() } },
            )
        }

        // 权限未授予提示
        if (!permissionsGranted) {
            MapChromePill(
                onClick = { permissionLauncher.launch(neededPermissions().toTypedArray()) },
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

        // ---- 左上角：设置入口 + 地图悬浮控件 ----
        MapChromePill(
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
        MapControls(
            layers = layerMenu,
            overlay = overlay,
            locateTargets = listOfNotNull(
                // 定位到设备位置
                LocateTarget(
                    icon = AppIcons.Phone,
                    contentDescription = stringResource(R.string.locate_to_device),
                    onClick = {
                        if (!MapBackendProvider.backend.locateMe(context)) {
                            android.widget.Toast.makeText(context, pendingFeatureHint, android.widget.Toast.LENGTH_SHORT).show()
                        }
                    },
                ),
                // 定位到所有无人机（视野框住全部，约 60% 区域）
                ScannerController.drones
                    .takeIf { list -> list.any { it.droneLat != null && it.droneLng != null } }
                    ?.let {
                        LocateTarget(
                            icon = AppIcons.Flight,
                            contentDescription = stringResource(R.string.locate_to_drone),
                            onClick = {
                                MapBackendProvider.backend.moveToDrones(ScannerController.drones)
                            },
                        )
                    },
            ),
            onResetNorth = {
                if (!MapBackendProvider.backend.resetNorth(context)) {
                    android.widget.Toast.makeText(context, pendingFeatureHint, android.widget.Toast.LENGTH_SHORT).show()
                }
            },
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 16.dp, top = 108.dp),
        )

        // ---- 右上角：扫描状态药丸（与其它浮层同一套设计）+ 统一菜单 ----
        var scanPillHeight by remember { mutableStateOf(0.dp) }
        val density = LocalDensity.current
        val (statusText, statusColor) = scanStatusInfo(permissionsGranted)
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .padding(end = 16.dp, top = 56.dp)
                .onSizeChanged {
                    // 回调不是 @Composable，密度必须在组合上下文里先取好
                    scanPillHeight = with(density) { it.height.toDp() }
                },
        ) {
            MapChromePill(onClick = { overlay.toggle(MapOverlay.SCAN) }) {
                // 静态状态点（无脉冲动画）
                Box(
                    Modifier
                        .size(8.dp)
                        .background(statusColor, CircleShape),
                )
                Text(
                    text = statusText,
                    style = DroneTypography.captionMedium,
                    color = colors.foreground,
                )
            }
            if (overlay.isOpen(MapOverlay.SCAN)) {
                MapPopupSurface(
                    alignment = Alignment.TopEnd,
                    offset = IntOffset(
                        0,
                        with(density) { (scanPillHeight + 8.dp).roundToPx() },
                    ),
                ) {
                    ScanStatusMenu(
                        permissionsGranted = permissionsGranted,
                        onRequestPermissions = {
                            overlay.close()
                            permissionLauncher.launch(neededPermissions().toTypedArray())
                        },
                        onOpenSearchSettings = {
                            overlay.close()
                            onOpenSearchSettings()
                        },
                    )
                }
            }
        }

        // ---- 下层：可拖拽 Bottom Sheet ----
        DroneBottomSheet(
            drones = ScannerController.drones,
            onDroneClick = onDroneClick,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

/** 扫描状态按钮的文字与颜色（真实状态，非死代码） */
@Composable
private fun scanStatusInfo(permissionsGranted: Boolean): Pair<String, Color> {
    val colors = LocalDroneColors.current
    val state = ScannerController.scanState
    return when {
        // 外置天线模式：手机不扫射频，等待板端设备接入（权限与通道状态均不适用）
        AppPrefs.scanMode == ScanMode.EXTERNAL ->
            stringResource(R.string.scan_status_external_wait) to colors.mutedForeground

        !permissionsGranted ->
            stringResource(R.string.scan_status_perm) to colors.danger

        !AppPrefs.bluetoothScanEnabled && !AppPrefs.wifiScanEnabled ->
            stringResource(R.string.scan_status_off) to colors.mutedForeground

        state.hasError ->
            stringResource(R.string.scan_status_error) to colors.warning

        state.isScanning ->
            stringResource(R.string.scanning) to colors.success

        else ->
            stringResource(R.string.scan_status_stopped) to colors.mutedForeground
    }
}

/** 系统蓝牙 / Wi-Fi 未开启提醒弹窗 */
@Composable
private fun SystemSwitchReminderDialog(
    needBluetooth: Boolean,
    needWifi: Boolean,
    onDismiss: () -> Unit,
    onGoSettings: () -> Unit,
) {
    val message = buildList {
        if (needBluetooth) add(stringResource(R.string.reminder_bt))
        if (needWifi) add(stringResource(R.string.reminder_wifi))
    }.joinToString("\n")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.reminder_title)) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onGoSettings) {
                Text(stringResource(R.string.reminder_go_settings))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.reminder_later))
            }
        },
    )
}

/**
 * 可拖拽无人机列表面板：
 *  - 收起：常驻屏幕约 1/3（标题 + 数量 + 前几项）
 *  - 上拉：展开为全屏列表（呼号 / 距离 / 高度）
 */
@Composable
private fun DroneBottomSheet(
    drones: List<Drone>,
    onDroneClick: (Drone) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalDroneColors.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    BoxWithConstraints(modifier.fillMaxWidth()) {
        val screenHeight = maxHeight
        val expandedHeight = screenHeight * 0.85f
        // 常驻面积：约屏幕 1/3
        val collapsedHeight = screenHeight * 0.33f
        val maxDrag = (expandedHeight - collapsedHeight)
        val maxDragPx = with(density) { maxDrag.toPx() }

        // sheetOffset: 0 = 完全展开；maxDragPx = 收起
        var sheetOffset by remember { mutableFloatStateOf(maxDragPx) }

        Column(
            Modifier
                .fillMaxWidth()
                .height(expandedHeight)
                .offset { IntOffset(0, sheetOffset.roundToInt()) }
                .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                .background(colors.card)
                .draggable(
                    orientation = Orientation.Vertical,
                    state = rememberDraggableState { delta ->
                        sheetOffset = (sheetOffset + delta).coerceIn(0f, maxDragPx)
                    },
                    onDragStopped = { velocity ->
                        val target = when {
                            velocity > 800f -> maxDragPx
                            velocity < -800f -> 0f
                            sheetOffset > maxDragPx / 2 -> maxDragPx
                            else -> 0f
                        }
                        scope.launch {
                            animate(
                                initialValue = sheetOffset,
                                targetValue = target,
                                animationSpec = spring(),
                            ) { value, _ ->
                                sheetOffset = value
                            }
                        }
                    },
                ),
        ) {
            // Drag handle
            Box(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 10.dp, bottom = 6.dp)
                    .size(width = 36.dp, height = 5.dp)
                    .background(colors.mutedForeground.copy(alpha = 0.3f), CircleShape),
            )

            // Header：标题 + 数量（演示模式显示"演示"标签）
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
                if (AppPrefs.demoMode) {
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

            // 列表（收起时可见前几项）
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
                    modifier = Modifier
                        .weight(1f)
                        .navigationBarsPadding(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 12.dp),
                ) {
                    items(drones, key = { it.id }) { drone ->
                        DroneListItem(
                            drone = drone,
                            onClick = { onDroneClick(drone) },
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
            }
        }
    }
}

/** 无人机列表项：状态点 + 呼号 + 距离/高度 */
@Composable
private fun DroneListItem(drone: Drone, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalDroneColors.current
    val statusColor = when (drone.status) {
        DroneStatus.SAFE -> colors.success
        DroneStatus.WARNING -> colors.warning
        DroneStatus.DANGER -> colors.danger
    }
    val auxInfo = listOf(
        drone.distanceM?.let { stringResource(R.string.distance_format, formatNumber(it)) } ?: "—",
        drone.heightAboveTakeoffM?.let { stringResource(R.string.altitude_format, formatNumber(it)) }
            ?: drone.geodeticAltitudeM?.let { stringResource(R.string.altitude_format, formatNumber(it)) }
            ?: "—",
    ).joinToString(" · ")
    val timeAgo = stringResource(R.string.time_ago_format, drone.lastSeenSeconds)

    Row(
        modifier
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
                imageVector = Icons.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = colors.mutedForeground,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

// ---------- 权限辅助 ----------

private fun neededPermissions(): List<String> = buildList {
    // 外置天线模式：射频扫描由板端负责，本机无需附近设备 / 定位权限
    // （返回空表时 checkAllPermissions 天然为 true，首页不会卡在权限门槛上）
    if (AppPrefs.scanMode == ScanMode.EXTERNAL) return@buildList
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
        MapHomeScreen(onOpenSettings = {}, onOpenSearchSettings = {}, onOpenLayerSettings = {}, onDroneClick = {})
    }
}
