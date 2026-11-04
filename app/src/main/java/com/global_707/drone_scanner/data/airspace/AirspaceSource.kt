package com.global_707.drone_scanner.data.airspace

import com.global_707.drone_scanner.util.GeoRect

/**
 * 空域图层数据源接口（**预留接口**：目前只有本地离线包实现，将来可加矢量包或网关实现）。
 *
 * 实现约定：
 *  - 只返回 **WGS-84** 坐标 / 原始 PNG 字节，不做任何画到地图上的换算；
 *  - [tile] 必须是**同步**读取（腾讯 `TileProvider` 的调用是同步的），因此本地实现直接读文件，
 *    将来的在线实现需要自己先做预取缓存；
 *  - 取不到数据返回 `null` / 空表，不要用异常表达"这块没有数据"。
 */
interface AirspaceSource {

    val layerId: String

    /** 图层元信息（manifest 解析结果，已在仓库层缓存） */
    fun catalog(): AirspaceCatalog?

    /** 读取一张瓦片（PNG 字节）；null = 该瓦片没有数据 */
    fun tile(z: Int, x: Int, y: Int): ByteArray?

    /**
     * 视口内的矢量要素（WGS-84）；栅格包不提供，返回空表即可。
     *
     * **必须同步返回**：矢量绘制是"清空再画"的原子过程，异步返回会让两次同步交错，
     * 出现重复叠色/漏画（1.1.5 演示空域中心块显示不一致就是这么来的）。
     */
    fun features(viewport: GeoRect): List<AirspaceFeature> = emptyList()
}
