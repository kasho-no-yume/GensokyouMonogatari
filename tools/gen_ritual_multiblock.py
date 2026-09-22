#!/usr/bin/env python3
"""仪式结构 → Patchouli 内联 multiblock + 分阶条目生成器（add-guide-book）。

读取 data/gensokyou/rituals/<id>.json（v5 四分之一对称增量），复刻 RitualPatternLoader
展开规则得到全量方块；按阶级裁剪出每阶的累计结构，输出：
  - 每阶 patchouli:multiblock 页（挂 `gensokyou:guide/tier_N` 门槛）
  - 每阶 gensokyou:ritual_tier_page 页（运行时渲染参数 + 到该阶所需材料，同样挂门槛）
  - 一配方一页的 gensokyou:ritual_page（recipe_index = i）
Patchouli 对锁住的页完全隐藏，故玩家只会看到 ≤ 当前阶级的页。

用法：
  python tools/gen_ritual_multiblock.py --ritual <pattern.json> --recipes-dir <dir> \
      --entry-out <entry.json> --name-key <key> --category <id> --icon <id> \
      [--text-page <key> ...]
"""

import argparse
import glob
import json
import os


def expand(key, x, y, z, out):
    """与 RitualPatternLoader.expandInto 一致的四重对称展开（朝向忽略）。"""
    if x == 0 and z == 0:
        out.append((key, 0, y, 0))
    elif x == 0:
        out.extend([(key, 0, y, z), (key, 0, y, -z), (key, z, y, 0), (key, -z, y, 0)])
    elif z == 0:
        out.extend([(key, x, y, 0), (key, -x, y, 0), (key, 0, y, x), (key, 0, y, -x)])
    else:
        out.extend([(key, x, y, z), (key, -x, y, z), (key, x, y, -z), (key, -x, y, -z)])


def classify(value):
    if value == "_ignore":
        return "ignore"
    if value in ("minecraft:air", "air"):
        return "air"
    if value.startswith("#"):
        return "tag"
    return "block"


def cumulative_cells(data, max_level=None):
    """按 level 递增累积（loader 同义）；max_level 为 None 表示全量。"""
    cells = {}
    for level in sorted(data["levels"], key=lambda entry: entry["level"]):
        if max_level is not None and level["level"] > max_level:
            continue
        for add in level["adds"]:
            key, x, y, z = add[0], add[1], add[2], add[3]
            tmp = []
            expand(key, x, y, z, tmp)
            for k, xx, yy, zz in tmp:
                cells[(xx, yy, zz)] = k
    return cells


def build_multiblock(cells, palette, anchor):
    mapping = {}

    def char_for(key):
        value = palette[key]
        kind = classify(value)
        if kind == "air":
            return " "
        if kind == "ignore":
            return "_"
        mapping[key] = value
        return key

    xs = [p[0] for p in cells]
    ys = [p[1] for p in cells]
    zs = [p[2] for p in cells]
    if not xs:
        return None
    min_x, max_x = min(xs), max(xs)
    min_y, max_y = min(ys), max(ys)
    min_z, max_z = min(zs), max(zs)

    patterns = []
    for y in range(max_y, min_y - 1, -1):
        layer = []
        for z in range(min_z, max_z + 1):
            row = []
            for x in range(min_x, max_x + 1):
                if (x, y, z) == (0, 0, 0):
                    row.append("0")
                    continue
                key = cells.get((x, y, z))
                row.append(" " if key is None else char_for(key))
            layer.append("".join(row))
        patterns.append(layer)

    anchor_value = palette[anchor]
    mapping["0"] = anchor_value if classify(anchor_value) in ("block", "tag") else "minecraft:air"

    result = {"pattern": patterns, "mapping": mapping}
    if min_x == -max_x and min_z == -max_z:
        result["symmetrical"] = True
    return result


def count_recipes(recipes_dir, ritual_id):
    if not recipes_dir:
        return 0
    total = 0
    for path in glob.glob(os.path.join(recipes_dir, "*.json")):
        with open(path, "r", encoding="utf-8") as handle:
            data = json.load(handle)
        if data.get("pattern") != ritual_id:
            continue
        if "recipes" in data:
            total += len(data["recipes"])
        else:
            total += 1
    return total


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--ritual", required=True)
    parser.add_argument("--recipes-dir")
    parser.add_argument("--entry-out")
    parser.add_argument("--multiblock-out")
    parser.add_argument("--name-key")
    parser.add_argument("--category")
    parser.add_argument("--icon")
    parser.add_argument("--text-page", action="append", default=[])
    args = parser.parse_args()

    with open(args.ritual, "r", encoding="utf-8") as handle:
        data = json.load(handle)
    ritual_id = data["id"]
    palette = data["palette"]
    anchor = data["anchorKey"]
    levels = sorted({entry["level"] for entry in data["levels"]})

    if args.multiblock_out:
        multiblock = build_multiblock(cumulative_cells(data), palette, anchor)
        with open(args.multiblock_out, "w", encoding="utf-8") as handle:
            json.dump(multiblock, handle, ensure_ascii=False, indent=2)
        print(f"multiblock -> {args.multiblock_out}")

    if not args.entry_out:
        return

    pages = [{"type": "patchouli:text", "text": key} for key in args.text_page]
    for level in levels:
        multiblock = build_multiblock(cumulative_cells(data, level), palette, anchor)
        if multiblock is None:
            continue
        gate = f"gensokyou:guide/tier_{level}" if level >= 1 else None
        structure_page = {
            "type": "patchouli:multiblock",
            "name": f"gensokyou.book.ritual.structure.{level}",
            "multiblock": multiblock,
            "enable_visualize": True,
        }
        tier_page = {
            "type": "gensokyou:ritual_tier_page",
            "ritual": ritual_id,
            "tier": level,
        }
        if gate:
            structure_page["advancement"] = gate
            tier_page["advancement"] = gate
        pages.append(structure_page)
        pages.append(tier_page)

    recipe_count = count_recipes(args.recipes_dir, ritual_id)
    for index in range(recipe_count):
        pages.append({
            "type": "gensokyou:ritual_page",
            "ritual": ritual_id,
            "recipe_index": index,
        })

    entry = {
        "name": args.name_key,
        "category": args.category,
        "icon": args.icon,
        "pages": pages,
    }
    with open(args.entry_out, "w", encoding="utf-8") as handle:
        json.dump(entry, handle, ensure_ascii=False, indent=2)
    print(f"entry -> {args.entry_out}  (tiers={levels}, recipe_pages={recipe_count}, total_pages={len(pages)})")


if __name__ == "__main__":
    main()
