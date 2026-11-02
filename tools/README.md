# tools

开发期辅助脚本，不参与 App 打包。

## 子模块：`tools/uom_map_capture`

`tools/uom_map_capture/` 是独立仓库 <https://github.com/707GLobal/uom_map_capture.git> 的 **git 子模块**，
用于后续「地图抓包 / 离线包生成」开发，与 App 构建无关（`assembleDebug` 不会编译它）。

- **首次克隆主仓库**：`git clone --recursive <主仓库>`；已经克隆过的补拉：`git submodule update --init`
- **本机 github.com 直连不通时**（当前环境就是这样，系统代理为 `127.0.0.1:7890`），git 操作需要带代理：
  ```bash
  git -c http.proxy=http://127.0.0.1:7890 submodule update --init
  # 或一次性配置（只写 .git/config，不进版本库）
  git config --local http.proxy http://127.0.0.1:7890
  ```
- **更新子模块**：`git -C tools/uom_map_capture pull`，然后回主仓库 `git add tools/uom_map_capture`
  并提交（例如 `Bump uom_map_capture`）——主仓库只记录子模块的提交指针。
- **改动流向**：抓包工具的功能改动请在子仓库里提交并推送，再回主仓库更新指针；
  不要在主仓库里直接改子模块内的文件（否则指针与实际内容不一致）。

## 适飞空域离线包格式

App 的适飞空域图层**只读本地数据**（无网络、无鉴权），数据包是一个 zip，解压后结构如下：

```text
airspace/
  manifest.json
  suitable/
    9/430/213.png      ← z/x/y.png，256×256，RGBA PNG
    10/861/427.png
```

`manifest.json`（schema v1）：

```json
{
  "schema": 1,
  "version": "2026-11-02",
  "generatedAt": 1762041600000,
  "tileSize": 256,
  "crs": "GCJ02",
  "zooms": [4, 13],
  "bbox": [73.0, 17.5, 135.5, 54.0],
  "style": { "fill": "#337FC4FF", "stroke": "#CC5AA9E6", "strokeWidth": 2 },
  "attribution": "数据说明（会显示在图层菜单/设置页）",
  "layers": [
    { "id": "suitable", "dir": "suitable", "zooms": [4, 13] }
  ]
}
```

字段说明：

| 字段 | 说明 |
|---|---|
| `schema` | 目前必须为 `1` |
| `version` | 数据版本，显示在图层菜单「数据 x · 来源」里；换包必须换版本 |
| `crs` | **瓦片网格坐标系**。`GCJ02` = 已按腾讯地图网格出图（零偏移，推荐）；`WGS84` = 原始墨卡托网格（App 会换算瓦片号，残余偏移约等于 GCJ 偏移，z13 约 30 px） |
| `zooms` | 包级缩放级别范围；图层可用自己的 `zooms` 覆盖 |
| `bbox` | 覆盖范围 `[minLng, minLat, maxLng, maxLat]`（WGS-84），仅用于提示/校验 |
| `style` | 图例使用的颜色（`#AARRGGBB` 或 `#RRGGBB`）；栅格最终颜色烘焙在 PNG 里 |
| `layers[].id` | 只接受已注册的图层 id（当前只有 `suitable`），未注册图层会被忽略并记日志 |
| `layers[].dir` | 相对目录，禁止 `..`、绝对路径 |

导入方式（二选一）：

1. 地图页「图层 → 适飞空域 → 导入」，选择 zip；
2. 「设置 → 显示设置 → 适飞空域数据 → 导入数据包」。

也可以把同样的目录放进 `app/src/main/assets/airspace/` 作为**内置包**（不导入也能显示，菜单来源显示「内置」）。

安全限制（`AirspacePack.importZip`）：条目数 ≤ 50000、单文件 ≤ 8 MB、解压总量 ≤ 200 MB、路径必须落在目标目录内（防 zip-slip）。

## 测试用示例包

```bash
python tools/make_sample_airspace_pack.py
```

产出 `tools/sample-airspace-pack.zip`：长沙附近一个**假的**矩形适飞区（带一个洞），`crs = WGS84`、z9–z13、透明浅蓝样式。

用途只有一个：在没有真实数据时验证「图层菜单 → 导入 → 瓦片显示 → 坐标对齐」整条链路。
**它不是真实空域数据，不要用于任何实际飞行判断。**
