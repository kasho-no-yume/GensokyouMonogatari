"""preview_item_icons.py - 物品图标预览：按 MC 的合成规则渲染物品图标。

模拟 MC 的物品渲染：
  - 模型 parent=item/generated → 两层（layer0 主层 / layer1 叠加层）共用同一个 quad；
  - 叠加层走 **tint 乘法**（MC 的 item tint 是 RGB 乘法，alpha 不参与）；
  - 图标在物品空间里只有 16×16，所以 64×64 贴图会被缩小 4 倍显示。

输出两张对照：
  - 左：64×64 贴图原样（= 界面尺寸 4 时的实际观感，1:1 像素完美）
  - 右：缩到 16×16（= 界面尺寸 1 时的观感，也是世界里的手持观感）

用法：
    python tools/preview_item_icons.py                     # 预览 assets 里的正式贴图
    python tools/preview_item_icons.py --dir tools/textures/_ai
    python tools/preview_item_icons.py --only item/yen
"""
import argparse
import json
import math
import sys
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / "src/main/resources/assets/gensokyou"

# 与 GensokyouItemColors / SpellCardEffects / TierPalette 一致的 tint（ARGB 的 RGB 部分）
TIER_RGB = {0: (0x9E, 0x9E, 0x9E), 1: (0x4C, 0xAF, 0x50), 2: (0x21, 0x96, 0xF3),
            3: (0xFF, 0xC1, 0x07), 4: (0xF4, 0x43, 0x36), 5: (0x9C, 0x27, 0xB0)}

TINT = {
    # 符卡：layer1 = 主题色
    "musou_fuuin": (0xF4, 0x43, 0x36),
    "icicle_fall": (0x4F, 0xC3, 0xF7),
    "light_reflect": (0xFF, 0xD5, 0x4F),
    "healing_garden": (0xF8, 0xBB, 0xD0),
    "flower_armor": (0xF4, 0x8F, 0xB1),
    "demarcation": (0x31, 0x1B, 0x92),
    "spider_web": (0xB0, 0xBE, 0xC5),
    "plague_repay": (0x7C, 0xB3, 0x42),
    "fox_servant": (0x7E, 0x57, 0xC2),
    # 增幅核：layer0 = 品阶色，layer1 = 实例晶石随机色
    "amp_core_t1": (0x4C, 0xAF, 0x50),
    "amp_core_t2": (0x21, 0x96, 0xF3),
    "amp_core_t3": (0xFF, 0xC1, 0x07),
}
# 增幅核 layer1 用实例随机色，固定一个示意色便于比对
CRYSTAL_RGB = (0x66, 0xCC, 0xFF)


def load_json(path):
    return json.loads(path.read_text(encoding="utf-8-sig"))


def multiply_tint(img, tint):
    """MC 的 item tint = RGB 乘法，alpha 原样。"""
    if tint is None:
        return img
    r, g, b, a = img.split()
    r = r.point(lambda v: v * tint[0] // 255)
    g = g.point(lambda v: v * tint[1] // 255)
    b = b.point(lambda v: v * tint[2] // 255)
    return Image.merge("RGBA", (r, g, b, a))


def build_icon(model_path, tex_root):
    """返回 (图标 RGBA, 错误信息或 None)。错误信息以 [跳过] 开头表示「不是缺陷」。"""
    data = load_json(model_path)
    parent = data.get("parent", "")
    if ":block/" in parent:
        return None, "[跳过] 方块物品，走方块模型渲染"
    textures = data.get("textures", {})
    layers = {}
    for key, value in textures.items():
        if not value.startswith("gensokyou:"):
            return None, f"[跳过] 非本命名空间贴图 {value}"
        rel = value[len("gensokyou:"):]
        path = Path(tex_root) / f"{rel}.png"
        if not path.exists():
            return None, f"缺贴图 {value}"
        layers[key] = Image.open(path).convert("RGBA")
    if "layer0" not in layers:
        return None, "[跳过] 没有 layer0（自定义渲染器接管）"
    base = layers["layer0"]
    stem = model_path.stem
    if stem.startswith("amp_core_t"):
        # 增幅核：layer0 乘品阶色，layer1 乘实例晶石色
        tier = int(stem.rsplit("t", 1)[1]) - 1
        base = multiply_tint(base, TIER_RGB.get(tier, TIER_RGB[0]))
    if "layer1" in layers:
        emb = layers["layer1"]
        if emb.size != base.size:
            return None, f"layer1 {emb.size} 与 layer0 {base.size} 尺寸不一致"
        tint = CRYSTAL_RGB if stem.startswith("amp_core_t") else TINT.get(stem)
        base = base.copy()
        base.alpha_composite(multiply_tint(emb, tint))
    return base, None


def checker(size, z):
    bg = Image.new("RGBA", (size, size), (139, 139, 139, 255))
    for y in range(0, size, z):
        for x in range(0, size, z):
            if (x // z + y // z) % 2 == 0:
                bg.paste((110, 110, 110, 255), (x, y, x + z, y + z))
    return bg


def main():
    ap = argparse.ArgumentParser(description="物品图标预览")
    ap.add_argument("--dir", default=str(ASSETS), help="assets 根目录或贴图目录")
    ap.add_argument("--models", default=None, help="模型目录（默认 <dir>/models/item）")
    ap.add_argument("--textures", default=None, help="贴图根目录（默认 <dir>/textures）")
    ap.add_argument("--only", action="append", default=None)
    ap.add_argument("--out", default=None, help="拼图输出路径")
    ap.add_argument("--cols", type=int, default=8)
    args = ap.parse_args()

    root = Path(args.dir)
    models = Path(args.models) if args.models else (root / "models/item")
    if not models.exists():
        models = root.parent / "models/item"
    tex_root = Path(args.textures) if args.textures else (root / "textures")
    if not tex_root.exists():
        tex_root = root.parent / "textures"

    entries = []
    errors = []
    skipped = 0
    for m in sorted(models.glob("*.json")):
        if args.only and m.stem not in args.only:
            continue
        icon, err = build_icon(m, tex_root)
        if err:
            if err.startswith("[跳过]"):
                skipped += 1
            else:
                errors.append(f"{m.name}: {err}")
            continue
        entries.append((m.stem, icon))

    print(f"成功 {len(entries)} 个，跳过 {skipped} 个，失败 {len(errors)} 个")
    for e in errors:
        print(f"[错误] {e}")

    if not entries:
        return 1

    big = 64
    small = 16
    cols = min(args.cols, len(entries))
    rows = math.ceil(len(entries) / cols)
    cell_w = big + small + 8
    cell_h = big + 14
    sheet = Image.new("RGBA", (cols * (cell_w + 6) + 6, rows * (cell_h + 6) + 6),
                      (26, 26, 32, 255))
    draw = ImageDraw.Draw(sheet)
    for i, (name, icon) in enumerate(entries):
        r, c = divmod(i, cols)
        x0 = 6 + c * (cell_w + 6)
        y0 = 6 + r * (cell_h + 6)
        a = icon.resize((big, big), Image.LANCZOS)
        b = icon.resize((small, small), Image.LANCZOS).resize((big, big), Image.NEAREST)
        for k, im in enumerate((a, b)):
            bg = checker(big, 8)
            bg.alpha_composite(im)
            sheet.alpha_composite(bg, (x0 + k * (big + 8), y0))
        draw.text((x0, y0 + big + 1), name[:20], fill=(230, 230, 240, 255))

    out = Path(args.out) if args.out else Path(
        r"C:\Users\etsuraku\AppData\Local\Temp\opencode\item_icons.png")
    out.parent.mkdir(parents=True, exist_ok=True)
    sheet.convert("RGB").save(out)
    print(f"拼图 -> {out}  ({sheet.size[0]}x{sheet.size[1]})")
    return 0


if __name__ == "__main__":
    sys.exit(main())
