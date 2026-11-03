package com.global_707.drone_scanner.data.airspace

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 空域图层的全局可观察状态（沿用项目既有模式：单例 + mutableStateOf，无 DI、无 ViewModel）。
 *
 * 只保存"每个图层当前用哪份数据"（[catalogs]）与最近一次读取失败原因；
 * 图层的开关状态在 [com.global_707.drone_scanner.data.AppPrefs.airspaceVisible]，
 * 菜单/设置页的展示状态由二者组合推导（不再单独维护 ZOOM_OUT 之类的中间状态）。
 *
 * 写入方：`AirspaceRepository`（刷新安装态/选包后调用 [publish]）。
 * 读取方：图层菜单、图层设置页、地图渲染层。
 */
object AirspaceState {

    /** layerId → 当前数据（来自某个离线包，或演示模式下的演示数据）；空表示没有可用数据 */
    var catalogs: Map<String, AirspaceCatalog> by mutableStateOf(emptyMap())
        private set

    /** 最近一次读取失败的原因（给设置页/日志用，不含技术细节） */
    var errorMessage: String? by mutableStateOf(null)
        private set

    /** 已导入的全部数据包（供「图层设置」页展示与选择） */
    var packs: List<PackSummary> by mutableStateOf(emptyList())
        private set

    val hasAnyData: Boolean get() = catalogs.isNotEmpty()

    fun catalogOf(layerId: String): AirspaceCatalog? = catalogs[layerId]

    fun publish(catalogs: Map<String, AirspaceCatalog>, packs: List<PackSummary>, errorMessage: String?) {
        this.catalogs = catalogs
        this.packs = packs
        this.errorMessage = errorMessage
    }

    fun clear() {
        catalogs = emptyMap()
        packs = emptyList()
        errorMessage = null
    }
}
