#!/usr/bin/env python3
"""程序化生成埴山姬神之壤 haniyasu_circle.json（v5 逐级增量，四重对称四分之一规范形）。

层级蓝图（坐标为四分之一规范形；轴上格一律写作 (0,y,d)）：
  土丘逐阶向下加宽台地，核心立于丘顶 (0,0,0)；每级台地 1 格高，可直接步行而上。
  L0 土の壜：y-3 基底 r²≤13 / y-2 中台 r²≤9 / y-1 顶台 r²≤5
      / y0 核心 + 4祭品台(0,2) + 8根"0"埴柱(1,2)(2,1) / y1 8盏灯笼。
      (0,1)@y0、(0,1,0)、(2,2)@y1 核心功能槽位保持缺席。
  L1 段丘の社：y-4 基底 r²≤25（凝灰岩裙+泥砖垣）/ y-3 砂砾步道/盆栽
      / y-2..0 8座"1"高埴柱 + 陶埴轮顶 + 灯笼 / y-1 4祭品台(0,3)。
  L2 埴山の頂：y-5 基底 r²≤41（石砖/凝灰岩/方钙石）/ y-4 泥砖垣 + 四正向 r5 四門
      （8根"2"柱 + 夯土笠木 + 陶顶）/ y-4 4祭品台(4,4) + 灯笼。
"""
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import validate_ritual_pattern as v5  # noqa: E402  复用 v5 levels 排版序列化器

OUT = (Path(__file__).resolve().parents[1] / 'src' / 'main' / 'resources'
       / 'data' / 'gensokyou' / 'rituals' / 'haniyasu_circle.json')

PALETTE = {
    'C': 'gensokyou:ritual_core',
    '0': '#gensokyou:ritual_stones',
    '1': '#gensokyou:ritual_stones_1_plus',
    '2': '#gensokyou:ritual_stones_2_plus',
    'P': '#gensokyou:ritual_pedestals',
    'a': 'minecraft:packed_mud',
    'b': 'minecraft:mud_bricks',
    'c': 'minecraft:mud_brick_wall',
    'd': 'minecraft:clay',
    'e': 'minecraft:coarse_dirt',
    'f': 'minecraft:rooted_dirt',
    'h': 'minecraft:mud',
    'i': 'minecraft:terracotta',
    'k': 'minecraft:stone_bricks',
    'l': 'minecraft:tuff',
    'm': 'minecraft:calcite',
    'n': 'minecraft:dripstone_block',
    'o': 'minecraft:gravel',
    'p': 'minecraft:sand',
    's': 'minecraft:lantern',
    't': 'minecraft:potted_dead_bush',
    'u': 'minecraft:potted_fern',
}

levels = [{}, {}, {}]


def canon(x, z):
    x, z = abs(x), abs(z)
    return (0, x) if z == 0 else (x, z)


def put(lv, key, x, y, z):
    cx, cz = canon(x, z)
    if cx == 0 and cz == 0 and y == 0:
        raise SystemExit(f'锚点格 (0,0,0) 保留给 C: level {lv} key={key}')
    cell = (cx, y, cz)
    old = levels[lv].get(cell)
    if old and old != key:
        raise SystemExit(f'冲突 level {lv} {cell}: {old} vs {key}')
    levels[lv][cell] = key


def disc(lv, y, table):
    for (x, z), key in table.items():
        put(lv, key, x, y, z)


# ---- L0 土の壜 (r²≤13) ----
D03 = {(0, 0): 'e', (0, 1): 'e', (1, 1): 'f', (0, 2): 'e', (1, 2): 'e', (2, 1): 'd', (2, 2): 'e',
       (0, 3): 'e', (1, 3): 'b', (3, 1): 'b', (2, 3): 'b', (3, 2): 'b'}
D02 = {(0, 0): 'e', (0, 1): 'h', (1, 1): 'e', (0, 2): 'e', (1, 2): 'd',
       (2, 1): 'e', (2, 2): 'b', (0, 3): 'b'}
D01 = {(0, 0): 'a', (0, 1): 'd', (1, 1): 'a', (0, 2): 'b', (1, 2): 'b', (2, 1): 'b'}
disc(0, -3, D03)
disc(0, -2, D02)
disc(0, -1, D01)
put(0, 'P', 0, 0, 2)
put(0, '0', 1, 0, 2)
put(0, '0', 2, 0, 1)
put(0, 's', 1, 1, 2)
put(0, 's', 2, 1, 1)

# ---- L1 段丘の社 (r²≤25) ----
levels[1] = dict(levels[0])
D14 = {(0, 0): 'e', (0, 1): 'e', (1, 1): 'f', (0, 2): 'e', (1, 2): 'e', (2, 1): 'd', (2, 2): 'e',
       (0, 3): 'e', (1, 3): 'f', (3, 1): 'd', (2, 3): 'l', (3, 2): 'l',
       (3, 3): 'n', (0, 4): 'd', (1, 4): 'l', (4, 1): 'l', (2, 4): 'l', (4, 2): 'l',
       (3, 4): 'k', (4, 3): 'k', (0, 5): 'e'}
disc(1, -4, D14)
for x, z in ((2, 4), (4, 2), (3, 3)):
    put(1, 'c', x, -3, z)
put(1, 'o', 0, -3, 4)
put(1, 'p', 1, -3, 4)
put(1, 'p', 4, -3, 1)
put(1, 't', 3, -3, 4)
put(1, 't', 4, -3, 3)
for y in (-2, -1):
    put(1, '1', 1, y, 3)
    put(1, '1', 3, y, 1)
put(1, 'i', 1, 0, 3)
put(1, 'i', 3, 0, 1)
put(1, 's', 1, 1, 3)
put(1, 's', 3, 1, 1)
put(1, 'P', 0, -1, 3)
put(1, 'm', 2, -2, 3)
put(1, 'm', 3, -2, 2)
put(1, 'd', 2, -1, 2)

# ---- L2 埴山の頂 (r²≤41) ----
levels[2] = dict(levels[1])
D25 = {(0, 0): 'f', (0, 1): 'e', (1, 1): 'e', (0, 2): 'e', (1, 2): 'f', (2, 1): 'e', (2, 2): 'e',
       (0, 3): 'e', (1, 3): 'f', (3, 1): 'e', (2, 3): 'l', (3, 2): 'l', (3, 3): 'l',
       (0, 4): 'e', (1, 4): 'l', (4, 1): 'l', (2, 4): 'l', (4, 2): 'l',
       (3, 4): 'k', (4, 3): 'k', (0, 5): 'e',
       (1, 5): 'l', (5, 1): 'l', (2, 5): 'k', (5, 2): 'k', (4, 4): 'n',
       (3, 5): 'k', (5, 3): 'k', (0, 6): 'm', (1, 6): 'l', (6, 1): 'l',
       (2, 6): 'n', (6, 2): 'n', (4, 5): 'k', (5, 4): 'k'}
disc(2, -5, D25)
for x, z in ((1, 6), (6, 1), (2, 6), (6, 2), (4, 5), (5, 4)):
    put(2, 'c', x, -4, z)
put(2, 'o', 1, -4, 5)
put(2, 'o', 5, -4, 1)
put(2, 'p', 0, -4, 6)
put(2, 'P', 4, -4, 4)
put(2, 's', 3, -4, 5)
put(2, 's', 5, -4, 3)
for y in (-4, -3, -2, -1):
    put(2, '2', 2, y, 5)
    put(2, '2', 5, y, 2)
put(2, 'a', 0, 0, 5)
put(2, 'a', 1, 0, 5)
put(2, 'a', 2, 0, 5)
put(2, 'a', 5, 0, 1)
put(2, 'a', 5, 0, 2)
put(2, 'i', 0, 1, 5)
put(2, 'u', 4, -3, 5)
put(2, 'u', 5, -3, 4)

for lv in levels:
    lv[(0, 0, 0)] = 'C'

doc = {
    'id': 'gensokyou:haniyasu_circle',
    'anchorKey': 'C',
    'toggleable': False,
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

STONES = {'0', '1', '2'}
for i, cells in enumerate(levels):
    by_key = {}
    for k in cells.values():
        by_key[k] = by_key.get(k, 0) + 1
    detail = ', '.join(f'{k}x{v}' for k, v in sorted(by_key.items()))
    ns = sum(v for k, v in by_key.items() if k in STONES)
    print(f'level {i}: 累积 {len(cells)} 格（新增 {len(adds_per_level[i])} 条目）'
          f' 石族 {ns}/{len(cells)}={ns / len(cells):.1%} [{detail}]')
print(f'写入 {OUT} ({OUT.stat().st_size} 字节)')
