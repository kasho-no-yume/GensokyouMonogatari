# aura_fx.py - 无尽藏晶脚下灵焰贴图（加法混合：亮度即贡献，向黑=不可见）
#   aura_flame : 纵向无缝循环的火焰条带（中心亮芯、两侧向黑收敛、亮斑上下错落成火舌）。
#                灰度底图，运行时按虹彩色 tint；V 向上滚动即"燃烧上升"，
#                配合弯曲摆动的 ribbon 几何出火苗扰动。

PAL_fx_aura_flame = {
    'a': (10, 10, 14), 'b': (58, 58, 68), 'c': (118, 120, 135),
    'd': (178, 182, 198), 'e': (225, 232, 248), 'f': (255, 255, 255),
}
AURA_FLAME = [
    "aaacccddddccccaa",
    "aaacccccbbbbcccc",
    "aabbccdeeffeccbb",
    "aaacccdddcccccdd",
    "aabacccceeffecdb",
    "aaacccddddccccaa",
    "aaacccccbbbbcccc",
    "aabacccdddcccabb",
    "aabbccdeeffeccbb",
    "aaacccddccdcccda",
    "aaacccddddccccaa",
    "aabbccdeeffeccbb",
    "aaacccddccdcccda",
    "aaacccddddccccaa",
    "aabacccceeffecdb",
    "aaacccccbbbbcccc",
]

TEXES = {
    "fx/aura_flame": AURA_FLAME,
}
