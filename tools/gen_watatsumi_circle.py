#!/usr/bin/env python3
"""绵津见神之藏 watatsumi_circle：海滨神社（0~2 阶，v5 逐级增量，四重对称四分之一规范形）。

逐层增量蓝图（坐标一律 canon 后的 (|x|,|z|)+y；核心固定写 (0,0,0)）：

阶0 磯の磐座（累积展开 ~70 格，半径 4）
  y-1 潮间岩台（八角 r^2<=16，49 格）：砾石/粗土/凝灰岩外缘，苔石+海晶砖潮线，
      蓝冰"潮池"与海晶灯水洼，苔藓/压实泥内圈；仪式石 0 环 (0,2)(2,2)。
  y0  核心 C；四向祭品台 P(0,3)×4；八座小石灯籠 (2,1)(1,2) 石墙+灯笼。

阶1 社の前庭（累积展开 ~238 格，半径 6）
  y-1 外环铺石 17<=r^2<=37：石砖/安山岩/凝灰岩，蓝冰池与海晶灯；外圈仪式石 1 收边。
  y0  四座木鸟居：柱 (1,6)(6,1) y0-4，貫 y3，額束 y4，笠木 y5（铜瓦），檐灯 y4；
      四座大石灯籠 (3,3) y0-2；祭品台 P(0,5)×4；盆栽蕨 (5,1)(1,5)。

阶2 神庫の円堂（累积展开 ~628 格，半径 8）
  y-1 再扩庭 38<=r^2<=64：深板岩瓦/石英/抛光深板岩铺面，仪式石 2 外圈，参道木板。
  y0  八柱圆堂：柱 (2,4)(4,2) y0-6（铜础 + 深橡木），y6 环梁（铜 + 海晶灯）；
      外圈石灯籠 (6,4)(4,6) y0-2；祭品台 P(0,4)×4；盆栽 (7,1)(1,7)。
  y7-9 逐层收小的宝形屋面（石英/深板岩瓦），正中垂链吊海晶"神灯"，顶部金相轮。

留空（不写进 pattern，归程序侧）：核心四向 y0、核心正上方 y1、电容槽 (2,2)@y1。
"""
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import validate_ritual_pattern as v5  # noqa: E402  复用 v5 levels 序列化器

OUT = (Path(__file__).resolve().parents[1] / 'src' / 'main' / 'resources'
       / 'data' / 'gensokyou' / 'rituals' / 'watatsumi_circle.json')

PALETTE = {
    'C': 'gensokyou:ritual_core',
    '0': '#gensokyou:ritual_stones',
    '1': '#gensokyou:ritual_stones_1_plus',
    '2': '#gensokyou:ritual_stones_2_plus',
    'P': '#gensokyou:ritual_pedestals',
    'a': 'minecraft:mossy_cobblestone',
    'b': 'minecraft:cobblestone',
    'd': 'minecraft:tuff',
    'e': 'minecraft:gravel',
    'f': 'minecraft:prismarine_bricks',
    'g': 'minecraft:dark_prismarine',
    'h': 'minecraft:sea_lantern',
    'i': 'minecraft:moss_block',
    'j': 'minecraft:coarse_dirt',
    'k': 'minecraft:packed_mud',
    'm': 'minecraft:lantern',
    'n': 'minecraft:potted_fern',
    'p': 'minecraft:stone_brick_wall',
    'q': 'minecraft:blue_ice',
    's': 'minecraft:stone_bricks',
    't': 'minecraft:polished_andesite',
    'u': 'minecraft:dark_oak_log',
    'v': 'minecraft:dark_oak_planks',
    'w': 'minecraft:chiseled_stone_bricks',
    'x': 'minecraft:waxed_oxidized_copper',
    'z': 'minecraft:chain',
    'D': 'minecraft:polished_deepslate',
    'E': 'minecraft:deepslate_tiles',
    'F': 'minecraft:quartz_block',
    'G': 'minecraft:quartz_pillar',
    'H': 'minecraft:waxed_copper_block',
    'I': 'minecraft:gold_block',
}

levels = [{}, {}, {}]


def canon(x, z):
    x, z = abs(x), abs(z)
    return (0, x) if z == 0 else (x, z)


def put(lv, key, x, y, z):
    cx, cz = canon(x, z)
    if cx == 0 and cz == 0:
        # 中心轴 (0,0,y) 允许放块；核心必须恰在 (0,0,0) 且只写一次
        if key == 'C' and y != 0:
            raise SystemExit(f'核心必须在 (0,0,0): level {lv} y={y}')
        cell = (0, y, 0)
        old = levels[lv].get(cell)
        if old and old != key:
            raise SystemExit(f'冲突 level {lv} {cell}: {old} vs {key}')
        levels[lv][cell] = key
        return
    cell = (cx, y, cz)
    old = levels[lv].get(cell)
    if old and old != key:
        raise SystemExit(f'冲突 level {lv} {cell}: {old} vs {key}')
    levels[lv][cell] = key


def pillar(lv, key, x, z, y0, y1):
    for y in range(y0, y1 + 1):
        put(lv, key, x, y, z)


def ring(lv, y, r2min, r2max, maker, skip=()):
    for x in range(0, 14):
        for z in range(0, 14):
            c = canon(x, z)
            r2 = c[0] * c[0] + c[1] * c[1]
            if r2 == 0 or not r2min <= r2 <= r2max or c in skip:
                continue
            key = maker(c, r2)
            if key:
                put(lv, key, c[0], y, c[1])


# ============================ 阶 0 · 磯の磐座 ============================
put(0, 'k', 0, -1, 0)          # 核心正下：压实泥（中心轴允许放块）
put(0, 'i', 0, -1, 1)          # 苔藓
put(0, 'a', 1, -1, 1)          # 苔石
put(0, '0', 0, -1, 2)          # 仪式石环
put(0, 'q', 1, -1, 2)          # 蓝冰潮池
put(0, 'h', 2, -1, 1)          # 海晶灯水洼
put(0, '0', 2, -1, 2)
put(0, 'b', 0, -1, 3)          # 鹅卵石（祭品台基座）
put(0, 'd', 1, -1, 3)          # 凝灰岩
put(0, 'j', 2, -1, 3)          # 粗土
put(0, 'd', 3, -1, 1)
put(0, 'j', 3, -1, 2)
put(0, 'e', 0, -1, 4)          # 砾石外缘
put(0, 'C', 0, 0, 0)           # 核心
put(0, 'P', 0, 0, 3)           # 四向祭品台
put(0, 'p', 2, 0, 1)           # 小石灯籠柱
put(0, 'm', 2, 1, 1)           # 灯笼
put(0, 'p', 1, 0, 2)
put(0, 'm', 1, 1, 2)

# ============================ 阶 1 · 社の前庭 ============================
levels[1] = dict(levels[0])
F1 = {(0, 5): 's', (0, 6): '1', (1, 4): 't', (1, 5): 'h', (1, 6): 't',
      (2, 4): 'q', (2, 5): 'w', (3, 3): 't', (3, 4): 'g', (3, 5): '1',
      (4, 1): 't', (4, 2): 'q', (4, 3): 's', (4, 4): '1',
      (5, 1): 't', (5, 2): 'w', (5, 3): '1', (6, 1): 't'}
for (x, z), key in F1.items():
    put(1, key, x, -1, z)
put(1, 'P', 0, 0, 5)           # 祭品台 (0,5)×4
put(1, 'b', 3, 0, 3)           # 大石灯籠基座（鹅卵石）
put(1, 'p', 3, 1, 3)
put(1, 'm', 3, 2, 3)
put(1, 'n', 5, 0, 1)           # 盆栽蕨 (5,1)/(1,5)
put(1, 'n', 1, 0, 5)
# 四座木鸟居：柱 y0-4，貫 y3，額束 y4，笠木 y5，檐灯 y4
pillar(1, 'u', 1, 6, 0, 4)
pillar(1, 'u', 6, 1, 0, 4)
put(1, 'u', 0, 3, 6)           # 貫
put(1, 'z', 0, 2, 6)           # 注連縄（貫下垂链）
put(1, 'w', 0, 4, 6)           # 額束
for (x, z) in [(0, 6), (1, 6), (2, 6), (6, 1), (6, 2)]:
    put(1, 'x', x, 5, z)       # 笠木
put(1, 'm', 2, 4, 6)           # 檐下灯笼
put(1, 'm', 6, 4, 2)

# ============================ 阶 2 · 神庫の円堂 ============================
levels[2] = dict(levels[1])
F2 = {(0, 7): 'v', (0, 8): '2', (1, 7): 'D', (2, 6): 'q', (2, 7): 'E',
      (3, 6): 'D', (3, 7): '2', (4, 5): 'h', (4, 6): 'E', (5, 4): 'h',
      (5, 5): 'F', (5, 6): '2', (6, 2): 'q', (6, 3): 'F', (6, 4): 'E',
      (6, 5): '2', (7, 1): 'D', (7, 2): 'E', (7, 3): '2'}
for (x, z), key in F2.items():
    put(2, key, x, -1, z)
put(2, 'P', 0, 0, 4)           # 祭品台 (0,4)×4
put(2, 'n', 7, 0, 1)           # 盆栽 (7,1)/(1,7)
put(2, 'n', 1, 0, 7)
# 外圈石灯籠 (6,4)/(4,6)
for (x, z) in [(6, 4), (4, 6)]:
    put(2, 'b', x, 0, z)
    put(2, 'p', x, 1, z)
    put(2, 'm', x, 2, z)
# 八柱圆堂：铜础 + 深橡木柱
for (x, z) in [(2, 4), (4, 2)]:
    put(2, 'H', x, 0, z)
    put(2, 'H', x, 1, z)
    pillar(2, 'u', x, z, 2, 6)
# y6 环梁：铜 + 海晶灯
put(2, 'H', 0, 6, 4)
put(2, 'h', 1, 6, 4)
put(2, 'h', 3, 6, 3)
put(2, 'H', 4, 6, 1)
# y7-9 宝形屋面（逐层收小），正中垂链吊海晶"神灯"、顶部金相轮
ring(2, 7, 1, 32, lambda c, r2: 'F' if r2 >= 25 else ('E' if r2 >= 13 else 'D'))
ring(2, 8, 1, 18, lambda c, r2: 'F' if r2 >= 13 else 'E')
ring(2, 9, 1, 6, lambda c, r2: 'E')
put(2, 'h', 0, 7, 0)           # 垂链神灯（海晶灯）
put(2, 'z', 0, 8, 0)           # 垂链
put(2, 'G', 0, 9, 0)           # 宝顶中心柱
put(2, 'I', 0, 10, 0)          # 金相轮顶

doc = {
    'id': 'gensokyou:watatsumi_circle',
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


def exploded(cells):
    return sum(1 if (x == 0 and z == 0) else 4 for (x, y, z) in cells)


for i, cells in enumerate(levels):
    by_key = {}
    for k in cells.values():
        by_key[k] = by_key.get(k, 0) + 1
    total = exploded(cells)
    stones = exploded({c for c, k in cells.items() if k in STONES})
    detail = ', '.join(f'{k}x{v}' for k, v in sorted(by_key.items()))
    print(f'level {i}: 累积展开 {total} 格（新增 {len(adds_per_level[i])} 条目）'
          f' 石族 {stones} ({stones / total:.1%}) [{detail}]')
print(f'写入 {OUT} ({OUT.stat().st_size} 字节)')
