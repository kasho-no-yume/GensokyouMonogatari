#!/usr/bin/env python3
"""批量生成仪式指导书条目（complete-ritual-book-entries）。

读取 data/gensokyou/rituals/*.json 与 ritual_recipes/，为每个**可正常游玩**的
已实现仪式生成独立 Patchouli 条目：
  故事+引言（.text 单页，或 .pN 拆多页）→ 逐阶结构页 / 阶级参数页 → 配方页 / 献祭产出页。

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

# 书内正文只在 zh_cn 维护（guide-book「新增书内文案仅维护 zh_cn」：
# en_us 缺键不算缺陷）。故事页键是否存在，以 zh_cn 为准——曾经误读 en_us，
# 结果 21 个仪式里有 19 个被判定为「无正文」，故事页被整条从条目里抹掉。
LANG_FILE = os.path.join(REPO, "src/main/resources/assets/gensokyou/lang/zh_cn.json")
CATEGORY = "gensokyou:rituals"
# 缺正文语言键的仪式（运行结束时报警并以非零码退出）
MISSING_TEXT = []
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
    ("kanayamahiko_circle", "minecraft:furnace", True),
    ("houjouno_teihou_circle", "minecraft:wheat_seeds", True),
    # 星移之仪：增幅核全量洗练。no_recipes=False —— 其 seii_core_* 配方只出 effect
    # （不产出物品），没有对应物品条目，配方页是玩家获知催化剂的唯一途径。
    ("seii_circle", "gensokyou:amp_core_t1", False),
    # 结界破坏：无 activation/passive 配方（祭品走 pattern requirements，非配方目录），
    # 故 no_recipes=True。阶级参数页现算缓存/流失/备料，供灵上限随归元托管核变动。
    ("barrier_break_circle", "gensokyou:sukima_fragment", True),
    # 百鬼夜行：召唤仪式。配方只出 effect（不产出物品），故配方页是玩家获知
    # 召唤配方的唯一途径，不能 no_recipes。3 条配方 <= MAX_RECIPE_PAGES，故会补配方页。
    ("hyakki_yagyo_circle", "minecraft:soul_lantern", False),
    # 灵浴：浴亭内的玩家注灵仪式。**无任何配方**（充灵靠槽核/路由供灵，不烧材料），
    # 故 no_recipes=True。阶级参数页现算缓存/受灵上限/注灵速率/兑换比。
    ("reiyoku_circle", "minecraft:sea_lantern", True),
    # 忘川灯坛：全亮门控产灵。**无任何配方**（不烧材料，靠逐根点蜡烛），
    # 故 no_recipes=True。阶级参数页现算产灵/缓存/供灵上限/蜡烛数/单盏熄灭率。
    ("bousen_circle", "minecraft:candle", True),
    # 少名：配方来自 brew_recipes + 全注册表反查（数十条，且每条要展示三个阶的产物，
    # 是"开放式"配方而非封闭配方表），故 no_recipes=True —— 标准阶页只算数值，
    # 配方展示归 JEI 炼药页签（一卡三阶平铺，正是本仪式的卖点）
    ("sunako_circle", "minecraft:brewing_stand", True),
]

ITEM_RECIPE_ENTRIES = [
    ("gensokyou:danmaku_weapon", "ritual", "zaohua_danmaku_weapon_frame", "weapons", 10),
    ("gensokyou:core_sphere_single", "ritual", "zaohua_core_sphere_single", "weapons", 11),
    ("gensokyou:core_sphere_shotgun", "ritual", "zaohua_core_sphere_shotgun", "weapons", 12),
    ("gensokyou:core_knife", "ritual", "zaohua_core_knife", "weapons", 13),
    ("gensokyou:core_talisman", "ritual", "zaohua_core_talisman", "weapons", 14),
    ("gensokyou:core_laser_gun", "ritual", "zaohua_core_laser_gun", "weapons", 15),
    ("gensokyou:weapon_core_lv1", "ritual", "zaohua_weapon_core_lv1", "weapons", 16),
    ("gensokyou:weapon_core_lv2", "ritual", "zaohua_weapon_core_lv2", "weapons", 17),
    ("gensokyou:amp_core_t1", "ritual", "zaohua_amp_core_t1", "weapons", 18),
    ("gensokyou:amp_core_t2", "ritual", "zaohua_amp_core_t2", "weapons", 19),
    ("gensokyou:spirit_core_0", "crafting", "spirit_core_0", "items", 40),
    ("gensokyou:spirit_core_1", "ritual", "zaohua_spirit_core_1", "items", 41),
    ("gensokyou:spirit_core_2", "ritual", "zaohua_spirit_core_2", "items", 42),
    ("gensokyou:ritual_pedestal", "crafting", "ritual_pedestal", "items", 43),
    ("gensokyou:danmaku_assembly_bench", "crafting", "danmaku_assembly_bench", "weapons", 44),
]

ITEM_RECIPE_TEXT_OVERRIDES = {
    "gensokyou:ritual_pedestal": "gensokyou.book.entry.item_recipe.pedestal.p1",
    "gensokyou:danmaku_weapon": "gensokyou.book.entry.item_danmaku_weapon.p1",
    "gensokyou:danmaku_assembly_bench": "gensokyou.book.entry.item_danmaku_assembly_bench.p1",
}


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
    gk_low = [(i, w) for i, w in loot.get("gensokyou_low", []) if w > 0]
    gk_high = [(i, w) for i, w in loot.get("gensokyou_high", []) if w > 0]
    gk_low_total = base_total + sum(w for _, w in gk_low)
    gk_high_total = base_total + sum(w for _, w in gk_high)

    out = []
    for i, w in base:
        out.append((i, w / base_total * 100 if base_total > 0 else 0.0, 0))
    for i, w in nether_pos:
        out.append((i, w / nether_total * 100 if nether_total > 0 else 0.0, 1))
    for i, w in end_pos:
        out.append((i, w / end_total * 100 if end_total > 0 else 0.0, 2))
    for i, w in gk_low:
        out.append((i, w / gk_low_total * 100 if gk_low_total > 0 else 0.0, 3))
    for i, w in gk_high:
        out.append((i, w / gk_high_total * 100 if gk_high_total > 0 else 0.0, 4))
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
        data = json.load(handle)
    special = []
    if level >= 1:
        special = (list(data["entries"]) + list(data.get("gensokyou_low", []))
                   + list(data.get("gensokyou_high", [])))
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


def intro_text_keys(path):
    """收集该仪式的正文（故事）页键，按页序返回。

    <p>两种形态都合法：
    * ``.text``——单页正文，绝大多数仪式用它；
    * ``.p1``/``.p2``/…——长正文按段拆页（Patchouli 页列表是静态的，无法运行期
      增页，故长文本在生成期拆成多张 ``patchouli:text``），见 guide-book
      「仪式章节配方卡与结构展示」。

    <p>正文页数量由语言文件决定，而不是硬编码一页：玩家读的是多段落的连贯叙述，
    硬塞进单页会变成技术手册那样的长块。每段一页，翻页节奏才正常。

    <p>一个键都没有时返回空列表并由调用方报警 —— 少了语言键会在游戏内显示原始
    key 字符串，这种缺陷必须吵，不能静默（见 tasks 14.7）。
    """
    prefix = f"gensokyou.book.entry.ritual.{path}."
    with open(LANG_FILE, "r", encoding="utf-8") as handle:
        lang = json.load(handle)
    single = None
    numbered = []
    for key in lang:
        if not key.startswith(prefix):
            continue
        tail = key[len(prefix):]
        if tail == "text":
            single = key
        elif tail.startswith("p") and tail[1:].isdigit():
            numbered.append((int(tail[1:]), key))
    numbered.sort()
    if numbered:
        # 拆页形态优先：它是 .text 的细化，两者同存时不该把整段再重复一遍。
        return [key for _, key in numbered]
    return [single] if single else []


def build_entry(path, icon, no_recipes, sortnum):
    ritual_file = os.path.join(RITUALS_DIR, path + ".json")
    with open(ritual_file, "r", encoding="utf-8") as handle:
        data = json.load(handle)
    ritual_id = data["id"]
    palette = data["palette"]
    anchor = data["anchorKey"]
    levels = sorted({entry["level"] for entry in data["levels"]})

    intro_keys = intro_text_keys(path)
    if not intro_keys:
        MISSING_TEXT.append(path)
    pages = [{"type": "patchouli:text", "text": key} for key in intro_keys]

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
            entries = tool_loot_entries(path, table["tier"])
            base = [e for e in entries if e[2] <= 2]
            gk = [e for e in entries if e[2] >= 3]
            tool_item = tool_item_for(tool_tag, table["tier"])
            pages.append({
                "type": "gensokyou:loot_page",
                "tool_item": tool_item,
                "tier": table["tier"],
                "mode": "tool",
                "entries": _enc(base),
            })
            loot_pages += 1
            # 信物带（低/中）单开一页，避免与基础池同页超框。
            if gk:
                pages.append({
                    "type": "gensokyou:loot_page",
                    "tool_item": tool_item,
                    "tier": table["tier"],
                    "mode": "tool",
                    "entries": _enc(gk),
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


def build_item_entry(item_id, source, recipe_name, category, sortnum):
    namespace, path = item_id.split(":", 1)
    if source == "ritual":
        recipe_file = os.path.join(RECIPES_DIR, "zaohua_circle.json")
        with open(recipe_file, "r", encoding="utf-8") as handle:
            recipe_data = json.load(handle)
        recipes = {recipe["name"]: recipe for recipe in recipe_data["recipes"]}
        recipe = recipes[recipe_name]
        description_key = "gensokyou.book.entry.item_recipe.p1"
        recipe_page = {
            "type": "gensokyou:ritual_page",
            "ritual": recipe_data["pattern"],
            "recipe": f"gensokyou:{recipe_name}",
        }
        entry_gate = gate_for(recipe.get("minTier", 1)) or ""
    elif source == "crafting":
        description_key = "gensokyou.book.entry.item_recipe.crafting.p1"
        recipe_page = {
            "type": "patchouli:crafting",
            "recipe": f"gensokyou:{recipe_name}",
        }
        entry_gate = ""
    else:
        raise ValueError(f"unknown item recipe source: {source}")
    description_key = ITEM_RECIPE_TEXT_OVERRIDES.get(item_id, description_key)
    entry = {
        "name": f"item.{namespace}.{path}",
        "category": f"gensokyou:{category}",
        "icon": item_id,
        "sortnum": sortnum,
        "pages": [
            {
                "type": "patchouli:spotlight",
                "item": item_id,
                "text": description_key,
            },
            recipe_page,
        ],
    }
    if entry_gate:
        entry["advancement"] = entry_gate
        entry["secret"] = True
    out = os.path.join(ENTRIES_DIR, f"item_{path}.json")
    with open(out, "w", encoding="utf-8") as handle:
        json.dump(entry, handle, ensure_ascii=False, indent=2)
    print(f"item:{path:<25} sortnum={sortnum:<3} source={source} recipe={recipe_name} "
          f"entry_gate={entry_gate or '-'}")


def build_item_entries():
    for item_id, source, recipe_name, category, sortnum in ITEM_RECIPE_ENTRIES:
        build_item_entry(item_id, source, recipe_name, category, sortnum)


def main():
    args = sys.argv[1:]
    unknown = [arg for arg in args if arg != "--items-only"]
    if unknown:
        raise SystemExit(f"unknown arguments: {' '.join(unknown)}")
    if "--items-only" not in args:
        for sortnum, (path, icon, no_recipes) in enumerate(ENTRIES, start=1):
            build_entry(path, icon, no_recipes, sortnum)
    build_item_entries()
    if MISSING_TEXT:
        # 缺键 = 故事页被整条抹掉（条目直接从结构页开始，读者看不到任何来历）。
        # 必须失败，别让缺陷静默溜过去。
        print("\nMISSING intro text keys in", os.path.relpath(LANG_FILE, REPO),
              "(the entry would open straight onto the structure page):",
              file=sys.stderr)
        for path in MISSING_TEXT:
            print(f"  - gensokyou.book.entry.ritual.{path}.text"
                  f"  (or .p1 / .p2 ...)", file=sys.stderr)
        raise SystemExit(1)


if __name__ == "__main__":
    main()
