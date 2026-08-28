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

/**
 * 扫描状态模型。
 * [bluetoothErrorCode] / [wifiErrorCode]：扫描失败的错误码，
 * 负数表示环境问题（-1 设备不支持、-2 系统开关未开启），正数为系统扫描回调错误码。
 */
data class ScanState(
    val isScanning: Boolean,
    val wifiScanning: Boolean,
    val bluetoothScanning: Boolean,
    val bluetoothErrorCode: Int? = null,
    val wifiErrorCode: Int? = null,
) {
    companion object {
        const val BT_ERR_UNAVAILABLE = -1
        const val BT_ERR_DISABLED = -2
        const val WIFI_ERR_UNAVAILABLE = -1
    }

    /** 是否存在需要提示的扫描异常 */
    val hasError: Boolean
        get() = bluetoothErrorCode != null || wifiErrorCode != null
}

/**
 * 无人机数据模型。
 *
 * 所有字段均来自真实 RID（ASTM F3411）蓝牙/Wi-Fi 信号解包；
 * 未解析到的字段为 null（界面显示"—"），严禁硬编码或杜撰参数。
 */
data class Drone(
    /** 设备键（蓝牙 MAC / WiFi BSSID），用于 ID Hash 取色 */
    val id: String,
    /** 呼号：序列号后四位（无序列号时用设备 id 后四位） */
    val name: String,

    // ---- Basic ID 消息 ----
    /** 序列号（UDI） */
    val snCode: String?,
    /** ID 类型：1=序列号 2=CCA 3=UTM 4=会话ID 5=注册号 */
    val idType: Int?,
    /** 协议版本 */
    val protocolVersion: Int?,

    // ---- Location/Vector 消息 ----
    /** 消息计数 */
    val messageCounter: Int?,
    /** 位置状态标志位 */
    val statusFlags: Int?,
    /** 航向（度） */
    val directionDeg: Float?,
    /** 水平速度（m/s） */
    val speedMs: Float?,
    /** 垂直速度（m/s） */
    val verticalSpeedMs: Float?,
    /** 气压高度（m） */
    val pressureAltitudeM: Float?,
    /** 地理（GNSS）高度（m） */
    val geodeticAltitudeM: Float?,
    /** 对起飞点高度（m） */
    val heightAboveTakeoffM: Float?,
    /** RID 时间戳（秒，单位 0.1s） */
    val ridTimestampSeconds: Float?,

    // ---- 位置 ----
    /** 无人机纬度（未知为 null） */
    val droneLat: Double?,
    /** 无人机经度（未知为 null） */
    val droneLng: Double?,
    /** 估计距离（米，由本机位置计算，未知为 null） */
    val distanceM: Float?,
    /** 遥控站位置 */
    val operatorLat: Double?,
    val operatorLng: Double?,
    /** 操作员 ID */
    val operatorId: String?,
    /** 遥控站与无人机距离（米，未知为 null） */
    val operatorDistanceM: Int?,

    // ---- 信号 ----
    /** 信号强度 0..4 */
    val signalStrength: Int,
    /** 协议：Bluetooth RID / WiFi RID */
    val protocol: String,
    /** 频率：2.4 GHz / 5.8 GHz */
    val frequency: String,

    /** 最后发现距今秒数 */
    val lastSeenSeconds: Int,
    val status: DroneStatus,
)
