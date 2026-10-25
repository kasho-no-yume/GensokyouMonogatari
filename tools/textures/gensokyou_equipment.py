# spirit_iron / star_silver / mermaid_scale / dragon_scale equipment textures
# (add-gensokyou-material-uses, add-early-material-uses).
#
# Silhouettes were traced from the vanilla iron tool/armour sprites (the project's
# established "vanilla-derived shape, mod palette" convention) and are stored here as
# plain ASCII maps -- no Mojang binaries live in the repo. The two tiers share every
# silhouette and are told apart purely by palette:
#   spirit iron -> pale desaturated cyan-grey
#   star silver -> warm bright silver
#
# Item maps are transparent-backed ('.'). Armour LAYER maps are 64x32 (the vanilla
# humanoid armour sheet size; the previous 32x32 sheets were wrong and made the armour
# render with broken UVs). Unused UV cells stay transparent, as in vanilla.
# Keys: o=outline s=shade b=base h=highlight H=brightest g/G=wood handle.

PAL_IRON = {
    '.': None,
    'o': (14, 36, 49),
    's': (31, 85, 107),
    'b': (64, 154, 178),
    'h': (132, 221, 233),
    'g': (92, 64, 36),
    'G': (150, 110, 58),
}

PAL_SILVER = {
    '.': None,
    'o': (35, 41, 56),
    's': (87, 100, 123),
    'b': (157, 174, 197),
    'h': (215, 230, 244),
    'g': (92, 64, 36),
    'G': (150, 110, 58),
}

# mermaid scale -> cool sea-green / pearl
PAL_MERMAID = {
    '.': None,
    'o': (16, 46, 50),
    's': (32, 105, 106),
    'b': (74, 176, 166),
    'h': (151, 226, 210),
    'g': (92, 64, 36),
    'G': (150, 110, 58),
}

# dragon scale -> deep crimson / molten gold
PAL_DRAGON = {
    '.': None,
    'o': (44, 12, 18),
    's': (116, 30, 34),
    'b': (176, 58, 48),
    'h': (238, 146, 84),
    'g': (92, 64, 36),
    'G': (150, 110, 58),
}

# ---------------------------------------------------------------- tools

TEX_PICKAXE = [
    "................",
    "................",
    "......sssss.....",
    ".....shhbbhsgG..",
    "......sooobbGg..",
    "..........ghbo..",
    ".........gGgbho.",
    "........gGg.obo.",
    ".......gGg..obo.",
    "......gGg...oho.",
    ".....gGg....oho.",
    "....gGg......o..",
    "...gGg..........",
    "..gGg...........",
    "..gg............",
    "................",
]

TEX_AXE = [
    "................",
    ".........ss.....",
    "........shhs....",
    ".......shbhs....",
    "......shbbbgG...",
    "......ohhbbbg...",
    ".......oogbbbo..",
    "........gGgbbo..",
    ".......gGg.oo...",
    "......gGg.......",
    ".....gGg........",
    "....gGg.........",
    "...gGg..........",
    "..gGg...........",
    "..gg............",
    "................",
]

TEX_SHOVEL = [
    "................",
    "................",
    "...........sso..",
    "..........shhbo.",
    ".........shhbho.",
    "........shhbhho.",
    ".........gbhho..",
    "........gGgho...",
    ".......gGg.o....",
    "......gGg.......",
    ".....gGg........",
    "....gGg.........",
    "..ggGg..........",
    "..gGg...........",
    "...gg...........",
    "................",
]

TEX_HOE = [
    "................",
    ".......sss......",
    "......shhhs.....",
    ".......oobhsgG..",
    ".........obbGo..",
    "..........ghbo..",
    ".........gGoo...",
    "........gGo.....",
    ".......gGo......",
    "......gGo.......",
    ".....gGo........",
    "....gGo.........",
    "...gGo..........",
    "..gGo...........",
    "..oo............",
    "................",
]

TEX_SWORD = [
    ".............sss",
    "............shho",
    "...........shbho",
    "..........shbho.",
    ".........shbho..",
    "........shbho...",
    "..ss...shbho....",
    "..sss.shbho.....",
    "...sbohbho......",
    "...sbbsho.......",
    "....ssso........",
    "...gGosso.......",
    "..gGg.ooso......",
    "ssGg....oo......",
    "sso.............",
    "ooo.............",
]

# ---------------------------------------------------------------- armour item icons

TEX_HELMET = [
    "................",
    "................",
    "................",
    ".....ssssss.....",
    "....sbbbbbso....",
    "...sbhhhbbbso...",
    "...sbhhbbbbbo...",
    "...sbbsssosbo...",
    "...sbsooooobo...",
    "...sbsoooooso...",
    "...sbooooooso...",
    "....so....oo....",
    "................",
    "................",
    "................",
    "................",
]

TEX_CHESTPLATE = [
    "................",
    "................",
    ".sssss....sssss.",
    ".shhbs....shhbs.",
    ".shbbbs..sbhbbs.",
    ".sbbbhbssbbbbbs.",
    ".ssbbhhhhbbbbss.",
    ".ooshhhbbbbbsoo.",
    "...ohhbbbbbbo...",
    "...obhbbbbbbo...",
    "...obbbbbbbbo...",
    "...obbbbbbbbo...",
    "...osbbbbbbso...",
    "....osbbbbso....",
    ".....oooooo.....",
    "................",
]

TEX_LEGGINGS = [
    "................",
    "................",
    "....ssssssso....",
    "...shhhhhbbso...",
    "...shhbbbbbbo...",
    "...shbbbbbbbo...",
    "...shbboosbbo...",
    "...sbbo..obbo...",
    "...sbbo..sbbo...",
    "...sbbo..sbbo...",
    "...sbbo..obbo...",
    "...sbso..obso...",
    "...osso..osso...",
    "...oooo..oooo...",
    "................",
    "................",
]

TEX_BOOTS = [
    "................",
    "................",
    "................",
    "....sss..sss....",
    "...shho..shho...",
    "...shho..shbo...",
    "...shbo..sbbo...",
    "...sbbo..sbbo...",
    "...sbbo..sbbo...",
    "..sbbbo..sbbbo..",
    ".sbbbso..ssbbbo.",
    ".sbbsoo..oobbso.",
    ".sooo......oooo.",
    "................",
    "................",
    "................",
]

# ---------------------------------------------------------------- armour layer sheets
# 64x32 vanilla humanoid layout; layer 1 = helmet + chestplate, layer 2 = leggings + boots.

PAL_ARMOUR_IRON = {
    '.': None,
    'o': (14, 36, 49),
    's': (31, 85, 107),
    'b': (64, 154, 178),
    'h': (132, 221, 233),
    'H': (200, 245, 250),
}

PAL_ARMOUR_SILVER = {
    '.': None,
    'o': (35, 41, 56),
    's': (87, 100, 123),
    'b': (157, 174, 197),
    'h': (215, 230, 244),
    'H': (248, 252, 255),
}

PAL_ARMOUR_MERMAID = {
    '.': None,
    'o': (16, 46, 50),
    's': (32, 105, 106),
    'b': (74, 176, 166),
    'h': (151, 226, 210),
    'H': (214, 250, 240),
}

PAL_ARMOUR_DRAGON = {
    '.': None,
    'o': (44, 12, 18),
    's': (116, 30, 34),
    'b': (176, 58, 48),
    'h': (238, 146, 84),
    'H': (255, 214, 150),
}

LAYER_1 = [
    "........oooooooo................................................",
    "........oHHHHhho................................................",
    "........oHbhhhho................................................",
    "........oHhbbbho................................................",
    "........oHhbhhho................................................",
    "........ohhbhhHo................................................",
    "........ohhhhHHo................................................",
    "........obbbbbbo................................................",
    "oooooooobbbbbbbboooooooooooooooo................................",
    "oHHhhhhbbHhhhhbbbhhhhHHbbHHHhhho................................",
    "oHhbbhHbboohboobbHhbbhHbbHhhbbho................................",
    "ohbbooooo..oo..ooooobbhbbHbbbbho................................",
    "oooo.......oo.......oooobhbhhhHo................................",
    "........................oohhHHoo................................",
    "..........................oooo..................................",
    "................................................................",
    "........oooo................................oooo................",
    "........ohbo................................oHHo................",
    "........ohho................................oHbo................",
    "........oooo................................obbo................",
    "................oooooo....oooooooo....oooooobbbboooooooo........",
    "................oHhbbHo..ohbbHhbbHooooHbbHHbbHHbbHHbbHHo........",
    "................oHhbbHHooHhbbHhbbHHhhHhbbHhbbbHbbhhbbhbo........",
    "................ohhbbHhhHhhbbhhbbHhhhhbbbhhbbhhbbbhbbhho........",
    "................ohhbbHhhhhhbbhhbbHhhhbhboooooooooooooooo........",
    "................ohbbbhhhhhhbbhbbbhhhhhho........................",
    "oooooooooooooooobhhbbhhhhhhbbhhbbhhhbbho........................",
    "oHHbbHHbbHHbbHHbbhbbbhhhhhhbbhbbbhhhhhho........................",
    "ohhbbhhbbhhbbhhbooooobhhhhboooooohhbbbho........................",
    "obhbbhbbbhhbbbho.....obhhbo......oooooo.........................",
    "ohHbbHhbbhHbbhHo......oooo......................................",
    "oooooooooooooooo................................................",
]

LAYER_2 = [
    "................................................................",
    "................................................................",
    "................................................................",
    "................................................................",
    "................................................................",
    "................................................................",
    "................................................................",
    "................................................................",
    "................................................................",
    "................................................................",
    "................................................................",
    "................................................................",
    "................................................................",
    "................................................................",
    "................................................................",
    "................................................................",
    "....oooo........................................................",
    "....oHho........................................................",
    "....ohbo........................................................",
    "....obbo........................................................",
    "oooobbbboooooooo................................................",
    "obhbbhhbbhhbbhbo................................................",
    "obhbbhhbbhhbbhho................................................",
    "ohhbbhhbbhhbbhho................................................",
    "ohhbbhbbbhhbbhho................................................",
    "obhbbbhbbhbbbhbo................................................",
    "ohbbbhbbbhhbbbho................................................",
    "ohhbbbbbbbhbbhhboooooooooooooooooooooooo........................",
    "oooooooooooooooobHhbbHHHhhhbbHhbbHHHHhho........................",
    "................ohbbbHhhbbhbbhbbbHhhbbho........................",
    "................obhbbhbbbhhbbbhbbhbbbhho........................",
    "................oooooooooooooooooooooooo........................",
]

_ROWS = {
    "item/spirit_iron_pickaxe": TEX_PICKAXE,
    "item/spirit_iron_axe": TEX_AXE,
    "item/spirit_iron_shovel": TEX_SHOVEL,
    "item/spirit_iron_hoe": TEX_HOE,
    "item/spirit_iron_sword": TEX_SWORD,
    "item/spirit_iron_helmet": TEX_HELMET,
    "item/spirit_iron_chestplate": TEX_CHESTPLATE,
    "item/spirit_iron_leggings": TEX_LEGGINGS,
    "item/spirit_iron_boots": TEX_BOOTS,
    "item/star_silver_pickaxe": TEX_PICKAXE,
    "item/star_silver_axe": TEX_AXE,
    "item/star_silver_shovel": TEX_SHOVEL,
    "item/star_silver_hoe": TEX_HOE,
    "item/star_silver_sword": TEX_SWORD,
    "item/star_silver_helmet": TEX_HELMET,
    "item/star_silver_chestplate": TEX_CHESTPLATE,
    "item/star_silver_leggings": TEX_LEGGINGS,
    "item/star_silver_boots": TEX_BOOTS,
    "item/mermaid_scale_helmet": TEX_HELMET,
    "item/mermaid_scale_chestplate": TEX_CHESTPLATE,
    "item/mermaid_scale_leggings": TEX_LEGGINGS,
    "item/mermaid_scale_boots": TEX_BOOTS,
    "item/dragon_scale_helmet": TEX_HELMET,
    "item/dragon_scale_chestplate": TEX_CHESTPLATE,
    "item/dragon_scale_leggings": TEX_LEGGINGS,
    "item/dragon_scale_boots": TEX_BOOTS,
    "models/armor/spirit_iron_layer_1": LAYER_1,
    "models/armor/spirit_iron_layer_2": LAYER_2,
    "models/armor/star_silver_layer_1": LAYER_1,
    "models/armor/star_silver_layer_2": LAYER_2,
    "models/armor/mermaid_scale_layer_1": LAYER_1,
    "models/armor/mermaid_scale_layer_2": LAYER_2,
    "models/armor/dragon_scale_layer_1": LAYER_1,
    "models/armor/dragon_scale_layer_2": LAYER_2,
}

_PALETTES = {
    "item/spirit_iron_pickaxe": PAL_IRON,
    "item/spirit_iron_axe": PAL_IRON,
    "item/spirit_iron_shovel": PAL_IRON,
    "item/spirit_iron_hoe": PAL_IRON,
    "item/spirit_iron_sword": PAL_IRON,
    "item/spirit_iron_helmet": PAL_IRON,
    "item/spirit_iron_chestplate": PAL_IRON,
    "item/spirit_iron_leggings": PAL_IRON,
    "item/spirit_iron_boots": PAL_IRON,
    "item/star_silver_pickaxe": PAL_SILVER,
    "item/star_silver_axe": PAL_SILVER,
    "item/star_silver_shovel": PAL_SILVER,
    "item/star_silver_hoe": PAL_SILVER,
    "item/star_silver_sword": PAL_SILVER,
    "item/star_silver_helmet": PAL_SILVER,
    "item/star_silver_chestplate": PAL_SILVER,
    "item/star_silver_leggings": PAL_SILVER,
    "item/star_silver_boots": PAL_SILVER,
    "item/mermaid_scale_helmet": PAL_MERMAID,
    "item/mermaid_scale_chestplate": PAL_MERMAID,
    "item/mermaid_scale_leggings": PAL_MERMAID,
    "item/mermaid_scale_boots": PAL_MERMAID,
    "item/dragon_scale_helmet": PAL_DRAGON,
    "item/dragon_scale_chestplate": PAL_DRAGON,
    "item/dragon_scale_leggings": PAL_DRAGON,
    "item/dragon_scale_boots": PAL_DRAGON,
    "models/armor/spirit_iron_layer_1": PAL_ARMOUR_IRON,
    "models/armor/spirit_iron_layer_2": PAL_ARMOUR_IRON,
    "models/armor/star_silver_layer_1": PAL_ARMOUR_SILVER,
    "models/armor/star_silver_layer_2": PAL_ARMOUR_SILVER,
    "models/armor/mermaid_scale_layer_1": PAL_ARMOUR_MERMAID,
    "models/armor/mermaid_scale_layer_2": PAL_ARMOUR_MERMAID,
    "models/armor/dragon_scale_layer_1": PAL_ARMOUR_DRAGON,
    "models/armor/dragon_scale_layer_2": PAL_ARMOUR_DRAGON,
}

TEXES = _ROWS

for _key, _pal in _PALETTES.items():
    globals()["PAL_" + _key.replace("/", "_")] = _pal
