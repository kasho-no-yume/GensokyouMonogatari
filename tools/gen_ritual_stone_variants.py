#!/usr/bin/env python3
"""批量生成仪式石装饰变种（台阶/楼梯/墙 × 品阶 0-5）的全部 JSON。

产物清单（共 144 个，幂等可重跑）：
  assets/gensokyou/blockstates/ritual_stone_{slab,stairs,wall}_N.json   18
  assets/gensokyou/models/block/*.json                                  54
  assets/gensokyou/models/item/*.json                                   18
  data/gensokyou/loot_table/blocks/*.json                               18
  data/gensokyou/recipe/*.json                                          36

贴图全部复用现有 gensokyou:block/ritual_stone_N；blockstate 旋转表
（楼梯 40 变体 / 墙 multipart）逐字对齐原版 stone_brick_stairs /
cobblestone_wall 的 1.21.1 数据。

用法：python tools/gen_ritual_stone_variants.py
"""

import json
from pathlib import Path

TIER_COUNT = 6
NS = "gensokyou"
ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / "src" / "main" / "resources" / "assets" / NS
DATA = ROOT / "src" / "main" / "resources" / "data" / NS

# ---------------------------------------------------------------- blockstates

SLAB_BLOCKSTATE = {
    "variants": {
        "type=bottom": {"model": f"{NS}:block/ritual_stone_slab_%d"},
        "type=top": {"model": f"{NS}:block/ritual_stone_slab_top_%d"},
        "type=double": {"model": f"{NS}:block/ritual_stone_%d"},
    }
}

# 楼梯旋转表：逐字对齐原版 stone_brick_stairs.json（facing x half x shape -> 模型/旋转）。
# 值为 (kind, x, y)；kind: 0=straight 1=inner 2=outer；None 表示不写该键。
STAIRS_TABLE = {
    ("east", "bottom", "straight"): (0, None, None),
    ("east", "bottom", "inner_left"): (1, None, 270),
    ("east", "bottom", "inner_right"): (1, None, None),
    ("east", "bottom", "outer_left"): (2, None, 270),
    ("east", "bottom", "outer_right"): (2, None, None),
    ("north", "bottom", "straight"): (0, None, 270),
    ("north", "bottom", "inner_left"): (1, None, 180),
    ("north", "bottom", "inner_right"): (1, None, 270),
    ("north", "bottom", "outer_left"): (2, None, 180),
    ("north", "bottom", "outer_right"): (2, None, 270),
    ("south", "bottom", "straight"): (0, None, 90),
    ("south", "bottom", "inner_left"): (1, None, None),
    ("south", "bottom", "inner_right"): (1, None, 90),
    ("south", "bottom", "outer_left"): (2, None, None),
    ("south", "bottom", "outer_right"): (2, None, 90),
    ("west", "bottom", "straight"): (0, None, 180),
    ("west", "bottom", "inner_left"): (1, None, 90),
    ("west", "bottom", "inner_right"): (1, None, 180),
    ("west", "bottom", "outer_left"): (2, None, 90),
    ("west", "bottom", "outer_right"): (2, None, 180),
    ("east", "top", "straight"): (0, 180, None),
    ("east", "top", "inner_left"): (1, 180, None),
    ("east", "top", "inner_right"): (1, 180, 90),
    ("east", "top", "outer_left"): (2, 180, None),
    ("east", "top", "outer_right"): (2, 180, 90),
    ("north", "top", "straight"): (0, 180, 270),
    ("north", "top", "inner_left"): (1, 180, 270),
    ("north", "top", "inner_right"): (1, 180, None),
    ("north", "top", "outer_left"): (2, 180, 270),
    ("north", "top", "outer_right"): (2, 180, None),
    ("south", "top", "straight"): (0, 180, 90),
    ("south", "top", "inner_left"): (1, 180, 90),
    ("south", "top", "inner_right"): (1, 180, 180),
    ("south", "top", "outer_left"): (2, 180, 90),
    ("south", "top", "outer_right"): (2, 180, 180),
    ("west", "top", "straight"): (0, 180, 180),
    ("west", "top", "inner_left"): (1, 180, 180),
    ("west", "top", "inner_right"): (1, 180, 270),
    ("west", "top", "outer_left"): (2, 180, 180),
    ("west", "top", "outer_right"): (2, 180, 270),
}
STAIRS_MODEL_NAMES = ["ritual_stone_stairs_%d", "ritual_stone_stairs_inner_%d", "ritual_stone_stairs_outer_%d"]


def stairs_blockstate(tier: int) -> dict:
    variants = {}
    for (facing, half, shape), (kind, x, y) in STAIRS_TABLE.items():
        model = STAIRS_MODEL_NAMES[kind] % tier
        entry = {"model": f"{NS}:block/{model}", "uvlock": True}
        if x is not None:
            entry["x"] = x
        if y is not None:
            entry["y"] = y
        variants[f"facing={facing},half={half},shape={shape}"] = entry
    return {"variants": variants}


# 墙 multipart：逐字对齐原版 cobblestone_wall.json。
WALL_DIRECTIONS = [("north", 0), ("east", 90), ("south", 180), ("west", 270)]


def wall_blockstate(tier: int) -> dict:
    parts = [{"when": {"up": "true"},
              "apply": {"model": f"{NS}:block/ritual_stone_wall_post_{tier}"}}]
    for level, model in (("low", "ritual_stone_wall_side_%d"), ("tall", "ritual_stone_wall_side_tall_%d")):
        for direction, y in WALL_DIRECTIONS:
            apply = {"model": f"{NS}:block/{model % tier}", "uvlock": True}
            if y:
                apply["y"] = y
            parts.append({"when": {direction: level}, "apply": apply})
    return {"multipart": parts}


# --------------------------------------------------------------------- models

CUBE_TEXTURE = ("bottom", "top", "side")


def write_json(path: Path, payload: dict) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8", newline="\n") as fh:
        json.dump(payload, fh, indent=2, ensure_ascii=False)
        fh.write("\n")


def main() -> None:
    written = 0

    for tier in range(TIER_COUNT):
        tex = f"{NS}:block/ritual_stone_{tier}"
        cube = {key: tex for key in CUBE_TEXTURE}

        # blockstates
        slab_bs = {k: {**v, "model": v["model"] % tier} for k, v in SLAB_BLOCKSTATE["variants"].items()}
        write_json(ASSETS / "blockstates" / f"ritual_stone_slab_{tier}.json", {"variants": slab_bs})
        write_json(ASSETS / "blockstates" / f"ritual_stone_stairs_{tier}.json", stairs_blockstate(tier))
        write_json(ASSETS / "blockstates" / f"ritual_stone_wall_{tier}.json", wall_blockstate(tier))
        written += 3

        # block models
        block_models = {
            "ritual_stone_slab": {"parent": "minecraft:block/slab", "textures": cube},
            "ritual_stone_slab_top": {"parent": "minecraft:block/slab_top", "textures": cube},
            "ritual_stone_stairs": {"parent": "minecraft:block/stairs", "textures": cube},
            "ritual_stone_stairs_inner": {"parent": "minecraft:block/inner_stairs", "textures": cube},
            "ritual_stone_stairs_outer": {"parent": "minecraft:block/outer_stairs", "textures": cube},
            "ritual_stone_wall_post": {"parent": "minecraft:block/template_wall_post", "textures": {"wall": tex}},
            "ritual_stone_wall_side": {"parent": "minecraft:block/template_wall_side", "textures": {"wall": tex}},
            "ritual_stone_wall_side_tall": {"parent": "minecraft:block/template_wall_side_tall", "textures": {"wall": tex}},
            "ritual_stone_wall_inventory": {"parent": "minecraft:block/wall_inventory", "textures": {"wall": tex}},
        }
        for name, model in block_models.items():
            write_json(ASSETS / "models" / "block" / f"{name}_{tier}.json", model)
        written += len(block_models)

        # item models
        item_models = {
            "ritual_stone_slab": f"{NS}:block/ritual_stone_slab_{tier}",
            "ritual_stone_stairs": f"{NS}:block/ritual_stone_stairs_{tier}",
            "ritual_stone_wall": f"{NS}:block/ritual_stone_wall_inventory_{tier}",
        }
        for name, parent in item_models.items():
            write_json(ASSETS / "models" / "item" / f"{name}_{tier}.json", {"parent": parent})
        written += len(item_models)

        # loot tables（台阶按半砖状态掉 1/2；楼梯/墙自掉，格式对齐本仓库既有表）
        slab_loot = {
            "type": "minecraft:block",
            "random_sequence": f"{NS}:blocks/ritual_stone_slab_{tier}",
            "pools": [{
                "rolls": 1,
                "entries": [{
                    "type": "minecraft:item",
                    "name": f"{NS}:ritual_stone_slab_{tier}",
                    "functions": [
                        {
                            "function": "minecraft:set_count",
                            "add": False,
                            "count": 2,
                            "conditions": [{
                                "condition": "minecraft:block_state_property",
                                "block": f"{NS}:ritual_stone_slab_{tier}",
                                "properties": {"type": "double"},
                            }],
                        },
                        {"function": "minecraft:explosion_decay"},
                    ],
                }],
            }],
        }
        def simple_loot(shape: str) -> dict:
            return {
                "type": "minecraft:block",
                "random_sequence": f"{NS}:blocks/ritual_stone_{shape}_{tier}",
                "pools": [{
                    "rolls": 1,
                    "entries": [{"type": "minecraft:item", "name": f"{NS}:ritual_stone_{shape}_{tier}"}],
                    "conditions": [{"condition": "minecraft:survives_explosion"}],
                }],
            }
        write_json(DATA / "loot_table" / "blocks" / f"ritual_stone_slab_{tier}.json", slab_loot)
        write_json(DATA / "loot_table" / "blocks" / f"ritual_stone_stairs_{tier}.json", simple_loot("stairs"))
        write_json(DATA / "loot_table" / "blocks" / f"ritual_stone_wall_{tier}.json", simple_loot("wall"))
        written += 3

        # recipes：合成 + 切石
        recipes = {
            "ritual_stone_slab": {"pattern": ["XXX"], "count": 6, "category": "building"},
            "ritual_stone_stairs": {"pattern": ["X  ", "XX ", "XXX"], "count": 4, "category": "building"},
            "ritual_stone_wall": {"pattern": ["XXX", "XXX"], "count": 6, "category": "misc"},
        }
        for shape, spec in recipes.items():
            write_json(DATA / "recipe" / f"{shape}_{tier}.json", {
                "type": "minecraft:crafting_shaped",
                "category": spec["category"],
                "key": {"X": {"item": f"{NS}:ritual_stone_{tier}"}},
                "pattern": spec["pattern"],
                "result": {"count": spec["count"], "id": f"{NS}:{shape}_{tier}"},
            })
            write_json(DATA / "recipe" / f"{shape}_{tier}_from_ritual_stone_{tier}_stonecutting.json", {
                "type": "minecraft:stonecutting",
                "ingredient": {"item": f"{NS}:ritual_stone_{tier}"},
                "result": {"count": 1, "id": f"{NS}:{shape}_{tier}"},
            })
        written += 6

    print(f"generated {written} JSON files under {ROOT / 'src' / 'main' / 'resources'}")


if __name__ == "__main__":
    main()
