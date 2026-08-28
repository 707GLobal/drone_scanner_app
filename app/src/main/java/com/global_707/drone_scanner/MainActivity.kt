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
import com.global_707.drone_scanner.data.MockData
import com.global_707.drone_scanner.ui.screens.DroneDetailScreen
import com.global_707.drone_scanner.ui.screens.MapHomeScreen
import com.global_707.drone_scanner.ui.screens.SettingsScreen
import com.global_707.drone_scanner.ui.theme.DroneScannerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DroneScannerTheme {
                DroneScannerApp()
            }
        }
    }
}

/** 应用根导航：地图主页 / 无人机详情 / 设置（参考设计文档第 10 节） */
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
            SettingsScreen(onBack = { route = "map" })
        }
    }
}
