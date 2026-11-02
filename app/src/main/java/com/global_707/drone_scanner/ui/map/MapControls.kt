package com.global_707.drone_scanner.ui.map

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
 *  - 图层按钮：点击弹出图层菜单（底图单选 + 叠加图层开关 + 图例），
 *    菜单用 Popup 独立窗口——不影响控件布局，点击外部/返回键自动关闭
 *  - 定位按钮：点击在右侧弹出 [locateTargets] 功能按钮（我的位置/无人机/遥控站），
 *    展开时定位按钮变为 X
 *  - 重置北向
 *
 * 两个弹出菜单互斥：打开一个会自动收起另一个。
 * 页面差异仅通过 [locateTargets] 与 [layers] 传入。
 */
@Composable
fun MapControls(
    layers: LayerMenuModel,
    locateTargets: List<LocateTarget>,
    onResetNorth: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var layersExpanded by remember { mutableStateOf(false) }
    var locateExpanded by remember { mutableStateOf(false) }
    val popupOffsetX = with(LocalDensity.current) { 52.dp.roundToPx() }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // 图层：弹出菜单（底图 + 叠加图层）
        Box {
            ControlButton(
                icon = AppIcons.Layers,
                contentDescription = stringResource(R.string.map_layers),
                highlighted = layersExpanded || layers.rows.any { it.visible },
                onClick = {
                    locateExpanded = false
                    layersExpanded = !layersExpanded
                },
            )
            if (layersExpanded) {
                Popup(
                    alignment = Alignment.TopStart,
                    offset = IntOffset(popupOffsetX, 0),
                    onDismissRequest = { layersExpanded = false },
                    properties = PopupProperties(
                        focusable = true,
                        dismissOnClickOutside = true,
                        dismissOnBackPress = true,
                    ),
                ) {
                    LayerMenuPanel(
                        model = layers,
                        onDismiss = { layersExpanded = false },
                    )
                }
            }
        }

        // 定位按钮：展开时变为 X
        Box {
            ControlButton(
                icon = if (locateExpanded) AppIcons.Close else AppIcons.MyLocation,
                contentDescription = stringResource(R.string.map_locate_me),
                highlighted = locateExpanded,
                onClick = {
                    if (locateTargets.isNotEmpty()) {
                        layersExpanded = false
                        locateExpanded = !locateExpanded
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

/** 圆形悬浮小按钮（MaterialIcon + 系统 Ripple）；[highlighted] 时加一圈主色描边 */
@Composable
private fun ControlButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    highlighted: Boolean = false,
) {
    val colors = LocalDroneColors.current
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(colors.card.copy(alpha = 0.9f))
            .then(
                if (highlighted) {
                    Modifier.border(1.5.dp, colors.primary.copy(alpha = 0.8f), CircleShape)
                } else {
                    Modifier
                },
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (highlighted) colors.primary else colors.primary.copy(alpha = 0.9f),
            modifier = Modifier.size(20.dp),
        )
    }
}
