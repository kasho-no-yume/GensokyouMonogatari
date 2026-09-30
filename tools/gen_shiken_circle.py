#!/usr/bin/env python3
"""生成 思兼神封（附魔仪式）pattern：v5 逐级增量，四重对称四分之一规范形。

祭品台语义（用户定案）：核心槽放装备（主物品），4 只祭品台放附魔书依次打上；
同名书可叠加（4 槽 = 4 条词条、每条可叠强度），故 L1/L2/L3 恒为 4 台，升阶只加结构。
四台正上方外侧各立一盏「封印灯」，随阶数同步长高（灯位与台位一一对应）。

层级蓝图（y 相对锚点核心，r∞ = max(|x|,|z|)）：
  L1 封坛    y-1 封坛地坪 r∞<=7（八向铜地脉自核心辐射、r∞4 骨线用 1 阶仪式石、中心封石承核）
             y-2 参道基    y-1 参道 (0,8)
             y0  夯土墙 r∞7（留四向门位）+ 祭品台 (0,3) + 封印柱础 (4,4) + 封印灯柱础 (0,4)
             y1 封印柱 (4,4) + 灯杆 (0,4)      y2 封印灯 (0,4)
  L2 封门回廊 y-1 前坪 r∞8 + 铜环 r∞5
             y0  前坪 + 门柱 (1,8) + 四角石灯 (1,1) + 相缪碑 (6,6)
             y1  门柱 + 灯 + 碑 + 墙身 r∞7
             y2  门额 (0,8)(1,8) + 廊顶 r∞6(石) r∞7(铜瓦)
             y3  门额枋 (0,8)
  L3 相缪殿  y-1 前庭 r∞9
             y0  前庭 + 前庭矮墙 r∞9 + 殿柱 (1,1) + 影壁 (2,3)(3,2) + 廊柱 r∞6
             y1  殿柱 + 影壁 + 廊柱头
             y2  影壁压顶     y3 殿柱     y4 殿柱头
             y5  殿檐 r∞<=2（留 (0,0) 火柱口，3 阶仪式石）+ 柱林续 (4,4)
             y6  顶心灵光 (1,1) + 柱林 (4,4)    y7 柱林灯冠 (4,4)
"""
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import validate_ritual_pattern as v5  # noqa: E402

OUT = (Path(__file__).resolve().parents[1] / 'src' / 'main' / 'resources'
       / 'data' / 'gensokyou' / 'rituals' / 'shiken_circle.json')

PALETTE = {
    'C': 'gensokyou:ritual_core',
    '1': '#gensokyou:ritual_stones_1_plus',
    '2': '#gensokyou:ritual_stones_2_plus',
    '3': '#gensokyou:ritual_stones_3_plus',
    'P': '#gensokyou:ritual_pedestals',
    'a': 'minecraft:stone_bricks',
    'b': 'minecraft:polished_andesite',
    'c': 'minecraft:chiseled_stone_bricks',
    'd': 'minecraft:mossy_stone_bricks',
    'e': 'minecraft:deepslate_bricks',
    'f': 'minecraft:oxidized_copper',
    'g': 'minecraft:moss_block',
    'h': 'minecraft:smooth_stone',
    'i': 'minecraft:stone_brick_wall',
    'j': 'minecraft:iron_bars',
    'k': 'minecraft:chain',
    'l': 'minecraft:sea_lantern',
    'm': 'minecraft:gold_block',
    'n': 'minecraft:dark_oak_log',
    'o': 'minecraft:amethyst_block',
}

R = 10
levels = [{}, {}, {}]
AXIS = lambda d: (0, d)  # noqa: E731


def canon(x, z):
    x, z = abs(x), abs(z)
    return (0, x) if z == 0 else (x, z)


def put(lv, key, x, y, z):
    c = canon(x, z)
    if c == (0, 0) and y == 0:
        raise SystemExit('(0,0,0) 归锚点 C 独占')
    cell = (c[0], y, c[1])
    old = levels[lv].get(cell)
    if old and old != key:
        raise SystemExit(f'冲突 level {lv + 1} {cell}: {old} vs {key}')
    levels[lv][cell] = key


def ring(lv, y, lo, hi, maker, skip=()):
    for x in range(0, R + 1):
        for z in range(0, R + 1):
            c = canon(x, z)
            if c in skip or not lo <= max(c) <= hi:
                continue
            key = maker(c)
            if key:
                put(lv, key, x, y, z)


def axis_ring(lv, y, maker, d):
    for c in ((0, d), (d, 0)):
        if maker(c):
            put(lv, maker(c), c[0], y, c[1])


# ============================ L1 封坛 ============================
def seal_floor(c):
    m = max(c)
    if c == (0, 0):
        return 'c'                                     # 封石（核心承台）
    if m <= 3 and (c[0] == 0 or c[1] == 0 or c[0] == c[1]):
        return 'f'                                     # 八向铜地脉
    if m <= 2:
        return 'a'
    if m == 3:
        return 'b'
    if m == 4:
        return '1'                                     # 1 阶仪式石骨线
    return 'a' if (c[0] + c[1]) % 3 else 'g'


ring(0, -1, 0, 7, seal_floor)
put(0, 'e', 0, -2, 8)                                  # 参道基
put(0, 'b', 0, -1, 8)                                  # 参道
ring(0, 0, 7, 7, lambda c: 'd', skip={AXIS(7)})        # 夯土围墙（留四向门位）
put(0, 'P', 0, 0, 3)                                   # 4 只祭品台（全阶恒定）
put(0, 'a', 0, 0, 4)                                   # 封印灯柱础
put(0, 'j', 0, 1, 4)                                   # 铁栅灯杆
put(0, 'l', 0, 2, 4)                                   # 封印灯
put(0, 'a', 4, 0, 4)                                   # 四角封印柱
put(0, 'h', 4, 1, 4)

# ============================ L2 封门回廊 ============================
levels[1] = dict(levels[0])
ring(1, -1, 8, 8, lambda c: 'b', skip={AXIS(8)})       # 门前石坪
put(1, 'c', 1, 0, 8)                                   # 四向石鸟居门柱
put(1, 'c', 1, 1, 8)
put(1, 'a', 1, 0, 1)                                   # 核心四角石（不封核心四面）
put(1, 'l', 1, 1, 1)                                   # 四角灵灯
put(1, 'c', 6, 0, 6)                                   # 相缪碑
put(1, 'f', 6, 1, 6)                                   # 铜顶碑
ring(1, 1, 7, 7, lambda c: 'i', skip={AXIS(7)})        # 墙身加高
ring(1, 2, 5, 6, lambda c: 'b')                       # 回廊顶（石板，r∞5..6）
ring(1, 2, 7, 7, lambda c: 'f')                       # 墙顶铜瓦
put(1, 'n', 0, 2, 8)                                   # 门额
put(1, 'n', 1, 2, 8)
put(1, 'o', 0, 3, 8)                                   # 门额枋（相缪紫眼）

# ============================ L3 相缪殿 ============================
levels[2] = dict(levels[1])
ring(2, -1, 9, 9, lambda c: 'b', skip={AXIS(9)})       # 前庭
ring(2, 0, 9, 9, lambda c: 'i', skip={AXIS(9)})        # 前庭矮墙
put(2, 'a', 1, 2, 1)                                   # 殿柱（立于四角石上）
put(2, 'a', 1, 3, 1)
put(2, '3', 1, 4, 1)                                   # 殿柱头
ring(2, 5, 0, 2, lambda c: '3', skip={(0, 0)})         # 殿檐（留火柱口）
put(2, 'l', 1, 6, 1)                                   # 顶心灵光
for c in ((2, 3), (3, 2)):                              # 相缪影壁
    put(2, 'a', c[0], 0, c[1])
    put(2, 'a', c[0], 1, c[1])
    put(2, '3', c[0], 2, c[1])
for c in ((2, 6), (3, 6), (4, 6), (5, 6), (6, 2), (6, 3), (6, 4), (6, 5)):
    put(2, 'a', c[0], 0, c[1])                         # 回廊柱（让开四向门位）
    put(2, '3', c[0], 1, c[1])
put(2, 'j', 4, 5, 4)                                   # 封印柱林续高
put(2, 'a', 4, 6, 4)
put(2, 'l', 4, 7, 4)                                   # 柱冠灯

levels[0][(0, 0, 0)] = 'C'

doc = {
    'id': 'gensokyou:shiken_circle',
    'anchorKey': 'C',
    'toggleable': False,
    'tiers': [1, 2, 3],
    'palette': PALETTE,
}
levels_json = [{'level': i + 1} for i in range(3)]
adds = []
for i in range(3):
    prev = levels[i - 1] if i else {}
    adds.append([(k, x, y, z) for (x, y, z), k in sorted(levels[i].items())
                 if (x, y, z) not in prev])
text = (json.dumps(doc, indent=2, ensure_ascii=False)[:-1].rstrip()
        + ',\n  "levels": ' + v5.serialize_levels_v5(levels_json, adds) + '\n}\n')
OUT.write_text(text, encoding='utf-8')

# ---- 玩家 BFS 自检（SKILL §4.11）----
def bfs_check(cells, label):
    occ = set()
    for (x, y, z), k in cells.items():
        for _k, ex, ey, ez, _o in v5.expand_entry(k, x, y, z, None):
            occ.add((ex, ey, ez))

    GROUND = -1

    reserved = {(0, 0, 1), (0, 0, -1), (1, 0, 0), (-1, 0, 0),   # 核心四向（y0）
                (0, 1, 0),                                       # 正上方火柱口
                (2, 1, 2), (2, 1, -2), (-2, 1, 2), (-2, 1, -2)}   # 电容槽位（y1）
    clash = reserved & occ
    if clash:
        raise SystemExit(f'{label}: 核心留空位被占 {sorted(clash)}')

    def stand(p):
        x, y, z = p
        return p not in occ and (x, y + 1, z) not in occ and (x, y - 1, z) in occ

    spots = [(p[0], GROUND + 1, p[2]) for p in occ
             if p[1] == GROUND and stand((p[0], GROUND + 1, p[2]))]
    outer = max(abs(p[0]) + abs(p[2]) for p in spots)
    starts = [p for p in spots if abs(p[0]) + abs(p[2]) >= outer - 1]
    seen, frontier = set(starts), list(starts)
    while frontier:
        nxt = []
        for x, y, z in frontier:
            for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                for dy in (-1, 0, 1):
                    p = (x + dx, y + dy, z + dz)
                    if p not in seen and stand(p):
                        seen.add(p)
                        nxt.append(p)
        frontier = nxt
    goal = [p for p in seen
            if (p[0] == 0 and abs(p[2]) == 1) or (p[2] == 0 and abs(p[0]) == 1)]
    ok = bool(goal)
    print(f'  BFS {label}: 外缘起点{len(starts)}格 可站{len(spots)}格 '
          f'达核心四邻={"OK " + str(goal[0]) if ok else "FAIL"}')
    if not ok:
        raise SystemExit(f'{label}: 核心不可达')


for i, cells in enumerate(levels):
    by = {}
    for k in cells.values():
        by[k] = by.get(k, 0) + 1
    print(f'level {i + 1}: 累积 {len(cells)} 规范格（新增 {len(adds[i])}）'
          + ', '.join(f'{k}x{v}' for k, v in sorted(by.items())))
    bfs_check(cells, f'level {i + 1}')
print(f'写入 {OUT}')
