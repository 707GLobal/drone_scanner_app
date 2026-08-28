package com.global_707.drone_scanner.data

/**
 * 地图标记位置（归一化 0..1 坐标）。
 * 依据设计稿 375dp 宽 / 452dp 高地图换算：
 * 无人机标记 18dp、飞手标记 12dp，位置见设计文档第 4.2 节。
 */
data class MapMarker(
    val droneX: Float,
    val droneY: Float,
    val operatorX: Float? = null,
    val operatorY: Float? = null,
)

object MockData {

    val drones = listOf(
        Drone(
            id = "drone-1",
            name = "DJI Mavic 3",
            snCode = "3P7XK0A2B1C4D5E6",
            ridCode = "RID-2024-0001",
            heightM = 120f,
            distanceM = 350f,
            speedMs = 12.5f,
            signalStrength = 4,
            protocol = "WiFi Broadcast",
            frequency = "2.4 GHz",
            droneLat = 31.2304,
            droneLng = 121.4737,
            operatorLat = 31.2312,
            operatorLng = 121.4751,
            operatorId = "OP-2024-7852",
            operatorPhone = "138****5678",
            operatorDistanceM = 280,
            lastSeenSeconds = 5,
            status = DroneStatus.SAFE,
        ),
        Drone(
            id = "drone-2",
            name = "Autel EVO II",
            snCode = "4T8YL1B3C2D5E6F7",
            ridCode = "RID-2024-0002",
            heightM = 85f,
            distanceM = 480f,
            speedMs = 8.2f,
            signalStrength = 3,
            protocol = "WiFi Broadcast",
            frequency = "5.8 GHz",
            droneLat = 31.2291,
            droneLng = 121.4788,
            operatorLat = 31.2299,
            operatorLng = 121.4795,
            operatorId = "OP-2024-6610",
            operatorPhone = "139****2341",
            operatorDistanceM = 420,
            lastSeenSeconds = 12,
            status = DroneStatus.WARNING,
        ),
        Drone(
            id = "drone-3",
            name = "未知设备",
            snCode = "UNKNOWN-DEVICE-03",
            ridCode = "—",
            heightM = 45f,
            distanceM = 120f,
            speedMs = 5.1f,
            signalStrength = 2,
            protocol = "Unknown",
            frequency = "—",
            droneLat = 31.2265,
            droneLng = 121.4702,
            operatorLat = null,
            operatorLng = null,
            operatorId = null,
            operatorPhone = null,
            operatorDistanceM = 95,
            lastSeenSeconds = 25,
            status = DroneStatus.DANGER,
        ),
    )

    /** 与 [drones] 一一对应的地图标记位置 */
    val markers = listOf(
        MapMarker(
            droneX = 95f / 375f, droneY = 130f / 452f,
            operatorX = 70f / 375f, operatorY = 158f / 452f,
        ),
        MapMarker(
            droneX = 240f / 375f, droneY = 90f / 452f,
            operatorX = 268f / 375f, operatorY = 115f / 452f,
        ),
        MapMarker(
            droneX = 180f / 375f, droneY = 290f / 452f,
            operatorX = null, operatorY = null,
        ),
    )

    val scanState = ScanState(
        isScanning = true,
        wifiScanning = true,
        bluetoothScanning = true,
    )
}
