#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
生成一个**测试用**适飞空域离线包的脚本（不是真实空域数据，仅用于验证图层渲染与坐标系对齐）。

产出：
  tools/sample-airspace-pack/airspace/manifest.json
  tools/sample-airspace-pack/airspace/suitable/{z}/{x}/{y}.png
  tools/sample-airspace-pack.zip           ← 可直接在 App「图层 → 导入」里选择

瓦片为 256×256 RGBA PNG，透明浅蓝填充（#337FC4FF）+ 80% 描边（#CC5AA9E6），
坐标系与 App 的瓦片数学一致：Web 墨卡托 XYZ，y 自北向南。

用法：
  python tools/make_sample_airspace_pack.py
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
ZOOMS = range(9, 14)
FILL = (0x7F, 0xC4, 0xFF, 0x33)
STROKE = (0x5A, 0xA9, 0xE6, 0xCC)

ROOT = os.path.dirname(os.path.abspath(__file__))
OUT_DIR = os.path.join(ROOT, "sample-airspace-pack", "airspace")
ZIP_PATH = os.path.join(ROOT, "sample-airspace-pack.zip")


def lonlat_to_px(lon: float, lat: float, zoom: int):
    """经纬度 → 全局像素坐标（zoom 级的 256×256 瓦片网格）"""
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


def tile_range(zoom):
    xs, ys = [], []
    for lon, lat in OUTER:
        px, py = lonlat_to_px(lon, lat, zoom)
        xs.append(int(px // TILE))
        ys.append(int(py // TILE))
    return range(min(xs), max(xs) + 1), range(min(ys), max(ys) + 1)


def render_tile(zoom, tile_x, tile_y):
    mask = Image.new("L", (TILE, TILE), 0)
    draw = ImageDraw.Draw(mask)
    draw.polygon(tile_polygon(OUTER, zoom, tile_x, tile_y), fill=255)
    draw.polygon(tile_polygon(HOLE, zoom, tile_x, tile_y), fill=0)

    image = Image.new("RGBA", (TILE, TILE), (0, 0, 0, 0))
    image.paste(Image.new("RGBA", (TILE, TILE), FILL), (0, 0), mask)
    draw = ImageDraw.Draw(image)
    draw.line(tile_polygon(OUTER, zoom, tile_x, tile_y) + [tile_polygon(OUTER, zoom, tile_x, tile_y)[0]],
              fill=STROKE, width=2, joint="curve")
    draw.line(tile_polygon(HOLE, zoom, tile_x, tile_y) + [tile_polygon(HOLE, zoom, tile_x, tile_y)[0]],
              fill=STROKE, width=1)
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

    count = 0
    for zoom in ZOOMS:
        tile_xs, tile_ys = tile_range(zoom)
        for tile_x in tile_xs:
            for tile_y in tile_ys:
                image = render_tile(zoom, tile_x, tile_y)
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
        "version": "sample-1",
        "generatedAt": int(time.time() * 1000),
        "tileSize": TILE,
        "crs": "WGS84",
        "zooms": [min(ZOOMS), max(ZOOMS)],
        "bbox": [min(lngs), min(lats), max(lngs), max(lats)],
        "style": {"fill": "#337FC4FF", "stroke": "#CC5AA9E6", "strokeWidth": 2},
        "attribution": "测试数据：仅用于验证适飞空域图层渲染与坐标对齐，不是真实空域",
        "layers": [{"id": "suitable", "dir": "suitable", "zooms": [min(ZOOMS), max(ZOOMS)]}],
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
    print(f"tiles={count} zip={ZIP_PATH} size={size_kb:.1f} KiB")


if __name__ == "__main__":
    main()
