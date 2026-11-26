"""Shared architectural toolkit for Gensokyou Human Village landmarks & machiya houses.

Provides:
- Blockstate/model generation for the 9 extended landmark_* blocks (all tintindex=0)
- VillageBuilder with rich Japanese machiya/shoin/izakaya architectural primitives
- Structure NBT (.nbt) + GLC3 local-coordinate color pack (.local.bin) exporter
- High-clarity shaded isometric PNG preview renderer (front-left & back-right views)
"""
from __future__ import annotations

import gzip
import json
import math
import struct
from pathlib import Path
from zipfile import ZipFile

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parent.parent
RESOURCES = ROOT / "src/main/resources"
ASSETS = RESOURCES / "assets/gensokyou"
STRUCTURES = RESOURCES / "data/gensokyou/structure"
LANDMARK_COLORS = ASSETS / "landmark"
PREVIEWS = ROOT / "build/village-previews"
VANILLA_JAR = ROOT / "build/moddev/artifacts/neoforge-21.1.248-client-extra-aka-minecraft-resources.jar"

DATA_VERSION = 3955  # Minecraft 1.21.1
GLC3_MAGIC = 0x474C4333

# PaletteRole constants (must match design.md §3.2 & HumanVillagePalette.java)
ROLE_FIXED = 0
ROLE_ROOF_MAIN = 1
ROLE_ROOF_RIDGE = 2
ROLE_WALL_PLASTER = 3
ROLE_WOOD_FRAME = 4
ROLE_WOOD_DARK = 5
ROLE_ACCENT_CURTAIN = 6
ROLE_FOUNDATION_STONE = 7

# 8 runtime machiya color themes (design.md §3.2)
PALETTES: list[dict[int, int]] = [
    # 0: 经典黛瓦白壁
    {1: 0x4B5E6F, 2: 0x687D8F, 3: 0xECE6D9, 4: 0x8A5D3B, 5: 0x4E3320, 6: 0x3A5F82, 7: 0x8E8980},
    # 1: 老铺黑瓦茶木
    {1: 0x363A40, 2: 0x525861, 3: 0xE5DCCB, 4: 0x6F482D, 5: 0x3C2516, 6: 0x9E3D2E, 7: 0x7F7B74},
    # 2: 清雅青瓦桧木
    {1: 0x5A737A, 2: 0x79939A, 3: 0xF2EEE4, 4: 0xB58A5E, 5: 0x7A5635, 6: 0x4B775B, 7: 0x969188},
    # 3: 匠人赤瓦深杉
    {1: 0x6B433B, 2: 0x885C52, 3: 0xE3D8C5, 4: 0x7C4E32, 5: 0x442818, 6: 0xC48A3A, 7: 0x867F76},
    # 4: 茶屋朱帘暖木
    {1: 0x485561, 2: 0x637280, 3: 0xEFE6D5, 4: 0x9B6A43, 5: 0x5B3A22, 6: 0xC14436, 7: 0x8D877E},
    # 5: 染坊蓝染灰瓦
    {1: 0x555E66, 2: 0x737E88, 3: 0xE8E6DF, 4: 0x7A5A42, 5: 0x463224, 6: 0x2F4E7A, 7: 0x8A8A86},
    # 6: 商家紫帘白藏
    {1: 0x3F4652, 2: 0x5C6573, 3: 0xF4F1EA, 4: 0x734C32, 5: 0x3E2719, 6: 0x6D4875, 7: 0x84817B},
    # 7: 郊野茅褐素木
    {1: 0x7D694D, 2: 0x988363, 3: 0xDFD3BC, 4: 0x9C754F, 5: 0x62462C, 6: 0x7A6A46, 7: 0x92897B},
]

# Shared semantic fixed colors
C_LANTERN_WARM = 0xFFE3A3
C_LANTERN_RED = 0xE85A3F
C_LAMP_GOLD = 0xFFDF94
C_SHOJI_PAPER = 0xF5EFE0
C_TATAMI = 0xC4C28E
C_TATAMI_TRIM = 0x4E5A42
C_BOOKSHELF = 0xA8825C
C_GRAVEL_LIGHT = 0xD2CCC0
C_GRAVEL_DARK = 0xB8B1A3
C_STONE_LIGHT = 0xA8A39A
C_STONE_MID = 0x8F8A82
C_STONE_DARK = 0x6C6862
C_MOSS_STONE = 0x77826B
C_VERMILION = 0xB83B2C
C_GOLD_ACCENT = 0xDFB44A
C_CHERRY_PINK = 0xE69CB3
C_PINE_GREEN = 0x4E7246

ALL_LANDMARK_SUFFIXES = {
    "brick", "wood", "tile", "tile_slab", "tile_stairs",
    "wood_door", "wood_trapdoor", "wood_fence", "stone_wall", "lamp",
    "wood_slab", "wood_stairs", "brick_slab", "brick_stairs",
    "wood_fence_gate", "shoji", "curtain", "bookshelf", "lantern",
}


def det_hash(x: int, y: int, z: int, salt: int = 0) -> int:
    h = (x * 374761393 + y * 668265263 + z * 2147483647 + salt * 1597334677) & 0xFFFFFFFF
    h = ((h ^ (h >> 13)) * 1274126177) & 0xFFFFFFFF
    return h ^ (h >> 16)


# ---------------------------------------------------------------------------
# Blockstate & Model Asset Generation for 9 Extended Landmark Blocks
# ---------------------------------------------------------------------------
def generate_extended_block_assets(write: bool = True) -> dict[Path, bytes]:
    if not VANILLA_JAR.exists():
        raise FileNotFoundError(f"Missing Minecraft dev resources jar: {VANILLA_JAR}")

    with ZipFile(VANILLA_JAR) as vanilla:
        def read(path: str) -> dict:
            return json.loads(vanilla.read(f"assets/minecraft/{path}.json"))

        def enc(data: dict) -> bytes:
            return (json.dumps(data, indent=2) + "\n").encode("utf-8")

        def tint_all(model: dict, render_type: str | None = None) -> dict:
            for el in model.get("elements", []):
                for face in el.get("faces", {}).values():
                    face["tintindex"] = 0
            if render_type:
                model["render_type"] = render_type
            return model

        out: dict[Path, bytes] = {}

        # 1 & 2. Slabs (wood_slab, brick_slab)
        for name, tex in (("landmark_wood_slab", "landmark_wood"), ("landmark_brick_slab", "landmark_brick")):
            full_block = "landmark_wood" if "wood" in name else "landmark_brick"
            bs = {
                "variants": {
                    "type=bottom": {"model": f"gensokyou:block/{name}"},
                    "type=double": {"model": f"gensokyou:block/{full_block}"},
                    "type=top": {"model": f"gensokyou:block/{name}_top"},
                }
            }
            out[ASSETS / f"blockstates/{name}.json"] = enc(bs)
            for suffix, tpl in (("", "slab"), ("_top", "slab_top")):
                m = tint_all(read(f"models/block/{tpl}"))
                m["textures"] = {
                    "bottom": f"gensokyou:block/{tex}",
                    "top": f"gensokyou:block/{tex}",
                    "side": f"gensokyou:block/{tex}",
                    "particle": f"gensokyou:block/{tex}",
                }
                out[ASSETS / f"models/block/{name}{suffix}.json"] = enc(m)

        # 3 & 4. Stairs (wood_stairs, brick_stairs)
        for name, tex in (("landmark_wood_stairs", "landmark_wood"), ("landmark_brick_stairs", "landmark_brick")):
            bs = read("blockstates/oak_stairs")
            for var in bs["variants"].values():
                orig = var["model"].split("/")[-1]
                var["model"] = "gensokyou:block/" + orig.replace("oak_stairs", name)
            out[ASSETS / f"blockstates/{name}.json"] = enc(bs)
            for suffix, tpl in (("", "stairs"), ("_inner", "inner_stairs"), ("_outer", "outer_stairs")):
                m = tint_all(read(f"models/block/{tpl}"))
                m["textures"] = {
                    "bottom": f"gensokyou:block/{tex}",
                    "top": f"gensokyou:block/{tex}",
                    "side": f"gensokyou:block/{tex}",
                    "particle": f"gensokyou:block/{tex}",
                }
                out[ASSETS / f"models/block/{name}{suffix}.json"] = enc(m)

        # 5. Fence gate (wood_fence_gate)
        fg_bs = read("blockstates/oak_fence_gate")
        for var in fg_bs["variants"].values():
            orig = var["model"].split("/")[-1]
            var["model"] = "gensokyou:block/" + orig.replace("oak_fence_gate", "landmark_wood_fence_gate")
        out[ASSETS / "blockstates/landmark_wood_fence_gate.json"] = enc(fg_bs)
        for suffix, tpl in (
            ("", "template_fence_gate"),
            ("_open", "template_fence_gate_open"),
            ("_wall", "template_fence_gate_wall"),
            ("_wall_open", "template_fence_gate_wall_open"),
        ):
            m = tint_all(read(f"models/block/{tpl}"))
            m["textures"] = {"texture": "gensokyou:block/landmark_wood", "particle": "gensokyou:block/landmark_wood"}
            out[ASSETS / f"models/block/landmark_wood_fence_gate{suffix}.json"] = enc(m)

        # 6. Shoji pane (IronBarsBlock, glass_pane multipart)
        shoji_bs = read("blockstates/glass_pane")
        for part in shoji_bs["multipart"]:
            orig = part["apply"]["model"].split("/")[-1]
            part["apply"]["model"] = "gensokyou:block/" + orig.replace("glass_pane", "landmark_shoji")
        out[ASSETS / "blockstates/landmark_shoji.json"] = enc(shoji_bs)
        for suffix, tpl in (
            ("_post", "template_glass_pane_post"),
            ("_side", "template_glass_pane_side"),
            ("_side_alt", "template_glass_pane_side_alt"),
            ("_noside", "template_glass_pane_noside"),
            ("_noside_alt", "template_glass_pane_noside_alt"),
        ):
            m = tint_all(read(f"models/block/{tpl}"), render_type="minecraft:cutout")
            m["textures"] = {
                "edge": "gensokyou:block/landmark_wood",
                "pane": "gensokyou:block/landmark_shoji",
                "particle": "gensokyou:block/landmark_shoji",
            }
            out[ASSETS / f"models/block/landmark_shoji{suffix}.json"] = enc(m)

        # 7. Noren curtain (custom thin hanging sheet with rod, cutout, 4-way facing)
        curtain_model = {
            "render_type": "minecraft:cutout",
            "textures": {
                "curtain": "gensokyou:block/landmark_curtain",
                "particle": "gensokyou:block/landmark_curtain",
            },
            "elements": [
                {
                    "from": [0, 2, 0.5],
                    "to": [16, 16, 1.5],
                    "faces": {
                        "north": {"uv": [0, 0, 16, 14], "texture": "#curtain", "tintindex": 0},
                        "south": {"uv": [0, 0, 16, 14], "texture": "#curtain", "tintindex": 0},
                        "east": {"uv": [0, 0, 1, 14], "texture": "#curtain", "tintindex": 0},
                        "west": {"uv": [15, 0, 16, 14], "texture": "#curtain", "tintindex": 0},
                        "up": {"uv": [0, 0, 16, 1], "texture": "#curtain", "tintindex": 0},
                        "down": {"uv": [0, 13, 16, 14], "texture": "#curtain", "tintindex": 0},
                    },
                }
            ],
        }
        out[ASSETS / "models/block/landmark_curtain.json"] = enc(curtain_model)
        out[ASSETS / "blockstates/landmark_curtain.json"] = enc({
            "variants": {
                "facing=south": {"model": "gensokyou:block/landmark_curtain"},
                "facing=west": {"model": "gensokyou:block/landmark_curtain", "y": 90},
                "facing=north": {"model": "gensokyou:block/landmark_curtain", "y": 180},
                "facing=east": {"model": "gensokyou:block/landmark_curtain", "y": 270},
            }
        })

        # 8. Bookshelf (full cube with wood top/bottom and bookshelf sides)
        book_model = tint_all(read("models/block/cube"))
        book_model["textures"] = {
            "particle": "gensokyou:block/landmark_bookshelf",
            "down": "gensokyou:block/landmark_wood",
            "up": "gensokyou:block/landmark_wood",
            "north": "gensokyou:block/landmark_bookshelf",
            "south": "gensokyou:block/landmark_bookshelf",
            "east": "gensokyou:block/landmark_bookshelf",
            "west": "gensokyou:block/landmark_bookshelf",
        }
        out[ASSETS / "models/block/landmark_bookshelf.json"] = enc(book_model)
        out[ASSETS / "blockstates/landmark_bookshelf.json"] = enc({
            "variants": {"": {"model": "gensokyou:block/landmark_bookshelf"}}
        })

        # 9. Lantern (standing & hanging, cutout, light 15)
        out[ASSETS / "blockstates/landmark_lantern.json"] = enc({
            "variants": {
                "hanging=false": {"model": "gensokyou:block/landmark_lantern"},
                "hanging=true": {"model": "gensokyou:block/landmark_lantern_hanging"},
            }
        })
        for suffix, tpl in (("", "template_lantern"), ("_hanging", "template_hanging_lantern")):
            m = tint_all(read(f"models/block/{tpl}"), render_type="minecraft:cutout")
            m["textures"] = {
                "lantern": "gensokyou:block/landmark_lantern",
                "particle": "gensokyou:block/landmark_lantern",
            }
            out[ASSETS / f"models/block/landmark_lantern{suffix}.json"] = enc(m)

    if write:
        for path, payload in out.items():
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(payload)
    return out


# ---------------------------------------------------------------------------
# VillageBuilder: Rich Japanese Architecture Primitives
# ---------------------------------------------------------------------------
class VillageBuilder:
    def __init__(self, sx: int, sy: int, sz: int, theme: dict[int, int] | None = None):
        self.sx = sx
        self.sy = sy
        self.sz = sz
        self.theme = theme or PALETTES[0]
        # (x, y, z) -> (block_name, rgb_or_None, role_id, props_tuple)
        self.cells: dict[tuple[int, int, int], tuple[str, int | None, int, tuple[tuple[str, str], ...]]] = {}

    def col(self, role: int) -> int:
        return self.theme[role]

    def set(self, x: int, y: int, z: int, block: str, color: int | None = None,
            role: int = ROLE_FIXED, **props: str) -> None:
        if not (0 <= x < self.sx and 0 <= y < self.sy and 0 <= z < self.sz):
            return
        if color is None and role != ROLE_FIXED and role in self.theme:
            color = self.theme[role]
        if block == "minecraft:air":
            self.cells.pop((x, y, z), None)
            return
        if ":" not in block and block not in ALL_LANDMARK_SUFFIXES:
            raise ValueError(f"Unknown landmark block suffix: {block}")
        sorted_props = tuple(sorted((k, str(v)) for k, v in props.items()))
        self.cells[(x, y, z)] = (block, color, role, sorted_props)

    def clear(self, x0: int, x1: int, y0: int, y1: int, z0: int, z1: int) -> None:
        for x in range(min(x0, x1), max(x0, x1) + 1):
            for y in range(min(y0, y1), max(y0, y1) + 1):
                for z in range(min(z0, z1), max(z0, z1) + 1):
                    self.cells.pop((x, y, z), None)

    def fill(self, x0: int, x1: int, y0: int, y1: int, z0: int, z1: int,
             block: str, color: int | None = None, role: int = ROLE_FIXED, **props: str) -> None:
        for x in range(min(x0, x1), max(x0, x1) + 1):
            for y in range(min(y0, y1), max(y0, y1) + 1):
                for z in range(min(z0, z1), max(z0, z1) + 1):
                    self.set(x, y, z, block, color, role, **props)

    def pillar(self, x: int, z: int, y0: int, y1: int,
               block: str = "wood", color: int | None = None, role: int = ROLE_WOOD_DARK) -> None:
        self.fill(x, x, y0, y1, z, z, block, color, role)

    def door(self, x: int, y: int, z: int, facing: str, hinge: str = "left",
             color: int | None = None, role: int = ROLE_WOOD_DARK) -> None:
        self.set(x, y, z, "wood_door", color, role,
                 facing=facing, half="lower", hinge=hinge, open="false", powered="false")
        self.set(x, y + 1, z, "wood_door", color, role,
                 facing=facing, half="upper", hinge=hinge, open="false", powered="false")

    def double_door(self, x: int, y: int, z: int, facing: str, axis: str = "x",
                    color: int | None = None, role: int = ROLE_WOOD_DARK) -> None:
        # In Minecraft, facing is the direction looking through the door from outside;
        # hinges must sit on the outer doorframes so both leaves swing outward to the sides.
        if facing in ("south", "west"):
            h1, h2 = "right", "left"
        else:
            h1, h2 = "left", "right"
        self.door(x, y, z, facing, h1, color, role)
        if axis == "x":
            self.door(x + 1, y, z, facing, h2, color, role)
        else:
            self.door(x, y, z + 1, facing, h2, color, role)

    def noren(self, x0: int, x1: int, y: int, z0: int, z1: int, facing: str,
              color: int | None = None, role: int = ROLE_ACCENT_CURTAIN) -> None:
        self.fill(x0, x1, y, y, z0, z1, "curtain", color, role, facing=facing)

    def hanging_lantern(self, x: int, y: int, z: int, color: int = C_LANTERN_WARM) -> None:
        self.set(x, y, z, "lantern", color, ROLE_FIXED, hanging="true", waterlogged="false")

    def standing_lantern(self, x: int, y: int, z: int, color: int = C_LANTERN_WARM) -> None:
        self.set(x, y, z, "lantern", color, ROLE_FIXED, hanging="false", waterlogged="false")

    def stone_lantern(self, x: int, y: int, z: int, lamp_color: int = C_LAMP_GOLD) -> None:
        self.set(x, y, z, "brick_slab", C_STONE_DARK, ROLE_FIXED, type="bottom", waterlogged="false")
        self.set(x, y + 1, z, "stone_wall", C_STONE_MID, ROLE_FIXED, up="true", waterlogged="false")
        self.set(x, y + 2, z, "lamp", lamp_color, ROLE_FIXED)
        self.set(x, y + 3, z, "tile_slab", C_STONE_DARK, ROLE_FIXED, type="bottom", waterlogged="false")

    def shoji_span(self, x0: int, x1: int, y0: int, y1: int, z0: int, z1: int,
                   color: int = C_SHOJI_PAPER) -> None:
        """Place connected shoji lattice panes along X or Z span."""
        for y in range(min(y0, y1), max(y0, y1) + 1):
            if z0 == z1:
                for x in range(min(x0, x1), max(x0, x1) + 1):
                    self.set(x, y, z0, "shoji", color, ROLE_FIXED,
                             east="true", west="true", north="false", south="false", waterlogged="false")
            else:
                for z in range(min(z0, z1), max(z0, z1) + 1):
                    self.set(x0, y, z, "shoji", color, ROLE_FIXED,
                             north="true", south="true", east="false", west="false", waterlogged="false")

    def tatami_floor(self, x0: int, x1: int, y: int, z0: int, z1: int) -> None:
        for x in range(min(x0, x1), max(x0, x1) + 1):
            for z in range(min(z0, z1), max(z0, z1) + 1):
                trim = ((x - x0) % 2 == 0 and (z - z0) % 3 == 0)
                self.set(x, y, z, "tile", C_TATAMI_TRIM if trim else C_TATAMI, ROLE_FIXED)

    def gable_roof_x(self, x0: int, x1: int, z0: int, z1: int, base_y: int,
                     overhang_x: int = 1, overhang_z: int = 1,
                     main_color: int | None = None, ridge_color: int | None = None,
                     main_role: int = ROLE_ROOF_MAIN, ridge_role: int = ROLE_ROOF_RIDGE,
                     gable_fill_role: int = ROLE_WALL_PLASTER,
                     gable_trim_role: int = ROLE_WOOD_DARK,
                     gable_fill_color: int | None = None,
                     gable_trim_color: int | None = None) -> int:
        """Japanese kirizuma (gable) roof with ridge running along X axis, sloping N/S.
        Seals the wall-to-roof junction at base_y completely so zero gap can ever appear.
        """
        rx0, rx1 = x0 - overhang_x, x1 + overhang_x
        rz0, rz1 = z0 - overhang_z, z1 + overhang_z
        span = rz1 - rz0 + 1
        steps = (span + 1) // 2

        # 0. Seal wall top ring at y = base_y so roof eaves meet walls cleanly with zero gap
        for x in range(x0, x1 + 1):
            self.set(x, base_y, z0, "wood", gable_trim_color, gable_trim_role)
            self.set(x, base_y, z1, "wood", gable_trim_color, gable_trim_role)
        for z in range(z0 + 1, z1):
            self.set(x0, base_y, z, "brick", gable_fill_color, gable_fill_role)
            self.set(x1, base_y, z, "brick", gable_fill_color, gable_fill_role)

        for step in range(steps):
            y = base_y + step
            zn = rz0 + step
            zs = rz1 - step
            if zn < zs:
                # North slope (faces south up) & South slope (faces north up)
                for x in range(rx0, rx1 + 1):
                    is_edge = (x == rx0 or x == rx1)
                    c = ridge_color if is_edge else main_color
                    r = ridge_role if is_edge else main_role
                    self.set(x, y, zn, "tile_stairs", c, r, facing="south", half="bottom", shape="straight", waterlogged="false")
                    self.set(x, y, zs, "tile_stairs", c, r, facing="north", half="bottom", shape="straight", waterlogged="false")
                    # Seal gable end triangles cleanly for every step including step >= 1
                    if x0 <= x <= x1 and step >= 1:
                        if x == x0 or x == x1:
                            for zi in range(zn + 1, zs):
                                self.set(x, y, zi, "brick", gable_fill_color, gable_fill_role)
                            self.set(x, y, zn + 1, "wood", gable_trim_color, gable_trim_role)
                            self.set(x, y, zs - 1, "wood", gable_trim_color, gable_trim_role)
                # Subtle upturned eave corners on lowest row
                if step == 0:
                    for ex in (rx0, rx1):
                        self.set(ex, y, zn, "tile_slab", ridge_color, ridge_role, type="top", waterlogged="false")
                        self.set(ex, y, zs, "tile_slab", ridge_color, ridge_role, type="top", waterlogged="false")
            elif zn == zs:
                # Single-block ridge peak
                for x in range(rx0, rx1 + 1):
                    self.set(x, y, zn, "tile", ridge_color, ridge_role)
                    self.set(x, y + 1, zn, "tile_slab", ridge_color, ridge_role, type="bottom", waterlogged="false")
                # Ridge end finials (onigawara)
                self.set(rx0, y + 1, zn, "tile", ridge_color, ridge_role)
                self.set(rx1, y + 1, zn, "tile", ridge_color, ridge_role)
        # If even span, add ridge cap across middle two rows
        if span % 2 == 0:
            top_y = base_y + steps
            zm = rz0 + steps - 1
            for x in range(rx0, rx1 + 1):
                self.set(x, top_y, zm, "tile_slab", ridge_color, ridge_role, type="bottom", waterlogged="false")
                self.set(x, top_y, zm + 1, "tile_slab", ridge_color, ridge_role, type="bottom", waterlogged="false")
            return top_y
        return base_y + steps

    def gable_roof_z(self, x0: int, x1: int, z0: int, z1: int, base_y: int,
                     overhang_x: int = 1, overhang_z: int = 1,
                     main_color: int | None = None, ridge_color: int | None = None,
                     main_role: int = ROLE_ROOF_MAIN, ridge_role: int = ROLE_ROOF_RIDGE,
                     gable_fill_role: int = ROLE_WALL_PLASTER,
                     gable_trim_role: int = ROLE_WOOD_DARK,
                     gable_fill_color: int | None = None,
                     gable_trim_color: int | None = None) -> int:
        """Japanese kirizuma (gable) roof with ridge running along Z axis, sloping E/W."""
        rx0, rx1 = x0 - overhang_x, x1 + overhang_x
        rz0, rz1 = z0 - overhang_z, z1 + overhang_z
        span = rx1 - rx0 + 1
        steps = (span + 1) // 2

        # Seal wall top ring at y = base_y
        for z in range(z0, z1 + 1):
            self.set(x0, base_y, z, "wood", gable_trim_color, gable_trim_role)
            self.set(x1, base_y, z, "wood", gable_trim_color, gable_trim_role)
        for x in range(x0 + 1, x1):
            self.set(x, base_y, z0, "brick", gable_fill_color, gable_fill_role)
            self.set(x, base_y, z1, "brick", gable_fill_color, gable_fill_role)

        for step in range(steps):
            y = base_y + step
            xw = rx0 + step
            xe = rx1 - step
            if xw < xe:
                for z in range(rz0, rz1 + 1):
                    is_edge = (z == rz0 or z == rz1)
                    c = ridge_color if is_edge else main_color
                    r = ridge_role if is_edge else main_role
                    self.set(xw, y, z, "tile_stairs", c, r, facing="east", half="bottom", shape="straight", waterlogged="false")
                    self.set(xe, y, z, "tile_stairs", c, r, facing="west", half="bottom", shape="straight", waterlogged="false")
                    if z0 <= z <= z1 and step >= 1:
                        if z == z0 or z == z1:
                            for xi in range(xw + 1, xe):
                                self.set(xi, y, z, "brick", gable_fill_color, gable_fill_role)
                            self.set(xw + 1, y, z, "wood", gable_trim_color, gable_trim_role)
                            self.set(xe - 1, y, z, "wood", gable_trim_color, gable_trim_role)
                if step == 0:
                    for ez in (rz0, rz1):
                        self.set(xw, y, ez, "tile_slab", ridge_color, ridge_role, type="top", waterlogged="false")
                        self.set(xe, y, ez, "tile_slab", ridge_color, ridge_role, type="top", waterlogged="false")
            elif xw == xe:
                for z in range(rz0, rz1 + 1):
                    self.set(xw, y, z, "tile", ridge_color, ridge_role)
                    self.set(xw, y + 1, z, "tile_slab", ridge_color, ridge_role, type="bottom", waterlogged="false")
                self.set(xw, y + 1, rz0, "tile", ridge_color, ridge_role)
                self.set(xw, y + 1, rz1, "tile", ridge_color, ridge_role)
        if span % 2 == 0:
            top_y = base_y + steps
            xm = rx0 + steps - 1
            for z in range(rz0, rz1 + 1):
                self.set(xm, top_y, z, "tile_slab", ridge_color, ridge_role, type="bottom", waterlogged="false")
                self.set(xm + 1, top_y, z, "tile_slab", ridge_color, ridge_role, type="bottom", waterlogged="false")
            return top_y
        return base_y + steps

    def hip_gable_roof_x(self, x0: int, x1: int, z0: int, z1: int, base_y: int,
                         overhang: int = 2,
                         main_color: int | None = None, ridge_color: int | None = None,
                         main_role: int = ROLE_ROOF_MAIN, ridge_role: int = ROLE_ROOF_RIDGE,
                         gable_fill_color: int | None = None,
                         gable_trim_color: int | None = None) -> int:
        """Grand Japanese irimoya (hip-and-gable) roof: lower hipped skirt + upper gable peak.
        Completely seals wall-to-roof junction at base_y.
        """
        rx0, rx1 = x0 - overhang, x1 + overhang
        rz0, rz1 = z0 - overhang, z1 + overhang
        hip_layers = 2

        # Seal wall perimeter at base_y so there is never an open gap between wall and eaves
        for x in range(x0, x1 + 1):
            self.set(x, base_y, z0, "wood", gable_trim_color, ROLE_WOOD_DARK if gable_trim_color is None else ROLE_FIXED)
            self.set(x, base_y, z1, "wood", gable_trim_color, ROLE_WOOD_DARK if gable_trim_color is None else ROLE_FIXED)
        for z in range(z0, z1 + 1):
            self.set(x0, base_y, z, "wood", gable_trim_color, ROLE_WOOD_DARK if gable_trim_color is None else ROLE_FIXED)
            self.set(x1, base_y, z, "wood", gable_trim_color, ROLE_WOOD_DARK if gable_trim_color is None else ROLE_FIXED)

        # Lower hipped skirt (4-sided stairs)
        for step in range(hip_layers):
            y = base_y + step
            ax0, ax1 = rx0 + step, rx1 - step
            az0, az1 = rz0 + step, rz1 - step
            for x in range(ax0, ax1 + 1):
                self.set(x, y, az0, "tile_stairs", main_color, main_role, facing="south", half="bottom", shape="straight", waterlogged="false")
                self.set(x, y, az1, "tile_stairs", main_color, main_role, facing="north", half="bottom", shape="straight", waterlogged="false")
            for z in range(az0 + 1, az1):
                self.set(ax0, y, z, "tile_stairs", main_color, main_role, facing="east", half="bottom", shape="straight", waterlogged="false")
                self.set(ax1, y, z, "tile_stairs", main_color, main_role, facing="west", half="bottom", shape="straight", waterlogged="false")
            # Upturned hip corners
            for cx in (ax0, ax1):
                for cz in (az0, az1):
                    self.set(cx, y, cz, "tile_slab", ridge_color, ridge_role,
                             type="top" if step == 0 else "bottom", waterlogged="false")
            # Seal interior ceiling under hip
            if step == hip_layers - 1:
                self.fill(ax0 + 1, ax1 - 1, y, y, az0 + 1, az1 - 1, "wood", gable_trim_color,
                          ROLE_WOOD_DARK if gable_trim_color is None else ROLE_FIXED)

        # Upper gable section (flush with hip shoulder so no flat wood ledge shows)
        return self.gable_roof_x(
            rx0 + hip_layers, rx1 - hip_layers,
            rz0 + hip_layers, rz1 - hip_layers,
            base_y + hip_layers,
            overhang_x=0, overhang_z=0,
            main_color=main_color, ridge_color=ridge_color,
            main_role=main_role, ridge_role=ridge_role,
            gable_fill_role=ROLE_FIXED if gable_fill_color is not None else ROLE_WALL_PLASTER,
            gable_trim_role=ROLE_FIXED if gable_trim_color is not None else ROLE_WOOD_DARK,
            gable_fill_color=gable_fill_color,
            gable_trim_color=gable_trim_color,
        )

    def simple_tree(self, cx: int, base_y: int, cz: int, height: int = 5,
                    leaf_block: str = "minecraft:cherry_leaves") -> None:
        self.fill(cx, cx, base_y, base_y + height - 2, cz, cz, "wood", 0x5C3E28, ROLE_FIXED)
        top = base_y + height - 1
        for dy in (-2, -1, 0, 1):
            rad = 2 if dy in (-1, 0) else 1
            for dx in range(-rad, rad + 1):
                for dz in range(-rad, rad + 1):
                    if abs(dx) == rad and abs(dz) == rad and (dy != -1 or (dx + dz) % 2 == 0):
                        continue
                    if dx == 0 and dz == 0 and dy < 0:
                        continue
                    self.set(cx + dx, top + dy, cz + dz, leaf_block, None, ROLE_FIXED,
                             persistent="true", distance="1")


# ---------------------------------------------------------------------------
# NBT & GLC3 Binary Exporters
# ---------------------------------------------------------------------------
def _nbt_text(value: str) -> bytes:
    raw = value.encode("utf-8")
    return struct.pack(">H", len(raw)) + raw


def _nbt_tag(kind: int, name: str, payload: bytes) -> bytes:
    return bytes([kind]) + _nbt_text(name) + payload


def _nbt_int(v: int) -> bytes:
    return struct.pack(">i", v)


def _nbt_list(kind: int, items: list[bytes]) -> bytes:
    return bytes([kind]) + _nbt_int(len(items)) + b"".join(items)


def _nbt_compound(*tags: bytes) -> bytes:
    return b"".join(tags) + b"\0"


def encode_structure_nbt(builder: VillageBuilder) -> bytes:
    palette: dict[tuple[str, tuple[tuple[str, str], ...]], int] = {}
    blocks: list[bytes] = []

    for pos, (block, _, _, props) in sorted(builder.cells.items(), key=lambda e: (e[0][1], e[0][0], e[0][2])):
        key = (block, props)
        if key not in palette:
            palette[key] = len(palette)
        blocks.append(_nbt_compound(
            _nbt_tag(9, "pos", _nbt_list(3, [_nbt_int(n) for n in pos])),
            _nbt_tag(3, "state", _nbt_int(palette[key])),
        ))

    states: list[bytes] = []
    for block, props in palette:
        full_name = block if ":" in block else f"gensokyou:landmark_{block}"
        if props:
            prop_tags = [_nbt_tag(8, k, _nbt_text(v)) for k, v in props]
            states.append(_nbt_compound(
                _nbt_tag(8, "Name", _nbt_text(full_name)),
                _nbt_tag(10, "Properties", _nbt_compound(*prop_tags)),
            ))
        else:
            states.append(_nbt_compound(_nbt_tag(8, "Name", _nbt_text(full_name))))

    root = _nbt_tag(10, "", _nbt_compound(
        _nbt_tag(3, "DataVersion", _nbt_int(DATA_VERSION)),
        _nbt_tag(9, "size", _nbt_list(3, [_nbt_int(builder.sx), _nbt_int(builder.sy), _nbt_int(builder.sz)])),
        _nbt_tag(9, "palette", _nbt_list(10, states)),
        _nbt_tag(9, "blocks", _nbt_list(10, blocks)),
        _nbt_tag(9, "entities", _nbt_list(10, [])),
    ))
    return gzip.compress(root)


def encode_glc3(builder: VillageBuilder) -> bytes:
    """Encode local-coordinate GLC3 color pack per design.md §3.1."""
    # (rgb, role_id) -> colorIndex (1..255); index 0 is reserved NO_TINT
    palette_entries: list[tuple[int, int]] = [(0, 0)]
    palette_map: dict[tuple[int, int], int] = {}

    expected_blocks: list[str] = []
    block_map: dict[str, int] = {}

    voxels: list[tuple[int, int, int, int, int]] = []
    for (lx, ly, lz), (block, color, role, _) in sorted(builder.cells.items()):
        if color is None or ":" in block:
            continue
        full_name = f"gensokyou:landmark_{block}"
        ckey = (color & 0xFFFFFF, role & 0xFF)
        if ckey not in palette_map:
            if len(palette_entries) >= 256:
                raise ValueError(f"GLC3 palette overflow (>255 colors): {ckey}")
            palette_map[ckey] = len(palette_entries)
            palette_entries.append(ckey)
        if full_name not in block_map:
            if len(expected_blocks) >= 255:
                raise ValueError("GLC3 expectedBlocks overflow (>255)")
            expected_blocks.append(full_name)
            block_map[full_name] = len(expected_blocks)
        voxels.append((lx, ly, lz, palette_map[ckey], block_map[full_name]))

    buf = bytearray()
    buf += struct.pack(">IHHH", GLC3_MAGIC, builder.sx, builder.sy, builder.sz)
    buf += struct.pack(">H", len(palette_entries))
    for rgb, role in palette_entries:
        buf += struct.pack(">IB", rgb & 0xFFFFFF, role & 0xFF)
    buf += struct.pack(">B", len(expected_blocks))
    for bname in expected_blocks:
        raw = bname.encode("utf-8")
        buf += struct.pack(">H", len(raw)) + raw
    buf += struct.pack(">I", len(voxels))
    for lx, ly, lz, ci, bi in voxels:
        buf += struct.pack(">HHHBB", lx, ly, lz, ci, bi)
    return gzip.compress(bytes(buf))


def export_building(struct_id: str, builder: VillageBuilder, write: bool = True,
                    render_preview: bool = True, preview_theme: dict[int, int] | None = None) -> None:
    nbt_bytes = encode_structure_nbt(builder)
    glc3_bytes = encode_glc3(builder)
    if write:
        STRUCTURES.mkdir(parents=True, exist_ok=True)
        LANDMARK_COLORS.mkdir(parents=True, exist_ok=True)
        (STRUCTURES / f"{struct_id}.nbt").write_bytes(nbt_bytes)
        (LANDMARK_COLORS / f"{struct_id}.local.bin").write_bytes(glc3_bytes)
    if render_preview:
        PREVIEWS.mkdir(parents=True, exist_ok=True)
        render_isometric_sheet(builder, PREVIEWS / f"{struct_id}.png", title=struct_id, theme_override=preview_theme)


# ---------------------------------------------------------------------------
# High-Clarity Isometric 3D Preview Renderer (Dual Angle Sheet)
# ---------------------------------------------------------------------------
def _block_rgb(block: str, color: int | None, role: int, theme_override: dict[int, int] | None) -> tuple[int, int, int]:
    if theme_override is not None and role in theme_override and role != ROLE_FIXED:
        c = theme_override[role]
        return ((c >> 16) & 0xFF, (c >> 8) & 0xFF, c & 0xFF)
    if color is not None:
        return ((color >> 16) & 0xFF, (color >> 8) & 0xFF, color & 0xFF)
    if "water" in block:
        return (62, 130, 185)
    if "cherry_leaves" in block:
        return (230, 156, 179)
    if "leaves" in block or "herb" in block:
        return (78, 138, 72)
    if "grass" in block:
        return (118, 158, 92)
    if "dirt" in block or "farmland" in block or "spirit_soil" in block:
        return (126, 94, 68)
    return (160, 160, 160)


def _shade(rgb: tuple[int, int, int], factor: float) -> tuple[int, int, int]:
    return (
        max(0, min(255, int(rgb[0] * factor))),
        max(0, min(255, int(rgb[1] * factor))),
        max(0, min(255, int(rgb[2] * factor))),
    )


def _render_single_iso(builder: VillageBuilder, rotate_180: bool = False,
                       scale: int = 8, theme_override: dict[int, int] | None = None) -> Image.Image:
    sx, sy, sz = builder.sx, builder.sy, builder.sz
    # Isometric projection: screenX = (u - v) * tileW, screenY = (u + v) * tileH - y * vStep
    tw = scale
    th = max(2, scale // 2)
    vs = scale

    margin = 24
    img_w = (sx + sz) * tw + margin * 2
    img_h = (sx + sz) * th + sy * vs + margin * 2
    ox = sz * tw + margin
    oy = sy * vs + margin

    img = Image.new("RGBA", (img_w, img_h), (238, 241, 246, 255))
    draw = ImageDraw.Draw(img)

    # Painter's algorithm sort: back-to-front by (u + v, y)
    items = []
    for (x, y, z), (block, color, role, props) in builder.cells.items():
        u = (sx - 1 - x) if rotate_180 else x
        v = (sz - 1 - z) if rotate_180 else z
        items.append((u + v, y, u, v, block, color, role, props))
    items.sort(key=lambda t: (t[0], t[1]))

    for _, y, u, v, block, color, role, props in items:
        rgb = _block_rgb(block, color, role, theme_override)
        px = ox + (u - v) * tw
        py = oy + (u + v) * th - y * vs

        is_slab = "slab" in block
        is_lantern = block == "lantern"
        is_fence = "fence" in block or "wall" in block
        is_curtain = block == "curtain"
        is_lamp = block in ("lamp", "lantern")

        # Adjust vertical height for slabs & hanging items
        h_top = vs
        h_bot = 0
        if is_slab:
            pdict = dict(props)
            if pdict.get("type") == "top":
                h_bot = vs // 2
            elif pdict.get("type") != "double":
                h_top = vs // 2
        elif is_lantern:
            h_bot = vs // 4
            h_top = vs - vs // 6
        elif is_curtain:
            h_bot = vs // 4

        w = tw if not (is_fence or is_lantern) else max(2, tw // 2)
        h = th if not (is_fence or is_lantern) else max(1, th // 2)

        top_y = py - h_top
        bot_y = py - h_bot

        top_c = _shade(rgb, 1.12 if is_lamp else 1.03)
        left_c = _shade(rgb, 1.04 if is_lamp else 0.86)
        right_c = _shade(rgb, 0.98 if is_lamp else 0.70)
        outline_c = _shade(rgb, 0.55)

        top_poly = [(px, top_y - h), (px + w, top_y), (px, top_y + h), (px - w, top_y)]
        left_poly = [(px - w, top_y), (px, top_y + h), (px, bot_y + h), (px - w, bot_y)]
        right_poly = [(px + w, top_y), (px, top_y + h), (px, bot_y + h), (px + w, bot_y)]

        draw.polygon(left_poly, fill=left_c, outline=outline_c if scale >= 7 else None)
        draw.polygon(right_poly, fill=right_c, outline=outline_c if scale >= 7 else None)
        draw.polygon(top_poly, fill=top_c, outline=outline_c if scale >= 7 else None)

    return img


def render_isometric_sheet(builder: VillageBuilder, out_path: Path, title: str = "",
                           theme_override: dict[int, int] | None = None) -> None:
    max_dim = max(builder.sx, builder.sz, builder.sy)
    scale = 10 if max_dim <= 16 else (7 if max_dim <= 28 else 5)
    front = _render_single_iso(builder, rotate_180=False, scale=scale, theme_override=theme_override)
    back = _render_single_iso(builder, rotate_180=True, scale=scale, theme_override=theme_override)

    header = 34
    w = front.width + back.width + 20
    h = max(front.height, back.height) + header + 10
    sheet = Image.new("RGBA", (w, h), (232, 236, 242, 255))
    sheet.paste(front, (6, header))
    sheet.paste(back, (front.width + 14, header))

    draw = ImageDraw.Draw(sheet)
    label = f"{title}   ({builder.sx}x{builder.sy}x{builder.sz})   [Left: Front-SouthEast | Right: Back-NorthWest]"
    draw.text((14, 10), label, fill=(40, 44, 52, 255))
    sheet.save(out_path)


if __name__ == "__main__":
    assets = generate_extended_block_assets(write=True)
    print(f"[ok] generated {len(assets)} extended landmark blockstate/model files")
