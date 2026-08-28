package com.global_707.drone_scanner.data

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.wifi.ScanResult as WifiScanResult
import android.net.wifi.WifiManager
import android.os.Build
import android.os.ParcelUuid
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 真实 RID 扫描控制器：
 *  - 蓝牙：扫描 RID Service UUID（0xFFFA-0xFFFD）广播并解析（ASTM F3411）
 *  - WiFi：扫描带 ASTM OUI (FA-0B-9C) 制造商 IE 的信标
 *  - 同一设备的多次广播（序列号 / 位置可能分帧）会被合并
 *  - 演示模式：设置开启时，在真实结果基础上叠加示例数据
 */
object ScannerController {

    /** 当前无人机列表（真实检测 + 可选演示数据） */
    var drones by mutableStateOf<List<Drone>>(emptyList())
        private set

    /** 扫描状态 */
    var scanState by mutableStateOf(ScanState(isScanning = false, wifiScanning = false, bluetoothScanning = false))
        private set

    private val detected = LinkedHashMap<String, DetectedDevice>()

    private var bluetoothLeScanner: BluetoothLeScanner? = null
    private var scanCallback: ScanCallback? = null
    private var wifiManager: WifiManager? = null
    private var scope: CoroutineScope? = null
    private var wifiJob: Job? = null
    private var lastKnownLocation: android.location.Location? = null
    @Volatile
    private var running = false

    /** 单台设备的累积状态（合并 Basic ID / Location / Operator 分帧消息） */
    private class DetectedDevice {
        val key: String
        var serial: String? = null
        var idType: Int? = null
        var latitude: Double? = null
        var longitude: Double? = null
        var speedMs: Float? = null
        var altitudeM: Float? = null
        var operatorId: String? = null
        var operatorLatitude: Double? = null
        var operatorLongitude: Double? = null
        var protocol = "Bluetooth RID"
        var frequency = "2.4 GHz"
        var rssi = -100
        var lastSeen = 0L

        constructor(key: String) {
            this.key = key
        }

        fun merge(msg: BluetoothRidParser.RidMessage) {
            msg.serialNumber?.let { serial = it }
            msg.idType?.let { idType = it }
            msg.latitude?.let { latitude = it }
            msg.longitude?.let { longitude = it }
            msg.speedMs?.let { speedMs = it }
            msg.altitudeM?.let { altitudeM = it }
            msg.operatorId?.let { operatorId = it }
            msg.operatorLatitude?.let { operatorLatitude = it }
            msg.operatorLongitude?.let { operatorLongitude = it }
        }
    }

    /** 启动扫描（需要已授予定位/蓝牙权限） */
    @SuppressLint("MissingPermission")
    fun start(context: Context) {
        if (running) return
        running = true
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        refreshLocation(context)
        if (AppPrefs.bluetoothScanEnabled) startBluetooth(context)
        if (AppPrefs.wifiScanEnabled) startWifi(context)
        updateScanState()
        refresh()
    }

    @SuppressLint("MissingPermission")
    fun stop() {
        running = false
        try {
            bluetoothLeScanner?.let { scanner ->
                scanCallback?.let { cb ->
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        scanner.stopScan(cb)
                    } else {
                        @Suppress("DEPRECATION")
                        scanner.stopScan(cb)
                    }
                }
            }
        } catch (_: Exception) {
        }
        wifiJob?.cancel()
        scope?.cancel()
        scope = null
        bluetoothLeScanner = null
        scanCallback = null
        wifiJob = null
        scanState = ScanState(isScanning = false, wifiScanning = false, bluetoothScanning = false)
    }

    /** 设置变化后重启扫描 */
    fun restart(context: Context) {
        stop()
        start(context)
    }

    /** 权限被拒绝或扫描失败时使用 */
    fun markScanStopped() {
        running = false
        scanState = ScanState(isScanning = false, wifiScanning = false, bluetoothScanning = false)
    }

    /** 演示模式 / 检测结果变化后刷新对外列表 */
    fun refresh() {
        val real = synchronized(detected) { detected.values.toList() }
            .filter { it.serial != null || it.latitude != null }
            .map { toDrone(it) }
        drones = if (AppPrefs.demoMode) real + MockData.drones else real
    }

    // ---------- 蓝牙 ----------

    @SuppressLint("MissingPermission")
    private fun startBluetooth(context: Context) {
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager ?: return
        val adapter = manager.adapter ?: return
        if (!adapter.isEnabled) return
        val scanner = adapter.bluetoothLeScanner ?: return
        bluetoothLeScanner = scanner
        val filters = BluetoothRidParser.RID_SERVICE_UUIDS.map { uuid16 ->
            ScanFilter.Builder()
                .setServiceUuid(ParcelUuid.fromString("0000%04X-0000-1000-8000-00805F9B34FB".format(uuid16)))
                .build()
        }
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                handleBluetoothResult(result)
            }

            override fun onScanFailed(errorCode: Int) {
                scanState = scanState.copy(bluetoothScanning = false)
            }
        }
        try {
            scanner.startScan(filters, settings, scanCallback)
        } catch (_: Exception) {
            scanCallback = null
        }
    }

    private fun handleBluetoothResult(result: ScanResult) {
        val record = result.scanRecord ?: return
        val rid = BluetoothRidParser.parse(record) ?: return
        val key = rid.serialNumber ?: result.device.address
        val now = System.currentTimeMillis()
        val device = synchronized(detected) {
            detected.getOrPut(key) { DetectedDevice(key) }.also { d ->
                d.merge(rid)
                d.rssi = result.rssi
                d.lastSeen = now
            }
        }
        refresh()
    }

    // ---------- WiFi ----------

    private fun startWifi(context: Context) {
        wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager ?: return
        if (wifiManager?.isWifiEnabled != true) return
        wifiJob = scope?.launch {
            while (isActive) {
                try {
                    @Suppress("DEPRECATION")
                    wifiManager?.startScan()
                } catch (_: Exception) {
                }
                delay(2500)
                val results = try {
                    wifiManager?.scanResults ?: emptyList()
                } catch (_: SecurityException) {
                    emptyList()
                }
                val now = System.currentTimeMillis()
                results.forEach { result ->
                    if (hasRidOui(result)) {
                        val key = "wifi:" + (result.BSSID ?: result.SSID)
                        val device = synchronized(detected) {
                            detected.getOrPut(key) {
                                DetectedDevice(key).apply {
                                    protocol = "WiFi RID"
                                    frequency = bandOf(result.frequency)
                                }
                            }.also { d ->
                                d.rssi = result.level
                                d.lastSeen = now
                            }
                        }
                    }
                }
                refresh()
                delay(6000)
            }
        }
    }

    /**
     * ASTM F3411 WiFi RID：制造商 IE（ID 221）携带 OUI FA-0B-9C。
     * 注意：API 36 起信息元素类型为 ScanResult.InformationElement（getId/getBytes），
     * 旧版本类型不同（WifiInformationElement），为避免版本兼容问题仅在 API 36+ 检测。
     */
    private fun hasRidOui(result: WifiScanResult): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) return false
        val elements = try {
            result.informationElements
        } catch (_: Exception) {
            return false
        }
        return elements.any { element ->
            try {
                if (element.getId() != 221) return@any false
                val payload = element.getBytes()
                if (payload.remaining() < 3) return@any false
                val b0 = payload.get(0).toInt() and 0xFF
                val b1 = payload.get(1).toInt() and 0xFF
                val b2 = payload.get(2).toInt() and 0xFF
                b0 == 0xFA && b1 == 0x0B && b2 == 0x9C
            } catch (_: Exception) {
                false
            }
        }
    }

    private fun bandOf(frequencyMHz: Int): String = when {
        frequencyMHz in 2400..2500 -> "2.4 GHz"
        frequencyMHz in 4900..5900 -> "5.8 GHz"
        else -> "${frequencyMHz} MHz"
    }

    // ---------- 转换 ----------

    private fun refreshLocation(context: Context) {
        try {
            val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) !=
                PackageManager.PERMISSION_GRANTED
            ) return
            lastKnownLocation = manager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: manager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
        } catch (_: Exception) {
        }
    }

    private fun toDrone(d: DetectedDevice): Drone {
        val serial = d.serial
        val hasLocation = d.latitude != null && d.longitude != null
        val myLocation = lastKnownLocation
        val distance = if (hasLocation && myLocation != null) {
            distanceMeters(myLocation.latitude, myLocation.longitude, d.latitude!!, d.longitude!!)
        } else {
            null
        }
        val operatorDistance = if (d.operatorLatitude != null && d.operatorLongitude != null && hasLocation) {
            distanceMeters(d.latitude!!, d.longitude!!, d.operatorLatitude!!, d.operatorLongitude!!)
        } else {
            null
        }
        val name = if (d.protocol == "WiFi RID") {
            "WiFi RID 设备"
        } else {
            serial?.let { "无人机 · ${it.takeLast(8)}" } ?: "蓝牙 RID 设备"
        }
        val status = when {
            serial == null -> DroneStatus.WARNING
            d.idType == 3 || d.idType == 4 -> DroneStatus.WARNING
            else -> DroneStatus.SAFE
        }
        return Drone(
            id = d.key,
            name = name,
            snCode = serial ?: "—",
            ridCode = serial ?: "—",
            heightM = d.altitudeM,
            distanceM = distance?.toFloat(),
            speedMs = d.speedMs,
            signalStrength = rssiToSignal(d.rssi),
            protocol = d.protocol,
            frequency = d.frequency,
            droneLat = d.latitude ?: 0.0,
            droneLng = d.longitude ?: 0.0,
            operatorLat = d.operatorLatitude,
            operatorLng = d.operatorLongitude,
            operatorId = d.operatorId,
            operatorPhone = null,
            operatorDistanceM = operatorDistance?.toInt(),
            lastSeenSeconds = ((System.currentTimeMillis() - d.lastSeen) / 1000).toInt(),
            status = status,
        )
    }

    private fun updateScanState() {
        scanState = ScanState(
            isScanning = running,
            wifiScanning = running && AppPrefs.wifiScanEnabled,
            bluetoothScanning = running && AppPrefs.bluetoothScanEnabled,
        )
    }

    private fun rssiToSignal(rssi: Int): Int = when {
        rssi >= -45 -> 4
        rssi >= -60 -> 3
        rssi >= -75 -> 2
        rssi >= -90 -> 1
        else -> 0
    }

    private fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLon / 2) * sin(dLon / 2)
        return r * 2 * atan2(sqrt(a), sqrt(1 - a))
    }
}
