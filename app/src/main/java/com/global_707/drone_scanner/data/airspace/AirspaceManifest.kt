package com.global_707.drone_scanner.data.airspace

import com.global_707.drone_scanner.util.GeoRect

/**
 * 离线数据包 manifest 的解析与校验（**纯函数**，不依赖 Android，可 JVM 单测）。
 *
 * 约定：
 *  - 客户端不得猜测数据结构，字段缺失或非法一律抛 [AirspaceException.Kind.MANIFEST_INVALID]；
 *  - manifest 里的坐标、bbox 都是 **WGS-84**；
 *  - 颜色与缩放级别由包提供，但只有注册过的图层（[AirspaceLayers.all]）才会被 App 使用。
 *
 * 示例：
 * ```json
 * {
 *   "schema": 1,
 *   "version": "2026-11-02",
 *   "generatedAt": 1762041600000,
 *   "tileSize": 256,
 *   "crs": "GCJ02",
 *   "zooms": [4, 13],
 *   "bbox": [73.0, 17.5, 135.5, 54.0],
 *   "style": { "fill": "#337FC4FF", "stroke": "#CC5AA9E6", "strokeWidth": 2 },
 *   "attribution": "适飞空域数据，仅供参考",
 *   "layers": [ { "id": "suitable", "dir": "suitable", "zooms": [4, 13] } ]
 * }
 * ```
 */
data class ManifestLayer(
    val id: String,
    val dir: String,
    val zooms: IntRange,
)

data class AirspaceManifest(
    val schema: Int,
    val version: String,
    val generatedAtMillis: Long?,
    val tileSize: Int,
    val crs: TileCrs,
    val style: AirspaceStyle?,
    val attribution: String?,
    val bbox: GeoRect?,
    val layers: List<ManifestLayer>,
) {
    fun layer(id: String): ManifestLayer? = layers.firstOrNull { it.id == id }

    companion object {
        const val SCHEMA_VERSION = 1

        /** 目前只支持 256 px 瓦片（腾讯 TileOverlay 的常规尺寸） */
        private const val SUPPORTED_TILE_SIZE = 256

        private val LAYER_ID_REGEX = Regex("^[a-z0-9_]{1,32}$")

        fun parse(json: String): AirspaceManifest {
            val root = runCatching { MiniJson.parse(json) }
                .getOrElse { throw invalid("JSON 解析失败：${it.message}") }
            val map = root as? Map<*, *> ?: throw invalid("manifest 顶层必须是对象")
            val obj = map.stringKeyMap()

            val schema = obj.int("schema") ?: throw invalid("缺少 schema")
            if (schema != SCHEMA_VERSION) throw invalid("不支持的 schema：$schema")

            val version = obj.optString("version")?.takeIf { it.isNotBlank() }
                ?: throw invalid("缺少 version")

            val tileSize = obj.int("tileSize") ?: SUPPORTED_TILE_SIZE
            if (tileSize != SUPPORTED_TILE_SIZE) {
                throw invalid("不支持的 tileSize：$tileSize（只支持 $SUPPORTED_TILE_SIZE）")
            }

            val crs = when (obj.optString("crs")?.uppercase() ?: "GCJ02") {
                "GCJ02" -> TileCrs.GCJ02
                "WGS84" -> TileCrs.WGS84
                else -> throw invalid("不支持的 crs：${obj.optString("crs")}")
            }

            val packZooms = obj.zoomRange("zooms")

            val layersRaw = obj["layers"] as? List<*>
                ?: throw invalid("缺少 layers")
            if (layersRaw.isEmpty()) throw invalid("layers 不能为空")

            val layers = layersRaw.mapIndexed { index, item ->
                val layerMap = (item as? Map<*, *>)?.stringKeyMap()
                    ?: throw invalid("layers[$index] 必须是对象")
                val id = layerMap.optString("id")?.trim().orEmpty()
                if (!LAYER_ID_REGEX.matches(id)) {
                    throw invalid("layers[$index].id 非法：'$id'（只允许小写字母、数字、下划线）")
                }
                val dir = layerMap.optString("dir")?.trim()?.takeIf { it.isNotEmpty() } ?: id
                if (!isSafeRelativePath(dir)) throw invalid("layers[$index].dir 非法：'$dir'")
                val zooms = layerMap.zoomRange("zooms") ?: packZooms
                ?: throw invalid("layers[$index] 缺少 zooms（包级 zooms 也未提供）")
                ManifestLayer(id = id, dir = dir, zooms = zooms)
            }
            val duplicated = layers.groupBy { it.id }.filterValues { it.size > 1 }.keys
            if (duplicated.isNotEmpty()) throw invalid("layers 中 id 重复：$duplicated")

            val bbox = obj.geoRect("bbox")

            return AirspaceManifest(
                schema = schema,
                version = version,
                generatedAtMillis = obj.long("generatedAt"),
                tileSize = tileSize,
                crs = crs,
                style = obj.style(),
                attribution = obj.optString("attribution")?.takeIf { it.isNotBlank() },
                bbox = bbox,
                layers = layers,
            )
        }

        /** 相对路径安全检查：禁止绝对路径、盘符、`..` 段（防止解压越界写入） */
        fun isSafeRelativePath(path: String): Boolean {
            if (path.isBlank()) return false
            if (path.startsWith("/") || path.startsWith("\\")) return false
            if (path.contains(':')) return false
            val segments = path.split('/', '\\')
            return segments.none { it.isEmpty() || it == "." || it == ".." }
        }

        private fun invalid(message: String) =
            AirspaceException(AirspaceException.Kind.MANIFEST_INVALID, message)
    }
}

// ---------- 解析辅助：把 MiniJson 的 Map<String, Any?> 转成强类型 --------------

@Suppress("UNCHECKED_CAST")
private fun Map<*, *>.stringKeyMap(): Map<String, Any?> =
    entries.associate { (k, v) -> k.toString() to v }

private fun Map<String, Any?>.optString(key: String): String? = this[key] as? String

private fun Map<String, Any?>.int(key: String): Int? = when (val v = this[key]) {
    is Double -> if (v % 1.0 == 0.0 && v >= Int.MIN_VALUE && v <= Int.MAX_VALUE) v.toInt() else null
    is Int -> v
    is String -> v.toIntOrNull()
    else -> null
}

private fun Map<String, Any?>.long(key: String): Long? = when (val v = this[key]) {
    is Double -> if (v % 1.0 == 0.0) v.toLong() else null
    is Int -> v.toLong()
    is String -> v.toLongOrNull()
    else -> null
}

private fun Map<String, Any?>.zoomRange(key: String): IntRange? {
    val raw = this[key] as? List<*> ?: return null
    if (raw.size != 2) throw AirspaceException(
        AirspaceException.Kind.MANIFEST_INVALID,
        "$key 必须是 [min, max] 两个整数",
    )
    val min = (raw[0] as? Double)?.toInt() ?: throw AirspaceException(
        AirspaceException.Kind.MANIFEST_INVALID, "$key[0] 必须是整数",
    )
    val max = (raw[1] as? Double)?.toInt() ?: throw AirspaceException(
        AirspaceException.Kind.MANIFEST_INVALID, "$key[1] 必须是整数",
    )
    if (min !in 0..22 || max !in 0..22) throw AirspaceException(
        AirspaceException.Kind.MANIFEST_INVALID, "$key 超出 0..22",
    )
    if (min > max) throw AirspaceException(
        AirspaceException.Kind.MANIFEST_INVALID, "$key 最小值大于最大值",
    )
    return min..max
}

private fun Map<String, Any?>.geoRect(key: String): GeoRect? {
    val raw = this[key] as? List<*> ?: return null
    if (raw.size != 4) throw AirspaceException(
        AirspaceException.Kind.MANIFEST_INVALID,
        "$key 必须是 [minLng, minLat, maxLng, maxLat]",
    )
    val values = raw.map { (it as? Double) ?: throw AirspaceException(
        AirspaceException.Kind.MANIFEST_INVALID, "$key 含非数字项",
    ) }
    val rect = GeoRect(minLat = values[1], minLng = values[0], maxLat = values[3], maxLng = values[2])
    if (!rect.isValid) throw AirspaceException(
        AirspaceException.Kind.MANIFEST_INVALID, "$key 不是合法矩形",
    )
    return rect
}

private fun Map<String, Any?>.style(): AirspaceStyle? {
    val raw = this["style"] as? Map<*, *> ?: return null
    val map = raw.stringKeyMap()
    val fill = map.color("fill") ?: return null
    val stroke = map.color("stroke") ?: return null
    val width = (map["strokeWidth"] as? Double)?.toFloat() ?: 2f
    if (width <= 0f || width > 20f) throw AirspaceException(
        AirspaceException.Kind.MANIFEST_INVALID, "style.strokeWidth 超出 0..20",
    )
    return AirspaceStyle(fillColor = fill, strokeColor = stroke, strokeWidthDp = width)
}

/** 支持 "#RRGGBB" 与 "#AARRGGBB" 两种写法（与生成器保持一致） */
private fun Map<String, Any?>.color(key: String): Int? {
    val text = this[key] as? String ?: return null
    val hex = text.removePrefix("#")
    if (hex.length != 6 && hex.length != 8) throw AirspaceException(
        AirspaceException.Kind.MANIFEST_INVALID, "style.$key 颜色格式非法：$text",
    )
    val value = hex.toLongOrNull(16) ?: throw AirspaceException(
        AirspaceException.Kind.MANIFEST_INVALID, "style.$key 颜色格式非法：$text",
    )
    return if (hex.length == 6) (0xFF000000L or value).toInt() else value.toInt()
}
