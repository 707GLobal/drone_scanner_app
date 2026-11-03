package com.global_707.drone_scanner.ui.map

import com.global_707.drone_scanner.data.airspace.AirspaceCatalog
import com.global_707.drone_scanner.data.airspace.AirspaceRepository
import com.global_707.drone_scanner.data.airspace.AirspaceSource
import com.global_707.drone_scanner.data.airspace.TileCrs
import com.global_707.drone_scanner.util.TileMath
import com.tencent.tencentmap.mapsdk.maps.model.Tile
import com.tencent.tencentmap.mapsdk.maps.model.TileProvider

/**
 * 空域图层瓦片提供者：**任何缩放级别都出图**。
 *
 * 取值优先级：
 *  1. `zoom < 原生最小级别` → 无数据（正常包会覆盖到 z3，基本不会走到）；
 *  2. `zoom <= 原生最大级别`（或包内已烘焙该级别）→ 直接读文件；
 *  3. `zoom > 原生最大级别` → 先看包内是否烘焙了该级别（生成器默认烘焙 zN+1、zN+2），
 *     否则交给 [TileOverzoom] 用父瓦片裁切放大（不闪断、不卡顿）。
 *
 * 坐标：`GCJ02` 包恒等映射（腾讯网格 = 包网格，零偏移、缩放不跳动）；
 * `WGS84` 旧包做瓦片号换算（会出现随缩放变化的偏移，仅兼容）。
 */
class AirspaceTileProvider(
    private val source: AirspaceSource,
    private val catalog: AirspaceCatalog,
) : TileProvider {

    private val nativeMin = catalog.zooms.first
    private val nativeMax = catalog.zooms.last

    override fun getTile(x: Int, y: Int, zoom: Int): Tile {
        val bytes = when {
            catalog.tileCrs == TileCrs.WGS84 -> {
                if (zoom !in nativeMin..nativeMax) return TileProvider.NO_TILE
                val (srcX, srcY) = TileMath.gcjTileToWgsTile(zoom, x, y)
                AirspaceRepository.tileNow(source, zoom, srcX, srcY)
            }

            zoom < nativeMin -> return TileProvider.NO_TILE

            zoom <= nativeMax -> AirspaceRepository.tileNow(source, zoom, x, y)
                // 生成器烘焙的 zN+1/zN+2 直接命中；
                // 再高就走运行期放大（包内没有该级别文件时才触发）
                ?: TileOverzoom.overzoom(source, nativeMax, zoom, x, y, catalog.tileSize)

            else -> AirspaceRepository.tileNow(source, zoom, x, y)
                ?: TileOverzoom.overzoom(source, nativeMax, zoom, x, y, catalog.tileSize)
        } ?: return TileProvider.NO_TILE

        if (!isPng(bytes)) return TileProvider.NO_TILE
        return Tile(catalog.tileSize, catalog.tileSize, bytes)
    }

    private fun isPng(bytes: ByteArray): Boolean =
        bytes.size > 8 &&
            bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() &&
            bytes[2] == 0x4E.toByte() && bytes[3] == 0x47.toByte()
}
