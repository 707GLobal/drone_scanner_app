package com.global_707.drone_scanner

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.global_707.drone_scanner.data.AppPrefs
import com.global_707.drone_scanner.data.MockData
import com.global_707.drone_scanner.ui.screens.AboutScreen
import com.global_707.drone_scanner.ui.screens.CheckUpdateScreen
import com.global_707.drone_scanner.ui.screens.DisplaySettingsScreen
import com.global_707.drone_scanner.ui.screens.DroneDetailScreen
import com.global_707.drone_scanner.ui.screens.MapHomeScreen
import com.global_707.drone_scanner.ui.screens.MapSettingsScreen
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

/**
 * 应用根导航：
 * 地图主页 → 无人机详情 / 设置
 * 设置 → 搜索设置 / 地图设置 / 显示设置 / 关于我们 / 检查更新
 */
@Composable
private fun DroneScannerApp() {
    var route by rememberSaveable { mutableStateOf("map") }
    var selectedDroneId by rememberSaveable { mutableStateOf<String?>(null) }
    val drones = remember { MockData.drones }

    when (route) {
        "map" -> MapHomeScreen(
            onOpenSettings = { route = "settings" },
            onDroneClick = { drone ->
                selectedDroneId = drone.id
                route = "detail"
            },
        )

        "detail" -> {
            BackHandler { route = "map" }
            val drone = drones.firstOrNull { it.id == selectedDroneId } ?: drones.first()
            DroneDetailScreen(
                drone = drone,
                onBack = { route = "map" },
            )
        }

        "settings" -> {
            BackHandler { route = "map" }
            SettingsScreen(
                onBack = { route = "map" },
                onSearchSettings = { route = "settings/search" },
                onMapSettings = { route = "settings/map" },
                onDisplaySettings = { route = "settings/display" },
                onAbout = { route = "settings/about" },
                onCheckUpdate = { route = "settings/update" },
            )
        }

        "settings/search" -> {
            BackHandler { route = "settings" }
            SearchSettingsScreen(onBack = { route = "settings" })
        }

        "settings/map" -> {
            BackHandler { route = "settings" }
            MapSettingsScreen(onBack = { route = "settings" })
        }

        "settings/display" -> {
            BackHandler { route = "settings" }
            DisplaySettingsScreen(onBack = { route = "settings" })
        }

        "settings/about" -> {
            BackHandler { route = "settings" }
            AboutScreen(onBack = { route = "settings" })
        }

        "settings/update" -> {
            BackHandler { route = "settings" }
            CheckUpdateScreen(onBack = { route = "settings" })
        }
    }
}
