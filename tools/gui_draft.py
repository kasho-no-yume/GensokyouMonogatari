"""gui_draft.py - 樱符面板的**线稿草稿**生成器（给 qwen21 multi_edit 当 image_1）。

草稿的作用是**把布局锁死**，让 AI 只负责上色与加装饰，不负责排版。

产出的线稿 MUST 是「黑线 + 白底」而不是深色面板 —— AI 读深色底图时容易把
「深紫底 + 亮紫线」当成要保留的风格（第一轮实测踩过：唐草边框糊成一坨，
且模型沿用了占位图的暗色调）。

用法（仓库根目录执行）：
    python tools/gui_draft.py --layout ritual_core --k 5
    python tools/gui_draft.py --layout danmaku_assembly_bench --k 5 --out draft.png

预览写到 tools/textures/_preview/（已 gitignore）。
"""
import argparse
import importlib.util
import pathlib
import sys

from PIL import Image, ImageDraw

ROOT = pathlib.Path(__file__).resolve().parent.parent
TEX_DIR = ROOT / "tools" / "textures"
PREVIEW = TEX_DIR / "_preview"

INK = (0, 0, 0)
PAPER = (255, 255, 255)


def load_layouts():
    spec = importlib.util.spec_from_file_location("gui_sakura", TEX_DIR / "gui_sakura.py")
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod.LAYOUTS, mod.SPRITES


def draw_sprite(d, sprite, x, y):
    """ASCII 零件 -> 线稿方框（草稿只画轮廓，不画内底）。"""
    for j, row in enumerate(sprite):
        for i, ch in enumerate(row):
            if ch in "cGiCG":
                d.point((x + i, y + j), fill=INK)


def build(layout, sprites):
    w, h = layout["size"]
    im = Image.new("RGB", (w, h), PAPER)
    d = ImageDraw.Draw(im)

    # 外框
    t = layout["frame"]
    d.rectangle([0, 0, w - 1, h - 1], outline=INK, width=t)

    # 圆形提示（构图暗示，非成品纹理）
    for cx, cy, r in layout.get("rings", []):
        d.ellipse([cx - r, cy - r, cx + r, cy + r], outline=INK, width=1)

    # 卡片
    for x0, y0, x1, y1 in layout.get("cards", []):
        d.rectangle([x0, y0, x1, y1], outline=INK, width=1)

    # 卡片内分隔线
    for y, x0, x1 in layout.get("dividers", []):
        d.line([x0, y, x1, y], fill=INK, width=1)

    # 竖分隔线（左/右栏）
    for x, y0, y1 in layout.get("vlines", []):
        d.line([x, y0, x, y1], fill=INK, width=1)

    # 横分隔线（列表行分隔）
    for y, x0, x1 in layout.get("hlines", []):
        d.line([x0, y, x1, y], fill=INK, width=1)

    # 特殊格（用 core_socket 画：外框 + 内底）
    socket = sprites["core_socket"]
    for sx, sy in layout.get("cells", []) + layout.get("runtime_cells", []):
        d.rectangle([sx, sy, sx + 17, sy + 17], outline=INK, width=1)

    # 规则格阵 + 快捷栏
    for g in layout.get("grids", []) + layout.get("hotbars", []):
        for row in range(g["rows"] if "rows" in g else 1):
            for col in range(g["cols"]):
                x = g["x0"] + col * g["pitch"]
                y = g["top"] + row * g["pitch"]
                d.rectangle([x - 1, y - 1, x + 16, y + 16], outline=INK, width=1)

    # 凹槽（输入框一类）
    for x0, y0, cw, ch in layout.get("wells", []):
        d.rectangle([x0, y0, x0 + cw - 1, y0 + ch - 1], outline=INK, width=1)

    # 按钮位（只画浅轮廓占位，按钮本体由 widget 画）
    for x0, y0, cw, ch in layout.get("buttons", []):
        d.rectangle([x0, y0, x0 + cw - 1, y0 + ch - 1], outline=INK, width=1)

    return im


def main(argv):
    ap = argparse.ArgumentParser(description="樱符面板线稿草稿生成器")
    ap.add_argument("--layout", required=True, help="gui_sakura.LAYOUTS 里的面板名")
    ap.add_argument("--k", type=int, default=5, help="放大倍数（生成图尺寸 = 面板尺寸 × k）")
    ap.add_argument("--out", default=None, help="输出路径（默认写 tools/textures/_preview/）")
    args = ap.parse_args(argv)

    layouts, sprites = load_layouts()
    if args.layout not in layouts:
        print("未知面板：%s（可选：%s）" % (args.layout, ", ".join(layouts)))
        return 1
    layout = layouts[args.layout]

    base = build(layout, sprites)
    if args.out:
        out = pathlib.Path(args.out)
    else:
        PREVIEW.mkdir(parents=True, exist_ok=True)
        out = PREVIEW / ("gui_draft_%s_k%d.png" % (args.layout, args.k))
    out.parent.mkdir(parents=True, exist_ok=True)

    # 草稿放大用 LANCZOS：AI 读平滑草图比读马赛克更容易理解成「排版稿」
    big = base.resize((base.width * args.k, base.height * args.k), Image.LANCZOS)
    big.save(out)
    print("[ok] %s  面板 %dx%d  ->  %dx%d  (%s)"
          % (args.layout, base.width, base.height, big.width, big.height, out))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
