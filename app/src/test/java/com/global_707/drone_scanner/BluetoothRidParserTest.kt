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
 * RID 消息解析单元测试：
 * 按 ASTM F3411 规范构造二进制字节流，验证蓝牙 25 字节承载与 Message Pack 解析。
 */
class BluetoothRidParserTest {

    /** 构造 25 字节 Location/Vector 消息（蓝牙承载格式） */
    private fun locationVector25(): ByteArray {
        val buf = ByteBuffer.allocate(25).order(ByteOrder.LITTLE_ENDIAN)
        buf.put(0x02)            // msgType: Location/Vector
        buf.put(0x02)            // protoVersion
        buf.put(0x07)            // msgCounter
        buf.put(0x00)            // status
        buf.putShort(13500)      // direction = 135.00°
        buf.putShort(50)         // speedH = 12.5 m/s (0.25 单位)
        buf.putShort(2)          // speedV = 0.5 m/s
        buf.putInt(312304000)    // lat = 31.2304° (1e-7)
        buf.putInt(1214737000)   // lon = 121.4737°
        buf.putShort(242)        // pressureAlt = 121.0 m (0.5 单位)
        buf.putShort(241)        // geodeticAlt = 120.5 m
        buf.putShort(120)        // heightAboveTakeoff = 60.0 m
        buf.put(0.toByte())      // verticalAccuracy
        return buf.array()
    }

    /** 构造 23 字节 Basic ID 消息 */
    private fun basicId(serial: String): ByteArray {
        val buf = ByteBuffer.allocate(23)
        buf.put(0x00)            // msgType: Basic ID
        buf.put(0x02)            // protoVersion
        buf.put(0x01)            // idType: 序列号
        buf.put(serial.toByteArray(Charsets.US_ASCII))
        return buf.array()
    }

    @Test
    fun `25字节 LocationVector 应完整解析位置与速度`() {
        val msgs = BluetoothRidParser.parseMessages(locationVector25())
        assertEquals(1, msgs.size)
        val m = msgs[0]

        assertEquals(0x07, m.messageCounter)
        assertEquals(135.0f, m.directionDeg!!, 0.01f)
        assertEquals(12.5f, m.speedMs!!, 0.01f)
        assertEquals(0.5f, m.verticalSpeedMs!!, 0.01f)
        assertEquals(31.2304, m.latitude!!, 1e-6)
        assertEquals(121.4737, m.longitude!!, 1e-6)
        assertEquals(121.0f, m.pressureAltitudeM!!, 0.01f)
        assertEquals(120.5f, m.geodeticAltitudeM!!, 0.01f)
        assertEquals(60.0f, m.heightAboveTakeoffM!!, 0.01f)
        // 蓝牙 25 字节承载无时间戳字段
        assertNull(m.ridTimestampSeconds)
    }

    @Test
    fun `Basic ID 应解析出序列号`() {
        val msgs = BluetoothRidParser.parseMessages(basicId("3P7XK0A2B1C4D5E6"))
        assertEquals(1, msgs.size)
        assertEquals("3P7XK0A2B1C4D5E6", msgs[0].serialNumber)
        assertEquals(1, msgs[0].idType)
        assertEquals(2, msgs[0].protocolVersion)
    }

    @Test
    fun `Message Pack 应解出多条子消息并合并字段`() {
        // 构造 0x0F + authType + Basic ID(25B) + Location(25B)
        val basicIdPacked = ByteArray(25) // 25 字节，Basic ID 后补 2 字节填充
        basicId("MOCK1234").copyInto(basicIdPacked)
        val pack = ByteBuffer.allocate(2 + 25 + 25)
        pack.put(0x0F.toByte())   // msgType: Message Pack
        pack.put(0x00)            // authType
        pack.put(basicIdPacked)
        pack.put(locationVector25())

        val msgs = BluetoothRidParser.parseMessages(pack.array())
        assertEquals(2, msgs.size)

        // 合并两条子消息
        val merged = msgs.fold(BluetoothRidParser.RidMessage()) { acc, m -> acc.merge(m) }
        assertEquals("MOCK1234", merged.serialNumber)
        assertEquals(1, merged.idType)
        assertEquals(31.2304, merged.latitude!!, 1e-6)
        assertEquals(121.4737, merged.longitude!!, 1e-6)
        assertEquals(12.5f, merged.speedMs!!, 0.01f)
    }

    @Test
    fun `非 RID 载荷应返回空`() {
        val garbage = byteArrayOf(0x55.toByte(), 0xAA.toByte(), 0x01, 0x02, 0x03)
        assertTrue(BluetoothRidParser.parseMessages(garbage).isEmpty())
        // 消息类型未知（如 0x04 Auth）应返回空
        val auth = byteArrayOf(0x04, 0x02, 0x01, 0x02, 0x03, 0x04)
        assertTrue(BluetoothRidParser.parseMessages(auth).isEmpty())
    }

    @Test
    fun `未知位置值应解析为 null`() {
        val buf = ByteBuffer.allocate(25).order(ByteOrder.LITTLE_ENDIAN)
        buf.put(0x02)
        buf.put(0x02)
        buf.put(0x01)
        buf.put(0x00)
        buf.putShort(0xFFFF.toShort())  // direction 未知
        buf.putShort(0xFFFF.toShort())  // speedH 未知
        buf.putShort(0xFFFF.toShort())  // speedV 未知
        buf.putInt(Int.MIN_VALUE)       // lat 未知
        buf.putInt(Int.MIN_VALUE)       // lon 未知
        buf.putShort(0xFFFF.toShort())  // pressureAlt 未知
        buf.putShort(0xFFFF.toShort())  // geodeticAlt 未知
        buf.putShort(0xFFFF.toShort())  // height 未知
        buf.put(0.toByte())
        val msgs = BluetoothRidParser.parseMessages(buf.array())
        assertEquals(1, msgs.size)
        val m = msgs[0]
        assertNull(m.directionDeg)
        assertNull(m.speedMs)
        assertNull(m.latitude)
        assertNull(m.longitude)
        assertNull(m.pressureAltitudeM)
        assertNull(m.geodeticAltitudeM)
        assertNull(m.heightAboveTakeoffM)
        assertNotNull(m.messageCounter)
    }

    /** 构造仅 direction 字段可变的 25 字节 Location/Vector 消息（其余字段置未知） */
    private fun locationWithDirection(rawDirection: Short): ByteArray {
        val buf = ByteBuffer.allocate(25).order(ByteOrder.LITTLE_ENDIAN)
        buf.put(0x02)            // msgType: Location/Vector
        buf.put(0x02)            // protoVersion
        buf.put(0x01)            // msgCounter
        buf.put(0x00)            // status
        buf.putShort(rawDirection)
        buf.putShort(0xFFFF.toShort())  // speedH 未知
        buf.putShort(0xFFFF.toShort())  // speedV 未知
        buf.putInt(Int.MIN_VALUE)       // lat 未知
        buf.putInt(Int.MIN_VALUE)       // lon 未知
        buf.putShort(0xFFFF.toShort())  // pressureAlt 未知
        buf.putShort(0xFFFF.toShort())  // geodeticAlt 未知
        buf.putShort(0xFFFF.toShort())  // height 未知
        buf.put(0.toByte())
        return buf.array()
    }

    @Test
    fun `航向超出360度应视为无效`() {
        // 0xFFFF（未知）→ null
        val unknown = BluetoothRidParser.parseMessages(locationWithDirection(0xFFFF.toShort()))
        assertNull(unknown[0].directionDeg)
        // 361.00°（原始值 36100，超出合法上限）→ null（无符号位型，toShort 截断不影响位型）
        val over = BluetoothRidParser.parseMessages(locationWithDirection(36100.toShort()))
        assertNull(over[0].directionDeg)
        // 360.00° 边界值合法
        val edge = BluetoothRidParser.parseMessages(locationWithDirection(36000.toShort()))
        assertEquals(360.0f, edge[0].directionDeg!!, 0.01f)
    }

    // ---------- 标准 ASTM F3411（0xFFA0 承载） ----------

    /** 构造标准 25 字节 Basic ID 消息 */
    private fun stdBasicId25(serial: String): ByteArray {
        val buf = ByteBuffer.allocate(25)
        buf.put(0x00)            // msgType: Basic ID
        buf.put(0x02)            // protocolVersion
        buf.put(0x01)            // idType: 序列号
        buf.put(0x11)            // uasIdType(低 4 位) + uasType(高 4 位)
        buf.put(serial.toByteArray(Charsets.US_ASCII))  // 剩余自动 0x00 填充
        buf.put(0)               // reserved
        return buf.array()
    }

    /** 构造标准 25 字节 Location/Vector 消息 */
    private fun stdLocation25(
        direction: Int = 36,
        speed: Int = 40,
        vSpeed: Int = 4,
        lat: Int = 312304000,
        lon: Int = 1214737000,
        pressure: Int = 2420,
        geodetic: Int = 2420,
        height: Int = 60,
        ts: Int = 30,
    ): ByteArray {
        val buf = ByteBuffer.allocate(25).order(ByteOrder.LITTLE_ENDIAN)
        buf.put(0x01)            // msgType: Location/Vector
        buf.put(0x02)            // protocolVersion
        buf.put(0x02)            // status: airborne
        buf.put(0)               // reserved
        buf.put(direction.toByte())
        buf.put(speed.toByte())
        buf.put(vSpeed.toByte())
        buf.putInt(lat)
        buf.putInt(lon)
        buf.putShort(pressure.toShort())
        buf.putShort(geodetic.toShort())
        buf.put(height.toByte())
        buf.put(0)               // horizAcc
        buf.put(0)               // vertAcc
        buf.put(0)               // baroAcc
        buf.put(0)               // speedAcc
        buf.put(ts.toByte())     // timestamp
        return buf.array()
    }

    @Test
    fun `标准LocationVector应按官方布局解析`() {
        val msgs = BluetoothRidParser.parseStandardMessages(stdLocation25())
        assertEquals(1, msgs.size)
        val m = msgs[0]
        // 航向 10° 步进、速度 0.25 m/s
        assertEquals(360.0f, m.directionDeg!!, 0.01f)
        assertEquals(10.0f, m.speedMs!!, 0.01f)
        assertEquals(1.0f, m.verticalSpeedMs!!, 0.01f)
        assertEquals(31.2304, m.latitude!!, 1e-6)
        assertEquals(121.4737, m.longitude!!, 1e-6)
        // 高度 = 原始值 × 0.5 − 1000（标准偏移编码）
        assertEquals(210.0f, m.pressureAltitudeM!!, 0.01f)
        assertEquals(210.0f, m.geodeticAltitudeM!!, 0.01f)
        assertEquals(60.0f, m.heightAboveTakeoffM!!, 0.01f)
        assertEquals(3.0f, m.ridTimestampSeconds!!, 0.01f)
        assertEquals(2, m.protocolVersion)
    }

    @Test
    fun `标准未知值应解析为null`() {
        val m = BluetoothRidParser.parseStandardMessages(
            stdLocation25(
                direction = 0xFF, speed = 0xFF, vSpeed = 0x80,
                lat = Int.MIN_VALUE, lon = Int.MIN_VALUE,
                pressure = 0xFFFF, geodetic = 0xFFFF, height = 0xFF, ts = 0xFF,
            ),
        )[0]
        assertNull(m.directionDeg)
        assertNull(m.speedMs)
        assertNull(m.verticalSpeedMs)
        assertNull(m.latitude)
        assertNull(m.longitude)
        assertNull(m.pressureAltitudeM)
        assertNull(m.geodeticAltitudeM)
        assertNull(m.heightAboveTakeoffM)
        assertNull(m.ridTimestampSeconds)
        assertNotNull(m.statusFlags)
    }

    @Test
    fun `标准BasicID与MessagePack应解析并合并`() {
        val basic = BluetoothRidParser.parseStandardMessages(stdBasicId25("1596F1ABCD1234567890"))
        assertEquals(1, basic.size)
        assertEquals("1596F1ABCD1234567890", basic[0].serialNumber)
        assertEquals(1, basic[0].idType)

        // Message Pack：0x0F + authType + Basic ID + Location（标准子消息分派）
        val pack = ByteBuffer.allocate(2 + 25 + 25)
        pack.put(0x0F.toByte())
        pack.put(0x00)
        pack.put(stdBasicId25("1596F1ABCD1234567890"))
        pack.put(stdLocation25())
        val msgs = BluetoothRidParser.parseStandardMessages(pack.array())
        assertEquals(2, msgs.size)
        val merged = msgs.fold(BluetoothRidParser.RidMessage()) { acc, m -> acc.merge(m) }
        assertEquals("1596F1ABCD1234567890", merged.serialNumber)
        assertEquals(31.2304, merged.latitude!!, 1e-6)
        assertEquals(10.0f, merged.speedMs!!, 0.01f)
    }

    @Test
    fun `标准System应解析操作员位置`() {
        val buf = ByteBuffer.allocate(25).order(ByteOrder.LITTLE_ENDIAN)
        buf.put(0x02)            // msgType: System
        buf.put(0x02)            // protocolVersion
        buf.putInt(432000)       // 系统时间戳（0.1s）
        buf.put(0x01)            // 操作员位置类型：动态实时
        buf.putInt(312304000)    // 操作员纬度
        buf.putInt(1214737000)   // 操作员经度
        buf.putShort(100)        // 区域半径
        buf.put(50)              // 区域高度
        buf.put(20)              // 区域下限
        buf.put(0)               // 精度枚举 ×4 + 保留
        buf.put(0)
        buf.put(0)
        buf.put(0)
        buf.put(0)
        buf.put(0)
        val msgs = BluetoothRidParser.parseStandardMessages(buf.array())
        assertEquals(1, msgs.size)
        assertEquals(31.2304, msgs[0].operatorLatitude!!, 1e-6)
        assertEquals(121.4737, msgs[0].operatorLongitude!!, 1e-6)
    }

    @Test
    fun `标准认证与自描述消息应被跳过`() {
        val auth = byteArrayOf(0x04, 0x02, 0x00, 0x01, 0x02, 0x03)
        assertTrue(BluetoothRidParser.parseStandardMessages(auth).isEmpty())
        val selfId = byteArrayOf(0x05, 0x02, 0x00, 0x41, 0x42)
        assertTrue(BluetoothRidParser.parseStandardMessages(selfId).isEmpty())
    }
}
