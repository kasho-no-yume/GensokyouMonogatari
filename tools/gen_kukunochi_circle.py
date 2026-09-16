#!/usr/bin/env python3
"""程序化生成久久能智神庭 kukunochi_circle.json（v5 逐级增量，四重对称四分之一规范形）。

层级蓝图（坐标为四分之一规范形；轴上格一律写作 (0,y,d)）：
  L0 苔石神木祭坛 r3：y-1 圆盘(素石+苔) / y0 核心 + 8根"0"灯柱(1,2)(2,1) + 4祭品台(0,3)
      + 苔上花瓣(2,2) / y1 8盏 lantern。(1,1) 全留空 → 2阶神木躯干生长位；
      (0,1)@y0、(0,1,0)、(2,2)@y1 核心功能槽位保持缺席。
  L1 四鸟居の庭 r6：y-1 圆盘扩至 r6 / y0 4祭品台(3,3) + 深橡木灯柱(4,4) + 苔/花瓣/盆栽
      / y1 海晶灯 / y2 cherry 贯 / y4 dark_oak 笠木 / y5 海晶灯顶；四正向 r5 立 "1" 鸟居柱。
  L2 神木の大廊 r8：y-1 圆盘扩至 r8 / y0-6 (1,1) 神木躯干 + 4祭品台(5,5)
      / y7-9 cherry 树冠(中心留天光) / "2" 玉垣柱(3,7)(7,3) + 石墙(5,6)(6,5) + 海晶灯 + 旗
      + 苔庭花瓣/盆栽/蕨。
"""
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import validate_ritual_pattern as v5  # noqa: E402  复用 v5 levels 排版序列化器

OUT = (Path(__file__).resolve().parents[1] / 'src' / 'main' / 'resources'
       / 'data' / 'gensokyou' / 'rituals' / 'kukunochi_circle.json')

PALETTE = {
    'C': 'gensokyou:ritual_core',
    '0': '#gensokyou:ritual_stones',
    '1': '#gensokyou:ritual_stones_1_plus',
    '2': '#gensokyou:ritual_stones_2_plus',
    'P': '#gensokyou:ritual_pedestals',
    'a': 'minecraft:stone_bricks',
    'b': 'minecraft:mossy_cobblestone',
    'c': 'minecraft:moss_block',
    'd': 'minecraft:deepslate_tiles',
    'e': 'minecraft:deepslate_bricks',
    'f': 'minecraft:chiseled_stone_bricks',
    'g': 'minecraft:pink_petals',
    'h': 'minecraft:potted_cherry_sapling',
    'i': 'minecraft:lantern',
    'j': 'minecraft:sea_lantern',
    'k': 'minecraft:dark_oak_log',
    'l': 'minecraft:dark_oak_planks',
    'm': 'minecraft:cherry_planks',
    'n': 'minecraft:cherry_log',
    'o': 'minecraft:stone_brick_wall',
    'q': 'minecraft:purple_banner',
    'w': 'minecraft:potted_fern',
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


def floor(lv, y, table):
    for (x, z), key in table.items():
        put(lv, key, x, y, z)


def anulus(lv, y, r2min, r2max, table, override=(), skip=()):
    ov = dict(override)
    for x in range(0, 9):
        for z in range(0, 9):
            r2 = x * x + z * z
            if r2 == 0 or not r2min <= r2 <= r2max:
                continue
            c = canon(x, z)
            if c in skip:
                continue
            key = ov.get(c) or table.get(c)
            if key:
                put(lv, key, x, y, z)


def pillar(lv, key, x, z, y0, y1):
    for y in range(y0, y1 + 1):
        put(lv, key, x, y, z)


# ---- L0 苔石神木祭坛 r3 ----
F0 = {(0, 0): 'd', (0, 1): 'd', (0, 2): 'd', (0, 3): 'f',
      (1, 1): 'a', (1, 2): 'b', (2, 1): 'b', (2, 2): 'c'}
floor(0, -1, F0)
put(0, '0', 1, 0, 2)
put(0, '0', 2, 0, 1)
put(0, 'P', 0, 0, 3)
put(0, 'g', 2, 0, 2)
put(0, 'i', 1, 1, 2)
put(0, 'i', 2, 1, 1)

# ---- L1 四鸟居の庭 r6 ----
levels[1] = dict(levels[0])
F1 = {(0, 4): 'd', (0, 5): 'd', (0, 6): 'd',
      (1, 3): 'a', (1, 4): 'e', (1, 5): 'a',
      (2, 3): 'c', (2, 4): 'c', (2, 5): 'a',
      (3, 1): 'a', (3, 2): 'c', (3, 3): 'f', (3, 4): 'b', (3, 5): 'c',
      (4, 1): 'e', (4, 2): 'c', (4, 3): 'b', (4, 4): 'b',
      (5, 1): 'a', (5, 2): 'a', (5, 3): 'c'}
anulus(1, -1, 10, 36, F1)
put(1, 'P', 3, 0, 3)
pillar(1, 'k', 4, 4, 0, 0)
put(1, 'j', 4, 1, 4)
put(1, 'g', 2, 0, 3)
put(1, 'g', 3, 0, 2)
put(1, 'g', 3, 0, 5)
put(1, 'g', 5, 0, 3)
put(1, 'h', 1, 0, 3)
put(1, 'h', 3, 0, 1)
put(1, 'w', 2, 0, 4)
put(1, 'w', 4, 0, 2)
pillar(1, '1', 2, 5, 0, 3)
pillar(1, '1', 5, 2, 0, 3)
put(1, 'm', 0, 2, 5)
put(1, 'm', 1, 2, 5)
put(1, 'm', 5, 2, 1)
put(1, 'l', 0, 4, 5)
put(1, 'l', 1, 4, 5)
put(1, 'l', 2, 4, 5)
put(1, 'l', 5, 4, 1)
put(1, 'l', 5, 4, 2)
put(1, 'j', 0, 5, 5)

# ---- L2 神木の大廊 r8 ----
levels[2] = dict(levels[1])
F2 = {(0, 7): 'd', (0, 8): 'd',
      (1, 6): 'e', (1, 7): 'c',
      (2, 6): 'c', (2, 7): 'b',
      (3, 6): 'b', (3, 7): 'c',
      (4, 5): 'b', (4, 6): 'c',
      (5, 4): 'b', (5, 5): 'f', (5, 6): 'c',
      (6, 1): 'e', (6, 2): 'c', (6, 3): 'b', (6, 4): 'c', (6, 5): 'b',
      (7, 1): 'c', (7, 2): 'b', (7, 3): 'c'}
anulus(2, -1, 37, 64, F2)
pillar(2, 'k', 1, 1, 0, 6)
for x, z in ((0, 1), (1, 1), (0, 2), (1, 2), (2, 1), (2, 2)):
    put(2, 'n', x, 7, z)
for x, z in ((0, 1), (1, 1), (0, 2)):
    put(2, 'n', x, 8, z)
put(2, 'n', 0, 9, 1)
put(2, 'P', 5, 0, 5)
pillar(2, '2', 3, 7, 0, 1)
pillar(2, '2', 7, 3, 0, 1)
put(2, 'j', 3, 2, 7)
put(2, 'j', 7, 2, 3)
pillar(2, 'o', 5, 6, 0, 1)
pillar(2, 'o', 6, 5, 0, 1)
put(2, 'j', 5, 2, 6)
put(2, 'j', 6, 2, 5)
put(2, 'q', 4, 2, 4)
put(2, 'g', 1, 0, 7)
put(2, 'g', 2, 0, 6)
put(2, 'g', 6, 0, 2)
put(2, 'g', 7, 0, 1)
put(2, 'h', 3, 0, 6)
put(2, 'h', 6, 0, 3)
put(2, 'w', 4, 0, 6)
put(2, 'w', 6, 0, 4)

for lv in levels:
    lv[(0, 0, 0)] = 'C'

doc = {
    'id': 'gensokyou:kukunochi_circle',
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
