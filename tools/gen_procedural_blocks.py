"""gen_procedural_blocks.py - 参数化周期无缝 16x16 地形材质合成器（方案 A）。

为什么用于自然地形方块（如沙子、岩石、壤土）：
1. 100% 周期边界无缝（Periodic Torus Domain）：所有三角/晶格噪声在 mod 16 域上严格周期连续，
   铺设上千格也不会出现十字网格接缝。
2. 高度场 + 方向性浮雕光照（Bump-mapped Heightfield）+ 微粒抖动（Micro-grain jitter）：
   模拟出 MC Jappa 原版沙子那种「既有柔和斜向风成沙丘起伏、又有细碎沙粒质感」的效果。
3. 极省 Token：每张贴图只需 5~8 行参数配置，且可一键导出 16x16 贴图、x8 预览及 3x3 平铺检验图。
"""
import math
import random
import sys
from pathlib import Path
from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / "src/main/resources/assets/gensokyou/textures"
PREVIEW = Path(__file__).resolve().parent / "textures/_preview"


def _periodic_value_noise(x, y, period_x, period_y, grid_table):
    """在 (period_x, period_y) 周期晶格上的双三次平滑值噪声。"""
    gx = (x / 16.0) * period_x
    gy = (y / 16.0) * period_y
    ix = int(math.floor(gx))
    iy = int(math.floor(gy))
    fx = gx - ix
    fy = gy - iy
    # Quintic smoothstep
    ux = fx * fx * fx * (fx * (fx * 6 - 15) + 10)
    uy = fy * fy * fy * (fy * (fy * 6 - 15) + 10)

    def g(cx, cy):
        return grid_table[(cx % period_x, cy % period_y)]

    v00 = g(ix, iy)
    v10 = g(ix + 1, iy)
    v01 = g(ix, iy + 1)
    v11 = g(ix + 1, iy + 1)
    vx0 = v00 + ux * (v10 - v00)
    vx1 = v01 + ux * (v11 - v01)
    return vx0 + uy * (vx1 - vx0)


def generate_dune_sand(spec):
    """合成具有风成沙丘波纹 + 细碎沙粒感 + 左上受光浮雕的 16x16 无缝沙子贴图。"""
    seed = spec.get("seed", 101)
    pal = spec["palette"]  # list of RGB tuples from dark (0) to bright (N-1)
    rng = random.Random(seed)

    # 预生成多频周期噪声表
    def make_table(px, py):
        return {(x, y): rng.uniform(-1.0, 1.0) for x in range(px) for y in range(py)}

    t_low = make_table(2, 2)
    t_mid = make_table(4, 4)
    t_high = make_table(8, 8)
    t_grain = {(x, y): rng.gauss(0.0, 1.0) for x in range(16) for y in range(16)}

    # 1. 计算 16x16 周期高度场 H(x, y)
    H = [[0.0] * 16 for _ in range(16)]
    wave_kx, wave_ky = spec.get("wave_k", (1, 2))  # 周期波数（整数保证严格 16x16 无缝）
    warp_amp = spec.get("warp", 2.2)
    grain_amp = spec.get("grain", 0.22)

    for y in range(16):
        for x in range(16):
            # 域扭曲（Domain Warping）：让沙波纹呈自然的弯曲弧线而非死板直线
            wx = _periodic_value_noise(x, y, 2, 2, t_low) * warp_amp
            wy = _periodic_value_noise(x + 5.3, y + 2.7, 2, 2, t_low) * warp_amp
            xw = (x + wx) % 16.0
            yw = (y + wy) % 16.0

            # 主沙波（非对称风成沙脊：迎风缓、背风陡）
            phase = 2.0 * math.pi * (wave_kx * xw / 16.0 + wave_ky * yw / 16.0)
            dune = math.sin(phase) + 0.35 * math.sin(2.0 * phase - 0.6)

            # 叠加中频沙丘起伏与高频细沙颗粒
            mid = _periodic_value_noise(xw, yw, 4, 4, t_mid) * 0.55
            high = _periodic_value_noise(x, y, 8, 8, t_high) * 0.30
            H[y][x] = dune * 0.65 + mid + high

    # 2. 计算左上方向受光浮雕导数 (Bump Lighting) + 高度环境光遮蔽 + 沙粒微抖动
    shade = [[0.0] * 16 for _ in range(16)]
    vals = []
    bump_weight = spec.get("bump_weight", 0.65)
    height_weight = spec.get("height_weight", 0.35)

    for y in range(16):
        for x in range(16):
            h_curr = H[y][x]
            # 左上像素 (x-1, y-1) 与右下像素 (x+1, y+1) 的差分
            h_ul = H[(y - 1) % 16][(x - 1) % 16]
            h_u = H[(y - 1) % 16][x]
            h_l = H[y][(x - 1) % 16]
            # 当左上比当前低时，当前处于迎光坡（亮）；当左上比当前高时，处于背风坡阴影（暗）
            slope_light = (h_curr - 0.5 * (h_u + h_l)) * 0.7 + (h_curr - h_ul) * 0.3
            g_jitter = t_grain[(x, y)] * grain_amp
            v = height_weight * h_curr + bump_weight * slope_light + g_jitter
            shade[y][x] = v
            vals.append(v)

    # 3. 按分位数/百分位量化到调色板（保证每个色阶占比均衡，层次分明且不糊）
    vals_sorted = sorted(vals)
    quantiles = spec.get("quantiles", [0.10, 0.34, 0.70, 0.92])
    thresholds = [vals_sorted[min(255, max(0, int(q * 256)))] for q in quantiles]

    img = Image.new("RGBA", (16, 16))
    px = img.load()
    for y in range(16):
        for x in range(16):
            v = shade[y][x]
            idx = 0
            for t in thresholds:
                if v > t:
                    idx += 1
            r, g, b = pal[min(idx, len(pal) - 1)]
            px[x, y] = (r, g, b, 255)
    return img


SPECS = {
    # 幻想沙 (gensokyo_sand)：替代原版沙子。
    # 告别死板的水平灰条，采用温润的淡金灰/月白暖灰沙色（暗部微偏紫褐，亮部微偏暖白），
    # 配合风成曲折沙波 + 细密沙粒微噪点，一眼就能认出是幻想乡的沙子。
    "block/gensokyo_sand": {
        "seed": 202611,
        "wave_k": (1, 2),
        "warp": 2.4,
        "grain": 0.24,
        "bump_weight": 0.68,
        "height_weight": 0.32,
        "quantiles": [0.11, 0.36, 0.72, 0.93],
        "palette": [
            (132, 124, 128),  # 0: 沙波背光暗谷（暖紫灰褐）
            (156, 150, 150),  # 1: 沙坡过渡色
            (180, 174, 170),  # 2: 幻想沙主色（温润苍灰砂）
            (204, 198, 190),  # 3: 迎光沙脊亮色（淡暖银白）
            (226, 222, 212),  # 4: 细砂晶粒高光
        ],
    },
}


def main(argv):
    write_assets = "--write-assets" in argv
    PREVIEW.mkdir(parents=True, exist_ok=True)
    for name, spec in SPECS.items():
        img = generate_dune_sand(spec)
        safe = name.replace("/", "_")
        if "--print-ascii" in argv:
            pal = spec["palette"]
            rmap = {c: str(i + 1) for i, c in enumerate(pal)}
            for y in range(16):
                row = "".join(rmap[img.getpixel((x, y))[:3]] for x in range(16))
                print(f'    "{row}",')
        # 单张 x8 放大预览
        img.resize((128, 128), Image.NEAREST).save(PREVIEW / f"{safe}_x8.png")
        # 3x3 无缝平铺检验图 (x4 放大 -> 192x192)，直观检验大面积铺设效果
        tiled = Image.new("RGBA", (48, 48))
        for ty in range(3):
            for tx in range(3):
                tiled.paste(img, (tx * 16, ty * 16))
        tiled.resize((192, 192), Image.NEAREST).save(PREVIEW / f"{safe}_tiled3x3.png")

        if write_assets:
            out = ASSETS / f"{name}.png"
            out.parent.mkdir(parents=True, exist_ok=True)
            img.save(out)
            print(f"[ok] {name}.png (16x16 procedural) -> assets & preview")
        else:
            print(f"[ok] {name} (16x16 procedural) -> preview only")


if __name__ == "__main__":
    main(sys.argv[1:])
