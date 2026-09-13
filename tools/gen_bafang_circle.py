#!/usr/bin/env python3
"""八方归元之仪 bafang_guiyuan_circle：2~5 阶八角祭坛（v5 逐级增量，四分之一规范形）。

蓝图（累积口径；多层圆台祭坛 + 八方祭品台，容量=结构内祭品台数）：
  L2（台4） 底盘 r²≤70（y-1 深板岩砖，唇边 56..70 磨制深板岩）
      y0  核心居中；台×4 轴向 (0,5)；受供围石 (0,6)(1,5)；外墙环带 52..72（四轴缺口 (0,8)）
      y1  墙顶石盖 52..72；地面灯珠 (0,7)(6,3) 海晶灯
      留空：核心四向 (0,1)@y0、核心正上、电容 (2,2)@y1
  L3（台+4=8） 外环地坪 r²≤137（末地石砖，唇边 130..137 磨制深板岩）
      y0  台×4 斜向 (5,5)；受供石 (4,5)
      入口门柱：缺口 (0,8) y1..2 石砖墙 + 紫玻璃 y3
      地面灯位 (4,4)(4,5) 魂灯笼
  L4（台+8=16） 外苑环地坪 r²≤226（深板岩瓦，唇边 214..226 磨制深板岩）
      y0  外苑矮墙 73..90（四轴缺口 (0,9)）；台前石 (9,4)(4,9)
      y1  抬升环台 73..90（磨制深板岩），台×8 双 orbit (7,6)(6,7)，台正下 y0 留空
      鸟居×4：柱 (0,11) y0..6、楣 y7 (0,10)(0,11)(0,12)（中段哭泣石）、笠冠 (1,11)(1,10) 紫晶
  L5（台+8=24） 最外环地坪 r²≤380（黑石砖，唇边 280..380 抛光玄武岩）
      参道轴向 (0,d) d=17..19；y0 台×8 双 orbit (10,8)(12,8)；台侧石 (10,7)(7,10)
      外缘矮墙 330..380；角灯柱 (15,5)(5,15) 黑石墙×2+魂灯
"""
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import validate_ritual_pattern as v5  # noqa: E402

OUT = (Path(__file__).resolve().parents[1] / 'src' / 'main' / 'resources'
       / 'data' / 'gensokyou' / 'rituals' / 'bafang_guiyuan_circle.json')

PALETTE = {
    'C': 'gensokyou:ritual_core',
    'e': '#gensokyou:ritual_stones_2_plus',
    'f': '#gensokyou:ritual_stones_3_plus',
    'g': '#gensokyou:ritual_stones_4_plus',
    'h': '#gensokyou:ritual_stones_5_plus',
    'a': '#gensokyou:ritual_pedestals',
    'b': '#gensokyou:ritual_pedestals',
    'c': '#gensokyou:ritual_pedestals',
    'd': '#gensokyou:ritual_pedestals',
    'q': 'minecraft:deepslate_bricks',
    'r': 'minecraft:polished_deepslate',
    's': 'minecraft:stone_brick_wall',
    't': 'minecraft:end_stone_bricks',
    'u': 'minecraft:deepslate_tiles',
    'v': 'minecraft:polished_blackstone_bricks',
    'w': 'minecraft:polished_basalt',
    'i': 'gensokyou:ritual_stone_slab_2',
    'j': 'gensokyou:ritual_stone_slab_3',
    'k': 'minecraft:quartz_pillar',
    'l': 'minecraft:purple_stained_glass',
    'm': 'minecraft:sea_lantern',
    'n': 'minecraft:soul_lantern',
    'o': 'minecraft:amethyst_block',
    'p': 'minecraft:crying_obsidian',
    'x': 'minecraft:blackstone_wall',
}

# levels 键=阶级号 2..5
levels = {i: {} for i in range(2, 6)}


def canon(x, z):
    x, z = abs(x), abs(z)
    return (0, x) if z == 0 else (x, z)


def put(lv, key, x, y, z):
    cx, cz = canon(x, z)
    if cx == 0 and cz == 0:
        raise SystemExit(f'原点保留给锚点 C: level {lv} key={key}')
    cell = (cx, y, cz)
    old = levels[lv].get(cell)
    if old and old != key:
        raise SystemExit(f'冲突 level {lv} {cell}: {old} vs {key}')
    levels[lv][cell] = key


def slab(lv, y, r2min, r2max, skip, maker):
    for x in range(0, 25):
        for z in range(0, 20):
            r2 = x * x + z * z
            if r2 == 0 or not r2min <= r2 <= r2max:
                continue
            c = canon(x, z)
            if c in skip:
                continue
            key = maker(c)
            if key:
                put(lv, key, x, y, z)


def pillar(lv, key, x, z, y0, y1):
    for y in range(y0, y1 + 1):
        put(lv, key, x, y, z)


# ---- level 2：底盘 + 台×4 ----
slab(2, -1, 1, 70, {(0, 7), (6, 3)},
     lambda c: 'r' if 56 <= c[0] * c[0] + c[1] * c[1] <= 70 else 'q')
put(2, 'm', 0, -1, 7)
put(2, 'm', 6, -1, 3)
put(2, 'a', 0, 0, 5)
put(2, 'e', 0, 0, 6)
put(2, 'e', 1, 0, 5)
slab(2, 0, 52, 72, {(0, 8)}, lambda c: 's')
slab(2, 1, 52, 72, {(0, 8), (1, 8)}, lambda c: 'i')
pillar(2, 'k', 7, 7, 0, 1)
put(2, 'l', 7, 2, 7)
pillar(2, 'k', 8, 6, 0, 1)
put(2, 'l', 8, 2, 6)

# ---- level 3：外环 + 斜向台×4 ----
slab(3, -1, 71, 137, {(4, 4), (4, 5)},
     lambda c: 'r' if 130 <= c[0] * c[0] + c[1] * c[1] <= 137 else 't')
put(3, 'b', 5, 0, 5)
put(3, 'f', 4, 0, 5)
put(3, 'f', 5, 0, 4)
pillar(3, 's', 1, 8, 1, 2)
put(3, 'l', 1, 3, 8)
pillar(3, 's', 8, 1, 1, 2)
put(3, 'l', 8, 3, 1)
put(3, 'n', 4, -1, 4)
put(3, 'n', 4, -1, 5)

# ---- level 4：外苑 + 抬升台×8 + 鸟居 ----
slab(4, -1, 138, 226, set(),
     lambda c: 'r' if 214 <= c[0] * c[0] + c[1] * c[1] <= 226 else 'u')
slab(4, 0, 73, 90, {(0, 9)}, lambda c: 's')
put(4, 'g', 9, 0, 4)
put(4, 'g', 4, 0, 9)
slab(4, 1, 73, 90, {(0, 9), (1, 9), (9, 1), (7, 6), (6, 7)}, lambda c: 'r')
put(4, 'c', 7, 1, 6)
put(4, 'c', 6, 1, 7)
pillar(4, 'k', 0, 11, 0, 6)
put(4, 'p', 0, 7, 11)
put(4, 'k', 0, 7, 10)
put(4, 'k', 0, 7, 12)
put(4, 'o', 1, 8, 11)
put(4, 'o', 1, 8, 10)

# ---- level 5：最外环 + 台×8 ----
slab(5, -1, 227, 380, set(),
     lambda c: 'w' if 280 <= c[0] * c[0] + c[1] * c[1] <= 380 else 'v')
put(5, 'd', 10, 0, 8)
put(5, 'd', 12, 0, 8)
put(5, 'h', 10, 0, 7)
put(5, 'h', 7, 0, 10)
slab(5, 0, 330, 380, {(16, 9), (17, 8), (19, 2), (17, 15), (15, 17)}, lambda c: 'x')
pillar(5, 'x', 15, 5, 0, 1)
put(5, 'n', 15, 2, 5)
pillar(5, 'x', 5, 15, 0, 1)
put(5, 'n', 5, 2, 15)

for lv in levels.values():
    lv[(0, 0, 0)] = 'C'

TIER = {'e': 2, 'f': 3, 'g': 4, 'h': 5, 'i': 2, 'j': 3}

doc = {
    'id': 'gensokyou:bafang_guiyuan_circle',
    'anchorKey': 'C',
    'tiers': [2, 3, 4, 5],
    'palette': PALETTE,
}
levels_json = [{'level': i} for i in sorted(levels)]
adds_per_level = []
for i in sorted(levels):
    prev = levels[i - 1] if i - 1 in levels else {}
    adds_per_level.append([(k, x, y, z)
                           for (x, y, z), k in sorted(levels[i].items())
                           if (x, y, z) not in prev])
text = (json.dumps(doc, indent=2, ensure_ascii=False)[:-1].rstrip()
        + ',\n  "levels": ' + v5.serialize_levels_v5(levels_json, adds_per_level)
        + '\n}\n')
OUT.write_text(text, encoding='utf-8')

STONE = set(TIER) | {'i', 'j'}


def expand_count(cells):
    return sum(1 if (x == 0 and z == 0) else 4 for (x, y, z) in cells)


for i in sorted(levels):
    cells = levels[i]
    prev = set(levels[i - 1]) if i - 1 in levels else set()
    adds = [c for c in cells if c not in prev]
    by_key = {}
    for k in cells.values():
        by_key[k] = by_key.get(k, 0) + 1
    stone = sum(v for k, v in by_key.items() if k in STONE)
    cum = sum(expand_count([c]) for c in cells)
    det = ', '.join(f'{k}x{v}' for k, v in sorted(by_key.items()))
    print(f'level {i}: 累积展开 {cum} 格 / 新增 canon {len(adds)} / 石族 canon {stone}')
    print(f'    [{det}]')
print(f'写入 {OUT} ({OUT.stat().st_size} 字节)')
