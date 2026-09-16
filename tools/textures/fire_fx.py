# fire_fx.py - 迦具土贴地烈火场贴图（加法混合：亮度即贡献，向黑渐变=不可见）
#   fire_tongue : 低伏火舌条带，U 横跨柱宽（中心亮芯、边缘黑），V 沿高无缝循环；滚动=火苗上升。
#   fire_bed    : 贴地余烬/辉光软团，中心亮、径向向黑收敛（叠加出"地面被炙烤"光斑）。

PAL_fx_fire_tongue = {
    'a': (8, 3, 1), 'b': (70, 24, 6), 'c': (170, 62, 12),
    'd': (240, 118, 26), 'e': (255, 176, 60), 'f': (255, 236, 150),
}
FIRE_TONGUE = [
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

PAL_fx_fire_bed = {
    'a': (0, 0, 0), 'b': (30, 10, 2), 'c': (80, 30, 6),
    'd': (150, 64, 14), 'e': (225, 130, 42), 'f': (255, 205, 95),
}
FIRE_BED = [
    "aaaaaaaaaaaaaaaa",
    "aaaabbbbbbbbaaaa",
    "aaabbbccccbbbaaa",
    "aabbccccccccbbaa",
    "abbccddddddccbba",
    "abbcddddddddcbba",
    "abccddeeeeddccba",
    "abccddeffeddccba",
    "abccddeffeddccba",
    "abccddeeeeddccba",
    "abbcddddddddcbba",
    "abbccddddddccbba",
    "aabbccccccccbbaa",
    "aaabbbccccbbbaaa",
    "aaaabbbbbbbbaaaa",
    "aaaaaaaaaaaaaaaa",
]

TEXES = {
    "fx/fire_tongue": FIRE_TONGUE,
    "fx/fire_bed": FIRE_BED,
}
