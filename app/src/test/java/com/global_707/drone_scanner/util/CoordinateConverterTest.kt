package com.global_707.drone_scanner.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 坐标转换单测：重点验证新增的 GCJ-02 → WGS-84 反解精度 */
class CoordinateConverterTest {

    @Test
    fun `境内往返转换误差小于一米`() {
        val samples = listOf(
            39.9042 to 116.4074, // 北京
            31.2304 to 121.4737, // 上海
            28.2282 to 112.9388, // 长沙
            22.5431 to 114.0579, // 深圳
        )
        samples.forEach { (lat, lng) ->
            val (gcjLat, gcjLng) = CoordinateConverter.wgs84ToGcj02(lat, lng)
            val (backLat, backLng) = CoordinateConverter.gcj02ToWgs84(gcjLat, gcjLng)
            // 1e-5 度约等于 1 米
            assertTrue("纬度往返误差过大：$backLat vs $lat", kotlin.math.abs(backLat - lat) < 1e-5)
            assertTrue("经度往返误差过大：$backLng vs $lng", kotlin.math.abs(backLng - lng) < 1e-5)
        }
    }

    @Test
    fun `境外坐标两次转换都原样返回`() {
        val lat = 35.6762
        val lng = 139.6503 // 东京
        val (gcjLat, gcjLng) = CoordinateConverter.wgs84ToGcj02(lat, lng)
        assertEquals(lat, gcjLat, 1e-12)
        assertEquals(lng, gcjLng, 1e-12)
        val (backLat, backLng) = CoordinateConverter.gcj02ToWgs84(lat, lng)
        assertEquals(lat, backLat, 1e-12)
        assertEquals(lng, backLng, 1e-12)
    }

    @Test
    fun `境内偏移量与公开算法量级一致`() {
        // GCJ-02 在境内的偏移量级为 300~800 m；这里用度数近似（1° 纬度 ≈ 111 km）校验量级，
        // 不假设偏移方向（不同区域的经纬方向分量不同）。
        val (gcjLat, gcjLng) = CoordinateConverter.wgs84ToGcj02(28.2282, 112.9388)
        val dLatMeters = kotlin.math.abs(gcjLat - 28.2282) * 111_000.0
        val dLngMeters = kotlin.math.abs(gcjLng - 112.9388) * 111_000.0 * kotlin.math.cos(Math.toRadians(28.2282))
        val total = kotlin.math.hypot(dLatMeters, dLngMeters)
        assertTrue("偏移量过小：$total m", total > 100.0)
        assertTrue("偏移量过大：$total m", total < 1_000.0)
    }
}
