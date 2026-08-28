package com.global_707.drone_scanner.ui.map

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.global_707.drone_scanner.data.Drone

/**
 * 地图后端接口 —— 预留接口。
 *
 * 当前内置 [PlaceholderMapBackend]（Canvas 绘制占位地图，无需 API Key、无需网络，
 * 模拟器/真机均可运行）。
 *
 * 后续接入真实地图 SDK（如高德 AMap / 百度 / Google Maps）时：
 *  1. 新增一个实现 [MapBackend] 的后端类；
 *  2. 在 [MapBackendProvider.backend] 中替换为新实现；
 *  3. 页面调用方（MapHomeScreen 等）无需任何改动。
 *
 * 真实地图后端通常需要：SDK 依赖、Manifest 中配置 API Key（meta-data）、
 * 以及生命周期的 onResume/onPause/onDestroy 处理（详见高德官方文档）。
 */
interface MapBackend {
    @Composable
    fun Content(
        drones: List<Drone>,
        satellite: Boolean,
        showOperator: Boolean,
        showLabels: Boolean,
        modifier: Modifier,
    )
}

/** 地图后端提供者：在这里切换地图实现 */
object MapBackendProvider {
    var backend: MapBackend = PlaceholderMapBackend()
}

/** 统一的 Compose 地图入口 */
@Composable
fun DroneMap(
    drones: List<Drone>,
    satellite: Boolean,
    showOperator: Boolean,
    showLabels: Boolean,
    modifier: Modifier = Modifier,
) {
    MapBackendProvider.backend.Content(
        drones = drones,
        satellite = satellite,
        showOperator = showOperator,
        showLabels = showLabels,
        modifier = modifier,
    )
}
