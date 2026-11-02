package com.global_707.drone_scanner.data.airspace

import android.content.Context

/**
 * 本地离线包数据源（当前唯一实现）。
 *
 * 读取路径由 [AirspacePack] 决定：用户导入的包读 `filesDir/airspace/`，内置包读 `assets/airspace/`。
 * 全程无网络、无鉴权。
 */
class LocalPackSource(
    private val context: Context,
    private val installed: AirspacePack.Installed,
    private val catalog: AirspaceCatalog,
) : AirspaceSource {

    override val layerId: String = catalog.layerId

    override fun catalog(): AirspaceCatalog = catalog

    override fun tile(z: Int, x: Int, y: Int): ByteArray? =
        AirspacePack.openTile(context, installed, catalog.dir, z, x, y)
}
