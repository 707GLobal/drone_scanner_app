package com.global_707.drone_scanner.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.global_707.drone_scanner.BuildConfig
import com.global_707.drone_scanner.R
import com.global_707.drone_scanner.data.AppPrefs
import com.global_707.drone_scanner.data.ScannerController
import com.global_707.drone_scanner.ui.components.ScanningSpinner
import com.global_707.drone_scanner.ui.components.SectionCard
import com.global_707.drone_scanner.ui.theme.DroneTypography
import com.global_707.drone_scanner.ui.theme.LocalDroneColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

// ============ 搜索设置 ============

@Composable
fun SearchSettingsScreen(onBack: () -> Unit) {
    val colors = LocalDroneColors.current
    val context = LocalContext.current

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
                .padding(16.dp),
        ) {
            SettingGroup {
                SwitchSettingRow(
                    label = stringResource(R.string.enable_wifi_scan),
                    checked = AppPrefs.wifiScanEnabled,
                    onCheckedChange = { value ->
                        AppPrefs.saveWifiScanEnabled(value)
                        ScannerController.restart(context)
                    },
                )
                SwitchSettingRow(
                    label = stringResource(R.string.enable_bt_scan),
                    checked = AppPrefs.bluetoothScanEnabled,
                    onCheckedChange = { value ->
                        AppPrefs.saveBluetoothScanEnabled(value)
                        ScannerController.restart(context)
                    },
                    showDivider = false,
                )
            }
            Spacer(Modifier.height(16.dp))
            SettingGroup {
                SwitchSettingRow(
                    label = stringResource(R.string.demo_mode),
                    subtitle = stringResource(R.string.demo_mode_desc),
                    checked = AppPrefs.demoMode,
                    onCheckedChange = { value ->
                        AppPrefs.saveDemoMode(value)
                        ScannerController.refresh()
                    },
                    showDivider = false,
                )
            }
        }
    }
}

// ============ 地图设置 ============

@Composable
fun MapSettingsScreen(onBack: () -> Unit) {
    val colors = LocalDroneColors.current

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        HeaderBar(title = stringResource(R.string.map_settings), onBack = onBack)
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            SettingGroup {
                // 地图类型选择
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.map_type),
                        style = DroneTypography.body,
                        color = colors.foreground,
                        modifier = Modifier.weight(1f),
                    )
                    MapTypeChip(
                        label = stringResource(R.string.map_standard),
                        selected = !AppPrefs.satelliteMap,
                        onClick = { AppPrefs.saveSatelliteMap(false) },
                    )
                    Spacer(Modifier.width(8.dp))
                    MapTypeChip(
                        label = stringResource(R.string.map_satellite),
                        selected = AppPrefs.satelliteMap,
                        onClick = { AppPrefs.saveSatelliteMap(true) },
                    )
                }
                HorizontalDivider(
                    modifier = Modifier.padding(start = 16.dp),
                    thickness = 1.dp,
                    color = colors.border,
                )
                SwitchSettingRow(
                    label = stringResource(R.string.show_operator_markers),
                    checked = AppPrefs.showOperatorMarkers,
                    onCheckedChange = { AppPrefs.saveShowOperatorMarkers(it) },
                    showDivider = false,
                )
            }
        }
    }
}

/** 地图类型选择胶囊 */
@Composable
private fun MapTypeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = LocalDroneColors.current
    Text(
        text = label,
        style = DroneTypography.caption,
        color = if (selected) colors.primaryForeground else colors.foreground,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) colors.primary else colors.muted)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

// ============ 显示设置 ============

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
            SettingGroup {
                SwitchSettingRow(
                    label = stringResource(R.string.show_name_labels),
                    checked = AppPrefs.showNameLabels,
                    onCheckedChange = { AppPrefs.saveShowNameLabels(it) },
                    showDivider = false,
                )
            }
        }
    }
}

// ============ 关于我们 ============

@Composable
fun AboutScreen(onBack: () -> Unit) {
    val colors = LocalDroneColors.current
    val context = LocalContext.current
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
                Text(
                    text = stringResource(R.string.app_name),
                    style = DroneTypography.largeTitle,
                    color = colors.foreground,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = stringResource(R.string.app_version),
                    style = DroneTypography.caption,
                    color = colors.mutedForeground,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.about_desc),
                    style = DroneTypography.caption,
                    color = colors.mutedForeground,
                )
            }
            Spacer(Modifier.height(16.dp))
            SettingGroup {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse(githubUrl)),
                            )
                        }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.github_repo),
                        style = DroneTypography.body,
                        color = colors.primary,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        imageVector = Icons.Filled.ChevronRight,
                        contentDescription = null,
                        tint = colors.mutedForeground,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}

// ============ 检查更新 ============

@Composable
fun CheckUpdateScreen(onBack: () -> Unit) {
    val colors = LocalDroneColors.current
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(false) }
    var resultText by remember { mutableStateOf<String?>(null) }
    var isLatest by remember { mutableStateOf(false) }
    val checking = stringResource(R.string.checking_update)
    val latest = stringResource(R.string.update_latest)
    val failed = stringResource(R.string.update_failed)

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        HeaderBar(title = stringResource(R.string.check_update), onBack = onBack)
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            SectionCard {
                Text(
                    text = stringResource(R.string.current_version, BuildConfig.VERSION_NAME),
                    style = DroneTypography.body,
                    color = colors.foreground,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = when {
                        loading -> checking
                        resultText != null -> resultText!!
                        isLatest -> latest
                        else -> ""
                    },
                    style = DroneTypography.caption,
                    color = if (isLatest) colors.success else colors.mutedForeground,
                )
                Spacer(Modifier.height(16.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (loading) {
                        ScanningSpinner(size = 16.dp)
                    }
                    Text(
                        text = stringResource(R.string.check_update),
                        style = DroneTypography.captionMedium,
                        color = colors.primary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(colors.primary.copy(alpha = 0.12f))
                            .clickable {
                                if (!loading) {
                                    loading = true
                                    resultText = null
                                    scope.launch {
                                        val (text, latestResult) = withContext(Dispatchers.IO) { fetchLatestRelease() }
                                        resultText = text
                                        isLatest = latestResult
                                        loading = false
                                    }
                                }
                            }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                    )
                }
            }
        }
    }
}

/** 查询 GitHub Releases 最新版本；返回 (展示文案, 是否为最新) */
private fun fetchLatestRelease(): Pair<String, Boolean> {
    return try {
        val conn = URL("https://api.github.com/repos/707GLobal/drone_scanner_app/releases/latest")
            .openConnection() as HttpURLConnection
        conn.connectTimeout = 8000
        conn.readTimeout = 8000
        conn.requestMethod = "GET"
        conn.setRequestProperty("Accept", "application/vnd.github+json")
        if (conn.responseCode == 200) {
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val tag = JSONObject(body).optString("tag_name")
            val latestTag = tag.removePrefix("v")
            if (latestTag.isNotBlank() && latestTag != BuildConfig.VERSION_NAME) {
                "发现新版本 v$latestTag" to false
            } else {
                "当前 v${BuildConfig.VERSION_NAME} 已是最新版本" to true
            }
        } else {
            "检查更新失败（HTTP ${conn.responseCode}）" to false
        }
    } catch (_: Exception) {
        "检查更新失败，请检查网络" to false
    }
}

// ============ 通用设置组件 ============

/** 设置分组卡片 */
@Composable
private fun SettingGroup(
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    val colors = LocalDroneColors.current
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(colors.card),
        content = content,
    )
}

/** 开关设置行 */
@Composable
private fun SwitchSettingRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    subtitle: String? = null,
    showDivider: Boolean = true,
) {
    val colors = LocalDroneColors.current
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = DroneTypography.body,
                    color = colors.foreground,
                )
                if (subtitle != null) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        style = DroneTypography.label,
                        color = colors.mutedForeground,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
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
        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(start = 16.dp),
                thickness = 1.dp,
                color = colors.border,
            )
        }
    }
}
