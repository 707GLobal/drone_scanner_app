# AGENTS.md — 无人机 RID 检测 App（Drone Scanner）

> 面向后续开发/AI 助手的项目速览。请先读本文件，再读 `TESTING.md`（手工测试矩阵）。
> 仓库：https://github.com/707GLobal/drone_scanner_app （分支 `main`，工作区干净）。

## 1. 项目是什么

一款 Android **无人机检测（Remote ID 侦查）** 应用：通过蓝牙与 Wi-Fi 监听附近无人机的
RID（Remote ID，ASTM F3411 / ASD-STAN EN 4709-002）广播，解包后在腾讯地图上展示
序列号、速度、高度、航向、操作员等真实字段。UI 全中文，包名/应用 ID
`com.global_707.drone_scanner`，应用名「无人机检测」，当前版本 v1.0.0。

两条射频通道：

- **蓝牙 BLE**：扫描 Service UUID `0xFFFA–0xFFFF` 广播并完整解包
  （单条 25 字节消息 + Message Pack 0x0F，全部小端序）。
- **Wi-Fi（仅 Android 16+ / API 36+）**：轮询 beacon，检测制造商 IE（ID 221）携带的
  ASTM OUI `FA-0B-9C`（只上报设备，不解包详细字段）。

地图使用**腾讯位置服务地图 SDK**（当前主线 `main` 最后一次提交由高德 AMap 迁移而来）。

## 2. 技术栈与版本（勿随意升级）

| 项 | 值 | 备注 |
|---|---|---|
| Gradle | 9.4.1（wrapper） | `gradlew.bat` 于 Windows 使用 |
| AGP | 9.2.1 | **新 DSL**：`compileSdk { version = release(36) { minorApiLevel = 1 } }`（即 36.1），不要改回旧写法 |
| Kotlin | 2.2.10 | `kotlin.code.style=official` |
| JDK | 21（toolchain，foojay 自动下载） | 见 `gradle/gradle-daemon-jvm.properties` |
| minSdk / targetSdk | 26 / 36 | |
| UI | Jetpack Compose + Material 3，Compose BOM 2025.06.01 | 单 Activity、无 Navigation 库、无 ViewModel、无 DI |
| 图标 | 仅 `material-icons-core` + 自绘图标（`AppIcons`） | **禁止引入 icons-extended**（约 10MB） |
| 地图 | `com.tencent.map:tencent-map-vector-sdk` 5.7.0 | 私有源 `https://mapapi.qq.com/map/mapsdk`（`settings.gradle.kts`） |
| 测试 | JUnit4（本地单测）+ Espresso/JUnit（androidTest 桩） | `androidx.test.ext:junit`、`espresso-core` |
| 协程 | 直接 import `kotlinx.coroutines.*` | 来自 Compose 传递依赖，未显式声明，勿依赖具体版本 |
| APK 体积策略 | `abiFilters` 仅 `arm64-v8a`/`armeabi-v7a`；`localeFilters` 仅 `zh`；依赖仅 core-ktx + Compose + 腾讯 SDK | 模拟器看地图需临时加 `x86_64`；XML 主题只能用框架 `android:Theme.Material*`（已移除 material/appcompat/constraintlayout） |

release 构建开启 minify + shrinkResources；腾讯 SDK 自带 consumer rules
（`app/proguard-rules.pro` 基本只有注释，文件注释为 GBK 编码、乱码属正常，无需处理）。

## 3. 目录结构与代码地图（按包分层）

单模块 `:app`，源码在 `app/src/main/java/com/global_707/drone_scanner/`。

### 入口

- `DroneScannerApp.kt` — `Application`。必须调用 `TencentMapInitializer.setAgreePrivacy(true)`，
  否则腾讯地图引擎不初始化（黑屏、`getMap()` 返回 null）。
- `MainActivity.kt` — 单 Activity。`AppPrefs.init()` → `setContent { DroneScannerTheme { DroneScannerRoot() } }`。

### data/ — 领域与扫描逻辑（纯业务，不依赖 Compose UI）

- `Drone.kt` — 数据模型：`Drone`（RID 解包字段全量映射，未知为 null）、`DroneStatus`、`ScanState`
  （含蓝牙/Wi-Fi 各自错误码：负数=环境问题 -1 不支持 / -2 系统开关未开，正数=系统回调错误码）。
- `BluetoothRidParser.kt` — **纯函数解析器**（对象单例）：BLE Service Data → 单条/Message Pack
  子消息 → `RidMessage`（可 `merge`）。唯一有单元测试的模块。
- `ScannerController.kt` — 扫描控制器单例（对象 + `mutableStateOf` 全局状态）：蓝牙 start/stop/restart、
  Wi-Fi 轮询、跨帧累积合并（`DetectedDevice`）、距离/信号换算、对外 `drones` 列表刷新。
- `AppPrefs.kt` — SharedPreferences + Compose 响应式状态（每项带 `saveXxx`）。
- `DeviceCapabilities.kt` — 实时查询本机 BT4 Legacy / BT5 Extended / Wi-Fi NAN 支持度。
- `MockData.kt` — 演示模式数据（仅演示模式注入，UI 带「演示」标签区分）。

### ui/ — Compose 层

- `screens/MapHomeScreen.kt` — 首页：全屏地图 + 扫描状态按钮/面板 + 可拖拽底部无人机列表面板
  （收起约 1/3 屏、展开约 85%）；**扫描生命周期与权限入口都在这**（见 §4）。
- `screens/DroneDetailScreen.kt` — 详情页 + 全屏地图页 + 共用 `HeaderBar`（M3 CenterAlignedTopAppBar）。
- `screens/SettingsScreen.kt`、`screens/SettingsSubScreens.kt` — 设置主页与
  `SearchSettingsScreen`（开关/能力状态/扫描优先级）、`DisplaySettingsScreen`（阻止休眠）、
  `AboutScreen`（演示模式开关、GitHub 链接、版本号）。
- `map/MapBackend.kt` — **地图后端抽象**：`MapBackend` 接口 + `MapBackendProvider.backend` 全局切换点
  + `DroneMap` Compose 门面 + 标记调色板（10 色，按设备 ID 哈希取模）。
- `map/TencentMapBackend.kt` — 腾讯地图实现（当前唯一后端）。
- `map/MapControls.kt` — 地图悬浮控件（图层/定位/北向），定位按钮弹 Popup 菜单（`LocateTarget`）。
- `components/Components.kt` — 通用组件：`PulseDot`、`FrostedPill`、`FrostedBar`、`ScanningSpinner`、
  `RadarGlyph`、`StatusDot`、`SectionCard`。
- `theme/` — `Color.kt`（Light/Dark `DroneColors` token）、`Theme.kt`（`DroneScannerTheme` +
  `LocalDroneColors` CompositionLocal）、`Type.kt`（`DroneTypography` 字号体系）、
  `AppIcons.kt`（自绘 Material Symbol path 图标，缺图标先加到这里）。

### util/

- `CoordinateConverter.kt` — WGS-84 → GCJ-02（中国地图坐标系偏移，境外坐标原样返回）。
- `NavigationLauncher.kt` — 调起外部导航（高德 → 百度 → Google → geo 兜底）+ 定位辅助。

### 资源 / 测试

- `res/values/strings.xml` — 全部用户可见文案（中文）。
- `src/test/.../BluetoothRidParserTest.kt` — 构造规范字节流验证解析（唯一单测）。
- `TESTING.md` — 手工功能测试矩阵（注意：其中 §0.2 高德 AMap Key/安全码说明在迁移到
  腾讯地图后已过期，Key 配置见 §6）。

## 4. 架构要点与数据流

**全局响应式状态（无 DI）**：`ScannerController` 与 `AppPrefs` 是单例对象，直接持有
`mutableStateOf` 属性；任何 Composable 读取它们即自动订阅重组。改动设置/扫描状态不要另建
状态副本，直接调单例的 `saveXxx` / 控制器方法。

**手动页面导航**：不用 navigation-compose。`MainActivity.DroneScannerRoot` 用字符串 `route`
状态 + `AnimatedContent`（280ms 滑入/淡入过渡）驱动页面切换；前进/后退方向由
`routeOrder` 列表索引比较决定。**新增页面必须登记进 `routeOrder` 并在 `when(route)` 加分支**，
否则过渡方向异常；返回手势用各 route 内的 `BackHandler`。

**扫描生命周期（重要）**：`MapHomeScreen` 首次组合且权限齐备时 `ScannerController.start()`，
`DisposableEffect` `onDispose` 时 `stop()`。即**只有停留在首页时扫描才运行**；进入详情/设置页会停扫，
返回首页重启。`detected` 累积表不清空，因此列表仍保留历史设备。

**RID 数据流水线**：
`BLE ScanResult → BluetoothRidParser.parse(record) → RidMessage → DetectedDevice.merge()`
（统一以**设备 MAC 为 key** 跨帧累积——避免 Basic ID 帧后到时同机分裂；`RidMessage.merge`
语义为非空覆盖）
→ `refresh()` 过滤/映射为 `Drone`（叠加演示数据）→ `drones`（Compose 状态）→ 列表与地图标记重组。

**首页轮询**：每 2s `ScannerController.refreshSystemState()`（读真实系统蓝牙/Wi-Fi 开关，
变化则 `restart()`，通道关闭置对应错误码）。BLE 采用**全量无过滤扫描**（不设 ScanFilter，
回调内用解析器判定，原因见 §9）；Wi-Fi 扫描协程按系统节流间隔（约 20s）触发 `startScan()`，
每 5s 读一次 `scanResults`。

**扫描状态错误码语义**（界面据此提示）：见 `ScanState`；映射文案在 `MapHomeScreen` 的
`channelErrorText()`（环境错误用自定义文案，正数回退显示系统错误码）。

**状态判定**（`ScannerController.toDrone`）：无序列号 → `WARNING`；`idType` 为 3/4（UTM/会话 ID）
→ `WARNING`；否则 `SAFE`。`DroneStatus.DANGER` 目前仅演示数据使用。

**地图后端抽象**：所有页面只调用 `DroneMap`/`MapBackendProvider.backend`/`MapControls`，
严禁在 screen 层直接触碰腾讯 API。要接新地图：实现 `MapBackend`，把
`MapBackendProvider.backend` 指向新实现即可。

## 5. 硬性约定（红线）

1. **数据真实性**：所有展示字段必须来自真实 RID 解包。未解到的字段保持 `null`，界面统一显示
   「—」；**严禁硬编码/杜撰参数**。演示数据只允许 `MockData` 且仅演示模式开启时注入（带「演示」标签）。
2. **坐标系**：RID/GPS 坐标是 WGS-84，中国境内地图（腾讯=GCJ-02）绘制前必须经
   `CoordinateConverter.wgs84ToGcj02`；境外原样。此转换只在地图边界做。
3. **地图 Key / 隐私合规**：Key 经 manifestPlaceholders 从用户级 `~/.gradle/gradle.properties`
   的 `tencentMapKey` 注入；缺失则用占位符（地图鉴权失败）。`setAgreePrivacy(true)` 必须在
   Application 中、任何地图使用前调用。
4. **文案进资源**：用户可见文本一律写 `strings.xml`（中文），代码中不得硬编码（例外：
   `AboutScreen` 的 GitHub URL 常量）。
5. **图标体积**：新图标优先在 `AppIcons` 用 Material Symbols path 自绘；不要引入
   `material-icons-extended`。
6. **设计 token**：颜色/字号只能改 `theme/Color.kt`、`theme/Type.kt` 或 `LocalDroneColors`；
   各 Composable 通过 `LocalDroneColors`/`DroneTypography` 取用，不写死值。
7. **权限按 API 分级**：沿用现有 `neededPermissions()` 模式（12+ 蓝牙、13+ 附近设备）；
   Manifest 中 `BLUETOOTH_SCAN` 声明了 `neverForLocation`；勿随意改动分级。
8. **协议解析保持纯函数**：新消息类型解析放 `BluetoothRidParser`（不碰 Android UI），
   并补充 `BluetoothRidParserTest` 用例。UI 与扫描只能真机验证（见 §6/§7）。
9. **注释与提交**：代码注释/KDoc 用中文（说明协议字节布局、设计意图）；git commit 用英文。
10. **扫描只在首页**：不要在其他页面自行 `start()` 造成双重扫描；如需全局后台扫描，先重构
    控制器生命周期再动手。

## 6. 构建 / 运行 / 配置 Key

Windows 下用 wrapper：

```bash
gradlew.bat assembleDebug                 # 构建 debug APK
gradlew.bat installDebug                  # 安装到已连接真机
gradlew.bat testDebugUnitTest             # 跑单测（BluetoothRidParserTest 6 例）
gradlew.bat assembleRelease               # release（minify + shrink）
```

腾讯地图 Key 配置：

1. 用户级 `C:\Users\<用户名>\.gradle\gradle.properties` 写入
   `tencentMapKey=<你的腾讯位置服务 Key>`（历史配置名为 `amapApiKey`，已随迁移废弃）。
2. Key 需在腾讯位置服务控制台绑定应用包名 `com.global_707.drone_scanner` 与签名 SHA1；
   更换过 debug keystore 后 SHA1 变化会导致地图空白/鉴权失败，需重新绑定并重装 APK。

环境要求：**必须真机**。RID 蓝牙/Wi-Fi 检测需要真实射频硬件；模拟器无法验证。
（`TESTING.md` 中「高德 SDK 仅支持 ARM」等描述来自迁移前，判断时以实际腾讯 SDK 为准。）

## 7. 测试策略

- 唯一自动化测试：`BluetoothRidParserTest`——按 ASTM F3411 规范构造 25 字节小端字节流，
  验证单条/Message Pack/垃圾载荷/未知值解析。改解析器务必同步更新。
- 其余（权限流、扫描面板、底部面板拖拽、演示模式、深色模式、导航按钮、地图标记）
  依赖 `TESTING.md` 手工矩阵，改 UI 后在真机上逐条回归。

## 8. 常见改动入口速查

| 想做什么 | 改哪里 |
|---|---|
| 新增设置项 | `AppPrefs`（属性 + `saveXxx`）→ 设置页 UI → 消费点读取 |
| 新增页面 | 在 `MainActivity.kt` 加 route + `routeOrder` + `when` 分支 + `BackHandler` |
| 解析新 RID 消息类型 | `BluetoothRidParser` + `Drone.kt` 字段 + `ScannerController.DetectedDevice.merge`/`toDrone` + 详情页表格 + 单测 |
| 地图标记样式/相机行为 | `TencentMapBackend`（位图绘制函数在文件底部） |
| 换地图 SDK | 实现 `MapBackend` 并替换 `MapBackendProvider.backend` |
| 扫描策略/频率 | `ScannerController`（`startBluetooth`/`startWifi` 协程周期） |
| 新增自定义图标 | `AppIcons` 追加 Material path |
| UI 文案 | `res/values/strings.xml`（勿硬编码） |

## 9. 已踩过的坑（改代码前先看）

- **BLE 收不到 RID（曾为外场实测主因）**：ASTM F3411 Legacy 广播把 25 字节消息放在
  Service Data AD 中，而 31 字节广播负载装不下独立的 Service-UUID 列表 AD，多数信标
  只带 Service Data；`ScanFilter.setServiceUuid` 只匹配 Service-UUID 列表，会把这类包
  全部过滤掉。因此 BLE 扫描**不得加 Service-UUID 过滤**（全量扫描 + 回调内用
  `BluetoothRidParser.parse()` 判定），并应设 `ScanSettings.setPhy(PHY_LE_ALL_SUPPORTED)`
  覆盖 BT5 扩展/长距广播；同设备合并 key 统一用 MAC，避免 Basic ID 帧后到时同机分裂。
- 腾讯 SDK：忘调 `setAgreePrivacy` 或忘配 Key → 地图黑屏/`getMap()` null；
  `MapView.getMap()` 异步就绪，需轮询（`TencentMapBackend` 的 `mapReady` 逻辑），不要在
  factory 里同步强转。
- 页面切换（AnimatedContent 过渡）期间新旧两页地图并存：全局 `tencentMap` 引用仅在销毁的
  实例仍指向自己时才清空，勿改成无条件 `= null`。
- 每次 `ScannerController.drones` 变化都会触发全量 `map.clear()` + 重画标记（BLE 高频回调尤其频繁）；
  在优化前不要假设标记可增量更新，也别在回调里做重量级工作。
- Wi-Fi RID 的 `informationElements` API 形态在 API 36 前后不同，代码用
  `Build.VERSION_CODES.VANILLA_ICE_CREAM` 门槛避开旧类型，改动时保持该兼容分支。
- BLE 传统广播 25 字节承载没有时间戳字段（31 字节 Wi-Fi Beacon 才有），`parseLocation`
  里 `ridTimestampSeconds` 只在 `data.size >= 31` 时解析——测试用例也验证了这一点。
- AGP 9 的 `compileSdk` 是块状新 DSL；旧工程写法（`compileSdk = 36`）在此会报错，别顺手改回去。
