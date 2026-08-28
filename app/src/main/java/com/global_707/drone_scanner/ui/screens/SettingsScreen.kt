package com.global_707.drone_scanner.ui.screens

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
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.global_707.drone_scanner.R
import com.global_707.drone_scanner.ui.components.RadarGlyph
import com.global_707.drone_scanner.ui.components.SectionCard
import com.global_707.drone_scanner.ui.theme.DroneTypography
import com.global_707.drone_scanner.ui.theme.LocalDroneColors

/**
 * 页面三：设置（Settings，参考设计文档第 6 节）
 */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onSearchSettings: () -> Unit = {},
    onMapSettings: () -> Unit = {},
    onDisplaySettings: () -> Unit = {},
    onAbout: () -> Unit = {},
    onCheckUpdate: () -> Unit = {},
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
            // App 信息卡片
            AppInfoCard(Modifier.padding(horizontal = 16.dp))

            // 第一组：检测设置
            SettingGroup(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp)) {
                SettingRow(label = stringResource(R.string.search_settings), onClick = onSearchSettings)
                SettingRow(label = stringResource(R.string.map_settings), onClick = onMapSettings)
                SettingRow(label = stringResource(R.string.display_settings), onClick = onDisplaySettings, showDivider = false)
            }

            // 第二组：关于
            SettingGroup(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp)) {
                SettingRow(label = stringResource(R.string.about_us), onClick = onAbout)
                SettingRow(
                    label = stringResource(R.string.check_update),
                    trailingText = stringResource(R.string.latest_version),
                    trailingColor = colors.success,
                    onClick = onCheckUpdate,
                    showDivider = false,
                )
            }
        }
    }
}

/** App 信息卡片（参考设计文档第 6.3 节） */
@Composable
private fun AppInfoCard(modifier: Modifier = Modifier) {
    val colors = LocalDroneColors.current
    SectionCard(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            // 左侧图标：60dp 圆角方块 + 雷达图标
            Box(
                Modifier
                    .size(60.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(colors.primary),
                contentAlignment = Alignment.Center,
            ) {
                RadarGlyph(
                    color = colors.primaryForeground,
                    modifier = Modifier.size(32.dp),
                )
            }
            // 右侧文字
            Column {
                Text(
                    text = stringResource(R.string.app_name),
                    style = DroneTypography.pageTitle.copy(fontWeight = FontWeight.Bold),
                    color = colors.foreground,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = stringResource(R.string.app_version),
                    style = DroneTypography.caption,
                    color = colors.mutedForeground,
                )
            }
        }
    }
}

/** 设置分组卡片（参考设计文档第 6.4 节） */
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

/** 设置行（参考设计文档第 6.5 节） */
@Composable
private fun SettingRow(
    label: String,
    onClick: () -> Unit,
    trailingText: String? = null,
    trailingColor: Color? = null,
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
            Text(
                text = label,
                style = DroneTypography.body,
                color = colors.foreground,
                modifier = Modifier.weight(1f),
            )
            if (trailingText != null) {
                Text(
                    text = trailingText,
                    style = DroneTypography.caption,
                    color = trailingColor ?: colors.mutedForeground,
                )
                Spacer(Modifier.width(8.dp))
            }
            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = colors.mutedForeground,
                modifier = Modifier.size(16.dp),
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

@androidx.compose.ui.tooling.preview.Preview(showBackground = true, widthDp = 375, heightDp = 812)
@Composable
private fun SettingsScreenPreview() {
    com.global_707.drone_scanner.ui.theme.DroneScannerTheme {
        SettingsScreen(onBack = {})
    }
}
