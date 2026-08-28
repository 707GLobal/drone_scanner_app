package com.global_707.drone_scanner.ui.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.amap.api.maps.AMap
import com.amap.api.maps.CameraUpdateFactory
import com.amap.api.maps.MapView
import com.amap.api.maps.model.BitmapDescriptorFactory
import com.amap.api.maps.model.LatLng
import com.amap.api.maps.model.MarkerOptions
import com.global_707.drone_scanner.data.Drone
import com.global_707.drone_scanner.util.NavigationLauncher

/**
 * 高德地图后端（真实地图）。
 *
 * 依赖：
 *  - API Key：用户级 ~/.gradle/gradle.properties 的 amapApiKey，
 *    经 manifestPlaceholders 注入 AndroidManifest 的 meta-data
 *  - 系统定位权限（显示"我的位置"与"定位到我"需要）
 *
 * 注意：高德 3D SDK 仅包含 ARM 原生库（arm64-v8a / armeabi-v7a），
 * x86_64 模拟器无法加载地图，必须在真机运行。
 */
object AmapMapBackend : MapBackend {

    @Volatile
    private var aMap: AMap? = null

    @Composable
    override fun Content(
        drones: List<Drone>,
        satellite: Boolean,
        showOperator: Boolean,
        showMyLocation: Boolean,
        showLabels: Boolean,
        modifier: Modifier,
    ) {
        val context = LocalContext.current
        val lifecycleOwner = LocalLifecycleOwner.current
        val mapView = remember { MapView(context) }

        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_RESUME -> mapView.onResume()
                    Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                    Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                    else -> Unit
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
                mapView.onDestroy()
                aMap = null
            }
        }

        AndroidView(
            factory = { mapView.apply { onCreate(Bundle()) } },
            modifier = modifier,
            update = { view ->
                val map = view.map
                aMap = map
                map.mapType = if (satellite) AMap.MAP_TYPE_SATELLITE else AMap.MAP_TYPE_NORMAL
                map.clear()

                // 无人机标记（ID Hash 取色 + 呼号标签）
                drones.forEach { drone ->
                    val lat = drone.droneLat ?: return@forEach
                    val lng = drone.droneLng ?: return@forEach
                    map.addMarker(
                        MarkerOptions()
                            .position(LatLng(lat, lng))
                            .anchor(0.5f, markerAnchorY(context, drone.name, showLabels))
                            .title(drone.name)
                            .icon(
                                BitmapDescriptorFactory.fromBitmap(
                                    droneMarkerBitmap(context, drone, showLabels),
                                ),
                            ),
                    )
                }

                // 遥控站标记（绿色，详情页）
                if (showOperator) {
                    drones.forEach { drone ->
                        val lat = drone.operatorLat ?: return@forEach
                        val lng = drone.operatorLng ?: return@forEach
                        map.addMarker(
                            MarkerOptions()
                                .position(LatLng(lat, lng))
                                .anchor(0.5f, 0.5f)
                                .title("遥控站")
                                .icon(
                                    BitmapDescriptorFactory.fromBitmap(
                                        circleMarkerBitmap(context, 0xFF34C759.toInt(), 12),
                                    ),
                                ),
                        )
                    }
                }

                // 我的位置（蓝色小点，详情页）
                if (showMyLocation) {
                    NavigationLauncher.lastKnownLocation(context)?.let { loc ->
                        map.addMarker(
                            MarkerOptions()
                                .position(LatLng(loc.latitude, loc.longitude))
                                .anchor(0.5f, 0.5f)
                                .title("我的位置")
                                .icon(
                                    BitmapDescriptorFactory.fromBitmap(
                                        circleMarkerBitmap(context, 0xFF1E88E5.toInt(), 10),
                                    ),
                                ),
                        )
                    }
                }
            },
        )
    }

    override fun locateMe(context: Context): Boolean {
        val location = NavigationLauncher.lastKnownLocation(context) ?: return false
        aMap?.moveCamera(
            CameraUpdateFactory.newLatLngZoom(
                LatLng(location.latitude, location.longitude),
                15f,
            ),
        )
        return true
    }

    override fun resetNorth(context: Context): Boolean {
        aMap?.moveCamera(CameraUpdateFactory.changeBearing(0f))
        return true
    }

    /** 无人机标记位图：ID Hash 色圆点 + 可选呼号标签 */
    private fun droneMarkerBitmap(context: Context, drone: Drone, showLabels: Boolean): Bitmap {
        val density = context.resources.displayMetrics.density
        val dotRadius = (6 * density).toInt()
        val dotSize = dotRadius * 2
        val color = markerColorArgb(drone)
        val label = if (showLabels) drone.name else null
        val textSize = (10 * density).toInt()
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.textSize = textSize.toFloat()
            this.color = 0xFF000000.toInt()
            this.textAlign = Paint.Align.CENTER
            this.isFakeBoldText = true
        }
        val textWidth = label?.let { textPaint.measureText(it).toInt() } ?: 0
        val bitmapWidth = maxOf(dotSize, textWidth + (4 * density).toInt() * 2)
        val textHeight = if (label != null) (textSize + (2 * density).toInt()) else 0
        val bitmap = Bitmap.createBitmap(bitmapWidth, dotSize + textHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawCircle(
            bitmapWidth / 2f,
            dotRadius.toFloat(),
            dotRadius.toFloat(),
            Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color },
        )
        if (label != null) {
            canvas.drawText(label, bitmapWidth / 2f, dotSize + textSize.toFloat(), textPaint)
        }
        return bitmap
    }

    /** 纯圆点位图（遥控站 / 我的位置） */
    private fun circleMarkerBitmap(context: Context, colorArgb: Int, sizeDp: Int): Bitmap {
        val density = context.resources.displayMetrics.density
        val size = (sizeDp * density).toInt()
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawCircle(
            size / 2f,
            size / 2f,
            size / 2f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = colorArgb },
        )
        return bitmap
    }

    /** 锚点 Y：圆点中心在整张位图中的比例 */
    private fun markerAnchorY(context: Context, name: String, showLabels: Boolean): Float {
        val density = context.resources.displayMetrics.density
        val dotRadius = (6 * density).toInt()
        val dotSize = dotRadius * 2
        val textSize = (10 * density).toInt()
        val textHeight = if (showLabels) textSize + (2 * density).toInt() else 0
        return dotRadius.toFloat() / (dotSize + textHeight)
    }
}
