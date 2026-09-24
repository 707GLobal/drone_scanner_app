package com.global_707.drone_scanner.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** 扫描优先级（参考搜索设置页"Scan priority"选项） */
object ScanPriority {
    const val LOW = 0
    const val NORMAL = 1
    const val HIGH = 2
}

/**
 * 扫描模式：
 *  - [INTERNAL] 手机直扫：使用本机蓝牙 / Wi-Fi 射频
 *  - [EXTERNAL] 连接外置天线：射频扫描由外置板端完成，手机只作显示端（见 ScannerController.ingestExternal）
 */
object ScanMode {
    const val INTERNAL = 0
    const val EXTERNAL = 1
}

/**
 * 日志记录方式：
 *  - [OFF]    关闭：不创建、不写入日志文件（已有文件保留，除非用户"清理日志"）
 *  - [AUTO]   开启 APP 自动记录：App 启动即开始记录，进程存活期间持续写入
 *  - [MANUAL] 手动开启记录：由用户在「日志设置」页开始 / 终止
 */
object LogMode {
    const val OFF = 0
    const val AUTO = 1
    const val MANUAL = 2
}

/**
 * 应用设置存储（SharedPreferences 持久化，Compose 响应式状态）。
 * 设置页修改后，地图主页 / 扫描器实时响应。
 */
object AppPrefs {
    private const val PREFS_NAME = "drone_scanner_prefs"
    private var prefs: SharedPreferences? = null

    var wifiScanEnabled by mutableStateOf(true)
    var bluetoothScanEnabled by mutableStateOf(true)
    var scanMode by mutableIntStateOf(ScanMode.INTERNAL)
    var scanPriority by mutableIntStateOf(ScanPriority.HIGH)
    /** 日志记录方式（见 [LogMode]） */
    var logMode by mutableIntStateOf(LogMode.OFF)
    /** 自定义日志目录（SAF 树 URI 字符串）；null 表示使用默认目录 */
    var logTreeUri by mutableStateOf<String?>(null)
    var satelliteMap by mutableStateOf(false)
    var keepScreenOn by mutableStateOf(false)
    var demoMode by mutableStateOf(false)

    fun init(context: Context) {
        if (prefs == null) {
            prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            wifiScanEnabled = prefs?.getBoolean("wifi_scan", true) ?: true
            bluetoothScanEnabled = prefs?.getBoolean("bt_scan", true) ?: true
            scanMode = prefs?.getInt("scan_mode", ScanMode.INTERNAL) ?: ScanMode.INTERNAL
            scanPriority = prefs?.getInt("scan_priority", ScanPriority.HIGH) ?: ScanPriority.HIGH
            logMode = prefs?.getInt("log_mode", LogMode.OFF) ?: LogMode.OFF
            logTreeUri = prefs?.getString("log_tree_uri", null)
            satelliteMap = prefs?.getBoolean("satellite_map", false) ?: false
            keepScreenOn = prefs?.getBoolean("keep_screen_on", false) ?: false
            demoMode = prefs?.getBoolean("demo_mode", false) ?: false
        }
    }

    fun saveWifiScanEnabled(value: Boolean) {
        wifiScanEnabled = value
        prefs?.edit()?.putBoolean("wifi_scan", value)?.apply()
    }

    fun saveBluetoothScanEnabled(value: Boolean) {
        bluetoothScanEnabled = value
        prefs?.edit()?.putBoolean("bt_scan", value)?.apply()
    }

    fun saveScanMode(value: Int) {
        scanMode = value
        prefs?.edit()?.putInt("scan_mode", value)?.apply()
    }

    fun saveScanPriority(value: Int) {
        scanPriority = value
        prefs?.edit()?.putInt("scan_priority", value)?.apply()
    }

    fun saveLogMode(value: Int) {
        logMode = value
        prefs?.edit()?.putInt("log_mode", value)?.apply()
    }

    fun saveLogTreeUri(value: String?) {
        logTreeUri = value
        prefs?.edit()?.putString("log_tree_uri", value)?.apply()
    }

    fun saveSatelliteMap(value: Boolean) {
        satelliteMap = value
        prefs?.edit()?.putBoolean("satellite_map", value)?.apply()
    }

    fun saveKeepScreenOn(value: Boolean) {
        keepScreenOn = value
        prefs?.edit()?.putBoolean("keep_screen_on", value)?.apply()
    }

    fun saveDemoMode(value: Boolean) {
        demoMode = value
        prefs?.edit()?.putBoolean("demo_mode", value)?.apply()
    }
}
