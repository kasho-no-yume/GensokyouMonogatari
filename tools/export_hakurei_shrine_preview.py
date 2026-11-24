"""Export the grand Hakurei Shrine preview landmark (structure NBT + GLC2 colors + extra gray models)."""
import argparse
import gzip
import hashlib
import json
import math
import struct
from pathlib import Path
from zipfile import ZipFile

ROOT = Path(__file__).resolve().parents[1]
RESOURCES = ROOT / "src/main/resources"
ASSETS = Path("assets/gensokyou")
LANDMARK_ID = "hakurei_shrine_preview"
GENSOKYO_COLOR_ID = "hakurei_shrine_gensokyo"
ORIGIN = (128, 64, 128)
GENSOKYO_ORIGIN = (3022, 83, 93)
SIZE = (95, 38, 71)  # X: West->East (95), Y: Height (38), Z: North->South (71)
CZ = 35              # Main east-west symmetry axis
COURT_Y = 12         # Main courtyard surface level (world Y=76 in preview, Y=96 in Gensokyo)

# ---------------------------------------------------------------------------
# Curated Traditional Japanese Shrine Palette (Daiqing Tile + Hinoki + Vermilion + Warm Lamps)
# ---------------------------------------------------------------------------
TILE_DARK = 0x262E3A
TILE_BASE = 0x2F3947
TILE_MID = 0x384556
TILE_LIGHT = 0x435266
TILE_RIDGE = 0x1E2530
TILE_MOSS = 0x3E4E45

GOLD_TRIM = 0xB8964B
GOLD_DARK = 0x8A6E35
CHIGI_WHITE = 0xECE6D8

VERMILION_SUN = 0xC84030
VERMILION_BASE = 0xB53527
VERMILION_SHADE = 0x96291E
VERMILION_DARK = 0x7A2018

WOOD_PALE = 0xB8936A
WOOD_BASE = 0xA27C54
WOOD_AGED = 0x8D6945
WOOD_BEAM = 0x664830
WOOD_DARK = 0x4B3422
DOOR_WOOD = 0x7A563A

PLASTER_BRIGHT = 0xEFECE3
PLASTER_BASE = 0xE3DEC9
PLASTER_SHADE = 0xCFC8B4

STONE_LIGHT = 0x9D9A92
STONE_BASE = 0x85827A
STONE_DARK = 0x6B6861
STONE_WET = 0x54544F
STONE_MOSS = 0x6C7660
GRAVEL_LIGHT = 0xCAC4B6
GRAVEL_BASE = 0xB8B1A2
SAND_KARESANSUI = 0xDDD8CB
SAND_WAVE = 0xCBC5B6

TATAMI_MAIN = 0xC4BC7E
TATAMI_ALT = 0xB6AE70
TATAMI_EDGE = 0x485838
SHOJI_PAPER = 0xF4EFE2

LACQUER_BLACK = 0x1F1D1C
LACQUER_EDGE = 0x38322E

# Unbreakable tinted lamp glow colors (light level 15)
LAMP_WARM = 0xFFE4AE
LAMP_AMBER = 0xFFC570
LAMP_SACRED = 0xFFF5D8
LAMP_VERMILION = 0xFFA27E

TILE_GRADIENT = (TILE_DARK, TILE_BASE, TILE_MID, TILE_LIGHT)
STONE_GRADIENT = (STONE_DARK, STONE_BASE, STONE_LIGHT, STONE_MOSS)
WOOD_GRADIENT = (WOOD_AGED, WOOD_BASE, WOOD_PALE)


def det_hash(x, y, z, salt=0):
    raw = struct.pack(">iiii", x, y, z, salt)
    return int.from_bytes(hashlib.blake2b(raw, digest_size=4).digest(), "big")


def pick(palette, x, y, z, salt=0):
    return palette[det_hash(x, y, z, salt) % len(palette)]


class Builder:
    def __init__(self, size):
        self.sx, self.sy, self.sz = size
        self.cells = {}

    def in_bounds(self, x, y, z):
        return 0 <= x < self.sx and 0 <= y < self.sy and 0 <= z < self.sz

    def set(self, x, y, z, block, color=None, **props):
        if not self.in_bounds(x, y, z):
            return
        if ":" in block:
            color = None
        self.cells[(x, y, z)] = (block, color, tuple(sorted((k, str(v)) for k, v in props.items())))

    def clear(self, x, y, z):
        self.cells.pop((x, y, z), None)

    def clear_box(self, x0, y0, z0, x1, y1, z1):
        for x in range(x0, x1 + 1):
            for y in range(y0, y1 + 1):
                for z in range(z0, z1 + 1):
                    self.cells.pop((x, y, z), None)

    def fill(self, x0, y0, z0, x1, y1, z1, block, color=None, **props):
        for x in range(x0, x1 + 1):
            for y in range(y0, y1 + 1):
                for z in range(z0, z1 + 1):
                    c = color(x, y, z) if callable(color) else color
                    self.set(x, y, z, block, c, **props)

    def door(self, x, y, z, facing, hinge="left", color=DOOR_WOOD):
        self.set(x, y, z, "wood_door", color, facing=facing, half="lower", hinge=hinge, open="false", powered="false")
        self.set(x, y + 1, z, "wood_door", color, facing=facing, half="upper", hinge=hinge, open="false", powered="false")

    def trapdoor(self, x, y, z, facing, color, half="bottom", is_open=False):
        self.set(
            x, y, z, "wood_trapdoor", color,
            facing=facing, half=half, open="true" if is_open else "false",
            powered="false", waterlogged="false",
        )

    def fence(self, x, y, z, color):
        self.set(x, y, z, "wood_fence", color, waterlogged="false")

    def wall(self, x, y, z, color):
        self.set(x, y, z, "stone_wall", color, up="true", waterlogged="false")

    def lamp(self, x, y, z, color=LAMP_WARM):
        """Unbreakable tinted washi lamp block (light level 15)."""
        self.set(x, y, z, "lamp", color)

    def stairs(self, x, y, z, facing, color, half="bottom", shape="straight"):
        self.set(x, y, z, "tile_stairs", color, facing=facing, half=half, shape=shape, waterlogged="false")

    def slab(self, x, y, z, color, slab_type="bottom"):
        self.set(x, y, z, "tile_slab", color, type=slab_type, waterlogged="false")

    def stone_lantern(self, x, y, z, glow=LAMP_AMBER):
        """100% unbreakable tinted Japanese Toro stone lantern (4 blocks tall)."""
        self.set(x, y, z, "brick", STONE_DARK)
        self.wall(x, y + 1, z, STONE_BASE)
        self.lamp(x, y + 2, z, glow)
        self.slab(x, y + 3, z, TILE_BASE, "bottom")

    def low_lantern(self, x, y, z, glow=LAMP_WARM):
        """Compact 2-block courtyard / porch stone lantern."""
        self.wall(x, y, z, STONE_DARK)
        self.lamp(x, y + 1, z, glow)
        self.slab(x, y + 2, z, TILE_BASE, "bottom")


# ---------------------------------------------------------------------------
# Color helpers with subtle weathering
# ---------------------------------------------------------------------------
def roof_color(x, y, z, eave=False):
    h = det_hash(x, y, z, 11)
    if eave and (h % 9 == 0):
        return TILE_MOSS
    idx = (h % 3) + (1 if (x + z) % 2 == 0 else 0)
    return TILE_GRADIENT[min(idx, len(TILE_GRADIENT) - 1)]


def stone_wall_color(x, y, z):
    h = det_hash(x, y, z, 23)
    if y <= 2 and h % 3 == 0:
        return STONE_WET
    if h % 7 == 0:
        return STONE_MOSS
    return STONE_GRADIENT[h % 3]


def wood_color(x, y, z):
    return pick(WOOD_GRADIENT, x, y, z, 37)


def vermilion_color(x, y, z):
    h = det_hash(x, y, z, 53)
    if h % 5 == 0:
        return VERMILION_SUN
    if h % 5 == 1:
        return VERMILION_SHADE
    return VERMILION_BASE


def plaster_color(x, y, z):
    h = det_hash(x, y, z, 71)
    if h % 6 == 0:
        return PLASTER_BRIGHT
    if h % 6 == 1:
        return PLASTER_SHADE
    return PLASTER_BASE


# ---------------------------------------------------------------------------
# 1. Sculpted Hillside Terrain, Retaining Stone Walls (Ishigaki) & Grand Stairs
# ---------------------------------------------------------------------------
def terrain_surface_y(x, z):
    if 34 <= x <= 86 and 6 <= z <= 64:
        return COURT_Y
    if x > 86:
        edge_drop = 0.0
        if z < 8:
            edge_drop = (8 - z) * 1.1
        elif z > 62:
            edge_drop = (z - 62) * 1.1
        rise = min(5.0, (x - 86) * 0.65)
        undul = 1.2 * math.sin(z * 0.18) + 0.7 * math.cos(x * 0.3)
        return max(1, int(round(COURT_Y + rise + undul - edge_drop)))
    if x < 34:
        t = max(0.0, min(1.0, (x - 4) / 30.0))
        smooth = t * t * (3.0 - 2.0 * t)
        base = COURT_Y * smooth
        if abs(z - CZ) <= 7:
            return int(round(base))
        z_dist = max(0, abs(z - CZ) - 24)
        shoulder = max(0.0, 1.0 - z_dist / 12.0)
        undul = 0.9 * math.sin(x * 0.22 + z * 0.15)
        return max(0, int(round(base * (0.45 + 0.55 * shoulder) + undul * t)))
    z_out = (6 - z) if z < 6 else (z - 64)
    fall = z_out * 1.9
    return max(0, int(round(COURT_Y - fall)))


def build_terrain(b: Builder):
    for x in range(b.sx):
        for z in range(b.sz):
            top = terrain_surface_y(x, z)
            for y in range(0, top + 1):
                if y == top:
                    b.set(x, y, z, "gensokyou:gensokyo_grass_block")
                elif y >= top - 2:
                    b.set(x, y, z, "gensokyou:gensokyo_dirt")
                else:
                    b.set(x, y, z, "gensokyou:gensokyo_stone")

    # Sculpted Ishigaki retaining stone walls
    for z in range(6, 65):
        if abs(z - CZ) <= 6:
            continue
        for y in range(0, COURT_Y + 1):
            if y < COURT_Y:
                b.set(33, y, z, "brick", stone_wall_color(33, y, z))
            else:
                b.set(33, y, z, "tile", STONE_LIGHT)
    for x in range(34, 87):
        for z in (6, 64):
            for y in range(0, COURT_Y + 1):
                b.set(x, y, z, "brick", stone_wall_color(x, y, z))
            b.slab(x, COURT_Y + 1, z, STONE_BASE, "bottom")

    # Pale stone gravel courtyard ("jari")
    for x in range(36, 85):
        for z in range(8, 63):
            if 41 <= x <= 59 and 46 <= z <= 62:
                continue
            if x >= 76 and (z <= 23 or z >= 47):
                continue
            c = GRAVEL_LIGHT if (det_hash(x, 0, z, 91) % 3 == 0) else GRAVEL_BASE
            b.set(x, COURT_Y, z, "brick", c)

    # Central stone flagstone path (Sando) from X=36 to Haiden steps X=57
    for x in range(36, 58):
        for z in range(CZ - 2, CZ + 3):
            is_border = abs(z - CZ) == 2
            c = STONE_DARK if is_border else (STONE_LIGHT if (x + z) % 2 == 0 else STONE_BASE)
            b.set(x, COURT_Y, z, "tile", c)

    # Ceremonial Toro Lanterns lining the courtyard Sando for bright evening ambiance
    for lx in (42, 48, 54):
        b.stone_lantern(lx, COURT_Y + 1, CZ - 4, glow=LAMP_AMBER)
        b.stone_lantern(lx, COURT_Y + 1, CZ + 4, glow=LAMP_AMBER)

    # Branch flagstone paths to North & South wings
    for z in range(21, CZ - 2):
        for x in (45, 46, 50, 51):
            b.set(x, COURT_Y, z, "tile", STONE_BASE if (x + z) % 2 == 0 else STONE_LIGHT)
    for z in range(CZ + 3, 50):
        for x in (52, 53):
            b.set(x, COURT_Y, z, "tile", STONE_BASE if (x + z) % 2 == 0 else STONE_LIGHT)

    # West approach arrival apron
    for x in range(0, 8):
        for z in range(CZ - 6, CZ + 7):
            c = STONE_LIGHT if abs(z - CZ) <= 3 else GRAVEL_BASE
            b.set(x, 0, z, "tile" if abs(z - CZ) <= 3 else "brick", c)


def build_grand_staircase(b: Builder):
    """3-flight ceremonial stone staircase rising 12 blocks (X=8..35, Y=0->12)."""
    profile = {}
    for i in range(4):
        profile[8 + i] = 1 + i
    for x in range(12, 16):
        profile[x] = 4
    for i in range(4):
        profile[16 + i] = 5 + i
    for x in range(20, 24):
        profile[x] = 8
    for i in range(4):
        profile[24 + i] = 9 + i
    for x in range(28, 36):
        profile[x] = 12

    stair_xs = set(range(8, 12)) | set(range(16, 20)) | set(range(24, 28))

    for x, y_top in profile.items():
        b.clear_box(x, y_top + 1, CZ - 6, x, y_top + 12, CZ + 6)
        for z in range(CZ - 5, CZ + 6):
            for y in range(0, y_top):
                b.set(x, y, z, "brick", stone_wall_color(x, y, z))
        for z in range(CZ - 4, CZ + 5):
            if x in stair_xs:
                b.stairs(x, y_top, z, "east", STONE_LIGHT if abs(z - CZ) <= 2 else STONE_BASE)
            else:
                c = STONE_LIGHT if (x + z) % 2 == 0 else STONE_BASE
                b.set(x, y_top, z, "tile", c)
        for z in (CZ - 5, CZ + 5):
            b.set(x, y_top, z, "brick", STONE_DARK)
            b.set(x, y_top + 1, z, "brick", stone_wall_color(x, y_top + 1, z))
            b.slab(x, y_top + 2, z, STONE_LIGHT, "bottom")

    # 8 Unbreakable Toro Stone Lanterns along flights, landings and summit
    for lx, ly in ((8, 3), (13, 6), (21, 10), (31, 14)):
        b.stone_lantern(lx, ly, CZ - 5, glow=LAMP_AMBER)
        b.stone_lantern(lx, ly, CZ + 5, glow=LAMP_AMBER)


# ---------------------------------------------------------------------------
# 2. Myojin Torii Gates (First & Second Vermilion Torii with Warm Night Lamps)
# ---------------------------------------------------------------------------
def build_torii(b: Builder, x, base_y, half_span=5, pillar_h=10):
    z_left = CZ - half_span
    z_right = CZ + half_span

    for zp in (z_left, z_right):
        for dx in (-1, 0, 1):
            for dz in (-1, 0, 1):
                if abs(dx) + abs(dz) <= 1:
                    b.set(x + dx, base_y, zp + dz, "brick", STONE_WET)
        b.set(x, base_y + 1, zp, "brick", STONE_DARK)
        for y in range(base_y + 2, base_y + pillar_h):
            b.set(x, y, zp, "wood", vermilion_color(x, y, zp))

    # Flanking stone lanterns beside both Torii pillars
    b.stone_lantern(x - 1, base_y, z_left - 2, glow=LAMP_AMBER)
    b.stone_lantern(x - 1, base_y, z_right + 2, glow=LAMP_AMBER)

    nuki_y = base_y + pillar_h - 3
    for z in range(z_left - 2, z_right + 3):
        b.set(x, nuki_y, z, "wood", VERMILION_BASE)
    b.set(x, nuki_y, z_left - 2, "wood", LACQUER_BLACK)
    b.set(x, nuki_y, z_right + 2, "wood", LACQUER_BLACK)
    # Suspended warm paper lamps under the Nuki beam
    b.lamp(x, nuki_y - 1, z_left - 1, LAMP_VERMILION)
    b.lamp(x, nuki_y - 1, z_right + 1, LAMP_VERMILION)

    shimaki_y = base_y + pillar_h - 1
    for z in range(z_left - 3, z_right + 4):
        b.set(x, shimaki_y, z, "wood", VERMILION_SUN)

    kasagi_y = base_y + pillar_h
    for z in range(z_left - 3, z_right + 4):
        b.set(x, kasagi_y, z, "tile", TILE_RIDGE)
    for z_tip, facing in ((z_left - 4, "south"), (z_right + 4, "north")):
        b.stairs(x, shimaki_y, z_tip, facing, VERMILION_BASE, half="top")
        b.set(x, kasagi_y, z_tip, "tile", TILE_RIDGE)
        b.slab(x, kasagi_y + 1, z_tip, TILE_RIDGE, "bottom")
    b.slab(x, kasagi_y + 1, z_left - 3, TILE_RIDGE, "bottom")
    b.slab(x, kasagi_y + 1, z_right + 3, TILE_RIDGE, "bottom")

    for y in range(nuki_y + 1, shimaki_y):
        b.set(x, y, CZ, "wood", LACQUER_BLACK)
        b.set(x - 1, y, CZ, "wood", GOLD_TRIM if y == nuki_y + 1 else LACQUER_BLACK)


# ---------------------------------------------------------------------------
# 3. Reusable Irimoya (Hip-and-Gable) Roof with Upturned Eaves, Chigi & Katsuogi
# ---------------------------------------------------------------------------
def build_irimoya_roof(
    b: Builder,
    x0, x1, z0, z1,
    eave_y,
    ridge_axis="z",
    chigi=False,
    katsuogi_count=0,
    max_height=10,
):
    layer = 0
    cx0, cx1, cz0, cz1 = x0, x1, z0, z1

    while layer < max_height and (cx1 - cx0) >= 2 and (cz1 - cz0) >= 2:
        y = eave_y + layer
        is_eave = (layer == 0)
        gable_phase = layer >= 2

        for x in range(cx0, cx1 + 1):
            for z in range(cz0, cz1 + 1):
                on_w = (x == cx0)
                on_e = (x == cx1)
                on_n = (z == cz0)
                on_s = (z == cz1)
                if not (on_w or on_e or on_n or on_s):
                    if layer == 0:
                        b.set(x, y, z, "wood", WOOD_BEAM)
                    continue

                c = roof_color(x, y, z, eave=is_eave)
                if ridge_axis == "z":
                    if on_w and not (on_n or on_s):
                        b.stairs(x, y, z, "east", c)
                    elif on_e and not (on_n or on_s):
                        b.stairs(x, y, z, "west", c)
                    elif on_n and not (on_w or on_e):
                        if gable_phase:
                            b.set(x, y, z, "tile", CHIGI_WHITE if (x == cx0 + 1 or x == cx1 - 1) else PLASTER_BRIGHT)
                        else:
                            b.stairs(x, y, z, "south", c)
                    elif on_s and not (on_w or on_e):
                        if gable_phase:
                            b.set(x, y, z, "tile", CHIGI_WHITE if (x == cx0 + 1 or x == cx1 - 1) else PLASTER_BRIGHT)
                        else:
                            b.stairs(x, y, z, "north", c)
                    else:
                        if is_eave:
                            b.set(x, y, z, "tile", TILE_RIDGE)
                            b.slab(x, y + 1, z, TILE_RIDGE, "bottom")
                        else:
                            b.set(x, y, z, "tile", TILE_RIDGE if gable_phase else c)
                else:
                    if on_n and not (on_w or on_e):
                        b.stairs(x, y, z, "south", c)
                    elif on_s and not (on_w or on_e):
                        b.stairs(x, y, z, "north", c)
                    elif on_w and not (on_n or on_s):
                        if gable_phase:
                            b.set(x, y, z, "tile", CHIGI_WHITE if (z == cz0 + 1 or z == cz1 - 1) else PLASTER_BRIGHT)
                        else:
                            b.stairs(x, y, z, "east", c)
                    elif on_e and not (on_n or on_s):
                        if gable_phase:
                            b.set(x, y, z, "tile", CHIGI_WHITE if (z == cz0 + 1 or z == cz1 - 1) else PLASTER_BRIGHT)
                        else:
                            b.stairs(x, y, z, "west", c)
                    else:
                        if is_eave:
                            b.set(x, y, z, "tile", TILE_RIDGE)
                            b.slab(x, y + 1, z, TILE_RIDGE, "bottom")
                        else:
                            b.set(x, y, z, "tile", TILE_RIDGE if gable_phase else c)

        if ridge_axis == "z":
            cx0 += 1
            cx1 -= 1
            if not gable_phase:
                cz0 += 1
                cz1 -= 1
        else:
            cz0 += 1
            cz1 -= 1
            if not gable_phase:
                cx0 += 1
                cx1 -= 1
        layer += 1

    ridge_y = eave_y + layer
    for x in range(cx0, cx1 + 1):
        for z in range(cz0, cz1 + 1):
            b.set(x, ridge_y, z, "tile", TILE_RIDGE)
            b.slab(x, ridge_y + 1, z, TILE_RIDGE, "bottom")

    if ridge_axis == "z":
        mid_x = (cx0 + cx1) // 2
        for z_end in (cz0, cz1):
            b.set(mid_x, ridge_y + 1, z_end, "tile", GOLD_TRIM)
        if chigi:
            for z_end, dz_out in ((cz0 + 1, -1), (cz1 - 1, 1)):
                b.set(mid_x - 1, ridge_y + 1, z_end, "wood", WOOD_DARK)
                b.set(mid_x + 1, ridge_y + 1, z_end, "wood", WOOD_DARK)
                b.set(mid_x - 2, ridge_y + 2, z_end, "wood", WOOD_BEAM)
                b.set(mid_x + 2, ridge_y + 2, z_end, "wood", WOOD_BEAM)
                b.set(mid_x - 2, ridge_y + 3, z_end, "wood", CHIGI_WHITE)
                b.set(mid_x + 2, ridge_y + 3, z_end, "wood", CHIGI_WHITE)
                b.set(mid_x, ridge_y - 1, z_end + dz_out, "wood", GOLD_TRIM)
        if katsuogi_count > 0:
            span = cz1 - cz0 - 4
            if span >= katsuogi_count:
                step = span / max(1, katsuogi_count - 1) if katsuogi_count > 1 else 0
                for i in range(katsuogi_count):
                    kz = int(round(cz0 + 2 + i * step))
                    b.set(mid_x, ridge_y + 1, kz, "wood", WOOD_DARK)
                    b.set(mid_x - 1, ridge_y + 1, kz, "wood", CHIGI_WHITE)
                    b.set(mid_x + 1, ridge_y + 1, kz, "wood", CHIGI_WHITE)
    else:
        mid_z = (cz0 + cz1) // 2
        for x_end in (cx0, cx1):
            b.set(x_end, ridge_y + 1, mid_z, "tile", GOLD_TRIM)

    return ridge_y


def fill_tatami(b: Builder, x0, x1, y, z0, z1):
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            on_edge = (x == x0 or x == x1 or z == z0 or z == z1 or ((x - x0) % 4 == 0 and (z - z0) % 2 == 0))
            if on_edge:
                c = TATAMI_EDGE
            else:
                c = TATAMI_MAIN if ((x // 2 + z // 2) % 2 == 0) else TATAMI_ALT
            b.set(x, y, z, "tile", c)


# ---------------------------------------------------------------------------
# 4. Main Shrine Complex: Grand Gate Haiden -> Open Walkthrough Heiden -> Honden
# ---------------------------------------------------------------------------
def build_haiden_and_honden(b: Builder):
    floor_y = COURT_Y + 2  # Y=14

    # --- A. HAIDEN (X=62..71, Z=26..44) with wide front porch (X=59..73, Z=24..46) ---
    for x in range(59, 74):
        for z in range(24, 47):
            if (x - 59) % 3 == 0 and (z - 24) % 3 == 0:
                b.set(x, COURT_Y, z, "brick", STONE_DARK)
                b.set(x, COURT_Y + 1, z, "wood", WOOD_DARK)
            b.set(x, floor_y, z, "wood", WOOD_AGED if (x <= 61 or x >= 72 or z <= 25 or z >= 45) else wood_color(x, floor_y, z))

    # Unbreakable tinted vermilion veranda railings (Koran)
    for x in range(59, 74):
        for z in (24, 46):
            if z == 46 and 65 <= x <= 69:
                continue  # Open to South Wing connecting corridor
            b.fence(x, floor_y + 1, z, VERMILION_BASE)
    for z in range(24, 47):
        if abs(z - CZ) > 3:
            b.fence(59, floor_y + 1, z, VERMILION_BASE)
        if abs(z - CZ) > 3:
            b.fence(73, floor_y + 1, z, VERMILION_BASE)

    # Illuminated veranda corner lantern posts (unbreakable warm lamps!)
    for x in (59, 73):
        for z in (24, 46):
            b.set(x, floor_y + 1, z, "wood", VERMILION_BASE)
            b.lamp(x, floor_y + 2, z, LAMP_WARM)
            b.slab(x, floor_y + 3, z, TILE_RIDGE, "bottom")

    # Front grand wooden steps (X=57..58, Z=32..38, climbing Y=12->14)
    for z in range(CZ - 3, CZ + 4):
        b.stairs(57, COURT_Y + 1, z, "east", WOOD_AGED)
        b.stairs(58, COURT_Y + 2, z, "east", WOOD_BASE)
        b.set(58, COURT_Y + 1, z, "wood", WOOD_DARK)
    for z in (CZ - 4, CZ + 4):
        b.set(57, COURT_Y + 1, z, "wood", VERMILION_SHADE)
        b.set(58, COURT_Y + 1, z, "wood", VERMILION_SHADE)
        b.set(58, COURT_Y + 2, z, "wood", VERMILION_BASE)
        b.set(58, COURT_Y + 3, z, "tile", GOLD_TRIM)

    # Haiden exterior walls (X=62..71, Z=26..44, Y=15..19)
    wall_top = floor_y + 5  # Y=19
    for y in range(floor_y + 1, wall_top + 1):
        for x in range(62, 72):
            for z in (26, 44):
                is_pillar = (x in (62, 65, 68, 71))
                is_window = (y in (floor_y + 2, floor_y + 3)) and (not is_pillar)
                if is_pillar or y == wall_top:
                    b.set(x, y, z, "wood", WOOD_BEAM)
                elif z == 44 and 66 <= x <= 68 and y in (floor_y + 1, floor_y + 2, floor_y + 3):
                    # Spacious 3x3 doorway to South Wing corridor
                    continue
                elif is_window:
                    b.fence(x, y, z, WOOD_DARK)
                else:
                    b.set(x, y, z, "brick", plaster_color(x, y, z))

        for z in range(26, 45):
            for x in (62, 71):
                is_pillar = (z in (26, 29, 32, 38, 41, 44))
                # Front grand shrine gate opening: 5 wide (CZ-2..CZ+2), 4 tall (Y=15..18)
                if x == 62 and abs(z - CZ) <= 2 and y <= floor_y + 4:
                    continue
                # Rear walkthrough opening to Heiden: 3 wide (CZ-1..CZ+1), 4 tall (Y=15..18)
                if x == 71 and abs(z - CZ) <= 1 and y <= floor_y + 4:
                    continue
                if is_pillar or y == wall_top:
                    b.set(x, y, z, "wood", VERMILION_BASE if (x == 62 and z in (32, 38)) else WOOD_BEAM)
                elif x == 62 and y in (floor_y + 2, floor_y + 3) and abs(z - CZ) in (4, 5):
                    # Unbreakable crimson lattice windows
                    b.fence(x, y, z, VERMILION_SHADE)
                elif y == floor_y + 1:
                    b.set(x, y, z, "wood", WOOD_AGED)
                else:
                    b.set(x, y, z, "brick", plaster_color(x, y, z))

    # --- REDESIGNED GRAND SHRINE GATE (5 wide x 4 tall, open double-leaf lattice gate!) ---
    # Flanking vermilion gate pillars (Z=32, 38) with golden lintel & openwork Ranma transom
    for z in range(CZ - 2, CZ + 3):
        # Upper transom lattice (Y=18)
        b.fence(62, floor_y + 4, z, GOLD_TRIM if z == CZ else WOOD_DARK)
        # Decorative lintel plaque above gate center (Y=19)
        if abs(z - CZ) <= 1:
            b.set(61, wall_top, z, "wood", LACQUER_BLACK)
    b.set(61, wall_top, CZ, "tile", GOLD_TRIM)
    # Thick swung-open ceremonial double gate leaves on left/right jambs (Z=33, 37; Y=15..17)
    for y in range(floor_y + 1, floor_y + 4):
        b.trapdoor(62, y, CZ - 2, facing="south", color=VERMILION_SHADE, half="bottom", is_open=True)
        b.trapdoor(62, y, CZ + 2, facing="north", color=VERMILION_SHADE, half="bottom", is_open=True)
    # Grand crimson-warm Chochin hanging lanterns flanking the shrine gate
    b.lamp(61, floor_y + 3, CZ - 3, LAMP_VERMILION)
    b.lamp(61, floor_y + 3, CZ + 3, LAMP_VERMILION)
    b.slab(61, floor_y + 4, CZ - 3, TILE_RIDGE, "bottom")
    b.slab(61, floor_y + 4, CZ + 3, TILE_RIDGE, "bottom")

    # Haiden Interior: Tatami mats + wide central ceremonial aisle straight into Heiden!
    fill_tatami(b, 63, 70, floor_y, 27, 43)
    # Central ceremonial runner (Z=34..36) completely unobstructed all the way to X=71
    for x in range(62, 71):
        for z in (CZ - 1, CZ, CZ + 1):
            b.set(x, floor_y, z, "wood", VERMILION_SHADE if z == CZ else WOOD_BASE)
    # Twin symmetrical side offering tables (leaving central aisle 100% open!)
    for z_group in ((29, 30, 31), (39, 40, 41)):
        for z in z_group:
            b.set(69, floor_y + 1, z, "wood", WOOD_DARK)
            b.slab(69, floor_y + 2, z, VERMILION_BASE, "bottom")
        b.lamp(69, floor_y + 2, z_group[1], LAMP_SACRED)
    # Sake barrels in corners
    for z in (27, 28, 42, 43):
        b.set(70, floor_y + 1, z, "minecraft:barrel", facing="up", open="false")

    # Bright interior ceiling lamp array inside Haiden (no dark corners!)
    for lx in (64, 67, 70):
        for lz in (29, 35, 41):
            b.lamp(lx, wall_top, lz, LAMP_WARM if lz != 35 else LAMP_SACRED)
    # Bright eave-level lamps wrapping Haiden outer perimeter
    for lx in (61, 65, 69, 72):
        b.lamp(lx, wall_top, 25, LAMP_WARM)
        b.lamp(lx, wall_top, 45, LAMP_WARM)
    for lz in (27, 31, 39, 43):
        b.lamp(61, wall_top, lz, LAMP_WARM)

    # Iconic Saisen-bako (Empty Offertory Box) placed cleanly at front porch edge (X=59)
    # Leaves generous 2-block walkthrough gap (X=60..61) in front of the Grand Gate!
    for z in range(CZ - 1, CZ + 2):
        b.set(59, floor_y + 1, z, "wood", WOOD_DARK)
        b.trapdoor(59, floor_y + 2, z, facing="west", color=WOOD_AGED, half="bottom", is_open=False)

    # Kohai (Forward-projecting Entrance Canopy), Giant Shimenawa, Shide & Suzu Bell
    for z in (CZ - 4, CZ + 4):
        for y in range(floor_y + 1, wall_top + 1):
            b.set(57, y, z, "wood", VERMILION_BASE if y < wall_top else WOOD_BEAM)
    for z in range(CZ - 5, CZ + 6):
        b.set(57, wall_top, z, "wood", WOOD_BEAM)
        b.stairs(56, wall_top + 1, z, "east", roof_color(56, wall_top + 1, z, eave=True))
        b.stairs(57, wall_top + 2, z, "east", roof_color(57, wall_top + 2, z))
        b.set(58, wall_top + 2, z, "tile", roof_color(58, wall_top + 2, z))
        b.stairs(58, wall_top + 3, z, "east", roof_color(58, wall_top + 3, z))
    for z in range(CZ - 3, CZ + 4):
        rope_y = wall_top - 1 if abs(z - CZ) <= 2 else wall_top
        b.set(57, rope_y, z, "minecraft:hay_block", axis="z")
    for z in (CZ - 3, CZ - 2, CZ + 2, CZ + 3):
        b.set(57, wall_top - 2, z, "minecraft:white_banner", rotation="4")
    b.set(58, wall_top - 1, CZ, "minecraft:bell", attachment="ceiling", facing="west", powered="false")
    b.set(58, wall_top - 2, CZ, "minecraft:chain", axis="y", waterlogged="false")
    # Bright porch eaves lamps on Kohai
    b.lamp(57, wall_top - 1, CZ - 5, LAMP_AMBER)
    b.lamp(57, wall_top - 1, CZ + 5, LAMP_AMBER)
    b.lamp(58, wall_top, CZ - 3, LAMP_WARM)
    b.lamp(58, wall_top, CZ + 3, LAMP_WARM)

    # Haiden Grand Irimoya Daiqing Tile Roof
    build_irimoya_roof(
        b,
        x0=58, x1=74, z0=22, z1=48,
        eave_y=wall_top + 1,
        ridge_axis="z",
        chigi=True,
        katsuogi_count=5,
        max_height=10,
    )

    # --- B. HEIDEN (Smooth Walkthrough Connecting Hall X=71..76, Z=31..39) ---
    heiden_y = floor_y + 1  # Y=15
    # Smooth 3-wide wooden step climbing from Haiden (Y=14) up to Heiden (Y=15)
    for z in range(CZ - 1, CZ + 2):
        b.stairs(71, heiden_y, z, "east", WOOD_BASE)
        b.clear_box(71, heiden_y + 1, z, 71, heiden_y + 3, z)

    for x in range(72, 76):
        for z in range(31, 40):
            b.set(x, COURT_Y, z, "brick", STONE_DARK)
            b.set(x, COURT_Y + 1, z, "wood", WOOD_DARK)
            b.set(x, heiden_y, z, "wood", VERMILION_SHADE if z == CZ else WOOD_BASE)
            # Clear 4 blocks of tall headroom inside Heiden (Y=16..19)
            b.clear_box(x, heiden_y + 1, z, x, heiden_y + 4, z)
            if z in (31, 39):
                for y in range(heiden_y + 1, heiden_y + 5):
                    if y in (heiden_y + 2, heiden_y + 3):
                        b.fence(x, y, z, WOOD_BEAM)
                    else:
                        b.set(x, y, z, "wood", WOOD_BEAM)
    # Bright ceiling lamps inside Heiden
    b.lamp(73, heiden_y + 4, 33, LAMP_WARM)
    b.lamp(73, heiden_y + 4, 37, LAMP_WARM)
    b.lamp(75, heiden_y + 4, 35, LAMP_SACRED)

    # Gable Daiqing roof over Heiden (lifted 1 block higher to preserve tall headroom!)
    for x in range(72, 76):
        for step in range(4):
            b.stairs(x, heiden_y + 5 + step, 30 + step, "south", roof_color(x, heiden_y + 5 + step, 30 + step, eave=(step == 0)))
            b.stairs(x, heiden_y + 5 + step, 40 - step, "north", roof_color(x, heiden_y + 5 + step, 40 - step, eave=(step == 0)))
        for z in range(34, 37):
            b.set(x, heiden_y + 8, z, "tile", TILE_RIDGE)

    # --- C. HONDEN (Inner Sanctuary X=76..84, Z=29..41, elevated Y=16, fully walkable!) ---
    honden_y = floor_y + 2  # Y=16
    for x in range(75, 86):
        for z in range(28, 43):
            if (x - 75) % 2 == 0 and (z - 28) % 2 == 0:
                b.set(x, COURT_Y, z, "brick", STONE_DARK)
                for py in range(COURT_Y + 1, honden_y):
                    b.set(x, py, z, "wood", WOOD_DARK)
            b.set(x, honden_y, z, "wood", WOOD_AGED)

    # Unbreakable crimson balcony railings around Honden
    for x in range(75, 86):
        for z in (28, 42):
            b.fence(x, honden_y + 1, z, VERMILION_BASE)
    for z in range(28, 43):
        b.fence(85, honden_y + 1, z, VERMILION_BASE)
        if abs(z - CZ) > 2:
            b.fence(75, honden_y + 1, z, VERMILION_BASE)
    for x in (75, 85):
        for z in (28, 42):
            b.set(x, honden_y + 1, z, "wood", VERMILION_BASE)
            b.lamp(x, honden_y + 2, z, LAMP_AMBER)

    # Honden sanctuary walls (X=76..84, Z=29..41, Y=17..21)
    honden_top = honden_y + 5  # Y=21
    for y in range(honden_y + 1, honden_top + 1):
        for x in range(76, 85):
            for z in (29, 41):
                is_p = (x in (76, 80, 84))
                b.set(x, y, z, "wood" if (is_p or y == honden_top) else "brick",
                      WOOD_BEAM if (is_p or y == honden_top) else plaster_color(x, y, z))
        for z in range(29, 42):
            for x in (76, 84):
                # Wide 3x4 walkthrough doorway from Heiden into Honden!
                if x == 76 and abs(z - CZ) <= 1 and y <= honden_y + 3:
                    continue
                is_p = (z in (29, 32, 38, 41))
                b.set(x, y, z, "wood" if (is_p or y == honden_top) else "brick",
                      WOOD_BEAM if (is_p or y == honden_top) else plaster_color(x, y, z))

    # Smooth 3-wide wooden step climbing from Heiden (Y=15) up to Honden (Y=16) at X=75
    for z in range(CZ - 1, CZ + 2):
        b.stairs(75, honden_y, z, "east", WOOD_BASE)
        b.clear_box(75, honden_y + 1, z, 75, honden_y + 3, z)
        b.clear_box(76, honden_y + 1, z, 76, honden_y + 3, z)

    # Sacred Hakurei Yin-Yang Orb / Mirror Altar inside Honden (bright & revered!)
    fill_tatami(b, 77, 83, honden_y, 30, 40)
    for z in range(CZ - 2, CZ + 3):
        b.set(81, honden_y + 1, z, "wood", VERMILION_BASE)
        b.set(82, honden_y + 1, z, "wood", VERMILION_DARK)
    b.set(82, honden_y + 2, CZ, "tile", GOLD_TRIM)
    b.lamp(82, honden_y + 3, CZ, LAMP_SACRED)
    b.lamp(82, honden_y + 2, CZ - 2, LAMP_WARM)
    b.lamp(82, honden_y + 2, CZ + 2, LAMP_WARM)
    for lx, lz in ((78, 32), (78, 38), (82, 32), (82, 38)):
        b.lamp(lx, honden_top, lz, LAMP_SACRED)

    # Honden Grand Roof
    build_irimoya_roof(
        b,
        x0=74, x1=86, z0=26, z1=44,
        eave_y=honden_top + 1,
        ridge_axis="z",
        chigi=True,
        katsuogi_count=3,
        max_height=8,
    )


# ---------------------------------------------------------------------------
# 5. North Wing: Chozuya, Ema Rack, Boundary Dais, Shamusho & Storehouse
# ---------------------------------------------------------------------------
def build_north_wing(b: Builder):
    # --- A. Chozuya (Purification Water Pavilion, X=42..48, Z=16..22) ---
    for x in range(42, 49):
        for z in range(16, 23):
            b.set(x, COURT_Y, z, "tile", STONE_BASE if (x + z) % 2 == 0 else STONE_WET)
    for x in (43, 47):
        for z in (17, 21):
            b.set(x, COURT_Y, z, "brick", STONE_DARK)
            for y in range(COURT_Y + 1, COURT_Y + 5):
                b.set(x, y, z, "wood", WOOD_BEAM)
    for x in range(44, 47):
        for z in range(18, 21):
            if x == 45 and z == 19:
                b.set(x, COURT_Y + 1, z, "minecraft:water", level="0")
            else:
                b.set(x, COURT_Y + 1, z, "brick", STONE_WET)
    b.set(45, COURT_Y + 2, 18, "minecraft:bamboo")
    b.lamp(45, COURT_Y + 4, 19, LAMP_WARM)
    b.low_lantern(41, COURT_Y + 1, 22, glow=LAMP_AMBER)
    build_irimoya_roof(b, x0=41, x1=49, z0=15, z1=23, eave_y=COURT_Y + 5, ridge_axis="x", max_height=4)

    # --- B. Ema Votive Plaque Rack (X=43..50, Z=10..12) ---
    for x in (43, 50):
        for y in range(COURT_Y + 1, COURT_Y + 5):
            b.set(x, y, 11, "wood", WOOD_BEAM)
    for x in range(44, 50):
        b.set(x, COURT_Y + 2, 11, "wood", WOOD_AGED)
        b.set(x, COURT_Y + 3, 11, "wood", WOOD_AGED)
        b.set(x, COURT_Y + 2, 12, "minecraft:birch_wall_sign", facing="south", waterlogged="false")
        b.set(x, COURT_Y + 3, 12, "minecraft:oak_wall_sign", facing="south", waterlogged="false")
    b.lamp(45, COURT_Y + 4, 11, LAMP_WARM)
    b.lamp(48, COURT_Y + 4, 11, LAMP_WARM)
    for x in range(42, 52):
        b.stairs(x, COURT_Y + 5, 10, "south", TILE_BASE)
        b.set(x, COURT_Y + 5, 11, "tile", TILE_RIDGE)
        b.stairs(x, COURT_Y + 5, 12, "north", TILE_BASE)

    # --- C. Boundary Stone Dais (Reserved Twin Sukima Gate Pad at lx=50, lz=19) ---
    dx_c, dz_c = 50, 19
    for x in range(dx_c - 2, dx_c + 3):
        for z in range(dz_c - 2, dz_c + 3):
            if abs(x - dx_c) == 2 and abs(z - dz_c) == 2:
                continue
            b.set(x, COURT_Y, z, "tile", STONE_DARK if max(abs(x - dx_c), abs(z - dz_c)) == 2 else PLASTER_SHADE)
    b.set(dx_c, COURT_Y, dz_c, "tile", GOLD_TRIM)
    for cx, cz in ((dx_c - 2, dz_c - 2), (dx_c + 2, dz_c - 2), (dx_c - 2, dz_c + 2), (dx_c + 2, dz_c + 2)):
        b.low_lantern(cx, COURT_Y + 1, cz, glow=LAMP_SACRED)

    # --- D. Shamusho / Amulet Office (X=55..65, Z=10..17) ---
    sy = COURT_Y + 1
    for x in range(55, 66):
        for z in range(10, 18):
            b.set(x, COURT_Y, z, "brick", STONE_DARK)
            b.set(x, sy, z, "wood", wood_color(x, sy, z))
    for y in range(sy + 1, sy + 5):
        for x in range(55, 66):
            for z in (10, 17):
                is_p = (x in (55, 60, 65))
                if z == 17 and y in (sy + 2, sy + 3) and not is_p:
                    if y == sy + 2 and 57 <= x <= 63:
                        b.slab(x, y, z, WOOD_PALE, "bottom")
                    else:
                        continue
                else:
                    b.set(x, y, z, "wood" if (is_p or y == sy + 4) else "brick",
                          WOOD_BEAM if (is_p or y == sy + 4) else plaster_color(x, y, z))
        for z in range(10, 18):
            for x in (55, 65):
                if x == 55 and z == 14 and y <= sy + 2:
                    continue
                is_p = (z in (10, 17))
                b.set(x, y, z, "wood" if (is_p or y == sy + 4) else "brick",
                      WOOD_BEAM if (is_p or y == sy + 4) else plaster_color(x, y, z))
    b.door(55, sy + 1, 14, facing="west", hinge="left", color=DOOR_WOOD)
    for x in range(57, 64):
        b.set(x, sy + 1, 11, "minecraft:bookshelf")
    # Bright warm lamps inside & along Shamusho front eave
    b.lamp(58, sy + 4, 18, LAMP_WARM)
    b.lamp(62, sy + 4, 18, LAMP_WARM)
    b.lamp(58, sy + 4, 14, LAMP_WARM)
    b.lamp(62, sy + 4, 14, LAMP_WARM)
    build_irimoya_roof(b, x0=53, x1=67, z0=8, z1=19, eave_y=sy + 5, ridge_axis="x", max_height=5)

    # --- E. Rear Banquet Sake Storehouse (Kura, X=72..80, Z=11..18) ---
    ky = COURT_Y + 1
    for x in range(72, 81):
        for z in range(11, 19):
            b.set(x, COURT_Y, z, "brick", STONE_DARK)
            b.set(x, ky, z, "wood", WOOD_AGED)
    for y in range(ky + 1, ky + 6):
        for x in range(72, 81):
            for z in (11, 18):
                b.set(x, y, z, "brick", plaster_color(x, y, z) if y > ky + 1 else STONE_WET)
        for z in range(11, 19):
            for x in (72, 80):
                if x == 72 and z == 15 and y <= ky + 2:
                    continue
                b.set(x, y, z, "brick", plaster_color(x, y, z) if y > ky + 1 else STONE_WET)
    b.door(72, ky + 1, 15, facing="west", hinge="left", color=WOOD_DARK)
    for x in (75, 76, 78, 79):
        b.set(x, ky + 1, 13, "minecraft:barrel", facing="up", open="false")
        b.set(x, ky + 1, 16, "minecraft:decorated_pot")
    b.set(78, ky + 2, 13, "minecraft:barrel", facing="up", open="false")
    b.lamp(76, ky + 5, 15, LAMP_WARM)
    b.lamp(71, ky + 4, 15, LAMP_AMBER)
    build_irimoya_roof(b, x0=70, x1=82, z0=9, z1=20, eave_y=ky + 6, ridge_axis="x", max_height=5)


# ---------------------------------------------------------------------------
# 6. South Wing: Reimu's Living Quarters, Wide Engawa & Cherry Blossom Pond Garden
# ---------------------------------------------------------------------------
def build_south_wing_and_garden(b: Builder):
    ey = COURT_Y + 2  # Y=14, aligned with Haiden floor

    # Connecting covered gallery from Haiden south veranda (X=65..69, Z=46..48)
    for x in range(65, 70):
        for z in range(46, 49):
            b.set(x, COURT_Y + 1, z, "wood", WOOD_DARK)
            b.set(x, ey, z, "wood", WOOD_BASE)
            if x in (65, 69):
                b.fence(x, ey + 1, z, WOOD_BEAM)
        for step in range(3):
            b.stairs(64 + step, ey + 5 + step, 47, "east", roof_color(64 + step, ey + 5 + step, 47))
            b.stairs(70 - step, ey + 5 + step, 47, "west", roof_color(70 - step, ey + 5 + step, 47))
        for z in range(46, 49):
            b.set(67, ey + 7, z, "tile", TILE_RIDGE)
    b.lamp(67, ey + 5, 47, LAMP_WARM)

    # --- A. Reimu's Living Pavilion (X=62..76, Z=49..60) + Wide Engawa (X=60..76, Z=49..62) ---
    for x in range(60, 77):
        for z in range(49, 63):
            if (x - 60) % 3 == 0 and (z - 49) % 3 == 0:
                b.set(x, COURT_Y, z, "brick", STONE_DARK)
                b.set(x, COURT_Y + 1, z, "wood", WOOD_DARK)
            b.set(x, ey, z, "wood", WOOD_PALE if (x < 62 or z > 60) else wood_color(x, ey, z))
    b.stairs(59, COURT_Y + 1, 55, "east", STONE_LIGHT)
    b.stairs(59, COURT_Y + 1, 56, "east", STONE_LIGHT)

    ptop = ey + 4  # Y=18
    for y in range(ey + 1, ptop + 1):
        for x in range(62, 77):
            for z in (49, 60):
                if z == 49 and 66 <= x <= 68 and y <= ey + 3:
                    continue  # Tall 3x3 entry from Haiden gallery
                is_p = (x in (62, 66, 70, 73, 76))
                if z == 60 and y in (ey + 1, ey + 2, ey + 3) and not is_p:
                    if y == ey + 1:
                        b.set(x, y, z, "wood", WOOD_AGED)
                    else:
                        b.fence(x, y, z, SHOJI_PAPER)
                elif is_p or y == ptop:
                    b.set(x, y, z, "wood", WOOD_BEAM)
                else:
                    b.set(x, y, z, "brick", plaster_color(x, y, z))
        for z in range(49, 61):
            for x in (62, 76):
                is_p = (z in (49, 53, 57, 60))
                if x == 62 and z in (54, 55, 56) and y <= ey + 3:
                    continue  # Open Shoji doorway onto West Engawa
                if x == 62 and not is_p and y in (ey + 2, ey + 3):
                    b.fence(x, y, z, SHOJI_PAPER)
                elif is_p or y == ptop:
                    b.set(x, y, z, "wood", WOOD_BEAM)
                else:
                    b.set(x, y, z, "brick", plaster_color(x, y, z))

    # Shoji sliding doors onto West Engawa
    b.door(62, ey + 1, 54, facing="west", hinge="left", color=SHOJI_PAPER)
    b.door(62, ey + 1, 56, facing="west", hinge="right", color=SHOJI_PAPER)

    # Interior: Tatami tea room, low table, cushions, alcove & bright warm ceiling lamps
    fill_tatami(b, 63, 75, ey, 50, 59)
    for x in range(68, 71):
        for z in (54, 55):
            b.slab(x, ey + 1, z, WOOD_DARK, "bottom")
    b.set(69, ey + 2, 54, "minecraft:flower_pot")
    for cx, cz in ((67, 54), (67, 55), (71, 54), (71, 55), (69, 53), (69, 56)):
        b.set(cx, ey + 1, cz, "minecraft:red_carpet")
    for z in range(52, 58):
        b.set(75, ey + 1, z, "wood", WOOD_DARK)
    b.set(75, ey + 2, 54, "minecraft:potted_cherry_sapling")
    b.set(75, ey + 2, 56, "minecraft:decorated_pot")
    b.set(74, ey + 1, 51, "minecraft:bookshelf")

    # Warm lamps inside tea room & wrapping Engawa eaves
    for lx, lz in ((65, 52), (65, 57), (72, 52), (72, 57), (69, 55)):
        b.lamp(lx, ptop, lz, LAMP_WARM)
    for lz in (51, 55, 59):
        b.lamp(60, ptop, lz, LAMP_AMBER)
    for lx in (64, 68, 72, 76):
        b.lamp(lx, ptop, 62, LAMP_AMBER)

    for x, z in ((60, 49), (60, 55), (60, 62), (68, 62), (76, 62)):
        for y in range(ey + 1, ptop + 1):
            b.set(x, y, z, "wood", WOOD_BEAM)

    build_irimoya_roof(b, x0=58, x1=78, z0=48, z1=64, eave_y=ptop + 1, ridge_axis="x", max_height=6)

    # --- B. South Garden: Karesansui Raked Sand, Koi Pond & Giant Ancient Sakura Tree ---
    for x in range(51, 59):
        for z in range(54, 62):
            c = SAND_WAVE if ((x + z) % 2 == 0) else SAND_KARESANSUI
            b.set(x, COURT_Y, z, "brick", c)
    b.set(55, COURT_Y + 1, 58, "brick", STONE_MOSS)
    b.set(56, COURT_Y + 1, 58, "brick", STONE_DARK)
    b.slab(55, COURT_Y + 2, 58, STONE_MOSS, "bottom")

    for x in range(42, 51):
        for z in range(52, 61):
            dist = math.hypot((x - 46.0) / 4.2, (z - 56.0) / 3.8)
            if dist <= 0.85:
                b.set(x, COURT_Y - 2, z, "brick", STONE_WET)
                b.set(x, COURT_Y - 1, z, "minecraft:water", level="0")
                b.set(x, COURT_Y, z, "minecraft:water", level="0")
            elif dist <= 1.08:
                b.set(x, COURT_Y, z, "brick", STONE_MOSS if (x + z) % 2 == 0 else STONE_WET)
    b.set(45, COURT_Y + 1, 55, "minecraft:lily_pad")
    b.set(47, COURT_Y + 1, 57, "minecraft:lily_pad")
    # Garden stone lanterns lighting the pond, sakura & Karesansui
    b.stone_lantern(49, COURT_Y + 1, 52, glow=LAMP_AMBER)
    b.low_lantern(41, COURT_Y + 1, 58, glow=LAMP_WARM)
    b.low_lantern(57, COURT_Y + 1, 61, glow=LAMP_WARM)

    build_ancient_sakura(b, 53, COURT_Y + 1, 50)


# ---------------------------------------------------------------------------
# 7. Organic Trees, Rear Forest Screen & Fallen Sakura Petals
# ---------------------------------------------------------------------------
def build_ancient_sakura(b: Builder, tx, ty, tz):
    for dx in (-1, 0, 1):
        for dz in (-1, 0, 1):
            if abs(dx) + abs(dz) <= 1:
                b.set(tx + dx, ty, tz + dz, "minecraft:cherry_log", axis="y")
    for dy in range(1, 7):
        ox = 1 if dy >= 4 else 0
        oz = 1 if dy >= 5 else 0
        b.set(tx + ox, ty + dy, tz + oz, "minecraft:cherry_log", axis="y")
        b.set(tx + ox + 1, ty + dy, tz + oz, "minecraft:cherry_log", axis="y")

    clusters = [
        (tx - 3, ty + 8, tz - 1, 3.6, 2.2),
        (tx + 4, ty + 9, tz + 3, 4.2, 2.5),
        (tx + 1, ty + 11, tz - 2, 4.0, 2.4),
        (tx - 2, ty + 10, tz + 4, 3.8, 2.3),
        (tx + 2, ty + 13, tz + 1, 3.5, 2.2),
    ]
    for cx, cy, cz, rx, ry in clusters:
        steps = 6
        for s in range(1, steps):
            bx = int(round((tx + 1) + (cx - (tx + 1)) * s / steps))
            by = int(round((ty + 6) + (cy - (ty + 6)) * s / steps))
            bz = int(round((tz + 1) + (cz - (tz + 1)) * s / steps))
            b.set(bx, by, bz, "minecraft:cherry_log", axis="y")
        for x in range(int(cx - rx - 1), int(cx + rx + 2)):
            for y in range(int(cy - ry), int(cy + ry + 2)):
                for z in range(int(cz - rx - 1), int(cz + rx + 2)):
                    d = ((x - cx) / rx) ** 2 + ((y - cy) / ry) ** 2 + ((z - cz) / rx) ** 2
                    if d <= 0.92 and (det_hash(x, y, z, 131) % 7 != 0):
                        if (x, y, z) not in b.cells:
                            b.set(x, y, z, "minecraft:cherry_leaves", distance="1", persistent="true", waterlogged="false")

    for x in range(tx - 7, tx + 8):
        for z in range(tz - 6, tz + 8):
            if (x, COURT_Y, z) in b.cells and b.cells[(x, COURT_Y, z)][0] == "gensokyou:gensokyo_grass_block":
                if (x, COURT_Y + 1, z) not in b.cells and det_hash(x, 0, z, 149) % 3 == 0:
                    amt = 1 + (det_hash(x, 1, z, 151) % 4)
                    b.set(x, COURT_Y + 1, z, "minecraft:pink_petals", facing="west", flower_amount=str(amt))


def build_sacred_tree(b: Builder, tx, ty, tz, height=11, crown_r=3.4):
    for dy in range(height):
        b.set(tx, ty + dy, tz, "gensokyou:sacred_wood", axis="y")
    cy = ty + height - 2
    for x in range(int(tx - crown_r - 1), int(tx + crown_r + 2)):
        for y in range(cy - 2, cy + 4):
            for z in range(int(tz - crown_r - 1), int(tz + crown_r + 2)):
                d = ((x - tx) / crown_r) ** 2 + ((y - cy) / 2.6) ** 2 + ((z - tz) / crown_r) ** 2
                if d <= 0.95 and (x, y, z) not in b.cells:
                    b.set(x, y, z, "gensokyou:sacred_leaves", distance="1", persistent="true", waterlogged="false")


def build_scenery(b: Builder):
    tree_spots = [
        (10, 16, 10, 3.2),
        (12, 54, 11, 3.4),
        (23, 13, 12, 3.6),
        (25, 57, 11, 3.4),
        (89, 18, 12, 3.8),
        (90, 30, 13, 4.0),
        (90, 42, 13, 3.8),
        (88, 54, 11, 3.5),
    ]
    for tx, tz, th, cr in tree_spots:
        base_y = terrain_surface_y(tx, tz) + 1
        build_sacred_tree(b, tx, base_y, tz, height=th, crown_r=cr)

    for bx, bz in ((86, 22), (87, 23), (87, 46), (86, 48), (85, 13), (86, 56)):
        by = terrain_surface_y(bx, bz) + 1
        bh = 6 + (det_hash(bx, 0, bz, 173) % 5)
        for dy in range(bh):
            b.set(bx, by + dy, bz, "minecraft:bamboo", age="1", leaves="large" if dy >= bh - 2 else "none", stage="0")

    for x in range(2, b.sx - 2):
        for z in range(2, b.sz - 2):
            y = terrain_surface_y(x, z)
            cell = b.cells.get((x, y, z))
            if cell and cell[0] == "gensokyou:gensokyo_grass_block" and (x, y + 1, z) not in b.cells:
                h = det_hash(x, y, z, 199)
                if h % 11 == 0:
                    b.set(x, y + 1, z, "gensokyou:gensokyo_short_grass")
                elif h % 37 == 0:
                    b.set(x, y + 1, z, "gensokyou:spirit_herb")


def build_hakurei_shrine():
    b = Builder(SIZE)
    build_terrain(b)
    build_grand_staircase(b)
    build_torii(b, x=4, base_y=1, half_span=5, pillar_h=10)
    build_torii(b, x=37, base_y=COURT_Y + 1, half_span=4, pillar_h=9)
    build_haiden_and_honden(b)
    build_north_wing(b)
    build_south_wing_and_garden(b)
    build_scenery(b)
    return b.cells


# ---------------------------------------------------------------------------
# Extra Tinted Gray Model Generation (trapdoor, fence, stone_wall, lamp)
# ---------------------------------------------------------------------------
def extra_model_assets(vanilla):
    def read(path):
        return json.loads(vanilla.read(f"assets/minecraft/{path}.json"))

    def encoded(data):
        return (json.dumps(data, indent=2) + "\n").encode("utf-8")

    def tint_all_faces(model):
        for element in model.get("elements", []):
            for face in element.get("faces", {}).values():
                face["tintindex"] = 0
        return model

    result = {}

    # 1. landmark_lamp (full cube, light level 15, tinted)
    lamp_model = tint_all_faces(read("models/block/cube"))
    lamp_model["textures"] = {
        face: "gensokyou:block/landmark_lamp"
        for face in ("particle", "down", "up", "north", "south", "east", "west")
    }
    result[ASSETS / "models/block/landmark_lamp.json"] = encoded(lamp_model)
    result[ASSETS / "blockstates/landmark_lamp.json"] = encoded({
        "variants": {"": {"model": "gensokyou:block/landmark_lamp"}}
    })

    # 2. landmark_wood_trapdoor (variants from oak_trapdoor)
    td_states = read("blockstates/oak_trapdoor")
    for variant in td_states["variants"].values():
        orig = variant["model"].split("/")[-1]
        variant["model"] = "gensokyou:block/" + orig.replace("oak_trapdoor", "landmark_wood_trapdoor")
    result[ASSETS / "blockstates/landmark_wood_trapdoor.json"] = encoded(td_states)
    for suffix in ("bottom", "top", "open"):
        model = tint_all_faces(read(f"models/block/template_trapdoor_{suffix}"))
        model["textures"] = {"texture": "gensokyou:block/landmark_wood", "particle": "gensokyou:block/landmark_wood"}
        result[ASSETS / f"models/block/landmark_wood_trapdoor_{suffix}.json"] = encoded(model)

    # 3. landmark_wood_fence (multipart from oak_fence)
    fence_states = read("blockstates/oak_fence")
    for part in fence_states["multipart"]:
        orig = part["apply"]["model"].split("/")[-1]
        part["apply"]["model"] = "gensokyou:block/" + orig.replace("oak_fence", "landmark_wood_fence")
    result[ASSETS / "blockstates/landmark_wood_fence.json"] = encoded(fence_states)
    for suffix, template in (("post", "fence_post"), ("side", "fence_side")):
        model = tint_all_faces(read(f"models/block/{template}"))
        model["textures"] = {"texture": "gensokyou:block/landmark_wood", "particle": "gensokyou:block/landmark_wood"}
        result[ASSETS / f"models/block/landmark_wood_fence_{suffix}.json"] = encoded(model)

    # 4. landmark_stone_wall (multipart from cobblestone_wall)
    wall_states = read("blockstates/cobblestone_wall")
    for part in wall_states["multipart"]:
        orig = part["apply"]["model"].split("/")[-1]
        part["apply"]["model"] = "gensokyou:block/" + orig.replace("cobblestone_wall", "landmark_stone_wall")
    result[ASSETS / "blockstates/landmark_stone_wall.json"] = encoded(wall_states)
    for suffix, template in (
        ("post", "template_wall_post"),
        ("side", "template_wall_side"),
        ("side_tall", "template_wall_side_tall"),
    ):
        model = tint_all_faces(read(f"models/block/{template}"))
        model["textures"] = {"wall": "gensokyou:block/landmark_brick", "particle": "gensokyou:block/landmark_brick"}
        result[ASSETS / f"models/block/landmark_stone_wall_{suffix}.json"] = encoded(model)

    return result


# ---------------------------------------------------------------------------
# NBT & GLC2 Color Packing
# ---------------------------------------------------------------------------
def text(value):
    raw = value.encode("ascii")
    return struct.pack(">H", len(raw)) + raw


def tag(kind, name, payload):
    return bytes([kind]) + text(name) + payload


def integer(value):
    return struct.pack(">i", value)


def sequence(kind, items):
    return bytes([kind]) + integer(len(items)) + b"".join(items)


def compound(*tags):
    return b"".join(tags) + b"\0"


def structure(cells, data_version, size=SIZE):
    palette = {}
    blocks = []
    for pos, (block, _, properties) in sorted(cells.items(), key=lambda e: (e[0][1], e[0][0], e[0][2])):
        key = (block, properties)
        if key not in palette:
            palette[key] = len(palette)
        blocks.append(compound(
            tag(9, "pos", sequence(3, [integer(n) for n in pos])),
            tag(3, "state", integer(palette[key])),
        ))
    states = []
    for block, props in palette:
        name = block if ":" in block else f"gensokyou:landmark_{block}"
        if props:
            states.append(compound(
                tag(8, "Name", text(name)),
                tag(10, "Properties", compound(*(tag(8, k, text(v)) for k, v in props))),
            ))
        else:
            states.append(compound(tag(8, "Name", text(name))))
    return tag(10, "", compound(
        tag(3, "DataVersion", integer(data_version)),
        tag(9, "size", sequence(3, [integer(n) for n in size])),
        tag(9, "palette", sequence(10, states)),
        tag(9, "blocks", sequence(10, blocks)),
        tag(9, "entities", sequence(10, [])),
    ))


def colors(cells, origin):
    palette = [0xFFFFFF] + sorted({c for _, c, _ in cells.values() if c is not None})
    if len(palette) > 256:
        raise ValueError(f"Format supports up to 255 explicit RGB values, got {len(palette) - 1}")
    indices = {rgb: i for i, rgb in enumerate(palette)}
    blocks = sorted({f"gensokyou:landmark_{block}" for block, c, _ in cells.values() if c is not None})
    if len(blocks) > 255:
        raise ValueError(f"Format supports up to 255 expected block types, got {len(blocks)}")
    block_indices = {block: i for i, block in enumerate(blocks, 1)}
    sections = {}
    for (x, y, z), (block, color, _) in cells.items():
        if color is None:
            continue
        wx, wy, wz = x + origin[0], y + origin[1], z + origin[2]
        key = (wx >> 4, wy >> 4, wz >> 4)
        if key not in sections:
            sections[key] = bytearray(8192)
        section = sections[key]
        index = ((wy & 15) << 8) | ((wz & 15) << 4) | (wx & 15)
        section[index] = indices[color]
        section[4096 + index] = block_indices[f"gensokyou:landmark_{block}"]
    data = b"GLC2" + struct.pack(">H", len(palette)) + b"".join(integer(c) for c in palette)
    data += bytes([len(blocks)]) + b"".join(text(block) for block in blocks)
    data += integer(len(sections))
    for key, section in sorted(sections.items()):
        data += struct.pack(">iii", *key) + section
    return data, len(palette) - 1, len(sections)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true", help="Verify checked-in generated outputs")
    parser.add_argument("--vanilla", type=Path, default=ROOT / "build/moddev/artifacts/neoforge-21.1.248-client-extra-aka-minecraft-resources.jar")
    args = parser.parse_args()

    cells = build_hakurei_shrine()
    with ZipFile(args.vanilla) as vanilla:
        outputs = extra_model_assets(vanilla)
        version = json.loads(vanilla.read("version.json"))["world_version"]

    raw_nbt = structure(cells, version)
    packed_nbt = gzip.compress(raw_nbt, mtime=0)
    raw_colors, rgb_count, section_count = colors(cells, ORIGIN)
    packed_colors = gzip.compress(raw_colors, mtime=0)
    raw_gensokyo_colors, _, gensokyo_sections = colors(cells, GENSOKYO_ORIGIN)
    packed_gensokyo_colors = gzip.compress(raw_gensokyo_colors, mtime=0)

    outputs[Path(f"data/gensokyou/structure/{LANDMARK_ID}.nbt")] = packed_nbt
    outputs[ASSETS / f"landmark/{LANDMARK_ID}.bin"] = packed_colors
    outputs[ASSETS / f"landmark/{GENSOKYO_COLOR_ID}.bin"] = packed_gensokyo_colors

    for relative, contents in outputs.items():
        path = RESOURCES / relative
        if args.check:
            actual = path.read_bytes()
            if path.suffix in {".nbt", ".bin"}:
                actual, contents = gzip.decompress(actual), gzip.decompress(contents)
            if actual != contents:
                raise SystemExit(f"Stale asset: {relative}")
        else:
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(contents)

    tinted = sum(1 for _, c, _ in cells.values() if c is not None)
    lamps = sum(1 for b_id, _, _ in cells.values() if b_id == "lamp")
    print(f"Hakurei Shrine Preview: {len(cells):,} blocks ({tinted:,} tinted, {lamps} lamps), {len(outputs)} files")
    print(f"NBT: {len(raw_nbt):,} bytes raw, {len(packed_nbt):,} bytes GZIP")
    print(f"Colors: {rgb_count} RGB entries, {section_count} sections, {len(raw_colors):,} bytes raw, {len(packed_colors):,} bytes GZIP")


if __name__ == "__main__":
    main()
