package com.global_707.drone_scanner.ui.map

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import com.global_707.drone_scanner.ui.theme.LocalDroneColors

/**
 * 指南针悬浮按钮（地图软件通用样式）：红色指针指向**正北**，随地图旋转同步转动。
 *
 * - 指针角度 = `-bearing`（地图顺时针转 θ，指针逆时针转 θ 才仍指向北）；
 * - 用最短路径插值，跨越 0°/360° 时不会整圈倒转；
 * - 点击 = 重置北向（由调用方处理）。
 */
@Composable
fun CompassButton(
    bearing: Float,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalDroneColors.current
    // 指针当前角度（不是 bearing 本身，避免 359°→1° 时整圈回甩）
    var needleAngle by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(bearing) {
        val target = -bearing
        val delta = ((target - needleAngle + 540f) % 360f) - 180f
        animate(
            initialValue = needleAngle,
            targetValue = needleAngle + delta,
            animationSpec = tween(240),
        ) { value, _ -> needleAngle = value }
    }

    Box(
        modifier = modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(colors.card.copy(alpha = 0.72f))
            .border(1.dp, colors.border.copy(alpha = 0.4f), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
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
