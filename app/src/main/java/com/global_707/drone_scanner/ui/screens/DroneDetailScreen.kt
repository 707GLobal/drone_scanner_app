package com.global_707.drone_scanner.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Navigation
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.global_707.drone_scanner.R
import com.global_707.drone_scanner.data.AppPrefs
import com.global_707.drone_scanner.data.Drone
import com.global_707.drone_scanner.data.ScannerController
import com.global_707.drone_scanner.ui.components.PulseDot
import com.global_707.drone_scanner.ui.map.DroneMap
import com.global_707.drone_scanner.ui.map.MapBackendProvider
import com.global_707.drone_scanner.ui.map.MapControls
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
) {
    val colors = LocalDroneColors.current
    val context = LocalContext.current
    val pendingFeatureHint = stringResource(R.string.map_feature_pending)
    val operatorLabel = stringResource(R.string.operator_label)

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
                showOperator = true,
                showMyLocation = true,
                showLabels = true,
                modifier = Modifier.fillMaxSize(),
            )

            // 地图悬浮控件（左上角）
            MapControls(
                satellite = AppPrefs.satelliteMap,
                onToggleSatellite = { AppPrefs.saveSatelliteMap(!AppPrefs.satelliteMap) },
                onLocateMe = {
                    if (!MapBackendProvider.backend.locateMe(context)) {
                        Toast.makeText(context, pendingFeatureHint, Toast.LENGTH_SHORT).show()
                    }
                },
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
            MapOverlayButton(
                icon = Icons.Filled.Fullscreen,
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
            KeyValueSection(title = stringResource(R.string.kv_basic_info)) {
                KeyValueRow(stringResource(R.string.kv_serial), drone.snCode ?: "—")
                KeyValueRow(stringResource(R.string.kv_id_type), idTypeLabel(drone.idType))
                KeyValueRow(stringResource(R.string.kv_proto_version), drone.protocolVersion?.toString() ?: "—")
                KeyValueRow(stringResource(R.string.kv_signal), signalLabel(drone.signalStrength))
                KeyValueRow(stringResource(R.string.kv_protocol), drone.protocol)
                KeyValueRow(stringResource(R.string.kv_frequency), drone.frequency)
                KeyValueRow(
                    stringResource(R.string.kv_last_seen),
                    stringResource(R.string.time_ago_format, drone.lastSeenSeconds),
                )
            }
            KeyValueSection(title = stringResource(R.string.kv_motion)) {
                KeyValueRow(stringResource(R.string.kv_speed_h), speedValue(drone.speedMs))
                KeyValueRow(stringResource(R.string.kv_speed_v), speedValue(drone.verticalSpeedMs))
                KeyValueRow(stringResource(R.string.kv_pressure_alt), altitudeValue(drone.pressureAltitudeM))
                KeyValueRow(stringResource(R.string.kv_geodetic_alt), altitudeValue(drone.geodeticAltitudeM))
                KeyValueRow(stringResource(R.string.kv_height_above), altitudeValue(drone.heightAboveTakeoffM))
                KeyValueRow(stringResource(R.string.kv_direction), drone.directionDeg?.let { "${formatNumber(it)}°" } ?: "—")
                KeyValueRow(
                    stringResource(R.string.kv_timestamp),
                    drone.ridTimestampSeconds?.let { String.format(Locale.US, "%.1f s", it) } ?: "—",
                )
                KeyValueRow(stringResource(R.string.kv_lat), drone.droneLat?.let { String.format(Locale.US, "%.6f°", it) } ?: "—")
                KeyValueRow(stringResource(R.string.kv_lng), drone.droneLng?.let { String.format(Locale.US, "%.6f°", it) } ?: "—")
                KeyValueRow(stringResource(R.string.kv_distance), drone.distanceM?.let { "${formatNumber(it)} m" } ?: "—")
            }
            KeyValueSection(title = stringResource(R.string.kv_operator)) {
                KeyValueRow(stringResource(R.string.kv_operator_id), drone.operatorId ?: "—")
                val operatorPos = if (drone.operatorLat != null && drone.operatorLng != null) {
                    String.format(Locale.US, "%.6f°, %.6f°", drone.operatorLat, drone.operatorLng)
                } else {
                    "—"
                }
                KeyValueRow(stringResource(R.string.kv_operator_pos), operatorPos)
            }
        }
    }
}

/** 地图上方小型覆盖按钮 */
@Composable
private fun MapOverlayButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalDroneColors.current
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(colors.card.copy(alpha = 0.9f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = colors.primary,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = label,
            style = DroneTypography.captionMedium,
            color = colors.foreground,
        )
    }
}

/** 底部导航按钮 */
@Composable
private fun NavigationButton(label: String, enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
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
            imageVector = Icons.Filled.Navigation,
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

/** 键值对分组段落 */
@Composable
private fun KeyValueSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    val colors = LocalDroneColors.current
    Column(Modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = DroneTypography.pageTitle.copy(fontWeight = FontWeight.SemiBold),
            color = colors.foreground,
            modifier = Modifier.padding(vertical = 10.dp),
        )
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(colors.card)
                .padding(horizontal = 16.dp, vertical = 4.dp),
        ) {
            content()
        }
        Spacer(Modifier.height(16.dp))
    }
}

/** 键值对行：标签（左） + 值（右，等宽字体） */
@Composable
private fun KeyValueRow(label: String, value: String) {
    val colors = LocalDroneColors.current
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
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

/** ID 类型中文映射（ASTM F3411 Basic ID） */
private fun idTypeLabel(idType: Int?): String = when (idType) {
    1 -> "序列号"
    2 -> "CCA 注册号"
    3 -> "UTM 会话 ID"
    4 -> "特定会话 ID"
    5 -> "注册号"
    else -> "—"
}

/** 信号强度 0..4 → 文字 */
private fun signalLabel(level: Int): String = when {
    level >= 4 -> "强"
    level >= 3 -> "中"
    level >= 1 -> "弱"
    else -> "无信号"
}

private fun speedValue(v: Float?): String = v?.let { "${formatNumber(it)} m/s" } ?: "—"

private fun altitudeValue(v: Float?): String = v?.let { "${formatNumber(it)} m" } ?: "—"

/** 全屏地图查看页（详情页进入） */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun FullscreenMapScreen(
    drone: Drone,
    onBack: () -> Unit,
) {
    val colors = LocalDroneColors.current
    val context = LocalContext.current
    val pendingFeatureHint = stringResource(R.string.map_feature_pending)

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
                showOperator = true,
                showMyLocation = true,
                showLabels = true,
                modifier = Modifier.fillMaxSize(),
            )
            MapControls(
                satellite = AppPrefs.satelliteMap,
                onToggleSatellite = { AppPrefs.saveSatelliteMap(!AppPrefs.satelliteMap) },
                onLocateMe = {
                    if (!MapBackendProvider.backend.locateMe(context)) {
                        Toast.makeText(context, pendingFeatureHint, Toast.LENGTH_SHORT).show()
                    }
                },
                onResetNorth = {
                    if (!MapBackendProvider.backend.resetNorth(context)) {
                        Toast.makeText(context, pendingFeatureHint, Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 16.dp, top = 16.dp),
            )
        }
    }
}
