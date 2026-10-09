# spirit_iron / star_silver tools and armour textures (16x16)
#
# Two tiers share one silhouette set (vanilla-derived shapes) and are told apart purely
# by palette: spirit iron is a desaturated pale cyan-grey, star silver is a warm bright
# silver. Each pick/axe/shovel/hoe/sword map is drawn on the vanilla 16x16 tool canvas.
#
# Item maps are transparent-backed ('.'), armour layer maps are fully opaque with
# NO '.' entries -- vanilla rejects transparent armour layers.

PAL_IRON = {
    '.': None,
    'o': (52, 62, 68),       # outline
    'b': (128, 152, 162),    # base metal
    'h': (186, 214, 222),    # highlight
    's': (82, 104, 116),     # shade
    'g': (110, 78, 38),      # wooden handle dark
    'G': (158, 116, 56),     # wooden handle light
}

PAL_SILVER = {
    '.': None,
    'o': (56, 50, 34),       # outline (warm dark)
    'b': (214, 208, 186),    # base metal
    'h': (250, 246, 226),    # highlight
    's': (158, 150, 124),    # shade
    'g': (92, 62, 34),       # handle dark
    'G': (142, 100, 52),     # handle light
}

# ---------------------------------------------------------------- tools

TEX_PICKAXE = [
    "................",
    ".........oooo...",
    "........obbbbo..",
    ".......obhhbbo..",
    "......obhhbbbo..",
    ".....obbbbbbbo..",
    "....obbo.obbbo..",
    "...obbo...obbo..",
    "..obbbo...obbbo.",
    "..obbbbo.obbbbo.",
    "..o.obbbbbbbo.o.",
    ".....ogggggo....",
    "....ogGGGGGgo...",
    "....ogGGGGGGgo..",
    ".....ogggggo....",
    "................",
]

TEX_AXE = [
    "................",
    "......oooo......",
    ".....obbbbo.....",
    "....obhhbbbo....",
    "...obhhbbbbbo...",
    "...obbbbbbbo....",
    "...obbbbbbbo....",
    "...obbbbbbbo....",
    "....obbbbo......",
    ".....obbbo..o...",
    "......ogbbo.o...",
    ".....ogGGo.o....",
    "....ogGGGo......",
    "....ogGGGGo.....",
    ".....ogggGo.....",
    "................",
]

TEX_SHOVEL = [
    "................",
    "......oooo......",
    ".....obbbo......",
    "....obhbbo......",
    "....obbbo.......",
    "....obbbo.......",
    "...obbbo........",
    "...obbo.o.......",
    "..obbbo.o.......",
    "..obbbo.o.......",
    "..obbbo.o.......",
    "..obbo..o.......",
    "..oobbbooo......",
    ".obbbbbbbbo.....",
    ".obbbbbsbbo.....",
    "..oooooooo......",
]

TEX_HOE = [
    "................",
    ".....oooo.......",
    "....obbbo.......",
    "...obhbbo.......",
    "..obbbbo........",
    "..obbbo.........",
    ".obbbo..........",
    ".obbo...........",
    ".obbbo..........",
    "obbbbo..........",
    "obbbbo..........",
    ".obbo...........",
    ".oobbbooo.......",
    "obbbbbbbbo......",
    "obbbbbsboo......",
    ".oooooooo.......",
]

TEX_SWORD = [
    "................",
    "........oooo....",
    ".......obbbbo...",
    "......obhhbbo...",
    ".....obhhbbbo...",
    "....obhhbbbbo...",
    "...obbbbbbbo....",
    "...obbbbbbbo....",
    "..obbbbbbo......",
    ".obbbbbbo.......",
    ".obbo..obbo.....",
    ".o.oo.ogbbo.....",
    ".....ogGGGbo....",
    "....ogGGGGbo....",
    ".....oggGGbo....",
    "......oooo......",
]

# ---------------------------------------------------------------- armour layers
# Fully opaque, 32x32 canvas, legacy 1.8 layout mirrored left/right.

PAL_ARMOUR_IRON = {
    'o': (46, 56, 62),
    'b': (122, 146, 156),
    'h': (178, 208, 218),
    's': (78, 100, 112),
    'p': (66, 84, 94),
}

PAL_ARMOUR_SILVER = {
    'o': (54, 48, 32),
    'b': (210, 204, 182),
    'h': (250, 246, 226),
    's': (154, 146, 120),
    'p': (170, 162, 134),
}

HELMET_1 = [
    "oooooooooooooooooooooooooooooooo",
    "oooooooooooooooooooooooooooooooo",
    "oooooooooooooooooooooooooooooooo",
    "oooooooooooooooooooooooooooooooo",
    "oooooooooooooooooooooooooooooooo",
    "oooooooooooooooooooooooooooooooo",
    "oooooooooooooooooooooooooooooooo",
    "oooooooooooooooooooooooooooooooo",
    "oooooooooooooooooooooooooooooooo",
    "ooooooobbbbbbbbbbbbbbbbbbbbooooo",
    "ooooobbbbbbbbbbbbbbbbbbbbbbboooo",
    "ooobbbbbbbbbbbbbbbbbbbbbbbbbbboo",
    "oobbbbbbbbbbbbbbbbbbbbbbbbbbbbboo",
    "oobbbbbbbbbbbbbbbbbbbbbbbbbbbbboo",
    "oobbhhhhhhbbbbbbbbbbbbbbbbbbbbboo",
    "oobbhhhhhhbbbbbbbbbbbbbbbbbbbbboo",
    "oobbhhhhhhbbbbbbbbbbbbbbbbbbbbboo",
    "oobbhhhhhhbbbbbbbbbbbbbbbbbbbbboo",
    "oobbhhhhhhbbbbbbbbbbbbbbbbbbbbboo",
    "oobbhhhhhhbbbbbbbbbbbbbbbbbbbbboo",
    "oobbhhhhhhbbbbbbbbbbbbbbbbbbbbboo",
    "oobbbbbbbbbbbbbbbbbbbbbbbbbbbbboo",
    "oobbbbbbbbbbbbbbbbbbbbbbbbbbbbboo",
    "ooobbbbbbbbbbbbbbbbbbbbbbbbbbboo",
    "ooooobbbbbbbbbbbbbbbbbbbbbbbbboo",
    "ooooooobbbbbbbbbbbbbbbbbbbbooooo",
    "oooooooooooooooooooooooooooooooo",
    "oooooooooooooooooooooooooooooooo",
    "oooooooooooooooooooooooooooooooo",
    "oooooooooooooooooooooooooooooooo",
    "oooooooooooooooooooooooooooooooo",
    "oooooooooooooooooooooooooooooooo",
    "oooooooooooooooooooooooooooooooo",
]

CHEST_1 = [
    "oooooooooooooooooooooooooooooooo",
    "oooooooooooooooooooooooooooooooo",
    "oooooooooooooooooooooooooooooooo",
    "oooooooooooooooooooooooooooooooo",
    "oooooooooooooooooooooooooooooooo",
    "oooooooooooooooooooooooooooooooo",
    "oooooooooooooooooooooooooooooooo",
    "oooooooooooooooooooooooooooooooo",
    "oooooooooooooooooooooooooooooooo",
    "oooooooooooooooooooooooooooooooo",
    "oooooooooooooooooooooooooooooooo",
    "oooobbbbbbbbbbbbbbbbbbbbbbbbbooo",
    "oobbbbbbbbbbbbbbbbbbbbbbbbbbbboo",
    "oobbbbbbbbbbbbbbbbbbbbbbbbbbbboo",
    "oobbbbbbbbbbbbbbbbbbbbbbbbbbbboo",
    "oobbbbhhbbbbbbbbbbbbbbbbbhhbbbboo",
    "oobbbbhhbbbbbbbbbbbbbbbbbhhbbbboo",
    "oobbbbhhbbbbbbbbbbbbbbbbbhhbbbboo",
    "oobbbbhhbbbbbbbbbbbbbbbbbhhbbbboo",
    "oobbbbhhbbbbbbbbbbbbbbbbbhhbbbboo",
    "oobbbbbbbbbbbbbbbbbbbbbbbbbbbboo",
    "oobbbbbbbbbbbbbbbbbbbbbbbbbbbboo",
    "oobbbbbbssbbbbbbbbbbbbbbssbbbbboo",
    "oobbbbbbssbbbbbbbbbbbbbbssbbbbboo",
    "oobbbbbbbbbbbbbbbbbbbbbbbbbbbboo",
    "oobbbbbbbbbbbbbbbbbbbbbbbbbbbboo",
    "ooooobbbbbbbbbbbbbbbbbbbbbbbbboo",
    "ooooobbbbbbbbbbbbbbbbbbbbbbbbboo",
    "oooooobbbbbbbbbbbbbbbbbbbbbbboo",
    "oooooooooooooooooooooooooooooooo",
    "oooooooooooooooooooooooooooooooo",
    "oooooooooooooooooooooooooooooooo",
    "oooooooooooooooooooooooooooooooo",
]

LEGS_1 = [
    "oooooooooooooooooooooooooooooooo",
    "oooooooooooooooooooooooooooooooo",
    "oooooooooooooooooooooooooooooooo",
    "oooooooooooooooooooooooooooooooo",
    "oooooooooooooooooooooooooooooooo",
    "oooooooooooooooooooooooooooooooo",
    "oooooooooooooooooooooooooooooooo",
    "oooooooooooooooooooooooooooooooo",
    "oooooooooooooooooooooooooooooooo",
    "oooooooooooooooooooooooooooooooo",
    "oooooooooooooooooooooooooooooooo",
    "oooooooooooooooooooooooooooooooo",
    "oobbbbbbbbbbooooooobbbbbbbbbboo",
    "oobbbbbbbbbbooooooobbbbbbbbbboo",
    "oobbbbbbbbbbooooooobbbbbbbbbboo",
    "oobbbbhhbbbbooooooobbbbhhbbbboo",
    "oobbbbhhbbbbooooooobbbbhhbbbboo",
    "oobbbbhhbbbbooooooobbbbhhbbbboo",
    "oobbbbhhbbbbooooooobbbbhhbbbboo",
    "oobbbbbbbbbbooooooobbbbbbbbbboo",
    "oobbbbbbbbbbooooooobbbbbbbbbboo",
    "oobbbbbbbbbbooooooobbbbbbbbbboo",
    "oobbbbbbssbbooooooobbbssbbbbboo",
    "oobbbbbbssbbooooooobbbssbbbbboo",
    "oobbbbbbbbbbooooooobbbbbbbbbboo",
    "oobbbbbbbbbbooooooobbbbbbbbbboo",
    "ooobbbbbbbbbooooooobbbbbbbbboo",
    "ooobbbbbbbbbooooooobbbbbbbbboo",
    "ooobbbbbbbbbooooooobbbbbbbbboo",
    "oooooooooooooooooooooooooooooooo",
    "oooooooooooooooooooooooooooooooo",
    "oooooooooooooooooooooooooooooooo",
    "oooooooooooooooooooooooooooooooo",
]

BOOTS_1 = [
    "oooooooooooooooooooooooooooooooo",
    "oooooooooooooooooooooooooooooooo",
    "oooooooooooooooooooooooooooooooo",
    "oooooooooooooooooooooooooooooooo",
    "oooooooooooooooooooooooooooooooo",
    "oooooooooooooooooooooooooooooooo",
    "oooooooooooooooooooooooooooooooo",
    "oooooooooooooooooooooooooooooooo",
    "oooooooooooooooooooooooooooooooo",
    "oooooooooooooooooooooooooooooooo",
    "oooooooooooooooooooooooooooooooo",
    "oooooooooooooooooooooooooooooooo",
    "oooooooooooooooooooooooooooooooo",
    "oooooooooooooooooooooooooooooooo",
    "oooooooooooooooooooooooooooooooo",
    "oooooooooooooooooooooooooooooooo",
    "oooooooooooooooooooooooooooooooo",
    "oooooooooooooooooooooooooooooooo",
    "oooooooooooooooooooooooooooooooo",
    "oooooooooooooooooooooooooooooooo",
    "oooooooooooooooooooooooooooooooo",
    "oooooooooooooooooooooooooooooooo",
    "oooooooooooooooooooooooooooooooo",
    "oooooooooooooooooooooooooooooooo",
    "oooooooooooooooooooooooooooooooo",
    "ooobbbbbbbbbbooooooobbbbbbbbbboo",
    "oobbbbbbbbbbbooooooobbbbbbbbbbbo",
    "oobbbbbbbbbbbooooooobbbbbbbbbbbo",
    "oobbbbhhbbbbbooooooobbbbhhbbbbooo",
    "oobbbbhhbbbbbooooooobbbbhhbbbbooo",
    "oobbbbbbbbbbbooooooobbbbbbbbbbbo",
    "oooooooooooooooooooooooooooooooo",
    "oooooooooooooooooooooooooooooooo",
]

# gen_tex.py expects TEXES = {name: rows} and a per-entry palette named
# PAL_<key with / as _>, so the rows and palettes are split into two maps here.
_ROWS = {
    # tools
    "item/spirit_iron_pickaxe": TEX_PICKAXE,
    "item/spirit_iron_axe": TEX_AXE,
    "item/spirit_iron_shovel": TEX_SHOVEL,
    "item/spirit_iron_hoe": TEX_HOE,
    "item/spirit_iron_sword": TEX_SWORD,
    "item/star_silver_pickaxe": TEX_PICKAXE,
    "item/star_silver_axe": TEX_AXE,
    "item/star_silver_shovel": TEX_SHOVEL,
    "item/star_silver_hoe": TEX_HOE,
    "item/star_silver_sword": TEX_SWORD,
    # armour layer 1
    "models/armor/spirit_iron_layer_1": HELMET_1 + CHEST_1,
    "models/armor/star_silver_layer_1": HELMET_1 + CHEST_1,
    # armour layer 2
    "models/armor/spirit_iron_layer_2": LEGS_1 + BOOTS_1,
    "models/armor/star_silver_layer_2": LEGS_1 + BOOTS_1,
}

_PALETTES = {
    "item/spirit_iron_pickaxe": PAL_IRON,
    "item/spirit_iron_axe": PAL_IRON,
    "item/spirit_iron_shovel": PAL_IRON,
    "item/spirit_iron_hoe": PAL_IRON,
    "item/spirit_iron_sword": PAL_IRON,
    "item/star_silver_pickaxe": PAL_SILVER,
    "item/star_silver_axe": PAL_SILVER,
    "item/star_silver_shovel": PAL_SILVER,
    "item/star_silver_hoe": PAL_SILVER,
    "item/star_silver_sword": PAL_SILVER,
    "models/armor/spirit_iron_layer_1": PAL_ARMOUR_IRON,
    "models/armor/star_silver_layer_1": PAL_ARMOUR_SILVER,
    "models/armor/spirit_iron_layer_2": PAL_ARMOUR_IRON,
    "models/armor/star_silver_layer_2": PAL_ARMOUR_SILVER,
}

TEXES = _ROWS

for _key, _pal in _PALETTES.items():
    globals()["PAL_" + _key.replace("/", "_")] = _pal
