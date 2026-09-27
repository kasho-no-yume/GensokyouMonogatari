import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DATA = ROOT / "src/main/resources/data/gensokyou"
ASSETS = ROOT / "src/main/resources/assets/gensokyou"
RECIPE_PATH = DATA / "recipe/ritual_pedestal.json"
ENTRIES = ASSETS / "patchouli_books/gensokyou_book/en_us/entries"
ENTRY_PATH = ENTRIES / "item_ritual_pedestal.json"

BOOTSTRAP_INGREDIENTS = {
    "minecraft:smooth_stone": 6,
    "gensokyou:ritual_stone_0": 2,
    "gensokyou:ppoint": 1,
}

recipe = json.loads(RECIPE_PATH.read_text(encoding="utf-8-sig"))
assert recipe["type"] == "minecraft:crafting_shaped", recipe["type"]
assert recipe["pattern"] == ["SSS", "RPR", "SSS"], recipe["pattern"]
assert recipe["result"] == {"id": "gensokyou:ritual_pedestal", "count": 1}, recipe["result"]

key_items = {symbol: value["item"] for symbol, value in recipe["key"].items()}
assert set(key_items) == {"S", "R", "P"}, key_items
assert key_items["S"] == "minecraft:smooth_stone", key_items
assert key_items["R"] == "gensokyou:ritual_stone_0", key_items
assert key_items["P"] == "gensokyou:ppoint", key_items

actual = {}
for row in recipe["pattern"]:
    for symbol in row:
        actual[key_items[symbol]] = actual.get(key_items[symbol], 0) + 1
assert actual == BOOTSTRAP_INGREDIENTS, actual
assert len(recipe["key"]) == len(set(key_items.values())), "每个符号必须唯一对应一种物品"

for forbidden in ("spCost", "minTier", "mode", "match", "xp", "experience", "advancement"):
    assert forbidden not in recipe, f"bootstrap 配方不得含 {forbidden}"

for path in (DATA / "ritual_recipes").glob("*.json"):
    data = json.loads(path.read_text(encoding="utf-8-sig"))
    for recipe_data in data["recipes"]:
        assert recipe_data.get("result", {}).get("item") != "gensokyou:ritual_pedestal", path.name
        for ingredient in recipe_data.get("ingredients", []):
            assert ingredient["item"] != "gensokyou:ritual_pedestal", path.name

loot = json.loads((DATA / "loot_table/blocks/ritual_pedestal.json").read_text(encoding="utf-8-sig"))
assert loot["pools"][0]["entries"][0]["name"] == "gensokyou:ritual_pedestal"

entry = json.loads(ENTRY_PATH.read_text(encoding="utf-8-sig"))
assert entry["name"] == "item.gensokyou.ritual_pedestal", entry["name"]
assert entry["category"] == "gensokyou:items", entry["category"]
assert entry["icon"] == "gensokyou:ritual_pedestal", entry["icon"]
assert entry["pages"][0]["text"] == "gensokyou.book.entry.item_recipe.pedestal.p1"
assert entry["pages"][1] == {
    "type": "patchouli:crafting",
    "recipe": "gensokyou:ritual_pedestal",
}
assert "advancement" not in entry
assert "secret" not in entry

for lang_name in ("zh_cn.json", "en_us.json"):
    lang = json.loads((ASSETS / "lang" / lang_name).read_text(encoding="utf-8"))
    assert lang["item.gensokyou.ritual_pedestal"]
    assert lang["block.gensokyou.ritual_pedestal"] == lang["item.gensokyou.ritual_pedestal"]
    assert lang["gensokyou.book.entry.item_recipe.pedestal.p1"]

for name in ("ritual_stone.json", "ritual_core.json", "spirit_core_0.json"):
    data = json.loads((DATA / "recipe" / name).read_text(encoding="utf-8-sig"))
    assert data["result"]["id"] != "gensokyou:ritual_pedestal", name

print("ritual pedestal recipe audit ok")
