package com.global_707.drone_scanner.ui.map

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.global_707.drone_scanner.data.Drone
import kotlin.math.abs

/**
 * 地图后端接口 —— 预留接口。
 *
 * 当前实现：[TencentMapBackend]（腾讯位置服务地图 SDK，真实地图，需 Key，仅真机可用）。
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

    /** 定位到我的位置（移动地图中心）。返回 false 表示后端未实现，由调用方给出提示。 */
    fun locateMe(context: Context): Boolean = false

    /** 移动地图中心到指定坐标（详情页定位弹窗用）。返回 false 表示后端未实现。 */
    fun moveTo(lat: Double, lng: Double, zoom: Float = 15f): Boolean = false

    /**
     * 调整视野以包含所有无人机（约 60% 区域，四周留白）。
     * 单架无人机时定位到该机。返回 false 表示后端未实现或无有效坐标。
     */
    fun moveToDrones(drones: List<Drone>): Boolean = false

    /** 重置北向（地图朝向正北）。返回 false 表示后端未实现，由调用方给出提示。 */
    fun resetNorth(context: Context): Boolean = false
}

/** 地图后端提供者：在这里切换地图实现 */
object MapBackendProvider {
    var backend: MapBackend = TencentMapBackend
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

/**
 * 无人机标记调色板：固定 10 色，按设备 ID 取模分配。
 * 同一设备（同一 ID）颜色恒定，遥控站与对应无人机同色；
 * 无人机数量超过调色板数量时循环复用，保证任何数量下都有可用颜色。
 */
val MARKER_PALETTE = listOf(
    0xFF007AFF.toInt(), // 蓝
    0xFF34C759.toInt(), // 绿
    0xFFFF9500.toInt(), // 橙
    0xFFFF3B30.toInt(), // 红
    0xFFAF52DE.toInt(), // 紫
    0xFF5AC8FA.toInt(), // 天蓝
    0xFFFFCC00.toInt(), // 黄
    0xFFFF2D55.toInt(), // 玫红
    0xFF00C7BE.toInt(), // 青
    0xFF5856D6.toInt(), // 靛蓝
)

/** 无人机标记色：按设备 ID 从调色板取色（同机恒同色） */
fun markerColorArgb(drone: Drone): Int =
    MARKER_PALETTE[abs(drone.id.hashCode()) % MARKER_PALETTE.size]
