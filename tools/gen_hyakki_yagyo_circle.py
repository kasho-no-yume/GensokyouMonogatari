#!/usr/bin/env python3
"""Programmatically generate hyakki_yagyo_circle.json (v5 incremental, four-fold symmetry).

Level blueprint (quarter-canonical form, coordinates are (|x|,|z|); y is relative to the anchor):
  L1  y-1 r2<=36 fully paved: center cross inlay / R1 ring(8..13) / polished band / stone-brick
         outer path with moss spots; y0 core + R1 corner/approach stones + 4 offering pedestals
         + amethyst inlay ring + 4 great stone lanterns(3,3) y0-5 + 4 small lanterns(5,1) y0-3
         + fern pots; y1 amethyst crystals beside the core
  L2  y-1 r2 37..80 floor (checker / R2 ring(50..56) / blackstone + crying obsidian / basalt);
         y0 4 torii posts(8,2) y0-3 + kei tablet + 4 offering pedestals(0,7) + 4 demon-blades
         (5,5) y0-3 + blade lamps + dead-bush planters flanking the four approaches;
         y1 4 diagonal arc balconies(4,4)(5,3)(3,5) on R2 bases -- the four torii axes stay
         open ground so the core is walkable; y2 torii crossbeam + balcony glass railing
         y3 balcony amethyst cap + banners + kei tablet
         y4 torii lintel
  L3  y-1 r2 81..144 floor (deepslate / R3 ring(101..110) / purpur / end-stone-brick rim);
         y-2 r2 137..169 first descending step; y-3 r2 162..196 second step;
         8 corner columns(4,2) y1-8; danmaku spiral climbing the four demon-blades from their
         amethyst tips (5,5)y4 inward to (4,3)y8, feeding the demon-eye ring y9
         (amethyst pupil / quartz white / purpur rim); 4 great banners(0,12) y0-5;
         blossom greenery
"""
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

OUT = (Path(__file__).resolve().parents[1] / 'src' / 'main' / 'resources'
       / 'data' / 'gensokyou' / 'rituals' / 'hyakki_yagyo_circle.json')

PALETTE = {
    'C': 'gensokyou:ritual_core',
    'R1': '#gensokyou:ritual_stones_1_plus',
    'R2': '#gensokyou:ritual_stones_2_plus',
    'R3': '#gensokyou:ritual_stones_3_plus',
    'P': '#gensokyou:ritual_pedestals',
    'am': 'minecraft:amethyst_block',
    'bl': 'minecraft:blackstone',
    'cb': 'minecraft:cobblestone',
    'cd': 'minecraft:chiseled_deepslate',
    'co': 'minecraft:crying_obsidian',
    'csb': 'minecraft:chiseled_stone_bricks',
    'db': 'minecraft:deepslate_bricks',
    'dol': 'minecraft:dark_oak_log',
    'dt': 'minecraft:deepslate_tiles',
    'esb': 'minecraft:end_stone_bricks',
    'mb': 'minecraft:moss_block',
    'mcb': 'minecraft:mossy_cobblestone',
    'msg': 'minecraft:magenta_stained_glass',
    'pb': 'minecraft:purple_banner',
    'pbl': 'minecraft:polished_basalt',
    'pet': 'minecraft:pink_petals',
    'pcs': 'minecraft:potted_cherry_sapling',
    'pdb': 'minecraft:potted_dead_bush',
    'pd': 'minecraft:polished_deepslate',
    'pf': 'minecraft:potted_fern',
    'psg': 'minecraft:purple_stained_glass',
    'pu': 'minecraft:purpur_block',
    'qb': 'minecraft:quartz_block',
    'sb': 'minecraft:stone_bricks',
    'sbw': 'minecraft:stone_brick_wall',
    'sl': 'minecraft:soul_lantern',
}

LEVELS = 3
PLAZA_R2 = 144
diff = [{} for _ in range(LEVELS)]
cum = [{} for _ in range(LEVELS)]


def canon(x, z):
    x, z = abs(x), abs(z)
    return (0, x) if z == 0 else (x, z)


def add(lv, key, x, y, z, orient=None):
    cx, cz = canon(x, z)
    if (cx, cz) == (0, 0) and y == 0 and key != 'C':
        raise SystemExit('(0,0,0) is reserved for the anchor C')
    cell = (cx, y, cz)
    if lv > 0 and cell in cum[lv - 1]:
        raise SystemExit(f'L{lv + 1} cross-level clash {cell}: already {cum[lv - 1][cell]}')
    old = diff[lv].get(cell)
    if old is not None and old[0] != key:
        raise SystemExit(f'L{lv + 1} in-level clash {cell}: {old[0]} vs {key}')
    diff[lv][cell] = (key, orient)
    cum[lv][cell] = (key, orient)


def begin(lv):
    if lv > 0:
        cum[lv] = dict(cum[lv - 1])


def disc(lv, y, r2min, r2max, maker, skip=frozenset()):
    for x in range(0, PLAZA_R2 + 1):
        for z in range(0, PLAZA_R2 + 1):
            r2 = x * x + z * z
            if not r2min <= r2 <= r2max:
                continue
            c = canon(x, z)
            if c in skip:
                continue
            key = maker(c)
            if key:
                add(lv, key, x, y, z)


def column(lv, key, cells, y0, y1):
    for (x, z) in cells:
        for y in range(y0, y1 + 1):
            add(lv, key, x, y, z)


def point(lv, key, cells, y):
    for (x, z) in cells:
        add(lv, key, x, y, z)


def both(a, b):
    return (a, b)


# ---------------------------------------------------------------- level 1
begin(0)
L1_FLOOR = {
    (0, 1): 'cd', (0, 2): 'cd',
    both(4, 3): 'cd', both(3, 4): 'cd',
    both(5, 2): 'mb', both(2, 5): 'mb',
    both(5, 3): 'mb', both(3, 5): 'mb',
}


def floor1(c):
    if c in L1_FLOOR:
        return L1_FLOOR[c]
    r2 = c[0] * c[0] + c[1] * c[1]
    if r2 == 0:
        return 'db'
    if 8 <= r2 <= 13:
        return 'R1'
    if 14 <= r2 <= 19:
        return 'pd'
    return 'sb'


disc(0, -1, 0, 36, floor1)
point(0, 'C', ((0, 0),), 0)
point(0, 'R1', ((1, 1),), 0)
point(0, 'am', ((1, 1),), 1)
point(0, 'R1', ((0, 2),), 0)
point(0, 'P', ((0, 3),), 0)
point(0, 'am', (both(4, 2), both(2, 4)), 0)
point(0, 'mcb', (both(4, 1), both(1, 4)), 0)
point(0, 'pf', (both(5, 2), both(2, 5)), 0)
for (key, y) in (('sb', 0), ('pd', 1), ('sbw', 2), ('sl', 3), ('csb', 4), ('am', 5)):
    point(0, key, ((3, 3),), y)
for (key, y) in (('cb', 0), ('mcb', 1), ('sl', 2), ('csb', 3)):
    point(0, key, (both(5, 1), both(1, 5)), y)

# ---------------------------------------------------------------- level 2
begin(1)
L2_FLOOR = {both(7, 3): 'co', both(3, 7): 'co',
            both(6, 5): 'co', both(5, 6): 'co'}


def floor2(c):
    if c in L2_FLOOR:
        return L2_FLOOR[c]
    r2 = c[0] * c[0] + c[1] * c[1]
    if 50 <= r2 <= 56:
        return 'R2'
    if r2 <= 49:
        return 'db' if (c[0] + c[1]) % 2 == 0 else 'dt'
    if r2 <= 72:
        return 'bl' if r2 <= 64 else 'pbl'
    return 'bl'


disc(1, -1, 37, 80, floor2)
for y in range(0, 4):
    point(1, 'dol', (both(8, 2), both(2, 8)), y)
point(1, 'am', ((0, 8),), 3)
point(1, 'P', ((0, 7),), 0)
# gallery: four diagonal arc balconies, the four torii axes stay open ground
BALCONY = ((4, 4), both(5, 3), both(3, 5))
point(1, 'R2', BALCONY, 0)
point(1, 'pd', BALCONY, 1)
point(1, 'psg', (both(5, 3), both(3, 5)), 2)
point(1, 'msg', ((4, 4),), 2)
point(1, 'am', BALCONY, 3)
point(1, 'pdb', (both(6, 1), both(1, 6)), 0)
for (key, y) in (('pbl', 0), ('bl', 1), ('pbl', 2), ('am', 3)):
    point(1, key, ((5, 5),), y)
point(1, 'sl', (both(5, 4), both(4, 5)), 0)
point(1, 'dol', ((0, 8), both(8, 1), both(1, 8)), 2)
for (x, z) in (both(8, 1), both(1, 8)):
    add(1, 'pb', x, 3, z, 'r0')
point(1, 'dol', ((0, 8), both(8, 1), both(1, 8), both(8, 2), both(2, 8)), 4)

# ---------------------------------------------------------------- level 3
begin(2)
L3_FLOOR = {both(10, 3): 'mb', both(3, 10): 'mb',
            both(11, 2): 'mb', both(2, 11): 'mb',
            both(11, 4): 'mb', both(4, 11): 'mb',
            (9, 9): 'am'}


def floor3(c):
    if c in L3_FLOOR:
        return L3_FLOOR[c]
    r2 = c[0] * c[0] + c[1] * c[1]
    if r2 <= 100:
        return 'db'
    if r2 <= 110:
        return 'R3'
    if r2 <= 130:
        return 'pu'
    return 'esb'


def step3(c):
    r2 = c[0] * c[0] + c[1] * c[1]
    if r2 == 145:
        return 'mb'
    return 'dt' if r2 < 162 else 'db'


disc(2, -1, 81, PLAZA_R2, floor3)
disc(2, -2, 137, 169, step3)
disc(2, -3, 162, 196, step3)
point(2, 'sl', (both(12, 1), both(1, 12)), -1)
for (key, y) in (('pbl', 1), ('pbl', 2), ('pbl', 3), ('pbl', 4), ('pbl', 5),
                 ('R3', 6), ('R3', 7), ('R3', 8)):
    point(2, key, (both(4, 2), both(2, 4)), y)
point(2, 'am', ((5, 5),), 4)
point(2, 'am', ((5, 5),), 5)
point(2, 'msg', (both(5, 4), both(4, 5)), 5)
point(2, 'msg', (both(5, 4), both(4, 5)), 6)
point(2, 'psg', (both(5, 3), both(3, 5)), 6)
point(2, 'psg', (both(5, 3), both(3, 5)), 7)
point(2, 'msg', (both(4, 3), both(3, 4)), 7)
point(2, 'msg', (both(4, 3), both(3, 4)), 8)
point(2, 'am', ((3, 3),), 9)
point(2, 'qb', (both(4, 1), both(1, 4), both(4, 2), both(2, 4)), 9)
point(2, 'pu', (both(4, 3), both(3, 4)), 9)
for (key, y) in (('dol', 0), ('dol', 1), ('dol', 2), ('pb', 3), ('dol', 4), ('am', 5)):
    point(2, key, ((0, 12),), y)
point(2, 'pcs', (both(11, 2), both(2, 11)), 0)
point(2, 'pet', (both(10, 3), both(3, 10)), 0)
point(2, 'pet', (both(11, 4), both(4, 11)), 0)


# ---------------------------------------------------------------- asserts
def expand(cx, cz):
    if cx == 0 and cz == 0:
        return [(0, 0)]
    if cx == 0 or cz == 0:
        d = cx or cz
        return [(d, 0), (-d, 0), (0, d), (0, -d)]
    return [(cx, cz), (-cx, cz), (cx, -cz), (-cx, -cz)]


def world(lv):
    out = {}
    for (cx, y, cz), (key, orient) in cum[lv].items():
        for (x, z) in expand(cx, cz):
            out[(x, y, z)] = key
    return out


prev = set()
FLOOR_R2 = (36, 80, PLAZA_R2)
for lv in range(LEVELS):
    cells = world(lv)
    if (0, 0, 0) not in cells:
        raise SystemExit(f'L{lv + 1} missing the anchor')
    if prev and (set(cells) - prev) & prev:
        raise SystemExit(f'L{lv + 1} re-declares a lower-level cell')
    seen = {(0, 0, 0)}
    stack = [(0, 0, 0)]
    while stack:
        x, y, z = stack.pop()
        for d in ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1)):
            n = (x + d[0], y + d[1], z + d[2])
            if n in cells and n not in seen:
                seen.add(n)
                stack.append(n)
    if seen != set(cells):
        lost = sorted(set(cells) - seen)
        raise SystemExit(f'L{lv + 1} has {len(lost)} floating/disconnected cells: '
                         f'{lost[:40]}')
    prev = seen
    floor = {p for p in cells if p[1] == -1}
    reach = FLOOR_R2[lv]
    for x in range(-reach, reach + 1):
        for z in range(-reach, reach + 1):
            if x * x + z * z <= reach and (x, -1, z) not in floor:
                raise SystemExit(f'L{lv + 1} floor hole at {x},{z}')
    for (x, y, z) in floor:
        if x * x + z * z > reach + 16:
            raise SystemExit(f'L{lv + 1} floor overhang at {x},{z}')
    stones = sum(1 for k in cells.values() if k.startswith('R'))
    print(f'level {lv + 1}: canonical {len(diff[lv])}, expanded {len(cells)}, '
          f'ritual_stone {stones} ({stones / len(cells):.1%})')
    if stones / len(cells) > 0.30:
        raise SystemExit(f'L{lv + 1} ritual stone share over 30%')

# ---------------------------------------------------------------- serialize
# palette keys must be single characters; readable names above map onto them
SINGLE = {
    'C': 'C', 'P': 'P', 'R1': '0', 'R2': '1', 'R3': '2',
    'am': 'a', 'bl': 'b', 'cb': 'c', 'cd': 'd', 'co': 'e', 'csb': 'f',
    'db': 'g', 'dol': 'h', 'dt': 'i', 'esb': 'j', 'mb': 'k', 'mcb': 'l',
    'msg': 'm', 'pb': 'n', 'pbl': 'o', 'pet': 'p', 'pcs': 'r', 'pdb': 's',
    'pd': 't', 'pf': 'u', 'psg': 'v', 'pu': 'w', 'qb': 'x', 'sb': 'y',
    'sbw': 'z', 'sl': 'Q',
}
assert set(SINGLE) == set(PALETTE) and all(len(v) == 1 for v in SINGLE.values())
assert len(set(SINGLE.values())) == len(SINGLE)

out = ['{', '  "id": "gensokyou:hyakki_yagyo_circle",', '  "anchorKey": "C",',
       '  "toggleable": true,', '  "tiers": [1, 2, 3],', '  "palette": {']
items = sorted(PALETTE.items(), key=lambda kv: (SINGLE[kv[0]] != SINGLE[kv[0]].upper(),
                                               SINGLE[kv[0]]))
for i, (k, v) in enumerate(items):
    tail = '' if i == len(items) - 1 else ','
    out.append(f'    "{SINGLE[k]}": "{v}"{tail}')
out.append('  },')
out.append('  "levels": [')
for lv in range(LEVELS):
    out += ['    {', f'      "level": {lv + 1},', '      "adds": [']
    for (cell, (key, orient)) in sorted(diff[lv].items(),
                                         key=lambda kv: (kv[0][1], kv[0][2], kv[0][0])):
        tail = '' if orient is None else f', "{orient}"'
        out.append(f'        ["{SINGLE[key]}",{cell[0]},{cell[1]},{cell[2]}{tail}],')
    out[-1] = out[-1][:-1]
    out.append('      ]')
    out.append('    }' + (',' if lv < LEVELS - 1 else ''))
out.append('  ]')
out.append('}')
OUT.write_text('\n'.join(out) + '\n', encoding='utf-8')
print(f'wrote {OUT} ({OUT.stat().st_size} bytes)')
