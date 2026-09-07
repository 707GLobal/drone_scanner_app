# 无人机检测（Drone Scanner）

Android 无人机 **Remote ID（RID）侦查**应用：通过蓝牙与 Wi-Fi 监听附近无人机的
Remote ID 广播（ASTM F3411 / ASD-STAN EN 4709-002），解包后在腾讯地图上实时展示
序列号、速度、高度、航向、操作员等真实字段。

- 应用名：无人机检测
- 包名：`com.global_707.drone_scanner`
- 版本：v1.0.0
- 语言：中文（UI 全中文）

> ⚠️ 本项目用于无线电频谱合法监测与安全研究。请遵守所在国家/地区的无线电与隐私法规，
> 仅在你拥有合法权限的区域使用。

## 功能特性

- **蓝牙 BLE 双通道解析**
  - 标准 ASTM F3411-22（Service UUID `0xFFA0`）：Basic ID / Location/Vector /
    System / Operator ID / Message Pack 等消息类型。
  - 私有/国标变体承载（Service UUID `0xFFFA–0xFFFF`）。
  - 跨帧按设备 MAC 累积合并，解析 Basic ID 帧后到时同机分裂问题。
- **Wi-Fi 扫描（Android 16+）**：轮询 beacon，识别制造商 IE（ID 221）携带的
  ASTM OUI `FA-0B-9C` 广播。
- **腾讯位置服务地图**：实时标记无人机位置，WGS-84 → GCJ-02 坐标转换。
- **扫描状态实时监测**：蓝牙/Wi-Fi 开关状态、通道能力检测、错误码分级提示。
- **演示模式**：无真实信号时便于预览界面效果（带"演示"标签，不混入真实数据）。
- 深色模式、可拖拽无人机列表面板、外部导航跳转等。

## 构建

环境要求：Windows + JDK 21（toolchain 自动下载）+ **Android 真机**
（RID 蓝牙/Wi-Fi 检测需要真实射频硬件，模拟器无法验证）。

```bash
gradlew.bat assembleDebug      # 构建 debug APK
gradlew.bat installDebug       # 安装到已连接真机
gradlew.bat testDebugUnitTest  # 运行单元测试
gradlew.bat assembleRelease    # 构建 release（minify + shrink）
```

### 腾讯地图 Key

1. 在用户级 `~/.gradle/gradle.properties` 写入
   `tencentMapKey=<你的腾讯位置服务 Key>`。
2. 在腾讯位置服务控制台将该 Key 绑定到应用包名
   `com.global_707.drone_scanner` **及实际签名的 SHA1**。

### 关于签名

仓库本身**不包含**任何 release keystore。默认构建使用 Android 调试证书
（`~/.android/debug.keystore`，`CN=Android Debug`）。正式发布前请自行生成并妥善保管
**专属的 release keystore**，并在控制台重新绑定腾讯地图 Key（更换签名后旧 Key 会鉴权失败）。

## 文档

- `AGENTS.md` — 面向后续开发/AI 助手的项目速览（架构、代码地图、红线约定）。
- `TESTING.md` — 手工功能测试矩阵。

## 开源许可

本项目基于 [MIT License](LICENSE) 开源。

---

**开发者：707GLobal** · © 2026 707GLobal，保留所有权利
