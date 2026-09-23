# gensokyo_materials - add-gensokyo-material-ladder 的 23 个新物品贴图 (16x16)
# 共用若干形状（岩石/土堆/原木/宝石/鳞/草药/花/蘑菇/瓶/鱼/炭/符纸/裂片），逐物品换调色板。
# 调色板角色：o 描边 / b 高光 / a 基色 / c 暗部 / d 点缀 / i 墨字

ROCK = [
    "................",
    "................",
    "................",
    "................",
    "......oooo......",
    "....obbbbbbo....",
    "...obbaaaabbo...",
    "..obbaaaaaabbo..",
    ".obaaaaaaaaaabo.",
    ".obaaaaaaaaaabo.",
    ".ocaaaaaaaaaaco.",
    "..ocaaaaaaaaco..",
    "...occcccccco...",
    "....oooooooo....",
    "................",
    "................",
]

PILE = [
    "................",
    "................",
    "................",
    "................",
    "................",
    "......oooo......",
    "...oobbbbbboo...",
    "..obbaaaaaabbo..",
    ".obaaaaaaaaaabo.",
    ".obaaaaaaaaaabo.",
    ".ocaaaaaaaaaaco.",
    ".occcccccccccco.",
    "..oooooooooooo..",
    "................",
    "................",
    "................",
]

LOG = [
    "................",
    "................",
    "....oooooooo....",
    "...obbbbbbbbo...",
    "..obaaaaaaaabo..",
    ".obaaaccccaaabo.",
    ".obaaaccccaaabo.",
    "..obaaaaaaaabo..",
    "...obaaaaaabo...",
    "....oooooooo....",
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
]

GEM = [
    "................",
    "................",
    "................",
    "......oooo......",
    ".....obbbbo.....",
    "....obbaabbo....",
    "...obbaaaabbo...",
    "..obbaaaaaabbo..",
    ".obaaaaaaaaaabo.",
    ".obaaaaaaaaaabo.",
    ".ocaaaaaaaaaaco.",
    "..ocaaaaaaaaco..",
    "...ocaaaaaaco...",
    "....occcccco....",
    ".....oooooo.....",
    "................",
]

SCALE = [
    "................",
    "................",
    "................",
    "......oooo......",
    ".....obbbbo.....",
    "....obbaaabo....",
    "...obbaaaaabo...",
    "..obbaaaaaaabo..",
    "..obaaaaaaaabo..",
    "..ocaaaaaaaaco..",
    "...ocaaaaaaco...",
    "....ocaaaaco....",
    ".....occcco.....",
    "......oooo......",
    "................",
    "................",
]

HERB = [
    "................",
    "................",
    "................",
    "................",
    "........oooo....",
    "......oobbbbo...",
    ".....obbbbbbbo..",
    "....obbbbaaabo..",
    "...obbbaaaaaabo.",
    "...obbaaaaaaabo.",
    "...obaaaaaaaabo.",
    "....obaaaaaabo..",
    ".....obaaaabo...",
    "......oobboo....",
    "........oo......",
    "................",
]

FLOWER = [
    "................",
    "................",
    "................",
    "......o..o......",
    ".....ob..bo.....",
    "......obbo......",
    "...o..obbo..o...",
    "..obo.obbo.obo..",
    "...obobbbobo....",
    "....obbbbbbo....",
    ".....obbbbo.....",
    "......obbo......",
    ".......oo.......",
    ".......oo.......",
    ".......oo.......",
    "................",
]

MUSHROOM = [
    "................",
    "................",
    "................",
    "................",
    "....oooooooo....",
    "..oobbbbbbbboo..",
    ".obbaaaaaaaabbo.",
    ".obaaaaaaaaaabo.",
    "..obbaaaaaabbo..",
    "....ocaaaaco....",
    ".....obaaabo....",
    ".....obaaabo....",
    ".....obaaabo....",
    ".....oooooo.....",
    "................",
    "................",
]

BOTTLE = [
    "................",
    "................",
    ".......oooo.....",
    ".......obbo.....",
    ".......obbo.....",
    "......obbbbo....",
    ".....obaaaabo...",
    "....obaaaaaabo..",
    "....obaaaaaabo..",
    "....obaaaaaabo..",
    "...obaaaaaaaabo.",
    "...obaaaaaaaabo.",
    "....obaaaaaabo..",
    "....oooooooooo..",
    "................",
    "................",
]

FISH = [
    "................",
    "................",
    "................",
    "................",
    "................",
    "......oooo......",
    "....ooobbbboo...",
    "...obbbaaaabbo..",
    "..obbccaaaaaabo.",
    "..obcccaaaaabbo.",
    "...obccaaaaabo..",
    "....oobbbbbbo...",
    ".....oooooo.....",
    "................",
    "................",
    "................",
]

CHARCOAL = [
    "................",
    "................",
    "................",
    "................",
    "......oooo......",
    "....ooobbooo....",
    "...obbaaaaabo...",
    "..obaaaaaaaaabo.",
    "..obaaaaaaaaabo.",
    "...ocaaaaaaaaco.",
    "....occdaaacco..",
    ".....ocddddco...",
    "......occcco....",
    "......oooo......",
    "................",
    "................",
]

PAPER = [
    "................",
    "................",
    "..oooooooooooo..",
    "..obbbbbbbbbbo..",
    "..obaaaaaaaabo..",
    "..obadddddaabo..",
    "..obadiiiidabo..",
    "..obadiiiidabo..",
    "..obadddddaabo..",
    "..obaaaaaaaabo..",
    "..obaaaaaaaabo..",
    "..obaaaaaaaabo..",
    "..obbbbbbbbbbo..",
    "..oooooooooooo..",
    "................",
    "................",
]

RIFT = [
    "................",
    "................",
    "................",
    ".......oo.......",
    "......obbo......",
    ".....obbbo......",
    "....obbddbo.....",
    "....obdddbo.....",
    "...obddddddo....",
    "...obdddddbbo...",
    "....obdddbbo....",
    ".....obbbbo.....",
    "......obbo......",
    ".......oo.......",
    "................",
    "................",
]

PAL_item_cinnabar = {
    '.': None, 'o': (70, 10, 20), 'b': (255, 140, 140), 'a': (200, 45, 55),
    'c': (130, 25, 40), 'd': (255, 90, 90),
}
PAL_item_spirit_iron = {
    '.': None, 'o': (40, 50, 60), 'b': (205, 220, 235), 'a': (130, 150, 170),
    'c': (80, 100, 120),
}
PAL_item_spirit_iron_ore = {
    '.': None, 'o': (56, 56, 60), 'b': (150, 168, 185), 'a': (120, 118, 112),
    'c': (80, 78, 74),
}
PAL_item_star_silver_ore = {
    '.': None, 'o': (56, 58, 64), 'b': (232, 238, 248), 'a': (124, 124, 130),
    'c': (82, 82, 88),
}
PAL_item_star_silver = {
    '.': None, 'o': (70, 75, 95), 'b': (250, 252, 255), 'a': (200, 210, 228),
    'c': (140, 150, 175), 'd': (255, 255, 255),
}
PAL_item_oni_stone = {
    '.': None, 'o': (40, 32, 50), 'b': (180, 165, 195), 'a': (115, 100, 130),
    'c': (70, 60, 85),
}
PAL_item_spirit_soil = {
    '.': None, 'o': (50, 40, 30), 'b': (180, 160, 120), 'a': (125, 105, 75),
    'c': (80, 65, 45), 'd': (120, 210, 180),
}
PAL_item_porcelain_clay = {
    '.': None, 'o': (120, 115, 105), 'b': (248, 246, 240), 'a': (225, 222, 212),
    'c': (190, 185, 172),
}
PAL_item_higan_soil = {
    '.': None, 'o': (45, 22, 25), 'b': (175, 100, 100), 'a': (115, 62, 62),
    'c': (72, 36, 38),
}
PAL_item_moon_sand = {
    '.': None, 'o': (120, 110, 70), 'b': (252, 246, 205), 'a': (230, 220, 160),
    'c': (188, 176, 120),
}
PAL_item_sacred_wood = {
    '.': None, 'o': (60, 40, 22), 'b': (205, 165, 115), 'a': (155, 115, 72),
    'c': (100, 70, 42),
}
PAL_item_magic_wood = {
    '.': None, 'o': (45, 30, 60), 'b': (175, 135, 195), 'a': (120, 88, 140),
    'c': (78, 55, 95),
}
PAL_item_eternal_wood = {
    '.': None, 'o': (30, 45, 42), 'b': (140, 180, 165), 'a': (88, 120, 110),
    'c': (55, 78, 72),
}
PAL_item_sanzu_flask = {
    '.': None, 'o': (35, 50, 55), 'b': (200, 225, 235), 'a': (150, 180, 190),
    'c': (95, 125, 140), 'd': (120, 200, 150),
}
PAL_item_spirit_fish = {
    '.': None, 'o': (25, 70, 90), 'b': (200, 245, 255), 'a': (95, 195, 215),
    'c': (52, 135, 165),
}
PAL_item_mermaid_scale = {
    '.': None, 'o': (80, 105, 125), 'b': (240, 248, 252), 'a': (178, 205, 225),
    'c': (125, 158, 182),
}
PAL_item_dragon_scale = {
    '.': None, 'o': (20, 70, 55), 'b': (150, 235, 195), 'a': (70, 160, 125),
    'c': (38, 100, 78),
}
PAL_item_tide_crystal = {
    '.': None, 'o': (30, 80, 95), 'b': (225, 255, 255), 'a': (115, 220, 232),
    'c': (60, 155, 175),
}
PAL_item_spirit_herb = {
    '.': None, 'o': (35, 70, 35), 'b': (180, 235, 150), 'a': (95, 175, 95),
    'c': (55, 115, 58),
}
PAL_item_gentian = {
    '.': None, 'o': (40, 45, 110), 'b': (200, 205, 250), 'a': (115, 125, 215),
    'c': (68, 74, 155),
}
PAL_item_higanbana = {
    '.': None, 'o': (70, 10, 20), 'b': (255, 150, 160), 'a': (215, 45, 65),
    'c': (135, 22, 38),
}
PAL_item_magic_mushroom = {
    '.': None, 'o': (55, 30, 70), 'b': (215, 160, 235), 'a': (155, 95, 185),
    'c': (98, 55, 125),
}
PAL_item_spirit_charcoal = {
    '.': None, 'o': (18, 18, 24), 'b': (110, 110, 122), 'a': (52, 52, 60),
    'c': (32, 32, 38), 'd': (160, 110, 210),
}
PAL_item_talisman_paper = {
    '.': None, 'o': (70, 50, 40), 'b': (250, 242, 215), 'a': (232, 222, 190),
    'c': (195, 182, 150), 'i': (200, 55, 55), 'd': (200, 55, 55),
}
PAL_item_sukima_fragment = {
    '.': None, 'o': (45, 15, 70), 'b': (225, 160, 255), 'a': (160, 80, 215),
    'c': (95, 35, 140), 'd': (245, 220, 255),
}

TEXES = {
    "item/cinnabar": ROCK,
    "item/spirit_iron": ROCK,
    "item/spirit_iron_ore": ROCK,
    "item/star_silver": ROCK,
    "item/star_silver_ore": ROCK,
    "item/oni_stone": ROCK,
    "item/spirit_soil": PILE,
    "item/porcelain_clay": PILE,
    "item/higan_soil": PILE,
    "item/moon_sand": PILE,
    "item/sacred_wood": LOG,
    "item/magic_wood": LOG,
    "item/eternal_wood": LOG,
    "item/sanzu_flask": BOTTLE,
    "item/spirit_fish": FISH,
    "item/mermaid_scale": SCALE,
    "item/dragon_scale": SCALE,
    "item/tide_crystal": GEM,
    "item/spirit_herb": HERB,
    "item/gentian": HERB,
    "item/higanbana": FLOWER,
    "item/magic_mushroom": MUSHROOM,
    "item/spirit_charcoal": CHARCOAL,
    "item/talisman_paper": PAPER,
    "item/sukima_fragment": RIFT,
}
