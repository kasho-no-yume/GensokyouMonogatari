import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DATA = ROOT / "src/main/resources/data"
RULE_PATH = DATA / "gensokyou/ritual_smelt_recipes/kanayamahiko_circle.json"
EXPECTED = {
    "gensokyou:cinnabar": ("gensokyou:spirit_charcoal", 1, "gensokyou:refined_cinnabar"),
    "gensokyou:rough_cinnabar_ore": ("gensokyou:spirit_charcoal", 1, "gensokyou:refined_cinnabar"),
    "gensokyou:spirit_iron_ore": ("gensokyou:spirit_charcoal", 1, "gensokyou:spirit_iron"),
    "gensokyou:rough_spirit_iron_ore": ("gensokyou:spirit_charcoal", 1, "gensokyou:spirit_iron"),
    "gensokyou:star_silver_ore": ("gensokyou:spirit_charcoal", 2, "gensokyou:star_silver"),
    "gensokyou:rough_star_silver_ore": ("gensokyou:spirit_charcoal", 2, "gensokyou:star_silver"),
}

rule_data = json.loads(RULE_PATH.read_text(encoding="utf-8"))
assert rule_data["pattern"] == "gensokyou:kanayamahiko_circle"
actual = {}
for rule in rule_data["rules"]:
    result = rule["result"]
    actual[rule["primary"]] = (rule["auxiliary"], rule["auxiliary_count"], result["item"])
    assert result["count"] == 1
assert actual == EXPECTED

for registry in ("item", "block"):
    tag = json.loads((DATA / f"c/tags/{registry}/ores.json").read_text(encoding="utf-8"))
    assert set(tag["values"]) == {
        "gensokyou:cinnabar",
        "gensokyou:spirit_iron_ore",
        "gensokyou:star_silver_ore",
    }
    assert not any("rough_" in value for value in tag["values"])

assert not (DATA / "gensokyou/ritual_recipes/kanayamahiko_circle.json").exists()
for path in DATA.rglob("*.json"):
    if "recipe" not in path.parts:
        continue
    text = path.read_text(encoding="utf-8")
    if any(f'"{kind}"' in text for kind in ("smelting", "blasting", "smoking")):
        assert "gensokyou:refined_cinnabar" not in text
        assert "gensokyou:spirit_iron" not in text
        assert "gensokyou:star_silver" not in text

print("kanayamahiko audit ok")
