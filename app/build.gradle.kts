import org.gradle.api.GradleException

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// ---------------------------------------------------------------------------
// 构建期机密：地图 Key、发布签名及口令，一律来自仓库之外的用户级
// ~/.gradle/gradle.properties（或 CI 环境变量），仓库里不出现任何密钥文件。
// 缺配置时 debug 构建照常；release 打包前会直接失败，不会产出未签名 /
// debug 证书签名 / 占位符 Key 的废包。
// ---------------------------------------------------------------------------

/** 先读 Gradle 属性（用户级 gradle.properties 或 -P 传入），再读同名环境变量。 */
fun secret(propertyName: String, envName: String): String? =
    providers.gradleProperty(propertyName).orNull?.takeIf { it.isNotBlank() }
        ?: providers.environmentVariable(envName).orNull?.takeIf { it.isNotBlank() }

val tencentMapKey: String? = secret("tencentMapKey", "TENCENT_MAP_KEY")

val releaseStoreFile: String? = secret("droneReleaseStoreFile", "DRONE_RELEASE_STORE_FILE")
val releaseStorePassword: String? =
    secret("droneReleaseStorePassword", "DRONE_RELEASE_STORE_PASSWORD")
val releaseKeyAlias: String? = secret("droneReleaseKeyAlias", "DRONE_RELEASE_KEY_ALIAS")
val releaseKeyPassword: String? = secret("droneReleaseKeyPassword", "DRONE_RELEASE_KEY_PASSWORD")

/** 四项齐全才允许 release 出包。 */
val releaseSigningConfigured: Boolean =
    listOf(releaseStoreFile, releaseStorePassword, releaseKeyAlias, releaseKeyPassword)
        .all { it != null }

if (tencentMapKey == null) {
    logger.warn(
        "警告：未配置 tencentMapKey，地图 SDK 会拿到占位符 Key（运行时地图鉴权失败）。" +
            "请在用户级 ~/.gradle/gradle.properties 中配置。"
    )
}
if (!releaseSigningConfigured) {
    logger.info("提示：未配置 release 签名，assembleDebug 不受影响，release 打包会被拒绝。")
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
        versionCode = 3
        versionName = "1.1.5"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // 腾讯位置服务地图 Key（在用户级 ~/.gradle/gradle.properties 配置 tencentMapKey=你的Key）
        manifestPlaceholders["TENCENT_MAP_KEY"] = tencentMapKey ?: "YOUR_TENCENT_MAP_KEY"

        // 仅保留 ARM 原生库：腾讯地图 .so 携带 5 个 ABI 共 ~26MB，其中 x86/x86_64/
        // legacy-armeabi 只服务模拟器/古董设备。RID 检测必须真机（见 AGENTS.md §6），
        // 剔除后 release APK 直减 ~16MB。如需在 x86_64 模拟器上看地图，临时加 "x86_64"。
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
    }

    // 正式发布签名：keystore 本身与口令都放在仓库外，仓库只保留读取逻辑。
    // minSdk 26 起 APK Signature Scheme v2 已足够，v1/v2 的启用交给 AGP 默认值。
    signingConfigs {
        if (releaseSigningConfigured) {
            create("release") {
                storeFile = rootProject.file(releaseStoreFile!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
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
            // 正式证书签名。缺配置时不回落到 debug 证书，由文件末尾的打包前检查拒绝出包。
            if (releaseSigningConfigured) {
                signingConfig = signingConfigs.getByName("release")
            }
            // 开启代码混淆与资源收缩，显著减小 APK
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

// ------------------------------- 打包前强制检查 -------------------------------
// release 任务一旦跑到打包阶段，缺签名或缺地图 Key 就直接失败并给出可操作提示，
// 避免再次产出 "-unsigned" 或 debug 证书签名的发布包。

val signingHint = """
    |release 签名未配置完成，拒绝打包（避免产出未签名或 debug 证书签名的包）。
    |请在用户级 ~/.gradle/gradle.properties 中写入以下四项，keystore 放在仓库之外：
    |  droneReleaseStoreFile=C:\Users\<用户名>\.android\keystores\drone-scanner-release.jks
    |  droneReleaseStorePassword=<口令>
    |  droneReleaseKeyAlias=<别名>
    |  droneReleaseKeyPassword=<口令>
    |CI 环境可用环境变量 DRONE_RELEASE_STORE_FILE / DRONE_RELEASE_STORE_PASSWORD /
    |DRONE_RELEASE_KEY_ALIAS / DRONE_RELEASE_KEY_PASSWORD 注入。
""".trimMargin()

val mapKeyHint = """
    |未配置 tencentMapKey，release 包的地图会鉴权失败（空白地图），拒绝打包。
    |请在用户级 ~/.gradle/gradle.properties 中写入：tencentMapKey=<你的腾讯位置服务 Key>
""".trimMargin()

tasks.configureEach {
    if (name.startsWith("package") && name.endsWith("Release")) {
        doFirst {
            if (!releaseSigningConfigured) throw GradleException(signingHint)
            if (tencentMapKey == null) throw GradleException(mapKeyHint)
        }
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
