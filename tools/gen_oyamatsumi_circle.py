#!/usr/bin/env python3
"""生成 oyamatsumi_circle.json（大山祇神之座，0~2 阶，山体岩窟风，v5 逐级增量）。

坐标 = 四分之一规范形（x>=0, z>=0），y 相对核心；核心 (0,0,0)。
  L0 y-1 磐座岩盘 r^2<=12 | y0 核心岩环 orbit(1,1)、对角祭品台 orbit(2,2)、巨石(1,2)(2,1)
     y1 岩环上层 + 苔石顶 | y2 煤层 / 紫水晶 / 提灯
  L1 y-1 岩窟地坪 r^2 13..25 | y0 神域石垣基环 r^2 17..25（阶1仪式石）
     y1..4 岩壁（矿脉/苔/滴水石）四向留门 | y4 岩拱楣 orbit(0,4) + 楣上提灯
     y5 岩盖 r^2 13..25（轴留空，核心上方天窗）
  L2 y-1 外岩坪 r^2 26..64（外缘有机碎裂）| y0 外环阶2仪式石 r^2 26..36、
     通天岩柱 (2,6)(6,2)、外环祭品台 orbit(0,7) | y6 岩窟穹顶 r^2 5..64（天窗 r^2<=4）
     y7 穹顶边缘加厚 r^2 40..64 | y5 垂吊提灯
"""
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import validate_ritual_pattern as v5  # noqa: E402

OUT = (Path(__file__).resolve().parents[1] / 'src' / 'main' / 'resources'
       / 'data' / 'gensokyou' / 'rituals' / 'oyamatsumi_circle.json')

PALETTE = {
    'C': 'gensokyou:ritual_core',
    '0': '#gensokyou:ritual_stones',
    '1': '#gensokyou:ritual_stones_1_plus',
    '2': '#gensokyou:ritual_stones_2_plus',
    'P': '#gensokyou:ritual_pedestals',
    'a': 'minecraft:stone',
    'b': 'minecraft:cobblestone',
    'c': 'minecraft:mossy_cobblestone',
    'd': 'minecraft:tuff',
    'e': 'minecraft:deepslate',
    'f': 'minecraft:cobbled_deepslate',
    'g': 'minecraft:andesite',
    'h': 'minecraft:calcite',
    'i': 'minecraft:dripstone_block',
    'j': 'minecraft:moss_block',
    'k': 'minecraft:stone_bricks',
    'l': 'minecraft:deepslate_tiles',
    'm': 'minecraft:polished_deepslate',
    'n': 'minecraft:chiseled_stone_bricks',
    'o': 'minecraft:coal_block',
    'p': 'minecraft:copper_block',
    'q': 'minecraft:raw_iron_block',
    'r': 'minecraft:raw_copper_block',
    's': 'minecraft:lapis_block',
    't': 'minecraft:amethyst_block',
    'u': 'minecraft:lantern',
    'w': 'minecraft:potted_fern',
    'x': 'minecraft:potted_brown_mushroom',
    'y': 'minecraft:potted_dead_bush',
}

levels = [{}, {}, {}]


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


def setc(lv, key, x, y, z):
    cx, cz = canon(x, z)
    levels[lv][(cx, y, cz)] = key


def h(*v):
    r = 2166136261
    for n in v:
        r = ((r ^ (n & 0xffffffff)) * 16777619) & 0xffffffff
    return r


ROCK = ['d', 'e', 'f', 'b', 'a']
WALLROCK = ['d', 'e', 'f', 'k', 'g', 'a']
AXIS = {(0, 1), (0, 2), (0, 3), (0, 4), (0, 5)}


def floor_maker(x, z, y):
    if x * x + z * z > 49 and h(x, z, 91) % 3 == 0:
        return None
    if h(x, z, 17) % 19 == 0:
        return 'c'
    if h(x, z, 23) % 37 == 0:
        return 'j'
    return ROCK[h(x // 2, z // 2, 29) % len(ROCK)]


def disc(lv, y, r2min, r2max, maker, skip=()):
    for x in range(0, 12):
        for z in range(0, 12):
            cx, cz = canon(x, z)          # maker 一律吃规范格，保证四重展开同值
            r2 = cx * cx + cz * cz
            if r2 == 0 or not r2min <= r2 <= r2max:
                continue
            if (cx, cz) in skip:
                continue
            key = maker(cx, cz, y)
            if key is None:
                continue
            put(lv, key, cx, y, cz)


# ---------------- LEVEL 0 「磐座」 ----------------
disc(0, -1, 1, 12, floor_maker)
levels[0][(0, 0, 0)] = 'C'        # 锚点（全文件唯一、仅最低阶，位于 (0,0,0)）
put(0, '0', 1, 0, 1)              # 核心岩环（对角 4 格）
put(0, '0', 1, 1, 1)              # 岩环上层
put(0, 'P', 0, 0, 3)              # 祭品台 ×4（沿轴 orbit）
put(0, 'b', 1, 0, 2)              # 巨石（+z 侧）
put(0, 'b', 2, 0, 1)              # 巨石（+x 侧）
put(0, 'c', 1, 1, 2)              # 苔圆石顶
put(0, 'c', 2, 1, 1)
put(0, 'o', 1, 2, 1)              # 煤层嵌岩顶
put(0, 't', 1, 2, 2)              # 紫水晶
put(0, 'u', 2, 2, 1)              # 提灯
setc(0, 'l', 0, -1, 3)            # 参道内段铺石

# ---------------- LEVEL 1 「岩窟」 ----------------
disc(1, -1, 13, 25, floor_maker)

WALL = [(1, 4), (4, 1), (2, 4), (4, 2), (3, 3), (3, 4), (4, 3)]
WALL_TOP = {(1, 4): 4, (4, 1): 4, (2, 4): 4, (4, 2): 4,
            (3, 3): 4, (3, 4): 3, (4, 3): 3}
ORE1 = {                          # 岩壁矿脉（对称成对）
    (2, 1, 4): 'p', (4, 1, 2): 'p',
    (2, 3, 4): 'o', (4, 3, 2): 'o',
    (3, 2, 3): 't',
    (1, 2, 4): 'q', (4, 2, 1): 'q',
    (3, 1, 4): 'r', (4, 1, 3): 'r',
}


def wall_key(x, y, z):
    if (x, y, z) in ORE1:
        return ORE1[(x, y, z)]
    r = h(x, y, z, 5)
    if r % 17 == 0:
        return 'j'
    if r % 13 == 0:
        return 'i'
    return WALLROCK[h(x // 2, y, z // 2, 9) % len(WALLROCK)]


for (x, z) in WALL:
    put(1, '1', x, 0, z)          # 神域石垣基环（阶 1 仪式石）
for (x, z) in WALL:
    for y in range(1, WALL_TOP[(x, z)] + 1):
        put(1, wall_key(x, y, z), x, y, z)


def roof_key(x, z, y):
    r = h(x, z, 55)
    if r % 23 == 0:
        return 't'
    if r % 17 == 0:
        return 'o'
    if r % 13 == 0:
        return 'i'
    if r % 11 == 0:
        return 'c'
    return ROCK[h(x // 2, z // 2, 57) % len(ROCK)]


disc(1, 5, 13, 25, roof_key, skip=AXIS)   # 岩盖（轴留空成天窗/门洞）
put(1, 'l', 0, 4, 4)                      # 四向岩拱楣
put(1, 'u', 0, 5, 4)                      # 楣上提灯
put(1, 'u', 2, 4, 3)                      # 岩盖下垂吊提灯
put(1, 'P', 2, 0, 2)                      # 祭品台 ×4（对角 orbit）

setc(1, 'l', 0, -1, 4)                    # 参道外段铺石
setc(1, 'l', 0, -1, 5)
setc(1, 'c', 2, -1, 3)                    # 苔圆石
setc(1, 'w', 2, 0, 3)                     # 盆栽蕨
setc(1, 'c', 3, -1, 2)
setc(1, 'x', 3, 0, 2)                     # 盆栽蘑菇
setc(1, 'c', 1, -1, 2)
setc(1, 'j', 1, -1, 3)


# ---------------- LEVEL 2 「山祇の岩殿」 ----------------
disc(2, -1, 26, 64, floor_maker)

RING2 = [(1, 5), (5, 1), (2, 5), (5, 2), (3, 5), (5, 3), (4, 4), (0, 6), (6, 0)]
for (x, z) in RING2:
    put(2, '2', x, 0, z)          # 外环阶 2 仪式石


def col_key(y):
    return ['m', 'm', 'n', 'n', 'h', 'd'][y]


for (x, z) in [(2, 6), (6, 2)]:   # 通天岩柱 ×8
    for y in range(0, 6):
        put(2, col_key(y), x, y, z)


def vault_key(x, z, y):
    r = h(x, z, 71)
    if r % 29 == 0:
        return 't'
    if r % 23 == 0:
        return 'o'
    if r % 19 == 0:
        return 's'
    if r % 17 == 0:
        return 'q'
    if r % 13 == 0:
        return 'p'
    if r % 11 == 0:
        return 'i'
    if r % 7 == 0:
        return 'c'
    return ROCK[h(x // 2, z // 2, 73) % len(ROCK)]


disc(2, 6, 5, 64, vault_key)      # 岩窟穹顶（核心正上方留天窗 r^2<=4）
disc(2, 7, 40, 64, vault_key)     # 穹顶边缘加厚
put(2, 'P', 0, 0, 7)              # 外环祭品台 ×4
put(2, 'u', 2, 5, 2)              # 天窗下垂吊提灯

put(2, 'b', 4, 0, 5)              # 外岩坪巨石群
put(2, 'c', 4, 1, 5)
put(2, 'u', 4, 2, 5)
put(2, 'b', 5, 0, 4)
put(2, 'c', 5, 1, 4)
put(2, 'd', 1, 0, 6)
put(2, 'i', 1, 1, 6)
put(2, 'd', 6, 0, 1)
put(2, 'i', 6, 1, 1)

setc(2, 's', 5, -1, 3)            # 外岩坪矿脉
setc(2, 't', 3, -1, 5)
setc(2, 'q', 4, -1, 4)
setc(2, 'j', 6, -1, 2)
setc(2, 'y', 6, 0, 2)

# ---------------- 落盘（v5 逐级增量） ----------------
doc = {
    'id': 'gensokyou:oyamatsumi_circle',
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

STONE = {'0', '1', '2'}
for i, cells in enumerate(levels):
    by_key = {}
    for k in cells.values():
        by_key[k] = by_key.get(k, 0) + 1
    total = len(cells)
    exp = sum(1 if (x == 0 and z == 0) else 4 for (x, _y, z) in cells)
    stone = sum(v for k, v in by_key.items() if k in STONE)
    detail = ', '.join(f'{k}x{v}' for k, v in sorted(by_key.items()))
    print(f'level {i}: 规范 {total} 格 / 展开 {exp} 格（新增 {len(adds_per_level[i])} 条目）'
          f' 石族 {stone}/{total} ({stone / total:.1%}) [{detail}]')
print(f'写入 {OUT} ({OUT.stat().st_size} 字节)')
