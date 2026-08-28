package com.global_707.drone_scanner.ui.map

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.global_707.drone_scanner.data.Drone
import kotlin.math.abs

/**
 * 地图后端接口 —— 预留接口。
 *
 * 当前提供两个实现：
 *  - [AmapMapBackend]：高德地图 3D SDK（真实地图，需 API Key，仅真机可用）
 *  - [PlaceholderMapBackend]：Canvas 占位地图（无需 Key，模拟器/真机均可）
 *
 * 参数语义：
 *  - [showOperator]   首页为 false（只显示飞机标记）；详情页为 true（飞机+遥控站）
 *  - [showMyLocation] 详情页 true，地图上显示本机位置
 *  - [showLabels]     标记旁显示飞机呼号（ID 后四位）
 */
interface MapBackend {
    @Composable
    fun Content(
        drones: List<Drone>,
        satellite: Boolean,
        showOperator: Boolean,
        showMyLocation: Boolean,
        showLabels: Boolean,
        modifier: Modifier,
    )

    /**
     * 定位到我的位置（移动地图中心）。
     * 返回 false 表示后端未实现（占位地图），由调用方给出提示。
     */
    fun locateMe(context: Context): Boolean = false

    /**
     * 重置北向（地图朝向正北）。
     * 返回 false 表示后端未实现（占位地图），由调用方给出提示。
     */
    fun resetNorth(context: Context): Boolean = false
}

/** 地图后端提供者：在这里切换地图实现 */
object MapBackendProvider {
    var backend: MapBackend = AmapMapBackend
}

/** 统一的 Compose 地图入口 */
@Composable
fun DroneMap(
    drones: List<Drone>,
    satellite: Boolean,
    showOperator: Boolean = false,
    showMyLocation: Boolean = false,
    showLabels: Boolean = true,
    modifier: Modifier = Modifier,
) {
    MapBackendProvider.backend.Content(
        drones = drones,
        satellite = satellite,
        showOperator = showOperator,
        showMyLocation = showMyLocation,
        showLabels = showLabels,
        modifier = modifier,
    )
}

/** 无人机标记色：按设备 ID Hash 取色（不同飞机颜色不同、色调均匀分布） */
fun markerColorArgb(drone: Drone): Int {
    val hue = (abs(drone.id.hashCode()) % 360).toFloat()
    return Color.hsv(hue, 0.72f, 0.95f).toArgb()
}
