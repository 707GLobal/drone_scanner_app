# 无人机检测（Drone Scanner）

Android 无人机 **Remote ID（RID）检测**应用：通过蓝牙与 Wi-Fi 监听附近无人机的
Remote ID 广播（ASTM F3411 / ASD-STAN EN 4709-002），解包后在腾讯地图上实时展示
序列号、速度、高度、航向、操作员等真实字段。

- 应用名：无人机检测
- 包名：`com.global_707.drone_scanner`
- 当前版本：v1.1.0
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
- 深色模式、可拖拽无人机列表、一键跳转外部导航。

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

## 从源码构建

环境要求：JDK 21、Android SDK（compileSdk 36.1 / targetSdk 36 / minSdk 26）。

```bash
./gradlew assembleDebug        # 构建 debug APK
./gradlew testDebugUnitTest    # 运行单元测试（RID 解析与日志模块）
./gradlew assembleRelease      # 构建 release APK（minify + shrinkResources）
```

Windows 下请使用 `gradlew.bat`。

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
