# -*- coding: utf-8 -*-
"""弹幕高清贴图程序化生成（球弹径向渐变 / 符纸 / 激光五芒星法阵）。

非像素画：矢量几何 + 超采样抗锯齿，所有贴图去色（白/灰 + 透明底），
运行时颜色完全由顶点色提供。用法：

    python tools/gen_danmaku_textures.py             # 预览 → tools/textures/_preview/
    python tools/gen_danmaku_textures.py --write-assets
"""
import math
import sys
from pathlib import Path

from PIL import Image, ImageDraw

PREVIEW_DIR = Path("tools/textures/_preview")
ASSET_DIR = Path("src/main/resources/assets/gensokyou/textures/entity")

# ---------------------------------------------------------------- 球弹主体

def make_sphere(size=64):
    """64×64 去色径向渐变：中心近白实心，边缘 alpha 平滑衰减到 0。

    无烤色——灰度值提供明暗，色相全部交给渲染时顶点色；
    中心区 alpha 平顶 + smoothstep 衰减，与白色亮核层叠加成白心。
    """
    solid_end = 0.62          # 实心区半径占比
    rim_darken = 28           # 边缘亮度衰减量（给球体一点层次）
    img = Image.new("RGBA", (size, size))
    px = img.load()
    c = size / 2.0
    for y in range(size):
        for x in range(size):
            d = math.hypot(x + 0.5 - c, y + 0.5 - c) / c
            if d >= 1.0:
                alpha = 0
            elif d <= solid_end:
                alpha = 255
            else:
                t = (d - solid_end) / (1.0 - solid_end)
                t = t * t * (3.0 - 2.0 * t)          # smoothstep
                alpha = round(255 * (1.0 - t))
            v = round(255 - rim_darken * min(d, 1.0))
            px[x, y] = (v, v, v, alpha)
    return img

# ---------------------------------------------------------------- 符纸

def make_talisman(size=64, ss=4):
    """64×64 白色符纸：纸条剪影与旧 16×16 一致（宽 37.5% × 高 75%，居中），
    横向符文条用中性灰（保持去色，染色后纸白符灰）。
    """
    big = size * ss
    img = Image.new("RGBA", (big, big), (0, 0, 0, 0))
    dr = ImageDraw.Draw(img)

    paper = (252, 252, 252, 255)
    paper_edge = (206, 206, 210, 255)
    rune = (132, 132, 140, 255)

    # 纸条主体（宽 0.375、高 0.75，居中）
    w = big * 0.375
    h = big * 0.75
    x0, y0 = (big - w) / 2.0, (big - h) / 2.0
    x1, y1 = x0 + w, y0 + h
    dr.rectangle([x0, y0, x1, y1], fill=paper, outline=paper_edge, width=2 * ss)

    # 符文条：横向灰条，长短错落（呼应旧贴图的字符布局）
    bars = [
        (0.22, 0.62),   # (条中心 y 占纸高比, 条长占纸宽比)
        (0.34, 0.40),
        (0.46, 0.62),
        (0.58, 0.30),
        (0.70, 0.50),
    ]
    bar_h = 0.075 * h
    for cy_ratio, len_ratio in bars:
        cy = y0 + h * cy_ratio
        half = w * len_ratio / 2.0
        cx = (x0 + x1) / 2.0
        dr.rectangle([cx - half, cy - bar_h / 2.0, cx + half, cy + bar_h / 2.0], fill=rune)

    return img.resize((size, size), Image.LANCZOS)

# ---------------------------------------------------------------- 五芒星法阵

def _poly_points(cx, cy, radius, start_deg, count):
    pts = []
    for i in range(count):
        a = math.radians(start_deg + i * 360.0 / count)
        pts.append((cx + radius * math.cos(a), cy + radius * math.sin(a)))
    return pts

def make_magic_circle(size=256, ss=4):
    """256×256 白线五芒星法阵：外圈双线圆环 + 内接五边形 + 五芒星 + 五角圆点缀。
    透明底、去色，4× 超采样后缩回获得平滑线条。
    """
    big = size * ss
    img = Image.new("RGBA", (big, big), (0, 0, 0, 0))
    dr = ImageDraw.Draw(img)
    cx = cy = big / 2.0

    def ring(radius, width, alpha):
        dr.ellipse([cx - radius, cy - radius, cx + radius, cy + radius],
                   outline=(255, 255, 255, alpha), width=width)

    # 外圈双线圆环
    ring(big * 0.465, 10 * ss, 225)
    ring(big * 0.437, 6 * ss, 200)

    # 五边形 + 五芒星（顶点朝上）
    r = big * 0.372
    verts = _poly_points(cx, cy, r, -90.0, 5)
    star_order = [0, 2, 4, 1, 3, 0]

    dr.line([verts[i] for i in star_order], fill=(255, 255, 255, 255),
            width=11 * ss, joint="curve")                       # 五芒星（主线条）
    dr.line(verts + [verts[0]], fill=(255, 255, 255, 180),
            width=7 * ss, joint="curve")                        # 内接五边形（衬线）

    # 五角圆点缀
    dot_r = big * 0.029
    dot_w = 8 * ss
    for vx, vy in verts:
        dr.ellipse([vx - dot_r, vy - dot_r, vx + dot_r, vy + dot_r],
                   outline=(255, 255, 255, 235), width=dot_w)

    return img.resize((size, size), Image.LANCZOS)

# ---------------------------------------------------------------- 入口

def main():
    write_assets = "--write-assets" in sys.argv
    images = {
        "sphere_danmaku.png": make_sphere(),
        "talisman_danmaku.png": make_talisman(),
        "laser_magic_circle.png": make_magic_circle(),
    }
    out_dir = ASSET_DIR if write_assets else PREVIEW_DIR
    out_dir.mkdir(parents=True, exist_ok=True)
    for name, img in images.items():
        path = out_dir / name
        img.save(path)
        print(("written" if write_assets else "preview"), path)

if __name__ == "__main__":
    main()
