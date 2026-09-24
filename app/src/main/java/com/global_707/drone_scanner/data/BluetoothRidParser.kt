package com.global_707.drone_scanner.data

import android.bluetooth.le.ScanRecord
import android.os.ParcelUuid
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Remote ID 广播解析（ASTM F3411 / ASD-STAN EN 4709-002 / GB 42590）。
 *
 * RID 消息在各承载（BLE Service Data、Wi-Fi Beacon 厂商 IE）内的字节格式是**同一套**，
 * 均为 25 字节定长消息，因此本解析器不分承载、只有一张消息类型表：
 *
 *  - 首字节：**高 4 位 = 消息类型，低 4 位 = 协议版本**
 *    （v0 ASTM F3411-19 / v1 ASD-STAN prEN 4709-002、GB C-RID / v2 ASTM F3411-22）
 *  - 单条消息类型：0x0 Basic ID、0x1 Location/Vector、0x4 System、0x5 Operator ID；
 *    0x2 Authentication 与 0x3 Self-ID 本 App 无对应展示字段，跳过
 *  - Message Pack（0xF）：[0]=0xF? [1]=SingleMessageSize(恒 25) [2]=MsgPackSize(1..9)
 *    [3..]=N×25 字节子消息
 *
 * 多字节字段均为小端序。同一设备会分帧广播不同消息类型，由调用方通过 [RidMessage] 合并。
 */
object BluetoothRidParser {

    /**
     * 承载 RID 消息的 16 位 Service UUID。
     * 0xFFFA/0xFFFB 为 OpenDroneID 定义的 BLE 广播服务，0xFFA0 为 ASTM F3411-22 使用的
     * 服务 UUID，0xFFFC–0xFFFF 为部分厂商沿用；四者内部消息格式相同，故统一按一套规则解析。
     */
    val RID_SERVICE_UUIDS = listOf(0xFFA0, 0xFFFA, 0xFFFB, 0xFFFC, 0xFFFD, 0xFFFE, 0xFFFF)

    /**
     * 预构建的 ParcelUuid 列表，避免 BLE 高频回调里反复字符串 format + parse 产生垃圾。
     * 使用 lazy：纯解析单测不触碰 Android 类型（单测环境下 ParcelUuid 是空实现会抛异常）。
     */
    private val RID_PARCEL_UUIDS: List<ParcelUuid> by lazy {
        RID_SERVICE_UUIDS.map { ParcelUuid.fromString("0000%04X-0000-1000-8000-00805F9B34FB".format(it)) }
    }

    /** 定长消息长度 */
    private const val MESSAGE_SIZE = 25

    /** 单个 Message Pack 最多容纳的子消息条数 */
    private const val MAX_PACK_MESSAGES = 9

    /** 单个 Message Pack 内 Basic ID 的最大条数（其余消息类型各最多 1 条） */
    private const val MAX_PACK_BASIC_IDS = 2

    // 消息类型（首字节高 4 位）
    private const val MSG_BASIC_ID = 0x0
    private const val MSG_LOCATION = 0x1
    private const val MSG_SYSTEM = 0x4
    private const val MSG_OPERATOR_ID = 0x5
    private const val MSG_PACK = 0xF

    // Location 消息 byte1 内的位（低 4 位）
    private const val FLAG_SPEED_MULT = 0x01
    private const val FLAG_EW_DIRECTION = 0x02

    /** 水平速度无符号无效值 */
    private const val INV_SPEED_H = 0xFF

    /** 垂直速度（有符号）有效编码范围，±63 为无效 */
    private const val MIN_SPEED_V_RAW = -62
    private const val MAX_SPEED_V_RAW = 62

    /** 高度的合理性上限（m）：0xFFFF 会解出 31767.5m，超限一律视为未知 */
    private const val MAX_ALTITUDE_M = 10_000f

    /** 解析出的 RID 字段（合并多次广播 / 多个子消息的结果） */
    data class RidMessage(
        val serialNumber: String? = null,
        val idType: Int? = null,
        val protocolVersion: Int? = null,
        /** 位置消息计数器（BLE 25 字节消息内无此字段，仅 Wi-Fi Beacon 厂商 IE 头部携带） */
        val messageCounter: Int? = null,
        /** Location 消息的 Status 字段（0 未声明 / 1 地面 / 2 空中 / 3 紧急 / 4 RID 系统故障） */
        val statusFlags: Int? = null,
        val directionDeg: Float? = null,
        val speedMs: Float? = null,
        val verticalSpeedMs: Float? = null,
        val latitude: Double? = null,
        val longitude: Double? = null,
        val pressureAltitudeM: Float? = null,
        val geodeticAltitudeM: Float? = null,
        val heightAboveTakeoffM: Float? = null,
        val ridTimestampSeconds: Float? = null,
        val operatorId: String? = null,
        val operatorLatitude: Double? = null,
        val operatorLongitude: Double? = null,
    ) {
        /** 合并另一条消息的字段（非 null 优先） */
        fun merge(other: RidMessage): RidMessage = RidMessage(
            serialNumber = other.serialNumber ?: serialNumber,
            idType = other.idType ?: idType,
            protocolVersion = other.protocolVersion ?: protocolVersion,
            messageCounter = other.messageCounter ?: messageCounter,
            statusFlags = other.statusFlags ?: statusFlags,
            directionDeg = other.directionDeg ?: directionDeg,
            speedMs = other.speedMs ?: speedMs,
            verticalSpeedMs = other.verticalSpeedMs ?: verticalSpeedMs,
            latitude = other.latitude ?: latitude,
            longitude = other.longitude ?: longitude,
            pressureAltitudeM = other.pressureAltitudeM ?: pressureAltitudeM,
            geodeticAltitudeM = other.geodeticAltitudeM ?: geodeticAltitudeM,
            heightAboveTakeoffM = other.heightAboveTakeoffM ?: heightAboveTakeoffM,
            ridTimestampSeconds = other.ridTimestampSeconds ?: ridTimestampSeconds,
            operatorId = other.operatorId ?: operatorId,
            operatorLatitude = other.operatorLatitude ?: operatorLatitude,
            operatorLongitude = other.operatorLongitude ?: operatorLongitude,
        )
    }

    /**
     * 从 ScanRecord 的 Service Data 中提取并合并 RID 消息；非 RID 广播返回 null。
     * 各 RID Service UUID 使用同一套消息格式，逐个取出载荷解析后合并。
     */
    fun parse(record: ScanRecord): RidMessage? {
        var merged: RidMessage? = null
        for (uuid in RID_PARCEL_UUIDS) {
            val data = record.getServiceData(uuid) ?: continue
            parseMessages(data).forEach { msg ->
                merged = merged?.merge(msg) ?: msg
            }
        }
        return merged
    }

    /**
     * 取出广播中所有 RID Service Data 原始字节。
     * 诊断用：识别出 RID 承载但解不出消息时打印 hex 核对协议布局（与 [parse] 共用同一份 UUID 列表）。
     */
    fun rawServiceData(record: ScanRecord): List<ByteArray> =
        RID_PARCEL_UUIDS.mapNotNull { record.getServiceData(it) }

    /**
     * 解析一段 RID 载荷（单条 25 字节消息或 Message Pack），返回其中全部可解析消息。
     * 仅依赖字节数组，不触碰 Android 类型，便于单测与外部数据源复用。
     */
    fun parseMessages(data: ByteArray): List<RidMessage> {
        if (data.size < 2) return emptyList()
        return when (messageTypeOf(data[0])) {
            MSG_PACK -> parseMessagePack(data)
            else -> listOfNotNull(parseSingle(data))
        }
    }

    /**
     * Message Pack：
     * [0] msgType(高4)+version(低4)  [1] SingleMessageSize（必须为 25）
     * [2] MsgPackSize（1..9）  [3..] N×25 字节子消息。
     *
     * 子消息条数超限（Basic ID > 2、Location/System/Operator ID > 1）时整包视为无效丢弃，
     * 避免随机广播噪声被解成多台设备（对应 OpenDroneID 参考实现的 checkPackContent）。
     */
    private fun parseMessagePack(data: ByteArray): List<RidMessage> {
        if (data.size < 3) return emptyList()
        // byte1 是单条消息长度，不等于 25 说明并非本格式
        if ((data[1].toInt() and 0xFF) != MESSAGE_SIZE) return emptyList()
        val count = data[2].toInt() and 0xFF
        if (count !in 1..MAX_PACK_MESSAGES) return emptyList()
        if (3 + count * MESSAGE_SIZE > data.size) return emptyList()

        val chunks = ArrayList<ByteArray>(count)
        for (i in 0 until count) {
            val start = 3 + i * MESSAGE_SIZE
            chunks.add(data.copyOfRange(start, start + MESSAGE_SIZE))
        }

        val types = chunks.map { messageTypeOf(it[0]) }
        if (types.count { it == MSG_BASIC_ID } > MAX_PACK_BASIC_IDS) return emptyList()
        if (types.count { it == MSG_LOCATION } > 1 ||
            types.count { it == MSG_SYSTEM } > 1 ||
            types.count { it == MSG_OPERATOR_ID } > 1
        ) {
            return emptyList()
        }

        return chunks.mapNotNull { parseSingle(it) }
    }

    /** 按首字节高 4 位分派单条 25 字节消息 */
    private fun parseSingle(data: ByteArray): RidMessage? {
        if (data.size < 2) return null
        return when (messageTypeOf(data[0])) {
            MSG_BASIC_ID -> parseBasicId(data)
            MSG_LOCATION -> parseLocation(data)
            MSG_SYSTEM -> parseSystem(data)
            MSG_OPERATOR_ID -> parseOperatorId(data)
            else -> null
        }
    }

    /**
     * Basic ID：
     * [0] msgType(高4)+version(低4)  [1] idType(高4)+uaType(低4)  [2..21] UASID(20, ASCII)
     * [22..24] 保留。
     * idType：1=序列号 2=注册号 3=UTM 分配 4=会话 ID。
     */
    private fun parseBasicId(data: ByteArray): RidMessage? {
        if (data.size < 22) return null
        val id = decodeId(data, 2, 20) ?: return null
        return RidMessage(
            serialNumber = id,
            idType = (data[1].toInt() ushr 4) and 0x0F,
            protocolVersion = protocolVersionOf(data[0]),
        )
    }

    /**
     * Location/Vector（25 字节，小端序）：
     * [0] msgType(高4)+version(低4)
     * [1] status(高4) | Reserved(1) | HeightType(1) | EWDirection(1) | SpeedMult(低1)
     * [2] 航向（1° 步进；EWDirection=1 时真实航向 = 编码值 + 180°）
     * [3] 水平速度（0.25 m/s 步进；SpeedMult=1 时改用高段量化 ×0.75 + 63.75）
     * [4] 垂直速度（有符号，0.5 m/s 步进）
     * [5..8] 纬度 / [9..12] 经度（int32，1e-7°）
     * [13..14] 气压高度 / [15..16] 大地高度 / [17..18] 相对高度（u16，0.5m 步进 −1000m 偏移）
     * [19] 垂直精度(高4)+水平精度(低4)  [20] 气压精度(高4)+速度精度(低4)
     * [21..22] 时间戳（u16，0.1s，0xFFFF 未知）  [23] 时间戳精度  [24] 保留
     *
     * 注：相对高度的基准（起飞点/地面）由 HeightType 位区分，本 App 目前合并展示。
     */
    private fun parseLocation(data: ByteArray): RidMessage? {
        if (data.size < MESSAGE_SIZE) return null
        val flags = data[1].toInt() and 0xFF
        val buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)

        // 航向：EWDirection 位为 1 表示真实航向 = 编码值 + 180°
        val directionRaw = data[2].toInt() and 0xFF
        val directionDeg = (if (flags and FLAG_EW_DIRECTION != 0) directionRaw + 180 else directionRaw)
            .takeIf { it <= 360 }
            ?.toFloat()

        // 水平速度：SpeedMult 位为 1 时使用高段量化（0.75 m/s 步进 + 63.75 m/s 偏移）
        val speedRaw = data[3].toInt() and 0xFF
        val speedMs = when {
            speedRaw == INV_SPEED_H -> null
            flags and FLAG_SPEED_MULT != 0 -> speedRaw * 0.75f + 255 * 0.25f
            else -> speedRaw * 0.25f
        }

        val verticalSpeedRaw = data[4].toInt() // 有符号
        val verticalSpeedMs = verticalSpeedRaw
            .takeIf { it in MIN_SPEED_V_RAW..MAX_SPEED_V_RAW }
            ?.let { it * 0.5f }

        buf.position(5)
        val position = resolveLatLon(int32OrNull(buf.int), int32OrNull(buf.int))
        val pressureAltitudeM = decodeAltitude(uint16OrNull(buf))
        val geodeticAltitudeM = decodeAltitude(uint16OrNull(buf))
        val heightAboveTakeoffM = decodeAltitude(uint16OrNull(buf))
        buf.position(21)
        val timestampRaw = uint16OrNull(buf)

        return RidMessage(
            protocolVersion = protocolVersionOf(data[0]),
            statusFlags = (flags ushr 4) and 0x0F,
            directionDeg = directionDeg,
            speedMs = speedMs,
            verticalSpeedMs = verticalSpeedMs,
            latitude = position?.first,
            longitude = position?.second,
            pressureAltitudeM = pressureAltitudeM,
            geodeticAltitudeM = geodeticAltitudeM,
            heightAboveTakeoffM = heightAboveTakeoffM,
            ridTimestampSeconds = timestampRaw?.let { it * 0.1f },
        )
    }

    /**
     * System：
     * [0] msgType(高4)+version(低4)
     * [1] Reserved(高3) | ClassificationType(3 位) | OperatorLocationType(低2)
     * [2..5] 操作员纬度 / [6..9] 操作员经度（int32，1e-7°）
     * [10..11] 区域数量  [12] 区域半径(×10m)  [13..14] 区域上限  [15..16] 区域下限
     * [17] EU 等级(高4)+类别(低4)  [18..19] 操作员大地高度  [20..23] 系统时间戳  [24] 保留
     *
     * 本 App 目前只取操作员位置。
     */
    private fun parseSystem(data: ByteArray): RidMessage? {
        if (data.size < 10) return null
        val buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        buf.position(2)
        val position = resolveLatLon(int32OrNull(buf.int), int32OrNull(buf.int)) ?: return null
        return RidMessage(
            protocolVersion = protocolVersionOf(data[0]),
            operatorLatitude = position.first,
            operatorLongitude = position.second,
        )
    }

    /**
     * Operator ID：
     * [0] msgType(高4)+version(低4)  [1] operatorIdType  [2..21] 操作员 ID(20, ASCII)
     * [22..24] 保留。
     */
    private fun parseOperatorId(data: ByteArray): RidMessage? {
        if (data.size < 22) return null
        val id = decodeId(data, 2, 20) ?: return null
        return RidMessage(
            operatorId = id,
            protocolVersion = protocolVersionOf(data[0]),
        )
    }

    /** 首字节高 4 位为消息类型 */
    private fun messageTypeOf(byte: Byte): Int = (byte.toInt() ushr 4) and 0x0F

    /** 首字节低 4 位为协议版本 */
    private fun protocolVersionOf(byte: Byte): Int = byte.toInt() and 0x0F

    /**
     * 经纬度解析：0x80000000 表示未知；原始值为 (0,0) 视为"未提供定位"，
     * 避免在地图上把设备画到几内亚湾；越界坐标一律丢弃。
     */
    private fun resolveLatLon(latRaw: Int?, lonRaw: Int?): Pair<Double, Double>? {
        if (latRaw == null || lonRaw == null) return null
        if (latRaw == 0 && lonRaw == 0) return null
        val lat = latRaw / 1e7
        val lon = lonRaw / 1e7
        if (lat !in -90.0..90.0 || lon !in -180.0..180.0) return null
        return lat to lon
    }

    /**
     * 高度解码：0.5m 步进 −1000m 偏移。编码值 0 表示未知；解码结果超过合理上限
     * （0xFFFF 会解出 31767.5m）同样视为未知，避免垃圾载荷解出荒谬值进入 UI。
     */
    private fun decodeAltitude(raw: Int?): Float? {
        if (raw == null || raw == 0) return null
        return (raw * 0.5f - 1000f).takeIf { it <= MAX_ALTITUDE_M }
    }

    /** 解码定长 ID 字符串（去掉尾部空字节/空白；全空返回 null） */
    private fun decodeId(data: ByteArray, offset: Int, length: Int): String? {
        if (offset + length > data.size) return null
        val end = (offset until offset + length).firstOrNull { data[it].toInt() == 0 }
            ?: (offset + length)
        val text = String(data, offset, end - offset, Charsets.US_ASCII).trim()
        return text.ifEmpty { null }
    }

    /** 0xFFFF 表示未知 */
    private fun uint16OrNull(buf: ByteBuffer): Int? {
        val raw = buf.short.toInt() and 0xFFFF
        return if (raw == 0xFFFF) null else raw
    }

    /** 0x80000000 表示未知 */
    private fun int32OrNull(raw: Int): Int? = if (raw == Int.MIN_VALUE) null else raw
}
