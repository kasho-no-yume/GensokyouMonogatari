"""validate_gui_textures.py - GUI 贴图校验：尺寸宪法 + 存在性 + 结构自洽。

校验三条：
1. **分辨率宪法** —— 贴图尺寸 MUST 等于面板逻辑尺寸（1 texel = 1 逻辑像素）。
   2x/4x 落盘后降尺寸 blit 会被 NEAREST 降采样丢像素，边缘糊掉。
2. assets 里 MUST 有对应贴图。
3. 槽格阵 / 槽框 MUST 落在面板内（越框 = 控件画到纸外，实机可见）。

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


def main():
    layouts = load_layouts()
    failures = []
    for name, layout in layouts.items():
        check(name, layout, failures)
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
