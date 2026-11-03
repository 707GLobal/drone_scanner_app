package com.global_707.drone_scanner.data.airspace

import com.global_707.drone_scanner.data.MockData
import com.global_707.drone_scanner.util.GeoPoint
import com.global_707.drone_scanner.util.GeoRect

/**
 * 演示空域（**仅演示模式**，非真实空域数据）。
 *
 * 位置：演示无人机附近（[MockData] 首架演示无人机的坐标），画一个"回"字环 + 中心小方块，
 * 便于在没有真实数据包时预览图层渲染与配色。菜单里会显示「演示」标记，与真实数据区分。
 *
 * 只提供矢量要素（[features]），不提供任何瓦片。
 */
class DemoAirspaceSource(private val catalog: AirspaceCatalog) : AirspaceSource {

    override val layerId: String = catalog.layerId

    override fun catalog(): AirspaceCatalog = catalog

    override fun tile(z: Int, x: Int, y: Int): ByteArray? = null

    override suspend fun features(viewport: GeoRect): List<AirspaceFeature> {
        val drone = MockData.drones.firstOrNull() ?: return emptyList()
        val lat = drone.droneLat ?: return emptyList()
        val lng = drone.droneLng ?: return emptyList()
        return listOf(
            AirspaceFeature(
                id = "demo-ring",
                kind = AirspaceKind.SUITABLE,
                geometry = AirspaceGeometry.Polygon(
                    listOf(
                        square(lat, lng, halfLat = 0.00675, halfLng = 0.0078), // 外环 ≈ 1.5 km
                        square(lat, lng, halfLat = 0.00450, halfLng = 0.0052), // 内洞 ≈ 1.0 km
                    ),
                ),
            ),
            AirspaceFeature(
                id = "demo-center",
                kind = AirspaceKind.SUITABLE,
                geometry = AirspaceGeometry.Polygon(
                    listOf(square(lat, lng, halfLat = 0.0009, halfLng = 0.0010)), // 中心小方块 ≈ 200 m
                ),
            ),
        )
    }

    private fun square(centerLat: Double, centerLng: Double, halfLat: Double, halfLng: Double) =
        listOf(
            GeoPoint(centerLat - halfLat, centerLng - halfLng),
            GeoPoint(centerLat - halfLat, centerLng + halfLng),
            GeoPoint(centerLat + halfLat, centerLng + halfLng),
            GeoPoint(centerLat + halfLat, centerLng - halfLng),
        )
}
