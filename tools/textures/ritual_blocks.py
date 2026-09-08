# ritual_blocks.py v2 - 仪式石(_0.._5) + 仪式核(基础+_0.._5) + 仪式基座(_0.._5 侧/顶/底)
# 风格基准 v2（紫系石族，2026-09 贴图翻新）：
#   - 石体：紫色同族，饱和度随品阶 0->5 渐增（t0 近灰紫 -> t5 深紫罗兰）
#   - 品阶色：TierPalette 主色作中心封印纹样点缀，纹样随品阶逐步点亮：
#     t0 石雕无彩 / t1 彩环 / t2 +瞳 / t3 +四方刻度 / t4 +四角星点+瞳芒 / t5 +环光+晶芒
#   - 核心晶体：淡紫冷光棱面 x + 白色晶芒 W；基座侧柱周围全部填实（修复旧版透明破洞）
#   - 铜箍 o/O/M/L 为全变体共用机械识别色；全部方块贴图无 '.'（全不透明）

TIERS = [
    # (后缀, 石体四色 (k缝,s暗,g中,G亮), 品阶三色 (m主,h高光,d暗))
    ("", ((52, 50, 62), (84, 82, 94), (112, 110, 124), (140, 138, 152)),
         ((198, 196, 214), (236, 234, 244), (136, 134, 152))),
    ("_0", ((52, 50, 62), (84, 82, 94), (112, 110, 124), (140, 138, 152)),
           ((198, 196, 214), (236, 234, 244), (136, 134, 152))),
    ("_1", ((48, 36, 70), (78, 58, 106), (104, 80, 134), (130, 106, 162)),
           ((76, 175, 80), (165, 214, 167), (46, 125, 50))),
    ("_2", ((46, 32, 74), (74, 52, 112), (100, 74, 140), (126, 100, 168)),
           ((33, 150, 243), (144, 202, 249), (21, 101, 192))),
    ("_3", ((44, 28, 72), (72, 46, 108), (98, 66, 136), (124, 92, 164)),
           ((255, 193, 7), (255, 224, 130), (184, 134, 11))),
    ("_4", ((42, 24, 70), (70, 42, 104), (96, 58, 132), (122, 84, 160)),
           ((244, 67, 54), (255, 171, 145), (183, 28, 28))),
    ("_5", ((38, 18, 64), (64, 34, 98), (90, 48, 126), (118, 74, 154)),
           ((156, 39, 176), (206, 147, 216), (106, 27, 154))),
]

FIXED = {
    'o': (58, 44, 24),
    'O': (88, 70, 44),
    'M': (185, 138, 68),
    'L': (232, 198, 135),
    'x': (214, 206, 238),
    'W': (255, 255, 255),
}

# ---- 仪式石：错缝砖墙 + 中心封印环（纹样随品阶点亮） ----
def _stone_base_rows():
    rows = []
    for band in range(4):
        vseams = (7, 8) if band % 2 == 0 else (3, 12)  # 偶宽居中，镜像对称
        for r in range(4):
            if r == 3:
                rows.append(["k"] * 16)
            else:
                face = "G" if r == 0 else "g"
                row = [face] * 16
                for c in vseams:
                    row[c] = "k"
                rows.append(row)
    return rows

RING = ([(5, x) for x in (6, 7, 8, 9)]      # 顶弧（高光）
        + [(10, x) for x in (6, 7, 8, 9)]   # 底弧（暗）
        + [(y, 5) for y in (6, 7, 8, 9)]    # 左环
        + [(y, 10) for y in (6, 7, 8, 9)])  # 右环
PUPIL = [(7, 7), (7, 8), (8, 7), (8, 8)]
TICKS = [(4, 7), (4, 8), (11, 7), (11, 8), (7, 3), (8, 3), (7, 12), (8, 12)]
CORNERS = [(4, 4), (4, 11), (11, 4), (11, 11)]

def _stone_rows(tier):
    rows = _stone_base_rows()
    if tier == 0:  # 石雕封印：缝色环，跨缝处提亮
        for y, x in RING:
            rows[y][x] = "g" if rows[y][x] == "k" else "k"
        return ["".join(r) for r in rows]
    for y, x in RING:
        rows[y][x] = "h" if y == 5 else ("d" if y == 10 else "m")
    if tier >= 2:
        for y, x in PUPIL:
            rows[y][x] = "m"
        if tier >= 4:
            rows[7][7] = "h"      # 瞳芒（成对保镜像）
            rows[7][8] = "h"
    if tier >= 3:
        for y, x in TICKS:
            rows[y][x] = "m"      # 四方刻度
    if tier >= 4:
        for y, x in CORNERS:
            rows[y][x] = "m"      # 四角星点
    if tier >= 5:
        rows[6][5] = "h"          # 环光（四角成对 shimmer）
        rows[6][10] = "h"
        rows[9][5] = "h"
        rows[9][10] = "h"
        rows[8][7] = "W"          # 晶芒（成对）
        rows[8][8] = "W"
    return ["".join(r) for r in rows]

# ---- 仪式核：紫石腔室（左右镜像）+ 淡紫冷光菱形晶体（偶宽 2/4/6/6/6/6/4/2） ----
_C = "Gkgk"
_CW = "kgkG"

def _core_row(inner8):
    return _C + inner8 + _CW

CORE_ROWS = [
    "G" * 16,
    "Gkkk" + "g" * 8 + "kkkG",
    "Gkgg" + "k" * 8 + "ggkG",
    _core_row("ssssssss"),
    _core_row("sssxxsss"),
    _core_row("ssxhhxss"),
    _core_row("sxhhhhxs"),
    _core_row("sxhWWhxs"),
    _core_row("sxmmmmxs"),
    _core_row("sxmddmxs"),
    _core_row("ssxddxss"),
    _core_row("sssddsss"),
    _core_row("ssssssss"),
    "Gkgg" + "k" * 8 + "ggkG",
    "Gkkk" + "g" * 8 + "kkkG",
    "G" * 16,
]

# ---- 仪式基座侧面：铜冠 + 品阶光带 + 深底浮柱（竖缝分板 + 品阶小封印环，全填实）+ 底部铜沿 ----
def _pedestal_rows():
    rows = [
        "k" * 16,
        "O" + "L" * 14 + "O",
        "O" + "G" * 14 + "O",
        "O" + "m" * 14 + "O",
        "O" + "k" * 14 + "O",
        "O" * 16,
    ]
    # 柱身截面：s G k g*6 k G s（cols2-13），竖缝在 col4/11，纹样区 cols6-9
    col = [list("kksGkggggggkGskk") for _ in range(8)]
    for r in range(8):  # 板面明暗：竖缝间交替 g/G 增加立体
        if r % 2 == 1:
            col[r][7] = col[r][8] = "G"
    for x in (6, 7, 8, 9):      # 小封印环（h 顶弧 / m 侧环 / d 底弧）
        col[1][x] = "h"         # abs 行7
        col[4][x] = "d"         # abs 行10
    col[2][6] = "m"             # abs 行8 侧环
    col[2][9] = "m"
    col[3][6] = "m"             # abs 行9 侧环
    col[3][9] = "m"
    rows += ["".join(r) for r in col]
    rows.append("k" * 16)
    rows.append("O" + "L" * 14 + "O")
    return rows

PED_ROWS = _pedestal_rows()

# ---- 仪式基座顶面：紫石缘 + 铜箍 + 深陷石面 + 中央品阶晶印 ----
PED_TOP_ROWS = (
    ["G" * 16,
     "G" + "g" * 14 + "G",
     "Gg" + "O" * 12 + "gG",
     "GgO" + "L" * 10 + "OgG"]
    + ["GgOL" + "k" * 8 + "LOgG"] * 2
    + ["GgOL" + "kk" + "hmmh" + "kk" + "LOgG",
       "GgOL" + "kk" + "mmmm" + "kk" + "LOgG",
       "GgOL" + "kk" + "mmmm" + "kk" + "LOgG",
       "GgOL" + "kk" + "dmmd" + "kk" + "LOgG"]
    + ["GgOL" + "k" * 8 + "LOgG"] * 2
    + ["GgO" + "L" * 10 + "OgG",
       "Gg" + "O" * 12 + "gG",
       "G" + "g" * 14 + "G",
       "G" * 16]
)

# ---- 仪式基座底面：暗紫石底 + 铜板（无品阶色） ----
PED_BOTTOM_ROWS = (
    ["s" * 16,
     "s" + "k" * 14 + "s",
     "sk" + "O" * 12 + "ks",
     "skO" + "M" * 10 + "Oks"]
    + ["skOM" + "M" * 8 + "MOks"] * 8
    + ["skO" + "M" * 10 + "Oks",
       "sk" + "O" * 12 + "ks",
       "s" + "k" * 14 + "s",
       "s" * 16]
)

TEXES = {}
for _suffix, _ramp, _acc in TIERS:
    _k, _s, _g, _G = _ramp
    _m, _h, _d = _acc
    _p = dict(FIXED)
    _p.update({"k": _k, "s": _s, "g": _g, "G": _G, "m": _m, "h": _h, "d": _d})
    _tier = int(_suffix[1:]) if _suffix else 0
    _name = "block/ritual_core" + _suffix
    TEXES[_name] = CORE_ROWS
    globals()["PAL_" + _name.replace("/", "_")] = _p
    if _suffix == "":
        continue  # 石/台为品阶方块族，不产无后缀基础版
    for _n, _r in (("block/ritual_stone" + _suffix, _stone_rows(_tier)),
                   ("block/ritual_pedestal" + _suffix, PED_ROWS),
                   ("block/ritual_pedestal_top" + _suffix, PED_TOP_ROWS),
                   ("block/ritual_pedestal_bottom" + _suffix, PED_BOTTOM_ROWS)):
        TEXES[_n] = _r
        globals()["PAL_" + _n.replace("/", "_")] = _p
