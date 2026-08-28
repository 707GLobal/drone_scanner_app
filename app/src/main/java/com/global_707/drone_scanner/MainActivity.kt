package com.global_707.drone_scanner

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.global_707.drone_scanner.data.AppPrefs
import com.global_707.drone_scanner.data.ScannerController
import com.global_707.drone_scanner.ui.screens.AboutScreen
import com.global_707.drone_scanner.ui.screens.DisplaySettingsScreen
import com.global_707.drone_scanner.ui.screens.DroneDetailScreen
import com.global_707.drone_scanner.ui.screens.FullscreenMapScreen
import com.global_707.drone_scanner.ui.screens.MapHomeScreen
import com.global_707.drone_scanner.ui.screens.SearchSettingsScreen
import com.global_707.drone_scanner.ui.screens.SettingsScreen
import com.global_707.drone_scanner.ui.theme.DroneScannerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppPrefs.init(applicationContext)
        enableEdgeToEdge()
        setContent {
            DroneScannerTheme {
                DroneScannerApp()
            }
        }
    }
}

/** 页面层级（用于判断导航前进/后退方向） */
private val routeOrder = listOf(
    "map",
    "detail",
    "settings",
    "fullscreen",
    "settings/search",
    "settings/display",
    "settings/about",
)

/**
 * 应用根导航（带 Material 页面过渡动画）：
 * 地图主页 → 飞机详情 → 全屏地图
 * 设置 → 搜索设置 / 显示设置 / 关于我们
 */
@Composable
private fun DroneScannerApp() {
    var route by rememberSaveable { mutableStateOf("map") }
    var navDirection by rememberSaveable { mutableIntStateOf(1) }
    var selectedDroneId by rememberSaveable { mutableStateOf<String?>(null) }
    val selectedDrone = ScannerController.drones.firstOrNull { it.id == selectedDroneId }

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

    fun navigateTo(newRoute: String) {
        navDirection = if (routeOrder.indexOf(newRoute) >= routeOrder.indexOf(route)) 1 else -1
        route = newRoute
    }

    AnimatedContent(
        targetState = route,
        transitionSpec = {
            val forward = navDirection > 0
            if (forward) {
                (slideInHorizontally(tween(280)) { it / 3 } + fadeIn(tween(280)))
                    .togetherWith(slideOutHorizontally(tween(280)) { -it / 4 } + fadeOut(tween(280)))
            } else {
                (slideInHorizontally(tween(280)) { -it / 3 } + fadeIn(tween(280)))
                    .togetherWith(slideOutHorizontally(tween(280)) { it / 4 } + fadeOut(tween(280)))
            }
        },
        label = "route",
    ) { currentRoute ->
        when (currentRoute) {
            "map" -> MapHomeScreen(
                onOpenSettings = { navigateTo("settings") },
                onOpenSearchSettings = { navigateTo("settings/search") },
                onDroneClick = { drone ->
                    selectedDroneId = drone.id
                    navigateTo("detail")
                },
            )

            "detail" -> {
                BackHandler { navigateTo("map") }
                if (selectedDrone != null) {
                    DroneDetailScreen(
                        drone = selectedDrone,
                        onBack = { navigateTo("map") },
                        onEnterFullscreen = { navigateTo("fullscreen") },
                    )
                } else {
                    // 设备已从列表中消失（长时间未检测到），返回首页
                    MapHomeScreen(
                        onOpenSettings = { navigateTo("settings") },
                        onOpenSearchSettings = { navigateTo("settings/search") },
                        onDroneClick = { navigateTo("map") },
                    )
                }
            }

            "fullscreen" -> {
                BackHandler { navigateTo("detail") }
                if (selectedDrone != null) {
                    FullscreenMapScreen(
                        drone = selectedDrone,
                        onBack = { navigateTo("detail") },
                    )
                } else {
                    MapHomeScreen(
                        onOpenSettings = { navigateTo("settings") },
                        onOpenSearchSettings = { navigateTo("settings/search") },
                        onDroneClick = { navigateTo("map") },
                    )
                }
            }

            "settings" -> {
                BackHandler { navigateTo("map") }
                SettingsScreen(
                    onBack = { navigateTo("map") },
                    onSearchSettings = { navigateTo("settings/search") },
                    onDisplaySettings = { navigateTo("settings/display") },
                    onAbout = { navigateTo("settings/about") },
                )
            }

            "settings/search" -> {
                BackHandler { navigateTo("settings") }
                SearchSettingsScreen(onBack = { navigateTo("settings") })
            }

            "settings/display" -> {
                BackHandler { navigateTo("settings") }
                DisplaySettingsScreen(onBack = { navigateTo("settings") })
            }

            "settings/about" -> {
                BackHandler { navigateTo("settings") }
                AboutScreen(onBack = { navigateTo("settings") })
            }
        }
    }
}
