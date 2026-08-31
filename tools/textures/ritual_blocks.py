# ritual_blocks.py - 仪式石(_0.._5) + 仪式核(基础+_0.._5) + 仪式基座(_0.._5)
# 品阶方块族（石/台）只有 _0.._5，无无后缀基础版；核心保留基础图（= _0 同图，spec 约定）
# 品阶色只作用于品阶字符 m/h/d；石质/金属字符全变体共用
# 行用表达式拼装保证 16 宽

TIERS = [
    ("", (158, 158, 158), (207, 207, 207), (97, 97, 97)),      # 0 灰（基础图同）
    ("_0", (158, 158, 158), (207, 207, 207), (97, 97, 97)),
    ("_1", (76, 175, 80), (165, 214, 167), (46, 125, 50)),
    ("_2", (33, 150, 243), (144, 202, 249), (21, 101, 192)),
    ("_3", (255, 193, 7), (255, 224, 130), (184, 134, 11)),
    ("_4", (244, 67, 54), (255, 171, 145), (183, 28, 28)),
    ("_5", (156, 39, 176), (206, 147, 216), (106, 27, 154)),
]

STONE = {
    '.': None,
    'k': (74, 74, 82),      # 石缝
    'g': (126, 126, 134),   # 石 中
    'G': (154, 154, 162),   # 石 亮
    's': (94, 94, 102),     # 石 暗
    'o': (58, 44, 24),
    'O': (88, 70, 44),      # 铜箍暗
    'M': (185, 138, 68),
    'L': (232, 198, 135),
    'W': (255, 255, 255),
}


TEXES = {}

# ---- 仪式石（_0.._5）：石砖（错缝）+ 中心隙间眼形符文（品阶色 m 外缘 + h 瞳）----
_stone = []
for _band in range(4):
    for _r in range(4):
        if _r == 3:
            _stone.append("k" * 16)                      # 横缝
        else:
            _left = (_band * 5) % 16
            _row = ["g"] * 16
            _row[_left] = "k"                            # 竖缝（逐带错位）
            _row[(_left + 8) % 16] = "k"
            _stone.append("".join(_row))
_stone[0] = _stone[0].replace("g", "G")
_stone[1] = _stone[1].replace("g", "G")
_stone[14] = _stone[14].replace("g", "s")
_stone[15] = _stone[15].replace("g", "s")
for _y, _x in ((6, 7), (7, 6), (7, 9), (8, 5), (8, 10), (9, 6), (9, 9), (10, 7), (10, 8)):
    _stone[_y] = _stone[_y][:_x] + "m" + _stone[_y][_x + 1:]   # 眼形符文外缘（品阶主色）
for _y, _x in ((8, 7), (8, 8)):
    _stone[_y] = _stone[_y][:_x] + "h" + _stone[_y][_x + 1:]   # 瞳（品阶高光）
STONE_ROWS = _stone

# ---- 仪式核：石台 + 铜箍，中央菱形晶石（h/m/d 品阶色）----
_C = "Gkgk"          # 核室左右壁
_CW = "kggkG"        # 右侧壁


def _core_row(inner7):
    return _C + inner7 + _CW


CORE_ROWS = [
    "G" * 16,
    "GkkkggggggggkksG",
    "GkggkkkkkkkggksG",
    _core_row("sssssss"),      # 晶室顶
    _core_row("sssLsss"),      # 菱尖
    _core_row("ssLhLss"),
    _core_row("sLhhhLs"),      # 最宽
    _core_row("sLhWhLs"),      # 星芒
    _core_row("sLmmmLs"),
    _core_row("sLmmdLs"),
    _core_row("ssLdLss"),
    _core_row("sssdsss"),      # 菱底尖
    _core_row("sssssss"),      # 晶室底
    "GkggkkkkkkkggksG",
    "GkkkggggggggkksG",
    "G" * 16,
]

# ---- 仪式基座：台面(品阶光带 m) + 柱身(符文) + 底座铜沿（侧面） ----
PED_IN = "..kg"           # 柱行左缘
PED_OUT = "gk.."         # 柱行右缘


def _ped_row(inner8):
    return PED_IN + inner8 + PED_OUT


PEDESTAL_ROWS = [
    "o" * 16,
    "O" + "L" * 14 + "O",
    "O" + "G" * 14 + "O",
    "O" + "m" * 14 + "O",      # 品阶光带
    "O" + "o" * 14 + "O",
    "O" * 16,
    _ped_row("GGGGGGGG"),
    _ped_row("Gggggggg"),
    _ped_row("Ggkkskkg"),
    _ped_row("Ggkssskg"),      # 符文
    _ped_row("Ggkkskkg"),
    _ped_row("Gggggggg"),
    _ped_row("Gggggggg"),
    _ped_row("GGGGGGGG"),
    ".o" + "k" * 12 + "o.",
    "O" + "L" * 14 + "O",
]

# ---- 仪式基座顶面：石缘 + 铜箍 + 凹陷石面 + 中央品阶晶点 ----
PEDESTAL_TOP_ROWS = (
    ["G" * 16,
     "G" + "g" * 14 + "G",
     "Gg" + "O" * 12 + "gG",
     "GgO" + "L" * 10 + "OgG"]
    + ["GgOL" + "s" * 8 + "LOgG"] * 2
    + ["GgOL" + "ss" + "hhmm" + "ss" + "LOgG",
       "GgOL" + "ss" + "hmmm" + "ss" + "LOgG",
       "GgOL" + "ss" + "mmmd" + "ss" + "LOgG",
       "GgOL" + "ss" + "mmdd" + "ss" + "LOgG"]
    + ["GgOL" + "s" * 8 + "LOgG"] * 2
    + ["GgO" + "L" * 10 + "OgG",
       "Gg" + "O" * 12 + "gG",
       "G" + "g" * 14 + "G",
       "G" * 16]
)

# ---- 仪式基座底面：暗石底 + 铜板（无品阶色） ----
PEDESTAL_BOTTOM_ROWS = (
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

for _suffix, _main, _hi, _dark in TIERS:
    _p = dict(STONE)
    _p.update({'m': _main, 'h': _hi, 'd': _dark})
    # 仪式核：基础 + _0.._5（基础图与 _0 同图，保留无后缀版）
    _name = "block/ritual_core" + _suffix
    TEXES[_name] = CORE_ROWS
    globals()["PAL_" + _name.replace("/", "_")] = _p
    if _suffix == "":
        continue  # 石/台为品阶方块族，不产无后缀基础版
    for _name, _rows in (("block/ritual_stone" + _suffix, STONE_ROWS),
                         ("block/ritual_pedestal" + _suffix, PEDESTAL_ROWS),
                         ("block/ritual_pedestal_top" + _suffix, PEDESTAL_TOP_ROWS),
                         ("block/ritual_pedestal_bottom" + _suffix, PEDESTAL_BOTTOM_ROWS)):
        TEXES[_name] = _rows
        globals()["PAL_" + _name.replace("/", "_")] = _p
