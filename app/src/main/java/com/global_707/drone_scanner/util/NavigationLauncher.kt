@file:SuppressLint("MissingPermission")

package com.global_707.drone_scanner.util

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** 外部导航启动器：通过 Uri Scheme 调起高德 / 百度 / Google Maps 等系统导航应用 */
object NavigationLauncher {

    private const val AMAP_PACKAGE = "com.autonavi.minimap"
    private const val BAIDU_PACKAGE = "com.baidu.BaiduMap"
    private const val GOOGLE_MAPS_PACKAGE = "com.google.android.apps.maps"

    /**
     * 导航到指定经纬度。
     * 优先高德（国内用户），其次百度，最后 Google / 通用 geo 链接。
     */
    fun navigateTo(context: Context, lat: Double, lng: Double, name: String) {
        val amapIntent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse(
                "androidamap://navi?sourceApplication=DroneScanner&poiname=${Uri.encode(name)}" +
                    "&lat=$lat&lon=$lng&dev=0&style=2",
            ),
        )
        if (isAppInstalled(context, AMAP_PACKAGE)) {
            context.startActivity(amapIntent)
            return
        }
        val baiduIntent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("baidumap://map/direction?destination=latlng:$lat,$lng&coord_type=gcj02&mode=driving"),
        )
        if (isAppInstalled(context, BAIDU_PACKAGE)) {
            context.startActivity(baiduIntent)
            return
        }
        val googleIntent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("google.navigation:q=$lat,$lng"),
        )
        if (isAppInstalled(context, GOOGLE_MAPS_PACKAGE)) {
            context.startActivity(googleIntent)
            return
        }
        // 兜底：系统通用 geo 链接
        runCatching {
            context.startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("geo:$lat,$lng?q=$lat,$lng(${Uri.encode(name)})"),
                ),
            )
        }
    }

    private fun isAppInstalled(context: Context, packageName: String): Boolean = try {
        context.packageManager.getPackageInfo(packageName, 0) != null
    } catch (_: Exception) {
        false
    }

    /** 获取最近一次已知位置（GPS 优先，网络定位兜底） */
    fun lastKnownLocation(context: Context): Location? {
        if (!hasLocationPermission(context)) return null
        return try {
            val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
            manager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: manager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * 获取当前位置（挂起函数）：
     * 优先返回最近已知位置；若为空则主动请求一次定位，
     * [timeoutMs] 内无结果返回 null。
     * 调用前已检查定位权限（hasLocationPermission），异常已捕获。
     */
    @SuppressLint("MissingPermission")
    suspend fun currentLocation(context: Context, timeoutMs: Long = 5000): Location? {
        lastKnownLocation(context)?.let { return it }
        if (!hasLocationPermission(context)) return null
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        return suspendCancellableCoroutine { continuation ->
            val listener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    if (continuation.isActive) continuation.resume(location)
                }

                override fun onProviderEnabled(provider: String) = Unit

                override fun onProviderDisabled(provider: String) = Unit

                @Deprecated("Deprecated in Java")
                override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
            }
            var done = false
            val finish: (Location?) -> Unit = { loc ->
                if (!done) {
                    done = true
                    runCatching { manager.removeUpdates(listener) }
                    if (continuation.isActive) continuation.resume(loc)
                }
            }
            runCatching {
                manager.requestSingleUpdate(LocationManager.GPS_PROVIDER, listener, Looper.getMainLooper())
                manager.requestSingleUpdate(LocationManager.NETWORK_PROVIDER, listener, Looper.getMainLooper())
            }
            continuation.invokeOnCancellation {
                finish(null)
            }
            Thread {
                Thread.sleep(timeoutMs)
                finish(null)
            }.start()
        }
    }

    private fun hasLocationPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
}
