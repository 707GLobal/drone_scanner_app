package com.global_707.drone_scanner.ui.map

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.global_707.drone_scanner.ui.theme.LocalDroneColors

/**
 * 地图悬浮控件统一样式（**设计规范**，改这里等于改全部地图页）：
 *  - 44dp 圆形、`card@0.72` 背景、1dp 描边（激活时主色 1.5dp）
 *  - 图标 20dp，主色
 *  - 弹出菜单：同一套"缩放 0.92 → 1 + 淡入"动画（160ms），锚点与偏移按 [MapPopupSurface] 传
 *
 * 新增地图控件请复用这两个组件，不要各写一套透明度/动画。
 */
@Composable
fun MapFloatingButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false,
) {
    val colors = LocalDroneColors.current
    Box(
        modifier = modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(colors.card.copy(alpha = 0.72f))
            .border(
                width = if (active) 1.5.dp else 1.dp,
                color = if (active) colors.primary.copy(alpha = 0.8f) else colors.border.copy(alpha = 0.4f),
                shape = CircleShape,
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = colors.primary.copy(alpha = 0.92f),
            modifier = Modifier.size(20.dp),
        )
    }
}

/**
 * 地图弹出菜单外壳：统一 Popup 属性 + 统一出入场动画。
 * 第一个参数是 Popup 相对锚点（通常是一个包住按钮的 Box）的对齐方式。
 */
@Composable
fun MapPopupSurface(
    alignment: Alignment,
    offset: IntOffset,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    val visibleState = remember { MutableTransitionState(false).apply { targetState = true } }
    val enterOrigin = if (alignment == Alignment.TopStart) {
        TransformOrigin(0f, 0f)
    } else {
        TransformOrigin(0f, 0.5f)
    }
    Popup(
        alignment = alignment,
        offset = offset,
        onDismissRequest = onDismiss,
        properties = PopupProperties(
            focusable = true,
            dismissOnClickOutside = true,
            dismissOnBackPress = true,
        ),
    ) {
        AnimatedVisibility(
            visibleState = visibleState,
            enter = scaleIn(initialScale = 0.92f, transformOrigin = enterOrigin) + fadeIn(tween(160)),
            exit = scaleOut(targetScale = 0.92f, transformOrigin = enterOrigin) + fadeOut(tween(120)),
        ) {
            content()
        }
    }
}
