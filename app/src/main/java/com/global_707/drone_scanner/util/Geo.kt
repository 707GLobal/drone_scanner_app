package com.global_707.drone_scanner.util

/**
 * 地理坐标基础类型（纯 Kotlin，可在 JVM 单测中直接使用）。
 *
 * 约定：本文件中的坐标**一律为 WGS-84**。空域数据源（在线接口 / 离线包 / 矢量文件）
 * 提供的原始坐标都是 WGS-84，转换到 GCJ-02 只允许发生在地图渲染边界
 * （见 ui/map/TencentAirspaceLayer 与 TencentMapBackend），不要把换算散落到数据层。
 */
data class GeoPoint(val lat: Double, val lng: Double) {
    /** 经度归一化到 [-180, 180)，用于跨 180° 经线的边界计算 */
    fun normalized(): GeoPoint = GeoPoint(lat, normalizeLng(lng))

    companion object {
        fun normalizeLng(lng: Double): Double {
            var value = lng
            while (value < -180.0) value += 360.0
            while (value >= 180.0) value -= 360.0
            return value
        }
    }
}

/** WGS-84 经纬度矩形；[minLat]/[minLng] 为西南角。 */
data class GeoRect(
    val minLat: Double,
    val minLng: Double,
    val maxLat: Double,
    val maxLng: Double,
) {
    val latSpan: Double get() = maxLat - minLat
    val lngSpan: Double get() = maxLng - minLng
    val center: GeoPoint get() = GeoPoint((minLat + maxLat) / 2.0, (minLng + maxLng) / 2.0)

    /** 跨度为正、纬度在合法区间内（用于校验 manifest 里的 bbox） */
    val isValid: Boolean
        get() = minLat in -90.0..90.0 && maxLat in -90.0..90.0 &&
            minLng in -180.0..180.0 && maxLng in -180.0..180.0 &&
            latSpan > 0.0 && lngSpan > 0.0

    fun contains(point: GeoPoint): Boolean =
        point.lat in minLat..maxLat && point.lng in minLng..maxLng

    /**
     * 四周按各自跨度比例外扩（例如 ratio = 0.3 表示上下左右各扩 30%），
     * 纬度夹到 ±85.05°（Web 墨卡托可视范围）。
     */
    fun pad(ratio: Double): GeoRect {
        require(ratio >= 0.0) { "ratio 不能为负" }
        val dLat = latSpan * ratio
        val dLng = lngSpan * ratio
        return GeoRect(
            minLat = (minLat - dLat).coerceIn(-85.05112878, 85.05112878),
            minLng = (minLng - dLng).coerceIn(-180.0, 180.0),
            maxLat = (maxLat + dLat).coerceIn(-85.05112878, 85.05112878),
            maxLng = (maxLng + dLng).coerceIn(-180.0, 180.0),
        )
    }

    companion object {
        /** 由两个对角点构造（自动取西南/东北） */
        fun of(a: GeoPoint, b: GeoPoint): GeoRect = GeoRect(
            minLat = minOf(a.lat, b.lat),
            minLng = minOf(a.lng, b.lng),
            maxLat = maxOf(a.lat, b.lat),
            maxLng = maxOf(a.lng, b.lng),
        )
    }
}
