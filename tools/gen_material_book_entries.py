#!/usr/bin/env python3
"""批量生成「幻想素材」指导书章节（fantasy-materials-book）。

五座资源仪式（大山祇/埴山姬/久久能智/绵津见/草野姬）的产物、乃至三种矿物的煅炉
配比，全部由数据派生，本脚本只负责排版——改 `ritual_loot/`、`ritual_special/`、
`ritual_smelt_recipes/` 后重跑即可，条目不会与实际产出脱节：

  总览页（常驻） → 每座资源仪式一条：引言页 + 逐带产出页。

**分级**：素材与仪式结构同阶。未解锁的带整页挂世界进度门槛（进入下界/进入末地），
玩家抵达后页面自行浮现（guide-book 与仪式条目同一套门槛口径）。
  低阶带（指导书信物）  常驻可见
  下界池（≥3 凋灵骷髅头）gensokyou:guide/nether_unlock
  末地池（≥1 龙首）      gensokyou:guide/end_unlock
  高阶带（隙间碎片信物）  gensokyou:guide/end_unlock
高阶带与末地池同属末地档：两者都是玩家推进到末地前后才会去解的深层产出，
而真正的分带条件（指导书/隙间碎片/骷髅头/龙首）由每页的带标签逐页写明，不含糊。

三种矿物（辰砂/灵铁/星银）各一条，煅炉配方由 `ritual_smelt_recipes` 派生后交给
`gensokyou:smelt_page` 渲染，与 JEI 煅炉页签同一数据源。

用法： python tools/gen_material_book_entries.py
"""

import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from gen_ritual_book_entries import (  # noqa: E402
    SPECIAL_DIR, _enc, tool_loot_entries,
)

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ENTRIES_DIR = os.path.join(
    REPO,
    "src/main/resources/assets/gensokyou/patchouli_books/gensokyou_book/en_us/entries")

CATEGORY = "gensokyou:fantasy_materials"

# 分带 → 世界进度门槛（与 gen_ritual_multiblock.gate_for 同一口径：阶 1 下界、阶 2 末地）
GATE_BY_SECTION = {
    1: "gensokyou:guide/nether_unlock",
    2: "gensokyou:guide/end_unlock",
    3: "",
    4: "gensokyou:guide/end_unlock",
}
BAND_ORDER = [3, 1, 2, 4]  # 低阶带 → 下界池 → 末地池 → 高阶带

# 折算权重时假定的工具材质。书页不摆工具（见 SOURCES 注释），此值只影响内联的
# 相对概率留档，取中间档 iron 以免暗示任何特定材质才是正解。
DISPLAY_TIER = "iron"

# (仪式 pattern, 词条名后缀, 图标, 是否钓鱼池)
# 刻意不带 tool_item：书页若摆一把铁镐，玩家会以为只有铁器才出产。
# 素材带与工具材质无关（同一件神木，木斧金斧都一样），
# 故本章节不摆具体工具，献祭工具的种类写在各条引言里；逐工具材质的确切产出见仪式条目。
SOURCES = [
    ("oyamatsumi_circle", "ores", "gensokyou:cinnabar", False),
    ("haniyasu_circle", "earth", "gensokyou:spirit_soil", False),
    ("kukunochi_circle", "wood", "gensokyou:sacred_wood", False),
    ("watatsumi_circle", "marine", "gensokyou:tide_crystal", True),
    ("kaya_no_hime_circle", "plants", "gensokyou:spirit_herb", False),
]

# 三种矿物：条目名直接用物品本地化名，故无需新标题键。
MINERALS = [
    ("gensokyou:refined_cinnabar", "refined_cinnabar", ""),
    ("gensokyou:spirit_iron", "spirit_iron", ""),
    ("gensokyou:star_silver", "star_silver", "gensokyou:guide/end_unlock"),
]

SMELT_PATTERN = "gensokyou:kanayamahiko_circle"
CREATION_PATTERN = "gensokyou:zaohua_circle"

# 非矿物、但同属本章的炼成物：(物品, 词条名后缀, 造化仪配方 id, 用途页文本键, 条目门槛)。
# 灵炭是煅炉的引火物：它自己没被煅炉炼出来，而是造化仪闷出来的，故走 ritual_page。
CRAFTABLES = [
    ("gensokyou:spirit_charcoal", "spirit_charcoal", "gensokyou:zaohua_spirit_charcoal",
     "gensokyou.book.entry.material_spirit_charcoal.p2", ""),
]


def watatsumi_bands():
    """绵津见：低/高两带共用一个「海洋特产」池，概率按池内总权重折算。"""
    with open(os.path.join(SPECIAL_DIR, "watatsumi_special.json"), encoding="utf-8") as handle:
        data = json.load(handle)
    low = [(i, w) for i, w in data.get("gensokyou_low", []) if w > 0]
    high = [(i, w) for i, w in data.get("gensokyou_high", []) if w > 0]
    total = sum(w for _, w in low) + sum(w for _, w in high)
    bands = {}
    for section, picked in ((3, low), (4, high)):
        if picked:
            bands[section] = [(i, w / total * 100 if total > 0 else 0.0) for i, w in picked]
    return bands


def tool_bands(path):
    """四座工具献祭仪式：低/高带与下界/末地池一并折算（与 JEI 同一权重模型）。"""
    entries = tool_loot_entries(path, DISPLAY_TIER)
    bands = {}
    for section in BAND_ORDER:
        picked = [(item, pct) for item, pct, sec in entries if sec == section]
        if picked:
            bands[section] = picked
    return bands


def band_page(entries, section):
    # 不摆 tool_item / tier：素材带与工具材质无关，摆一把铁镐会误导成「只有铁器才出产」；
    # tier 变量在 loot_page 里只服务于钓竿模式，这里带上有 dead data。
    page = {
        "type": "gensokyou:loot_page",
        "mode": "tool",
        "entries": _enc([(item, pct, section) for item, pct in entries]),
    }
    gate = GATE_BY_SECTION.get(section, "")
    if gate:
        page["advancement"] = gate
    return page


def build_source_entry(path, suffix, icon, fishing, sortnum):
    bands = watatsumi_bands() if fishing else tool_bands(path)
    pages = [{"type": "patchouli:text", "text": f"gensokyou.book.entry.material_{suffix}.p1"}]
    for section in BAND_ORDER:
        entries = bands.get(section)
        if not entries:
            continue
        pages.append(band_page(entries, section))
    write(f"material_{suffix}.json", {
        "name": f"gensokyou.book.entry.material_{suffix}",
        "category": CATEGORY,
        "icon": icon,
        "sortnum": sortnum,
        "pages": pages,
    })
    gated = sum(1 for page in pages if page.get("advancement"))
    print(f"material:{suffix:<18} sortnum={sortnum:<3} bands={len(pages) - 1} "
          f"gated_pages={gated} pages={len(pages)}")


def build_overview(sortnum):
    write("fantasy_materials_overview.json", {
        "name": "gensokyou.book.entry.fantasy_materials_overview",
        "category": CATEGORY,
        "icon": "gensokyou:refined_cinnabar",
        "sortnum": sortnum,
        "pages": [
            {"type": "patchouli:text", "text": f"gensokyou.book.entry.fantasy_materials_overview.p{n}"}
            for n in (1, 2, 3)
        ],
    })
    print(f"material:{'overview':<18} sortnum={sortnum:<3} pages=3")


def entry_name(item_id):
    return f"item.{item_id.split(':', 1)[0]}.{item_id.split(':', 1)[1]}"


def build_mineral_entry(item_id, suffix, gate, sortnum):
    entry = {
        "name": entry_name(item_id),
        "category": CATEGORY,
        "icon": item_id,
        "sortnum": sortnum,
        "pages": [
            {"type": "patchouli:spotlight", "item": item_id,
             "text": f"gensokyou.book.entry.material_{suffix}.p1"},
            {"type": "gensokyou:smelt_page", "ritual": SMELT_PATTERN, "result": item_id},
        ],
    }
    if gate:
        entry["advancement"] = gate
        entry["secret"] = True
    write(f"material_{suffix}.json", entry)
    print(f"item:{suffix:<24} sortnum={sortnum:<3} entry_gate={gate or '-'}")


def build_craftable_entry(item_id, suffix, recipe, usage_key, gate, sortnum):
    """非矿物、但同属本章的炼成物：给造化仪配方页（而非煅炉配方页）＋一句用途。"""
    entry = {
        "name": entry_name(item_id),
        "category": CATEGORY,
        "icon": item_id,
        "sortnum": sortnum,
        "pages": [
            {"type": "patchouli:spotlight", "item": item_id,
             "text": f"gensokyou.book.entry.material_{suffix}.p1"},
            {"type": "gensokyou:ritual_page", "ritual": CREATION_PATTERN, "recipe": recipe},
            {"type": "patchouli:text", "text": usage_key},
        ],
    }
    if gate:
        entry["advancement"] = gate
        entry["secret"] = True
    write(f"material_{suffix}.json", entry)
    print(f"item:{suffix:<24} sortnum={sortnum:<3} recipe={recipe} entry_gate={gate or '-'}")


def write(filename, data):
    out = os.path.join(ENTRIES_DIR, filename)
    with open(out, "w", encoding="utf-8") as handle:
        json.dump(data, handle, ensure_ascii=False, indent=2)


def main():
    args = sys.argv[1:]
    if args:
        raise SystemExit(f"unknown arguments: {' '.join(args)}")
    stale = os.path.join(ENTRIES_DIR, "materials_overview.json")
    if os.path.exists(stale):
        raise SystemExit(
            f"旧的总览词条仍在 items 分类下：{stale}\n"
            "它已由 fantasy_materials_overview 取代，请先删除再重跑（避免两处「素材」词条并存）。")
    build_overview(0)
    for offset, (path, suffix, icon, fishing) in enumerate(SOURCES, start=1):
        build_source_entry(path, suffix, icon, fishing, offset)
    for offset, (item_id, suffix, gate) in enumerate(MINERALS, start=len(SOURCES) + 1):
        build_mineral_entry(item_id, suffix, gate, offset)
    for offset, craftable in enumerate(CRAFTABLES, start=len(SOURCES) + len(MINERALS) + 1):
        build_craftable_entry(*craftable, offset)


if __name__ == "__main__":
    main()
