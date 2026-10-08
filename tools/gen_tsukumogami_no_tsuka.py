# -*- coding: utf-8 -*-
"""Generate data/gensokyou/rituals/tsukumogami_no_tsuka.json (v5 incremental).

Fuusou no Tsuka (Tumulus of the Tsukumogami) -- tiers 0/1/2.
A stepped earthen tumulus; core atop; offering pedestals on the diagonal.
Canonical quarter (x>=0, z>=0); put() canonicalises the axis orbit and
self-checks for intra-level cell conflicts.
"""
import os
import json

OUT = os.path.join("src", "main", "resources", "data", "gensokyou",
                   "rituals", "tsukumogami_no_tsuka.json")

data = {0: {}, 1: {}, 2: {}}


def canon(x, z):
    # the axis orbit (d,0) and (0,d) are the SAME orbit: collapse to (0,d)
    if z == 0:
        return (0, x)
    if x == 0:
        return (0, z)
    return (x, z)


def put(lv, key, x, y, z):
    if x < 0 or z < 0:
        raise Exception("negative coord %d,%d" % (x, z))
    cx, cz = canon(x, z)
    p = (cx, y, cz)
    d = data[lv]
    if p in d:
        if d[p] != key:
            raise Exception("L%d conflict at %s: %s vs %s" % (lv, p, d[p], key))
        return
    d[p] = key


def earth(x, z):
    # visible earth terrace variety (symmetric in x,z)
    if x == z and x > 0:
        return 'q'          # terracotta band on the diagonals
    t = (x + z) % 3
    if t == 0:
        return 'k'          # packed mud
    if t == 1:
        return 'j'          # coarse dirt
    return 'l'              # moss block


def top_mat(x, z):
    m = max(x, z)
    if m <= 1:
        return '0'          # ritual_stones dais (0..5 tag)
    if x == 2 and z == 2:
        return 'c'          # mossy corner
    if x == 0 or z == 0:
        return 'a'          # stone brick rim on the axes
    return 'b'              # cracked edge


# ---------------------------------------------------------------- level 0
# earthen tumulus base: 7x7 at y-2
for x in range(4):
    for z in range(4):
        put(0, earth(x, z), x, -2, z)

# stone top platform: 5x5 at y-1
for x in range(3):
    for z in range(3):
        put(0, top_mat(x, z), x, -1, z)

# core
put(0, 'C', 0, 0, 0)

# top-platform offerings (y0)
put(0, 'o', 1, 0, 1)        # potted_fern (1,1)
put(0, 'n', 2, 0, 1)        # cauldron (2,1) -- 受器
put(0, 'd', 1, 0, 2)        # chiseled_stone_bricks (1,2)

# terrace r=3 offerings (y-1)
put(0, 's', 3, -1, 1)       # lantern (3,1)
put(0, 'm', 3, -1, 2)       # decorated_pot (3,2)
put(0, 'p', 1, -1, 3)       # potted_dead_bush (1,3)

# 4 offering pedestals on the diagonals (3,3)
put(0, 'P', 3, -1, 3)

# ---------------------------------------------------------------- level 1
# terrace y-3 9x9: outer ring = ritual_stones (1+), interior earth
for x in range(5):
    for z in range(5):
        key = '1' if max(x, z) == 4 else earth(x, z)
        put(1, key, x, -3, z)

# +4 pedestals at (4,4)
put(1, 'P', 4, -2, 4)

# four corner pillars (2,2), y0..y2
for y in range(0, 3):
    put(1, 'g', 2, y, 2)

# top frame beams at y2: soul_lantern on axes, planks on edges
put(1, 'v', 0, 2, 2)        # soul_lantern (0,2)
put(1, 'h', 2, 2, 1)        # dark_oak_planks (2,1)
put(1, 'h', 1, 2, 2)        # dark_oak_planks (1,2)

# old vessels arrayed on the y-3 terrace (surface y-2)
put(1, 'v', 4, -2, 1)       # soul_lantern (4,1)
put(1, 'y', 4, -2, 2)       # anvil (4,2)
put(1, 'z', 4, -2, 3)       # grindstone (4,3)
put(1, 'A', 2, -2, 4)       # smithing_table (2,4)
put(1, 'n', 1, -2, 4)       # cauldron (1,4)

# ---------------------------------------------------------------- level 2
# terrace y-4 11x11: outer ring = ritual_stones (2+), interior earth
for x in range(6):
    for z in range(6):
        key = '2' if max(x, z) == 5 else earth(x, z)
        put(2, key, x, -4, z)

# +4 pedestals at (5,5)
put(2, 'P', 5, -3, 5)

# pillars extended
put(2, 'g', 2, 3, 2)
put(2, 'g', 2, 4, 2)

# upper frame at y4 (planks) + eave slab ring r=3
put(2, 'h', 0, 4, 2)
put(2, 'h', 2, 4, 1)
put(2, 'h', 1, 4, 2)
for x in range(4):
    for z in range(4):
        if max(x, z) == 3:
            put(2, 'f', x, 4, z)   # stone_brick_slab

# crown ring r=2 at y5 (oculus over the core left open)
put(2, 'B', 2, 5, 1)        # gilded_blackstone (2,1)
put(2, 'B', 1, 5, 2)        # gilded_blackstone (1,2)
put(2, 'w', 2, 5, 2)        # amethyst finial (2,2)
put(2, 'E', 0, 5, 2)        # crying_obsidian (0,2)

# glowing glass band r=2 at y3
put(2, 'x', 0, 3, 2)        # purple_stained_glass (0,2)
put(2, 'x', 2, 3, 1)        # purple_stained_glass (2,1)
put(2, 'x', 1, 3, 2)        # purple_stained_glass (1,2)

# offerings on the y-4 terrace (surface y-3)
put(2, 'w', 5, -3, 2)       # amethyst_block (5,2)
put(2, 'E', 5, -3, 3)       # crying_obsidian (5,3)
put(2, 'm', 5, -3, 4)       # decorated_pot (5,4)
put(2, 'F', 2, -3, 5)       # quartz_block (2,5)
put(2, 's', 1, -3, 5)       # lantern (1,5)

# ---------------------------------------------------------------- palette
BLOCK = {
    'C': 'gensokyou:ritual_core',
    '0': '#gensokyou:ritual_stones',
    '1': '#gensokyou:ritual_stones_1_plus',
    '2': '#gensokyou:ritual_stones_2_plus',
    'P': '#gensokyou:ritual_pedestals',
    'a': 'minecraft:stone_bricks',
    'b': 'minecraft:cracked_stone_bricks',
    'c': 'minecraft:mossy_stone_bricks',
    'd': 'minecraft:chiseled_stone_bricks',
    'f': 'minecraft:stone_brick_slab',
    'g': 'minecraft:dark_oak_log',
    'h': 'minecraft:dark_oak_planks',
    'j': 'minecraft:coarse_dirt',
    'k': 'minecraft:packed_mud',
    'l': 'minecraft:moss_block',
    'm': 'minecraft:decorated_pot',
    'n': 'minecraft:cauldron',
    'o': 'minecraft:potted_fern',
    'p': 'minecraft:potted_dead_bush',
    'q': 'minecraft:terracotta',
    's': 'minecraft:lantern',
    'v': 'minecraft:soul_lantern',
    'w': 'minecraft:amethyst_block',
    'x': 'minecraft:purple_stained_glass',
    'y': 'minecraft:anvil',
    'z': 'minecraft:grindstone',
    'A': 'minecraft:smithing_table',
    'B': 'minecraft:gilded_blackstone',
    'E': 'minecraft:crying_obsidian',
    'F': 'minecraft:quartz_block',
}

# sanity: cross-level disjoint + core location
seen = {}
for lv in (0, 1, 2):
    for (cx, y, cz), key in data[lv].items():
        if (cx, y, cz) in seen:
            raise Exception("cell %s in L%d and L%d" % ((cx, y, cz), seen[(cx, y, cz)], lv))
        seen[(cx, y, cz)] = lv
assert data[0].get((0, 0, 0)) == 'C', "core must be at (0,0,0) in level 0"
for lv in (1, 2):
    assert 'C' not in data[lv].values(), "core only in level 0"

used = set()
for lv in (0, 1, 2):
    used.update(data[lv].values())
unused = set(BLOCK) - used
if unused:
    print("unused palette keys dropped:", sorted(unused))

palette = {k: BLOCK[k] for k in BLOCK if k in used}

lines = []
lines.append('{')
lines.append('  "id": "gensokyou:tsukumogami_no_tsuka",')
lines.append('  "anchorKey": "C",')
lines.append('  "toggleable": true,')
lines.append('  "tiers": [')
lines.append('    0,')
lines.append('    1,')
lines.append('    2')
lines.append('  ],')
lines.append('  "palette": {')
items = sorted(palette.items())
for i, (k, v) in enumerate(items):
    comma = ',' if i < len(items) - 1 else ''
    lines.append('    "%s": "%s"%s' % (k, v, comma))
lines.append('  },')
lines.append('  "levels": [')
for li, lv in enumerate((0, 1, 2)):
    arr = data[lv]
    keys = sorted(arr.keys(), key=lambda p: (p[1], p[0], p[2]))
    comma = ',' if li < 2 else ''
    lines.append('    { "level": %d, "adds": [' % lv)
    for j, p in enumerate(keys):
        cx, y, cz = p
        c2 = ',' if j < len(keys) - 1 else ''
        lines.append('      ["%s",%d,%d,%d]%s' % (arr[p], cx, y, cz, c2))
    lines.append('    ] }%s' % comma)
lines.append('  ]')
lines.append('}')
text = '\n'.join(lines) + '\n'

os.makedirs(os.path.dirname(OUT), exist_ok=True)
with open(OUT, 'w', encoding='utf-8') as f:
    f.write(text)

# report
for lv in (0, 1, 2):
    total = 0
    for (cx, y, cz) in data[lv]:
        total += 1 if (cx == 0 and cz == 0) else (4 if cx == 0 or cz == 0 else 4)
    print("L%d canon=%d expanded=%d" % (lv, len(data[lv]), total))
print("written", OUT)
