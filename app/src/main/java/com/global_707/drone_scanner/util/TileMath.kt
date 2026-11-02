package com.global_707.drone_scanner.util

import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.sinh
import kotlin.math.tan

/**
 * Web 墨卡托 XYZ 瓦片数学（纯函数，可 JVM 单测）。
 *
 * 编号约定与腾讯位置服务 / 高德 / 谷歌一致：**XYZ，y 从北向南递增**（不是 TMS）。
 * 输入输出经纬度一律 WGS-84。
 */
object TileMath {

    /** Web 墨卡托可表示的纬度上限 */
    const val MAX_MERCATOR_LAT = 85.05112878

    /** 瓦片总数：2^zoom */
    fun tileCount(zoom: Int): Int {
        require(zoom in 0..30) { "非法缩放级别：$zoom" }
        return 1 shl zoom
    }

    /** 经纬度 → 瓦片号；返回 (x, y)。越界值会被夹到有效网格内。 */
    fun lonLatToTile(lat: Double, lng: Double, zoom: Int): Pair<Int, Int> {
        val n = tileCount(zoom)
        val x = ((GeoPoint.normalizeLng(lng) + 180.0) / 360.0 * n)
            .let { floor(it).toInt() }
            .coerceIn(0, n - 1)
        val safeLat = lat.coerceIn(-MAX_MERCATOR_LAT, MAX_MERCATOR_LAT)
        val latRad = safeLat * PI / 180.0
        val y = ((1.0 - ln(tan(latRad) + 1.0 / kotlin.math.cos(latRad)) / PI) / 2.0 * n)
            .let { floor(it).toInt() }
            .coerceIn(0, n - 1)
        return x to y
    }

    /** 瓦片中心点（WGS-84） */
    fun tileCenter(zoom: Int, x: Int, y: Int): GeoPoint {
        val n = tileCount(zoom)
        val lng = (x + 0.5) / n * 360.0 - 180.0
        val lat = Math.toDegrees(atan(sinh(PI * (1.0 - 2.0 * (y + 0.5) / n))))
        return GeoPoint(lat, lng)
    }

    /** 瓦片四至（WGS-84）：[minLat]/[minLng] 为西南角 */
    fun tileRect(zoom: Int, x: Int, y: Int): GeoRect {
        val north = tileTopLat(zoom, y)
        val south = tileTopLat(zoom, y + 1)
        val west = tileLeftLng(zoom, x)
        val east = tileLeftLng(zoom, x + 1)
        return GeoRect(
            minLat = minOf(north, south),
            minLng = minOf(west, east),
            maxLat = maxOf(north, south),
            maxLng = maxOf(west, east),
        )
    }

    /**
     * 地图（GCJ-02 网格）瓦片号 → 数据源（WGS-84 网格）瓦片号。
     *
     * 腾讯地图的瓦片网格建立在 GCJ-02 上，而适飞空域数据的瓦片网格建立在 WGS-84 上，
     * 两者在中国境内相差 300~800 m。瓦片是离散编号，只能整体取"包含偏移后中心点"的那一张，
     * 因此该映射无法消除偏移本身（残余约等于 GCJ 偏移，z13 约 30 px）。
     *
     * 想做到零偏移有两个办法：
     *  1. 离线包在**生成时**按 GCJ 网格出图（manifest 里声明 crs = GCJ02，本函数不参与）；
     *  2. 在瓦片 provider 内做像素级预移位（成本高，暂未实现）。
     *
     * @return (x, y) 为同 zoom 下的 WGS-84 瓦片号。
     */
    fun gcjTileToWgsTile(zoom: Int, x: Int, y: Int): Pair<Int, Int> {
        val center = tileCenter(zoom, x, y)
        val (wgsLat, wgsLng) = CoordinateConverter.gcj02ToWgs84(center.lat, center.lng)
        return lonLatToTile(wgsLat, wgsLng, zoom)
    }

    private fun tileLeftLng(zoom: Int, x: Int): Double = x.toDouble() / tileCount(zoom) * 360.0 - 180.0

    private fun tileTopLat(zoom: Int, y: Int): Double {
        val n = tileCount(zoom).toDouble()
        return Math.toDegrees(atan(sinh(PI * (1.0 - 2.0 * (y / n)))))
    }
}
