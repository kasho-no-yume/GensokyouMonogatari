# landscaping tool and spirit bomb item textures (16x16)
#
# Landscaping tool: a broad flat tamper/spade head on a shaft -- reads as "clear and
# level the ground" (its job: one-shot ritual building-site clearing + flattening).
# Spirit bomb: a runed talisman orb with a lit fuse; the accent colour shifts warm so
# it reads as dangerous even at 16x16.

PAL_LANDSCAPE = {
    '.': None,
    'o': (44, 34, 22),
    'g': (112, 84, 46),
    'G': (168, 130, 74),
    'b': (128, 132, 140),
    'h': (196, 200, 210),
    's': (86, 90, 98),
    'c': (146, 108, 52),
}

PAL_BOMB = {
    '.': None,
    'o': (46, 20, 30),
    'b': (150, 44, 60),
    'h': (238, 128, 128),
    's': (96, 26, 40),
    'r': (252, 214, 106),
    'R': (255, 244, 186),
    'f': (252, 232, 140),
    'k': (60, 54, 60),
}

# Spirit bomb BLOCK textures (16x16, fully opaque -- a placed block, not an item sprite).
# A runed crimson talisman cube with a gold sigil on each face and a fuse nub on top.
PAL_BLOCK_BOMB = {
    'o': (30, 14, 20),
    'b': (126, 36, 50),
    'h': (176, 60, 74),
    's': (82, 22, 34),
    'r': (232, 190, 92),
    'R': (255, 238, 160),
    'k': (54, 48, 54),
}

TEX_LANDSCAPING = [
    "................",
    "................",
    "....ooggggoo....",
    "......oggo......",
    "......oggo......",
    "......oggo......",
    "......oggo......",
    "......oggo......",
    "......oggo......",
    "......oggo......",
    "......obbo......",
    "...oobhhhhboo...",
    "..obbbbbbbbbbo..",
    "..obbbbbbbbbbo..",
    "..obssssssssbo..",
    "...oooooooooo...",
]

TEX_SPIRIT_BOMB = [
    "................",
    "...........f....",
    "..........fk....",
    ".........kk.....",
    "........kk......",
    "......oooo......",
    "....obhhhbbo....",
    "...obhhbbbbbo...",
    "..obhbbbbbbrbo..",
    "..obrRRRRRRrbo..",
    "..obbbbbbbbbbo..",
    "...obbbbbbbbo...",
    "....obssssbo....",
    "......oooo......",
    "................",
    "................",
]

TEX_BOMB_SIDE = [
    "oooooooooooooooo",
    "obbbbbbbbbbbbbbo",
    "obbbbbbbbbbbbbbo",
    "obrbbbbbbbbbbrbo",
    "obbbbbbbbbbbbbbo",
    "obbbbbbrrbbbbbbo",
    "obbbbbrRRrbbbbbo",
    "obbbbrRRRRrbbbbo",
    "obbbbbrRRrbbbbbo",
    "obbbbbbrrbbbbbbo",
    "obbbbbbbbbbbbbbo",
    "obbbbbbbbbbbbbbo",
    "obrbbbbbbbbbbrbo",
    "obbbbbbbbbbbbbbo",
    "osbbbbbbbbbbbbso",
    "oooooooooooooooo",
]

TEX_BOMB_TOP = [
    "oooooooooooooooo",
    "obbbbbbbbbbbbbbo",
    "obbbbbbbbbbbbbbo",
    "obbrrrrrrrrrrbbo",
    "obrbbbbbbbbbbrbo",
    "obrbbbbbbbbbbrbo",
    "obrbbbkkkkbbbrbo",
    "obrbbbkkkkbbbrbo",
    "obrbbbkkkkbbbrbo",
    "obrbbbkkkkbbbrbo",
    "obrbbbbbbbbbbrbo",
    "obrbbbbbbbbbbrbo",
    "obbrrrrrrrrrrbbo",
    "obbbbbbbbbbbbbbo",
    "osbbbbbbbbbbbbso",
    "oooooooooooooooo",
]

TEX_BOMB_BOTTOM = [
    "oooooooooooooooo",
    "obbbbbbbbbbbbbbo",
    "obbbbbbbbbbbbbbo",
    "obbbbbbbbbbbbbbo",
    "obbbbbbbbbbbbbbo",
    "obbbbbbbbbbbbbbo",
    "obbbbbbrrbbbbbbo",
    "obbbbbbrrbbbbbbo",
    "obrrrrrrrrrrrbbo",
    "obbbbbbrrbbbbbbo",
    "obbbbbbrrbbbbbbo",
    "obbbbbbbbbbbbbbo",
    "obbbbbbbbbbbbbbo",
    "obbbbbbbbbbbbbbo",
    "osbbbbbbbbbbbbso",
    "oooooooooooooooo",
]

TEX_BOMB_FUSE = [
    "rrrrrrrrrrrrrrrr",
    "rrrrrrrrrrrrrrrr",
    "rrrrrrrrrrrrrrrr",
    "kkkkkkkkkkkkkkkk",
    "kkkkkkkkkkkkkkkk",
    "kkkkkkkkkkkkkkkk",
    "kkkkkkkkkkkkkkkk",
    "kkkkkkkkkkkkkkkk",
    "kkkkkkkkkkkkkkkk",
    "kkkkkkkkkkkkkkkk",
    "kkkkkkkkkkkkkkkk",
    "kkkkkkkkkkkkkkkk",
    "kkkkkkkkkkkkkkkk",
    "kkkkkkkkkkkkkkkk",
    "kkkkkkkkkkkkkkkk",
    "kkkkkkkkkkkkkkkk",
]

TEXES = {
    "item/landscaping_tool": TEX_LANDSCAPING,
    "item/spirit_bomb": TEX_SPIRIT_BOMB,
    "block/spirit_bomb_side": TEX_BOMB_SIDE,
    "block/spirit_bomb_top": TEX_BOMB_TOP,
    "block/spirit_bomb_bottom": TEX_BOMB_BOTTOM,
    "block/spirit_bomb_fuse": TEX_BOMB_FUSE,
}

globals()["PAL_item_landscaping_tool"] = PAL_LANDSCAPE
globals()["PAL_item_spirit_bomb"] = PAL_BOMB
globals()["PAL_block_spirit_bomb_side"] = PAL_BLOCK_BOMB
globals()["PAL_block_spirit_bomb_top"] = PAL_BLOCK_BOMB
globals()["PAL_block_spirit_bomb_bottom"] = PAL_BLOCK_BOMB
globals()["PAL_block_spirit_bomb_fuse"] = PAL_BLOCK_BOMB
