package com.global_707.drone_scanner.data.airspace

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** 单个叠加层的显示状态 */
enum class AirspaceLayerStatus {
    /** 图层关闭 */
    OFF,

    /** 数据就绪且当前缩放在可用范围内 */
    READY,

    /** 当前缩放不在数据包的级别范围内（图层不显示） */
    ZOOM_OUT,

    /** 未安装数据包 */
    UNAVAILABLE,

    /** 数据包损坏 / 读取失败 */
    ERROR,
}

/**
 * 空域图层的全局可观察状态（沿用项目既有模式：单例 + mutableStateOf，无 DI、无 ViewModel）。
 *
 * 写入方：`AirspaceRepository`（安装态/目录）与 `TencentAirspaceLayer`（缩放态）。
 * 读取方：图层菜单、显示设置页。除这两个写入方外不要在别处修改。
 */
object AirspaceState {

    /** 已安装图层的元信息，key = layerId；空表示未安装 */
    var catalogs: Map<String, AirspaceCatalog> by mutableStateOf(emptyMap())
        private set

    /** 每个注册图层的当前状态 */
    var statuses: Map<String, AirspaceLayerStatus> by mutableStateOf(emptyMap())
        private set

    /** 最近一次读取失败的原因（给菜单/设置页展示，不含技术细节） */
    var errorMessage: String? by mutableStateOf(null)
        private set

    val installed: Boolean get() = catalogs.isNotEmpty()

    /** 数据版本（多层时取第一个，仅用于展示） */
    val version: String? get() = catalogs.values.firstOrNull()?.version

    val source: AirspaceSourceKind? get() = catalogs.values.firstOrNull()?.source

    /** 发布一次读取结果（仓库调用） */
    fun publish(catalogs: Map<String, AirspaceCatalog>, errorMessage: String?) {
        this.catalogs = catalogs
        this.errorMessage = errorMessage
        statuses = buildMap {
            AirspaceLayers.all.forEach { spec ->
                val previous = statuses[spec.id]
                put(
                    spec.id,
                    when {
                        errorMessage != null -> AirspaceLayerStatus.ERROR
                        !catalogs.containsKey(spec.id) -> AirspaceLayerStatus.UNAVAILABLE
                        // 刷新安装态时保留渲染层给出的缩放态，避免菜单状态跳回"正常"
                        previous == AirspaceLayerStatus.READY ||
                            previous == AirspaceLayerStatus.ZOOM_OUT -> previous
                        else -> AirspaceLayerStatus.READY
                    },
                )
            }
        }
    }

    /** 更新单个图层的缩放态（渲染层调用） */
    fun setStatus(layerId: String, status: AirspaceLayerStatus) {
        if (statuses[layerId] == status) return
        statuses = statuses + (layerId to status)
    }

    fun statusOf(layerId: String): AirspaceLayerStatus =
        statuses[layerId] ?: AirspaceLayerStatus.UNAVAILABLE

    fun catalogOf(layerId: String): AirspaceCatalog? = catalogs[layerId]
}
