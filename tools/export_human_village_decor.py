"""Export non-functional atmosphere landmarks for the Human Village (NBT + GLC3 + previews).

- hv_dragon_plaza   (29x16x29) Central Dragon God Statue Plaza
- hv_gate_east      (14x12x19) East Kabukimon village gate + Kosatsuba noticeboard
- hv_gate_west      (10x11x15) West Kabukimon village gate (toward Kourindou / Magic Forest)
- hv_gate_north     (15x11x10) North Kabukimon village gate
- hv_watermill      (12x12x12) Riverside wooden watermill cottage
- hv_stall_dango / fish / umbrella / books (5x5x4) 4 market stalls
- hv_well (5x6x5), hv_jizo (3x5x3), hv_rest_tree (7x9x7) 3 street-corner micro nodes
"""
from __future__ import annotations

from PIL import Image, ImageDraw

from export_human_village_common import (
    C_BOOKSHELF,
    C_GOLD_ACCENT,
    C_LAMP_GOLD,
    C_LANTERN_RED,
    C_LANTERN_WARM,
    C_MOSS_STONE,
    C_STONE_DARK,
    C_STONE_LIGHT,
    C_STONE_MID,
    C_VERMILION,
    PREVIEWS,
    ROLE_FIXED,
    VillageBuilder,
    _render_single_iso,
    export_building,
)

ROOF_DARK = 0x3D4956
ROOF_RIDGE = 0x5E6F80
WOOD_DARK = 0x452D1E
WOOD_MAIN = 0x7D5436
WOOD_LIGHT = 0xA87F56
DRAGON_STONE = 0x768582
DRAGON_LIGHT = 0x99ABA7
DRAGON_EYE = 0x7EE8D4


def build_dragon_plaza() -> VillageBuilder:
    sx, sy, sz = 29, 16, 29
    b = VillageBuilder(sx, sy, sz)
    cx, cz = 14, 14

    # Octagonal flagstone plaza floor at y=1 (solid brick sub-base at y=0)
    for x in range(sx):
        for z in range(sz):
            dx, dz = abs(x - cx), abs(z - cz)
            if dx + dz <= 20:
                b.set(x, 0, z, "brick", C_STONE_DARK, ROLE_FIXED)
                border = (dx == 14 or dz == 14 or dx + dz == 20)
                ring = (dx in (6, 7) and dz <= 7) or (dz in (6, 7) and dx <= 7)
                c = C_STONE_DARK if border else (C_STONE_LIGHT if (x + z) % 2 == 0 or ring else C_STONE_MID)
                b.set(x, 1, z, "tile", c, ROLE_FIXED)

    # Reflecting shallow water basin around pedestal (radius 4..5)
    for x in range(cx - 5, cx + 6):
        for z in range(cz - 5, cz + 6):
            dx, dz = abs(x - cx), abs(z - cz)
            if dx == 5 or dz == 5:
                # Stone basin curb (open on 4 cardinal walkways of width 3)
                if dx > 1 and dz > 1:
                    b.set(x, 2, z, "brick_slab", C_STONE_DARK, ROLE_FIXED, type="bottom", waterlogged="false")
            elif dx == 4 or dz == 4:
                if dx > 1 and dz > 1:
                    b.set(x, 1, z, "minecraft:water")

    # 3-tier stepped stone pedestal at center (y=2..4)
    b.fill(cx - 3, cx + 3, 2, 2, cz - 3, cz + 3, "brick", C_STONE_MID, ROLE_FIXED)
    for d in (-3, 3):
        for k in range(-2, 3):
            b.set(cx + d, 2, cz + k, "brick_stairs", C_STONE_LIGHT, ROLE_FIXED,
                  facing="west" if d > 0 else "east", half="bottom", shape="straight", waterlogged="false")
            b.set(cx + k, 2, cz + d, "brick_stairs", C_STONE_LIGHT, ROLE_FIXED,
                  facing="north" if d > 0 else "south", half="bottom", shape="straight", waterlogged="false")
    b.fill(cx - 2, cx + 2, 3, 3, cz - 2, cz + 2, "brick", C_STONE_LIGHT, ROLE_FIXED)
    b.fill(cx - 1, cx + 1, 4, 4, cz - 1, cz + 1, "brick", C_STONE_DARK, ROLE_FIXED)

    # Sculpted Coiling Dragon God Statue (y=5..13)
    # Coiling tail & lower body
    b.fill(cx - 1, cx + 1, 5, 5, cz - 1, cz + 1, "brick", DRAGON_STONE, ROLE_FIXED)
    b.set(cx - 2, 5, cz + 1, "brick_slab", DRAGON_STONE, ROLE_FIXED, type="bottom", waterlogged="false")
    b.set(cx + 2, 5, cz - 1, "brick_slab", DRAGON_LIGHT, ROLE_FIXED, type="top", waterlogged="false")
    # Rising sinuous torso
    b.set(cx, 6, cz, "brick", DRAGON_STONE, ROLE_FIXED)
    b.set(cx + 1, 6, cz, "brick", DRAGON_LIGHT, ROLE_FIXED)
    b.set(cx + 1, 7, cz - 1, "brick", DRAGON_STONE, ROLE_FIXED)
    b.set(cx, 7, cz - 1, "stone_wall", DRAGON_LIGHT, ROLE_FIXED, up="true", waterlogged="false")
    b.set(cx - 1, 8, cz - 1, "brick", DRAGON_STONE, ROLE_FIXED)
    b.set(cx - 1, 8, cz, "brick", DRAGON_LIGHT, ROLE_FIXED)
    b.set(cx - 1, 9, cz, "brick", DRAGON_STONE, ROLE_FIXED)
    b.set(cx, 10, cz, "brick", DRAGON_STONE, ROLE_FIXED)
    # Dragon neck, whiskers & proud raised head facing South (+Z)
    b.set(cx, 11, cz, "brick", DRAGON_LIGHT, ROLE_FIXED)
    b.set(cx, 12, cz, "brick", DRAGON_STONE, ROLE_FIXED)
    b.set(cx, 12, cz + 1, "brick", DRAGON_LIGHT, ROLE_FIXED)
    b.set(cx, 12, cz + 2, "brick_slab", DRAGON_STONE, ROLE_FIXED, type="top", waterlogged="false")
    # Dragon horns & glowing jade eyes
    b.set(cx - 1, 13, cz - 1, "stone_wall", C_GOLD_ACCENT, ROLE_FIXED, up="true", waterlogged="false")
    b.set(cx + 1, 13, cz - 1, "stone_wall", C_GOLD_ACCENT, ROLE_FIXED, up="true", waterlogged="false")
    b.set(cx, 11, cz + 1, "lamp", DRAGON_EYE, ROLE_FIXED)  # Dragon pearl / glowing eye core

    # 4 corner grand stone lanterns + stone resting benches
    for sx_i in (cx - 8, cx + 8):
        for sz_i in (cz - 8, cz + 8):
            b.stone_lantern(sx_i, 2, sz_i, C_LAMP_GOLD)
    for bx in (cx - 10, cx + 10):
        b.fill(bx, bx, 2, 2, cz - 5, cz - 3, "brick_slab", C_STONE_LIGHT, ROLE_FIXED, type="bottom", waterlogged="false")
        b.fill(bx, bx, 2, 2, cz + 3, cz + 5, "brick_slab", C_STONE_LIGHT, ROLE_FIXED, type="bottom", waterlogged="false")
    return b


def build_gate_east() -> VillageBuilder:
    sx, sy, sz = 14, 12, 19
    b = VillageBuilder(sx, sy, sz)
    # Road paving along X at z=7..11
    b.fill(0, sx - 1, 0, 0, 7, 11, "tile", C_STONE_MID, ROLE_FIXED)
    # Grand Kabukimon gate straddling z=6..12 at x=4..6
    for pz in (6, 12):
        b.fill(4, 6, 0, 1, pz, pz, "brick", C_STONE_DARK, ROLE_FIXED)
        b.pillar(5, pz, 2, 7, "wood", WOOD_DARK, ROLE_FIXED)
        b.pillar(4, pz, 2, 5, "wood", WOOD_MAIN, ROLE_FIXED)
        b.pillar(6, pz, 2, 5, "wood", WOOD_MAIN, ROLE_FIXED)
    # Crossbeams & vermilion plaque
    b.fill(4, 6, 6, 7, 5, 13, "wood", WOOD_DARK, ROLE_FIXED)
    b.set(6, 6, 9, "tile", C_GOLD_ACCENT, ROLE_FIXED)
    b.set(4, 6, 9, "tile", C_GOLD_ACCENT, ROLE_FIXED)
    b.hanging_lantern(5, 5, 7, C_LANTERN_WARM)
    b.hanging_lantern(5, 5, 11, C_LANTERN_WARM)
    # Pitched tile gate roof
    for z in range(4, 15):
        b.set(3, 8, z, "tile_stairs", ROOF_DARK, ROLE_FIXED, facing="east", half="bottom", shape="straight", waterlogged="false")
        b.set(7, 8, z, "tile_stairs", ROOF_DARK, ROLE_FIXED, facing="west", half="bottom", shape="straight", waterlogged="false")
        b.set(4, 9, z, "tile_stairs", ROOF_DARK, ROLE_FIXED, facing="east", half="bottom", shape="straight", waterlogged="false")
        b.set(6, 9, z, "tile_stairs", ROOF_DARK, ROLE_FIXED, facing="west", half="bottom", shape="straight", waterlogged="false")
        b.set(5, 9, z, "tile", ROOF_RIDGE, ROLE_FIXED)
        b.set(5, 10, z, "tile_slab", ROOF_RIDGE, ROLE_FIXED, type="bottom", waterlogged="false")

    # Kosatsuba (Official Village Noticeboard) on North shoulder (x=8..12, z=1..4)
    b.fill(7, 13, 0, 0, 1, 6, "tile", C_STONE_LIGHT, ROLE_FIXED)
    b.pillar(8, 2, 1, 4, "wood", WOOD_DARK, ROLE_FIXED)
    b.pillar(12, 2, 1, 4, "wood", WOOD_DARK, ROLE_FIXED)
    b.fill(9, 11, 2, 3, 2, 2, "wood", WOOD_LIGHT, ROLE_FIXED)  # Noticeboard panels
    b.fill(8, 12, 1, 1, 3, 3, "wood_fence", WOOD_MAIN, ROLE_FIXED, east="true", west="true", north="false", south="false", waterlogged="false")
    b.gable_roof_x(8, 12, 2, 3, base_y=5, overhang_x=1, overhang_z=1,
                   main_color=ROOF_DARK, ridge_color=ROOF_RIDGE, main_role=ROLE_FIXED, ridge_role=ROLE_FIXED)
    b.stone_lantern(13, 1, 5, C_LAMP_GOLD)
    return b


def build_gate_simple(ns_oriented: bool) -> VillageBuilder:
    if ns_oriented:
        sx, sy, sz = 15, 11, 10
        b = VillageBuilder(sx, sy, sz)
        b.fill(5, 9, 0, 0, 0, sz - 1, "tile", C_STONE_MID, ROLE_FIXED)
        for px in (4, 10):
            b.pillar(px, 5, 1, 6, "wood", WOOD_DARK, ROLE_FIXED)
        b.fill(3, 11, 6, 6, 4, 6, "wood", WOOD_DARK, ROLE_FIXED)
        b.hanging_lantern(5, 5, 5, C_LANTERN_WARM)
        b.hanging_lantern(9, 5, 5, C_LANTERN_WARM)
        b.gable_roof_x(3, 11, 4, 6, base_y=7, overhang_x=1, overhang_z=1,
                       main_color=ROOF_DARK, ridge_color=ROOF_RIDGE, main_role=ROLE_FIXED, ridge_role=ROLE_FIXED)
        b.stone_lantern(2, 1, 7, C_LAMP_GOLD)
        b.stone_lantern(12, 1, 7, C_LAMP_GOLD)
        return b
    else:
        sx, sy, sz = 10, 11, 15
        b = VillageBuilder(sx, sy, sz)
        b.fill(0, sx - 1, 0, 0, 5, 9, "tile", C_STONE_MID, ROLE_FIXED)
        for pz in (4, 10):
            b.pillar(5, pz, 1, 6, "wood", WOOD_DARK, ROLE_FIXED)
        b.fill(4, 6, 6, 6, 3, 11, "wood", WOOD_DARK, ROLE_FIXED)
        b.hanging_lantern(5, 5, 5, C_LANTERN_WARM)
        b.hanging_lantern(5, 5, 9, C_LANTERN_WARM)
        for z in range(2, 13):
            b.set(3, 7, z, "tile_stairs", ROOF_DARK, ROLE_FIXED, facing="east", half="bottom", shape="straight", waterlogged="false")
            b.set(7, 7, z, "tile_stairs", ROOF_DARK, ROLE_FIXED, facing="west", half="bottom", shape="straight", waterlogged="false")
            b.set(4, 8, z, "tile_stairs", ROOF_DARK, ROLE_FIXED, facing="east", half="bottom", shape="straight", waterlogged="false")
            b.set(6, 8, z, "tile_stairs", ROOF_DARK, ROLE_FIXED, facing="west", half="bottom", shape="straight", waterlogged="false")
            b.set(5, 8, z, "tile", ROOF_RIDGE, ROLE_FIXED)
            b.set(5, 9, z, "tile_slab", ROOF_RIDGE, ROLE_FIXED, type="bottom", waterlogged="false")
        b.stone_lantern(2, 1, 2, C_LAMP_GOLD)
        b.stone_lantern(2, 1, 12, C_LAMP_GOLD)
        return b


def build_watermill() -> VillageBuilder:
    sx, sy, sz = 12, 13, 12
    b = VillageBuilder(sx, sy, sz)
    # Stone water-level foundation
    b.fill(1, 8, 0, 1, 2, 9, "brick", C_STONE_DARK, ROLE_FIXED)
    b.fill(2, 7, 2, 2, 3, 8, "wood", WOOD_LIGHT, ROLE_FIXED)
    # Timber mill cottage (x=2..8, z=2..9, y=2..5)
    for y in range(2, 6):
        c = WOOD_MAIN if y <= 3 else 0xE6DAC6
        blk = "wood" if y <= 3 else "brick"
        for x in range(2, 9):
            b.set(x, y, 2, blk, c, ROLE_FIXED)
            b.set(x, y, 9, blk, c, ROLE_FIXED)
        for z in range(2, 10):
            b.set(2, y, z, blk, c, ROLE_FIXED)
            b.set(8, y, z, blk, c, ROLE_FIXED)
    for px in (2, 8):
        for pz in (2, 9):
            b.pillar(px, pz, 2, 5, "wood", WOOD_DARK, ROLE_FIXED)
    b.clear(5, 5, 2, 3, 2, 2)
    b.door(5, 2, 2, facing="north", color=WOOD_DARK, role=ROLE_FIXED)
    b.hanging_lantern(6, 4, 1, C_LANTERN_WARM)
    b.gable_roof_x(2, 8, 2, 9, base_y=6, overhang_x=1, overhang_z=1,
                   main_color=0x6E5B45, ridge_color=0x8A7358, main_role=ROLE_FIXED, ridge_role=ROLE_FIXED)
    # Wooden waterwheel on East side (x=9..10, z=4..7, y=1..5) dipping down to stream
    b.set(9, 3, 5, "wood", WOOD_DARK, ROLE_FIXED)
    b.set(9, 3, 6, "wood", WOOD_DARK, ROLE_FIXED)
    for wy, wz in ((1, 5), (1, 6), (2, 4), (2, 7), (3, 4), (3, 7), (4, 4), (4, 7), (5, 5), (5, 6)):
        b.set(10, wy, wz, "wood", WOOD_MAIN, ROLE_FIXED)
    for wy, wz in ((2, 5), (2, 6), (4, 5), (4, 6)):
        b.set(10, wy, wz, "wood_fence", WOOD_LIGHT, ROLE_FIXED, north="true", south="true", east="false", west="false", waterlogged="false")
    return b


def build_stall(curtain_color: int, roof_color: int, prop_type: str) -> VillageBuilder:
    sx, sy, sz = 5, 5, 4
    b = VillageBuilder(sx, sy, sz)
    b.fill(0, 4, 0, 0, 0, 3, "tile", C_STONE_MID, ROLE_FIXED)
    for px in (0, 4):
        b.pillar(px, 0, 1, 3, "wood", WOOD_DARK, ROLE_FIXED)
        b.pillar(px, 3, 1, 3, "wood", WOOD_DARK, ROLE_FIXED)
    # Counter & goods
    b.fill(1, 3, 1, 1, 2, 2, "wood", WOOD_LIGHT, ROLE_FIXED)
    if prop_type == "books":
        b.fill(1, 3, 1, 2, 0, 0, "bookshelf", C_BOOKSHELF, ROLE_FIXED)
    elif prop_type == "dango":
        b.set(1, 1, 3, "wood_slab", C_VERMILION, ROLE_FIXED, type="bottom", waterlogged="false")
        b.set(3, 1, 3, "wood_slab", C_VERMILION, ROLE_FIXED, type="bottom", waterlogged="false")
    # Hanging noren & slanted canopy
    b.noren(1, 3, 3, 3, 3, facing="south", color=curtain_color, role=ROLE_FIXED)
    b.fill(0, 4, 4, 4, 0, 1, "tile_slab", roof_color, ROLE_FIXED, type="top", waterlogged="false")
    b.fill(0, 4, 4, 4, 2, 3, "tile_slab", roof_color, ROLE_FIXED, type="bottom", waterlogged="false")
    b.hanging_lantern(0, 3, 2, C_LANTERN_WARM if prop_type != "dango" else C_LANTERN_RED)
    return b


def build_well() -> VillageBuilder:
    b = VillageBuilder(5, 6, 5)
    b.fill(0, 4, 0, 0, 0, 4, "tile", C_STONE_MID, ROLE_FIXED)
    b.fill(1, 3, 1, 1, 1, 3, "brick", C_STONE_DARK, ROLE_FIXED)
    b.set(2, 0, 2, "brick", C_STONE_DARK, ROLE_FIXED)
    b.set(2, 1, 2, "minecraft:water")
    b.pillar(1, 2, 2, 3, "wood", WOOD_DARK, ROLE_FIXED)
    b.pillar(3, 2, 2, 3, "wood", WOOD_DARK, ROLE_FIXED)
    b.set(2, 3, 2, "wood_fence", WOOD_MAIN, ROLE_FIXED, east="true", west="true", north="false", south="false", waterlogged="false")
    b.fill(0, 4, 4, 4, 1, 3, "tile_slab", ROOF_DARK, ROLE_FIXED, type="bottom", waterlogged="false")
    b.fill(0, 4, 4, 4, 2, 2, "tile", ROOF_RIDGE, ROLE_FIXED)
    return b


def build_jizo() -> VillageBuilder:
    b = VillageBuilder(3, 5, 3)
    b.fill(0, 2, 0, 0, 0, 2, "brick", C_STONE_MID, ROLE_FIXED)
    b.set(1, 1, 1, "brick_slab", C_STONE_DARK, ROLE_FIXED, type="bottom", waterlogged="false")
    b.set(1, 2, 1, "stone_wall", C_MOSS_STONE, ROLE_FIXED, up="true", waterlogged="false")
    b.set(0, 1, 1, "wood_fence", WOOD_DARK, ROLE_FIXED, north="false", south="false", east="false", west="false", waterlogged="false")
    b.set(2, 1, 1, "wood_fence", WOOD_DARK, ROLE_FIXED, north="false", south="false", east="false", west="false", waterlogged="false")
    b.set(0, 2, 1, "wood_fence", WOOD_DARK, ROLE_FIXED, north="false", south="false", east="false", west="false", waterlogged="false")
    b.set(2, 2, 1, "wood_fence", WOOD_DARK, ROLE_FIXED, north="false", south="false", east="false", west="false", waterlogged="false")
    b.fill(0, 2, 3, 3, 0, 2, "tile_slab", ROOF_DARK, ROLE_FIXED, type="bottom", waterlogged="false")
    b.set(1, 3, 1, "tile", ROOF_RIDGE, ROLE_FIXED)
    b.set(1, 1, 2, "lantern", C_LANTERN_WARM, ROLE_FIXED, hanging="false", waterlogged="false")
    return b


def build_rest_tree() -> VillageBuilder:
    b = VillageBuilder(7, 9, 7)
    b.fill(0, 6, 0, 0, 0, 6, "tile", C_STONE_LIGHT, ROLE_FIXED)
    b.fill(2, 4, 0, 0, 2, 4, "gensokyou:gensokyo_grass_block")
    # Low stone planter rim around cherry tree
    for k in range(1, 6):
        b.set(k, 1, 1, "brick_slab", C_STONE_DARK, ROLE_FIXED, type="bottom", waterlogged="false")
        b.set(k, 1, 5, "brick_slab", C_STONE_DARK, ROLE_FIXED, type="bottom", waterlogged="false")
        b.set(1, 1, k, "brick_slab", C_STONE_DARK, ROLE_FIXED, type="bottom", waterlogged="false")
        b.set(5, 1, k, "brick_slab", C_STONE_DARK, ROLE_FIXED, type="bottom", waterlogged="false")
    b.simple_tree(3, 1, 3, height=6, leaf_block="minecraft:cherry_leaves")
    b.stone_lantern(0, 1, 0, C_LAMP_GOLD)
    b.fill(2, 4, 1, 1, 6, 6, "wood_slab", WOOD_MAIN, ROLE_FIXED, type="bottom", waterlogged="false")
    return b


DECOR_BUILDERS = (
    ("hv_dragon_plaza", build_dragon_plaza),
    ("hv_gate_east", build_gate_east),
    ("hv_gate_west", lambda: build_gate_simple(ns_oriented=False)),
    ("hv_gate_north", lambda: build_gate_simple(ns_oriented=True)),
    ("hv_watermill", build_watermill),
    ("hv_stall_dango", lambda: build_stall(0xC14436, 0x4A5560, "dango")),
    ("hv_stall_fish", lambda: build_stall(0x2F5478, 0x566470, "fish")),
    ("hv_stall_umbrella", lambda: build_stall(0x6D4875, 0x4B5561, "umbrella")),
    ("hv_stall_books", lambda: build_stall(0x426E54, 0x4E5B66, "books")),
    ("hv_well", build_well),
    ("hv_jizo", build_jizo),
    ("hv_rest_tree", build_rest_tree),
)


def export_all(write: bool = True, render_preview: bool = True) -> None:
    thumbs: list[tuple[str, Image.Image]] = []
    for struct_id, fn in DECOR_BUILDERS:
        b = fn()
        export_building(struct_id, b, write=write, render_preview=(render_preview and struct_id in ("hv_dragon_plaza", "hv_gate_east", "hv_watermill")))
        if render_preview and struct_id != "hv_dragon_plaza":
            thumbs.append((struct_id, _render_single_iso(b, rotate_180=False, scale=7)))
        print(f"[ok] {struct_id}: {b.sx}x{b.sy}x{b.sz}, {len(b.cells)} blocks")

    if render_preview and thumbs:
        cols = 4
        rows = (len(thumbs) + cols - 1) // cols
        cell_w = max(im.width for _, im in thumbs) + 16
        cell_h = max(im.height for _, im in thumbs) + 32
        sheet = Image.new("RGBA", (cols * cell_w + 16, rows * cell_h + 44), (232, 236, 242, 255))
        draw = ImageDraw.Draw(sheet)
        draw.text((16, 12), "Human Village Non-Functional Decor Landmarks", fill=(35, 38, 46, 255))
        for idx, (label, im) in enumerate(thumbs):
            r, c = divmod(idx, cols)
            x = 8 + c * cell_w + (cell_w - im.width) // 2
            y = 40 + r * cell_h + (cell_h - 22 - im.height) // 2
            sheet.paste(im, (x, y))
            draw.text((8 + c * cell_w + 8, 40 + r * cell_h + cell_h - 20), label, fill=(45, 50, 58, 255))
        PREVIEWS.mkdir(parents=True, exist_ok=True)
        sheet.save(PREVIEWS / "hv_decor_catalog.png")
        print(f"[ok] decor catalog -> {PREVIEWS / 'hv_decor_catalog.png'}")


if __name__ == "__main__":
    export_all()
