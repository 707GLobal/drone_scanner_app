package com.global_707.drone_scanner.data

import android.bluetooth.le.ScanRecord
import android.os.ParcelUuid
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 蓝牙 Remote ID 广播解析（ASTM F3411 / ASD-STAN EN 4709-002）。
 *
 * RID 通过蓝牙广播的 Service Data 字段承载：
 *  - 0xFFFC / 0xFFFD：Plaintext（单条 25 字节消息，传统广播 BT4）
 *  - 0xFFFA / 0xFFFB：Message Pack（0x0F + authType + 4×25 字节子消息，BT5 扩展广播）
 *  - 0xFFFE / 0xFFFF：BT5 Long Range（同样为 Message Pack 格式）
 * 多字节字段均为小端序。
 *
 * 蓝牙承载的消息均为 25 字节（传统广播 31 字节 payload 限制），
 * Location/Vector 消息中时间戳/精度字段仅在 Wi-Fi Beacon 承载（31 字节）中存在。
 * 同一设备会分帧广播不同消息类型，由调用方通过 [RidMessage] 合并各帧字段。
 */
object BluetoothRidParser {

    /** 承载 RID 消息的 16 位 Service UUID 列表 */
    val RID_SERVICE_UUIDS = listOf(0xFFFA, 0xFFFB, 0xFFFC, 0xFFFD, 0xFFFE, 0xFFFF)

    /** Message Pack 内子消息固定长度 */
    private const val MESSAGE_PACK_SIZE = 25

    private const val MSG_PACK = 0x0F
    private const val MSG_BASIC_ID = 0x00
    private const val MSG_BASIC_ID_OPERATOR = 0x01
    private const val MSG_LOCATION = 0x02
    private const val MSG_LOCATION_AUTH = 0x03
    private const val MSG_OPERATOR_ID = 0x06
    private const val MSG_OPERATOR_LOCATION = 0x08

    /** 解析出的 RID 字段（合并多次广播 / 多个子消息的结果） */
    data class RidMessage(
        val serialNumber: String? = null,
        val idType: Int? = null,
        val protocolVersion: Int? = null,
        val messageCounter: Int? = null,
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

    /** 从 ScanRecord 提取并合并 RID 消息；非 RID 广播返回 null */
    fun parse(record: ScanRecord): RidMessage? {
        var merged: RidMessage? = null
        for (uuid16 in RID_SERVICE_UUIDS) {
            val uuid = ParcelUuid.fromString(
                "0000%04X-0000-1000-8000-00805F9B34FB".format(uuid16)
            )
            val data = record.getServiceData(uuid) ?: continue
            parseMessages(data).forEach { msg ->
                merged = merged?.merge(msg) ?: msg
            }
        }
        return merged
    }

    /**
     * 解析一段 RID 载荷，支持：
     *  - 单条消息（0x00/0x01/0x02/0x03/0x06/0x08）
     *  - Message Pack（0x0F + authType + N×25 字节子消息）
     * 返回所有可解析消息（Message Pack 可能同时包含 Basic ID 与 Location 等）。
     */
    fun parseMessages(data: ByteArray): List<RidMessage> {
        if (data.size < 2) return emptyList()
        return when (data[0].toInt() and 0xFF) {
            MSG_PACK -> parseMessagePack(data)
            else -> listOfNotNull(parseSingle(data))
        }
    }

    private fun parseMessagePack(data: ByteArray): List<RidMessage> {
        val result = mutableListOf<RidMessage>()
        var offset = 2 // 跳过 msgType(0x0F) + authType
        while (offset + MESSAGE_PACK_SIZE <= data.size) {
            parseSingle(data.copyOfRange(offset, offset + MESSAGE_PACK_SIZE))
                ?.let { result.add(it) }
            offset += MESSAGE_PACK_SIZE
        }
        return result
    }

    private fun parseSingle(data: ByteArray): RidMessage? {
        if (data.size < 2) return null
        return when (data[0].toInt() and 0xFF) {
            MSG_BASIC_ID, MSG_BASIC_ID_OPERATOR -> parseBasicId(data)
            MSG_LOCATION, MSG_LOCATION_AUTH -> parseLocation(data)
            MSG_OPERATOR_ID -> parseOperatorId(data)
            MSG_OPERATOR_LOCATION -> parseOperatorLocation(data)
            else -> null
        }
    }

    /**
     * Basic ID：msgType(1) protoVersion(1) idType(1) id(20)（蓝牙承载共 25 字节，含 2 字节填充）
     */
    private fun parseBasicId(data: ByteArray): RidMessage? {
        if (data.size < 23) return null
        val protoVersion = data[1].toInt() and 0xFF
        val idType = data[2].toInt() and 0xFF
        val id = decodeId(data, 3, 20)
        if (id.isNullOrBlank()) return null
        return RidMessage(serialNumber = id, idType = idType, protocolVersion = protoVersion)
    }

    /**
     * Location/Vector（蓝牙承载 25 字节，小端序）：
     * msgType(1) protoVersion(1) msgCounter(1) status(1)
     * direction(2, 0.01°) speedH(2, 0.25m/s) speedV(2, 0.25m/s)
     * lat(4, 1e-7°) lon(4, 1e-7°) pressureAlt(2, 0.5m) geodeticAlt(2, 0.5m)
     * height(2, 0.5m) vertAcc(1)
     * 若载荷 ≥31 字节（Wi-Fi Beacon 承载）另含 horizAcc(1) baroAcc(1) speedAcc(1) ts(2, 0.1s) tsAcc(1)。
     * 特殊值 0xFFFF / 0x80000000 表示未知。
     */
    private fun parseLocation(data: ByteArray): RidMessage? {
        if (data.size < 25) return null
        val buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        val msgCounter = data[2].toInt() and 0xFF
        val statusFlags = data[3].toInt() and 0xFF
        buf.position(4)
        val direction = uint16OrNull(buf)
        val speedH = uint16OrNull(buf)
        val speedV = uint16OrNull(buf)
        val lat = int32OrNull(buf)?.let { it / 1e7 }?.takeIf { kotlin.math.abs(it) <= 90.0 }
        val lon = int32OrNull(buf)?.let { it / 1e7 }?.takeIf { kotlin.math.abs(it) <= 180.0 }
        val pressureAlt = uint16OrNull(buf)?.let { it * 0.5f }
        val geodeticAlt = uint16OrNull(buf)?.let { it * 0.5f }
        val height = uint16OrNull(buf)?.let { it * 0.5f }
        val ts = if (data.size >= 31) {
            buf.position(28)
            uint16OrNull(buf)
        } else {
            null
        }
        return RidMessage(
            messageCounter = msgCounter,
            statusFlags = statusFlags,
            // 航向合法范围 0..360°（0xFFFF 未知已在 uint16OrNull 过滤），其余按无效丢弃，
            // 避免垃圾载荷解出 600°+ 的乱数据直接进 UI
            directionDeg = direction?.let { it / 100f }?.takeIf { it <= 360f },
            speedMs = speedH?.let { it * 0.25f },
            verticalSpeedMs = speedV?.let { it * 0.25f },
            latitude = lat,
            longitude = lon,
            pressureAltitudeM = pressureAlt,
            geodeticAltitudeM = geodeticAlt,
            heightAboveTakeoffM = height,
            ridTimestampSeconds = ts?.let { it * 0.1f },
        )
    }

    /** Operator ID：msgType(1) protoVersion(1) operatorIdType(1) operatorId(20) */
    private fun parseOperatorId(data: ByteArray): RidMessage? {
        if (data.size < 23) return null
        val operatorId = decodeId(data, 3, 20)
        if (operatorId.isNullOrBlank()) return null
        return RidMessage(operatorId = operatorId)
    }

    /** Operator Location：msgType(1) protoVersion(1) msgCounter(1) lat(4) lon(4) alt(2) … */
    private fun parseOperatorLocation(data: ByteArray): RidMessage? {
        if (data.size < 12) return null
        val buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        buf.position(3)
        val lat = buf.int.toDouble() / 1e7
        val lon = buf.int.toDouble() / 1e7
        if (kotlin.math.abs(lat) > 90.0 || kotlin.math.abs(lon) > 180.0) return null
        return RidMessage(operatorLatitude = lat, operatorLongitude = lon)
    }

    /** 解码定长 ID 字符串（去掉尾部空字节/空白） */
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
    private fun int32OrNull(buf: ByteBuffer): Int? {
        val raw = buf.int
        return if (raw == Int.MIN_VALUE || raw == 0) null else raw
    }
}
