#!/usr/bin/env python3
"""生成 原初造化之仪 gensokyou:zaohua_circle 的 v5 逐级增量 pattern（四分之一规范形）。

设计：层叠台地（中心最高、向外每阶降 1 格）的八方环坛，紫金神坛配色。
  - 每阶向外扩一圈 2 格厚的环形台面（y=顶面 / 顶面-1 打底）
  - 台面外缘立一环 8 座祭品台（四正 (0,R) + 四隅 (d,d)）：0 阶起 8 座，每阶 +8
  - 8 条辐射轴（四正+四隅）铺该阶品阶仪式石；其余台面铺装饰 EXACT
  - 四正外缘逐阶加装饰：石灯 -> 铜柱 -> 紫晶柱 -> 紫珀鸟居 -> 旗幡 -> 巨鸟居

坐标=四分之一规范形（x>=0,z>=0，y 相对核心）；核心 (0,0,0) 仅最低阶声明。
"""
import json
import math
import sys
from pathlib import Path

try:
    sys.stdout.reconfigure(encoding='utf-8')
except Exception:
    pass

OUT = (Path(__file__).resolve().parents[1] / 'src' / 'main' / 'resources'
       / 'data' / 'gensokyou' / 'rituals' / 'zaohua_circle.json')

PALETTE = {
    'C': 'gensokyou:ritual_core',
    '0': '#gensokyou:ritual_stones',
    '1': '#gensokyou:ritual_stones_1_plus',
    '2': '#gensokyou:ritual_stones_2_plus',
    '3': '#gensokyou:ritual_stones_3_plus',
    '4': '#gensokyou:ritual_stones_4_plus',
    '5': '#gensokyou:ritual_stones_5_plus',
    'P': '#gensokyou:ritual_pedestals',
    'A': 'minecraft:stone_bricks',
    'B': 'minecraft:chiseled_stone_bricks',
    'D': 'minecraft:deepslate_bricks',
    'G': 'minecraft:quartz_block',
    'I': 'minecraft:quartz_bricks',
    'K': 'minecraft:soul_lantern',
    'L': 'minecraft:lantern',
    'M': 'minecraft:sea_lantern',
    'N': 'minecraft:end_stone_bricks',
    'O': 'minecraft:purpur_block',
    'Q': 'minecraft:amethyst_block',
    'R': 'minecraft:purple_stained_glass',
    'S': 'minecraft:magenta_stained_glass',
    'U': 'minecraft:chain',
    'V': 'minecraft:waxed_copper_block',
    'X': 'minecraft:cherry_log',
    'Z': 'minecraft:potted_cherry_sapling',
    'a': 'minecraft:gold_block',
    'b': 'minecraft:purpur_pillar',
    'c': 'minecraft:stone_brick_wall',
    'd': 'minecraft:purple_banner',
    'e': 'minecraft:glowstone',
    'h': 'minecraft:deepslate_tiles',
}

R2 = [16, 49, 100, 169, 256, 400]
RAD = [4, 7, 10, 13, 16, 20]
YTOP = [-1, -2, -3, -4, -5, -6]
STONE = ['0', '1', '2', '3', '4', '5']
BASE = ['A', 'G', 'N', 'O', 'I', 'O']
ACC = ['D', 'I', 'O', 'N', 'a', 'Q']
AXIS_PED = [3, 6, 9, 12, 15, 18]
DIAG_PED = [2, 4, 6, 8, 11, 13]

levels = [{} for _ in range(6)]


def canon(x, z):
    x, z = abs(x), abs(z)
    return (0, x) if z == 0 else (x, z)


def add(t, key, x, y, z, o=None, over=False):
    lv = levels[t]
    cx, cz = canon(x, z)
    cell = (cx, y, cz)
    val = (key, o) if o else key
    old = lv.get(cell)
    if old and old != val and not over:
        raise SystemExit(f'冲突 L{t} {cell}: {old} vs {val}')
    lv[cell] = val


def floor_key(t, x, z, r2):
    if t == 0 and r2 <= 4:
        return 'B'
    if x == 0 or z == 0 or x == z:
        return STONE[t]
    if r2 > (RAD[t] - 1) ** 2:
        return ACC[t]
    return BASE[t] if (x + z) % 2 == 0 else ACC[t]


def fill_band(t):
    r2lo = R2[t - 1] + 1 if t > 0 else 0
    for x in range(0, RAD[t] + 1):
        for z in range(0, RAD[t] + 1):
            r2 = x * x + z * z
            if r2 == 0 or r2 < r2lo or r2 > R2[t]:
                continue
            cx, cz = canon(x, z)
            add(t, floor_key(t, cx, cz, r2), cx, YTOP[t], cz)
            add(t, 'D', cx, YTOP[t] - 1, cz)


def add_pedestals(t):
    add(t, 'P', 0, YTOP[t] + 1, AXIS_PED[t])
    add(t, 'P', DIAG_PED[t], YTOP[t] + 1, DIAG_PED[t])


def add_ornament(t):
    if t == 0:
        add(0, 'c', 0, 0, 4)
        add(0, 'L', 0, 1, 4)
        add(0, 'M', 2, -1, 1, over=True)
        add(0, 'M', 1, -1, 2, over=True)
    elif t == 1:
        add(1, 'V', 0, -1, 7)
        add(1, 'V', 0, 0, 7)
        add(1, 'M', 0, 1, 7)
    elif t == 2:
        add(2, 'Q', 0, -2, 10)
        add(2, 'Q', 0, -1, 10)
        add(2, 'R', 0, 0, 10)
        add(2, 'M', 0, 1, 10)
    elif t == 3:
        for (px, pz) in [(2, 12), (12, 2)]:
            for y in range(-3, 1):
                add(3, 'b', px, y, pz)
        for (px, pz) in [(0, 12), (1, 12), (12, 0), (12, 1)]:
            add(3, 'O', px, 0, pz)
        for (px, pz) in [(0, 12), (1, 12), (2, 12), (12, 0), (12, 1), (12, 2)]:
            add(3, 'O', px, 1, pz)
        add(3, 'U', 0, -1, 12)
        add(3, 'U', 12, -1, 0)
    elif t == 4:
        for y in (-4, -3, -2):
            add(4, 'X', 0, y, 16)
        add(4, 'd', 0, -1, 16, o='r0')
        add(4, 'Z', 1, -4, 15)
        add(4, 'Z', 15, -4, 1)
    elif t == 5:
        for (px, pz) in [(3, 19), (19, 3)]:
            for y in range(-5, 2):
                add(5, 'b', px, y, pz)
        for (px, pz) in [(0, 19), (1, 19), (2, 19), (19, 0), (19, 1), (19, 2)]:
            add(5, 'O', px, 0, pz)
        for (px, pz) in [(0, 19), (1, 19), (2, 19), (3, 19),
                         (19, 0), (19, 1), (19, 2), (19, 3)]:
            add(5, 'O', px, 2, pz)
        add(5, 'U', 0, -1, 19)
        add(5, 'U', 19, -1, 0)
        add(5, 'e', 0, 1, 19)
        add(5, 'e', 19, 1, 0)
        for (px, pz) in [(2, 18), (18, 2), (3, 18), (18, 3)]:
            add(5, 'Z', px, -5, pz)


for t in range(6):
    if t:
        levels[t] = dict(levels[t - 1])
    fill_band(t)
    add_pedestals(t)
    add_ornament(t)

add(0, 'B', 0, -1, 0)
add(0, 'D', 0, -2, 0)

for lv in levels:
    lv[(0, 0, 0)] = 'C'


def ser_levels():
    out = []
    for i in range(6):
        prev = levels[i - 1] if i else {}
        adds = []
        for (x, y, z), val in sorted(levels[i].items()):
            if (x, y, z) in prev:
                continue
            if isinstance(val, tuple):
                k, o = val
                adds.append([k, x, y, z, o])
            else:
                adds.append([val, x, y, z])
        out.append({'level': i, 'adds': adds})
    return out


levels_json = ser_levels()
head = json.dumps({
    'id': 'gensokyou:zaohua_circle',
    'anchorKey': 'C',
    'toggleable': False,
    'tiers': [0, 1, 2, 3, 4, 5],
    'palette': PALETTE,
}, indent=2, ensure_ascii=False)

body = []
for j, lv in enumerate(levels_json):
    body.append('    { "level": %d, "adds": [' % lv['level'])
    for i, a in enumerate(lv['adds']):
        tail = ',' if i < len(lv['adds']) - 1 else ''
        body.append('      ' + json.dumps(a, ensure_ascii=False, separators=(',', ':')) + tail)
    body.append('    ] }' + (',' if j < len(levels_json) - 1 else ''))
text = head[:-2] + ',\n  "levels": [\n' + '\n'.join(body) + '\n  ]\n}\n'
OUT.write_text(text, encoding='utf-8')

SI = set(STONE)
for i, cells in enumerate(levels):
    stone = sum(1 for v in cells.values() if (v[0] if isinstance(v, tuple) else v) in SI)
    prev = levels[i - 1] if i else {}
    adds = sum(1 for c in cells if c not in prev)
    ratio = stone / len(cells) if cells else 0
    print(f'level {i}: 累积 {len(cells)} 格（新增 {adds}）石族 {stone} ({ratio:.1%})')
print(f'写入 {OUT} ({OUT.stat().st_size} 字节)')
