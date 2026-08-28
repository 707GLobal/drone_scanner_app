package com.global_707.drone_scanner.ui.map

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.global_707.drone_scanner.R
import com.global_707.drone_scanner.ui.theme.LocalDroneColors

/**
 * 地图悬浮控件组（FAB 风格小组件）：
 *  - 图层切换（标准 / 卫星）
 *  - 定位到我的位置
 *  - 重置北向
 *
 * 置顶悬浮显示在所有包含地图的页面（首页 & 飞机详情页）的右上角/左上角。
 * 占位地图阶段：图层切换立即生效；定位/北向操作在真实地图 SDK 接入后生效
 *（当前点击给出提示，见 [onFeatureHint]）。
 */
@Composable
fun MapControls(
    satellite: Boolean,
    onToggleSatellite: () -> Unit,
    onLocateMe: () -> Unit,
    onResetNorth: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalDroneColors.current
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ControlButton(
            icon = Icons.Filled.Layers,
            contentDescription = if (satellite) {
                stringResource(R.string.map_standard)
            } else {
                stringResource(R.string.map_satellite)
            },
            onClick = onToggleSatellite,
        )
        ControlButton(
            icon = Icons.Filled.MyLocation,
            contentDescription = stringResource(R.string.map_locate_me),
            onClick = onLocateMe,
        )
        ControlButton(
            icon = Icons.Filled.Explore,
            contentDescription = stringResource(R.string.map_reset_north),
            onClick = onResetNorth,
        )
    }
}

/** 圆形悬浮小按钮（MaterialIcon + 系统 Ripple） */
@Composable
private fun ControlButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    val colors = LocalDroneColors.current
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(colors.card.copy(alpha = 0.9f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = colors.primary,
            modifier = Modifier.size(20.dp),
        )
    }
}
