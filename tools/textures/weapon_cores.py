# weapon_cores.py - 六种弹幕核（槽1 BulletCoreItem）
# 外形按弹幕类型差异化；主体色按默认 requiredTier：单发玉/散弹玉/飞刀=1绿，灵符/激光机枪=2蓝，激光炮=3金
# 左下角 3×3 类型微图标（openspec/project.md §5.4）

TIERS = {
    1: ((76, 175, 80), (165, 214, 167), (46, 125, 50)),
    2: ((33, 150, 243), (144, 202, 249), (21, 101, 192)),
    3: ((255, 193, 7), (255, 224, 130), (184, 134, 11)),
}

BASE_PAL = {
    '.': None,
    'o': (58, 44, 24),      # 金属暗轮廓
    'M': (185, 138, 68),    # 黄铜中
    'L': (232, 198, 135),   # 黄铜亮
    'W': (255, 255, 255),   # 星芒
    'i': (60, 60, 60),      # 类型微图标
}


def _pal(tier):
    hi, main, dark = TIERS[tier]
    p = dict(BASE_PAL)
    p.update({'h': hi, 'm': main, 'd': dark})
    return p


TEXES = {
    "item/core_sphere_single": [
        "................",
        "................",
        "......hhhh......",
        ".....hmmmmh.....",
        "....hmWWmmmh....",
        "...hmWmmmmmmd...",
        "...hmmmmmmmdd...",
        "..hmmmmmmmmdd...",
        "..mmmmmmmmmdd...",
        "..dmmmmmmmddd...",
        "..ddmmmmmdddd...",
        "...ddddddddd....",
        "i..oLLLLLLLo....",
        "ii.oMMMMMMMo....",
        "i..ooooooooo....",
        "................",
    ],
    "item/core_sphere_shotgun": [
        "................",
        "................",
        "......hhhh......",
        "......hmmh......",
        "......hmmd......",
        ".......dd.......",
        "..hhhh...hhhh...",
        "..hmmh...hmmh...",
        "..hmmd...hmmd...",
        "...dd.....dd....",
        "................",
        "................",
        "i.i.............",
        ".i..............",
        "................",
        "................",
    ],
    "item/core_knife": [
        "................",
        ".......hh.......",
        ".......hm.......",
        ".......hm.......",
        ".......hm.......",
        ".......hm.......",
        ".......hm.......",
        ".......hd.......",
        ".......hd.......",
        ".......dd.......",
        ".....oMMMMo.....",
        ".......oM.......",
        "i......oM.......",
        "i......oM.......",
        "ii.....oo.......",
        "................",
    ],
    "item/core_talisman": [
        "................",
        ".......oo.......",
        "......ohho......",
        ".....hmmmmh.....",
        ".....hmWWmh.....",
        ".....hmmmmh.....",
        ".....hmWWmh.....",
        ".....hmmmmh.....",
        ".....hmWWmh.....",
        ".....hmmmmh.....",
        ".....hmWWmh.....",
        ".....hmmmmh.....",
        ".....hmmmmh.....",
        "i....hmmmmh.....",
        "ii....dddd......",
        "................",
    ],
    "item/core_laser_gun": [
        "................",
        "................",
        "................",
        "................",
        "................",
        "................",
        "..oLLLLLLLLLo...",
        ".oMMMMmmMLLLLo..",
        ".oMMMMmmMoMMmmo.",
        ".oMMMMMMMMMMMMo.",
        "..oMMMMMMMMo....",
        "...oMMo.........",
        "....oo..........",
        "iii.............",
        "................",
        "................",
    ],
    "item/core_laser_cannon": [
        "................",
        "................",
        "................",
        "................",
        "................",
        "...oLLLLLLLLLLoo",
        "..oLLMMmmMLLLLMo",
        ".oMMMMmmmmMMMMMo",
        ".oMMMMmmmmMMMMMo",
        ".oMMMMMmmMMMMMo.",
        "..oMMMMMMMMMMo..",
        "...oMMMMMMMo....",
        ".i..oooooo......",
        "iiioMMMMMMMo....",
        ".i..............",
        "................",
    ],
}

for _name in list(TEXES):
    _tier = {"sphere_single": 1, "sphere_shotgun": 1, "knife": 1,
             "talisman": 2, "laser_gun": 2, "laser_cannon": 3}[_name.split("core_")[1]]
    globals()["PAL_" + _name.replace("/", "_")] = _pal(_tier)
