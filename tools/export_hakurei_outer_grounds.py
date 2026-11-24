"""Export the three Hakurei outer-grounds landmarks (structure NBT + GLC2 colors)."""
import argparse
import gzip
import json
from dataclasses import dataclass
from pathlib import Path
from zipfile import ZipFile

import export_hakurei_shrine_preview as shrine


ROOT = shrine.ROOT
RESOURCES = shrine.RESOURCES
ASSETS = shrine.ASSETS
VANILLA_JAR = ROOT / "build/moddev/artifacts/neoforge-21.1.248-client-extra-aka-minecraft-resources.jar"


@dataclass(frozen=True)
class Grounds:
    structure_id: str
    color_id: str
    origin: tuple[int, int, int]
    size: tuple[int, int, int]


FRONT = Grounds(
    "hakurei_front_gateway",
    "hakurei_front_gateway_gensokyo",
    (2976, 78, 96),
    (46, 18, 60),
)
NORTH = Grounds(
    "hakurei_north_guesthouse",
    "hakurei_north_guesthouse_gensokyo",
    (3040, 82, 62),
    (72, 26, 31),
)
EAST = Grounds(
    "hakurei_east_grounds",
    "hakurei_east_grounds_gensokyo",
    (3118, 88, 100),
    (48, 32, 92),
)
GROUNDS = (FRONT, NORTH, EAST)


# ---------------------------------------------------------------------------
# Shared terrain and detail helpers
# ---------------------------------------------------------------------------
def build_terrain(b, surface_y, salt=0):
    """Give each satellite its own solid, deterministic terrain pad."""
    for x in range(b.sx):
        for z in range(b.sz):
            top = max(0, min(b.sy - 1, surface_y(x, z)))
            for y in range(top + 1):
                if y == top:
                    block = "gensokyou:gensokyo_grass_block"
                elif y >= top - 2:
                    block = "gensokyou:gensokyo_dirt"
                else:
                    block = "gensokyou:gensokyo_stone"
                b.set(x, y, z, block)


def pave(b, x0, x1, y, z0, z1, border=False):
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            edge = border and (x in (x0, x1) or z in (z0, z1))
            color = shrine.STONE_DARK if edge else (
                shrine.STONE_LIGHT if (x + z) % 2 == 0 else shrine.STONE_BASE
            )
            b.set(x, y, z, "tile", color)


def gravel(b, x0, x1, y, z0, z1):
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            color = shrine.GRAVEL_LIGHT if shrine.det_hash(x, y, z, 211) % 3 == 0 else shrine.GRAVEL_BASE
            b.set(x, y, z, "brick", color)


def add_ground_cover(b, surface_y, salt=0):
    for x in range(1, b.sx - 1):
        for z in range(1, b.sz - 1):
            y = surface_y(x, z)
            cell = b.cells.get((x, y, z))
            if not cell or cell[0] != "gensokyou:gensokyo_grass_block" or (x, y + 1, z) in b.cells:
                continue
            value = shrine.det_hash(x, y, z, 223 + salt)
            if value % 13 == 0:
                b.set(x, y + 1, z, "gensokyou:gensokyo_short_grass")
            elif value % 41 == 0:
                b.set(x, y + 1, z, "gensokyou:spirit_herb")


def normalize_sacred_leaves(b):
    """Sacred leaves are a plain Block in this mod, not a vanilla LeavesBlock."""
    for pos, (block, color, _) in list(b.cells.items()):
        if block == "gensokyou:sacred_leaves":
            b.cells[pos] = (block, color, ())


def build_low_irimoya(b, x0, x1, z0, z1, eave_y, ridge_axis, max_height):
    """Use the shrine roof primitive, then keep every satellite below its template ceiling."""
    shrine.build_irimoya_roof(
        b,
        x0=x0,
        x1=x1,
        z0=z0,
        z1=z1,
        eave_y=eave_y,
        ridge_axis=ridge_axis,
        chigi=False,
        katsuogi_count=0,
        max_height=max_height,
    )


def add_ema_rack(b, x0, x1, y, z):
    for x in (x0, x1):
        for dy in range(4):
            b.set(x, y + dy, z, "wood", shrine.WOOD_BEAM)
    for x in range(x0 + 1, x1):
        b.set(x, y + 1, z, "wood", shrine.WOOD_AGED)
        b.set(x, y + 2, z, "wood", shrine.WOOD_AGED)
        b.set(x, y + 1, z + 1, "minecraft:birch_wall_sign", facing="south", waterlogged="false")
        b.set(x, y + 2, z + 1, "minecraft:oak_wall_sign", facing="south", waterlogged="false")
    b.lamp(x0 + 2, y + 3, z, shrine.LAMP_WARM)
    b.lamp(x1 - 2, y + 3, z, shrine.LAMP_WARM)


# ---------------------------------------------------------------------------
# 1. Front gateway: roadside teahouse, rest pavilion, talisman stall and supply rack
# ---------------------------------------------------------------------------
FRONT_ROAD_Z = 32


def front_surface_y(x, z):
    plaza = 25 <= x <= 41 and 22 <= z <= 42
    road = abs(z - FRONT_ROAD_Z) <= 4
    if plaza or road:
        return 6  # World Y=84
    return 5 if z < FRONT_ROAD_Z else 4  # World Y=83 / 82


def build_front_paths(b):
    for x in range(b.sx):
        for z in range(FRONT_ROAD_Z - 4, FRONT_ROAD_Z + 5):
            edge = abs(z - FRONT_ROAD_Z) == 4
            color = shrine.STONE_DARK if edge else (
                shrine.STONE_LIGHT if (x + z) % 2 == 0 else shrine.STONE_BASE
            )
            b.set(x, 6, z, "tile", color)

    # A generous stone arrival apron, still crossed only by the central Sando.
    gravel(b, 25, 41, 6, 22, 42)
    pave(b, 25, 41, 6, FRONT_ROAD_Z - 2, FRONT_ROAD_Z + 2, border=True)

    # North teahouse branch and south rest-pavilion branch.
    pave(b, 13, 16, 5, 18, 27, border=True)
    pave(b, 14, 17, 4, 37, 43, border=True)
    for x in range(13, 17):
        b.stairs(x, 6, 28, "north", shrine.STONE_LIGHT)
    for x in range(14, 18):
        b.stairs(x, 5, 37, "north", shrine.STONE_LIGHT)
        b.set(x, 4, 37, "brick", shrine.STONE_DARK)

    # Short spur to the talisman stall and the open supply rack.
    pave(b, 34, 36, 5, 21, 27, border=True)
    pave(b, 35, 37, 4, 37, 44, border=True)


def build_front_gateway(b):
    # Two vermilion pillars stand outside the seven-block clear processional lane.
    for z in (25, 39):
        b.set(28, 6, z, "brick", shrine.STONE_WET)
        b.set(28, 7, z, "brick", shrine.STONE_DARK)
        for y in range(8, 15):
            b.set(28, y, z, "wood", shrine.vermilion_color(28, y, z))
        b.set(27, 7, z, "brick", shrine.STONE_BASE)
        b.set(29, 7, z, "brick", shrine.STONE_BASE)

    for z in range(24, 41):
        b.set(28, 13, z, "wood", shrine.VERMILION_BASE)
        b.set(28, 15, z, "wood", shrine.VERMILION_SUN)
        b.set(28, 16, z, "tile", shrine.TILE_RIDGE)
    b.set(28, 13, 24, "wood", shrine.LACQUER_BLACK)
    b.set(28, 13, 40, "wood", shrine.LACQUER_BLACK)
    b.slab(28, 17, 24, shrine.TILE_RIDGE, "bottom")
    b.slab(28, 17, 40, shrine.TILE_RIDGE, "bottom")

    # Hanging lamps and a lacquered name plaque stay above the walking lane.
    for z in (27, 37):
        b.lamp(27, 12, z, shrine.LAMP_VERMILION)
        b.slab(27, 13, z, shrine.TILE_RIDGE, "bottom")
    for y in (14, 15):
        b.set(27, y, FRONT_ROAD_Z, "wood", shrine.LACQUER_BLACK)
    b.set(27, 15, FRONT_ROAD_Z, "tile", shrine.GOLD_TRIM)


def build_teahouse(b):
    # Raised warm-wood deck at local X=7..22, Z=6..19.
    for x in range(7, 23):
        for z in range(6, 20):
            b.set(x, 6, z, "wood", shrine.wood_color(x, 6, z))
    for x, z in ((8, 7), (14, 7), (21, 7), (8, 18), (14, 18), (21, 18)):
        b.set(x, 5, z, "brick", shrine.STONE_DARK)
        for y in range(7, 11):
            b.set(x, y, z, "wood", shrine.WOOD_BEAM)

    # Plaster back and side walls with shoji-like lattice openings.
    for y in range(7, 11):
        for x in range(8, 22):
            pillar = x in (8, 14, 21)
            if pillar or y == 10:
                b.set(x, y, 7, "wood", shrine.WOOD_BEAM)
            elif y in (8, 9):
                b.fence(x, y, 7, shrine.SHOJI_PAPER)
            else:
                b.set(x, y, 7, "brick", shrine.plaster_color(x, y, 7))
        for z in range(8, 18):
            for x in (8, 21):
                pillar = z in (8, 12, 17)
                if pillar or y == 10:
                    b.set(x, y, z, "wood", shrine.WOOD_BEAM)
                elif y in (8, 9):
                    b.fence(x, y, z, shrine.SHOJI_PAPER)
                else:
                    b.set(x, y, z, "brick", shrine.plaster_color(x, y, z))

    # Open serving front: split counter leaves the middle walkable.
    for x in list(range(9, 14)) + list(range(17, 21)):
        b.set(x, 7, 18, "wood", shrine.WOOD_DARK)
        b.slab(x, 8, 18, shrine.WOOD_PALE, "bottom")
    for x in (12, 18):
        b.lamp(x, 10, 18, shrine.LAMP_WARM)
        b.slab(x, 11, 18, shrine.TILE_RIDGE, "bottom")

    # Tea tables, cushions, shelf and storage inside.
    for x in range(11, 14):
        for z in (11, 12):
            b.slab(x, 7, z, shrine.WOOD_DARK, "bottom")
    for x, z in ((10, 10), (10, 13), (14, 10), (14, 13)):
        b.set(x, 7, z, "minecraft:red_carpet")
    b.set(19, 7, 9, "minecraft:bookshelf")
    b.set(20, 7, 15, "minecraft:barrel", facing="up", open="false")
    b.set(9, 7, 16, "minecraft:flower_pot")
    b.lamp(15, 10, 11, shrine.LAMP_AMBER)

    build_low_irimoya(b, 6, 23, 4, 21, 11, "x", 4)


def build_rest_pavilion(b):
    # Low open pavilion at local X=8..23, Z=43..56, floor world Y=83.
    for x in range(8, 24):
        for z in range(43, 57):
            b.set(x, 5, z, "wood", shrine.wood_color(x, 5, z))
    for x, z in ((9, 44), (22, 44), (9, 55), (22, 55), (15, 44), (15, 55)):
        for y in range(6, 10):
            b.set(x, y, z, "wood", shrine.WOOD_BEAM)
    for x in range(9, 23):
        b.fence(x, 6, 44, shrine.WOOD_AGED)
        if not 14 <= x <= 17:
            b.fence(x, 6, 55, shrine.WOOD_AGED)
    for z in range(45, 55):
        b.fence(9, 6, z, shrine.WOOD_AGED)
        b.fence(22, 6, z, shrine.WOOD_AGED)

    # Benches and a low shared table, with no obstruction in the central approach.
    for x in range(11, 21):
        b.slab(x, 6, 47, shrine.WOOD_PALE, "bottom")
        b.slab(x, 6, 53, shrine.WOOD_PALE, "bottom")
    for x in range(14, 18):
        b.slab(x, 6, 50, shrine.WOOD_DARK, "bottom")
    b.set(20, 6, 50, "minecraft:flower_pot")
    b.lamp(10, 8, 54, shrine.LAMP_WARM)
    b.lamp(21, 8, 54, shrine.LAMP_WARM)
    b.slab(10, 9, 54, shrine.TILE_RIDGE, "bottom")
    b.slab(21, 9, 54, shrine.TILE_RIDGE, "bottom")
    build_low_irimoya(b, 7, 24, 42, 57, 9, "x", 4)


def build_talisman_stall(b):
    for x in range(30, 41):
        for z in range(13, 23):
            b.set(x, 6, z, "wood", shrine.wood_color(x, 6, z))
    for x, z in ((30, 13), (40, 13), (30, 22), (40, 22)):
        for y in range(7, 11):
            b.set(x, y, z, "wood", shrine.WOOD_BEAM)
    for x in range(31, 40):
        b.set(x, 7, 21, "wood", shrine.WOOD_DARK)
        b.slab(x, 8, 21, shrine.VERMILION_BASE, "bottom")
        b.set(x, 8, 22, "minecraft:birch_wall_sign", facing="south", waterlogged="false")
    b.set(33, 7, 15, "minecraft:barrel", facing="up", open="false")
    b.set(37, 7, 15, "minecraft:decorated_pot")
    b.set(38, 7, 18, "minecraft:flower_pot")
    b.lamp(32, 10, 21, shrine.LAMP_VERMILION)
    b.lamp(38, 10, 21, shrine.LAMP_VERMILION)
    build_low_irimoya(b, 29, 41, 12, 23, 10, "x", 3)


def build_supply_rack(b):
    # Unroofed roadside shelf for rice, charms and pilgrim supplies.
    for x in (33, 41):
        for y in range(5, 9):
            b.set(x, y, 49, "wood", shrine.WOOD_BEAM)
            b.set(x, y, 45, "wood", shrine.WOOD_BEAM)
    for z in range(45, 50):
        b.set(33, 6, z, "wood", shrine.WOOD_AGED)
        b.set(41, 6, z, "wood", shrine.WOOD_AGED)
        b.set(33, 8, z, "wood", shrine.WOOD_AGED)
        b.set(41, 8, z, "wood", shrine.WOOD_AGED)
    for x, z, block in (
        (35, 46, "minecraft:barrel"),
        (38, 46, "minecraft:barrel"),
        (35, 49, "minecraft:decorated_pot"),
        (38, 49, "minecraft:hay_block"),
    ):
        props = {"facing": "up", "open": "false"} if block.endswith("barrel") else (
            {"axis": "x"} if block.endswith("hay_block") else {}
        )
        b.set(x, 5, z, block, **props)
    b.lamp(37, 8, 49, shrine.LAMP_WARM)
    b.slab(37, 9, 49, shrine.TILE_RIDGE, "bottom")


def build_front():
    b = shrine.Builder(FRONT.size)
    build_terrain(b, front_surface_y, salt=1)
    build_front_paths(b)
    build_front_gateway(b)
    build_teahouse(b)
    build_rest_pavilion(b)
    build_talisman_stall(b)
    build_supply_rack(b)
    add_ema_rack(b, 4, 11, 6, 23)

    # A few sacred trees soften the roadside cluster without closing the Sando.
    shrine.build_sacred_tree(b, 4, 6, 16, height=8, crown_r=3.0)
    shrine.build_sacred_tree(b, 25, 6, 17, height=9, crown_r=3.2)
    shrine.build_sacred_tree(b, 4, 5, 49, height=8, crown_r=3.1)
    shrine.build_sacred_tree(b, 26, 5, 52, height=9, crown_r=3.0)
    add_ground_cover(b, front_surface_y, salt=1)
    normalize_sacred_leaves(b)
    return b.cells


# ---------------------------------------------------------------------------
# 2. North guesthouse: guest hall, ritual store, purification basin and south gallery
# ---------------------------------------------------------------------------
def north_surface_y(x, z):
    edge = min(x, 71 - x, z, 30 - z)
    return 14 if edge >= 2 else 13  # World Y=96 platform, one-block natural apron


def build_north_paths(b):
    # Main paved entry from the south edge to the guest hall.
    pave(b, 15, 19, 14, 21, 30, border=True)
    pave(b, 25, 37, 14, 24, 27, border=True)
    pave(b, 43, 54, 14, 20, 26, border=True)
    gravel(b, 27, 62, 14, 21, 28)

    # Engawa floor is one block above the stone platform, matching the hall floor.
    for x in range(7, 13):
        for z in range(22, 29):
            b.set(x, 15, z, "wood", shrine.wood_color(x, 15, z))
    for z in range(22, 29):
        b.fence(7, 16, z, shrine.WOOD_BEAM)
        b.fence(12, 16, z, shrine.WOOD_BEAM)
    for z in (22, 25, 28):
        for x in (7, 12):
            for y in range(16, 20):
                b.set(x, y, z, "wood", shrine.WOOD_BEAM)
    for x in range(7, 13):
        b.stairs(x, 19, 22, "south", shrine.roof_color(x, 19, 22, eave=True))
        b.set(x, 20, 23, "tile", shrine.TILE_RIDGE)
        b.set(x, 20, 24, "tile", shrine.TILE_RIDGE)
        b.set(x, 20, 25, "tile", shrine.TILE_RIDGE)
        b.set(x, 20, 26, "tile", shrine.TILE_RIDGE)
        b.set(x, 20, 27, "tile", shrine.TILE_RIDGE)
        b.stairs(x, 19, 28, "north", shrine.roof_color(x, 19, 28, eave=True))
    for x, z in ((8, 24), (11, 26)):
        b.lamp(x, 18, z, shrine.LAMP_WARM)


def build_guesthouse_walls(b, x0, x1, z0, z1, floor_y, wall_top, entry_side=None):
    for y in range(floor_y + 1, wall_top + 1):
        for x in range(x0, x1 + 1):
            for z in (z0, z1):
                if entry_side == "south" and z == z1 and x0 + 7 <= x <= x0 + 10 and y <= wall_top - 1:
                    continue
                pillar = x in (x0, (x0 + x1) // 2, x1)
                if pillar or y == wall_top:
                    b.set(x, y, z, "wood", shrine.WOOD_BEAM)
                elif y in (floor_y + 2, floor_y + 3):
                    b.fence(x, y, z, shrine.SHOJI_PAPER)
                else:
                    b.set(x, y, z, "brick", shrine.plaster_color(x, y, z))
        for z in range(z0 + 1, z1):
            for x in (x0, x1):
                pillar = z in (z0, (z0 + z1) // 2, z1)
                if pillar or y == wall_top:
                    b.set(x, y, z, "wood", shrine.WOOD_BEAM)
                elif y in (floor_y + 2, floor_y + 3):
                    b.fence(x, y, z, shrine.SHOJI_PAPER)
                else:
                    b.set(x, y, z, "brick", shrine.plaster_color(x, y, z))


def build_north_guesthouse(b):
    # Guest hall floor world Y=97, walls Y=98..101, low roof topping out at Y=107.
    for x in range(7, 27):
        for z in range(4, 23):
            b.set(x, 15, z, "wood", shrine.wood_color(x, 15, z))
    shrine.fill_tatami(b, 9, 24, 15, 6, 20)
    build_guesthouse_walls(b, 8, 25, 5, 21, 15, 19, entry_side="south")

    # South-facing open doorway with noren banners kept above head height.
    for x in (15, 18):
        b.set(x, 19, 21, "wood", shrine.VERMILION_BASE)
        b.set(x, 18, 22, "minecraft:red_wall_banner", facing="south")
    for x in range(15, 19):
        b.set(x, 19, 21, "wood", shrine.VERMILION_BASE)
    b.lamp(14, 18, 22, shrine.LAMP_VERMILION)
    b.lamp(19, 18, 22, shrine.LAMP_VERMILION)

    # Quiet reception furniture and warm ceiling light.
    for x in range(12, 15):
        for z in (12, 13):
            b.slab(x, 16, z, shrine.WOOD_DARK, "bottom")
    for x, z in ((11, 11), (11, 14), (15, 11), (15, 14)):
        b.set(x, 16, z, "minecraft:red_carpet")
    b.set(23, 16, 8, "minecraft:bookshelf")
    b.set(24, 16, 17, "minecraft:decorated_pot")
    for x, z in ((11, 8), (11, 18), (22, 8), (22, 18), (17, 13)):
        b.lamp(x, 19, z, shrine.LAMP_WARM)
    build_low_irimoya(b, 6, 27, 3, 23, 20, "z", 4)

    # Ritual preparation store, local X=31..43, Z=7..19.
    for x in range(30, 45):
        for z in range(6, 21):
            b.set(x, 15, z, "wood", shrine.wood_color(x, 15, z))
    build_guesthouse_walls(b, 31, 43, 7, 19, 15, 18)
    for y in range(16, 19):
        b.clear_box(37, y, 19, 37, y, 19)
    b.door(37, 16, 19, facing="south", hinge="left", color=shrine.WOOD_DARK)
    for x, z in ((33, 9), (36, 9), (40, 9), (33, 16), (41, 16)):
        b.set(x, 16, z, "minecraft:barrel", facing="up", open="false")
    b.set(34, 16, 13, "minecraft:chest", facing="east")
    b.set(40, 16, 13, "minecraft:bookshelf")
    b.set(42, 16, 17, "minecraft:decorated_pot")
    b.lamp(37, 18, 13, shrine.LAMP_AMBER)
    build_low_irimoya(b, 29, 45, 5, 21, 19, "x", 4)


def build_purification_basin(b):
    # Stone chozuya with an open water basin and bamboo spout.
    for x in range(50, 59):
        for z in range(8, 17):
            b.set(x, 15, z, "tile", shrine.STONE_BASE if (x + z) % 2 == 0 else shrine.STONE_WET)
    for x in range(51, 58):
        for z in range(9, 16):
            if x == 54 and z in (11, 12, 13):
                b.set(x, 16, z, "minecraft:water", level="0")
            else:
                b.set(x, 16, z, "brick", shrine.STONE_WET)
    for x, z in ((50, 8), (58, 8), (50, 16), (58, 16)):
        for y in range(16, 19):
            b.set(x, y, z, "wood", shrine.WOOD_BEAM)
    b.set(54, 17, 10, "minecraft:bamboo", age="1", leaves="none", stage="0")
    b.lamp(54, 18, 12, shrine.LAMP_SACRED)
    build_low_irimoya(b, 49, 59, 7, 17, 19, "x", 3)


def build_north_garden_and_walls(b):
    # Small raked garden south of the purification pavilion.
    for x in range(46, 63):
        for z in range(21, 29):
            color = shrine.SAND_WAVE if (x + z) % 2 == 0 else shrine.SAND_KARESANSUI
            b.set(x, 14, z, "brick", color)
    for x, z, color in ((48, 24, shrine.STONE_MOSS), (49, 24, shrine.STONE_DARK), (61, 26, shrine.STONE_MOSS)):
        b.set(x, 15, z, "brick", color)
    b.slab(48, 16, 24, shrine.STONE_MOSS, "bottom")
    shrine.build_sacred_tree(b, 59, 15, 24, height=7, crown_r=2.5)
    b.stone_lantern(46, 15, 27, glow=shrine.LAMP_AMBER)
    b.low_lantern(62, 15, 21, glow=shrine.LAMP_WARM)

    # Low boundary walls with south, west and east entries.
    for x in range(3, 69):
        if 14 <= x <= 20:
            continue
        b.wall(x, 15, 3, shrine.stone_wall_color(x, 15, 3))
        b.wall(x, 15, 28, shrine.stone_wall_color(x, 15, 28))
    for z in range(4, 28):
        b.wall(3, 15, z, shrine.stone_wall_color(3, 15, z))
        if not 12 <= z <= 18:
            b.wall(68, 15, z, shrine.stone_wall_color(68, 15, z))
    for x, z in ((3, 3), (68, 3), (3, 28), (68, 28), (13, 28), (21, 28)):
        b.slab(x, 16, z, shrine.STONE_LIGHT, "bottom")

    b.stone_lantern(12, 15, 26, glow=shrine.LAMP_AMBER)
    b.stone_lantern(22, 15, 26, glow=shrine.LAMP_AMBER)
    b.stone_lantern(28, 15, 23, glow=shrine.LAMP_WARM)
    b.low_lantern(45, 15, 26, glow=shrine.LAMP_WARM)


def build_north():
    b = shrine.Builder(NORTH.size)
    build_terrain(b, north_surface_y, salt=2)
    build_north_paths(b)
    build_north_guesthouse(b)
    build_purification_basin(b)
    build_north_garden_and_walls(b)
    add_ground_cover(b, north_surface_y, salt=2)
    normalize_sacred_leaves(b)
    return b.cells


# ---------------------------------------------------------------------------
# 3. East grounds: bell tower, practice dojo-style deck, ema rack and green frame
# ---------------------------------------------------------------------------
def east_surface_y(x, z):
    if z < 52:
        top = 12  # Bell-tower ground, world Y=100
    elif z < 60:
        top = 12 - (z - 51) // 3  # Gentle two-block transition
    else:
        top = 10  # Practice ground, world Y=98
    edge = min(x, 47 - x, z, 91 - z)
    if edge < 2 and not (5 <= x <= 42 and 58 <= z <= 82):
        top -= 1
    return max(0, top)


def build_east_paths(b):
    # Bell-tower forecourt and the processional way to the practice deck.
    for x in range(5, 14):
        for z in range(4, 18):
            b.set(x, 12, z, "tile", shrine.STONE_LIGHT if (x + z) % 2 == 0 else shrine.STONE_BASE)
    for z in range(16, 59):
        y = east_surface_y(9, z)
        for x in range(8, 11):
            b.set(x, y, z, "tile", shrine.STONE_LIGHT if (x + z) % 2 == 0 else shrine.STONE_BASE)
    for x in range(9, 31):
        y = east_surface_y(x, 57)
        for z in range(56, 59):
            b.set(x, y, z, "tile", shrine.STONE_BASE if (x + z) % 2 == 0 else shrine.STONE_LIGHT)
    pave(b, 27, 32, 10, 58, 60, border=True)

    # Stepping stones around the green frame keep the grounds walkable.
    for x in range(17, 42):
        if x % 3 == 0:
            z = 48 if x < 30 else 52
            y = east_surface_y(x, z)
            b.set(x, y, z, "tile", shrine.STONE_MOSS if x % 2 else shrine.STONE_BASE)


def build_bell_tower(b):
    # Raised stone base at world Y=101, local X=5..13, Z=4..12.
    for x in range(5, 14):
        for z in range(4, 13):
            b.set(x, 13, z, "brick", shrine.STONE_DARK if x in (5, 13) or z in (4, 12) else shrine.STONE_BASE)
    for z in range(13, 17):
        b.stairs(9, 13, z, "north", shrine.STONE_LIGHT)
        b.set(9, 12, z, "brick", shrine.STONE_DARK)

    for x, z in ((6, 5), (12, 5), (6, 11), (12, 11)):
        for y in range(14, 23):
            b.set(x, y, z, "wood", shrine.WOOD_BEAM if y % 4 else shrine.VERMILION_SHADE)
        b.set(x, 13, z, "brick", shrine.STONE_WET)
    for x in range(6, 13):
        b.set(x, 22, 5, "wood", shrine.WOOD_BEAM)
        b.set(x, 22, 11, "wood", shrine.WOOD_BEAM)
    for z in range(5, 12):
        b.set(6, 22, z, "wood", shrine.WOOD_BEAM)
        b.set(12, 22, z, "wood", shrine.WOOD_BEAM)
    # Diagonal-ish cross braces use the same existing wood block shape.
    for x, z, dx, dz in ((6, 5, 1, 1), (12, 5, -1, 1), (6, 11, 1, -1), (12, 11, -1, -1)):
        b.set(x + dx, 18, z + dz, "wood", shrine.WOOD_AGED)
        b.set(x + dx * 2, 20, z + dz * 2, "wood", shrine.WOOD_AGED)

    b.set(9, 21, 8, "minecraft:chain", axis="y", waterlogged="false")
    b.set(9, 20, 8, "minecraft:bell", attachment="ceiling", facing="east", powered="false")
    b.set(10, 20, 8, "wood", shrine.LACQUER_BLACK)
    b.set(10, 19, 8, "wood", shrine.WOOD_AGED)
    b.lamp(6, 21, 8, shrine.LAMP_AMBER)
    b.lamp(12, 21, 8, shrine.LAMP_AMBER)
    build_low_irimoya(b, 4, 14, 3, 13, 23, "z", 4)


def build_practice_grounds(b):
    # Practice platform world Y=99, local X=20..39, Z=60..79.
    for x in range(20, 40):
        for z in range(60, 80):
            edge = x in (20, 39) or z in (60, 79)
            b.set(x, 11, z, "wood", shrine.WOOD_BEAM if edge else shrine.wood_color(x, 11, z))
    # A pale stone sparring circle gives the large deck a clear visual center.
    for x in range(26, 34):
        for z in range(65, 74):
            distance = abs(x - 29.5) + abs(z - 69.0)
            if distance <= 4.5:
                border = distance >= 3.5
                color = shrine.STONE_DARK if border else (
                    shrine.STONE_LIGHT if (x + z) % 2 == 0 else shrine.STONE_BASE
                )
                b.set(x, 11, z, "tile", color)
    for x, z in ((20, 60), (39, 60), (20, 79), (39, 79), (29, 60), (29, 79)):
        b.set(x, 10, z, "brick", shrine.STONE_DARK)
        for y in range(12, 15):
            b.set(x, y, z, "wood", shrine.WOOD_BEAM)
    for x in range(20, 40):
        b.fence(x, 12, 60, shrine.WOOD_AGED)
        if not 27 <= x <= 32:
            b.fence(x, 12, 79, shrine.WOOD_AGED)
    for z in range(61, 79):
        b.fence(20, 12, z, shrine.WOOD_AGED)
        b.fence(39, 12, z, shrine.WOOD_AGED)

    # Training targets and hay bales use vanilla blocks; the dummies use landmark shapes only.
    for x, z in ((23, 64), (36, 64), (23, 75), (36, 75)):
        b.set(x, 12, z, "minecraft:target")
        b.set(x, 13, z, "wood", shrine.WOOD_DARK)
        b.slab(x, 14, z, shrine.WOOD_AGED, "bottom")
    for x, z in ((27, 68), (32, 68)):
        b.set(x, 12, z, "minecraft:hay_block", axis="x")
    for x, z in ((25, 70), (34, 70), (29, 74)):
        b.fence(x, 12, z, shrine.WOOD_DARK)
        b.set(x, 13, z, "wood", shrine.WOOD_AGED)
        b.set(x - 1, 13, z, "wood", shrine.WOOD_PALE)
        b.set(x + 1, 13, z, "wood", shrine.WOOD_PALE)
        b.slab(x, 14, z, shrine.WOOD_PALE, "bottom")
    b.set(29, 12, 66, "minecraft:barrel", facing="north", open="false")
    b.set(30, 12, 66, "minecraft:chest", facing="north")

    # Weapon rack along the east edge, built only from landmark wood/fence shapes.
    for z in (66, 72):
        for y in range(12, 16):
            b.set(37, y, z, "wood", shrine.WOOD_BEAM)
    for z in range(66, 73):
        b.set(37, 13, z, "wood", shrine.WOOD_AGED)
        b.set(37, 15, z, "wood", shrine.WOOD_AGED)
        b.fence(36, 14, z, shrine.WOOD_PALE)
    b.lamp(29, 15, 61, shrine.LAMP_WARM)
    b.slab(29, 16, 61, shrine.TILE_RIDGE, "bottom")


def build_east_details(b):
    add_ema_rack(b, 16, 25, 11, 34)
    b.stone_lantern(15, 13, 18, glow=shrine.LAMP_AMBER)
    b.stone_lantern(40, 13, 20, glow=shrine.LAMP_AMBER)
    b.stone_lantern(16, 13, 48, glow=shrine.LAMP_WARM)
    b.stone_lantern(41, 13, 52, glow=shrine.LAMP_WARM)
    b.low_lantern(18, 11, 59, glow=shrine.LAMP_AMBER)
    b.low_lantern(41, 11, 59, glow=shrine.LAMP_AMBER)
    b.low_lantern(18, 11, 80, glow=shrine.LAMP_WARM)
    b.low_lantern(41, 11, 80, glow=shrine.LAMP_WARM)

    # Sacred trees form a green frame while leaving both activity zones open.
    tree_spots = (
        (7, 23, 10, 3.2),
        (16, 29, 9, 3.0),
        (40, 29, 11, 3.5),
        (6, 43, 10, 3.3),
        (43, 45, 10, 3.2),
        (13, 55, 9, 2.9),
        (44, 58, 9, 3.0),
        (13, 85, 11, 3.5),
        (34, 86, 10, 3.3),
        (44, 84, 11, 3.6),
    )
    for x, z, height, radius in tree_spots:
        shrine.build_sacred_tree(b, x, east_surface_y(x, z) + 1, z, height=height, crown_r=radius)


def build_east():
    b = shrine.Builder(EAST.size)
    build_terrain(b, east_surface_y, salt=3)
    build_east_paths(b)
    build_bell_tower(b)
    build_practice_grounds(b)
    build_east_details(b)
    add_ground_cover(b, east_surface_y, salt=3)
    normalize_sacred_leaves(b)
    return b.cells


# ---------------------------------------------------------------------------
# Export / deterministic check
# ---------------------------------------------------------------------------
def validate(grounds, cells, vanilla_blockstates):
    if not cells:
        raise ValueError(f"{grounds.structure_id}: empty drawing")
    for x, y, z in cells:
        if not (0 <= x < grounds.size[0] and 0 <= y < grounds.size[1] and 0 <= z < grounds.size[2]):
            raise ValueError(f"{grounds.structure_id}: out-of-bounds cell {(x, y, z)}")
    for block, _, _ in cells.values():
        name = block if ":" in block else f"gensokyou:landmark_{block}"
        namespace, block_id = name.split(":", 1)
        if namespace == "minecraft":
            relative = f"assets/minecraft/blockstates/{block_id}.json"
            exists = relative in vanilla_blockstates
        elif namespace == "gensokyou":
            exists = (RESOURCES / f"assets/gensokyou/blockstates/{block_id}.json").is_file()
        else:
            raise ValueError(f"{grounds.structure_id}: unknown block ID {name}")
        if not exists:
            raise ValueError(f"{grounds.structure_id}: unknown block ID {name}")
    for pos, (block, _, props) in cells.items():
        if block == "gensokyou:sacred_leaves" and props:
            raise ValueError(f"{grounds.structure_id}: invalid properties for {block} at {pos}: {dict(props)}")
    if grounds is FRONT:
        # Nothing may obstruct the processional lane at walking height.
        for (x, y, z), (block, _, _) in cells.items():
            if 29 <= z <= 35 and 7 <= y <= 10:
                raise ValueError(f"Front Sando obstruction at {(x, y, z)}: {block}")


def export(args):
    with ZipFile(args.vanilla) as vanilla:
        version = json.loads(vanilla.read("version.json"))["world_version"]
        vanilla_blockstates = {
            name for name in vanilla.namelist()
            if name.startswith("assets/minecraft/blockstates/") and name.endswith(".json")
        }

    total_files = 0
    for grounds, builder in ((FRONT, build_front), (NORTH, build_north), (EAST, build_east)):
        cells = builder()
        validate(grounds, cells, vanilla_blockstates)
        raw_nbt = shrine.structure(cells, version, size=grounds.size)
        packed_nbt = gzip.compress(raw_nbt, mtime=0)
        raw_colors, rgb_count, section_count = shrine.colors(cells, grounds.origin)
        packed_colors = gzip.compress(raw_colors, mtime=0)
        outputs = (
            (Path(f"data/gensokyou/structure/{grounds.structure_id}.nbt"), packed_nbt),
            (ASSETS / f"landmark/{grounds.color_id}.bin", packed_colors),
        )

        for relative, contents in outputs:
            path = RESOURCES / relative
            if args.check:
                if not path.is_file():
                    raise SystemExit(f"Missing asset: {relative}")
                actual = path.read_bytes()
                actual_raw = gzip.decompress(actual)
                expected_raw = gzip.decompress(contents)
                if actual_raw != expected_raw:
                    raise SystemExit(f"Stale asset: {relative}")
            else:
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_bytes(contents)
            total_files += 1

        tinted = sum(1 for _, color, _ in cells.values() if color is not None)
        lamps = sum(1 for block, _, _ in cells.values() if block == "lamp")
        print(
            f"{grounds.structure_id}: {len(cells):,} blocks "
            f"({tinted:,} tinted, {lamps} lamps), origin={grounds.origin}, size={grounds.size}"
        )
        print(f"  NBT: {len(raw_nbt):,} bytes raw, {len(packed_nbt):,} bytes GZIP")
        print(
            f"  Colors: {rgb_count} RGB entries, {section_count} sections, "
            f"{len(raw_colors):,} bytes raw, {len(packed_colors):,} bytes GZIP"
        )

    action = "checked" if args.check else "wrote"
    print(f"{action.capitalize()} {total_files} outer-grounds assets")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true", help="Verify checked-in NBT/GLC2 outputs without writing")
    parser.add_argument("--vanilla", type=Path, default=VANILLA_JAR, help="Minecraft client-extra resources jar")
    args = parser.parse_args()
    export(args)


if __name__ == "__main__":
    main()
