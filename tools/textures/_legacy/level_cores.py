# level_cores.py - 武器等级核（槽2 WeaponLevelCoreItem）
# 统一六角棱镜外形，主体色按等级 1绿/2蓝/3金（§5.4 配色环），顶部白色刻痕数 = 等级

TIERS = {
    1: ((76, 175, 80), (165, 214, 167), (46, 125, 50)),
    2: ((33, 150, 243), (144, 202, 249), (21, 101, 192)),
    3: ((255, 193, 7), (255, 224, 130), (184, 134, 11)),
}

BASE_PAL = {
    '.': None,
    'o': (58, 44, 24),
    'M': (185, 138, 68),
    'L': (232, 198, 135),
    'W': (255, 255, 255),
}

# 六角棱镜主体（行4 为刻痕行，程序化插入 1/2/3 道白色刻痕）
_ROWS = [
    "................",
    "................",
    "....oooooooo....",
    "...oLLLLLLLLo...",
    "..oLLhhmmmmmMo..",
    "KEEP",
    "..oLhmmmmmmmMo..",
    "..oMmmmmmmmdMo..",
    "..oMmmmmmmddMo..",
    "..oMmmmmmdddMo..",
    "..oMmmmmddddMo..",
    "...oMmmddddMo...",
    "....oooooooo....",
    "................",
    "................",
    "................",
]
# 刻痕覆盖在宝石顶行上（保持边框与底色连续，W 为白色刻痕）
_MARKS = {1: "..oLhhWWmmmmMo..", 2: "..oLhWWWWmmmMo..", 3: "..oLWWWWWWmmMo.."}


def _rows(level):
    out = []
    for r in _ROWS:
        out.append(_MARKS[level] if r == "KEEP" else r)
    return out


TEXES = {}
for _lv, _colors in TIERS.items():
    _name = "item/weapon_core_lv%d" % _lv
    TEXES[_name] = _rows(_lv)
    _hi, _main, _dark = _colors
    _p = dict(BASE_PAL)
    _p.update({'h': _hi, 'm': _main, 'd': _dark})
    globals()["PAL_" + _name.replace("/", "_")] = _p
