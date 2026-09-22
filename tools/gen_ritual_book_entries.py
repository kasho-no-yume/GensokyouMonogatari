#!/usr/bin/env python3
"""批量生成仪式指导书条目（complete-ritual-book-entries）。

读取 data/gensokyou/rituals/*.json 与 ritual_recipes/，为每个**可正常游玩**的
已实现仪式生成独立 Patchouli 条目：
  故事+引言（同一页）→ 逐阶结构页 / 阶级参数页 → 配方页 / 献祭产出页。

- 最低结构阶 ≥ 1 的仪式，条目挂该阶对应的**世界进度门槛**（secret），否则常驻可见。
- 阶级参数由客户端组件（RitualTierComponent）按该阶从 config 计算，不写公式。
- 4 个工具献祭仪式（铲/斧/锄/镐）各按工具材质出 6 张「献祭产出页」（物品图标 + 概率），
  绵津见（钓竿）按等级出 3 张；概率由本脚本按与 JEI 相同的权重归一算出后内联。
- 创造/调试类（赛尔能源）与占位/未实现仪式（无 pattern 数据）不在此列。

用法： python tools/gen_ritual_book_entries.py
"""

import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from gen_ritual_multiblock import (  # noqa: E402
    build_multiblock, count_recipes, cumulative_cells, gate_for,
)

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RITUALS_DIR = os.path.join(REPO, "src/main/resources/data/gensokyou/rituals")
RECIPES_DIR = os.path.join(REPO, "src/main/resources/data/gensokyou/ritual_recipes")
LOOT_DIR = os.path.join(REPO, "src/main/resources/data/gensokyou/ritual_loot")
SPECIAL_DIR = os.path.join(REPO, "src/main/resources/data/gensokyou/ritual_special")
ENTRIES_DIR = os.path.join(
    REPO,
    "src/main/resources/assets/gensokyou/patchouli_books/gensokyou_book/en_us/entries")

CATEGORY = "gensokyou:rituals"
MAX_RECIPE_PAGES = 12
TOOL_LOOT_RITUALS = {
    "haniyasu_circle", "kukunochi_circle", "kaya_no_hime_circle", "oyamatsumi_circle",
}
# 工具标签 + 材质 → 原版工具物品 id（供书页顶部摆出「献祭工具」）。
TOOL_KIND = {
    "#minecraft:pickaxes": "pickaxe",
    "#minecraft:shovels": "shovel",
    "#minecraft:axes": "axe",
    "#minecraft:hoes": "hoe",
}
TIER_PREFIX = {
    "wood": "wooden", "stone": "stone", "gold": "golden",
    "iron": "iron", "diamond": "diamond", "netherite": "netherite",
}


def tool_item_for(tool_tag, tier):
    kind = TOOL_KIND.get(tool_tag)
    prefix = TIER_PREFIX.get(tier)
    if kind is None or prefix is None:
        return ""
    return f"minecraft:{prefix}_{kind}"


# 原版钓鱼静态池（与 WatatsumiLootCardWrapper 一致，luck=0）。
WAT_FISH = [("minecraft:cod", 60), ("minecraft:salmon", 25),
            ("minecraft:pufferfish", 13), ("minecraft:tropical_fish", 2)]
WAT_JUNK = [("minecraft:lily_pad", 17), ("minecraft:leather_boots", 10), ("minecraft:leather", 10),
            ("minecraft:bone", 10), ("minecraft:potion", 10), ("minecraft:string", 5),
            ("minecraft:fishing_rod", 2), ("minecraft:bowl", 10), ("minecraft:stick", 5),
            ("minecraft:ink_sac", 1), ("minecraft:tripwire_hook", 10),
            ("minecraft:rotten_flesh", 10), ("minecraft:bamboo", 10)]
WAT_TREASURE = [("minecraft:name_tag", 1), ("minecraft:saddle", 1), ("minecraft:bow", 1),
                ("minecraft:fishing_rod", 1), ("minecraft:book", 1), ("minecraft:nautilus_shell", 1)]

# (path, icon, no_recipes)；列表顺序即 sortnum 1..N。name-key 复用 JEI 仪式名。
ENTRIES = [
    ("kagutsuchi_flame_circle", "minecraft:blaze_powder", False),
    ("yumewatari_circle", "minecraft:red_bed", False),
    ("haniyasu_circle", "minecraft:iron_shovel", False),
    ("kukunochi_circle", "minecraft:iron_axe", False),
    ("kaya_no_hime_circle", "minecraft:iron_hoe", False),
    ("oyamatsumi_circle", "minecraft:iron_pickaxe", False),
    ("watatsumi_circle", "minecraft:fishing_rod", False),
    ("shujou_yoroku_circle", "gensokyou:codex_of_beings", False),
    ("nichirin_circle", "minecraft:daylight_detector", False),
    ("tsukikage_circle", "minecraft:end_rod", False),
    ("wujinzang_circle", "minecraft:shulker_box", False),
    ("zaohua_circle", "gensokyou:spellcard_star", True),  # 开放式/极多配方，不补配方页
    ("kami_no_megumi_circle", "gensokyou:spirit_core_1", False),
    ("bafang_guiyuan_circle", "gensokyou:spirit_core_5", False),
    ("resonance_relay", "gensokyou:crystal", False),
]


def fmt_pct(percent):
    if percent >= 10.0:
        return f"{percent:.0f}"
    if percent >= 1.0:
        return f"{percent:.1f}"
    return f"{percent:.2f}"


def _enc(entries):
    return ";".join(f"{item},{fmt_pct(pct)},{section}" for item, pct, section in entries)


def tool_loot_entries(path, tier):
    """按 RitualLootTable 的权重模型算某工具材质的产物概率（与 JEI 一致）。"""
    with open(os.path.join(LOOT_DIR, path + ".json"), encoding="utf-8") as handle:
        loot = json.load(handle)
    commons = loot["commons"]
    commons_total = loot.get("commonsTotal", 100)
    top_nether = loot.get("nether", [])
    top_end = loot.get("end", [])
    table = next(t for t in loot["tables"] if t["tier"] == tier)
    special = table.get("special", [])
    nether = table.get("nether", top_nether)
    end = table.get("end", top_end)

    special_sum = sum(w for _, w in special if w > 0)
    bucket = max(0.0, commons_total - special_sum)
    commons_rel = sum(w for _, w in commons if w > 0)
    base = []
    if bucket > 0.0 and commons_rel > 0.0:
        scale = bucket / commons_rel
        base += [(i, w * scale) for i, w in commons if w > 0]
    base += [(i, w) for i, w in special if w > 0]
    base_total = sum(w for _, w in base)
    nether_pos = [(i, w) for i, w in nether if w > 0]
    nether_total = base_total + sum(w for _, w in nether_pos)
    end_pos = [(i, w) for i, w in end if w > 0]
    end_total = nether_total + sum(w for _, w in end_pos)

    out = []
    for i, w in base:
        out.append((i, w / base_total * 100 if base_total > 0 else 0.0, 0))
    for i, w in nether_pos:
        out.append((i, w / nether_total * 100 if nether_total > 0 else 0.0, 1))
    for i, w in end_pos:
        out.append((i, w / end_total * 100 if end_total > 0 else 0.0, 2))
    return out


def _scale_into(src, category_weight):
    total = sum(w for _, w in src)
    if total <= 0:
        return []
    scale = category_weight / total
    return [(i, w * scale) for i, w in src]


def watatsumi_entries(level):
    """与 WatatsumiLootCardWrapper 一致：钓鱼池(fish85+junk10)、宝藏(5，L≥1)、特产池(L≥1)。"""
    with open(os.path.join(SPECIAL_DIR, "watatsumi_special.json"), encoding="utf-8") as handle:
        special = json.load(handle)["entries"] if level >= 1 else []
    fishing = _scale_into(WAT_FISH, 85.0) + _scale_into(WAT_JUNK, 10.0)
    treasure = _scale_into(WAT_TREASURE, 5.0) if level >= 1 else []
    fishing_total = sum(w for _, w in fishing) + sum(w for _, w in treasure)
    special_total = sum(w for _, w in special)
    out = []
    # 海产页不分区：所有产物混一起单网格显示（避免超框）。
    for i, w in fishing:
        out.append((i, w / fishing_total * 100 if fishing_total > 0 else 0.0, 0))
    for i, w in treasure:
        out.append((i, w / fishing_total * 100 if fishing_total > 0 else 0.0, 0))
    for i, w in special:
        out.append((i, w / special_total * 100 if special_total > 0 else 0.0, 0))
    return out


def build_entry(path, icon, no_recipes, sortnum):
    ritual_file = os.path.join(RITUALS_DIR, path + ".json")
    with open(ritual_file, "r", encoding="utf-8") as handle:
        data = json.load(handle)
    ritual_id = data["id"]
    palette = data["palette"]
    anchor = data["anchorKey"]
    levels = sorted({entry["level"] for entry in data["levels"]})

    pages = [{"type": "patchouli:text", "text": f"gensokyou.book.entry.ritual.{path}.text"}]

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
        if no_recipes:
            tier_page["show_recipes"] = False
        pages.append(structure_page)
        pages.append(tier_page)

    recipe_count = count_recipes(RECIPES_DIR, ritual_id)
    recipe_pages = 0
    if not no_recipes and 0 < recipe_count <= MAX_RECIPE_PAGES:
        for index in range(recipe_count):
            pages.append({
                "type": "gensokyou:ritual_page",
                "ritual": ritual_id,
                "recipe_index": index,
            })
        recipe_pages = recipe_count

    loot_pages = 0
    if path in TOOL_LOOT_RITUALS:
        with open(os.path.join(LOOT_DIR, path + ".json"), encoding="utf-8") as handle:
            loot = json.load(handle)
        tool_tag = loot["toolTag"]
        for table in loot["tables"]:
            pages.append({
                "type": "gensokyou:loot_page",
                "tool_item": tool_item_for(tool_tag, table["tier"]),
                "tier": table["tier"],
                "mode": "tool",
                "entries": _enc(tool_loot_entries(path, table["tier"])),
            })
            loot_pages += 1
    elif path == "watatsumi_circle":
        for level in (0, 1, 2):
            pages.append({
                "type": "gensokyou:loot_page",
                "tool_item": "minecraft:fishing_rod",
                "tier": str(level),
                "count": 5 * (4 ** level),
                "mode": "watatsumi",
                "entries": _enc(watatsumi_entries(level)),
            })
            loot_pages += 1

    entry = {
        "name": f"jei.gensokyou.ritual.{path}",
        "category": CATEGORY,
        "icon": icon,
        "sortnum": sortnum,
        "pages": pages,
    }
    # 最低结构阶 ≥ 1 的仪式：条目挂该阶世界进度门槛（其余常驻可见）。
    entry_gate = gate_for(levels[0]) or ""
    if entry_gate:
        entry["advancement"] = entry_gate
        entry["secret"] = True

    out = os.path.join(ENTRIES_DIR, f"ritual_{path}.json")
    with open(out, "w", encoding="utf-8") as handle:
        json.dump(entry, handle, ensure_ascii=False, indent=2)
    print(f"{path:<26} sortnum={sortnum:<3} tiers={levels} recipe_pages={recipe_pages} "
          f"loot_pages={loot_pages} entry_gate={entry_gate or '-'} pages={len(pages)}")


def main():
    for sortnum, (path, icon, no_recipes) in enumerate(ENTRIES, start=1):
        build_entry(path, icon, no_recipes, sortnum)


if __name__ == "__main__":
    main()
