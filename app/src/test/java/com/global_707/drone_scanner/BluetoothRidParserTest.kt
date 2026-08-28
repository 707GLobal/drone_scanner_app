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
}
