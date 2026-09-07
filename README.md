# 无人机检测（Drone Scanner）

Android 无人机 **Remote ID（RID）检测**应用：通过蓝牙与 Wi-Fi 监听附近无人机的
Remote ID 广播（ASTM F3411 / ASD-STAN EN 4709-002），解包后在腾讯地图上实时展示
序列号、速度、高度、航向、操作员等真实字段。

- 应用名：无人机检测
- 包名：`com.global_707.drone_scanner`
- 语言：中文

> ⚠️ 请仅在你拥有合法权限的区域使用本应用，遵守所在国家/地区的无线电与隐私法规。

## 功能特性

- **蓝牙 BLE 检测**
  - 标准 ASTM F3411-22（Service UUID `0xFFA0`）：Basic ID / Location/Vector /
    System / Operator ID / Message Pack 等消息类型。
  - 私有/国标变体承载（Service UUID `0xFFFA–0xFFFF`）。
  - 跨帧按设备 MAC 累积合并，自动识别同一架无人机的多帧广播。
- **Wi-Fi 检测（Android 16+）**：识别制造商 IE（ID 221）携带的 ASTM OUI `FA-0B-9C` 广播。
- **腾讯地图实时展示**：无人机位置标记，序列号、速度、高度、航向等真实字段一览。
- **扫描状态实时监测**：蓝牙/Wi-Fi 开关状态、设备通道能力检测。
- 深色模式、可拖拽无人机列表、外部导航跳转等。

## 获取软件

请前往 **GitHub Releases** 下载最新版 APK：

👉 **[下载最新 Release](https://github.com/707GLobal/drone_scanner_app/releases/latest)**

安装提示：

- Android 会拦截未上架商店的 APK，请允许「安装未知来源应用」后安装。
- 首次使用请开启**定位**与**蓝牙/附近设备**权限，并打开蓝牙与 Wi-Fi。
- 本应用需要真实射频硬件，**模拟器无法检测**无人机信号。
- 想先看看界面效果？安装后进入「设置 → 关于我们」打开**演示模式**即可预览。

## 开源许可

本项目基于 [MIT License](LICENSE) 开源。

---

**开发者：707GLobal** · © 2026 707GLobal，保留所有权利
