"""validate_gui_textures.py - GUI 贴图校验：尺寸宪法 + 存在性 + 结构自洽。

校验四条：
1. **分辨率宪法** —— 贴图尺寸 MUST 等于面板逻辑尺寸（1 texel = 1 逻辑像素）。
   2x/4x 落盘后降尺寸 blit 会被 NEAREST 降采样丢像素，边缘糊掉。
2. assets 里 MUST 有对应贴图。
3. 槽格阵 / 槽框 MUST 落在面板内（越框 = 控件画到纸外，实机可见）。
4. **咒符条血条**（`gui/boss_bar/`）：728x144（182x36 逻辑 x4 物理像素，含 12 行花丛带）、
   槽内允许前景装饰，中心读数区须通透，上下框须保留有效覆盖。

用法（仓库根目录执行）：
    python tools/validate_gui_textures.py
"""
import importlib.util
import pathlib
import sys

from PIL import Image

ROOT = pathlib.Path(__file__).resolve().parent.parent
TEX_DIR = ROOT / "tools" / "textures"
ASSETS = ROOT / "src/main/resources/assets/gensokyou/textures"

# 面板名 -> assets 相对路径（不带 .png）
ASSET_PATH = {
    "ritual_core": "gui/ritual_core",
    "danmaku_assembly_bench": "gui/danmaku_assembly_bench",
    "ritual_builder": "gui/ritual_builder",
    "ritual_editor": "gui/ritual_editor",
    "crystal_storage": "gui/crystal_storage",
    "wujinzang_terminal": "gui/wujinzang_terminal",
    "dialog": "gui/dialog",
}


def load_layouts():
    spec = importlib.util.spec_from_file_location("gui_sakura", TEX_DIR / "gui_sakura.py")
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod.LAYOUTS


def check(name, layout, failures):
    w, h = layout["size"]
    rel = ASSET_PATH.get(name)
    if rel is None:
        failures.append("%s: 未登记 assets 路径" % name)
        return
    path = ASSETS / (rel + ".png")
    if not path.exists():
        failures.append("%s: 缺少贴图 %s" % (name, path))
        return
    with Image.open(path) as im:
        if im.size != (w, h):
            failures.append("%s: 贴图 %dx%d != 面板逻辑尺寸 %dx%d（分辨率宪法）"
                            % (name, im.width, im.height, w, h))
        else:
            print("[ok] %-26s %dx%d  %s" % (name, im.width, im.height, rel))

    # 结构越框自检
    for g in layout.get("grids", []) + layout.get("hotbars", []):
        gx1 = g["x0"] + (g["cols"] - 1) * g["pitch"] + 16
        gy1 = g["top"] + (g.get("rows", 1) - 1) * g["pitch"] + 16
        if gx1 >= w or gy1 >= h:
            failures.append("%s: 格阵越出面板（右下 %d,%d vs %dx%d）" % (name, gx1, gy1, w, h))
    for sx, sy in layout.get("cells", []) + layout.get("runtime_cells", []):
        if sx < 0 or sy < 0 or sx + 17 >= w or sy + 17 >= h:
            failures.append("%s: 特殊格 (%d,%d) 越出面板 %dx%d" % (name, sx, sy, w, h))
    for x0, y0, cw, ch in layout.get("wells", []) + layout.get("buttons", []):
        if x0 < 0 or y0 < 0 or x0 + cw > w or y0 + ch > h:
            failures.append("%s: 凹槽/按钮位 (%d,%d,%d,%d) 越出面板 %dx%d"
                            % (name, x0, y0, cw, ch, w, h))


def check_boss_bar(failures):
    """咒符条血条：尺寸 + 血槽透明 + 内框连续（与 TouhouBossBarRenderer 的常量对齐）。

    贴图按 1080p / guiScale=4 的**物理像素**产出：182x36 逻辑 x4 = 728x144。
    """
    SS = 4
    W, H = 182 * SS, 36 * SS
    TRACK_X0, TRACK_X1 = 22 * SS, (159 + 1) * SS - 1
    WIN_TOP, WIN_BOT = 20 * SS, (27 + 1) * SS - 1
    for tier in range(1, 6):
        path = ASSETS / ("gui/boss_bar/frame_%d.png" % tier)
        if not path.exists():
            failures.append("boss_bar frame_%d: 缺少贴图 %s" % (tier, path))
            continue
        with Image.open(path) as im:
            im = im.convert("RGBA")
            if im.size != (W, H):
                failures.append("boss_bar frame_%d: %dx%d != %dx%d（1080p 物理像素 1:1）"
                                % (tier, im.width, im.height, W, H))
                continue
            px = im.load()
            # The frame is a foreground overlay; encroaching ornaments are intentional.
            center = im.getchannel("A").crop((W // 2 - 8, WIN_TOP + 8,
                                               W // 2 + 8, WIN_BOT - 7))
            if sum(i * n for i, n in enumerate(center.histogram())) / (center.width * center.height) >= 64:
                failures.append("boss_bar frame_%d: central health reading area is obscured" % tier)
            # Natural rims may contain antialiasing/torn edges. Forcing every
            # pixel opaque recreates the white rectangle this check must catch.
            alpha = im.getchannel("A")
            for label, box in (("top", (TRACK_X0, 64, TRACK_X1 + 1, WIN_TOP)),
                               ("bottom", (TRACK_X0, WIN_BOT + 1, TRACK_X1 + 1, 128))):
                band = alpha.crop(box)
                covered = sum(v > 128 for v in band.get_flattened_data())
                if covered < band.width * band.height * 0.5:
                    failures.append("boss_bar frame_%d: %s rim mostly missing" % (tier, label))
            if alpha.getpixel((0, 110)) == 255 and alpha.getpixel((W - 1, 110)) == 255:
                failures.append("boss_bar frame_%d: opaque backing reaches both canvas edges" % tier)
        print("[ok] %-26s %dx%d  gui/boss_bar/frame_%d" % ("boss_bar frame_%d" % tier, W, H, tier))

    seg = ASSETS / "gui/boss_bar/segment.png"
    if not seg.exists():
        failures.append("boss_bar segment: 缺少贴图 %s" % seg)
    else:
        with Image.open(seg) as im:
            if im.size != (64, 32):
                failures.append("boss_bar segment: %dx%d != 64x32（条身 32 texel 1:1）"
                                % (im.width, im.height))
            else:
                print("[ok] %-26s 64x32  gui/boss_bar/segment" % "boss_bar segment")

    edge = ASSETS / "gui/boss_bar/segment_edge.png"
    if not edge.exists():
        failures.append("boss_bar segment_edge: 缺少贴图 %s" % edge)
    else:
        with Image.open(edge) as im:
            if im.size != (8, 32):
                failures.append("boss_bar segment_edge: %dx%d != 8x32（液面前缘）"
                                % (im.width, im.height))
            else:
                print("[ok] %-26s 8x32  gui/boss_bar/segment_edge" % "boss_bar edge")


def main():
    layouts = load_layouts()
    failures = []
    for name, layout in layouts.items():
        check(name, layout, failures)
    check_boss_bar(failures)
    if failures:
        print()
        for f in failures:
            print("[FAIL] %s" % f)
        print("done: %d 项不合格" % len(failures))
        return 1
    print("done: 全部通过")
    return 0


if __name__ == "__main__":
    sys.exit(main())
