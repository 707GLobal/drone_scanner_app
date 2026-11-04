package com.global_707.drone_scanner.ui.map

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import com.global_707.drone_scanner.ui.theme.LocalDroneColors

/**
 * 指南针悬浮按钮：红色指针指向**正北**，随地图旋转同步转动。
 *
 * - 指针角度 = 屏幕上正北的方向角（`MapCameraState.northAngle`，由投影算出），
 *   不依赖各 SDK 对 `bearing` 正负的不同定义，因此不会转反；
 * - 用最短路径插值，跨越 ±180° 时不整圈回甩；
 * - 外观复用 [MapChromeButtonContainer]（与图层/定位按钮同一套尺寸、透明度、描边、按压动画）；
 * - 点击 = 重置北向（由调用方处理）。
 */
@Composable
fun CompassButton(
    northAngle: Float,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalDroneColors.current
    var needleAngle by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(northAngle) {
        val delta = ((northAngle - needleAngle + 540f) % 360f) - 180f
        animate(
            initialValue = needleAngle,
            targetValue = needleAngle + delta,
            animationSpec = tween(240),
        ) { value, _ -> needleAngle = value }
    }

    MapChromeButtonContainer(
        onClick = onClick,
        modifier = modifier,
        description = contentDescription,
    ) {
        Canvas(Modifier.size(24.dp)) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val half = size.minDimension / 2f
            rotate(degrees = needleAngle, pivot = center) {
                // 北针（红）
                val north = Path().apply {
                    moveTo(center.x, center.y - half * 0.92f)
                    lineTo(center.x - half * 0.34f, center.y + half * 0.06f)
                    lineTo(center.x + half * 0.34f, center.y + half * 0.06f)
                    close()
                }
                drawPath(north, Color(0xFFE53935))
                // 南针（灰）
                val south = Path().apply {
                    moveTo(center.x, center.y + half * 0.92f)
                    lineTo(center.x - half * 0.34f, center.y + half * 0.06f)
                    lineTo(center.x + half * 0.34f, center.y + half * 0.06f)
                    close()
                }
                drawPath(south, colors.mutedForeground.copy(alpha = 0.8f))
            }
            // 中心轴
            drawCircle(colors.card, radius = half * 0.1f, center = center)
        }
    }
}
