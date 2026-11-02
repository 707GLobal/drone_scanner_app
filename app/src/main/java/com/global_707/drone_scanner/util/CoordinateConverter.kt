package com.global_707.drone_scanner.util

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 坐标转换：WGS-84（GPS / RID 广播坐标系）→ GCJ-02（火星坐标系，腾讯/高德地图）。
 *
 * 中国境内 WGS-84 与 GCJ-02 存在 300~800 米偏差，
 * 直接在 GCJ-02 地图上绘制 WGS-84 坐标会导致定位明显偏移。
 * 使用公开的 GCJ-02 偏移算法（精度误差 <1 米）。
 */
object CoordinateConverter {

    private const val PI = Math.PI
    private const val A = 6378245.0
    private const val EE = 0.00669342162296594323

    /** WGS-84 → GCJ-02；返回 Pair(纬度, 经度) */
    fun wgs84ToGcj02(lat: Double, lng: Double): Pair<Double, Double> {
        if (outOfChina(lat, lng)) return lat to lng
        var dLat = transformLat(lng - 105.0, lat - 35.0)
        var dLng = transformLng(lng - 105.0, lat - 35.0)
        val radLat = lat / 180.0 * PI
        var magic = sin(radLat)
        magic = 1 - EE * magic * magic
        val sqrtMagic = sqrt(magic)
        dLat = (dLat * 180.0) / ((A * (1 - EE)) / (magic * sqrtMagic) * PI)
        dLng = (dLng * 180.0) / (A / sqrtMagic * cos(radLat) * PI)
        return (lat + dLat) to (lng + dLng)
    }

    /**
     * GCJ-02 → WGS-84（迭代反解，收敛后误差 < 0.1 m）。
     *
     * **只用于"地图视口 → 数据源查询范围"这类反算**（例如把腾讯地图的 GCJ-02 视口
     * 换成离线包 manifest 里 WGS-84 的 bbox），不要用它把数据画到地图上——
     * 绘制方向永远是 [wgs84ToGcj02]。
     *
     * 境外坐标与 [wgs84ToGcj02] 对称：原样返回。
     */
    fun gcj02ToWgs84(lat: Double, lng: Double): Pair<Double, Double> {
        if (outOfChina(lat, lng)) return lat to lng
        var wgsLat = lat
        var wgsLng = lng
        // 正解是"加偏移"，反解即用残差逐次修正；4 次迭代足以收敛到亚米级
        repeat(4) {
            val (gcjLat, gcjLng) = wgs84ToGcj02(wgsLat, wgsLng)
            wgsLat -= gcjLat - lat
            wgsLng -= gcjLng - lng
        }
        return wgsLat to wgsLng
    }

    private fun outOfChina(lat: Double, lng: Double): Boolean =
        lng < 72.004 || lng > 137.8347 || lat < 0.8293 || lat > 55.8271

    private fun transformLat(x: Double, y: Double): Double {
        var ret = -100.0 + 2.0 * x + 3.0 * y + 0.2 * y * y + 0.1 * x * y + 0.2 * sqrt(abs(x))
        ret += (20.0 * sin(6.0 * x * PI) + 20.0 * sin(2.0 * x * PI)) * 2.0 / 3.0
        ret += (20.0 * sin(y * PI) + 40.0 * sin(y / 3.0 * PI)) * 2.0 / 3.0
        ret += (160.0 * sin(y / 12.0 * PI) + 320 * sin(y * PI / 30.0)) * 2.0 / 3.0
        return ret
    }

    private fun transformLng(x: Double, y: Double): Double {
        var ret = 300.0 + x + 2.0 * y + 0.1 * x * x + 0.1 * x * y + 0.1 * sqrt(abs(x))
        ret += (20.0 * sin(6.0 * x * PI) + 20.0 * sin(2.0 * x * PI)) * 2.0 / 3.0
        ret += (20.0 * sin(x * PI) + 40.0 * sin(x / 3.0 * PI)) * 2.0 / 3.0
        ret += (150.0 * sin(x / 12.0 * PI) + 300.0 * sin(x / 30.0 * PI)) * 2.0 / 3.0
        return ret
    }
}
