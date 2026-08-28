package com.global_707.drone_scanner.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 应用设置存储（SharedPreferences 持久化，Compose 响应式状态）。
 * 设置页修改后，地图主页 / 扫描器实时响应。
 */
object AppPrefs {
    private const val PREFS_NAME = "drone_scanner_prefs"
    private var prefs: SharedPreferences? = null

    var demoMode by mutableStateOf(false)
    var wifiScanEnabled by mutableStateOf(true)
    var bluetoothScanEnabled by mutableStateOf(true)
    var satelliteMap by mutableStateOf(false)
    var showOperatorMarkers by mutableStateOf(true)
    var showNameLabels by mutableStateOf(true)

    fun init(context: Context) {
        if (prefs == null) {
            prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            demoMode = prefs?.getBoolean("demo_mode", false) ?: false
            wifiScanEnabled = prefs?.getBoolean("wifi_scan", true) ?: true
            bluetoothScanEnabled = prefs?.getBoolean("bt_scan", true) ?: true
            satelliteMap = prefs?.getBoolean("satellite_map", false) ?: false
            showOperatorMarkers = prefs?.getBoolean("show_operator", true) ?: true
            showNameLabels = prefs?.getBoolean("show_labels", true) ?: true
        }
    }

    fun saveDemoMode(value: Boolean) {
        demoMode = value
        prefs?.edit()?.putBoolean("demo_mode", value)?.apply()
    }

    fun saveWifiScanEnabled(value: Boolean) {
        wifiScanEnabled = value
        prefs?.edit()?.putBoolean("wifi_scan", value)?.apply()
    }

    fun saveBluetoothScanEnabled(value: Boolean) {
        bluetoothScanEnabled = value
        prefs?.edit()?.putBoolean("bt_scan", value)?.apply()
    }

    fun saveSatelliteMap(value: Boolean) {
        satelliteMap = value
        prefs?.edit()?.putBoolean("satellite_map", value)?.apply()
    }

    fun saveShowOperatorMarkers(value: Boolean) {
        showOperatorMarkers = value
        prefs?.edit()?.putBoolean("show_operator", value)?.apply()
    }

    fun saveShowNameLabels(value: Boolean) {
        showNameLabels = value
        prefs?.edit()?.putBoolean("show_labels", value)?.apply()
    }
}
