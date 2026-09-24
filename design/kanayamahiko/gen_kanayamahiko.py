#!/usr/bin/env python3
"""Generate gensokyou:kanayamahiko_circle (金山彦命煅炉) v5 incremental pattern.

0~2 阶和风锻冶场。产出直接覆写 src/main/resources/data/gensokyou/rituals/。
坐标：四分之一规范形 (x>=0, z>=0)，核心固定在 (0,0,0)。
"""
import json
import os

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
OUT = os.path.join(ROOT, "src", "main", "resources", "data", "gensokyou",
                   "rituals", "kanayamahiko_circle.json")

PALETTE = {
    "C": "gensokyou:ritual_core",
    "P": "#gensokyou:ritual_pedestals",
    "0": "#gensokyou:ritual_stones",
    "1": "#gensokyou:ritual_stones_1_plus",
    "2": "#gensokyou:ritual_stones_2_plus",
    "a": "minecraft:deepslate_bricks",
    "c": "minecraft:deepslate_tiles",
    "d": "minecraft:stone_bricks",
    "e": "minecraft:chiseled_stone_bricks",
    "f": "minecraft:polished_andesite",
    "g": "minecraft:coal_block",
    "h": "minecraft:iron_bars",
    "j": "minecraft:lantern",
    "k": "minecraft:cauldron",
    "m": "minecraft:magma_block",
    "n": "minecraft:glowstone",
    "o": "minecraft:waxed_copper_block",
    "p": "minecraft:gold_block",
    "q": "minecraft:sea_lantern",
    "s": "minecraft:furnace",
    "t": "minecraft:blast_furnace",
    "u": "minecraft:deepslate_tile_stairs",
}

RITUAL_KEYS = {"0", "1", "2"}


def orbit(x, z):
    x, z = abs(x), abs(z)
    if x == 0 and z == 0:
        return (0, 0)
    if x == 0:
        return (0, z)
    if z == 0:
        return (0, x)
    return (x, z)


def sq(n):
    return {orbit(x, z) for x in range(n + 1) for z in range(n + 1)}


def border(n):
    return {orbit(x, z) for x in range(n + 1) for z in range(n + 1) if max(x, z) == n}


placed = {}   # 已完成的低级格位 pos -> (key, orient)
current = {}  # 当前级格位 pos -> (key, orient)，同级可覆盖（后者胜）
levels = []   # 每级最终格位列表 [(key, x, y, z, orient), ...]


def put(x, y, z, key, orient=None):
    cx, cz = orbit(x, z)
    pos = (cx, y, cz)
    if pos in current:            # 同级重复登记 -> 覆盖（后者胜）
        current[pos] = (key, orient)
        return
    if pos in placed:             # 与低级相交 -> 纯增量违规
        raise SystemExit("CONFLICT at %s: %s vs %s" % (pos, placed[pos], (key, orient)))
    current[pos] = (key, orient)


def region(reg, y, key, orient_fn=None):
    for (x, z) in sorted(reg):
        put(x, y, z, key, orient_fn(x, z) if orient_fn else None)


def roof_tier(n, y):
    """一环梯级坡顶：border(n) 台阶 + 角满块 + 内部 sq(n-1)-sq(1) 满块。"""
    region({(n, n)}, y, "c")
    for (x, z) in sorted(border(n) - {(n, n)}):
        put(x, y, z, "u", "west" if x == n else "north")
    region(sq(n - 1) - sq(1), y, "c")


def start():
    global current
    current = {}


def finish():
    global current
    levels.append([(k, p[0], p[1], p[2], o) for p, (k, o) in current.items()])
    placed.update(current)
    current = {}


# ============================== 0 阶 ==============================
start()
# 地面 y=-1
region(sq(3), -1, "a")
region(border(3), -1, "c")
region(sq(1), -1, "0")
region({(0, 2)}, -1, "0")
region({(2, 2)}, -1, "g")
region({(1, 2), (2, 1)}, -1, "f")
# y=0
put(0, 0, 0, "C")
region({(1, 2), (2, 1)}, 0, "P")   # 8 祭品台：避轴，留出四向入口走廊
region({(2, 2)}, 0, "k")           # 4 淬火槽（对角）
region({(3, 3)}, 0, "e")           # 4 角柱基
# y=1,2 四角灯柱
region({(3, 3)}, 1, "d")
region({(3, 3)}, 2, "j")
finish()

# ============================== 1 阶 ==============================
start()
# 地面外扩
region(sq(5) - sq(3), -1, "d")
region(border(5), -1, "c")
region({(0, 4), (4, 4), (2, 4), (4, 2)}, -1, "1")
region({(3, 4), (4, 3)}, -1, "n")
region({(1, 4), (4, 1)}, -1, "m")
region({(5, 5)}, -1, "o")
# 围墙（四面正中开门）
walls = border(5) - {(0, 5)}
region(walls, 0, "a")
region(walls, 1, "d")
region({(2, 5), (5, 2)}, 1, "h")
region(border(5), 2, "d")
region({(0, 5)}, 2, "e")
region({(5, 5)}, 2, "o")
# 坡顶
roof_tier(5, 3)
roof_tier(4, 4)
roof_tier(3, 5)
roof_tier(2, 6)
region({(2, 2)}, 7, "j")
# 中央烟囱：核心正上方 y=1,2 完全留空（火柱/光效通道），烟囱自屋顶 y=3 起
for yy in range(3, 8):
    region({(0, 1)}, yy, "h" if yy == 6 else "c")
    region({(1, 1)}, yy, "c")
# 新增祭品台（避轴，留出入口走廊）
region({(2, 4), (4, 2)}, 0, "P")
finish()

# ============================== 2 阶 ==============================
start()
# 地面外扩
region(sq(7) - sq(5), -1, "d")
region(border(7), -1, "c")
region({(0, 6), (6, 6), (2, 6), (6, 2), (4, 6), (6, 4), (3, 6), (6, 3)}, -1, "2")
region({(0, 7)}, -1, "p")
# 外回廊矮墙（四面留鸟居门洞）
gate = {(0, 7), (1, 7), (2, 7), (3, 7), (7, 1), (7, 2), (7, 3)}
para = border(7) - gate
region(para, 0, "d")
region(para, 1, "e")
region({(5, 7), (7, 5)}, 2, "n")
# 鸟居门
region({(3, 7), (7, 3)}, 0, "o")
region({(3, 7), (7, 3)}, 1, "o")
region({(3, 7), (7, 3)}, 2, "o")
region({(3, 7), (7, 3)}, 3, "o")
region({(0, 7), (1, 7), (2, 7), (3, 7), (7, 1), (7, 2), (7, 3)}, 4, "p")
# 新增祭品台（避开四向入口走廊 x/z∈[-1,1]）
region({(2, 6), (4, 6), (6, 2), (6, 4)}, 0, "P")
# 环廊装饰熔炉阵列（同样避开走廊）
region({(3, 6), (5, 6)}, 0, "s", lambda x, z: "south")
region({(6, 3), (6, 5)}, 0, "t", lambda x, z: "east")
# 环廊四角铜柱
region({(6, 6)}, 0, "o")
# 烟囱加高
for yy in range(8, 13):
    region({(0, 1)}, yy, "h" if yy == 11 else "c")
    region({(1, 1)}, yy, "c")
region(sq(1), 13, "p")
region({(0, 2)}, 13, "q")
finish()

# ============================== 输出与自检 ==============================
# 展开计数
def expanded_count(cells):
    n = 0
    for pos, (key, orient) in cells.items():
        n += 1 if pos[0] == 0 and pos[2] == 0 else 4
    return n


print("level | canon | expanded | ritual | ratio  | maxR2")
cum = {}
for i, lv in enumerate(levels):
    for (key, x, y, z, orient) in lv:
        cum[(x, y, z)] = (key, orient)
    exp = expanded_count(cum)
    rit = sum(1 for pos, (k, o) in cum.items()
              if k in RITUAL_KEYS or k.startswith("ritual_stone"))
    rit_exp = 0
    for pos, (k, o) in cum.items():
        if k in RITUAL_KEYS:
            rit_exp += 1 if pos[0] == 0 and pos[2] == 0 else 4
    maxr = max((x * x + z * z) for (x, y, z) in cum)
    print("%5d | %5d | %8d | %6d | %5.1f%% | %d"
          % (i, len(cum), exp, rit_exp, 100.0 * rit_exp / exp, maxr))

# 输出 JSON（每级差分）
out_levels = []
for i, lv in enumerate(levels):
    adds = []
    for (key, x, y, z, orient) in lv:
        if orient:
            adds.append([key, x, y, z, orient])
        else:
            adds.append([key, x, y, z])
    out_levels.append({"level": i, "adds": adds})

doc = {
    "id": "gensokyou:kanayamahiko_circle",
    "anchorKey": "C",
    "toggleable": True,
    "tiers": [0, 1, 2],
    "palette": PALETTE,
    "levels": out_levels,
}

os.makedirs(os.path.dirname(OUT), exist_ok=True)
with open(OUT, "w", encoding="utf-8") as f:
    json.dump(doc, f, ensure_ascii=False, indent=2)
    f.write("\n")

print("wrote", OUT)

# ---- 连通性自检（四重展开后 BFS 从核心出发）----
world = set()
for lv in levels:
    for (key, x, y, z, orient) in lv:
        if x == 0 and z == 0:
            world.add((0, y, 0))
        else:
            for (wx, wz) in [(x, z), (-z, x), (-x, -z), (z, -x)]:
                world.add((wx, y, wz))

seen = {(0, 0, 0)}
stack = [(0, 0, 0)]
while stack:
    cx, cy, cz = stack.pop()
    for (dx, dy, dz) in [(1, 0, 0), (-1, 0, 0), (0, 1, 0),
                         (0, -1, 0), (0, 0, 1), (0, 0, -1)]:
        np = (cx + dx, cy + dy, cz + dz)
        if np in world and np not in seen:
            seen.add(np)
            stack.append(np)

if len(seen) != len(world):
    print("WARN 悬浮/断开: unreachable", len(world) - len(seen),
          "of", len(world), "e.g.", sorted(world - seen)[:12])
else:
    print("connectivity OK: all", len(world), "cells reachable from core")

# ---- 核心外露自检：四面与顶面不得被占 ----
core_faces = [(0, 1, 0), (1, 0, 0), (-1, 0, 0), (0, 0, 1), (0, 0, -1)]
blocked = [n for n in core_faces if n in world]
if blocked:
    print("WARN 核心被包裹: blocked faces", blocked)
else:
    print("core exposure OK: 四面+顶面全空")

# ---- 入口走廊自检：四向门洞到核心外围 2 格高通道不得被占 ----
corridor = [(0, y, z) for z in range(2, 8) for y in (0, 1)] + \
           [(x, y, 0) for x in range(2, 8) for y in (0, 1)]
blocked_c = [c for c in corridor if c in world]
if blocked_c:
    print("WARN 入口被堵:", blocked_c)
else:
    print("entrance corridors OK: 四向 2 格高通道贯通到核心外围")
