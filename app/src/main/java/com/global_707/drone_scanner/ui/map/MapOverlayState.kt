package com.global_707.drone_scanner.ui.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * 地图上的浮层（弹出的菜单/面板）。**同一时间只允许一个**。
 *
 * 之前图层菜单、定位菜单、扫描面板各自持有一个布尔状态，互不知晓：
 *  - 先开扫描面板再点图层钮 → 两个都出现、互相重叠；
 *  - 先开图层菜单再点扫描钮 → 点击被 Popup 的"点外部关闭"吃掉，只关不弹。
 *
 * 现在三个入口共用同一个状态，[toggle] 天然互斥；返回键优先级见各页面的 BackHandler。
 */
enum class MapOverlay { NONE, LAYERS, LOCATE, SCAN }

class MapOverlayState {

    var open: MapOverlay by mutableStateOf(MapOverlay.NONE)
        private set

    fun isOpen(target: MapOverlay): Boolean = open == target

    /** 点同一个按钮 = 收起；点另一个 = 切换（旧菜单自动关闭） */
    fun toggle(target: MapOverlay) {
        open = if (open == target) MapOverlay.NONE else target
    }

    fun close() {
        open = MapOverlay.NONE
    }
}

@Composable
fun rememberMapOverlayState(): MapOverlayState = remember { MapOverlayState() }
