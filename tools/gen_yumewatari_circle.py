#!/usr/bin/env python3
"""生成 gensokyou:yumewatari_circle（梦渡之座，0~2 阶）。

风格：月白梦境渡坛。冷色夜石圆坛，中央核心坐在月纹梦池上；0 阶初梦小坛，
1 阶外扩成四柱华盖露台，2 阶再扩并架起月轮光环。床由玩家自行铺在坛面，
structure 内不放床。四重对称四分之一规范形；核心 (0,0,0) 仅最低阶声明，
核心正上方整列永久留空（put 守卫）。纯增量，逐阶新增仪式石 key 分别为
#ritual_stones / _1_plus / _2_plus。
"""
import json
import sys
from collections import deque
from pathlib import Path

try:
    sys.stdout.reconfigure(encoding='utf-8')
except Exception:
    pass

sys.path.insert(0, str(Path(__file__).resolve().parent))
import validate_ritual_pattern as v5  # noqa: E402

OUT = (Path(__file__).resolve().parents[1] / 'src' / 'main' / 'resources'
       / 'data' / 'gensokyou' / 'rituals' / 'yumewatari_circle.json')

PALETTE = {
    'C': 'gensokyou:ritual_core',
    '0': '#gensokyou:ritual_stones',
    '1': '#gensokyou:ritual_stones_1_plus',
    '2': '#gensokyou:ritual_stones_2_plus',
    'P': '#gensokyou:ritual_pedestals',
    'a': 'minecraft:deepslate_bricks',
    'b': 'minecraft:polished_deepslate',
    'c': 'minecraft:deepslate_tiles',
    'd': 'minecraft:stone_bricks',
    'e': 'minecraft:chiseled_stone_bricks',
    'f': 'minecraft:dark_oak_log',
    'g': 'minecraft:dark_oak_planks',
    'h': 'minecraft:sea_lantern',
    'i': 'minecraft:lantern',
    'j': 'minecraft:chain',
    'l': 'minecraft:purple_stained_glass',
    'm': 'minecraft:amethyst_block',
    'p': 'minecraft:potted_cherry_sapling',
    'q': 'minecraft:waxed_oxidized_copper',
    'r': 'minecraft:soul_lantern',
    't': 'minecraft:polished_basalt',
    'u': 'minecraft:crying_obsidian',
    'v': 'minecraft:end_stone_bricks',
    'w': 'minecraft:purpur_block',
    'z': 'minecraft:magenta_stained_glass',
}

levels = [dict() for _ in range(3)]


def canon(x, z):
    x, z = abs(x), abs(z)
    return (0, x) if z == 0 else (x, z)


def put(lv, key, x, y, z, over=False):
    cx, cz = canon(x, z)
    if cx == 0 and cz == 0 and y >= 0:
        raise SystemExit(f'核心正上方整列与原点须留空: level {lv} key={key} ({x},{y},{z})')
    cell = (cx, y, cz)
    old = levels[lv].get(cell)
    if old and old != key and not over:
        raise SystemExit(f'冲突 level {lv} {cell}: {old!r} vs {key!r}')
    levels[lv][cell] = key


def put4(lv, key, x, y, z, over=False):
    put(lv, key, x, y, z, over)
    if x != z:
        put(lv, key, z, y, x, over)


def disc(lv, y, r2max, keyf, hole=True):
    r = int(r2max ** 0.5) + 1
    for x in range(0, r + 1):
        for z in range(0, r + 1):
            r2 = x * x + z * z
            if r2 > r2max or (hole and r2 == 0):
                continue
            k = keyf(x, z, r2)
            if k:
                put(lv, k, x, y, z)


def annulus(lv, y, r2min, r2max, keyf):
    r = int(r2max ** 0.5) + 1
    for x in range(0, r + 1):
        for z in range(0, r + 1):
            r2 = x * x + z * z
            if not r2min <= r2 <= r2max:
                continue
            k = keyf(x, z, r2)
            if k:
                put(lv, k, x, y, z)


def square_ring(lv, y, h, keyf):
    for x in range(0, h + 1):
        for z in range(0, h + 1):
            if max(x, z) != h:
                continue
            k = keyf(x, z)
            if k:
                put(lv, k, x, y, z)


def pillar(lv, key, x, z, y0, y1):
    for y in range(y0, y1 + 1):
        put(lv, key, x, y, z)


# ============================ 0 阶「初梦」 ============================
# 冷色圆坛 r²≤16：心部深板岩瓦、中环磨制深板岩、内环深板岩砖、外缘石砖。
disc(0, -1, 16,
     lambda x, z, r2: 'c' if r2 <= 1 else 'b' if r2 <= 4 else 'a' if r2 <= 9 else 'd',
     hole=False)
put4(0, '0', 0, 0, 2)
put4(0, '0', 2, 0, 2)
put4(0, 'p', 0, 0, 3)
pillar(0, 'f', 0, 4, 0, 2)
put4(0, 'i', 0, 3, 4)

# ============================ 1 阶「梦华盖」 ============================
# 外扩圆坛 r²≤49；最外缘防锈铜收边、其内紫玻璃梦池；四柱华盖 + 四隅祭品台。
levels[1] = dict(levels[0])
annulus(1, -1, 17, 49,
        lambda x, z, r2: 'q' if r2 >= 45 else 'l' if r2 >= 37
        else ('a' if (x + z) % 2 == 0 else 'd'))
put4(1, '1', 0, -1, 5, over=True)
put4(1, '1', 3, -1, 4, over=True)
put4(1, '1', 4, -1, 4, over=True)
put4(1, 'e', 0, -1, 7, over=True)
pillar(1, 'f', 0, 5, 0, 4)
square_ring(1, 5, 5, lambda x, z: 'l' if (x + z) % 2 == 0 else 'g')
for dz in (1, 2, 3, 4):
    put4(1, 'g', 0, 5, dz)
put4(1, 'j', 0, 4, 3)
put4(1, 'i', 0, 3, 3)
put4(1, 'P', 4, 0, 4)
pillar(1, 't', 0, 6, 0, 1)
put4(1, 'r', 0, 2, 6)

# ============================ 2 阶「渡月」 ============================
# 再扩圆坛 r²≤100；末地石砖/紫珀棋盘 + 紫水晶/品红玻璃梦环；八方柱 + 月轮光环。
levels[2] = dict(levels[1])
annulus(2, -1, 50, 100,
        lambda x, z, r2: 'z' if r2 >= 93 else 'm' if r2 >= 82
        else ('v' if (x + z) % 2 == 0 else 'w'))
put4(2, '2', 0, -1, 10, over=True)
put4(2, '2', 6, -1, 8, over=True)
put4(2, '2', 7, -1, 7, over=True)
pillar(2, 't', 0, 10, 0, 5)
pillar(2, 't', 6, 8, 0, 5)
pillar(2, 't', 8, 6, 0, 5)
annulus(2, 6, 81, 100,
        lambda x, z, r2: 'm' if (x + z) % 4 == 0
        else ('z' if (x + z) % 2 == 0 else 'w'))
put4(2, 'h', 0, 7, 10)
put4(2, 'h', 6, 7, 8)
put4(2, 'h', 8, 7, 6)
put4(2, 'u', 0, 5, 9)
put4(2, 'u', 3, 5, 9)
put4(2, 'P', 7, 0, 7)

# ---------------------------- 序列化 ----------------------------
for lv in levels:
    lv[(0, 0, 0)] = 'C'

doc = {
    'id': 'gensokyou:yumewatari_circle',
    'anchorKey': 'C',
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


# ---------------------------- 自检：连通性 / 悬浮 ----------------------------
def check_connectivity(cells):
    expanded = {}
    for (x, y, z), k in cells.items():
        for k2, x2, y2, z2, _o in v5.expand_entry(k, x, y, z, None):
            expanded[(x2, y2, z2)] = k2
    keys = set(expanded)
    start = next(iter(keys))
    seen = {start}
    q = deque([start])
    while q:
        x, y, z = q.popleft()
        for nx, ny, nz in ((x + 1, y, z), (x - 1, y, z), (x, y + 1, z),
                           (x, y - 1, z), (x, y, z + 1), (x, y, z - 1)):
            if (nx, ny, nz) in keys and (nx, ny, nz) not in seen:
                seen.add((nx, ny, nz))
                q.append((nx, ny, nz))
    return len(seen), len(keys)


stone_keys = set('012')
for i, cells in enumerate(levels):
    by_key = {}
    for k in cells.values():
        by_key[k] = by_key.get(k, 0) + 1
    total = len(cells)
    stones = sum(v for k, v in by_key.items() if k in stone_keys)
    detail = ', '.join(f'{k}x{v}' for k, v in sorted(by_key.items()))
    print(f'level {i}: 累积 {total} 格（新增 {len(adds_per_level[i])}）'
          f' 石族 {stones}/{total} = {stones / total:.1%}  [{detail}]')
reach, allc = check_connectivity(levels[2])
print(f'顶层连通性: {reach}/{allc} 可达' + ('  OK' if reach == allc else '  !! 有孤立块组'))
print(f'写入 {OUT} ({OUT.stat().st_size} 字节)')
