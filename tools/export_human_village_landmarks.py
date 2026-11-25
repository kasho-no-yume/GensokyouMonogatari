"""Export the 4 grand Touhou NPC landmarks of the Human Village (NBT + GLC3 + isometric previews).

All buildings strictly follow the flush-ground convention:
  y = 0 is the exterior road / foundation plane AND ground-floor walkable floor (zero jump-in!).
  Roof-to-wall junctions are completely sealed by gable_roof_x / gable_roof_z / hip_gable_roof_x.

Grand 4x-area footprints:
1. hv_kourindou       (44x25x36) - Kourindou grand antique shop (West forest edge, faces South +Z)
2. hv_geidontei       (42x26x36) - Geidontei riverside izakaya tavern (SE canal lane, faces West -X)
3. hv_suzunaan        (38x26x34) - Suzunaan grand multi-tier bookshop (Central market SW, faces East +X)
4. hv_hieda_residence (76x28x64) - Hieda noble estate & chronicle archive (North terrace, faces South +Z)
"""
from __future__ import annotations

from export_human_village_common import (
    C_BOOKSHELF,
    C_GOLD_ACCENT,
    C_GRAVEL_DARK,
    C_GRAVEL_LIGHT,
    C_LAMP_GOLD,
    C_LANTERN_RED,
    C_LANTERN_WARM,
    C_MOSS_STONE,
    C_PINE_GREEN,
    C_SHOJI_PAPER,
    C_STONE_DARK,
    C_STONE_LIGHT,
    C_STONE_MID,
    C_TATAMI,
    C_VERMILION,
    ROLE_FIXED,
    VillageBuilder,
    export_building,
)


def _staircase_z(b: VillageBuilder, x0: int, x1: int, y_start: int, z_start: int, steps: int,
                 going_north: bool, color: int) -> None:
    """Build a smooth wooden staircase climbing +Y along Z."""
    dz = -1 if going_north else 1
    facing = "north" if going_north else "south"
    for i in range(steps):
        z = z_start + i * dz
        y = y_start + i
        for x in range(x0, x1 + 1):
            b.set(x, y, z, "wood_stairs", color, ROLE_FIXED, facing=facing, half="bottom", shape="straight", waterlogged="false")
            # Clear headroom above stairs
            b.clear(x, x, y + 1, y + 4, z, z)


# ---------------------------------------------------------------------------
# 1. 香霖堂 (Kourindou Grand Antique Shop) - 44x25x36, faces South (+Z)
# ---------------------------------------------------------------------------
def build_kourindou() -> VillageBuilder:
    sx, sy, sz = 44, 25, 36
    b = VillageBuilder(sx, sy, sz)

    ROOF = 0x465868
    RIDGE = 0x6A8094
    PLASTER = 0xEDE4D1
    WOOD_MAIN = 0x7A5338
    WOOD_DARK = 0x422B1B
    WOOD_LIGHT = 0xAD8358
    SIGN_GREEN = 0x355C4C
    CURTAIN = 0x33536B

    # 1. Ground plane y=0: forest-edge grass + stone approach + flush wooden shop floor!
    b.fill(0, sx - 1, 0, 0, 0, sz - 1, "gensokyou:gensokyo_grass_block")
    # Main shop footprint: x=5..33, z=4..25 (29x22 interior grand hall!)
    x0, x1, z0, z1 = 5, 33, 4, 25
    b.fill(x0, x1, 0, 0, z0, z1, "wood", WOOD_LIGHT, ROLE_FIXED)
    # Stone approach plaza & flush doorway threshold at y=0 (zero jump!)
    b.fill(15, 23, 0, 0, z1, sz - 1, "tile", C_STONE_LIGHT, ROLE_FIXED)
    b.fill(x0 - 1, x1 + 1, 0, 0, z1 + 1, z1 + 2, "tile", C_STONE_MID, ROLE_FIXED)
    # Perimeter stone foundation under walls (except doorway x=18..19)
    for x in range(x0, x1 + 1):
        b.set(x, 0, z0, "brick", C_STONE_DARK, ROLE_FIXED)
        if x not in (18, 19):
            b.set(x, 0, z1, "brick", C_STONE_DARK, ROLE_FIXED)
    for z in range(z0, z1 + 1):
        b.set(x0, 0, z, "brick", C_STONE_DARK, ROLE_FIXED)
        b.set(x1, 0, z, "brick", C_STONE_DARK, ROLE_FIXED)

    # 2. Two-story grand walls (y=1..10): lower dark cedar + upper cream plaster + heavy timber bays
    for y in range(1, 11):
        blk = "wood" if y in (1, 2, 6) else "brick"
        col = WOOD_MAIN if y <= 2 else (WOOD_DARK if y == 6 else PLASTER)
        for x in range(x0, x1 + 1):
            b.set(x, y, z0, blk, col, ROLE_FIXED)
            b.set(x, y, z1, blk, col, ROLE_FIXED)
        for z in range(z0, z1 + 1):
            b.set(x0, y, z, blk, col, ROLE_FIXED)
            b.set(x1, y, z, blk, col, ROLE_FIXED)

    for px in (x0, 11, 16, 21, 27, x1):
        b.pillar(px, z0, 1, 10, "wood", WOOD_DARK, ROLE_FIXED)
        b.pillar(px, z1, 1, 10, "wood", WOOD_DARK, ROLE_FIXED)
    for pz in (z0, 10, 15, 20, z1):
        b.pillar(x0, pz, 1, 10, "wood", WOOD_DARK, ROLE_FIXED)
        b.pillar(x1, pz, 1, 10, "wood", WOOD_DARK, ROLE_FIXED)

    # 3. Symmetrical 2-block-wide grand double door at x=18..19 (flush y=0 entry!)
    b.clear(18, 19, 1, 3, z1, z1)
    b.double_door(18, 1, z1, facing="south", axis="x", color=WOOD_DARK, role=ROLE_FIXED)
    b.set(17, 1, z1, "wood", WOOD_DARK, ROLE_FIXED)
    b.set(17, 2, z1, "wood", WOOD_DARK, ROLE_FIXED)
    b.set(17, 3, z1, "wood", WOOD_DARK, ROLE_FIXED)
    b.set(20, 1, z1, "wood", WOOD_DARK, ROLE_FIXED)
    b.set(20, 2, z1, "wood", WOOD_DARK, ROLE_FIXED)
    b.set(20, 3, z1, "wood", WOOD_DARK, ROLE_FIXED)
    b.fill(17, 20, 4, 4, z1, z1, "wood", WOOD_DARK, ROLE_FIXED)
    # Shop noren & antique bronze-green signboard on front portico
    b.noren(17, 20, 3, z1 + 1, z1 + 1, facing="south", color=CURTAIN, role=ROLE_FIXED)
    b.fill(16, 21, 5, 5, z1 + 1, z1 + 1, "wood", SIGN_GREEN, ROLE_FIXED)
    b.fill(18, 19, 5, 5, z1 + 1, z1 + 1, "tile", C_GOLD_ACCENT, ROLE_FIXED)

    # Wide display showcase windows flanking entrance + 2nd floor shoji gallery windows
    b.shoji_span(7, 10, 2, 4, z1, z1, C_SHOJI_PAPER)
    b.shoji_span(12, 15, 2, 4, z1, z1, C_SHOJI_PAPER)
    b.shoji_span(22, 26, 2, 4, z1, z1, C_SHOJI_PAPER)
    b.shoji_span(28, 31, 2, 4, z1, z1, C_SHOJI_PAPER)
    # 2nd floor front & side windows
    b.shoji_span(7, 10, 7, 9, z1, z1, C_SHOJI_PAPER)
    b.shoji_span(13, 24, 7, 9, z1, z1, C_SHOJI_PAPER)
    b.shoji_span(28, 31, 7, 9, z1, z1, C_SHOJI_PAPER)
    for wz0, wz1 in ((6, 9), (11, 14), (16, 19), (21, 24)):
        b.shoji_span(x0, x0, 2, 4, wz0, wz1, C_SHOJI_PAPER)
        b.shoji_span(x0, x0, 7, 9, wz0, wz1, C_SHOJI_PAPER)

    # 4. Grand projecting entrance porch (genkan, x=15..22, z=26..29) + lower wrap hisashi
    for x in range(x0 - 1, x1 + 2):
        b.set(x, 6, z1 + 1, "tile_slab", ROOF, ROLE_FIXED, type="top", waterlogged="false")
    b.pillar(15, 29, 1, 5, "wood", WOOD_DARK, ROLE_FIXED)
    b.pillar(22, 29, 1, 5, "wood", WOOD_DARK, ROLE_FIXED)
    b.fill(15, 22, 5, 5, 26, 29, "wood", WOOD_DARK, ROLE_FIXED)
    b.clear(16, 21, 5, 5, 26, 28)
    b.gable_roof_z(15, 22, 26, 29, base_y=6, overhang_x=1, overhang_z=1,
                   main_color=ROOF, ridge_color=RIDGE, main_role=ROLE_FIXED, ridge_role=ROLE_FIXED,
                   gable_fill_role=ROLE_FIXED, gable_trim_role=ROLE_FIXED,
                   gable_fill_color=PLASTER, gable_trim_color=WOOD_DARK)
    b.hanging_lantern(16, 4, 28, C_LANTERN_WARM)
    b.hanging_lantern(21, 4, 28, C_LANTERN_WARM)

    # 5. Grand Irimoya Roof over main hall (base_y=11, sealed zero-gap)
    top_y = b.hip_gable_roof_x(x0, x1, z0, z1, base_y=11, overhang=2,
                               main_color=ROOF, ridge_color=RIDGE,
                               main_role=ROLE_FIXED, ridge_role=ROLE_FIXED,
                               gable_fill_color=PLASTER, gable_trim_color=WOOD_DARK)
    b.set(x0 + 3, min(sy - 1, top_y), (z0 + z1) // 2, "tile", C_GOLD_ACCENT, ROLE_FIXED)
    b.set(x1 - 3, min(sy - 1, top_y), (z0 + z1) // 2, "tile", C_GOLD_ACCENT, ROLE_FIXED)

    # 6. RICH ANTIQUE SHOP INTERIOR ("琳琅满目的外界道具与古董商店")
    # Perimeter floor-to-ceiling curio & book shelves
    b.fill(x0 + 1, x1 - 1, 1, 4, z0 + 1, z0 + 1, "bookshelf", C_BOOKSHELF, ROLE_FIXED)
    b.fill(x0 + 1, x0 + 1, 1, 4, z0 + 2, z1 - 2, "bookshelf", 0x9C724A, ROLE_FIXED)
    b.fill(x1 - 1, x1 - 1, 1, 4, z0 + 2, z1 - 2, "bookshelf", 0x9C724A, ROLE_FIXED)

    # Rinnosuke's Grand Appraisal Counter (north center, x=14..24, z=8..10)
    b.fill(14, 24, 1, 1, 9, 9, "wood", WOOD_DARK, ROLE_FIXED)
    b.fill(14, 24, 2, 2, 9, 9, "wood_slab", WOOD_LIGHT, ROLE_FIXED, type="bottom", waterlogged="false")
    b.set(15, 2, 9, "lantern", C_LANTERN_WARM, ROLE_FIXED, hanging="false", waterlogged="false")
    b.set(23, 2, 9, "lantern", C_LANTERN_WARM, ROLE_FIXED, hanging="false", waterlogged="false")
    # Tatami appraisal dais behind counter
    b.tatami_floor(15, 23, 0, 6, 8)
    b.set(19, 1, 6, "wood_slab", WOOD_DARK, ROLE_FIXED, type="bottom", waterlogged="false")

    # 6 Colorful Merchandise Display Islands across the main floor (left & right wings)
    islands = [
        # (ix0, ix1, iz0, iz1, table_color, item_colors)
        (8, 13, 13, 15, WOOD_MAIN, (0x4CC2A8, 0xE6B84C, 0xC8524A)),   # Jade/gold/ruby oddities
        (8, 13, 19, 21, WOOD_MAIN, (0x5B82C4, 0xD98B43, 0x8F5AA8)),   # Outside-world artifacts
        (25, 30, 13, 15, WOOD_MAIN, (0xC94C4C, 0xE0C368, 0x4B8B6B)),  # Ceramics & lacquerware
        (25, 30, 19, 21, WOOD_MAIN, (0x6B8E9B, 0xD46A4E, 0xC9A86A)),  # Scrolls & antique tools
    ]
    for ix0, ix1, iz0, iz1, tc, cols in islands:
        b.fill(ix0, ix1, 1, 1, iz0, iz1, "wood", tc, ROLE_FIXED)
        # Curios displayed on top of counters (colored slabs, lamps, books, hanging banners)
        b.set(ix0 + 1, 2, iz0 + 1, "lamp", cols[0], ROLE_FIXED)
        b.set(ix0 + 2, 2, iz0 + 1, "tile_slab", cols[1], ROLE_FIXED, type="bottom", waterlogged="false")
        b.set(ix0 + 3, 2, iz0 + 1, "bookshelf", C_BOOKSHELF, ROLE_FIXED)
        b.set(ix0 + 4, 2, iz0 + 1, "brick_slab", cols[2], ROLE_FIXED, type="bottom", waterlogged="false")
        b.set(ix0, 2, iz0, "lantern", C_LANTERN_WARM, ROLE_FIXED, hanging="false", waterlogged="false")
    # Central aisle antique rug & rare showcase pedestal
    b.fill(17, 21, 0, 0, 12, 23, "tile", 0x8F3A32, ROLE_FIXED)
    b.fill(18, 20, 1, 1, 16, 18, "brick", C_STONE_DARK, ROLE_FIXED)
    b.set(19, 2, 17, "lamp", 0x9EF0E0, ROLE_FIXED)  # Glowing centerpiece outside-world relic!

    # Second-floor wrap-around mezzanine gallery (y=6, open central atrium x=12..26, z=11..21!)
    b.fill(x0 + 1, x1 - 1, 6, 6, z0 + 1, z1 - 1, "wood", WOOD_MAIN, ROLE_FIXED)
    b.clear(12, 26, 6, 6, 11, 21)
    # Gallery railings overlooking the 1st-floor shop atrium
    for x in range(12, 27):
        b.set(x, 7, 11, "wood_fence", WOOD_DARK, ROLE_FIXED, east="true", west="true", north="false", south="false", waterlogged="false")
        b.set(x, 7, 21, "wood_fence", WOOD_DARK, ROLE_FIXED, east="true", west="true", north="false", south="false", waterlogged="false")
    for z in range(11, 22):
        b.set(12, 7, z, "wood_fence", WOOD_DARK, ROLE_FIXED, north="true", south="true", east="false", west="false", waterlogged="false")
        b.set(26, 7, z, "wood_fence", WOOD_DARK, ROLE_FIXED, north="true", south="true", east="false", west="false", waterlogged="false")
    # 2nd floor curio shelves along walls
    b.fill(x0 + 1, x1 - 1, 7, 9, z0 + 1, z0 + 1, "bookshelf", C_BOOKSHELF, ROLE_FIXED)
    b.fill(x0 + 1, x0 + 1, 7, 9, z0 + 2, z1 - 2, "bookshelf", C_BOOKSHELF, ROLE_FIXED)
    b.fill(x1 - 1, x1 - 1, 7, 9, z0 + 2, z1 - 2, "bookshelf", C_BOOKSHELF, ROLE_FIXED)
    # Wide wooden staircase up to 2nd floor gallery (east back corner)
    _staircase_z(b, 29, 31, 1, 11, 6, going_north=True, color=WOOD_DARK)
    # Bright warm lighting across 1st floor shelves, 2nd floor wrap-around gallery & central atrium
    for lx in (9, 14, 19, 24, 29):
        for lz in (7, 13, 19, 23):
            b.hanging_lantern(lx, 5, lz, C_LANTERN_WARM)
            b.hanging_lantern(lx, 10, lz, C_LANTERN_WARM)
    for gx in (12, 19, 26):
        b.standing_lantern(gx, 8, 11, C_LANTERN_WARM)
        b.standing_lantern(gx, 8, 21, C_LANTERN_WARM)
    for gz in (14, 18):
        b.standing_lantern(12, 8, gz, C_LANTERN_WARM)
        b.standing_lantern(26, 8, gz, C_LANTERN_WARM)
    for lx in (8, 19, 30):
        for lz in (7, 16, 23):
            b.set(lx, 11, lz, "lamp", C_LAMP_GOLD, ROLE_FIXED)

    # 7. East Covered Antique Warehouse Yard (x=34..41, z=8..23)
    b.fill(34, 41, 0, 0, 8, 23, "brick", C_STONE_MID, ROLE_FIXED)
    for pz in (8, 15, 23):
        b.pillar(41, pz, 1, 4, "wood", WOOD_DARK, ROLE_FIXED)
    for z in range(8, 24):
        for dx in range(8):
            b.set(34 + dx, 5 - (dx // 4), z, "tile_slab", ROOF, ROLE_FIXED,
                  type="bottom" if dx % 2 == 0 else "top", waterlogged="false")
    # Crates, stacked curios & banners in yard
    b.fill(35, 37, 1, 2, 10, 12, "wood", WOOD_LIGHT, ROLE_FIXED)
    b.fill(35, 36, 1, 3, 17, 20, "bookshelf", C_BOOKSHELF, ROLE_FIXED)
    b.noren(41, 41, 3, 10, 14, facing="east", color=SIGN_GREEN, role=ROLE_FIXED)

    # Forest-edge lanterns & oak trees
    b.stone_lantern(13, 1, 33, C_LAMP_GOLD)
    b.stone_lantern(25, 1, 33, C_LAMP_GOLD)
    b.simple_tree(2, 1, 30, height=7, leaf_block="minecraft:oak_leaves")
    b.simple_tree(39, 1, 30, height=7, leaf_block="minecraft:oak_leaves")
    return b


# ---------------------------------------------------------------------------
# 2. 鲵吞亭 (Geidontei Grand Riverside Izakaya Tavern) - 42x26x36, faces West (-X)
# ---------------------------------------------------------------------------
def build_geidontei() -> VillageBuilder:
    sx, sy, sz = 42, 29, 36
    b = VillageBuilder(sx, sy, sz)

    ROOF = 0x2F343D
    RIDGE = 0x525B69
    WOOD_DARK = 0x332014
    WOOD_MAIN = 0x633B22
    WOOD_WARM = 0x946139
    PLASTER = 0xEAE0CC
    NOREN_RED = 0xC23327
    SAKE_BARREL = 0xC7A67B
    SAKE_HOOP = 0x4B3624

    # 1. Ground y=0: riverside wooden Kawadoko deck (x=0..10) + flush tavern floor (x=11..37)
    b.fill(0, sx - 1, 0, 0, 0, sz - 1, "brick", C_STONE_DARK, ROLE_FIXED)
    b.fill(0, 10, 0, 0, 3, 32, "wood", WOOD_MAIN, ROLE_FIXED)  # Wide riverside terrace deck
    x0, x1, z0, z1 = 11, 37, 4, 31
    b.fill(x0, x1, 0, 0, z0, z1, "wood", WOOD_WARM, ROLE_FIXED)

    # 2. Riverside Kawadoko Deck Details (x=0..10, z=3..32): vermilion rail, low sake tables & lanterns
    for dz in range(3, 33):
        if dz not in (16, 17, 18, 19):  # Wide 4-block main riverside entrance opening
            b.set(1, 1, dz, "wood_fence", C_VERMILION, ROLE_FIXED,
                  north="true" if dz > 3 and dz != 20 else "false",
                  south="true" if dz < 32 and dz != 15 else "false",
                  east="false", west="false", waterlogged="false")
    for pz in (3, 9, 15, 20, 26, 32):
        b.pillar(1, pz, 1, 2, "wood", C_VERMILION, ROLE_FIXED)
        b.standing_lantern(1, 3, pz, C_LANTERN_RED)
    # 4 Outdoor riverside dining tables with red zabuton cushions
    for tz in (6, 11, 22, 27):
        b.fill(4, 6, 1, 1, tz, tz + 2, "wood_slab", WOOD_DARK, ROLE_FIXED, type="bottom", waterlogged="false")
        b.set(3, 1, tz + 1, "wood_slab", NOREN_RED, ROLE_FIXED, type="bottom", waterlogged="false")
        b.set(7, 1, tz + 1, "wood_slab", NOREN_RED, ROLE_FIXED, type="bottom", waterlogged="false")
        b.set(5, 2, tz + 1, "lantern", C_LANTERN_WARM, ROLE_FIXED, hanging="false", waterlogged="false")

    # Iconic stacked Sake Barrels (Komodaru) flanking tavern entrance!
    for bz in (11, 12, 23, 24):
        b.set(9, 1, bz, "wood", SAKE_BARREL, ROLE_FIXED)
        b.set(9, 2, bz, "wood", SAKE_BARREL, ROLE_FIXED)
        b.set(10, 1, bz, "wood", SAKE_HOOP, ROLE_FIXED)
        b.set(10, 2, bz, "wood", SAKE_BARREL, ROLE_FIXED)
        b.set(10, 3, bz, "wood_slab", SAKE_BARREL, ROLE_FIXED, type="bottom", waterlogged="false")

    # 3. Two-Story Grand Izakaya Walls (y=1..11)
    for y in range(1, 12):
        for x in range(x0, x1 + 1):
            is_beam = y in (5, 6, 11)
            blk = "wood" if (y <= 3 or is_beam) else "brick"
            col = WOOD_DARK if is_beam else (WOOD_MAIN if y <= 3 else PLASTER)
            b.set(x, y, z0, blk, col, ROLE_FIXED)
            b.set(x, y, z1, blk, col, ROLE_FIXED)
        for z in range(z0, z1 + 1):
            is_beam = y in (5, 6, 11)
            blk = "wood" if (y <= 3 or is_beam) else "brick"
            col = WOOD_DARK if is_beam else (WOOD_MAIN if y <= 3 else PLASTER)
            b.set(x0, y, z, blk, col, ROLE_FIXED)
            b.set(x1, y, z, blk, col, ROLE_FIXED)

    for px in (x0, 17, 24, 31, x1):
        b.pillar(px, z0, 1, 11, "wood", WOOD_DARK, ROLE_FIXED)
        b.pillar(px, z1, 1, 11, "wood", WOOD_DARK, ROLE_FIXED)
    for pz in (z0, 10, 15, 20, 25, z1):
        b.pillar(x0, pz, 1, 11, "wood", WOOD_DARK, ROLE_FIXED)
        b.pillar(x1, pz, 1, 11, "wood", WOOD_DARK, ROLE_FIXED)

    # 4. Iconic West Izakaya Frontage (x=x0): wide open doorway, huge red noren, Sugidama cedar ball & lantern curtain!
    b.clear(x0, x0, 1, 4, 15, 20)
    b.noren(x0, x0, 4, 15, 20, facing="west", color=NOREN_RED, role=ROLE_FIXED)
    b.noren(x0 - 1, x0 - 1, 4, 15, 20, facing="west", color=NOREN_RED, role=ROLE_FIXED)
    # Sugidama (traditional green cedar ball hanging above sake tavern entrance!)
    b.fill(x0 - 2, x0 - 2, 4, 5, 17, 18, "minecraft:oak_leaves", None, ROLE_FIXED, persistent="true", distance="1")
    # Warm glowing ground-floor shoji windows along facade
    b.shoji_span(x0, x0, 2, 4, 6, 9, C_LANTERN_WARM)
    b.shoji_span(x0, x0, 2, 4, 26, 29, C_LANTERN_WARM)
    # Deep projecting West hisashi canopy (x=x0-3..x0-1, y=6) + dense row of glowing red chochin lanterns!
    for z in range(z0 - 1, z1 + 2):
        b.set(x0 - 1, 6, z, "tile_stairs", ROOF, ROLE_FIXED, facing="east", half="bottom", shape="straight", waterlogged="false")
        b.set(x0 - 2, 6, z, "tile_slab", ROOF, ROLE_FIXED, type="top", waterlogged="false")
        b.set(x0 - 3, 6, z, "tile_slab", ROOF, ROLE_FIXED, type="bottom", waterlogged="false")
    for lz in range(z0, z1 + 1, 3):
        b.hanging_lantern(x0 - 3, 5, lz, C_LANTERN_RED)
        if lz % 6 == 1:
            b.set(x0 - 2, 5, lz, "lamp", C_LANTERN_RED, ROLE_FIXED)

    # Second-floor overhanging riverside balcony + warm glowing banquet shoji windows
    b.shoji_span(x0, x0, 7, 10, 6, 14, C_LANTERN_WARM)
    b.shoji_span(x0, x0, 7, 10, 16, 20, C_LANTERN_WARM)
    b.shoji_span(x0, x0, 7, 10, 22, 29, C_LANTERN_WARM)
    b.shoji_span(14, 34, 7, 9, z0, z0, C_SHOJI_PAPER)
    b.shoji_span(14, 34, 7, 9, z1, z1, C_SHOJI_PAPER)
    for z in range(z0 + 1, z1):
        b.set(x0 - 2, 7, z, "wood_fence", C_VERMILION, ROLE_FIXED,
              north="true" if z > z0 + 1 else "false",
              south="true" if z < z1 - 1 else "false",
              east="false", west="false", waterlogged="false")
    for lz in range(z0 + 2, z1, 5):
        b.hanging_lantern(x0 - 2, 10, lz, C_LANTERN_RED)

    # 5. Grand Tavern Interior (Ground Floor y=0..5)
    # Huge U-shaped Izakaya Bar Counter (x=22..32, z=10..25)
    b.fill(22, 22, 1, 1, 10, 25, "wood", WOOD_DARK, ROLE_FIXED)
    b.fill(22, 32, 1, 1, 10, 10, "wood", WOOD_DARK, ROLE_FIXED)
    b.fill(22, 32, 1, 1, 25, 25, "wood", WOOD_DARK, ROLE_FIXED)
    b.fill(22, 22, 2, 2, 10, 25, "wood_slab", WOOD_WARM, ROLE_FIXED, type="bottom", waterlogged="false")
    # Bar stools lining outside of U-counter
    for sz_i in range(11, 25, 2):
        b.set(20, 1, sz_i, "wood_slab", WOOD_MAIN, ROLE_FIXED, type="bottom", waterlogged="false")
    # Glowing Sake Bottle Wall & Robata Hearth inside bar (x=34..36)
    b.fill(x1 - 1, x1 - 1, 1, 4, 8, 27, "bookshelf", 0x9C633D, ROLE_FIXED)
    b.fill(27, 30, 1, 1, 16, 19, "brick", C_STONE_DARK, ROLE_FIXED)
    b.fill(28, 29, 1, 1, 17, 18, "lamp", 0xFF7B3B, ROLE_FIXED)  # Glowing charcoal robata hearth!
    # Booth seating alcoves on North & South walls with noren dividers
    for bz in (6, 28):
        for bx in (14, 21, 28):
            b.fill(bx, bx + 3, 1, 1, bz, bz + 1, "wood_slab", WOOD_DARK, ROLE_FIXED, type="bottom", waterlogged="false")
            b.noren(bx, bx + 3, 4, bz + 2 if bz < 15 else bz - 1, bz + 2 if bz < 15 else bz - 1,
                    facing="south" if bz < 15 else "north", color=NOREN_RED, role=ROLE_FIXED)

    # 6. Second Floor Banquet Hall (y=6..11) + Staircase
    b.fill(x0 + 1, x1 - 1, 6, 6, z0 + 1, z1 - 1, "wood", WOOD_MAIN, ROLE_FIXED)
    b.tatami_floor(x0 + 2, x1 - 2, 6, z0 + 2, z1 - 2)
    _staircase_z(b, 33, 35, 1, 29, 6, going_north=True, color=WOOD_DARK)
    # Long banquet tables upstairs with warm table lanterns & full 2nd-floor ceiling illumination
    for tz in (10, 17, 24):
        b.fill(16, 28, 7, 7, tz, tz + 1, "wood_slab", WOOD_DARK, ROLE_FIXED, type="bottom", waterlogged="false")
        b.standing_lantern(16, 8, tz, C_LANTERN_WARM)
        b.standing_lantern(22, 8, tz, C_LANTERN_WARM)
        b.standing_lantern(28, 8, tz, C_LANTERN_WARM)
    for lx in (14, 20, 26, 32):
        for lz in (7, 13, 19, 25, 29):
            b.hanging_lantern(lx, 5, lz, C_LANTERN_WARM)
            b.hanging_lantern(lx, 11, lz, C_LANTERN_WARM)
    for lx in (15, 23, 31):
        for lz in (8, 18, 27):
            b.set(lx, 12, lz, "lamp", C_LAMP_GOLD, ROLE_FIXED)

    # 7. Majestic Irimoya Roof (base_y=12, ridge along Z so grand broad eave faces West canal!)
    # Use hip_gable_roof_x on wide rectangular hull + ridge finials
    top_y = b.hip_gable_roof_x(x0, x1, z0, z1, base_y=12, overhang=2,
                               main_color=ROOF, ridge_color=RIDGE,
                               main_role=ROLE_FIXED, ridge_role=ROLE_FIXED,
                               gable_fill_color=PLASTER, gable_trim_color=WOOD_DARK)
    b.set(x0 + 3, min(sy - 1, top_y), (z0 + z1) // 2, "tile", C_GOLD_ACCENT, ROLE_FIXED)
    b.set(x1 - 3, min(sy - 1, top_y), (z0 + z1) // 2, "tile", C_GOLD_ACCENT, ROLE_FIXED)
    return b


# ---------------------------------------------------------------------------
# 3. 铃奈庵 (Suzunaan Grand Multi-Tier Bookshop) - 38x26x34, faces East (+X)
# ---------------------------------------------------------------------------
def build_suzunaan() -> VillageBuilder:
    sx, sy, sz = 38, 26, 34
    b = VillageBuilder(sx, sy, sz)

    ROOF = 0x41566B
    RIDGE = 0x678099
    PLASTER = 0xF4EFE2
    WOOD_MAIN = 0x8A5F3B
    WOOD_DARK = 0x452D1B
    WOOD_LIGHT = 0xB38B62
    INDIGO = 0x264A7C
    INDIGO_LIGHT = 0x3F6AA3

    # 1. Flush ground y=0: street paving on East (x=31..37) + wooden bookshop floor (x=3..30)
    b.fill(0, sx - 1, 0, 0, 0, sz - 1, "tile", C_STONE_MID, ROLE_FIXED)
    b.fill(31, sx - 1, 0, 0, 1, sz - 2, "tile", C_STONE_LIGHT, ROLE_FIXED)

    # Two-tier silhouette for dramatic "Grand Bookshop" architectural hierarchy:
    # - Front Wing (East, x=17..30, z=4..29): 2-story open storefront & reading gallery (wall y=1..9)
    # - Rear Archive Hall (West, x=3..18, z=3..30): taller 2.5-story vaulted library hall (wall y=1..12)
    fx0, fx1, fz0, fz1 = 17, 30, 4, 29
    rx0, rx1, rz0, rz1 = 3, 18, 3, 30

    b.fill(rx0, fx1, 0, 0, fz0, fz1, "wood", WOOD_LIGHT, ROLE_FIXED)
    b.fill(rx0, rx1, 0, 0, rz0, rz1, "wood", WOOD_LIGHT, ROLE_FIXED)

    # 2. Rear Tall Archive Hall walls (y=1..12)
    for y in range(1, 13):
        is_beam = y in (5, 6, 12)
        blk = "wood" if (y <= 2 or is_beam) else "brick"
        col = WOOD_DARK if is_beam else (WOOD_MAIN if y <= 2 else PLASTER)
        for x in range(rx0, rx1 + 1):
            b.set(x, y, rz0, blk, col, ROLE_FIXED)
            b.set(x, y, rz1, blk, col, ROLE_FIXED)
        for z in range(rz0, rz1 + 1):
            b.set(rx0, y, z, blk, col, ROLE_FIXED)
            if y >= 10:  # Upper clerestory wall above front wing roof
                b.set(rx1, y, z, blk, col, ROLE_FIXED)
    for px in (rx0, 10, rx1):
        b.pillar(px, rz0, 1, 12, "wood", WOOD_DARK, ROLE_FIXED)
        b.pillar(px, rz1, 1, 12, "wood", WOOD_DARK, ROLE_FIXED)
    b.shoji_span(rx1, rx1, 10, 11, 7, 26, C_LANTERN_WARM)  # Upper glowing clerestory strip facing East!
    b.shoji_span(6, 15, 7, 10, rz0, rz0, C_SHOJI_PAPER)
    b.shoji_span(6, 15, 7, 10, rz1, rz1, C_SHOJI_PAPER)

    # 3. Front 2-Story Bookshop Wing walls (y=1..9)
    for y in range(1, 10):
        is_beam = y in (5, 9)
        blk = "wood" if (y <= 2 or is_beam) else "brick"
        col = WOOD_DARK if is_beam else (WOOD_MAIN if y <= 2 else PLASTER)
        for x in range(fx0, fx1 + 1):
            b.set(x, y, fz0, blk, col, ROLE_FIXED)
            b.set(x, y, fz1, blk, col, ROLE_FIXED)
        for z in range(fz0, fz1 + 1):
            b.set(fx1, y, z, blk, col, ROLE_FIXED)
    for px in (fx0, 23, fx1):
        b.pillar(px, fz0, 1, 9, "wood", WOOD_DARK, ROLE_FIXED)
        b.pillar(px, fz1, 1, 9, "wood", WOOD_DARK, ROLE_FIXED)
    for pz in (fz0, 10, 14, 19, 23, fz1):
        b.pillar(fx1, pz, 1, 9, "wood", WOOD_DARK, ROLE_FIXED)

    # 4. Grand East Bookshop Street Frontage (x=fx1): huge open bay, indigo noren, tiered street bookstalls!
    b.clear(fx1, fx1, 1, 4, 6, 27)
    b.noren(fx1, fx1, 4, 6, 27, facing="east", color=INDIGO, role=ROLE_FIXED)
    b.noren(fx1 + 1, fx1 + 1, 4, 14, 19, facing="east", color=INDIGO_LIGHT, role=ROLE_FIXED)
    # Outdoor tiered book display racks flanking wide central entrance (z=14..19 stays wide open!)
    for bz0, bz1 in ((6, 12), (21, 27)):
        b.fill(fx1 + 1, fx1 + 1, 1, 1, bz0, bz1, "wood_slab", WOOD_MAIN, ROLE_FIXED, type="top", waterlogged="false")
        b.fill(fx1 + 1, fx1 + 1, 2, 2, bz0 + 1, bz1 - 1, "bookshelf", C_BOOKSHELF, ROLE_FIXED)
    # Projecting East hisashi canopy + warm lanterns
    for z in range(fz0 - 1, fz1 + 2):
        b.set(fx1 + 1, 5, z, "tile_stairs", ROOF, ROLE_FIXED, facing="west", half="bottom", shape="straight", waterlogged="false")
        b.set(fx1 + 2, 5, z, "tile_slab", ROOF, ROLE_FIXED, type="top", waterlogged="false")
        b.set(fx1 + 3, 5, z, "tile_slab", ROOF, ROLE_FIXED, type="bottom", waterlogged="false")
    for lz in range(fz0 + 1, fz1, 4):
        b.hanging_lantern(fx1 + 3, 4, lz, C_LANTERN_WARM)

    # 2nd floor front reading gallery windows & overhanging wooden balustrade
    b.shoji_span(fx1, fx1, 6, 8, 6, 27, C_SHOJI_PAPER)
    b.shoji_span(19, 28, 6, 8, fz0, fz0, C_SHOJI_PAPER)
    b.shoji_span(19, 28, 6, 8, fz1, fz1, C_SHOJI_PAPER)
    for z in range(fz0 + 1, fz1):
        b.set(fx1 + 1, 6, z, "wood_fence", WOOD_DARK, ROLE_FIXED,
              north="true" if z > fz0 + 1 else "false",
              south="true" if z < fz1 - 1 else "false",
              east="false", west="false", waterlogged="false")

    # 5. Towering 2-Story Grand Bookshop Interior!
    # Floor-to-ceiling 9-block-high book walls in rear hall!
    b.fill(rx0 + 1, rx0 + 1, 1, 10, rz0 + 1, rz1 - 1, "bookshelf", C_BOOKSHELF, ROLE_FIXED)
    b.fill(rx0 + 2, fx1 - 2, 1, 4, fz0 + 1, fz0 + 1, "bookshelf", C_BOOKSHELF, ROLE_FIXED)
    b.fill(rx0 + 2, fx1 - 2, 1, 4, fz1 - 1, fz1 - 1, "bookshelf", C_BOOKSHELF, ROLE_FIXED)

    # 6 Rows of double-sided browsing bookshelves on 1st floor (like a true grand library/bookshop)
    for row_z in (8, 12, 21, 25):
        b.fill(8, 16, 1, 3, row_z, row_z, "bookshelf", C_BOOKSHELF, ROLE_FIXED)
        b.set(8, 4, row_z, "lantern", C_LANTERN_WARM, ROLE_FIXED, hanging="false", waterlogged="false")
        b.set(16, 4, row_z, "lantern", C_LANTERN_WARM, ROLE_FIXED, hanging="false", waterlogged="false")
    # Central Reading Hall table & Kosuzu's Lending Desk near East entry
    b.tatami_floor(10, 18, 0, 15, 18)
    b.fill(11, 17, 1, 1, 16, 17, "wood_slab", WOOD_DARK, ROLE_FIXED, type="bottom", waterlogged="false")
    b.fill(24, 24, 1, 1, 13, 20, "wood", WOOD_DARK, ROLE_FIXED)
    b.fill(24, 24, 2, 2, 13, 20, "wood_slab", WOOD_LIGHT, ROLE_FIXED, type="bottom", waterlogged="false")

    # 2nd floor wrap-around library mezzanine (y=6) overlooking central atrium (x=10..22, z=10..23 open!)
    b.fill(rx0 + 1, fx1 - 1, 6, 6, fz0 + 1, fz1 - 1, "wood", WOOD_MAIN, ROLE_FIXED)
    b.clear(10, 22, 6, 6, 10, 23)
    for x in range(10, 23):
        b.set(x, 7, 10, "wood_fence", WOOD_DARK, ROLE_FIXED, east="true", west="true", north="false", south="false", waterlogged="false")
        b.set(x, 7, 23, "wood_fence", WOOD_DARK, ROLE_FIXED, east="true", west="true", north="false", south="false", waterlogged="false")
    for z in range(10, 24):
        b.set(10, 7, z, "wood_fence", WOOD_DARK, ROLE_FIXED, north="true", south="true", east="false", west="false", waterlogged="false")
        b.set(22, 7, z, "wood_fence", WOOD_DARK, ROLE_FIXED, north="true", south="true", east="false", west="false", waterlogged="false")
    # 2nd floor rare-book shelves
    b.fill(rx0 + 2, fx1 - 2, 7, 9, fz0 + 1, fz0 + 1, "bookshelf", 0x9E704B, ROLE_FIXED)
    b.fill(rx0 + 2, fx1 - 2, 7, 9, fz1 - 1, fz1 - 1, "bookshelf", 0x9E704B, ROLE_FIXED)
    # Dual staircases up to 2nd-floor reading gallery
    _staircase_z(b, 26, 28, 1, 11, 6, going_north=True, color=WOOD_DARK)
    _staircase_z(b, 26, 28, 1, 22, 6, going_north=False, color=WOOD_DARK)
    # Bright warm illumination across 1st-floor aisles, 2nd-floor reading gallery & high archive hall
    for lx in (6, 12, 18, 24, 28):
        for lz in (6, 12, 17, 22, 27):
            b.hanging_lantern(lx, 5, lz, C_LANTERN_WARM)
            b.hanging_lantern(lx, 9, lz, C_LANTERN_WARM)
    for gx in (10, 16, 22):
        b.standing_lantern(gx, 8, 10, C_LANTERN_WARM)
        b.standing_lantern(gx, 8, 23, C_LANTERN_WARM)
    for gz in (14, 19):
        b.standing_lantern(10, 8, gz, C_LANTERN_WARM)
        b.standing_lantern(22, 8, gz, C_LANTERN_WARM)
    for lx in (7, 13):
        for lz in (7, 16, 25):
            b.hanging_lantern(lx, 12, lz, C_LANTERN_WARM)

    # 6. Layered Stepped Roofs: Front wing lower gable (base_y=10) + Rear tall archive gable (base_y=13)
    b.gable_roof_z(fx0, fx1, fz0, fz1, base_y=10, overhang_x=2, overhang_z=2,
                   main_color=ROOF, ridge_color=RIDGE, main_role=ROLE_FIXED, ridge_role=ROLE_FIXED,
                   gable_fill_role=ROLE_FIXED, gable_trim_role=ROLE_FIXED,
                   gable_fill_color=PLASTER, gable_trim_color=WOOD_DARK)
    top_y = b.gable_roof_z(rx0, rx1, rz0, rz1, base_y=13, overhang_x=2, overhang_z=2,
                           main_color=ROOF, ridge_color=RIDGE, main_role=ROLE_FIXED, ridge_role=ROLE_FIXED,
                           gable_fill_role=ROLE_FIXED, gable_trim_role=ROLE_FIXED,
                           gable_fill_color=PLASTER, gable_trim_color=WOOD_DARK)
    b.set((rx0 + rx1) // 2, min(sy - 1, top_y), rz0 - 1, "tile", C_GOLD_ACCENT, ROLE_FIXED)
    b.set((rx0 + rx1) // 2, min(sy - 1, top_y), rz1 + 1, "tile", C_GOLD_ACCENT, ROLE_FIXED)
    return b


# ---------------------------------------------------------------------------
# 4. 稗田阿求居所 (Hieda Noble Estate) - 76x28x64, faces South (+Z)
# ---------------------------------------------------------------------------
def build_hieda_residence() -> VillageBuilder:
    sx, sy, sz = 76, 28, 64
    b = VillageBuilder(sx, sy, sz)

    ROOF = 0x333B46
    RIDGE = 0x576575
    PLASTER = 0xF5F1E8
    WOOD_DARK = 0x382418
    WOOD_MAIN = 0x734E34
    WOOD_ENG = 0xAD855C
    GATE_RED = 0x8F2F25

    # 1. Ground y=0: ENTIRE estate courtyard & entry plaza flush at y=0 (ZERO 2-block threshold!)
    b.fill(0, sx - 1, 0, 0, 0, sz - 1, "gensokyou:gensokyo_grass_block")

    # 2. Noble Perimeter Estate Wall (x=3..72, z=3..59): stone base y=1 + white plaster y=2..4 + tile roof y=5
    wx0, wx1, wz0, wz1 = 3, 72, 3, 59
    for x in range(wx0, wx1 + 1):
        for z in (wz0, wz1):
            b.set(x, 0, z, "brick", C_STONE_DARK, ROLE_FIXED)
            b.set(x, 1, z, "brick", C_STONE_MID, ROLE_FIXED)
            b.set(x, 2, z, "brick", PLASTER, ROLE_FIXED)
            b.set(x, 3, z, "brick", PLASTER, ROLE_FIXED)
            b.set(x, 4, z, "wood", WOOD_DARK, ROLE_FIXED)
            b.set(x, 5, z, "tile_slab", ROOF, ROLE_FIXED, type="bottom", waterlogged="false")
    for z in range(wz0, wz1 + 1):
        for x in (wx0, wx1):
            b.set(x, 0, z, "brick", C_STONE_DARK, ROLE_FIXED)
            b.set(x, 1, z, "brick", C_STONE_MID, ROLE_FIXED)
            b.set(x, 2, z, "brick", PLASTER, ROLE_FIXED)
            b.set(x, 3, z, "brick", PLASTER, ROLE_FIXED)
            b.set(x, 4, z, "wood", WOOD_DARK, ROLE_FIXED)
            b.set(x, 5, z, "tile_slab", ROOF, ROLE_FIXED, type="bottom", waterlogged="false")

    # 3. Grand Nagayamon South Gatehouse (x=25..50, z=55..61) - Flush y=0 walkthrough!
    gx0, gx1, gz0, gz1 = 25, 50, 55, 61
    b.clear(gx0, gx1, 1, 6, gz0, gz1)
    b.fill(gx0, gx1, 0, 0, gz0, sz - 1, "tile", C_STONE_LIGHT, ROLE_FIXED)
    for wing_x0, wing_x1 in ((gx0, 34), (41, gx1)):
        for y in range(1, 6):
            blk = "wood" if y in (1, 5) else "brick"
            col = WOOD_DARK if y in (1, 5) else PLASTER
            b.fill(wing_x0, wing_x1, y, y, gz0, gz1, blk, col, ROLE_FIXED)
        b.clear(wing_x0 + 1, wing_x1 - 1, 1, 4, gz0 + 1, gz1 - 1)
        b.shoji_span(wing_x0 + 2, wing_x1 - 2, 2, 4, gz1, gz1, C_SHOJI_PAPER)
        for px in (wing_x0, wing_x1):
            b.pillar(px, gz0, 1, 5, "wood", WOOD_DARK, ROLE_FIXED)
            b.pillar(px, gz1, 1, 5, "wood", WOOD_DARK, ROLE_FIXED)
    # Grand 6-block open central portal (x=35..40) with heavy crossbeams & open vermilion leaves
    b.fill(34, 41, 5, 5, gz0, gz1, "wood", WOOD_DARK, ROLE_FIXED)
    b.set(35, 3, gz1, "wood_trapdoor", GATE_RED, ROLE_FIXED, facing="east", half="top", open="true", powered="false", waterlogged="false")
    b.set(35, 4, gz1, "wood_trapdoor", GATE_RED, ROLE_FIXED, facing="east", half="top", open="true", powered="false", waterlogged="false")
    b.set(40, 3, gz1, "wood_trapdoor", GATE_RED, ROLE_FIXED, facing="west", half="top", open="true", powered="false", waterlogged="false")
    b.set(40, 4, gz1, "wood_trapdoor", GATE_RED, ROLE_FIXED, facing="west", half="top", open="true", powered="false", waterlogged="false")
    b.hanging_lantern(35, 4, gz1 + 1, C_LANTERN_WARM)
    b.hanging_lantern(40, 4, gz1 + 1, C_LANTERN_WARM)
    b.stone_lantern(31, 1, 62, C_LAMP_GOLD)
    b.stone_lantern(44, 1, 62, C_LAMP_GOLD)
    b.gable_roof_x(gx0, gx1, gz0, gz1, base_y=6, overhang_x=2, overhang_z=1,
                   main_color=ROOF, ridge_color=RIDGE, main_role=ROLE_FIXED, ridge_role=ROLE_FIXED,
                   gable_fill_role=ROLE_FIXED, gable_trim_role=ROLE_FIXED,
                   gable_fill_color=PLASTER, gable_trim_color=WOOD_DARK)

    # 4. Expansive Twin Courtyard Gardens (z=30..54, flush y=0)
    b.fill(35, 40, 0, 0, 29, 54, "tile", C_STONE_LIGHT, ROLE_FIXED)  # Central grand stone avenue

    # West Garden: Grand Karesansui (Zen Raked Sand & Moss Boulder Garden, x=7..32, z=33..52)
    for x in range(7, 33):
        for z in range(33, 53):
            b.set(x, 0, z, "brick", C_GRAVEL_LIGHT if (z % 2 == 0) else C_GRAVEL_DARK, ROLE_FIXED)
    for bx, bz, h in ((14, 42, 3), (24, 38, 2), (22, 47, 2)):
        b.fill(bx, bx + 2, 1, h, bz, bz + 1, "brick", C_STONE_DARK, ROLE_FIXED)
        b.set(bx + 1, h + 1, bz, "brick_slab", C_MOSS_STONE, ROLE_FIXED, type="bottom", waterlogged="false")
    b.stone_lantern(31, 1, 51, C_LAMP_GOLD)
    b.stone_lantern(31, 1, 35, C_LAMP_GOLD)
    b.simple_tree(9, 1, 35, height=7, leaf_block="minecraft:cherry_leaves")
    b.simple_tree(10, 1, 50, height=6, leaf_block="minecraft:cherry_leaves")

    # East Garden: Grand Chisen Pond Garden with Arched Vermilion Bridge & Pavilion (x=43..68, z=33..52)
    b.fill(43, 68, 0, 0, 33, 52, "minecraft:water")
    for x in range(42, 70):
        b.set(x, 0, 32, "brick", C_MOSS_STONE, ROLE_FIXED)
        b.set(x, 0, 53, "brick", C_MOSS_STONE, ROLE_FIXED)
    for z in range(32, 54):
        b.set(42, 0, z, "brick", C_MOSS_STONE, ROLE_FIXED)
        b.set(69, 0, z, "brick", C_MOSS_STONE, ROLE_FIXED)
    # Arched vermilion bridge crossing pond (x=54..56, z=33..52)
    for z in range(33, 53):
        dist = abs(z - 42.5)
        y_b = 1 if dist > 5 else 2
        b.fill(54, 56, y_b, y_b, z, z, "wood_slab", WOOD_ENG, ROLE_FIXED, type="bottom", waterlogged="false")
        b.set(53, y_b, z, "wood_fence", C_VERMILION, ROLE_FIXED, north="true", south="true", east="false", west="false", waterlogged="false")
        b.set(57, y_b, z, "wood_fence", C_VERMILION, ROLE_FIXED, north="true", south="true", east="false", west="false", waterlogged="false")
    b.stone_lantern(44, 1, 51, C_LAMP_GOLD)
    b.simple_tree(67, 1, 35, height=7, leaf_block="minecraft:cherry_leaves")
    b.simple_tree(66, 1, 50, height=6, leaf_block="minecraft:cherry_leaves")

    # 5. Grand Main Shoin-zukuri Mansion Hall (x=14..51, z=8..26, elevated veranda y=1 with gentle 1-step stair!)
    mx0, mx1, mz0, mz1 = 14, 51, 8, 26
    b.fill(mx0 - 2, mx1 + 2, 0, 0, mz0 - 2, mz1 + 3, "brick", C_STONE_MID, ROLE_FIXED)
    b.fill(mx0 - 2, mx1 + 2, 1, 1, mz0 - 2, mz1 + 3, "wood", WOOD_ENG, ROLE_FIXED)
    b.tatami_floor(mx0 + 1, mx1 - 1, 1, mz0 + 1, mz1 - 1)
    # Smooth 1-block-high wide entry stairs from y=0 avenue up to y=1 veranda
    b.fill(34, 41, 1, 1, mz1 + 4, mz1 + 4, "brick_stairs", C_STONE_LIGHT, ROLE_FIXED,
           facing="north", half="bottom", shape="straight", waterlogged="false")

    # Main Hall walls (y=2..8)
    for y in range(2, 9):
        is_beam = y == 8
        blk = "wood" if is_beam else "brick"
        col = WOOD_DARK if is_beam else PLASTER
        for x in range(mx0, mx1 + 1):
            b.set(x, y, mz0, blk, col, ROLE_FIXED)
            b.set(x, y, mz1, blk, col, ROLE_FIXED)
        for z in range(mz0, mz1 + 1):
            b.set(mx0, y, z, blk, col, ROLE_FIXED)
            b.set(mx1, y, z, blk, col, ROLE_FIXED)
    for px in range(mx0, mx1 + 1, 6):
        b.pillar(px, mz0, 2, 8, "wood", WOOD_DARK, ROLE_FIXED)
        b.pillar(px, mz1, 2, 8, "wood", WOOD_DARK, ROLE_FIXED)
    b.pillar(mx1, mz0, 2, 8, "wood", WOOD_DARK, ROLE_FIXED)
    b.pillar(mx1, mz1, 2, 8, "wood", WOOD_DARK, ROLE_FIXED)
    for pz in (mz0, 14, 20, mz1):
        b.pillar(mx0, pz, 2, 8, "wood", WOOD_DARK, ROLE_FIXED)
        b.pillar(mx1, pz, 2, 8, "wood", WOOD_DARK, ROLE_FIXED)

    # Grand South shoji sliding doors & open reception entry (x=35..40)
    b.clear(35, 40, 2, 5, mz1, mz1)
    for sx0, sx1 in ((16, 19), (21, 25), (27, 31), (42, 46), (47, 50)):
        b.shoji_span(sx0, sx1, 2, 5, mz1, mz1, C_SHOJI_PAPER)
    b.shoji_span(mx0, mx0, 2, 5, 10, 24, C_SHOJI_PAPER)
    b.shoji_span(mx1, mx1, 2, 5, 10, 24, C_SHOJI_PAPER)

    # Engawa veranda posts & warm hanging lanterns
    for vx in (mx0 - 2, 20, 26, 33, 42, 48, mx1 + 2):
        b.pillar(vx, mz1 + 3, 1, 7, "wood", WOOD_DARK, ROLE_FIXED)
    b.hanging_lantern(34, 6, mz1 + 2, C_LANTERN_WARM)
    b.hanging_lantern(41, 6, mz1 + 2, C_LANTERN_WARM)

    # Main Hall Interior: Akyuu's Grand Chronicle Writing Hall, Tokonoma & Bookshelves
    b.fill(18, 47, 2, 5, mz0 + 1, mz0 + 1, "bookshelf", C_BOOKSHELF, ROLE_FIXED)
    b.fill(30, 36, 2, 2, 14, 16, "wood_slab", WOOD_DARK, ROLE_FIXED, type="bottom", waterlogged="false")
    b.set(33, 3, 15, "lantern", C_LANTERN_WARM, ROLE_FIXED, hanging="false", waterlogged="false")
    # Flanking guest seating cushions
    b.fill(18, 24, 2, 2, 17, 19, "wood_slab", WOOD_MAIN, ROLE_FIXED, type="bottom", waterlogged="false")
    b.fill(42, 47, 2, 2, 17, 19, "wood_slab", WOOD_MAIN, ROLE_FIXED, type="bottom", waterlogged="false")
    b.fill(mx0 + 1, mx1 - 1, 8, 8, mz0 + 1, mz1 - 1, "wood", WOOD_MAIN, ROLE_FIXED)
    for lx in (20, 28, 36, 44):
        for lz in (13, 21):
            b.set(lx, 8, lz, "lamp", C_LAMP_GOLD, ROLE_FIXED)

    # Majestic Irimoya Grand Roof over Main Hall (base_y=9, completely sealed!)
    top_y = b.hip_gable_roof_x(mx0 - 2, mx1 + 2, mz0 - 2, mz1 + 3, base_y=9, overhang=2,
                               main_color=ROOF, ridge_color=RIDGE,
                               main_role=ROLE_FIXED, ridge_role=ROLE_FIXED,
                               gable_fill_color=PLASTER, gable_trim_color=WOOD_DARK)
    b.set(mx0 + 2, min(sy - 1, top_y), (mz0 + mz1) // 2, "tile", C_GOLD_ACCENT, ROLE_FIXED)
    b.set(mx1 - 2, min(sy - 1, top_y), (mz0 + mz1) // 2, "tile", C_GOLD_ACCENT, ROLE_FIXED)

    # 6. East Grand Two-Story Chronicle Archive Kura (白壁土藏大文库, x=57..69, z=8..25)
    kx0, kx1, kz0, kz1 = 57, 69, 8, 25
    b.fill(kx0, kx1, 0, 0, kz0, kz1, "brick", C_STONE_DARK, ROLE_FIXED)
    b.fill(kx0 + 1, kx1 - 1, 1, 1, kz0 + 1, kz1 - 1, "wood", WOOD_MAIN, ROLE_FIXED)
    for y in range(1, 12):
        blk = "tile" if y <= 2 else "brick"
        col = ROOF if y <= 2 else PLASTER
        for x in range(kx0, kx1 + 1):
            b.set(x, y, kz0, blk, col, ROLE_FIXED)
            b.set(x, y, kz1, blk, col, ROLE_FIXED)
        for z in range(kz0, kz1 + 1):
            b.set(kx0, y, z, blk, col, ROLE_FIXED)
            b.set(kx1, y, z, blk, col, ROLE_FIXED)
    # Vault double door facing West toward covered corridor
    b.clear(kx0, kx0, 2, 4, 15, 16)
    b.double_door(kx0, 2, 15, facing="west", axis="z", color=WOOD_DARK, role=ROLE_FIXED)
    # 2-story archive bookshelves inside Kura + bright 1st & 2nd floor warm lighting
    b.fill(kx1 - 1, kx1 - 1, 2, 10, kz0 + 1, kz1 - 1, "bookshelf", C_BOOKSHELF, ROLE_FIXED)
    b.fill(kx0 + 1, kx1 - 2, 2, 10, kz0 + 1, kz0 + 1, "bookshelf", C_BOOKSHELF, ROLE_FIXED)
    b.fill(kx0 + 1, kx1 - 2, 2, 10, kz1 - 1, kz1 - 1, "bookshelf", C_BOOKSHELF, ROLE_FIXED)
    b.fill(kx0 + 1, kx1 - 1, 6, 6, kz0 + 1, kz1 - 1, "wood", WOOD_MAIN, ROLE_FIXED)
    b.clear(kx0 + 2, kx0 + 5, 6, 6, 14, 18)
    for lx in (60, 65):
        for lz in (11, 16, 22):
            b.hanging_lantern(lx, 5, lz, C_LANTERN_WARM)
            b.hanging_lantern(lx, 11, lz, C_LANTERN_WARM)
            b.set(lx, 12, lz, "lamp", C_LAMP_GOLD, ROLE_FIXED)
    b.standing_lantern(60, 7, 11, C_LANTERN_WARM)
    b.standing_lantern(65, 7, 22, C_LANTERN_WARM)
    b.gable_roof_z(kx0, kx1, kz0, kz1, base_y=12, overhang_x=2, overhang_z=2,
                   main_color=ROOF, ridge_color=RIDGE, main_role=ROLE_FIXED, ridge_role=ROLE_FIXED,
                   gable_fill_role=ROLE_FIXED, gable_trim_role=ROLE_FIXED,
                   gable_fill_color=PLASTER, gable_trim_color=WOOD_DARK)

    # Covered wooden corridor linking Main Hall east side to Chronicle Archive (x=54..56, z=14..18)
    b.fill(54, 56, 1, 1, 14, 18, "wood", WOOD_ENG, ROLE_FIXED)
    b.fill(54, 56, 6, 6, 14, 18, "tile_slab", ROOF, ROLE_FIXED, type="bottom", waterlogged="false")

    return b


LANDMARKS = (
    ("hv_kourindou", build_kourindou),
    ("hv_geidontei", build_geidontei),
    ("hv_suzunaan", build_suzunaan),
    ("hv_hieda_residence", build_hieda_residence),
)


def export_all(write: bool = True, render_preview: bool = True) -> None:
    for struct_id, fn in LANDMARKS:
        b = fn()
        export_building(struct_id, b, write=write, render_preview=render_preview)
        print(f"[ok] {struct_id}: {b.sx}x{b.sy}x{b.sz}, {len(b.cells)} blocks")


if __name__ == "__main__":
    export_all()
