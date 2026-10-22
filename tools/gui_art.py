"""gui_art.py - 樱符 GUI 底图的**收口管线**：AI 高分辨率原图 -> 可进游戏的 1:1 像素图。

规格见 openspec/changes/sakura-gui-art-system/（capability gui-art-system）。
「AI 出材质装饰层、程序定结构层」的分工在本文件里落地。

流程（顺序有讲究，颠倒会出脏边）：

    1. 目标尺寸 × 整数倍的 AI 原图
       -> 预乘 alpha 的 LANCZOS 缩放到 1:1
       （直接缩 RGBA 会把透明区的黑混进边缘）
    2. alpha 二值化（在量化之前；反了会把羽化边变成脏边）
    3. 量化到固定调色板 —— **默认关闭**。实测：22 色固定量化会把樱枝洗白、
       纸面渐变压平，并把贴图里的 1px 金线打成黄虚线，品质反而下降；
       已认可的仪式核心 A 版就是**未量化**的。只在原图过于「绘画感」时
       用 --quantize N 手动收。
    4. 结构区擦除：AI 在结构坐标附近画歪的线擦成纸底
       （只擦**边缘窄带**，不整片擦 —— 整片擦会把卡片里的柔和底色与装饰一起抹掉）
    5. ASCII 零件叠加：槽格 / 嵌槽 / 卡片框 / 分隔线（坐标锁死，必须精确）
    6. 输出 + 预览

用法（仓库根目录执行）：
    python tools/gui_art.py --layout ritual_core --ai raw.png
    python tools/gui_art.py --layout ritual_core --ai raw.png --write-assets

预览写到 tools/textures/_preview/，只有 --write-assets 才覆盖 assets。
"""
import argparse
import importlib.util
import pathlib
import statistics
import sys

from PIL import Image

ROOT = pathlib.Path(__file__).resolve().parent.parent
TEX_DIR = ROOT / "tools" / "textures"
ASSETS = ROOT / "src/main/resources/assets/gensokyou/textures"
PREVIEW = TEX_DIR / "_preview"

# 面板名 -> assets 里的相对路径
ASSET_PATH = {
    "ritual_core": "gui/ritual_core",
    "danmaku_assembly_bench": "gui/danmaku_assembly_bench",
    "ritual_builder": "gui/ritual_builder",
    "ritual_editor": "gui/ritual_editor",
    "crystal_storage": "gui/crystal_storage",
    "wujinzang_terminal": "gui/wujinzang_terminal",
}


def load_module():
    spec = importlib.util.spec_from_file_location("gui_sakura", TEX_DIR / "gui_sakura.py")
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


# ---------------------------------------------------------------- 基础操作

def premultiplied_resize(im, size):
    """预乘 alpha 的 LANCZOS 缩放。直接缩 RGBA 会把透明区黑色混进边缘。"""
    if im.mode == "RGBA":
        return im.convert("RGBa").resize(size, Image.LANCZOS).convert("RGBA")
    return im.convert("RGB").resize(size, Image.LANCZOS)


def binarize_alpha(im, threshold=128):
    """alpha 二值化。必须在量化之前，否则羽化边会变成脏边。"""
    if im.mode != "RGBA":
        return im
    r, g, b, a = im.split()
    a = a.point(lambda v: 255 if v >= threshold else 0)
    return Image.merge("RGBA", (r, g, b, a))


def quantize_fixed(im, palette):
    """量化到固定调色板（无抖动）。"""
    pal = Image.new("P", (1, 1))
    flat = []
    for c in palette:
        flat.extend(c)
    pal.putpalette(flat + [0] * (768 - len(flat)))
    pal = pal.resize((len(palette), 1), Image.NEAREST)
    src = im.convert("RGB")
    q = src.quantize(palette=pal, dither=Image.Dither.NONE)
    return q.convert("RGB")


def region_median(im, box):
    x0, y0, x1, y1 = box
    px = im.load()
    ch = [[], [], []]
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            for i in range(3):
                ch[i].append(px[x, y][i])
    n = len(ch[0])
    return tuple(sorted(c)[n // 2] for c in ch)


def fill_box(im, box, color):
    x0, y0, x1, y1 = box
    im.paste(color, (x0, y0, x1 + 1, y1 + 1))


def hline(im, y, x0, x1, color):
    px = im.load()
    for x in range(x0, x1 + 1):
        px[x, y] = color


def vline(im, x, y0, y1, color):
    px = im.load()
    for y in range(y0, y1 + 1):
        px[x, y] = color


def rect_outline(im, box, color):
    x0, y0, x1, y1 = box
    hline(im, y0, x0, x1, color)
    hline(im, y1, x0, x1, color)
    vline(im, x0, y0, y1, color)
    vline(im, x1, y0, y1, color)


def paste_ascii(im, rows, pal, x, y):
    """ASCII 零件叠加。sprite 原点 = 槽坐标 -1（原版物品画在槽坐标上，内缩 1px 居中）。"""
    px = im.load()
    for j, row in enumerate(rows):
        for i, ch in enumerate(row):
            color = pal[ch]
            if color is None:
                continue
            tx, ty = x + i, y + j
            if 0 <= tx < im.width and 0 <= ty < im.height:
                px[tx, ty] = color


# ---------------------------------------------------------------- 收口主流程

def compose(layout, sprites, pal, quant_palette, ai_path, quantize=0):
    w, h = layout["size"]
    raw = Image.open(ai_path)
    im = premultiplied_resize(raw, (w, h))
    im = binarize_alpha(im)
    if quantize:
        im = quantize_fixed(im, quant_palette[:quantize])

    paper = pal["P"]
    card = pal["p"]
    line = pal["C"]
    dark = pal["c"]

    # ---- 4. 结构区擦除（AI 画歪的线擦成纸底）----
    # 格阵：整片擦成局部纸色，再铺 ASCII 槽格
    def grid_box(g):
        x0 = g["x0"] - 1
        x1 = g["x0"] + (g["cols"] - 1) * g["pitch"] + 16
        y0 = g["top"] - 1
        y1 = g["top"] + ((g.get("rows", 1) - 1) * g["pitch"]) + 16
        return x0, y0, x1, y1

    for g in layout.get("grids", []) + layout.get("hotbars", []):
        b = grid_box(g)
        fill_box(im, b, region_median(im, b))

    # 卡片：**只擦 2px 边缘窄带**（AI 的卡框可能差 1~2px），内部整片保留
    # —— 内部有 AI 画的柔和底色与装饰，整片擦会把手绘质感抹平成一块死色。
    for x0, y0, x1, y1 in layout.get("cards", []):
        edge = region_median(im, (x0 + 1, y0 + 1, x1 - 1, y1 - 1))
        fill_box(im, (x0, y0, x1, y0 + 1), edge)
        fill_box(im, (x0, y1 - 1, x1, y1), edge)
        fill_box(im, (x0, y0, x0 + 1, y1), edge)
        fill_box(im, (x1 - 1, y0, x1, y1), edge)

    # 特殊格：擦成局部纸色
    for sx, sy in layout.get("cells", []) + layout.get("runtime_cells", []):
        fill_box(im, (sx, sy, sx + 17, sy + 17), region_median(im, (sx, sy, sx + 17, sy + 17)))

    # 凹槽 / 按钮位：擦干净（按钮本体由 widget 画）
    for x0, y0, cw, ch in layout.get("wells", []):
        fill_box(im, (x0, y0, x0 + cw - 1, y0 + ch - 1),
                 region_median(im, (x0, y0, x0 + cw - 1, y0 + ch - 1)))
    for x0, y0, cw, ch in layout.get("buttons", []):
        fill_box(im, (x0, y0, x0 + cw - 1, y0 + ch - 1),
                 region_median(im, (x0, y0, x0 + cw - 1, y0 + ch - 1)))

    # ---- 5. ASCII 零件叠加 ----
    # 格阵（同一个 18x18 sprite 复制 N 次，不是逐格手绘）
    cell = sprites["slot_cell"]
    for g in layout.get("grids", []) + layout.get("hotbars", []):
        for row in range(g.get("rows", 1)):
            for col in range(g["cols"]):
                paste_ascii(im, cell, pal,
                            g["x0"] + col * g["pitch"] - 1, g["top"] + row * g["pitch"] - 1)

    # 特殊格（核心嵌槽）
    sock = sprites["core_socket"]
    for sx, sy in layout.get("cells", []):
        paste_ascii(im, sock, pal, sx, sy)

    # 卡片框 + 卡内分隔线
    for x0, y0, x1, y1 in layout.get("cards", []):
        rect_outline(im, (x0, y0, x1, y1), line)
    for y, x0, x1 in layout.get("dividers", []):
        hline(im, y, x0, x1, line)
    # 竖分隔线（左/右栏）
    for x, y0, y1 in layout.get("vlines", []):
        vline(im, x, y0, y1, line)
    # 横分隔线（列表行分隔）—— 用更弱的柔线
    soft = pal.get("g", line)
    for y, x0, x1 in layout.get("hlines", []):
        hline(im, y, x0, x1, soft)

    return im, paper, dark


def main(argv):
    ap = argparse.ArgumentParser(description="樱符 GUI 底图收口")
    ap.add_argument("--layout", required=True, help="gui_sakura.LAYOUTS 里的面板名")
    ap.add_argument("--ai", required=True, help="AI 生成的高分辨率原图路径")
    ap.add_argument("--out", default=None, help="输出路径（默认写 tools/textures/_preview/）")
    ap.add_argument("--quantize", type=int, default=0,
                    help="量化到固定调色板的前 N 色（0 = 不量化，默认；见文件头实测结论）")
    ap.add_argument("--write-assets", action="store_true", help="覆盖 assets 里的正式贴图")
    args = ap.parse_args(argv)

    mod = load_module()
    if args.layout not in mod.LAYOUTS:
        print("未知面板：%s（可选：%s）" % (args.layout, ", ".join(mod.LAYOUTS)))
        return 1
    layout = mod.LAYOUTS[args.layout]

    im, paper, dark = compose(layout, mod.SPRITES, mod.PAL, mod.QUANT_PALETTE,
                              args.ai, quantize=args.quantize)

    if args.out:
        out = pathlib.Path(args.out)
    else:
        PREVIEW.mkdir(parents=True, exist_ok=True)
        out = PREVIEW / ("gui_%s_final.png" % args.layout)
    out.parent.mkdir(parents=True, exist_ok=True)
    im.save(out)
    scale = max(1, 720 // im.width)
    im.resize((im.width * scale, im.height * scale), Image.NEAREST).save(
        PREVIEW / ("gui_%s_final_x%d.png" % (args.layout, scale)) if PREVIEW.exists() else out)
    print("[ok] %s  %dx%d  ->  %s" % (args.layout, im.width, im.height, out))

    if args.write_assets:
        if args.layout not in ASSET_PATH:
            print("未登记 assets 路径：%s" % args.layout)
            return 1
        dest = ASSETS / (ASSET_PATH[args.layout] + ".png")
        dest.parent.mkdir(parents=True, exist_ok=True)
        im.save(dest)
        print("[ok] 已写入 assets：%s" % dest)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
