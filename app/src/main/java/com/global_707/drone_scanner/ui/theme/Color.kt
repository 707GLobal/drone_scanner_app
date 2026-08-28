package com.global_707.drone_scanner.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * 设计 token 颜色（参考 Android-UI-Implementation-Reference.md 第 2.1 节）
 * Light / Dark 两套，结构相同，仅色值不同。
 */
data class DroneColors(
    val background: Color,
    val foreground: Color,
    val card: Color,
    val cardForeground: Color,
    val primary: Color,
    val primaryForeground: Color,
    val muted: Color,
    val mutedForeground: Color,
    val border: Color,
    val danger: Color,
    val warning: Color,
    val success: Color,
)

val LightDroneColors = DroneColors(
    background = Color(0xFFF2F2F7),
    foreground = Color(0xFF000000),
    card = Color(0xFFFFFFFF),
    cardForeground = Color(0xFF000000),
    primary = Color(0xFF007AFF),
    primaryForeground = Color(0xFFFFFFFF),
    muted = Color(0xFFE5E5EA),
    mutedForeground = Color(0xFF8E8E93),
    border = Color(0xFFC6C6C8),
    danger = Color(0xFFFF3B30),
    warning = Color(0xFFFF9500),
    success = Color(0xFF34C759),
)

val DarkDroneColors = DroneColors(
    background = Color(0xFF000000),
    foreground = Color(0xFFFFFFFF),
    card = Color(0xFF1C1C1E),
    cardForeground = Color(0xFFFFFFFF),
    primary = Color(0xFF0A84FF),
    primaryForeground = Color(0xFFFFFFFF),
    muted = Color(0xFF2C2C2E),
    mutedForeground = Color(0xFF8E8E93),
    border = Color(0xFF38383A),
    danger = Color(0xFFFF453A),
    warning = Color(0xFFFFA631),
    success = Color(0xFF30D158),
)

/** 地图占位底图背景色（非设计 token，仅用于示意地图区域） */
val MapBackgroundColor = Color(0xFFE9EDF1)
