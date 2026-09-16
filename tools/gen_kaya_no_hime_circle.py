#!/usr/bin/env python3
"""程序化生成草野姬神花亭 kaya_no_hime_circle.json（v5 逐级增量，四重对称四分之一规范形）。

层级蓝图（坐标为四分之一规范形；轴上格一律写作 (0,y,d)）：
  核心＝花亭：橡木柱撑苔藓花顶，顶心留天窗 (0,y,0) 光柱直通核心。
  ⚠ 地基只用不会变质的土壤：moss_block(绿地/屋顶) / rooted_dirt(承托实心方块处) / coarse_dirt(小径)。
    禁用 grass_block（上覆实心块会退化为泥土）与纯 dirt（会被邻近草蔓延）。
  L0 花の亭：y-1 草庭 r²≤9 / y0 核心 + 4祭品台(0,3) + 4橡木柱(1,2)(2,1) + "0"石标(2,2)
      / y1-2 橡木柱 / y3 苔藓顶(0,1)(1,1)(0,2)(1,2)(2,1)，中心(0,0)镂空 / y4 顶面花。
      (0,1)@y0、(0,1,0)、(2,2)@y1 核心功能槽位保持缺席。
  L1 花の庭：y-1 庭院 r²≤25 同心花环 / y0 8根"1"花柱(2,3)(3,2) + 4祭品台(3,3)
      / y2 柱顶苔藓 / y3 灯笼。
  L2 花亭の大庭：y-1 庭院 r²≤41 / y0 8根"2"花塔(3,5)(5,3) + 4祭品台(4,4) + 橡木凳
      / y3 塔顶苔藓 / y4 塔顶绒球葱 + 亭上二层柱(1,1) / y5 二层柱 / y6 二层苔藓顶
      / y7 二层顶面花。
"""
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import validate_ritual_pattern as v5  # noqa: E402  复用 v5 levels 排版序列化器

OUT = (Path(__file__).resolve().parents[1] / 'src' / 'main' / 'resources'
       / 'data' / 'gensokyou' / 'rituals' / 'kaya_no_hime_circle.json')

PALETTE = {
    'C': 'gensokyou:ritual_core',
    '0': '#gensokyou:ritual_stones',
    '1': '#gensokyou:ritual_stones_1_plus',
    '2': '#gensokyou:ritual_stones_2_plus',
    'P': '#gensokyou:ritual_pedestals',
    'a': 'minecraft:moss_block',
    'b': 'minecraft:rooted_dirt',
    'd': 'minecraft:poppy',
    'e': 'minecraft:cornflower',
    'f': 'minecraft:short_grass',
    'g': 'minecraft:pink_petals',
    'h': 'minecraft:dandelion',
    'i': 'minecraft:coarse_dirt',
    'j': 'minecraft:oak_log',
    'k': 'minecraft:oak_planks',
    'o': 'minecraft:allium',
    'p': 'minecraft:azure_bluet',
    'q': 'minecraft:oxeye_daisy',
    'r': 'minecraft:lily_of_the_valley',
    's': 'minecraft:lantern',
    'x': 'minecraft:red_tulip',
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


def ground(lv, y, table):
    for (x, z), key in table.items():
        put(lv, key, x, y, z)


# ---- L0 花の亭 (r²≤9) ----
G09 = {(0, 0): 'b', (0, 1): 'i', (1, 1): 'a', (0, 2): 'i',
       (1, 2): 'b', (2, 1): 'b', (2, 2): 'b', (0, 3): 'i'}
ground(0, -1, G09)
put(0, 'P', 0, 0, 3)
put(0, 'j', 1, 0, 2)
put(0, 'j', 2, 0, 1)
put(0, '0', 2, 0, 2)
put(0, 'd', 1, 0, 1)
put(0, 'f', 0, 0, 2)
put(0, 'j', 1, 1, 2)
put(0, 'j', 2, 1, 1)
put(0, 'j', 1, 2, 2)
put(0, 'j', 2, 2, 1)
for x, z in ((0, 1), (1, 1), (0, 2), (1, 2), (2, 1)):
    put(0, 'a', x, 3, z)
put(0, 'd', 0, 4, 1)
put(0, 'e', 0, 4, 2)

# ---- L1 花の庭 (r²≤25) ----
levels[1] = dict(levels[0])
G25 = {(1, 3): 'a', (3, 1): 'a', (2, 3): 'b', (3, 2): 'b', (3, 3): 'b',
       (0, 4): 'i', (1, 4): 'a', (4, 1): 'a', (2, 4): 'a', (4, 2): 'a',
       (3, 4): 'a', (4, 3): 'a', (0, 5): 'i'}
ground(1, -1, G25)
put(1, 'P', 3, 0, 3)
put(1, '1', 2, 0, 3)
put(1, '1', 3, 0, 2)
put(1, 'e', 1, 0, 3)
put(1, 'e', 3, 0, 1)
put(1, 'p', 1, 0, 4)
put(1, 'p', 4, 0, 1)
put(1, 'q', 2, 0, 4)
put(1, 'q', 4, 0, 2)
put(1, 'o', 3, 0, 4)
put(1, 'o', 4, 0, 3)
put(1, '1', 2, 1, 3)
put(1, '1', 3, 1, 2)
put(1, 'a', 2, 2, 3)
put(1, 'a', 3, 2, 2)
put(1, 's', 2, 3, 3)
put(1, 's', 3, 3, 2)

# ---- L2 花亭の大庭 (r²≤41) ----
levels[2] = dict(levels[1])
G41 = {(1, 5): 'a', (5, 1): 'a', (2, 5): 'a', (5, 2): 'a', (3, 5): 'b', (5, 3): 'b',
       (4, 4): 'b', (0, 6): 'i', (1, 6): 'a', (6, 1): 'a', (2, 6): 'a', (6, 2): 'a',
       (4, 5): 'a', (5, 4): 'a'}
ground(2, -1, G41)
put(2, 'P', 4, 0, 4)
for y in (0, 1, 2):
    put(2, '2', 3, y, 5)
    put(2, '2', 5, y, 3)
put(2, 'h', 1, 0, 5)
put(2, 'h', 5, 0, 1)
put(2, 'q', 2, 0, 5)
put(2, 'q', 5, 0, 2)
put(2, 'r', 2, 0, 6)
put(2, 'r', 6, 0, 2)
put(2, 'x', 4, 0, 5)
put(2, 'x', 5, 0, 4)
put(2, 'k', 1, 0, 6)
put(2, 'k', 6, 0, 1)
put(2, 'a', 3, 3, 5)
put(2, 'a', 5, 3, 3)
put(2, 'o', 3, 4, 5)
put(2, 'o', 5, 4, 3)
put(2, 'j', 1, 4, 1)
put(2, 'j', 1, 5, 1)
put(2, 'a', 0, 6, 1)
put(2, 'a', 1, 6, 1)
put(2, 'd', 0, 7, 1)
put(2, 'e', 1, 7, 1)

for lv in levels:
    lv[(0, 0, 0)] = 'C'

doc = {
    'id': 'gensokyou:kaya_no_hime_circle',
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
