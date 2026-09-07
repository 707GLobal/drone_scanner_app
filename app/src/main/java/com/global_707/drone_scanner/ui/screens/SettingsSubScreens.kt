package com.global_707.drone_scanner.ui.screens

import android.content.Context
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
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
import com.global_707.drone_scanner.data.ScannerController
import com.global_707.drone_scanner.ui.components.RadarGlyph
import com.global_707.drone_scanner.ui.components.SectionCard
import com.global_707.drone_scanner.ui.theme.AppIcons
import com.global_707.drone_scanner.ui.theme.DroneTypography
import com.global_707.drone_scanner.ui.theme.LocalDroneColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

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
    val wifiNanSupported by produceState(initialValue = DeviceCapabilities.wifiNanSupported(context)) {
        while (isActive) {
            value = DeviceCapabilities.wifiNanSupported(context)
            delay(2000)
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
            // ---- 蓝牙扫描 ----
            GroupTitle(title = stringResource(R.string.scan_group_bluetooth))
            Spacer(Modifier.height(12.dp))
            SectionCard {
                FeatureRow(
                    icon = AppIcons.Bluetooth,
                    label = stringResource(R.string.scan_bluetooth),
                    checked = AppPrefs.bluetoothScanEnabled,
                    onCheckedChange = { value ->
                        AppPrefs.saveBluetoothScanEnabled(value)
                        ScannerController.restart(context)
                    },
                )
                Spacer(Modifier.height(14.dp))
                CapabilityStatusRow(
                    supported = bt4Supported,
                    title = stringResource(R.string.bt4_legacy_supported),
                    description = stringResource(R.string.bt4_legacy_desc),
                )
                Spacer(Modifier.height(14.dp))
                CapabilityStatusRow(
                    supported = bt5Supported,
                    title = stringResource(R.string.bt5_extended_supported),
                    description = stringResource(R.string.bt5_extended_desc),
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

            // ---- Wi-Fi 扫描 ----
            GroupTitle(title = stringResource(R.string.scan_group_wifi))
            Spacer(Modifier.height(12.dp))
            SectionCard {
                FeatureRow(
                    icon = AppIcons.Wifi,
                    label = stringResource(R.string.scan_wifi),
                    checked = AppPrefs.wifiScanEnabled,
                    onCheckedChange = { value ->
                        AppPrefs.saveWifiScanEnabled(value)
                        ScannerController.restart(context)
                    },
                )
                Spacer(Modifier.height(14.dp))
                CapabilityStatusRow(
                    supported = wifiNanSupported,
                    title = if (wifiNanSupported) {
                        stringResource(R.string.wifi_nan_supported)
                    } else {
                        stringResource(R.string.wifi_nan_not_supported)
                    },
                    description = stringResource(R.string.wifi_nan_desc),
                )
            }
        }
    }
}

/** 分组标题（参考原型图：标题 + 贯穿下划线） */
@Composable
private fun GroupTitle(title: String) {
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

/** 特性行：图标方块 + 名称 + 开关 */
@Composable
private fun FeatureRow(
    icon: ImageVector,
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
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
                .background(colors.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = colors.primary,
                modifier = Modifier.size(26.dp),
            )
        }
        Spacer(Modifier.width(14.dp))
        Text(
            text = label,
            style = DroneTypography.body,
            color = colors.foreground,
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedTrackColor = colors.primary,
                uncheckedTrackColor = colors.muted,
                uncheckedThumbColor = colors.card,
            ),
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
        }
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
