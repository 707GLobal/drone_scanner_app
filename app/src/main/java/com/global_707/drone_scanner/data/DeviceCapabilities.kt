package com.global_707.drone_scanner.data

import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

/**
 * 本机 RID 检测能力检测（搜索设置页展示"supported / not supported"状态）。
 * 全部基于系统公开 API 实时判断，不使用硬编码参数。
 */
object DeviceCapabilities {

    /** 蓝牙适配器可用 */
    fun bluetoothAvailable(context: Context): Boolean {
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        return manager?.adapter != null
    }

    /**
     * 蓝牙 4 Legacy（BLE 广播承载 RID）。
     * 所有带蓝牙适配器的设备均支持 Bluetooth 4 LE 广播。
     */
    fun bluetooth4Legacy(context: Context): Boolean = bluetoothAvailable(context)

    /**
     * 蓝牙 5 Extended（长距 / 扩展广播 / 2M PHY 承载 RID）。
     * 依据系统公开 API 判断，任一 BT5 特性支持即视为支持。
     */
    fun bluetooth5Extended(context: Context): Boolean {
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter = manager?.adapter ?: return false
        return try {
            adapter.isLe2MPhySupported ||
                adapter.isLeCodedPhySupported ||
                adapter.isLeExtendedAdvertisingSupported
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Wi-Fi 信标 RID 解析能力（本 App Wi-Fi 通道的实际门槛）。
     *
     * RID 的 Wi-Fi 承载是信标（Beacon）厂商 IE，读取 IE 需要 API 30+
     * 的 `ScanResult.InformationElement.getId()/getBytes()`，且本机需有 Wi-Fi 硬件。
     */
    fun wifiBeaconRidSupported(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return false
        return context.packageManager.hasSystemFeature(PackageManager.FEATURE_WIFI)
    }
}
