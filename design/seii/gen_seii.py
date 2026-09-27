#!/usr/bin/env python3
"""Generate gensokyou:seii_circle (星移之仪) v5 incremental pattern.

三级跳阶仪式（level 1 / 3 / 5）：浑天仪・星盘主题，增幅核洗练用。
核心固定 (0,0,0)，四分之一规范形 (x>=0, z>=0)，产出直接覆写 resources/data/gensokyou/rituals/。
留空契约：核心四向 orbit(0,1)@y0、核心正上方 (0,0,1)、电容槽 (2,2)@y1、
          光柱通道 (0,0,1..6) 全空（洗练演出光柱由核心射向天极星）。
"""
import json
import math
import os

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
OUT = os.path.join(ROOT, "src", "main", "resources", "data", "gensokyou",
                   "rituals", "seii_circle.json")

LEVELS = [1, 3, 5]

PALETTE = {
    "C": "gensokyou:ritual_core",
    "P": "#gensokyou:ritual_pedestals",
    "1": "#gensokyou:ritual_stones_1_plus",
    "3": "#gensokyou:ritual_stones_3_plus",
    "5": "#gensokyou:ritual_stones_5_plus",
    "a": "minecraft:stone_bricks",
    "b": "minecraft:chiseled_stone_bricks",
    "c": "minecraft:polished_deepslate",
    "d": "minecraft:deepslate_bricks",
    "e": "minecraft:deepslate_tiles",
    "f": "minecraft:amethyst_block",
    "g": "minecraft:waxed_copper_block",
    "h": "minecraft:waxed_oxidized_copper",
    "i": "minecraft:sea_lantern",
    "j": "minecraft:lantern",
    "k": "minecraft:iron_bars",
    "m": "minecraft:chain",
    "o": "minecraft:stone_brick_wall",
}

RITUAL_KEYS = {"1", "3", "5"}
RESERVED = {(0, 0, 0, 1), (0, 0, 1, 0), (2, 2, 1, 0)}   # (x, z, y, 展开面数) 见下


def orbit(x, z):
    x, z = abs(x), abs(z)
    if x == 0:
        return (0, z)
    if z == 0:
        return (0, x)
    return (x, z)


def disc(r):
    return {orbit(x, z) for x in range(r + 1) for z in range(r + 1)
            if x * x + z * z <= r * r}


def ring(r):
    return disc(r) - disc(r - 1)


def hoop(r):
    """八角悬环：切角方环，四重展开后逐面 4 连通（整数圆环在轴向只对角相接，不可用）。"""
    out = set()
    for x in range(r + 1):
        for z in range(r + 1):
            if (max(x, z) == r and min(x, z) <= r - 1) or (x == r - 1 and z == r - 1):
                out.add(orbit(x, z))
    return out


def arc(r, ymax):
    """竖拱：y -> [(0,d), (0,d-1)] 的 2 格厚阶梯，逐面 4 连通。"""
    out = {}
    prev = r
    for y in range(0, ymax + 1):
        d = int(round(math.sqrt(max(0, r * r - y * y))))
        d = max(prev - 1, min(prev + 1, d))
        out[y] = d
        prev = d
    return out


placed = {}
current = {}
levels = []


def put(x, y, z, key, orient=None):
    cx, cz = orbit(x, z)
    pos = (cx, y, cz)
    if pos in current:
        current[pos] = (key, orient)
        return
    if pos in placed:
        raise SystemExit("CONFLICT at %s: %s vs %s" % (pos, placed[pos], (key, orient)))
    current[pos] = (key, orient)


def region(reg, y, key, orient_fn=None):
    for (x, z) in sorted(reg):
        put(x, y, z, key, orient_fn(x, z) if orient_fn else None)


def pillar(key, cells, y0, y1):
    for (x, z) in sorted(cells):
        for y in range(y0, y1 + 1):
            put(x, y, z, key)


def start():
    global current
    current = {}


def finish():
    global current
    levels.append([(k, p[0], p[1], p[2], o) for p, (k, o) in current.items()])
    placed.update(current)
    current = {}


# ============================== 1 阶「初星盘」 ==============================
start()
put(0, 0, 0, "C")
region(disc(2), -1, "a")
region(ring(3), -1, "a")
region(ring(4), -1, "b")                             # 八分刻环
region(ring(5), -1, "c")
region(ring(6), -1, "a")
region({(0, 6)}, -1, "b")                            # 四向门槛石（不用半砖：无物品形态识别不到）
put(0, -1, 0, "f")                                   # 紫水晶座，托起核心（须在地面之后写）
region({(0, 2), (2, 2)}, 0, "1")                    # 初轨内环
region({(3, 3)}, 0, "1")                            # 四角星位
region({(1, 2)}, 0, "P")                            # 4 祭品台
pillar("h", {(4, 4)}, 0, 2)                         # 四铜柱
region({(4, 4)}, 3, "i")                            # 四星灯
region({(3, 4), (4, 3)}, 0, "k")                    # 柱侧铁栅
region({(3, 4), (4, 3)}, 1, "k")
region({(2, 5), (5, 2)}, 0, "j")                    # 参道灯
finish()

# ============================== 3 阶「三轨星盘」 ==============================
start()
region(ring(7), -1, "g")                            # 铜环带
region(ring(8), -1, "d")
region(ring(9), -1, "c")
region(ring(10), -1, "e")
region(ring(11), -1, "a")
region(ring(12), -1, "d")
region({c for c in ring(9) if (c[0] + c[1]) % 2 == 0}, -1, "h")   # 铜星线
region({(0, 12)}, -1, "b")                           # 三阶门槛石
region(ring(7) - {(0, 7)}, 0, "3")                  # 中轨环廊，四向留口
pillar("h", {(0, 4), (0, 10)}, 0, 9)                # 星门双足
region({(0, d) for d in range(3, 12)}, 10, "h")     # 门楣
region({(0, 7)}, 11, "i")                           # 门楣顶星
for y, d in arc(9, 8).items():                      # 竖拱（浑天仪双环上半，2 格厚）
    put(0, y, d, "h")
    if d >= 1:
        put(0, y, d - 1, "h")
region({(0, 7)}, 9, "m")                            # 吊灯链
region({(0, 7)}, 8, "j")                            # 门楣吊灯
region({(1, 5), (5, 1)}, 0, "P")                    # +4 祭品台
finish()

# ============================== 5 阶「万象天球」 ==============================
start()
region(ring(13), -1, "d")
region(ring(14), -1, "g")
region(ring(15), -1, "e")
region(ring(16), -1, "c")
region(ring(17), -1, "a")
region(ring(18), -1, "a")
region(ring(19), -1, "d")
region(ring(20), -1, "c")
region(ring(21) - {(0, 21)}, -1, "o")               # 外沿矮墙，四向开口
region({(0, 21)}, -1, "b")                           # 五阶门槛石
region(ring(13) - {(0, 13)}, 0, "5")                # 外轨
pillar("d", {(14, 14)}, 0, 14)                      # 四角巨柱
region({(13, 14), (14, 13)}, 3, "g")                # 柱箍
region({(13, 14), (14, 13)}, 6, "g")
region({(13, 14), (14, 13)}, 9, "g")
region({(13, 14), (14, 13)}, 12, "g")
region({(14, 14)}, 15, "c")
region({(14, 14)}, 16, "f")
region({(14, 14)}, 17, "i")                         # 柱顶灯
pillar("h", {(0, 15), (0, 20)}, 0, 15)              # 外星门双足
region({(0, d) for d in range(13, 23)}, 16, "h")    # 外门楣
region({(0, 19)}, 17, "i")                          # 外门顶星
for y in range(7, 16):                               # 悬盘吊链
    put(0, y, 13, "m")
region(hoop(13), 6, "h")                            # 悬盘（y=6 八角大环）
region({(0, 13)}, 6, "i")                           # 悬盘四星
for t in range(1, 12):                               # 斜辐条阶梯：接天球与悬盘切角
    put(t, 6, t, "h")
    put(t + 1, 6, t, "h")
put(12, 6, 12, "h")
for y in range(2, 6):                                # 天球竖骨
    put(0, y, 2, "h")
region({(0, 1), (0, 2)}, 6, "h")                    # 天球顶环
region({(0, 1)}, 7, "h")                            # 冠环（承天极星）
put(0, 7, 0, "f")                                    # 天极星
put(0, 9, 6, "m")                                    # 上环吊链（吊自三阶门楣 y=10）
region(hoop(6), 8, "h")                             # 上环
region({(0, 6)}, 8, "i")
region({(1, 9), (9, 1)}, 0, "P")                    # +4 祭品台
finish()

# ============================== 输出与自检 ==============================
def expanded(cells):
    n = 0
    for (x, y, z) in cells:
        n += 1 if x == 0 and z == 0 else 4
    return n


print("level | canon | expanded | ritual | ratio | maxR")
cum = {}
for i, lv in enumerate(levels):
    for (key, x, y, z, _o) in lv:
        cum[(x, y, z)] = key
    exp = expanded(cum)
    rit = expanded({pos: k for pos, k in cum.items() if k in RITUAL_KEYS})
    maxr = max(x * x + z * z for (x, y, z) in cum)
    print("%5d | %5d | %8d | %6d | %5.1f%% | %d"
          % (LEVELS[i], len(cum), exp, rit, 100.0 * rit / exp, maxr))

out_levels = []
for i, lv in enumerate(levels):
    adds = [[key, x, y, z] if not o else [key, x, y, z, o]
            for (key, x, y, z, o) in lv]
    out_levels.append({"level": LEVELS[i], "adds": adds})

doc = {
    "id": "gensokyou:seii_circle",
    "anchorKey": "C",
    "toggleable": False,
    "tiers": LEVELS,
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
    for (key, x, y, z, _o) in lv:
        if x == 0 and z == 0:
            world.add((0, y, 0))
        else:
            for (wx, wz) in [(x, z), (-z, x), (-x, -z), (z, -x)]:
                world.add((wx, y, wz))
seen = {(0, 0, 0)}
stack = [(0, 0, 0)]
while stack:
    cx, cy, cz = stack.pop()
    for (dx, dy, dz) in [(1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1)]:
        np = (cx + dx, cy + dy, cz + dz)
        if np in world and np not in seen:
            seen.add(np)
            stack.append(np)
if len(seen) != len(world):
    print("WARN 悬浮/断开:", len(world) - len(seen), sorted(world - seen)[:12])
else:
    print("connectivity OK:", len(world), "cells reachable")

# ---- 核心外露 + 留空契约自检 ----
forbidden = {"core faces": [(0, 1, 0), (1, 0, 0), (-1, 0, 0), (0, 0, 1), (0, 0, -1)],
             "core side orbit(0,1)@y0": [(0, 0, 1), (0, 0, -1), (1, 0, 0), (-1, 0, 0)],
             "capacitor (2,2)@y1": [(2, 1, 2), (2, 1, -2), (-2, 1, 2), (-2, 1, -2)],
             "light shaft (0,0,1..6)": [(0, y, 0) for y in range(1, 7)]}
for label, cells in forbidden.items():
    bad = [c for c in cells if c in world]
    print(("WARN 占位 %s: %s" % (label, bad)) if bad else ("leave-open OK: " + label))
