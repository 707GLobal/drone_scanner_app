package com.global_707.drone_scanner.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.global_707.drone_scanner.ui.theme.DroneTypography
import com.global_707.drone_scanner.ui.theme.LocalDroneColors
import kotlin.math.cos
import kotlin.math.sin

/**
 * 全局通用组件（参考设计文档第 3 节）。
 * 注：毛玻璃效果在 Android 上以「半透明 card 背景 + 边框」近似实现。
 */

/** 脉冲圆点（无人机标记 / 飞手标记 / 扫描点 / 实时状态点） */
@Composable
fun PulseDot(
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 18.dp,
    pulseScale: Float = 1.8f,
    durationMillis: Int = 2000,
) {
    val transition = rememberInfiniteTransition(label = "pulse")
    val scale by transition.animateFloat(
        initialValue = 1f,
        targetValue = pulseScale,
        animationSpec = infiniteRepeatable(tween(durationMillis, easing = FastOutSlowInEasing)),
        label = "pulseScale",
    )
    val alpha by transition.animateFloat(
        initialValue = 0.6f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(durationMillis, easing = FastOutSlowInEasing)),
        label = "pulseAlpha",
    )
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(size * scale)
                .background(color.copy(alpha = alpha), CircleShape),
        )
        Box(
            Modifier
                .size(size)
                .background(color, CircleShape),
        )
    }
}

/** 毛玻璃胶囊：地图页左上角设置按钮、右上角扫描状态按钮 */
@Composable
fun FrostedPill(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    val colors = LocalDroneColors.current
    val shape = RoundedCornerShape(50)
    Row(
        modifier = modifier
            .clip(shape)
            .background(colors.card.copy(alpha = 0.72f))
            .border(1.dp, colors.border.copy(alpha = 0.4f), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

/** 毛玻璃条：详情页固定底部操作栏背景 */
@Composable
fun FrostedBar(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = LocalDroneColors.current
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
            .background(colors.card.copy(alpha = 0.85f)),
        content = content,
    )
}

/** 扫描中加载指示器：14dp 圆环，无限旋转 */
@Composable
fun ScanningSpinner(modifier: Modifier = Modifier, size: Dp = 14.dp) {
    val colors = LocalDroneColors.current
    val transition = rememberInfiniteTransition(label = "spin")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(800, easing = LinearEasing)),
        label = "spinAngle",
    )
    androidx.compose.foundation.Canvas(modifier.size(size)) {
        val stroke = 2.dp.toPx()
        drawArc(
            color = colors.muted,
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            style = Stroke(width = stroke),
        )
        drawArc(
            color = colors.primary,
            startAngle = angle,
            sweepAngle = 120f,
            useCenter = false,
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
    }
}

/** 信号强度柱：4 根，高度依次 4/8/12/16dp（参考设计文档第 5.5 节） */
@Composable
fun SignalBars(level: Int, color: Color, modifier: Modifier = Modifier) {
    val heights = listOf(4.dp, 8.dp, 12.dp, 16.dp)
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        heights.forEachIndexed { index, h ->
            Box(
                Modifier
                    .width(4.dp)
                    .height(h)
                    .background(
                        color = if (index < level) color else color.copy(alpha = 0.25f),
                        shape = RoundedCornerShape(2.dp),
                    ),
            )
        }
    }
}

/** 雷达图标：中心圆点 + 8 条放射线（设置页 App 图标） */
@Composable
fun RadarGlyph(color: Color, modifier: Modifier = Modifier) {
    androidx.compose.foundation.Canvas(modifier) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val r = size.minDimension / 2f
        val lineLength = r - 4.dp.toPx()
        drawCircle(color, radius = 2.5.dp.toPx(), center = Offset(cx, cy))
        for (i in 0 until 8) {
            val angle = Math.toRadians(i * 45.0)
            val end = Offset(
                x = cx + lineLength * cos(angle).toFloat(),
                y = cy + lineLength * sin(angle).toFloat(),
            )
            drawLine(color, Offset(cx, cy), end, strokeWidth = 1.5.dp.toPx(), cap = StrokeCap.Round)
        }
    }
}

/** 网格地图占位背景 */
@Composable
fun MapGridBackground(
    gridColor: Color,
    backgroundColor: Color,
    modifier: Modifier = Modifier,
    cellSize: Dp = 50.dp,
) {
    androidx.compose.foundation.Canvas(modifier) {
        drawRect(backgroundColor)
        val step = cellSize.toPx()
        val lineColor = gridColor.copy(alpha = 0.3f)
        var x = 0f
        while (x <= size.width) {
            drawLine(lineColor, Offset(x, 0f), Offset(x, size.height), 1.dp.toPx())
            x += step
        }
        var y = 0f
        while (y <= size.height) {
            drawLine(lineColor, Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
            y += step
        }
    }
}

/** 状态圆点（列表项左侧） */
@Composable
fun StatusDot(color: Color, modifier: Modifier = Modifier, size: Dp = 10.dp) {
    Box(modifier.size(size).background(color, CircleShape))
}

/** 信息卡片：card 背景，16dp 圆角，16dp 内边距 */
@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = LocalDroneColors.current
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(colors.card)
            .padding(16.dp),
        content = content,
    )
}

/** 图标 + 文字行（扫描状态面板行），尾部可放 spinner */
@Composable
fun IconLabelRow(
    icon: ImageVector,
    text: String,
    modifier: Modifier = Modifier,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    val colors = LocalDroneColors.current
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = colors.foreground,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = text,
            style = DroneTypography.captionMedium,
            color = colors.foreground,
            modifier = Modifier.weight(1f),
        )
        trailing()
    }
}
