# ritual_fx.py - 运行态特效贴图（加法混合：亮度即贡献，全不透明、向黑渐变=透明）
#   flame_column : U 横跨柱宽（边缘黑、中心亮芯），V 沿高无缝循环；暗/亮带滚动 = 火苗上升。
#   spirit_mist  : 紫雾软团，blur 采样。
#   bolt_core    : 白芯列逐行左右摆出 Z 字，V 平铺即噼啪流动。
#   bolt_glow    : 横向软梯度，奇偶行宽度交替。

PAL_fx_flame_column = {
    'a': (8, 3, 1), 'b': (70, 24, 6), 'c': (170, 62, 12),
    'd': (240, 118, 26), 'e': (255, 176, 60), 'f': (255, 236, 150),
}
FLAME = [
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

PAL_fx_spirit_mist = {
    'a': (6, 3, 10), 'b': (26, 14, 42), 'c': (58, 32, 86),
    'd': (96, 52, 134), 'e': (136, 76, 174), 'f': (172, 108, 208),
}
MIST = [
    "aabcccccbbbbaaba",
    "abccddeeeccbbacc",
    "acddeeeffeddccde",
    "abccdeeefedcccdb",
    "aabcaccbccaabbaa",
    "aabaacccaabaaaaa",
    "bccccbbccbbbaacc",
    "ccddddcceccbdddd",
    "cdeeeffccdeeefff",
    "cdeeffffcdeeffff",
    "bccddefedbccddee",
    "aabbccdccbbaabcc",
    "aaaabbaaaccaaacc",
    "ccddbbcddccbbbdd",
    "cdeedccdeecccdee",
    "bcccbbaacbbabbac",
]

PAL_fx_bolt_core = {
    'a': (0, 0, 0), 'b': (90, 110, 150), 'c': (160, 200, 235), 'f': (255, 255, 255),
}
BOLT_CORE = [
    "aaaaabccffcaaaaa", "aaaabccffcaaaaaa",
    "aaaaaabccffcaaaa", "aaaaaaccffffaaaa",
] * 4

PAL_fx_bolt_glow = {
    'a': (0, 0, 0), 'b': (60, 70, 85), 'c': (120, 140, 165), 'f': (230, 240, 250),
}
BOLT_GLOW = [
    "aabbccffffccbbaa", "aabcccffffffccba",
] * 8

TEXES = {
    "fx/flame_column": FLAME,
    "fx/spirit_mist": MIST,
    "fx/bolt_core": BOLT_CORE,
    "fx/bolt_glow": BOLT_GLOW,
}
