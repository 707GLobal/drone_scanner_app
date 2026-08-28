package com.global_707.drone_scanner.ui.map

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.global_707.drone_scanner.R
import com.global_707.drone_scanner.data.Drone
import com.global_707.drone_scanner.ui.components.MapGridBackground
import com.global_707.drone_scanner.ui.components.PulseDot
import com.global_707.drone_scanner.ui.theme.DroneTypography
import com.global_707.drone_scanner.ui.theme.LocalDroneColors
import com.global_707.drone_scanner.ui.theme.MapBackgroundColor
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.min

/**
 * 内置占位地图后端（Canvas 绘制）。
 *
 * 用于真实地图 SDK 接入前的界面预览：
 *  - 无需 API Key / 网络，模拟器与真机均可运行
 *  - 无人机标记按设备 ID Hash 取色（不同飞机颜色不同），标记紧凑且只显示呼号（ID 后四位）
 *  - 首页只显示飞机标记；详情页显示 飞机 + 遥控站 + 我的位置 三个动态标记
 *  - 支持 卫星/标准 配色切换
 *
 * 标记位置为归一化示意坐标（真实 SDK 接入后按经纬度绘制）。
 */
class PlaceholderMapBackend : MapBackend {

    /** 无人机标记的归一化摆放位置（0..1） */
    private val dronePositions = listOf(
        Offset(0.25f, 0.28f),
        Offset(0.62f, 0.20f),
        Offset(0.48f, 0.64f),
        Offset(0.32f, 0.55f),
        Offset(0.72f, 0.62f),
    )

    /** 遥控站标记相对无人机的偏移 */
    private val operatorOffset = Offset(-0.07f, 0.075f)

    /** 我的位置示意位置 */
    private val myLocationPos = Offset(0.5f, 0.78f)

    @Composable
    override fun Content(
        drones: List<Drone>,
        satellite: Boolean,
        showOperator: Boolean,
        showMyLocation: Boolean,
        showLabels: Boolean,
        modifier: Modifier,
    ) {
        val colors = LocalDroneColors.current
        // 适配系统深色模式：深色主题下占位地图使用深色底图，避免出格
        val darkTheme = isSystemInDarkTheme()
        val background = when {
            satellite -> Color(0xFF2A3038)
            darkTheme -> Color(0xFF1C1C1E)
            else -> MapBackgroundColor
        }
        val gridColor = if (satellite || darkTheme) Color(0xFF4E5864) else colors.border
        val labelColor = if (satellite || darkTheme) Color.White else Color.Black.copy(alpha = 0.85f)

        BoxWithConstraints(modifier) {
            val w = maxWidth
            val h = maxHeight

            MapGridBackground(
                gridColor = gridColor,
                backgroundColor = background,
                modifier = Modifier.fillMaxSize(),
            )

            // 遥控站 → 无人机 虚线连接（详情页）
            Canvas(Modifier.fillMaxSize()) {
                val dash = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()))
                val lineLength = 32.dp.toPx()
                drones.forEachIndexed { index, drone ->
                    if (!showOperator || drone.operatorLat == null) return@forEachIndexed
                    val dronePos = dronePositions[index % dronePositions.size]
                    val opPos = dronePos + operatorOffset
                    val op = Offset(size.width * opPos.x, size.height * opPos.y)
                    val dp = Offset(size.width * dronePos.x, size.height * dronePos.y)
                    val dist = hypot(dp.x - op.x, dp.y - op.y)
                    if (dist > 0f) {
                        val len = min(lineLength, dist)
                        val ux = (dp.x - op.x) / dist
                        val uy = (dp.y - op.y) / dist
                        drawLine(
                            color = colors.success.copy(alpha = 0.6f),
                            start = op,
                            end = Offset(op.x + ux * len, op.y + uy * len),
                            strokeWidth = 1.5.dp.toPx(),
                            pathEffect = dash,
                        )
                    }
                }
            }

            // 无人机标记：ID Hash 取色 + 呼号（紧凑标签）
            drones.forEachIndexed { index, drone ->
                val pos = dronePositions[index % dronePositions.size]
                val markerColor = colorForDrone(drone)
                PulseDot(
                    color = markerColor,
                    size = 12.dp,
                    modifier = Modifier.offset(
                        x = w * pos.x - 6.dp,
                        y = h * pos.y - 6.dp,
                    ),
                )
                if (showLabels) {
                    Text(
                        text = drone.name,
                        style = DroneTypography.micro,
                        color = labelColor,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.offset(
                            x = w * pos.x - 14.dp,
                            y = h * pos.y + 6.dp,
                        ),
                    )
                }
            }

            // 遥控站标记（绿色，详情页）
            drones.forEachIndexed { index, drone ->
                if (!showOperator || drone.operatorLat == null) return@forEachIndexed
                val pos = dronePositions[index % dronePositions.size]
                val opPos = pos + operatorOffset
                PulseDot(
                    color = colors.success,
                    size = 10.dp,
                    modifier = Modifier.offset(
                        x = w * opPos.x - 5.dp,
                        y = h * opPos.y - 5.dp,
                    ),
                )
            }

            // 我的位置（蓝色小点，详情页）
            if (showMyLocation) {
                PulseDot(
                    color = Color(0xFF1E88E5),
                    size = 8.dp,
                    modifier = Modifier.offset(
                        x = w * myLocationPos.x - 4.dp,
                        y = h * myLocationPos.y - 4.dp,
                    ),
                )
            }

            // 占位提示（无设备时显示）
            if (drones.isEmpty()) {
                Text(
                    text = stringResource(R.string.map_placeholder_hint),
                    style = DroneTypography.label,
                    color = colors.mutedForeground.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
        }
    }

    /** 按设备 ID Hash 取色：不同飞机颜色不同、色调均匀分布 */
    private fun colorForDrone(drone: Drone): Color {
        val hue = (abs(drone.id.hashCode()) % 360).toFloat()
        return Color.hsv(hue, 0.72f, 0.95f)
    }
}
