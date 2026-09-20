#!/usr/bin/env python3
"""程序化生成「无尽藏」wujinzang_circle.json（v5 逐级增量，四重对称四分之一规范形）。

圆形主基调：
  地面：逐阶外扩的圆形石台（r² 阈值），外缘铺该阶仪式石，材质随阶跳变。
  柱廊：16 根石柱环列于 r≈8 的圆周（4 组方位 ×4）。
  悬空圆环：半径不一的圆环楼台（内缘 r=8 贴柱，外半径 11/13/10/14 各异）
            + 顶悬圆环（铁链吊起）。层间铁链垂挂成"垂镜"。
  祭品台 4/8/16/32/64/128：散布于地面 y0 与各层圆环 y7/11/15/19/25。
保留位（不写进 pattern）：核心四向 (±1,0)/(0,±1)@y0、正上 (0,1,0)、电容位 (2,2)@y1。
"""
import json
import math
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import validate_ritual_pattern as v5  # noqa: E402

OUT = (Path(__file__).resolve().parents[1] / 'src' / 'main' / 'resources'
       / 'data' / 'gensokyou' / 'rituals' / 'wujinzang_circle.json')

PALETTE = {
    'C': 'gensokyou:ritual_core',
    '0': '#gensokyou:ritual_stones',
    '1': '#gensokyou:ritual_stones_1_plus',
    '2': '#gensokyou:ritual_stones_2_plus',
    '3': '#gensokyou:ritual_stones_3_plus',
    '4': '#gensokyou:ritual_stones_4_plus',
    '5': '#gensokyou:ritual_stones_5_plus',
    'p': '#gensokyou:ritual_pedestals',
    'B': 'minecraft:deepslate_bricks',
    'T': 'minecraft:deepslate_tiles',
    'A': 'minecraft:amethyst_block',
    'U': 'minecraft:quartz_block',
    'G': 'minecraft:gold_block',
    'V': 'minecraft:purple_stained_glass',
    'a': 'minecraft:magenta_stained_glass',
    'L': 'minecraft:lantern',
    'M': 'minecraft:sea_lantern',
    'H': 'minecraft:chain',
    'W': 'minecraft:potted_cherry_sapling',
    'N': 'minecraft:soul_lantern',
    'O': 'minecraft:crying_obsidian',
    'E': 'minecraft:polished_blackstone_bricks',
    'F': 'minecraft:gilded_blackstone',
    'S': 'minecraft:end_stone_bricks',
    'J': 'minecraft:end_stone',
    'Z': 'minecraft:purpur_block',
    'R': 'minecraft:purpur_pillar',
}

R_GROUND = [3, 7, 10, 12, 15, 18]
RIM = ['0', '1', '2', '3', '4', '5']
COL_TOP = [None, None, 6, 10, 14, 18]
DECK_Y = [6, 10, 14, 18]
DECK_R = [11, 13, 10, 14]
COLS = None

ADD = [dict() for _ in range(6)]


def canon(x, z):
    x, z = abs(x), abs(z)
    return (0, x) if z == 0 else (x, z)


def put(lv, key, x, y, z, force=False):
    cx, cz = canon(x, z)
    if cx == 0 and cz == 0 and y == 0:
        raise SystemExit(f'原点 y=0 保留给锚点 C: level {lv} key={key}')
    cell = (cx, y, cz)
    old = ADD[lv].get(cell)
    if old and old != key and not force:
        raise SystemExit(f'冲突 level {lv} {cell}: {old} vs {key}')
    ADD[lv][cell] = key


def csq(lo2, hi2, exclude=()):
    hi = math.isqrt(hi2)
    out = set()
    for x in range(0, hi + 1):
        for z in range(0, hi + 1):
            q = x * x + z * z
            if lo2 < q <= hi2:
                c = canon(x, z)
                if c not in exclude:
                    out.add(c)
    return sorted(out, key=lambda c: math.atan2(c[1], c[0]))


def pick(cells, n, rot=0):
    m = len(cells)
    return [cells[(rot + int(round(i * m / n))) % m] for i in range(n)]


def floor_mat(k, q):
    if k <= 1:
        return 'B'
    if k == 2:
        return 'S'
    if k == 3:
        return 'J'
    return 'G' if q % 13 == 0 else 'Z'


def deck_key(q):
    if q % 7 == 0:
        return 'G'
    if q % 3 == 0:
        return 'a'
    return 'Z'


def ring(lv, lo2, hi2, y, fn):
    for (x, z) in csq(lo2, hi2):
        put(lv, fn(x * x + z * z), x, y, z)


def mirror_ring(lv, R, y):
    for dy in (0, 1):
        ring(lv, (R - 1) ** 2, R ** 2, y + dy,
             lambda q: 'R' if (q // 3 + dy) % 2 else 'V')


def column(lv, y0, y1):
    for (x, z) in COLS:
        for y in range(y0, y1 + 1):
            put(lv, 'G' if y % 5 == 0 else 'R', x, y, z)


# ---------------- 地面圆台 ----------------
for k in range(6):
    lo2 = R_GROUND[k - 1] ** 2 if k else -1
    rim2 = (R_GROUND[k] - 1) ** 2
    for (x, z) in csq(lo2, R_GROUND[k] ** 2):
        q = x * x + z * z
        put(k, RIM[k] if q > rim2 else floor_mat(k, q), x, -1, z)

COLS = csq(57, 63)

# ---------------- Ⅰ 藏匣 ----------------
put(0, 'G', 0, -1, 0, force=True)
put(0, 'A', 0, -1, 2, force=True)
put(0, 'M', 1, -1, 1, force=True)
for (x, z) in csq(1, 2):
    put(0, 'T', x, 0, z)
    put(0, 'T', x, 1, z)
    put(0, 'A', x, 2, z)
for (x, z) in csq(1, 8):
    put(0, 'B', x, 3, z)
put(0, 'L', 0, 0, 3)
put(0, 'W', 2, 0, 2)

# ---------------- Ⅱ 藏门 ----------------
for y in range(0, 5):
    put(1, 'E', 1, y, 6)
    put(1, 'E', 2, y, 6)
for y in range(1, 5):
    put(1, 'V', 0, y, 6)
for (x, z) in csq(30, 36):
    put(1, 'F', x, 5, z)
put(1, 'N', 2, 6, 6)
put(1, 'W', 0, 0, 4)

# ---------------- Ⅲ 藏界 ----------------
column(2, 0, COL_TOP[2])
ring(2, 63, DECK_R[0] ** 2, DECK_Y[0], deck_key)
mirror_ring(2, DECK_R[0], DECK_Y[0] + 1)
put(2, 'O', 0, -1, 8, force=True)

# ---------------- Ⅳ 藏宫 ----------------
column(3, COL_TOP[2] + 1, COL_TOP[3])
ring(3, 63, DECK_R[1] ** 2, DECK_Y[1], deck_key)
mirror_ring(3, DECK_R[1], DECK_Y[1] + 1)

# ---------------- Ⅴ 藏天 ----------------
column(4, COL_TOP[3] + 1, COL_TOP[4])
ring(4, 63, DECK_R[2] ** 2, DECK_Y[2], deck_key)
mirror_ring(4, DECK_R[2], DECK_Y[2] + 1)
for (hx, hz) in ((0, 12), (8, 8)):
    for y in (9, 8, 7):
        put(4, 'H', hx, y, hz)
    put(4, 'V', hx, 6, hz)

# ---------------- Ⅵ 无尽 ----------------
column(5, COL_TOP[4] + 1, COL_TOP[5])
ring(5, 63, DECK_R[3] ** 2, DECK_Y[3], deck_key)
mirror_ring(5, DECK_R[3], DECK_Y[3] + 1)
ring(5, 81, 121, 24, deck_key)
for (cx, cz) in ((0, 10), (8, 6)):
    for y in (19, 20, 21, 22, 23):
        put(5, 'H', cx, y, cz)
ring(5, 64, 100, 28, deck_key)
for (cx, cz) in ((0, 10), (8, 6)):
    for y in (25, 26, 27):
        put(5, 'H', cx, y, cz)

# ---------------- 祭品台（地面 / 各层圆环），(轨道数, lo², hi², y, rot) ----------------
PED = {
    0: [(1, 2, 9, 0, 0)],
    1: [(1, 9, 49, 0, 0)],
    2: [(2, 63, (DECK_R[0] - 1) ** 2, DECK_Y[0] + 1, 0)],
    3: [(2, 100, 144, 0, 0),
        (2, 63, (DECK_R[1] - 1) ** 2, DECK_Y[1] + 1, 0)],
    4: [(2, 144, 225, 0, 0),
        (3, 63, (DECK_R[1] - 1) ** 2, DECK_Y[1] + 1, 1),
        (3, 63, (DECK_R[2] - 1) ** 2, DECK_Y[2] + 1, 0)],
    5: [(4, 63, (DECK_R[2] - 1) ** 2, DECK_Y[2] + 1, 3),
        (6, 63, (DECK_R[3] - 1) ** 2, DECK_Y[3] + 1, 0),
        (4, 81, 121, 25, 0),
        (2, 225, 324, 0, 0)],
}
for k in range(6):
    for (n, lo2, hi2, y, rot) in PED[k]:
        cells = csq(lo2, hi2)
        for (x, z) in pick(cells, n, rot=rot):
            put(k, 'p', x, y, z, force=True)

ADD[0][(0, 0, 0)] = 'C'

levels = []
for k in range(6):
    cur = dict(levels[k - 1]) if k else {}
    for cell, key in ADD[k].items():
        if cell in cur and cur[cell] != key:
            raise SystemExit(f'跨级冲突 level {k} {cell}: {cur[cell]} vs {key}')
        cur[cell] = key
    levels.append(cur)

doc = {
    'id': 'gensokyou:wujinzang_circle',
    'anchorKey': 'C',
    'toggleable': False,
    'tiers': [0, 1, 2, 3, 4, 5],
    'palette': PALETTE,
}

levels_json = [{'level': i} for i in range(6)]
adds_per_level = []
for i in range(6):
    prev = levels[i - 1] if i else {}
    adds_per_level.append([(key, x, y, z)
                           for (x, y, z), key in sorted(levels[i].items())
                           if (x, y, z) not in prev])

text = (json.dumps(doc, indent=2, ensure_ascii=False)[:-1].rstrip()
        + ',\n  "levels": ' + v5.serialize_levels_v5(levels_json, adds_per_level)
        + '\n}\n')
OUT.write_text(text, encoding='utf-8')

for i, cells in enumerate(levels):
    by_key = {}
    for key in cells.values():
        by_key[key] = by_key.get(key, 0) + 1
    ped = by_key.get('p', 0)
    if ped != 2 ** i:
        raise SystemExit(f'level {i} 台位轨道数 {ped} != {2 ** i}（台位冲突）')
    rock = sum(v for kk, v in by_key.items() if kk in '012345')
    detail = ', '.join(f'{kk}x{v}' for kk, v in sorted(by_key.items()))
    print(f'level {i}: 累积 {len(cells)} 格（新增 {len(adds_per_level[i])}）'
          f' 石x{rock} 台x{ped} [{detail}]')
print(f'写入 {OUT} ({OUT.stat().st_size} 字节)')
