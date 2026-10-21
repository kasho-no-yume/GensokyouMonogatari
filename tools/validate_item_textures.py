"""validate_item_textures.py - 物品贴图静态校验。

检查项：
  1. models/item/*.json 引用的 gensokyou 贴图是否都存在；
  2. textures/ 下所有 PNG 是否可解码、尺寸是否符合预期（非方形的物品图标视为异常）；
  3. 被染色层（tint）用到的贴图是否仍是灰度（带固有色相会把主题色染脏）；
  4. 染色层与其叠加层尺寸是否一致（item/generated 的两层共用同一个 quad，
     尺寸不一致会错位）。

用法：
    python tools/validate_item_textures.py
"""
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / "src/main/resources/assets/gensokyou"
ITEM_MODELS = ASSETS / "models/item"
TEXTURES = ASSETS / "textures"

# 已知是染色层（灰度乘法底图）的贴图：允许非灰度是因为它们由渲染器逐顶点调制
GRAYSCALE_TINT_LAYERS = {
    "item/amp_core",
    "item/amp_core_dye",
    "item/spellcard_emblem",
}


def load_json(path):
    return json.loads(path.read_text(encoding="utf-8-sig"))


def is_grayish(img):
    """是否接近灰度（每像素 R≈G≈B）。"""
    rgb = img.convert("RGB")
    px = rgb.load()
    w, h = rgb.size
    bad = 0
    total = 0
    for y in range(0, h, max(1, h // 16)):
        for x in range(0, w, max(1, w // 16)):
            r, g, b = px[x, y]
            total += 1
            if max(r, g, b) - min(r, g, b) > 24:
                bad += 1
    return total == 0 or bad / total < 0.05


def main():
    errors = []
    warnings = []
    checked = 0

    # --- 1. 模型引用的贴图存在性 ---
    layer_pairs = {}
    for model in sorted(ITEM_MODELS.glob("*.json")):
        data = load_json(model)
        layers = {}
        for key, value in data.get("textures", {}).items():
            if not value.startswith("gensokyou:"):
                continue
            rel = value[len("gensokyou:"):]
            path = TEXTURES / f"{rel}.png"
            if not path.exists():
                errors.append(f"{model.name}: 引用了不存在的贴图 {value}")
                continue
            layers[key] = path
            checked += 1
        if "layer0" in layers and "layer1" in layers:
            layer_pairs[model.name] = (layers["layer0"], layers["layer1"])

    # --- 2. PNG 可解码 + 尺寸 ---
    sizes = {}
    for png in sorted(TEXTURES.rglob("*.png")):
        rel = png.relative_to(TEXTURES).as_posix()[:-4]
        try:
            from PIL import Image
            with Image.open(png) as im:
                im.load()
                sizes[rel] = im.size
        except Exception as exc:
            errors.append(f"{rel}.png: 无法解码 ({exc})")
            continue
        w, h = sizes[rel]
        if rel.startswith("item/") and w != h:
            errors.append(f"{rel}.png: 物品图标应为正方形，实为 {w}x{h}")

    # --- 3. 染色层必须灰度 ---
    for rel in sorted(GRAYSCALE_TINT_LAYERS):
        path = TEXTURES / f"{rel}.png"
        if not path.exists():
            warnings.append(f"{rel}.png 不存在（染色层约定失效，请检查）")
            continue
        from PIL import Image
        with Image.open(path) as im:
            if not is_grayish(im):
                warnings.append(f"{rel}.png 带有固有色相；作为 tint 乘法底图会染脏主题色")

    # --- 4. 双层物品两层尺寸必须一致 ---
    for model, (l0, l1) in sorted(layer_pairs.items()):
        if l0.name not in sizes or l1.name not in sizes:
            continue
        if sizes[l0.name] != sizes[l1.name]:
            errors.append(
                f"{model}: layer0({l0.stem}) {sizes[l0.name]} 与 "
                f"layer1({l1.stem}) {sizes[l1.name]} 尺寸不一致；"
                "item/generated 两层共用同一 quad，尺寸不一致会错位")

    print(f"检查了 {checked} 处贴图引用，{len(sizes)} 张贴图")
    for w in warnings:
        print(f"[警告] {w}")
    for e in errors:
        print(f"[错误] {e}")
    if errors:
        print(f"\n{len(errors)} 个错误")
        return 1
    print("通过")
    return 0


if __name__ == "__main__":
    sys.exit(main())
