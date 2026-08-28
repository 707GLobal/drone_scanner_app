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
 * 应用设置存储（SharedPreferences 持久化，Compose 响应式状态）。
 * 设置页修改后，地图主页 / 扫描器实时响应。
 */
object AppPrefs {
    private const val PREFS_NAME = "drone_scanner_prefs"
    private var prefs: SharedPreferences? = null

    var wifiScanEnabled by mutableStateOf(true)
    var bluetoothScanEnabled by mutableStateOf(true)
    var scanPriority by mutableIntStateOf(ScanPriority.HIGH)
    var satelliteMap by mutableStateOf(false)
    var keepScreenOn by mutableStateOf(false)
    var demoMode by mutableStateOf(false)

    fun init(context: Context) {
        if (prefs == null) {
            prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            wifiScanEnabled = prefs?.getBoolean("wifi_scan", true) ?: true
            bluetoothScanEnabled = prefs?.getBoolean("bt_scan", true) ?: true
            scanPriority = prefs?.getInt("scan_priority", ScanPriority.HIGH) ?: ScanPriority.HIGH
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

    fun saveScanPriority(value: Int) {
        scanPriority = value
        prefs?.edit()?.putInt("scan_priority", value)?.apply()
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
