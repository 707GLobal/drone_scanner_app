package com.global_707.drone_scanner

import com.global_707.drone_scanner.data.AppLogger
import com.global_707.drone_scanner.data.LogFileStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 日志模块纯函数单测：时长格式化、体积格式化、日志行格式、hex 转储。
 * 只测不依赖 Android 的部分（文件 IO / SAF 由真机验证）。
 */
class AppLoggerTest {

    @Test
    fun `时长格式应为 HH mm ss`() {
        assertEquals("00:00:00", AppLogger.formatDuration(0))
        assertEquals("00:00:01", AppLogger.formatDuration(1_000))
        assertEquals("00:01:00", AppLogger.formatDuration(60_000))
        assertEquals("01:00:00", AppLogger.formatDuration(3_600_000))
        assertEquals("01:02:03", AppLogger.formatDuration(3_723_000))
        // 毫秒截断（不进位）
        assertEquals("00:00:00", AppLogger.formatDuration(999))
        // 异常入参不崩溃
        assertEquals("00:00:00", AppLogger.formatDuration(-5))
    }

    @Test
    fun `体积格式应按量级切换单位`() {
        assertEquals("0 B", AppLogger.formatSize(0))
        assertEquals("512 B", AppLogger.formatSize(512))
        assertEquals("1.0 KB", AppLogger.formatSize(1024))
        assertEquals("1.5 KB", AppLogger.formatSize(1536))
        assertEquals("1.0 MB", AppLogger.formatSize(1024L * 1024))
        assertEquals("12.4 MB", AppLogger.formatSize((12.4 * 1024 * 1024).toLong()))
        assertEquals("1.00 GB", AppLogger.formatSize(1024L * 1024 * 1024))
        assertEquals("—", AppLogger.formatSize(-1))
    }

    @Test
    fun `日志行格式为 时间戳 级别 TAG 消息`() {
        val line = AppLogger.formatLine(
            tsMs = 0L,
            level = 'I',
            tag = "Scan",
            message = "window packets=10 ridHits=2",
            tr = null,
        )
        // 时间戳由 LogDeviceInfo 生成（本地时区），这里只校验结构与内容
        assertTrue(line.endsWith(" I/Scan: window packets=10 ridHits=2"))
        assertTrue(line.contains("-"))
        assertTrue(line.contains(":"))
    }

    @Test
    fun `异常日志应附完整堆栈`() {
        val boom = IllegalStateException("boom")
        val line = AppLogger.formatLine(0L, 'E', "Crash", "未捕获异常", boom)
        assertTrue(line.contains("E/Crash: 未捕获异常"))
        assertTrue(line.contains("java.lang.IllegalStateException: boom"))
        assertTrue(line.contains("AppLoggerTest"))
    }

    @Test
    fun `hex 转储应大写空格分隔`() {
        val hex = AppLogger.toHex(byteArrayOf(0x0F, 0x00, 0xAB.toByte(), 0xFF.toByte()))
        assertEquals("0F 00 AB FF", hex)
        assertEquals("", AppLogger.toHex(ByteArray(0)))
    }

    @Test
    fun `日志文件命名规则应只认本应用生成的文件`() {
        assertTrue(LogFileStore.isLogFileName("log_20260924_132604_123.log"))
        assertTrue(LogFileStore.isLogFileName("log_20260924_132604_123_1.log"))
        // MediaStore 会按 MIME 类型补扩展名，真机落盘的是 .log.txt —— 必须同样认作日志，
        // 否则列表恒为空、总大小显示 0 B、清理按钮被禁用（曾出现"清理日志失效"）
        assertTrue(LogFileStore.isLogFileName("log_20260924_142306_290.log.txt"))
        // 其他文件不得被当成日志（清理时避免误删用户文件）
        assertFalse(LogFileStore.isLogFileName("photo.jpg"))
        assertFalse(LogFileStore.isLogFileName("log_2026.txt"))
        assertFalse(LogFileStore.isLogFileName("mylog_1.log"))
    }
}
