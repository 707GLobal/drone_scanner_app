package com.global_707.drone_scanner.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * 字体层级（参考设计文档第 2.4 节）
 * 系统默认 sans-serif，等宽字体用于 SN 码、坐标、操作员 ID。
 */
object DroneTypography {
    /** 大标题：详情页无人机名称 */
    val largeTitle = TextStyle(
        fontSize = 22.sp,
        fontWeight = FontWeight.Bold,
        lineHeight = 26.sp,
    )

    /** 页面标题：导航栏标题 */
    val pageTitle = TextStyle(
        fontSize = 17.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeight = 20.sp,
    )

    /** 正文 / 列表主文字 */
    val body = TextStyle(
        fontSize = 15.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeight = 20.sp,
    )

    /** 辅助说明 */
    val caption = TextStyle(
        fontSize = 13.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = 17.sp,
    )

    /** 辅助说明（中粗） */
    val captionMedium = TextStyle(
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        lineHeight = 17.sp,
    )

    /** 小标签 */
    val label = TextStyle(
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        lineHeight = 14.sp,
    )

    /** 微文字：地图缩略图标签、图例 */
    val micro = TextStyle(
        fontSize = 10.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeight = 12.sp,
    )

    /** 指标数值 */
    val metricValue = TextStyle(
        fontSize = 28.sp,
        fontWeight = FontWeight.Bold,
        lineHeight = 31.sp,
    )

    /** 指标单位 */
    val metricUnit = TextStyle(
        fontSize = 14.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = 16.sp,
    )

    /** 等宽数字：SN 码、坐标、操作员 ID */
    val mono = TextStyle(
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        fontFamily = FontFamily.Monospace,
        lineHeight = 16.sp,
    )
}
