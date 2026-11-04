# 无人机检测（Drone Scanner）

Android 无人机 **Remote ID（RID）检测**应用：通过蓝牙与 Wi-Fi 监听附近无人机的
Remote ID 广播（ASTM F3411 / ASD-STAN EN 4709-002），解包后在腾讯地图上实时展示
序列号、速度、高度、航向、操作员等真实字段。

- 应用名：无人机检测
- 包名：`com.global_707.drone_scanner`
- 当前版本：v1.1.6
- 语言：中文
- 系统要求：Android 8.0（API 26）及以上；**Wi-Fi 检测通道需 Android 11（API 30）及以上**

> ⚠️ 请仅在你拥有合法权限的区域使用本应用，遵守所在国家/地区的无线电与隐私法规。

## 功能特性

### 双通道 RID 接收

- **蓝牙 BLE**
  - 从广播 Service Data 中提取 RID 载荷，识别 UUID `0xFFA0` 与 `0xFFFA–0xFFFF`，
    它们使用**同一套标准解码器**。
  - 支持单条 25 字节消息与 Message Pack 信封；解析 Basic ID、Location/Vector、
    System、Operator ID 等消息类型。
  - 采用**全量扫描**（不设 ScanFilter，避免漏掉只带 Service Data、没有独立 UUID 列表的广播），
    在扫描回调内用解析器判定是否为 RID 帧。
- **Wi-Fi Beacon（Android 11+）**
  - 识别厂商信息元素（IE 221）：OUI `FA-0B-BC` + Vendor Type `0x0D` + MessageCounter + Message Pack。
  - 与蓝牙共用同一解码器，协议版本（v0/v1/v2）在同一张消息类型表内分派。
- **跨帧累积合并**：以设备 MAC 为键累积多帧广播，自动拼合同一架无人机的完整信息。

### 扫描模式

- **手机直连**：使用手机自身的蓝牙与 Wi-Fi 接收 RID 广播。
- **外置天线**（预留，后续实现）：不启用本机射频，界面为外接设备状态占位；外接板端解出的 RID
  消息可经预留接口进入同一套展示流程。

### 地图与展示

- **腾讯地图实时展示**：无人机位置标记，序列号、速度、高度、航向、操作员等字段一览。
- **详情页 / 全屏地图**：进入详情页自动定位到该架无人机；返回首页定位到本机位置。
- **坐标系转换**：RID 坐标为 WGS-84，在中国境内绘制前自动转换为 GCJ-02，境外原样显示。
- **数据真实性**：所有字段均来自真实解包，未解析到的字段统一显示「—」。
- **图层菜单**：点地图左上角图层按钮弹出「底图 / 叠加图层」菜单——底图单选（标准 / 卫星），
  叠加图层各自独立开关，开关后不关闭菜单；菜单底部可进入「图层设置」。
- **指南针**：红色指针随地图旋转实时指北（投影法计算，不受各 SDK bearing 定义差异影响），点击回正北。
- **字段可复制**：点击详情页任意字段即复制（原生点按效果 +「xxx 复制成功」提示），
  「基本信息」右侧可一键复制全部信息。
- **返回逻辑**：从哪个页面进入就退回哪个页面（不再固定回"设置"或"首页"）。
- 深色模式、可拖拽无人机列表、一键跳转外部导航。

### 适飞空域图层（离线包）

- **纯本地**：不联网、无鉴权。离线数据包由用户导入，保存在应用私有目录（`filesDir/airspace/packs/`）。
- **每层独立数据包**：目前只有「适飞空域」一层；后续接入禁飞区 / 限飞区时沿用同一套结构——
  图层菜单、图层设置、渲染与配色都会自动多出一项，不需要改页面。
- **图层设置**（设置 → 图层设置）：页首说明「每个图层可独立选择数据包」，之后**每个图层一个区块**：
  色块图例 + 图层名 + 数据包下拉 +「导入」「删除」；只显示数据包名称与大小。
- **任意缩放都显示**：数据包提供原生级别（建议 z3–13）并由生成器额外烘焙两级；更高的缩放级别
  由 App 用父瓦片裁切放大（overzoom，MapLibre / Leaflet 的通用做法），不逐级生成瓦片，避免卡顿。
- **坐标对齐**：数据包按 **GCJ-02 网格**出图，与腾讯底图网格一致——零偏移、缩放不跳动。
- **配色**：`#93F4F1` 填充 45% + 深青描边 `#3FA3B0` 强调边界（颜色烘焙在瓦片中，改色需重新生成数据包）。
- **演示模式**：开启演示模式、且该图层尚未选择数据包时，会在演示无人机附近显示一个演示空域
  （带「演示」标记，不会覆盖真实数据）。

数据包格式与生成脚本见 [`tools/README.md`](tools/README.md)。

### 应用日志

- 记录方式：**自动记录 / 手动记录 / 关闭**；手动模式可随时开始、终止并显示已记录时长。
- 保存位置：默认保存到公开目录 `Download/RIDScannerLogs/`（无需存储权限），
  也可用系统目录选择器指定自定义位置。
- 文件名 `log_yyyyMMdd_HHmmss_SSS.log`（在 Android 10+ 的公开目录中，系统可能按文件类型
  追加扩展名，实际显示为 `log_….log.txt`），单文件超过 5 MB 自动轮转；
  文件头记录应用、设备、系统、显示、语言与权限等环境信息。
- 「日志设置」页可查看日志目录、统计占用与一键清理；未捕获异常会写入完整堆栈。

## 获取软件

请前往 **GitHub Releases** 下载最新版 APK：

👉 **[下载最新 Release](https://github.com/707GLobal/drone_scanner_app/releases/latest)**

安装提示：

- Android 会拦截未上架商店的 APK，请允许「安装未知来源应用」后安装。
- 首次使用请开启**定位**与**蓝牙/附近设备**权限，并打开蓝牙与 Wi-Fi。
- 本应用需要真实射频硬件，**模拟器无法检测**无人机信号。
- 想先看看界面效果？安装后进入「设置 → 关于我们」打开**演示模式**即可预览。

## 权限说明

| 权限 | 用途 |
|---|---|
| 附近的设备（`BLUETOOTH_SCAN` / `BLUETOOTH_CONNECT`，Android 12+） | 扫描并连接蓝牙 RID 广播 |
| 蓝牙（`BLUETOOTH` / `BLUETOOTH_ADMIN`，Android 11 及以下） | 同上，旧版本系统的对应权限 |
| 附近的 Wi-Fi 设备（`NEARBY_WIFI_DEVICES`，Android 13+） | 触发 Wi-Fi 扫描以读取 RID 信标 |
| Wi-Fi 状态（`ACCESS_WIFI_STATE` / `CHANGE_WIFI_STATE`） | 读取无线状态并主动触发扫描 |
| 位置信息（粗略 / 精确） | Wi-Fi 扫描结果与地图定位所需（蓝牙扫描已声明 `neverForLocation`） |
| 网络与网络状态 | 加载腾讯地图底图 |

扫描结果与应用日志**只保存在本机**，应用本身不会把它们上传到任何服务器；
地图底图与坐标服务由腾讯位置服务提供，需要联网。

## 已知限制

- **不支持 Wi-Fi NAN（Wi-Fi Aware）嗅探**：Android 未提供监听管理帧（monitor / promiscuous）的
  公开 API，系统扫描结果中也看不到 NAN 的 Service Discovery 帧，故无法实现。
- **Wi-Fi 检测通道需 Android 11（API 30）及以上**，更低版本只能使用蓝牙通道。
- **外置天线模式为预留接口**，暂无配套硬件，界面为占位状态。
- 检测效果取决于环境与无人机广播功率，无法保证发现距离与数量。
- **适飞空域数据需要自行准备**：应用不内置任何空域数据，需先在「图层设置」里导入离线数据包；
  图层颜色烘焙在瓦片中，调整配色需要重新生成数据包。

## 从源码构建

环境要求：JDK 21、Android SDK（compileSdk 36.1 / targetSdk 36 / minSdk 26）。

```bash
./gradlew assembleDebug        # 构建 debug APK
./gradlew testDebugUnitTest    # 运行单元测试（50 例：RID 解析、日志、瓦片数学、坐标反解、JSON、manifest 校验）
./gradlew assembleRelease      # 构建 release APK（minify + shrinkResources）
```

Windows 下请使用 `gradlew.bat`。

生成一个**测试用**空域数据包（长沙附近的假区域，用于验证导入与对齐）：

```bash
python tools/make_sample_airspace_pack.py   # 产出 tools/sample-airspace-pack.zip
```

数据包格式说明见 [`tools/README.md`](tools/README.md)。

### 腾讯地图 Key

在用户级 `~/.gradle/gradle.properties` 中写入（也可用环境变量 `TENCENT_MAP_KEY`）：

```properties
tencentMapKey=<你的腾讯位置服务 Key>
```

并在腾讯位置服务控制台的 Key 设置里，把「授权应用」填成应用包名
`com.global_707.drone_scanner`（每行一个，留空则不限制）。

腾讯与高德的规则不同：**Key 只校验包名 / Bundle Identifier，不校验签名 SHA1**。
所以换 keystore、重新签名都不需要改控制台，地图也不会失效；只有改了
applicationId（包名）才需要回来改「授权应用」。

未配置时 debug 包仍可构建（地图空白，构建时告警），release 包会被直接拒绝。

### 发布签名

仓库内不存放任何密钥文件。正式 keystore 放在仓库之外，四项凭据写在用户级
`~/.gradle/gradle.properties`（或 CI 环境变量 `DRONE_RELEASE_*`）：

```properties
droneReleaseStoreFile=C:\\Users\\<用户名>\\.android\\keystores\\drone-scanner-release.jks
droneReleaseStorePassword=<口令>
droneReleaseKeyAlias=drone-scanner
droneReleaseKeyPassword=<口令>
```

```bash
./gradlew assembleRelease   # 输出 app-release.apk（正式证书签名）
```

缺任意一项时 `assembleDebug` 不受影响，而 `assembleRelease` / `bundleRelease`
会在打包前失败并提示补齐配置，不会产出未签名或用 debug 证书签名的包。
keystore 与口令请单独备份：丢失后无法再对已发布应用做覆盖升级，只能换包名重新上架。

## 开源许可

本项目基于 [MIT License](LICENSE) 开源。

---

**开发者：707GLobal** · © 2026 707GLobal，保留所有权利
