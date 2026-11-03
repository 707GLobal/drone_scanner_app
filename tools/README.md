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

## 适飞空域离线包格式（1.1.5）

App 的适飞空域图层**只读本地数据**（无网络、无鉴权）。导入的 zip 会被解压到：

```text
filesDir/airspace/packs/<packId>/
  manifest.json
  suitable/
    3/6/3.png            ← z/x/y.png，256×256，RGBA PNG
    13/6665/3425.png
```

- **支持导入多个数据包**；每个图层在「图层设置」里**独立选择**用哪个包；
- 包 id 由导入时间生成（`p<epochMillis>`），删除包会清掉指向它的选包。

`manifest.json`（schema v1）：

```json
{
  "schema": 1,
  "name": "武汉离线包-1102",
  "version": "2026-11-02",
  "generatedAt": 1762041600000,
  "tileSize": 256,
  "crs": "GCJ02",
  "zooms": [3, 13],
  "bbox": [114.2, 30.4, 114.5, 30.7],
  "style": { "fill": "#4D93F4F1", "stroke": "#D993F4F1", "strokeWidth": 2 },
  "attribution": "数据说明（仅日志/内部使用，界面不展示）",
  "layers": [
    { "id": "suitable", "dir": "suitable", "zooms": [3, 13] }
  ]
}
```

字段说明：

| 字段 | 说明 |
|---|---|
| `schema` | 目前必须为 `1` |
| `name` | **数据包显示名**（图层菜单副标题、图层设置下拉都用它）；缺省回退 `version` |
| `version` | 数据版本；换包必须换版本（缓存按它失效） |
| `crs` | **必须 `GCJ02`**：腾讯底图是 GCJ 网格，按同网格出图才零偏移、缩放不跳动；`WGS84` 仅兼容旧包（会出现随缩放变化的偏移） |
| `zooms` | **原生级别范围**（建议 3–13，取决于数据精度）；包内可额外烘焙 `zN+1`、`zN+2` 两级，更高级别由 App 运行期用父瓦片放大（overzoom） |
| `bbox` | 覆盖范围 `[minLng, minLat, maxLng, maxLat]`（WGS-84） |
| `style` | 图例颜色参考（`#AARRGGBB`）；App 图例以自身图层注册表为准，颜色以 PNG 烘焙为准 |
| `layers[].id` | 只接受已注册的图层 id（当前只有 `suitable`），未注册图层会被忽略并记日志 |
| `layers[].dir` | 相对目录，禁止 `..`、绝对路径 |

导入方式（二选一）：

1. 地图页「图层 → 适飞空域 → 导入」；
2. 「设置 → 图层设置 → 导入数据包」。

安全限制（`PackStore.importZip`）：条目数 ≤ 80000、单文件 ≤ 8 MB、解压总量 ≤ 300 MB、路径必须落在目标目录内（防 zip-slip）。

## 测试用示例包

```bash
python tools/make_sample_airspace_pack.py
```

产出 `tools/sample-airspace-pack.zip`：长沙附近一个**假的**矩形适飞区（带一个洞），
`crs = GCJ02`、原生 z3–z13、额外烘焙 z14–z15、颜色 `#93F4F1`（填充 30% / 描边 85%）。

用途只有一个：在没有真实数据时验证「图层设置 → 导入 → 选包 → 瓦片显示 → 坐标对齐 → 缩放不跳动」整条链路。
**它不是真实空域数据，不要用于任何实际飞行判断。**
