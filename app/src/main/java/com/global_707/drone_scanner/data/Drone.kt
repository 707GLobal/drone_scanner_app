package com.global_707.drone_scanner.data

/** 无人机状态（参考设计文档第 12.1 节） */
enum class DroneStatus {
    /** 安全 / 在线（success 绿） */
    SAFE,

    /** 警告（warning 橙） */
    WARNING,

    /** 危险 / 未知（danger 红） */
    DANGER,
}

/** 无人机数据模型（参考设计文档第 12.1 节） */
data class Drone(
    val id: String,
    val name: String,
    val snCode: String,
    val ridCode: String,
    /** 高度（米），未知为 null */
    val heightM: Float?,
    /** 距离（米），未知为 null */
    val distanceM: Float?,
    /** 速度（米/秒），未知为 null */
    val speedMs: Float?,
    /** 信号强度 0..4 */
    val signalStrength: Int,
    val protocol: String,
    val frequency: String,
    val droneLat: Double,
    val droneLng: Double,
    val operatorLat: Double?,
    val operatorLng: Double?,
    val operatorId: String?,
    val operatorPhone: String?,
    /** 飞手距离（米），未知为 null */
    val operatorDistanceM: Int?,
    val lastSeenSeconds: Int,
    val status: DroneStatus,
)

/** 扫描状态模型（参考设计文档第 12.2 节） */
data class ScanState(
    val isScanning: Boolean,
    val wifiScanning: Boolean,
    val bluetoothScanning: Boolean,
)
