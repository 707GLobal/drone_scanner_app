package com.global_707.drone_scanner.ui.screens

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.global_707.drone_scanner.R
import com.global_707.drone_scanner.data.AppPrefs
import com.global_707.drone_scanner.data.DeviceCapabilities
import com.global_707.drone_scanner.data.ScanPriority
import com.global_707.drone_scanner.data.ScanMode
import com.global_707.drone_scanner.data.ScannerController
import com.global_707.drone_scanner.ui.components.RadarGlyph
import com.global_707.drone_scanner.ui.components.SectionCard
import com.global_707.drone_scanner.ui.theme.AppIcons
import com.global_707.drone_scanner.ui.theme.DroneTypography
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.global_707.drone_scanner.data.airspace.AirspaceLayers
import com.global_707.drone_scanner.data.airspace.AirspaceRepository
import com.global_707.drone_scanner.data.airspace.AirspaceSourceKind
import com.global_707.drone_scanner.data.airspace.AirspaceState
import com.global_707.drone_scanner.ui.theme.LocalDroneColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

// ============ 搜索设置（蓝牙 / Wi-Fi 分组 + 实时能力状态 + 扫描优先级） ============

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchSettingsScreen(onBack: () -> Unit) {
    val colors = LocalDroneColors.current
    val context = LocalContext.current

    // 设备能力实时读取：每 2 秒重新查询系统 API（蓝牙/Wi-Fi 开关变化后自动更新）
    val bt4Supported by produceState(initialValue = DeviceCapabilities.bluetooth4Legacy(context)) {
        while (isActive) {
            value = DeviceCapabilities.bluetooth4Legacy(context)
            delay(2000)
        }
    }
    val bt5Supported by produceState(initialValue = DeviceCapabilities.bluetooth5Extended(context)) {
        while (isActive) {
            value = DeviceCapabilities.bluetooth5Extended(context)
            delay(2000)
        }
    }
    // Wi-Fi 通道的实际门槛：API 30+ 且有 Wi-Fi 硬件（信标厂商 IE 才可读）
    val wifiBeaconSupported by produceState(
        initialValue = DeviceCapabilities.wifiBeaconRidSupported(context),
    ) {
        while (isActive) {
            value = DeviceCapabilities.wifiBeaconRidSupported(context)
            delay(2000)
        }
    }

    // 本机不支持的能力：强制关闭对应扫描通道并持久化（开关置灰、不可开启，原因见能力行）
    LaunchedEffect(bt4Supported) {
        if (!bt4Supported && AppPrefs.bluetoothScanEnabled) {
            AppPrefs.saveBluetoothScanEnabled(false)
        }
    }
    LaunchedEffect(wifiBeaconSupported) {
        if (!wifiBeaconSupported && AppPrefs.wifiScanEnabled) {
            AppPrefs.saveWifiScanEnabled(false)
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        HeaderBar(title = stringResource(R.string.search_settings), onBack = onBack)
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 16.dp),
        ) {
            // ---- 扫描模式：顶部滑块（手机直连 / 外置天线）----
            GroupTitle(title = stringResource(R.string.scan_source_title))
            Spacer(Modifier.height(12.dp))
            SectionCard {
                ScanSourceSelector()
                Spacer(Modifier.height(10.dp))
                Text(
                    text = stringResource(
                        if (AppPrefs.scanMode == ScanMode.EXTERNAL) {
                            R.string.scan_source_external_desc
                        } else {
                            R.string.scan_source_internal_desc
                        },
                    ),
                    style = DroneTypography.label,
                    color = colors.mutedForeground,
                )
            }

            Spacer(Modifier.height(24.dp))

            // ---- 手机直连：本机射频相关设置（M3 原生过渡动画切换显隐）----
            AnimatedVisibility(
                visible = AppPrefs.scanMode == ScanMode.INTERNAL,
                enter = fadeIn(tween(220)) + expandVertically(tween(220)),
                exit = fadeOut(tween(140)) + shrinkVertically(tween(180)),
            ) {
                Column {
                    GroupTitle(title = stringResource(R.string.scan_group_bluetooth))
                    Spacer(Modifier.height(12.dp))
                    SectionCard {
                        FeatureRow(
                            icon = AppIcons.Bluetooth,
                            label = stringResource(R.string.scan_bluetooth),
                            checked = AppPrefs.bluetoothScanEnabled && bt4Supported,
                            onCheckedChange = { value ->
                                AppPrefs.saveBluetoothScanEnabled(value)
                                ScannerController.restart(context)
                            },
                            // 本机不支持时置灰不可开启（偏好值已被上面的 LaunchedEffect 强制关闭）
                            enabled = bt4Supported,
                        )
                        Spacer(Modifier.height(14.dp))
                        CapabilityStatusRow(
                            supported = bt4Supported,
                            title = stringResource(
                                if (bt4Supported) R.string.cap_bt4_title else R.string.cap_bt4_title_no,
                            ),
                            description = stringResource(
                                if (bt4Supported) R.string.cap_bt4_desc else R.string.cap_bt4_reason,
                            ),
                        )
                        Spacer(Modifier.height(14.dp))
                        CapabilityStatusRow(
                            supported = bt5Supported,
                            title = stringResource(
                                if (bt5Supported) R.string.cap_bt5_title else R.string.cap_bt5_title_no,
                            ),
                            description = stringResource(
                                if (bt5Supported) R.string.cap_bt5_desc else R.string.cap_bt5_reason,
                            ),
                        )
                    }

                    Spacer(Modifier.height(16.dp))

                    // 扫描优先级（M3 原生下拉，修改后立即重启扫描）
                    SectionCard {
                        ScanPrioritySelector()
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = stringResource(
                                R.string.scan_mode_current,
                                stringResource(
                                    when (AppPrefs.scanPriority) {
                                        ScanPriority.LOW -> R.string.scan_mode_low_power
                                        ScanPriority.NORMAL -> R.string.scan_mode_balanced
                                        else -> R.string.scan_mode_low_latency
                                    },
                                ),
                            ),
                            style = DroneTypography.label,
                            color = colors.mutedForeground,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = stringResource(R.string.scan_priority_desc),
                            style = DroneTypography.caption,
                            color = colors.mutedForeground,
                        )
                    }

                    Spacer(Modifier.height(24.dp))

                    GroupTitle(title = stringResource(R.string.scan_group_wifi))
                    Spacer(Modifier.height(12.dp))
                    SectionCard {
                        FeatureRow(
                            icon = AppIcons.Wifi,
                            label = stringResource(R.string.scan_wifi),
                            checked = AppPrefs.wifiScanEnabled && wifiBeaconSupported,
                            onCheckedChange = { value ->
                                AppPrefs.saveWifiScanEnabled(value)
                                ScannerController.restart(context)
                            },
                            enabled = wifiBeaconSupported,
                        )
                        Spacer(Modifier.height(14.dp))
                        CapabilityStatusRow(
                            supported = wifiBeaconSupported,
                            title = stringResource(
                                if (wifiBeaconSupported) {
                                    R.string.cap_wifi_title
                                } else {
                                    R.string.cap_wifi_title_no
                                },
                            ),
                            description = stringResource(
                                if (wifiBeaconSupported) R.string.cap_wifi_desc else R.string.cap_wifi_reason,
                            ),
                        )
                    }
                }
            }

            // ---- 外置天线：相关选项内容预留（后续接入 USB / 蓝牙链路时替换为真实控件）----
            AnimatedVisibility(
                visible = AppPrefs.scanMode == ScanMode.EXTERNAL,
                enter = fadeIn(tween(220)) + expandVertically(tween(220)),
                exit = fadeOut(tween(140)) + shrinkVertically(tween(180)),
            ) {
                Column {
                    GroupTitle(title = stringResource(R.string.scan_group_external))
                    Spacer(Modifier.height(12.dp))
                    SectionCard {
                        PlaceholderRow(
                            label = stringResource(R.string.external_option_connect),
                            value = stringResource(R.string.external_option_connect_value),
                        )
                        Spacer(Modifier.height(14.dp))
                        PlaceholderRow(
                            label = stringResource(R.string.external_option_status),
                            value = stringResource(R.string.scan_external_not_connected),
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = stringResource(R.string.scan_external_hint),
                            style = DroneTypography.label,
                            color = colors.mutedForeground,
                        )
                    }
                }
            }
        }
    }
}

/** 分组标题（参考原型图：标题 + 贯穿下划线）；日志设置页复用 */
@Composable
internal fun GroupTitle(title: String) {
    val colors = LocalDroneColors.current
    Column {
        Text(
            text = title,
            style = DroneTypography.pageTitle.copy(fontWeight = FontWeight.SemiBold),
            color = colors.foreground,
        )
        Spacer(Modifier.height(4.dp))
        HorizontalDivider(thickness = 2.dp, color = colors.primary.copy(alpha = 0.35f))
    }
}

/** 特性行：图标方块 + 名称 + 开关（[enabled] 为 false 时仅置灰，偏好值仍保留） */
@Composable
private fun FeatureRow(
    icon: ImageVector,
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    val colors = LocalDroneColors.current
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(colors.primary.copy(alpha = if (enabled) 0.12f else 0.06f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (enabled) colors.primary else colors.mutedForeground,
                modifier = Modifier.size(26.dp),
            )
        }
        Spacer(Modifier.width(14.dp))
        Text(
            text = label,
            style = DroneTypography.body,
            color = if (enabled) colors.foreground else colors.mutedForeground,
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedTrackColor = colors.primary,
                uncheckedTrackColor = colors.muted,
                uncheckedThumbColor = colors.card,
            ),
        )
    }
}

/**
 * 扫描模式选择：M3 原生分段控件（手机直扫 / 连接外置天线）。
 * 切换后立即重启扫描；因扫描只在首页运行，在设置页内该调用为空操作，
 * 返回首页时按新模式启动。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScanSourceSelector() {
    val colors = LocalDroneColors.current
    val context = LocalContext.current
    val options = listOf(
        ScanMode.INTERNAL to stringResource(R.string.scan_source_internal),
        ScanMode.EXTERNAL to stringResource(R.string.scan_source_external),
    )

    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, (mode, label) ->
            SegmentedButton(
                selected = AppPrefs.scanMode == mode,
                onClick = {
                    AppPrefs.saveScanMode(mode)
                    ScannerController.restart(context)
                },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = colors.primary,
                    activeContentColor = colors.primaryForeground,
                    inactiveContainerColor = colors.card,
                    inactiveContentColor = colors.foreground,
                    activeBorderColor = colors.primary,
                    inactiveBorderColor = colors.border,
                ),
            ) {
                Text(text = label, style = DroneTypography.label)
            }
        }
    }
}

/**
 * 预留项行：左标签 + 右取值。
 * 外置天线相关选项尚未接入，先用只读占位，避免出现"点了没反应"的控件。
 */
@Composable
private fun PlaceholderRow(label: String, value: String) {
    val colors = LocalDroneColors.current
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
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

/** 能力状态行：✓ / ✗ 圆形图标（样式统一）+ 标题 + 说明（实时检测结果） */
@Composable
private fun CapabilityStatusRow(supported: Boolean, title: String, description: String) {
    val colors = LocalDroneColors.current
    Row {
        // 统一样式：浅色圆底 + 同系列实心圆形图标（CheckCircle / Cancel）
        Box(
            Modifier
                .size(24.dp)
                .clip(RoundedCornerShape(50))
                .background(
                    (if (supported) colors.success else colors.danger).copy(alpha = 0.15f),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (supported) Icons.Filled.CheckCircle else AppIcons.Cancel,
                contentDescription = null,
                tint = if (supported) colors.success else colors.danger,
                modifier = Modifier.size(16.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = DroneTypography.body,
                color = colors.foreground,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = description,
                style = DroneTypography.label,
                color = colors.mutedForeground,
            )
        }
    }
}

/** 扫描优先级：M3 原生 ExposedDropdownMenuBox（选项菜单跟随右侧控件弹出） */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScanPrioritySelector() {
    val colors = LocalDroneColors.current
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }
    val priorityLabel = when (AppPrefs.scanPriority) {
        ScanPriority.LOW -> stringResource(R.string.scan_priority_low)
        ScanPriority.NORMAL -> stringResource(R.string.scan_priority_normal)
        else -> stringResource(R.string.scan_priority_high)
    }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        OutlinedTextField(
            value = priorityLabel,
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.scan_priority)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                focusedBorderColor = colors.primary,
                unfocusedBorderColor = colors.border,
                focusedLabelColor = colors.primary,
                cursorColor = colors.primary,
            ),
            modifier = Modifier
                .menuAnchor(androidx.compose.material3.MenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            listOf(
                ScanPriority.LOW to stringResource(R.string.scan_priority_low),
                ScanPriority.NORMAL to stringResource(R.string.scan_priority_normal),
                ScanPriority.HIGH to stringResource(R.string.scan_priority_high),
            ).forEach { (value, label) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        AppPrefs.saveScanPriority(value)
                        ScannerController.restart(context)
                        expanded = false
                    },
                )
            }
        }
    }
}

// ============ 显示设置（仅保留 Keep Screen On） ============

@Composable
fun DisplaySettingsScreen(onBack: () -> Unit) {
    val colors = LocalDroneColors.current

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        HeaderBar(title = stringResource(R.string.display_settings), onBack = onBack)
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            SectionCard {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.keep_screen_on),
                        style = DroneTypography.body,
                        color = colors.foreground,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(
                        checked = AppPrefs.keepScreenOn,
                        onCheckedChange = { AppPrefs.saveKeepScreenOn(it) },
                        colors = SwitchDefaults.colors(
                            checkedTrackColor = colors.primary,
                            uncheckedTrackColor = colors.muted,
                            uncheckedThumbColor = colors.card,
                        ),
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            // ---- 适飞空域数据（本地离线包：导入 / 更换 / 移除） ----
            GroupTitle(title = stringResource(R.string.airspace_data_title))
            Spacer(Modifier.height(12.dp))
            AirspaceDataCard()
        }
    }
}

/**
 * 适飞空域离线数据管理卡片。
 *
 * 数据只保存在本机（`filesDir/airspace/`），导入后可在地图「图层」菜单里单独开关；
 * 这里也是"导入数据包"的第二入口（第一入口在图层菜单里）。
 */
@Composable
private fun AirspaceDataCard() {
    val colors = LocalDroneColors.current
    val context = LocalContext.current
    val appContext = context.applicationContext
    val scope = rememberCoroutineScope()
    var importing by remember { mutableStateOf(false) }
    var confirmRemove by remember { mutableStateOf(false) }

    val importer = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            importing = true
            val result = AirspaceRepository.importAndRefresh(appContext, uri)
            importing = false
            result.fold(
                onSuccess = { manifest ->
                    Toast.makeText(
                        appContext,
                        appContext.getString(R.string.airspace_import_success, manifest.version),
                        Toast.LENGTH_LONG,
                    ).show()
                },
                onFailure = { error ->
                    Toast.makeText(
                        appContext,
                        appContext.getString(
                            R.string.airspace_import_failed,
                            error.message ?: appContext.getString(R.string.airspace_import_failed_unknown),
                        ),
                        Toast.LENGTH_LONG,
                    ).show()
                },
            )
        }
    }

    LaunchedEffect(Unit) { AirspaceRepository.refresh(appContext) }

    val catalog = AirspaceState.catalogOf(AirspaceLayers.SUITABLE)
    val sourceLabel = when (catalog?.source) {
        AirspaceSourceKind.BUNDLED -> stringResource(R.string.airspace_source_bundled)
        AirspaceSourceKind.IMPORTED -> stringResource(R.string.airspace_source_imported)
        null -> null
    }

    SectionCard {
        Text(
            text = stringResource(R.string.airspace_data_desc),
            style = DroneTypography.label,
            color = colors.mutedForeground,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = when {
                importing -> stringResource(R.string.airspace_status_importing)
                catalog == null -> stringResource(R.string.airspace_data_none)
                else -> stringResource(
                    R.string.airspace_data_version,
                    catalog.version,
                    sourceLabel.orEmpty(),
                    catalog.zooms.first,
                    catalog.zooms.last,
                )
            },
            style = DroneTypography.captionMedium,
            color = if (catalog == null) colors.mutedForeground else colors.foreground,
        )
        catalog?.attribution?.let { attribution ->
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.airspace_data_attribution, attribution),
                style = DroneTypography.micro,
                color = colors.mutedForeground,
            )
        }

        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AirspaceDataButton(
                label = stringResource(
                    if (catalog == null) R.string.airspace_data_import else R.string.airspace_data_replace,
                ),
                onClick = { importer.launch(arrayOf("*/*")) },
            )
            if (catalog?.source == AirspaceSourceKind.IMPORTED) {
                AirspaceDataButton(
                    label = stringResource(R.string.airspace_data_remove),
                    danger = true,
                    onClick = { confirmRemove = true },
                )
            }
        }
    }

    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text(stringResource(R.string.airspace_data_remove_confirm_title)) },
            text = { Text(stringResource(R.string.airspace_data_remove_confirm_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmRemove = false
                        scope.launch {
                            AirspaceRepository.removeAndRefresh(appContext)
                            Toast.makeText(
                                appContext,
                                appContext.getString(R.string.airspace_data_remove_ok),
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    },
                ) {
                    Text(stringResource(R.string.airspace_data_remove))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmRemove = false }) {
                    Text(stringResource(R.string.airspace_data_remove_cancel))
                }
            },
        )
    }
}

/** 空域数据卡片的小按钮 */
@Composable
private fun AirspaceDataButton(label: String, danger: Boolean = false, onClick: () -> Unit) {
    val colors = LocalDroneColors.current
    Row(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (danger) {
                    colors.danger.copy(alpha = 0.12f)
                } else {
                    colors.primary.copy(alpha = 0.12f)
                },
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = DroneTypography.label,
            color = if (danger) colors.danger else colors.primary,
        )
    }
}

// ============ 关于我们（含演示模式开关 + 版本信息在页面底部） ============

@Composable
fun AboutScreen(onBack: () -> Unit) {
    val colors = LocalDroneColors.current
    val context: Context = LocalContext.current
    val githubUrl = "https://github.com/707GLobal/drone_scanner_app"

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        HeaderBar(title = stringResource(R.string.about_us), onBack = onBack)
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadarGlyph(
                        color = colors.primary,
                        modifier = Modifier.size(40.dp),
                    )
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(
                            text = stringResource(R.string.app_name),
                            style = DroneTypography.largeTitle,
                            color = colors.foreground,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = stringResource(R.string.about_static_note),
                            style = DroneTypography.caption,
                            color = colors.mutedForeground,
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    text = stringResource(R.string.about_desc),
                    style = DroneTypography.caption,
                    color = colors.mutedForeground,
                )
            }

            Spacer(Modifier.height(16.dp))

            // 演示模式（原设置入口移至此页）
            SectionCard {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.demo_mode),
                            style = DroneTypography.body,
                            color = colors.foreground,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = stringResource(R.string.demo_mode_desc),
                            style = DroneTypography.label,
                            color = colors.mutedForeground,
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Switch(
                        checked = AppPrefs.demoMode,
                        onCheckedChange = { value ->
                            AppPrefs.saveDemoMode(value)
                            ScannerController.refresh()
                        },
                        colors = SwitchDefaults.colors(
                            checkedTrackColor = colors.primary,
                            uncheckedTrackColor = colors.muted,
                            uncheckedThumbColor = colors.card,
                        ),
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            SectionCard {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable {
                            // 设备无浏览器时 startActivity 会抛 ActivityNotFoundException，吞掉避免崩溃
                            runCatching {
                                context.startActivity(
                                    android.content.Intent(
                                        android.content.Intent.ACTION_VIEW,
                                        android.net.Uri.parse(githubUrl),
                                    ),
                                )
                            }
                        }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.github_repo),
                        style = DroneTypography.body,
                        color = colors.primary,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        imageVector = Icons.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = colors.mutedForeground,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
            // 版本与署名信息：仅展示在关于我们页面底部
            Text(
                text = stringResource(R.string.app_version),
                style = DroneTypography.label,
                color = colors.mutedForeground,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            // 开发者署名：开发留名，与版本号一同置于页脚
            Text(
                text = stringResource(R.string.about_developer),
                style = DroneTypography.label,
                color = colors.mutedForeground,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Text(
                text = stringResource(R.string.about_copyright),
                style = DroneTypography.caption,
                color = colors.mutedForeground.copy(alpha = 0.6f),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}
