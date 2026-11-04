package com.global_707.drone_scanner.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.activity.compose.BackHandler
import com.global_707.drone_scanner.R
import com.global_707.drone_scanner.data.AppPrefs
import com.global_707.drone_scanner.data.Drone
import com.global_707.drone_scanner.data.ScannerController
import com.global_707.drone_scanner.ui.map.DroneMap
import com.global_707.drone_scanner.ui.map.LocateTarget
import com.global_707.drone_scanner.ui.map.MapBackendProvider
import com.global_707.drone_scanner.ui.map.MapChromeLabelPill
import com.global_707.drone_scanner.ui.map.MapControls
import com.global_707.drone_scanner.ui.map.MapOverlay
import com.global_707.drone_scanner.ui.map.rememberMapOverlayState
import com.global_707.drone_scanner.ui.map.rememberLayerMenu
import com.global_707.drone_scanner.ui.theme.AppIcons
import com.global_707.drone_scanner.ui.theme.DroneTypography
import com.global_707.drone_scanner.ui.theme.LocalDroneColors
import com.global_707.drone_scanner.util.NavigationLauncher
import java.util.Locale

/**
 * 全局导航栏：M3 CenterAlignedTopAppBar（谷歌原生返回按钮 + 水波纹 + 过渡动画）。
 * 供详情页 / 设置页 / 子页面共用。
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
internal fun HeaderBar(title: String, onBack: () -> Unit) {
    val colors = LocalDroneColors.current
    androidx.compose.material3.CenterAlignedTopAppBar(
        title = {
            Text(
                text = title,
                style = DroneTypography.pageTitle,
                color = colors.foreground,
            )
        },
        navigationIcon = {
            androidx.compose.material3.IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.back),
                    tint = colors.primary,
                )
            }
        },
        colors = androidx.compose.material3.TopAppBarDefaults.centerAlignedTopAppBarColors(
            containerColor = colors.card.copy(alpha = 0.85f),
        ),
    )
}

/**
 * 飞机详情页：
 *  - 上半屏（≥1/2）：位置追踪大图，三个实时动态标记（飞机 / 遥控站 / 我的位置），
 *    支持全屏查看、导航到飞机 / 导航到遥控站（调用系统外部导航）
 *  - 下半屏：双列键值对表格，全部字段来自真实 RID 解包（未解析到显示"—"）
 */
@Composable
fun DroneDetailScreen(
    drone: Drone,
    onBack: () -> Unit,
    onEnterFullscreen: () -> Unit,
    onOpenLayerSettings: () -> Unit,
) {
    val colors = LocalDroneColors.current
    val context = LocalContext.current
    val layerMenu = rememberLayerMenu(context, onOpenLayerSettings)
    val overlay = rememberMapOverlayState()

    // 返回键：先关闭地图浮层，再走页面返回
    BackHandler(enabled = overlay.open != MapOverlay.NONE) { overlay.close() }
    val pendingFeatureHint = stringResource(R.string.map_feature_pending)
    val operatorLabel = stringResource(R.string.operator_label)
    val locateDevice = stringResource(R.string.locate_to_device)
    val locateDrone = stringResource(R.string.locate_to_drone)
    val locateOperator = stringResource(R.string.locate_to_operator)

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        // ---- 导航栏 ----
        HeaderBar(title = drone.name, onBack = onBack)

        // ---- 位置追踪大图（≥ 1/2 屏幕） ----
        Box(
            Modifier
                .fillMaxWidth()
                .weight(0.55f),
        ) {
            DroneMap(
                drones = listOf(drone),
                satellite = AppPrefs.satelliteMap,
                airspaceLayers = AppPrefs.airspaceVisible,
                showOperator = true,
                showMyLocation = true,
                showLabels = true,
                modifier = Modifier.fillMaxSize(),
            )

            // 浮层打开时：全屏点击遮罩（位于地图之上、控件之下，点其它按钮可穿透切换菜单）
            if (overlay.open != MapOverlay.NONE) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) { detectTapGestures { overlay.close() } },
                )
            }

            // 地图悬浮控件（左上角）：定位按钮弹出菜单（设备/无人机/遥控站，图标按钮 + 动画）
            MapControls(
                layers = layerMenu,
                overlay = overlay,
                locateTargets = listOfNotNull(
                    LocateTarget(
                        icon = AppIcons.Phone,
                        contentDescription = locateDevice,
                        onClick = {
                            if (!MapBackendProvider.backend.locateMe(context)) {
                                Toast.makeText(context, pendingFeatureHint, Toast.LENGTH_SHORT).show()
                            }
                        },
                    ),
                    drone.droneLat?.let { lat ->
                        drone.droneLng?.let { lng ->
                            LocateTarget(
                                icon = AppIcons.Flight,
                                contentDescription = locateDrone,
                                onClick = { MapBackendProvider.backend.moveTo(lat, lng) },
                            )
                        }
                    },
                    drone.operatorLat?.let { lat ->
                        drone.operatorLng?.let { lng ->
                            LocateTarget(
                                icon = AppIcons.Gamepad,
                                contentDescription = locateOperator,
                                onClick = { MapBackendProvider.backend.moveTo(lat, lng) },
                            )
                        }
                    },
                ),
                onResetNorth = {
                    if (!MapBackendProvider.backend.resetNorth(context)) {
                        Toast.makeText(context, pendingFeatureHint, Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 12.dp, top = 12.dp),
            )

            // 全屏查看按钮（右上角）
            MapChromeLabelPill(
                icon = AppIcons.Fullscreen,
                label = stringResource(R.string.map_enter_fullscreen),
                onClick = onEnterFullscreen,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 12.dp, top = 12.dp),
            )

            // 导航按钮（地图底部）
            Row(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                NavigationButton(
                    label = stringResource(R.string.nav_to_drone),
                    enabled = drone.droneLat != null && drone.droneLng != null,
                    onClick = {
                        drone.droneLat?.let { lat ->
                            drone.droneLng?.let { lng ->
                                NavigationLauncher.navigateTo(context, lat, lng, drone.name)
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                )
                NavigationButton(
                    label = stringResource(R.string.nav_to_operator),
                    enabled = drone.operatorLat != null && drone.operatorLng != null,
                    onClick = {
                        drone.operatorLat?.let { lat ->
                            drone.operatorLng?.let { lng ->
                                NavigationLauncher.navigateTo(context, lat, lng, operatorLabel)
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        // ---- 键值对表格（真实 RID 字段） ----
        Column(
            Modifier
                .weight(0.45f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            val basicRows = listOf(
                stringResource(R.string.kv_serial) to (drone.snCode ?: "—"),
                stringResource(R.string.kv_id_type) to idTypeLabel(drone.idType),
                stringResource(R.string.kv_proto_version) to (drone.protocolVersion?.toString() ?: "—"),
                stringResource(R.string.kv_signal) to
                    (drone.rssiDbm?.let { "${it.coerceIn(-100, 0)} dBm" } ?: "—"),
                stringResource(R.string.kv_protocol) to drone.protocol,
                stringResource(R.string.kv_frequency) to drone.frequency,
                stringResource(R.string.kv_last_seen) to
                    stringResource(R.string.time_ago_format, drone.lastSeenSeconds),
            )
            val motionRows = listOf(
                stringResource(R.string.kv_speed_h) to speedValue(drone.speedMs),
                stringResource(R.string.kv_speed_v) to speedValue(drone.verticalSpeedMs),
                stringResource(R.string.kv_pressure_alt) to altitudeValue(drone.pressureAltitudeM),
                stringResource(R.string.kv_geodetic_alt) to altitudeValue(drone.geodeticAltitudeM),
                stringResource(R.string.kv_height_above) to altitudeValue(drone.heightAboveTakeoffM),
                stringResource(R.string.kv_direction) to
                    (drone.directionDeg?.let { "${formatNumber(it)}°" } ?: "—"),
                stringResource(R.string.kv_timestamp) to
                    (drone.ridTimestampSeconds?.let { String.format(Locale.US, "%.1f s", it) } ?: "—"),
                stringResource(R.string.kv_lat) to
                    (drone.droneLat?.let { String.format(Locale.US, "%.6f°", it) } ?: "—"),
                stringResource(R.string.kv_lng) to
                    (drone.droneLng?.let { String.format(Locale.US, "%.6f°", it) } ?: "—"),
                stringResource(R.string.kv_distance) to
                    (drone.distanceM?.let { "${formatNumber(it)} m" } ?: "—"),
            )
            val operatorRows = listOf(
                stringResource(R.string.kv_operator_id) to (drone.operatorId ?: "—"),
                // 与航空器经纬度一致：纬度、经度分成两行
                stringResource(R.string.kv_operator_lat) to
                    (drone.operatorLat?.let { String.format(Locale.US, "%.6f°", it) } ?: "—"),
                stringResource(R.string.kv_operator_lng) to
                    (drone.operatorLng?.let { String.format(Locale.US, "%.6f°", it) } ?: "—"),
            )
            val sections = listOf(
                stringResource(R.string.kv_basic_info) to basicRows,
                stringResource(R.string.kv_motion) to motionRows,
                stringResource(R.string.kv_operator) to operatorRows,
            )
            val clipboard = LocalClipboardManager.current
            fun copyToClipboard(text: String, toast: String) {
                clipboard.setText(AnnotatedString(text))
                Toast.makeText(context, toast, Toast.LENGTH_SHORT).show()
            }

            KeyValueSection(
                title = sections[0].first,
                action = {
                    CopyAllButton(
                        onClick = {
                            copyToClipboard(
                                formatSections(sections),
                                context.getString(R.string.kv_copy_all_done),
                            )
                        },
                    )
                },
            ) {
                basicRows.forEach { (label, value) -> KeyValueRow(label, value) }
            }
            KeyValueSection(title = sections[1].first) {
                motionRows.forEach { (label, value) -> KeyValueRow(label, value) }
            }
            KeyValueSection(title = sections[2].first) {
                operatorRows.forEach { (label, value) -> KeyValueRow(label, value) }
            }
        }
    }
}

/** 地图底部导航按钮（实心主色，禁用态灰） */
@Composable
private fun NavigationButton(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalDroneColors.current
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (enabled) colors.primary else colors.muted,
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = AppIcons.Navigation,
            contentDescription = null,
            tint = if (enabled) colors.primaryForeground else colors.mutedForeground,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = label,
            style = DroneTypography.captionMedium,
            color = if (enabled) colors.primaryForeground else colors.mutedForeground,
        )
    }
}

/** 键值对分组段落：标题行（可带右侧动作）+ 卡片内容 */
@Composable
private fun KeyValueSection(
    title: String,
    action: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = LocalDroneColors.current
    Column(Modifier.fillMaxWidth()) {
        Row(
            // 固定行高：三个分组标题高度一致，不受右侧按钮高度影响
            Modifier
                .fillMaxWidth()
                .height(36.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = DroneTypography.pageTitle.copy(fontWeight = FontWeight.SemiBold),
                color = colors.foreground,
                modifier = Modifier.weight(1f),
            )
            action?.invoke()
        }
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(colors.card)
                .padding(horizontal = 16.dp, vertical = 4.dp),
        ) {
            content()
        }
        Spacer(Modifier.height(20.dp))
    }
}

/** 「复制全部信息」按钮（与页面其它按钮同一套 M3 点按反馈） */
@Composable
private fun CopyAllButton(onClick: () -> Unit) {
    val colors = LocalDroneColors.current
    TextButton(
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
        modifier = Modifier.height(28.dp),
    ) {
        Text(
            text = stringResource(R.string.kv_copy_all),
            style = DroneTypography.label,
            color = colors.primary,
        )
    }
}

/**
 * 键值对行：标签（左）+ 值（右，等宽字体）。
 * **点击整行即复制值**（原生 ripple + Toast「xxx 复制成功」），不再依赖长按选择。
 */
@Composable
private fun KeyValueRow(label: String, value: String) {
    val colors = LocalDroneColors.current
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable {
                    clipboard.setText(AnnotatedString(value))
                    Toast.makeText(
                        context,
                        context.getString(R.string.kv_copied, label),
                        Toast.LENGTH_SHORT,
                    ).show()
                }
                .padding(horizontal = 6.dp, vertical = 10.dp),
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
        HorizontalDivider(thickness = 0.5.dp, color = colors.border)
    }
}

/** 全部字段文本（分组标题 + 每行「标签: 值」，组间空行） */
private fun formatSections(sections: List<Pair<String, List<Pair<String, String>>>>): String =
    sections.joinToString("\n\n") { (title, rows) ->
        buildString {
            append(title)
            rows.forEach { (label, value) ->
                append('\n')
                append(label)
                append(": ")
                append(value)
            }
        }
    }

/** ID 类型中文映射（ASTM F3411 Basic ID） */
private fun idTypeLabel(idType: Int?): String = when (idType) {
    1 -> "序列号"
    2 -> "CCA 注册号"
    3 -> "UTM 会话 ID"
    4 -> "特定会话 ID"
    5 -> "注册号"
    else -> "—"
}

private fun speedValue(v: Float?): String = v?.let { "${formatNumber(it)} m/s" } ?: "—"

private fun altitudeValue(v: Float?): String = v?.let { "${formatNumber(it)} m" } ?: "—"

/** 全屏地图查看页（详情页进入） */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun FullscreenMapScreen(
    drone: Drone,
    onBack: () -> Unit,
    onOpenLayerSettings: () -> Unit,
) {
    val colors = LocalDroneColors.current
    val context = LocalContext.current
    val layerMenu = rememberLayerMenu(context, onOpenLayerSettings)
    val overlay = rememberMapOverlayState()

    // 返回键：先关闭地图浮层，再走页面返回
    BackHandler(enabled = overlay.open != MapOverlay.NONE) { overlay.close() }
    val pendingFeatureHint = stringResource(R.string.map_feature_pending)
    val locateDevice = stringResource(R.string.locate_to_device)
    val locateDrone = stringResource(R.string.locate_to_drone)
    val locateOperator = stringResource(R.string.locate_to_operator)

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        androidx.compose.material3.CenterAlignedTopAppBar(
            title = {
                Text(
                    text = drone.name,
                    style = DroneTypography.pageTitle,
                    color = colors.foreground,
                )
            },
            navigationIcon = {
                androidx.compose.material3.IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.back),
                        tint = colors.primary,
                    )
                }
            },
            colors = androidx.compose.material3.TopAppBarDefaults.centerAlignedTopAppBarColors(
                containerColor = colors.card.copy(alpha = 0.85f),
            ),
        )
        Box(Modifier.fillMaxSize()) {
            DroneMap(
                drones = listOf(drone),
                satellite = AppPrefs.satelliteMap,
                airspaceLayers = AppPrefs.airspaceVisible,
                showOperator = true,
                showMyLocation = true,
                showLabels = true,
                modifier = Modifier.fillMaxSize(),
            )

            // 浮层打开时：全屏点击遮罩（位于地图之上、控件之下，点其它按钮可穿透切换菜单）
            if (overlay.open != MapOverlay.NONE) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) { detectTapGestures { overlay.close() } },
                )
            }

            MapControls(
                layers = layerMenu,
                overlay = overlay,
                locateTargets = listOfNotNull(
                    LocateTarget(
                        icon = AppIcons.Phone,
                        contentDescription = locateDevice,
                        onClick = {
                            if (!MapBackendProvider.backend.locateMe(context)) {
                                Toast.makeText(context, pendingFeatureHint, Toast.LENGTH_SHORT).show()
                            }
                        },
                    ),
                    drone.droneLat?.let { lat ->
                        drone.droneLng?.let { lng ->
                            LocateTarget(
                                icon = AppIcons.Flight,
                                contentDescription = locateDrone,
                                onClick = { MapBackendProvider.backend.moveTo(lat, lng) },
                            )
                        }
                    },
                    drone.operatorLat?.let { lat ->
                        drone.operatorLng?.let { lng ->
                            LocateTarget(
                                icon = AppIcons.Gamepad,
                                contentDescription = locateOperator,
                                onClick = { MapBackendProvider.backend.moveTo(lat, lng) },
                            )
                        }
                    },
                ),
                onResetNorth = {
                    if (!MapBackendProvider.backend.resetNorth(context)) {
                        Toast.makeText(context, pendingFeatureHint, Toast.LENGTH_SHORT).show()
                    }
                },
                // 全屏页：控件置于左上角，定位菜单在右侧展开（不超出屏幕）
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 16.dp, top = 16.dp),
            )
        }
    }
}
