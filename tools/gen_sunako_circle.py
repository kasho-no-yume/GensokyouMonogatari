#!/usr/bin/env python3
"""生成 少名渡湯（炼药仪式）pattern：v5 逐级增量，四重对称四分之一规范形。

祭品台语义（用户定案）：核心槽放炼药标志主物品（定药方），祭品台放瓶装三途川水；
台数 = 一炉产量（1/2 阶 4 瓶，3 阶 8 瓶），按实际填充台数缩放产出。
故 L1 内圈 4 台（半径 3），L3 外圈再加 4 台（半径 8），L2 不加台。

层级蓝图（y 相对锚点核心，r∞ = max(|x|,|z|)）：
  L1 渡汤庭  y-2 冥河底 r∞5..6（液体层刻意缺席留空，给程序侧注水）+ 参道基
          y-1 汤庭地板 r∞<=4（r∞2 骨线用 1 阶仪式石，中心雕花汤座承核）
               四座石桥 (0,5)(0,6)、外岸 r∞7..8、参道 (0,9)(0,10)
          y0  祭品台 (0,3) + 四角魂灯柱 (4,4)      y1 药釜 (0,3)
  L2 药庐回廊 y-1 外廊 r∞9
          y0  庭缘栏墙 r∞4（留门位 (0,4)、灯位 (4,4)）+ 四角药庐 3x3（檐柱/药架/酿造台/药釜）
          y1  药庐顶 3x3 + 门额立柱 (1,4) + 魂灯柱 (4,4) 加高
          y2  门额 (0,4)(1,4) + 外檐 r∞10          y3 注连绳 (0,4) + 外墙 r∞10
  L3 冥府汤殿 y0 殿柱础 (1,1) + 外圈矮墙 r∞12
          y1 殿柱 (1,1) + 第二圈药釜 (0,8)        y2 殿柱 + 药庐顶灯楼 (8,8)
          y3 殿柱 + 药庐灯 + 外墙压顶 r∞10        y4 殿柱头 + 魂灯柱 (4,4) 加高
          y5 殿檐 r∞<=2（留 (0,0) 火柱口，3 阶仪式石）+ 殿檐顶心 (1,1)
          y-1 外圈巡道 r∞11..12（r∞11 骨线用 3 阶仪式石）+ 矮墙 r∞12 留四向门位
"""
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import validate_ritual_pattern as v5  # noqa: E402

OUT = (Path(__file__).resolve().parents[1] / 'src' / 'main' / 'resources'
       / 'data' / 'gensokyou' / 'rituals' / 'sunako_circle.json')

PALETTE = {
    'C': 'gensokyou:ritual_core',
    '1': '#gensokyou:ritual_stones_1_plus',
    '2': '#gensokyou:ritual_stones_2_plus',
    '3': '#gensokyou:ritual_stones_3_plus',
    'P': '#gensokyou:ritual_pedestals',
    'a': 'minecraft:deepslate_bricks',
    'b': 'minecraft:deepslate_tiles',
    'c': 'minecraft:chiseled_deepslate',
    'd': 'minecraft:polished_basalt',
    'e': 'minecraft:dripstone_block',
    'f': 'minecraft:calcite',
    'g': 'minecraft:moss_block',
    'h': 'minecraft:polished_deepslate',
    'i': 'minecraft:deepslate_brick_wall',
    'j': 'minecraft:soul_lantern',
    'k': 'minecraft:iron_bars',
    'l': 'minecraft:chain',
    'm': 'minecraft:cauldron',
    'n': 'minecraft:dark_prismarine',
    'o': 'minecraft:dark_oak_log',
    'p': 'minecraft:dark_oak_planks',
    'q': 'minecraft:bookshelf',
    'r': 'minecraft:brewing_stand',
    's': 'minecraft:amethyst_block',
}

R = 12
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


def boxq(lv, key, x0, x1, z0, z1, y):
    """规范象限内的方块（x,z>=0），四重展开成四座对称小室。"""
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            put(lv, key, x, y, z)


def spots(lv, key, cells, y):
    for c in cells:
        put(lv, key, c[0], y, c[1])


# ============================ L1 渡汤庭 ============================
ring(0, -2, 5, 6, lambda c: 'e' if (c[0] + c[1]) % 2 == 0 else 'n')
spots(0, 'a', [AXIS(9), AXIS(10)], -2)


def court(c):
    m = max(c)
    if c == (0, 0):
        return 'c'          # 汤座（核心承台）
    if m <= 1:
        return 'h'
    if m == 2:
        return '1'          # 1 阶仪式石骨线
    if m == 3:
        return 'b'
    return 'a'


ring(0, -1, 0, 4, court)
ring(0, -1, 7, 8, lambda c: 'g' if (c[0] + c[1]) % 2 == 0 else 'f')
spots(0, 'h', [AXIS(5), AXIS(6)], -1)          # 四座石桥
spots(0, 'd', [AXIS(9), AXIS(10)], -1)         # 参道
put(0, 'P', 0, 0, 3)                          # 内圈 4 只祭品台
put(0, 'm', 0, 1, 3)                          # 每台一釜
put(0, 'd', 4, 0, 4)                          # 四角魂灯柱
put(0, 'j', 4, 1, 4)

# ============================ L2 药庐回廊 ============================
levels[1] = dict(levels[0])
ring(1, -1, 9, 9, lambda c: 'd', skip={AXIS(9)})
# 庭缘栏墙：只留四段，r∞4 的 (1,4)(4,1) 留空作 3 格宽门洞（(0,4)(4,4) 亦空）
ring(1, 0, 4, 4, lambda c: 'i', skip={AXIS(4), (1, 4), (4, 1), (4, 4)})
ring(1, 1, 4, 4, lambda c: 'i', skip={AXIS(4), (1, 4), (4, 1), (4, 4)})
boxq(1, 'p', 7, 9, 7, 9, 1)                              # 药庐顶
for c in ((7, 9), (8, 9), (9, 7), (9, 8), (9, 9)):         # 药庐外墙（r∞9 一侧）
    put(1, 'a', c[0], 0, c[1])
put(1, 'o', 7, 0, 7)                                     # 檐柱
put(1, 'm', 8, 0, 7)                                     # 药釜
put(1, 'q', 7, 0, 8)                                     # 药架
put(1, 'r', 8, 0, 8)                                     # 酿造台
put(1, 'k', 4, 2, 4)                                     # 魂灯柱加高
put(1, 'j', 4, 3, 4)
spots(1, 'o', [(2, 4), (4, 2)], 2)                    # 门柱（立于栏墙之上）
spots(1, 'o', [AXIS(4), (1, 4), (2, 4)], 3)            # 门额
put(1, 'l', 0, 2, 4)                                   # 注连绳
ring(1, 0, 10, 10, lambda c: 'i', skip={AXIS(10)})       # 外墙
ring(1, 1, 10, 10, lambda c: 'i', skip={AXIS(10)})
ring(1, 2, 10, 10, lambda c: 'p', skip={AXIS(10)})       # 外檐
ring(1, 3, 10, 10, lambda c: 'c', skip={AXIS(10)})       # 压顶

# ============================ L3 冥府汤殿 ============================
levels[2] = dict(levels[1])
put(2, 'c', 1, 0, 1)                                     # 殿柱础
for y in (1, 2, 3):
    put(2, 'a', 1, y, 1)
put(2, '3', 1, 4, 1)                                     # 柱头
ring(2, 5, 0, 2, lambda c: '3', skip={(0, 0)})           # 殿檐（留火柱口）
put(2, 'j', 1, 6, 1)                                     # 顶心灵光
put(2, 'o', 8, 2, 8)                                     # 药庐顶灯楼
put(2, 'j', 8, 3, 8)
put(2, 'i', 4, 4, 4)                                     # 魂灯柱再加高
put(2, 'j', 4, 5, 4)
put(2, 'P', 0, 0, 8)                                     # 外圈 4 只祭品台（共 8）
put(2, 'm', 0, 1, 8)
ring(2, -1, 11, 11, lambda c: '3')                      # 外圈巡道骨线
ring(2, -1, 12, 12, lambda c: 'd')
ring(2, 0, 12, 12, lambda c: 'i', skip={AXIS(12)})      # 外圈矮墙（四向开口，与参道对位）

levels[0][(0, 0, 0)] = 'C'

doc = {
    'id': 'gensokyou:sunako_circle',
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

# ---- 玩家 BFS 自检（SKILL §4.11）：可站 = 本格与上一格皆缺席且脚下为本 pattern 地坪；
#      移动含跨上/跨下一格；逐阶确认「最外缘可站格 → 核心四邻格」连通 ----
def bfs_check(cells, label):
    occ = set()
    for (x, y, z), k in cells.items():
        for _k, ex, ey, ez, _o in v5.expand_entry(k, x, y, z, None):
            occ.add((ex, ey, ez))

    GROUND = -1  # 主地坪层：其上一层才是玩家站立高度；冥河/池内液体层刻意缺席，
                 # 故不在 occ 中，自然不算通路（§4.12 池内留空）

    # 核心功能留空位（§1）：核心四向 y0 + 正上方火柱口 + 电容槽位 y1
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
    gy = GROUND + 1
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
    print(f'  BFS {label}: 地坪y{gy} 外缘起点{len(starts)}格 可站{len(spots)}格 '
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
