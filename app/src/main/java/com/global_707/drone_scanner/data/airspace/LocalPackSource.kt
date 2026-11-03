package com.global_707.drone_scanner.data.airspace

import android.content.Context

/**
 * 本地离线包数据源：某个图层 + 某个数据包。
 *
 * 读取路径由 [PackStore] 决定（`filesDir/airspace/packs/<packId>/`），全程无网络、无鉴权。
 */
class LocalPackSource(
    private val context: Context,
    private val pack: PackStore.Pack,
    private val catalog: AirspaceCatalog,
) : AirspaceSource {

    override val layerId: String = catalog.layerId

    override fun catalog(): AirspaceCatalog = catalog

    override fun tile(z: Int, x: Int, y: Int): ByteArray? =
        PackStore.tileBytes(context, pack, catalog.dir, z, x, y)
}
