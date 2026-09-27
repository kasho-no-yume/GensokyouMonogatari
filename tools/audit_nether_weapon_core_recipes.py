import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DATA = ROOT / "src/main/resources/data/gensokyou"
ASSETS = ROOT / "src/main/resources/assets/gensokyou"
RECIPE_PATH = DATA / "ritual_recipes/zaohua_circle.json"
PATTERN_PATH = DATA / "rituals/zaohua_circle.json"
ENTRIES = ASSETS / "patchouli_books/gensokyou_book/en_us/entries"

EXPECTED = {
    "zaohua_danmaku_weapon_frame": (1, 20000, "gensokyou:danmaku_weapon", {
        "gensokyou:ritual_stone_1": 1,
        "gensokyou:spirit_iron": 2,
        "minecraft:netherite_ingot": 1,
        "gensokyou:star_silver": 1,
        "gensokyou:ppoint": 4,
    }),
    "zaohua_core_sphere_single": (1, 8000, "gensokyou:core_sphere_single", {
        "gensokyou:refined_cinnabar": 1,
        "minecraft:quartz": 2,
        "minecraft:amethyst_shard": 2,
        "gensokyou:ppoint": 2,
    }),
    "zaohua_core_sphere_shotgun": (1, 12000, "gensokyou:core_sphere_shotgun", {
        "gensokyou:refined_cinnabar": 2,
        "minecraft:blaze_powder": 2,
        "minecraft:redstone": 4,
        "minecraft:gunpowder": 2,
    }),
    "zaohua_core_knife": (1, 10000, "gensokyou:core_knife", {
        "gensokyou:spirit_iron": 1,
        "minecraft:quartz": 2,
        "minecraft:iron_ingot": 2,
        "minecraft:amethyst_shard": 1,
    }),
    "zaohua_core_talisman": (2, 24000, "gensokyou:core_talisman", {
        "gensokyou:talisman_paper": 4,
        "gensokyou:tide_crystal": 1,
        "gensokyou:magic_mushroom": 1,
        "gensokyou:star_silver": 1,
    }),
    "zaohua_core_laser_gun": (2, 24000, "gensokyou:core_laser_gun", {
        "gensokyou:star_silver": 2,
        "gensokyou:tide_crystal": 1,
        "minecraft:sea_lantern": 1,
        "minecraft:redstone_block": 1,
    }),
    "zaohua_weapon_core_lv1": (1, 20000, "gensokyou:weapon_core_lv1", {
        "gensokyou:ritual_stone_1": 1,
        "minecraft:quartz_block": 1,
        "minecraft:redstone_block": 1,
        "gensokyou:ppoint": 4,
    }),
    "zaohua_weapon_core_lv2": (2, 40000, "gensokyou:weapon_core_lv2", {
        "gensokyou:ritual_stone_2": 1,
        "gensokyou:star_silver": 2,
        "gensokyou:tide_crystal": 1,
        "gensokyou:broken_spell_card_star": 4,
    }),
    "zaohua_amp_core_t1": (1, 30000, "gensokyou:amp_core_t1", {
        "gensokyou:talisman_paper": 2,
        "gensokyou:memory_fragment": 4,
        "minecraft:amethyst_shard": 4,
        "gensokyou:broken_spell_card_star": 1,
    }),
    "zaohua_amp_core_t2": (2, 60000, "gensokyou:amp_core_t2", {
        "gensokyou:star_silver": 1,
        "gensokyou:tide_crystal": 1,
        "gensokyou:talisman_paper": 2,
        "gensokyou:memory_fragment": 8,
    }),
    "zaohua_spirit_core_1": (1, 20000, "gensokyou:spirit_core_1", {
        "gensokyou:ritual_stone_1": 1,
        "gensokyou:spirit_iron": 1,
        "minecraft:soul_sand": 4,
        "minecraft:quartz": 4,
    }),
    "zaohua_spirit_core_2": (2, 50000, "gensokyou:spirit_core_2", {
        "gensokyou:ritual_stone_2": 1,
        "gensokyou:star_silver": 2,
        "gensokyou:tide_crystal": 1,
        "gensokyou:sukima_fragment": 1,
    }),
}

ITEM_ENTRIES = {
    "danmaku_weapon": ("gensokyou:weapons", "ritual", "zaohua_danmaku_weapon_frame", "gensokyou:guide/nether_unlock"),
    "core_sphere_single": ("gensokyou:weapons", "ritual", "zaohua_core_sphere_single", "gensokyou:guide/nether_unlock"),
    "core_sphere_shotgun": ("gensokyou:weapons", "ritual", "zaohua_core_sphere_shotgun", "gensokyou:guide/nether_unlock"),
    "core_knife": ("gensokyou:weapons", "ritual", "zaohua_core_knife", "gensokyou:guide/nether_unlock"),
    "core_talisman": ("gensokyou:weapons", "ritual", "zaohua_core_talisman", "gensokyou:guide/end_unlock"),
    "core_laser_gun": ("gensokyou:weapons", "ritual", "zaohua_core_laser_gun", "gensokyou:guide/end_unlock"),
    "weapon_core_lv1": ("gensokyou:weapons", "ritual", "zaohua_weapon_core_lv1", "gensokyou:guide/nether_unlock"),
    "weapon_core_lv2": ("gensokyou:weapons", "ritual", "zaohua_weapon_core_lv2", "gensokyou:guide/end_unlock"),
    "amp_core_t1": ("gensokyou:weapons", "ritual", "zaohua_amp_core_t1", "gensokyou:guide/nether_unlock"),
    "amp_core_t2": ("gensokyou:weapons", "ritual", "zaohua_amp_core_t2", "gensokyou:guide/end_unlock"),
    "spirit_core_0": ("gensokyou:items", "crafting", "spirit_core_0", None),
    "spirit_core_1": ("gensokyou:items", "ritual", "zaohua_spirit_core_1", "gensokyou:guide/nether_unlock"),
    "spirit_core_2": ("gensokyou:items", "ritual", "zaohua_spirit_core_2", "gensokyou:guide/end_unlock"),
}

ITEM_TEXT_OVERRIDES = {
    "danmaku_weapon": "gensokyou.book.entry.item_danmaku_weapon.p1",
}

# 源初造化中不属于本变更、但必须一并存在的基础配方
PREEXISTING_RECIPES = {
    "zaohua_stone_t1",
    "zaohua_stone_t2",
    "zaohua_talisman_paper",
    "zaohua_codex_of_beings",
    "zaohua_spellcard_star",
}

# 碎符卡星为 BOSS 专属稀缺物：任何配方都不得产出（zaohua-crafting 需求）
BOSS_ONLY_ITEMS = {"gensokyou:broken_spell_card_star"}
STAR_ITEMS = {"gensokyou:spellcard_star", "gensokyou:broken_spell_card_star"}

# 弹幕系统的入口级物品：配方一旦卡在星类材料（BOSS 掉落）之后，
# 就会形成「无台 → 无武器 → 打不过 BOSS → 无星」的死锁（danmaku-assembly-bench / danmaku-weapon-crafting 需求）
ENTRY_ITEMS = {"gensokyou:danmaku_weapon", "gensokyou:danmaku_assembly_bench"}

recipe_data = json.loads(RECIPE_PATH.read_text(encoding="utf-8"))
assert recipe_data["pattern"] == "gensokyou:zaohua_circle"
assert len(recipe_data["recipes"]) == 17
recipes = {recipe["name"]: recipe for recipe in recipe_data["recipes"]}
assert len(recipes) == 17
assert set(recipes) == set(EXPECTED) | PREEXISTING_RECIPES
assert "zaohua_spirit_core_0" not in recipes

pattern = json.loads(PATTERN_PATH.read_text(encoding="utf-8"))
pedestal_capacity = {0: 0}
for level in sorted(entry["level"] for entry in pattern["levels"]):
    tier = next(entry for entry in pattern["levels"] if entry["level"] == level)
    added = sum(1 if entry[0] == "P" and entry[1] == 0 and entry[3] == 0 else 4
                for entry in tier["adds"] if entry[0] == "P")
    pedestal_capacity[level] = pedestal_capacity.get(level - 1, 0) + added
assert pedestal_capacity[0] >= 8
assert pedestal_capacity[1] >= 12
assert pedestal_capacity[2] >= 20

for name, (min_tier, sp_cost, result_item, expected_ingredients) in EXPECTED.items():
    recipe = recipes[name]
    assert recipe["mode"] == "activation"
    assert recipe["match"] == "max"
    assert recipe["minTier"] == min_tier
    assert recipe["spCost"] == sp_cost
    assert "effect" not in recipe
    assert recipe["result"] == {"item": result_item, "count": 1}
    actual_ingredients = {}
    for ingredient in recipe["ingredients"]:
        assert set(ingredient) == {"item", "count"}
        assert ingredient["item"] not in actual_ingredients
        actual_ingredients[ingredient["item"]] = ingredient["count"]
    assert actual_ingredients == expected_ingredients
    assert any(item.startswith("gensokyou:") for item in actual_ingredients)
    assert sum(expected_ingredients.values()) <= pedestal_capacity[min_tier]
    assert "gensokyou:danmaku_weapon" not in expected_ingredients
    assert not any(item.startswith("gensokyou:spirit_core_") for item in expected_ingredients)

assert not (DATA / "recipe/danmaku_weapon.json").exists()

# 入口级物品（武器 / 方术台）的配方不得消耗星类材料
bench = json.loads((DATA / "recipe/danmaku_assembly_bench.json").read_text(encoding="utf-8-sig"))
assert bench["result"]["id"] == "gensokyou:danmaku_assembly_bench"
bench_symbols = {symbol: value["item"] for symbol, value in bench["key"].items()}
assert set(bench_symbols.values()) <= {
    "gensokyou:ritual_stone_0", "minecraft:iron_ingot", "gensokyou:refined_cinnabar",
}, bench_symbols
assert not STAR_ITEMS & set(bench_symbols.values())
assert "spCost" not in bench

frame = recipes["zaohua_danmaku_weapon_frame"]
frame_items = {ingredient["item"] for ingredient in frame["ingredients"]}
assert frame["result"]["item"] in ENTRY_ITEMS
assert not STAR_ITEMS & frame_items, "武器框不得消耗星类材料，否则无法在 BOSS 之前造出武器"

normal_results = set()
for path in (DATA / "recipe").glob("*.json"):
    data = json.loads(path.read_text(encoding="utf-8-sig"))
    normal_results.add(data["result"]["id"])
bootstrap_result = "gensokyou:spirit_core_0"
assert normal_results.isdisjoint({value[2] for value in EXPECTED.values()})
assert bootstrap_result in normal_results

bootstrap = json.loads((DATA / "recipe/spirit_core_0.json").read_text(encoding="utf-8-sig"))
assert bootstrap["type"] == "minecraft:crafting_shapeless"
assert bootstrap["result"] == {"id": bootstrap_result, "count": 1}
assert "spCost" not in bootstrap
bootstrap_ingredients = {}
for ingredient in bootstrap["ingredients"]:
    assert set(ingredient) == {"item"}
    bootstrap_ingredients[ingredient["item"]] = bootstrap_ingredients.get(ingredient["item"], 0) + 1
assert bootstrap_ingredients == {
    "gensokyou:ritual_stone_0": 4,
    "gensokyou:ppoint": 4,
    "minecraft:amethyst_block": 1,
}
assert len(bootstrap["ingredients"]) == 9
assert not any(item.startswith("gensokyou:spirit_core_") for item in bootstrap_ingredients)

recipe_paths = list((DATA / "recipe").glob("*.json")) + list((DATA / "ritual_recipes").glob("*.json"))
for path in recipe_paths:
    data = json.loads(path.read_text(encoding="utf-8-sig"))
    text = json.dumps(data)
    for item in ("core_laser_cannon", "weapon_core_lv3", "amp_core_t3"):
        assert f"gensokyou:{item}" not in text
    if path.parent.name == "ritual_recipes":
        for recipe in data["recipes"]:
            result = recipe.get("result", {})
            assert result.get("item") not in BOSS_ONLY_ITEMS, f"{path.name} 不得量产 {result.get('item')}"
    else:
        assert data["result"]["id"] not in BOSS_ONLY_ITEMS, f"{path.name} 不得量产 {data['result']['id']}"

for item_path, (category, source, recipe_name, gate) in ITEM_ENTRIES.items():
    path = ENTRIES / f"item_{item_path}.json"
    data = json.loads(path.read_text(encoding="utf-8-sig"))
    assert data["name"] == f"item.gensokyou.{item_path}"
    assert data["category"] == category
    assert data["icon"] == f"gensokyou:{item_path}"
    if source == "ritual":
        expected_text = ITEM_TEXT_OVERRIDES.get(item_path, "gensokyou.book.entry.item_recipe.p1")
        assert data["pages"][0]["text"] == expected_text
        assert data["pages"][1] == {
            "type": "gensokyou:ritual_page",
            "ritual": "gensokyou:zaohua_circle",
            "recipe": f"gensokyou:{recipe_name}",
        }
    else:
        assert data["pages"][0]["text"] == "gensokyou.book.entry.item_recipe.crafting.p1"
        assert data["pages"][1] == {
            "type": "patchouli:crafting",
            "recipe": f"gensokyou:{recipe_name}",
        }
    assert data.get("advancement") == gate
    assert data.get("secret", False) == (gate is not None)

zaohua_entry = json.loads((ENTRIES / "ritual_zaohua_circle.json").read_text(encoding="utf-8"))
assert all(page.get("show_recipes") is False for page in zaohua_entry["pages"]
           if page.get("type") == "gensokyou:ritual_tier_page")

for lang_name in ("zh_cn.json", "en_us.json"):
    lang = json.loads((ASSETS / "lang" / lang_name).read_text(encoding="utf-8"))
    for recipe_name in EXPECTED:
        assert f"jei.gensokyou.recipe.{recipe_name}" in lang
    assert "jei.gensokyou.recipe.zaohua_spirit_core_0" not in lang
    assert "gensokyou.book.entry.item_recipe.p1" in lang
    assert "gensokyou.book.entry.item_recipe.crafting.p1" in lang

assert all((ENTRIES / f"item_{name}.json").exists() for name in ITEM_ENTRIES)
print("nether weapon core recipe audit ok")
