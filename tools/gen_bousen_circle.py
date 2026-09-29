#!/usr/bin/env python3
"""程序化生成 忘川灯壇 bousen_circle.json（v5 逐级增量，四重对称四分之一规范形）。

意象：**平坛三重灯轮**。整座灯坛是一张完全水平的坛面（唯一的行走面 y=0），
      落差全部长在灯上——三重灯环由内向外逐级升高，形成向外的「灯火渐高」：
        第1重 16 盏：祭品台落地，蜡烛 y=1（及腰）
        第2重 16 盏：勾玉座 1 皮，祭品台 y=1，蜡烛 y=2（举高）
        第3重 32 盏：勾玉座 2 皮，祭品台 y=2，蜡烛 y=3（ overhead 伸手可及）
      玩家要亲自去点 16/32/64 根蜡烛，所以坛面绝不设落差、不设台阶、不设
      需跳跃的缘石：三环之间只用 0.5 高的石板缘石分隔（可直接走过去），
      任何一根蜡烛旁边都有同层立足点，走遍全坛零跳跃。

层高（坛面 block y=-1，坛基 y=-2；唯一行走面 y=0，各环缘石顶面 y=0.5）
  y=4   鳥居笠木        y=3   外陣蜡烛(32) / 隅灯 / 灯籠帽
  y=2   鳥居贯/岛木     y=2   中陣蜡烛(16) / 灯籠灯
       + 外陣祭品台(32) + 中門贯
  y=1   鳥居柱          y=1   内陣蜡烛(16) + 中陣祭品台(16) + 中門柱 + 灯籠柱
  y=0   核心 + 内陣祭品台(16) + 内庭装饰 + 緣石带(r=5) + 中門柱 + 玉垣(r=9)
       + 外陣祭品台(32) + 鳥居柱 + 隅柱 + 緣石带(r=7)
  y=-1  坛面 r<=9（'1'勾玉参道 / 'd'灯下深板岩 / 'a'石砖场 / 'e'深板岩瓷砖
       / 'f'苔庭 / 'g'紫水晶脉 / 'k'+'m'外陣紫珀灯台 / '3'鸟居础 / 'o'隅石）
  y=-2  坛基 r<=9（'d'勒脚 + 'u'风化皮）

留空（归程序侧）：核心四向(0,1)@y0、核心正上方(0,0)@y1、电容槽(2,2)@y1。
"""
import json
import math
import sys
from collections import deque
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import validate_ritual_pattern as v5  # noqa: E402  复用 v5 levels 排版序列化器

OUT = (Path(__file__).resolve().parents[1] / 'src' / 'main' / 'resources'
       / 'data' / 'gensokyou' / 'rituals' / 'bousen_circle.json')

PALETTE = {
    'C': 'gensokyou:ritual_core',
    '1': '#gensokyou:ritual_stones_1_plus',
    '2': '#gensokyou:ritual_stones_2_plus',
    '3': '#gensokyou:ritual_stones_3_plus',
    'P': '#gensokyou:ritual_pedestals',
    'D': 'minecraft:candle',
    # 忘川色系：暗河床坛基 + 苔庭 + 紫水晶脉 + 魂灯
    'a': 'minecraft:stone_bricks',
    'b': 'minecraft:chiseled_stone_bricks',
    'c': 'minecraft:polished_andesite',
    'd': 'minecraft:deepslate_bricks',
    'e': 'minecraft:deepslate_tiles',
    'u': 'minecraft:cracked_deepslate_bricks',
    'f': 'minecraft:moss_block',
    'g': 'minecraft:amethyst_block',
    'i': 'minecraft:quartz_pillar',
    'j': 'minecraft:sea_lantern',
    'k': 'minecraft:purpur_block',
    'l': 'minecraft:purpur_pillar',
    'm': 'minecraft:purple_stained_glass',
    'n': 'minecraft:soul_lantern',
    'o': 'minecraft:crying_obsidian',
    'p': 'minecraft:potted_cherry_sapling',
    'q': 'minecraft:stone_brick_wall',
    'w': 'minecraft:stone_brick_slab',                    # 0.5 高缘石：可直接走过
}

levels = [{}, {}, {}]
SLAB, SLAB_H = 'w', 0.5


def canon(x, z):
    x, z = abs(x), abs(z)
    return (0, x) if z == 0 else (x, z)


def put(lv, key, x, y, z):
    cx, cz = canon(x, z)
    if cx == 0 and cz == 0 and y == 0:
        raise SystemExit(f'原点 (0,0,0) 保留给锚点 C: level {lv + 1} key={key}')
    cell = (cx, y, cz)
    old = levels[lv].get(cell)
    if old and old != key:
        raise SystemExit(f'冲突 level {lv + 1} {cell}: {old} vs {key}')
    levels[lv][cell] = key


def emit(lv, table, y):
    for (cx, cz), key in table.items():
        put(lv, key, cx, y, cz)


def sq(r):
    """实心方域 r<=r 的规范格集合（轴上格统一记作 (0,d)，共 (r+1)+r² 格）。"""
    return {(0, d) for d in range(r + 1)} | {(x, z) for x in range(1, r + 1)
                                             for z in range(1, r + 1)}


def ring(r):
    """方形环 max(|x|,|z|)=r 的规范格集合（轴格只出现一次）。"""
    return {c for c in sq(r) if max(c) == r}


def fill(cells, key):
    return {c: key for c in cells}


# ================================================================ 坛面材质分区
FLOOR = {
    # 内陣：勾玉座心 + 参道 + 雕纹
    (0, 0): '1',
    (0, 1): '1', (1, 1): 'b',
    (0, 2): '1', (2, 1): '1', (2, 2): '1', (1, 2): '1',
    # r=3 内庭
    (0, 3): 'c', (3, 1): 'a', (3, 2): 'a', (3, 3): 'b', (2, 3): 'a', (1, 3): 'a',
    # r=4 第1重灯环（灯下深板岩）
    (0, 4): 'c', (4, 1): 'd', (4, 2): 'a', (4, 3): 'd', (4, 4): 'b',
    (3, 4): 'd', (2, 4): 'a', (1, 4): 'd',
    # r=5 苔庭
    (0, 5): 'c', (5, 1): 'f', (5, 2): 'f', (5, 3): 'f', (5, 4): 'f', (5, 5): 'b',
    (4, 5): 'f', (3, 5): 'f', (2, 5): 'f', (1, 5): 'f',
    # r=6 第2重灯环（深板岩瓷砖 + 紫水晶脉）
    (0, 6): 'c', (6, 1): 'e', (6, 2): 'g', (6, 3): 'e', (6, 4): 'e', (6, 5): 'g',
    (6, 6): 'e', (5, 6): 'e', (4, 6): 'g', (3, 6): 'e', (2, 6): 'e', (1, 6): 'g',
    # r=7 中陣门基
    (0, 7): '2', (7, 1): '2', (7, 2): '2', (7, 3): 'e', (7, 4): 'e', (7, 5): 'e',
    (7, 6): 'b', (7, 7): 'b', (6, 7): 'b', (5, 7): 'e', (4, 7): 'e', (3, 7): 'e',
    (2, 7): '2', (1, 7): '2',
    # r=8 第3重灯环（紫珀灯台 + 紫玻璃隙）
    (0, 8): 'c',
    (8, 1): 'k', (8, 2): 'm', (8, 3): 'k', (8, 4): 'm', (8, 5): 'k',
    (8, 6): 'm', (8, 7): 'k', (8, 8): 'g',
    (7, 8): 'k', (6, 8): 'm', (5, 8): 'k', (4, 8): 'm', (3, 8): 'k', (2, 8): 'm',
    (1, 8): 'k',
    # r=9 外周（鳥居础 / 隅石）
    (0, 9): '3', (9, 1): '3', (9, 2): 'e', (9, 3): 'e', (9, 4): 'e', (9, 5): 'e',
    (9, 6): 'e', (9, 7): 'e', (9, 8): 'e', (9, 9): 'o',
    (8, 9): 'e', (7, 9): 'e', (6, 9): 'e', (5, 9): 'e', (4, 9): 'e', (3, 9): 'e',
    (2, 9): 'e', (1, 9): '3',
}
assert set(FLOOR) == sq(9) and len(FLOOR) == 91, len(FLOOR)

# ================================================================ L1 内陣・十六灯
emit(0, {c: k for c, k in FLOOR.items() if max(c) <= 5}, -1)
emit(0, fill(sq(5), 'd'), -2)                            # 坛基勒脚

L1_LAMPS = [(4, 1), (4, 3), (3, 4), (1, 4)]              # 方位轴与四角留空
for cell in L1_LAMPS:
    emit(0, {cell: 'P'}, 0)                              # 祭品台落地
    emit(0, {cell: 'D'}, 1)                              # 蜡烛 y=1
emit(0, {(1, 1): 'f', (2, 2): 'b', (3, 3): 'p'}, 0)     # 苔 / 勾玉座 / 樱花

# ================================================================ L2 中陣・加十六灯
emit(1, {c: k for c, k in FLOOR.items() if 5 < max(c) <= 7}, -1)
emit(1, fill(ring(6) | ring(7), 'd'), -2)

L2_LAMPS = [(6, 2), (2, 6), (6, 4), (4, 6)]              # 留空 (6,1)/(1,6) 供中门
for cell in L2_LAMPS:
    emit(1, {cell: '2'}, 0)                              # 勾玉座 1 皮
    emit(1, {cell: 'P'}, 1)
    emit(1, {cell: 'D'}, 2)                              # 蜡烛 y=2
emit(1, fill(ring(5), SLAB), 0)                          # 0.5 高缘石：分隔两重灯环
GATE_POST = {(7, 1): 'i', (1, 7): 'i'}
emit(1, GATE_POST, 0)                                    # 中门（界标）
emit(1, GATE_POST, 1)
emit(1, {(0, 7): '2', (7, 1): '2', (1, 7): '2'}, 2)     # 贯
emit(1, {(0, 7): 'b', (7, 1): 'b', (1, 7): 'b'}, 3)     # 岛木
emit(1, {(7, 7): 'i'}, 0)                                # 四角灯籠
emit(1, {(7, 7): 'i'}, 1)
emit(1, {(7, 7): 'j'}, 2)
emit(1, {(7, 7): 'b'}, 3)

# ================================================================ L3 外陣・加三十二灯
emit(2, {c: k for c, k in FLOOR.items() if max(c) >= 8}, -1)
emit(2, fill(ring(8) | ring(9), 'u'), -2)

L3_LAMPS = [(8, 1), (8, 3), (8, 5), (8, 7),
            (7, 8), (5, 8), (3, 8), (1, 8)]
for cell in L3_LAMPS:
    emit(2, {cell: '3'}, 0)                              # 勾玉座 2 皮
    emit(2, {cell: '3'}, 1)
    emit(2, {cell: 'P'}, 2)
    emit(2, {cell: 'D'}, 3)                              # 蜡烛 y=3
emit(2, fill(ring(7) - {(0, 7), (7, 1), (1, 7), (7, 7)}, SLAB), 0)
TORII_POST = {(9, 1): 'l', (1, 9): 'l'}
emit(2, TORII_POST, 0)                                   # 鳥居
emit(2, TORII_POST, 1)
emit(2, {(0, 9): 'k', (9, 1): 'k', (1, 9): 'k'}, 2)      # 贯
emit(2, {(0, 9): 'l', (9, 1): 'l', (1, 9): 'l'}, 3)      # 岛木
emit(2, {(0, 9): 'k', (9, 1): 'k', (9, 2): 'k',
         (1, 9): 'k', (2, 9): 'k'}, 4)                   # 笠木
emit(2, fill({(9, z) for z in range(2, 9)} | {(z, 9) for z in range(2, 9)}, 'q'), 0)
emit(2, {(9, 4): 'n', (4, 9): 'n', (9, 6): 'n', (6, 9): 'n'}, 1)   # 魂灯
emit(2, {(9, 9): 'o'}, 0)                               # 隅柱
emit(2, {(9, 9): 'o'}, 1)
emit(2, {(9, 9): 'k'}, 2)
emit(2, {(9, 9): 'n'}, 3)                               # 隅灯
emit(2, {(8, 8): 'p'}, 0)                               # 樱花

for lv in levels:
    lv[(0, 0, 0)] = 'C'

# ================================================================ 自查
def orbit(cell):
    x, y, z = cell
    if x == 0 and z == 0:
        return [(0, y, 0)]
    if z == 0:
        return [(x, y, 0), (-x, y, 0), (0, y, x), (0, y, -x)]
    if x == 0:
        return [(0, y, z), (0, y, -z), (z, y, 0), (-z, y, 0)]
    return [(sx * x, y, sz * z) for sx in (1, -1) for sz in (1, -1)]


def cells_at(y, rlo, rhi):
    return {(x, y, z) for x in range(-rhi, rhi + 1) for z in range(-rhi, rhi + 1)
            if rlo <= max(abs(x), abs(z)) <= rhi}


def stand_map(world):
    """可站立空气格 -> 站立面标高（满块顶面 y+1，半高石板顶面 y+0.5）。"""
    solid = set(world)
    return {(x, y + 1, z): y + (SLAB_H if world.get((x, y, z)) == SLAB else 1.0)
            for (x, y, z) in solid
            if (x, y + 1, z) not in solid and (x, y + 2, z) not in solid}


def flat_walk(stand, rmax):
    """全坛连通性：只走 <=0.6 落差边（玩家 stepHeight=0.6，纯步行）。"""
    starts = [p for p in stand if max(abs(p[0]), abs(p[2])) >= rmax - 1]
    goal = {(1, 0, 0), (-1, 0, 0), (0, 0, 1), (0, 0, -1)}
    seen, q = set(starts), deque(starts)
    while q:
        cur = q.popleft()
        if cur in goal:
            return True
        x, y, z = cur
        for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            for dy in (0, 1, -1):
                n = (x + dx, y + dy, z + dz)
                if n in stand and n not in seen and abs(stand[n] - stand[cur]) <= 0.6:
                    seen.add(n)
                    q.append(n)
    return False


REACH, EYE = 3.0, 1.62


def light_audit(world):
    """点灯审计：每根蜡烛须有玩家站得到、且在手可及范围内的立足点。
    允许从低 1~2 格处仰点灯（勾玉座挡不住手），这正是「灯逐级升高」的本意。"""
    stand = stand_map(world)
    bad = []
    for p, k in world.items():
        if k != 'D':
            continue
        ok = False
        for (sx, sy, sz), top in stand.items():
            if max(abs(sx - p[0]), abs(sz - p[2])) > 1:
                continue
            eye = top + EYE
            lo, hi = p[1], p[1] + 1
            gap = lo - eye if eye < lo else (eye - hi if eye > hi else 0.0)
            horiz = math.hypot(sx - p[0], sz - p[2])
            if math.hypot(horiz, gap) <= REACH:
                ok = True
                break
        if not ok:
            bad.append(p)
    return len(bad), bad[:4]


STONE_TAGS = tuple(v for v in PALETTE.values() if v.startswith('#gensokyou:ritual_stones'))
SHELL = ((1, -1, 0, 5), (1, -2, 0, 5), (2, -1, 5, 7), (2, -2, 5, 7),
         (3, -1, 7, 9), (3, -2, 7, 9))
EXPECT = (16, 32, 64)
fail = False

for lv in range(3):
    world = {}
    for prev in levels[:lv + 1]:
        for cell, key in prev.items():
            for p in orbit(cell):
                world[p] = key
    rmax = max(max(abs(x), abs(z)) for (x, _, z) in world)
    holes = [f'y{y} r{rlo}..{rhi}' for start, y, rlo, rhi in SHELL
             if lv >= start - 1 and not cells_at(y, rlo, rhi) <= set(world)]
    stone = sum(1 for k in world.values() if PALETTE[k] in STONE_TAGS)
    ped = sum(1 for k in world.values() if k == 'P')
    can = sum(1 for k in world.values() if k == 'D')
    off = [p for p, k in world.items()
           if k == 'D' and world.get((p[0], p[1] - 1, p[2])) != 'P']
    stand = stand_map(world)
    walk = flat_walk(stand, rmax)
    dark, dark_at = light_audit(world)
    ok = (not holes and can == EXPECT[lv] and ped == EXPECT[lv] and not off
          and walk and not dark and stone / len(world) <= 0.30)
    fail |= not ok
    print(f'level {lv + 1}: 累积 {len(world)} 格 · 半径 {rmax} · 坛面全平 y=0 · '
          f'{"坛身实心" if not holes else f"镂空 {holes}"} · '
          f'仪式石 {stone} ({stone / len(world):.1%}, 上限 30%) · '
          f'祭品台 {ped} · 蜡烛 {can} · 蜡烛下非台 {len(off)} · '
          f'点不到 {dark}{dark_at if dark else ""} · '
          f'全坛纯步行 {"通" if walk else "断"} · {"PASS" if ok else "FAIL"}')
assert fail is False, '自查未通过'

# ================================================================ 落盘
doc = {
    'id': 'gensokyou:bousen_circle',
    'anchorKey': 'C',
    'toggleable': False,
    'tiers': [1, 2, 3],
    'palette': PALETTE,
}
levels_json = [{'level': i + 1} for i in range(3)]
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
print(f'写入 {OUT} ({OUT.stat().st_size} 字节)，'
      f'各级新增条目 {[len(a) for a in adds_per_level]}')
