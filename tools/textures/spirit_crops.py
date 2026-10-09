# spirit-crop-farming - seeds, crop growth stages, spirit soil farmland (16x16)
#
# Item/crop textures use the cross model (transparent background), so '.' is legal.
# The farmland maps are fully opaque by design -- no '.' entries in PAL_SOIL.
#
# Palette groups:
#   seeds     - husk bag with kernel accent, one accent colour per plant family
#   crops     - 3 growth stages per plant (sprout / leafy / mature + bloom)
#   farmland  - tilled earth, sides separate from the top face

PAL_SEEDS = {
    '.': None,
    'o': (48, 34, 22),      # husk outline
    'b': (112, 88, 60),     # husk base
    'B': (150, 122, 84),    # husk highlight
    's': (72, 54, 32),      # husk shade
    'a': (108, 186, 96),    # green accent (spirit herb)
    'p': (110, 118, 214),   # blue-violet accent (gentian)
    'r': (198, 74, 74),     # crimson accent (higanbana)
    'v': (176, 108, 220),   # violet accent (magic mushroom)
    'w': (232, 214, 172),   # seed kernels
}

PAL_CROP_1 = {  # green-stem family (spirit herb)
    '.': None,
    'o': (34, 58, 30),
    'g': (96, 168, 82),
    'h': (152, 214, 122),
    's': (58, 104, 52),
    'W': (238, 236, 196),
}

PAL_CROP_2 = {  # blue-violet family (gentian)
    '.': None,
    'o': (38, 40, 86),
    'g': (108, 122, 214),
    'h': (166, 176, 246),
    's': (62, 68, 142),
    'W': (226, 232, 250),
}

PAL_CROP_3 = {  # crimson family (higanbana spider lily)
    '.': None,
    'o': (70, 22, 22),
    'g': (198, 74, 74),
    'h': (240, 152, 130),
    's': (128, 40, 40),
    'W': (252, 226, 170),
}

PAL_CROP_4 = {  # violet mushroom family
    '.': None,
    'o': (46, 24, 66),
    'g': (150, 100, 200),
    'h': (206, 168, 244),
    's': (92, 56, 130),
    'W': (238, 220, 250),
}

PAL_SOIL = {  # farmland is fully opaque: no '.' entries at all
    'D': (78, 56, 38),      # dark furrow
    'd': (104, 78, 52),     # base soil
    'l': (128, 98, 66),     # left/top highlight
    'r': (88, 64, 42),      # right/bottom shade
}

# ------------------------------------------------------------------ seeds

TEX_SEED_HERB = [
    "................",
    "................",
    "................",
    ".....oooooo.....",
    "....obbbbbbo....",
    "...obBbbbbbbo...",
    "..obbwaawbbbo...",
    "..obbwaawbbbo...",
    "..obbbbbbbbo....",
    "..obsbbbbbbo....",
    "..obbbbbbbbo....",
    "...osbbbbbbo....",
    "....ossssso.....",
    ".....oooooo.....",
    "................",
    "................",
]

TEX_SEED_GENTIAN = [
    "................",
    "................",
    "................",
    ".....oooooo.....",
    "....obbbbbbo....",
    "...obBppbbbbo...",
    "..obbbppppbbo...",
    "..obbppwppbbo...",
    "..obbbppppbbo...",
    "..obbbbbbbbbo...",
    "..obsbbbbbbo....",
    "...osbbbbbbo....",
    "....ossssso.....",
    ".....oooooo.....",
    "................",
    "................",
]

TEX_SEED_HIGANBANA = [
    "................",
    "................",
    "................",
    ".....oooooo.....",
    "....obbbbbbo....",
    "...obBrbbbbbbo..",
    "..obbbrrrbbbbo..",
    "..obbrrwrrbbbo..",
    "..obbrrrrrbbbo..",
    "..obbbbbbbbbo...",
    "..obsbbbbbbo....",
    "...osbbbbbbo....",
    "....ossssso.....",
    ".....oooooo.....",
    "................",
    "................",
]

TEX_SEED_MUSHROOM = [
    "................",
    "................",
    "................",
    ".....oooooo.....",
    "....obbbbbbo....",
    "...obBvbbbbbo...",
    "..obbvvvvbbbo...",
    "..obbvvwvbbbo...",
    "..obbvvvvbbbo...",
    "..obbbbbbbbbo...",
    "..obsbbbbbbo....",
    "...osbbbbbbo....",
    "....ossssso.....",
    ".....oooooo.....",
    "................",
    "................",
]

# ------------------------------------------------------------------ crop stage 0: sprout

STAGE_HERB_0 = [
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
    "......o.........",
    ".....ogo........",
    "....ogggo.......",
    "....ogggo.......",
    ".....oso........",
    "......o.........",
    "................",
    "................",
]

STAGE_GENTIAN_0 = [
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
    "......o.........",
    ".....ogo........",
    "....ogggo.......",
    "....ogggo.......",
    ".....oso........",
    "......o.........",
    "................",
    "................",
]

STAGE_HIGANBANA_0 = [
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
    "......o.........",
    ".....oso........",
    "....osso........",
    "....osso........",
    ".....oso........",
    "......o.........",
    "................",
    "................",
]

STAGE_MUSHROOM_0 = [
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
    "......o.........",
    ".....ogo........",
    "....ogggo.......",
    "....ogggo.......",
    ".....ogo........",
    "......o.........",
    "......o.........",
    "................",
    "................",
]

# ------------------------------------------------------------------ crop stage 1: leafy

STAGE_HERB_1 = [
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
    ".....o...o......",
    "....ogo.ogo.....",
    "...ogggogggo....",
    "...ogggogggo....",
    "....ogo.ogo.....",
    ".....oso.so.....",
    ".....oso.so.....",
    "......o.o.......",
    "................",
    "................",
]

STAGE_GENTIAN_1 = [
    "................",
    "................",
    "................",
    "................",
    "................",
    "......o.........",
    "......o.........",
    "....o.o.o.......",
    "...ogo.ogo......",
    "..ogggogggo.....",
    "..ogggogggo.....",
    "...ogo.ogo......",
    "....o...o.......",
    "......o.o.......",
    "......oso.......",
    ".......o........",
]

STAGE_HIGANBANA_1 = [
    "................",
    "................",
    "................",
    "................",
    "................",
    "......o.........",
    "......o.........",
    "....o.o.o.......",
    "...oso.oso......",
    "..ogggoggo......",
    ".osso.o.o.osso..",
    ".osso.o.o.osso..",
    "...ogo.ogo......",
    "....o...o.......",
    "......oso.......",
    ".......o........",
]

STAGE_MUSHROOM_1 = [
    "................",
    "................",
    "................",
    "......ooo.......",
    ".....ogggo......",
    "....ogggggo.....",
    "....oggggggo....",
    ".....ogggo......",
    "......ooo.......",
    ".......o........",
    "....o..o..o.....",
    "...ogo.ogo.ogo..",
    "...ogo.ogo.ogo..",
    "....o...o...o...",
    ".......o.o......",
    "................",
]

# ------------------------------------------------------------------ crop stage 2: mature + bloom

STAGE_HERB_2 = [
    "................",
    "......ooo.......",
    ".....oWWWo......",
    "....oWhWhWo.....",
    ".....oWWWo......",
    "......ooo.......",
    "......o.o.......",
    ".....o...o......",
    "....og.gg.go....",
    "...oggo.oggo....",
    "...oggo.oggo....",
    "....og.gg.go....",
    ".....o...o......",
    "......o.o.......",
    "......oso.......",
    ".......o........",
]

STAGE_GENTIAN_2 = [
    "................",
    "......ooo.......",
    ".....oWWWo......",
    "....oWhWhWo.....",
    ".....oWWWo......",
    "......ooo.......",
    "......o.o.......",
    ".....o...o......",
    "....og.gg.go....",
    "...oggo.oggo....",
    "..ogggo.ogggo...",
    "..ogggo.ogggo...",
    "...oggo.oggo....",
    "....og.gg.go....",
    "......o.o.......",
    "......oso.......",
]

STAGE_HIGANBANA_2 = [
    "................",
    "......ooo.......",
    ".....ogggo......",
    "....ogggggo.....",
    "...ogWWWWWWgo...",
    "...ogWWWWWWgo...",
    "....ogggggo.....",
    ".....ogggo......",
    "......ooo.......",
    ".......o........",
    "....o..o..o.....",
    "...og..o..ggo...",
    ".osso.o.o.osso..",
    ".osso.o.o.osso..",
    "...og..o..go....",
    "......oso.......",
]

STAGE_MUSHROOM_2 = [
    "................",
    "......ooo.......",
    ".....ohhho......",
    "....ohhhhhho....",
    "...ohhWhWhhho...",
    "...ohhWWWWhho...",
    "....ohhWhhho....",
    ".....ohhhho.....",
    "......ooo.......",
    ".......o........",
    ".....o.o.o......",
    "....og.gg.go....",
    "...oggo.ogggo...",
    "...oggo.ogggo...",
    "....ogo.oggo....",
    "......oso.......",
]

# ------------------------------------------------------------------ spirit soil farmland

TEX_FARMLAND_TOP = [
    "DDdDDdDDlDDdDlDD",
    "DDdDDdDlDDdDlDDD",
    "dDDlDdDDDlDDdDDd",
    "DdlDDdDDdDlDdDDD",
    "DDDdDdDDlDDdDlDd",
    "dDDdDDdDDdDlDDDd",
    "DDdDlDDdDDlDDdDD",
    "DDdDDdDlDDdDlDDD",
    "dDDlDdDDdDlDDdDd",
    "DDdDDdDDdDlDDdDD",
    "DdDlDDdDDdDlDDdD",
    "DDdDDdDlDDdDlDDD",
    "dDDdDDdDDlDDdDDd",
    "DDlDdDDdDlDdDDDD",
    "DDdDDdDlDDdDlDDd",
    "dDDdDlDDdDlDDdDd",
]

TEX_FARMLAND_SIDE = [
    "dddddddddddddddd",
    "dlllldllldlllldl",
    "drrrrdrrrdrrrrdr",
    "dddddddddddddddd",
    "dlllldllldlllldl",
    "drrrrdrrrdrrrrdr",
    "dDDdDDdDDdDDdDDd",
    "dlllldllldlllldl",
    "drrrrdrrrdrrrrdr",
    "dDDdDDdDDdDDdDDd",
    "dlllldllldlllldl",
    "drrrrdrrrdrrrrdr",
    "dDDdDDdDDdDDdDDd",
    "dlllldllldlllldl",
    "drrrrdrrrdrrrrdr",
    "dddddddddddddddd",
]

TEXES = {
    "item/spirit_herb_seeds": TEX_SEED_HERB,
    "item/gentian_seeds": TEX_SEED_GENTIAN,
    "item/higanbana_seeds": TEX_SEED_HIGANBANA,
    "item/magic_mushroom_spores": TEX_SEED_MUSHROOM,
    "block/spirit_herb_crop_0": STAGE_HERB_0,
    "block/spirit_herb_crop_1": STAGE_HERB_1,
    "block/spirit_herb_crop_2": STAGE_HERB_2,
    "block/gentian_crop_0": STAGE_GENTIAN_0,
    "block/gentian_crop_1": STAGE_GENTIAN_1,
    "block/gentian_crop_2": STAGE_GENTIAN_2,
    "block/higanbana_crop_0": STAGE_HIGANBANA_0,
    "block/higanbana_crop_1": STAGE_HIGANBANA_1,
    "block/higanbana_crop_2": STAGE_HIGANBANA_2,
    "block/magic_mushroom_crop_0": STAGE_MUSHROOM_0,
    "block/magic_mushroom_crop_1": STAGE_MUSHROOM_1,
    "block/magic_mushroom_crop_2": STAGE_MUSHROOM_2,
    "block/spirit_soil_farmland_top": TEX_FARMLAND_TOP,
    "block/spirit_soil_farmland_side": TEX_FARMLAND_SIDE,
}

# gen_tex.py resolves a per-entry palette via PAL_<key with / as _>, so build them
# from the named groups above.
_PALETTES = {
    "item/spirit_herb_seeds": PAL_SEEDS,
    "item/gentian_seeds": PAL_SEEDS,
    "item/higanbana_seeds": PAL_SEEDS,
    "item/magic_mushroom_spores": PAL_SEEDS,
    "block/spirit_herb_crop_0": PAL_CROP_1,
    "block/spirit_herb_crop_1": PAL_CROP_1,
    "block/spirit_herb_crop_2": PAL_CROP_1,
    "block/gentian_crop_0": PAL_CROP_2,
    "block/gentian_crop_1": PAL_CROP_2,
    "block/gentian_crop_2": PAL_CROP_2,
    "block/higanbana_crop_0": PAL_CROP_3,
    "block/higanbana_crop_1": PAL_CROP_3,
    "block/higanbana_crop_2": PAL_CROP_3,
    "block/magic_mushroom_crop_0": PAL_CROP_4,
    "block/magic_mushroom_crop_1": PAL_CROP_4,
    "block/magic_mushroom_crop_2": PAL_CROP_4,
    "block/spirit_soil_farmland_top": PAL_SOIL,
    "block/spirit_soil_farmland_side": PAL_SOIL,
}
for _key, _pal in _PALETTES.items():
    globals()["PAL_" + _key.replace("/", "_")] = _pal
