package com.global_707.drone_scanner.data

import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * 应用日志器（单例）。
 *
 * 设计要点：
 *  - **三种记录方式**（[LogMode]）：关闭 / App 自动记录 / 手动记录；切换时先停当前再应用新模式
 *  - **不阻塞主线程**：日志行进入有界队列，由 IO 协程批量写盘并 flush；队列满则丢弃并计数
 *  - **线程安全**：写盘与崩溃日志共用同一把锁
 *  - **轮转**：单文件超过 [LogFileStore.MAX_FILE_BYTES] 自动换新文件（带新 Header）
 *  - **崩溃捕获**：未捕获异常先同步写入堆栈，再交回系统默认处理
 *  - 同时镜像到 logcat（tag = [TAG]），保证 `adb logcat -s RidScanner` 习惯不变
 *
 * 三种模式共用同一套日志管线（与射频来源无关）：本机蓝牙 / 本机 Wi-Fi / 外置天线
 * 都通过 [i]/[w]/[e]/[d] 记录，来源写在消息里（`source=ble|wifi_beacon|external`）。
 */
object AppLogger {

    /** logcat 用 TAG，保持与既有诊断日志一致 */
    const val TAG = "RidScanner"

    private const val QUEUE_CAPACITY = 512
    private const val MAX_HEX_BYTES = 256

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val queue = Channel<String>(QUEUE_CAPACITY)
    private val writeLock = Any()

    private var appContext: Context? = null
    private var sink: LogSink? = null
    private var currentFileName: String? = null
    private var currentBytes: Long = 0
    private var droppedCount = 0
    private var writerJob: Job? = null
    private var timerJob: Job? = null
    private var crashHandlerInstalled = false
    private var startedAtMs = 0L

    /** 是否正在记录（驱动 UI 按钮状态） */
    var isRecording by mutableStateOf(false)
        private set

    /** 本次记录已持续时长（毫秒）；停止后保留最终值 */
    var elapsedMs by mutableLongStateOf(0L)
        private set

    /** 当前日志文件大小（字节），驱动"总大小"显示刷新 */
    var currentFileBytes by mutableLongStateOf(0L)
        private set

    /** 累计已写入行数 */
    var writtenLines by mutableIntStateOf(0)
        private set

    fun init(context: Context, mode: Int) {
        appContext = context.applicationContext
        if (mode == LogMode.AUTO) {
            start(context, "auto")
        } else if (mode == LogMode.MANUAL && isRecording) {
            // 进程内重复初始化不需要重启
        }
        installCrashHandler()
    }

    /**
     * 切换记录方式：若正在记录，先停止并保存当前文件，再按新模式决定是否开始。
     */
    fun applyMode(context: Context, mode: Int) {
        if (isRecording) stop("mode_change")
        when (mode) {
            LogMode.AUTO -> start(context, "auto")
            LogMode.MANUAL -> Unit // 手动模式等用户点"开始记录"
            else -> Unit
        }
    }

    fun startManual(context: Context) {
        if (AppPrefs.logMode != LogMode.MANUAL) AppPrefs.saveLogMode(LogMode.MANUAL)
        start(context, "manual")
    }

    fun stopManual() {
        stop("manual_stop")
    }

    /**
     * 清理全部日志文件（「日志设置」页调用），返回删除个数；全程在 IO 线程。
     *
     * 若此刻正在记录：Linux 上删除被打开的文件只是 unlink（inode 仍在被写入），
     * 会出现"记录还在继续、目录里却看不到文件"，所以删完立刻[rotate]出一个新文件。
     */
    suspend fun clearLogs(context: Context): Int = withContext(Dispatchers.IO) {
        val deleted = LogFileStore.clear(context)
        if (isRecording) synchronized(writeLock) { rotate() }
        deleted
    }

    // ---------------- 开始 / 停止 ----------------

    private fun start(context: Context, reason: String) {
        if (isRecording) return
        appContext = context.applicationContext
        startedAtMs = System.currentTimeMillis()
        try {
            val opened = LogFileStore.openNew(context, startedAtMs) ?: run {
                Log.w(TAG, "日志文件创建失败（记录未开始）")
                return
            }
            synchronized(writeLock) {
                sink = opened.sink
                currentFileName = opened.name
                currentBytes = 0
                droppedCount = 0
                writtenLines = 0
                currentFileBytes = 0
                opened.sink.write(LogDeviceInfo.build(context, startedAtMs))
                opened.sink.flush()
            }
        } catch (e: Exception) {
            Log.w(TAG, "日志启动异常: ${e.message}")
            return
        }
        isRecording = true
        elapsedMs = 0L
        ensureWriter()
        startTimer()
        i("Log", "start reason=$reason file=${currentFileName ?: "—"} mode=${modeName(AppPrefs.logMode)}")
    }

    private fun stop(reason: String) {
        if (!isRecording) return
        // 先把收尾行入队（此时 isRecording 仍为 true）
        i("Log", "stop reason=$reason elapsed=${formatDuration(elapsedMs)} lines=$writtenLines")
        isRecording = false
        timerJob?.cancel()
        timerJob = null
        val previousWriter = writerJob
        writerJob = null
        // 收尾放到 IO 线程：先把剩余行写完再关闭文件，避免任何主线程等待
        scope.launch {
            previousWriter?.cancel()
            repeat(3) {
                drainQueue()
                delay(50)
            }
            drainQueue()
            synchronized(writeLock) {
                sink?.close()
                sink = null
            }
        }
        currentFileName = null
    }

    // ---------------- 写入 ----------------

    fun d(tag: String, message: String) = logLine('D', tag, message, null)

    fun i(tag: String, message: String) = logLine('I', tag, message, null)

    fun w(tag: String, message: String) = logLine('W', tag, message, null)

    fun e(tag: String, message: String, tr: Throwable? = null) = logLine('E', tag, message, tr)

    /** 解析失败等场景：把原始字节以 hex 记入日志（长度上限 [MAX_HEX_BYTES]） */
    fun hex(tag: String, message: String, data: ByteArray) {
        val shown = if (data.size > MAX_HEX_BYTES) data.copyOf(MAX_HEX_BYTES) else data
        val suffix = if (data.size > MAX_HEX_BYTES) "…(截断)" else ""
        logLine('W', tag, "$message len=${data.size} hex=${toHex(shown)}$suffix", null)
    }

    private fun logLine(level: Char, tag: String, message: String, tr: Throwable?) {
        // logcat 镜像始终输出，保持既有 adb 诊断习惯不受记录方式影响
        when (level) {
            'E' -> Log.e(TAG, "[$tag] $message", tr)
            'W' -> Log.w(TAG, "[$tag] $message", tr)
            'D' -> Log.d(TAG, "[$tag] $message", tr)
            else -> Log.i(TAG, "[$tag] $message", tr)
        }
        // 第一道门控：未在记录文件时到此为止——不格式化、不分配、不入队
        if (!isRecording) return
        val line = formatLine(System.currentTimeMillis(), level, tag, message, tr)
        if (queue.trySend(line).isFailure) {
            droppedCount++
        }
    }

    private fun ensureWriter() {
        if (writerJob?.isActive == true) return
        writerJob = scope.launch {
            while (isActive) {
                drainQueue()
                delay(120)
            }
        }
    }

    /** 把队列中已排队的行写出并 flush；返回本次写出的行数。由 IO 协程或停止流程调用 */
    private fun drainQueue(): Int {
        val batch = ArrayList<String>(16)
        while (true) {
            val line = queue.tryReceive().getOrNull() ?: break
            batch.add(line)
            if (batch.size >= 128) break
        }
        if (batch.isEmpty()) return 0
        synchronized(writeLock) {
            val target = sink ?: return 0
            try {
                batch.forEach { target.write(it); target.write("\n") }
                if (droppedCount > 0) {
                    target.write(formatLine(System.currentTimeMillis(), 'W', "Log", "dropped=$droppedCount（队列满，已丢弃）", null))
                    target.write("\n")
                    droppedCount = 0
                }
                target.flush()
                currentBytes += batch.sumOf { it.length + 1 }
                writtenLines += batch.size
                currentFileBytes = currentBytes
            } catch (e: Exception) {
                Log.w(TAG, "写日志失败: ${e.message}")
                return 0
            }
            if (currentBytes >= LogFileStore.MAX_FILE_BYTES) {
                rotate()
            }
        }
        return batch.size
    }

    /** 轮转：关闭当前文件，创建新文件并写 Header */
    private fun rotate() {
        val context = appContext ?: return
        val old = sink ?: return
        try {
            old.write("\n=== LOG ROTATED（继续写入新文件）===\n")
            old.flush()
            old.close()
        } catch (_: Exception) {
        }
        sink = null
        val opened = LogFileStore.openNew(context, System.currentTimeMillis())
        if (opened == null) {
            Log.w(TAG, "轮转失败，日志记录停止")
            isRecording = false
            return
        }
        sink = opened.sink
        currentFileName = opened.name
        currentBytes = 0
        currentFileBytes = 0
        opened.sink.write(LogDeviceInfo.build(context, startedAtMs))
        opened.sink.flush()
    }

    // ---------------- 计时 ----------------

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = scope.launch {
            val t0 = SystemClock.elapsedRealtime()
            while (isActive) {
                elapsedMs = SystemClock.elapsedRealtime() - t0
                delay(1000)
            }
        }
    }

    // ---------------- 崩溃捕获 ----------------

    private fun installCrashHandler() {
        if (crashHandlerInstalled) return
        crashHandlerInstalled = true
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                // 同步直写：进程即将终止，不能走异步队列
                val line = formatLine(
                    System.currentTimeMillis(),
                    'E',
                    "Crash",
                    "未捕获异常 thread=${thread.name}",
                    throwable,
                )
                synchronized(writeLock) {
                    sink?.let {
                        it.write(line)
                        it.write("\n")
                        it.flush()
                    }
                }
            } catch (_: Throwable) {
            }
            previous?.uncaughtException(thread, throwable)
        }
    }

    // ---------------- 查询 / 纯函数 ----------------

    fun currentFileName(): String? = currentFileName

    /** 日志行：`时间戳 级别/TAG: 消息`；异常附完整堆栈 */
    fun formatLine(tsMs: Long, level: Char, tag: String, message: String, tr: Throwable?): String {
        val head = "${LogDeviceInfo.formatTime(tsMs)} $level/$tag: $message"
        if (tr == null) return head
        val sw = java.io.StringWriter()
        tr.printStackTrace(java.io.PrintWriter(sw))
        return head + "\n" + sw.toString().trimEnd()
    }

    /** 时长格式化 `HH:mm:ss`（纯函数，便于单测） */
    fun formatDuration(ms: Long): String {
        val total = if (ms < 0) 0 else ms / 1000
        return String.format(Locale.US, "%02d:%02d:%02d", total / 3600, (total % 3600) / 60, total % 60)
    }

    /** 体积格式化，例如 `12.4 MB`（纯函数，便于单测） */
    fun formatSize(bytes: Long): String {
        if (bytes < 0) return "—"
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024) return String.format(Locale.US, "%.1f KB", kb)
        val mb = kb / 1024.0
        if (mb < 1024) return String.format(Locale.US, "%.1f MB", mb)
        return String.format(Locale.US, "%.2f GB", mb / 1024.0)
    }

    /** 字节数组转十六进制（大写、空格分隔） */
    fun toHex(data: ByteArray): String {
        val sb = StringBuilder(data.size * 3)
        for (b in data) {
            sb.append("%02X ".format(b.toInt() and 0xFF))
        }
        return sb.toString().trimEnd()
    }

    private fun modeName(mode: Int): String = when (mode) {
        LogMode.AUTO -> "auto"
        LogMode.MANUAL -> "manual"
        else -> "off"
    }
}
