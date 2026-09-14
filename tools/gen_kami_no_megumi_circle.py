#!/usr/bin/env python3
"""生成 gensokyou:kami_no_megumi_circle（八百万神恩，1~5 阶）。

风格：祭坛神龛·内向密室。四重对称四分之一规范形；核心 (0,0,0)，
其正上方 (0,0,y>=1) 永久留空作降神光柱（各层 band 默认 hole=True 跳过原点）。

竖向：中央神殿逐阶收分加高（half 4,4,3,3,2），每阶外加一层更宽的低台与回廊；
纯增量，低级格位绝不重复登记。逐阶新增仪式石：key str(N) = #ritual_stones_N_plus。
"""
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import validate_ritual_pattern as v5  # noqa: E402

OUT = (Path(__file__).resolve().parents[1] / 'src' / 'main' / 'resources'
       / 'data' / 'gensokyou' / 'rituals' / 'kami_no_megumi_circle.json')

PALETTE = {
    'C': 'gensokyou:ritual_core',
    '1': '#gensokyou:ritual_stones_1_plus',
    '2': '#gensokyou:ritual_stones_2_plus',
    '3': '#gensokyou:ritual_stones_3_plus',
    '4': '#gensokyou:ritual_stones_4_plus',
    '5': '#gensokyou:ritual_stones_5_plus',
    'P': '#gensokyou:ritual_pedestals',
    'a': 'minecraft:deepslate_bricks',
    'b': 'minecraft:polished_deepslate',
    'c': 'minecraft:stone_bricks',
    'd': 'minecraft:chiseled_stone_bricks',
    'e': 'minecraft:stone_brick_wall',
    'f': 'minecraft:iron_bars',
    'g': 'minecraft:chain',
    'h': 'minecraft:lantern',
    'i': 'minecraft:glowstone',
    'j': 'minecraft:sea_lantern',
    'k': 'minecraft:dark_oak_log',
    'l': 'minecraft:dark_oak_planks',
    'm': 'minecraft:quartz_block',
    'n': 'minecraft:quartz_pillar',
    'o': 'minecraft:amethyst_block',
    'p': 'minecraft:purple_stained_glass',
    'q': 'minecraft:magenta_stained_glass',
    'r': 'minecraft:purpur_block',
    's': 'minecraft:purpur_pillar',
    't': 'minecraft:end_stone_bricks',
    'u': 'minecraft:crying_obsidian',
    'v': 'minecraft:oxidized_copper',
    'w': 'minecraft:deepslate_tiles',
    'x': 'minecraft:potted_cherry_sapling',
    'y': 'minecraft:purple_banner',
    'z': 'minecraft:gold_block',
    'A': 'minecraft:moss_block',
    'B': 'minecraft:pink_petals',
}

levels = [dict() for _ in range(5)]


def canon(x, z):
    x, z = abs(x), abs(z)
    return (0, x) if z == 0 else (x, z)


def put(lv, key, x, y, z):
    cx, cz = canon(x, z)
    if cx == 0 and cz == 0 and y == 0:
        raise SystemExit(f'origin reserved for anchor C (level {lv + 1})')
    cell = (cx, y, cz)
    old = levels[lv].get(cell)
    if old and old != key:
        raise SystemExit(f'冲突 level {lv + 1} {cell}: {old!r} vs {key!r}')
    levels[lv][cell] = key


def put4(lv, key, x, y, z):
    put(lv, key, x, y, z)
    if x != z:
        put(lv, key, z, y, x)


def flowerbed4(lv, x, z, flower='B'):
    """合规花坛：下方一格铺苔藓块承托，花瓣置于其上（严禁石头直接栽花）。"""
    put4(lv, 'A', x, -1, z)
    put4(lv, flower, x, 0, z)


def band(lv, y, hout, hin, key, hole=True, skip=()):
    """hin <= max(|x|,|z|) <= hout 的实心/环形层。hole=False 时含原点（仅地面用）。"""
    for x in range(-hout, hout + 1):
        for z in range(-hout, hout + 1):
            m = max(abs(x), abs(z))
            if not (hin <= m <= hout):
                continue
            if canon(x, z) in skip:
                continue
            if hole and x == 0 and z == 0:
                continue
            put(lv, key, x, y, z)


def wallring(lv, y, half, key, skip=()):
    for x in range(-half, half + 1):
        for z in range(-half, half + 1):
            if max(abs(x), abs(z)) != half:
                continue
            if canon(x, z) in skip:
                continue
            put(lv, key, x, y, z)


def pillar(lv, key, x, z, y0, y1):
    for y in range(y0, y1 + 1):
        put(lv, key, x, y, z)


# ============================ LEVEL 1 「祠」 ============================
# 9x9 内向石室：y0-3 围壁（1 阶仪式石 + 石砖），四向 3 格高门洞，无顶（露天降神）。
band(0, -1, 3, 0, 'a', hole=False, skip={(3, 3)})
band(0, -1, 4, 4, 'c', hole=False)
door, corner = canon(0, 4), (4, 4)
wallring(0, 0, 4, '1', skip={door, corner})
wallring(0, 1, 4, 'c', skip={door, corner, (1, 4), (4, 1)})
put4(0, 'f', 1, 1, 4)
put4(0, 'f', 4, 1, 1)
wallring(0, 2, 4, 'c', skip={door, corner, (2, 4), (4, 2)})
put4(0, 'h', 2, 2, 4)
put4(0, 'h', 4, 2, 2)
pillar(0, 'k', 4, 4, 0, 2)
put(0, 'd', 4, 3, 4)
wallring(0, 3, 4, 'm', skip={corner})
put(0, 'P', 2, 0, 2)
flowerbed4(0, 3, 3)

# ============================ LEVEL 2 「社」 ============================
# 外扩 13x13 低台 + 四面八方回廊；中央石室封顶（内向密室），高层开紫玻璃高窗。
band(1, -1, 5, 5, 'a', hole=False)
band(1, -1, 6, 6, 'c', hole=False)
d6, c6 = canon(0, 6), (6, 6)
wallring(1, 0, 6, '2', skip={d6, c6})
wallring(1, 1, 6, 'c', skip={d6, c6})
wallring(1, 2, 6, 'd', skip={d6, c6, (2, 6), (6, 2)})
wallring(1, 3, 6, 'm', skip={c6})
pillar(1, 'k', 6, 6, 0, 3)
put4(1, 'h', 2, 2, 6)
band(1, 4, 6, 5, 'l', hole=False)
put(1, 'P', 5, 0, 5)
wallring(1, 4, 4, 'p')
wallring(1, 5, 4, 'c')
band(1, 6, 4, 0, 'w')
band(1, 7, 2, 0, 'w')
put4(1, 'g', 0, 2, 6)

# ============================ LEVEL 3 「本殿」 ============================
# 再扩 17x17 低台；中央收分为 7x7 高殿，加窗带与挑檐；台缘石灯籠成列。
band(2, -1, 7, 7, 'a', hole=False, skip={(2, 7), (7, 2)})
band(2, -1, 8, 8, 'c', hole=False)
d8, c8 = canon(0, 8), (8, 8)
wallring(2, 0, 8, '3', skip={d8, c8})
wallring(2, 1, 8, 'c', skip={d8, c8})
wallring(2, 2, 8, 'd', skip={d8, c8, (3, 8), (8, 3)})
wallring(2, 3, 8, 'm', skip={c8})
pillar(2, 'k', 8, 8, 0, 3)
put4(2, 'h', 3, 2, 8)
band(2, 4, 8, 7, 'l', hole=False)
put(2, 'P', 7, 0, 7)
band(2, 7, 3, 3, 'w')
wallring(2, 8, 3, '3')
wallring(2, 9, 3, 'c')
wallring(2, 10, 3, 'p')
wallring(2, 11, 3, 'm')
band(2, 12, 3, 0, 'w')
band(2, 13, 1, 0, 'm')
put4(2, 'g', 0, 2, 8)
put4(2, 'A', 5, 0, 7)
flowerbed4(2, 2, 7)

# ============================ LEVEL 4 「大社」 ============================
# 21x21 外院；墙基嵌入神龛（哭泣黑曜石 / 盆栽）；中央起 3 层高塔 + 天光高窗。
band(3, -1, 9, 9, 'a', hole=False)
band(3, -1, 10, 10, 'c', hole=False)
d10, c10 = canon(0, 10), (10, 10)
wallring(3, 0, 10, '4', skip={d10, c10})
wallring(3, 1, 10, 'c', skip={d10, c10, (2, 10), (10, 2), (4, 10), (10, 4)})
put4(3, 'u', 2, 1, 10)
put4(3, 'x', 4, 1, 10)
wallring(3, 2, 10, 'd', skip={d10, c10, (2, 10), (10, 2), (6, 10), (10, 6)})
put4(3, 'o', 2, 2, 10)
put4(3, 'h', 6, 2, 10)
wallring(3, 3, 10, 'm', skip={c10})
pillar(3, 'k', 10, 10, 0, 3)
put(3, 'y', 10, 5, 10)
band(3, 4, 10, 9, 'l', hole=False)
put(3, 'P', 9, 0, 9)
band(3, 13, 3, 2, 'w')
wallring(3, 14, 3, '4')
wallring(3, 15, 3, 'p')
wallring(3, 16, 3, 'c')
wallring(3, 17, 3, 'm')
band(3, 18, 3, 0, 'w')
band(3, 19, 1, 0, 'w')
put4(3, 'g', 0, 2, 10)
put4(3, 'A', 5, 0, 9)

# ============================ LEVEL 5 「神域」 ============================
# 27x27 神域广场；四向楼门（石柱 + 斗拱 + 重檐）；中央神塔升至顶并立金色宝顶。
band(4, -1, 11, 11, 'a', hole=False)
band(4, -1, 12, 12, 'a', hole=False, skip={(5, 12), (12, 5)})
band(4, -1, 13, 13, 'c', hole=False)
c13 = (13, 13)
gate = {(0, 13), (1, 13), (2, 13), (13, 1), (13, 2)}
niche = {(3, 13), (13, 3), (5, 13), (13, 5), (8, 13), (13, 8), (7, 13), (13, 7)}
wallring(4, 0, 13, '5', skip=gate | {c13} | niche)
wallring(4, 1, 13, 'c', skip=gate | {c13} | niche)
put4(4, 'u', 3, 1, 13)
put4(4, 'x', 5, 1, 13)
put4(4, 't', 8, 1, 13)
wallring(4, 2, 13, 'd', skip=gate | {c13} | niche)
put4(4, 'o', 3, 2, 13)
put4(4, 'h', 7, 2, 13)
wallring(4, 3, 13, 'm', skip=gate | {c13})
pillar(4, 'k', 13, 13, 0, 3)
put(4, 'y', 13, 5, 13)
band(4, 4, 13, 12, 'l', hole=False, skip=gate)
put(4, 'P', 12, 0, 12)
# 四向楼门
pillar(4, 'n', 2, 13, 0, 5)
pillar(4, 'n', 13, 2, 0, 5)
put4(4, 'm', 0, 6, 13)
put4(4, 'm', 1, 6, 13)
put4(4, 'w', 0, 7, 13)
put4(4, 'w', 1, 7, 13)
put4(4, 'h', 2, 6, 13)
put4(4, 's', 0, 8, 13)
# 中央神塔顶
band(4, 19, 2, 2, 'w')
wallring(4, 20, 2, '5')
wallring(4, 21, 2, 'q')
wallring(4, 22, 2, 'c')
wallring(4, 23, 2, 'm')
band(4, 24, 2, 0, 'w')
band(4, 25, 1, 0, 'z')
put4(4, 'h', 2, 25, 2)
put4(4, 'A', 6, 0, 12)
flowerbed4(4, 5, 12)

# ---------------------------- 序列化 ----------------------------
for lv in levels:
    lv[(0, 0, 0)] = 'C'

doc = {
    'id': 'gensokyou:kami_no_megumi_circle',
    'anchorKey': 'C',
    'tiers': [1, 2, 3, 4, 5],
    'palette': PALETTE,
}
levels_json = [{'level': i + 1} for i in range(5)]
adds_per_level = []
for i in range(5):
    prev = levels[i - 1] if i else {}
    adds_per_level.append([(k, x, y, z)
                           for (x, y, z), k in sorted(levels[i].items())
                           if (x, y, z) not in prev])
text = (json.dumps(doc, indent=2, ensure_ascii=False)[:-1].rstrip()
        + ',\n  "levels": ' + v5.serialize_levels_v5(levels_json, adds_per_level)
        + '\n}\n')
OUT.write_text(text, encoding='utf-8')

stone_keys = set('12345')
for i, cells in enumerate(levels):
    by_key = {}
    for k in cells.values():
        by_key[k] = by_key.get(k, 0) + 1
    total = len(cells)
    stones = sum(v for k, v in by_key.items() if k in stone_keys)
    detail = ', '.join(f'{k}x{v}' for k, v in sorted(by_key.items()))
    print(f'level {i + 1}: 累积 {total} 格（新增 {len(adds_per_level[i])} 条目）'
          f' 石族占比 {stones}/{total} = {stones / total:.1%}  [{detail}]')
print(f'写入 {OUT} ({OUT.stat().st_size} 字节)')
