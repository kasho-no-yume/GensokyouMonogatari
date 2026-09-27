# summon_fx.py - 百鬼夜行召唤演出贴图
#   summon_blob  : 充能球与冲击环共用的径向软团。**加法混合**：全不透明，向黑渐变=透明贡献。
#   summon_debris: 爆散碎片软团。**alpha 混合**（加法只能加亮、读不出"碎"感，故此处走真 alpha）。
#   summon_pillar: 降临光柱竖向渐变。U 横向柔和、V 纵向亮芯（UV 随 gameTime 上滚）。
#
# 配色：充能/爆散 = 黑红（暗血红 → 赤红 → 暗红棕）；降临 = 淡金。

# ---- summon_blob：16x16 径向软团，加法用（黑=透明）----
PAL_fx_summon_blob = {
    'a': (0, 0, 0), 'b': (26, 2, 2), 'c': (58, 4, 4),
    'd': (104, 8, 8), 'e': (150, 14, 14), 'f': (196, 26, 22),
}
SUMMON_BLOB = [
    "aaaaabccccbbaaaa",
    "aaabbcddddcbbbaa",
    "aabbcddeeeedccba",
    "abcddeeeefffedda",
    "abcdeeeffffffedc",
    "bcdeeefffffffeec",
    "bcddefffffffeedc",
    "bcddefffffffddcc",
    "bcddefffffffddcc",
    "bcdeeefffffffeec",
    "abcdeeeffffffedc",
    "abcddeeeefffedda",
    "aabbcddeeeedccba",
    "aaabbcddddcbbbaa",
    "aaaaabccccbbaaaa",
    "aaaaaabbbbbbaaaa",
]

# ---- summon_debris：16x16 软边烟团，alpha 混合用（真透明边缘）----
# 色相刻意压到暗红棕：alpha 混合下 74/12/16 才是"碎块"的读感，
# 若沿用加法组的亮红会读成"又一群发光小球"。
PAL_fx_summon_debris = {
    '.': None,
    'a': (30, 4, 6, 255), 'b': (52, 8, 12, 235), 'c': (74, 12, 16, 200),
    'd': (96, 18, 20, 150), 'e': (118, 24, 24, 95), 'f': (140, 32, 28, 45),
}
SUMMON_DEBRIS = [
    "......ff........",
    ".....ffeeed.....",
    "....ffeeddcb....",
    "...ffeeddcbba...",
    "..ffeeddcbbaa...",
    ".ffeeddcbbaa....",
    ".ffeddcbbaa.....",
    ".ffeddcbbaa.....",
    ".ffeddcbbaa.....",
    ".ffeddcbbaa.....",
    ".ffeeddcbbaa....",
    ".ffeddcbbaa.....",
    "..ffeeddcbbaa...",
    "...ffeeddcbba...",
    "....ffeeddcb....",
    ".....ffeeed.....",
]

# ---- summon_pillar：16x16 竖向渐变。V 方向亮芯居中、上下渐隐（柱体侧壁用）----
# 横向上下边缘也要收，否则正对柱身时会看到一条硬边。
# 构造上每行恒为「2 边 + 12 芯 + 2 边」，保证宽度恒为 16。
PAL_fx_summon_pillar = {
    'a': (150, 122, 58, 0), 'b': (196, 162, 84, 90), 'c': (226, 196, 122, 170),
    'd': (244, 222, 164, 225), 'f': (255, 246, 210, 255),
}
SUMMON_PILLAR = [
    "aaaaaaaaaaaaaaaa",
    "aabbbccdccbbbaaa",
    "abbccdffdccbbaaa",
    "abccdfffdccbbaaa",
    "abbcfffffdcbbaaa",
    "aabbcffdccbbbaaa",
    "aabbcffdccbbbaaa",
    "aabbcffdccbbbaaa",
    "aabbcffdccbbbaaa",
    "aabbcffdccbbbaaa",
    "aabbcffdccbbbaaa",
    "abbcfffffdcbbaaa",
    "abccdfffdccbbaaa",
    "abbccdffdccbbaaa",
    "aabbbccdccbbbaaa",
    "aaaaaaaaaaaaaaaa",
]

TEXES = {
    "fx/summon_blob": SUMMON_BLOB,
    "fx/summon_debris": SUMMON_DEBRIS,
    "fx/summon_pillar": SUMMON_PILLAR,
}
