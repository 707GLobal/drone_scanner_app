package com.global_707.drone_scanner.data

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.net.wifi.ScanResult as WifiScanResult
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log
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
 *  - 蓝牙：扫描 RID Service UUID（0xFFFA-0xFFFD）广播并完整解包（ASTM F3411）
 *  - WiFi：扫描带 ASTM OUI (FA-0B-9C) 制造商 IE 的信标（Android 16+）
 *  - 同一设备的多次广播（序列号 / 位置 / 操作员分帧）会被合并
 *  - 扫描优先级（低/中/高）影响蓝牙扫描模式与功耗
 */
object ScannerController {

    /** 当前无人机列表（全部来自真实检测，无模拟数据） */
    var drones by mutableStateOf<List<Drone>>(emptyList())
        private set

    /** 扫描状态 */
    var scanState by mutableStateOf(ScanState(isScanning = false, wifiScanning = false, bluetoothScanning = false))
        private set

    /** 系统蓝牙开关真实状态（实时读取，非写死） */
    var bluetoothSystemOn by mutableStateOf(false)
        private set

    /** 系统 Wi-Fi 开关真实状态（实时读取，非写死） */
    var wifiSystemOn by mutableStateOf(false)
        private set

    private val detected = LinkedHashMap<String, DetectedDevice>()

    private var bluetoothLeScanner: BluetoothLeScanner? = null
    private var scanCallback: ScanCallback? = null
    private var wifiManager: WifiManager? = null
    private var scope: CoroutineScope? = null
    private var wifiJob: Job? = null
    private var lastKnownLocation: Location? = null
    @Volatile
    private var running = false

    /** 是否已有一次节流刷新在排队（配合 [requestRefresh] 合并 BLE 高频回调） */
    @Volatile
    private var refreshScheduled = false

    // ---------- 诊断日志 / 节流参数 ----------
    private const val TAG = "RidScanner"
    /** 现场诊断：BLE 扫描窗口长度（毫秒），定期汇总一条日志 */
    private const val SCAN_LOG_WINDOW_MS = 5000L
    /** Wi-Fi startScan 系统节流下限（毫秒）：更频繁调用会被系统忽略并返回陈旧结果 */
    private const val WIFI_SCAN_INTERVAL_MS = 20_000L
    /** Wi-Fi scanResults 读取轮询间隔（毫秒） */
    private const val WIFI_RESULTS_READ_MS = 5000L

    /** UI 刷新节流窗口：BLE 高频回调合并为至多每 300ms 一次列表重建 + 地图重画 */
    private const val REFRESH_COALESCE_MS = 300L
    private var scanWindowStart = 0L
    private var scanWindowPackets = 0
    private var scanWindowRid = 0

    /** 单台设备的累积状态（合并 Basic ID / Location / Operator 分帧消息） */
    private class DetectedDevice(val key: String) {
        var serial: String? = null
        var idType: Int? = null
        var protocolVersion: Int? = null
        var messageCounter: Int? = null
        var statusFlags: Int? = null
        var directionDeg: Float? = null
        var speedMs: Float? = null
        var verticalSpeedMs: Float? = null
        var latitude: Double? = null
        var longitude: Double? = null
        var pressureAltitudeM: Float? = null
        var geodeticAltitudeM: Float? = null
        var heightAboveTakeoffM: Float? = null
        var ridTimestampSeconds: Float? = null
        var operatorId: String? = null
        var operatorLatitude: Double? = null
        var operatorLongitude: Double? = null
        var protocol = "Bluetooth RID"
        var frequency = "2.4 GHz"
        var rssi = -100
        var lastSeen = 0L

        fun merge(msg: BluetoothRidParser.RidMessage) {
            msg.serialNumber?.let { serial = it }
            msg.idType?.let { idType = it }
            msg.protocolVersion?.let { protocolVersion = it }
            msg.messageCounter?.let { messageCounter = it }
            msg.statusFlags?.let { statusFlags = it }
            msg.directionDeg?.let { directionDeg = it }
            msg.speedMs?.let { speedMs = it }
            msg.verticalSpeedMs?.let { verticalSpeedMs = it }
            msg.latitude?.let { latitude = it }
            msg.longitude?.let { longitude = it }
            msg.pressureAltitudeM?.let { pressureAltitudeM = it }
            msg.geodeticAltitudeM?.let { geodeticAltitudeM = it }
            msg.heightAboveTakeoffM?.let { heightAboveTakeoffM = it }
            msg.ridTimestampSeconds?.let { ridTimestampSeconds = it }
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
        refreshScheduled = false
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        refreshLocation(context)
        refreshSystemState(context)
        if (AppPrefs.bluetoothScanEnabled) startBluetooth(context)
        if (AppPrefs.wifiScanEnabled) startWifi(context)
        updateScanState()
        refresh()
    }

    /**
     * 实时读取系统蓝牙 / Wi-Fi 开关状态（每次调用查询真实硬件状态，非写死）。
     *
     * 返回 true 表示开关状态与上次相比发生变化（供调用方决定是否重启扫描）。
     * 检测到通道被关闭时，同步更新扫描状态为对应错误码，界面据此提示用户。
     */
    @SuppressLint("MissingPermission")
    fun refreshSystemState(context: Context): Boolean {
        val btOn = try {
            (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)
                ?.adapter?.isEnabled == true
        } catch (_: Exception) {
            false
        }
        val wifiOn = try {
            (context.getSystemService(Context.WIFI_SERVICE) as? WifiManager)?.isWifiEnabled == true
        } catch (_: Exception) {
            false
        }
        val changed = btOn != bluetoothSystemOn || wifiOn != wifiSystemOn
        bluetoothSystemOn = btOn
        wifiSystemOn = wifiOn

        // 通道关闭时给出具体错误；重新开启时清除错误（扫描由调用方 restart 重建）
        if (!btOn) {
            if (AppPrefs.bluetoothScanEnabled) {
                scanState = scanState.copy(
                    bluetoothScanning = false,
                    bluetoothErrorCode = ScanState.BT_ERR_DISABLED,
                )
            }
        } else if (scanState.bluetoothErrorCode == ScanState.BT_ERR_DISABLED) {
            scanState = scanState.copy(bluetoothErrorCode = null)
        }
        if (!wifiOn) {
            if (AppPrefs.wifiScanEnabled) {
                scanState = scanState.copy(
                    wifiScanning = false,
                    wifiErrorCode = ScanState.WIFI_ERR_UNAVAILABLE,
                )
            }
        } else if (scanState.wifiErrorCode == ScanState.WIFI_ERR_UNAVAILABLE) {
            scanState = scanState.copy(wifiErrorCode = null)
        }
        return changed
    }

    @SuppressLint("MissingPermission")
    fun stop() {
        running = false
        refreshScheduled = false
        try {
            scanCallback?.let { cb -> bluetoothLeScanner?.stopScan(cb) }
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

    /** 设置变化后重启扫描（仅在扫描运行中响应；未运行时不主动拉起——扫描只在首页运行） */
    fun restart(context: Context) {
        if (!running) return
        stop()
        start(context)
    }

    /** 权限被拒绝或扫描失败时使用 */
    fun markScanStopped() {
        running = false
        scanState = ScanState(isScanning = false, wifiScanning = false, bluetoothScanning = false)
    }

    /** 检测结果变化后刷新对外列表（演示模式开启时叠加演示数据） */
    fun refresh() {
        val real = synchronized(detected) { detected.values.toList() }
            .filter { it.serial != null || it.latitude != null || it.protocol == "WiFi RID" }
            .map { toDrone(it) }
        drones = if (AppPrefs.demoMode) real + MockData.drones else real
    }

    // ---------- 诊断日志 ----------

    /**
     * BLE 回调热路径专用刷新：把每包一次的全量列表重建（`drones` 状态写入会连锁触发
     * 地图 clear + 全量重画标记）合并为 [REFRESH_COALESCE_MS] 窗口内一次，
     * 显著降低主线程与 UI 负载。非热路径（设置变更、Wi-Fi 轮询）仍直接调 [refresh]。
     */
    private fun requestRefresh() {
        if (refreshScheduled) return
        refreshScheduled = true
        val s = scope
        if (s == null) {
            refreshScheduled = false
            refresh()
            return
        }
        s.launch {
            delay(REFRESH_COALESCE_MS)
            refreshScheduled = false
            refresh()
        }
    }

    /** 现场诊断：统计 BLE 扫描窗口内广播包总数与 RID 命中数，每 5 秒汇总一条日志 */
    private fun logScanWindow(isRid: Boolean) {
        val now = System.currentTimeMillis()
        if (scanWindowStart == 0L) scanWindowStart = now
        scanWindowPackets++
        if (isRid) scanWindowRid++
        if (now - scanWindowStart >= SCAN_LOG_WINDOW_MS) {
            Log.d(
                TAG,
                "BLE 扫描 ${(now - scanWindowStart) / 1000}s：广播包=$scanWindowPackets RID命中=$scanWindowRid",
            )
            scanWindowStart = now
            scanWindowPackets = 0
            scanWindowRid = 0
        }
    }

    // ---------- 蓝牙 ----------

    @SuppressLint("MissingPermission")
    private fun startBluetooth(context: Context) {
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter = manager?.adapter
        if (adapter == null) {
            // 设备不支持蓝牙
            scanState = scanState.copy(
                bluetoothScanning = false,
                bluetoothErrorCode = ScanState.BT_ERR_UNAVAILABLE,
            )
            return
        }
        if (!adapter.isEnabled) {
            // 系统蓝牙未开启
            scanState = scanState.copy(
                bluetoothScanning = false,
                bluetoothErrorCode = ScanState.BT_ERR_DISABLED,
            )
            return
        }
        val scanner = adapter.bluetoothLeScanner
        if (scanner == null) {
            scanState = scanState.copy(
                bluetoothScanning = false,
                bluetoothErrorCode = ScanState.BT_ERR_UNAVAILABLE,
            )
            return
        }
        bluetoothLeScanner = scanner

        // 重要：不要按 Service-UUID 列表做 ScanFilter 过滤！
        // RID（ASTM F3411）蓝牙广播把 25 字节消息放在「Service Data」AD 里；Legacy 广播
        // 总负载只有 31 字节，装不下额外的 Service-UUID 列表 AD，因此多数信标只带
        // Service Data。而 ScanFilter.setServiceUuid 只匹配 Service-UUID 列表，
        // 会把这类 RID 包全部过滤掉 → 一个结果都收不到（外场实测主因）。已移除过滤，
        // 改为全量扫描 + 回调内解析（BluetoothRidParser 对非 RID 载荷返回 null，代价很小）。
        val settings = ScanSettings.Builder()
            .setScanMode(scanModeFor(AppPrefs.scanPriority))
            // 接收全部 PHY（1M/2M/Coded），否则 BT5 扩展广播 / 长距 RID（0xFFFE/0xFFFF）收不到
            .setPhy(ScanSettings.PHY_LE_ALL_SUPPORTED)
            .build()
        scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                logScanWindow(isRid = false)
                handleBluetoothResult(result)
            }

            override fun onBatchScanResults(results: List<ScanResult>) {
                results.forEach { onScanResult(0, it) }
            }

            override fun onScanFailed(errorCode: Int) {
                Log.w(TAG, "BLE 扫描失败 errorCode=$errorCode")
                scanState = scanState.copy(
                    bluetoothScanning = false,
                    bluetoothErrorCode = errorCode,
                )
            }
        }
        try {
            // filters 传 null = 全量扫描，是否 RID 由解析器判定
            scanner.startScan(null, settings, scanCallback)
            Log.d(TAG, "BLE 扫描已启动（全量, phy=ALL, mode=${scanModeFor(AppPrefs.scanPriority)}）")
            scanState = scanState.copy(bluetoothScanning = true, bluetoothErrorCode = null)
        } catch (e: Exception) {
            Log.w(TAG, "启动 BLE 扫描失败: ${e.message}")
            scanCallback = null
            scanState = scanState.copy(
                bluetoothScanning = false,
                bluetoothErrorCode = ScanState.BT_ERR_UNAVAILABLE,
            )
        }
    }

    private fun scanModeFor(priority: Int): Int = when (priority) {
        ScanPriority.LOW -> ScanSettings.SCAN_MODE_LOW_POWER
        ScanPriority.NORMAL -> ScanSettings.SCAN_MODE_BALANCED
        else -> ScanSettings.SCAN_MODE_LOW_LATENCY
    }

    private fun handleBluetoothResult(result: ScanResult) {
        val record = result.scanRecord ?: return
        val rid = BluetoothRidParser.parse(record) ?: return
        logScanWindow(isRid = true)
        Log.d(
            TAG,
            "RID 命中 addr=${result.device.address} rssi=${result.rssi} " +
                "serial=${rid.serialNumber ?: "—"} idType=${rid.idType ?: "—"} " +
                "lat=${rid.latitude ?: "—"} lon=${rid.longitude ?: "—"}",
        )
        // 统一以设备 MAC 为 key：同一设备 Basic ID / Location / Operator 分帧才能合并到
        // 同一条记录。若以 serial 为 key，先到 Location（无序列号）、后到 Basic ID（带
        // 序列号）会把同一设备分裂成两条记录，各自只含一半字段。
        val key = result.device.address.ifBlank { rid.serialNumber ?: "unknown" }
        val now = System.currentTimeMillis()
        val device = synchronized(detected) {
            detected.getOrPut(key) { DetectedDevice(key) }.also { d ->
                d.merge(rid)
                d.rssi = result.rssi
                d.lastSeen = now
            }
        }
        requestRefresh()
    }

    // ---------- WiFi ----------

    private fun startWifi(context: Context) {
        wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        if (wifiManager == null || wifiManager?.isWifiEnabled != true) {
            // Wi-Fi 不可用或未开启
            scanState = scanState.copy(
                wifiScanning = false,
                wifiErrorCode = ScanState.WIFI_ERR_UNAVAILABLE,
            )
            return
        }
        wifiManager?.let {
            scanState = scanState.copy(wifiScanning = true, wifiErrorCode = null)
        }
        wifiJob = scope?.launch {
            // 注意：RID over Wi-Fi 的主流承载是 Wi-Fi Aware (NAN) / Beacon 广播，
            // 本通道只能发现以 AP/Beacon 模式广播 ASTM OUI（FA-0B-9C）的信标（Android 16+），
            // NAN 广播不会出现在 WifiManager.scanResults 中。
            // Android 对 startScan 有系统级节流（后台约 20s+ 一次，13+ 更严），高频调用会被
            // 系统静默忽略并持续返回陈旧结果，故按节流间隔低频触发 + 周期性读取结果缓存。
            var lastScanAt = 0L
            while (isActive) {
                val now = System.currentTimeMillis()
                if (now - lastScanAt >= WIFI_SCAN_INTERVAL_MS) {
                    lastScanAt = now
                    try {
                        @Suppress("DEPRECATION")
                        val accepted = wifiManager?.startScan()
                        if (accepted == false) {
                            Log.w(TAG, "WifiManager.startScan 被系统拒绝（节流/权限），按间隔重试")
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "WifiManager.startScan 异常: ${e.message}")
                    }
                }
                delay(WIFI_RESULTS_READ_MS)
                val results = try {
                    wifiManager?.scanResults ?: emptyList()
                } catch (_: SecurityException) {
                    emptyList()
                }
                val nowMs = System.currentTimeMillis()
                var wifiRidHits = 0
                results.forEach { result ->
                    if (hasRidOui(result)) {
                        wifiRidHits++
                        val key = "wifi:" + (result.BSSID ?: result.SSID)
                        synchronized(detected) {
                            detected.getOrPut(key) {
                                DetectedDevice(key).apply {
                                    protocol = "WiFi RID"
                                    frequency = bandOf(result.frequency)
                                }
                            }.also { d ->
                                d.rssi = result.level
                                d.lastSeen = nowMs
                            }
                        }
                    }
                }
                if (wifiRidHits > 0) {
                    Log.d(TAG, "Wi-Fi RID 命中 ${wifiRidHits} 个信标")
                }
                refresh()
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
        val callSign = serial?.takeLast(4) ?: d.key.takeLast(4).replace(":", "").ifEmpty { "N/A" }
        val status = when {
            serial == null -> DroneStatus.WARNING
            d.idType == 3 || d.idType == 4 -> DroneStatus.WARNING
            else -> DroneStatus.SAFE
        }
        return Drone(
            id = d.key,
            name = callSign,
            snCode = serial,
            idType = d.idType,
            protocolVersion = d.protocolVersion,
            messageCounter = d.messageCounter,
            statusFlags = d.statusFlags,
            directionDeg = d.directionDeg,
            speedMs = d.speedMs,
            verticalSpeedMs = d.verticalSpeedMs,
            pressureAltitudeM = d.pressureAltitudeM,
            geodeticAltitudeM = d.geodeticAltitudeM,
            heightAboveTakeoffM = d.heightAboveTakeoffM,
            ridTimestampSeconds = d.ridTimestampSeconds,
            droneLat = d.latitude,
            droneLng = d.longitude,
            distanceM = distance?.toFloat(),
            operatorLat = d.operatorLatitude,
            operatorLng = d.operatorLongitude,
            operatorId = d.operatorId,
            operatorDistanceM = operatorDistance?.toInt(),
            signalStrength = rssiToSignal(d.rssi),
            protocol = d.protocol,
            frequency = d.frequency,
            lastSeenSeconds = ((System.currentTimeMillis() - d.lastSeen) / 1000).toInt(),
            status = status,
        )
    }

    private fun updateScanState() {
        // 注意：copy 保留 startBluetooth/startWifi 刚写入的各通道实际状态与错误码；
        // 若整表重建（旧行为）会把刚检测到的"设备不支持/开关未开启"错误抹成"扫描中"。
        scanState = scanState.copy(
            isScanning = running,
            bluetoothScanning = running && AppPrefs.bluetoothScanEnabled &&
                scanState.bluetoothScanning && scanState.bluetoothErrorCode == null,
            wifiScanning = running && AppPrefs.wifiScanEnabled &&
                scanState.wifiScanning && scanState.wifiErrorCode == null,
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
