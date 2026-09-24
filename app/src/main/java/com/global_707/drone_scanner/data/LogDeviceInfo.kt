package com.global_707.drone_scanner.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.util.DisplayMetrics
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * 日志文件 Header 构建：App / 设备 / 系统 / 显示 / 语言时区 / 系统能力 / 关键权限。
 *
 * 全部取自系统公开 API（`Build`、`PackageManager.hasSystemFeature`、权限检查），
 * **不做任何推测或填充**：取不到的字段写 `unknown`，绝不编造。
 *
 * 输出为「键 = 值」分段文本（UTF-8），便于人读与脚本解析，且不依赖 org.json
 * （org.json 在 JVM 单测里是空实现，会拖累可测性）。
 */
object LogDeviceInfo {

    private const val UNKNOWN = "unknown"

    /** 生成完整 Header 文本（含结尾的分隔标记） */
    fun build(context: Context, startedAtMs: Long): String {
        val sb = StringBuilder(2048)
        sb.append("=== RID SCANNER LOG ===\n")
        appendApp(sb, context)
        appendDevice(sb)
        appendSystem(sb)
        appendDisplay(sb, context)
        appendLocale(sb)
        appendFeatures(sb, context)
        appendPermissions(sb, context)
        sb.append("[Log]\n")
        sb.append("  startedAt = ").append(formatTime(startedAtMs)).append('\n')
        sb.append("  timeZone = ").append(TimeZone.getDefault().id).append('\n')
        sb.append("=== LOG START ===\n")
        return sb.toString()
    }

    private fun appendApp(sb: StringBuilder, context: Context) {
        val pm = context.packageManager
        sb.append("[App]\n")
        sb.append("  packageName = ").append(context.packageName).append('\n')
        try {
            @Suppress("DEPRECATION")
            val info = pm.getPackageInfo(context.packageName, 0)
            sb.append("  versionName = ").append(info.versionName ?: UNKNOWN).append('\n')
            @Suppress("DEPRECATION")
            val versionCode = info.versionCode
            sb.append("  versionCode = ").append(versionCode).append('\n')
        } catch (_: Exception) {
            sb.append("  versionName = ").append(UNKNOWN).append('\n')
            sb.append("  versionCode = ").append(UNKNOWN).append('\n')
        }
        val isDebug = (context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
        sb.append("  buildType = ").append(if (isDebug) "debug" else "release").append('\n')
    }

    private fun appendDevice(sb: StringBuilder) {
        sb.append("[Device]\n")
        sb.append("  manufacturer = ").append(Build.MANUFACTURER).append('\n')
        sb.append("  brand = ").append(Build.BRAND).append('\n')
        sb.append("  model = ").append(Build.MODEL).append('\n')
        sb.append("  device = ").append(Build.DEVICE).append('\n')
        sb.append("  product = ").append(Build.PRODUCT).append('\n')
        sb.append("  hardware = ").append(Build.HARDWARE).append('\n')
        sb.append("  board = ").append(Build.BOARD).append('\n')
        sb.append("  abi = ").append(Build.SUPPORTED_ABIS.joinToString(",")).append('\n')
    }

    private fun appendSystem(sb: StringBuilder) {
        sb.append("[System]\n")
        sb.append("  androidVersion = ").append(Build.VERSION.RELEASE).append('\n')
        sb.append("  apiLevel = ").append(Build.VERSION.SDK_INT).append('\n')
        sb.append("  securityPatch = ").append(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Build.VERSION.SECURITY_PATCH else UNKNOWN,
        ).append('\n')
        sb.append("  buildId = ").append(Build.DISPLAY ?: UNKNOWN).append('\n')
        // ROM 信息：厂商系统属性只作只读展示，取不到就写 unknown
        sb.append("  romFingerprint = ").append(Build.FINGERPRINT ?: UNKNOWN).append('\n')
        sb.append("  romIncremental = ")
            .append(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Build.VERSION.INCREMENTAL else UNKNOWN)
            .append('\n')
        sb.append("  kernelAbi = ").append(Build.VERSION.SDK_INT).append(" / ").append(Build.SUPPORTED_ABIS.firstOrNull() ?: UNKNOWN).append('\n')
    }

    private fun appendDisplay(sb: StringBuilder, context: Context) {
        sb.append("[Display]\n")
        val metrics: DisplayMetrics = context.resources.displayMetrics
        sb.append("  resolution = ").append(metrics.widthPixels).append('x').append(metrics.heightPixels).append('\n')
        sb.append("  densityDpi = ").append(metrics.densityDpi).append('\n')
        sb.append("  density = ").append(metrics.density).append('\n')
        // 字体缩放（大字体排查用）；scaledDensity 已废弃，改用 configuration.fontScale
        sb.append("  fontScale = ").append(context.resources.configuration.fontScale).append('\n')
        sb.append("  refreshRate = ").append(refreshRate(context)).append('\n')
        sb.append("  freeStorageBytes = ").append(freeStorageBytes()).append('\n')
    }

    private fun refreshRate(context: Context): Float = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            context.display?.refreshRate ?: 0f
        } else {
            @Suppress("DEPRECATION")
            val wm = context.getSystemService(Context.WINDOW_SERVICE) as? android.view.WindowManager
            @Suppress("DEPRECATION")
            wm?.defaultDisplay?.refreshRate ?: 0f
        }
    } catch (_: Exception) {
        0f
    }

    private fun freeStorageBytes(): Long = try {
        val stat = StatFs(Environment.getDataDirectory().absolutePath)
        stat.availableBytes
    } catch (_: Exception) {
        -1L
    }

    private fun appendLocale(sb: StringBuilder) {
        val locale = Locale.getDefault()
        sb.append("[Locale]\n")
        sb.append("  language = ").append(locale.language).append('\n')
        sb.append("  country = ").append(locale.country).append('\n')
        sb.append("  locale = ").append(locale.toString()).append('\n')
        sb.append("  timeZoneOffsetMin = ").append(TimeZone.getDefault().getOffset(System.currentTimeMillis()) / 60000).append('\n')
    }

    private fun appendFeatures(sb: StringBuilder, context: Context) {
        sb.append("[Features]\n")
        FEATURES.forEach { (key, feature) ->
            sb.append("  ").append(key).append(" = ").append(hasFeature(context, feature)).append('\n')
        }
    }

    private fun hasFeature(context: Context, feature: String): Boolean = try {
        context.packageManager.hasSystemFeature(feature)
    } catch (_: Exception) {
        false
    }

    private fun appendPermissions(sb: StringBuilder, context: Context) {
        sb.append("[Permissions]\n")
        PERMISSIONS.forEach { (key, permission) ->
            sb.append("  ").append(key).append(" = ").append(granted(context, permission)).append('\n')
        }
    }

    private fun granted(context: Context, permission: String): Boolean = try {
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    } catch (_: Exception) {
        false
    }

    /** 时间格式：yyyy-MM-dd HH:mm:ss.SSS（不含冒号以外的特殊字符，便于嵌入日志行） */
    fun formatTime(ms: Long): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date(ms))

    private val FEATURES = listOf(
        "camera" to PackageManager.FEATURE_CAMERA_ANY,
        "bluetooth" to PackageManager.FEATURE_BLUETOOTH,
        "bluetoothLe" to PackageManager.FEATURE_BLUETOOTH_LE,
        "nfc" to PackageManager.FEATURE_NFC,
        "gps" to PackageManager.FEATURE_LOCATION_GPS,
        "telephony" to PackageManager.FEATURE_TELEPHONY,
        "wifi" to PackageManager.FEATURE_WIFI,
        "wifiDirect" to PackageManager.FEATURE_WIFI_DIRECT,
        "usbHost" to PackageManager.FEATURE_USB_HOST,
        "fingerprint" to PackageManager.FEATURE_FINGERPRINT,
        "accelerometer" to PackageManager.FEATURE_SENSOR_ACCELEROMETER,
        "gyroscope" to PackageManager.FEATURE_SENSOR_GYROSCOPE,
        "magnetometer" to PackageManager.FEATURE_SENSOR_COMPASS,
        "barometer" to PackageManager.FEATURE_SENSOR_BAROMETER,
        "stepCounter" to PackageManager.FEATURE_SENSOR_STEP_COUNTER,
    )

    private val PERMISSIONS = listOf(
        "fineLocation" to Manifest.permission.ACCESS_FINE_LOCATION,
        "coarseLocation" to Manifest.permission.ACCESS_COARSE_LOCATION,
        "bluetoothScan" to Manifest.permission.BLUETOOTH_SCAN,
        "bluetoothConnect" to Manifest.permission.BLUETOOTH_CONNECT,
        "nearbyWifiDevices" to "android.permission.NEARBY_WIFI_DEVICES",
        "postNotifications" to "android.permission.POST_NOTIFICATIONS",
    )
}
