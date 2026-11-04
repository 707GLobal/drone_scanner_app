package com.global_707.drone_scanner.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing

/**
 * 全局动效 token（Material 规范收口）。
 *
 * 所有动画时长/曲线从这里取，禁止各页面各写一套（之前出现 120/160/200/240/280 混用）。
 * - 路由切换：Material **Shared Axis X**（双方各平移 30dp、共 300ms；旧页 100ms 淡出，新页延迟 100ms 用 200ms 淡入）
 * - 菜单/弹层：进 160ms、出 120ms
 * - 按压反馈：120ms
 * - 状态切换：200ms
 */
object AppMotion {

    /** Material Shared Axis X：平移 30dp / 总时长 300ms / 旧页淡出 100ms / 新页延迟 100ms 淡入 200ms */
    const val NavSlideMs = 300
    const val NavShiftDp = 30
    const val NavFadeOutMs = 100
    const val NavFadeInMs = 200
    const val NavFadeInDelayMs = 100

    const val MenuEnterMs = 160
    const val MenuExitMs = 120
    const val PressMs = 120
    const val StateMs = 200

    /** Material 3 emphasized 曲线（近似 fast-out-slow-in） */
    val Easing: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
}
