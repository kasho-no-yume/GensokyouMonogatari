#!/usr/bin/env python3
"""生成 shujou_yoroku_circle.json（众生余录，0~2 阶，v5 逐级增量，四重对称四分之一规范形）。

蓝图（坐标为四分之一规范形；轴上格一律写作 y,d）：
  L0 y-1 石庭圆盘 r²≤25 同心带：
        A 纸心(1,2) / D 刻纹(4,5) / 0 灵纹(8,9,10) / B 石砖(13,16,17,18) / A 外带(20,25)
     y0  录台P(0,3)×4；灯柱 J(3,3)+L(3,3)y1；藏卷架 K(2,2)；垣 W(r²25)
  L1 y-1 外扩 r²26..64：B(26,29,32) / 1 灵纹(34,36,37) / H 深板岩砖(40,41,45,49,50) / A(52,53,58,61,64)
     y0  录台P(0,6)×4；八柱 J(0,7)(5,5)+Q y1-3；藏卷柜 K(4,5)(5,4) y0-1
     y1  槛 F(3,6)(6,3)（H 座 y0）
     y4  檐环 M(0,7) / U(1,7)(7,1)(5,5)
  L2 y-1 外扩 r²65..121：B(65..80) / 2 灵纹(81,82,85) / N 末地砖(89..109) / R 紫珀(113..121)
     y0  录台P(0,9)×4；外角藏卷塔 V(6,6) y0-2 + K y3-4 + V y5-6 + X y7 + Z y8
     y5  二层栏 S(0,7) / F(1,7)(7,1)(5,5)；y6 檐梁 U(r²49,50)
     y7  冠链 G(0,7)；y8 魂灯 Z(0,7)；对角塔顶 Z(5,5) y7
"""
import json
import sys
from collections import deque
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import validate_ritual_pattern as v5  # noqa: E402  复用 v5 levels 排版序列化器

OUT = (Path(__file__).resolve().parents[1] / 'src' / 'main' / 'resources'
       / 'data' / 'gensokyou' / 'rituals' / 'shujou_yoroku_circle.json')

PALETTE = {
    'C': 'gensokyou:ritual_core',
    '0': '#gensokyou:ritual_stones',
    '1': '#gensokyou:ritual_stones_1_plus',
    '2': '#gensokyou:ritual_stones_2_plus',
    'P': '#gensokyou:ritual_pedestals',
    # 0 阶主世界素材
    'A': 'minecraft:polished_andesite',
    'B': 'minecraft:stone_bricks',
    'D': 'minecraft:chiseled_stone_bricks',
    'J': 'minecraft:dark_oak_log',
    'K': 'minecraft:bookshelf',
    'L': 'minecraft:lantern',
    'W': 'minecraft:stone_brick_wall',
    # 1 阶
    'H': 'minecraft:deepslate_tiles',
    'Q': 'minecraft:quartz_pillar',
    'U': 'minecraft:quartz_block',
    'M': 'minecraft:sea_lantern',
    'F': 'minecraft:iron_bars',
    'S': 'minecraft:purple_stained_glass',
    # 2 阶
    'N': 'minecraft:end_stone_bricks',
    'R': 'minecraft:purpur_block',
    'V': 'minecraft:purpur_pillar',
    'X': 'minecraft:amethyst_block',
    'Z': 'minecraft:soul_lantern',
    'G': 'minecraft:chain',
}

levels = [{}, {}, {}]


def canon(x, z):
    x, z = abs(x), abs(z)
    return (0, x) if z == 0 else (x, z)


def put(lv, key, x, y, z):
    cx, cz = canon(x, z)
    if cx == 0 and cz == 0:
        raise SystemExit(f'原点列保留给锚点 C: level {lv} key={key}')
    cell = (cx, y, cz)
    old = levels[lv].get(cell)
    if old and old != key:
        raise SystemExit(f'冲突 level {lv} {cell}: {old} vs {key}')
    levels[lv][cell] = key


def slab(lv, y, r2min, r2max, skip, maker):
    for x in range(0, 16):
        for z in range(0, 16):
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


def band(rule):
    def maker(c):
        r2 = c[0] * c[0] + c[1] * c[1]
        for lo, hi, key in rule:
            if lo <= r2 <= hi:
                return key
        return None
    return maker


# ---- L0 一录·余庭 ----
slab(0, -1, 1, 25, set(), band([
    (8, 10, '0'), (4, 5, 'D'), (1, 2, 'A'),
    (13, 18, 'B'), (20, 25, 'A'),
]))
put(0, 'P', 3, 0, 0)
put(0, 'J', 3, 0, 3)
put(0, 'L', 3, 1, 3)
put(0, 'K', 2, 0, 2)
slab(0, 0, 25, 25, set(), lambda c: 'W')

# ---- L1 二录·回廊 ----
levels[1] = dict(levels[0])
slab(1, -1, 26, 64, set(), band([
    (34, 37, '1'), (26, 32, 'B'), (40, 50, 'H'), (52, 64, 'A'),
]))
put(1, 'P', 6, 0, 0)
put(1, 'J', 7, 0, 0)
pillar(1, 'Q', 7, 0, 1, 3)
put(1, 'J', 5, 0, 5)
pillar(1, 'Q', 5, 5, 1, 3)
put(1, 'M', 7, 4, 0)
put(1, 'U', 1, 4, 7)
put(1, 'U', 7, 4, 1)
put(1, 'U', 5, 4, 5)
pillar(1, 'K', 5, 4, 0, 1)
pillar(1, 'K', 4, 5, 0, 1)
put(1, 'H', 6, 0, 3)
put(1, 'F', 6, 1, 3)
put(1, 'H', 3, 0, 6)
put(1, 'F', 3, 1, 6)

# ---- L2 三录·高阁 ----
levels[2] = dict(levels[1])
slab(2, -1, 65, 121, set(), band([
    (81, 85, '2'), (65, 80, 'B'), (89, 109, 'N'), (113, 121, 'R'),
]))
put(2, 'P', 9, 0, 0)
pillar(2, 'V', 6, 6, 0, 2)
pillar(2, 'K', 6, 6, 3, 4)
pillar(2, 'V', 6, 6, 5, 6)
put(2, 'X', 6, 7, 6)
put(2, 'Z', 6, 8, 6)
put(2, 'S', 7, 5, 0)
put(2, 'F', 1, 5, 7)
put(2, 'F', 7, 5, 1)
put(2, 'F', 5, 5, 5)
put(2, 'U', 7, 6, 0)
put(2, 'U', 1, 6, 7)
put(2, 'U', 7, 6, 1)
put(2, 'U', 5, 6, 5)
put(2, 'G', 7, 7, 0)
put(2, 'Z', 7, 8, 0)
put(2, 'Z', 5, 7, 5)

for lv in levels:
    lv[(0, 0, 0)] = 'C'

def expand(cells):
    out = set()
    for (x, y, z) in cells:
        if x == 0 and z == 0:
            out.add((0, y, 0))
        elif x == 0:
            out.update({(z, y, 0), (-z, y, 0), (0, y, z), (0, y, -z)})
        else:
            out.update({(x, y, z), (x, y, -z), (-x, y, z), (-x, y, -z)})
    return out


# 自检：BFS 连通（核心列除外）+ 立柱下方有地面
placed = set().union(*[set(d) for d in levels])
real = expand(placed)
noncore = {c for c in real if c != (0, 0, 0)}
start = next(c for c in noncore if c[1] == -1)
seen = {start}
q = deque([start])
while q:
    x, y, z = q.popleft()
    for n in ((x + 1, y, z), (x - 1, y, z), (x, y + 1, z),
              (x, y - 1, z), (x, y, z + 1), (x, y, z - 1)):
        if n in noncore and n not in seen:
            seen.add(n)
            q.append(n)
if seen != noncore:
    print(f'[警告] 存在 {len(noncore - seen)} 个悬空格: {sorted(noncore - seen)[:8]}')
else:
    print('[自检] BFS 连通: 全部非核心格连通')

ground = {(x, z) for (x, y, z) in real if y == -1}
floaters = sorted({(x, z) for (x, y, z) in noncore if y > -1 and (x, z) not in ground})
print(f'[自检] 无地面承托的立柱: {floaters if floaters else "无"}')

doc = {
    'id': 'gensokyou:shujou_yoroku_circle',
    'anchorKey': 'C',
    'toggleable': True,
    'tiers': [0, 1, 2],
    'palette': PALETTE,
}
levels_json = [{'level': i} for i in range(3)]
adds_per_level = []
for i in range(3):
    prev = levels[i - 1] if i else {}
    adds_per_level.append([(k, x, y, z)
                           for (x, y, z), k in sorted(levels[i].items())
                           if (x, y, z) not in prev])
text = (json.dumps(doc, indent=2, ensure_ascii=False)[:-1].rstrip()
        + ',\n  "levels": ' + v5.serialize_levels_v5(levels_json, adds_per_level)
        + '\n}\n')
OUT.write_text(text, encoding='utf-8')

for i, cells in enumerate(levels):
    by_key = {}
    for k in cells.values():
        by_key[k] = by_key.get(k, 0) + 1
    stone = sum(n for k, n in by_key.items() if k in ('0', '1', '2'))
    detail = ', '.join(f'{k}x{n}' for k, n in sorted(by_key.items()))
    pct = 100 * stone / len(cells)
    print(f'level {i}: 累积展开 {4 * (len(cells) - 1) + 1} 格（规范 {len(cells)}）'
          f' 石族 {stone} ({pct:.1f}%) [{detail}]')
print(f'写入 {OUT} ({OUT.stat().st_size} 字节)')
