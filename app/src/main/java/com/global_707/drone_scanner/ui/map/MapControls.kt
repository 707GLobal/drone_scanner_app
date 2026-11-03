package com.global_707.drone_scanner.ui.map

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
 *  - 三个控件同一尺寸/透明度/描边（见 [MapFloatingButton]）；
 *  - 两个弹出菜单同一套动画与定位（见 [MapPopupSurface]），互斥（打开一个自动收起另一个）；
 *  - 指北控件是红色指针指南针（[CompassButton]），随地图旋转实时指北，点击回正北。
 *
 * 页面差异只通过 [layers] 与 [locateTargets] 传入。
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
        // 图层：底图 + 叠加图层
        Box {
            MapFloatingButton(
                icon = AppIcons.Layers,
                contentDescription = stringResource(R.string.map_layers),
                active = layersExpanded || layers.rows.any { it.visible },
                onClick = {
                    locateExpanded = false
                    layersExpanded = !layersExpanded
                },
            )
            if (layersExpanded) {
                MapPopupSurface(
                    alignment = Alignment.TopStart,
                    offset = IntOffset(popupOffsetX, 0),
                    onDismiss = { layersExpanded = false },
                ) {
                    LayerMenuPanel(
                        model = layers,
                        onDismiss = { layersExpanded = false },
                    )
                }
            }
        }

        // 定位：展开一排功能按钮
        Box {
            MapFloatingButton(
                icon = if (locateExpanded) AppIcons.Close else AppIcons.MyLocation,
                contentDescription = stringResource(R.string.map_locate_me),
                active = locateExpanded,
                onClick = {
                    if (locateTargets.isNotEmpty()) {
                        layersExpanded = false
                        locateExpanded = !locateExpanded
                    }
                },
            )
            if (locateExpanded) {
                MapPopupSurface(
                    alignment = Alignment.CenterStart,
                    offset = IntOffset(popupOffsetX, 0),
                    onDismiss = { locateExpanded = false },
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        locateTargets.forEach { target ->
                            MapFloatingButton(
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

        // 指北：红色指针随地图旋转，点击回正北
        CompassButton(
            northAngle = MapCameraState.northAngle,
            contentDescription = stringResource(R.string.map_reset_north),
            onClick = onResetNorth,
        )
    }
}
