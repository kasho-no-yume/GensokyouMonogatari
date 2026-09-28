# ritual_fx.py - 运行态特效贴图（加法混合：亮度即贡献，全不透明、向黑渐变=透明）
#   spirit_mist  : 紫雾软团，blur 采样。
#   bolt_core    : 白芯列逐行左右摆出 Z 字，V 平铺即噼啪流动。
#   bolt_glow    : 横向软梯度，奇偶行宽度交替。
#   （迦具土火焰贴图已迁至 fire_fx.py：fire_tongue / fire_bed）

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

# 灵浴水面：对角涟漪带（周期 8，x+y 相位），带一道锐亮波峰（w）读作水面高光。
# U/V 均无缝平铺——渲染端按世界坐标取 uv，相邻格连续，故平铺接缝不可见；V 方向滚动即"流动"。
# 加法混合下暗处≈透明，故谷底抬到 (10,26,52) 而非纯黑：水面读作连续的一片浅蓝，
# 而非一堆分离的亮带。零散白点是碎光（caustic），落在相位 0/7 上，不破坏平铺结构。
PAL_fx_bath_water = {
    'a': (10, 26, 52), 'b': (18, 44, 84), 'c': (28, 64, 112), 'd': (42, 90, 146),
    'e': (60, 120, 178), 'f': (86, 152, 206), 'w': (170, 214, 246),
}
BATH_WATER = [
    "abcdewfdabcdewfd",
    "bcdewfdabcdefdew",
    "cdewfdabcdewfwab",
    "dewfdabwdewfdabc",
    "ewfdabcdewfdabcd",
    "wfdabcdewfdabcde",
    "fdabcdewfdabcdew",
    "dabcdewfdabcwdef",
    "abcdewfdabcdewfd",
    "bcdewfdabcdefdew",
    "cdewfdabcdewfwab",
    "wdewfdabcdewfdab",
    "dewfdabcdewfdabc",
    "ewfdabcdewfdabcd",
    "wfdabcdewfdabcde",
    "fdabcdewfdabcdew",
]

TEXES = {
    "fx/spirit_mist": MIST,
    "fx/bolt_core": BOLT_CORE,
    "fx/bolt_glow": BOLT_GLOW,
    "fx/bath_water": BATH_WATER,
}
