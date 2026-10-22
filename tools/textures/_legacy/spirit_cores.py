# spirit_cores.py - 六阶灵力核心 spirit_core_0..5
# 复用菱形晶核轮廓，品阶=TierPalette 主色整体换色：
#   0 灰（无彩）/ 1 绿 / 2 蓝 / 3 金 / 4 红 / 5 紫；
# 外环符文点 n 随阶递增点亮（t1 无点，t2..t5 逐阶 +1 对）。

TIER_GEM = [
    # (suffix, glass(p/H/G), core(a/C/s))
    ("item/spirit_core_0", ((92, 88, 102), (152, 148, 162), (58, 54, 68)),
     ((158, 158, 158), (228, 228, 228), (104, 104, 104))),
    ("item/spirit_core_1", ((86, 64, 118), (140, 112, 176), (56, 40, 84)),
     ((76, 175, 80), (178, 226, 180), (38, 118, 42))),
    ("item/spirit_core_2", ((82, 60, 120), (134, 108, 178), (54, 38, 86)),
     ((33, 150, 243), (144, 205, 249), (18, 96, 186))),
    ("item/spirit_core_3", ((80, 56, 116), (132, 104, 172), (52, 36, 82)),
     ((255, 193, 7), (255, 224, 130), (178, 128, 10))),
    ("item/spirit_core_4", ((78, 52, 112), (130, 98, 166), (50, 34, 78)),
     ((244, 67, 54), (255, 176, 152), (178, 26, 26))),
    ("item/spirit_core_5", ((74, 46, 108), (124, 92, 158), (46, 30, 74)),
     ((156, 39, 176), (214, 152, 224), (100, 20, 118))),
]

BASE = [
    "................",
    "......dddd......",
    "....ddppppdd....",
    "...dpHHHHHHpd...",
    "..dHHpaCCapHHd..",
    ".dHppaCCCCappd..",
    "dHppaCCCCCCappHd",
    "dGppaCCCCCCappGd",
    "dGppssCCaassGpGd",
    ".dGppaaCCaappGd.",
    "..dGppaaappGd...",
    "...dGppppppGd...",
    "....dGppppGd....",
    ".....dGGGGd.....",
    "......dddd......",
    "................",
]

# 校验底图行宽（对齐错误在此即报）
for _r in BASE:
    assert len(_r) == 16, f"bad row len {len(_r)}: {_r}"

RUNE_GROUPS = [  # (y, x) 成对镜像，点亮顺序自下而上
    [(3, 6), (3, 9)],
    [(12, 6), (12, 9)],
    [(9, 3), (9, 12)],
    [(6, 3), (6, 12)],
    [(13, 2), (13, 13)],
]

PAL_FIXED = {
    'n': None,  # 运行时替换为品阶主色
}


def _rows_with_runes(tier):
    # 以 t0 底图复制；符文点由内向外成对点亮（count = tier-1，t1 仍为 0 → 外观同 t0 但换色）
    rows = [list(r) for r in BASE]
    for g in range(max(0, tier - 1)):
        for (y, x) in RUNE_GROUPS[g]:
            rows[y][x] = 'n'
    return ["".join(r) for r in rows]


TEXES = {}
for i, (name, glass, core) in enumerate(TIER_GEM):
    p, h, gshade = glass
    a, c, s = core
    pal = {
        '.': None,
        'd': (38, 22, 54) if i > 0 else (40, 38, 46),
        'p': p, 'H': h, 'G': gshade,
        'a': a, 'C': c, 's': s,
        'n': a,
    }
    pal = {**pal, 'n': (min(255, a[0] + 40), min(255, a[1] + 40), min(255, a[2] + 40))}
    TEXES[name] = _rows_with_runes(i)
    globals()[f"PAL_{name.replace('/', '_')}"] = pal
