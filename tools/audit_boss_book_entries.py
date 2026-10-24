"""Static guard for boss entries in the Patchouli guide book.

Contract (see openspec change add-boss-guidebook-entries):
  * every boss reachable through the current spawn path has an entry
  * the entry gate matches the boss's own tier (bossTier())
  * sortnum sits in the boss band (100 * tier .. 100 * tier + 99)
  * the entry icon exists (16x16 portrait png or item id)
  * the arrival page exists (anchor "arrival") and shows the boss token
  * a ritual summon page points at a real recipe whose effect is this boss
  * the boss-residual-codex entry lists one gated recipe page per token

It deliberately does NOT require a summon recipe. Later bosses arrive through
world quests or structures; only the discovery source needs to grow then.
"""

from __future__ import annotations

import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "src/main/java/com/bitsson/gensokyou"
ENTRIES = ROOT / "src/main/resources/assets/gensokyou/patchouli_books/gensokyou_book/en_us/entries"
ASSETS = ROOT / "src/main/resources/assets"
DATA = ROOT / "src/main/resources/data/gensokyou"
LANG = ROOT / "src/main/resources/assets/gensokyou/lang/zh_cn.json"

SUMMON_SRC = JAVA / "ritual/behavior/SummonBossEffects.java"
ENTITY_TYPES_SRC = JAVA / "registry/ModEntityTypes.java"
ITEMS_SRC = JAVA / "registry/ModItems.java"

GATE_BY_TIER = {
    1: "gensokyou:guide/nether_unlock",
    2: "gensokyou:guide/end_unlock",
    3: "gensokyou:guide/gensokyo_unlock",
    4: "gensokyou:guide/tier_4",
    5: "gensokyou:guide/tier_5",
}

errors: list[str] = []
warnings: list[str] = []


def fail(message: str) -> None:
    errors.append(message)


def read(path: Path) -> str:
    return path.read_text(encoding="utf-8")


def parse_entity_type_registrations() -> dict[str, tuple[str, str]]:
    """constant name -> (entity id, entity class)."""
    source = read(ENTITY_TYPES_SRC)
    pattern = re.compile(
        r"DeferredHolder<EntityType<\?>, EntityType<([\w.]+)>>\s+(\w+)\s*=\s*"
        r"ENTITY_TYPES\.register\(\"([^\"]+)\"",
        re.S,
    )
    result = {}
    for entity_class, constant, entity_id in pattern.findall(source):
        result[constant] = (entity_id, entity_class)
    return result


def parse_item_registrations() -> dict[str, str]:
    """constant name -> item id."""
    source = read(ITEMS_SRC)
    patterns = [
        r"(\w+)\s*=\s*\n?\s*ITEMS\.register\(\"([^\"]+)\"",
        r"(\w+)\s*=\s*simpleMaterial\(\"([^\"]+)\"\)",
    ]
    result: dict[str, str] = {}
    for pattern in patterns:
        for constant, item_id in re.findall(pattern, source):
            result[constant] = item_id
    return result


def discover_bosses() -> dict[str, dict]:
    """Bosses reachable through the current spawn path, keyed by entity id."""
    entity_types = parse_entity_type_registrations()
    summon_source = read(SUMMON_SRC)
    pattern = re.compile(r"register\(\"([^\"]+)\",\s*ModEntityTypes\.([A-Z0-9_]+)\)")

    bosses: dict[str, dict] = {}
    for effect, constant in pattern.findall(summon_source):
        if constant not in entity_types:
            fail(f"[discovery] unknown ModEntityTypes constant '{constant}' used by effect '{effect}'")
            continue
        entity_id, entity_class = entity_types[constant]
        tier = parse_boss_tier(entity_class)
        if tier is None:
            continue
        bosses[entity_id] = {
            "effect": effect,
            "class": entity_class,
            "tier": tier,
            "token": parse_signature_item(entity_class),
        }
    if not bosses:
        fail("[discovery] no bosses found; SummonBossEffects format changed?")
    return bosses


def entity_source(entity_class: str) -> Path:
    if "." in entity_class:
        return ROOT / "src/main/java" / (entity_class.replace(".", "/") + ".java")
    return JAVA / "entity" / f"{entity_class}.java"


def parse_boss_tier(entity_class: str) -> int | None:
    path = entity_source(entity_class)
    if not path.exists():
        fail(f"[tier] cannot find entity source {path.relative_to(ROOT)}")
        return None
    match = re.search(r"bossTier\(\)\s*\{\s*return\s+(\d+)\s*;", read(path))
    if not match:
        fail(f"[tier] {path.name}: bossTier() is not a plain numeric constant; keep it readable for this audit")
        return None
    return int(match.group(1))


def parse_signature_item(entity_class: str) -> str | None:
    path = entity_source(entity_class)
    if not path.exists():
        return None
    match = re.search(r"signatureItem\(\)\s*\{\s*return\s+Optional\.of\(ModItems\.(\w+)\.get\(\)\)", read(path))
    if not match:
        return None
    item_id = parse_item_registrations().get(match.group(1))
    if item_id is None:
        fail(f"[token] {path.name}: ModItems.{match.group(1)} has no ITEMS.register id")
        return None
    return f"gensokyou:{item_id}"


def load_json(path: Path) -> dict:
    return json.loads(path.read_text(encoding="utf-8-sig"))


def find_recipe(recipe_id: str) -> dict | None:
    namespace, path = recipe_id.split(":", 1)
    if namespace != "gensokyou":
        return None
    for file in (DATA / "ritual_recipes").glob("*.json"):
        data = load_json(file)
        for recipe in data.get("recipes", []):
            name = recipe.get("name")
            if name and name == path:
                return recipe
    return None


def check_icon(entity_id: str, boss: dict, entry: dict) -> None:
    icon = entry.get("icon", "")
    expected = boss["token"]
    if expected is None:
        warnings.append(f"boss_{entity_id} has no signature item; icon falls back to '{icon}'")
    elif icon != expected:
        fail(f"[entry] boss_{entity_id}: icon must be the signature item '{expected}' (got '{icon}')")
    if ":" not in icon:
        fail(f"[entry] boss_{entity_id}: icon '{icon}' is not a namespaced item id")
        return
    namespace, path = icon.split(":", 1)
    if not (ASSETS / namespace / "models/item" / f"{path}.json").exists():
        fail(f"[entry] boss_{entity_id}: icon item model missing: {icon}")


def check_arrival_page(entity_id: str, boss: dict, pages: list[dict]) -> None:
    arrival = next((page for page in pages if page.get("anchor") == "arrival"), None)
    if arrival is None:
        fail(f"[entry] boss_{entity_id}: no page with anchor 'arrival'")
        return

    page_type = arrival.get("type")
    recipe_id = arrival.get("recipe")
    if recipe_id:
        if page_type != "gensokyou:boss_arrival":
            fail(f"[entry] boss_{entity_id}: ritual arrival should use gensokyou:boss_arrival")
        recipe = find_recipe(recipe_id)
        if recipe is None:
            fail(f"[entry] boss_{entity_id}: recipe '{recipe_id}' not found")
        else:
            effect = recipe.get("effect", "")
            if effect != boss["effect"]:
                fail(f"[entry] boss_{entity_id}: recipe '{recipe_id}' effect '{effect}' does not match '{boss['effect']}'")
    else:
        if page_type != "patchouli:text":
            fail(f"[entry] boss_{entity_id}: arrival without a recipe must be a patchouli:text page")

    token = boss["token"]
    if token is None:
        return
    shown = arrival.get("token") or arrival.get("item")
    if shown != token:
        fail(f"[entry] boss_{entity_id}: arrival page does not show token '{token}' (got {shown!r})")


def check_boss_entry(entity_id: str, boss: dict, lang: dict) -> None:
    path = ENTRIES / f"boss_{entity_id}.json"
    if not path.exists():
        fail(f"[entry] missing {path.relative_to(ROOT)}")
        return
    entry = load_json(path)

    if entry.get("category") != "gensokyou:monsters":
        fail(f"[entry] boss_{entity_id}: category must be gensokyou:monsters")
    name_key = entry.get("name", "")
    if name_key not in lang:
        fail(f"[entry] boss_{entity_id}: name key '{name_key}' missing from zh_cn.json")
    if not entry.get("secret"):
        fail(f"[entry] boss_{entity_id}: must be secret:true")

    expected_gate = GATE_BY_TIER.get(boss["tier"])
    if expected_gate is None:
        fail(f"[entry] boss_{entity_id}: unsupported boss tier {boss['tier']}")
    elif entry.get("advancement") != expected_gate:
        fail(
            f"[entry] boss_{entity_id}: advancement is {entry.get('advancement')!r}, "
            f"expected {expected_gate!r} for tier {boss['tier']}"
        )

    sortnum = entry.get("sortnum")
    band_low = 100 * boss["tier"]
    if not isinstance(sortnum, int) or not (band_low <= sortnum <= band_low + 99):
        fail(f"[entry] boss_{entity_id}: sortnum {sortnum!r} outside boss band {band_low}..{band_low + 99}")

    check_icon(entity_id, boss, entry)

    pages = entry.get("pages", [])
    if len(pages) != 3:
        fail(f"[entry] boss_{entity_id}: expected 3 pages (intro / model / arrival), got {len(pages)}")
    if pages and pages[0].get("type") != "patchouli:text":
        fail(f"[entry] boss_{entity_id}: first page must be patchouli:text")
    if len(pages) > 1:
        model = pages[1]
        if model.get("type") != "patchouli:entity" or model.get("entity") != f"gensokyou:{entity_id}":
            fail(f"[entry] boss_{entity_id}: second page must be a patchouli:entity for gensokyou:{entity_id}")
        if model.get("text"):
            fail(f"[entry] boss_{entity_id}: model page must not carry description text")
    check_arrival_page(entity_id, boss, pages)

    for page in pages:
        text_key = page.get("text")
        if text_key and text_key not in lang:
            fail(f"[entry] boss_{entity_id}: text key '{text_key}' missing from zh_cn.json")


def check_residual_codex(bosses: dict[str, dict], lang: dict) -> None:
    path = ENTRIES / "item_boss_residual_codex.json"
    if not path.exists():
        fail("[residual] item_boss_residual_codex.json missing")
        return
    entry = load_json(path)
    crafting = [page for page in entry.get("pages", []) if page.get("type") == "patchouli:crafting"]
    expected = {f"gensokyou:boss_residual_codex_{entity_id}" for entity_id in bosses}
    found = {page.get("recipe") for page in crafting}
    if found != expected:
        fail(f"[residual] crafting pages {sorted(found)} != expected {sorted(expected)}")

    for page in crafting:
        recipe_id = page.get("recipe", "")
        entity_id = recipe_id.rsplit("_", 1)[-1]
        boss = bosses.get(entity_id)
        if boss is None:
            continue
        gate = GATE_BY_TIER.get(boss["tier"])
        if page.get("advancement") != gate:
            fail(f"[residual] {recipe_id}: advancement {page.get('advancement')!r} != {gate!r}")
        token = boss["token"]
        expected_title = token.replace("gensokyou:", "item.gensokyou.") if token else None
        if expected_title and page.get("title") != expected_title:
            fail(f"[residual] {recipe_id}: title {page.get('title')!r} != {expected_title!r}")
        text_key = page.get("text", "")
        if text_key not in lang:
            fail(f"[residual] {recipe_id}: text key '{text_key}' missing from zh_cn.json")
        elif f"boss_{entity_id}" not in lang[text_key]:
            fail(f"[residual] {recipe_id}: text does not link to boss_{entity_id}")


def main() -> int:
    lang = json.loads(LANG.read_text(encoding="utf-8"))
    bosses = discover_bosses()
    for entity_id, boss in sorted(bosses.items()):
        check_boss_entry(entity_id, boss, lang)
    check_residual_codex(bosses, lang)

    for line in warnings:
        print(f"[audit] warning: {line}", file=sys.stderr)
    if errors:
        for line in errors:
            print(f"[audit] {line}", file=sys.stderr)
        print(f"[audit] {len(errors)} problem(s) in {len(bosses)} boss entries", file=sys.stderr)
        return 1

    print(f"[audit] boss guide book ok ({len(bosses)} bosses: {', '.join(sorted(bosses))})")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
