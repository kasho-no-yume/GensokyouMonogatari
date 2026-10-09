# landscaping tool and spirit bomb item textures (16x16)
#
# Landscaping tool: a heavy flat trowel/spade head on a shaft -- reads as "move earth".
# Spirit bomb: a runed talisman orb with a lit fuse; the accent colour shifts warm so
# it reads as dangerous even at 16x16.

PAL_LANDSCAPE = {
    '.': None,
    'o': (44, 34, 22),      # outline
    'g': (112, 84, 46),     # shaft dark
    'G': (168, 130, 74),    # shaft light
    'b': (128, 132, 140),   # blade base
    'h': (196, 200, 210),   # blade highlight
    's': (86, 90, 98),      # blade shade
    'c': (146, 108, 52),    # copper clamp
}

PAL_BOMB = {
    '.': None,
    'o': (46, 20, 30),      # outline
    'b': (150, 44, 60),     # orb base (crimson)
    'h': (238, 128, 128),   # orb highlight
    's': (96, 26, 40),      # orb shade
    'r': (252, 214, 106),   # rune gold
    'R': (255, 244, 186),   # rune bright
    'f': (252, 232, 140),   # fuse spark
    'k': (60, 54, 60),      # fuse cord
}

TEX_LANDSCAPING = [
    "................",
    ".........ggg....",
    "........gGGGg...",
    ".......gGGGGg...",
    "......gGGGGg....",
    ".....gGGGg......",
    "....gGGg........",
    "..ccccccc.......",
    ".obhhhhhhbo.....",
    "obbbbbbbbbbo....",
    ".obbbbbbbbbo....",
    "..obssssssbo....",
    "...ossssssso....",
    "....oooooooo....",
    "................",
    "................",
]

TEX_SPIRIT_BOMB = [
    "................",
    "............f...",
    "...........fk...",
    "..........fk....",
    "....ooooook.....",
    "...obrrrrrbbo...",
    "..obRrbbbbrRbo..",
    ".obRrbbbbbbbrbo.",
    ".obrbbhbbbbrbbo.",
    ".obrbbhbbbbrbbo.",
    ".obrbbbbbbbrbbo.",
    "..obrbbbbbbrbo..",
    "...obrrrrrbbo...",
    "....oooooooo....",
    "................",
    "................",
]

TEXES = {
    "item/landscaping_tool": TEX_LANDSCAPING,
    "item/spirit_bomb": TEX_SPIRIT_BOMB,
}

globals()["PAL_item_landscaping_tool"] = PAL_LANDSCAPE
globals()["PAL_item_spirit_bomb"] = PAL_BOMB
