package com.global_707.drone_scanner.ui.map

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue

/**
 * 地图相机状态（全局可观察）。
 *
 * 腾讯地图的 `setOnCameraChangeListener` 是单 setter，**只能由 [TencentMapBackend] 注册一次**，
 * 然后把回调分发到这里（以及 `TencentAirspaceLayer`）。指南针、图层缩放判断等都从这里读，
 * 不要各自再去 set 监听（否则后设置的会把先设置的覆盖掉）。
 */
object MapCameraState {

    /** 地图旋转角（0 = 正北朝上，顺时针为正） */
    var bearing: Float by mutableFloatStateOf(0f)
        private set

    /** 当前缩放级别（腾讯地图为浮点，整数部分即瓦片级别） */
    var zoom: Float by mutableFloatStateOf(16f)
        private set

    fun update(newBearing: Float, newZoom: Float) {
        bearing = normalize(newBearing)
        zoom = newZoom
    }

    fun resetBearing() {
        bearing = 0f
    }

    private fun normalize(degree: Float): Float {
        var value = degree % 360f
        if (value < 0f) value += 360f
        return value
    }
}
