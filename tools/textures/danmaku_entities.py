# danmaku_entities.py - 弹幕实体贴图（全部白/浅灰为主，颜色由渲染器顶点色 tint）
# laser_danmaku: U=横跨光束宽度（中心亮两侧渐隐），V=沿长度 1 格/次无缝平铺 → 各行一致
# laser_cap:     径向渐变圆斑（中心白边缘透明）
# talisman_danmaku: 竖长符纸（billboard 0.34x0.52）
# knife_danmaku: 竖直细刀，刀尖朝图下方（V=1 = 飞行方向前端）

TEXES = {}

# ---- 激光光束：16 行相同（V 平铺无缝），列向 5 档渐隐 ----
_BEAM_ROW = "..." + "EDCB" + "AA" + "BCED" + "..."    # 3+4+2+4+3 = 16，中心 AA 在 cols 7-8
TEXES["entity/laser_danmaku"] = [_BEAM_ROW] * 16

# ---- 激光端盖：径向量化 4 环（程序化生成 ASCII）----
_cap_rows = []
for _y in range(16):
    _row = []
    for _x in range(16):
        _d = ((_x - 7.5) ** 2 + (_y - 7.5) ** 2) ** 0.5
        _row.append("A" if _d <= 2.0 else "B" if _d <= 3.5 else "C" if _d <= 5.0 else "D" if _d <= 6.5 else ".")
    _cap_rows.append("".join(_row))
TEXES["entity/laser_cap"] = _cap_rows

# ---- 灵符：竖长符纸，白纸 + 深灰咒线 ----
TEXES["entity/talisman_danmaku"] = [
    "................",
    "......ssss......",
    ".....sWWWWDs....",
    ".....sWWWWDs....",
    ".....sSSSWDs....",
    ".....sWWWWDs....",
    ".....sWWSSDs....",
    ".....sWWWWDs....",
    ".....sSSSWDs....",
    ".....sWWWWDs....",
    ".....sWWSSDs....",
    ".....sWWWWDs....",
    ".....sWWWWDs....",
    ".....sWWWWDs....",
    "......ssss......",
    "................",
]

# ---- 飞刀：16×16 图集（配合 KnifeDanmakuRenderer 的苦无模型）----
# 刃=左上8x8（u沿刃长：基部暗→尖端亮）；护手=右上rows0-1；柄=rows2-9（u沿柄长缠绳条纹）；柄尾=rows10-11
_blade = "hh" + "WWWW" + "BB"
_guard_top = "G" * 8
_guard_bot = "d" * 8
_handle = "ddDDddDD"
_pommel = "G" * 8
_fill = "D" * 8
TEXES["entity/knife_danmaku"] = [
    _blade + _guard_top,   # 护手亮缘
    _blade + _guard_bot,   # 护手
] + [
    _blade + _handle,      # 柄（缠绳条纹）×8
] * 8 + [
    _blade + _pommel,      # 柄尾
    _blade + _pommel,
] + [
    _blade + _fill,
] * 4

PAL_entity_laser_danmaku = {
    '.': None,
    'A': (255, 255, 255, 255),
    'B': (235, 240, 255, 190),
    'C': (215, 228, 255, 120),
    'D': (200, 216, 255, 65),
    'E': (190, 208, 255, 30),
}
PAL_entity_laser_cap = {
    '.': None,
    'A': (255, 255, 255, 255),
    'B': (255, 255, 255, 200),
    'C': (255, 255, 255, 120),
    'D': (255, 255, 255, 50),
}
PAL_entity_talisman_danmaku = {
    '.': None,
    'W': (250, 250, 252),   # 纸白
    'D': (208, 206, 214),   # 纸影
    's': (96, 92, 100),     # 符纸边
    'S': (60, 56, 66),      # 咒文深
}
PAL_entity_knife_danmaku = {
    '.': None,
    'W': (245, 246, 250),   # 刃主色
    'B': (255, 255, 255),   # 刃尖端
    'h': (205, 208, 215),   # 刃基部
    'G': (95, 98, 108),     # 护手/柄尾
    'd': (70, 72, 80),      # 缠绳亮
    'D': (58, 56, 64),      # 柄暗
}
