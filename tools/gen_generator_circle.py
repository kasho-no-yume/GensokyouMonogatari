#!/usr/bin/env python3
"""程序化生成忠实版 generator_circle.json（五级累积，四重对称四分之一规范形）。

层级蓝图（外半径以 r² 计，坐标为四分之一规范形；轴上格一律写作 (0,y,d)）：
  L1 y0  末地石底盘 r²≤152 + "1"内嵌环(8..13)；四向港口 orbit(0,1) 留空
  L2 y1  末地石台面 r²≤120（核心顶列(0,0,0)/电容槽(2,2)/鸟居脚(1,10)(10,1) 留空）
     y2  紫珀平台 r²≤45（轴上刻槽(0,1)(0,2)）+ "2"环(46..53) + 紫/玻璃导流槽(54..85)
         E柱(0,2) y2-3 + A帽 y4；J柱(0,6)(4,4) y3-4 + A帽 y5；P台(3,3)
  L3 y3  紫珀内台 r²≤12（去(0,1)(0,2)）+ "3"环(13..21，去P(3,3))；A镶嵌(2,2) y4
         Q台(5,3)(3,5)；W八方位柱(0,9)(7,6) y2-3
  L4 y3  "4"回廊(58..85，去W柱)；y2 K深橡木外道(86..116，去鸟居脚)
         角楼 J(7,7) y3-6 + K顶 y7 + L y8；R台(6,6)y4；B旗(8,3)y4；L灯(0,9)y4
  L5 y3  "5"环(86..116，去鸟居/樱花/角楼/花瓣位)；y4 栏杆H(106..116)
         巨鸟居 O柱(1,10)(10,1) y1-7 + 貫H(0,10)y5 + 横木O y8 + B旗y6
         樱花柱 T(9,4)(4,9) y3-5；灯柱 J(8,8) y1-3 + A y4 + L y5；花瓣Y(5,8)(8,5)
"""
import json
from pathlib import Path

OUT = (Path(__file__).resolve().parents[1] / 'src' / 'main' / 'resources'
       / 'data' / 'gensokyou' / 'rituals' / 'generator_circle.json')

PALETTE = {
    'C': 'gensokyou:ritual_core',
    '1': '#gensokyou:ritual_stones_1_plus',
    '2': '#gensokyou:ritual_stones_2_plus',
    '3': '#gensokyou:ritual_stones_3_plus',
    '4': '#gensokyou:ritual_stones_4_plus',
    '5': '#gensokyou:ritual_stones_5_plus',
    'P': '#gensokyou:ritual_pedestals_2_plus',
    'Q': '#gensokyou:ritual_pedestals_3_plus',
    'R': '#gensokyou:ritual_pedestals_4_plus',
    'E': 'gensokyou:ritual_stone_wall_2',
    'W': 'gensokyou:ritual_stone_wall_3',
    'U': 'minecraft:purpur_block',
    'X': 'minecraft:end_stone',
    'D': 'minecraft:purple_stained_glass',
    'J': 'minecraft:basalt',
    'K': 'minecraft:dark_oak_planks',
    'O': 'minecraft:dark_oak_log',
    'H': 'minecraft:dark_oak_fence',
    'A': 'minecraft:amethyst_block',
    'B': 'minecraft:purple_banner',
    'L': 'minecraft:soul_lantern',
    'T': 'minecraft:cherry_log',
    'Y': 'minecraft:pink_petals',
}

levels = [{}, {}, {}, {}, {}]


def canon(x, z):
    x, z = abs(x), abs(z)
    return (0, x) if z == 0 else (x, z)


def put(lv, key, x, y, z):
    cx, cz = canon(x, z)
    if cx == 0 and cz == 0:
        raise SystemExit(f'原点保留给锚点 C: level {lv + 1} key={key}')
    cell = (cx, y, cz)
    old = levels[lv].get(cell)
    if old and old != key:
        raise SystemExit(f'冲突 level {lv + 1} {cell}: {old} vs {key}')
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


slab(0, 0, 1, 152, {(0, 1)},
     lambda c: '1' if 8 <= c[0] * c[0] + c[1] * c[1] <= 13 else 'X')

levels[1] = dict(levels[0])
slab(1, 1, 1, 120, {(0, 1), (2, 2), (1, 10), (10, 1)}, lambda c: 'X')
slab(1, 2, 1, 45, {(0, 1), (0, 2)}, lambda c: 'U')
slab(1, 2, 46, 53, set(), lambda c: '2')
slab(1, 2, 54, 85, {(0, 9), (7, 6)},
     lambda c: 'D' if (c[0] + c[1]) % 2 else 'U')
pillar(1, 'E', 0, 2, 2, 3)
put(1, 'A', 0, 4, 2)
pillar(1, 'J', 0, 6, 3, 4)
put(1, 'A', 0, 5, 6)
pillar(1, 'J', 4, 4, 3, 4)
put(1, 'A', 4, 5, 4)
put(1, 'P', 3, 3, 3)

levels[2] = dict(levels[1])
slab(2, 3, 1, 12, {(0, 1), (0, 2)}, lambda c: 'U')
slab(2, 3, 13, 21, {(3, 3)}, lambda c: '3')
put(2, 'A', 2, 4, 2)
put(2, 'Q', 5, 3, 3)
put(2, 'Q', 3, 3, 5)
pillar(2, 'W', 0, 9, 2, 3)
pillar(2, 'W', 7, 6, 2, 3)

levels[3] = dict(levels[2])
slab(3, 3, 58, 85, {(0, 9), (7, 6)}, lambda c: '4')
slab(3, 2, 86, 116, {(1, 10), (10, 1)}, lambda c: 'K')
pillar(3, 'J', 7, 7, 3, 6)
put(3, 'K', 7, 7, 7)
put(3, 'L', 7, 7, 8)
put(3, 'R', 6, 4, 6)
put(3, 'B', 8, 4, 3)
put(3, 'L', 0, 4, 9)

levels[4] = dict(levels[3])
pillar(4, 'O', 1, 10, 1, 7)
pillar(4, 'O', 10, 1, 1, 7)
put(4, 'H', 0, 5, 10)
put(4, 'B', 0, 6, 10)
put(4, 'O', 0, 8, 10)
put(4, 'O', 1, 8, 10)
put(4, 'O', 10, 8, 1)
slab(4, 3, 86, 116,
     {(1, 10), (10, 1), (9, 4), (4, 9), (7, 7), (5, 8), (8, 5)},
     lambda c: '5')
slab(4, 4, 106, 116, set(), lambda c: 'H')
put(4, 'Y', 5, 3, 8)
put(4, 'Y', 8, 3, 5)
pillar(4, 'T', 9, 4, 3, 5)
pillar(4, 'T', 4, 9, 3, 5)
pillar(4, 'J', 8, 8, 1, 3)
put(4, 'A', 8, 4, 8)
put(4, 'L', 8, 5, 8)

for lv in levels:
    lv[(0, 0, 0)] = 'C'

doc = {
    'id': 'gensokyou:generator_circle',
    'anchorKey': 'C',
    'toggleable': True,
    'palette': PALETTE,
    'levels': [
        {'level': i + 1,
         'blocks': [{'key': k, 'x': x, 'y': y, 'z': z}
                    for (x, y, z), k in sorted(levels[i].items())]}
        for i in range(5)
    ],
}
OUT.write_text(json.dumps(doc, indent=2, ensure_ascii=False) + '\n',
               encoding='utf-8')
for i, cells in enumerate(levels):
    by_key = {}
    for k in cells.values():
        by_key[k] = by_key.get(k, 0) + 1
    detail = ', '.join(f'{k}x{v}' for k, v in sorted(by_key.items()))
    print(f'level {i + 1}: {len(cells)} 格 [{detail}]')
print(f'写入 {OUT} ({OUT.stat().st_size} 字节)')
