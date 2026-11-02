package com.global_707.drone_scanner.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 瓦片数学单测：以接入文档给出的示例为准（z13 / 112.928,28.228 → 6665/3425），
 * 并校验四至、中心点与"GCJ 瓦片 → WGS 瓦片"的换算方向。
 */
class TileMathTest {

    @Test
    fun `文档示例反查瓦片号`() {
        // 文档示例：bbox 覆盖 112.90~112.96 / 28.20~28.26，z13 → 6665/3425
        val (x, y) = TileMath.lonLatToTile(lat = 28.228, lng = 112.928, zoom = 13)
        assertEquals(6665, x)
        assertEquals(3425, y)
    }

    @Test
    fun `赤道与格林尼治为网格原点`() {
        val (x, y) = TileMath.lonLatToTile(lat = 0.0, lng = 0.0, zoom = 1)
        assertEquals(1, x)
        assertEquals(1, y)
    }

    @Test
    fun `瓦片四至包含其中心点`() {
        val rect = TileMath.tileRect(zoom = 13, x = 6665, y = 3425)
        val center = TileMath.tileCenter(zoom = 13, x = 6665, y = 3425)
        assertTrue(rect.contains(center))
        assertTrue(rect.minLat < rect.maxLat)
        assertTrue(rect.minLng < rect.maxLng)
        // z13 在纬度 28° 附近，单瓦片约 4 km 量级
        assertTrue(rect.lngSpan in 0.03..0.06)
    }

    @Test
    fun `GCJ 瓦片换算到 WGS 瓦片后偏移不超过一张`() {
        // 中国境内 GCJ-02 相对 WGS-84 整体向西北偏移（经度偏大、纬度偏大），
        // 因此换算回来的 WGS 瓦片号：x 不会更大、y 不会更小（y 向南递增）。
        val z = 13
        val x = 6665
        val y = 3425
        val (wgsX, wgsY) = TileMath.gcjTileToWgsTile(z, x, y)
        assertTrue("经度方向不应向东偏移", wgsX <= x)
        assertTrue("纬度方向不应向北偏移", wgsY >= y)
        assertTrue(kotlin.math.abs(wgsX - x) <= 1)
        assertTrue(kotlin.math.abs(wgsY - y) <= 1)
    }

    @Test
    fun `纬度越界被夹到墨卡托上限`() {
        val (_, yNorth) = TileMath.lonLatToTile(lat = 89.0, lng = 0.0, zoom = 4)
        val (_, yLimit) = TileMath.lonLatToTile(lat = TileMath.MAX_MERCATOR_LAT, lng = 0.0, zoom = 4)
        assertEquals(yLimit, yNorth)
    }

    @Test
    fun `跨 180 经线经度被归一化`() {
        val (xEast, _) = TileMath.lonLatToTile(lat = 0.0, lng = 179.9, zoom = 3)
        val (xWrapped, _) = TileMath.lonLatToTile(lat = 0.0, lng = -180.1, zoom = 3)
        assertEquals(xEast, xWrapped)
    }
}
