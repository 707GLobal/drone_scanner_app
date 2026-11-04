package com.global_707.drone_scanner.ui.map

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.global_707.drone_scanner.ui.theme.AppMotion
import com.global_707.drone_scanner.ui.theme.DroneTypography
import com.global_707.drone_scanner.ui.theme.LocalDroneColors

/**
 * 地图浮层的**唯一设计规范实现**（改这里等于改全部地图页）。
 *
 * 三种形态：
 *  - [MapChromeButton]    圆形图标钮（图层 / 定位 / 指北）
 *  - [MapChromePill]      图标+文字药丸（设置 / 扫描状态 / 全屏查看）
 *  - [MapPopupSurface]    所有派生菜单的统一外壳（同一套动画、同一个锚点规则）
 *
 * 按钮四态（所有浮层控件一致）：
 *  1. 默认：`colors.card@0.72` 背景 + 1dp `colors.border@0.4` 描边；
 *  2. 按下：整体缩放到 0.96（160ms）；
 *  3. 激活：**仅当它自己的菜单打开时**，描边 1.5dp 主色（0.9 不透明度）；
 *  4. 状态点：右上角 6dp 主色圆点，表示"该控件关联的功能处于开启状态"（例如有叠加图层开启）；
 *     状态点与激活描边是两件事，不要用描边表达持续状态。
 */
@Composable
fun MapChromeButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false,
) {
    MapChromeButtonContainer(
        onClick = onClick,
        modifier = modifier,
        active = active,
        description = contentDescription,
    ) {
        val colors = LocalDroneColors.current
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = colors.primary.copy(alpha = 0.92f),
            modifier = Modifier.size(20.dp),
        )
    }
}

/** 圆形按钮容器（自定义内容用，例如指南针指针） */
@Composable
fun MapChromeButtonContainer(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    description: String? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val colors = LocalDroneColors.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = tween(AppMotion.PressMs),
        label = "chromePress",
    )

    Box(
        modifier = modifier
            .size(44.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .semantics { if (description != null) this.contentDescription = description }
            .clip(CircleShape)
            .background(colors.card.copy(alpha = 0.72f))
            .border(
                width = if (active) 1.5.dp else 1.dp,
                color = if (active) {
                    colors.primary.copy(alpha = 0.9f)
                } else {
                    colors.border.copy(alpha = 0.4f)
                },
                shape = CircleShape,
            )
            .clickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

/** 药丸容器（与圆钮同一套背景/描边/按压动画）；内容由调用方提供 */
@Composable
fun MapChromePill(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    val colors = LocalDroneColors.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = tween(AppMotion.PressMs),
        label = "chromePillPress",
    )
    val shape = RoundedCornerShape(50)

    Row(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .background(colors.card.copy(alpha = 0.72f))
            .border(1.dp, colors.border.copy(alpha = 0.4f), shape)
            .clickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                onClick = onClick,
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp),
        content = content,
    )
}

/** 图标+文字药丸（详情页「全屏查看」等） */
@Composable
fun MapChromeLabelPill(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalDroneColors.current
    MapChromePill(onClick = onClick, modifier = modifier) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = colors.primary,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = label,
            style = DroneTypography.captionMedium,
            color = colors.foreground,
        )
    }
}

/**
 * 所有派生菜单的统一外壳：Popup + 同一套出入场动画（缩放 0.92 + 淡入 160ms）。
 *
 * **非焦点 Popup**（`focusable = false` + 不自动关闭）：
 *  - 点击菜单外的其它悬浮按钮可**直接穿透**到下层窗口 → 实现"点另一个按钮 = 换菜单"，
 *    不会被"点外部仅关闭"吃掉第一次点击（1.1.5 的不对称问题）；
 *  - 关闭由页面里的全屏"点击遮罩" + 返回键统一处理（见各页面）。
 */
@Composable
fun MapPopupSurface(
    alignment: Alignment,
    offset: IntOffset,
    content: @Composable () -> Unit,
) {
    val visibleState = remember { MutableTransitionState(false).apply { targetState = true } }
    val originX = if (alignment == Alignment.TopStart || alignment == Alignment.BottomStart) 0f else 1f
    val originY = if (alignment == Alignment.TopStart || alignment == Alignment.TopEnd) 0f else 0.5f
    val origin = TransformOrigin(originX, originY)

    Popup(
        alignment = alignment,
        offset = offset,
        // 不抢焦点、不自动关闭：点击穿透给下层按钮，关闭由页面遮罩/返回键处理
        properties = PopupProperties(
            focusable = false,
            dismissOnClickOutside = false,
            dismissOnBackPress = false,
        ),
    ) {
        AnimatedVisibility(
            visibleState = visibleState,
            enter = scaleIn(initialScale = 0.95f, transformOrigin = origin) + fadeIn(tween(AppMotion.MenuEnterMs)),
            exit = scaleOut(targetScale = 0.95f, transformOrigin = origin) + fadeOut(tween(AppMotion.MenuExitMs)),
        ) {
            content()
        }
    }
}

/** 菜单分组标题（图层/扫描菜单共用） */
@Composable
fun MapMenuGroupLabel(text: String) {
    val colors = LocalDroneColors.current
    Text(
        text = text,
        style = DroneTypography.label,
        color = colors.mutedForeground,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 2.dp),
    )
}

/**
 * 菜单底部动作行（「图层设置」「前往搜索设置」共用）：图标 + 正文色文字 + 右侧 chevron。
 * 两个菜单的动作行必须用这一个实现，避免视觉分叉。
 */
@Composable
fun MapMenuActionRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    val colors = LocalDroneColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = colors.primary,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = label,
            style = DroneTypography.body,
            color = colors.foreground,
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = Icons.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = colors.mutedForeground,
            modifier = Modifier.size(16.dp),
        )
    }
}

/**
 * 菜单宽度：基准 252dp，小屏时最多留 32dp 边距（保证不越界）。
 * 所有弹出菜单都用它，不要直接写 252.dp。
 */
@Composable
fun mapMenuWidth(): androidx.compose.ui.unit.Dp {
    val screenWidth = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp
    return minOf(252, (screenWidth - 32).coerceAtLeast(200)).dp
}
