package com.global_707.drone_scanner.data.airspace

import com.global_707.drone_scanner.util.GeoPoint
import com.global_707.drone_scanner.util.GeoRect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 演示空域几何（1.1.6 修复"中心块显示不一致"后）：
 * 必须由四条**互不重叠**的矩形带拼成"回"字，不再依赖多边形洞、也不再有中心方块。
 */
class DemoAirspaceSourceTest {

    private val catalog = AirspaceCatalog(
        layerId = AirspaceLayers.SUITABLE,
        packId = "demo",
        packName = "演示数据",
        version = "0",
        zooms = 3..13,
        tileCrs = TileCrs.GCJ02,
        tileSize = 256,
        style = AirspaceLayers.all.first().style,
        attribution = null,
        generatedAtMillis = null,
        dir = "",
        bbox = null,
        isDemo = true,
    )

    private val source = DemoAirspaceSource(catalog)
    private val world = GeoRect(minLat = -85.0, minLng = -180.0, maxLat = 85.0, maxLng = 180.0)

    @Test
    fun `演示空域是四条矩形带`() {
        val features = source.features(world)
        assertEquals(4, features.size)
        assertEquals(4, features.mapNotNull { it.id }.toSet().size)
        features.forEach { feature ->
            val polygon = feature.geometry as AirspaceGeometry.Polygon
            assertEquals(1, polygon.rings.size)
            assertEquals(4, polygon.rings[0].size)
        }
    }

    @Test
    fun `四条带面积之和等于外框减内框`() {
        val rects = source.features(world).map { feature ->
            (feature.geometry as AirspaceGeometry.Polygon).rings[0]
        }
        val outerLat = 0.00675
        val outerLng = 0.00780
        val innerLat = 0.00450
        val innerLng = 0.00520
        val expected = (2 * outerLat) * (2 * outerLng) - (2 * innerLat) * (2 * innerLng)
        assertEquals(expected, rects.sumOf { area(it) }, 1e-12)
    }

    @Test
    fun `中心点不落在任何环带内`() {
        val rings = source.features(world).map { (it.geometry as AirspaceGeometry.Polygon).rings[0] }
        val center = rings.flatMap { it }.let { points ->
            GeoPoint(
                (points.minOf { it.lat } + points.maxOf { it.lat }) / 2,
                (points.minOf { it.lng } + points.maxOf { it.lng }) / 2,
            )
        }
        rings.forEach { ring ->
            assertFalse("中心不应属于任何环带", contains(ring, center))
        }
    }

    @Test
    fun `每条带的内部点只落在该带内`() {
        val rings = source.features(world).map { (it.geometry as AirspaceGeometry.Polygon).rings[0] }
        rings.forEachIndexed { index, ring ->
            val centroid = GeoPoint(
                ring.sumOf { it.lat } / ring.size,
                ring.sumOf { it.lng } / ring.size,
            )
            assertTrue("第 $index 条带应包含自身中心", contains(ring, centroid))
            rings.forEachIndexed { other, otherRing ->
                if (other != index) {
                    assertFalse("第 $index 条带与第 $other 条带不应重叠", contains(otherRing, centroid))
                }
            }
        }
    }

    /** 鞋带公式（度²，取绝对值） */
    private fun area(ring: List<GeoPoint>): Double {
        var sum = 0.0
        for (i in ring.indices) {
            val a = ring[i]
            val b = ring[(i + 1) % ring.size]
            sum += a.lng * b.lat - b.lng * a.lat
        }
        return kotlin.math.abs(sum) / 2.0
    }

    /** 射线法：点是否在多边形内（仅用于本测试的凸矩形） */
    private fun contains(ring: List<GeoPoint>, point: GeoPoint): Boolean {
        var inside = false
        var j = ring.lastIndex
        for (i in ring.indices) {
            val a = ring[i]
            val b = ring[j]
            if ((a.lat > point.lat) != (b.lat > point.lat)) {
                val crossLng = (b.lng - a.lng) * (point.lat - a.lat) / (b.lat - a.lat) + a.lng
                if (point.lng < crossLng) inside = !inside
            }
            j = i
        }
        return inside
    }
}
