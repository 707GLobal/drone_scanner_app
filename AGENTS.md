# AGENTS.md — 无人机 RID 检测 App（Drone Scanner）

> 面向后续开发/AI 助手的项目速览。先读本文件；改 UI / 地图 / 射频相关代码前务必看 §9、§10。
> 仓库：https://github.com/707GLobal/drone_scanner_app （分支 `main`，工作区干净）。

## 1. 项目是什么

一款 Android **无人机检测（Remote ID 侦查）** 应用：通过蓝牙与 Wi-Fi 监听附近无人机的
RID（Remote ID，ASTM F3411 / ASD-STAN EN 4709-002）广播，解包后在腾讯地图上展示
序列号、速度、高度、航向、操作员等真实字段。UI 全中文，包名/应用 ID
`com.global_707.drone_scanner`，应用名「无人机检测」，当前版本 v1.1.6。

两条射频承载，**内部消息格式完全相同**（25 字节定长消息；解析器只有一张消息类型表）：

- **蓝牙 BLE**（Service UUID `0xFFA0` / `0xFFFA`–`0xFFFF` 统一处理）：
  首字节**高 4 位 = 消息类型，低 4 位 = 协议版本**（v0 ASTM F3411-19 / v1 ASD-STAN、GB C-RID / v2 ASTM F3411-22）。
  消息类型 `0x0` Basic ID / `0x1` Location/Vector / `0x4` System / `0x5` Operator ID
  / `0x2` Auth 与 `0x3` Self-ID（无展示字段，跳过）+ `0xF` Message Pack。
- **Wi-Fi Beacon（Android 11+ / API 30+）**：厂商 IE（Element ID 221）为
  `[OUI FA-0B-BC][Vendor Type 0x0D][MessageCounter 1B][Message Pack]`，与蓝牙共用同一解码器。

关键字段编码（ASTM F3411 / ASD-STAN 官方参考实现，全部小端序）：

- Basic ID：byte1 高 4 位 idType + 低 4 位 uaType，UASID 在 **byte2..21**
- Location：byte1 = status(高4)｜Reserved｜HeightType｜EWDirection｜SpeedMult；
  byte2 航向（**1° 步进**，EWDirection 位置 1 时 +180°）；byte3 水平速度（0.25 m/s，
  SpeedMult 时 ×0.75+63.75）；byte4 垂直速度（int8，**0.5 m/s**）；byte5–8 纬度、byte9–12 经度；
  byte13–18 气压/大地/相对高度（u16，0.5m 步进 −1000m 偏移）；byte21–22 时间戳（u16，0.1s）
- Message Pack：byte1 = SingleMessageSize（必须 25）、byte2 = MsgPackSize（1..9）、**byte3 起**才是子消息

地图使用**腾讯位置服务地图 SDK**（当前主线 `main` 最后一次提交由高德 AMap 迁移而来）。

## 2. 技术栈与版本（勿随意升级）

| 项 | 值 | 备注 |
|---|---|---|
| Gradle | 9.4.1（wrapper） | `gradlew.bat` 于 Windows 使用 |
| AGP | 9.2.1 | **新 DSL**：`compileSdk { version = release(36) { minorApiLevel = 1 } }`（即 36.1），不要改回旧写法 |
| Kotlin | 2.2.10 | `kotlin.code.style=official` |
| JDK | 21（toolchain，foojay 自动下载） | 见 `gradle/gradle-daemon-jvm.properties` |
| minSdk / targetSdk | 26 / 36 | Wi-Fi RID 的 IE 访问需 API 30+，低于此只有蓝牙通道可用 |
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
- `BluetoothRidParser.kt` — **纯函数解析器**（对象单例）：RID 载荷（单条 25 字节消息 / Message Pack）
  → `RidMessage`（可 `merge`）。只有 `parse(record)` 触碰 Android 类型，其余可 JVM 单测；
  `rawServiceData()` 供未解析载荷的 hex 诊断。唯一有单元测试的模块。
- `ScannerController.kt` — 扫描控制器单例（对象 + `mutableStateOf` 全局状态）：蓝牙 start/stop/restart、
  Wi-Fi 轮询与厂商 IE 解包、跨帧累积合并（`DetectedDevice`，统一走 `mergeInto`）、距离/信号换算、
  对外 `drones` 列表刷新；`ingestExternal()` 为外置设备预留接入点（见 §4）。
- `AppPrefs.kt` — SharedPreferences + Compose 响应式状态（每项带 `saveXxx`）。
- `AppLogger.kt` — **应用日志器单例**：三种记录方式（关闭 / 自动 / 手动）、实时时长、
  有界队列 + IO 协程批量写盘、5MB 轮转、崩溃捕获；同时镜像 logcat（tag `RidScanner`）。
  纯函数 `formatDuration`/`formatSize`/`formatLine`/`toHex` 有单测。
- `LogFileStore.kt` — 日志位置与文件操作。**默认位置按版本区分**：API 29+ 用 MediaStore 写公开目录
  `Download/RIDScannerLogs/`（免权限、文件管理器可浏览）；API 26–28 用 `getExternalFilesDir(null)/logs`
  （那些版本 `Android/data` 仍可浏览），无外部存储回退 `filesDir/logs`。另支持 SAF 自定义目录
  （写入其下 `AppLogs/<包名>/`）。命名/轮转/统计/清理；清理只删符合本应用命名规则的文件
  （`log_` 前缀 + 含 `.log`，见 §9 的 MediaStore 扩展名坑）。
  `directoryViewUri()` 给出**文件管理器可导航的目录 URI**（见 §9）。
- `LogDeviceInfo.kt` — 日志 Header 构建（App/设备/系统/显示/语言时区/系统能力/关键权限）。
- `DeviceCapabilities.kt` — 实时查询本机能力：BT4 Legacy / BT5 Extended / Wi-Fi 信标 RID 解析（API 30+ 且有 Wi-Fi 硬件）。
- `MockData.kt` — 演示模式数据（仅演示模式注入，UI 带「演示」标签区分）。

### ui/ — Compose 层

- `screens/MapHomeScreen.kt` — 首页：全屏地图 + 扫描状态药丸（统一菜单 `ScanStatusMenu`）+ 可拖拽底部无人机列表面板
  （收起约 1/3 屏、展开约 85%）；**扫描生命周期与权限入口都在这**（见 §4）。
- `screens/DroneDetailScreen.kt` — 详情页 + 全屏地图页 + 共用 `HeaderBar`（M3 CenterAlignedTopAppBar）；
  字段行点按复制（Toast），「基本信息」右侧可复制全部信息；**分组标题固定 36dp 行高**（三个分组间距一致）；
  信号强度显示原始 `RSSI dBm`（`Drone.rssiDbm`，未知显示 —）。
- `screens/SettingsScreen.kt`、`screens/SettingsSubScreens.kt` — 设置主页与
  `SearchSettingsScreen`（**顶部扫描模式滑块** + 本机能力状态 + 扫描开关 + 扫描优先级；
  外置天线模式下隐藏本机射频内容、显示预留的外置天线选项）、`DisplaySettingsScreen`（阻止休眠）、
  `AboutScreen`（演示模式开关、GitHub 链接、版本号）。
- `screens/LogSettingsScreen.kt` — 日志设置页：记录方式（自动/手动/关闭）、手动开始/终止 + 实时时长、
  保存位置（默认 / SAF 自定义 / 恢复默认）、日志总大小、查看日志、清理日志（带确认）。
  目录选择用 `ActivityResultContracts.OpenDocumentTree`；`GroupTitle` 从 `SettingsSubScreens.kt` 复用（internal）。
- `map/MapBackend.kt` — **地图后端抽象**：`MapBackend` 接口 + `MapBackendProvider.backend` 全局切换点
  + `DroneMap` Compose 门面 + 标记调色板（10 色，按设备 ID 哈希取模）。
  `centerOnMyLocationOnce` 控制**入口相机**：首页传 `true`（定位到本机位置），详情/全屏页默认 `false`（定位到该无人机）。
- `map/TencentMapBackend.kt` — 腾讯地图实现（当前唯一后端）；入口相机只决策一次（`initialCentered`），
  入口后新发现的无人机仍会跟随（`cameraMoved`）。
- `map/MapChrome.kt` — 地图浮层的**唯一设计实现**：`MapChromeButton`（圆钮，四态 + 按压缩放）、
  `MapChromeLabelPill`（图标+文字药丸）、`MapPopupSurface`（菜单外壳，统一动画）。新控件必须复用它。
- `map/MapControls.kt` — 地图悬浮控件组（图层/定位/指北）：三颗按钮都来自 `MapChrome`，
  菜单统一走 `MapPopupSurface`；互斥状态见 `map/MapOverlayState.kt`。
  图层菜单模型见 `map/LayerMenu.kt` 的 `rememberLayerMenu`；指北用 `map/CompassButton.kt`（红色指针随地图旋转）。
- `map/MapOverlayState.kt` — 地图浮层状态机（`MapOverlay.{NONE,LAYERS,LOCATE,SCAN}`）：同一时间只允许一个菜单。
- `screens/ScanStatusMenu.kt` — 右上角扫描状态菜单（与图层菜单同款卡片）；`channelErrorText()` 也在这里。
- `map/MapCameraState.kt` — 相机 bearing/zoom 的全局可观察状态；**腾讯 `setOnCameraChangeListener` 是单 setter**，
  只能由 `TencentMapBackend` 注册一次后分发（见 §10）。
- `map/TileOverzoom.kt` — 运行期瓦片放大（父瓦片裁切 + 双线性放大 + 缓存 + 并发上限），
  保证"任何缩放级别都显示"而不必为每一级生成瓦片。
- `map/TencentAirspaceLayer.kt` — 空域叠加层的**唯一腾讯 API 触点**：为每个开启图层挂/卸
  `TileOverlay`，演示/矢量数据画 `Polygon`/`Polyline`（逐点 WGS→GCJ）；**不注册相机监听**
  （由 `TencentMapBackend` 统一注册，见 §10.5）。`map/AirspaceTileProvider.kt` 负责
  原生级别直读 + 运行期 overzoom；**不再用 `map.clear()`**（见 §9）。
- `data/airspace/` — 适飞空域图层（**纯本地离线包，无网络/无鉴权**）：
  `AirspaceModels.kt`（图层注册表 `AirspaceLayers` + 样式 + `PackSummary`）、`AirspaceManifest.kt`（manifest 解析校验，纯函数）、
  `MiniJson.kt`（自写极简 JSON，避免 org.json 在 JVM 单测里是桩）、`PackStore.kt`（**多包**目录/导入/删除/大小/旧包迁移）、
  `AirspaceRepository.kt`（每层选包 → 数据源 + 瓦片 LRU）、`AirspaceState.kt`（catalogs/packs 可观察状态）、
  `LocalPackSource.kt`、`DemoAirspaceSource.kt`（演示模式下的演示空域，矢量）。
  数据包格式见 `tools/README.md`，示例包用 `tools/make_sample_airspace_pack.py` 生成。
- `screens/LayerSettingsScreen.kt` — **图层设置**独立页（与搜索/显示/日志并列）：页首一句总说明，
  之后**每个图层一个区块**（色块 + 图层名 + 数据包下拉 +「导入」「删除」）；`SettingsScaffold` 统一外壳（必须铺 `colors.background`）。
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
- `src/test/.../BluetoothRidParserTest.kt`（15 例）+ `AppLoggerTest.kt`（6 例）+ `ExampleUnitTest.kt`。

## 4. 架构要点与数据流

**全局响应式状态（无 DI）**：`ScannerController` 与 `AppPrefs` 是单例对象，直接持有
`mutableStateOf` 属性；任何 Composable 读取它们即自动订阅重组。改动设置/扫描状态不要另建
状态副本，直接调单例的 `saveXxx` / 控制器方法。

**手动页面导航（显式返回栈，语义对齐 Android 导航原则）**：不用 navigation-compose。
`MainActivity.DroneScannerRoot` 维护 `backStack: SnapshotStateList<NavEntry>`，其中
`NavEntry(route, droneId)` = 「路由 + 参数」；`rememberSaveable` 用 `listSaver` 拍平成字符串保存。

- `push(route, droneId)` 入栈（相同 `route+droneId` 不重复入栈），`goBack()` 出栈；
- **详情/全屏从自己那条 entry 的 `droneId` 取数据**，不要用全局 `selectedDroneId`；
- **参数失效（无人机已消失）时直接 `LaunchedEffect { goBack() }` 退栈**，绝不在详情路由里渲染首页
  （1.1.6 的"详情点不进去/菜单错乱"就是这条兜底造成的）；
- 全屏返回 = `goBack()`（**不要 `push("detail")`**，会叠出重复详情条目）；
- 新增页面只需在 `when(entry.route)` 加分支（不再有 `routeOrder`），`onBack`/`BackHandler` 一律 `goBack()`；
- 栈底 `"map"` 的返回交给系统退出 App。

**扫描生命周期（重要）**：**由路由驱动**——`MainActivity` 的 `LaunchedEffect(currentRoute)` 在离开首页时
`stop()`；`MapHomeScreen` 首次组合且权限齐备时 `start()`（`start` 本身幂等）。
**不要在页面 `onDispose` 里 stop**：`AnimatedContent` 转场期间，新首页可能已经 start、旧首页才 dispose，
会把刚启动的扫描停掉（1.1.6 修复的"返回后偶发停止扫描"）。`detected` 累积表不清空，列表仍保留历史设备。

**扫描模式（`AppPrefs.scanMode`，设置在「搜索设置」页顶部滑块：手机直连 / 外置天线）**：

- `INTERNAL` **手机直连**：启动本机蓝牙/Wi-Fi，按 `neededPermissions()` 申请附近设备/定位权限；
  首页状态面板显示模式标题 + Wi-Fi/蓝牙通道状态 + 「前往搜索设置」
- `EXTERNAL` **外置天线**：**不启动任何本机射频**；`neededPermissions()` 返回空表
  （不逼用户授权，`checkAllPermissions()` 天然通过）；首页右上角显示「等待外置设备」、
  状态面板只显示外置天线状态；`refreshSystemState()` 的通道错误判定与系统开关提醒弹窗均跳过；
  搜索设置页隐藏蓝牙/Wi-Fi/优先级内容，改为预留的外置天线选项（`PlaceholderRow`，只读占位）
- 切换模式只需写偏好，不必手动重启：扫描只在首页运行，返回首页时按新模式 `start()`
- 外置设备数据经 `ScannerController.ingestExternal(deviceKey, message, protocolLabel, frequencyLabel, rssi)`
  进入同一套累积与展示流程（板端负责扫描与解码，手机只作显示端）

**本机能力门控（搜索设置页）**：能力行文案统一为「本机支持 / 本机不支持…」，不支持时
`LaunchedEffect` **强制关闭**对应通道并持久化，开关置灰不可开启，并把原因写在能力行描述里。
蓝牙以 BT4 Legacy 为门槛，Wi-Fi 以 `DeviceCapabilities.wifiBeaconRidSupported()`（API 30+ 且有 Wi-Fi 硬件）为门槛。

**RID 数据流水线**：
蓝牙：`ScanResult → BluetoothRidParser.parse(record) → RidMessage → mergeInto(MAC, …)`；
Wi-Fi：`ScanResult.informationElements → ridPayloadOf()（校验 OUI FA-0B-BC + Vendor Type 0x0D）→
parseMessages() → mergeInto("wifi:"+BSSID, …)`。
两条路径统一以**设备 MAC 为 key** 跨帧累积（避免 Basic ID 帧后到时同机分裂；`RidMessage.merge`
语义为非空覆盖）
→ `refresh()` 过滤/映射为 `Drone`（叠加演示数据）→ `drones`（Compose 状态）→ 列表与地图标记重组。

**首页轮询**：每 2s `ScannerController.refreshSystemState()`（读真实系统蓝牙/Wi-Fi 开关，
变化则 `restart()`，通道关闭置对应错误码）。BLE 采用**全量无过滤扫描**（不设 ScanFilter，
回调内用解析器判定，原因见 §9）；Wi-Fi 扫描协程按系统节流间隔（约 20s）触发 `startScan()`，
每 5s 读一次 `scanResults`。

**扫描状态错误码语义**（界面据此提示）：见 `ScanState`；映射文案在 `ScanStatusMenu.kt` 的
`channelErrorText()`（环境错误用自定义文案，正数回退显示系统错误码）。

**状态判定**（`ScannerController.toDrone`）：无序列号 → `WARNING`；`idType` 为 3/4（UTM/会话 ID）
→ `WARNING`；否则 `SAFE`。`DroneStatus.DANGER` 目前仅演示数据使用。

**地图后端抽象**：所有页面只调用 `DroneMap`/`MapBackendProvider.backend`/`MapControls`，
严禁在 screen 层直接触碰腾讯 API。要接新地图：实现 `MapBackend`，把
`MapBackendProvider.backend` 指向新实现即可。

**入口相机规则**（`centerOnMyLocationOnce`）：打开 App / 返回首页 → 定位到**本机位置**（等本机定位
最多 `ENTRY_LOCATION_WAIT_MS`=2s，拿不到则退回跟随无人机）；进入无人机详情页 / 全屏地图 →
定位到**该架无人机**。入口决策只做一次，之后新发现的无人机仍会自动跟随（`cameraMoved`）。

**日志系统（`AppLogger`，与射频来源无关，两种扫描模式共用同一套）**：

- 记录方式 `AppPrefs.logMode`：`OFF` 不写文件（logcat 仍输出）／`AUTO` App 启动即开始（`MainActivity`
  在 `AppPrefs.init()` 后调 `AppLogger.init()`）／`MANUAL` 由「日志设置」页开关按钮控制
- 切换方式会**先停止并保存**当前文件再应用新模式（`AppLogger.applyMode`）；
  手动模式的计时在 App 级协程里跑，离开页面不停
- 写入：`i/w/e/d/hex` → 有界 Channel(512) → IO 协程批量写盘并 flush；**未记录时只有一次布尔判断**，
  不格式化不入队；队列满丢最旧并记一条 `dropped=N`
- 事件命名（`module` + `event` + `k=v`）：`Scan/start·stop·window`、`Ble/scan_start·scan_fail`、
  `Wifi/scan_start·scan_reject·window`、`Decode/raw_unparsed`（含 hex，对应参考项目的 `decode_fail`）、
  `Rid/drone_first`（首次发现，含 source/key/serial/lat/lon；三种来源都打）、`Crash/`、`Log/start·stop`
- 单文件 5MB 轮转（新文件重写 Header）；未捕获异常由 `installCrashHandler()` 同步写入堆栈后交回系统
- 文件开头 Header 由 `LogDeviceInfo.build()` 生成，末尾 `=== LOG START ===`；正文
  `yyyy-MM-dd HH:mm:ss.SSS 级别/TAG: 消息`（UTF-8），异常附完整堆栈

## 5. 硬性约定（红线）

1. **数据真实性**：所有展示字段必须来自真实 RID 解包。未解到的字段保持 `null`，界面统一显示
   「—」；**严禁硬编码/杜撰参数**。演示数据只允许 `MockData` 且仅演示模式开启时注入（带「演示」标签）。
2. **坐标系**：RID/GPS 坐标是 WGS-84，中国境内地图（腾讯=GCJ-02）绘制前必须经
   `CoordinateConverter.wgs84ToGcj02`；境外原样。此转换只在地图边界做。
3. **地图 Key / 隐私合规**：Key 经 manifestPlaceholders 从用户级 `~/.gradle/gradle.properties`
   的 `tencentMapKey`（或环境变量 `TENCENT_MAP_KEY`）注入；缺失时 debug 用占位符（地图鉴权失败、
   构建告警）而 **release 打包直接失败**。`setAgreePrivacy(true)` 必须在
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
11. **发布签名**：release 包必须用**正式 keystore** 签名，密钥文件与口令一律放仓库之外
    （用户级 `~/.gradle/gradle.properties` 的 `droneRelease*` 四项，或 CI 的同名
    `DRONE_RELEASE_*` 环境变量）。仓库内不得出现 `*.jks`/`*.keystore`/口令文件
    （`.gitignore` 已覆盖）。**严禁用 debug 证书签名对外发布**，也不要把
    `app-release-unsigned.apk` 手工签完就发出去。

## 6. 构建 / 运行 / 配置 Key

Windows 下用 wrapper：

```bash
gradlew.bat assembleDebug                 # 构建 debug APK
gradlew.bat installDebug                  # 安装到已连接真机
gradlew.bat testDebugUnitTest             # 跑单测（50 例：RID 解析 15 + 日志 6 + 瓦片数学 6 + 坐标 3 + JSON 6 + manifest 9 + 演示空域 4 + 模板 1）
gradlew.bat assembleRelease               # release（minify + shrink + 正式签名）
```

腾讯地图 Key 配置：

1. 用户级 `C:\Users\<用户名>\.gradle\gradle.properties` 写入
   `tencentMapKey=<你的腾讯位置服务 Key>`（历史配置名 `amapApiKey` 已废弃，勿再写）。
2. Key 在控制台「Key 设置 → 授权应用」里填包名 `com.global_707.drone_scanner`
   （每行一个，留空则不限制）。**腾讯只校验包名 / Bundle ID，不校验签名 SHA1**
   （这点与高德不同，高德要安全码 SHA1）。因此换 keystore / 换签名不用动控制台，
   只有改 applicationId 才需要回来改授权应用。详见 §9 的 Key 绑定规则条目。

release 签名配置（仓库外，`.gitignore` 已排除相关文件名）：

```properties
droneReleaseStoreFile=C:\\Users\\<用户名>\\.android\\keystores\\drone-scanner-release.jks
droneReleaseStorePassword=<口令>
droneReleaseKeyAlias=drone-scanner
droneReleaseKeyPassword=<口令>
```

`app/build.gradle.kts` 用 `secret()` 统一读取（Gradle 属性优先、环境变量兜底：
`TENCENT_MAP_KEY`、`DRONE_RELEASE_STORE_FILE`、`DRONE_RELEASE_STORE_PASSWORD`、
`DRONE_RELEASE_KEY_ALIAS`、`DRONE_RELEASE_KEY_PASSWORD`）。缺签名或缺 Key 时
`assembleRelease`/`bundleRelease` 会在 `packageRelease*` 任务上直接 `GradleException` 失败，
`assembleDebug` 不受影响。keystore 与口令必须单独备份——丢失后无法对已发布应用覆盖升级。

环境要求：**必须真机**。RID 蓝牙/Wi-Fi 检测需要真实射频硬件；模拟器无法验证。

## 7. 测试策略

- 自动化测试共 50 例：`BluetoothRidParserTest`（15 例，见下）+ `AppLoggerTest`（6 例：时长/体积格式化、
  日志行格式、异常堆栈、hex 转储、日志文件命名规则）+ `TileMathTest`（6 例：文档示例瓦片号反查、
  四至/中心点、GCJ→WGS 瓦片换算方向、纬度夹取、经度归一化）+ `CoordinateConverterTest`（3 例：
  GCJ 反解往返精度、境外原样、偏移量级）+ `MiniJsonTest`（6 例）+ `AirspaceManifestTest`（9 例：
  合法解析、非法 schema/crs/tileSize/id/dir/zooms/bbox/颜色、相对路径安全）+ `DemoAirspaceSourceTest`（4 例：
  四条带结构、面积恰好等于外框减内框、中心不属于任何带、各带互不重叠）+ `ExampleUnitTest`（1 例）。
- 适飞空域相关逻辑（瓦片数学、manifest 校验、JSON、坐标反解）**全部是纯 Kotlin**，改动后必须保持
  单测可通过；`TencentAirspaceLayer`（瓦片挂载/相机监听）与图层菜单只能真机验证。
- `BluetoothRidParserTest`（15 例）——按 ASTM F3411 / ASD-STAN 官方布局构造
  25 字节小端字节流（v1 `0x11`/`v2 0x12` 两套），覆盖位序分派、Message Pack 信封与条数上限、
  四个消息类型的字段偏移、未知/越界取值、GB 42590 试行标准布局交叉验证。改解析器务必同步更新。
  **注意：夹具必须按官方布局写**——旧夹具曾按错误布局构造，导致单测"全绿"却掩盖了真实的偏移 bug。
- 其余（权限流、扫描面板、底部面板拖拽、演示模式、深色模式、导航按钮、地图标记、
  图层导入/选包/删除/缩放对齐/指南针方向）**只能真机回归**：改 UI 或地图后必须在真机上逐条验证。

## 8. 常见改动入口速查

| 想做什么 | 改哪里 |
|---|---|
| 新增设置项 | `AppPrefs`（属性 + `saveXxx`）→ 设置页 UI → 消费点读取 |
| 新增页面 | 在 `MainActivity.kt` 加 route 常量 + `when(entry.route)` 分支 + `BackHandler` |
| 解析新 RID 消息类型 | `BluetoothRidParser` + `Drone.kt` 字段 + `ScannerController.DetectedDevice.merge`/`toDrone` + 详情页表格 + 单测 |
| 地图标记样式/相机行为 | `TencentMapBackend`（位图绘制函数在文件底部） |
| 换地图 SDK | 实现 `MapBackend` 并替换 `MapBackendProvider.backend` |
| 扫描策略/频率 | `ScannerController`（`startBluetooth`/`startWifi` 协程周期） |
| 新增自定义图标 | `AppIcons` 追加 Material path |
| 新增叠加图层 | `AirspaceLayers.all` 加一条（id/配色）+ `layerTitleRes` 加文案 + 数据包 `layers[]` 加目录；菜单/设置页自动出现 |
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
- **不要再对地图调用 `map.clear()`**：它会连空域叠加层的 `TileOverlay` 一起清掉（无人机每 2s 刷新一次，
  空域图层会频繁"消失")。无人机标记改为**自持列表逐个 `remove()`**（`TencentMapBackend.applyMap` 的
  `markers` 参数），空域覆盖物由 `TencentAirspaceLayer` 独占管理，两者生命周期互不干扰。
- 每次 `ScannerController.drones` 变化都会触发全量重画标记（BLE 高频回调尤其频繁）；
  在优化前不要假设标记可增量更新，也别在回调里做重量级工作。
- **消息类型必须按首字节高 4 位分派**（`(data[0] ushr 4) and 0x0F`），协议版本在低 4 位。
  曾按整个字节比较：真实 v1/v2 帧（如 Location `0x11`/`0x12`）被两条通道同时丢弃，
  而 Basic ID `0x02` 被误判成 System/Location 并解出垃圾坐标（凭空产生幽灵目标）。
- **Message Pack 信封是 3 字节头**：`[0]=0xF?`、`[1]=SingleMessageSize（必须 25）`、
  `[2]=MsgPackSize（1..9）`，子消息从**偏移 3** 开始。曾把 byte1 当作 authType、从偏移 2 起切，
  整包错位 1 字节；且缺少条数上限校验（Basic ID ≤ 2、其余类型各 ≤ 1），随机噪声会被解成多台设备。
- **Wi-Fi RID 的 `informationElements` 自 API 30 起形态稳定**：`getId()`/`getBytes()` 是随
  `ScanResult.InformationElement`（`since="30"`）一并引入的，不存在"API 36 前后不同"
  （可用 `platforms/android-36.1/data/api-versions.xml` 核实）。门槛应为 `VERSION_CODES.R`。
- **Wi-Fi 承载的 RID OUI 是 `FA-0B-BC` + Vendor Type `0x0D`**（不是 `0x9C`）；两个判据都要校验，
  载荷位于 IE 头 5 字节（OUI + Type + MessageCounter）之后。
- **Wi-Fi NAN（Wi-Fi Aware）不做，也不必再讨论/加回 UI**：Android 没有 monitor/promiscuous 模式
  公开 API，`WifiManager.scanResults` 看不到 NAN 的 Service Discovery 帧（那是 management
  type 0 / subtype 13 的 Public Action 帧），`WifiAwareManager` 是发布-订阅语义而非嗅探。
  参考项目同样没接：其嗅探器只放行 beacon(8)/probe response(5)（`main_rx/crid_sniffer.c:83`），
  官方库里的 `odid_wifi_receive_message_pack_nan_action_frame()` 全仓库无人调用（只有定义+声明）。
  若将来外置板端要做 NAN，只能在板子上按 Discovery Window 抓帧后调该函数，再走
  `ingestExternal()` 进 App。
- **25 字节 Location 消息内就有时间戳**（偏移 21–22，u16，0.1s）。Wi-Fi Beacon 承载的是
  Message Pack，子消息仍是 25 字节，不存在"31 字节 Wi-Fi Location"这种形态。
- 三个易错编码：航向 **1° 步进**（EWDirection 位置 1 时 +180°，不是 10° 步进）；
  垂直速度 **0.5** m/s（不是 0.25）；高度 u16 × 0.5 − 1000（编码值 0 表示未知）。
- **未识别载荷要先抓 hex 再改解析**：带 RID Service UUID 却解不出消息的广播会经
  `ScannerController.logUnparsedPayload()` 打印 `origin/counter/len/hex`（Wi-Fi 同理，
  `adb logcat -s RidScanner`）。这是核对新协议（如国标变体）字节布局的唯一依据，
  **不要凭猜测写解析代码、再把猜测写进单测**。
- **深色模式必须用 `darkColorScheme(...)`**：`Theme.kt` 的 `DarkColorScheme` 曾误用
  `lightColorScheme(...)`，导致 `isLight=true`、且未显式覆盖的 M3 槽位（如各类 `*Container`）
  取浅色默认值，深色下控件配色错乱。改主题时勿改回。
- 首页扫描状态面板 / 搜索设置的模式相关区块用 M3 原生动画（`AnimatedVisibility` +
  `expandVertically`/`shrinkVertically`、`Modifier.animateContentSize()`）切换显隐，
  新增模式相关内容时沿用同一套，不要各写各的。
- **日志位置必须是"文件管理器允许浏览"的目录**（真机实测 Android 16 / MagicOS 得出，代价三次返工）：
  应用私有目录（`Android/data` 及其子目录）在 **Android 11+ 无法被文件管理器打开浏览**——
  `ACTION_VIEW + vnd.android.document/directory` 会被系统「文件」**忽略并停在它自己的默认位置「下载」**；
  SAF 选择器则直接提示「无法使用此文件夹，为保护您的隐私，请选择其他文件夹」；
  自己注册 DocumentsProvider 暴露该目录也**没用**（`ACTION_VIEW` 仍停在下载，实测）。
  → 所以默认位置：**API 29+ 用 MediaStore 写公开目录 `Download/RIDScannerLogs/`（免权限）**，
  **API 26–28 用 `getExternalFilesDir(null)/logs`**（那些版本 `Android/data` 仍可浏览）。
- **「查看日志」要同时满足两件事**：目录位于可浏览位置（见上）+ 意图**指名 `com.android.documentsui`**。
  不指名会被 WPS Office／网盘等声明了通用 `ACTION_VIEW` 的应用抢答，弹出「打开方式」选择器
  （实测不指名时落到 `HwResolverActivity`，用户看到的就是那个面板）。
  该 ROM 上没有 `resource/folder` 的处理器，别用它兜底；`vnd.android.document/directory` 才是通用 MIME。
- **Kotlin 块注释可以嵌套**：注释里写 `Android/data/**`、`children/*` 这类路径示例时，`/*` 会被当作
  嵌套注释开始，**吞掉后面整段代码**（本项目已两次踩到：编译报 `Unclosed comment` 或"类结构错乱 /
  一堆 Unresolved reference"）。写路径示例时避开 `/*` 序列。
- **日志系统四个坑**：① 日志行格式化只在 `isRecording` 时进行，**不要在未记录时也拼字符串**
  （蓝牙回调高频，一拼就是垃圾）；② `AppLogger.stop()` 不许在主线程等待队列排空（会 ANR），
  收尾必须丢给 IO 协程；③ MediaStore 插入失败时要降级到私有目录并置位标记，
  否则会出现"日志写进去了但目录 URI 指向别处"的不一致；④ **MediaStore 会按 MIME 类型补扩展名**：
  `text/plain` 的日志落盘是 `log_...log.txt`（不是 `.log`），所以识别"本应用日志"**不能只判
  `endsWith(".log")`**——曾因此列表恒为空、"总大小"永远 0 B、"清理日志"按钮恒被禁用（用户报"清理失效"）。
  另一个相关坑：正在记录时清理会把文件 unlink 掉而写入继续进 inode，必须删完立刻轮转
  （`AppLogger.clearLogs`），否则"记录还在跑、目录里却没有文件"。
- **腾讯位置服务的 Key 只校验包名，不校验签名 SHA1**（与高德的关键差异）：控制台
  「我的应用 → 添加Key / 编辑 → 地图SDK（Android/iOS）」里的「授权应用」只接收
  PackageName / Bundle Identifier（可多行、留空不限），面板上**没有 SHA1 字段**；
  官方 FAQ「如何让 key 与 APP 包名绑定」与「获取Key」文档也只提包名。高德则相反
  （Key 必须填安全码 SHA1，换签名会地图空白）。本项目从高德迁移过来时把这条规则一起
  抄了进来，README/本文档一度写着"绑包名 + SHA1、换签名要重新绑定"，据此在换 keystore
  后去找控制台补绑一个不存在的 SHA1 字段——属于白跑。结论：换签名 / 换 keystore /
  换设备都**不需要**动腾讯控制台，只有改 applicationId 才要改「授权应用」。
  真需要「Key 防冒用」时，腾讯只在 WebService API 侧提供域名白名单 / 授权 IP / SK 签名校验，
  与 Android 地图 SDK 鉴权无关，别混为一谈。
- **演示空域的矢量绘制必须同步、且不要用"洞"**：1.1.5 的演示环用 `setHolePoints` 挖洞 + 协程异步绘制，
  两次 `sync()` 交错时会重复叠色或漏画（表现为"中心块只在某些时候出现"，切换深浅色/重进页面后又不同）。
  1.1.6 起：环拆成 4 条矩形带、同步幂等绘制、切底图主动重画（见 §10.6）。
- **不要在页面 `onDispose` 里停扫描**：`AnimatedContent` 转场期间新页面先 start、旧页面后 dispose，
  旧实现会把刚启动的扫描停掉（"详情页返回后偶发停止扫描"）。扫描生命周期只由 `MainActivity` 的路由
  `LaunchedEffect(currentRoute)` 驱动。
- AGP 9 的 `compileSdk` 是块状新 DSL；旧工程写法（`compileSdk = 36`）在此会报错，别顺手改回去。

## 10. 地图 UI 与图层设计规范（1.1.5 起）

### 10.1 地图悬浮控件（统一设计规范）

**唯一实现**：`ui/map/MapChrome.kt` —— `MapChromeButton`（圆钮）/ `MapChromeLabelPill`（图标+文字药丸）/
`MapPopupSurface`（菜单外壳）。任何地图页的新控件都必须复用，禁止另写尺寸/透明度/动画。

| 属性 | 规范值 |
|---|---|
| 圆钮尺寸 | 44dp 圆形；药丸高约 40dp（水平 12dp / 垂直 10dp 内边距，圆角 50%） |
| 背景 | `colors.card @ 0.72`（不要 0.9、不要无描边） |
| 描边（默认） | 1dp `colors.border @ 0.4` |
| 描边（激活） | 1.5dp `colors.primary @ 0.9`，**仅当该按钮自己的菜单打开** |
| 图标 | 圆钮 20dp、药丸 18dp，`colors.primary @ 0.92` |
| 按压反馈 | 整体缩放 0.96，`tween(AppMotion.PressMs=120)`；所有圆钮/药丸/菜单一致 |
| 菜单 | 圆角 16dp、`colors.card @ 0.82`、1dp `colors.border @ 0.4` 描边、宽 252dp；`scaleIn(0.95)+fadeIn(160)` / `scaleOut(0.95)+fadeOut(120)` |
| 菜单对齐 | 圆钮菜单：**顶边 = 按钮顶边**、左边缘 = 按钮右边缘 + 8dp（`Alignment.TopStart`）；药丸菜单：右边缘对齐药丸、顶部 = 药丸底部 + 8dp（`Alignment.TopEnd`） |
| 菜单宽度 | `mapMenuWidth()` = `min(252dp, 屏宽 - 32dp)`，小屏不越界；不要直接写死 252dp |
| 动效 token | 统一取 `ui/theme/Motion.kt` 的 `AppMotion`（路由 Shared Axis 300 / 菜单进 160 出 120 / 按压 120 / 状态 200）；不要在页面里另写时长 |
| 菜单动作行 | 「图层设置」「搜索设置」必须共用 `MapMenuActionRow`（图标 + 正文色文字 + chevron），不许各写一套 |
| 边距 | 全屏地图页 16dp；详情页内嵌地图 12dp（地图区域小，属规范例外） |
| 排列 | 圆钮纵向 8dp 间距；首页控件列从设置药丸下方 52dp 开始 |

**互斥**：图层菜单、定位菜单、扫描菜单共用一个 `MapOverlayState`，同一时间只有一个；
点另一个入口 = 关闭旧的、打开新的。扫描状态菜单与图层菜单同款卡片（252dp / 16dp 圆角 / 同一动画），
内容按"分组标题 + 行 + 底部动作"组织。

### 10.2 指北（指南针）

- Canvas 自绘：红（北）/灰（南）双色指针 + 中心轴；
- 指针角度 = **屏幕上正北的方向角**（`MapCameraState.northAngle`：把地图中心与其「正北约 100 m 处」的点投影到屏幕后求夹角）。
  **不要用 `bearing` 取反**：腾讯 SDK 的 bearing 正负与 Google 式定义相反，取反会转反；投影法在任何旋转/倾斜下都正确。
  用最短路径插值（`tween(240)`），跨越 ±180° 不整圈回甩；
- 点击 = 回正北；`setRotateGesturesEnabled(true)`、`setCompassEnabled(false)`（隐藏 SDK 自带指南针）；
- 容器复用 `MapChromeButtonContainer`（尺寸/描边/按压动画与其它圆钮一致；指针绘制除外）。

### 10.3 叠加图层通用规则（对将来所有图层生效）

1. **注册即显示**：只有 `AirspaceLayers.all` 里注册过的图层才出现在菜单/设置页；数据包里未注册的 `layerKey` 只记日志；
2. **每层独立选包 + 每层一套入口**：`AppPrefs.airspacePackByLayer`（layerId → packId）；「图层设置」里每个图层一个区块，
   区块内含数据包下拉 +「导入」+「删除」，顶部先给一句总说明（每个图层可独立选择数据包）；
   导入只绑定当前图层（包不含该层会提示），删除会移除整个包并清空所有指向它的层选择；
3. **图例**：行首 16dp 色块 = 图例（颜色取图层 `style.strokeColor`），关闭时降到 35% 透明度；**不单独列图例区**；
4. **副标题**：只显示当前数据包名（`manifest.name`）；演示数据显示「演示数据」+「演示」徽标；
5. **菜单结构**：底图组（单选，选完关闭）→ 叠加图层组（独立开关，开关后不关闭）→ 「图层设置」入口；顶部不显示「图层」标题；
6. **颜色**：`#93F4F1` 填充 45%（`0x7393F4F1`）+ 更深的描边 `#3FA3B0` 不透明（`0xFF3FA3B0`，2.5dp）；栅格颜色烘焙在 PNG 里，改色需重新生成数据包；
7. **坐标**：数据层只产出 WGS-84；**唯一转换点**是渲染层的 `GeoPoint.toLatLng()`（`wgs84ToGcj02`）。

### 10.4 缩放与 LOD（"任何缩放都显示"）

- 包内原生级别建议 z3–13（取决于数据精度），生成器额外烘焙 zN+1、zN+2；
- 更高级别由 `TileOverzoom` 用父瓦片裁切 + 双线性放大（与 MapLibre `maxzoom`、Leaflet `maxNativeZoom` 的 overzoom 行为一致），
  父瓦片与结果各 8 MB LRU，同时最多 2 个放大任务；
- **不要**再用 `TileOverlayOptions.visibleLevelRange` 限制显示级别，也不要有 `ZOOM_OUT` 状态——缺级别会表现为空白；
- 数据包必须 `crs = GCJ02`（按腾讯网格出图），否则缩放时会出现整体偏移跳变。

### 10.5 相机监听（单 setter，重要）

腾讯 `TencentMap.setOnCameraChangeListener` 只能注册一个监听。**只有 `TencentMapBackend` 注册**，
回调里更新 `MapCameraState`（bearing/zoom）；空域渲染、指南针等一律读 `MapCameraState`，
不要各自 `setOnCameraChangeListener`（会互相覆盖，表现为"指南针不动/图层状态不更新"）。

### 10.6 演示数据

演示模式的无人机来自 `MockData`，演示空域来自 `DemoAirspaceSource`（演示无人机附近画"回"字环，矢量渲染）；
两者都必须打「演示」标记，且**不得覆盖真实数据包**（该层已选包时不用演示数据）。

演示环 = **4 条互不重叠的矩形带**（上/下/左/右），**不使用多边形洞、不画中心方块**；
矢量绘制必须**同步 + 幂等**（数据源 `features()` 约定同步返回），切底图时清空重画——
1.1.5 用"洞 + 异步协程"曾出现"中心块时有时无"（见 §9）。

### 10.7 地图浮层与返回优先级

- 三个入口（图层钮、定位钮、扫描药丸）都调用 `MapOverlayState.toggle(...)`，天然互斥、不会重叠；
- 点地图空白 / 系统返回键：**先关闭浮层**（`BackHandler(enabled = overlay.open != NONE)`），再交给页面返回；
- 菜单用 `Popup(focusable = false, dismissOnClickOutside = false, dismissOnBackPress = false)`：
  点击可**穿透**到下层按钮（实现"点另一个按钮直接换菜单"，不会被"点外部只关闭"吃掉第一次点击）；
- 关闭由页面里的**全屏点击遮罩**（位于地图之上、按钮之下）+ 返回键统一处理；
- **路由切换**：Material **Shared Axis X**（Google 主流页面切换）——两个页面各平移 **30dp**、共 **300ms**；
  旧页前 **100ms** 淡出，新页延迟 100ms 后用 **200ms** 淡入；方向由 `push`/`goBack` 写入 `navDirection`，
  参数集中在 `AppMotion`（`NavShiftDp/NavSlideMs/NavFadeOutMs/NavFadeInMs/NavFadeInDelayMs`）。
  **不要**改成整屏滑动（突兀）或纯 crossfade（发闷）；
- **地图必须用 `TextureMapView`**（`TextureView`）而不是 `MapView`（`SurfaceView`）：
  SurfaceView 由系统单独合成，整页转场时表现为「地图硬切、只有浮层在淡入淡出」；TextureView 才能参与整页 alpha。

### 10.8 页面返回栈与复制

- 导航 = 显式返回栈（`MainActivity.backStack`）：`push(route)` / `goBack()`；栈底 `"map"` 的返回交给系统退出 App；
- **禁止**在页面里把"父页面"写死——从哪进就退回哪（首页→搜索设置→返回=首页；设置→搜索设置→返回=设置）；
- 详情/全屏页的 `onBack`、`BackHandler` 一律调 `goBack()`；无人机 id 存在该条 `NavEntry` 里，**不再有全局 `selectedDroneId`**；
- 详情页字段 = **点按复制**：整行 `clickable`（原生 ripple）+ Toast「xxx 复制成功」；
  **不要用 `SelectionContainer`**（与 `verticalScroll` 组合会出现文字重影/错位，1.1.6 已移除）；
  「基本信息」标题右侧提供「复制全部信息」（全部 19 个字段，含分组标题，逐行 `标签: 值`）。
