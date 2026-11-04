package com.global_707.drone_scanner.data.airspace

import com.global_707.drone_scanner.data.MockData
import com.global_707.drone_scanner.util.GeoPoint
import com.global_707.drone_scanner.util.GeoRect

/**
 * 演示空域（**仅演示模式**，非真实空域数据）。
 *
 * 位置：演示无人机附近（[MockData] 首架演示无人机的坐标），画一个"回"字环，便于在没有真实
 * 数据包时预览图层渲染与配色。菜单里会显示「演示」标记，与真实数据区分。
 *
 * 实现要点（1.1.6 起）：
 *  - **不使用多边形洞**：环由 4 条互不重叠的矩形带拼成（上/下/左/右），规避 SDK 的洞渲染
 *    在首次绘制/底图切换后出现的"中心块时有时无"；
 *  - 中心不再叠加小方块（无人机本身已有标记，叠加只会引入透明度与绘制顺序问题）；
 *  - 返回空表也不要抛异常：无数据就是无数据。
 */
class DemoAirspaceSource(private val catalog: AirspaceCatalog) : AirspaceSource {

    override val layerId: String = catalog.layerId

    override fun catalog(): AirspaceCatalog = catalog

    override fun tile(z: Int, x: Int, y: Int): ByteArray? = null

    override fun features(viewport: GeoRect): List<AirspaceFeature> {
        val drone = MockData.drones.firstOrNull() ?: return emptyList()
        val lat = drone.droneLat ?: return emptyList()
        val lng = drone.droneLng ?: return emptyList()

        // 外框 ≈ 1.5 km，内框 ≈ 1.0 km（按北纬 31° 粗略换算）
        val outerLat = 0.00675
        val outerLng = 0.00780
        val innerLat = 0.00450
        val innerLng = 0.00520

        return listOf(
            band("demo-band-top", lat + innerLat, lat + outerLat, lng - outerLng, lng + outerLng),
            band("demo-band-bottom", lat - outerLat, lat - innerLat, lng - outerLng, lng + outerLng),
            band("demo-band-left", lat - innerLat, lat + innerLat, lng - outerLng, lng - innerLng),
            band("demo-band-right", lat - innerLat, lat + innerLat, lng + innerLng, lng + outerLng),
        )
    }

    /** 由两个纬度和两个经度构成的矩形带（逆时针四条边，首尾不重复） */
    private fun band(
        id: String,
        latSouth: Double,
        latNorth: Double,
        lngWest: Double,
        lngEast: Double,
    ): AirspaceFeature = AirspaceFeature(
        id = id,
        kind = AirspaceKind.SUITABLE,
        geometry = AirspaceGeometry.Polygon(
            listOf(
                listOf(
                    GeoPoint(latSouth, lngWest),
                    GeoPoint(latSouth, lngEast),
                    GeoPoint(latNorth, lngEast),
                    GeoPoint(latNorth, lngWest),
                ),
            ),
        ),
    )
}
