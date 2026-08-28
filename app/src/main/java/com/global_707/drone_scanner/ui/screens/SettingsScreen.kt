package com.global_707.drone_scanner.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
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
import com.global_707.drone_scanner.ui.theme.DroneTypography
import com.global_707.drone_scanner.ui.theme.LocalDroneColors

/**
 * 设置页面（重构后）：
 *  - 搜索设置（蓝牙 / Wi-Fi 扫描 + 能力状态 + 扫描优先级）
 *  - 显示设置（仅"阻止机器休眠"）
 *  - 关于我们（含演示模式开关、版本信息）
 *  - 地图设置已移除，改为地图页面悬浮控件
 *  - 每行前置 Material 图标
 */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onSearchSettings: () -> Unit = {},
    onDisplaySettings: () -> Unit = {},
    onAbout: () -> Unit = {},
) {
    val colors = LocalDroneColors.current

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        // ---- 导航栏 ----
        HeaderBar(title = stringResource(R.string.settings_title), onBack = onBack)

        // ---- 滚动内容 ----
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(top = 16.dp, bottom = 16.dp),
        ) {
            // 功能设置
            SettingGroup(Modifier.padding(horizontal = 16.dp)) {
                SettingRow(
                    icon = Icons.Filled.Search,
                    label = stringResource(R.string.search_settings),
                    onClick = onSearchSettings,
                )
                SettingRow(
                    icon = Icons.Filled.Settings,
                    label = stringResource(R.string.display_settings),
                    onClick = onDisplaySettings,
                    showDivider = false,
                )
            }

            // 关于我们
            SettingGroup(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp)) {
                SettingRow(
                    icon = Icons.Filled.Info,
                    label = stringResource(R.string.about_us),
                    onClick = onAbout,
                    showDivider = false,
                )
            }
        }
    }
}

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

/** 设置行：Material 图标 + 标签 + 右箭头 */
@Composable
private fun SettingRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    showDivider: Boolean = true,
) {
    val colors = LocalDroneColors.current
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 图标（浅色圆形底 + Material 图标）
            Box(
                Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(50))
                    .background(colors.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = colors.primary,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(14.dp))
            Text(
                text = label,
                style = DroneTypography.body,
                color = colors.foreground,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = Icons.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = colors.mutedForeground,
                modifier = Modifier.size(16.dp),
            )
        }
        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(start = 66.dp),
                thickness = 1.dp,
                color = colors.border,
            )
        }
    }
}
