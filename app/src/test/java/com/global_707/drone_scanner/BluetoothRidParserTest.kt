package com.global_707.drone_scanner

import com.global_707.drone_scanner.data.BluetoothRidParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * RID 消息解析单元测试。
 *
 * 字节布局依据 ASTM F3411 / ASD-STAN prEN 4709-002 官方参考实现（OpenDroneID C 库
 * 的 ODID_*_encoded 位域定义）与 GB 42590 试行标准（表 3/表 4/表 6）：
 * 首字节高 4 位为消息类型、低 4 位为协议版本；各承载（BLE / Wi-Fi Beacon）消息格式相同。
 */
class BluetoothRidParserTest {

    // ---------- 夹具构造（严格按官方布局） ----------

    /**
     * 25 字节 Location/Vector：
     * [0] msgType(高4)+version(低4)  [1] status(高4)|Reserved|HeightType|EWDirection|SpeedMult
     * [2] 航向(1°) [3] 水平速度 [4] 垂直速度
     * [5..8] 纬度 [9..12] 经度 [13..14] 气压高度 [15..16] 大地高度 [17..18] 相对高度
     * [19] 精度 [20] 精度 [21..22] 时间戳 [23] 时间戳精度 [24] 保留
     */
    private fun location(
        version: Int = 2,
        status: Int = 2,
        heightType: Int = 0,
        ewDirection: Boolean = false,
        speedMult: Boolean = false,
        directionRaw: Int = 36,
        speedRaw: Int = 40,
        verticalSpeedRaw: Int = 4,
        latRaw: Int = 312304000,
        lonRaw: Int = 1214737000,
        pressureAltRaw: Int = 2420,
        geodeticAltRaw: Int = 2420,
        heightRaw: Int = 2120,
        timestampRaw: Int = 30,
    ): ByteArray {
        val buf = ByteBuffer.allocate(25).order(ByteOrder.LITTLE_ENDIAN)
        buf.put(((0x1 shl 4) or version).toByte())
        buf.put(
            (
                (status shl 4) or
                    (heightType shl 2) or
                    (if (ewDirection) 0x02 else 0) or
                    (if (speedMult) 0x01 else 0)
                ).toByte(),
        )
        buf.put(directionRaw.toByte())
        buf.put(speedRaw.toByte())
        buf.put(verticalSpeedRaw.toByte())
        buf.putInt(latRaw)
        buf.putInt(lonRaw)
        buf.putShort(pressureAltRaw.toShort())
        buf.putShort(geodeticAltRaw.toShort())
        buf.putShort(heightRaw.toShort())
        buf.put(((4 shl 4) or 11).toByte()) // 垂直精度 4 / 水平精度 11
        buf.put(((0 shl 4) or 4).toByte())  // 气压精度 0 / 速度精度 4
        buf.putShort(timestampRaw.toShort())
        buf.put(2)                          // 时间戳精度
        buf.put(0)                          // 保留
        return buf.array()
    }

    /** 25 字节 Basic ID：[0] type+version [1] idType(高4)+uaType(低4) [2..21] UASID [22..24] 保留 */
    private fun basicId(
        serial: String,
        version: Int = 2,
        idType: Int = 1,
        uaType: Int = 2,
    ): ByteArray {
        val buf = ByteBuffer.allocate(25)
        buf.put(((0x0 shl 4) or version).toByte())
        buf.put(((idType shl 4) or (uaType and 0x0F)).toByte())
        buf.put(serial.toByteArray(Charsets.US_ASCII))
        return buf.array()
    }

    /** 25 字节 System：[0] type+version [1] Reserved|ClassificationType|OperatorLocationType [2..5] lat [6..9] lon */
    private fun system(
        version: Int = 2,
        operatorLocationType: Int = 1,
        operatorLatRaw: Int = 312304000,
        operatorLonRaw: Int = 1214737000,
    ): ByteArray {
        val buf = ByteBuffer.allocate(25).order(ByteOrder.LITTLE_ENDIAN)
        buf.put(((0x4 shl 4) or version).toByte())
        buf.put((operatorLocationType and 0x03).toByte())
        buf.putInt(operatorLatRaw)
        buf.putInt(operatorLonRaw)
        repeat(25 - 10) { buf.put(0) }
        return buf.array()
    }

    /** 25 字节 Operator ID：[0] type+version [1] operatorIdType [2..21] 操作员 ID [22..24] 保留 */
    private fun operatorId(id: String, version: Int = 2, idType: Int = 0): ByteArray {
        val buf = ByteBuffer.allocate(25)
        buf.put(((0x5 shl 4) or version).toByte())
        buf.put(idType.toByte())
        buf.put(id.toByteArray(Charsets.US_ASCII))
        return buf.array()
    }

    /** Message Pack：[0] 0xF? [1] SingleMessageSize(25) [2] MsgPackSize [3..] N×25 */
    private fun pack(version: Int, vararg messages: ByteArray): ByteArray {
        val buf = ByteBuffer.allocate(3 + messages.size * 25)
        buf.put(((0xF shl 4) or version).toByte())
        buf.put(25)
        buf.put(messages.size.toByte())
        messages.forEach { buf.put(it) }
        return buf.array()
    }

    // ---------- Location ----------

    @Test
    fun `Location 应按官方布局解析`() {
        val msgs = BluetoothRidParser.parseMessages(location())
        assertEquals(1, msgs.size)
        val m = msgs[0]

        assertEquals(2, m.protocolVersion)
        assertEquals(2, m.statusFlags)
        assertEquals(36.0f, m.directionDeg!!, 0.01f)
        assertEquals(10.0f, m.speedMs!!, 0.01f)      // 40 × 0.25
        assertEquals(2.0f, m.verticalSpeedMs!!, 0.01f) // 4 × 0.5
        assertEquals(31.2304, m.latitude!!, 1e-6)
        assertEquals(121.4737, m.longitude!!, 1e-6)
        assertEquals(210.0f, m.pressureAltitudeM!!, 0.01f) // 2420 × 0.5 − 1000
        assertEquals(210.0f, m.geodeticAltitudeM!!, 0.01f)
        assertEquals(60.0f, m.heightAboveTakeoffM!!, 0.01f) // 2120 × 0.5 − 1000
        assertEquals(3.0f, m.ridTimestampSeconds!!, 0.01f)  // 30 × 0.1
    }

    @Test
    fun `航向应按 1 度步进并应用 EWDirection 位`() {
        // 回归：曾按 10° 步进解析，36 会被错解成 360°
        assertEquals(36.0f, BluetoothRidParser.parseMessages(location(directionRaw = 36))[0].directionDeg!!, 0.01f)
        assertEquals(
            280.0f,
            BluetoothRidParser.parseMessages(location(directionRaw = 100, ewDirection = true))[0].directionDeg!!,
            0.01f,
        )
        assertEquals(
            360.0f,
            BluetoothRidParser.parseMessages(location(directionRaw = 180, ewDirection = true))[0].directionDeg!!,
            0.01f,
        )
        // +180° 后越界（> 360°）按无效丢弃
        assertNull(
            BluetoothRidParser.parseMessages(location(directionRaw = 200, ewDirection = true))[0].directionDeg,
        )
    }

    @Test
    fun `水平速度高段量化应按 SpeedMult 位切换`() {
        // 低段：raw × 0.25
        assertEquals(25.0f, BluetoothRidParser.parseMessages(location(speedRaw = 100))[0].speedMs!!, 0.01f)
        // 高段：raw × 0.75 + 255 × 0.25
        assertEquals(
            138.75f,
            BluetoothRidParser.parseMessages(location(speedRaw = 100, speedMult = true))[0].speedMs!!,
            0.01f,
        )
    }

    @Test
    fun `未知与越界取值应解析为 null`() {
        val m = BluetoothRidParser.parseMessages(
            location(
                speedRaw = 0xFF,
                verticalSpeedRaw = 63,
                latRaw = Int.MIN_VALUE,
                lonRaw = Int.MIN_VALUE,
                pressureAltRaw = 0,
                geodeticAltRaw = 0xFFFF,
                heightRaw = 0,
                timestampRaw = 0xFFFF,
            ),
        )[0]
        assertNull(m.speedMs)
        assertNull(m.verticalSpeedMs)
        assertNull(m.latitude)
        assertNull(m.longitude)
        assertNull(m.pressureAltitudeM)
        assertNull(m.geodeticAltitudeM) // 0xFFFE 等大值解出 31767m，超过合理上限按未知
        assertNull(m.heightAboveTakeoffM)
        assertNull(m.ridTimestampSeconds)
        assertNotNull(m.statusFlags) // 状态字段仍应解析出来

        // 垂直速度 −63 亦为无效
        assertNull(BluetoothRidParser.parseMessages(location(verticalSpeedRaw = -63))[0].verticalSpeedMs)
        // (0,0) 视为未提供定位，不在地图上画出设备
        val zero = BluetoothRidParser.parseMessages(location(latRaw = 0, lonRaw = 0))[0]
        assertNull(zero.latitude)
        assertNull(zero.longitude)
        // 越界坐标丢弃
        val outOfRange = BluetoothRidParser.parseMessages(location(latRaw = 1_000_000_000))[0]
        assertNull(outOfRange.latitude)
    }

    // ---------- 消息类型分派 ----------

    @Test
    fun `消息类型必须按首字节高 4 位分派`() {
        // 0x02 = Basic ID + 版本 2：回归"整字节比较"导致误判为 System/Location
        val basic = BluetoothRidParser.parseMessages(basicId("1596F1ABCD1234567890"))[0]
        assertEquals("1596F1ABCD1234567890", basic.serialNumber)
        assertNull(basic.latitude)

        // 0x12 = Location + 版本 2：回归"整字节比较"导致被两条通道同时丢弃
        val loc = BluetoothRidParser.parseMessages(location())[0]
        assertNotNull(loc.latitude)
        assertNull(loc.serialNumber)

        // 0x42 = System
        val sys = BluetoothRidParser.parseMessages(system())[0]
        assertNotNull(sys.operatorLatitude)

        // 0x52 = Operator ID
        val op = BluetoothRidParser.parseMessages(operatorId("CHN1234567890ABCDEF"))[0]
        assertEquals("CHN1234567890ABCDEF", op.operatorId)

        // 0xF2 = Message Pack
        assertEquals(1, BluetoothRidParser.parseMessages(pack(2, location())).size)
    }

    @Test
    fun `Basic ID 的 idType 在高 4 位且 UASID 从 byte2 起`() {
        val m = BluetoothRidParser.parseMessages(basicId("1596F1ABCD1234567890", idType = 1))[0]
        assertEquals(1, m.idType)
        assertEquals(2, m.protocolVersion)
        // 回归：曾从 byte1 取版本、从 byte2 取 idType、从 byte4 取 ID，导致序列号丢前两位、idType 变成 ASCII 码
        assertEquals("1596F1ABCD1234567890", m.serialNumber)

        val caa = BluetoothRidParser.parseMessages(basicId("CHN00000000000000001", idType = 2))[0]
        assertEquals(2, caa.idType)
    }

    @Test
    fun `System 的操作员位置应从 byte2 起`() {
        val m = BluetoothRidParser.parseMessages(system())[0]
        assertEquals(31.2304, m.operatorLatitude!!, 1e-6)
        assertEquals(121.4737, m.operatorLongitude!!, 1e-6)
        assertEquals(2, m.protocolVersion)
        // 操作员位置缺失（0x80000000）时不应产出位置
        val missing = BluetoothRidParser.parseMessages(
            system(operatorLatRaw = Int.MIN_VALUE, operatorLonRaw = Int.MIN_VALUE),
        )
        assertTrue(missing.isEmpty())
    }

    @Test
    fun `认证与自描述消息应被跳过`() {
        val auth = ByteArray(25).also { it[0] = ((0x2 shl 4) or 2).toByte() }
        assertTrue(BluetoothRidParser.parseMessages(auth).isEmpty())
        val selfId = ByteArray(25).also { it[0] = ((0x3 shl 4) or 2).toByte() }
        assertTrue(BluetoothRidParser.parseMessages(selfId).isEmpty())
    }

    // ---------- Message Pack ----------

    @Test
    fun `Message Pack 应解出多条子消息并合并`() {
        val msgs = BluetoothRidParser.parseMessages(pack(2, basicId("MOCK1234"), location()))
        assertEquals(2, msgs.size)
        val merged = msgs.fold(BluetoothRidParser.RidMessage()) { acc, m -> acc.merge(m) }
        assertEquals("MOCK1234", merged.serialNumber)
        assertEquals(1, merged.idType)
        assertEquals(31.2304, merged.latitude!!, 1e-6)
        assertEquals(121.4737, merged.longitude!!, 1e-6)
        assertEquals(10.0f, merged.speedMs!!, 0.01f)
    }

    @Test
    fun `ASTM 与国标 Pack 信封格式相同`() {
        // ASTM F3411-22 用 0xF2（版本 2），GB 42590 试行标准用 0xF1（版本 1），信封结构一致
        val astm = BluetoothRidParser.parseMessages(pack(2, basicId("ASTM0001"), location(version = 2)))
        assertEquals(2, astm.size)
        assertEquals(2, astm[0].protocolVersion)

        val gb = BluetoothRidParser.parseMessages(pack(1, basicId("GB42590001", version = 1), location(version = 1)))
        assertEquals(2, gb.size)
        assertEquals(1, gb[0].protocolVersion)
        assertEquals("GB42590001", gb[0].serialNumber)
    }

    @Test
    fun `Message Pack 信封非法应整包丢弃`() {
        // SingleMessageSize 必须为 25
        val wrongSize = pack(2, location()).also { it[1] = 24 }
        assertTrue(BluetoothRidParser.parseMessages(wrongSize).isEmpty())

        // MsgPackSize 必须为 1..9
        val zeroCount = pack(2, location()).also { it[2] = 0 }
        assertTrue(BluetoothRidParser.parseMessages(zeroCount).isEmpty())
        val tooMany = pack(2, location()).also { it[2] = 10 }
        assertTrue(BluetoothRidParser.parseMessages(tooMany).isEmpty())

        // 声明的条数与实际长度不符（截断）
        val truncated = pack(2, location()).also { it[2] = 3 }
        assertTrue(BluetoothRidParser.parseMessages(truncated).isEmpty())

        // 长度不足 3 字节
        assertTrue(BluetoothRidParser.parseMessages(byteArrayOf(0xF2.toByte(), 25)).isEmpty())
    }

    @Test
    fun `Message Pack 子消息条数超限应整包丢弃`() {
        // Location 最多 1 条
        assertTrue(BluetoothRidParser.parseMessages(pack(2, location(), location())).isEmpty())
        // System 最多 1 条
        assertTrue(BluetoothRidParser.parseMessages(pack(2, system(), system())).isEmpty())
        // Basic ID 允许 2 条
        assertEquals(2, BluetoothRidParser.parseMessages(pack(2, basicId("AAAA"), basicId("BBBB"))).size)
        // Basic ID 超过 2 条
        assertTrue(
            BluetoothRidParser.parseMessages(pack(2, basicId("AAAA"), basicId("BBBB"), basicId("CCCC"))).isEmpty(),
        )
    }

    // ---------- 与参考实现交叉验证 ----------

    @Test
    fun `GB 42590 试行标准 Location 布局应能解析`() {
        // 按参考实现 encode_gb42590.c 的字段写入方式构造：航向 90°、水平速度 15.0m/s、
        // 垂直速度 0、气压/大地高度 100m、相对高度 50m、时间戳 630.0s，协议版本 1
        val buf = ByteBuffer.allocate(25).order(ByteOrder.LITTLE_ENDIAN)
        buf.put(((0x1 shl 4) or 1).toByte()) // 0x11 = Location + Version 1
        buf.put(((2 shl 4) or (0 shl 2) or 0).toByte()) // status=2 空中
        // 航向字节只有 1 字节（0–255），>255° 需配合 EWDirection 位，故此处取 90°
        buf.put(90.toByte())                 // 航向 1° 步进
        buf.put(60.toByte())                 // 15.0 m/s ÷ 0.25
        buf.put(0.toByte())                  // 垂直速度 0
        buf.putInt(312304000)                // 纬度
        buf.putInt(1214737000)               // 经度
        buf.putShort(2200.toShort())         // 气压高度 (100 + 1000) ÷ 0.5
        buf.putShort(2200.toShort())         // 大地高度
        buf.putShort(2100.toShort())         // 相对高度 (50 + 1000) ÷ 0.5
        buf.put(((4 shl 4) or 11).toByte())
        buf.put(4)
        buf.putShort(6300.toShort())         // 时间戳 63.00s × 10
        buf.put(2)
        buf.put(0)

        val m = BluetoothRidParser.parseMessages(buf.array())[0]
        assertEquals(1, m.protocolVersion)
        assertEquals(2, m.statusFlags)
        assertEquals(90.0f, m.directionDeg!!, 0.01f)
        assertEquals(15.0f, m.speedMs!!, 0.01f)
        assertEquals(0.0f, m.verticalSpeedMs!!, 0.01f)
        assertEquals(31.2304, m.latitude!!, 1e-6)
        assertEquals(121.4737, m.longitude!!, 1e-6)
        assertEquals(100.0f, m.pressureAltitudeM!!, 0.01f)
        assertEquals(100.0f, m.geodeticAltitudeM!!, 0.01f)
        assertEquals(50.0f, m.heightAboveTakeoffM!!, 0.01f)
        assertEquals(630.0f, m.ridTimestampSeconds!!, 0.01f)
    }

    // ---------- 噪声 ----------

    @Test
    fun `非 RID 载荷与过短载荷应返回空`() {
        assertTrue(BluetoothRidParser.parseMessages(ByteArray(0)).isEmpty())
        assertTrue(BluetoothRidParser.parseMessages(byteArrayOf(0x01)).isEmpty())
        // 消息类型 7 不属于 RID
        assertTrue(BluetoothRidParser.parseMessages(byteArrayOf(0x77, 0xAA.toByte(), 0x01, 0x02, 0x03)).isEmpty())
        // 类型对但长度不足：不应崩溃，且不产出任何字段
        val short = ByteArray(6).also { it[0] = ((0x1 shl 4) or 2).toByte() }
        val msgs = BluetoothRidParser.parseMessages(short)
        assertTrue(msgs.isEmpty())
    }

    @Test
    fun `仅接受 BasicID Location System OperatorID 与 Pack 五类消息`() {
        // 0x2 Auth 与 0x3 Self-ID 无展示字段，其余类型不属于 RID，一律不产出
        for (type in intArrayOf(0x2, 0x3, 0x6, 0x7, 0x8, 0x9, 0xA, 0xB, 0xC, 0xD, 0xE)) {
            val data = ByteArray(25)
            data[0] = ((type shl 4) or 2).toByte()
            assertTrue("消息类型 $type 不应产出", BluetoothRidParser.parseMessages(data).isEmpty())
        }
    }
}
