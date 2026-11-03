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

    /** 地图旋转角（SDK 原始值，0 = 正北朝上；仅用于诊断/日志，不要直接拿来转指针） */
    var bearing: Float by mutableFloatStateOf(0f)
        private set

    /** 当前缩放级别（腾讯地图为浮点，整数部分即瓦片级别） */
    var zoom: Float by mutableFloatStateOf(16f)
        private set

    /**
     * **屏幕坐标系里"正北"的方向角**（度，0 = 正上方，顺时针为正）。
     *
     * 不用 `bearing` 反推：不同地图 SDK 对 bearing 正负/含义的定义不一致（腾讯实测与"西式"
     * 定义相反），直接取反会转反。这里由 [TencentMapBackend] 用投影把"中心点"和"正北 100m 处的点"
     * 都投到屏幕上求出方向角，任何旋转/倾斜/投影形变下都正确。
     */
    var northAngle: Float by mutableFloatStateOf(0f)
        private set

    fun update(newBearing: Float, newZoom: Float) {
        bearing = normalize(newBearing)
        zoom = newZoom
    }

    /** [angle] 为屏幕坐标系里正北的方向角（0 = 上，顺时针为正） */
    fun updateNorth(angle: Float) {
        northAngle = normalizeSigned(angle)
    }

    fun resetBearing() {
        bearing = 0f
        northAngle = 0f
    }

    private fun normalize(degree: Float): Float {
        var value = degree % 360f
        if (value < 0f) value += 360f
        return value
    }

    /** 归一化到 (-180, 180]，避免 359°→1° 时指针整圈回甩 */
    private fun normalizeSigned(degree: Float): Float {
        var value = degree % 360f
        if (value > 180f) value -= 360f
        if (value <= -180f) value += 360f
        return value
    }
}
