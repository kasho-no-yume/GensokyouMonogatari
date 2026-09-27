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


# Patchouli DenseMultiblock 保留字符：'0' = 唯一中心、' ' = 空气、'_' = 任意方块。
RESERVED_CHARS = ("0", " ", "_")
CHAR_POOL = "123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz"


def build_multiblock(cells, palette, anchor):
    mapping = {}
    out_char = {}

    # 不能直接拿 palette 的字符键当图案字符：palette 里常有键 "0"（0 阶仪式石），
    # 与 Patchouli 的中心保留字 '0' 冲突，会生成多个中心而报
    # "A structure can't have two centers"。故先给「单字符且非保留」的键保留原名，
    # 再为其余键分配一个未占用的安全字符。
    used = set(RESERVED_CHARS)
    for key, value in palette.items():
        if classify(value) in ("block", "tag") and len(key) == 1 and key not in used:
            out_char[key] = key
            used.add(key)
    for key, value in palette.items():
        if classify(value) in ("block", "tag") and key not in out_char:
            free = next((c for c in CHAR_POOL if c not in used), None)
            if free is None:
                raise RuntimeError("palette too large to render multiblock")
            out_char[key] = free
            used.add(free)
    for key, char in out_char.items():
        mapping[char] = palette[key]

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
                if key is None:
                    row.append(" ")
                    continue
                kind = classify(palette[key])
                if kind == "air":
                    row.append(" ")
                elif kind == "ignore":
                    row.append("_")
                else:
                    row.append(out_char[key])
            layer.append("".join(row))
        patterns.append(layer)

    # 中心校验：图案必须恰有一个 '0'（Patchouli 硬性要求）。
    center_count = sum(row.count("0") for layer in patterns for row in layer)
    if center_count != 1:
        raise ValueError(f"multiblock must have exactly one center, got {center_count}")

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


# 分阶门槛 = 世界进度（complete-ritual-book-entries D1）：
#   1→下界、2→末地、3→幻想乡维度；4/5 为 temperLevel 过渡门槛；0 → 无门槛。
GATE_BY_LEVEL = {
    1: "gensokyou:guide/nether_unlock",
    2: "gensokyou:guide/end_unlock",
    3: "gensokyou:guide/gensokyo_unlock",
    4: "gensokyou:guide/tier_4",
    5: "gensokyou:guide/tier_5",
}


def gate_for(level):
    return GATE_BY_LEVEL.get(level)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--ritual", required=True)
    parser.add_argument("--recipes-dir")
    parser.add_argument("--entry-out")
    parser.add_argument("--multiblock-out")
    parser.add_argument("--name-key")
    parser.add_argument("--category")
    parser.add_argument("--icon")
    parser.add_argument("--sortnum", type=int, default=0)
    parser.add_argument("--max-recipes", type=int, default=12,
                        help="配方数超过该值则不补配方页（默认 12）")
    parser.add_argument("--no-recipes", action="store_true",
                        help="不补配方页（开放式/极多配方仪式，作者显式指认）")
    parser.add_argument("--entry-advancement", default="",
                        help="条目级门槛覆盖；缺省取最低结构阶门槛")
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
        gate = gate_for(level)
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
        if args.no_recipes:
            tier_page["show_recipes"] = False
        pages.append(structure_page)
        pages.append(tier_page)

    recipe_count = count_recipes(args.recipes_dir, ritual_id)
    recipe_pages = 0
    if not args.no_recipes and 0 < recipe_count <= args.max_recipes:
        for index in range(recipe_count):
            pages.append({
                "type": "gensokyou:ritual_page",
                "ritual": ritual_id,
                "recipe_index": index,
            })
        recipe_pages = recipe_count

    # 条目级门槛：取最低结构阶对应的门槛（最低阶 0 → 无条件可见/不 secret）。
    entry_gate = args.entry_advancement.strip() or (gate_for(levels[0]) or "")
    entry = {
        "name": args.name_key,
        "category": args.category,
        "icon": args.icon,
        "sortnum": args.sortnum,
        "pages": pages,
    }
    if entry_gate:
        entry["advancement"] = entry_gate
        entry["secret"] = True
    with open(args.entry_out, "w", encoding="utf-8") as handle:
        json.dump(entry, handle, ensure_ascii=False, indent=2)
    print(f"entry -> {args.entry_out}  (tiers={levels}, recipe_pages={recipe_pages}, "
          f"entry_gate={entry_gate or '-'}, total_pages={len(pages)})")


if __name__ == "__main__":
    main()
