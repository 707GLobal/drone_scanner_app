package com.global_707.drone_scanner.ui.map

import android.content.Context
import com.global_707.drone_scanner.data.AppLogger
import com.global_707.drone_scanner.data.airspace.AirspaceCatalog
import com.global_707.drone_scanner.data.airspace.AirspaceFeature
import com.global_707.drone_scanner.data.airspace.AirspaceGeometry
import com.global_707.drone_scanner.data.airspace.AirspaceRepository
import com.global_707.drone_scanner.util.CoordinateConverter
import com.global_707.drone_scanner.util.GeoPoint
import com.global_707.drone_scanner.util.GeoRect
import com.tencent.tencentmap.mapsdk.maps.TencentMap
import com.tencent.tencentmap.mapsdk.maps.model.LatLng
import com.tencent.tencentmap.mapsdk.maps.model.Polygon
import com.tencent.tencentmap.mapsdk.maps.model.PolygonOptions
import com.tencent.tencentmap.mapsdk.maps.model.Polyline
import com.tencent.tencentmap.mapsdk.maps.model.PolylineOptions
import com.tencent.tencentmap.mapsdk.maps.model.TileOverlay
import com.tencent.tencentmap.mapsdk.maps.model.TileOverlayOptions

/**
 * 空域叠加层的地图侧宿主（**唯一接触腾讯地图 API 的空域代码**）。
 *
 * 职责：
 *  - 栅格图层：为每个"已开启且已选包"的图层挂/卸一个 `TileOverlay`（任何缩放级别都显示，
 *    超出原生级别由 [TileOverzoom] 放大）；
 *  - 矢量图层（演示数据、将来的矢量包）：把 `features()` 的 WGS-84 几何**逐顶点**转成 GCJ-02
 *    后画 `Polygon` / `Polyline`；
 *  - 相机监听**不在这里注册**：腾讯 `setOnCameraChangeListener` 是单 setter，
 *    由 `TencentMapBackend` 统一注册后分发（见 `MapCameraState`）。
 *
 * 绘制是**同步 + 幂等**的（数据源 `features()` 约定同步返回）：
 * 一次 `sync()` 内要么整层重画，要么什么都不做，不会出现"两次绘制交错 → 中心块时有时无"。
 *
 * 绝不调用 `map.clear()`：无人机标记由 `TencentMapBackend` 自己逐个移除，两者互不干扰。
 */
class TencentAirspaceLayer(private val context: Context) {

    private var map: TencentMap? = null
    private val tileOverlays = mutableMapOf<String, TileOverlay>()
    private val appliedKeys = mutableMapOf<String, String>()
    private val polygons = mutableMapOf<String, MutableList<Polygon>>()
    private val polylines = mutableMapOf<String, MutableList<Polyline>>()

    /** 已绘制矢量的数据指纹（只有它变化时才重画，避免重复叠色） */
    private val renderedVectorKeys = mutableMapOf<String, String>()

    /** 上一次同步时的底图类型：切换底图后强制重建瓦片层与矢量层 */
    private var lastSatellite: Boolean? = null

    fun attach(target: TencentMap) {
        if (map === target) return
        release()
        map = target
    }

    /**
     * 按"当前应显示的图层集合 + 每层的数据"同步覆盖物。
     * 由 Compose 在（地图就绪 / 开关变化 / 选包变化 / 演示模式变化 / 底图切换）时调用。
     */
    fun sync(visibleLayers: Set<String>, catalogs: Map<String, AirspaceCatalog>, satellite: Boolean) {
        val target = map ?: return

        val baseMapChanged = lastSatellite != null && lastSatellite != satellite
        if (baseMapChanged) {
            // 切底图后部分 SDK 版本会清掉覆盖物，这里主动重建，避免"图层闪没/残影"
            tileOverlays.values.forEach { runCatching { it.remove() } }
            tileOverlays.clear()
            appliedKeys.clear()
            polygons.keys.toList().forEach { clearVectorHandles(it) }
            renderedVectorKeys.clear()
        }
        lastSatellite = satellite

        // 1) 卸载不再显示的图层
        tileOverlays.keys.toList().forEach { layerId ->
            if (layerId !in visibleLayers || catalogs[layerId] == null) {
                tileOverlays.remove(layerId)?.let { runCatching { it.remove() } }
                appliedKeys.remove(layerId)
            }
        }
        (polygons.keys + polylines.keys).toSet().forEach { layerId ->
            if (layerId !in visibleLayers || catalogs[layerId] == null) {
                clearVectorHandles(layerId)
                renderedVectorKeys.remove(layerId)
            }
        }

        // 2) 创建/更新需要显示的图层（同步完成，不并发）
        visibleLayers.forEach { layerId ->
            val catalog = catalogs[layerId] ?: return@forEach
            if (catalog.isDemo) {
                ensureDemoVector(target, catalog)
            } else {
                ensureTileOverlay(target, catalog)
            }
        }
    }

    fun release() {
        tileOverlays.values.forEach { runCatching { it.remove() } }
        tileOverlays.clear()
        appliedKeys.clear()
        polygons.keys.toList().forEach { clearVectorHandles(it) }
        polylines.clear()
        renderedVectorKeys.clear()
        map = null
    }

    // ---------- 栅格 ----------

    private fun ensureTileOverlay(target: TencentMap, catalog: AirspaceCatalog) {
        val key = dataKey(catalog)
        if (appliedKeys[catalog.layerId] == key) return
        val source = AirspaceRepository.source(catalog.layerId) ?: return

        // 换包/换级别后，放大缓存必须失效（否则会显示旧数据）
        TileOverzoom.clear()
        tileOverlays.remove(catalog.layerId)?.let { runCatching { it.remove() } }
        val overlay = runCatching {
            target.addTileOverlay(
                TileOverlayOptions()
                    .tileProvider(AirspaceTileProvider(source, catalog))
                    .zIndex(TILE_Z_INDEX),
            )
        }.onFailure {
            AppLogger.e("Airspace", "overlay_add_failed layer=${catalog.layerId} msg=${it.message}", it)
        }.getOrNull()

        if (overlay != null) {
            tileOverlays[catalog.layerId] = overlay
            appliedKeys[catalog.layerId] = key
            AppLogger.i(
                "Airspace",
                "overlay_added layer=${catalog.layerId} pack=${catalog.packName} id=${catalog.packId} zooms=${catalog.zooms}",
            )
        }
    }

    // ---------- 矢量（演示数据 / 将来的矢量包） ----------

    private fun ensureDemoVector(target: TencentMap, catalog: AirspaceCatalog) {
        val key = "${catalog.packId}|${catalog.layerId}"
        if (renderedVectorKeys[catalog.layerId] == key && polygons.containsKey(catalog.layerId)) return
        val source = AirspaceRepository.source(catalog.layerId) ?: return

        // 先清后画，且同步完成：即使 sync 被连续调用，也不会出现两套多边形叠加
        clearVectorHandles(catalog.layerId)
        val features = runCatching { source.features(WORLD) }.getOrDefault(emptyList())
        drawVectors(target, catalog, features)
        renderedVectorKeys[catalog.layerId] = key
        AppLogger.i(
            "Airspace",
            "vector_drawn layer=${catalog.layerId} features=${features.size}",
        )
    }

    private fun drawVectors(
        target: TencentMap,
        catalog: AirspaceCatalog,
        features: List<AirspaceFeature>,
    ) {
        val drawnPolygons = mutableListOf<Polygon>()
        val drawnPolylines = mutableListOf<Polyline>()
        features.forEach { feature ->
            when (val geometry = feature.geometry) {
                is AirspaceGeometry.Polygon -> {
                    val rings = geometry.rings
                        .map { ring -> ring.map { it.toLatLng() } }
                        .filter { it.size >= 3 }
                    if (rings.isEmpty()) return@forEach
                    drawnPolygons += target.addPolygon(
                        PolygonOptions()
                            .add(rings.first())
                            .setHolePoints(rings.drop(1))
                            .fillColor(catalog.style.fillColor)
                            .strokeColor(catalog.style.strokeColor)
                            .strokeWidth(catalog.style.strokeWidthDp)
                            .zIndex(VECTOR_Z_INDEX),
                    )
                }

                is AirspaceGeometry.Line -> {
                    val points = geometry.points.map { it.toLatLng() }
                    if (points.size < 2) return@forEach
                    drawnPolylines += target.addPolyline(
                        PolylineOptions()
                            .addAll(points)
                            .color(catalog.style.strokeColor)
                            .width(catalog.style.strokeWidthDp)
                            .zIndex(VECTOR_Z_INDEX),
                    )
                }
            }
        }
        polygons[catalog.layerId] = drawnPolygons
        polylines[catalog.layerId] = drawnPolylines
    }

    /** 只移除覆盖物句柄，不动"数据指纹"（指纹由调用方维护） */
    private fun clearVectorHandles(layerId: String) {
        polygons.remove(layerId)?.forEach { runCatching { it.remove() } }
        polylines.remove(layerId)?.forEach { runCatching { it.remove() } }
    }

    /** WGS-84 → GCJ-02：空域坐标入图的**唯一**转换点 */
    private fun GeoPoint.toLatLng(): LatLng {
        val (lat, lng) = CoordinateConverter.wgs84ToGcj02(this.lat, this.lng)
        return LatLng(lat, lng)
    }

    private fun dataKey(catalog: AirspaceCatalog): String =
        "${catalog.packId}|${catalog.version}|${catalog.zooms.first}-${catalog.zooms.last}|${catalog.dir}"

    private companion object {
        /** 底图之上、标记之下 */
        const val TILE_Z_INDEX = 0

        /** 矢量面压在同一层，位于瓦片之上 */
        const val VECTOR_Z_INDEX = 1

        /** 演示数据不看视口，用全世界范围即可 */
        val WORLD = GeoRect(-85.0, -180.0, 85.0, 180.0)
    }
}
