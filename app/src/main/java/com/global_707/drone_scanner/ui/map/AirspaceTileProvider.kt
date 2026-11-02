package com.global_707.drone_scanner.ui.map

import com.global_707.drone_scanner.data.airspace.AirspaceCatalog
import com.global_707.drone_scanner.data.airspace.AirspaceRepository
import com.global_707.drone_scanner.data.airspace.AirspaceSource
import com.global_707.drone_scanner.data.airspace.TileCrs
import com.global_707.drone_scanner.util.TileMath
import com.tencent.tencentmap.mapsdk.maps.model.Tile
import com.tencent.tencentmap.mapsdk.maps.model.TileProvider

/**
 * 空域图层的瓦片提供者（腾讯 `TileProvider` 实现）。
 *
 * 坐标系：
 *  - 包内瓦片已按 **GCJ-02 网格**出图（manifest `crs = GCJ02`）→ 瓦片号直接透传，零偏移；
 *  - 包内瓦片是 **WGS-84 网格**（兼容旧包）→ 用 [TileMath.gcjTileToWgsTile] 换算瓦片号，
 *    残余偏移约等于 GCJ 偏移（z13 约 30 px）。精确边界请让生成器按 GCJ 网格出图。
 *
 * 线程：腾讯 SDK 在其瓦片线程调用 [getTile]，这里只查内存缓存 / 读本地文件，不做网络。
 */
class AirspaceTileProvider(
    private val source: AirspaceSource,
    private val catalog: AirspaceCatalog,
) : TileProvider {

    override fun getTile(x: Int, y: Int, zoom: Int): Tile {
        val (sourceX, sourceY) = sourceTileIndex(zoom, x, y)
        val bytes = AirspaceRepository.tileNow(source, zoom, sourceX, sourceY)
            ?: return TileProvider.NO_TILE
        if (!isPng(bytes)) return TileProvider.NO_TILE
        return Tile(catalog.tileSize, catalog.tileSize, bytes)
    }

    /** 地图网格瓦片号 → 数据包网格瓦片号 */
    private fun sourceTileIndex(zoom: Int, x: Int, y: Int): Pair<Int, Int> =
        when (catalog.tileCrs) {
            TileCrs.GCJ02 -> x to y
            TileCrs.WGS84 -> TileMath.gcjTileToWgsTile(zoom, x, y)
        }

    private fun isPng(bytes: ByteArray): Boolean =
        bytes.size > 8 &&
            bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() &&
            bytes[2] == 0x4E.toByte() && bytes[3] == 0x47.toByte()
}
