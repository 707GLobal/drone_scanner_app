package com.global_707.drone_scanner

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.global_707.drone_scanner.data.AppLogger
import com.global_707.drone_scanner.data.AppPrefs
import com.global_707.drone_scanner.data.ScannerController
import com.global_707.drone_scanner.ui.screens.AboutScreen
import com.global_707.drone_scanner.ui.theme.AppMotion
import com.global_707.drone_scanner.ui.screens.DisplaySettingsScreen
import com.global_707.drone_scanner.ui.screens.DroneDetailScreen
import com.global_707.drone_scanner.ui.screens.FullscreenMapScreen
import com.global_707.drone_scanner.ui.screens.LayerSettingsScreen
import com.global_707.drone_scanner.ui.screens.LogSettingsScreen
import com.global_707.drone_scanner.ui.screens.MapHomeScreen
import com.global_707.drone_scanner.ui.screens.SearchSettingsScreen
import com.global_707.drone_scanner.ui.screens.SettingsScreen
import com.global_707.drone_scanner.ui.theme.DroneScannerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppPrefs.init(applicationContext)
        // 日志模块：按用户设置决定是否自动开始记录（自动模式）
        AppLogger.init(applicationContext, AppPrefs.logMode)
        enableEdgeToEdge()
        setContent {
            DroneScannerTheme {
                DroneScannerRoot()
            }
        }
    }
}

/** 页面层级（用于判断导航前进/后退方向） */

/**
 * 应用根导航（带 Material 页面过渡动画）：
 * 地图主页 → 飞机详情 → 全屏地图
 * 设置 → 搜索设置 / 显示设置 / 关于我们
 */
/** 一个导航条目：路由 + 参数（详情/全屏带的无人机 id）。参数只属于该条目，不再用全局变量。 */
private data class NavEntry(val route: String, val droneId: String? = null)

/**
 * 应用根导航（显式返回栈，语义对齐 Android 导航原则）：
 *  - 一个 destination 只渲染一种界面；**参数失效就退栈**，绝不把首页塞进详情路由；
 *  - 返回 = pop 当前条目，回到"进入时的那个页面"；
 *  - 重复的「路由 + 参数」不入栈。
 */
@Composable
private fun DroneScannerRoot() {
    val backStack = rememberSaveable(
        saver = listSaver<SnapshotStateList<NavEntry>, String>(
            save = { entries -> entries.flatMap { listOf(it.route, it.droneId.orEmpty()) } },
            restore = { saved ->
                saved.chunked(2)
                    .map { NavEntry(it[0], it[1].ifEmpty { null }) }
                    .toMutableStateList()
            },
        ),
    ) { mutableStateListOf(NavEntry("map")) }
    val currentEntry = backStack.last()
    val currentRoute = currentEntry.route
    // 1 = 前进（新页从右滑入），-1 = 返回（新页从左滑入）
    var navDirection by rememberSaveable { mutableIntStateOf(1) }

    // 扫描只在首页运行：离开首页时停止。
    // 必须放在路由层而不是页面的 onDispose：280ms 转场期间"新首页 start 后旧首页才 dispose"，
    // 旧实现会在返回首页的瞬间把刚启动的扫描 stop 掉（表现为"有时停止扫描"）。
    LaunchedEffect(currentRoute) {
        if (currentRoute != "map") ScannerController.stop()
    }

    // 阻止机器休眠（显示设置，全局生效）
    val activity = LocalActivity.current
    DisposableEffect(AppPrefs.keepScreenOn) {
        val flag = WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        if (AppPrefs.keepScreenOn) {
            activity?.window?.addFlags(flag)
        } else {
            activity?.window?.clearFlags(flag)
        }
        onDispose { activity?.window?.clearFlags(flag) }
    }

    fun push(route: String, droneId: String? = null) {
        val entry = NavEntry(route, droneId)
        if (backStack.last() == entry) return
        navDirection = 1
        backStack.add(entry)
    }

    /** 返回上一页；栈底（地图首页）不处理，交给系统退出 App */
    fun goBack() {
        if (backStack.size <= 1) return
        navDirection = -1
        backStack.removeAt(backStack.lastIndex)
    }

    // Shared Axis 的 30dp 位移（在组合上下文里换算成 px，transitionSpec 内不能读 CompositionLocal）
    val navShiftPx = with(LocalDensity.current) { AppMotion.NavShiftDp.dp.roundToPx() }

    AnimatedContent(
        targetState = currentEntry,
        contentKey = { it.route },
        transitionSpec = {
            // Material Shared Axis X（Google 主流做法）：
            // 两个页面各平移 30dp、共 300ms；旧页前 100ms 淡出，新页延迟 100ms 后用 200ms 淡入。
            // 比整屏滑动柔和，也不会像纯 crossfade 那样"发闷"。
            val shift = navShiftPx
            val forward = navDirection > 0
            val slide: FiniteAnimationSpec<IntOffset> = tween(
                durationMillis = AppMotion.NavSlideMs,
                easing = AppMotion.Easing,
            )
            val inFade = fadeIn(
                tween(
                    durationMillis = AppMotion.NavFadeInMs,
                    delayMillis = AppMotion.NavFadeInDelayMs,
                    easing = AppMotion.Easing,
                ),
            )
            val outFade = fadeOut(tween(AppMotion.NavFadeOutMs))
            (
                slideInHorizontally(slide) { if (forward) shift else -shift } + inFade
                ) togetherWith (
                slideOutHorizontally(slide) { if (forward) -shift else shift } + outFade
                )
        },
        label = "route",
    ) { entry ->
        when (entry.route) {
            "map" -> MapHomeScreen(
                onOpenSettings = { push("settings") },
                onOpenSearchSettings = { push("settings/search") },
                onOpenLayerSettings = { push("settings/layers") },
                onDroneClick = { drone -> push("detail", drone.id) },
            )

            "detail" -> {
                BackHandler { goBack() }
                val drone = ScannerController.drones.firstOrNull { it.id == entry.droneId }
                if (drone == null) {
                    // 参数失效（无人机已消失）：直接退栈，不渲染别的页面
                    LaunchedEffect(entry) { goBack() }
                } else {
                    DroneDetailScreen(
                        drone = drone,
                        onBack = { goBack() },
                        onEnterFullscreen = { push("fullscreen", drone.id) },
                        onOpenLayerSettings = { push("settings/layers") },
                    )
                }
            }

            "fullscreen" -> {
                BackHandler { goBack() }
                val drone = ScannerController.drones.firstOrNull { it.id == entry.droneId }
                if (drone == null) {
                    LaunchedEffect(entry) { goBack() }
                } else {
                    FullscreenMapScreen(
                        drone = drone,
                        onBack = { goBack() },
                        onOpenLayerSettings = { push("settings/layers") },
                    )
                }
            }

            "settings" -> {
                BackHandler { goBack() }
                SettingsScreen(
                    onBack = { goBack() },
                    onSearchSettings = { push("settings/search") },
                    onDisplaySettings = { push("settings/display") },
                    onLayerSettings = { push("settings/layers") },
                    onLogSettings = { push("settings/log") },
                    onAbout = { push("settings/about") },
                )
            }

            "settings/search" -> {
                BackHandler { goBack() }
                SearchSettingsScreen(onBack = { goBack() })
            }

            "settings/display" -> {
                BackHandler { goBack() }
                DisplaySettingsScreen(onBack = { goBack() })
            }

            "settings/layers" -> {
                BackHandler { goBack() }
                LayerSettingsScreen(onBack = { goBack() })
            }

            "settings/log" -> {
                BackHandler { goBack() }
                LogSettingsScreen(onBack = { goBack() })
            }

            "settings/about" -> {
                BackHandler { goBack() }
                AboutScreen(onBack = { goBack() })
            }
        }
    }
}
