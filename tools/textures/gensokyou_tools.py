# gensokyou_tools.py - 灵力筑基器（landscaping_tool）与灵力引爆器（spirit_bomb）方块贴图（16x16，全不透明）
#
# 1. 灵力筑基器（镇地罗盘桩）：
#    - 紫石镇地基座 + 黄铜箍（o/O/M/L）+ 辰砂罗盘阵眼（d/r/y）+ 翡翠筑基灵光（e/E）
#    - 彻底废弃把透明底物品图 UV 贴在扁盒上的旧做法，提供全不透明的方块贴图（side / top / bottom）
# 2. 灵力引爆器（朱漆铜箍符雷）：
#    - 升级旧版的单色平板红面：加入朱红木漆明暗层次、四角黄铜铆钉包边、精致八角爆裂雷纹符印、麻绳火星引信

PAL_BLOCK_LANDSCAPE = {
    'k': (42, 34, 54),       # 紫石深缝 / 描边
    's': (68, 56, 86),       # 紫石暗部
    'g': (98, 82, 122),      # 紫石主色
    'G': (130, 112, 156),    # 紫石受光棱
    'o': (47, 36, 24),       # 铜箍深缝
    'O': (92, 68, 40),       # 黄铜暗部
    'M': (176, 132, 66),     # 黄铜主色
    'L': (232, 198, 135),    # 黄铜高光
    'd': (128, 32, 34),      # 辰砂暗红
    'r': (204, 56, 52),      # 辰砂正红
    'y': (248, 224, 168),    # 辰砂金芒
    'e': (56, 152, 112),     # 筑基灵光青绿
    'E': (148, 238, 188),    # 筑基灵光高光
}

# 灵力筑基器侧面：上沿黄铜压圈 + 中央紫石柱面嵌辰砂镇地符与翡翠刻度 + 下部重型黄铜镇地夯圈
TEX_LANDSCAPE_SIDE = [
    "kkkkkkkkkkkkkkkk",
    "kLMMMMMMMMMMMMLk",
    "kOOOOOOOOOOOOOOk",
    "kGggskkkkkksggGk",
    "kgskeMMMMMMeksgk",
    "kgskMdrryrdMksgk",
    "ksgOMryyyyrMOgsk",
    "ksgOMryEEyrMOgsk",
    "ksgOMryEEyrMOgsk",
    "ksgOMryyyyrMOgsk",
    "kgskMdrryrdMksgk",
    "kgskeMMMMMMeksgk",
    "kGggskkkkkksggGk",
    "kLMMOMMMMMMOMMLk",
    "kOOOOOOOOOOOOOOk",
    "kkkkkkkkkkkkkkkk",
]

# 灵力筑基器顶面：八角黄铜罗盘 + 紫石外框 + 四向翡翠灵力刻度 + 中央辰砂阵眼
TEX_LANDSCAPE_TOP = [
    "kkkkkkkkkkkkkkkk",
    "kLMGggggggggGMLk",
    "kMGgskkkkkksgGMk",
    "kGgsMLLLLLLMsgGk",
    "kgskLooEEooLksgk",
    "kgskLoOrrOoLksgk",
    "kgskEoOyyOoEksgk",
    "kgskEryEEyrEksgk",
    "kgskEryEEyrEksgk",
    "kgskEoOyyOoEksgk",
    "kgskLoOrrOoLksgk",
    "kgskLooEEooLksgk",
    "kGgsMOOOOOOMsgGk",
    "kMGgskkkkkksgGMk",
    "kLMGssssssssGMLk",
    "kkkkkkkkkkkkkkkk",
]

# 灵力筑基器底面：镇地黄铜夯盘 + 紫石底座
TEX_LANDSCAPE_BOTTOM = [
    "kkkkkkkkkkkkkkkk",
    "kLMssssssssssMLk",
    "kMskkkkkkkkkksMk",
    "kskOOOOOOOOOOksk",
    "kskOMMMMMMMMOksk",
    "kskOMLMMMMMLOksk",
    "kskOMMooooMMOksk",
    "kskOMMoOOoMMOksk",
    "kskOMMoOOoMMOksk",
    "kskOMMooooMMOksk",
    "kskOMLMMMMMLOksk",
    "kskOMMMMMMMMOksk",
    "kskOOOOOOOOOOksk",
    "kMskkkkkkkkkksMk",
    "kLMssssssssssMLk",
    "kkkkkkkkkkkkkkkk",
]

# 灵力引爆器方块贴图（16x16，全不透明）：朱漆木匣 + 黄铜铆钉箍 + 八角爆裂雷纹 + 火星引信
PAL_BLOCK_BOMB = {
    'o': (30, 14, 20),       # 深描边 / 漆缝
    's': (76, 20, 30),       # 朱漆暗部
    'b': (122, 34, 46),      # 朱漆主色
    'h': (168, 54, 66),      # 朱漆受光高光
    'O': (112, 78, 38),      # 黄铜暗箍
    'r': (204, 158, 72),     # 黄铜主色 / 金符边
    'R': (248, 218, 132),    # 符印亮金
    'W': (255, 248, 204),    # 雷纹核心白金高光
    'k': (54, 48, 54),       # 引信孔炭黑
    'f': (244, 116, 44),     # 引信火星橙红
}

TEX_BOMB_SIDE = [
    "oooooooooooooooo",
    "oRrrrrrrrrrrrrRo",
    "orhhhhhhhhhhhhro",
    "orhRsoooooosRhro",
    "orhbOrrrrrrObhro",
    "orhbobbrrbbobhro",
    "orhbobrRRrbobhro",
    "orhbsoRWWRosbhro",
    "orhbsoRWWRosbhro",
    "orhbobrRRrbobhro",
    "orhbobbrrbbobhro",
    "orhbOrrrrrrObhro",
    "orsRsoooooosRsro",
    "orssssssssssssro",
    "oRrrrrrrrrrrrrRo",
    "oooooooooooooooo",
]

TEX_BOMB_TOP = [
    "oooooooooooooooo",
    "oRrrrrrrrrrrrrRo",
    "orhhhhhhhhhhhhro",
    "orhRrrrrrrrrRhro",
    "orhrbboooobbrhro",
    "orhrboOOOOobrhro",
    "orhrbOkkkkObrhro",
    "orhrbOkffkObrhro",
    "orhrbOkffkObrhro",
    "orhrbOkkkkObrhro",
    "orhrboOOOOobrhro",
    "orhrbboooobbrhro",
    "orsRrrrrrrrrRsro",
    "orssssssssssssro",
    "oRrrrrrrrrrrrrRo",
    "oooooooooooooooo",
]

TEX_BOMB_BOTTOM = [
    "oooooooooooooooo",
    "oOrrrrrrrrrrrrOo",
    "orssssssssssssro",
    "orsbssoooossbsro",
    "orsbsoOOOOosbsro",
    "orsbsOrrrrOsbsro",
    "orsoorrOOrrsooro",
    "orsooROooORsooro",
    "orsooROooORsooro",
    "orsoorrOOrrsooro",
    "orsbsOrrrrOsbsro",
    "orsbsoOOOOosbsro",
    "orsbssoooossbsro",
    "orssssssssssssro",
    "oOrrrrrrrrrrrrOo",
    "oooooooooooooooo",
]

TEX_BOMB_FUSE = [
    "WWWWWWWWWWWWWWWW",
    "RRRRRRRRRRRRRRRR",
    "ffffffffffffffff",
    "rrrrrrrrrrrrrrrr",
    "kkkkkkkkkkkkkkkk",
    "OOOOOOOOOOOOOOOO",
    "kkkkkkkkkkkkkkkk",
    "kkkkkkkkkkkkkkkk",
    "OOOOOOOOOOOOOOOO",
    "kkkkkkkkkkkkkkkk",
    "kkkkkkkkkkkkkkkk",
    "OOOOOOOOOOOOOOOO",
    "kkkkkkkkkkkkkkkk",
    "kkkkkkkkkkkkkkkk",
    "OOOOOOOOOOOOOOOO",
    "kkkkkkkkkkkkkkkk",
]

TEXES = {
    "block/landscaping_tool": TEX_LANDSCAPE_SIDE,
    "block/landscaping_tool_top": TEX_LANDSCAPE_TOP,
    "block/landscaping_tool_bottom": TEX_LANDSCAPE_BOTTOM,
    "block/spirit_bomb_side": TEX_BOMB_SIDE,
    "block/spirit_bomb_top": TEX_BOMB_TOP,
    "block/spirit_bomb_bottom": TEX_BOMB_BOTTOM,
    "block/spirit_bomb_fuse": TEX_BOMB_FUSE,
}

globals()["PAL_block_landscaping_tool"] = PAL_BLOCK_LANDSCAPE
globals()["PAL_block_landscaping_tool_top"] = PAL_BLOCK_LANDSCAPE
globals()["PAL_block_landscaping_tool_bottom"] = PAL_BLOCK_LANDSCAPE
globals()["PAL_block_spirit_bomb_side"] = PAL_BLOCK_BOMB
globals()["PAL_block_spirit_bomb_top"] = PAL_BLOCK_BOMB
globals()["PAL_block_spirit_bomb_bottom"] = PAL_BLOCK_BOMB
globals()["PAL_block_spirit_bomb_fuse"] = PAL_BLOCK_BOMB
