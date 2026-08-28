package com.global_707.drone_scanner.ui.map

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.global_707.drone_scanner.R
import com.global_707.drone_scanner.ui.theme.AppIcons
import com.global_707.drone_scanner.ui.theme.LocalDroneColors

/** 定位菜单目标（展开菜单中的一个功能按钮） */
data class LocateTarget(
    val icon: ImageVector,
    val contentDescription: String,
    val onClick: () -> Unit,
)

/**
 * 地图悬浮控件组（所有包含地图的页面共用）：
 *  - 图层切换（标准 / 卫星）
 *  - 定位按钮：点击在右侧弹出 [locateTargets] 功能按钮（我的位置/无人机/遥控站），
 *    弹出菜单使用 Popup 独立窗口——不影响控件布局（上下按钮纹丝不动）、
 *    点击 Popup 外任意处自动关闭；展开时定位按钮变为 X
 *  - 重置北向
 *
 * 页面差异仅通过 [locateTargets] 传入；展开动画依次错开弹出。
 */
@Composable
fun MapControls(
    satellite: Boolean,
    locateTargets: List<LocateTarget>,
    onToggleSatellite: () -> Unit,
    onResetNorth: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var locateExpanded by remember { mutableStateOf(false) }
    val popupOffsetX = with(LocalDensity.current) { 52.dp.roundToPx() }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ControlButton(
            icon = AppIcons.Layers,
            contentDescription = if (satellite) {
                stringResource(R.string.map_standard)
            } else {
                stringResource(R.string.map_satellite)
            },
            onClick = onToggleSatellite,
        )

        // 定位按钮：展开时变为 X
        Box {
            ControlButton(
                icon = if (locateExpanded) AppIcons.Close else AppIcons.MyLocation,
                contentDescription = stringResource(R.string.map_locate_me),
                onClick = {
                    if (locateTargets.isNotEmpty()) {
                        locateExpanded = !locateExpanded
                    } else {
                        locateTargets.firstOrNull()?.onClick()
                    }
                },
            )
            // 展开菜单：Popup 独立窗口，点击外部自动关闭，不影响本控件布局
            if (locateExpanded) {
                Popup(
                    alignment = Alignment.CenterStart,
                    offset = IntOffset(popupOffsetX, 0),
                    onDismissRequest = { locateExpanded = false },
                    properties = PopupProperties(
                        focusable = true,
                        dismissOnClickOutside = true,
                        dismissOnBackPress = true,
                    ),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        locateTargets.forEachIndexed { index, target ->
                            AnimatedVisibility(
                                visible = true,
                                enter = slideInHorizontally(
                                    animationSpec = tween(250, delayMillis = index * 60),
                                ) { it / 2 } + fadeIn(tween(250, delayMillis = index * 60)),
                            ) {
                                ControlButton(
                                    icon = target.icon,
                                    contentDescription = target.contentDescription,
                                    onClick = {
                                        locateExpanded = false
                                        target.onClick()
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }

        ControlButton(
            icon = AppIcons.Explore,
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
