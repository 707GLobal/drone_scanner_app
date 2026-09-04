package com.global_707.drone_scanner.ui.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.global_707.drone_scanner.data.Drone
import com.global_707.drone_scanner.util.CoordinateConverter
import com.global_707.drone_scanner.util.NavigationLauncher
import com.tencent.tencentmap.mapsdk.maps.CameraUpdateFactory
import com.tencent.tencentmap.mapsdk.maps.MapView
import com.tencent.tencentmap.mapsdk.maps.TencentMap
import com.tencent.tencentmap.mapsdk.maps.model.BitmapDescriptorFactory
import com.tencent.tencentmap.mapsdk.maps.model.CameraPosition
import com.tencent.tencentmap.mapsdk.maps.model.LatLng
import com.tencent.tencentmap.mapsdk.maps.model.LatLngBounds
import com.tencent.tencentmap.mapsdk.maps.model.MarkerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * 腾讯位置服务地图后端（真实地图）。
 *
 * 依赖：
 *  - Key：用户级 ~/.gradle/gradle.properties 的 tencentMapKey，
 *    经 manifestPlaceholders 注入 AndroidManifest 的 TencentMapSDK meta-data
 *  - 隐私合规：Application 中调用 TencentMapInitializer.setAgreePrivacy(true)
 *    （否则地图引擎不初始化，getMap() 返回 null、黑屏、零网络请求）
 *
 * 标记设计：
 *  - 无人机：调色板色圆底 + 白色飞机符号（Material 官方素材 path）
 *  - 遥控站：与对应无人机同色圆底 + 白色遥控器符号（Material 官方素材 path）
 *  - 我的位置：Google Maps 风格定位点（半透明蓝外圈 + 白边 + 中心白点）
 *  - 首次加载自动移动相机到第一架飞机位置
 */
object TencentMapBackend : MapBackend {

    @Volatile
    private var tencentMap: TencentMap? = null

    /** 我的位置（地图坐标 + 指南针朝向） */
    private data class MyPosition(val latLng: LatLng, val bearing: Float)

    /**
     * 全局地图引用登记：在 update 回调（地图真正就绪）时登记本页实例；
     * 销毁时仅当全局仍指向自己才清空，避免 AnimatedContent 过渡期间误清其他页面的地图引用。
     * （getMap() 异步就绪，组合期捕获恒为 null——旧写法在此捕获导致清理逻辑永不生效。）
     */
    private class MapRef {
        var registered: TencentMap? = null

        fun register(map: TencentMap) {
            registered = map
            tencentMap = map
        }

        fun unregister() {
            if (tencentMap === registered) tencentMap = null
            registered = null
        }
    }

    /** 已绘制内容的签名：参数均未变化时跳过 map.clear() + 全量重画标记 */
    private data class DrawSignature(
        val drones: List<Drone>,
        val satellite: Boolean,
        val showOperator: Boolean,
        val showLabels: Boolean,
        val myPosition: MyPosition?,
    )

    /** Material 官方「飞机」图标 path（24dp 视口） */
    private const val ICON_FLIGHT =
        "M21 16v-2l-8-5V3.5c0-.83-.67-1.5-1.5-1.5S10 2.67 10 3.5V9l-8 5v2l8-2.5V19l-2 1.5V22l3.5-1 3.5 1v-1.5L13 19v-5.5l8 2.5z"

    /** Material 官方「游戏手柄/遥控器」图标 path（24dp 视口） */
    private const val ICON_GAMEPAD =
        "M21 6H3c-1.1 0-2 .9-2 2v8c0 1.1.9 2 2 2h18c1.1 0 2-.9 2-2V8c0-1.1-.9-2-2-2zm-10 7H8v3H6v-3H3v-2h3V8h2v3h3v2zm4.5 2c-.83 0-1.5-.67-1.5-1.5s.67-1.5 1.5-1.5 1.5.67 1.5 1.5-.67 1.5-1.5 1.5zm4-3c-.83 0-1.5-.67-1.5-1.5S18.67 9 19.5 9s1.5.67 1.5 1.5-.67 1.5-1.5 1.5z"

    private val flightPath: Path by lazy { parsePath(ICON_FLIGHT) }
    private val gamepadPath: Path by lazy { parsePath(ICON_GAMEPAD) }

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
        var cameraMoved by remember { mutableStateOf(false) }
        var lastDrawn by remember { mutableStateOf<DrawSignature?>(null) }
        // 我的位置（主动定位，异步获取后刷新标记；含指南针朝向）
        var myPosition by remember { mutableStateOf<MyPosition?>(null) }

        // 需要显示我的位置时主动请求定位（lastKnown 优先，失败则单次请求）
        LaunchedEffect(showMyLocation) {
            if (showMyLocation) {
                val loc = withContext(Dispatchers.IO) { NavigationLauncher.currentLocation(context) }
                if (loc != null) {
                    myPosition = MyPosition(
                        latLng = toMapLatLng(loc.latitude, loc.longitude),
                        bearing = if (loc.hasBearing()) loc.bearing else 0f,
                    )
                }
            } else {
                myPosition = null
            }
        }

        // 方向传感器（罗盘）：实时驱动我的位置指向箭头
        // 方向 = 设备背面（-Z 轴）的水平方位 = 用户面朝方向。
        // 旋转向量（四元数）→ 旋转矩阵（设备→世界），手写计算以绕开
        // 编译环境下 SensorManager 静态方法的解析问题。
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val rotationSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        DisposableEffect(showMyLocation) {
            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    if (event.sensor.type != Sensor.TYPE_ROTATION_VECTOR) return
                    val v = event.values
                    if (v.size < 3) return
                    // 归一化旋转向量，并补全四元数 w
                    val nx = v[0]
                    val ny = v[1]
                    val nz = v[2]
                    val norm = sqrt(nx * nx + ny * ny + nz * nz)
                    if (norm <= 0f) return
                    val qx = nx / norm
                    val qy = ny / norm
                    val qz = nz / norm
                    val wSq = 1f - qx * qx - qy * qy - qz * qz
                    val qw = if (wSq > 0f) sqrt(wSq) else 0f
                    // 旋转矩阵（设备→世界）第三列 = 设备 Z 轴在世界系的分量：
                    // R[6] = 2(xz - wy), R[7] = 2(yz + wx)
                    // 设备背面 = -Z：水平分量 (-R[6], -R[7])，世界系 X 东 Y 北
                    val east = -(2f * (qx * qz - qw * qy))
                    val north = -(2f * (qy * qz + qw * qx))
                    val azimuth = (
                        (Math.toDegrees(atan2(east.toDouble(), north.toDouble())) + 360.0) % 360.0
                        ).toFloat()
                    val current = myPosition ?: return
                    // 节流：方向变化超过 5° 才刷新标记
                    if (abs(current.bearing - azimuth) > 5f) {
                        myPosition = current.copy(bearing = azimuth)
                    }
                }

                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
            }
            if (showMyLocation) {
                sensorManager?.registerListener(
                    listener,
                    rotationSensor,
                    SensorManager.SENSOR_DELAY_NORMAL,
                )
            }
            onDispose { sensorManager?.unregisterListener(listener) }
        }

        val mapRef = remember { MapRef() }
        DisposableEffect(lifecycleOwner) {
            var destroyed = false
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_START -> mapView.onStart()
                    Lifecycle.Event.ON_RESUME -> mapView.onResume()
                    Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                    Lifecycle.Event.ON_STOP -> mapView.onStop()
                    Lifecycle.Event.ON_DESTROY -> {
                        if (!destroyed) {
                            destroyed = true
                            mapView.onDestroy()
                            mapRef.unregister()
                        }
                    }
                    else -> Unit
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
                if (!destroyed) {
                    destroyed = true
                    mapView.onDestroy()
                    mapRef.unregister()
                }
            }
        }

        Box(modifier) {
            AndroidView(
                // 腾讯 MapView 无需 onCreate；getMap() 在引擎就绪前可能返回 null，需等待（见 mapReady）
                factory = { mapView },
                modifier = Modifier.fillMaxSize(),
                update = update@{ view ->
                    val map = view.getMap() ?: return@update
                    mapRef.register(map)
                    // 绘制签名去重：drones/卫星图/遥控站/标签/我的位置均未变化时，
                    // 跳过 map.clear() + 全量重画（罗盘 >5° 之外的高频重组不再触发）
                    val signature = DrawSignature(
                        drones = drones,
                        satellite = satellite,
                        showOperator = showOperator,
                        showLabels = showLabels,
                        myPosition = if (showMyLocation) myPosition else null,
                    )
                    if (signature == lastDrawn) return@update
                    lastDrawn = signature
                    applyMap(
                        map = map,
                        context = context,
                        drones = drones,
                        satellite = satellite,
                        showOperator = showOperator,
                        myPosition = if (showMyLocation) myPosition else null,
                        showLabels = showLabels,
                    ) { first ->
                        // 首次加载：移动相机到第一架飞机，保证标记可见
                        if (!cameraMoved) {
                            map.moveCamera(CameraUpdateFactory.newLatLngZoom(first, 14f))
                            cameraMoved = true
                        }
                    }
                },
            )

            // 腾讯地图引擎异步初始化：轮询等待 getMap() 非 null 后触发重组
            var mapReady by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) {
                while (!mapReady) {
                    if (mapView.getMap() != null) {
                        mapReady = true
                    } else {
                        delay(100)
                    }
                }
            }
        }
    }

    /** 应用地图配置与标记（地图就绪后调用） */
    private fun applyMap(
        map: TencentMap,
        context: Context,
        drones: List<Drone>,
        satellite: Boolean,
        showOperator: Boolean,
        myPosition: MyPosition?,
        showLabels: Boolean,
        onFirstDrone: (LatLng) -> Unit,
    ) {
        map.mapType = if (satellite) {
            TencentMap.MAP_TYPE_SATELLITE
        } else {
            TencentMap.MAP_TYPE_NORMAL
        }
        map.clear()

        // 无人机标记：调色板色圆底 + 飞机符号 + 呼号标签（WGS-84 → GCJ-02）
        var firstDrone: LatLng? = null
        drones.forEach { drone ->
            val lat = drone.droneLat ?: return@forEach
            val lng = drone.droneLng ?: return@forEach
            val mapPos = toMapLatLng(lat, lng)
            if (firstDrone == null) firstDrone = mapPos
            map.addMarker(
                MarkerOptions()
                    .position(mapPos)
                    .anchor(0.5f, markerAnchorY(context, drone.name, showLabels))
                    .title(drone.name)
                    .icon(
                        BitmapDescriptorFactory.fromBitmap(
                            droneMarkerBitmap(context, drone, showLabels),
                        ),
                    ),
            )
        }
        firstDrone?.let(onFirstDrone)

        // 遥控站标记：与对应无人机同色圆底 + 遥控器符号（详情页）
        if (showOperator) {
            drones.forEach { drone ->
                val lat = drone.operatorLat ?: return@forEach
                val lng = drone.operatorLng ?: return@forEach
                map.addMarker(
                    MarkerOptions()
                        .position(toMapLatLng(lat, lng))
                        .anchor(0.5f, 0.5f)
                        .title("遥控站")
                        .icon(
                            BitmapDescriptorFactory.fromBitmap(
                                operatorMarkerBitmap(context, drone),
                            ),
                        ),
                )
            }
        }

        // 我的位置：Google Maps 风格定位点 + 指南针朝向（详情页）
        if (myPosition != null) {
            map.addMarker(
                MarkerOptions()
                    .position(myPosition.latLng)
                    .anchor(0.5f, 0.5f)
                    .title("我的位置")
                    .icon(
                        BitmapDescriptorFactory.fromBitmap(
                            myLocationBitmap(context, myPosition.bearing),
                        ),
                    ),
            )
        }
    }

    override fun locateMe(context: Context): Boolean {
        val location = NavigationLauncher.lastKnownLocation(context) ?: return false
        val (lat, lng) = CoordinateConverter.wgs84ToGcj02(location.latitude, location.longitude)
        tencentMap?.moveCamera(
            CameraUpdateFactory.newLatLngZoom(LatLng(lat, lng), 15f),
        )
        return true
    }

    override fun moveTo(lat: Double, lng: Double, zoom: Float): Boolean {
        val (mapLat, mapLng) = CoordinateConverter.wgs84ToGcj02(lat, lng)
        tencentMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(mapLat, mapLng), zoom))
        return true
    }

    override fun moveToDrones(drones: List<Drone>): Boolean {
        val map = tencentMap ?: return false
        val points = drones.mapNotNull { drone ->
            val lat = drone.droneLat ?: return@mapNotNull null
            val lng = drone.droneLng ?: return@mapNotNull null
            toMapLatLng(lat, lng)
        }
        if (points.isEmpty()) return false
        if (points.size == 1) {
            map.moveCamera(CameraUpdateFactory.newLatLngZoom(points[0], 15f))
            return true
        }
        // 多架：视野框住所有飞机，四周留白（内容约占 60% 区域）
        val bounds = LatLngBounds.builder().apply { points.forEach { include(it) } }.build()
        val padding = 200
        map.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, padding))
        return true
    }

    override fun resetNorth(context: Context): Boolean {
        val map = tencentMap ?: return false
        val current = map.cameraPosition
        val builder = CameraPosition.builder()
            .target(current.target)
            .zoom(current.zoom)
            .bearing(0f)
        map.moveCamera(CameraUpdateFactory.newCameraPosition(builder.build()))
        return true
    }

    /** WGS-84 原始坐标 → 腾讯地图（GCJ-02）坐标 */
    private fun toMapLatLng(lat: Double, lng: Double): LatLng {
        val (mapLat, mapLng) = CoordinateConverter.wgs84ToGcj02(lat, lng)
        return LatLng(mapLat, mapLng)
    }

    // ---------- 标记位图绘制 ----------

    /** 无人机标记：调色板色圆底 + 白色飞机符号 + 可选呼号标签 */
    private fun droneMarkerBitmap(context: Context, drone: Drone, showLabels: Boolean): Bitmap {
        val density = context.resources.displayMetrics.density
        val dotRadius = (9 * density).toInt()
        val dotSize = dotRadius * 2
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
        val cx = bitmapWidth / 2f

        canvas.drawCircle(
            cx,
            dotRadius.toFloat(),
            dotRadius.toFloat(),
            Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = markerColorArgb(drone) },
        )
        drawIconOnCanvas(canvas, flightPath, cx, dotRadius.toFloat(), dotRadius.toFloat(), 0xFFFFFFFF.toInt())

        if (label != null) {
            canvas.drawText(label, cx, dotSize + textSize.toFloat(), textPaint)
        }
        return bitmap
    }

    /** 遥控站标记：与对应无人机同色圆底 + 白色遥控器符号 */
    private fun operatorMarkerBitmap(context: Context, drone: Drone): Bitmap {
        val density = context.resources.displayMetrics.density
        val dotRadius = (9 * density).toInt()
        val dotSize = dotRadius * 2
        val bitmap = Bitmap.createBitmap(dotSize, dotSize, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawCircle(
            dotRadius.toFloat(),
            dotRadius.toFloat(),
            dotRadius.toFloat(),
            Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = markerColorArgb(drone) },
        )
        drawIconOnCanvas(
            canvas,
            gamepadPath,
            dotRadius.toFloat(),
            dotRadius.toFloat(),
            dotRadius.toFloat(),
            0xFFFFFFFF.toInt(),
        )
        return bitmap
    }

    /**
     * 我的位置：Google Maps 风格定位点
     * （淡蓝外圈 + 白边蓝点 + 蓝色指南针朝向箭头，参考高德/Google 地图）
     */
    private fun myLocationBitmap(context: Context, bearing: Float): Bitmap {
        val density = context.resources.displayMetrics.density
        val size = (34 * density).toInt()
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val c = size / 2f
        val white = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = 0xFFFFFFFF.toInt() }
        val blue = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = 0xFF1A73E8.toInt() }

        // 淡蓝外圈（定位精度示意）
        canvas.drawCircle(c, c, 15f * density, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = 0x1A1A73E8
        })
        // 蓝色朝向箭头（醒目：尺寸接近圆球，蓝色带白描边提升对比度）
        val arrow = Path().apply {
            moveTo(c, c - 13f * density)
            lineTo(c - 6f * density, c - 2.5f * density)
            lineTo(c + 6f * density, c - 2.5f * density)
            close()
        }
        canvas.save()
        canvas.rotate(bearing, c, c)
        canvas.drawPath(arrow, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = 0xFFFFFFFF.toInt()
            this.style = Paint.Style.STROKE
            this.strokeWidth = 1.2f * density
        })
        canvas.drawPath(arrow, blue)
        canvas.restore()
        // 白边蓝点
        canvas.drawCircle(c, c, 9f * density, white)
        canvas.drawCircle(c, c, 7f * density, blue)
        // 中心白点
        canvas.drawCircle(c, c, 2.2f * density, white)
        return bitmap
    }

    /** 在画布上按比例居中绘制 24dp 视口的图标 path */
    private fun drawIconOnCanvas(canvas: Canvas, path: Path, cx: Float, cy: Float, maxRadius: Float, color: Int) {
        val iconHalfSize = 10f
        val scale = (maxRadius * 1.25f) / iconHalfSize
        canvas.save()
        canvas.translate(cx - 12f * scale, cy - 12f * scale)
        canvas.scale(scale, scale)
        canvas.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            this.style = Paint.Style.FILL
        })
        canvas.restore()
    }

    /** 锚点 Y：圆点中心在整张位图中的比例 */
    private fun markerAnchorY(context: Context, name: String, showLabels: Boolean): Float {
        val density = context.resources.displayMetrics.density
        val dotRadius = (9 * density).toInt()
        val dotSize = dotRadius * 2
        val textSize = (10 * density).toInt()
        val textHeight = if (showLabels) textSize + (2 * density).toInt() else 0
        return dotRadius.toFloat() / (dotSize + textHeight)
    }

    /** 解析 Material pathData 为 android.graphics.Path */
    private fun parsePath(pathData: String): Path =
        PathParser().parsePathString(pathData).toPath().asAndroidPath()
}
