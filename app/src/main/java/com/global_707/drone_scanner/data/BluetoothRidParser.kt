package com.global_707.drone_scanner.data

import android.bluetooth.le.ScanRecord
import android.os.ParcelUuid
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 蓝牙 Remote ID 广播解析（ASTM F3411 / ASD-STAN EN 4709-002）。
 *
 * RID 通过蓝牙传统广播的 Service Data 字段承载，Service UUID 为：
 *  0xFFFA Message Pack / 0xFFFB Message Pack+Auth / 0xFFFC Plaintext / 0xFFFD Plaintext+Auth
 * 载荷首字节为消息类型，随后是协议版本与各字段（多字节均为小端序）。
 */
object BluetoothRidParser {

    /** 承载 RID 消息的 16 位 Service UUID 列表 */
    val RID_SERVICE_UUIDS = listOf(0xFFFA, 0xFFFB, 0xFFFC, 0xFFFD)

    /** 消息类型 */
    private const val MSG_BASIC_ID = 0x00          // Basic ID
    private const val MSG_BASIC_ID_OPERATOR = 0x01 // Basic ID + Operator ID
    private const val MSG_LOCATION = 0x02          // Location/Vector
    private const val MSG_LOCATION_AUTH = 0x03     // Location/Vector + Auth
    private const val MSG_OPERATOR_ID = 0x06       // Operator ID
    private const val MSG_OPERATOR_LOCATION = 0x08 // Operator Location

    /** 解析出的 RID 字段（合并多次广播的结果） */
    data class RidMessage(
        val serialNumber: String? = null,
        val idType: Int? = null,
        val latitude: Double? = null,
        val longitude: Double? = null,
        val speedMs: Float? = null,
        val altitudeM: Float? = null,
        val directionDeg: Float? = null,
        val operatorId: String? = null,
        val operatorLatitude: Double? = null,
        val operatorLongitude: Double? = null,
    )

    /** 从 ScanRecord 提取 RID 消息；非 RID 广播返回 null */
    fun parse(record: ScanRecord): RidMessage? {
        for (uuid16 in RID_SERVICE_UUIDS) {
            val uuid = ParcelUuid.fromString(
                "0000%04X-0000-1000-8000-00805F9B34FB".format(uuid16)
            )
            val data = record.getServiceData(uuid) ?: continue
            val msg = parsePayload(data) ?: continue
            return msg
        }
        return null
    }

    private fun parsePayload(data: ByteArray): RidMessage? {
        if (data.size < 2) return null
        val msgType = data[0].toInt() and 0xFF
        return when (msgType) {
            MSG_BASIC_ID -> parseBasicId(data)
            MSG_BASIC_ID_OPERATOR -> parseBasicId(data)
            MSG_LOCATION, MSG_LOCATION_AUTH -> parseLocation(data)
            MSG_OPERATOR_ID -> parseOperatorId(data)
            MSG_OPERATOR_LOCATION -> parseOperatorLocation(data)
            else -> null
        }
    }

    /** Basic ID：msgType(1) protoVersion(1) idType(1) id(20) [operatorIdType(1) operatorId(20)] */
    private fun parseBasicId(data: ByteArray): RidMessage? {
        if (data.size < 23) return null
        val idType = data[2].toInt() and 0xFF
        val id = decodeId(data, 3, 20)
        if (id.isNullOrBlank()) return null
        return RidMessage(serialNumber = id, idType = idType)
    }

    /** Location/Vector：msgType(1) protoVersion(1) msgCounter(1) status(1) direction(2) speedH(2) speedV(2) lat(4) lon(4) … */
    private fun parseLocation(data: ByteArray): RidMessage? {
        if (data.size < 22) return null
        val buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        buf.position(4)
        val direction = buf.short.toInt() / 100f           // 0.01°
        val speedH = buf.short.toInt() * 0.25f             // 0.25 m/s
        buf.short // speedV
        val lat = buf.int.toDouble() / 1e7
        val lon = buf.int.toDouble() / 1e7
        buf.short // pressureAlt
        val geodeticAlt = buf.short.toInt() * 0.5f         // 0.5 m
        if (lat == 0.0 && lon == 0.0) return null
        return RidMessage(
            latitude = lat,
            longitude = lon,
            speedMs = speedH,
            altitudeM = geodeticAlt,
            directionDeg = direction,
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
        if (lat == 0.0 && lon == 0.0) return null
        return RidMessage(operatorLatitude = lat, operatorLongitude = lon)
    }

    /** 解码定长 ID 字符串（去掉尾部空字节/空白） */
    private fun decodeId(data: ByteArray, offset: Int, length: Int): String? {
        if (offset + length > data.size) return null
        val end = (offset until offset + length).firstOrNull { data[it].toInt() == 0 } ?: (offset + length)
        val text = String(data, offset, end - offset, Charsets.US_ASCII).trim()
        return text.ifEmpty { null }
    }
}
