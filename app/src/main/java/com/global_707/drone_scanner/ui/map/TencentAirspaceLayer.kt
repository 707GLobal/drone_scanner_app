package com.global_707.drone_scanner.ui.map

import android.content.Context
import com.global_707.drone_scanner.data.AppLogger
import com.global_707.drone_scanner.data.airspace.AirspaceCatalog
import com.global_707.drone_scanner.data.airspace.AirspaceLayerStatus
import com.global_707.drone_scanner.data.airspace.AirspaceRepository
import com.global_707.drone_scanner.data.airspace.AirspaceState
import com.tencent.tencentmap.mapsdk.maps.TencentMap
import com.tencent.tencentmap.mapsdk.maps.model.CameraPosition
import com.tencent.tencentmap.mapsdk.maps.model.TileOverlay
import com.tencent.tencentmap.mapsdk.maps.model.TileOverlayOptions

/**
 * 空域叠加层的地图侧宿主（**唯一接触腾讯地图 API 的空域代码**）。
 *
 * 职责：
 *  - 为每个"已开启且已安装"的图层挂/卸一个 `TileOverlay`；
 *  - 统一注册相机监听（腾讯的 setter 是单例式，必须单点分发），把当前缩放换算成图层状态；
 *  - 绝不调用 `map.clear()`：无人机标记由 `TencentMapBackend` 自己逐个移除，
 *    空域覆盖物由本类独占管理，两边生命周期互不干扰。
 *
 * 坐标系：瓦片包按 GCJ-02 网格生成时直接贴合底图；WGS-84 网格由
 * [AirspaceTileProvider] 做瓦片号换算（残余偏移见其 KDoc）。
 */
class TencentAirspaceLayer(private val context: Context) {

    private var map: TencentMap? = null
    private val overlays = mutableMapOf<String, TileOverlay>()

    /** 已挂载图层的数据指纹（版本/级别/网格变化时必须重建覆盖物） */
    private val appliedKeys = mutableMapOf<String, String>()

    /** 上一次同步时的底图类型：切换底图后强制重建瓦片层（部分 SDK 版本会清空覆盖物） */
    private var lastSatellite: Boolean? = null

    private val cameraListener = object : TencentMap.OnCameraChangeListener {
        override fun onCameraChange(position: CameraPosition) = Unit

        override fun onCameraChangeFinished(position: CameraPosition) {
            updateZoomStatus(position.zoom)
        }
    }

    /** 绑定地图（幂等）；同一实例只注册一次相机监听 */
    fun attach(target: TencentMap) {
        if (map === target) return
        release()
        map = target
        target.setOnCameraChangeListener(cameraListener)
    }

    /**
     * 按"当前应显示的图层集合 + 已安装的元信息"同步覆盖物。
     * 由 Compose 的 LaunchedEffect 在（地图就绪 / 开关变化 / 包版本变化）时调用。
     */
    fun sync(visibleLayers: Set<String>, catalogs: Map<String, AirspaceCatalog>, satellite: Boolean) {
        val target = map ?: return

        if (lastSatellite != null && lastSatellite != satellite) {
            // 底图切换后重建：避免某些 SDK 版本在 mapType 变化时清掉瓦片层导致图层"消失"
            overlays.values.forEach { runCatching { it.remove() } }
            overlays.clear()
            appliedKeys.clear()
        }
        lastSatellite = satellite

        // 1) 卸载不再显示或数据已失效的图层
        overlays.keys.toList().forEach { layerId ->
            if (layerId !in visibleLayers || catalogs[layerId] == null) {
                overlays.remove(layerId)?.let { runCatching { it.remove() } }
                appliedKeys.remove(layerId)
                AirspaceState.setStatus(layerId, AirspaceLayerStatus.OFF)
            }
        }

        // 2) 创建 / 更新需要显示的图层
        visibleLayers.forEach { layerId ->
            val catalog = catalogs[layerId] ?: return@forEach
            val key = dataKey(catalog)
            if (appliedKeys[layerId] == key) return@forEach
            val source = AirspaceRepository.source(layerId) ?: return@forEach

            overlays.remove(layerId)?.let { runCatching { it.remove() } }
            val overlay = runCatching {
                target.addTileOverlay(
                    TileOverlayOptions()
                        .tileProvider(AirspaceTileProvider(source, catalog))
                        .visibleLevelRange(catalog.zooms.first, catalog.zooms.last)
                        .zIndex(TILE_Z_INDEX),
                )
            }.onFailure {
                AppLogger.e("Airspace", "overlay_add_failed layer=$layerId msg=${it.message}", it)
            }.getOrNull()

            if (overlay != null) {
                overlays[layerId] = overlay
                appliedKeys[layerId] = key
                AppLogger.i(
                    "Airspace",
                    "overlay_added layer=$layerId version=${catalog.version} zooms=${catalog.zooms} crs=${catalog.tileCrs}",
                )
            }
        }

        updateZoomStatus(target.cameraPosition?.zoom)
    }

    /** 释放全部覆盖物与地图引用（页面销毁时调用） */
    fun release() {
        overlays.values.forEach { runCatching { it.remove() } }
        overlays.clear()
        appliedKeys.clear()
        map = null
    }

    /** 当前缩放是否落在数据包的级别范围内 → 更新图层状态（菜单据此显示提示） */
    private fun updateZoomStatus(zoom: Float?) {
        if (zoom == null) return
        val z = zoom.toInt()
        val catalogs = AirspaceState.catalogs
        overlays.keys.toList().forEach { layerId ->
            val catalog = catalogs[layerId] ?: return@forEach
            AirspaceState.setStatus(
                layerId,
                if (z in catalog.zooms) AirspaceLayerStatus.READY else AirspaceLayerStatus.ZOOM_OUT,
            )
        }
    }

    private fun dataKey(catalog: AirspaceCatalog): String =
        "${catalog.version}|${catalog.zooms.first}-${catalog.zooms.last}|${catalog.tileCrs}|${catalog.dir}"

    private companion object {
        /** 底图之上、标记之下 */
        const val TILE_Z_INDEX = 0
    }
}
