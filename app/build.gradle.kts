plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.global_707.drone_scanner"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.global_707.drone_scanner"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // 腾讯位置服务地图 Key（在用户级 ~/.gradle/gradle.properties 配置 tencentMapKey=你的Key）
        manifestPlaceholders["TENCENT_MAP_KEY"] =
            (project.findProperty("tencentMapKey") as String?) ?: "YOUR_TENCENT_MAP_KEY"

        // 仅保留 ARM 原生库：腾讯地图 .so 携带 5 个 ABI 共 ~26MB，其中 x86/x86_64/
        // legacy-armeabi 只服务模拟器/古董设备。RID 检测必须真机（见 AGENTS.md §6），
        // 剔除后 release APK 直减 ~16MB。如需在 x86_64 模拟器上看地图，临时加 "x86_64"。
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    androidResources {
        // 仅保留中文资源：剔除依赖库携带的几十种语言资源表（应用 UI 全中文）
        localeFilters += listOf("zh")
    }

    buildTypes {
        release {
            // 开启代码混淆与资源收缩，显著减小 APK（高德 SDK keep 规则见 proguard-rules.pro）
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    // 图标：使用 core 集 + 少量自定义（避免 extended 带来的 ~10MB 体积）
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.activity.compose)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // 腾讯位置服务地图 SDK
    implementation(libs.tencent.map.sdk)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}