#!/usr/bin/env python3
"""生成 gensokyou:nichirin_circle（日轮天台，0~1 阶）。

风格：露天圆盘石台，白天核心直晒阳光产灵力。四重对称四分之一规范形；
核心 (0,0,0)，站立面 y0，结构地板 y-1，全顶无盖（供阳光直照）。

  L0「日輪盤」  y-1 石砖圆盘 r²≤20（盘心錾制石砖 / 日轮内环 #ritual_stones /
                外缘磨制安山岩 / 四正位金块日芒尖）；y0 核心四边台基角 +
                四正位石英日柱(海晶灯帽) + 四斜角石砖墙角灯。
  L1「日冕台」  y-1 外扩台面 r²21..81（日冕金环 / 日輪大环 #ritual_stones_1_plus /
                外缘磨制安山岩，四正位留参道口）+ 四向日芒辐条；y0 八柱柱廊(金冠)
                + 近核心四祭品台 + 外台盆栽 + 外缘矮栏(留四向门)。

纯增量，低级格位绝不重复登记。逐阶新增仪式石：key N = #ritual_stones_N_plus。
"""
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import validate_ritual_pattern as v5  # noqa: E402

OUT = (Path(__file__).resolve().parents[1] / 'src' / 'main' / 'resources'
       / 'data' / 'gensokyou' / 'rituals' / 'nichirin_circle.json')

PALETTE = {
    'C': 'gensokyou:ritual_core',
    '0': '#gensokyou:ritual_stones',
    '1': '#gensokyou:ritual_stones_1_plus',
    'P': '#gensokyou:ritual_pedestals',
    'a': 'minecraft:stone_bricks',
    'b': 'minecraft:polished_andesite',
    'd': 'minecraft:chiseled_stone_bricks',
    'e': 'minecraft:stone_brick_wall',
    'h': 'minecraft:lantern',
    'j': 'minecraft:sea_lantern',
    'n': 'minecraft:quartz_pillar',
    'z': 'minecraft:gold_block',
    'p': 'minecraft:potted_cherry_sapling',
}

levels = [dict(), dict()]


def canon(x, z):
    x, z = abs(x), abs(z)
    return (0, x) if z == 0 else (x, z)


def put(lv, key, x, y, z):
    cx, cz = canon(x, z)
    if cx == 0 and cz == 0 and y == 0:
        raise SystemExit(f'origin reserved for anchor C (level {lv})')
    cell = (cx, y, cz)
    old = levels[lv].get(cell)
    if old and old != key:
        raise SystemExit(f'冲突 level {lv} {cell}: {old!r} vs {key!r}')
    levels[lv][cell] = key


def disc(lv, y, r2min, r2max, maker, skip=()):
    R = int(r2max ** 0.5) + 1
    for x in range(-R, R + 1):
        for z in range(-R, R + 1):
            r2 = x * x + z * z
            if not (r2min <= r2 <= r2max):
                continue
            c = canon(x, z)
            if c in skip:
                continue
            key = maker(c)
            if key:
                put(lv, key, x, y, z)


def ring(lv, y, r2min, r2max, key, skip=()):
    disc(lv, y, r2min, r2max, lambda c: key, skip)


def pillar(lv, key, x, z, y0, y1):
    for y in range(y0, y1 + 1):
        put(lv, key, x, y, z)


# ============================ L0「日輪盤」 ============================
def l0_floor(c):
    x, z = c
    r2 = x * x + z * z
    if r2 <= 4:
        return 'd'
    if 9 <= r2 <= 13:
        return '0'
    if 17 <= r2 <= 20:
        return 'b'
    return 'a'


disc(0, -1, 0, 20, l0_floor, skip={(0, 4)})
put(0, 'z', 0, -1, 4)                 # 四正位日芒尖 (0,±4)/(±4,0)
put(0, 'd', 0, 0, 2)                  # 核心四边 d=2 台基角
pillar(0, 'n', 0, 3, 0, 1)            # 四正位石英日柱
put(0, 'j', 0, 2, 3)                  # 日柱海晶灯帽
put(0, 'e', 3, 0, 3)                  # 四斜角石砖墙
put(0, 'h', 3, 1, 3)                  # 角灯

# ============================ L1「日冕台」 ============================
levels[1] = dict(levels[0])


def l1_floor(c):
    x, z = c
    r2 = x * x + z * z
    if 29 <= r2 <= 32:
        return 'z'
    if 33 <= r2 <= 40:
        return '1'
    if 65 <= r2 <= 81:
        return 'b'
    return 'a'


SPOKES = {(0, 5), (0, 7), (0, 8), (5, 5)}   # 日芒辐条（连接内外环）
disc(1, -1, 21, 81, l1_floor, skip=SPOKES)
for sx, sz in sorted(SPOKES):
    put(1, '1', sx, -1, sz)

pillar(1, 'n', 2, 7, 0, 3)            # 八柱柱廊：(±2,±7)，避让四正参道
put(1, 'z', 2, 4, 7)                  # 金冠
pillar(1, 'n', 5, 5, 0, 3)            # 八柱柱廊：(±5,±5)
put(1, 'z', 5, 4, 5)                  # 金冠

put(1, 'P', 2, 0, 2)                  # 近核心四斜位祭品台
put(1, 'p', 6, 0, 3)                  # 外台盆栽 (±6,±3)
put(1, 'p', 3, 0, 6)                  # 外台盆栽 (±3,±6)

ring(1, 0, 65, 81, 'e', skip={(0, 9), (1, 8)})  # 外缘矮栏，四正位开三格参道口
put(1, 'h', 6, 1, 6)                  # 栏上灯笼
put(1, 'h', 3, 1, 8)

# ---------------------------- 序列化 ----------------------------
levels[0][(0, 0, 0)] = 'C'

doc = {
    'id': 'gensokyou:nichirin_circle',
    'anchorKey': 'C',
    'toggleable': True,
    'tiers': [0, 1],
    'palette': PALETTE,
}
levels_json = [{'level': i} for i in range(2)]
adds_per_level = []
for i in range(2):
    prev = levels[i - 1] if i else {}
    adds_per_level.append([(k, x, y, z)
                           for (x, y, z), k in sorted(levels[i].items())
                           if (x, y, z) not in prev])
text = (json.dumps(doc, indent=2, ensure_ascii=False)[:-1].rstrip()
        + ',\n  "levels": ' + v5.serialize_levels_v5(levels_json, adds_per_level)
        + '\n}\n')
OUT.write_text(text, encoding='utf-8')

stone_keys = set('01')
for i, cells in enumerate(levels):
    by_key = {}
    for k in cells.values():
        by_key[k] = by_key.get(k, 0) + 1
    total = len(cells)
    stones = sum(v for k, v in by_key.items() if k in stone_keys)
    detail = ', '.join(f'{k}x{v}' for k, v in sorted(by_key.items()))
    print(f'level {i}: 累积 {total} 格（新增 {len(adds_per_level[i])} 条目）'
          f' 石族占比 {stones}/{total} = {stones / total:.1%}  [{detail}]')
print(f'写入 {OUT} ({OUT.stat().st_size} 字节)')
