package com.global_707.drone_scanner.ui.map

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.global_707.drone_scanner.R
import com.global_707.drone_scanner.ui.theme.AppIcons

/** 定位菜单目标（展开菜单中的一个功能按钮） */
data class LocateTarget(
    val icon: ImageVector,
    val contentDescription: String,
    val onClick: () -> Unit,
)

/**
 * 地图悬浮控件组（所有包含地图的页面共用）。
 *
 * 设计规范（统一后，改这里等于改全部地图页）：
 *  - 三颗按钮同一尺寸/透明度/描边/按压动画（[MapChromeButton] / [MapChromeButtonContainer]）；
 *  - **激活描边只表示"它自己的菜单打开"**；"有图层开启"用右上角状态点表达（不占用描边）；
 *  - 所有派生菜单走 [MapPopupSurface]，并由 [MapOverlayState] 保证**同一时间只有一个**；
 *  - 指北是红色指针指南针（[CompassButton]），随地图旋转实时指北，点击回正北。
 */
@Composable
fun MapControls(
    layers: LayerMenuModel,
    locateTargets: List<LocateTarget>,
    overlay: MapOverlayState,
    onResetNorth: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val popupOffsetX = with(LocalDensity.current) { 52.dp.roundToPx() }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // 图层：底图 + 叠加图层
        Box {
            MapChromeButton(
                icon = AppIcons.Layers,
                contentDescription = stringResource(R.string.map_layers),
                active = overlay.isOpen(MapOverlay.LAYERS),
                onClick = { overlay.toggle(MapOverlay.LAYERS) },
            )
            if (overlay.isOpen(MapOverlay.LAYERS)) {
                MapPopupSurface(
                    // 恢复原排布：菜单顶边 = 按钮顶边，左边缘 = 按钮右边缘 + 8dp
                    alignment = Alignment.TopStart,
                    offset = IntOffset(popupOffsetX, 0),
                ) {
                    LayerMenuPanel(
                        model = layers,
                        onDismiss = overlay::close,
                    )
                }
            }
        }

        // 定位：展开一排功能按钮
        Box {
            MapChromeButton(
                icon = if (overlay.isOpen(MapOverlay.LOCATE)) AppIcons.Close else AppIcons.MyLocation,
                contentDescription = stringResource(R.string.map_locate_me),
                active = overlay.isOpen(MapOverlay.LOCATE),
                onClick = {
                    if (locateTargets.isNotEmpty()) overlay.toggle(MapOverlay.LOCATE)
                },
            )
            if (overlay.isOpen(MapOverlay.LOCATE)) {
                MapPopupSurface(
                    alignment = Alignment.TopStart,
                    offset = IntOffset(popupOffsetX, 0),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        locateTargets.forEach { target ->
                            MapChromeButton(
                                icon = target.icon,
                                contentDescription = target.contentDescription,
                                onClick = {
                                    overlay.close()
                                    target.onClick()
                                },
                            )
                        }
                    }
                }
            }
        }

        // 指北：红色指针随地图旋转，点击回正北
        CompassButton(
            northAngle = MapCameraState.northAngle,
            contentDescription = stringResource(R.string.map_reset_north),
            onClick = onResetNorth,
        )
    }
}
