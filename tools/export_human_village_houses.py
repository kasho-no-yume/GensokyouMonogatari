"""Parametric Japanese Machiya Base engine -> 10 ordinary NPC house variants (hv_machiya_v01..v10).

All 10 variants are generated from a single build_machiya_base(MachiyaSpec) engine with
PaletteRole-tagged GLC3 color packs, supporting 8 runtime palettes x 4 rotations = 320 looks.
Unrotated (NONE) entrance always faces South (+Z), door anchor at (sx // 2, 1, sz).
"""
from __future__ import annotations

from dataclasses import dataclass
from PIL import Image, ImageDraw

from export_human_village_common import (
    C_BOOKSHELF,
    C_LANTERN_WARM,
    C_SHOJI_PAPER,
    C_STONE_MID,
    PALETTES,
    PREVIEWS,
    ROLE_ACCENT_CURTAIN,
    ROLE_FIXED,
    ROLE_FOUNDATION_STONE,
    ROLE_ROOF_MAIN,
    ROLE_ROOF_RIDGE,
    ROLE_WALL_PLASTER,
    ROLE_WOOD_DARK,
    ROLE_WOOD_FRAME,
    VillageBuilder,
    _render_single_iso,
    export_building,
)


@dataclass(frozen=True)
class MachiyaSpec:
    struct_id: str
    sx: int
    sy: int
    sz: int
    # Floor layout: "hiraya" (1F), "zushi" (1.5F), "hon2f" (full 2F), "kura" (storehouse)
    tier: str
    # Facade type: "residence", "shop", "teahouse", "workshop", "kura", "corner", "farmhouse"
    facade: str
    # Optional side/rear annex: None, "garden", "courtyard", "shed", "crop_plot"
    annex: str | None = None
    # Default preview palette index (0..7)
    preview_palette: int = 0


SPECS: tuple[MachiyaSpec, ...] = (
    MachiyaSpec("hv_machiya_v01", 9, 12, 10, "hiraya", "residence", None, preview_palette=0),
    MachiyaSpec("hv_machiya_v02", 11, 12, 10, "hiraya", "residence", "garden", preview_palette=2),
    MachiyaSpec("hv_machiya_v03", 9, 14, 11, "zushi", "shop", None, preview_palette=1),
    MachiyaSpec("hv_machiya_v04", 11, 14, 11, "zushi", "shop", None, preview_palette=5),
    MachiyaSpec("hv_machiya_v05", 11, 16, 12, "hon2f", "teahouse", None, preview_palette=4),
    MachiyaSpec("hv_machiya_v06", 13, 16, 12, "hon2f", "shop", None, preview_palette=6),
    MachiyaSpec("hv_machiya_v07", 9, 15, 11, "kura", "kura", None, preview_palette=6),
    MachiyaSpec("hv_machiya_v08", 11, 13, 12, "hiraya", "workshop", "shed", preview_palette=3),
    MachiyaSpec("hv_machiya_v09", 13, 15, 13, "zushi", "corner", None, preview_palette=0),
    MachiyaSpec("hv_machiya_v10", 11, 13, 13, "hiraya", "farmhouse", "crop_plot", preview_palette=7),
)


def build_machiya_base(spec: MachiyaSpec) -> VillageBuilder:
    theme = PALETTES[spec.preview_palette]
    b = VillageBuilder(spec.sx, spec.sy, spec.sz, theme=theme)

    # Compute main house body footprint inside (0..sx-1, 0..sz-1), leaving room for eaves & optional annex
    if spec.annex in ("garden", "shed", "crop_plot"):
        hx0, hx1 = 1, spec.sx - 4
        hz0, hz1 = 1, spec.sz - 3
    else:
        hx0, hx1 = 1, spec.sx - 2
        hz0, hz1 = 1, spec.sz - 3

    if spec.facade == "corner":
        hx0, hx1 = 2, spec.sx - 2
        hz0, hz1 = 2, spec.sz - 3

    door_x = spec.sx // 2
    door_x = max(hx0 + 2, min(hx1 - 2, door_x))

    # 1. Ground-level floor (y=0 flush with exterior street paving so players walk straight in!)
    b.fill(hx0, hx1, 0, 0, hz0, hz1, "wood", None, ROLE_WOOD_FRAME)
    b.fill(hx0, hx1, 0, 0, hz1 + 1, spec.sz - 1, "tile", C_STONE_MID, ROLE_FIXED)
    if spec.facade != "kura" and (hx1 - hx0) >= 6 and (hz1 - hz0) >= 6:
        b.tatami_floor(hx0 + 2, hx1 - 2, 0, hz0 + 2, hz1 - 3)
    # Stone foundation curb under perimeter walls (y=0)
    for x in range(hx0, hx1 + 1):
        b.set(x, 0, hz0, "brick", None, ROLE_FOUNDATION_STONE)
        b.set(x, 0, hz1, "brick", None, ROLE_FOUNDATION_STONE)
    for z in range(hz0, hz1 + 1):
        b.set(hx0, 0, z, "brick", None, ROLE_FOUNDATION_STONE)
        b.set(hx1, 0, z, "brick", None, ROLE_FOUNDATION_STONE)
    # Flush doorway threshold tile at y=0
    b.set(door_x, 0, hz1, "tile", C_STONE_MID, ROLE_FIXED)

    # 2. Ground floor walls (y=1..4)
    gf_top = 4
    wall_block = "brick" if spec.tier == "kura" else "brick"
    wall_role = ROLE_WALL_PLASTER

    # Solid perimeter base
    for y in range(1, gf_top + 1):
        for x in range(hx0, hx1 + 1):
            b.set(x, y, hz0, wall_block, None, wall_role)
            b.set(x, y, hz1, wall_block, None, wall_role)
        for z in range(hz0, hz1 + 1):
            b.set(hx0, y, z, wall_block, None, wall_role)
            b.set(hx1, y, z, wall_block, None, wall_role)

    # Timber wainscot (lower wooden siding on ground floor, except kura which is thick plaster)
    if spec.tier != "kura":
        for x in range(hx0 + 1, hx1):
            b.set(x, 1, hz0, "wood", None, ROLE_WOOD_FRAME)
            b.set(x, 2, hz0, "wood", None, ROLE_WOOD_FRAME)
            b.set(x, 1, hz1, "wood", None, ROLE_WOOD_FRAME)
        for z in range(hz0 + 1, hz1):
            b.set(hx0, 1, z, "wood", None, ROLE_WOOD_FRAME)
            b.set(hx0, 2, z, "wood", None, ROLE_WOOD_FRAME)
            b.set(hx1, 1, z, "wood", None, ROLE_WOOD_FRAME)
            b.set(hx1, 2, z, "wood", None, ROLE_WOOD_FRAME)

    # Corner & bay timber pillars + horizontal tie beam at y=gf_top
    for px in (hx0, hx1):
        for pz in (hz0, hz1):
            b.pillar(px, pz, 1, gf_top, "wood", None, ROLE_WOOD_DARK)
    for x in range(hx0, hx1 + 1):
        b.set(x, gf_top, hz0, "wood", None, ROLE_WOOD_DARK)
        b.set(x, gf_top, hz1, "wood", None, ROLE_WOOD_DARK)
    for z in range(hz0, hz1 + 1):
        b.set(hx0, gf_top, z, "wood", None, ROLE_WOOD_DARK)
        b.set(hx1, gf_top, z, "wood", None, ROLE_WOOD_DARK)

    # 3. Ground floor facade specialization (South face z = hz1)
    if spec.facade == "shop":
        # Open storefront with counter, noren curtains, interior shelves & hanging lanterns
        open_x0 = max(hx0 + 1, door_x - 2)
        open_x1 = min(hx1 - 1, door_x + 2)
        b.clear(open_x0, open_x1, 1, 3, hz1, hz1)
        # Noren curtain along top of shop opening (y=3)
        b.noren(open_x0, open_x1, 3, hz1, hz1, facing="south", role=ROLE_ACCENT_CURTAIN)
        # Display counter on one side, walk-in doorway on door_x
        for x in range(open_x0, open_x1 + 1):
            if abs(x - door_x) >= 1 and x != door_x:
                b.set(x, 1, hz1, "wood_slab", None, ROLE_WOOD_FRAME, type="top", waterlogged="false")
        # Back-wall shop shelves
        b.fill(hx0 + 2, hx1 - 2, 2, 3, hz0 + 1, hz0 + 1, "bookshelf", C_BOOKSHELF, ROLE_FIXED)
        b.hanging_lantern(hx0 + 1, 3, hz1 + 1, C_LANTERN_WARM)
        b.hanging_lantern(hx1 - 1, 3, hz1 + 1, C_LANTERN_WARM)

    elif spec.facade == "teahouse":
        # Wide warm opening + red-curtain noren + outside red tea benches
        open_x0 = hx0 + 2
        open_x1 = hx1 - 2
        b.clear(open_x0, open_x1, 1, 3, hz1, hz1)
        b.noren(open_x0, open_x1, 3, hz1, hz1, facing="south", role=ROLE_ACCENT_CURTAIN)
        # Outside tea bench with accent cloth on left & right of entrance
        b.set(hx0 + 1, 1, hz1 + 1, "wood_slab", None, ROLE_ACCENT_CURTAIN, type="bottom", waterlogged="false")
        b.set(hx0 + 2, 1, hz1 + 1, "wood_slab", None, ROLE_ACCENT_CURTAIN, type="bottom", waterlogged="false")
        b.set(hx1 - 1, 1, hz1 + 1, "wood_slab", None, ROLE_ACCENT_CURTAIN, type="bottom", waterlogged="false")
        # Interior tea counter
        b.fill(hx0 + 2, hx1 - 2, 1, 1, hz0 + 2, hz0 + 2, "wood_slab", None, ROLE_WOOD_DARK, type="top", waterlogged="false")
        b.hanging_lantern(hx0 + 1, 3, hz1 + 1, C_LANTERN_WARM)
        b.hanging_lantern(hx1 - 1, 3, hz1 + 1, C_LANTERN_WARM)

    elif spec.facade == "kura":
        # Thick black-plaster vault door & small barred vents (flush y=0 floor)
        b.fill(hx0, hx1, 1, 1, hz0, hz1, "tile", None, ROLE_ROOF_MAIN)
        b.clear(hx0 + 1, hx1 - 1, 1, 1, hz0 + 1, hz1 - 1)
        b.clear(door_x, door_x + 1, 1, 3, hz1, hz1)
        b.set(door_x, 0, hz1, "tile", C_STONE_MID, ROLE_FIXED)
        b.set(door_x + 1, 0, hz1, "tile", C_STONE_MID, ROLE_FIXED)
        b.double_door(door_x, 1, hz1, facing="south", axis="x", role=ROLE_WOOD_DARK)
        b.set(door_x, 3, hz1, "wood", None, ROLE_WOOD_DARK)
        b.set(door_x + 1, 3, hz1, "wood", None, ROLE_WOOD_DARK)
        b.hanging_lantern(door_x - 1, 3, hz1 + 1, C_LANTERN_WARM)

    elif spec.facade == "corner":
        # L-shaped dual-frontage shop/residence facing both South (+Z) and West (-X)
        b.clear(door_x - 1, door_x + 1, 1, 3, hz1, hz1)
        b.noren(door_x - 1, door_x + 1, 3, hz1, hz1, facing="south", role=ROLE_ACCENT_CURTAIN)
        # West frontage shoji & secondary noren
        mid_z = (hz0 + hz1) // 2
        b.clear(hx0, hx0, 2, 3, mid_z - 1, mid_z + 1)
        b.shoji_span(hx0, hx0, 2, 2, mid_z - 1, mid_z + 1, C_SHOJI_PAPER)
        b.noren(hx0, hx0, 3, mid_z - 1, mid_z + 1, facing="west", role=ROLE_ACCENT_CURTAIN)
        b.hanging_lantern(hx0 - 1, 3, hz1 + 1, C_LANTERN_WARM)
        b.hanging_lantern(hx1 - 1, 3, hz1 + 1, C_LANTERN_WARM)

    else:
        # Residence / workshop / farmhouse: wooden door + shoji lattice windows + inuyarai fence
        b.clear(door_x, door_x, 1, 2, hz1, hz1)
        b.door(door_x, 1, hz1, facing="south", role=ROLE_WOOD_DARK)
        b.noren(door_x, door_x, 3, hz1, hz1, facing="south", role=ROLE_ACCENT_CURTAIN)
        # Shoji windows flanking the door
        if door_x - 2 >= hx0 + 1:
            b.shoji_span(hx0 + 1, door_x - 2, 2, 3, hz1, hz1, C_SHOJI_PAPER)
            b.set(hx0 + 1, 1, hz1 + 1, "wood_fence", None, ROLE_WOOD_DARK,
                  north="true", south="false", east="false", west="false", waterlogged="false")
        if door_x + 2 <= hx1 - 1:
            b.shoji_span(door_x + 2, hx1 - 1, 2, 3, hz1, hz1, C_SHOJI_PAPER)
        b.hanging_lantern(door_x + 1, 3, hz1 + 1, C_LANTERN_WARM)

    # Side & rear windows
    mid_z = (hz0 + hz1) // 2
    if spec.tier != "kura" and spec.facade != "corner":
        b.shoji_span(hx0, hx0, 2, 2, mid_z - 1, mid_z + 1, C_SHOJI_PAPER)
        b.shoji_span(hx1, hx1, 2, 2, mid_z - 1, mid_z + 1, C_SHOJI_PAPER)

    # 4. Front hisashi (projecting lower eave canopy over ground floor facade, top-half so noren stays clearly visible)
    for x in range(hx0 - 1, hx1 + 2):
        b.set(x, gf_top, hz1 + 1, "tile_slab", None, ROLE_ROOF_MAIN, type="top", waterlogged="false")
    if spec.facade == "corner":
        for z in range(hz0 - 1, hz1 + 2):
            b.set(hx0 - 1, gf_top, z, "tile_slab", None, ROLE_ROOF_MAIN, type="top", waterlogged="false")

    # Interior ceiling & warm ambient lamp
    b.fill(hx0 + 1, hx1 - 1, gf_top, gf_top, hz0 + 1, hz1 - 1, "wood", None, ROLE_WOOD_FRAME)
    b.set((hx0 + hx1) // 2, gf_top, (hz0 + hz1) // 2, "lamp", C_LANTERN_WARM, ROLE_FIXED)

    # 5. Upper stories by tier
    if spec.tier == "hiraya":
        roof_base_y = gf_top + 1
    elif spec.tier == "zushi":
        # 1.5-story low mezzanine with mushiko-mado lattice windows
        u_top = gf_top + 2
        for y in range(gf_top + 1, u_top + 1):
            for x in range(hx0, hx1 + 1):
                b.set(x, y, hz0, "brick", None, ROLE_WALL_PLASTER)
                b.set(x, y, hz1, "brick", None, ROLE_WALL_PLASTER)
            for z in range(hz0, hz1 + 1):
                b.set(hx0, y, z, "brick", None, ROLE_WALL_PLASTER)
                b.set(hx1, y, z, "brick", None, ROLE_WALL_PLASTER)
        for px in (hx0, hx1):
            for pz in (hz0, hz1):
                b.pillar(px, pz, gf_top + 1, u_top, "wood", None, ROLE_WOOD_DARK)
        # Mushiko-mado (low grilled attic windows on front)
        b.shoji_span(hx0 + 2, hx1 - 2, gf_top + 1, gf_top + 1, hz1, hz1, C_SHOJI_PAPER)
        roof_base_y = u_top + 1
    elif spec.tier == "kura":
        # Tall thick-walled plaster upper storehouse with tiny iron-bar vents
        u_top = gf_top + 3
        for y in range(gf_top + 1, u_top + 1):
            for x in range(hx0, hx1 + 1):
                b.set(x, y, hz0, "brick", None, ROLE_WALL_PLASTER)
                b.set(x, y, hz1, "brick", None, ROLE_WALL_PLASTER)
            for z in range(hz0, hz1 + 1):
                b.set(hx0, y, z, "brick", None, ROLE_WALL_PLASTER)
                b.set(hx1, y, z, "brick", None, ROLE_WALL_PLASTER)
        # Recessed small shuttered upper window
        b.set((hx0 + hx1) // 2, gf_top + 2, hz1, "wood_trapdoor", None, ROLE_WOOD_DARK,
              facing="south", half="bottom", open="true", powered="false", waterlogged="false")
        roof_base_y = u_top + 1
    else:
        # "hon2f": full 2nd story with shoji windows + wooden balcony balustrade
        u_top = gf_top + 3
        for y in range(gf_top + 1, u_top + 1):
            for x in range(hx0, hx1 + 1):
                b.set(x, y, hz0, "brick", None, ROLE_WALL_PLASTER)
                b.set(x, y, hz1, "brick", None, ROLE_WALL_PLASTER)
            for z in range(hz0, hz1 + 1):
                b.set(hx0, y, z, "brick", None, ROLE_WALL_PLASTER)
                b.set(hx1, y, z, "brick", None, ROLE_WALL_PLASTER)
        for px in (hx0, hx1):
            for pz in (hz0, hz1):
                b.pillar(px, pz, gf_top + 1, u_top, "wood", None, ROLE_WOOD_DARK)
        # Upper front shoji band + wooden handrail on hisashi roof
        b.shoji_span(hx0 + 2, hx1 - 2, gf_top + 1, gf_top + 2, hz1, hz1, C_SHOJI_PAPER)
        for x in range(hx0 + 1, hx1):
            b.set(x, gf_top + 1, hz1 + 1, "wood_fence", None, ROLE_WOOD_DARK,
                  east="true" if x < hx1 - 1 else "false",
                  west="true" if x > hx0 + 1 else "false",
                  north="false", south="false", waterlogged="false")
        # Top tie beam
        for x in range(hx0, hx1 + 1):
            b.set(x, u_top, hz1, "wood", None, ROLE_WOOD_DARK)
        roof_base_y = u_top + 1

    # 6. Kirizuma pitched tile roof
    b.gable_roof_x(
        hx0, hx1, hz0, hz1,
        base_y=roof_base_y,
        overhang_x=1, overhang_z=1,
        main_role=ROLE_ROOF_MAIN,
        ridge_role=ROLE_ROOF_RIDGE,
        gable_fill_role=ROLE_WALL_PLASTER,
        gable_trim_role=ROLE_WOOD_DARK,
    )

    # 7. Optional side annex (East side x = hx1+1 .. sx-1)
    ax0, ax1 = hx1 + 1, spec.sx - 1
    if spec.annex == "garden":
        # Walled mini courtyard with stone wall, stepping stones & cherry sapling/bush
        b.fill(ax0, ax1, 0, 0, hz0 + 1, hz1, "gensokyou:gensokyo_grass_block")
        for z in range(hz0 + 1, hz1 + 1):
            b.set(ax1, 1, z, "stone_wall", None, ROLE_FOUNDATION_STONE, up="true", waterlogged="false")
        b.set(ax0, 1, hz1, "wood_fence_gate", None, ROLE_WOOD_DARK,
              facing="south", open="false", in_wall="false", powered="false")
        b.set(ax1, 1, hz1, "stone_wall", None, ROLE_FOUNDATION_STONE, up="true", waterlogged="false")
        b.set(ax0 + 1, 1, (hz0 + hz1) // 2, "minecraft:cherry_leaves", None, ROLE_FIXED, persistent="true", distance="1")
        b.set(ax0 + 1, 2, (hz0 + hz1) // 2, "minecraft:cherry_leaves", None, ROLE_FIXED, persistent="true", distance="1")

    elif spec.annex == "shed":
        # Open timber workshop lean-to with stacked lumber & drying rack
        b.fill(ax0, ax1, 0, 0, hz0 + 1, hz1, "brick", None, ROLE_FOUNDATION_STONE)
        b.pillar(ax1, hz0 + 1, 1, 3, "wood", None, ROLE_WOOD_DARK)
        b.pillar(ax1, hz1, 1, 3, "wood", None, ROLE_WOOD_DARK)
        for z in range(hz0 + 1, hz1 + 1):
            b.set(ax0, 4, z, "wood_slab", None, ROLE_WOOD_FRAME, type="bottom", waterlogged="false")
            b.set(ax1, 3, z, "wood_slab", None, ROLE_WOOD_FRAME, type="top", waterlogged="false")
        # Stacked timber & dye cloth
        b.fill(ax0, ax0, 1, 2, hz0 + 2, hz0 + 3, "wood", None, ROLE_WOOD_FRAME)
        b.noren(ax1, ax1, 2, hz0 + 2, hz1 - 1, facing="east", role=ROLE_ACCENT_CURTAIN)

    elif spec.annex == "crop_plot":
        # Suburban spirit-soil vegetable patch + low wooden fence
        b.fill(ax0, ax1, 0, 0, hz0 + 1, hz1, "gensokyou:spirit_soil")
        for z in range(hz0 + 1, hz1 + 1):
            b.set(ax1, 1, z, "wood_fence", None, ROLE_WOOD_FRAME,
                  north="true" if z > hz0 + 1 else "false",
                  south="true" if z < hz1 else "false",
                  east="false", west="false", waterlogged="false")
            if z % 2 == 0:
                b.set(ax0, 1, z, "gensokyou:spirit_herb")
        b.set(ax0, 1, hz1, "wood_fence_gate", None, ROLE_WOOD_FRAME,
              facing="south", open="false", in_wall="false", powered="false")

    return b


def export_all(write: bool = True, render_preview: bool = True) -> None:
    thumbs: list[tuple[str, Image.Image]] = []
    for spec in SPECS:
        b = build_machiya_base(spec)
        export_building(spec.struct_id, b, write=write, render_preview=render_preview)
        if render_preview:
            thumb = _render_single_iso(b, rotate_180=False, scale=7)
            thumbs.append((f"{spec.struct_id} ({spec.tier}/{spec.facade})", thumb))
        print(f"[ok] {spec.struct_id}: {b.sx}x{b.sy}x{b.sz}, {len(b.cells)} blocks")

    if render_preview and thumbs:
        cols = 5
        rows = (len(thumbs) + cols - 1) // cols
        cell_w = max(im.width for _, im in thumbs) + 16
        cell_h = max(im.height for _, im in thumbs) + 34
        sheet = Image.new("RGBA", (cols * cell_w + 16, rows * cell_h + 44), (232, 236, 242, 255))
        draw = ImageDraw.Draw(sheet)
        draw.text((16, 12), "Human Village Machiya Base -> 10 Parametric Variants (8 Runtime Palettes)", fill=(35, 38, 46, 255))
        for idx, (label, im) in enumerate(thumbs):
            r, c = divmod(idx, cols)
            x = 8 + c * cell_w + (cell_w - im.width) // 2
            y = 40 + r * cell_h + (cell_h - 24 - im.height) // 2
            sheet.paste(im, (x, y))
            draw.text((8 + c * cell_w + 8, 40 + r * cell_h + cell_h - 22), label, fill=(45, 50, 58, 255))
        PREVIEWS.mkdir(parents=True, exist_ok=True)
        sheet.save(PREVIEWS / "hv_machiya_catalog.png")
        print(f"[ok] catalog preview -> {PREVIEWS / 'hv_machiya_catalog.png'}")


if __name__ == "__main__":
    export_all()
