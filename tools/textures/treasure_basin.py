# treasure_basin.py - 聚宝盆方块贴图（16x16，全不透明）
# 铜箍系沿用 ritual_blocks.py 的 o/O/M/L 识别色；辰砂内嵌带 d/r；琥珀金 G。
# 面分工：
#   side     圈足/腹/盆底外壁（横向三带 + 铆钉 + 辰砂嵌纹）
#   rim_side 盆沿外壁（金箍 + 铆钉，下接青铜）
#   rim_top  盆沿顶面（抛光金环，内缘压深边）
#   inner_*  盆膛底（暗铜 + 符文环，按 fill 0..3 逐段点亮）
#   bottom   圈足底面（暗铜 + 匠印）

PAL = {
    'o': (47, 36, 24),
    'O': (82, 62, 38),
    'M': (176, 132, 66),
    'L': (231, 199, 140),
    'G': (255, 193, 7),
    'd': (120, 32, 32),
    'r': (188, 54, 44),
}

# ---- 外壁：上亮带 / 铆钉 / 辰砂嵌纹 / 下暗带 ----
SIDE = [
    "oooooooooooooooo",
    "LLLLLLLLLLLLLLLL",
    "MMMMMMMMMMMMMMMM",
    "MMMOMMMMMMMMOMMM",
    "MMMLMMMMMMMMLMMM",
    "MMMMMMMMMMMMMMMM",
    "oooooooooooooooo",
    "dddddddddddddddd",
    "ddddddrrrrdddddd",
    "dddddrrrrrrddddd",
    "dddddrrrrrrddddd",
    "ddddddrrrrdddddd",
    "oooooooooooooooo",
    "MMMOMMMMMMMMOMMM",
    "MMMMMMMMMMMMMMMM",
    "oooooooooooooooo",
]

# ---- 盆沿外壁：金箍（上 5 行）+ 青铜（下半） ----
RIM_SIDE = [
    "oooooooooooooooo",
    "LLLLLLLLLLLLLLLL",
    "GGGGGGGGGGGGGGGG",
    "GGGLGGGGGGGGLGGG",
    "GGGGGGGGGGGGGGGG",
    "MMMMMMMMMMMMMMMM",
    "OOOOOOOOOOOOOOOO",
    "MMMMMMMMMMMMMMMM",
    "OOOOOOOOOOOOOOOO",
    "MMMMMMMMMMMMMMMM",
    "OOOOOOOOOOOOOOOO",
    "MMMMMMMMMMMMMMMM",
    "OOOOOOOOOOOOOOOO",
    "MMMMMMMMMMMMMMMM",
    "OOOOOOOOOOOOOOOO",
    "oooooooooooooooo",
]

# ---- 盆沿顶面：抛光金环，四角与中段各有一对铆钉 ----
RIM_TOP = [
    "LLLLLLLLLLLLLLLL",
    "LGGGGGGGGGGGGGGL",
    "LGGGGGGGGGGGGGGL",
    "LGGGLGGGGGGLGGGL",
    "LGGGGGGGGGGGGGGL",
    "LGGGGGGGGGGGGGGL",
    "LGGGGGGGGGGGGGGL",
    "LGGGGGGGGGGGGGGL",
    "LGGGGGGGGGGGGGGL",
    "LGGGGGGGGGGGGGGL",
    "LGGGGGGGGGGGGGGL",
    "LGGGGGGGGGGGGGGL",
    "LGGGGGGGGGGGGGGL",
    "LGGGLGGGGGGLGGGL",
    "LGGGGGGGGGGGGGGL",
    "oooooooooooooooo",
]

# ---- 盆膛底：暗铜 + 方形符文环（环本身按档位替换成 G）----
INNER_BASE = [
    "oooooooooooooooo",
    "oOOOOOOOOOOOOOOo",
    "oOOOOOOOOOOOOOOo",
    "oOOMMMMMMMMMMOOo",
    "oOOMooooooooMOOo",
    "oOOMooooooooMOOo",
    "oOOMooooooooMOOo",
    "oOOMooooooooMOOo",
    "oOOMooooooooMOOo",
    "oOOMooooooooMOOo",
    "oOOMooooooooMOOo",
    "oOOMMMMMMMMMMOOo",
    "oOOOOOOOOOOOOOOo",
    "oOOOOOOOOOOOOOOo",
    "oOOOOOOOOOOOOOOo",
    "oooooooooooooooo",
]

BOTTOM = [
    "oooooooooooooooo",
    "oOOOOOOOOOOOOOOo",
    "oOOOOOOOOOOOOOOo",
    "oOOOOOOOOOOOOOOo",
    "oOOOOOOOOOOOOOOo",
    "oOOMMMMMMMMMMOOo",
    "oOOMMMMMMMMMMOOo",
    "oOOMMMMMMMMMMOOo",
    "oOOMMMMMMMMMMOOo",
    "oOOOOOOOOOOOOOOo",
    "oOOOOOOOOOOOOOOo",
    "oOOOOOOOOOOOOOOo",
    "oOOOOOOOOOOOOOOo",
    "oOOOOOOOOOOOOOOo",
    "oOOOOOOOOOOOOOOo",
    "oooooooooooooooo",
]


def _inner(level):
    """0 不亮 / 1 上下弧 / 2 整环 + 辰砂心 / 3 环外四向晶芒。"""
    rows = [list(r) for r in INNER_BASE]
    if level >= 1:
        for x in range(3, 13):
            rows[3][x] = 'G'
            rows[12][x] = 'G'
    if level >= 2:
        for y in range(4, 12):
            rows[y][3] = 'G'
            rows[y][12] = 'G'
        for y in (7, 8):
            for x in (7, 8):
                rows[y][x] = 'r'
    if level >= 3:
        for y, x in ((2, 7), (2, 8), (13, 7), (13, 8),
                     (7, 2), (8, 2), (7, 13), (8, 13)):
            rows[y][x] = 'L'
    return ["".join(r) for r in rows]


TEXES = {
    "block/treasure_basin_side": SIDE,
    "block/treasure_basin_rim_side": RIM_SIDE,
    "block/treasure_basin_rim_top": RIM_TOP,
    "block/treasure_basin_inner_0": _inner(0),
    "block/treasure_basin_inner_1": _inner(1),
    "block/treasure_basin_inner_2": _inner(2),
    "block/treasure_basin_inner_3": _inner(3),
    "block/treasure_basin_bottom": BOTTOM,
}
