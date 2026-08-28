package com.global_707.drone_scanner

import android.app.Application
import com.tencent.tencentmap.mapsdk.maps.TencentMapInitializer

/**
 * 应用入口：腾讯地图 SDK 初始化。
 *
 * 腾讯地图 SDK 5.x 隐私合规要求：必须在用户同意隐私政策后调用
 * [TencentMapInitializer.setAgreePrivacy] 声明，否则地图引擎不会初始化
 *（getMap() 返回 null、无网络请求、地图黑屏）。
 */
class DroneScannerApp : Application() {

    override fun onCreate() {
        super.onCreate()
        TencentMapInitializer.setAgreePrivacy(true)
    }
}
