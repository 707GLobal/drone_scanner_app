#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
生成一个**测试用**适飞空域离线包的脚本（不是真实空域数据，仅用于验证图层渲染与坐标系对齐）。

产出：
  tools/sample-airspace-pack/airspace/manifest.json
  tools/sample-airspace-pack/airspace/suitable/{z}/{x}/{y}.png
  tools/sample-airspace-pack.zip           ← 可直接在 App「图层设置 → 导入数据包」里选择

关键约定（与 App 的 1.1.5 渲染规则一致）：
  - 瓦片**按 GCJ-02 网格出图**（crs = GCJ02）：腾讯底图就是 GCJ 网格，这样零偏移、缩放不跳动；
  - 原生级别 z3–z13（数据包实际精度），额外烘焙 z14–z15 两级（清晰一档，避免一上来就放大）；
    再高的级别由 App 运行期用父瓦片放大（overzoom），所以"任何缩放都显示"；
  - 颜色 #93F4F1：填充 30%（#4D93F4F1）、描边 85%（#D993F4F1），与 App 图层注册表一致。
"""

import json
import math
import os
import time
import zipfile

from PIL import Image, ImageDraw

TILE = 256
# 长沙附近的测试矩形（WGS-84）：外环 + 一个洞
OUTER = [
    (112.880, 28.160),
    (112.980, 28.160),
    (112.980, 28.280),
    (112.880, 28.280),
]
HOLE = [
    (112.905, 28.190),
    (112.935, 28.190),
    (112.935, 28.220),
    (112.905, 28.220),
]
NATIVE_MIN, NATIVE_MAX = 3, 13
ZOOMS = range(NATIVE_MIN, 16)          # 原生 3–13，烘焙 14–15
FILL = (0x93, 0xF4, 0xF1, 0x4D)        # #4D93F4F1
STROKE = (0x93, 0xF4, 0xF1, 0xD9)      # #D993F4F1
PACK_NAME = "示例数据包-长沙"

ROOT = os.path.dirname(os.path.abspath(__file__))
OUT_DIR = os.path.join(ROOT, "sample-airspace-pack", "airspace")
ZIP_PATH = os.path.join(ROOT, "sample-airspace-pack.zip")

_A = 6378245.0
_EE = 0.00669342162296594323


def _transform_lat(x: float, y: float) -> float:
    ret = -100.0 + 2.0 * x + 3.0 * y + 0.2 * y * y + 0.1 * x * y + 0.2 * math.sqrt(abs(x))
    ret += (20.0 * math.sin(6.0 * x * math.pi) + 20.0 * math.sin(2.0 * x * math.pi)) * 2.0 / 3.0
    ret += (20.0 * math.sin(y * math.pi) + 40.0 * math.sin(y / 3.0 * math.pi)) * 2.0 / 3.0
    ret += (160.0 * math.sin(y / 12.0 * math.pi) + 320 * math.sin(y * math.pi / 30.0)) * 2.0 / 3.0
    return ret


def _transform_lng(x: float, y: float) -> float:
    ret = 300.0 + x + 2.0 * y + 0.1 * x * x + 0.1 * x * y + 0.1 * math.sqrt(abs(x))
    ret += (20.0 * math.sin(6.0 * x * math.pi) + 20.0 * math.sin(2.0 * x * math.pi)) * 2.0 / 3.0
    ret += (20.0 * math.sin(x * math.pi) + 40.0 * math.sin(x / 3.0 * math.pi)) * 2.0 / 3.0
    ret += (150.0 * math.sin(x / 12.0 * math.pi) + 300.0 * math.sin(x / 30.0 * math.pi)) * 2.0 / 3.0
    return ret


def wgs84_to_gcj02(lng: float, lat: float):
    """WGS-84 → GCJ-02（与 App 内 CoordinateConverter 同一算法）"""
    if lng < 72.004 or lng > 137.8347 or lat < 0.8293 or lat > 55.8271:
        return lng, lat
    d_lat = _transform_lat(lng - 105.0, lat - 35.0)
    d_lng = _transform_lng(lng - 105.0, lat - 35.0)
    rad_lat = lat / 180.0 * math.pi
    magic = 1 - _EE * math.sin(rad_lat) ** 2
    sqrt_magic = math.sqrt(magic)
    d_lat = (d_lat * 180.0) / ((_A * (1 - _EE)) / (magic * sqrt_magic) * math.pi)
    d_lng = (d_lng * 180.0) / (_A / sqrt_magic * math.cos(rad_lat) * math.pi)
    return lng + d_lng, lat + d_lat


def to_gcj(points):
    return [wgs84_to_gcj02(lng, lat) for lng, lat in points]


def lonlat_to_px(lon: float, lat: float, zoom: int):
    """GCJ 经纬度 → GCJ 网格的全局像素坐标（256×256 瓦片）"""
    n = 2 ** zoom
    x = (lon + 180.0) / 360.0 * n * TILE
    safe_lat = max(-85.05112878, min(85.05112878, lat))
    lat_rad = math.radians(safe_lat)
    y = (1.0 - math.log(math.tan(lat_rad) + 1.0 / math.cos(lat_rad)) / math.pi) / 2.0 * n * TILE
    return x, y


def tile_polygon(points, zoom, tile_x, tile_y):
    origin_x, origin_y = tile_x * TILE, tile_y * TILE
    result = []
    for lon, lat in points:
        px, py = lonlat_to_px(lon, lat, zoom)
        result.append((px - origin_x, py - origin_y))
    return result


def tile_range(zoom, points):
    xs, ys = [], []
    for lon, lat in points:
        px, py = lonlat_to_px(lon, lat, zoom)
        xs.append(int(px // TILE))
        ys.append(int(py // TILE))
    return range(min(xs), max(xs) + 1), range(min(ys), max(ys) + 1)


def render_tile(zoom, tile_x, tile_y, outer_gcj, hole_gcj):
    mask = Image.new("L", (TILE, TILE), 0)
    draw = ImageDraw.Draw(mask)
    draw.polygon(tile_polygon(outer_gcj, zoom, tile_x, tile_y), fill=255)
    draw.polygon(tile_polygon(hole_gcj, zoom, tile_x, tile_y), fill=0)

    image = Image.new("RGBA", (TILE, TILE), (0, 0, 0, 0))
    image.paste(Image.new("RGBA", (TILE, TILE), FILL), (0, 0), mask)
    draw = ImageDraw.Draw(image)
    outer = tile_polygon(outer_gcj, zoom, tile_x, tile_y)
    draw.line(outer + [outer[0]], fill=STROKE, width=2, joint="curve")
    hole = tile_polygon(hole_gcj, zoom, tile_x, tile_y)
    draw.line(hole + [hole[0]], fill=STROKE, width=1)
    return image


def main():
    if os.path.isdir(OUT_DIR):
        for name in os.listdir(OUT_DIR):
            path = os.path.join(OUT_DIR, name)
            if os.path.isdir(path):
                for root, _dirs, files in os.walk(path, topdown=False):
                    for f in files:
                        os.remove(os.path.join(root, f))
                    os.rmdir(root)
            else:
                os.remove(path)
    os.makedirs(OUT_DIR, exist_ok=True)

    outer_gcj = to_gcj(OUTER)
    hole_gcj = to_gcj(HOLE)

    count = 0
    for zoom in ZOOMS:
        tile_xs, tile_ys = tile_range(zoom, outer_gcj)
        for tile_x in tile_xs:
            for tile_y in tile_ys:
                image = render_tile(zoom, tile_x, tile_y, outer_gcj, hole_gcj)
                if image.getbbox() is None:
                    continue  # 全透明瓦片不落盘（App 会把缺失文件当作空瓦片）
                target_dir = os.path.join(OUT_DIR, "suitable", str(zoom), str(tile_x))
                os.makedirs(target_dir, exist_ok=True)
                image.save(os.path.join(target_dir, f"{tile_y}.png"), "PNG")
                count += 1

    lngs = [p[0] for p in OUTER]
    lats = [p[1] for p in OUTER]
    manifest = {
        "schema": 1,
        "name": PACK_NAME,
        "version": "sample-gcj-1",
        "generatedAt": int(time.time() * 1000),
        "tileSize": TILE,
        "crs": "GCJ02",
        "zooms": [NATIVE_MIN, NATIVE_MAX],
        "bbox": [min(lngs), min(lats), max(lngs), max(lats)],
        "style": {"fill": "#4D93F4F1", "stroke": "#D993F4F1", "strokeWidth": 2},
        "attribution": "测试数据：仅用于验证适飞空域图层渲染与坐标对齐，不是真实空域",
        "layers": [{"id": "suitable", "dir": "suitable", "zooms": [NATIVE_MIN, NATIVE_MAX]}],
    }
    with open(os.path.join(OUT_DIR, "manifest.json"), "w", encoding="utf-8") as handle:
        json.dump(manifest, handle, ensure_ascii=False, indent=2)

    if os.path.exists(ZIP_PATH):
        os.remove(ZIP_PATH)
    base = os.path.dirname(OUT_DIR)  # .../sample-airspace-pack
    with zipfile.ZipFile(ZIP_PATH, "w", zipfile.ZIP_DEFLATED) as archive:
        for folder, _dirs, files in os.walk(base):
            for name in files:
                full = os.path.join(folder, name)
                archive.write(full, os.path.relpath(full, base))

    size_kb = os.path.getsize(ZIP_PATH) / 1024.0
    print(f"tiles={count} native_z={NATIVE_MIN}-{NATIVE_MAX} baked_to={max(ZOOMS)} "
          f"zip={ZIP_PATH} size={size_kb:.1f} KiB")


if __name__ == "__main__":
    main()
