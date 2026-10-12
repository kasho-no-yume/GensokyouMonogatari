#!/usr/bin/env python3
"""镇魂返照之仪 (chinkon_hanshou_circle) v5 incremental pattern generator.

Blueprint (canon quarter, x>=0, z>=0; core fixed at (0,0,0)):
  L1 封魂龛: stone platform (r2<=26) + inner ritual-stone rune ring (r2 9..13),
             4 corner posts (glass band + chain + soul-lantern), 4 chain stakes
             flanking the axes, 4 pedestals in the diagonal quadrants.
  L2 镇魂环: floor to r2<=60, outer rune ring (r2 50..56) of tier-2 stones with
             amethyst inlay, 4-arcade enclosure wall (r2 34..40, 4 axis doorways,
             purple/magenta glass band), 4 crying-obsidian pylons (5,5),
             banners + lantern posts.
  L3 断魂楼: floor to r2<=110, upper wall band y3/y4 (same r2 band + doorways),
             ring roof y5 (r2 17..40, oculus above core), chain curtain hanging
             from the roof inner edge (r2 18..25, y1..4, axis gaps),
             4 corner turrets (7,7), 4 soul-fire braziers.
  L4 返星顶: floor to r2<=170, outer gate posts (9,9), stepped crown above the
             roof (purpur / end-stone-brick tiers, amethyst inlay).
  L5 返魂神域: floor to r2<=240, four torii-like gates on the axes (dark oak),
             banners, crown cap.

Self-checks (design invariants, not enforced by the offline validator):
  * floor coverage at y=-1 (no holes inside the footprint)
  * ritual-stone family ratio <= 30% per cumulative level (pedestals excluded
    from the numerator but counted in the denominator)
  * player BFS reachability: some standing cell adjacent to the core is connected
    to a standing cell near the platform rim, every level
"""
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import validate_ritual_pattern as v5  # noqa: E402

OUT = (Path(__file__).resolve().parents[1] / 'src' / 'main' / 'resources'
       / 'data' / 'gensokyou' / 'rituals' / 'chinkon_hanshou_circle.json')

FLOOR_Y = -1

PALETTE = {
    'C': 'gensokyou:ritual_core',
    '1': '#gensokyou:ritual_stones_1_plus',
    '2': '#gensokyou:ritual_stones_2_plus',
    '3': '#gensokyou:ritual_stones_3_plus',
    '4': '#gensokyou:ritual_stones_4_plus',
    '5': '#gensokyou:ritual_stones_5_plus',
    'P': '#gensokyou:ritual_pedestals',
    # shrine masonry
    'a': 'minecraft:stone_bricks',
    'b': 'minecraft:chiseled_stone_bricks',
    'd': 'minecraft:deepslate_bricks',
    'e': 'minecraft:deepslate_tiles',
    'f': 'minecraft:polished_deepslate',
    # binding
    'G': 'minecraft:chain',
    # cold fire / light
    'L': 'minecraft:soul_lantern',
    # glass
    'S': 'minecraft:purple_stained_glass',
    'T': 'minecraft:magenta_stained_glass',
    # star / accents
    'X': 'minecraft:amethyst_block',
    'B': 'minecraft:purple_banner',
    'O': 'minecraft:crying_obsidian',
    'q': 'minecraft:quartz_block',
    # nether / end (allowed from tier 2 onwards)
    'U': 'minecraft:purpur_block',
    'V': 'minecraft:purpur_pillar',
    'W': 'minecraft:end_stone_bricks',
    # ground that never degrades
    'x': 'minecraft:moss_block',
    'y': 'minecraft:coarse_dirt',
    # gate wood
    'k': 'minecraft:dark_oak_log',
}

STONE_KEYS = {'1', '2', '3', '4', '5'}  # ritual-stone family (numerator)

levels = [{}, {}, {}, {}, {}]


def canon(x, z):
    x, z = abs(x), abs(z)
    return (0, x) if z == 0 else (x, z)


def put(lv, key, x, y, z, over=False):
    cx, cz = canon(x, z)
    if cx == 0 and cz == 0 and y == 0 and key != 'C':
        raise SystemExit(f'原点仅限核心: level {lv + 1} key={key} y={y}')
    cell = (cx, y, cz)
    old = levels[lv].get(cell)
    if old and old != key and not over:
        raise SystemExit(f'冲突 level {lv + 1} {cell}: {old} vs {key}')
    levels[lv][cell] = key


def slab(lv, y, r2min, r2max, skip, maker, over=False):
    for x in range(0, 22):
        for z in range(0, 22):
            r2 = x * x + z * z
            if r2 == 0 or not r2min <= r2 <= r2max:
                continue
            c = canon(x, z)
            if c in skip:
                continue
            key = maker(c)
            if key:
                put(lv, key, x, y, z, over=over)


def pillar(lv, key, x, z, y0, y1):
    for y in range(y0, y1 + 1):
        put(lv, key, x, y, z)


def band(lv, y, r2min, r2max, maker, door=True):
    """Ring band on walls; door=True leaves the four axis cells open."""
    for x in range(0, 22):
        for z in range(0, 22):
            r2 = x * x + z * z
            if not r2min <= r2 <= r2max:
                continue
            if door and (x == 0 or z == 0):
                continue
            c = canon(x, z)
            put(lv, maker(c), x, y, z)


# ---------------------------------------------------------------- L1 封魂龛
levels[0] = {}
slab(0, FLOOR_Y, 1, 26, set(),
     lambda c: 'e' if (c[0] * c[0] + c[1] * c[1]) >= 21
     else ('x' if (c[0] * 3 + c[1] * 5) % 7 == 0 else 'a'))
put(0, 'a', 0, FLOOR_Y, 0)                                   # floor under core
slab(0, FLOOR_Y, 9, 13, set(),
     lambda c: '1' if (c[0] + c[1]) % 2 == 0 else 'X', over=True)   # inner rune ring
# corner posts: masonry / glass band / chain / cold flame
pillar(0, 'b', 3, 3, 0, 0)
put(0, 'S', 3, 1, 3)
put(0, 'b', 3, 2, 3)
put(0, 'G', 3, 3, 3)
put(0, 'L', 3, 4, 3)
# chain stakes flanking the approach
for (sx, sz) in ((1, 3), (3, 1)):
    put(0, 'G', sx, 0, sz)
    put(0, 'G', sx, 1, sz)
    put(0, 'L', sx, 2, sz)
# pedestals in the diagonal quadrants (keep the axes walkable)
put(0, 'P', 2, 0, 2)

# ---------------------------------------------------------------- L2 镇魂环
levels[1] = dict(levels[0])
slab(1, FLOOR_Y, 27, 60, set(),
     lambda c: 'd' if (c[0] * c[0] + c[1] * c[1]) >= 56
     else ('x' if (c[0] * 3 + c[1] * 5) % 7 == 0 else 'e'))
slab(1, FLOOR_Y, 50, 54, set(),
     lambda c: '2' if (c[0] + c[1]) % 2 == 0 else 'X', over=True)       # outer rune ring
band(1, 0, 34, 40, lambda c: 'a')
band(1, 1, 34, 40, lambda c: 'S' if (c[0] + c[1]) % 2 == 0 else 'T')
band(1, 2, 34, 40, lambda c: 'a')
# crying-obsidian pylons
pillar(1, 'f', 5, 5, 0, 0)
pillar(1, 'O', 5, 5, 1, 2)
put(1, 'L', 5, 3, 5)
put(1, 'q', 5, 4, 5)
# banners and lantern posts
put(1, 'B', 4, 0, 4)
for (sx, sz) in ((2, 5), (5, 2)):
    put(1, 'b', sx, 0, sz)
    put(1, 'L', sx, 1, sz)

# ---------------------------------------------------------------- L3 断魂楼
levels[2] = dict(levels[1])
slab(2, FLOOR_Y, 61, 110, set(),
     lambda c: 'd' if (c[0] * c[0] + c[1] * c[1]) >= 100
     else ('x' if (c[0] * 3 + c[1] * 5) % 7 == 0 else 'e'))
band(2, 3, 34, 40, lambda c: 'b')
band(2, 4, 34, 40, lambda c: 'S' if (c[0] + c[1]) % 2 == 0 else 'T')
# ring roof with an oculus above the core
slab(2, 5, 17, 40, set(), lambda c: 'a')
# chain curtain hanging from the roof inner edge, axis gaps kept open
for _y in (1, 2, 3, 4):
    for _x in range(0, 22):
        for _z in range(0, 22):
            _r2 = _x * _x + _z * _z
            if not 18 <= _r2 <= 25 or _x == 0 or _z == 0:
                continue
            if canon(_x, _z) == (3, 3):      # corner-post column
                continue
            put(2, 'G', _x, _y, _z)
# corner turrets
pillar(2, 'd', 7, 7, 0, 1)
put(2, 'e', 7, 2, 7)
put(2, 'S', 7, 3, 7)
put(2, 'e', 7, 4, 7)
put(2, 'a', 7, 5, 7)
put(2, 'L', 7, 6, 7)
# soul-fire braziers
for (sx, sz) in ((3, 9), (9, 3)):
    put(2, 'b', sx, 0, sz)
    put(2, 'L', sx, 1, sz)
# outer star-orbit rune ring (tier-3 stones)
slab(2, FLOOR_Y, 90, 102, set(),
     lambda c: '3' if (c[0] + c[1]) % 2 == 0 else 'X', over=True)

# ---------------------------------------------------------------- L4 返星顶
levels[3] = dict(levels[2])
slab(3, FLOOR_Y, 111, 170, set(),
     lambda c: 'd' if (c[0] * c[0] + c[1] * c[1]) >= 160
     else ('x' if (c[0] * 3 + c[1] * 5) % 7 == 0 else 'e'))
# outer gate posts
pillar(3, 'd', 9, 9, 0, 0)
pillar(3, 'O', 9, 9, 1, 3)
put(3, 'e', 9, 4, 9)
put(3, 'L', 9, 5, 9)
put(3, 'q', 9, 6, 9)
# stepped crown above the roof (the far shore condenses the star shards)
slab(3, 6, 17, 32, set(),
     lambda c: 'X' if (c[0] + c[1]) % 3 == 0 else 'U')
slab(3, 7, 21, 32, set(),
     lambda c: 'S' if (c[0] + c[1]) % 2 == 0 else 'W')
slab(3, 8, 25, 32, set(), lambda c: 'U')
# outer star-orbit rune ring (tier-4 stones)
slab(3, FLOOR_Y, 150, 162, set(),
     lambda c: '4' if (c[0] + c[1]) % 2 == 0 else 'X', over=True)

# ---------------------------------------------------------------- L5 返魂神域
levels[4] = dict(levels[3])
slab(4, FLOOR_Y, 171, 288, set(),
     lambda c: 'd' if (c[0] * c[0] + c[1] * c[1]) >= 225
     else ('y' if (c[0] * 3 + c[1] * 5) % 7 == 0 else 'e'))
# outer precinct rail (axis doorways kept open)
band(4, 0, 260, 288, lambda c: 'd', door=True)
band(4, 1, 260, 288, lambda c: 'S' if (c[0] + c[1]) % 2 == 0 else 'd', door=True)
# periodic lantern pillars on the outer plaza
for (px, pz) in ((9, 13), (13, 9), (5, 15), (15, 5)):
    put(4, 'd', px, 0, pz)
    put(4, 'e', px, 1, pz)
    put(4, 'L', px, 2, pz)
# torii-like gates on the four axes (posts off-axis, lintel spans the path)
for (px, pz) in ((2, 14), (14, 2)):
    pillar(4, 'k', px, pz, 0, 5)
    put(4, 'L', px, 6, pz)
for (lx, lz) in ((0, 14), (1, 14), (14, 0), (14, 1)):
    put(4, 'k', lx, 5, lz)
    put(4, 'k', lx, 6, lz)
put(4, 'L', 0, 4, 14)
# banners on the outer approach
put(4, 'B', 4, 0, 13)
put(4, 'B', 13, 0, 4)
# crown cap
slab(4, 9, 28, 32, set(), lambda c: 'W')
slab(4, 10, 31, 32, set(), lambda c: 'V')
# outer star-orbit rune ring (tier-5 stones)
slab(4, FLOOR_Y, 246, 258, set(),
     lambda c: '5' if (c[0] + c[1]) % 2 == 0 else 'X', over=True)

# core at (0,0,0), lowest level only
levels[0][(0, 0, 0)] = 'C'

doc = {
    'id': 'gensokyou:chinkon_hanshou_circle',
    'anchorKey': 'C',
    'toggleable': False,
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


# ------------------------------------------------------------- self-checks
def expanded(lv):
    out = {}
    for (x, y, z), k in levels[lv].items():
        for k2, x2, y2, z2, _o in v5.expand_entry(k, x, y, z, None):
            out[(x2, y2, z2)] = k2
    return out


def check_levels():
    ok = True
    for lv in range(5):
        cells = expanded(lv)
        total = len(cells)
        stones = sum(1 for k in cells.values() if k in STONE_KEYS)
        ratio = stones / total if total else 0.0
        # floor coverage: the platform is a filled disk of radius r at y=FLOOR_Y
        fr2 = max((x * x + z * z for (x, y, z) in cells if y == FLOOR_Y), default=0)
        r = int(fr2 ** 0.5)
        holes = 0
        for x in range(-r - 1, r + 2):
            for z in range(-r - 1, r + 2):
                if x * x + z * z <= fr2 and (x, FLOOR_Y, z) not in cells:
                    holes += 1
        # player BFS
        def stand(p):
            x, y, z = p
            return ((x, y, z) not in cells and (x, y + 1, z) not in cells
                    and (x, y - 1, z) in cells)
        start = [p for p in ((1, 0, 0), (-1, 0, 0), (0, 0, 1), (0, 0, -1))
                 if stand(p)]
        comp = set(start)
        frontier = list(start)
        while frontier:
            x, y, z = frontier.pop()
            for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                for dy in (-1, 0, 1):
                    q = (x + dx, y + dy, z + dz)
                    if q not in comp and stand(q):
                        comp.add(q)
                        frontier.append(q)
        rim = [p for p in comp if max(abs(p[0]), abs(p[2])) >= r - 1]
        reach = bool(start) and bool(rim)
        flag = '' if (ratio <= 0.30 and holes == 0 and reach) else '  <-- CHECK'
        ok = ok and ratio <= 0.30 and holes == 0 and reach
        print(f'level {lv + 1}: 展开 {total} 格  石族 {stones} ({ratio:.1%})  '
              f'地坪半径 {r}  地坪洞 {holes}  BFS起{len(start)} 连通{len(comp)} '
              f'边缘可达 {reach}{flag}')
        if holes:
            miss = [(x, z) for x in range(-r, r + 1) for z in range(-r, r + 1)
                    if (x, FLOOR_Y, z) not in cells][:8]
            print(f'   缺失地坪样例: {miss}')
    return ok


if not check_levels():
    print('自检未通过，未落盘（修正后重跑）')
    sys.exit(2)

OUT.write_text(text, encoding='utf-8')
for i, cells in enumerate(levels):
    by_key = {}
    for k in cells.values():
        by_key[k] = by_key.get(k, 0) + 1
    detail = ', '.join(f'{k}x{v}' for k, v in sorted(by_key.items()))
    print(f'level {i + 1}: 累积 {len(cells)} 规范格（新增 {len(adds_per_level[i])} 条目）[{detail}]')
print(f'写入 {OUT} ({OUT.stat().st_size} 字节)')
